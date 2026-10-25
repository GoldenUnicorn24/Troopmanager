package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Regressions at the boundary between cinematic plans, core combat and schema-5 saves. */
class TacticalBattlePersistenceTest {
    private fun prepared(wall: Boolean, wonders: Boolean = true): GameState {
        val base = GameEngine.newGame("Mauerwacht", 24, Species.HUMAN, null)
        val state = base.copy(
            armyPools = listOf(
                ArmyUnitPool(UnitType.HUMAN_ARCHER, 600, experience = 35, morale = 60),
                ArmyUnitPool(UnitType.HUMAN_SWORD, 300, experience = 25, morale = 60),
                ArmyUnitPool(UnitType.KNIGHT, 100, morale = 60),
            ),
            population = Population(human = 5000),
            commanderAssignments = emptyList(),
            realm = Realm(buildings = mapOf(BuildingType.WALL to 4, BuildingType.TOWER to 4)),
            militaryStock = MilitaryStock(arrows = 8000, siegeParts = 100),
            reign = base.reign.copy(wonders = if (wonders) listOf(
                WonderProject(WonderKind.GREAT_WALL, 0, 1, finished = true),
                WonderProject(WonderKind.DRAGON_BALLISTAE, 0, 1, finished = true),
            ) else emptyList()),
            invasion = Invasion(EnemyType.ORC, 1, 600, 1, devices = emptyList()),
        )
        return BattleEngine.start(state, EnemyType.ORC, if (wall) Tactic.FORTIFY else Tactic.HOLD,
            seed = 971, enemyStrength = 600,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 450), UnitAllocation(UnitType.HUMAN_ARCHER, 150)),
            night = false).state
    }

    @Test fun greatWallPlanCanBeSavedAndResumed() {
        val before = prepared(wall = true)
        val chosen = TacticalBattleEngine.choosePlan(before, TacticalBattleEngine.Plan.HOLD_WALL).state
        assertTrue(chosen.battleSession!!.segments.all { it.cover <= .85 })
        val resumed = SaveCodec.decode(SaveCodec.encode(chosen))
        assertEquals(chosen, resumed)
        assertEquals(TacticalBattleEngine.step(chosen).state, TacticalBattleEngine.step(resumed).state)
        assertEquals(before.militaryStock, chosen.militaryStock)
        assertEquals(before.battleSession!!.battleArrowsRemaining, chosen.battleSession.battleArrowsRemaining)
    }

    @Test fun everyPlanPersistsItsActualReplayStartingConditions() {
        TacticalBattleEngine.Plan.entries.forEach { plan ->
            val chosen = TacticalBattleEngine.choosePlan(prepared(plan.wall), plan).state
            val battle = chosen.battleSession!!
            val replay = battle.replayStart!!
            assertEquals(plan.name, battle.contingents, replay.initialContingents)
            assertEquals(plan.name, battle.fronts, replay.initialFronts)
            assertEquals(plan.name, battle.segments, replay.initialSegments)
            assertEquals(plan.name, battle.heroPerk, replay.heroPerk)
            assertEquals(plan.name, battle.reservePerk, replay.reservePerk)
            assertEquals(plan.name, battle.rallyPerk, replay.rallyPerk)
            assertEquals(plan.name, battle.moraleBonus, replay.moraleBonus)
            assertEquals(chosen, SaveCodec.decode(SaveCodec.encode(chosen)))
        }
    }

    @Test fun firstExchangeReplaysTheChosenPlanRatherThanTheUnboostedArmy() {
        TacticalBattleEngine.Plan.entries.forEach { plan ->
            val chosen = TacticalBattleEngine.choosePlan(prepared(plan.wall), plan).state
            val actual = BattleEngine.advance(chosen).state.battleSession!!
            val record = BattleRecord("plan-check", chosen.day, "Mauerwacht", actual.enemy, actual.seed,
                actual.tactic, actual.ownStart, actual.enemyStart, actual.ownRemaining, actual.enemyRemaining,
                false, actual.minute, actual.casualties, terrain = actual.terrain,
                participation = actual.participation, replay = actual.replayStart,
                inputs = actual.inputs, enemyUnits = actual.enemyUnits)
            val replay = BattleEngine.replay(record)!!
            assertEquals(plan.name, actual.contingents, replay.contingents)
            assertEquals(plan.name, actual.fronts, replay.fronts)
            assertEquals(plan.name, actual.segments, replay.segments)
            assertEquals(plan.name, actual.exchanges, replay.exchanges)
            assertEquals(plan.name, actual.battleArrowsRemaining, replay.battleArrowsRemaining)
            assertEquals(plan.name, actual.casualties, replay.casualties)
        }
    }

    @Test fun planCannotBeAppliedAfterTheDetailedSimulationHasStarted() {
        val running = BattleEngine.advance(prepared(wall = false, wonders = false)).state
        assertEquals(0, running.battleSession!!.tacticalStage)
        assertTrue(running.battleSession.step > 0)
        assertSame(running, TacticalBattleEngine.choosePlan(running, TacticalBattleEngine.Plan.SHIELD_WALL).state)
    }

    @Test fun planCannotChangeACompletedBattle() {
        val ended = BattleEngine.order(prepared(wall = false, wonders = false),
            BattleDecision.ORDERED_RETREAT, BattleSection.CENTER).state
        assertFalse(ended.battleSession!!.isActive)
        assertSame(ended, TacticalBattleEngine.choosePlan(ended, TacticalBattleEngine.Plan.SHIELD_WALL).state)
    }

    @Test fun planSelectionIsOneShotAndDoesNotGrantWonderBonusesWithoutTheWonder() {
        val before = prepared(wall = true, wonders = false)
        val chosen = TacticalBattleEngine.choosePlan(before, TacticalBattleEngine.Plan.HOLD_WALL).state
        assertEquals(before.battleSession!!.segments, chosen.battleSession!!.segments)
        assertEquals(before.battleSession.contingents.map { it.cohesion }, chosen.battleSession.contingents.map { it.cohesion })
        assertSame(chosen, TacticalBattleEngine.choosePlan(chosen, TacticalBattleEngine.Plan.HOLD_WALL).state)
        assertEquals(chosen, SaveCodec.decode(SaveCodec.encode(chosen)))
    }
}
