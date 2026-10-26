package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.HeroineSagaEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Schema 6 (Project Rebirth) is additive: every v2.1 border-lord save loads as a classic landed game. */
class SchemaSixMigrationTest {
    private fun asSchemaFive(state: GameState): String {
        val root = Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject
        val legacy = JsonObject(root.filterKeys { it != "life" && it != "_checksum" } + ("version" to JsonPrimitive(5)))
        val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(legacy.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return JsonObject(legacy + ("_checksum" to JsonPrimitive(checksum))).toString()
    }

    @Test fun schemaFiveSaveBecomesClassicLandedGame() {
        var original = HeroineSagaEngine.setPartner(GameEngine.newGame("Leon", 27, Species.HALF_ELF, null),
            PartnerProfile("Mira", 25, Species.HALF_ELF, "alina", null))
        repeat(3) { original = GameEngine.advanceDay(original).state }
        val migrated = SaveCodec.decode(asSchemaFive(original))
        assertEquals(SaveCodec.CURRENT_VERSION, migrated.version)
        assertEquals(GameMode.BORDER_LORD, migrated.life.mode)
        assertTrue(migrated.life.landed)
        assertTrue(migrated.life.storyActs)
        assertEquals(original.copy(life = LifeState(), version = SaveCodec.CURRENT_VERSION), migrated.copy(life = LifeState()))
        assertEquals(original.armySize, migrated.armySize)
        assertEquals(original.crown.saga.partner, migrated.crown.saga.partner)
        // The migrated campaign keeps playing.
        val next = GameEngine.advanceDay(migrated).state
        assertEquals(migrated.day + 1, next.day)
        assertEquals(next, SaveCodec.decode(SaveCodec.encode(next)))
    }

    @Test fun invalidLifeDataIsRejected() {
        val state = GameEngine.newGame("Leon", 27, Species.HUMAN, null)
        val minor = com.goldenunicorn.troopmanager.engine.NpcEngine.generate("x", NpcRole.REGULAR, "gm_rabenfurt").copy(age = 16)
        try {
            SaveCodec.encode(state.copy(life = state.life.copy(npcs = listOf(minor))))
            fail("Minors must never enter the people list")
        } catch (_: IllegalArgumentException) { }
        try {
            SaveCodec.encode(state.copy(life = state.life.copy(purse = -1)))
            fail("Negative purse must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
