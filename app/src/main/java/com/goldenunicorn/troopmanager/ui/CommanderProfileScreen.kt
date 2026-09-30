package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.customizeCommanderPortrait
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun ArmyCommanderCard(state: GameState, commander: Commander, onOpen: () -> Unit) {
    val home = state.commanderAssignments.firstOrNull { it.commanderId == commander.id }?.total ?: 0
    val away =
        state.activeMissions
            .filter { it.commanderId == commander.id && it.status.isAway }
            .sumOf { it.total }
    Surface(onClick = onOpen, color = Panel, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CommanderPortrait(
                commander,
                Modifier.width(82.dp).height(104.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    commander.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    "${commander.rank} · ${commander.culture.label}",
                    color = Gold,
                    fontSize = 13.sp,
                )
                Text(
                    "${home + away} Soldaten · Loyalität ${commander.loyalty} %",
                    color = Mist,
                    fontSize = 13.sp,
                )
                Text(
                    if (state.commanderAway(commander.id)) "Auf Mission"
                    else "Bereit · Stufe ${commander.level}",
                    color = PaleGold,
                    fontSize = 13.sp,
                )
                Text("Profil & Truppen öffnen ›", color = Gold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun CommanderProfileScreen(
    state: GameState,
    commander: Commander,
    onBack: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var assigning by remember(commander.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val portraitPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                onState(customizeCommanderPortrait(state, commander.id, uri.toString()))
                onNotice("Kommandanten-Portrait aktualisiert.")
            }
        }
    if (assigning) {
        CommanderAllocationScreen(state, commander, { assigning = false }, onState, onNotice)
        return
    }
    BackHandler { onBack() }
    val locked = state.commanderAway(commander.id) || state.battleSession?.isActive == true
    val missions = state.activeMissions.filter { it.commanderId == commander.id }
    val homeUnits =
        state.commanderAssignments.firstOrNull { it.commanderId == commander.id }?.units.orEmpty()
    val awayUnits = missions.filter { it.status.isAway }.flatMap { it.units }
    val units =
        (homeUnits + awayUnits)
            .groupBy { it.type }
            .map { (type, rows) -> UnitAllocation(type, rows.sumOf { it.amount }) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("‹ Kommandanten")
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Box(Modifier.fillMaxWidth().height(330.dp)) {
                    CommanderPortrait(commander, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color(0xF5090D10)))
                            )
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            commander.name,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "${commander.rank} · ${commander.culture.label} · Stufe ${commander.level}",
                            color = PaleGold,
                            fontSize = 14.sp,
                        )
                        Text(
                            if (locked) "Im Einsatz" else "Bereit für deinen Auftrag",
                            color = Mist,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { assigning = true },
                    enabled = !locked,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    Text("Truppen zuweisen", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { portraitPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    Text("Portrait ändern", fontSize = 13.sp)
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Fähigkeiten & Loyalität",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    ArmyQuality(
                        "Führung",
                        commander.leadership,
                        "Führung stärkt die Kampfkraft des Kontingents und hilft, die Formation zusammenzuhalten.",
                    )
                    ArmyQuality(
                        "Taktik",
                        commander.tactics,
                        "Taktik verbessert Missionschancen und Entscheidungen im Gefecht.",
                    )
                    ArmyQuality("Schwert", commander.sword)
                    ArmyQuality("Bogen", commander.bow)
                    ArmyQuality("Belagerung", commander.siege)
                    ArmyQuality(
                        "Loyalität",
                        commander.loyalty,
                        "Loyalität zeigt die Bindung an dein Reich. Training und gemeinsame Erfolge stärken das Vertrauen.",
                    )
                    Text("Eigenschaft: ${commander.trait}", color = PaleGold, fontSize = 14.sp)
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Aktuelles Kontingent · ${units.sumOf { it.amount }} Soldaten",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (units.isEmpty())
                        Text("Noch keine Soldaten zugewiesen", color = Mist, fontSize = 14.sp)
                    units.forEach { unit ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                unit.type.label,
                                color = Mist,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(unit.amount.toString(), color = PaleGold, fontSize = 14.sp)
                        }
                    }
                    Text(
                        "Dir direkt unterstellt: ${UnitType.entries.sumOf { state.directCommand(it) }} Soldaten",
                        color = Gold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Dienstlaufbahn",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    ArmyMetrics(
                        listOf(
                            "Missionen beendet" to commander.missionsCompleted,
                            "Schlachten" to commander.battlesFought,
                            "Siege" to commander.victories,
                            "Gefallene" to commander.casualties,
                        )
                    )
                    Text(
                        "Die Dienststatistik wird seit v0.45 erfasst; bestehende Kontingente bleiben erhalten.",
                        color = Mist,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (missions.isNotEmpty()) {
            item { SectionTitle("Missionen & Rückkehrberichte") }
            items(missions.sortedByDescending { it.startDay }, key = { it.id }) { mission ->
                Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            mission.missionType.label,
                            color = PaleGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            "Tag ${mission.startDay} · ${mission.total} Soldaten",
                            color = Mist,
                            fontSize = 13.sp,
                        )
                        Text(
                            if (mission.status.isAway)
                                "Noch ${mission.remainingDays} Tage · ${if (mission.status == MissionStatus.RETURNING) "Auf Rückkehr" else "Unterwegs"}"
                            else
                                "${mission.outcome?.label ?: "Abgeschlossen"} · ${mission.losses} Verluste",
                            color = Gold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Entwicklung",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (commander.id == COMPANION_COMMANDER_ID) {
                        Text(
                            "Gemeinsames Training entwickelt deine Gefährtin und nutzt eine Beziehungsaktion. Ihre Werte bleiben mit dem Hof synchron.",
                            color = Mist,
                            fontSize = 13.sp,
                        )
                    } else {
                        val academyLevel = state.realm.level(BuildingType.ACADEMY)
                        val academyBonus = academyLevel.coerceAtMost(3)
                        Text(
                            "Je Training: Führung +${2 + academyBonus} · Taktik +${2 + academyBonus}",
                            color = PaleGold,
                            fontSize = 14.sp,
                        )
                        Text(
                            "Schwert, Bogen, Belagerung und Loyalität jeweils +1. Alle Werte bis maximal 100.",
                            color = Mist,
                            fontSize = 13.sp,
                        )
                        Text(
                            "Offiziersschule Stufe $academyLevel: zusätzlich +$academyBonus auf Führung und Taktik (maximal +3).",
                            color = Gold,
                            fontSize = 13.sp,
                        )
                        Text(
                            "Höhere Stufen ermöglichen die Beförderung zum General und Marschall.",
                            color = Mist,
                            fontSize = 13.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val result = GameEngine.trainCommander(state, commander.id)
                            onState(result.state)
                            onNotice(result.message)
                        },
                        enabled = !locked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (commander.id == COMPANION_COMMANDER_ID) "Gemeinsam trainieren"
                            else "Trainieren · 120 Gold",
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommanderAllocationScreen(
    state: GameState,
    commander: Commander,
    onBack: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    BackHandler { onBack() }
    val types =
        UnitType.entries.filter { state.soldiers(it) > 0 || state.assignedTo(commander.id, it) > 0 }
    val fields =
        remember(commander.id, state.commanderAssignments) {
            mutableStateMapOf<UnitType, String>().apply {
                types.forEach { this[it] = state.assignedTo(commander.id, it).toString() }
            }
        }
    fun requested(type: UnitType) =
        fields[type].orEmpty().ifBlank { "0" }.toIntOrNull() ?: Int.MAX_VALUE
    val locked = state.commanderAway(commander.id) || state.battleSession?.isActive == true
    val valid =
        !locked &&
            types.all {
                requested(it) in 0..GameEngine.freeSoldiersForCommander(state, commander.id, it)
            }
    val personalPreview =
        UnitType.entries.sumOf { type ->
            if (type in types)
                (GameEngine.freeSoldiersForCommander(state, commander.id, type) - requested(type))
                    .coerceAtLeast(0)
            else state.directCommand(type)
        }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("‹ Zurück zum Profil")
            }
            PageTitle("TRUPPEN ZUWEISEN", commander.name)
            Surface(color = Panel2, shape = RoundedCornerShape(14.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Dein persönliches Kommando",
                        color = PaleGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        "Dir direkt unterstellt: $personalPreview Soldaten",
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                    Text("Vorschau nach dem Speichern", color = Mist, fontSize = 13.sp)
                    if (!valid)
                        Text(
                            if (locked) "Kontingent ist im Einsatz"
                            else "Bitte markierte Zuweisungen korrigieren",
                            color = Danger,
                            fontSize = 13.sp,
                        )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (types.isEmpty())
                item { EmptyCard("Noch keine Soldaten verfügbar. Bilde zuerst Truppen aus.") }
            items(types) { type ->
                val maximum = GameEngine.freeSoldiersForCommander(state, commander.id, type)
                val current = requested(type)
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            type.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        ArmyMetrics(
                            listOf(
                                "Gesamt" to state.soldiers(type),
                                "Für dich verfügbar" to maximum,
                                "Bereits zugewiesen" to state.assignedTo(commander.id, type),
                                "Andere Kommandos" to
                                    (state.assigned(type) - state.assignedTo(commander.id, type))
                                        .coerceAtLeast(0),
                            )
                        )
                        OutlinedTextField(
                            value = fields[type].orEmpty(),
                            onValueChange = { value ->
                                fields[type] = value.filter(Char::isDigit).take(10)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Soldaten zuweisen") },
                            supportingText = {
                                Text("Maximal $maximum · unterwegs ${state.away(type)}")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !locked,
                            isError = current > maximum,
                        )
                        // Two rows keep every shortcut readable and touch targets at least 48 dp
                        // tall.
                        listOf(0 to "0", 25 to "25 %", 50 to "50 %", 75 to "75 %", 100 to "ALLE")
                            .chunked(3)
                            .forEach { shortcuts ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    shortcuts.forEach { (percent, label) ->
                                        OutlinedButton(
                                            onClick = {
                                                fields[type] =
                                                    (maximum.toLong() * percent / 100).toString()
                                            },
                                            enabled = !locked,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                            contentPadding = PaddingValues(4.dp),
                                        ) {
                                            Text(label, fontSize = 13.sp)
                                        }
                                    }
                                    repeat(3 - shortcuts.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        if (current > maximum)
                            Text(
                                "Zu viele Soldaten: maximal $maximum verfügbar",
                                color = Danger,
                                fontSize = 13.sp,
                            )
                    }
                }
            }
        }
        Button(
            onClick = {
                val units =
                    types.mapNotNull { type ->
                        requested(type).takeIf { it > 0 }?.let { UnitAllocation(type, it) }
                    }
                val result = GameEngine.setCommanderAllocation(state, commander.id, units)
                onState(result.state)
                onNotice(result.message)
                if (result.state !== state) onBack()
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
        ) {
            Text("Zuweisung speichern", fontSize = 15.sp)
        }
    }
}

@Composable
private fun CommanderPortrait(commander: Commander, modifier: Modifier) {
    if (commander.portraitUri != null)
        AsyncImage(
            model = commander.portraitUri,
            contentDescription = "Portrait von ${commander.name}",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    else
        Image(
            painter = painterResource(portraitResource(commander.portraitKey)),
            contentDescription = "Portrait von ${commander.name}",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
}
