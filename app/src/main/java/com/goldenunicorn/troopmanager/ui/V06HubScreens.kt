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
internal fun HubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    ModernTabStrip(labels, selected.coerceIn(0, labels.lastIndex.coerceAtLeast(0)), onSelect,
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

