package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.ActEngine
import com.goldenunicorn.troopmanager.engine.AdvisorEngine
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.HeroineSagaEngine
import com.goldenunicorn.troopmanager.engine.InboxEngine
import com.goldenunicorn.troopmanager.model.*

/** Everything a hub needs to render its pages and jump elsewhere. */
internal class HubContext(
    val state: GameState,
    val onState: (GameState) -> Unit,
    val onNotice: (String) -> Unit,
    val open: (Spot) -> Unit,
    val onAdvanceDay: () -> Unit,
    val onOpenScene: () -> Unit,
) {
    fun legacy(destination: GameDestination) = open(Navigation.spotFor(destination))
}

/** One area: its tab strip, an optional sub-view switch and the page itself. */
@Composable
internal fun AreaHub(area: MainArea, tab: Int, sub: Int, onTab: (Int) -> Unit, onSub: (Int) -> Unit, ctx: HubContext) {
    val tabs = Navigation.tabs.getValue(area)
    val page = tab.coerceIn(0, tabs.lastIndex)
    val subs = tabs[page].subs
    val subPage = sub.coerceIn(0, (subs.size - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        HubTabs(tabs.mapIndexed { i, t -> tabLabel(ctx.state, area, i, t.label) }, page, onTab)
        if (subs.isNotEmpty()) SubSwitch(subs, subPage, onSub)
        Box(Modifier.weight(1f)) { HubPage(area, page, subPage, ctx) }
    }
}

private fun tabLabel(state: GameState, area: MainArea, index: Int, label: String): String =
    if (area == MainArea.TODAY && index == 0) InboxEngine.count(state).let { if (it > 0) "$label · $it" else label } else label

@Composable
internal fun SubSwitch(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Text(
                label,
                color = if (active) Ink else Mist,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (active) Gold else StoneRaised)
                    .clickable { onSelect(i) }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun ScrollPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m), content = content)
}

@Composable
private fun HubPage(area: MainArea, tab: Int, sub: Int, ctx: HubContext) {
    val s = ctx.state
    when (area) {
        MainArea.TODAY -> when (tab) {
            0 -> TodayScreen(ctx)
            1 -> if (sub == 0) CommandOverview(s, ctx::legacy, ctx.onAdvanceDay, ctx.onState, ctx.onNotice) else DailyReportList(s, ctx::legacy)
            2 -> QuestJournalScreen(s, ctx::legacy)
            else -> ChronicleScreen(s)
        }
        MainArea.REALM -> when (tab) {
            0 -> CrownPanel(s, ctx.onState, ctx.onNotice, CrownSection.LANDS)
            1 -> if (sub == 0) RivalsPanel(s, ctx.onState, ctx.onNotice) else KingdomsScreen(s, ctx.onState, ctx.onNotice)
            2 -> if (sub == 0) CrownPanel(s, ctx.onState, ctx.onNotice, CrownSection.TRADE)
                 else ScrollPanel { MarketPanel(s) { kind, amount, buy -> com.goldenunicorn.troopmanager.engine.CityEngine.trade(s, kind, amount, buy).let { ctx.onState(it.state); ctx.onNotice(it.message) } } }
            else -> CrownPanel(s, ctx.onState, ctx.onNotice, CrownSection.EDICTS)
        }
        MainArea.ARMY -> when (tab) {
            0 -> FrontScreen(ctx)
            1 -> when (sub) {
                0 -> ArmyScreen(s, ctx.onState, ctx.onNotice)
                1 -> CrownPanel(s, ctx.onState, ctx.onNotice, CrownSection.LEVY)
                2 -> MilitaryLogisticsPanel(s, ctx.onState, ctx.onNotice)
                else -> ScrollPanel { WarManagementPanel(s, ctx.onState, ctx.onNotice) }
            }
            2 -> if (sub == 0) CommanderDirectoryScreen(s, ctx.onState, ctx.onNotice) else WorldScreen(s, ctx.onState, ctx.onNotice)
            else -> CrownPanel(s, ctx.onState, ctx.onNotice, CrownSection.ARTS)
        }
        MainArea.COURT -> when (tab) {
            0 -> if (sub == 0) CrownPanel(s, ctx.onState, ctx.onNotice, CrownSection.HEROINES) else RulerPairScreen(s, ctx.onState, ctx.onNotice) { ctx.open(Spot.COUNCIL) }
            1 -> FamilyScreen(s, ctx.onState, ctx.onNotice)
            2 -> when (sub) {
                0 -> CouncilScreen(s, ctx.onState, ctx.onNotice)
                1 -> PeopleCourtScreen(s, ctx.onState, ctx.onNotice)
                else -> CourtNetworkScreen(s, ctx.onState, ctx.onNotice)
            }
            else -> if (sub == 0) CourtScreen(s, ctx.onState, ctx.onNotice) else CharacterSkillsScreen(s, ctx.onState, ctx.onNotice)
        }
        MainArea.CITY -> when (tab) {
            0 -> CityScreen(s, ctx.onState, ctx.onNotice, ctx.onAdvanceDay, onNavigate = { type ->
                when (type) {
                    BuildingType.BARRACKS, BuildingType.STABLES -> ctx.open(Spot.TROOPS)
                    BuildingType.ARSENAL -> ctx.open(Spot.ARSENAL)
                    BuildingType.HOSPITAL -> ctx.open(Spot.HOSPITAL)
                    BuildingType.PALACE -> ctx.open(Spot.OFFICES)
                    BuildingType.ACADEMY -> ctx.open(Spot.RESEARCH)
                    BuildingType.EMBASSY -> ctx.open(Spot.POWERS)
                    BuildingType.WALL, BuildingType.TOWER -> ctx.open(Spot.FRONT)
                    else -> Unit
                }
            })
            1 -> WondersScreen(s, ctx.onState, ctx.onNotice)
            else -> ResearchPanel(s, ctx.onState, ctx.onNotice)
        }
    }
}

// ---------------------------------------------------------------- Heute

@Composable
internal fun TodayScreen(ctx: HubContext) {
    val state = ctx.state
    val inbox = remember(state) { InboxEngine.items(state) }
    val advice = remember(state) { AdvisorEngine.advice(state) }
    val urgent = advice.filter { it.severity == AdvisorEngine.Severity.CRITICAL || it.severity == AdvisorEngine.Severity.WARNING }
    val chances = advice.filterNot { it in urgent }
    val net = remember(state) { EconomyEngine.production(state).net }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.m, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            PremiumPanel(emphasized = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Portrait("knight", state.player.portraitUri, 60, state.crown.playerLook)
                    if (state.companion.met) Portrait(HeroineSagaEngine.companionDef(state)?.portraitKey ?: "companion", state.companion.portraitUri, 60, HeroineSagaEngine.companionLook(state))
                    Column(Modifier.weight(1f)) {
                        Text("TAG ${state.day}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        ActEngine.current(state)?.let { Text("Akt ${it.number}: ${it.title}", color = PaleGold, fontSize = 12.sp) }
                    }
                }
                Text("${state.player.name}, ${state.title}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                val partner = if (state.companion.met) " · an deiner Seite: ${state.companion.name}" else ""
                Text("${state.realm.settlementName} · ${state.crown.ownedProvinces.size} Kronprovinzen$partner", color = Mist, fontSize = 14.sp)
                ActEngine.current(state)?.takeIf { !ActEngine.goalsMet(state) }?.let {
                    val open = ActEngine.goals(state).filterNot { g -> g.done }
                    Text("Nächstes Ziel: " + open.take(2).joinToString(" · ") { g -> "${g.text} ${fmt(g.current)}/${fmt(g.target)}" }, color = Gold, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    BigStat("Heer", fmt(state.armySize), Modifier.weight(1f), Crimson)
                    BigStat("Gold/Tag", signed(net.gold), Modifier.weight(1f), Gold)
                    BigStat("Nahrung/Tag", signed(net.food), Modifier.weight(1f), if (net.food < 0) Crimson else Emerald)
                }
                GoldButton("Tag beenden", ctx.onAdvanceDay, Modifier.fillMaxWidth())
            }
        }
        state.crown.saga.pendingScene?.let { scene ->
            item(key = "scene") {
                AdviceCard(AdvisorEngine.Advice(AdvisorEngine.Severity.CRITICAL, scene.title, "Eine Szene bei ${scene.location} wartet. Tippe, um sie zu öffnen.", Spot.INBOX), ctx.onOpenScene)
            }
        }
        if (inbox.isNotEmpty()) {
            item { Heading("Entscheidungen · ${inbox.size}", "Unbeantwortete Fragen entscheiden die Räte nach ${InboxEngine.AUTO_DAYS} Tagen selbst – meist vorsichtig.") }
            inbox.forEach { entry ->
                item(key = entry.key) {
                    Box(Modifier.animateItem()) {
                        InboxCard(state, entry, onOpen = { ctx.open(it) }) { option ->
                            val result = InboxEngine.choose(state, entry.key, option)
                            ctx.onState(result.state); ctx.onNotice(result.message)
                        }
                    }
                }
            }
        }
        if (urgent.isNotEmpty()) {
            item { Heading("Jetzt wichtig", "Diese Dinge kosten dich etwas, wenn du sie liegen lässt.") }
            urgent.forEach { a -> item(key = "u" + a.title) { Box(Modifier.animateItem()) { AdviceCard(a) { ctx.open(a.spot) } } } }
        } else if (inbox.isEmpty()) item {
            Heading("Alles ruhig", "Keine akuten Gefahren. Nutze den Tag für Expansion, Handel oder deine Partnerin.")
        }
        if (chances.isNotEmpty()) {
            item { Heading("Chancen & Hinweise") }
            chances.forEach { a -> item(key = "c" + a.title) { Box(Modifier.animateItem()) { AdviceCard(a) { ctx.open(a.spot) } } } }
        }
    }
}

@Composable
private fun InboxCard(state: GameState, item: InboxEngine.Item, onOpen: (Spot) -> Unit, onChoose: (String) -> Unit) {
    var expanded by remember(item.key) { mutableStateOf(item.urgent) }
    val accent = if (item.urgent) Crimson else Gold
    val shape = RoundedCornerShape(18.dp)
    val since = state.reign.inboxSince[item.key]
    Column(
        Modifier.fillMaxWidth().clip(shape).background(StoneRaised).border(1.dp, accent.copy(alpha = .3f), shape)
            .clickable { expanded = !expanded }.animateContentSize().padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item.portraitKey?.let {
                if (it == "companion") Portrait(HeroineSagaEngine.companionDef(state)?.portraitKey ?: "companion", state.companion.portraitUri, 40, HeroineSagaEngine.companionLook(state))
                else Portrait(it, null, 40)
            }
            Column(Modifier.weight(1f)) {
                Text(item.source.label.uppercase() + (since?.let { s -> if (item.autoSettles) " · noch ${(InboxEngine.AUTO_DAYS - (state.day - s)).coerceAtLeast(0)} T." else "" } ?: ""),
                    color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (expanded) "–" else "+", color = accent, fontSize = 22.sp)
        }
        if (expanded) {
            Text(item.text, color = Mist, fontSize = 13.sp, lineHeight = 18.sp)
            if (item.openSpot != null) OutlinedButton({ onOpen(item.openSpot) }, Modifier.fillMaxWidth()) { Text("Öffnen") }
            item.options.forEach { option ->
                Surface(
                    onClick = { onChoose(option.id) },
                    color = Color(0xFF17212B), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(option.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (option.detail.isNotBlank()) Text(option.detail, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

internal fun signed(value: Int): String = (if (value > 0) "+" else "") + fmt(value)
