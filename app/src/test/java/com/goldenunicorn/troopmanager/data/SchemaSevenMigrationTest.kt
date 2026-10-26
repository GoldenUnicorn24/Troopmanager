package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SchemaSevenMigrationTest {
    private fun oldSave(state: GameState, version: Int): String {
        val encoded = Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject
        val root = JsonObject(encoded.filterKeys { it != "customCharacters" && it != "_checksum" } + ("version" to JsonPrimitive(version)))
        val checksum = MessageDigest.getInstance("SHA-256").digest(root.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        return JsonObject(root + ("_checksum" to JsonPrimitive(checksum))).toString()
    }
    @Test fun schemaFourFiveSixLoadWithoutLosingCampaignData() {
        val state = GameEngine.newGame("Leon", 27, Species.HUMAN, null)
        listOf(4, 5, 6).forEach { version ->
            val loaded = SaveCodec.decode(oldSave(state, version))
            assertEquals(7, loaded.version)
            assertTrue(loaded.customCharacters.isEmpty())
            assertEquals(state.player, loaded.player)
            assertEquals(state.life, loaded.life)
            assertEquals(loaded, SaveCodec.decode(SaveCodec.encode(loaded)))
        }
    }
    @Test fun contentUrisAreCopiedOnceAndMissingOriginalFallsBack() {
        val state = GameEngine.newGame("Leon", 27, Species.HUMAN, "content://old/player")
            .copy(companion = CompanionProfile(portraitUri = "content://missing/photo"))
        val calls = mutableListOf<String>()
        val migrated = LegacyPortraitMigration.migrate(state) { value -> calls += value; if (value.contains("missing")) null else "file:///internal/custom/one/portrait.webp" }
        assertEquals("file:///internal/custom/one/portrait.webp", migrated.player.portraitUri)
        assertNull(migrated.companion.portraitUri)
        assertEquals(2, calls.size)
        assertEquals(migrated, LegacyPortraitMigration.migrate(migrated) { fail("Must not read original twice"); null })
    }
    @Test fun customNpcHasNormalDialogueAndKeepsMemoriesWhenReplaced() {
        val base = GameEngine.newGame("Leon", 27, Species.HUMAN, null)
        val record = CustomCharacter("test-one", "npc:test", CustomCharacterDefinition("Mira", 27), "Eigenes Testbild")
        val added = CustomCharacterEngine.apply(base, record, "file:///internal/custom/test-one/portrait.webp")
        assertEquals(SocialStage.STRANGER, added.life.npc("test")!!.relation.stage)
        assertEquals("test-one", added.life.npc("test")!!.appearance.customImageId)
        LifeEngine.validate(added)
        assertEquals(added, SaveCodec.decode(SaveCodec.encode(added)))
        val remembered = added.copy(life = added.life.copy(npcs = added.life.npcs.map { it.copy(memories = listOf(NpcMemory("test", 1, "Erinnerung"))) }))
        val replaced = CustomCharacterEngine.apply(remembered, record.copy(id = "test-two"), "file:///internal/custom/test-two/portrait.webp")
        assertEquals("Erinnerung", replaced.life.npc("test")!!.memories.single().text)
    }
    @Test(expected = IllegalArgumentException::class) fun minorsCannotBeImported() {
        CustomCharacterDefinition("Mira", 17).validate()
    }
}
