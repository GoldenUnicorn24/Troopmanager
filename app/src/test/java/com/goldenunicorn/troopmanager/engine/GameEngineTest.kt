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
}
