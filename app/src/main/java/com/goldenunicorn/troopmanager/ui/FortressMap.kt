package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.model.BuildingType
import com.goldenunicorn.troopmanager.model.GameState

/** Compatibility seam for callers outside the full-screen city tab. */
@Composable
internal fun FortressMap(state: GameState, onBuilding: (BuildingType) -> Unit = {}) {
    Surface(color = Panel) {
        CityScene(state, modifier = Modifier.fillMaxWidth().height(400.dp), season = state.city.season, onBuilding = onBuilding)
    }
}
