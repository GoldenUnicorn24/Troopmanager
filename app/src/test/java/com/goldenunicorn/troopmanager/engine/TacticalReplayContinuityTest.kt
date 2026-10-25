package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class TacticalReplayContinuityTest {
    private fun prepared(wall: Boolean, seed: Int): GameState {
        val base = GameEngine.newGame("Mauerwacht", 24, Species.HUMAN, null).copy(
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 600, experience = 35, morale = 80),
                ArmyUnitPool(UnitType.HUMAN_SWORD, 600, experience = 25, morale = 80),
                ArmyUnitPool(UnitType.KNIGHT, 300, morale = 80)),
            population = Population(human = 6000), commanderAssignments = emptyList(),
            realm = Realm(buildings = mapOf(BuildingType.WALL to 4, BuildingType.TOWER to 4)),
            militaryStock = MilitaryStock(arrows = 12000, siegeParts = 100),
            invasion = Invasion(EnemyType.URUK, 1, 2400, 1, devices = listOf(SiegeDevice.LADDERS, SiegeDevice.RAM)),
        )
        return BattleEngine.start(base, EnemyType.URUK, if (wall) Tactic.FORTIFY else Tactic.HOLD,
            seed = seed, enemyStrength = 2400,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1800), UnitAllocation(UnitType.HUMAN_ARCHER, 600)),
            night = false).state
    }

    @Test fun completeTacticalBattlesReplayTheSameCombatAndCommandPoints() {
        for (plan in TacticalBattleEngine.Plan.entries) {
            val planned = TacticalBattleEngine.choosePlan(prepared(plan.wall, 731), plan).state
            val finished = TacticalBattleEngine.autoResolve(planned)
            assertFalse(plan.name, finished.battleSession!!.isActive)
            val loaded = SaveCodec.decode(SaveCodec.encode(finished))
            val actual = loaded.battleSession!!
            val replay = BattleEngine.replay(loaded.war.history.last())
            assertNotNull(plan.name, replay)
            replay!!
            assertEquals(plan.name, actual.status, replay.status)
            assertEquals(plan.name, actual.contingents, replay.contingents)
            assertEquals(plan.name, actual.fronts, replay.fronts)
            assertEquals(plan.name, actual.segments, replay.segments)
            assertEquals(plan.name, actual.causes, replay.causes)
            assertEquals(plan.name, actual.casualties, replay.casualties)
            assertEquals(plan.name, actual.commandPoints, replay.commandPoints)
            assertEquals(plan.name, actual.battleArrowsRemaining, replay.battleArrowsRemaining)
            assertEquals(plan.name, actual.enemyArrowsRemaining, replay.enemyArrowsRemaining)
        }
    }
}
