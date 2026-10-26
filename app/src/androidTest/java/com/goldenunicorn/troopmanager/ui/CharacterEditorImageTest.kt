package com.goldenunicorn.troopmanager.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldenunicorn.troopmanager.engine.CustomCharacterEngine
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Import/storage is tested separately; these tests exercise the editor's live state transition. */
@RunWith(AndroidJUnit4::class)
class CharacterEditorImageTest {
    @get:Rule val rule = createAndroidComposeRule<TourActivity>()

    private fun selfPage() {
        rule.runOnIdle { TourActivity.page = "life-3" }
        rule.waitForIdle()
    }
    private fun importedState() {
        rule.runOnIdle {
            val state = TourActivity.latestState!!
            val definition = CustomCharacterDefinition(state.player.name,state.player.age,state.life.gender,state.player.species,
                state.life.culture,state.life.origin,state.life.profession,homeProvinceId = state.life.homeProvinceId,
                appearance = state.life.appearance!!.copy(customImageId = null))
            val record = CustomCharacter("editor-regression","player",definition,"Eigenes Testbild")
            TourActivity.replaceState!!(CustomCharacterEngine.apply(state,record,CustomImageReference.uri(record.id)))
        }
        rule.waitForIdle()
    }
    private fun confirmAndCheck() {
        rule.onNodeWithText("Übernehmen").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("editor-regression",TourActivity.latestState!!.life.appearance!!.customImageId)
            assertEquals("editor-regression",TourActivity.latestState!!.crown.playerLook!!.customImageId)
        }
    }
    @Test fun importWhileAppearanceEditorIsOpenCannotBeOverwrittenByOldDraft() {
        selfPage()
        rule.onNodeWithText("Aussehen ändern").performScrollTo().performClick()
        rule.onNodeWithTag("editor_preview").assertExists()
        importedState()
        confirmAndCheck()
    }
    @Test fun resettingProceduralFeaturesKeepsTheImportedImage() {
        selfPage()
        importedState()
        rule.onNodeWithText("Aussehen ändern").performScrollTo().performClick()
        rule.onNodeWithText("Alles zurücksetzen").performScrollTo().performClick()
        confirmAndCheck()
    }
}
