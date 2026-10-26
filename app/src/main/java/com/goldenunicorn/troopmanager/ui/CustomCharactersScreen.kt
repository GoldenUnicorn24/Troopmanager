package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.engine.CustomCharacterEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal val LocalOpenCustomCharacters = staticCompositionLocalOf<((String?) -> Unit)?> { null }
internal val LocalCustomCharacterDefinitions = staticCompositionLocalOf<Map<String, CustomCharacterDefinition>> { emptyMap() }

@Composable
internal fun CustomCharactersHost(state: GameState?, onState: (GameState) -> Unit, content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    val definitions = remember(state?.customCharacters) { state?.customCharacters?.associate { it.id to it.definition }.orEmpty() }
    CompositionLocalProvider(LocalCustomCharacterDefinitions provides definitions,
        LocalOpenCustomCharacters provides if (state == null) null else { value -> target = value; open = true }) { content() }
    if (open && state != null) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CustomCharactersScreen(state, onState, { open = false }, target)
    }
}

@Composable
fun CustomCharactersScreen(state: GameState, onState: (GameState) -> Unit, onBack: () -> Unit, initialTarget: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = remember(context) { CustomCharacterImages(context) }
    var target by remember { mutableStateOf(initialTarget ?: "new") }
    val choices = remember(state) { listOf("new" to "Neue Person", "player" to state.player.name, "companion" to state.companion.name) +
        state.life.npcs.map { "npc:${it.id}" to it.name } + state.commanders.map { "commander:${it.id}" to it.name } }
    fun initialDefinition(): CustomCharacterDefinition {
        state.customCharacters.firstOrNull { it.target == target }?.let { return it.definition }
        state.life.npcs.firstOrNull { "npc:${it.id}" == target }?.let { return CustomCharacterEngine.definition(it) }
        return when (target) {
            "player" -> CustomCharacterDefinition(state.player.name, state.player.age, state.life.gender, state.player.species, state.life.culture,
                state.life.origin, state.life.profession, appearance = state.life.appearance ?: CharacterAppearance())
            "companion" -> CustomCharacterDefinition(state.companion.name, state.companion.age, species = state.companion.species, role = NpcRole.COMPANION)
            else -> CustomCharacterDefinition(state.commanders.firstOrNull { "commander:${it.id}" == target }?.name ?: "", 18)
        }
    }
    var definition by remember(target) { mutableStateOf(initialDefinition()) }
    var age by remember(target) { mutableStateOf(if (target == "new" || target.startsWith("commander:")) "" else definition.age.toString()) }
    var interests by remember(target) { mutableStateOf(initialDefinition().interests.joinToString(", ")) }
    var showExports by remember { mutableStateOf(false) }
    var pendingPack by remember { mutableStateOf<ImportedCharacterPack?>(null) }
    var source by remember(target) { mutableStateOf(state.customCharacters.firstOrNull { it.target == target }?.source ?: "") }
    var portrait by remember(target) { mutableStateOf<Bitmap?>(null) }
    var fullbody by remember(target) { mutableStateOf<Bitmap?>(null) }
    var cropInput by remember { mutableStateOf<Bitmap?>(null) }
    var kind by remember { mutableStateOf(CharacterImageKind.PORTRAIT) }
    var rights by remember(target) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestTarget by rememberUpdatedState(target)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val requestedTarget = target
            busy = true
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) { images.read(uri, kind) }
                    if (latestTarget == requestedTarget) { cropInput = bitmap; error = null } else bitmap.recycle()
                } catch (e: Exception) { error = e.message ?: "Das Bild konnte nicht geladen werden." }
                finally { busy = false }
            }
        }
    }
    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { CharacterPackSharing(context).read(uri) }
                    pendingPack = imported
                    error = null
                } catch (e: Exception) { error = e.message ?: "Das Charakterpaket ist ungültig." }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(pendingPack, target) {
        val imported = pendingPack
        if (imported != null) {
            definition = imported.manifest.character
            age = definition.age.toString()
            source = imported.manifest.source
            interests = definition.interests.joinToString(", ")
            portrait = imported.portrait
            fullbody = imported.fullbody
            rights = false
            pendingPack = null
        }
    }
    val cropBitmap = cropInput
    if (cropBitmap != null) {
        CharacterCropEditor(cropBitmap, kind, onCancel = { cropInput = null }, onSave = { crop ->
            busy = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) { images.crop(cropBitmap, kind, crop) }
                    if (kind == CharacterImageKind.PORTRAIT) portrait = result else fullbody = result
                    cropInput = null
                    error = null
                } catch (e: Exception) { error = e.message }
                finally { busy = false }
            }
        }, busy = busy, error = error)
        return
    }
    BackHandler(enabled = !busy) { onBack() }
    val scroll = rememberScrollState()
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(0) }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().verticalScroll(scroll).padding(16.dp).testTag("custom_characters"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Eigene Charaktere", style = MaterialTheme.typography.headlineSmall, color = PaleGold)
        Text("Nur eigene oder erlaubte Bilder verwenden. Keine Bilder Minderjähriger als erwachsene Figuren. Alles bleibt auf diesem Gerät.", color = Mist)
        error?.let { Text(it, color = Danger, modifier = Modifier.testTag("custom_error")) }
        TextButton(onClick = onBack, enabled = !busy) { Text("Zurück") }
        OutlinedButton(enabled = !busy, onClick = { packagePicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Charakterpaket importieren (.zip)") }
        if (state.customCharacters.isNotEmpty()) {
            TextButton(onClick = { showExports = !showExports }) { Text(if (showExports) "Paketliste schließen" else "Eigene Charakterpakete teilen") }
            if (showExports) state.customCharacters.forEach { record ->
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val intent = withContext(Dispatchers.IO) { CharacterPackSharing(context).prepareShare(record) }
                            context.startActivity(android.content.Intent.createChooser(intent, "Charakterpaket teilen"))
                        } catch (e: Exception) { error = e.message ?: "Das Paket konnte nicht exportiert werden." }
                        finally { busy = false }
                    }
                }) { Text("${record.definition.name} teilen") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (key, label) -> FilterChip(target == key, { if (!busy) target = key }, label = { Text(label) }) }
        }
        OutlinedTextField(definition.name, { definition = definition.copy(name = it) }, label = { Text("Name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(5) }, label = { Text("Alter (Pflicht, mindestens 18)") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        portrait?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "Porträt-Vorschau", Modifier.height(220.dp).aspectRatio(.8f)) }
        fullbody?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "Ganzkörper-Vorschau", Modifier.height(220.dp).aspectRatio(.5f).background(Steel)) }
        OutlinedButton(onClick = { kind = CharacterImageKind.PORTRAIT; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("Porträt wählen und zuschneiden") }
        OutlinedButton(onClick = { kind = CharacterImageKind.FULLBODY; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("Ganzkörper wählen (optional, transparent)") }
        Text("Ohne Ganzkörperbild bleibt die Figur gezeichnet. PNG/WebP mit Transparenz sind für Ganzkörper erforderlich.", color = Muted)
        ChoiceRow("Geschlecht", Gender.entries, definition.gender, { it.label }) { definition = definition.copy(gender = it) }
        ChoiceRow("Volk", Species.entries, definition.species, { it.label }) { definition = definition.copy(species = it) }
        ChoiceRow("Kultur", Culture.entries, definition.culture, { it.label }) { definition = definition.copy(culture = it) }
        OutlinedTextField(definition.origin, { definition = definition.copy(origin = it) }, label = { Text("Herkunft") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(definition.profession, { definition = definition.copy(profession = it) }, label = { Text("Beruf") }, modifier = Modifier.fillMaxWidth())
        ChoiceRow("Rolle", NpcRole.entries, definition.role, { it.label }) { definition = definition.copy(role = it) }
        ChoiceRow("Heimatstadt", Towns.all, Towns.byProvince(definition.homeProvinceId) ?: Towns.all.first(), { CrownCatalog.info(it.provinceId).name }) { definition = definition.copy(homeProvinceId = it.provinceId) }
        OutlinedTextField(interests, { interests = it; definition = definition.copy(interests = it.split(',').map(String::trim).filter(String::isNotBlank)) }, label = { Text("Interessen (mit Komma trennen)") }, modifier = Modifier.fillMaxWidth())
        val p = definition.personality
        listOf(Triple("Herzlichkeit", p.warmth, { v: Int -> p.copy(warmth = v) }), Triple("Wagemut", p.boldness, { v: Int -> p.copy(boldness = v) }),
            Triple("Ehre", p.honor, { v: Int -> p.copy(honor = v) }), Triple("Humor", p.humor, { v: Int -> p.copy(humor = v) }),
            Triple("Neugier", p.curiosity, { v: Int -> p.copy(curiosity = v) }), Triple("Temperament", p.temper, { v: Int -> p.copy(temper = v) })).forEach { (label, value, change) ->
            Text("$label: $value", color = Mist)
            Slider(value.toFloat(), { definition = definition.copy(personality = change(it.toInt())) }, valueRange = 0f..100f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(definition.romanceable, { definition = definition.copy(romanceable = it) }); Text("Romantik offen (keine automatische Beziehung)", color = Mist) }
        OutlinedTextField(source, { source = it }, label = { Text("Bildherkunft / Lizenz") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rights, { rights = it }); Text("Ich habe die Bildrechte; alle abgebildeten Personen sind erwachsen.", color = Mist) }
        if (busy) CircularProgressIndicator()
        Button(enabled = !busy && rights && portrait != null, onClick = {
            val d = definition.copy(age = age.toIntOrNull() ?: 0, appearance = definition.appearance.copy(customImageId = null))
            val validation = runCatching { d.validate(); require(source.isNotBlank() && source.length <= 2000) { "Bitte Bildherkunft und Nutzungsrechte mit höchstens 2000 Zeichen angeben." } }
            if (validation.isFailure) error = validation.exceptionOrNull()?.message else {
                busy = true
                scope.launch {
                    try {
                        val imageId = withContext(Dispatchers.IO) { images.install(portrait!!, fullbody) }
                        val personTarget = if (target == "new") "npc:custom-${UUID.randomUUID()}" else target
                        val record = CustomCharacter(imageId, personTarget, d, source, fullbody = if (fullbody != null) "fullbody.webp" else null)
                        val next = CustomCharacterEngine.apply(state, record, Uri.fromFile(images.file(imageId, CharacterImageKind.PORTRAIT)).toString())
                        onState(next)
                        onBack()
                    } catch (e: Exception) { error = e.message ?: "Der Import ist fehlgeschlagen." }
                    finally { busy = false }
                }
            }
        }) { Text("Charakter speichern") }
    }
}

@Composable
private fun <T> ChoiceRow(label: String, values: List<T>, selected: T, title: (T) -> String, change: (T) -> Unit) {
    Text(label, color = PaleGold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value -> FilterChip(value == selected, { change(value) }, label = { Text(title(value)) }) }
    }
}

@Composable
internal fun CharacterCropEditor(bitmap: Bitmap, kind: CharacterImageKind, onCancel: () -> Unit, onSave: (CharacterImageCrop) -> Unit, busy: Boolean = false, error: String? = null) {
    var crop by remember(bitmap) { mutableStateOf(CharacterImageCrop()) }
    var confirmed by remember(bitmap) { mutableStateOf(false) }
    val latestCrop by rememberUpdatedState(crop)
    val currentBusy by rememberUpdatedState(busy)
    BackHandler(enabled = !busy) { onCancel() }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp).testTag("character_crop"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bild zuschneiden", style = MaterialTheme.typography.headlineSmall, color = PaleGold)
        Text(if (kind == CharacterImageKind.PORTRAIT) "Gesicht im Oval, Augen auf die goldene Linie, Schultern auf die untere Linie ausrichten." else "Körper mittig, Füße auf die goldene Linie ausrichten. Hintergrund muss transparent sein.", color = Mist)
        Canvas(Modifier.widthIn(max = if (kind == CharacterImageKind.PORTRAIT) 352.dp else 220.dp).fillMaxWidth().aspectRatio(kind.width.toFloat() / kind.height).background(Steel).clipToBounds().pointerInput(bitmap, kind) {
            detectTransformGestures { _, pan, zoom, rotation ->
                if (!currentBusy) crop = latestCrop.copy(zoom = (latestCrop.zoom * zoom).coerceIn(1f, 6f), x = latestCrop.x + pan.x * kind.width / size.width,
                    y = latestCrop.y + pan.y * kind.width / size.width, rotation = (latestCrop.rotation + rotation).coerceIn(-180f, 180f))
            }
        }) {
            val factor = size.width / kind.width
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
            val line = if (kind == CharacterImageKind.PORTRAIT) CharacterImageFrames.EYE_Y else CharacterImageFrames.FOOT_Y
            drawLine(Gold, Offset(0f, line * factor), Offset(size.width, line * factor), 2.dp.toPx())
            drawLine(Color.White.copy(alpha = .6f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
            if (kind == CharacterImageKind.PORTRAIT) {
                drawOval(Gold, Offset(240f * factor, 210f * factor), Size(544f * factor, 680f * factor), style = Stroke(2.dp.toPx()))
                drawLine(Gold.copy(alpha = .7f), Offset(0f, 1050f * factor), Offset(size.width, 1050f * factor), 1.dp.toPx())
            }
        }
        Text("Zoom", color = Mist); Slider(crop.zoom, { crop = crop.copy(zoom = it) }, valueRange = 1f..6f, enabled = !busy)
        Text("Drehen", color = Mist); Slider(crop.rotation, { crop = crop.copy(rotation = it) }, valueRange = -180f..180f, enabled = !busy)
        Text("Horizontal verschieben", color = Mist); Slider(crop.x.coerceIn(-1536f, 1536f), { crop = crop.copy(x = it) }, valueRange = -1536f..1536f, enabled = !busy)
        Text("Vertikal verschieben", color = Mist); Slider(crop.y.coerceIn(-2304f, 2304f), { crop = crop.copy(y = it) }, valueRange = -2304f..2304f, enabled = !busy)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirmed, { confirmed = it }); Text("Ausrichtung in der Führungsmaske geprüft", color = Mist) }
        error?.let { Text(it, color = Danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Abbrechen") }
            OutlinedButton(onClick = { crop = CharacterImageCrop(); confirmed = false }, enabled = !busy) { Text("Reset") }
            Button(onClick = { onSave(crop) }, enabled = confirmed && !busy) { Text("Übernehmen") }
        }
    }
}
