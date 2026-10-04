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
private fun CommandOverview(
    state: GameState,
    onNavigate: (GameDestination) -> Unit,
    onAdvanceDay: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    val tasks = remember(state) { QuestJournalEngine.tasks(state) }
    val urgent = tasks.filter {
        it.category in listOf(JournalCategory.OPEN, JournalCategory.PERSONAL) ||
            it.dueDay?.let { day -> day <= state.day + 3 } == true
    }
    val nearestThreat = state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }
    val wounded = state.war.wounded.sumOf { it.soldiers }
    val foodNet = EconomyEngine.production(state).net.food
    val foodStatus =
        if (foodNet >= 0) "+$foodNet/Tag"
        else "$foodNet/Tag"
    val situations =
        listOfNotNull(
            state.campaign.pendingDecision?.let {
                Triple(
                    "Herrscherentscheidung",
                    it.title,
                    "Bis Tag ${it.expiresDay} · ${it.text}",
                ) to GameDestination.DECISIONS
            },
            nearestThreat?.let {
                Triple(
                    "Grenzlage",
                    it.name,
                    "${it.estimatedStrengthLabel} · Ankunft in ${it.daysToArrival} Tagen",
                ) to GameDestination.FRONTIER
            },
            if (wounded > 0)
                Triple(
                    "Lazarett",
                    "$wounded Verwundete",
                    "Soldaten warten auf Versorgung und Rückkehr in den Dienst.",
                ) to GameDestination.HOSPITAL
            else null,
            urgent.firstOrNull()?.let {
                Triple(
                    "Aufgabe",
                    it.title,
                    it.dueDay?.let { day -> "Frist Tag $day" } ?: it.detail,
                ) to it.destination
            },
        )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            RealmHero(
                state = state,
                onPrimary = {
                    onNavigate(
                        if (state.campaign.pendingDecision != null) GameDestination.DECISIONS
                        else if (nearestThreat != null) GameDestination.FRONTIER
                        else GameDestination.JOURNAL
                    )
                },
                onSecondary = { onNavigate(GameDestination.CITY) },
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusMetric(
                    "Heer",
                    state.homeArmySize.toString(),
                    Modifier.weight(1f),
                    supporting = "${state.awayArmySize} unterwegs",
                )
                StatusMetric(
                    "Nahrung",
                    state.resources.food.toString(),
                    Modifier.weight(1f),
                    accent = if (foodNet < 0) Danger else Success,
                    supporting = foodStatus,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusMetric(
                    "Stimmung",
                    "${state.city.satisfaction}%",
                    Modifier.weight(1f),
                    accent = if (state.city.satisfaction < 45) Danger else Success,
                    supporting = if (state.city.satisfaction < 45) "angespannt" else "stabil",
                )
                StatusMetric(
                    "Druck",
                    "${state.campaign.pressure}",
                    Modifier.weight(1f),
                    accent = if (state.campaign.pressure >= 65) Danger else Gold,
                    supporting = "Momentum ${state.campaign.momentum}",
                )
            }
        }

        item { SectionTitle("Jetzt wichtig") }
        if (situations.isEmpty()) {
            item {
                SituationCard(
                    eyebrow = "Ruhige Lage",
                    title = "Keine akute Krise",
                    detail = "Nutze den Tag für Ausbau, Diplomatie, Training oder gemeinsame Zeit.",
                    onClick = { onNavigate(GameDestination.DECISIONS) },
                )
            }
        } else {
            situations.take(3).forEachIndexed { index, row ->
                item {
                    val (copy, destination) = row
                    SituationCard(
                        eyebrow = copy.first,
                        title = copy.second,
                        detail = copy.third,
                        urgent = index == 0 && (state.campaign.pendingDecision != null || nearestThreat?.daysToArrival?.let { it <= 3 } == true),
                        onClick = { onNavigate(destination) },
                    )
                }
            }
        }

        item { CampaignPulseCard(state, onState, onNotice) }

        if (state.companion.met) {
            item {
                Box(
                    Modifier.fillMaxWidth().clickable { onNavigate(GameDestination.RULERS) }
                ) {
                    RulerPairHero(state)
                }
            }
        }

        item {
            PremiumPanel(emphasized = true) {
                Text("TAGESZIEL", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text(
                    state.frontier.dailyGoal.ifBlank { "Stärke heute eine Säule deines Reiches." },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.frontier.dailyGoalClaimed)
                        "Tageslohn bereits erhalten."
                    else
                        "Erledige Patrouille oder Ausbildung und sichere dir anschließend den Tageslohn.",
                    color = Muted,
                    fontSize = 11.sp,
                )
                if (!state.frontier.dailyGoalClaimed) {
                    GoldButton(
                        "Abendlohn nehmen",
                        {
                            val result = FrontierEngine.claimDailyGoal(state)
                            onState(result.state)
                            onNotice(result.message)
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionTitle("Direktzugriff") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onNavigate(GameDestination.MILITARY) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp),
                ) { Text("HEER", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                OutlinedButton(
                    onClick = { onNavigate(GameDestination.WORLD) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp),
                ) { Text("WELT", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                OutlinedButton(
                    onClick = { onNavigate(GameDestination.PALACE) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(15.dp),
                ) { Text("PALAST", fontSize = 11.sp, fontWeight = FontWeight.Black) }
            }
        }

        item {
            GoldButton(
                if (state.campaign.pendingDecision != null) "Tag fortsetzen · Entscheidung offen"
                else "Nächsten Tag beginnen",
                onAdvanceDay,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CampaignPulseCard(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    val pulse = state.campaign
    val momentumColor = when {
        pulse.momentum >= 70 -> Success
        pulse.momentum <= 30 -> Danger
        else -> Gold
    }
    val pressureColor = when {
        pulse.pressure >= 65 -> Danger
        pulse.pressure >= 40 -> Gold
        else -> Success
    }
    CommandCard("Kampagnenpuls", {}) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("MOMENTUM · ${pulse.momentum}", color = momentumColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { pulse.momentum / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    color = momentumColor,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("DRUCK · ${pulse.pressure}", color = pressureColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { pulse.pressure / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    color = pressureColor,
                )
            }
        }
        Text(
            "Serie: ${pulse.streak} Tage · Bestwert ${pulse.bestStreak} · Fokus: ${pulse.focus.label}",
            color = Mist,
            fontSize = 11.sp,
        )

        Text("Reichsschwerpunkt", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CampaignFocus.entries.forEach { focus ->
                FilterChip(
                    selected = focus == pulse.focus,
                    onClick = {
                        val result = CampaignPulseEngine.setFocus(state, focus)
                        onState(result.state)
                        onNotice(result.message)
                    },
                    label = { Text(focus.label) },
                )
            }
        }
        Text(pulse.focus.description, color = Mist, fontSize = 11.sp)

        pulse.pendingDecision?.let { decision ->
            HorizontalDivider(color = Gold.copy(alpha = .35f))
            Text(decision.title, color = PaleGold, fontWeight = FontWeight.Bold)
            Text(decision.text, color = Color.White, fontSize = 13.sp)
            Text(
                "Entscheidung bis Tag ${decision.expiresDay} · Druckstufe ${decision.intensity}",
                color = pressureColor,
                fontSize = 11.sp,
            )
            CampaignPulseEngine.choices(state, decision).forEach { choice ->
                SmallAction("${choice.label} · ${choice.detail}") {
                    val result = CampaignPulseEngine.resolve(state, choice.id)
                    onState(result.state)
                    onNotice(result.message)
                }
            }
        } ?: Text(
            "Der Hof hat gerade keine akute Grundsatzfrage. Alle vier Tage entsteht aus deiner tatsächlichen Reichslage eine neue Entscheidung.",
            color = Mist,
            fontSize = 12.sp,
        )

        if (pulse.prosperityDays > 0 || pulse.supplyReliefDays > 0 || pulse.defenseReadinessDays > 0) {
            val active = buildList {
                if (pulse.prosperityDays > 0) add("Wohlstand ${pulse.prosperityDays} T.")
                if (pulse.supplyReliefDays > 0) add("Versorgung ${pulse.supplyReliefDays} T.")
                if (pulse.defenseReadinessDays > 0) add("Bereitschaft ${pulse.defenseReadinessDays} T.")
            }
            Text("Aktive Folgen: ${active.joinToString(" · ")}", color = Success, fontSize = 11.sp)
        }
        pulse.recentOutcomes.lastOrNull()?.let {
            Text("Zuletzt: $it", color = Gold, fontSize = 11.sp)
        }
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
    val rooms = listOf(
        Triple("Thronsaal", "Reichsentscheidungen, Audienzen und Grundsatzfragen", GameDestination.DECISIONS),
        Triple("Kriegsrat", "Strategie, Mitregentin und offene Ratsfragen", GameDestination.COUNCIL),
        Triple("Privatgemächer", "Beziehung, gemeinsame Zeit und Erinnerungen", GameDestination.RULERS),
        Triple("Familie", "Dynastie, Nachfolge und Haus des Herrschers", GameDestination.FAMILY),
        Triple("Hof", "Ämter, Persönlichkeiten und politische Netzwerke", GameDestination.COURT),
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RulerPairHero(state) }
        item {
            PremiumPanel(emphasized = true) {
                Text("PALASTBEZIRK", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Text(
                    if (state.relationship.romanceStage == RomanceStage.CO_RULERS)
                        "Das politische Herz eurer gemeinsamen Herrschaft."
                    else
                        "Audienzen, Rat und das persönliche Leben des Herrscherhauses.",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Offene Ratsfragen: ${CoRulerEngine.councilCases(state).size} · Hofämter frei: ${CourtOffice.entries.count { it !in state.court.offices }}",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
        items(rooms, key = { it.first }) { room ->
            SituationCard(
                eyebrow = "Palast",
                title = room.first,
                detail = room.second,
                urgent = room.third == GameDestination.COUNCIL && CoRulerEngine.councilCases(state).isNotEmpty(),
                onClick = { onNavigate(room.third) },
            )
        }
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
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = StoneRaised,
        shape = RoundedCornerShape(19.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.1.sp)
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
