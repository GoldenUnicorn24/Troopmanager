package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.GameDestination
import com.goldenunicorn.troopmanager.model.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextActionEngineTest {
    @Test fun damagedWallOpensTheCityRatherThanTheArmy() {
        val original = GameEngine.newGame("Grenzwacht", 28, Species.HUMAN, null)
        val state = original.copy(
            realm = original.realm.copy(wallIntegrity = 62),
            resources = original.resources.copy(food = 100_000),
            frontier = original.frontier.copy(hordes = emptyList()),
            campaign = original.campaign.copy(pendingDecision = null),
        )
        val recommendation = CampaignInsightsEngine.nextAction(state)
        assertEquals(GameDestination.CITY, recommendation.destination)
        assertTrue(recommendation.reason.contains("62"))
        assertTrue(recommendation.button.contains("Mauer"))
    }

    @Test fun recommendationHasAValidDestinationAndNeverChangesCampaignState() {
        val state = GameEngine.newGame("Grenzwacht", 28, Species.HUMAN, null)
        val before = SaveCodec.encode(state)
        val recommendation = CampaignInsightsEngine.nextAction(state)
        assertTrue(recommendation.title.isNotBlank())
        assertTrue(recommendation.reason.isNotBlank())
        assertTrue(recommendation.button.isNotBlank())
        assertTrue(recommendation.priority > 0)
        assertEquals(before, SaveCodec.encode(state))
    }
}
