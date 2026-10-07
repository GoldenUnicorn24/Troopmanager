package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** The same distances, docking progress and local breaches drive combat and the Canvas. */
object SiegeEngine {
    data class Result(val battle: BattleSession, val structural: Map<BattleSection, Int>,
        val gateDamage: Map<BattleSection, Int>, val splash: Map<BattleSection, Double>, val events: List<String>,
        val collapsed: Set<BattleSection> = emptySet())

    fun advance(state: GameState, original: BattleSession, decision: BattleDecision?, target: BattleSection?): Result {
        val fortified = original.tactic == Tactic.FORTIFY
        val events = mutableListOf<String>()
        val structural = mutableMapOf<BattleSection, Int>()
        val gateDamage = mutableMapOf<BattleSection, Int>()
        val splash = mutableMapOf<BattleSection, Double>()
        val fronts = original.fronts.map { front ->
            val intent = BattleAiEngine.intent(original, front)
            val troops = original.contingents.filter { it.section == front.section && it.soldiers > 0 && !it.routed }
            val count = troops.sumOf { it.soldiers }.coerceAtLeast(1)
            val morale = troops.sumOf { it.soldiers.toLong() * it.morale }.div(count).toInt()
            val cohesion = troops.sumOf { it.soldiers.toLong() * it.cohesion }.div(count).toInt()
            val fallback = !fortified && original.segment(front.section)?.let {
                it.contactState == BattleContactState.FIELD_CONTACT && !it.fallenBack } == true &&
                (morale < original.plan.moraleFallback || cohesion < original.plan.cohesionFallback)
            val speed = if (original.enemy == EnemyType.TAO_TEI) 60 else 40
            val movement = when (intent) { BattleAiIntent.WITHDRAW -> -45; BattleAiIntent.MISSILES -> 10
                else -> (speed * (.55 + front.cohesion / 180.0) * (1 - front.fatigue / 300.0)).toInt().coerceAtLeast(15) }
            val preferred = original.plan.preferredEngagementDistance
            val ownMovement = if (fortified || preferred == 0 || intent == BattleAiIntent.WITHDRAW) 0
                else (front.enemyDistance - preferred).coerceIn(-20, 20)
            front.copy(intent = intent, enemyDistance = (front.enemyDistance - movement - ownMovement + (if (fallback) 80 else 0) -
                if (decision == BattleDecision.ADVANCE && target == front.section && !fortified) 40 else 0).coerceIn(0, 400))
        }
        val crewAvailable = fronts.associate { front -> front.section to original.enemyRoster.filter {
            it.section == front.section && it.type.ranged < 8 }.sumOf { it.soldiers } }.toMutableMap()
        val artilleryTarget = if (decision == BattleDecision.ARTILLERY_TARGET) original.siegeDevices.filter {
            it.section == target && it.detected && !it.disabled && it.distance <= 320 }.maxByOrNull {
                when (it.type) { SiegeDevice.TOWER -> 6; SiegeDevice.RAM -> 5; SiegeDevice.CATAPULT -> 4; SiegeDevice.TUNNEL -> 3; else -> 2 } }?.id else null
        var devices = original.siegeDevices.map { device ->
            if (device.disabled || device.integrity <= 0) device.copy(disabled = true)
            else if (device.type == SiegeDevice.CATAPULT && device.ammunition == 0) device.copy(crew = 0)
            else {
                val front = fronts.first { it.section == device.section }
                val crew = minOf(device.crew, crewAvailable[device.section] ?: 0)
                crewAvailable[device.section] = (crewAvailable[device.section] ?: 0) - crew
                val skill = original.contingents.filter { it.section == device.section && !it.commanderWounded }.mapNotNull {
                    c -> c.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.siege }
                }.maxOrNull() ?: state.player.tactics
                val detected = device.detected || device.type != SiegeDevice.TUNNEL || skill + state.player.tactics >= 70 || device.progress >= 50
                if (crew == 0 || front.intent == BattleAiIntent.WITHDRAW) device.copy(crew = crew, detected = detected,
                    distance = (device.distance + 30).coerceAtMost(400))
                else {
                    val speed = when (device.type) { SiegeDevice.TOWER -> 22; SiegeDevice.RAM -> 45; SiegeDevice.LADDERS -> 40; SiegeDevice.CLIMBERS -> 60; else -> 0 }
                    val distance = (device.distance - speed).coerceAtLeast(0)
                    var progress = device.progress
                    var hp = device.integrity
                    if (target == device.section) {
                        when (decision) {
                            BattleDecision.COUNTER_TUNNEL -> if (device.type == SiegeDevice.TUNNEL && detected) { progress = (progress - 35).coerceAtLeast(0); hp -= 30 + skill / 10 }
                            BattleDecision.REPEL_LADDERS -> if (device.type in listOf(SiegeDevice.LADDERS, SiegeDevice.CLIMBERS) && distance == 0) { progress = (progress - 35).coerceAtLeast(0); hp -= 18 }
                            BattleDecision.ARTILLERY_TARGET -> if (device.id == artilleryTarget) {
                                val crewCount = original.contingents.filter { it.section == target && it.type == UnitType.DRAGON_ARTILLERY && !it.routed }.sumOf { it.soldiers.toLong() }
                                val required = ((crewCount + 9) / 10).coerceAtLeast(1)
                                val supply = minOf(1.0, original.battleArtilleryRemaining.toDouble() / required)
                                if (crewCount > 0) {
                                    hp -= ((25 + crewCount.coerceAtMost(150) / 3) * supply).toInt()
                                    events += "Gezieltes Artilleriefeuer trifft ${device.type.label} am ${device.section.label}."
                                }
                            }
                            else -> Unit
                        }
                    }
                    if (hp <= 0) device.copy(integrity = 0, crew = crew, disabled = true, detected = detected)
                    else {
                        if (distance == 0) progress = (progress + when (device.type) {
                            SiegeDevice.LADDERS -> 30; SiegeDevice.CLIMBERS -> 45; SiegeDevice.TOWER -> 35; SiegeDevice.TUNNEL -> 15; else -> 0
                        }).coerceAtMost(100)
                        if (device.type == SiegeDevice.CATAPULT && device.ammunition > 0 && distance <= 320) {
                            structural[device.section] = (structural[device.section] ?: 0) + 3 + crew / 10
                            splash[device.section] = (splash[device.section] ?: 0.0) + crew * .06
                        }
                        if (device.type == SiegeDevice.RAM && distance == 0) {
                            val protection = state.war.gateReinforcement / 5 +
                                (if (original.plan.gatePolicy == GatePolicy.GATE_DEFENSE) 3 else 0) +
                                if (decision == BattleDecision.HOLD_GATE && target == device.section) 5 else 0
                            gateDamage[device.section] = (gateDamage[device.section] ?: 0) + (9 + crew / 10 - protection).coerceAtLeast(1)
                        }
                        if (device.type == SiegeDevice.TUNNEL && progress >= 100) structural[device.section] = (structural[device.section] ?: 0) + 28
                        device.copy(distance = distance, progress = progress, crew = crew, integrity = hp.coerceIn(0, 100), detected = detected,
                            ammunition = if (device.type == SiegeDevice.CATAPULT) (device.ammunition - 1).coerceAtLeast(0) else device.ammunition)
                    }
                }
            }
        }
        val segments = original.segments.map { segment ->
            val front = fronts.first { it.section == segment.section }
            val focus = target == segment.section
            var integrity = (segment.integrity - (structural[segment.section] ?: 0)).coerceAtLeast(0)
            var gate = (segment.gateIntegrity - (gateDamage[segment.section] ?: 0)).coerceAtLeast(0)
            var progress = segment.assaultProgress
            var open = segment.gateOpen
            var fallenBack = segment.fallenBack
            var order = segment.rangedOrder
            var priority = segment.devicePriority
            val defenders = original.contingents.filter { it.section == segment.section && !it.routed && it.soldiers > 0 }
            val ownCount = defenders.sumOf { it.soldiers }
            val ownMorale = if (ownCount == 0) 0 else defenders.sumOf { it.soldiers.toLong() * it.morale }.div(ownCount).toInt()
            val cohesion = if (ownCount == 0) 0 else defenders.sumOf { it.soldiers.toLong() * it.cohesion }.div(ownCount).toInt()
            if (segment.contactState.allowsMelee && !fallenBack &&
                (ownMorale < original.plan.moraleFallback || cohesion < original.plan.cohesionFallback)) {
                fallenBack = true
                events += "${segment.section.label}: Rückfall auf die zweite Linie (${original.plan.fallbackPolicy.label}); Moral $ownMorale%, Kohäsion $cohesion%."
            }
            if (fortified && original.plan.gatePolicy == GatePolicy.CONTROLLED_SORTIE &&
                original.plan.doctrine == BattleDoctrine.COUNTERATTACK && segment.section == BattleSection.CENTER &&
                front.enemyDistance <= 45 && ownCount > front.enemySoldiers * 2L && ownMorale >= 65 && cohesion >= 60 && !open) {
                open = true
                events += "Ein kontrollierter Ausfall öffnet das Tor bei klarer lokaler Überlegenheit."
            }
            if (focus) {
                when (decision) {
                    BattleDecision.HOLD_FIRE -> order = RangedOrder.HOLD
                    BattleDecision.NORMAL_FIRE -> { order = RangedOrder.NORMAL; priority = false }
                    BattleDecision.PRIORITIZE_DEVICES -> priority = true
                    BattleDecision.OPEN_GATE -> open = true
                    BattleDecision.HOLD_GATE -> open = false
                    BattleDecision.FALL_BACK_COURTYARD -> fallenBack = true
                    BattleDecision.SCALE_WALL, BattleDecision.TOWER_ASSAULT -> if (!fortified && segment.cover > 0) { progress = (progress + 40).coerceAtMost(100); integrity = (integrity - 7).coerceAtLeast(0) }
                    BattleDecision.BREACH_GATE, BattleDecision.UNDERMINE, BattleDecision.ARTILLERY_TARGET -> if (!fortified && segment.cover > 0) { integrity = (integrity - if (decision == BattleDecision.UNDERMINE) 22 else 14).coerceAtLeast(0); if (segment.section == BattleSection.CENTER) gate = integrity }
                    else -> Unit
                }
            }
            val local = devices.filter { it.section == segment.section && !it.disabled && it.crew > 0 }
            val assault = local.filter { it.type in listOf(SiegeDevice.LADDERS, SiegeDevice.TOWER, SiegeDevice.CLIMBERS) && it.distance == 0 }
            if (fortified) progress = if (segment.assaultProgress >= 100) 100 else assault.maxOfOrNull { it.progress } ?: 0
            if (focus && decision == BattleDecision.REPEL_LADDERS) progress = (progress - 35).coerceAtLeast(0)
            if (focus && decision == BattleDecision.COUNTERATTACK && front.position >= 50) progress = (progress - 30).coerceAtLeast(0)
            val assaultWidth = maxOf(segment.assaultWidth, assault.filter { it.progress >= 100 }.map {
                if (it.type == SiegeDevice.TOWER) 45 else if (it.type == SiegeDevice.CLIMBERS) 12 else 8 }.sum())
            val hasFort = fortified || original.segments.any { it.cover > 0 }
            val breach = if (hasFort && (integrity == 0 || segment.section == BattleSection.CENTER && gate == 0))
                maxOf(segment.breachWidth, if (integrity == 0) 80 else 28) else segment.breachWidth
            val inside = segment.contactState in listOf(BattleContactState.BREACHED, BattleContactState.COURTYARD)
            val contact = when {
                front.enemySoldiers == 0 || front.intent == BattleAiIntent.WITHDRAW -> BattleContactState.DISTANT
                !hasFort && front.enemyDistance == 0 -> BattleContactState.FIELD_CONTACT
                hasFort && front.enemyDistance == 0 && fallenBack && (breach > 0 || open || progress >= 100 || inside) -> BattleContactState.COURTYARD
                hasFort && front.enemyDistance == 0 && (breach > 0 || open || inside) -> BattleContactState.BREACHED
                hasFort && front.enemyDistance == 0 && progress >= 100 -> BattleContactState.WALL_ASSAULT
                hasFort && local.any { it.distance == 0 && it.type != SiegeDevice.TUNNEL } -> BattleContactState.SIEGE_CONTACT
                front.enemyDistance <= 200 -> BattleContactState.MISSILE_RANGE
                front.enemyDistance <= 300 -> BattleContactState.APPROACH
                else -> BattleContactState.DISTANT
            }
            if (!segment.contactState.allowsMelee && contact.allowsMelee) events += "${segment.section.label}: ${contact.label}; lokaler Nahkontakt ist möglich."
            if (segment.gateIntegrity > 0 && gate == 0 && segment.section == BattleSection.CENTER) events += "Das Tor bricht; nur das Zentrum ist geöffnet."
            if (segment.integrity > 0 && integrity == 0) events += "${segment.section.label}: Mauer bricht; eine Bresche öffnet sich."
            val stage = when {
                front.intent == BattleAiIntent.WITHDRAW || front.enemySoldiers == 0 -> SiegeStage.WITHDRAWAL
                contact == BattleContactState.COURTYARD -> SiegeStage.COURTYARD
                contact == BattleContactState.BREACHED -> SiegeStage.BREACH
                contact == BattleContactState.WALL_ASSAULT -> SiegeStage.ASSAULT
                segment.assaultProgress > progress && focus -> SiegeStage.REPULSED
                contact in listOf(BattleContactState.SIEGE_CONTACT, BattleContactState.FIELD_CONTACT) -> SiegeStage.CONTACT
                local.any { it.detected && it.distance <= 320 } -> SiegeStage.DEVICES_IN_RANGE
                contact == BattleContactState.MISSILE_RANGE -> SiegeStage.MISSILE_FIRE
                contact == BattleContactState.DISTANT -> SiegeStage.SCOUTING
                else -> SiegeStage.APPROACH
            }
            if (stage != segment.siegeStage) events += "${segment.section.label}: ${stage.label} (${front.enemyDistance} m)."
            segment.copy(integrity = integrity, gateIntegrity = gate, assaultProgress = progress, assaultWidth = assaultWidth, breachWidth = breach,
                contactState = contact, rangedOrder = order, gateOpen = open, fallenBack = fallenBack, devicePriority = priority, siegeStage = stage)
        }
        devices = devices.map { it.copy(disabled = it.disabled || it.integrity == 0) }
        val average = segments.sumOf { if (it.section == BattleSection.CENTER) (it.integrity + it.gateIntegrity) / 2 else it.integrity } / 3
        return Result(original.copy(fronts = fronts, segments = segments, siegeDevices = devices,
            devices = devices.filterNot { it.disabled }.map { it.type }.distinct(),
            wallIntegrity = if (fortified) average else original.wallIntegrity,
            enemyFortification = if (!fortified && original.enemyFortification > 0) segments.sumOf { it.integrity } / 3 else original.enemyFortification),
            structural, gateDamage, splash, events, segments.filter { changed ->
                val before = original.segment(changed.section)!!
                before.integrity > 0 && changed.integrity == 0 || before.gateIntegrity > 0 && changed.gateIntegrity == 0
            }.map { it.section }.toSet())
    }

    fun frontage(battle: BattleSession, section: BattleSection): Int {
        val segment = battle.segment(section)
        return when (segment?.contactState) {
            BattleContactState.WALL_ASSAULT -> segment.assaultWidth.coerceAtLeast(8)
            BattleContactState.BREACHED -> if (segment.gateOpen && segment.breachWidth == 0) 20 else segment.breachWidth.coerceIn(18, 160)
            BattleContactState.COURTYARD -> 32
            BattleContactState.FIELD_CONTACT -> terrainFrontage(battle.terrain[section] ?: BattleTerrain.PLAIN)
            else -> 0
        }
    }

    fun terrainFrontage(terrain: BattleTerrain): Int = when (terrain) {
        BattleTerrain.BRIDGE -> 24; BattleTerrain.PASS -> 32; BattleTerrain.RIVER, BattleTerrain.STREET -> 50
        BattleTerrain.FOREST, BattleTerrain.HILL -> 90; BattleTerrain.MUD -> 100; else -> 180
    }

    fun exposure(segment: FortificationSegmentState?): Double {
        if (segment == null || segment.cover == 0.0) return 1.0
        val retained = when (segment.contactState) {
            BattleContactState.BREACHED -> .25
            BattleContactState.COURTYARD -> .15
            BattleContactState.WALL_ASSAULT -> .75
            else -> .35 + .65 * segment.integrity / 100.0
        }
        return (1.0 - segment.cover * retained).coerceIn(.15, 1.0)
    }
}
