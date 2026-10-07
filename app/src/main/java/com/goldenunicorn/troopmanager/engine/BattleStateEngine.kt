package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/** Idempotent additive migration. No campaign supplies are created or debited here. */
object BattleStateEngine {
    val sections = listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT)

    fun initialize(state: GameState, battle: BattleSession): BattleSession {
        require(battle.combatVersion in 1..3) { "Unbekannte Kampfversion." }
        if (battle.combatVersion == 3 && battle.segments.size == 3 && battle.battleArrowsRemaining >= 0 &&
            battle.enemyArrowsRemaining >= 0 && battle.battleArtilleryRemaining >= 0 && battle.enemyArtilleryRemaining >= 0 && battle.enemyRoster.isNotEmpty() && battle.siegeDevices.all { it.ammunition >= 0 }) return battle
        val allocations = battle.enemyUnits.ifEmpty { generateRoster(battle.enemy, battle.enemyStart, battle.seed) }
        val roster = battle.enemyRoster.ifEmpty {
            var available = allocations.map { it.amount }.toMutableList()
            battle.fronts.flatMap { front ->
                var slots = front.enemyStart
                val units = allocations.mapIndexedNotNull { index, allocation ->
                    val number = if (index == allocations.lastIndex) minOf(slots, available[index])
                    else minOf(slots, available[index], (front.enemyStart.toLong() * allocation.amount / battle.enemyStart.coerceAtLeast(1)).toInt())
                    available[index] -= number
                    slots -= number
                    if (number == 0) null else EnemyBattleUnit(allocation.type, front.section, number, number,
                        battle.enemyExperience.coerceAtMost(100))
                }.toMutableList()
                // Integer remainders are taken from the remaining real roster, never generated twice.
                allocations.indices.forEach { index ->
                    val number = minOf(slots, available[index])
                    if (number > 0) {
                        available[index] -= number
                        slots -= number
                        val existing = units.indexOfFirst { it.type == allocations[index].type }
                        if (existing < 0) units += EnemyBattleUnit(allocations[index].type, front.section, number, number, battle.enemyExperience.coerceAtMost(100))
                        else units[existing] = units[existing].copy(soldiers = units[existing].soldiers + number, startSoldiers = units[existing].startSoldiers + number)
                    }
                }
                var losses = front.enemyStart - front.enemySoldiers
                units.map { unit ->
                    val removed = minOf(losses, unit.soldiers)
                    losses -= removed
                    unit.copy(soldiers = unit.soldiers - removed)
                }
            }
        }
        val fortified = battle.tactic == Tactic.FORTIFY || battle.enemyFortification > 0
        val cover = if (fortified) (0.60 + WarEngine.effectiveLevel(state, BuildingType.WALL).coerceAtMost(4) * .045 +
            WarEngine.effectiveLevel(state, BuildingType.TOWER).coerceAtMost(4) * .025).coerceIn(.60, .85) else 0.0
        val segments = battle.segments.ifEmpty { sections.map { section ->
            val integrity = if (battle.tactic == Tactic.FORTIFY) battle.wallIntegrity else battle.enemyFortification
            FortificationSegmentState(section, integrity = if (fortified) integrity else 0,
                gateIntegrity = if (fortified) integrity else 0, cover = cover,
                breachWidth = if (fortified && integrity == 0) 80 else 0,
                contactState = if (fortified && integrity == 0) BattleContactState.BREACHED else BattleContactState.APPROACH,
                rangedOrder = if (battle.plan.holdFire) RangedOrder.HOLD else RangedOrder.NORMAL)
        } }
        val siegeDevices = if (battle.combatVersion >= 2) battle.siegeDevices else battle.siegeDevices.ifEmpty {
            createDevices(battle.devices, battle.fronts)
        }
        val shooters = battle.contingents.filter { it.type.ranged >= 8 && it.type != UnitType.DRAGON_ARTILLERY }.sumOf { it.startSoldiers.toLong() }
        val legacyLoad = (shooters * 4 * battle.rangedSupplyFactor).toLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val ownArrows = if (battle.battleArrowsRemaining >= 0) battle.battleArrowsRemaining
            else (legacyLoad.toLong() - shooters * battle.step).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val enemyShooters = roster.filter { it.type.ranged >= 8 && it.type != UnitType.DRAGON_ARTILLERY }.sumOf { it.soldiers.toLong() }
        val enemyArrows = if (battle.enemyArrowsRemaining >= 0) battle.enemyArrowsRemaining
            else (enemyShooters * (8 - battle.step).coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val artilleryLoad = ((battle.contingents.filter { it.type == UnitType.DRAGON_ARTILLERY }.sumOf { it.startSoldiers.toLong() } + 9) / 10 * 6).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val ownArtillery = if (battle.battleArtilleryRemaining >= 0) battle.battleArtilleryRemaining
            else (artilleryLoad.toLong() * (6 - battle.step).coerceAtLeast(0) / 6).toInt()
        val enemyArtillery = if (battle.enemyArtilleryRemaining >= 0) battle.enemyArtilleryRemaining else
            ((roster.filter { it.type == UnitType.DRAGON_ARTILLERY }.sumOf { it.soldiers.toLong() } + 9) / 10 * (8 - battle.step).coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val legacyCauses = if (battle.combatVersion >= 3 || battle.causes.isNotEmpty()) battle.causes else BattleReportEngine.addCauses(emptyList(),
            battle.contingents.map { BattleCauseBreakdown(it.section, it.type, BattleDamageCause.LEGACY, own = it.startSoldiers - it.soldiers) } +
                roster.map { BattleCauseBreakdown(it.section, it.type, BattleDamageCause.LEGACY, enemy = it.startSoldiers - it.soldiers) })
        val initialized = battle.copy(combatVersion = 3, enemyUnits = allocations, enemyRoster = roster,
            causes = legacyCauses,
            contingents = if (battle.combatVersion < 3 && battle.step == 0) battle.contingents.map {
                it.copy(formation = battle.plan.formations[it.section] ?: it.formation)
            } else battle.contingents,
            segments = segments, battleArrowsRemaining = ownArrows,
            battleArrowsLoaded = if (battle.battleArrowsRemaining < 0) legacyLoad else battle.battleArrowsLoaded,
            enemyArrowsRemaining = enemyArrows, battleArtilleryRemaining = ownArtillery,
            battleArtilleryLoaded = if (battle.battleArtilleryRemaining < 0) artilleryLoad else battle.battleArtilleryLoaded,
            enemyArtilleryRemaining = enemyArtillery,
            siegeDevices = siegeDevices.map { if (it.ammunition >= 0) it else it.copy(ammunition = if (it.type == SiegeDevice.CATAPULT) (8 - battle.step).coerceAtLeast(0) else 0) })
        val pending = initialized.pendingEvent ?: return initialized
        if (battle.combatVersion >= 2) return initialized
        val safe = if (initialized.status == BattleStatus.PURSUIT) BattleDecision.HOLD_FORMATION else BattleDecision.HOLD
        val legal = pending.options.filter { BattleEngine.canOrder(initialized, it, pending.section) }
        return initialized.copy(pendingEvent = pending.copy(options = (legal + safe).distinct()))
    }

    fun generateRoster(enemy: EnemyType, strength: Int, seed: Int): List<UnitAllocation> {
        val rng = Random(seed xor 0x524F5354)
        val rangedShare = when (enemy) { EnemyType.ORC -> rng.nextInt(8, 16); EnemyType.URUK -> rng.nextInt(22, 31); EnemyType.TAO_TEI -> 0 }
        val ranged = (strength.toLong() * rangedShare / 100).toInt()
        val elite = if (enemy == EnemyType.URUK) strength / 4 else if (enemy == EnemyType.TAO_TEI) strength / 3 else 0
        val infantry = strength - ranged - elite
        return listOfNotNull(
            if (infantry > 0) UnitAllocation(if (enemy == EnemyType.TAO_TEI) UnitType.WOOD_BLADE else UnitType.HUMAN_SWORD, infantry) else null,
            if (ranged > 0) UnitAllocation(if (enemy == EnemyType.URUK) UnitType.GOLD_ARCHER else UnitType.HUMAN_ARCHER, ranged) else null,
            if (elite > 0) UnitAllocation(if (enemy == EnemyType.TAO_TEI) UnitType.TIGER_CORPS else UnitType.BEAR_CORPS, elite) else null,
        )
    }

    fun createDevices(types: List<SiegeDevice>, fronts: List<BattleFront>): List<SiegeDeviceState> = types.flatMapIndexed { index, type ->
        val targets = when (type) {
            SiegeDevice.LADDERS, SiegeDevice.CLIMBERS -> sections
            SiegeDevice.TOWER -> listOf(BattleSection.RIGHT)
            SiegeDevice.CATAPULT -> listOf(BattleSection.LEFT)
            else -> listOf(BattleSection.CENTER)
        }
        targets.map { section -> SiegeDeviceState("siege_${index}_${section.name}", type, section,
            distance = when (type) { SiegeDevice.CATAPULT -> 260; SiegeDevice.TUNNEL -> 0; else -> 180 },
            crew = minOf(if (type == SiegeDevice.TOWER) 40 else 20, fronts.firstOrNull { it.section == section }?.enemySoldiers ?: 0),
            detected = type != SiegeDevice.TUNNEL)
        }
    }

    fun migrate(state: GameState): GameState = state.battleSession?.let { state.copy(battleSession = initialize(state, it)) } ?: state

    fun canFight(unit: BattleContingent): Boolean = unit.soldiers > 0 && !unit.routed && unit.morale >= 15 && unit.cohesion >= 15

    fun frontStatus(battle: BattleSession, section: BattleSection): BattleFrontStatus {
        require(section in sections)
        val front = battle.fronts.first { it.section == section }
        val segment = battle.segment(section)!!
        val troops = battle.contingents.filter { it.section == section && it.soldiers > 0 }
        val count = troops.sumOf { it.soldiers.toLong() }
        fun average(value: (BattleContingent) -> Int) = if (count == 0L) 0 else
            (troops.sumOf { it.soldiers.toLong() * value(it) } / count).toInt()
        return BattleFrontStatus(section, front.enemyDistance, segment.contactState, SiegeEngine.frontage(battle, section),
            battle.fighting(section), front.enemySoldiers, battle.terrain[section] ?: BattleTerrain.PLAIN,
            1.0 - SiegeEngine.exposure(segment), average { it.morale }, front.morale,
            average { it.cohesion }, average { it.fatigue }, battle.visibility * if (battle.night) .65 else 1.0,
            battle.battleArrowsRemaining, troops.maxByOrNull { it.soldiers }?.formation ?: BattleFormation.LINE,
            battle.reserveReinforcement?.takeIf { it.section == section }, segment.integrity, segment.gateIntegrity,
            segment.breachWidth, segment.siegeStage)
    }

    fun validateCauses(causes: List<BattleCauseBreakdown>) {
        require(causes.size <= BattleSection.entries.size * UnitType.entries.size * BattleDamageCause.entries.size &&
            causes.all { it.own >= 0 && it.enemy >= 0 && it.own.toLong() + it.enemy > 0 } &&
            causes.map { Triple(it.section, it.type, it.cause) }.distinct().size == causes.size) { "Ungültige Verlustursachen." }
    }

    fun validate(battle: BattleSession) {
        require(BattleEngine.validPlan(battle.plan) && battle.visibility.isFinite() && battle.visibility in .25..1.0) { "Ungültiger Schlachtplan oder Sicht." }
        battle.reserveReinforcement?.let { require(it.section in sections && it.soldiers > 0 && it.readyStep in battle.step..(battle.step + 4) &&
            it.soldiers <= battle.fighting(BattleSection.RESERVE) && (it.order == null || it.order in listOf(BattleDecision.SEND_RESERVE,
                BattleDecision.RELOCATE_RESERVE, BattleDecision.STRENGTHEN_SECTION, BattleDecision.ROTATE_RESERVE,
                BattleDecision.RESCUE_COMMANDER, BattleDecision.CAVALRY_CHARGE, BattleDecision.OPEN_GATE))) { "Ungültige Reservebewegung." } }
        require(battle.combatVersion in 1..3) { "Unbekannte Kampfversion." }
        if (battle.combatVersion < 2) return
        require(battle.segments.map { it.section }.toSet() == sections.toSet() && battle.segments.size == 3 &&
            battle.segments.all { it.integrity in 0..100 && it.gateIntegrity in 0..100 && it.cover.isFinite() && it.cover in 0.0..0.85 &&
                it.assaultProgress in 0..100 && it.assaultWidth in 0..180 && it.breachWidth in 0..180 && it.fire in 0..100 }) { "Ungültige Mauersegmente." }
        require(battle.siegeDevices.map { it.id }.distinct().size == battle.siegeDevices.size && battle.siegeDevices.size <= 24 &&
            battle.siegeDevices.all { it.section in sections && it.integrity in 0..100 && it.distance in 0..400 && it.progress in 0..100 && it.crew in 0..1000 && it.ammunition in 0..100 && (!it.disabled || it.integrity >= 0) }) { "Ungültige Belagerungsgeräte." }
        require(battle.battleArtilleryRemaining in 0..battle.battleArtilleryLoaded && battle.enemyArtilleryRemaining >= 0)
        require(battle.battleArrowsRemaining in 0..battle.battleArrowsLoaded && battle.enemyArrowsRemaining >= 0 && battle.personalSection in sections) { "Ungültige Schlachtmunition." }
        require(battle.contingents.all { it.cohesion in 0..100 && it.fatigue in 0..100 &&
            (it.pendingFormation == null) == (it.formationReadyStep == null) &&
            (it.formationReadyStep == null || it.formationReadyStep in 0..(battle.step + 2)) } &&
            battle.fronts.all { it.cohesion in 0..100 && it.fatigue in 0..100 && it.enemyDistance in 0..400 }) { "Ungültige taktische Zustände." }
        require(battle.enemyRoster.all { it.soldiers in 0..it.startSoldiers && it.section in sections && it.experience in 0..100 && it.equipment in 0..100 } &&
            battle.enemyRoster.map { it.type to it.section }.distinct().size == battle.enemyRoster.size) { "Ungültige Gegnerformationen." }
        battle.fronts.forEach { front -> require(battle.enemyRoster.filter { it.section == front.section }.sumOf { it.soldiers.toLong() } == front.enemySoldiers.toLong()) { "Gegnerformationen stimmen nicht mit der Front überein." } }
        require(battle.exchanges.size <= 20 && battle.exchanges.all { report -> report.minute in 0..95 &&
            report.fronts.all { it.preventedLosses.isFinite() && it.preventedLosses >= 0 && it.frontage >= 0 && it.ownActive >= 0 && it.enemyActive >= 0 &&
                (it.contactState.allowsMelee || it.ownDamage.melee.toLong() + it.enemyDamage.melee == 0L) &&
                it.suppression in 0..30 && it.volleys.isFinite() && it.volleys in 0.0..4.0 &&
                it.arrowsUsed >= 0 && it.enemyArrowsUsed >= 0 && it.deviceDamage >= 0 && it.artilleryChargesUsed >= 0 && listOf(it.ownDamage.ranged, it.ownDamage.melee, it.ownDamage.splash, it.enemyDamage.ranged,
                    it.enemyDamage.melee, it.enemyDamage.splash, it.enemyDamage.wallWeapons,
                    it.ownDamage.artillery, it.enemyDamage.artillery, it.ownDamage.pursuit, it.enemyDamage.pursuit,
                    it.ownDamage.collapse, it.enemyDamage.collapse).all { value -> value >= 0 } } }) { "Ungültiger Austauschbericht." }
        validateCauses(battle.causes)
        battle.exchanges.forEach { report ->
            validateCauses(report.causes)
            require(listOf(report.ownCasualties, report.enemyCasualties).all {
                it.dead >= 0 && it.wounded >= 0 && it.missing >= 0 && it.captured >= 0
            }) { "Ungültige Verwundeten-/Gefallenenbilanz einer Phase." }
            if (report.causes.isNotEmpty()) require(report.causes.sumOf { it.own.toLong() } == report.ownLosses.toLong() &&
                report.causes.sumOf { it.enemy.toLong() } == report.enemyLosses.toLong() &&
                report.ownCasualties.total == report.ownLosses && report.enemyCasualties.total == report.enemyLosses) { "Verlustbilanz einer Schlachtphase ist ungültig." }
        }
        if (battle.combatVersion >= 3) require(battle.causes.sumOf { it.own.toLong() } == battle.ownStart.toLong() - battle.ownRemaining &&
            battle.causes.sumOf { it.enemy.toLong() } == battle.enemyStart.toLong() - battle.enemyRemaining) { "Verlustursachen stimmen nicht mit der Schlachtbilanz überein." }
    }
}
