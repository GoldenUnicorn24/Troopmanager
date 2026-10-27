package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Own pictures chosen in the character editor: registration without touching the person's data. */
class OwnImageRegistrationTest {
    private fun wizardGame(companion: Boolean = true): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = GameMode.FREE_LIFE, name = "Leon Falkner", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = Background.MERCENARY, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = Towns.startable().first().provinceId,
        backstory = "", appearance = CharacterAppearance(),
        attributes = GameEngine.StartingAttributes(50, 50, 40, 40, 40, 40),
        startingCultures = null, weapon = "Langschwert", armorStyle = "Leder", ironman = false, storyActs = false,
        companionChoice = if (companion) CompanionChoice.FULL else CompanionChoice.NONE,
        companionDraft = if (companion) NpcEngine.CompanionDraft(name = "Mira Taufarn", age = 23, appearance = CharacterAppearance(hairStyle = 2)) else null,
        companionPath = "alina",
    ))
    private val files = CustomCharacterEngine.ImageFiles(portrait = true, fullbody = false, source = "Eigenes Testbild")

    @Test fun playerImageFromTheEditorBecomesARecordAndAPortraitReference() {
        val state = wizardGame(companion = false)
        val look = state.life.appearance!!.copy(customImageId = "own-player")
        val saved = CustomCharacterEngine.syncEditorImage(state, "player", look, files)
        assertEquals("own-player", saved.life.appearance!!.customImageId)
        assertEquals("own-player", saved.crown.playerLook!!.customImageId)
        assertEquals(CustomImageReference.uri("own-player"), saved.player.portraitUri)
        assertEquals(listOf("player"), saved.customCharacters.map { it.target })
        assertEquals(state.player.name, saved.player.name)
        assertEquals(state.life.purse, saved.life.purse)
        assertEquals(saved, SaveCodec.decode(SaveCodec.encode(saved)))
    }

    @Test fun replacingThePlayerImageKeepsOneRecordAndRemovingRestoresTheStandardFace() {
        val state = wizardGame(companion = false)
        val first = CustomCharacterEngine.syncEditorImage(state, "player", state.life.appearance!!.copy(customImageId = "one"), files)
        val second = CustomCharacterEngine.syncEditorImage(first, "player", first.life.appearance!!.copy(customImageId = "two"), files)
        assertEquals(listOf("two"), second.customCharacters.map { it.id })
        assertEquals("two", second.life.appearance!!.customImageId)
        val removed = CustomCharacterEngine.syncEditorImage(second, "player", second.life.appearance!!.copy(customImageId = null), null)
        assertNull(removed.life.appearance!!.customImageId)
        assertNull(removed.player.portraitUri)
        assertTrue(removed.customCharacters.isEmpty())
    }

    @Test fun unchangedImageOnlyUpdatesTheLook() {
        val state = CustomCharacterEngine.syncEditorImage(wizardGame(companion = false), "player",
            wizardGame(companion = false).life.appearance!!.copy(customImageId = "same"), files)
        val edited = CustomCharacterEngine.syncEditorImage(state, "player", state.life.appearance!!.copy(hairColor = 5), files)
        assertEquals(5, edited.life.appearance!!.hairColor)
        assertEquals(state.customCharacters, edited.customCharacters)
    }

    @Test fun fullBodyOnlySetKeepsTheStandardPortrait() {
        val state = wizardGame(companion = false)
        val saved = CustomCharacterEngine.syncEditorImage(state, "player", state.life.appearance!!.copy(customImageId = "body-only"),
            CustomCharacterEngine.ImageFiles(portrait = false, fullbody = true, source = "Eigenes Testbild"))
        val record = saved.customCharacters.single()
        assertNull(record.portrait)
        assertEquals("fullbody.webp", record.fullbody)
        record.validate()
        assertNull(saved.player.portraitUri)
    }

    @Test(expected = IllegalArgumentException::class) fun recordWithoutAnyImageIsRejected() {
        CustomCharacter("x", "player", CustomCharacterDefinition("Mira", 27), "Eigenes Bild", portrait = null, fullbody = null).validate()
    }

    @Test fun designedCompanionKeepsHerIndependentLifeWhenHerPictureIsRegistered() {
        val state = wizardGame()
        val id = state.life.companionNpcId!!
        val before = state.life.npc(id)!!
        val saved = CustomCharacterEngine.registerDesignedImage(state, id, "own-companion", files)
        val after = saved.life.npc(id)!!
        assertEquals("own-companion", after.appearance.customImageId)
        assertEquals(before.copy(appearance = before.appearance.copy(customImageId = "own-companion")), after)
        assertEquals(SocialStage.STRANGER, after.relation.stage)
        assertEquals(before.attractedTo, after.attractedTo)
        assertEquals(before.routine, after.routine)
        assertEquals("npc:$id", saved.customCharacters.single().target)
        assertEquals(state.companion, saved.companion)
        LifeEngine.validate(saved)
        assertEquals(saved, SaveCodec.decode(SaveCodec.encode(saved)))
    }

    @Test fun replacingAPictureNeverDropsRelationsOrMemories() {
        val state = wizardGame()
        val id = state.life.companionNpcId!!
        val known = state.copy(life = state.life.copy(npcs = state.life.npcs.map {
            if (it.id == id) it.copy(memories = listOf(NpcMemory(id, 1, "Begegnung am Markt")), relation = it.relation.copy(trust = 35)) else it
        }))
        val first = CustomCharacterEngine.registerDesignedImage(known, id, "first", files)
        val second = CustomCharacterEngine.registerDesignedImage(first, id, "second", files)
        val npc = second.life.npc(id)!!
        assertEquals("second", npc.appearance.customImageId)
        assertEquals(35, npc.relation.trust)
        assertEquals("Begegnung am Markt", npc.memories.single().text)
        assertEquals(listOf("second"), second.customCharacters.map { it.id })
    }

    @Test fun designedPartnerInLandedModesShowsTheOwnPortrait() {
        val state = LifeEngine.newGame(LifeEngine.StartConfig(
            mode = GameMode.BORDER_LORD, name = "Leon", age = 30, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
            background = Background.BORDER_LORD, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = Towns.startable().first().provinceId,
            backstory = "", appearance = CharacterAppearance(), attributes = null, startingCultures = setOf(Culture.HUMAN),
            weapon = "Langschwert", armorStyle = "Leder", ironman = false, storyActs = false,
            companionChoice = CompanionChoice.FULL, companionDraft = NpcEngine.CompanionDraft(name = "Mira", age = 25), companionPath = "alina"))
        val id = state.life.companionNpcId!!
        val saved = CustomCharacterEngine.registerDesignedImage(state, id, "landed-partner", files)
        val partner = saved.crown.saga.partner!!
        assertEquals("landed-partner", partner.look!!.customImageId)
        assertEquals(CustomImageReference.uri("landed-partner"), partner.portraitUri)
        assertEquals(state.crown.saga.partner!!.name, partner.name)
    }
}
