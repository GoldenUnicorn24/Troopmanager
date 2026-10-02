package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SettingsPanel(state: GameState, onState: (GameState) -> Unit) {
    val settings = state.settings
    fun change(next: GameSettings) { onState(state.copy(settings = next)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Einstellungen")
        SettingsToggle("Soundeffekte", settings.sound) { change(settings.copy(sound = it)) }
        SettingsToggle("Musik", settings.music) { change(settings.copy(music = it)) }
        SettingsToggle("Haptik", settings.haptics) { change(settings.copy(haptics = it)) }
        SettingsToggle("Animationen", settings.animations) { change(settings.copy(animations = it)) }
        Text("Textgröße · ${"%.0f".format(settings.textScale * 100)} %", color = PaleGold)
        var textScale by remember(settings.textScale) { mutableStateOf(settings.textScale) }
        Slider(textScale, { textScale = it }, valueRange = .8f..1.6f, steps = 7,
            onValueChangeFinished = { change(settings.copy(textScale = textScale)) })
        Text("Schlachtanimation · ${settings.battleSpeed}×", color = PaleGold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(.5f, 1f, 2f, 3f).forEach { speed -> FilterChip(settings.battleSpeed == speed, { change(settings.copy(battleSpeed = speed)) }, label = { Text("${speed}×") }) }
        }
        SectionTitle("Romantik & Familie")
        RomanceMode.entries.forEach { mode ->
            FilterChip(settings.romance == mode, { change(settings.copy(romance = mode)) }, label = { Text(mode.label) })
        }
        Text("Freundschaft bleibt immer möglich. Romantische Schritte brauchen gegenseitiges Interesse und Zustimmung. Intime Ereignisse sind ausschließlich erwachsen und werden ausgeblendet.", color = Mist)
        SettingsToggle("Dynastie & Nachfolge", settings.dynasty) { change(settings.copy(dynasty = it)) }
        SettingsToggle("Sterbliche Schlachtführer", settings.permadeath) { change(settings.copy(permadeath = it)) }
        Text("Dynastie ergänzt Alterung, Familienplanung und Nachfolge. Sterbliche Kommandanten können bei schweren Niederlagen sterben; dein eigener Tod setzt die aktivierte Nachfolge voraus.", color = Mist)
        SectionTitle("Schwierigkeit")
        Difficulty.entries.forEach { level -> FilterChip(settings.difficulty == level, { change(settings.copy(difficulty = level)) }, label = { Text(level.label) }) }
        Text(when (settings.difficulty) {
            Difficulty.STORY -> "Mehr Nahrungsertrag und vorsichtigere gegnerische Entscheidungen."
            Difficulty.STANDARD -> "Ausgewogene Planung, Versorgung und Aufklärung."
            Difficulty.VETERAN -> "Vorausschauende Gegner und präzisere feindliche Aufklärung."
        }, color = Mist)
        Text(if (settings.ironman) "Ironman · ein fortlaufender Autosave, kein Rückimport" else "Standardkampagne · Autosave mit Sicherungskopie", color = PaleGold)
    }
}

@Composable
private fun SettingsToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Mist, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(value, onChange)
    }
}

@Composable
internal fun SaveSlotsPanel(ui: GameUiState, controller: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val raw = controller.exportSave() ?: return@launch
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(raw.toByteArray()) } ?: error("Exportziel ist nicht verfügbar.") }
                controller.notice("Spielstand exportiert.")
            } catch (error: Exception) { controller.notice(error.message ?: "Export fehlgeschlagen.") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                pendingImport = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 16 * 1024 * 1024) { "Der Spielstand überschreitet 16 MB." }
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("Importdatei ist nicht verfügbar.")
                }
            } catch (error: Exception) { controller.notice(error.message ?: "Import fehlgeschlagen.") }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Kampagnenplatz", color = PaleGold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { slot -> FilterChip(ui.activeSlot == slot, { controller.selectSlot(slot) }, label = { Text("Platz $slot") }, modifier = Modifier.weight(1f)) }
        }
        val selected = ui.slots[ui.activeSlot - 1]
        if (selected == null) Text("Leerer Platz", color = Mist)
        else {
            Text("${selected.playerName} · Tag ${selected.day} · ${selected.title}", color = PaleGold)
            Text("${selected.population} Einwohner${if (selected.ironman) " · Ironman" else ""}", color = Mist)
            if (selected.savedAt > 0) Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(selected.savedAt)), color = Mist)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ export.launch("LastWall-Platz${ui.activeSlot}.json") }, enabled = selected != null) { Text("Exportieren") }
            OutlinedButton({ import.launch(arrayOf("application/json", "text/plain")) }, enabled = selected?.ironman != true) { Text("Importieren") }
        }
    }
    pendingImport?.let { raw ->
        AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("Spielstand importieren?") },
            text = { Text("Der geprüfte Spielstand ersetzt die Kampagne in Platz ${ui.activeSlot}.") },
            confirmButton = { TextButton({ pendingImport = null; controller.importSave(raw) }) { Text("Importieren") } },
            dismissButton = { TextButton({ pendingImport = null }) { Text("Abbrechen") } })
    }
}
