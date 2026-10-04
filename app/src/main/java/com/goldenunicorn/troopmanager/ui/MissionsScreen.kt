package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.MissionExperienceEngine
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
            Text("Schnellzugriff · Missionen auch über die Weltkarte starten", color = Mist, fontSize = 12.sp)
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
    var report by remember { mutableStateOf<ActiveMission?>(null) }
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(mission.missionType.label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(missionLeaderNames(state, mission), color = Gold)
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
                    "${mission.readyReturned} einsatzbereit · ${mission.reportedWounded} verwundet · ${mission.reportedDead} gefallen",
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
                SmallAction("Rückkehrbericht öffnen") { report = mission }
            }
        }
    }
    report?.let { MissionResultScreen(state, it) { report = null } }
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
    var commanderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var playerParticipates by remember { mutableStateOf(!state.playerAwayOnMission) }
    val leaderCount = commanderIds.size + if (playerParticipates) 1 else 0
    val available =
        UnitType.entries.associateWith {
            MissionEngine.available(state, commanderIds.toList(), it)
        }
    var counts by remember(commanderIds) { mutableStateOf(emptyMap<UnitType, Int>()) }
    val allocations =
        UnitType.entries.mapNotNull { type ->
            val count = (counts[type] ?: 0).coerceIn(0, available.getValue(type))
            if (count > 0) UnitAllocation(type, count) else null
        }
    val total = allocations.sumOf { it.amount.toLong() }
    val duration =
        MissionEngine.duration(
            state,
            mission,
            commanderIds.toList(),
            playerParticipates,
        )
    val supply = total * duration.toLong() * 2L
    val estimate =
        MissionEngine.estimate(
            state,
            mission,
            allocations,
            commanderIds.toList(),
            playerParticipates,
        )
    FullScreenMission("MISSION VORBEREITEN", onDismiss, bottomBar = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$total Soldaten · $duration Tage · $supply Nahrung", color = Gold, fontSize = 13.sp)
            Button(onClick = {
                val result =
                    MissionEngine.start(
                        state,
                        mission,
                        commanderIds.toList(),
                        playerParticipates,
                        allocations,
                        region?.id,
                    )
                onState(result.state)
                onNotice(result.message)
                if (result.state.activeMissions.any { launched -> state.activeMissions.none { it.id == launched.id } }) onDismiss()
            }, modifier = Modifier.fillMaxWidth(), enabled = leaderCount in 1..3 &&
                total >= mission.spec().minimum &&
                total <= Int.MAX_VALUE.toLong() && supply <= state.resources.food &&
                state.battleSession?.isActive != true, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("MISSION STARTEN", fontWeight = FontWeight.Bold) }
        }
    }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().height(175.dp)) {
                    Image(painterResource(R.drawable.art_world_map), "Missionsgebiet", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Surface(Modifier.align(Alignment.BottomStart).fillMaxWidth(), color = Ink.copy(alpha = .88f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(region?.name ?: mission.label, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text(if (region?.owned == true) "Eigenes Gebiet" else region?.type?.label ?: "Grenzregion · freies Missionsziel", color = Mist, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Text(mission.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(missionHint(mission), color = Mist, fontSize = 13.sp)
                StatGrid(listOf("Dauer" to "$duration Tage", "Risiko" to mission.spec().risk))
            }
            item {
                SectionTitle("Wer führt die Mission? · 1–3 Personen")
                Text(
                    "Du kannst selbst mitreiten und zusätzlich deine Gefährtin oder andere Kommandanten auswählen. Dauerhafte Armee-Zuteilungen bleiben nach der Mission erhalten.",
                    color = Mist,
                    fontSize = 12.sp,
                )
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = playerParticipates,
                                onCheckedChange = { checked ->
                                    if (!checked || leaderCount < 3) playerParticipates = checked
                                },
                                enabled = !state.playerAwayOnMission,
                            )
                            Column {
                                Text(state.player.name + " · Du selbst", color = Color.White)
                                if (state.playerAwayOnMission)
                                    Text("Bereits auf Mission", color = Danger, fontSize = 11.sp)
                            }
                        }
                        state.commanders.forEach { commander ->
                            val checked = commander.id in commanderIds
                            val away = state.commanderAway(commander.id)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { select ->
                                        commanderIds =
                                            if (select && leaderCount < 3) commanderIds + commander.id
                                            else if (!select) commanderIds - commander.id
                                            else commanderIds
                                    },
                                    enabled = !away && (checked || leaderCount < 3),
                                )
                                Column {
                                    Text(
                                        commander.name +
                                            if (commander.id == COMPANION_COMMANDER_ID) " · Gefährtin" else " · ${commander.rank}",
                                        color = if (away) Mist else Color.White,
                                    )
                                    if (away) Text("Nicht verfügbar", color = Danger, fontSize = 11.sp)
                                }
                            }
                        }
                        Text("$leaderCount / 3 Führungspersonen gewählt", color = Gold, fontSize = 12.sp)
                        MissionExperienceEngine.roleLabels(state, commanderIds, playerParticipates).forEach {
                            Text(it, color = Mist, fontSize = 12.sp)
                        }
                        Text("Mitgenommene Soldaten fehlen in der Heimat. Dauerhafte Kommandantenzuweisungen werden für die Reise freigegeben; Rückkehr benötigt einen echten Rückmarsch.", color = PaleGold, fontSize = 12.sp)
                    }
                }
                Text("Freie Soldaten plus feste Kontingente der ausgewählten Kommandanten können temporär mitgeschickt werden.", color = Mist, fontSize = 12.sp)
            }
            item { SectionTitle("Truppen auswählen") }
            items(UnitType.entries.filter { available.getValue(it) > 0 }) { type ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Box(Modifier.padding(14.dp)) {
                        TroopCountPicker(type.label, available.getValue(type), counts[type] ?: 0) { amount -> counts = counts + (type to amount) }
                    }
                }
            }
            if (available.values.sumOf { it.toLong() } == 0L) item { EmptyCard("Keine verfügbaren Soldaten. Prüfe die Truppenzuweisung in der Armee.") }
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Einschätzung", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(estimate.outcomeHint, color = Mist)
                        StatGrid(listOf("Goldbeute" to "${estimate.goldRange.first}–${estimate.goldRange.last}", "Spieler-XP" to "${estimate.xpRange.first}–${estimate.xpRange.last}"))
                        Text("Schätzung ohne Erfolgsgarantie; Qualität, Führung und Zufall bestimmen die Rückkehr.", color = Mist, fontSize = 12.sp)
                    }
                }
            }
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Versorgung: $supply / ${state.resources.food} Nahrung", color = if (supply > state.resources.food) Danger else Gold)
                        Text("Mindestens ${mission.spec().minimum}, empfohlen ${mission.spec().recommended} Soldaten.", color = Mist, fontSize = 12.sp)
                        Text("Versorgung wird beim Start bezahlt. Truppen fehlen bis zur Rückkehr in der Festung.", color = Mist, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenMission(
    title: String,
    onDismiss: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Scaffold(
            modifier = Modifier.fillMaxSize(), containerColor = Ink,
            topBar = {
                Surface(color = Panel) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Zurück", color = Gold) }
                        Text(title, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            bottomBar = { Surface(color = Panel, modifier = Modifier.navigationBarsPadding()) { bottomBar() } },
            content = content,
        )
    }
}

@Composable
private fun MissionResultScreen(state: GameState, mission: ActiveMission, onDismiss: () -> Unit) {
    FullScreenMission("RÜCKKEHRBERICHT", onDismiss) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PageTitle(mission.missionType.label.uppercase(), mission.outcome?.label ?: "Zurückgekehrt") }
            item {
                Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(missionLeaderNames(state, mission), color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(state.regions.firstOrNull { it.id == mission.regionId }?.name ?: "Grenzregion", color = Mist)
                        Text("Tag ${mission.startDay} · ${mission.duration} Tage · ${mission.supplyCost} Nahrung Versorgung", color = Mist, fontSize = 12.sp)
                    }
                }
            }
            item {
                StatGrid(
                    listOf(
                        "Eingesetzt" to "${mission.deployedTotal}",
                        "Einsatzbereit" to "${mission.readyReturned}",
                        "Verwundet" to "${mission.reportedWounded}",
                        "Gefallen" to "${mission.reportedDead}",
                        "Spieler-XP" to "${mission.xpReward}",
                    )
                )
            }
            if (mission.reportedWounded > 0) {
                item {
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Lazarett", color = PaleGold, fontWeight = FontWeight.Bold)
                            Text(
                                "${mission.reportedWounded} Verwundete werden behandelt" +
                                    if (mission.woundedRecoveryDay > 0) " · voraussichtlich einsatzbereit ab Tag ${mission.woundedRecoveryDay}" else "",
                                color = Mist,
                                fontSize = 12.sp,
                            )
                            Text("Einsehbar unter Armee → Lazarett & Versorgung.", color = Gold, fontSize = 12.sp)
                        }
                    }
                }
            }
            item { SectionTitle("Beute & Ruhm") }
            item { StatGrid(listOf("Gold" to "${mission.reward.gold}", "Nahrung" to "${mission.reward.food}", "Holz" to "${mission.reward.wood}", "Stein" to "${mission.reward.stone}", "Eisen" to "${mission.reward.iron}", "Ruhm" to "${mission.renownReward}")) }
            item { SectionTitle("Eingesetzte Einheiten") }
            items(mission.units) { unit ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(unit.type.label, color = Mist)
                    Text("${unit.amount}", color = Color.White)
                }
            }
            item {
                val event = when {
                    mission.status == MissionStatus.RETURNING || mission.outcome == null -> "Die Mission wurde zurückgerufen. Die Truppen haben die Festung erreicht."
                    mission.missionType == MissionType.RELIEF && mission.reward.gold > 0 -> "Die Hilfsmission hat Bewohner unterstützt und den Einfluss deines Reiches gestärkt."
                    mission.missionType == MissionType.SCOUT && mission.status == MissionStatus.COMPLETE -> "Erkundungsberichte geben deiner Festung zusätzliche Vorwarnzeit."
                    mission.reportedWounded > 0 -> "Verwundete sind vorübergehend nicht einsatzbereit und kehren nach ihrer Behandlung automatisch ins Heer zurück."
                    mission.reportedDead > 0 -> "Die Rückkehrer berichten von schweren Gefechten. Gefallene sind dauerhafte Verluste."
                    else -> "Die Truppen stehen der Festung wieder zur Verfügung. Beute und Erfahrung sind bereits verbucht."
                }
                EmptyCard(event)
            }
            item { GoldButton("BERICHT SCHLIESSEN", onDismiss, Modifier.fillMaxWidth()) }
        }
    }
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

private fun missionLeaderNames(state: GameState, mission: ActiveMission): String =
    buildList {
        if (mission.playerParticipates || (mission.commanderId == null && mission.commanderIds.isEmpty()))
            add(state.player.name + " · persönlich")
        mission.allCommanderIds.mapNotNullTo(this) { id ->
            state.commanders.firstOrNull { it.id == id }?.name
        }
    }.distinct().joinToString(" · ").ifBlank { "Führung ohne Namen" }

private fun missionHint(type: MissionType): String =
    when (type) {
        MissionType.PATROL -> "Grenzen sichern und Ruhm gewinnen."
        MissionType.ESCORT -> "Karawanen schützen; Ritter stärken den Geleitschutz."
        MissionType.BANDITS -> "Banditen besiegen und Gold sowie Ausrüstung bergen."
        MissionType.HUNT -> "Elite und Fernkampf helfen gegen gefährliche Monster."
        MissionType.RELIEF -> "Dörfer schützen und neue Bewohner gewinnen."
        MissionType.SCOUT -> "Waldelben erkunden die Grenze und warnen vor Angriffen."
    }
