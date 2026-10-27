package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the causes of the visible art changes in "Neues Spiel → Aussehen":
 * the generated face/vector art used to be the loading state of every own photo and every
 * photographic portrait, and a framing switch restarted the custom image load.
 */
@RunWith(AndroidJUnit4::class)
class CharacterArtStateTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val images = CustomCharacterImages(instrumentation.targetContext)

    private fun installPortrait(withBody: Boolean = false): String {
        val bytes = instrumentation.context.assets.open("adult-portrait.png").use { it.readBytes() }
        val decoded = images.decode(bytes, CharacterImageKind.PORTRAIT)
        val portrait = images.crop(decoded, CharacterImageKind.PORTRAIT, CharacterImageCrop())
        val body = if (withBody) images.decode(instrumentation.context.assets.open("adult-fullbody.png").use { it.readBytes() }, CharacterImageKind.FULLBODY)
            .let { images.crop(it, CharacterImageKind.FULLBODY, CharacterImageCrop()) } else null
        return images.install(portrait, body, "Testbild CC0", mapOf(CharacterImageKind.PORTRAIT to (decoded to CharacterImageCrop())))
    }

    private fun framesUntil(done: () -> Boolean, eachFrame: () -> Unit) {
        val started = System.currentTimeMillis()
        while (!done()) {
            rule.mainClock.advanceTimeByFrame()
            eachFrame()
            assertTrue("Bild wurde nicht rechtzeitig geladen", System.currentTimeMillis() - started < 10_000)
            Thread.sleep(8)
        }
    }

    @Test fun ownPortraitNeverShowsTheGeneratedFaceWhileLoading() {
        val id = installPortrait()
        rule.mainClock.autoAdvance = false
        rule.setContent { CharacterPortrait(CharacterAppearance(customImageId = id), Gender.MALE, Modifier.size(220.dp, 275.dp)) }
        framesUntil({ rule.onAllNodesWithContentDescription("Eigenes Porträt").fetchSemanticsNodes().isNotEmpty() }) {
            assertTrue("Erzeugtes Gesicht blitzte vor dem eigenen Foto auf",
                rule.onAllNodesWithContentDescription("Porträt").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun switchingHeadAndBustReusesTheLoadedOwnPortrait() {
        val id = installPortrait()
        var framing by mutableStateOf(Framing.BUST)
        rule.setContent { CharacterPortrait(CharacterAppearance(customImageId = id), Gender.MALE, Modifier.size(220.dp, 275.dp), framing) }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Eigenes Porträt").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.autoAdvance = false
        framing = Framing.HEAD
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithContentDescription("Eigenes Porträt").assertExists()
        assertTrue(rule.onAllNodesWithContentDescription("Bild wird geladen").fetchSemanticsNodes().isEmpty())
    }

    @Test fun photographicPortraitUsesANeutralPlaceholderInsteadOfVectorArt() {
        // A fresh look (unusual combination) is not in the memory cache: the first frame must be the placeholder.
        val look = CharacterAppearance(faceShape = 4, hairStyle = 5, hairTexture = 2, hairColor = 6, skinTone = 5, eyeShape = 3, background = 4)
        rule.mainClock.autoAdvance = false
        rule.setContent { CharacterPortrait(look, Gender.FEMALE, Modifier.size(200.dp, 250.dp)) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithContentDescription("Bild wird geladen").assertExists()
        rule.mainClock.autoAdvance = true
        rule.waitUntil(10_000) { ArtLoading.pending.get() == 0 && rule.onAllNodesWithContentDescription("Porträt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun portraitWithoutOwnFullBodyShowsTheStandardFigureNotAFakePhotoFigure() {
        val id = installPortrait(withBody = false)
        rule.setContent { CharacterPortrait(CharacterAppearance(customImageId = id), Gender.MALE, Modifier.size(200.dp, 250.dp), Framing.FULL) }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Ganzkörperfigur").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithContentDescription("Eigenes Ganzkörperbild").fetchSemanticsNodes().isEmpty())
    }

    @Test fun ownFullBodyIsShownInTheFullView() {
        val id = installPortrait(withBody = true)
        rule.setContent { CharacterPortrait(CharacterAppearance(customImageId = id), Gender.MALE, Modifier.size(200.dp, 250.dp), Framing.FULL) }
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Eigenes Ganzkörperbild").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun editorControlsDoNotCoverThePicture() {
        var look by mutableStateOf(CharacterAppearance())
        rule.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { CharacterEditor(look, Gender.MALE, Species.HUMAN, { look = it }, "player") } }
        rule.waitForIdle()
        val bar = rule.onNodeWithTag("framing_bar").getBoundsInRoot()
        val preview = rule.onNodeWithTag("editor_preview").getBoundsInRoot()
        val expression = rule.onNodeWithText("Ruhig").getBoundsInRoot()
        assertTrue("Ansichtsleiste liegt über dem Bild", bar.bottom <= preview.top)
        assertTrue("Ausdruck liegt im Bild", expression.top >= preview.bottom)
        rule.onNodeWithTag("own_image_panel").assertExists()
        rule.onNodeWithTag("upload_portrait").assertHasClickAction()
        rule.onNodeWithTag("upload_fullbody").assertHasClickAction()
    }

    @Test fun requirementsAreReachableFromTheEditorAndNameTheRegistration() {
        var look by mutableStateOf(CharacterAppearance())
        rule.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { CharacterEditor(look, Gender.MALE, Species.HUMAN, { look = it }, "player") } }
        rule.onNodeWithTag("image_help").performScrollTo().performClick()
        rule.onNodeWithTag("image_requirements").assertExists()
        rule.onNodeWithText("Augen auf y = 560", substring = true).assertExists()
        rule.onAllNodesWithText("Ganzkörper").filterToOne(hasAnyAncestor(hasTestTag("image_requirements"))).performClick()
        rule.onNodeWithText("Fußsohlen auf der Linie y = 1480", substring = true).assertExists()
        rule.onNodeWithText("Verstanden").performClick()
        rule.onNodeWithTag("image_requirements").assertDoesNotExist()
    }

    @Test fun rapidFramingSwitchesEndInTheLastChosenView() {
        var look by mutableStateOf(CharacterAppearance())
        rule.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { CharacterEditor(look, Gender.MALE, Species.HUMAN, { look = it }, "player") } }
        repeat(3) {
            rule.onNodeWithTag("framing_full").performClick()
            rule.onNodeWithTag("framing_head").performClick()
            rule.onNodeWithTag("framing_bust").performClick()
        }
        rule.onNodeWithTag("framing_full").performClick()
        rule.waitUntil(10_000) { ArtLoading.pending.get() == 0 && rule.onAllNodesWithContentDescription("Ganzkörperfigur").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithContentDescription("Bild wird geladen").fetchSemanticsNodes().isEmpty())
    }

    @Test fun changingTheLookWhileLoadingShowsTheNewestLook() {
        var look by mutableStateOf(CharacterAppearance(hairColor = 0))
        rule.setContent { CharacterPortrait(look, Gender.MALE, Modifier.size(200.dp, 250.dp), identity = "player") }
        repeat(6) { look = look.copy(hairColor = (look.hairColor + 1) % 8, hairStyle = it % 7) }
        rule.waitUntil(10_000) { ArtLoading.pending.get() == 0 }
        rule.waitForIdle()
        // The cache now holds exactly the newest look; nothing older can overwrite it.
        assertNotNull(PortraitAssets.cachedLayersFor(instrumentation.targetContext, look.normalized(), Gender.MALE, 
            (200 * instrumentation.targetContext.resources.displayMetrics.density).toInt().coerceIn(32, 1024)))
        rule.onNodeWithContentDescription("Porträt").assertExists()
    }

    @Test fun imageSetsAreManagedPerKind() {
        val id = installPortrait(withBody = true)
        val withoutBody = images.derive(id, CharacterImageKind.FULLBODY, null)!!
        assertTrue(images.has(withoutBody, CharacterImageKind.PORTRAIT))
        assertFalse(images.has(withoutBody, CharacterImageKind.FULLBODY))
        val bodyOnly = images.derive(id, CharacterImageKind.PORTRAIT, null)!!
        assertFalse(images.has(bodyOnly, CharacterImageKind.PORTRAIT))
        assertTrue(images.has(bodyOnly, CharacterImageKind.FULLBODY))
        assertNull(images.derive(withoutBody, CharacterImageKind.PORTRAIT, null))
        // The original stays available for "neu ausrichten" and the old set is never modified.
        assertNotNull(images.original(withoutBody, CharacterImageKind.PORTRAIT))
        assertTrue(images.has(id, CharacterImageKind.FULLBODY))
        assertTrue(images.library().any { it.id == id && it.hasPortrait && it.hasFullbody })
        val copy = images.copy(id)
        assertNotEquals(id, copy)
        assertTrue(images.has(copy, CharacterImageKind.FULLBODY))
        assertEquals("Testbild CC0", images.meta(copy)?.source)
    }

    @Test fun oversizedOrOpaqueFullBodyIsRejectedWithAnExplanation() {
        val tooBig = ByteArray(CustomCharacterImages.MAX_BYTES + 1)
        try { images.decode(tooBig, CharacterImageKind.PORTRAIT); fail() } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("15 MB")) }
        val opaque = instrumentation.context.assets.open("adult-portrait.png").use { it.readBytes() }
        try { images.decode(opaque, CharacterImageKind.FULLBODY); fail() } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Hintergrund"))
        }
    }
}
