package com.goldenunicorn.troopmanager.ui

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.NpcEngine
import com.goldenunicorn.troopmanager.model.AppearanceOptions
import com.goldenunicorn.troopmanager.model.CharacterAppearance
import com.goldenunicorn.troopmanager.model.Gender
import com.goldenunicorn.troopmanager.model.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Character editor with a large live preview (head, bust or full figure), grouped tabs, sliders,
 * per-field reset, undo, controlled randomisation (lock what you like) and saved templates.
 * Used for the player and for the optional future companion.
 */
@Composable
fun CharacterEditor(
    appearance: CharacterAppearance,
    gender: Gender,
    species: Species,
    onChange: (CharacterAppearance) -> Unit,
    templateKind: String,
    modifier: Modifier = Modifier,
    initialFraming: Framing = Framing.BUST,
) {
    val openCustom = LocalOpenCustomCharacters.current
    val context = LocalContext.current
    var framing by rememberSaveable { mutableStateOf(initialFraming) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<CharacterAppearance>() }
    var locks by remember { mutableStateOf(setOf<String>()) }
    var showTemplates by remember { mutableStateOf(false) }
    val defaults = remember(gender, species) { defaultLook(gender, species) }
    val latestAppearance by rememberUpdatedState(appearance)
    fun change(next: CharacterAppearance) {
        if (next == appearance) return
        history += appearance
        if (history.size > 40) history.removeAt(0)
        onChange(next.normalized().copy(customImageId = appearance.customImageId))
    }
    val own = rememberOwnImageStatus(appearance.customImageId)
    val ownPortrait = own?.portrait == true
    val ownFullbody = own?.fullbody == true

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // View mode lives above the picture, never on top of the face.
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .35f))
            .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(14.dp)).padding(4.dp).testTag("framing_bar"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Framing.HEAD to "Kopf", Framing.BUST to "Brustbild", Framing.FULL to "Ganzkörper").forEach { (f, label) ->
                val selected = framing == f
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Gold.copy(alpha = .22f) else Color.Transparent)
                    .border(1.dp, if (selected) Gold else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { framing = f }.testTag("framing_${f.name.lowercase()}"), contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) PaleGold else Mist, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val previewWidth = minOf(maxWidth, 380.dp)
            Box(
                Modifier.width(previewWidth).aspectRatio(.8f).clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Gold.copy(alpha = .25f), RoundedCornerShape(24.dp)).testTag("editor_preview"),
            ) {
                // Same identity in all three views; only the framing changes (soft cross-fade).
                Crossfade(framing, animationSpec = androidx.compose.animation.core.tween(180), label = "framing") { f ->
                    CharacterPortrait(appearance, gender, Modifier.fillMaxSize(), f, identity = templateKind)
                }
            }
            // Warm the other two views of this look so switching does not wait for decoding.
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widthPx = with(density) { previewWidth.roundToPx() }.coerceIn(32, 1024)
            LaunchedEffect(appearance, gender, widthPx) {
                kotlinx.coroutines.delay(350)
                withContext(Dispatchers.IO) {
                    val look = latestAppearance.normalized()
                    runCatching {
                        PortraitAssets.layersFor(context, look, gender, widthPx)
                        PortraitAssets.layersFor(context, look, gender, (widthPx * 1024 / 640).coerceAtMost(1024))
                        FigureAssets.load(context, look, gender, "standing", widthPx)
                        look.customImageId?.let { id ->
                            val images = com.goldenunicorn.troopmanager.data.CustomCharacterImages(context)
                            images.load(id, com.goldenunicorn.troopmanager.data.CharacterImageKind.PORTRAIT, widthPx)
                            images.load(id, com.goldenunicorn.troopmanager.data.CharacterImageKind.FULLBODY, widthPx.coerceAtMost(768))
                        }
                    }
                }
            }
        }
        val note = when {
            framing == Framing.FULL && appearance.customImageId != null && !ownFullbody && own != null ->
                "Kein eigenes Ganzkörperbild: Hier steht eine Standardfigur mit erzeugtem Gesicht. Sie gibt dein Porträt nicht wieder."
            framing == Framing.FULL && ownFullbody -> "Dein eigenes Ganzkörperbild – feste Pose, es wird nicht animiert."
            framing != Framing.FULL && ownPortrait -> "Dein eigenes Porträt. Gesichts- und Haarregler wirken nur auf die Standarddarstellung und die Ganzkörperfigur."
            else -> null
        }
        if (note != null) Text(note, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("preview_note"))
        // Expression is separate from the view mode and never covers the figure.
        val expressionActive = !(framing != Framing.FULL && ownPortrait) && !(framing == Framing.FULL && ownFullbody)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (expressionActive) "Ausdruck" else "Ausdruck (nur für die Standarddarstellung)", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppearanceOptions.expressions.forEachIndexed { i, label ->
                    RbChip(label, appearance.expression == i, enabled = expressionActive) { change(appearance.copy(expression = i)) }
                }
            }
        }
        OwnImagePanel(appearance.customImageId, onImageChange = { id -> onChange(latestAppearance.copy(customImageId = id)) })
        if (openCustom != null) TextButton(onClick = { openCustom(if (templateKind == "player") "player" else "companion") }) {
            Text("Weitere Personen und Bilder verwalten", color = Gold)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RbAction("Zufall") {
                change(NpcEngine.randomAppearance(kotlin.random.Random(System.nanoTime()), gender, species, appearance, locks))
            }
            RbAction("Rückgängig", enabled = history.isNotEmpty()) {
                val previous = history.removeAt(history.lastIndex)
                onChange(previous.copy(customImageId = appearance.customImageId))
            }
            RbAction("Alles zurücksetzen") { change(defaults) }
            RbAction("Vorlagen") { showTemplates = true }
        }
        Text("Beim Zufall festhalten:", color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("face" to "Gesicht", "eyes" to "Augen", "mouth" to "Mund", "skin" to "Haut", "hair" to "Frisur", "hairColor" to "Haarfarbe",
                "beard" to "Bart", "body" to "Körper", "outfit" to "Ausrüstung", "colors" to "Farben").forEach { (key, label) ->
                RbChip((if (key in locks) "🔒 " else "") + label, key in locks) { locks = if (key in locks) locks - key else locks + key }
            }
        }
        val tabs = listOf("Gesicht", "Augen & Mund", "Haut & Zeichen", "Haare", "Körper", "Ausrüstung", "Farben")
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Gold, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, fontSize = 13.sp) }) }
        }
        val a = appearance
        val o = AppearanceOptions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> {
                    OptionRow("Gesichtsform", o.faceShapes, a.faceShape, defaults.faceShape) { change(a.copy(faceShape = it)) }
                    SliderRow("Kiefer", a.jaw, "schmal", "breit", defaults.jaw) { change(a.copy(jaw = it)) }
                    SliderRow("Wangenknochen", a.cheekbones, "weich", "markant", defaults.cheekbones) { change(a.copy(cheekbones = it)) }
                    OptionRow("Nase", o.noseShapes, a.noseShape, defaults.noseShape) { change(a.copy(noseShape = it)) }
                    SliderRow("Nasengröße", a.noseSize, "klein", "groß", defaults.noseSize) { change(a.copy(noseSize = it)) }
                    OptionRow("Ohren", o.ears, a.ears, defaults.ears) { change(a.copy(ears = it)) }
                }
                1 -> {
                    OptionRow("Augenform", o.eyeShapes, a.eyeShape, defaults.eyeShape) { change(a.copy(eyeShape = it)) }
                    ColorRow("Augenfarbe", ArtPalette.eyes, o.eyeColors, a.eyeColor, defaults.eyeColor) { change(a.copy(eyeColor = it)) }
                    OptionRow("Augenbrauen", o.browStyles, a.browStyle, defaults.browStyle) { change(a.copy(browStyle = it)) }
                    SliderRow("Lippen", a.lips, "schmal", "voll", defaults.lips) { change(a.copy(lips = it)) }
                    SliderRow("Mundbreite", a.mouthWidth, "klein", "breit", defaults.mouthWidth) { change(a.copy(mouthWidth = it)) }
                }
                2 -> {
                    ColorRow("Hautfarbe", ArtPalette.skin.map { it.base }, o.skinTones, a.skinTone, defaults.skinTone) { change(a.copy(skinTone = it)) }
                    OptionRow("Narben", o.scars, a.scar, defaults.scar) { change(a.copy(scar = it)) }
                    OptionRow("Tätowierungen", o.tattoos, a.tattoo, defaults.tattoo) { change(a.copy(tattoo = it)) }
                    OptionRow("Bart", o.beards, a.beard, defaults.beard) { change(a.copy(beard = it)) }
                }
                3 -> {
                    OptionRow("Frisur", o.hairStyles, a.hairStyle, defaults.hairStyle) { change(a.copy(hairStyle = it)) }
                    SliderRow("Haarlänge", a.hairLength, "kurz", "lang", defaults.hairLength) { change(a.copy(hairLength = it)) }
                    ColorRow("Haarfarbe", ArtPalette.hair, o.hairColors, a.hairColor, defaults.hairColor) { change(a.copy(hairColor = it)) }
                    OptionRow("Haarstruktur", o.hairTextures, a.hairTexture, defaults.hairTexture) { change(a.copy(hairTexture = it)) }
                }
                4 -> {
                    SliderRow("Körpergröße", a.height, "klein", "groß", defaults.height) { change(a.copy(height = it)) }
                    OptionRow("Körperbau", o.builds, a.build, defaults.build) { change(a.copy(build = it)) }
                    SliderRow("Muskulatur", a.muscle, "zierlich", "muskulös", defaults.muscle) { change(a.copy(muscle = it)) }
                    OptionRow("Haltung", o.postures, a.posture, defaults.posture) { change(a.copy(posture = it)) }
                    Text("Tipp: Die Ganzkörper-Ansicht zeigt Größe, Statur und Haltung.", color = Muted, fontSize = 11.sp)
                }
                5 -> {
                    OptionRow("Kleidung", o.clothing, a.clothing, defaults.clothing) { change(a.copy(clothing = it)) }
                    OptionRow("Rüstung", o.armor, a.armor, defaults.armor) { change(a.copy(armor = it)) }
                    OptionRow("Umhang", o.cloaks, a.cloak, defaults.cloak) { change(a.copy(cloak = it)) }
                    OptionRow("Schmuck", o.jewelry, a.jewelry, defaults.jewelry) { change(a.copy(jewelry = it)) }
                    OptionRow("Waffe", o.weapons, a.weapon, defaults.weapon) { change(a.copy(weapon = it)) }
                }
                else -> {
                    ColorRow("Hauptfarbe", ArtPalette.fabric, o.fabricColors, a.primaryColor, defaults.primaryColor) { change(a.copy(primaryColor = it)) }
                    ColorRow("Akzentfarbe", ArtPalette.fabric, o.fabricColors, a.accentColor, defaults.accentColor) { change(a.copy(accentColor = it)) }
                    OptionRow("Hintergrund", o.backgrounds, a.background, defaults.background) { change(a.copy(background = it)) }
                }
            }
        }
    }
    if (showTemplates) TemplateDialog(context, templateKind, appearance, onDismiss = { showTemplates = false }) { picked ->
        change(picked)
        showTemplates = false
    }
}

/** Sensible starting look per gender and people; also the target of "Alles zurücksetzen". */
fun defaultLook(gender: Gender, species: Species): CharacterAppearance {
    val female = gender == Gender.FEMALE
    return CharacterAppearance(
        faceShape = if (female) 3 else 0, jaw = if (female) 36 else 62, cheekbones = 56, noseSize = if (female) 38 else 52,
        eyeShape = 0, eyeColor = if (species == Species.HUMAN) 0 else 2, browStyle = if (female) 1 else 0,
        lips = if (female) 62 else 44, ears = if (species == Species.HUMAN) 0 else 1, skinTone = 1,
        beard = if (female || species == Species.ELF) 0 else 1, hairStyle = if (female) 2 else 1, hairLength = if (female) 72 else 34,
        hairColor = if (female) 4 else 1, height = if (female) 44 else 58, build = 1, muscle = if (female) 42 else 58,
        clothing = if (female) 1 else 1, armor = 1, cloak = 1, weapon = if (species == Species.HUMAN) 0 else 1,
        primaryColor = if (female) 1 else 0, accentColor = 2, background = if (female) 1 else 0,
    )
}

// ------------------------------------------------------------ rows

@Composable
internal fun RbChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) Gold.copy(alpha = .2f) else Color.Black.copy(alpha = .35f),
        border = BorderStroke(1.dp, if (selected) Gold else Color.White.copy(alpha = .14f)),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, color = (if (selected) PaleGold else Mist).copy(alpha = if (enabled) 1f else .4f), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
internal fun RbAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, if (enabled) Gold.copy(alpha = .4f) else Color.White.copy(alpha = .08f))) {
        Text(label, color = if (enabled) PaleGold else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RowHeader(label: String, changed: Boolean, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Mist, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (changed) Text("↺ zurück", color = Gold, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, default: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RowHeader(label, selected != default) { onSelect(default) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, name -> RbChip(name, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, low: String, high: String, default: Int, onValue: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        RowHeader("$label · ${local.toInt()}", value != default) { onValue(default) }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onValue(local.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color.White.copy(alpha = .15f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(low, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(high, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ColorRow(label: String, colors: List<Color>, names: List<String>, selected: Int, default: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RowHeader("$label · ${names.getOrElse(selected) { "" }}", selected != default) { onSelect(default) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEachIndexed { i, color ->
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(100.dp)).background(color)
                        .border(if (i == selected) 3.dp else 1.dp, if (i == selected) Gold else Color.White.copy(alpha = .2f), RoundedCornerShape(100.dp))
                        .clickable { onSelect(i) },
                )
            }
        }
    }
}

// ------------------------------------------------------------ templates

@Serializable
data class LookTemplate(val name: String, val kind: String, val appearance: CharacterAppearance)

internal object LookTemplates {
    private val json = Json { ignoreUnknownKeys = true }
    private const val KEY = "templates"

    fun load(context: Context): List<LookTemplate> = runCatching {
        val raw = context.getSharedPreferences("look_templates", Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        json.decodeFromString(ListSerializer(LookTemplate.serializer()), raw)
    }.getOrDefault(emptyList())

    fun save(context: Context, list: List<LookTemplate>) {
        context.getSharedPreferences("look_templates", Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(ListSerializer(LookTemplate.serializer()), list.takeLast(20))).apply()
    }
}

@Composable
private fun TemplateDialog(context: Context, kind: String, current: CharacterAppearance, onDismiss: () -> Unit, onPick: (CharacterAppearance) -> Unit) {
    var list by remember { mutableStateOf(LookTemplates.load(context)) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Vorlagen", color = PaleGold, fontFamily = Cinzel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name der Vorlage") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RbAction("Aktuelles Aussehen speichern", enabled = name.isNotBlank()) {
                    list = list.filterNot { it.name == name.trim() } + LookTemplate(name.trim(), kind, current)
                    LookTemplates.save(context, list)
                    name = ""
                }
                if (list.isEmpty()) Text("Noch keine Vorlagen gespeichert.", color = Muted, fontSize = 12.sp)
                list.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(t.appearance) }.padding(6.dp)) {
                        PaintedPortrait(t.appearance, if (t.kind == "companion") Gender.FEMALE else Gender.MALE, Modifier.size(48.dp).clip(RoundedCornerShape(100.dp)), Framing.HEAD)
                        Spacer(Modifier.width(10.dp))
                        Text(t.name, color = Color.White, modifier = Modifier.weight(1f))
                        Text("Löschen", color = Danger, fontSize = 12.sp, modifier = Modifier.clickable {
                            list = list - t
                            LookTemplates.save(context, list)
                        }.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen", color = Gold) } },
    )
}
