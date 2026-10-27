package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goldenunicorn.troopmanager.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the editor shows about the current own image set; read from disk once per id. */
internal data class OwnImageStatus(val portrait: Boolean, val fullbody: Boolean)

@Composable
internal fun rememberOwnImageStatus(id: String?): OwnImageStatus? {
    val context = LocalContext.current
    var status by remember(id) { mutableStateOf<OwnImageStatus?>(if (id == null) OwnImageStatus(false, false) else null) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        status = withContext(Dispatchers.IO) {
            val images = CustomCharacterImages(context)
            OwnImageStatus(images.has(id, CharacterImageKind.PORTRAIT), images.has(id, CharacterImageKind.FULLBODY))
        }
    }
    return status
}

/** File facts needed by the engine when an edited look with an own image is saved. */
internal fun ownImageFiles(context: android.content.Context, id: String?): com.goldenunicorn.troopmanager.engine.CustomCharacterEngine.ImageFiles? {
    if (id == null) return null
    val images = CustomCharacterImages(context)
    return com.goldenunicorn.troopmanager.engine.CustomCharacterEngine.ImageFiles(images.has(id, CharacterImageKind.PORTRAIT),
        images.has(id, CharacterImageKind.FULLBODY), images.meta(id)?.source ?: CustomCharacterImages.DEFAULT_SOURCE)
}

private data class CropRequest(val kind: CharacterImageKind, val bitmap: Bitmap, val crop: CharacterImageCrop, val fromLibrary: Boolean)

/**
 * „Eigenes Bild verwenden“: upload, re-align, replace and remove the portrait and the full-body
 * picture separately, pick an already imported set, and open the picture requirements. Works for
 * every editor (player, future companion) without a running game: the result is an image id on
 * the appearance; the game registers it when the character is saved.
 */
@Composable
internal fun OwnImagePanel(imageId: String?, onImageChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = remember(context) { CustomCharacterImages(context) }
    val status = rememberOwnImageStatus(imageId)
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var pickKind by remember { mutableStateOf(CharacterImageKind.PORTRAIT) }
    var request by remember { mutableStateOf<CropRequest?>(null) }
    var showHelp by remember { mutableStateOf<CharacterImageKind?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    val latestId by rememberUpdatedState(imageId)

    fun launchTask(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { block(); problem = null }
            catch (e: Exception) { problem = e.message ?: "Das Bild konnte nicht verarbeitet werden." }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) launchTask {
            val kind = pickKind
            val bitmap = withContext(Dispatchers.IO) { images.read(uri, kind) }
            request = CropRequest(kind, bitmap, CharacterImageCrop(), fromLibrary = false)
        }
    }
    fun pick(kind: CharacterImageKind) { pickKind = kind; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    fun realign(kind: CharacterImageKind) = launchTask {
        val id = latestId ?: return@launchTask
        val original = withContext(Dispatchers.IO) { images.original(id, kind) } ?: throw IllegalStateException("Das gespeicherte Bild ist nicht mehr lesbar.")
        request = CropRequest(kind, original.first, original.second, fromLibrary = true)
    }
    fun remove(kind: CharacterImageKind) = launchTask {
        val next = withContext(Dispatchers.IO) { images.derive(latestId, kind, null) }
        onImageChange(next)
    }

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Steel.copy(alpha = .55f))
        .border(1.dp, Gold.copy(alpha = .45f), RoundedCornerShape(20.dp)).padding(14.dp).testTag("own_image_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Eigenes Bild verwenden", color = PaleGold, fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Dein Foto ersetzt das erzeugte Gesicht überall im Spiel. Alles bleibt auf diesem Gerät.", color = Mist, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        TextButton(onClick = { showHelp = CharacterImageKind.PORTRAIT }, contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.testTag("image_help")) { Text("ⓘ  Welches Bild benötige ich?", color = Gold, fontWeight = FontWeight.SemiBold) }
        OwnImageRow("Porträt", "1024 × 1280 · JPG, PNG oder WebP", status?.portrait == true, busy, "portrait",
            onUpload = { pick(CharacterImageKind.PORTRAIT) }, onRealign = { realign(CharacterImageKind.PORTRAIT) },
            onRemove = { remove(CharacterImageKind.PORTRAIT) })
        OwnImageRow("Ganzkörper", "768 × 1536 · PNG/WebP mit transparentem Hintergrund", status?.fullbody == true, busy, "fullbody",
            onUpload = { pick(CharacterImageKind.FULLBODY) }, onRealign = { realign(CharacterImageKind.FULLBODY) },
            onRemove = { remove(CharacterImageKind.FULLBODY) })
        RbAction("Bereits importierte Bilder wählen", enabled = !busy, modifier = Modifier.fillMaxWidth()) { showLibrary = true }
        if (imageId != null) RbAction("Standarddarstellung wiederherstellen", enabled = !busy, modifier = Modifier.fillMaxWidth()) { onImageChange(null) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
        problem?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.testTag("own_image_error")) }
    }

    request?.let { r ->
        Dialog(onDismissRequest = { if (!busy) request = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            CharacterCropEditor(r.bitmap, r.kind, initialCrop = r.crop, requireRights = !r.fromLibrary, busy = busy, error = problem,
                onCancel = { request = null; problem = null },
                onHelp = { showHelp = r.kind },
                onSave = { crop -> launchTask {
                    val next = withContext(Dispatchers.Default) {
                        val result = images.crop(r.bitmap, r.kind, crop)
                        try { images.derive(latestId, r.kind, result, r.bitmap to crop) } finally { result.recycle() }
                    }
                    request = null
                    onImageChange(next)
                } })
        }
    }
    showHelp?.let { kind -> ImageRequirementsDialog(kind) { showHelp = null } }
    if (showLibrary) ImageLibraryDialog(onDismiss = { showLibrary = false }) { picked ->
        showLibrary = false
        launchTask { onImageChange(withContext(Dispatchers.IO) { images.copy(picked) }) }
    }
}

@Composable
private fun OwnImageRow(title: String, spec: String, present: Boolean, busy: Boolean, tag: String,
                        onUpload: () -> Unit, onRealign: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.copy(alpha = .45f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (present) "✓ eigenes Bild" else "Standard", color = if (present) Success else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(spec, color = Muted, fontSize = 11.sp)
        Button(onClick = onUpload, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("upload_$tag"),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
            Text(if (present) "$title ersetzen" else "$title hochladen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (present) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRealign, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = .4f)), contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Neu ausrichten", color = PaleGold, fontSize = 13.sp, maxLines = 1)
            }
            OutlinedButton(onClick = onRemove, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                border = BorderStroke(1.dp, Danger.copy(alpha = .4f)), contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("Entfernen", color = Danger, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------ library

@Composable
internal fun ImageLibraryDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var sets by remember { mutableStateOf<List<CustomImageSet>?>(null) }
    LaunchedEffect(Unit) { sets = withContext(Dispatchers.IO) { CustomCharacterImages(context).library() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Bereits importierte Bilder", color = PaleGold, fontFamily = Cinzel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = sets
                when {
                    list == null -> CircularProgressIndicator(color = Gold)
                    list.isEmpty() -> Text("Noch keine eigenen Bilder auf diesem Gerät.", color = Muted)
                    else -> {
                        Text("Eine Auswahl wird als eigene Kopie übernommen; andere Personen behalten ihr Bild.", color = Muted, fontSize = 12.sp)
                        list.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { set ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(set.id) }.padding(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        PersonImage(set.id, null, if (set.hasPortrait) Framing.BUST else Framing.FULL,
                                            Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(10.dp)), useDefinitionFallback = false) { }
                                        Text(listOfNotNull("Porträt".takeIf { set.hasPortrait }, "Ganzkörper".takeIf { set.hasFullbody }).joinToString(" + "),
                                            color = Mist, fontSize = 10.sp, textAlign = TextAlign.Center)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen", color = Gold) } },
    )
}

// ------------------------------------------------------------ requirements

internal object ImagePrompts {
    const val PORTRAIT = "Photorealistic medieval fantasy portrait of one adult person (age 25–40), front view, looking straight into the camera, " +
        "neutral relaxed expression, head and both shoulders fully visible, eyes level at 44 % of the image height, " +
        "shoulders at 82 % of the image height, face centered horizontally, soft even light from the upper left, " +
        "natural skin texture, realistic hairline, simple dark blurred background, period clothing (linen, leather or wool), " +
        "no helmet, no hands or objects in front of the face, sharp focus, no text, no watermark, no frame. " +
        "Portrait format 4:5, 1024 × 1280 pixels."
    const val FULLBODY = "Photorealistic full-body image of one adult person in medieval fantasy clothing, standing upright, " +
        "front view, arms relaxed slightly away from the body, the whole figure visible from the top of the head to both feet, " +
        "feet flat on the ground at 96 % of the image height, body centered horizontally, head inside the top 12 % margin, " +
        "even soft light from the upper left, realistic anatomy and proportions, no cropped hands or feet, " +
        "isolated subject on a fully transparent background (PNG with alpha channel), no shadow box, no text, no watermark. " +
        "Format 1:2, 768 × 1536 pixels."
}

/** „Welches Bild benötige ich?“ – exact requirements with the registration mask. */
@Composable
internal fun ImageRequirementsDialog(initial: CharacterImageKind = CharacterImageKind.PORTRAIT, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImageRequirementsContent(initial, onDismiss, Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f))
    }
}

/** Body of „Welches Bild benötige ich?“; also shown directly in screenshot tours. */
@Composable
internal fun ImageRequirementsContent(initial: CharacterImageKind, onDismiss: () -> Unit, modifier: Modifier) {
    var kind by remember { mutableStateOf(initial) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    run {
        Column(modifier.clip(RoundedCornerShape(22.dp)).background(Panel)
            .border(1.dp, Gold.copy(alpha = .3f), RoundedCornerShape(22.dp)).padding(16.dp).testTag("image_requirements"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Welches Bild benötige ich?", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RbChip("Porträt", kind == CharacterImageKind.PORTRAIT) { kind = CharacterImageKind.PORTRAIT; copied = false }
                RbChip("Ganzkörper", kind == CharacterImageKind.FULLBODY) { kind = CharacterImageKind.FULLBODY; copied = false }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RegistrationGuide(kind, Modifier.width(if (kind == CharacterImageKind.PORTRAIT) 136.dp else 96.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (kind == CharacterImageKind.PORTRAIT) {
                            Spec("Empfohlen", "1024 × 1280 Pixel (Hochformat 4:5)")
                            Spec("Mindestens", "512 × 640 Pixel")
                            Spec("Dateityp", "JPG, PNG oder WebP, höchstens 15 MB")
                            Spec("Ausrichtung", "Augen auf y = 560, Schultern auf y = 1050, Gesicht mittig (x = 512)")
                        } else {
                            Spec("Empfohlen", "768 × 1536 Pixel (Hochformat 1:2)")
                            Spec("Mindestens", "384 × 768 Pixel")
                            Spec("Dateityp", "PNG oder WebP mit echtem transparentem Hintergrund, höchstens 15 MB")
                            Spec("Ausrichtung", "Körpermitte auf x = 384, Fußsohlen auf der Linie y = 1480")
                        }
                    }
                }
                SectionTitle("So sollte das Bild aussehen")
                (if (kind == CharacterImageKind.PORTRAIT) listOf(
                    "Eine erwachsene Person – du selbst oder jemand, der zugestimmt hat.",
                    "Frontal oder fast frontal, Blick in die Kamera.",
                    "Gesicht vollständig sichtbar, möglichst neutraler Gesichtsausdruck.",
                    "Kopf und beide Schultern vollständig im Bild.",
                    "Gleichmäßiges, weiches Licht; keine harten Schatten im Gesicht.",
                    "Nichts vor dem Gesicht: keine Hand, kein Glas, keine Sonnenbrille.",
                    "Scharf und ohne starke Weitwinkel-Verzerrung (nicht zu nah fotografieren).",
                    "Hintergrund beliebig – er wird mitgezeigt, ruhige dunkle Töne passen am besten.",
                ) else listOf(
                    "Eine vollständige erwachsene Person, aufrecht stehend und frontal.",
                    "Vom Scheitel bis zu den Füßen sichtbar, beide Füße erkennbar.",
                    "Arme leicht vom Körper entfernt, keine abgeschnittenen Hände oder Füße.",
                    "Natürliche Körperproportionen, keine starke Perspektive von oben oder unten.",
                    "Der Hintergrund muss transparent sein (freigestellt). Ein normales Foto mit Hintergrund wird abgelehnt – " +
                        "stelle es vorher mit einer Freistellungs-App frei und speichere es als PNG oder WebP.",
                    "Die App stellt nicht automatisch frei und erfindet keine Teile des Körpers.",
                    "Das Bild behält seine Pose: Ein Foto ist kein 3D-Modell und wird beim Gehen nicht animiert.",
                )).forEach { Text("•  $it", color = Mist, fontSize = 13.sp, lineHeight = 18.sp) }
                SectionTitle("Nach dem Hochladen")
                Text("Im Zuschnitt kannst du mit zwei Fingern verschieben, zoomen und drehen oder die Knöpfe benutzen. " +
                    (if (kind == CharacterImageKind.PORTRAIT) "Lege die Augen auf die goldene Augenlinie und die Schultern auf die untere Linie; das Bild muss den Rahmen ganz füllen."
                    else "Stelle die Füße auf die goldene Linie und die Körpermitte auf die senkrechte Linie.") +
                    " Die App erzeugt daraus das interne Format und speichert es dauerhaft auf dem Gerät.", color = Mist, fontSize = 13.sp, lineHeight = 18.sp)
                if (kind == CharacterImageKind.PORTRAIT) Text("Ohne eigenes Ganzkörperbild zeigt die Ganzkörperansicht eine Standardfigur mit erzeugtem Gesicht – sie gibt dein Foto nicht wieder.",
                    color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                SectionTitle("Vorlage für einen Bildgenerator")
                val prompt = if (kind == CharacterImageKind.PORTRAIT) ImagePrompts.PORTRAIT else ImagePrompts.FULLBODY
                Text(prompt, color = Mist, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ink).padding(10.dp))
                RbAction(if (copied) "Kopiert ✓" else "Vorlage kopieren") { clipboard.setText(AnnotatedString(prompt)); copied = true }
                Text("Nur eigene oder ausdrücklich erlaubte Bilder verwenden. Keine Bilder Minderjähriger. Der einfache JSON-Spielstandexport " +
                    "enthält keine Bilddateien; Charakterpakete (.zip) enthalten sie.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                Text("Verstanden", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Spec(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** The registration mask drawn in standard-image coordinates. */
@Composable
internal fun RegistrationGuide(kind: CharacterImageKind, modifier: Modifier) {
    Canvas(modifier.aspectRatio(kind.width.toFloat() / kind.height).clip(RoundedCornerShape(8.dp)).background(Steel)) {
        drawGuides(kind, size.width / kind.width, strong = true)
    }
}

private fun DrawScope.drawGuides(kind: CharacterImageKind, factor: Float, strong: Boolean) {
    val line = 1.5.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    val alpha = if (strong) 1f else .85f
    drawLine(Color.White.copy(alpha = .55f * alpha), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx(), pathEffect = dash)
    if (kind == CharacterImageKind.PORTRAIT) {
        drawOval(Gold.copy(alpha = alpha), Offset(240f * factor, 210f * factor), Size(544f * factor, 680f * factor), style = Stroke(line))
        drawLine(Gold.copy(alpha = alpha), Offset(0f, CharacterImageFrames.EYE_Y * factor), Offset(size.width, CharacterImageFrames.EYE_Y * factor), line)
        drawLine(Gold.copy(alpha = .7f * alpha), Offset(0f, CharacterImageFrames.SHOULDER_Y * factor), Offset(size.width, CharacterImageFrames.SHOULDER_Y * factor), line, pathEffect = dash)
        for (x in listOf(417f, 607f)) drawCircle(Gold.copy(alpha = alpha), 3.dp.toPx(), Offset(x * factor, CharacterImageFrames.EYE_Y * factor), style = Stroke(line))
    } else {
        drawLine(Gold.copy(alpha = alpha), Offset(0f, CharacterImageFrames.FOOT_Y * factor), Offset(size.width, CharacterImageFrames.FOOT_Y * factor), line)
        drawLine(Gold.copy(alpha = .6f * alpha), Offset(0f, 60f * factor), Offset(size.width, 60f * factor), line, pathEffect = dash)
        // Simple standing silhouette for orientation.
        val c = CharacterImageFrames.BODY_CENTER_X * factor
        drawCircle(Gold.copy(alpha = .35f * alpha), 82f * factor, Offset(c, 180f * factor), style = Stroke(line))
        drawLine(Gold.copy(alpha = .35f * alpha), Offset(c, 262f * factor), Offset(c, 860f * factor), line)
        drawLine(Gold.copy(alpha = .35f * alpha), Offset(c, 860f * factor), Offset(c - 90f * factor, CharacterImageFrames.FOOT_Y * factor), line)
        drawLine(Gold.copy(alpha = .35f * alpha), Offset(c, 860f * factor), Offset(c + 90f * factor, CharacterImageFrames.FOOT_Y * factor), line)
        drawLine(Gold.copy(alpha = .35f * alpha), Offset(c - 200f * factor, 820f * factor), Offset(c, 360f * factor), line)
        drawLine(Gold.copy(alpha = .35f * alpha), Offset(c + 200f * factor, 820f * factor), Offset(c, 360f * factor), line)
    }
}

// ------------------------------------------------------------ crop

/**
 * Touch crop: drag, pinch-zoom and two-finger rotation, plus buttons for zoom, quarter turns,
 * guides, undo, reset and confirm. The canvas shows exactly the raster that will be saved.
 */
@Composable
internal fun CharacterCropEditor(
    bitmap: Bitmap,
    kind: CharacterImageKind,
    onCancel: () -> Unit,
    onSave: (CharacterImageCrop) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    initialCrop: CharacterImageCrop = CharacterImageCrop(),
    requireRights: Boolean = false,
    onHelp: (() -> Unit)? = null,
) {
    var crop by remember(bitmap) { mutableStateOf(initialCrop) }
    val history = remember(bitmap) { mutableStateListOf<CharacterImageCrop>() }
    var guides by remember { mutableStateOf(true) }
    var sliding by remember { mutableStateOf(false) }
    var rights by remember(bitmap) { mutableStateOf(!requireRights) }
    val latestCrop by rememberUpdatedState(crop)
    val currentBusy by rememberUpdatedState(busy)
    fun commit(next: CharacterImageCrop) {
        val bounded = next.copy(zoom = next.zoom.coerceIn(CharacterImageCrop.MIN_ZOOM, CharacterImageCrop.MAX_ZOOM),
            x = next.x.coerceIn(-kind.width * 2f, kind.width * 2f), y = next.y.coerceIn(-kind.height * 2f, kind.height * 2f))
        if (bounded == crop) return
        history += crop
        if (history.size > 50) history.removeAt(0)
        crop = bounded
    }
    val covers = kind != CharacterImageKind.PORTRAIT || crop.coversFrame(bitmap.width, bitmap.height, kind)
    BackHandler(enabled = !busy) { onCancel() }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).testTag("character_crop"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (kind == CharacterImageKind.PORTRAIT) "Porträt ausrichten" else "Ganzkörperbild ausrichten", color = PaleGold, fontFamily = Cinzel, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(if (kind == CharacterImageKind.PORTRAIT) "Augen auf die goldene Linie, Schultern auf die gestrichelte, Gesicht im Oval."
                    else "Füße auf die goldene Linie, Körpermitte auf die senkrechte Linie.", color = Mist, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (onHelp != null) TextButton(onClick = onHelp) { Text("ⓘ Hilfe", color = Gold) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(kind.width.toFloat() / kind.height, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp)).background(Steel).clipToBounds().testTag("crop_canvas")
                .pointerInput(bitmap, kind) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var started = false
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val rotation = event.calculateRotation()
                            if (!currentBusy && (zoom != 1f || pan != Offset.Zero || rotation != 0f)) {
                                if (!started) { history += latestCrop; if (history.size > 50) history.removeAt(0); started = true }
                                val unit = kind.width / size.width.toFloat()
                                val c = latestCrop
                                crop = c.copy(zoom = (c.zoom * zoom).coerceIn(CharacterImageCrop.MIN_ZOOM, CharacterImageCrop.MAX_ZOOM),
                                    x = (c.x + pan.x * unit).coerceIn(-kind.width * 2f, kind.width * 2f),
                                    y = (c.y + pan.y * unit).coerceIn(-kind.height * 2f, kind.height * 2f)).turned(rotation)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }) {
                val factor = size.width / kind.width
                if (kind == CharacterImageKind.FULLBODY) {
                    // Checkerboard shows which parts are transparent in the saved figure.
                    val cell = 12.dp.toPx()
                    var y = 0f; var row = 0
                    while (y < size.height) {
                        var x = if (row % 2 == 0) 0f else cell
                        while (x < size.width) { drawRect(Color.White.copy(alpha = .05f), Offset(x, y), Size(cell, cell)); x += cell * 2 }
                        y += cell; row++
                    }
                }
                drawContext.canvas.nativeCanvas.apply {
                    val save = save()
                    scale(factor, factor)
                    translate(kind.width / 2f + crop.x, kind.height / 2f + crop.y)
                    rotate(crop.rotation)
                    val s = crop.scale(bitmap.width, bitmap.height, kind)
                    scale(s, s)
                    drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
                    restoreToCount(save)
                }
                if (guides) drawGuides(kind, factor, strong = false)
            }
        }
        if (!covers) Text("Das Porträt füllt den Rahmen noch nicht ganz.", color = Danger, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RbChip("−", false) { commit(crop.copy(zoom = crop.zoom / 1.12f)) }
            RbChip("+", false) { commit(crop.copy(zoom = crop.zoom * 1.12f)) }
            RbChip("↺ 90°", false) { commit(crop.turned(-90f)) }
            RbChip("↻ 90°", false) { commit(crop.turned(90f)) }
            RbChip("Hilfslinien", guides) { guides = !guides }
            if (!covers) RbChip("Rahmen füllen", false) { commit(crop.copy(zoom = crop.coveringZoom(bitmap.width, bitmap.height, kind))) }
        }
        Text("Feinausrichtung: Drehen ${"%.0f".format(crop.rotation)}°", color = Muted, fontSize = 11.sp)
        Slider(crop.rotation, { if (!sliding) { history += crop; sliding = true }; crop = crop.copy(rotation = it) }, valueRange = -180f..180f, enabled = !busy,
            onValueChangeFinished = { sliding = false },
            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color.White.copy(alpha = .15f)))
        if (requireRights) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rights = !rights }) {
            Checkbox(rights, { rights = it }, modifier = Modifier.testTag("crop_rights"))
            Text("Ich habe die Rechte an diesem Bild; alle abgebildeten Personen sind erwachsen.", color = Mist, fontSize = 12.sp, lineHeight = 16.sp)
        }
        error?.let { Text(it, color = Danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (history.isNotEmpty()) crop = history.removeAt(history.lastIndex) }, enabled = !busy && history.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp)) { Text("Rückgängig", color = PaleGold, maxLines = 1) }
            OutlinedButton(onClick = { commit(CharacterImageCrop()) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 46.dp)) {
                Text("Zurücksetzen", color = PaleGold, maxLines = 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Abbrechen", color = Mist, maxLines = 1) }
            Button(onClick = { onSave(crop) }, enabled = !busy && covers && rights, modifier = Modifier.weight(1.4f).heightIn(min = 52.dp).testTag("crop_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp) else Text("Übernehmen", fontWeight = FontWeight.Black)
            }
        }
    }
}
