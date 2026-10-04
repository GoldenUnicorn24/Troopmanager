package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** The same distances, docking progress and local breaches drive combat and the Canvas. */
object SiegeEngine {
    data class Result(val battle: BattleSession, val structural: Map<BattleSection, Int>,
        val gateDamage: Map<BattleSection, Int>, val splash: Map<BattleSection, Double>, val events: List<String>)

    fun advance(state: GameState, original: BattleSession, decision: BattleDecision?, target: BattleSection?): Result {
        val fortified = original.tactic == Tactic.FORTIFY
        val events = mutableListOf<String>()
        val structural = mutableMapOf<BattleSection, Int>()
        val gateDamage = mutableMapOf<BattleSection, Int>()
        val splash = mutableMapOf<BattleSection, Double>()
        val fronts = original.fronts.map { front ->
            val intent = BattleAiEngine.intent(original, front)
            val speed = if (original.enemy == EnemyType.TAO_TEI) 60 else 40
            val movement = when (intent) { BattleAiIntent.WITHDRAW -> -45; BattleAiIntent.MISSILES -> 10; else -> speed }
            front.copy(intent = intent, enemyDistance = (front.enemyDistance - movement -
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
                                hp -= ((25 + crewCount.coerceAtMost(150) / 3) * supply).toInt()
                                events += "Gezieltes Artilleriefeuer trifft ${device.type.label} am ${device.section.label}."
                            }
                            else -> Unit
                        }
                    }
                    if (hp <= 0) device.copy(integrity = 0, crew = crew, disabled = true, detected = detected)
                    else {
                        if (distance == 0) progress = (progress + when (device.type) {
                            SiegeDevice.LADDERS -> 30; SiegeDevice.CLIMBERS -> 45; SiegeDevice.TOWER -> 35; SiegeDevice.TUNNEL -> 15; else -> 0
                        }).coerceAtMost(100)
                        if (device.type == SiegeDevice.CATAPULT && device.ammunition > 0) {
                            structural[device.section] = (structural[device.section] ?: 0) + 3 + crew / 10
                            splash[device.section] = (splash[device.section] ?: 0.0) + crew * .06
                        }
                        if (device.type == SiegeDevice.RAM && distance == 0) {
                            val protection = state.war.gateReinforcement / 5 +
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
            if (focus) {
                when (decision) {
                    BattleDecision.HOLD_FIRE -> order = RangedOrder.HOLD
                    BattleDecision.NORMAL_FIRE -> { order = RangedOrder.NORMAL; priority = false }
                    BattleDecision.PRIORITIZE_DEVICES -> priority = true
                    BattleDecision.OPEN_GATE -> open = true
                    BattleDecision.HOLD_GATE -> open = false
                    BattleDecision.FALL_BACK_COURTYARD -> fallenBack = true
                    BattleDecision.SCALE_WALL, BattleDecision.TOWER_ASSAULT -> if (!fortified && original.enemyFortification > 0) { progress = (progress + 40).coerceAtMost(100); integrity = (integrity - 7).coerceAtLeast(0) }
                    BattleDecision.BREACH_GATE, BattleDecision.UNDERMINE, BattleDecision.ARTILLERY_TARGET -> if (!fortified && original.enemyFortification > 0) { integrity = (integrity - if (decision == BattleDecision.UNDERMINE) 22 else 14).coerceAtLeast(0); if (segment.section == BattleSection.CENTER) gate = integrity }
                    else -> Unit
                }
            }
            val local = devices.filter { it.section == segment.section && !it.disabled && it.crew > 0 }
            val assault = local.filter { it.type in listOf(SiegeDevice.LADDERS, SiegeDevice.TOWER, SiegeDevice.CLIMBERS) && it.distance == 0 }
            if (fortified) progress = if (segment.assaultProgress >= 100) 100 else assault.maxOfOrNull { it.progress } ?: 0
            if (focus && decision == BattleDecision.COUNTERATTACK && front.position >= 50) progress = (progress - 30).coerceAtLeast(0)
            val assaultWidth = maxOf(segment.assaultWidth, assault.filter { it.progress >= 100 }.map {
                if (it.type == SiegeDevice.TOWER) 45 else if (it.type == SiegeDevice.CLIMBERS) 12 else 8 }.sum())
            val hasFort = fortified || original.segments.any { it.cover > 0 }
            val breach = if (hasFort && (integrity == 0 || segment.section == BattleSection.CENTER && gate == 0))
                maxOf(segment.breachWidth, if (integrity == 0) 80 else 28) else segment.breachWidth
            val contact = when {
                front.enemySoldiers == 0 -> BattleContactState.DISTANT
                !hasFort && front.enemyDistance == 0 -> BattleContactState.FIELD_CONTACT
                hasFort && front.enemyDistance == 0 && fallenBack && (breach > 0 || open || progress >= 100) -> BattleContactState.COURTYARD
                hasFort && front.enemyDistance == 0 && (breach > 0 || open) -> BattleContactState.BREACHED
                hasFort && front.enemyDistance == 0 && progress >= 100 -> BattleContactState.WALL_ASSAULT
                hasFort && local.any { it.distance == 0 && it.type != SiegeDevice.TUNNEL } -> BattleContactState.SIEGE_CONTACT
                front.enemyDistance <= 200 -> BattleContactState.MISSILE_RANGE
                front.enemyDistance <= 300 -> BattleContactState.APPROACH
                else -> BattleContactState.DISTANT
            }
            if (!segment.contactState.allowsMelee && contact.allowsMelee) events += "${segment.section.label}: ${contact.label}; lokaler Nahkontakt ist möglich."
            if (segment.gateIntegrity > 0 && gate == 0 && segment.section == BattleSection.CENTER) events += "Das Tor bricht; nur das Zentrum ist geöffnet."
            if (segment.integrity > 0 && integrity == 0) events += "${segment.section.label}: Mauer bricht; eine Bresche öffnet sich."
            segment.copy(integrity = integrity, gateIntegrity = gate, assaultProgress = progress, assaultWidth = assaultWidth, breachWidth = breach,
                contactState = contact, rangedOrder = order, gateOpen = open, fallenBack = fallenBack, devicePriority = priority)
        }
        devices = devices.map { it.copy(disabled = it.disabled || it.integrity == 0) }
        val average = segments.sumOf { if (it.section == BattleSection.CENTER) (it.integrity + it.gateIntegrity) / 2 else it.integrity } / 3
        return Result(original.copy(fronts = fronts, segments = segments, siegeDevices = devices,
            devices = devices.filterNot { it.disabled }.map { it.type }.distinct(),
            wallIntegrity = if (fortified) average else original.wallIntegrity,
            enemyFortification = if (!fortified && original.enemyFortification > 0) segments.sumOf { it.integrity } / 3 else original.enemyFortification),
            structural, gateDamage, splash, events)
    }

    fun frontage(battle: BattleSession, section: BattleSection): Int {
        val segment = battle.segment(section)
        return when (segment?.contactState) {
            BattleContactState.WALL_ASSAULT -> segment.assaultWidth.coerceAtLeast(8)
            BattleContactState.BREACHED -> if (segment.gateOpen && segment.breachWidth == 0) 20 else segment.breachWidth.coerceIn(18, 160)
            BattleContactState.COURTYARD -> 32
            BattleContactState.FIELD_CONTACT -> when (battle.terrain[section]) {
                BattleTerrain.BRIDGE -> 24; BattleTerrain.PASS -> 32; BattleTerrain.RIVER, BattleTerrain.STREET -> 50
                BattleTerrain.FOREST, BattleTerrain.HILL -> 90; BattleTerrain.MUD -> 100; else -> 180
            }
            else -> 0
        }
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
