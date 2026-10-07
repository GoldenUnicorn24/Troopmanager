package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class CombatThreeTacticsTest {
    private fun base(leadership: Int = 80, tactics: Int = 80): GameState =
        GameEngine.newGame("Wacht", 24, Species.HUMAN, null).copy(
            population = Population(human = 5000),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 600, morale = 65),
                ArmyUnitPool(UnitType.HUMAN_SWORD, 300, morale = 65), ArmyUnitPool(UnitType.KNIGHT, 100)),
            commanders = listOf(Commander(7, "Alaric", Culture.HUMAN, "human", leadership = leadership, tactics = tactics)),
            commanderAssignments = listOf(CommanderAssignment(7, listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 200)))),
            militaryStock = MilitaryStock(arrows = 8000),
            realm = Realm(buildings = mapOf(BuildingType.WALL to 4, BuildingType.TOWER to 4)),
            invasion = Invasion(EnemyType.ORC, 1, 1500, 1, devices = emptyList()))

    private fun start(state: GameState = base(), plan: BattlePlan = BattlePlan()) = BattleEngine.start(state,
        EnemyType.ORC, Tactic.FORTIFY, seed = 100, enemyStrength = 1500,
        enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 900), UnitAllocation(UnitType.GOLD_ARCHER, 600)),
        plan = plan, night = false).state

    private fun step(state: GameState) = BattleEngine.advance(state, state.battleSession!!.pendingEvent?.options?.let {
        if (BattleDecision.HOLD in it) BattleDecision.HOLD else it.first()
    }).state

    @Test fun frontPriorityTargetsRealFormationsAndIsSerialized() {
        val plan = BattlePlan(frontPriorities = mapOf(BattleSection.LEFT to TargetPriority.RANGED,
            BattleSection.RIGHT to TargetPriority.LIGHT_INFANTRY))
        val state = step(start(base().copy(commanders = emptyList(), commanderAssignments = emptyList()), plan))
        val battle = state.battleSession!!
        assertEquals(UnitType.GOLD_ARCHER, battle.lastReport(BattleSection.LEFT)!!.targetType)
        assertEquals(UnitType.HUMAN_SWORD, battle.lastReport(BattleSection.RIGHT)!!.targetType)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun freelyChosenFireReleaseDistanceAndHoldOrderApplyToActualMagazines() {
        val waiting = step(start(plan = BattlePlan(fireReleaseDistance = 60))).battleSession!!
        assertEquals(waiting.battleArrowsLoaded, waiting.battleArrowsRemaining)
        val held = step(start(plan = BattlePlan(holdFire = true))).battleSession!!
        assertEquals(held.battleArrowsLoaded, held.battleArrowsRemaining)
        assertTrue(held.segments.all { it.rangedOrder == RangedOrder.HOLD })
    }

    @Test fun commandersExplainMoraleRegenerationAndHaveRealDifferentReactionTimes() {
        val strong = step(start(base(90, 90)))
        val weak = step(start(base(10, 10)))
        val strongTroop = strong.battleSession!!.contingents.first { it.commanderId == 7L }
        val weakTroop = weak.battleSession!!.contingents.first { it.commanderId == 7L }
        assertTrue(strongTroop.morale > weakTroop.morale)
        assertTrue(strong.battleSession.log.any { "Alaric" in it.text && "Moral" in it.text })
        assertTrue(strong.battleSession.log.any { "Befehlsreichweite" in it.text && "Initiative" in it.text })
        assertTrue(BattleCommandEngine.influence(strong, strong.battleSession, BattleSection.LEFT).reactionSteps <
            BattleCommandEngine.influence(weak, weak.battleSession, BattleSection.LEFT).reactionSteps)
    }

    @Test fun liveFormationChangeCostsPointsAndWaitsForCommanderReaction() {
        val running = step(start())
        val plan = running.battleSession!!.plan.copy(formations = mapOf(BattleSection.LEFT to BattleFormation.LOOSE))
        val changed = BattleEngine.configurePlan(running, plan).state
        assertEquals(running.battleSession.commandPoints - 2, changed.battleSession!!.commandPoints)
        assertTrue(changed.battleSession.contingents.filter { it.section == BattleSection.LEFT }.all { it.formation == BattleFormation.LINE })
        assertTrue(changed.battleSession.contingents.filter { it.section == BattleSection.LEFT }.all { it.pendingFormation == BattleFormation.LOOSE })
        val transit = step(SaveCodec.decode(SaveCodec.encode(changed)))
        val arrived = step(transit)
        assertTrue(arrived.battleSession!!.contingents.filter { it.section == BattleSection.LEFT }.all { it.formation == BattleFormation.LOOSE })
        assertTrue(arrived.battleSession.log.any { "Alaric" in it.text && "Offene Ordnung" in it.text })
    }

    @Test fun openOrderReducesIncomingArrowCasualties() {
        val simple = base().copy(commanders = emptyList(), commanderAssignments = emptyList())
        val normal = step(start(simple)).battleSession!!
        val loose = step(start(simple, BattlePlan(formations = BattleStateEngine.sections.associateWith { BattleFormation.LOOSE }))).battleSession!!
        assertTrue(loose.ownStart - loose.ownRemaining < normal.ownStart - normal.ownRemaining)
    }

    @Test fun reserveCoordinationIncludesTerrainAndCommandReachAndNeverTeleports() {
        val state = start(base(10, 10))
        val battle = state.battleSession!!
        val mud = battle.copy(terrain = battle.terrain + (BattleSection.LEFT to BattleTerrain.MUD))
        assertTrue(BattleCommandEngine.reserveDelay(state, mud, BattleSection.LEFT) > 1)
        assertEquals(1, BattleCommandEngine.reserveDelay(start(base()), start(base()).battleSession!!, BattleSection.LEFT))
    }

    @Test fun projectionIncludesAllLocalFrontFactsFromAuthoritativeModels() {
        val state = step(start())
        val b = state.battleSession!!
        val p = BattleStateEngine.frontStatus(b, BattleSection.LEFT)
        assertEquals(b.fronts.first().enemyDistance, p.distance)
        assertEquals(b.segment(BattleSection.LEFT)!!.contactState, p.contact)
        assertEquals(b.fighting(BattleSection.LEFT), p.ownSoldiers)
        assertEquals(b.battleArrowsRemaining, p.arrows)
        assertEquals(BattleTerrain.WALL, p.terrain)
        assertEquals(100, p.wallIntegrity)
        assertTrue(p.cover > 0 && p.cohesion > 0 && p.visibility > 0)
    }

    @Test fun sourceArmorAndMedicalPreparationChangeWoundedShareWithoutChangingLossTotals() {
        val state = start()
        val battle = state.battleSession!!
        fun injured(cause: BattleDamageCause, prepared: GameState = state): Int = WarEngine.woundedFromCauses(prepared,
            battle.copy(causes = listOf(BattleCauseBreakdown(BattleSection.LEFT, UnitType.HUMAN_ARCHER, cause, own = 100))),
            UnitType.HUMAN_ARCHER, 100)
        assertTrue(injured(BattleDamageCause.ARROWS) > injured(BattleDamageCause.ARTILLERY))
        assertTrue(injured(BattleDamageCause.MELEE) > injured(BattleDamageCause.PURSUIT))
        val prepared = state.copy(realm = state.realm.copy(buildings = state.realm.buildings + (BuildingType.HOSPITAL to 4)),
            militaryStock = state.militaryStock.copy(medicine = 100))
        assertTrue(injured(BattleDamageCause.MELEE, prepared) > injured(BattleDamageCause.MELEE))
    }

    @Test fun planValidationRejectsInvalidDistancesThresholdsAndReserveFronts() {
        for (plan in listOf(BattlePlan(preferredEngagementDistance = 351), BattlePlan(fireReleaseDistance = -1),
            BattlePlan(reserveThreshold = 0), BattlePlan(gateReservePercent = 101), BattlePlan(fallbackThreshold = 101),
            BattlePlan(formations = mapOf(BattleSection.RESERVE to BattleFormation.DENSE)),
            BattlePlan(frontPriorities = mapOf(BattleSection.RESERVE to TargetPriority.ELITE)))) {
            assertFalse(BattleEngine.validPlan(plan))
            val state = base()
            assertEquals(state, BattleEngine.configurePlan(state, plan).state)
        }
    }

    @Test fun everyExchangeBalancesCausesDeadAndWoundedAgainstRealLosses() {
        val state = step(start())
        val battle = state.battleSession!!
        val phase = battle.exchanges.single()
        assertTrue(phase.ownLosses > 0 && phase.enemyLosses > 0)
        assertEquals(phase.ownLosses, phase.causes.sumOf { it.own })
        assertEquals(phase.enemyLosses, phase.causes.sumOf { it.enemy })
        assertEquals(phase.ownLosses, phase.ownCasualties.total)
        assertEquals(phase.enemyLosses, phase.enemyCasualties.total)
        assertEquals(battle.ownStart - battle.ownRemaining, battle.casualties.total)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun editableGateShareAndReservePressureThresholdChangeRealDispatches() {
        val gate = start(plan = BattlePlan(reservePolicy = ReservePolicy.THREATENED_GATE, gateReservePercent = 25))
        val threatened = gate.copy(battleSession = gate.battleSession!!.copy(segments = gate.battleSession.segments.map {
            if (it.section == BattleSection.CENTER) it.copy(gateIntegrity = 45) else it }))
        assertEquals(25, step(threatened).battleSession!!.reserveReinforcement!!.soldiers)
        fun pressure(threshold: Int): BattleSession {
            val state = start(plan = BattlePlan(reservePolicy = ReservePolicy.WEAKEST_FRONT, reserveThreshold = threshold))
            return step(state.copy(battleSession = state.battleSession!!.copy(fronts = state.battleSession.fronts.map {
                it.copy(enemyDistance = 80)
            }))).battleSession!!
        }
        assertNotNull(pressure(100).reserveReinforcement)
        assertNull(pressure(300).reserveReinforcement)
    }

    @Test fun preferredEngagementDistanceChangesActualFieldMovement() {
        val state = base()
        fun approach(distance: Int): BattleSession = step(BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD,
            seed = 100, enemyStrength = 1500, plan = BattlePlan(preferredEngagementDistance = distance),
            terrain = BattleStateEngine.sections.associateWith { BattleTerrain.PLAIN }, night = false).state).battleSession!!
        assertTrue(approach(90).fronts.first().enemyDistance < approach(0).fronts.first().enemyDistance)
    }

    @Test fun commanderRiskAndTrainingChangeDeterministicTargetingMistakes() {
        val cautious = start(base(90, 90), BattlePlan(commanderRisk = CommanderRisk.CAUTIOUS,
            rangedPriority = TargetPriority.RANGED))
        val bold = start(base(10, 10), BattlePlan(commanderRisk = CommanderRisk.BOLD,
            rangedPriority = TargetPriority.RANGED))
        fun mistakes(state: GameState): Int = (0..31).count { step -> BattleCommandEngine.makesMistake(state,
            state.battleSession!!.copy(step = step), BattleSection.LEFT) }
        assertTrue(mistakes(bold) > mistakes(cautious))
        val faulty = (0..31).first { BattleCommandEngine.makesMistake(bold, bold.battleSession!!.copy(step = it), BattleSection.LEFT) }
        assertEquals(TargetPriority.NEAREST, BattleCommandEngine.priority(bold, bold.battleSession!!.copy(step = faulty), BattleSection.LEFT))
    }

    @Test fun damagedWallReadinessUsesExactlyTheCapacityOfTheFirstExchange() {
        val healthy = base()
        val initial = start(healthy.copy(realm = healthy.realm.copy(wallIntegrity = 25)))
        val ready = WarEngine.defenseReadiness(initial)
        val report = step(initial).battleSession!!.exchanges.single()
        assertEquals(ready.wallArchers, report.fronts.sumOf { it.ownShooters })
        assertTrue(ready.wallArchers < WarEngine.defenseReadiness(start(healthy)).wallArchers)
    }

    @Test fun attackingArchersDoNotInheritTheDefendersWallFiringPositions() {
        val state = BattleEngine.start(base(), EnemyType.ORC, Tactic.FORTIFY, seed = 100,
            enemyStrength = 1500, enemyUnits = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 1500)), night = false).state
        assertTrue(step(state).battleSession!!.exchanges.single().fronts.all { it.enemyShooters == 180 })
    }
}
