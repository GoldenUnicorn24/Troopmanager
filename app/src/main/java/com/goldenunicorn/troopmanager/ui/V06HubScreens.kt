package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.model.GameState

@Composable
internal fun WorldHubScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, initialPage: Int = 0) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(listOf("Kampagnenkarte", "Missionen", "Reiche & Politik"), page) { page = it }
        ContextTutorialCard(state, if (page == 2) "diplomacy" else "world", onState)
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> CampaignMapScreen(state, onState, onNotice)
                1 -> WorldScreen(state, onState, onNotice)
                else -> KingdomsScreen(state, onState, onNotice)
            }
        }
    }
}

@Composable
internal fun CourtHubScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(listOf("Ämter & Karrieren", "Hofnetzwerk", "Rat"), page) { page = it }
        ContextTutorialCard(state, "court", onState)
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> PeopleCourtScreen(state, onState, onNotice)
                1 -> CourtNetworkScreen(state, onState, onNotice)
                else -> CouncilScreen(state, onState, onNotice)
            }
        }
    }
}

@Composable
internal fun ArmyHubScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, initialPage: Int = 0) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(listOf("Armee", "Kommandanten", "Missionen", "Lazarett", "Arsenal & Doktrinen", "Frontier / Mauer"), page) { page = it }
        ContextTutorialCard(state, "army", onState)
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> ArmyScreen(state, onState, onNotice)
                1 -> CommanderDirectoryScreen(state, onState, onNotice)
                2 -> WorldScreen(state, onState, onNotice)
                3 ->
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        WarManagementPanel(state, onState, onNotice)
                    }
                4 -> MilitaryLogisticsPanel(state, onState, onNotice)
                else -> FrontierScreen(state, onState, onNotice)
            }
        }
    }
}

@Composable
internal fun HubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label -> FilterChip(selected == index, { onSelect(index) }, label = { Text(label) }) }
    }
}

@Composable
internal fun CharacterHubScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(listOf("Profil & Attribute", "Fertigkeiten & Perks"), page) { page = it }
        Box(Modifier.weight(1f)) {
            if (page == 0) CourtScreen(state, onState, onNotice) else CharacterSkillsScreen(state, onState, onNotice)
        }
    }
}
