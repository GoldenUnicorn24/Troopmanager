package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReplayInputCompatibilityTest {
    private fun battle(): GameState = BattleEngine.start(
        GameEngine.newGame("Mauerwacht", 24, Species.HUMAN, null), EnemyType.ORC,
        Tactic.FORTIFY, seed = 731, enemyStrength = 300).state

    @Test fun oldInputsWithoutCommandPointsKeepTheirLegacyDefault() {
        val input = Json.decodeFromString<BattleInput>("""{"decision":"HOLD"}""")
        assertEquals(BattleDecision.HOLD, input.decision)
        assertNull(input.commandPointsBefore)
        assertFalse(Json { encodeDefaults = true }.encodeToString(input).contains("commandPointsBefore"))
    }

    @Test fun commandPointSnapshotsRoundTripInExistingSaveSchema() {
        val state = BattleEngine.advance(battle()).state
        val input = state.battleSession!!.inputs.single()
        assertNotNull(input.commandPointsBefore)
        assertEquals(SaveCodec.CURRENT_VERSION, state.version)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun impossibleCommandPointSnapshotsAreRejectedBeforeSaving() {
        val state = battle()
        for (points in listOf(-1, state.battleSession!!.maxCommandPoints + 1)) {
            val bad = state.copy(battleSession = state.battleSession.copy(
                inputs = listOf(BattleInput(commandPointsBefore = points))))
            assertThrows(IllegalArgumentException::class.java) { SaveCodec.encode(bad) }
        }
    }
}
