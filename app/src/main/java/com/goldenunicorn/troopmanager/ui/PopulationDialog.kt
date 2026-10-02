package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.Culture
import com.goldenunicorn.troopmanager.model.GameState

@Composable
fun PopulationDialog(state: GameState, onDismiss: () -> Unit) {
    val rows = Culture.entries.map { culture ->
        val total = state.population.count(culture)
        val children = total * 28 / 100
        val adults = total - children
        val men = adults * 49 / 100
        val women = adults - men
        val emergency = men * 62 / 100 + women * 18 / 100
        culture to listOf(total, men, women, children, emergency)
    }
    val men = rows.sumOf { it.second[1] }
    val women = rows.sumOf { it.second[2] }
    val children = rows.sumOf { it.second[3] }
    val emergency = rows.sumOf { it.second[4] }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Bevölkerung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.population.total} Einwohner", color = Color.White, fontWeight = FontWeight.Bold)
                Text("$men Männer · $women Frauen · $children Kinder", color = PaleGold, fontSize = 13.sp)
                Text("Im Notfall kampffähig: $emergency. Kinder zählen nicht, von den Frauen nur ein kleiner Teil.", color = Mist, fontSize = 12.sp)
                rows.forEach { (culture, values) ->
                    Text("${culture.label} · ${values[0]}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${values[1]} Männer, ${values[2]} Frauen, ${values[3]} Kinder · Notensatz ${values[4]}", color = Mist, fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { values[0] / state.population.total.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Gold,
                    )
                }
            }
        },
    )
}
