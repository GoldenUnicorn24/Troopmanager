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
        HubTabs(listOf("Menschen & Hof", "Profile & Attribute"), page) { page = it }
        ContextTutorialCard(state, "court", onState)
        Box(Modifier.weight(1f)) {
            if (page == 0) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                PeopleCourtScreen(state, onState, onNotice)
            } else CourtScreen(state, onState, onNotice)
        }
    }
}

@Composable
internal fun ArmyHubScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HubTabs(listOf("Armee", "Lazarett & Versorgung"), page) { page = it }
        ContextTutorialCard(state, "army", onState)
        Box(Modifier.weight(1f)) {
            if (page == 0) ArmyScreen(state, onState, onNotice) else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                WarManagementPanel(state, onState, onNotice)
            }
        }
    }
}

@Composable
private fun HubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label -> FilterChip(selected == index, { onSelect(index) }, label = { Text(label) }) }
    }
}
