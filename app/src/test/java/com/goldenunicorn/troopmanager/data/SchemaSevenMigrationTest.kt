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
    @Test fun commanderImportUpdatesTheSameNpcAndKeepsOnePortableIdentity() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,null)
        val original = CustomCharacter("npc-image","npc:shared",CustomCharacterDefinition("Mira",27),"Eigenes Bild")
        val added = CustomCharacterEngine.apply(base,original,CustomImageReference.uri(original.id))
        val atCourt = added.copy(commanders = added.commanders + Commander(999,"Mira",Culture.HUMAN,"knight",portraitUri = CustomImageReference.uri(original.id)))
        val replacement = original.copy(id = "court-image",target = "commander:999")
        val changed = CustomCharacterEngine.apply(atCourt,replacement,CustomImageReference.uri(replacement.id))
        assertEquals("court-image",changed.life.npc("shared")!!.appearance.customImageId)
        assertEquals(1,changed.customCharacters.size)
        assertEquals("court-image",changed.customCharacters.single().id)
        val cleared = CustomCharacterEngine.clearImage(changed,"court-image")
        assertNull(cleared.life.npc("shared")!!.appearance.customImageId)
        assertNull(cleared.commanders.first { it.id == 999L }.portraitUri)
        assertEquals(changed.life.npc("shared")!!.memories,cleared.life.npc("shared")!!.memories)
    }
    @Test fun exportUsesCurrentPlayerDataAndHomeTown() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,null)
        val record = CustomCharacter("player-image","player",CustomCharacterDefinition("Mira",27,homeProvinceId = "al_aldenburg"),"Eigenes Bild")
        val added = CustomCharacterEngine.apply(base,record,CustomImageReference.uri(record.id))
        assertEquals("al_aldenburg",added.life.homeProvinceId)
        val renamed = added.copy(player = added.player.copy(name = "Mira Neu",age = 28))
        val exported = CustomCharacterEngine.currentDefinition(renamed,record)
        assertEquals("Mira Neu",exported.name)
        assertEquals(28,exported.age)
        val cleared = CustomCharacterEngine.clearImage(renamed,record.id)
        assertNull(cleared.player.portraitUri)
        assertNull(cleared.life.appearance?.customImageId)
        assertNull(cleared.crown.playerLook?.customImageId)
    }

    @Test fun photoImportedAfterCourtTransferFollowsTheExplicitNpcIdentity() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,null)
        val friend = NpcEngine.generate("friend",NpcRole.REGULAR,"gm_rabenfurt").copy(name = "Mira")
        val sword = NpcEngine.generate("sword",NpcRole.SELLSWORD,"gm_rabenfurt")
        val before = base.copy(life = base.life.copy(npcs = listOf(friend,sword),party = listOf(PartyMember("friend",1),PartyMember("sword",1,4))))
        val court = LifeEngine.handOverFollowers(before)
        assertEquals("friend",court.companion.npcIdentityId)
        val captain = court.commanders.first { it.npcIdentityId == "sword" }
        val record = CustomCharacter("after-court","commander:${captain.id}",CustomCharacterEngine.definition(sword),"Eigenes Bild")
        val withPicture = CustomCharacterEngine.apply(court,record,CustomImageReference.uri(record.id))
        assertEquals("after-court",withPicture.life.npc("sword")!!.appearance.customImageId)
        val friendRecord = CustomCharacter("friend-photo","npc:friend",CustomCharacterEngine.definition(friend),"Eigenes Bild")
        val withFriend = CustomCharacterEngine.apply(withPicture,friendRecord,CustomImageReference.uri(friendRecord.id))
        assertEquals("custom://friend-photo/portrait",withFriend.companion.portraitUri)
        assertEquals("custom://friend-photo/portrait",withFriend.commanders.first { it.id == COMPANION_COMMANDER_ID }.portraitUri)
        assertEquals(withFriend,SaveCodec.decode(SaveCodec.encode(withFriend)))
    }
    @Test fun editingEveryStockNpcDoesNotRegenerateDuplicatePeople() {
        val populated = LifeEngine.ensurePeople(GameEngine.newGame("Leon",27,Species.HUMAN,null))
        val edited = populated.copy(life = populated.life.copy(npcs = populated.life.npcs.map { it.copy(designedByPlayer = true) }))
        assertEquals(edited,LifeEngine.ensurePeople(edited))
    }
    @Test fun legacyCourtLinkIsRecoveredOnlyForUniqueRecordedPeople() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,null)
        val npc = NpcEngine.generate("former",NpcRole.REGULAR,"gm_rabenfurt").copy(name = "Mira",species = Species.HUMAN,departed = true,
            memories = listOf(NpcMemory("court",1,"An den Hof gezogen")))
        val old = base.copy(companion = CompanionProfile(met = true,name = "Mira",species = Species.HUMAN,portraitUri = "content://old/photo"),
            life = base.life.copy(npcs = listOf(npc)))
        val linked = LegacyPortraitMigration.migrate(old) { "file:///internal/custom/mira-photo/portrait.webp" }
        assertEquals("former",linked.companion.npcIdentityId)
        assertEquals("mira-photo",linked.life.npc("former")!!.appearance.customImageId)
        val ambiguous = old.copy(life = old.life.copy(npcs = listOf(npc,npc.copy(id = "another"))))
        val unlinked = LegacyPortraitMigration.migrate(ambiguous) { "file:///internal/custom/mira-photo/portrait.webp" }
        assertNull(unlinked.companion.npcIdentityId)
        assertTrue(unlinked.life.npcs.all { it.appearance.customImageId == null })
    }

    @Test fun sameOriginalPhotoForDifferentPeopleDoesNotMergeTheirIdentities() {
        val base = GameEngine.newGame("Leon",27,Species.HUMAN,"content://same/photo")
            .copy(companion = CompanionProfile(name = "Mira",portraitUri = "content://same/photo"))
        var copies = 0
        val migrated = LegacyPortraitMigration.migrate(base) { copies++; "file:///internal/custom/person-$copies/portrait.webp" }
        assertEquals(2,copies)
        assertNotEquals(migrated.player.portraitUri,migrated.companion.portraitUri)
        val own = CustomCharacter("new-player-photo","player",CustomCharacterDefinition("Leon Neu",28),"Eigenes Bild")
        val changed = CustomCharacterEngine.apply(migrated,own,CustomImageReference.uri(own.id))
        assertEquals("Mira",changed.companion.name)
        assertEquals(migrated.companion.portraitUri,changed.companion.portraitUri)
    }

}
