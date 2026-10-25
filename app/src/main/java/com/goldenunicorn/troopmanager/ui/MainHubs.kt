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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
        // Tabs sit on a strip of the area's painting: atmosphere without costing screen height.
        Box(Modifier.fillMaxWidth()) {
            ArtImage(areaArt(area), Modifier.matchParentSize(), areaArtAlignment(area))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = .45f), Ink.copy(alpha = .78f), Ink))))
            Column {
                HubTabs(tabs.mapIndexed { i, t -> tabLabel(ctx.state, area, i, t.label) }, page, onTab)
                if (subs.isNotEmpty()) SubSwitch(subs, subPage, onSub)
            }
        }
        Box(Modifier.weight(1f)) { HubPage(area, page, subPage, ctx) }
    }
}

internal fun areaArt(area: MainArea): String = when (area) {
    MainArea.TODAY -> "menu_cover.webp"
    MainArea.REALM -> "world_map.webp"
    MainArea.ARMY -> "category_human.webp"
    MainArea.COURT -> "frontier/bond_train.webp"
    MainArea.CITY -> "city_landscape.webp"
}

private fun areaArtAlignment(area: MainArea): Alignment = when (area) {
    MainArea.TODAY -> BiasAlignment(0f, -0.35f)
    MainArea.COURT -> BiasAlignment(0f, -0.6f)
    else -> Alignment.Center
}

/** A bundled painting from the assets folder. */
@Composable
internal fun ArtImage(asset: String, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    coil.compose.AsyncImage(
        model = "file:///android_asset/$asset",
        contentDescription = null,
        modifier = modifier,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        alignment = alignment,
    )
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
                    .background(if (active) Gold else Color.Black.copy(alpha = .45f))
                    .clickable { onSelect(i) }
                    .semantics { this.selected = active }
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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
            HeroCard("menu_cover.webp", BiasAlignment(0f, -0.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Portrait("knight", state.player.portraitUri, 60, state.crown.playerLook)
                    if (state.companion.met) Portrait(HeroineSagaEngine.companionDef(state)?.portraitKey ?: "companion", state.companion.portraitUri, 60, HeroineSagaEngine.companionLook(state))
                    Column(Modifier.weight(1f)) {
                        Text("TAG ${state.day}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        ActEngine.current(state)?.let { Text("Akt ${it.number}: ${it.title}", color = PaleGold, fontSize = 12.sp) }
                    }
                }
                Text("${state.player.name}, ${state.title}", color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Cinzel)
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
            val key = item.portraitKey
            when {
                key == "companion" -> Portrait(HeroineSagaEngine.companionDef(state)?.portraitKey ?: "companion", state.companion.portraitUri, 48, HeroineSagaEngine.companionLook(state))
                key != null -> Portrait(key, null, 48)
                else -> ArtImage(inboxArt(item), Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
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

/** A painting that fits the kind of decision. */
private fun inboxArt(item: InboxEngine.Item): String = when (item.source) {
    InboxEngine.Source.WEEKLY -> when {
        "ORC_STORM" in item.key -> "frontier/horde_orc.webp"
        "HARVEST" in item.key -> "city_landscape.webp"
        "TOURNAMENT" in item.key -> "category_human.webp"
        "MERCHANTS" in item.key -> "frontier/ally_gold.webp"
        "PLAGUE" in item.key -> "frontier/ally_holds.webp"
        else -> "category_gold_elf.webp"
    }
    InboxEngine.Source.FRONTIER -> "frontier/wall_crown.webp"
    InboxEngine.Source.REALM -> "category_human.webp"
    InboxEngine.Source.WORLD -> "frontier/ally_holds.webp"
    InboxEngine.Source.CAMPAIGN -> "world_map.webp"
    InboxEngine.Source.COMMANDER -> "category_wall.webp"
    InboxEngine.Source.RELATIONSHIP -> "frontier/bond_train.webp"
    InboxEngine.Source.FAMILY -> "frontier/bond_train.webp"
    InboxEngine.Source.STORY, InboxEngine.Source.DEMAND -> "city_landscape.webp"
    InboxEngine.Source.COUNCIL -> "category_gold_elf.webp"
}

/** A card with a painting behind its content, used for the big hero moments of a screen. */
@Composable
internal fun HeroCard(asset: String, alignment: Alignment = Alignment.Center, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().clip(shape).border(1.dp, Gold.copy(alpha = .30f), shape)) {
        ArtImage(asset, Modifier.matchParentSize(), alignment)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = .35f), Ink.copy(alpha = .80f), Ink.copy(alpha = .95f)))))
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

internal fun signed(value: Int): String = (if (value > 0) "+" else "") + fmt(value)
