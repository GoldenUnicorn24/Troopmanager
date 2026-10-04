package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.CampaignFocus
import com.goldenunicorn.troopmanager.model.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignPulseEngineTest {
    @Test
    fun campaignDecisionAppearsWithoutBlockingNormalProgression() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        repeat(4) { state = GameEngine.advanceDay(state).state }

        assertTrue(state.day >= 5)
        assertNotNull(state.campaign.pendingDecision)
        assertTrue(state.campaign.pressure in 0..100)
        assertTrue(state.campaign.momentum in 0..100)
    }

    @Test
    fun focusCanBeChangedAndPersists() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        val result = CampaignPulseEngine.setFocus(state, CampaignFocus.DEFENSE)

        assertEquals(CampaignFocus.DEFENSE, result.state.campaign.focus)
    }

    @Test
    fun aFreeFallbackChoiceResolvesTheDilemma() {
        var state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        repeat(4) { state = GameEngine.advanceDay(state).state }

        val decision = requireNotNull(state.campaign.pendingDecision)
        val fallback = CampaignPulseEngine.choices(state, decision).last()
        val result = CampaignPulseEngine.resolve(state, fallback.id)

        assertNull(result.state.campaign.pendingDecision)
        assertTrue(result.state.campaign.recentOutcomes.isNotEmpty())
    }
}
