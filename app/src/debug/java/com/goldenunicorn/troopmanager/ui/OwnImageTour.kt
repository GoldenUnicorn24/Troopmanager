package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class OwnImageFixture(val portraitOnly: String, val both: String, val portrait: Bitmap)

/** Debug-only screens of the new editor with the CC0 test images supplied by the test APK. */
@Composable
internal fun OwnImageTour(page: String) {
    val context = LocalContext.current
    var fixture by remember { mutableStateOf<OwnImageFixture?>(null) }
    LaunchedEffect(Unit) {
        fixture = withContext(Dispatchers.IO) {
            val bytes = TourActivity.customPortraitBytes ?: return@withContext null
            val images = CustomCharacterImages(context)
            val decoded = images.decode(bytes, CharacterImageKind.PORTRAIT)
            val portrait = images.crop(decoded, CharacterImageKind.PORTRAIT, CharacterImageCrop())
            val body = TourActivity.customFullbodyBytes?.let { images.decode(it, CharacterImageKind.FULLBODY) }
                ?.let { images.crop(it, CharacterImageKind.FULLBODY, CharacterImageCrop()) }
            val portraitOnly = images.install(portrait, null, "Selbst erstellte geometrische Testfigur, CC0-1.0",
                mapOf(CharacterImageKind.PORTRAIT to (decoded to CharacterImageCrop())))
            val both = images.install(portrait, body, "Selbst erstellte geometrische Testfigur, CC0-1.0")
            OwnImageFixture(portraitOnly, both, decoded)
        }
    }
    val ready = fixture
    SideEffect { TourActivity.customReady = ready != null }
    if (ready == null) { Text("Testbild wird intern importiert …", color = Gold); return }
    Box(Modifier.fillMaxSize().background(Ink).testTag("own_image_tour")) {
        val framing = Framing.valueOf(page.substringAfterLast('-').uppercase())
        when {
            page.startsWith("wizard-own-") -> NewGameWizard({}, {}, initialStep = 2,
                initialLook = defaultLook(Gender.MALE, Species.HUMAN).copy(customImageId = ready.portraitOnly), initialFraming = framing)
            page.startsWith("wizard-ownbody-") -> NewGameWizard({}, {}, initialStep = 2,
                initialLook = defaultLook(Gender.MALE, Species.HUMAN).copy(customImageId = ready.both), initialFraming = framing)
            page.startsWith("crop-") -> CharacterCropEditor(ready.portrait, CharacterImageKind.PORTRAIT, {}, {}, requireRights = true, onHelp = {})
            else -> Unit
        }
    }
}

/** The companion editor exactly as in step 5 of a new game. */
@Composable
internal fun CompanionEditorTour(framing: Framing) {
    var look by remember { mutableStateOf(defaultLook(Gender.FEMALE, Species.HALF_ELF)) }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SectionTitle("Ihr Aussehen")
        CharacterEditor(look, Gender.FEMALE, Species.HALF_ELF, { look = it }, "companion", initialFraming = framing)
    }
}
