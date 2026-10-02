package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.AllyPeople
import com.goldenunicorn.troopmanager.model.HordeKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontierEngineTest {
    @Test
    fun earlyCampaignDoesNotSpawnUruksOrTaoTei() {
        var state = GameEngine.newGame("Leon", 23, com.goldenunicorn.troopmanager.model.Species.HALF_ELF, null)
        repeat(12) { state = GameEngine.advanceDay(state).state }
        assertTrue(state.frontier.hordes.all { it.kind == HordeKind.ORC })
        assertTrue(state.frontier.hordes.all { it.soldiers <= 80 })
    }

    @Test
    fun customUnitsStayLockedAtTheStart() {
        val state = GameEngine.newGame("Leon", 23, com.goldenunicorn.troopmanager.model.Species.HALF_ELF, null)
        assertFalse(FrontierEngine.customUnitsUnlocked(state))
        val denied = FrontierEngine.designUnit(state, "Garde", com.goldenunicorn.troopmanager.model.Culture.HUMAN, 8, 8, 4, null)
        assertTrue(denied.state.frontier.designs.isEmpty())
    }

    @Test
    fun allySaleAddsSoldiers() {
        val state = GameEngine.newGame("Leon", 23, com.goldenunicorn.troopmanager.model.Species.HALF_ELF, null)
        val before = state.armySize
        val bought = GameEngine.buyReinforcements(state, AllyPeople.GOLD_ELVES, 40).state
        assertTrue(bought.armySize > before)
    }
}
