package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.ArmyEngine
import com.goldenunicorn.troopmanager.engine.FrontierEngine
import com.goldenunicorn.troopmanager.model.Culture
import com.goldenunicorn.troopmanager.model.GameState

@Composable
fun PopulationDialog(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, onDismiss: () -> Unit) {
    val rows = Culture.entries.map { culture ->
        val total = state.population.count(culture)
        val children = total * 28 / 100
        val adults = total - children
        val men = adults * 49 / 100
        val women = adults - men
        val emergency = men * 62 / 100 + women * 18 / 100
        val soldiers = state.armyPools.filter { it.type.culture == culture }.sumOf { it.soldiers }
        culture to listOf(total, men, women, children, emergency, soldiers, state.population.recruits(culture))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Völker") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.population.total} Einwohner", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Förderung zieht Zuzug und Rekruten zu diesem Volk. Das Heer folgt den Rekruten.", color = Mist, fontSize = 12.sp)
                rows.forEach { (culture, values) ->
                    val favor = state.culturePatronage[culture] ?: 0
                    val cost = 500 + favor * 250
                    Text("${culture.label} · ${values[0]}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${values[1]} Männer, ${values[2]} Frauen, ${values[3]} Kinder", color = Mist, fontSize = 12.sp)
                    Text("Nottruppe ${values[4]} · Rekruten ${values[6]} · im Heer ${values[5]}", color = Gold, fontSize = 12.sp)
                    Text("Förderung $favor / 5", color = PaleGold, fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { values[0] / state.population.total.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Gold,
                    )
                    if (favor < 5) OutlinedButton(onClick = {
                        val result = FrontierEngine.patronize(state, culture)
                        onState(result.state)
                        onNotice(result.message)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Fördern · $cost Gold") }
                    Text("Nottruppe nur bei Belagerung, wenn kein Soldat an der Mauer steht. Dann automatisch, schwach, und die Leute fehlen danach.", color = Mist, fontSize = 11.sp)
                }
            }
        },
    )
}
