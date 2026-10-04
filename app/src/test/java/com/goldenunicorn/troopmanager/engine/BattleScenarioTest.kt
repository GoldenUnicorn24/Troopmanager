package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import com.goldenunicorn.troopmanager.data.SaveCodec
import kotlinx.serialization.json.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/** Fixed-seed acceptance scenarios, expressed through the public battle API. */
class BattleScenarioTest {
    private fun defenders(arrows: Int = 2400) = GameEngine.newGame("Mauerwacht", 23, Species.HUMAN, null).copy(
        armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 300, experience = 30, morale = 80)),
        militaryStock = MilitaryStock(arrows = arrows),
        invasion = Invasion(EnemyType.ORC, 1, 300, 1, devices = emptyList()),
    )

    @Test fun pureMeleeCannotHurtIntactWallBeforeContact() {
        var state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY,
            seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state
        repeat(2) { state = BattleEngine.advance(state).state }
        val battle = state.battleSession!!
        assertEquals("An intact wall prevents all personnel damage from distant melee troops", 300, battle.ownRemaining)
        assertTrue("Defending archers can shoot approaching infantry", battle.enemyRemaining < 300)
    }

    @Test fun emptyAmmunitionCannotProvideFullRangedPower() {
        fun first(arrows: Int) = BattleEngine.advance(BattleEngine.start(defenders(arrows), EnemyType.ORC,
            Tactic.FORTIFY, seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state).state.battleSession!!
        assertTrue(first(2400).enemyRemaining < first(0).enemyRemaining)
    }

    private fun exchange(state: GameState): GameState = BattleEngine.advance(state,
        state.battleSession!!.pendingEvent?.options?.first()).state

    private fun siege(type: SiegeDevice, troops: Int = 300): GameState {
        val state = defenders().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, troops)),
            invasion = Invasion(EnemyType.URUK, 1, 600, 1, devices = listOf(type)))
        return BattleEngine.start(state, EnemyType.URUK, Tactic.FORTIFY, seed = 92, enemyStrength = 600,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 600))).state
    }

    @Test fun twiceAsManyMeleeAttackersStillCannotAttackThroughWalls() {
        var state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 91, enemyStrength = 600,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 600))).state
        repeat(4) { state = exchange(state) }
        assertEquals(300, state.battleSession!!.ownRemaining)
        assertTrue(state.battleSession!!.exchanges.flatMap { it.fronts }.all { it.ownDamage.melee == 0 })
    }

    @Test fun wallPreventsSixtyToEightyFivePercentOfOrdinaryArrowExposure() {
        val state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 300))).state
        val battle = exchange(state).battleSession!!
        assertTrue(battle.ownRemaining < 300)
        battle.segments.forEach { assertTrue(SiegeEngine.exposure(it) in .15.. .40) }
        assertTrue(battle.exchanges.last().fronts.sumOf { it.preventedLosses } > 0)
        assertTrue(battle.exchanges.last().fronts.all { it.ownDamage.melee == 0 })
    }

    @Test fun noDevicesMeansNoPathToTheCourtyardEvenAfterAnHour() {
        var state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state
        repeat(18) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = exchange(state) }
        assertEquals(300, state.battleSession!!.ownRemaining)
        assertTrue(state.battleSession!!.segments.none { it.contactState.allowsMelee })
    }

    @Test fun ramDamagesOnlyTheGateBeforeOpeningLocalMeleeContact() {
        var state = siege(SiegeDevice.RAM)
        repeat(5) { state = exchange(state) }
        val battle = state.battleSession!!
        assertTrue(battle.segment(BattleSection.CENTER)!!.gateIntegrity in 1..99)
        assertEquals(100, battle.segment(BattleSection.LEFT)!!.integrity)
        assertEquals(100, battle.segment(BattleSection.RIGHT)!!.integrity)
        assertTrue(battle.exchanges.all { report -> report.fronts.all { it.ownDamage.melee == 0 } })
        repeat(10) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = exchange(state) }
        assertEquals(0, state.battleSession!!.segment(BattleSection.CENTER)!!.gateIntegrity)
        assertTrue(state.battleSession!!.segment(BattleSection.CENTER)!!.contactState.allowsMelee)
        assertFalse(state.battleSession!!.segment(BattleSection.LEFT)!!.contactState.allowsMelee)
    }

    @Test fun dockingTowerOpensOnlyTheRightFront() {
        var state = siege(SiegeDevice.TOWER)
        repeat(11) { state = exchange(state) }
        val battle = state.battleSession!!
        assertEquals(BattleContactState.WALL_ASSAULT, battle.segment(BattleSection.RIGHT)!!.contactState)
        assertEquals(45, SiegeEngine.frontage(battle, BattleSection.RIGHT))
        assertFalse(battle.segment(BattleSection.LEFT)!!.contactState.allowsMelee)
        assertFalse(battle.segment(BattleSection.CENTER)!!.contactState.allowsMelee)
    }

    @Test fun catapultHasStructuralAndLimitedSplashDamageWithoutGlobalMelee() {
        var state = siege(SiegeDevice.CATAPULT)
        repeat(4) { state = exchange(state) }
        val battle = state.battleSession!!
        assertTrue(battle.segment(BattleSection.LEFT)!!.integrity < 100)
        assertEquals(100, battle.segment(BattleSection.CENTER)!!.integrity)
        assertTrue(battle.ownStart - battle.ownRemaining in 0..8)
        assertTrue(battle.exchanges.flatMap { it.fronts }.all { it.ownDamage.melee == 0 })
    }

    @Test fun openFieldHasHigherLossesThanAnIntactFortress() {
        fun run(tactic: Tactic): BattleSession {
            var state = BattleEngine.start(defenders().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 300))),
                EnemyType.ORC, tactic, seed = 90, enemyStrength = 300,
                terrain = BattleStateEngine.sections.associateWith { BattleTerrain.PLAIN },
                enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state
            repeat(12) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = exchange(state) }
            return state.battleSession!!
        }
        assertTrue(run(Tactic.HOLD).ownRemaining < run(Tactic.FORTIFY).ownRemaining - 10)
    }

    @Test fun narrowFrontLimitsActiveFightersRatherThanApplyingAMagicWallMultiplier() {
        var state = BattleEngine.start(defenders().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 300))),
            EnemyType.ORC, Tactic.HOLD, seed = 90, enemyStrength = 600,
            terrain = BattleStateEngine.sections.associateWith { BattleTerrain.BRIDGE },
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 600))).state
        repeat(5) { state = exchange(state) }
        val reports = state.battleSession!!.exchanges.last().fronts
        assertTrue(reports.all { it.frontage == 24 && it.ownActive <= 24 && it.enemyActive <= 24 })
    }

    @Test fun splittingAFormationDoesNotIncreaseItsTotalCasualties() {
        val base = defenders().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 100)))
        fun run(deployments: List<BattleDeployment>) = exchange(BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY,
            deployments, seed = 96, enemyStrength = 600, enemyUnits = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 600))).state).battleSession!!
        val whole = run(listOf(BattleDeployment(null, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 100)))))
        val split = run(List(100) { BattleDeployment(null, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 1))) })
        assertEquals(whole.ownRemaining, split.ownRemaining)
        assertEquals(whole.enemyRemaining, split.enemyRemaining)
    }

    @Test fun smallExpectedLossesUsuallyRoundToZeroAndAllocationConservesTheBudget() {
        val rounded = (0..99).map { BattleResolutionEngine.stochasticRound(.05, Random(it)) }
        assertTrue(rounded.count { it == 0 } >= 85)
        assertTrue(rounded.all { it in 0..1 })
        val allocated = BattleResolutionEngine.allocate(17, listOf(1, 3, 20), listOf(100.0, 1.0, 1.0))
        assertEquals(17, allocated.sum())
        assertEquals(1, allocated[0])
        assertTrue(allocated[1] <= 3 && allocated[2] <= 20)
    }

    @Test fun holdFireSavesArrowsAndDoesNotCreateExtraCover() {
        val state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state
        val held = BattleEngine.order(state, BattleDecision.HOLD_FIRE, BattleSection.CENTER).state.battleSession!!
        assertEquals(0, held.lastReport(BattleSection.CENTER)!!.arrowsUsed)
        assertEquals(0, held.lastReport(BattleSection.CENTER)!!.enemyDamage.ranged)
        assertEquals(state.battleSession!!.segment(BattleSection.CENTER)!!.cover, held.segment(BattleSection.CENTER)!!.cover, 0.0)
    }

    @Test fun volleyUsesMoreAmmunitionAndHasABoundedBurst() {
        val state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 600,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 600))).state
        val normal = exchange(state).battleSession!!
        val volley = BattleEngine.order(state, BattleDecision.ARROW_VOLLEY, BattleSection.CENTER).state.battleSession!!
        assertTrue(volley.battleArrowsRemaining < normal.battleArrowsRemaining)
        assertTrue(volley.lastReport(BattleSection.CENTER)!!.enemyDamage.ranged > normal.lastReport(BattleSection.CENTER)!!.enemyDamage.ranged)
        assertFalse(BattleEngine.canOrder(volley.copy(battleArrowsRemaining = 0), BattleDecision.ARROW_VOLLEY, BattleSection.CENTER))
    }

    @Test fun repeatedPreparationNeverDebitsAmmunitionTwice() {
        val state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300).state
        val changed = BattleEngine.configure(state, participation = BattleParticipation.PERSONAL, personalSection = BattleSection.RIGHT).state
        assertEquals(state.militaryStock.arrows, changed.militaryStock.arrows)
        assertEquals(state.battleSession!!.battleArrowsRemaining, changed.battleSession!!.battleArrowsRemaining)
        assertEquals(BattleSection.RIGHT, changed.battleSession!!.personalSection)
    }

    @Test fun sourcesExactlyAccountForTheExchangeAndSameInputRemainsDeterministic() {
        val started = BattleEngine.start(defenders(), EnemyType.URUK, Tactic.FORTIFY, seed = 98, enemyStrength = 600).state
        val first = exchange(started)
        assertEquals(first, exchange(started))
        val report = first.battleSession!!.exchanges.last()
        assertEquals(started.battleSession!!.ownRemaining - first.battleSession!!.ownRemaining, report.ownLosses)
        assertEquals(started.battleSession!!.enemyRemaining - first.battleSession!!.enemyRemaining, report.enemyLosses)
        assertEquals(first, SaveCodec.decode(SaveCodec.encode(first)))
    }

    @Test fun oldRunningSessionGetsPlausibleDefaultsWithoutTakingCampaignArrowsAgain() {
        val state = exchange(BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90).state)
        val root = Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject.toMutableMap()
        root.remove("_checksum")
        root["version"] = JsonPrimitive(3)
        val fields = setOf("combatVersion", "segments", "siegeDevices", "enemyRoster", "battleArrowsRemaining", "battleArrowsLoaded", "enemyArrowsRemaining", "exchanges", "wallWeapons")
        root["battleSession"] = JsonObject(root.getValue("battleSession").jsonObject.filterKeys { it !in fields })
        val migrated = SaveCodec.decode(JsonObject(root).toString())
        assertEquals(2, migrated.battleSession!!.combatVersion)
        assertEquals(3, migrated.battleSession!!.segments.size)
        assertEquals(state.militaryStock.arrows, migrated.militaryStock.arrows)
        assertEquals(migrated, SaveCodec.decode(SaveCodec.encode(migrated)))
        assertTrue(exchange(migrated).battleSession!!.minute > migrated.battleSession!!.minute)
    }

    @Test fun orderedWithdrawalHasItsOwnOutcomeGrade() {
        val state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY, seed = 90).state
        val ended = BattleEngine.order(state, BattleDecision.ORDERED_RETREAT, BattleSection.CENTER).state.battleSession!!
        assertEquals(BattleOutcomeGrade.TACTICAL_WITHDRAWAL, ended.outcomeGrade)
        assertEquals(0, ended.casualties.captured)
    }
}
