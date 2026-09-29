package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.GameState

@Composable
internal fun ChronicleScreen(state: GameState, onMenu: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageTitle(
                "CHRONIK",
                state.victories.toString() + " Siege · " + state.defeats + " Niederlagen · Tag " + state.day
            )
        }
        items(state.chronicle.asReversed()) { entry ->
            Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Tag " + entry.day + " · " + entry.title,
                        color = PaleGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(entry.text, color = Mist, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
                Text("Zum Hauptmenü")
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Spielstand löschen", color = Danger)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Spielstand wirklich löschen?") },
            text = { Text("Das aktuelle Reich wird dauerhaft aus dem lokalen Speicher entfernt.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Löschen", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}
