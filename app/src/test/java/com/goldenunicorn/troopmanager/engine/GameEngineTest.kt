package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {

    @Test
    fun companionJoinsEarlyCareer() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        repeat(4) { state = GameEngine.advanceDay(state).state }
        assertTrue(state.companion.met)
    }

    @Test
    fun percentRecruitmentCreatesTrainingOrder() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        val result = GameEngine.recruit(state, UnitType.HUMAN_ARCHER, 50).state
        assertEquals(1, result.trainingQueue.size)
        assertEquals(21, result.trainingQueue.first().amount)
        assertTrue(result.population.humanRecruits < state.population.humanRecruits)
    }

    @Test
    fun trainingBecomesRegiment() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        state = GameEngine.recruit(state, UnitType.HUMAN_SWORD, 50).state
        repeat(8) { state = GameEngine.advanceDay(state).state }
        assertTrue(state.regiments.isNotEmpty())
        assertTrue(state.armySize > 0)
    }

    @Test
    fun halfElfCanRecruitEveryCulture() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        UnitType.entries.forEach { assertTrue(GameEngine.isUnitUnlocked(state, it)) }
    }

    @Test
    fun deterministicMissionCanAwardRenown() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        val result = GameEngine.runMission(state, MissionType.PATROL, 1.15).state
        assertTrue(result.renown > state.renown)
        assertTrue(result.resources.gold > state.resources.gold)
    }

    @Test
    fun quarryProducesStone() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        state = state.copy(resources = state.resources.copy(gold = 5000, wood = 5000, stone = 5000))
        state = GameEngine.build(state, BuildingType.QUARRY).state
        val before = state.resources.stone
        state = GameEngine.advanceDay(state).state
        assertTrue(state.resources.stone > before)
    }

    @Test
    fun tutorialCanBeMarkedSeen() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        assertFalse(state.tutorialSeen)
        assertTrue(GameEngine.markTutorialSeen(state).tutorialSeen)
    }

    @Test
    fun commanderCanReceiveMixedUnitQuantities() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        state = state.copy(
            resources = state.resources.copy(gold = 100000, iron = 100000),
            regiments = listOf(
                Regiment(1, "A", UnitType.HUMAN_SWORD, 500, 500),
                Regiment(2, "B", UnitType.HUMAN_ARCHER, 300, 300)
            )
        )
        state = GameEngine.promoteCommander(state).state
        val commander = state.commanders.first()
        val result = GameEngine.setCommanderAllocation(
            state,
            commander.id,
            listOf(
                UnitAllocation(UnitType.HUMAN_SWORD, 500),
                UnitAllocation(UnitType.HUMAN_ARCHER, 300)
            )
        ).state
        assertEquals(800, result.commanderAssignments.first().total)
        assertEquals(500, result.assignedTo(commander.id, UnitType.HUMAN_SWORD))
        assertEquals(300, result.assignedTo(commander.id, UnitType.HUMAN_ARCHER))
    }

    @Test
    fun liveBattleProducesReadableTimeline() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null).copy(
            regiments = listOf(
                Regiment(1, "A", UnitType.HUMAN_SWORD, 1200, 1200),
                Regiment(2, "B", UnitType.HUMAN_ARCHER, 800, 800)
            )
        )
        val result = GameEngine.simulateBattleLive(state, EnemyType.ORC, Tactic.HOLD, seed = 42)
        assertTrue(result.frames.size >= 5)
        assertEquals(2000, result.ownStart)
        assertTrue(result.frames.last().minute <= 90)
        assertTrue(result.finalState.armySize <= state.armySize)
    }

    @Test
    fun commanderCanReceiveMixedUnitCounts() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        state = state.copy(
            resources = state.resources.copy(gold = 100000, iron = 100000),
            population = state.population.copy(humanRecruits = 1200)
        )
        state = GameEngine.recruit(state, UnitType.HUMAN_SWORD, 50).state
        state = GameEngine.recruit(state, UnitType.HUMAN_ARCHER, 100).state
        repeat(14) { state = GameEngine.advanceDay(state).state }
        state = GameEngine.promoteCommander(state).state
        val commander = state.commanders.first()
        val result = GameEngine.setCommanderAllocation(
            state,
            commander.id,
            listOf(
                UnitAllocation(UnitType.HUMAN_SWORD, 300),
                UnitAllocation(UnitType.HUMAN_ARCHER, 200)
            )
        ).state
        assertEquals(300, result.assignedTo(commander.id, UnitType.HUMAN_SWORD))
        assertEquals(200, result.assignedTo(commander.id, UnitType.HUMAN_ARCHER))
    }

    @Test
    fun liveBattleProducesReadableFrames() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        state = state.copy(
            resources = state.resources.copy(gold = 100000, iron = 100000),
            population = state.population.copy(humanRecruits = 900)
        )
        state = GameEngine.recruit(state, UnitType.HUMAN_SWORD, 100).state
        repeat(10) { state = GameEngine.advanceDay(state).state }
        val battle = GameEngine.simulateBattleLive(state, EnemyType.ORC, Tactic.HOLD, seed = 42)
        assertTrue(battle.frames.isNotEmpty())
        assertTrue(battle.frames.last().minute >= 80)
        assertTrue(battle.ownStart > 0)
        assertTrue(battle.enemyStart > 0)
    }
}
