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
        assertEquals("one", migrated.life.appearance?.customImageId)
        assertEquals("one", migrated.crown.playerLook?.customImageId)
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
    @Test fun importingCommanderDataUpdatesTheDisplayedCourtAge() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,null)
        val commander = base.commanders.first()
        val record = CustomCharacter("commander-image","commander:${commander.id}",CustomCharacterDefinition("Mira",29,origin = "Rabenfurt"),"Eigenes Bild")
        val imported = CustomCharacterEngine.apply(base,record,CustomImageReference.uri(record.id))
        assertEquals(29,imported.court.characters.first { it.commanderId == commander.id }.age)
        assertEquals("Rabenfurt",imported.court.characters.first { it.commanderId == commander.id }.origin)
    }
    @Test(expected = IllegalArgumentException::class) fun minorsCannotBeImported() {
        CustomCharacterDefinition("Mira", 17).validate()
    }
    @Test fun customPhotoReferenceSurvivesBecomingACommanderAndReplacement() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,null)
        val record = CustomCharacter("old-image","npc:photo-sword",CustomCharacterDefinition("Mira",27),"Eigenes Bild")
        val added = CustomCharacterEngine.apply(base,record,CustomImageReference.uri(record.id))
        val atCourt = added.copy(commanders = added.commanders + Commander(999,"Mira",Culture.HUMAN,"knight",portraitUri = CustomImageReference.uri(record.id)))
        val replaced = CustomCharacterEngine.apply(atCourt,record.copy(id = "new-image"),CustomImageReference.uri("new-image"))
        assertEquals("custom://new-image/portrait",replaced.commanders.first { it.id == 999L }.portraitUri)
        assertEquals("new-image",replaced.life.npc("photo-sword")!!.appearance.customImageId)
        assertNull(CustomImageReference.idFromUri("custom://bad.id/portrait"))
        assertNull(CustomImageReference.idFromUri("custom://old-image/../../private"))
    }
}
