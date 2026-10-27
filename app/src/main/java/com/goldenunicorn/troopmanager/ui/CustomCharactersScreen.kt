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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
    val definitions = remember(state) { state?.customCharacters?.associate { it.id to CustomCharacterEngine.currentDefinition(state,it) }.orEmpty() }
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
    val companionTarget = if (!state.companion.met) state.life.companion?.let { "npc:${it.id}" }
        ?: state.crown.saga.partner?.let { "partner:${it.background}" } ?: "companion" else "companion"
    var target by remember { mutableStateOf(when (initialTarget) {
        "companion" -> companionTarget
        "commander:$COMPANION_COMMANDER_ID" -> if (state.companion.met) "companion" else initialTarget
        else -> initialTarget ?: "new"
    }) }
    val choices = remember(state) { listOf("new" to "Neue Person", "player" to state.player.name, companionTarget to (if (state.companion.met) state.companion.name else state.life.companion?.name ?: state.crown.saga.partner?.name ?: state.companion.name)) +
        state.life.npcs.filter { "npc:${it.id}" != companionTarget }.map { "npc:${it.id}" to "${it.name} · ${CrownCatalog.info(it.homeProvinceId).name}" } + state.commanders.filterNot { it.id == COMPANION_COMMANDER_ID && state.companion.met }.map { "commander:${it.id}" to it.name } }
    fun initialDefinition(): CustomCharacterDefinition {
        val imageId = CustomCharacterEngine.imageIdForTarget(state,target)
        state.customCharacters.firstOrNull { it.target == target || it.id == imageId }?.let { return CustomCharacterEngine.currentDefinition(state,it.copy(target = target)) }
        state.life.npcs.firstOrNull { "npc:${it.id}" == target }?.let { return CustomCharacterEngine.definition(it) }
        state.crown.saga.partner?.takeIf { "partner:${it.background}" == target }?.let {
            return CustomCharacterDefinition(it.name,it.age,species = it.species,role = NpcRole.COMPANION)
        }
        return when (target) {
            "player" -> CustomCharacterDefinition(state.player.name, state.player.age, state.life.gender, state.player.species, state.life.culture,
                state.life.origin, state.life.profession, homeProvinceId = state.life.homeProvinceId, appearance = state.life.appearance ?: CharacterAppearance())
            "companion" -> CustomCharacterDefinition(state.companion.name, state.companion.age, species = state.companion.species, role = NpcRole.COMPANION)
            else -> CustomCharacterDefinition(state.commanders.firstOrNull { "commander:${it.id}" == target }?.name ?: "", 18)
        }
    }
    var definition by remember(target) { mutableStateOf(initialDefinition()) }
    var age by remember(target) { mutableStateOf(if (target == "new" || target.startsWith("commander:")) "" else definition.age.toString()) }
    var interests by remember(target) { mutableStateOf(initialDefinition().interests.joinToString(", ")) }
    var showExports by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var pendingPack by remember { mutableStateOf<ImportedCharacterPack?>(null) }
    val existingImageId = remember(target, state) { CustomCharacterEngine.imageIdForTarget(state,target) }
    var source by remember(target) { mutableStateOf(state.customCharacters.firstOrNull { it.target == target || it.id == CustomCharacterEngine.imageIdForTarget(state,target) }?.source ?: "") }
    var portrait by remember(target) { mutableStateOf<Bitmap?>(null) }
    var fullbody by remember(target) { mutableStateOf<Bitmap?>(null) }
    // Pictures already stored for this person are loaded, so editing a name never requires a new upload.
    LaunchedEffect(target, existingImageId) {
        val id = existingImageId ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            images.load(id, CharacterImageKind.PORTRAIT, CharacterImageKind.PORTRAIT.width) to images.load(id, CharacterImageKind.FULLBODY, CharacterImageKind.FULLBODY.width)
        }
        if (portrait == null) portrait = loaded.first
        if (fullbody == null) fullbody = loaded.second
    }
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
        }, busy = busy, error = error, onHelp = { showHelp = true })
        if (showHelp) ImageRequirementsDialog(kind) { showHelp = false }
        return
    }
    BackHandler(enabled = !busy) { onBack() }
    val scroll = rememberScrollState()
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(0) }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().verticalScroll(scroll).padding(16.dp).testTag("custom_characters"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Charakterverwaltung", style = MaterialTheme.typography.headlineSmall, color = PaleGold)
                Text("Eigene Personen mit eigenen Bildern. Alles bleibt auf diesem Gerät.", color = Mist, fontSize = 13.sp)
            }
            TextButton(onClick = onBack, enabled = !busy) { Text("Schließen", color = Gold) }
        }
        error?.let { Text(it, color = Danger, modifier = Modifier.testTag("custom_error")) }
        if (state.customCharacters.isNotEmpty()) {
            SectionTitle("Deine Personen")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.customCharacters.forEach { record ->
                    val d = CustomCharacterEngine.currentDefinition(state, record)
                    val selected = record.target == target
                    Column(Modifier.width(92.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) Gold.copy(alpha = .18f) else Steel.copy(alpha = .5f))
                        .clickable(enabled = !busy) { target = record.target }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PersonImage(record.id, null, Framing.HEAD, Modifier.size(72.dp).clip(RoundedCornerShape(100.dp)), useDefinitionFallback = true) {
                            CharacterPortrait(d.appearance, d.gender, Modifier.fillMaxSize(), Framing.HEAD)
                        }
                        Text(d.name, color = if (selected) PaleGold else Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (record.target == "player") "Du" else d.role.label, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text("Person wählen", color = PaleGold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (key, label) -> FilterChip(target == key, { if (!busy) target = key }, label = { Text(label) }) }
        }
        PremiumPanel(emphasized = true) {
            Text("Eigenes Bild verwenden", color = PaleGold, fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            TextButton(onClick = { showHelp = true }, contentPadding = PaddingValues(0.dp)) { Text("ⓘ  Welches Bild benötige ich?", color = Gold) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(12.dp)).background(Steel), contentAlignment = Alignment.Center) {
                        portrait?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "Porträt-Vorschau", Modifier.fillMaxSize()) }
                            ?: Text("Kein Porträt", color = Muted, fontSize = 12.sp)
                    }
                    Button(onClick = { kind = CharacterImageKind.PORTRAIT; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text(if (portrait == null) "Porträt hochladen" else "Porträt ersetzen", fontSize = 12.sp) }
                    if (portrait != null) TextButton(onClick = { portrait = null }, enabled = !busy) { Text("Entfernen", color = Danger, fontSize = 12.sp) }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.8f).clip(RoundedCornerShape(12.dp)).background(Steel), contentAlignment = Alignment.Center) {
                        fullbody?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "Ganzkörper-Vorschau", Modifier.fillMaxSize()) }
                            ?: Text("Kein Ganzkörperbild", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    Button(onClick = { kind = CharacterImageKind.FULLBODY; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text(if (fullbody == null) "Ganzkörper hochladen" else "Ganzkörper ersetzen", fontSize = 12.sp) }
                    if (fullbody != null) TextButton(onClick = { fullbody = null }, enabled = !busy) { Text("Entfernen", color = Danger, fontSize = 12.sp) }
                }
            }
            Text("Ohne eigenes Ganzkörperbild zeigt die Ganzkörperansicht eine Standardfigur. Ganzkörperbilder brauchen einen transparenten Hintergrund.", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showLibrary = true }, enabled = !busy) { Text("Importierte Bilder") }
                OutlinedButton(enabled = !busy, onClick = { packagePicker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Paket (.zip)") }
            }
        }
        OutlinedTextField(definition.name, { definition = definition.copy(name = it) }, label = { Text("Name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(age, { age = it.filter(Char::isDigit).take(5) }, label = { Text("Alter (Pflicht, mindestens 18)") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        ChoiceRow("Geschlecht", Gender.entries, definition.gender, { it.label }) { definition = definition.copy(gender = it) }
        ChoiceRow("Volk", Species.entries, definition.species, { it.label }) { definition = definition.copy(species = it) }
        ChoiceRow("Kultur", Culture.entries, definition.culture, { it.label }) { definition = definition.copy(culture = it) }
        OutlinedTextField(definition.origin, { definition = definition.copy(origin = it) }, label = { Text("Herkunft") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(definition.profession, { definition = definition.copy(profession = it) }, label = { Text("Beruf") }, modifier = Modifier.fillMaxWidth())
        ChoiceRow("Rolle in der Welt", NpcRole.entries, definition.role, { it.label }) { definition = definition.copy(role = it) }
        Text("Stammgäste sitzen in der Taverne, Söldner und Ausbilder in der Kaserne, Adlige auf der Burg. Freundschaft oder mehr entsteht nur im Spiel.", color = Muted, fontSize = 12.sp)
        ChoiceRow("Aufenthaltsort", Towns.all, Towns.byProvince(definition.homeProvinceId) ?: Towns.all.first(), { CrownCatalog.info(it.provinceId).name }) { definition = definition.copy(homeProvinceId = it.provinceId) }
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
        Button(enabled = !busy && rights && (portrait != null || fullbody != null), onClick = {
            val d = definition.copy(age = age.toIntOrNull() ?: 0, appearance = definition.appearance.copy(customImageId = null))
            val validation = runCatching { d.validate(); require(source.isNotBlank() && source.length <= 2000) { "Bitte Bildherkunft und Nutzungsrechte mit höchstens 2000 Zeichen angeben." } }
            if (validation.isFailure) error = validation.exceptionOrNull()?.message else {
                busy = true
                scope.launch {
                    try {
                        val imageId = withContext(Dispatchers.IO) { images.install(portrait, fullbody, source) }
                        val personTarget = if (target == "new") "npc:custom-${UUID.randomUUID()}" else target
                        val record = CustomCharacter(imageId, personTarget, d, source, portrait = if (portrait != null) "portrait.webp" else null,
                            fullbody = if (fullbody != null) "fullbody.webp" else null)
                        val next = CustomCharacterEngine.apply(state, record, CustomImageReference.uri(imageId))
                        onState(next)
                        onBack()
                    } catch (e: Exception) { error = e.message ?: "Der Import ist fehlgeschlagen." }
                    finally { busy = false }
                }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Charakter speichern", fontWeight = FontWeight.Bold) }
        if (state.customCharacters.isNotEmpty()) {
            TextButton(onClick = { showExports = !showExports }) { Text(if (showExports) "Paketliste schließen" else "Eigene Charakterpakete teilen") }
            if (showExports) state.customCharacters.forEach { record ->
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val intent = withContext(Dispatchers.IO) { CharacterPackSharing(context).prepareShare(record.copy(definition = CustomCharacterEngine.currentDefinition(state,record))) }
                            context.startActivity(android.content.Intent.createChooser(intent, "Charakterpaket teilen"))
                        } catch (e: Exception) { error = e.message ?: "Das Paket konnte nicht exportiert werden." }
                        finally { busy = false }
                    }
                }) { Text("${record.definition.name} teilen") }
            }
        }
        Text("Hinweis: Der einfache JSON-Spielstandexport enthält keine Bilddateien. Zum Sichern oder Weitergeben eigener Bilder Charakterpakete (.zip) verwenden.",
            color = Muted, fontSize = 12.sp)
    }
    if (showHelp) ImageRequirementsDialog(kind) { showHelp = false }
    if (showLibrary) ImageLibraryDialog(onDismiss = { showLibrary = false }) { picked ->
        showLibrary = false
        busy = true
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    images.load(picked, CharacterImageKind.PORTRAIT, CharacterImageKind.PORTRAIT.width) to images.load(picked, CharacterImageKind.FULLBODY, CharacterImageKind.FULLBODY.width)
                }
                portrait = loaded.first; fullbody = loaded.second
                images.meta(picked)?.source?.takeIf { it.isNotBlank() }?.let { source = it }
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }
}

@Composable
private fun <T> ChoiceRow(label: String, values: List<T>, selected: T, title: (T) -> String, change: (T) -> Unit) {
    Text(label, color = PaleGold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value -> FilterChip(value == selected, { change(value) }, label = { Text(title(value)) }) }
    }
}

