package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.ActEngine
import com.goldenunicorn.troopmanager.engine.AdvisorEngine
import com.goldenunicorn.troopmanager.engine.HeroineSagaEngine
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.model.*

/** The five main areas of v1.2. Every older destination lives in exactly one of them. */
internal enum class MainArea(val label: String) { TODAY("Heute"), REALM("Reich"), ARMY("Heer"), COURT("Hof"), CITY("Stadt") }

internal val realmTabs = listOf("Kronlande", "Könige", "Weltkarte", "Diplomatie", "Handel", "Edikte")
internal val armyTabs = listOf("Truppen", "Heerbann", "Kommandanten", "Missionen", "Kampfkunst", "Lazarett", "Arsenal", "Grenze")
internal val courtTabs = listOf("Partnerin", "Herrscherpaar", "Familie", "Ämter", "Hofnetz", "Rat", "Profil", "Fertigkeiten")

/** Maps every destination of older versions onto an area and a tab. */
internal fun areaFor(destination: GameDestination): Pair<MainArea, Int> = when (destination) {
    GameDestination.KINGDOM -> MainArea.REALM to 0
    GameDestination.WORLD -> MainArea.REALM to 2
    GameDestination.MILITARY -> MainArea.ARMY to 0
    GameDestination.MISSIONS -> MainArea.ARMY to 3
    GameDestination.HOSPITAL -> MainArea.ARMY to 5
    GameDestination.FRONTIER -> MainArea.ARMY to 7
    GameDestination.COURT -> MainArea.COURT to 3
    GameDestination.RULERS -> MainArea.COURT to 1
    GameDestination.FAMILY -> MainArea.COURT to 2
    GameDestination.COUNCIL -> MainArea.COURT to 5
    GameDestination.CHARACTER -> MainArea.COURT to 6
    GameDestination.CITY -> MainArea.CITY to 0
    else -> MainArea.TODAY to 0
}

@Composable
internal fun TodayScreen(
    state: GameState,
    onAdvanceDay: () -> Unit,
    onOpen: (GameDestination, Int) -> Unit,
) {
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    BigStat("Heer", fmt(state.armySize), Modifier.weight(1f), Crimson)
                    BigStat("Gold/Tag", signed(net.gold), Modifier.weight(1f), Gold)
                    BigStat("Nahrung/Tag", signed(net.food), Modifier.weight(1f), if (net.food < 0) Crimson else Emerald)
                }
                GoldButton("Tag beenden", onAdvanceDay, Modifier.fillMaxWidth())
            }
        }
        if (urgent.isNotEmpty()) {
            item { Heading("Jetzt wichtig", "Diese Dinge kosten dich etwas, wenn du sie liegen lässt.") }
            items(urgent.size, key = { "u$it" + urgent[it].title }) { i -> val a = urgent[i]; Box(Modifier.animateItem()) { AdviceCard(a) { onOpen(a.destination, a.tab) } } }
        } else item {
            Heading("Alles ruhig", "Keine akuten Gefahren. Nutze den Tag für Expansion, Handel oder deine Partnerin.")
        }
        if (chances.isNotEmpty()) {
            item { Heading("Chancen & Hinweise") }
            items(chances.size, key = { "c$it" + chances[it].title }) { i -> val a = chances[i]; Box(Modifier.animateItem()) { AdviceCard(a) { onOpen(a.destination, a.tab) } } }
        }
        item { Heading("Mehr Überblick") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ModernActionTile("Lagezentrum", "Ziele, Dilemmata, Bericht", ModernBlue, { onOpen(GameDestination.COMMAND, 0) }, Modifier.weight(1f))
                ModernActionTile("Kronlande", "Karte & Feldzüge", Gold, { onOpen(GameDestination.KINGDOM, 0) }, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ModernActionTile("Aufgaben", "Journal aller offenen Ziele", Emerald, { onOpen(GameDestination.JOURNAL, 0) }, Modifier.weight(1f))
                ModernActionTile("Chronik", "Was bisher geschah", Muted, { onOpen(GameDestination.CHRONICLE, 0) }, Modifier.weight(1f))
            }
        }
    }
}

private fun signed(value: Int): String = (if (value > 0) "+" else "") + fmt(value)

@Composable
private fun HubFrame(tabs: List<String>, tab: Int, onTab: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HubTabs(tabs, tab, onTab)
        Box(Modifier.weight(1f)) { content(tab.coerceIn(0, tabs.lastIndex)) }
    }
}

@Composable
private fun ScrollPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l), content = content)
}

@Composable
internal fun RealmArea(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, tab: Int, onTab: (Int) -> Unit) =
    HubFrame(realmTabs, tab, onTab) { page ->
        when (page) {
            0 -> CrownPanel(state, onState, onNotice, CrownSection.LANDS)
            1 -> RivalsPanel(state, onState, onNotice)
            2 -> CampaignMapScreen(state, onState, onNotice)
            3 -> KingdomsScreen(state, onState, onNotice)
            4 -> CrownPanel(state, onState, onNotice, CrownSection.TRADE)
            else -> CrownPanel(state, onState, onNotice, CrownSection.EDICTS)
        }
    }

@Composable
internal fun ArmyArea(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, tab: Int, onTab: (Int) -> Unit) =
    HubFrame(armyTabs, tab, onTab) { page ->
        when (page) {
            0 -> ArmyScreen(state, onState, onNotice)
            1 -> CrownPanel(state, onState, onNotice, CrownSection.LEVY)
            2 -> CommanderDirectoryScreen(state, onState, onNotice)
            3 -> WorldScreen(state, onState, onNotice)
            4 -> CrownPanel(state, onState, onNotice, CrownSection.ARTS)
            5 -> ScrollPanel { WarManagementPanel(state, onState, onNotice) }
            6 -> ScrollPanel { MilitaryLogisticsPanel(state, onState, onNotice) }
            else -> FrontierScreen(state, onState, onNotice)
        }
    }

@Composable
internal fun CourtArea(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, tab: Int, onTab: (Int) -> Unit) =
    HubFrame(courtTabs, tab, onTab) { page ->
        when (page) {
            0 -> CrownPanel(state, onState, onNotice, CrownSection.HEROINES)
            1 -> RulerPairScreen(state, onState, onNotice) { onTab(5) }
            2 -> FamilyScreen(state, onState, onNotice)
            3 -> PeopleCourtScreen(state, onState, onNotice)
            4 -> CourtNetworkScreen(state, onState, onNotice)
            5 -> CouncilScreen(state, onState, onNotice)
            6 -> CourtScreen(state, onState, onNotice)
            else -> CharacterSkillsScreen(state, onState, onNotice)
        }
    }
