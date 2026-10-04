package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.floor
import kotlin.random.Random

/** Aggregate attack pools followed by one casualty budget per side and front. */
object BattleResolutionEngine {
    data class Result(val battle: BattleSession, val losses: List<UnitAllocation>,
        val commandedLosses: Map<Pair<Long, UnitType>, Int>, val report: BattleExchangeReport)

    fun stochasticRound(expected: Double, rng: Random): Int {
        require(expected.isFinite() && expected >= 0)
        val bounded = expected.coerceAtMost(Int.MAX_VALUE.toDouble())
        val whole = floor(bounded).toInt()
        return if (whole < Int.MAX_VALUE && rng.nextDouble() < bounded - whole) whole + 1 else whole
    }

    /** Capped largest-remainder allocation. Runtime depends on formations, never soldiers. */
    fun allocate(budget: Int, capacities: List<Int>, weights: List<Double>): List<Int> {
        require(capacities.size == weights.size && budget >= 0 && capacities.all { it >= 0 } && weights.all { it.isFinite() && it >= 0 })
        val result = MutableList(capacities.size) { 0 }
        var left = minOf(budget.toLong(), capacities.sumOf { it.toLong() }).toInt()
        while (left > 0) {
            val eligible = capacities.indices.filter { result[it] < capacities[it] && weights[it] > 0 }
            if (eligible.isEmpty()) break
            val sum = eligible.sumOf { weights[it] }
            val fractions = mutableListOf<Pair<Int, Double>>()
            val before = left
            eligible.forEach { index ->
                val ideal = before * weights[index] / sum
                val amount = minOf(capacities[index] - result[index], floor(ideal).toInt())
                result[index] += amount
                left -= amount
                if (result[index] < capacities[index]) fractions += index to (ideal - floor(ideal))
            }
            fractions.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first }).forEach { (index, _) ->
                if (left > 0 && result[index] < capacities[index]) { result[index]++; left-- }
            }
            if (before == left) break
        }
        return result
    }

    private fun sources(budget: Int, ranged: Double, melee: Double, splash: Double): BattleDamageSources {
        val parts = allocate(budget, List(3) { budget }, listOf(ranged, melee, splash))
        return BattleDamageSources(parts[0], parts[1], parts[2])
    }

    fun resolve(state: GameState, battle: BattleSession, siege: SiegeEngine.Result,
        decision: BattleDecision?, target: BattleSection?): Result {
        val changed = battle.contingents.toMutableList()
        val enemies = BattleReportEngine.rosterAfterFrontLosses(battle.enemyRoster, battle.fronts).toMutableList()
        val losses = mutableListOf<UnitAllocation>()
        val commanded = mutableMapOf<Pair<Long, UnitType>, Int>()
        var arrows = battle.battleArrowsRemaining
        var enemyArrows = battle.enemyArrowsRemaining
        var artillery = battle.battleArtilleryRemaining
        var enemyArtillery = battle.enemyArtilleryRemaining
        val devices = battle.siegeDevices.toMutableList()
        val reports = mutableListOf<FrontExchangeReport>()
        val events = siege.events.toMutableList()
        val newFronts = battle.fronts.sortedBy { if (target == it.section && decision in listOf(BattleDecision.ARROW_VOLLEY, BattleDecision.FOCUS_FIRE, BattleDecision.ARTILLERY_TARGET)) -1 else it.section.ordinal }.map { front ->
            val segment = battle.segment(front.section)!!
            val contact = segment.contactState.allowsMelee
            val width = SiegeEngine.frontage(battle, front.section)
            val indices = changed.indices.filter { changed[it].section == front.section && changed[it].soldiers > 0 && !changed[it].routed }
            val units = indices.map { changed[it] }
            val enemyIndices = enemies.indices.filter { enemies[it].section == front.section && enemies[it].soldiers > 0 }
            val enemyUnits = enemyIndices.map { enemies[it] }
            val count = units.sumOf { it.soldiers }
            val enemyCount = minOf(front.enemySoldiers, enemyUnits.sumOf { it.soldiers })
            val focus = target == front.section
            val active = allocate(if (contact) minOf(width, count) else 0,
                units.map { if (it.type == UnitType.DRAGON_ARTILLERY) 0 else it.soldiers }, units.map { it.soldiers * if (it.type.ranged >= 8) .45 else 1.5 })
            val operators = battle.siegeDevices.filter { it.section == front.section && !it.disabled && it.type in listOf(SiegeDevice.RAM, SiegeDevice.CATAPULT, SiegeDevice.TUNNEL) }.sumOf { it.crew }
            val assignedCrew = allocate(operators, enemyUnits.map { if (it.type.ranged < 8) it.soldiers else 0 }, enemyUnits.map { if (it.type.ranged < 8) it.soldiers.toDouble() else 0.0 })
            val enemyActive = allocate(if (contact) minOf(width, enemyCount) else 0,
                enemyUnits.mapIndexed { i, c -> if (c.type == UnitType.DRAGON_ARTILLERY) 0 else c.soldiers - assignedCrew[i] }, enemyUnits.map { it.soldiers * if (it.type.ranged >= 8) .45 else 1.5 })
            val missileRange = front.enemyDistance <= 220 && enemyCount > 0
            val aimedArtillery = focus && decision == BattleDecision.ARTILLERY_TARGET
            val shootingSlots = if (battle.tactic == Tactic.FORTIFY) 140 else if (battle.terrain[front.section] == BattleTerrain.FOREST) 65 else 180
            val shooters = allocate(if (missileRange || aimedArtillery) shootingSlots else 0,
                units.mapIndexed { index, c -> if (c.type.ranged >= 8 &&
                    (segment.rangedOrder != RangedOrder.HOLD || aimedArtillery && c.type == UnitType.DRAGON_ARTILLERY) &&
                    (missileRange || c.type == UnitType.DRAGON_ARTILLERY)) c.soldiers - active[index] else 0 },
                units.map { if (it.type.ranged >= 8) it.soldiers.toDouble() else 0.0 })
            val enemyShooters = allocate(if (missileRange && front.intent != BattleAiIntent.WITHDRAW) shootingSlots else 0,
                enemyUnits.mapIndexed { index, c -> if (c.type.ranged >= 8) c.soldiers - enemyActive[index] else 0 },
                enemyUnits.map { if (it.type.ranged >= 8) it.soldiers.toDouble() else 0.0 })
            val arrowShooters = shooters.indices.sumOf { if (units[it].type == UnitType.DRAGON_ARTILLERY) 0 else shooters[it] }
            val firingCost = if (focus && decision == BattleDecision.ARROW_VOLLEY) 3 else if (focus && decision == BattleDecision.FOCUS_FIRE) 2 else 1
            val ammoCost = minOf(arrows, (arrowShooters.toLong() * firingCost * DoctrineEngine.arrowConsumptionFactor(state)).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val required = (arrowShooters.toLong() * firingCost * DoctrineEngine.arrowConsumptionFactor(state)).coerceAtLeast(1.0)
            val ammoFactor = if (arrowShooters == 0) 1.0 else ammoCost / required
            arrows -= ammoCost
            val enemyArrowShooters = enemyShooters.indices.sumOf { if (enemyUnits[it].type == UnitType.DRAGON_ARTILLERY) 0 else enemyShooters[it] }
            val enemyAmmoCost = minOf(enemyArrows, enemyArrowShooters)
            val enemyAmmoFactor = if (enemyArrowShooters == 0) 1.0 else enemyAmmoCost.toDouble() / enemyArrowShooters
            enemyArrows -= enemyAmmoCost
            val ownArtilleryCrew = shooters.indices.sumOf { if (units[it].type == UnitType.DRAGON_ARTILLERY) shooters[it] else 0 }
            val ownChargesRequired = (ownArtilleryCrew + 9) / 10
            val ownCharges = minOf(artillery, ownChargesRequired)
            val artilleryFactor = if (ownChargesRequired == 0) 0.0 else ownCharges.toDouble() / ownChargesRequired
            artillery -= ownCharges
            val enemyArtilleryCrew = enemyShooters.indices.sumOf { if (enemyUnits[it].type == UnitType.DRAGON_ARTILLERY) enemyShooters[it] else 0 }
            val enemyChargesRequired = (enemyArtilleryCrew + 9) / 10
            val enemyCharges = minOf(enemyArtillery, enemyChargesRequired)
            val enemyArtilleryFactor = if (enemyChargesRequired == 0) 0.0 else enemyCharges.toDouble() / enemyChargesRequired
            enemyArtillery -= enemyCharges
            val rangedCommand = if (focus && decision == BattleDecision.ARROW_VOLLEY) 1.65 else if (focus && decision in listOf(BattleDecision.FOCUS_ARCHERS, BattleDecision.FOCUS_FIRE)) 1.30 else 1.0
            val ownRangedPower = units.indices.sumOf { index ->
                val c = units[index]
                shooters[index] * c.type.ranged * 2.0 * ownQuality(state, battle, c, true) *
                    (if (c.type == UnitType.DRAGON_ARTILLERY) artilleryFactor else ammoFactor)
            } * rangedCommand
            val terrain = battle.terrain[front.section] ?: BattleTerrain.PLAIN
            val enemyRangedPower = enemyUnits.indices.sumOf { index ->
                val c = enemyUnits[index]
                enemyShooters[index] * c.type.ranged * 2.0 * enemyQuality(c, front) * battle.rangedWeather *
                    BattleEngine.terrainMultiplier(terrain, c.type, BattlePhase.RANGED) *
                    (if (c.type == UnitType.DRAGON_ARTILLERY) enemyArtilleryFactor else enemyAmmoFactor)
            }
            val targetIndex = if (segment.devicePriority) devices.indices.filter { devices[it].section == front.section &&
                !devices[it].disabled && devices[it].detected && devices[it].crew > 0 && devices[it].distance <= 220 }.minByOrNull { devices[it].distance } else null
            val artilleryPower = units.indices.sumOf { index -> if (units[index].type != UnitType.DRAGON_ARTILLERY) 0.0 else
                shooters[index] * units[index].type.ranged * 2.0 * ownQuality(state, battle, units[index], true) * artilleryFactor } * rangedCommand
            val aimedPower = if (aimedArtillery) artilleryPower else if (targetIndex == null) 0.0 else ownRangedPower * .65
            val deviceDamage = if (targetIndex == null || aimedArtillery) 0 else {
                val device = devices[targetIndex]
                val damage = minOf(device.integrity, stochasticRound(aimedPower / 140.0,
                    Random(battle.seed xor ((battle.step + 1) * 104729) xor front.section.ordinal xor 0x444556)))
                devices[targetIndex] = device.copy(integrity = device.integrity - damage, disabled = device.integrity <= damage)
                if (damage > 0) events += "${front.section.label}: Fokusbeschuss beschädigt ${device.type.label} um $damage Punkte."
                damage
            }
            val defensible = battle.tactic == Tactic.FORTIFY || battle.terrain[front.section] in listOf(BattleTerrain.BRIDGE, BattleTerrain.PASS, BattleTerrain.STREET)
            val hold = focus && decision in listOf(BattleDecision.HOLD, BattleDecision.HOLD_GATE, BattleDecision.HOLD_BREACH, BattleDecision.SECOND_LINE)
            val meleeCommand = when {
                focus && decision in listOf(BattleDecision.CAVALRY_CHARGE, BattleDecision.OPEN_GATE) -> 1.25
                focus && decision in listOf(BattleDecision.COUNTERATTACK, BattleDecision.ADVANCE) -> 1.18
                else -> 1.0
            }
            val ownMeleePower = units.indices.sumOf { index ->
                val c = units[index]
                active[index] * (c.type.attack + c.type.defense * (if (defensible || hold) .75 else .35)) *
                    ownQuality(state, battle, c, false) * if (c.type.ranged >= 8) .55 else 1.0
            } * meleeCommand
            val enemyMeleePower = enemyUnits.indices.sumOf { index ->
                val c = enemyUnits[index]
                enemyActive[index] * (c.type.attack + c.type.defense * .35) * enemyQuality(c, front) *
                    BattleEngine.terrainMultiplier(terrain, c.type, BattlePhase.MAIN) *
                    (if (c.type == UnitType.KNIGHT) battle.cavalryWeather else 1.0) *
                    (if (c.type.ranged >= 8) .55 else 1.0)
            }
            val ownDefense = if (count == 0) 1.0 else units.sumOf { it.soldiers * (.55 + it.type.defense / 15.0) * (.45 + it.equipment / 180.0) } / count *
                (if (state.resources.food == 0) .70 else 1.0) * (if (hold) 1.18 else 1.0)
            val enemyDefense = if (enemyCount == 0) 1.0 else enemyUnits.sumOf { it.soldiers * (.55 + it.type.defense / 15.0) * (.45 + it.equipment / 180.0) } / enemyCount
            val coverOwn = if (battle.tactic == Tactic.FORTIFY) SiegeEngine.exposure(segment) else 1.0
            val coverEnemy = if (battle.tactic != Tactic.FORTIFY && battle.enemyFortification > 0) SiegeEngine.exposure(segment) else 1.0
            val rawRangedOwn = if (count > 0) enemyRangedPower / 115.0 / ownDefense.coerceAtLeast(.2) else 0.0
            val rangedOwn = rawRangedOwn * coverOwn
            val rangedEnemy = if (enemyCount > 0) (ownRangedPower - aimedPower) / 115.0 / enemyDefense.coerceAtLeast(.2) * coverEnemy else 0.0
            val lineDefense = if (defensible) 1.45 else 1.0
            val meleeOwn = if (contact && count > 0) enemyMeleePower / 95.0 / ownDefense.coerceAtLeast(.2) / lineDefense else 0.0
            val meleeEnemy = if (contact && enemyCount > 0) ownMeleePower / 95.0 / enemyDefense.coerceAtLeast(.2) else 0.0
            val splashOwn = if (count > 0) (siege.splash[front.section] ?: 0.0) * if (battle.tactic == Tactic.FORTIFY) .5 else 1.0 else 0.0
            val rngOwn = Random(battle.seed xor ((battle.step + 1) * 104729) xor (front.section.ordinal * 8191) xor 0x4F574E)
            val rngEnemy = Random(battle.seed xor ((battle.step + 1) * 104729) xor (front.section.ordinal * 8191) xor 0x454E45)
            val ownBudget = minOf(count, stochasticRound(rangedOwn + meleeOwn + splashOwn, rngOwn))
            val enemyBudget = minOf(enemyCount, stochasticRound(rangedEnemy + meleeEnemy, rngEnemy))
            val ownAllocation = allocate(ownBudget, units.map { it.soldiers }, units.indices.map { index ->
                val c = units[index]
                val exposure = if (contact) active[index] + shooters[index] * .5 + c.soldiers * .05 else shooters[index] + c.soldiers * .2
                exposure / (.5 + c.type.defense / 12.0) / (.5 + c.equipment / 200.0)
            })
            val enemyAllocation = allocate(enemyBudget, enemyUnits.map { it.soldiers }, enemyUnits.indices.map { index ->
                val c = enemyUnits[index]
                val exposure = if (contact) enemyActive[index] + enemyShooters[index] * .5 + c.soldiers * .05 else c.soldiers.toDouble()
                exposure / (.5 + c.type.defense / 12.0)
            })
            indices.forEachIndexed { index, troopIndex ->
                val c = units[index]
                val lost = ownAllocation[index]
                losses += UnitAllocation(c.type, lost)
                c.commanderId?.let { id -> val key = id to c.type; commanded[key] = (commanded[key] ?: 0) + lost }
                val leader = c.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.leadership } ?: state.player.leadership
                val neighbours = battle.fronts.count { it.section != front.section && it.position <= 20 }
                val casualtyShock = (lost * 100.0 / c.soldiers.coerceAtLeast(1) / 4).toInt()
                val localBreach = segment.contactState == BattleContactState.BREACHED &&
                    battle.exchanges.lastOrNull()?.fronts?.firstOrNull { it.section == front.section }?.contactState != BattleContactState.BREACHED
                val cohesion = (c.cohesion - casualtyShock - neighbours * 2 - (if (localBreach) 12 else 0) -
                    (if (c.commanderWounded && !c.commanderRescued) 5 else 0) + if (hold) 2 + leader / 50 else 0).coerceIn(0, 100)
                val used = active[index] + shooters[index]
                val fatigue = (c.fatigue + if (used > 0) 4 + c.type.defense / 5 else -3).coerceIn(0, 100)
                val morale = (c.morale - casualtyShock - neighbours * 2 - (if (state.resources.food == 0) 3 else 0) -
                    (if (c.commanderWounded && !c.commanderRescued) 3 else 0) + if (enemyBudget > ownBudget * 2 && enemyBudget > 0) 1 else 0).coerceIn(0, 100)
                val routed = c.soldiers - lost > 0 && (morale < 12 || morale < 28 && cohesion < 28 || morale < 35 && cohesion < 15 && contact)
                if (routed && !c.routed) events += "${c.displayName ?: c.type.label} fliehen aus ${front.section.label}; Moral und Kohäsion brechen."
                changed[troopIndex] = c.copy(soldiers = c.soldiers - lost, morale = morale, cohesion = cohesion, fatigue = fatigue, routed = routed)
            }
            enemyIndices.forEachIndexed { index, enemyIndex -> enemies[enemyIndex] = enemyUnits[index].copy(soldiers = enemyUnits[index].soldiers - enemyAllocation[index]) }
            val stalled = front.enemyDistance == 0 && !contact && battle.tactic == Tactic.FORTIFY &&
                battle.siegeDevices.none { it.section == front.section && !it.disabled && it.crew > 0 }
            val shock = (enemyBudget * 100.0 / enemyCount.coerceAtLeast(1) / 3).toInt()
            val morale = (front.morale - shock - (if (stalled) 4 else if (enemyBudget > ownBudget) 2 else 0) -
                (if (focus && decision == BattleDecision.ARROW_VOLLEY && enemyBudget > 0) 3 else 0)).coerceIn(0, 100)
            val position = (front.position + if (!contact) 0 else if (count == 0) -15 else if (ownBudget > enemyBudget * 2) -6 else if (enemyBudget > ownBudget * 2) 3 else 0).coerceIn(0, 100)
            reports += FrontExchangeReport(front.section, segment.contactState, width, active.sum(), enemyActive.sum(),
                shooters.sum(), enemyShooters.sum(), sources(ownBudget, rangedOwn, meleeOwn, splashOwn),
                sources(enemyBudget, rangedEnemy, meleeEnemy, 0.0).copy(wallWeapons = battle.wallWeaponReports[front.section] ?: 0),
                rawRangedOwn - rangedOwn, siege.structural[front.section] ?: 0, siege.gateDamage[front.section] ?: 0, ammoCost, enemyAmmoCost, deviceDamage, ownCharges)
            front.copy(enemySoldiers = enemyCount - enemyBudget, morale = morale, position = position,
                cohesion = (front.cohesion - shock - if (position < 25) 3 else 0).coerceIn(0, 100),
                fatigue = (front.fatigue + if (contact || enemyShooters.sum() > 0) 5 else 2).coerceAtMost(100))
        }
        val report = BattleExchangeReport(battle.minute + 5, reports.sortedBy { it.section.ordinal }, events)
        return Result(battle.copy(contingents = changed, fronts = newFronts.sortedBy { it.section.ordinal }, enemyRoster = enemies,
            battleArrowsRemaining = arrows, enemyArrowsRemaining = enemyArrows,
            battleArtilleryRemaining = artillery, enemyArtilleryRemaining = enemyArtillery,
            siegeDevices = devices, devices = devices.filterNot { it.disabled }.map { it.type }.distinct(),
            exchanges = (battle.exchanges + report).takeLast(20), wallWeaponReports = emptyMap()), losses, commanded, report)
    }

    private fun ownQuality(state: GameState, battle: BattleSession, c: BattleContingent, ranged: Boolean): Double {
        val commander = c.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id } }
        val skill = if (ranged) commander?.bow ?: state.player.bow else commander?.sword ?: state.player.sword
        val command = 1 + (commander?.leadership ?: state.player.leadership) / 600.0 +
            (commander?.tactics ?: state.player.tactics) / 450.0 + skill / 700.0
        val terrain = battle.terrain[c.section] ?: BattleTerrain.PLAIN
        val stance = when (battle.tactic) {
            Tactic.RANGED -> if (ranged) 1.20 else .95
            Tactic.AGGRESSIVE -> if (ranged) .95 else 1.12
            Tactic.FLANK -> if (c.section != BattleSection.CENTER) 1.12 else .95
            else -> 1.0
        }
        return (.5 + c.morale / 200.0) * (.5 + c.equipment / 200.0) * (1 + c.experience / 200.0) * command *
            (.65 + c.cohesion / 230.0) * (1 - c.fatigue / 180.0) * stance *
            (if (c.commanderRescued) .92 else if (c.commanderWounded) .80 else 1.0) *
            BattleEngine.terrainMultiplier(terrain, c.type, if (ranged) BattlePhase.RANGED else BattlePhase.MAIN) *
            FrontierEngine.customUnitPowerFactor(state, c.type) *
            (if (c.commanderId == COMPANION_COMMANDER_ID) FrontierEngine.bondCombatFactor(state) else 1.0) *
            (state.war.equipment.firstOrNull { it.type == c.type }?.quality?.power ?: 1.0) *
            DoctrineEngine.equipmentPowerFactor(state) * (if (ranged) battle.rangedWeather * DoctrineEngine.rangedPowerFactor(state) else 1.0) *
            (if (c.type == UnitType.KNIGHT) battle.cavalryWeather else 1.0) * battle.seasonPenalty *
            (if (state.resources.food == 0) .70 else 1.0) *
            (if (battle.participation == BattleParticipation.PERSONAL && battle.personalSection == c.section && c.commanderId == null)
                if (battle.heroPerk) 1.22 else 1.10 else 1.0) *
            (if (state.doctrine == MilitaryDoctrine.FOREST_WARFARE && terrain == BattleTerrain.FOREST) 1.12 else 1.0) *
            (if (state.doctrine == MilitaryDoctrine.DISCIPLINED_LINE && !ranged && c.type != UnitType.KNIGHT) 1.05 else 1.0) *
            (if (state.war.eliteUnits.any { it.type == c.type && "Unbeugsam" in it.traits }) 1.05 else 1.0)
    }

    private fun enemyQuality(unit: EnemyBattleUnit, front: BattleFront): Double =
        (.5 + front.morale / 200.0) * (.5 + unit.equipment / 200.0) * (1 + unit.experience / 200.0) *
            (.65 + front.cohesion / 230.0) * (1 - front.fatigue / 180.0)
}
