package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
internal fun MoreScreen(
    state: GameState,
    onMenu: () -> Unit,
    onDelete: () -> Unit,
    onReplayTutorial: () -> Unit
) {
    var page by remember { mutableStateOf("help") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageTitle("MEHR", "Hilfe, Chronik und Spielstand") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("help" to "Hilfe", "chronicle" to "Chronik", "save" to "Spielstand").forEach { entry ->
                    FilterChip(
                        selected = page == entry.first,
                        onClick = { page = entry.first },
                        label = { Text(entry.second) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when (page) {
            "help" -> {
                item {
                    HelpCard(
                        "Die Grundidee",
                        "Du startest als unbedeutender Kämpfer. Verdiene Ruhm, bilde Armeen aus, kaufe Land und entwickle dein Grenzlager zu einem Reich."
                    )
                }
                item {
                    HelpCard(
                        "Ressourcen",
                        "Gold bezahlt Ausbildung und Ausbau. Nahrung versorgt Reich und Heer. Holz wird für Bauten gebraucht. Stein entsteht vor allem im Steinbruch und ist für Mauern, Türme und große Projekte wichtig. Eisen wird für Waffen und Eliteeinheiten benötigt."
                    )
                }
                item {
                    HelpCard(
                        "Gebäude",
                        "Bauernhof = Nahrung. Sägewerk = Holz. Steinbruch = Stein. Eisenmine & Schmiede = Eisen. Markt = Gold. Kaserne = schnellere Ausbildung. Mauern und Türme = Verteidigung. Residenz = Herrschaft und Entwicklung deiner Hauptstadt."
                    )
                }
                item {
                    HelpCard(
                        "Armee & Ausbildung",
                        "Öffne in der Armee zuerst Menschen, Waldelben, Goldelben oder Mauerlegion. Danach wählst du z. B. Bogenschützen und 10, 25, 50 oder 100 Prozent der aktuell verfügbaren Rekruten. Normale Soldaten bleiben Regimenter; nur wichtige Kommandanten sind einzelne Charaktere."
                    )
                }
                item {
                    HelpCard(
                        "Kommandanten",
                        "Ab 100 aktiven Soldaten kannst du besondere Soldaten befördern. Sie besitzen eigene Werte und Bilder. Weise ihnen ein Regiment derselben Kultur zu, damit ihr Führungs- und Taktikbonus im Kampf stärker zählt."
                    )
                }
                item {
                    HelpCard(
                        "Gefährtin",
                        "Nach einigen Tagen begegnet dir eine Soldatin. Vertrauen, Respekt und Zuneigung entwickeln sich getrennt. Sie kann trainiert werden, eigene Verantwortung übernehmen und später Mitregentin werden."
                    )
                }
                item {
                    HelpCard(
                        "Schlachten",
                        "Deine Kampfkraft entsteht aus Einheitentypen, Erfahrung, Moral, Kommandanten, Gefährtin und Taktik. Mauern und Türme helfen besonders bei befestigter Verteidigung. Monster verursachen höhere Verluste als einfache Ork-Raubzüge."
                    )
                }
                item {
                    HelpCard(
                        "Völker",
                        "Menschen sind günstig und zahlreich. Waldelben sind schnelle Fernkämpfer. Goldelben sind seltene Elite. Die Mauerlegion besitzt spezialisierte Korps bis hin zur Drachenartillerie. Halbelben können alle Kulturen direkt vereinen."
                    )
                }
                item {
                    GoldButton("Tutorial erneut anzeigen", onReplayTutorial, Modifier.fillMaxWidth())
                }
                item {
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Bildnachweise", color = PaleGold, fontWeight = FontWeight.Bold)
                            Text(
                                "Die vier großen Armee-Kategoriebilder stammen aus frei lizenzierten Wikimedia-Commons-Quellen. Genaue Urheber- und Lizenzangaben befinden sich zusätzlich in CREDITS.md im Repository.",
                                color = Mist,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            "chronicle" -> {
                items(state.chronicle.asReversed().size) { index ->
                    val entry = state.chronicle.asReversed()[index]
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Tag " + entry.day + " · " + entry.title, color = PaleGold, fontWeight = FontWeight.Bold)
                            Text(entry.text, color = Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            else -> {
                item {
                    HelpCard(
                        "Lokaler Spielstand",
                        "Das Spiel speichert automatisch auf deinem Gerät. Es benötigt kein Konto, keinen Server und keine Internetverbindung."
                    )
                }
                item {
                    OutlinedButton(onClick = onMenu, modifier = Modifier.fillMaxWidth()) {
                        Text("Zum Hauptmenü")
                    }
                }
                item {
                    var askDelete by remember { mutableStateOf(false) }
                    TextButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Spielstand löschen", color = Danger)
                    }
                    if (askDelete) {
                        AlertDialog(
                            onDismissRequest = { askDelete = false },
                            title = { Text("Spielstand löschen?") },
                            text = { Text("Dein aktuelles Reich wird dauerhaft entfernt.") },
                            confirmButton = {
                                TextButton(onClick = onDelete) { Text("Löschen", color = Danger) }
                            },
                            dismissButton = {
                                TextButton(onClick = { askDelete = false }) { Text("Abbrechen") }
                            }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HelpCard(title: String, body: String) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(body, color = Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
internal fun TutorialDialog(onFinish: () -> Unit, onSkip: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
        Triple("Willkommen an der Letzten Mauer", "Du beginnst klein. Dein Ziel ist nicht nur eine Schlacht zu gewinnen, sondern ein eigenes Reich aufzubauen.", "1 / 7"),
        Triple("1. Ressourcen", "Dein Reich lebt von Gold, Nahrung, Holz, Stein und Eisen. Besonders wichtig: Stein kommt zuverlässig aus dem neuen Steinbruch.", "2 / 7"),
        Triple("2. Armee", "Öffne die Armee und tippe zuerst eine der vier Kulturen an. Dort verteilst du 10, 25, 50 oder 100 Prozent der Rekruten auf konkrete Ausbildungen.", "3 / 7"),
        Triple("3. Zeit", "Ausbildung und Wirtschaft brauchen Tage. Mit 'Nächsten Tag beginnen' läuft Produktion weiter und fertige Ausbildungen werden zu Regimentern.", "4 / 7"),
        Triple("4. Kommandanten", "Normale Soldaten bleiben übersichtliche Verbände. Nur besondere Soldaten werden zu individuellen Kommandanten und können Regimentern zugeteilt werden.", "5 / 7"),
        Triple("5. Welt & Kampf", "Missionen bringen früh Gold und Ruhm. Große Schlachten nutzen deine Regimenter, Moral, Taktik, Mauern und Führungskräfte.", "6 / 7"),
        Triple("6. Dein Reich", "Kaufe Land, baue Produktion und Verteidigung aus und entwickle deine Gefährtin zur Kommandantin oder späteren Mitregentin. Alle Erklärungen findest du jederzeit unter 'Mehr'.", "7 / 7")
    )
    val current = steps[step]

    AlertDialog(
        onDismissRequest = onSkip,
        title = {
            Column {
                Text(current.third, color = Gold, fontSize = 11.sp)
                Text(current.first)
            }
        },
        text = { Text(current.second) },
        confirmButton = {
            Button(
                onClick = {
                    if (step >= steps.lastIndex) onFinish() else step++
                }
            ) {
                Text(if (step >= steps.lastIndex) "Los geht's" else "Weiter")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Überspringen") }
        }
    )
}
