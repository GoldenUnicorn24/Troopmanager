package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.BuildConfig
import com.goldenunicorn.troopmanager.engine.DevCheatEngine
import com.goldenunicorn.troopmanager.model.GameState

/**
 * Build number row in the options. Tapping it seven times unlocks the hidden developer menu,
 * the same gesture Android uses for its own developer options.
 */
@Composable
internal fun BuildInfoAndDevMenu(state: GameState, onState: (GameState) -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    SectionTitle("Über das Spiel")
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
            if (state.crown.devMode) {
                feedback = "Du bist bereits Entwickler."
                return@clickable
            }
            taps++
            val left = 7 - taps
            when {
                left <= 0 -> {
                    taps = 0
                    feedback = "Entwicklermodus aktiviert!"
                    onState(DevCheatEngine.unlock(state, true))
                }
                left <= 4 -> feedback = "Noch $left Mal tippen, um Entwickler zu werden."
            }
        },
        color = Panel,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Realm of the Last Wall", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME}", color = Mist, fontSize = 13.sp)
            Text("Build-Nummer ${BuildConfig.VERSION_CODE}", color = Mist, fontSize = 13.sp)
        }
    }
    feedback?.let { Text(it, color = PaleGold, fontSize = 13.sp) }
    if (state.crown.devMode) DevCheatPanel(state, onState) { feedback = it }
}

@Composable
private fun DevCheatPanel(state: GameState, onState: (GameState) -> Unit, onFeedback: (String) -> Unit) {
    PremiumPanel(emphasized = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ENTWICKLERMENÜ", color = Crimson, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text("${state.crown.devCheatsUsed} Cheats genutzt", color = Muted, fontSize = 12.sp)
        }
        Text("Nur zum Testen. Cheats werden im Spielstand gezählt.", color = Muted, fontSize = 12.sp)
        DevCheatEngine.Cheat.entries.forEach { cheat ->
            val label = if (cheat == DevCheatEngine.Cheat.GOD_MODE) cheat.label + if (state.crown.godMode) " · AN" else " · AUS" else cheat.label
            OutlinedButton(
                onClick = {
                    val result = DevCheatEngine.apply(state, cheat)
                    onState(result.state)
                    onFeedback(result.message)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(cheat.description, color = Muted, fontSize = 12.sp)
                }
            }
        }
        TextButton(onClick = {
            onState(DevCheatEngine.unlock(state, false))
            onFeedback("Entwicklermodus deaktiviert.")
        }) { Text("Entwicklermodus ausschalten", color = Crimson) }
    }
}
