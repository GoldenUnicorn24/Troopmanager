package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*
import com.goldenunicorn.troopmanager.R

@Composable
internal fun CommandCenterScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit,
                                 onNavigate: (GameDestination) -> Unit, onAdvanceDay: () -> Unit, initialPage: Int = 0) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(listOf("Tageslage", "Entscheidungen & Reich", "Aufgaben", "Tagesbericht", "Chronik"), page) { page = it }
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> CommandOverview(state, onNavigate, onAdvanceDay, onState, onNotice)
                1 -> RealmDashboard(state, onState, onNotice, { onNavigate(GameDestination.CITY) },
                    { onNavigate(GameDestination.WORLD) }, { onNavigate(GameDestination.MILITARY) },
                    { onNavigate(GameDestination.COURT) }, onAdvanceDay)
                2 -> QuestJournalScreen(state, onNavigate)
                3 -> LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { PageTitle("TAGESBERICHT · TAG ${state.day}", "Entscheidungen, Herkunft und Folgen deiner Tageslage.") }
                    items(state.dailyReport.entries, key = { "${it.category}:${it.title}:${it.detail}" }) { row ->
                        CommandCard(row.title, { onNavigate(reportDestination(row)) }) {
                            Text(row.category.label, color = Gold, fontSize = 12.sp)
                            Text(row.detail, color = Mist)
                        }
                    }
                }
                else -> ChronicleScreen(state)
            }
        }
    }
}

@Composable
private fun CommandOverview(state: GameState, onNavigate: (GameDestination) -> Unit, onAdvanceDay: () -> Unit, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val presence = PresenceEngine.presence(state)
    val tasks = remember(state) { QuestJournalEngine.tasks(state) }
    val urgent = tasks.filter { it.category in listOf(JournalCategory.OPEN, JournalCategory.PERSONAL) ||
        it.dueDay?.let { day -> day <= state.day + 3 } == true }
    val completed = state.dailyReport.entries.filter { it.important && it.title.contains("abgeschlossen") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("KOMMANDOZENTRALE · TAG ${state.day}", state.realm.settlementName) }
        item {
            CommandCard("Tagesziel", { onNavigate(GameDestination.FRONTIER) }) {
                Text(state.frontier.dailyGoal.ifBlank { "Prüft Hof, Ausbildung und Grenze." }, color = Color.White, fontSize = 14.sp)
                Text(if (state.frontier.dailyGoalClaimed) "Tageslohn genommen." else "Patrouille oder Ausbildung, dann den Lohn holen.", color = Mist, fontSize = 12.sp)
                if (!state.frontier.dailyGoalClaimed) GoldButton("Abendlohn nehmen", {
                    val result = FrontierEngine.claimDailyGoal(state)
                    onState(result.state)
                    onNotice(result.message)
                })
                val candidates = FrontierEngine.captainCandidates(state)
                if (candidates.isNotEmpty()) {
                    Text("Hauptmann-Kandidaten", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    candidates.forEachIndexed { index, candidate ->
                        SmallAction("${candidate.name} · ${candidate.culture.label} · F ${candidate.leadership} / T ${candidate.tactics} · ${candidate.trait} · ${FrontierEngine.captainCost(index)} Gold") {
                            val result = FrontierEngine.hireCaptain(state, index)
                            onState(result.state)
                            onNotice(result.message)
                        }
                    }
                }
            }
        }
        if (state.day <= 7) item {
            CommandCard("Erster Abend", { onNavigate(GameDestination.MILITARY) }) {
                Text("1. Hof und Bauernhof prüfen. 2. 20 Mann ausbilden. 3. Unter Grenze eine kleine Patrouille schicken. Die ersten Orks bleiben klein.", color = Mist, fontSize = 13.sp)
            }
        }
        item {
            CommandCard(if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "Das Herrscherpaar" else state.title,
                { onNavigate(GameDestination.RULERS) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RulerIdentity(state.player.name, state.title, state.player.portraitUri ?: "file:///android_asset/portrait_player.webp",
                        presence.player.location.label, Modifier.weight(1f))
                    if (state.companion.met) RulerIdentity(state.companion.name, state.companion.role,
                        state.companion.portraitUri ?: "file:///android_asset/portrait_companion.webp",
                        presence.companion.location.label, Modifier.weight(1f))
                }
                if (state.relationship.romanceStage == RomanceStage.CO_RULERS)
                    Text("Ressort: ${state.coRuler.portfolio.label} · Regierung: ${CoRulerEngine.regency(state).actor}", color = Gold, fontSize = 12.sp)
            }
        }
        item {
            CommandCard("Heute wichtig", { onNavigate(GameDestination.JOURNAL) }) {
                var count = 0
                urgent.take(5).forEach { task ->
                    TextButton(onClick = { onNavigate(task.destination) }, contentPadding = PaddingValues(0.dp)) {
                        Text("${task.title}${task.dueDay?.let { " · Tag $it" }.orEmpty()}", color = if (task.dueDay?.let { it <= state.day + 1 } == true) Danger else PaleGold)
                    }
                    count++
                }
                if (state.war.wounded.isNotEmpty() && count < 6) {
                    TextButton(onClick = { onNavigate(GameDestination.HOSPITAL) }) { Text("${state.war.wounded.sumOf { it.soldiers }} Verwundete versorgen", color = Danger) }
                    count++
                }
                completed.take((6 - count).coerceAtLeast(0)).forEach { row ->
                    TextButton(onClick = { onNavigate(reportDestination(row)) }) { Text(row.title, color = Success) }
                }
                if (count == 0 && completed.isEmpty()) Text("Keine dringenden Entscheidungen. Zeit für Planung und gemeinsame Aufgaben.", color = Mist)
            }
        }
        item {
            CommandCard("Reich", { onNavigate(GameDestination.CITY) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Metric("Nahrung", state.resources.food, state.dailyReport.trends["food"])
                        Metric("Gold", state.resources.gold, state.dailyReport.trends["gold"])
                        Metric("Bevölkerung", state.population.total, state.dailyReport.trends["population"])
                    }
                    Column(Modifier.weight(1f)) {
                        Metric("Zufriedenheit", state.city.satisfaction, state.dailyReport.trends["satisfaction"], true)
                        Metric("Sicherheit", state.city.security, state.dailyReport.trends["security"], true)
                    }
                }
                Text("Bilanz gegenüber dem Vortag · Versorgung und Abgaben bestimmen die Entwicklung.", color = Mist, fontSize = 11.sp)
            }
        }
        item {
            CommandCard("Militär", { onNavigate(GameDestination.MILITARY) }) {
                Text("${state.homeArmySize} einsatzbereit · ${state.awayArmySize} unterwegs", color = PaleGold)
                Text("${state.war.wounded.sumOf { it.soldiers }} verwundet · ${state.trainingSize} in Ausbildung", color = Mist)
                TextButton(onClick = { onNavigate(GameDestination.FRONTIER) }) {
                    val discovered = state.frontier.hordes.count { it.discovered }
                    Text("Grenzlage: $discovered gesichtete Banner", color = if (discovered == 0) Success else Danger)
                }
            }
        }
        item {
            CommandCard("Hof", { onNavigate(GameDestination.COURT) }) {
                Text("${CourtOffice.entries.count { it !in state.court.offices }} Ämter unbesetzt", color = Mist)
                Text(state.commanderEvents.pending?.title ?: "${CoRulerEngine.councilCases(state).size} Ratsentscheidungen offen", color = PaleGold)
                TextButton(onClick = { onNavigate(GameDestination.COUNCIL) }) { Text("Rat einberufen", color = Gold) }
            }
        }
        if (state.companion.met) item {
            CommandCard("Beziehung / Herrscherpaar", { onNavigate(GameDestination.RULERS) }) {
                Text(state.relationshipStage(), color = Gold)
                Metric("Konflikt", state.relationship.conflict, state.dailyReport.trends["conflict"], true, true)
                Text(state.relationship.politicalOpinion, color = Mist, fontSize = 12.sp)
                state.relationship.memories.lastOrNull()?.let { Text("Letzter Moment · Tag ${it.day}: ${it.text}", color = PaleGold, fontSize = 12.sp) }
            }
        }
        item { GoldButton("Nächsten Tag beginnen", onAdvanceDay, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun RulerIdentity(name: String, title: String, portrait: String, location: String, modifier: Modifier) {
    Column(modifier) {
        AsyncImage(portrait, name, Modifier.fillMaxWidth().height(112.dp), contentScale = ContentScale.Crop,
            error = painterResource(if (portrait.contains("companion")) R.drawable.portrait_companion else R.drawable.portrait_knight))
        Text(name, color = PaleGold, fontWeight = FontWeight.Bold)
        Text("$title · $location", color = Mist, fontSize = 11.sp)
    }
}

@Composable
private fun Metric(label: String, value: Int, trend: Int?, bar: Boolean = false, negative: Boolean = false) {
    val direction = when { trend == null -> ""; trend > 0 -> " ↑ +$trend"; trend < 0 -> " ↓ $trend"; else -> " →" }
    val color = when { trend == null || trend == 0 -> Mist; (trend > 0) xor negative -> Success; else -> Danger }
    Text("$label $value$direction", color = color, fontSize = 12.sp)
    if (bar) LinearProgressIndicator(progress = { value.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = if (negative) Danger else Gold)
}

@Composable
internal fun QuestJournalScreen(state: GameState, onNavigate: (GameDestination) -> Unit) {
    var category by remember { mutableStateOf<JournalCategory?>(null) }
    var history by remember { mutableStateOf(false) }
    val tasks = remember(state, history) { if (history) state.journal.history.asReversed() else QuestJournalEngine.tasks(state) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageTitle("AUFGABENJOURNAL", "Entscheidungen, Fristen und persönliche Aufgaben.") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(category == null && !history, { category = null; history = false }, label = { Text("Alle") })
                JournalCategory.entries.forEach { c -> FilterChip(category == c && !history, { category = c; history = false }, label = { Text(c.label) }) }
                FilterChip(history, { category = null; history = true }, label = { Text("Historie") })
            }
        }
        val shown = tasks.filter { category == null || it.category == category }
        if (shown.isEmpty()) item { EmptyCard(if (history) "Noch keine abgeschlossenen Aufgaben." else "In diesem Bereich sind keine Aufgaben offen.") }
        items(shown, key = { "${it.id}:${it.startedDay}:${it.closedDay}" }) { task ->
            CommandCard(task.title, { onNavigate(task.destination) }) {
                Text(task.detail, color = Mist)
                Text(task.outcome ?: task.dueDay?.let { "Frist / Ankunft: Tag $it · noch ${(it - state.day).coerceAtLeast(0)} Tage" } ?: "Seit Tag ${task.startedDay}", color = Gold, fontSize = 12.sp)
                Text("Öffnen: ${task.destination.label}", color = PaleGold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun PalaceHubScreen(state: GameState, onNavigate: (GameDestination) -> Unit) {
    val rooms = listOf("Thronsaal" to GameDestination.DECISIONS, "Kriegsrat" to GameDestination.COUNCIL,
        "Privatgemächer" to GameDestination.RULERS, "Familie" to GameDestination.FAMILY, "Hof" to GameDestination.COURT)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("PALASTBEZIRK", if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "Das Herrscherpaar empfängt Rat und Gesandte." else "Audienzen, Rat und das gemeinsame Leben im Reich.") }
        items(rooms, key = { it.first }) { room -> CommandCard(room.first, { onNavigate(room.second) }) { Text(room.second.label, color = Mist) } }
    }
}

@Composable
internal fun CommanderDirectoryScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var selected by remember { mutableStateOf<Long?>(null) }
    val commander = state.commanders.firstOrNull { it.id == selected }
    if (commander != null) CommanderProfileScreen(state, commander, { selected = null }, onState, onNotice)
    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("KOMMANDANTEN", "Profile, Einsätze und echte Truppenzuweisung.") }
        items(state.commanders, key = { it.id }) { person -> ArmyCommanderCard(state, person) { selected = person.id } }
    }
}

@Composable
private fun CommandCard(title: String, onOpen: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            content()
        }
    }
}

private fun reportDestination(row: DailyReportEntry): GameDestination = row.destination ?: when (row.category) {
    ReportCategory.ECONOMY, ReportCategory.CITY -> GameDestination.CITY
    ReportCategory.MILITARY -> GameDestination.MILITARY
    ReportCategory.WORLD, ReportCategory.DIPLOMACY -> GameDestination.WORLD
    ReportCategory.COURT -> GameDestination.COURT
    ReportCategory.WARNING, ReportCategory.FRONTIER -> GameDestination.FRONTIER
    ReportCategory.RULERS -> GameDestination.RULERS
    ReportCategory.FAMILY -> GameDestination.FAMILY
}
