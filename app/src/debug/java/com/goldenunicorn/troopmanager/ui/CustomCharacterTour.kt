package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.engine.CustomCharacterEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CustomTourFixture(val state: GameState, val portrait: Bitmap)

/** The test APK supplies its CC0 images; no test photo ships in the release APK. */
@Composable
internal fun CustomCharacterTour(page: String, base: GameState) {
    val context = LocalContext.current
    var fixture by remember { mutableStateOf<CustomTourFixture?>(null) }
    LaunchedEffect(Unit) {
        fixture = withContext(Dispatchers.IO) {
            val bytes = TourActivity.customPortraitBytes ?: return@withContext null
            val images = CustomCharacterImages(context)
            val portrait = images.decode(bytes, CharacterImageKind.PORTRAIT)
            val fullbody = TourActivity.customFullbodyBytes?.let { images.decode(it, CharacterImageKind.FULLBODY) }
            val id = images.install(portrait, fullbody)
            val record = CustomCharacter(id, "npc:tour-custom", CustomCharacterDefinition("Mira Testfigur", 27, role = NpcRole.REGULAR,
                homeProvinceId = base.life.provinceId), "Selbst erstellte geometrische Testfigur, CC0-1.0", fullbody = if (fullbody != null) "fullbody.webp" else null)
            val state = CustomCharacterEngine.apply(base.copy(life = base.life.copy(timeSlot = TimeSlot.EVENING)), record,
                Uri.fromFile(images.file(id, CharacterImageKind.PORTRAIT)).toString())
            fullbody?.recycle()
            CustomTourFixture(state, portrait)
        }
    }
    val ready = fixture
    SideEffect { TourActivity.customReady = ready != null }
    if (ready == null) { Text("Testbild wird intern importiert …", color = Gold); return }
    Box(Modifier.fillMaxSize().testTag("custom_art_ready")) {
        when (page) {
            "custom-import" -> CustomCharactersScreen(ready.state, {}, {})
            "custom-crop" -> CharacterCropEditor(ready.portrait, CharacterImageKind.PORTRAIT, {}, {})
            "custom-tavern" -> TavernScreen(ready.state, {}, {}, {})
            else -> Column(Modifier.fillMaxSize().background(Ink).padding(16.dp)) {
                Text("Gleiche Person: Fallback / internes Bild", color = PaleGold)
                val npc = ready.state.life.npc("tour-custom")!!
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CharacterPortrait(npc.appearance.copy(customImageId = null), npc.gender, Modifier.weight(1f).aspectRatio(.8f))
                    CharacterPortrait(npc.appearance, npc.gender, Modifier.weight(1f).aspectRatio(.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CharacterPortrait(npc.appearance, npc.gender, Modifier.weight(1f).aspectRatio(1f), Framing.HEAD)
                    CharacterPortrait(npc.appearance, npc.gender, Modifier.weight(1f).aspectRatio(.5f), Framing.FULL)
                }
            }
        }
    }
}
