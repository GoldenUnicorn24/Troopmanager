package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.MissionEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun MissionsScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("MISSIONEN", "${state.awayArmySize} Soldaten unterwegs") }
        item { MissionsSection(state, onState, onNotice) }
    }
}

@Composable
internal fun MissionsSection(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var preparing by remember { mutableStateOf<MissionType?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Gold) {
            listOf("Verfügbar", "Unterwegs", "Abgeschlossen").forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title, fontSize = 12.sp) },
                )
            }
        }
        if (tab == 0) {
            MissionType.entries.forEach { mission ->
                Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(mission.label, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "${mission.spec().days} Tage · Risiko ${mission.spec().risk}",
                            color = Gold,
                            fontSize = 12.sp,
                        )
                        Text(
                            "${mission.spec().minimum}–${mission.spec().recommended} Soldaten empfohlen",
                            color = Mist,
                            fontSize = 12.sp,
                        )
                        Text(missionHint(mission), color = Mist, fontSize = 12.sp)
                        SmallAction("Vorbereiten") { preparing = mission }
                    }
                }
            }
        } else {
            val missions =
                state.activeMissions.filter {
                    if (tab == 1) it.status.isAway else !it.status.isAway
                }
            if (missions.isEmpty())
                EmptyCard(
                    if (tab == 1) "Keine Truppen unterwegs."
                    else "Noch keine abgeschlossenen Missionen."
                )
            missions.asReversed().forEach { mission ->
                MissionCard(state, mission, onState, onNotice)
            }
        }
    }
    preparing?.let { mission ->
        MissionPreparationDialog(state, mission, null, { preparing = null }, onState, onNotice)
    }
}

@Composable
private fun MissionCard(
    state: GameState,
    mission: ActiveMission,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(mission.missionType.label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                state.commanders.firstOrNull { it.id == mission.commanderId }?.name
                    ?: "Direktes Oberkommando",
                color = Gold,
            )
            mission.regionId?.let { id ->
                state.regions
                    .firstOrNull { it.id == id }
                    ?.let { Text(it.name, color = Mist, fontSize = 12.sp) }
            }
            if (mission.status.isAway) {
                val elapsed =
                    (mission.duration - mission.remainingDays).coerceIn(0, mission.duration)
                Text(
                    "${mission.total} Soldaten · ${mission.remainingDays} Tage bis zur Rückkehr",
                    color = Mist,
                    fontSize = 12.sp,
                )
                LinearProgressIndicator(
                    progress = { elapsed.toFloat() / mission.duration.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Gold,
                )
                Text(
                    if (mission.status == MissionStatus.RETURNING) "Rückruf: Auf dem Heimweg"
                    else "Tag $elapsed / ${mission.duration}",
                    color = Mist,
                    fontSize = 12.sp,
                )
                mission.units.forEach {
                    Text("${it.type.label}: ${it.amount}", color = Mist, fontSize = 12.sp)
                }
                if (mission.status == MissionStatus.ACTIVE)
                    SmallAction("Truppen zurückrufen") {
                        val result = MissionEngine.recall(state, mission.id)
                        onState(result.state)
                        onNotice(result.message)
                    }
            } else {
                Text(
                    mission.outcome?.label ?: "Zurückgekehrt",
                    color = if (mission.status == MissionStatus.FAILED) Danger else Success,
                )
                Text(
                    "${mission.total - mission.losses} zurückgekehrt · ${mission.losses} Verluste",
                    color = Mist,
                    fontSize = 12.sp,
                )
                val rewards = buildList {
                    if (mission.reward.gold != 0) add("${mission.reward.gold} Gold")
                    if (mission.reward.food != 0) add("${mission.reward.food} Nahrung")
                    if (mission.reward.wood != 0) add("${mission.reward.wood} Holz")
                    if (mission.reward.stone != 0) add("${mission.reward.stone} Stein")
                    if (mission.reward.iron != 0) add("${mission.reward.iron} Eisen")
                    if (mission.renownReward != 0) add("${mission.renownReward} Ruhm")
                }
                Text(
                    if (rewards.isEmpty()) "Keine Beute" else rewards.joinToString(" · "),
                    color = Gold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun MissionPreparationDialog(
    state: GameState,
    mission: MissionType,
    region: WorldRegion?,
    onDismiss: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var commanderId by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    val available =
        UnitType.entries.associateWith { MissionEngine.available(state, commanderId, it) }
    var counts by remember(commanderId) { mutableStateOf(emptyMap<UnitType, Int>()) }
    val allocations =
        UnitType.entries.mapNotNull { type ->
            val count = (counts[type] ?: 0).coerceIn(0, available.getValue(type))
            if (count > 0) UnitAllocation(type, count) else null
        }
    val total = allocations.sumOf { it.amount.toLong() }
    val duration = MissionEngine.duration(state, mission, commanderId)
    val supply = total * duration.toLong() * 2L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mission.label) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 540.dp),
            ) {
                region?.let { item { Text("Ziel: ${it.name}", color = Gold) } }
                item {
                    Box {
                        OutlinedButton(
                            onClick = { menu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                state.commanders.firstOrNull { it.id == commanderId }?.name
                                    ?: "Direktes Oberkommando"
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Direktes Oberkommando") },
                                onClick = {
                                    commanderId = null
                                    menu = false
                                },
                            )
                            state.commanders
                                .filterNot { state.commanderAway(it.id) }
                                .forEach { commander ->
                                    DropdownMenuItem(
                                        text = { Text(commander.name) },
                                        onClick = {
                                            commanderId = commander.id
                                            menu = false
                                        },
                                    )
                                }
                        }
                    }
                    Text(
                        "Eigene Kontingente und freie Soldaten verfügbar. Andere Kommandos bleiben reserviert.",
                        color = Mist,
                        fontSize = 12.sp,
                    )
                }
                items(UnitType.entries.filter { available.getValue(it) > 0 }) { type ->
                    TroopCountPicker(type.label, available.getValue(type), counts[type] ?: 0) {
                        amount ->
                        counts = counts + (type to amount)
                    }
                }
                if (available.values.sumOf { it.toLong() } == 0L)
                    item {
                        Text(
                            "Keine verfügbaren Soldaten. Prüfe die Truppenzuweisungen in der Armee.",
                            color = Mist,
                        )
                    }
                item {
                    Text(
                        "$total Soldaten · $duration Tage · Risiko ${mission.spec().risk}",
                        color = Gold,
                    )
                    Text(
                        "Versorgung: $supply Nahrung / ${state.resources.food} verfügbar",
                        color = if (supply > state.resources.food) Danger else Mist,
                    )
                    Text(
                        "Mindestens ${mission.spec().minimum}, empfohlen ${mission.spec().recommended} Soldaten. Unterwegs fehlen sie der Festung. Belohnungen gibt es erst bei Rückkehr.",
                        color = Mist,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    total >= mission.spec().minimum &&
                        total <= Int.MAX_VALUE.toLong() &&
                        supply <= state.resources.food &&
                        state.battleSession?.isActive != true,
                onClick = {
                    val result =
                        MissionEngine.start(state, mission, commanderId, allocations, region?.id)
                    onState(result.state)
                    onNotice(result.message)
                    if (
                        result.state.activeMissions.any { launched ->
                            state.activeMissions.none { it.id == launched.id }
                        }
                    )
                        onDismiss()
                },
            ) {
                Text("MISSION STARTEN")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zurück") } },
    )
}

@Composable
internal fun TroopCountPicker(label: String, available: Int, value: Int, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("$available verfügbar", color = Mist, fontSize = 12.sp)
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { input ->
                onValue(
                    (input.filter(Char::isDigit).take(10).toLongOrNull() ?: 0L)
                        .coerceIn(0L, available.toLong())
                        .toInt()
                )
            },
            singleLine = true,
            label = { Text("Soldaten") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 25, 50, 75, 100).forEach { percent ->
                TextButton(
                    onClick = { onValue((available.toLong() * percent / 100).toInt()) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        if (percent == 100) "Alle" else if (percent == 0) "0" else "$percent %",
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun missionHint(type: MissionType): String =
    when (type) {
        MissionType.PATROL -> "Grenzen sichern und Ruhm gewinnen."
        MissionType.ESCORT -> "Karawanen schützen; Ritter stärken den Geleitschutz."
        MissionType.BANDITS -> "Banditen besiegen und Gold sowie Ausrüstung bergen."
        MissionType.HUNT -> "Elite und Fernkampf helfen gegen gefährliche Monster."
        MissionType.RELIEF -> "Dörfer schützen und neue Bewohner gewinnen."
        MissionType.SCOUT -> "Waldelben erkunden die Grenze und warnen vor Angriffen."
    }
