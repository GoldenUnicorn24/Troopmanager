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
import com.goldenunicorn.troopmanager.model.GameDestination
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun MoreScreen(
    state: GameState,
    onMenu: () -> Unit,
    onDelete: () -> Unit,
    onReplayTutorial: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    controller: GameViewModel,
    onNavigate: (GameDestination) -> Unit,
) {
    var page by remember { mutableStateOf("areas") }
    var chronicleCategory by remember { mutableStateOf(ChronicleCategory.ALL) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageTitle("MEHR", "Hilfe, Chronik und Spielstand") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("areas" to "Bereiche", "help" to "Hilfe", "chronicle" to "Chronik", "settings" to "Optionen", "save" to "Spielstand").forEach { entry ->
                    FilterChip(
                        selected = page == entry.first,
                        onClick = { page = entry.first },
                        label = { Text(entry.second) },
                    )
                }
            }
        }

        when (page) {
            "areas" -> {
                item { HelpCard("Reichsbereiche", "Seltener benötigte Bereiche liegen hier statt dauerhaft in der Hauptnavigation. Warnungen oben führen direkt zu dringenden Orten.") }
                items(
                    listOf(
                        "Grenze & Mauer" to GameDestination.FRONTIER,
                        "Hof & Ämter" to GameDestination.COURT,
                        "Herrscherpaar" to GameDestination.RULERS,
                        "Familie & Dynastie" to GameDestination.FAMILY,
                        "Charakter & Fertigkeiten" to GameDestination.CHARACTER,
                        "Forschung" to GameDestination.RESEARCH,
                        "Rat" to GameDestination.COUNCIL,
                        "Palast" to GameDestination.PALACE,
                    )
                ) { (label, destination) ->
                    OutlinedButton(onClick = { onNavigate(destination) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Text(label, modifier = Modifier.weight(1f))
                        Text("Öffnen", color = Gold)
                    }
                }
            }
            "settings" -> { item { SettingsPanel(state, onState) } }
            "help" -> {
                items(listOf(
                    "Dein Grenzreich" to "Du beginnst mit einem eigenen Gebiet, einer Grenzfeste, ausgebauten Produktionsgebäuden, Vorräten und einer einsatzfähigen Armee. Entwickle dein Reich bis zum Hochkönig; danach bleiben Invasionen und Ausbau spielbar.",
                    "Wirtschaft & Unterhalt" to "Produktion läuft beim Tageswechsel. Arbeiter, Gebäude und Gebiete liefern Gold, Nahrung, Holz, Stein und Eisen. Die Wirtschaft zeigt Produktion, Armeeunterhalt und täglichen Nettogewinn. Ritter, Goldelben und Artillerie verbrauchen mehr Nahrung. Bei Nahrung 0 sinken Moral, Wachstum und Ausbildungstempo.",
                    "Bevölkerung" to "Gesamtbevölkerung teilt sich in Zivilisten, Soldaten und Auszubildende. Zivilisten arbeiten, stehen als Rekruten bereit oder bleiben frei. Soldaten arbeiten nicht gleichzeitig in der Wirtschaft. Fehlende Arbeiter senken die Produktion.",
                    "Gebäude & Festung" to "Tippe ein Gebäude auf der Festungskarte an, um Details und den Ausbau zu öffnen. Bauprojekte kosten sofort Ressourcen und dauern Tage. Wohnviertel und Lager schaffen Kapazität. Arbeiterprioritäten, Steuern und Stadtwerte beeinflussen die Wirtschaft. Bauernhof, Sägewerk, Steinbruch, Mine und Markt erzeugen Ressourcen. Kasernen beschleunigen Ausbildung; Mauern und Türme helfen gegen Belagerungen. Ein eigener Siedlungsname bleibt beim Ausbau erhalten.",
                    "Armee & Ausbildung" to "Die vier Kulturkarten zeigen Gesamtbestände und Ausbildung. Jeder Einheitentyp besitzt einen gemeinsamen Truppenbestand mit Moral, Erfahrung und Ausrüstung; diese Werte verändern die Kampfkraft. Die Ausbildung verbraucht Rekruten, Gold und Eisen und endet erst nach mehreren Tagen.",
                    "Kommandanten & persönliches Kommando" to "Weise einem Kommandanten konkrete Soldatenzahlen zu. Die Zuweisungsseite zeigt Gesamtbestand, daheim, unterwegs, frei und aktuell zugewiesen. Eingaben und 0 / 25 / 50 / 75 / ALLE verteilen nur verfügbare Soldaten. Doppelzuweisungen sind ausgeschlossen. Freie Soldaten kämpfen unter deinem persönlichen Kommando; Kommandantenboni gelten für ihre Kontingente.",
                    "Missionen mit Truppen" to "Unter Welt → Missionen wählst du Verfügbar, Unterwegs oder Abgeschlossen. Bereite eine Mission mit konkreten Einheiten und optionalem Kommandanten vor. Nahrung bezahlt die Versorgung. Dauer, Risiko und Spezialisierung beeinflussen den Ausgang. Erst der Tageswechsel bringt Fortschritt, Verluste und Belohnungen.",
                    "Soldaten unterwegs" to "Missionstruppen und ihre Kommandanten fehlen in der Festung, bei neuen Zuweisungen und bei weiteren Missionen. Ein Rückruf benötigt Rückreisezeit. Missionen können großen Erfolg, Erfolg, Teilerfolg, Scheitern oder eine Katastrophe bringen. Erfahrung, Moral, Truppentyp und Führung verändern die Chancen.",
                    "Invasionen vorbereiten" to "40% Bedrohung bringt Späherwarnungen, 60% Grenzüberfälle, 75% Heeresbewegungen und 90% eine angekündigte Invasion. Bei voller Bedrohung beginnt ein Angriff. Der Countdown lässt Zeit für Vorräte, Mauerreparaturen, Ausbildung, Rückrufe und verbündete Hilfe. Nur Soldaten daheim verteidigen deine Festung.",
                    "Interaktive Schlachten" to "Verteile daheim verfügbare Truppen und Kommandanten auf linken Flügel, Zentrum, rechten Flügel und Reserve. Die Schlacht läuft schrittweise über Aufstellung, Fernkampf, Kontakt, Hauptkampf, Reserven und Entscheidung. Reagiere auf Ereignisse, setze Reserven ein und entscheide nach einem Sieg über Verfolgung oder Formation halten. Eine laufende Schlacht wird gespeichert.",
                    "Gefährtin & Beziehungen" to "Vertrauen, Respekt und Zuneigung entwickeln sich durch Ereignisse und Entscheidungen. Pro Tag sind höchstens eine große oder zwei kleine Beziehungsaktionen möglich. Freundschaft und freiwillige Romanze verlaufen getrennt; Romantik ist in den Optionen abschaltbar. Die Gefährtin kann als Kommandantin Truppen führen und Missionen übernehmen.",
                    "Kulturen & Charakter" to "Halbelben starten mit allen vier Kulturen. Menschen und Elben benötigen echte Bevölkerung durch diplomatische Begegnungen, Bündnisse und Immigration, bevor fremde Kulturen ausgebildet werden. Missionen, Schlachten und Ereignisse geben Erfahrung; neue Level ermöglichen die Verteilung von Skillpunkten auf wirksame Charakterwerte.",
                    "Offline & Spielstände" to "Alles bleibt auf deinem Gerät: kein Konto, kein Server, keine Echtgeldkäufe. Das Spiel speichert automatisch mit Sicherungskopie. Frühere Spielstände werden beim Laden migriert; alte Truppenbestände werden je Einheitentyp zusammengeführt. Eigene Bilder bleiben lokale Gerätedateien."
                )) { (title, body) -> HelpCard(title, body) }
                item { GoldButton("Tutorial erneut anzeigen", onReplayTutorial, Modifier.fillMaxWidth()) }
                item { HelpCard("Bildnachweise", "Kategoriebilder, Hauptmenü und App-Icon wurden für dieses Spiel erstellt und sind in der APK enthalten. Weitere Nachweise stehen in CREDITS.md.") }
            }

            "chronicle" -> {
                chronicleItems(state, chronicleCategory) { chronicleCategory = it }
            }

            else -> {
                item { val ui by controller.ui.collectAsStateWithLifecycle(); SaveSlotsPanel(ui, controller) }
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
        Triple("1. Dein Gebiet", "Deine Grenzfeste besitzt ab Tag 1 ein eigenes Gebiet, Gebäude, Vorräte und eine stehende Armee. Tippe Gebäude auf der Karte an, um Details und Ausbau zu öffnen.", "1 / 10"),
        Triple("2. Ressourcen", "Gold, Nahrung, Holz, Stein und Eisen werden täglich produziert. Die Wirtschaftsübersicht zeigt Bruttoertrag, Armeeunterhalt und Netto. Nahrungsmangel schwächt dein Reich.", "2 / 10"),
        Triple("3. Bevölkerung", "Arbeiter produzieren, Rekruten sind für Ausbildung verfügbar, freie Bevölkerung bildet deine Reserve. Soldaten und Auszubildende arbeiten nicht zugleich als Zivilisten.", "3 / 10"),
        Triple("4. Armee", "Die Kulturkarten zeigen deine Gesamtbestände. Moral, Erfahrung und Ausrüstung verändern die Stärke. Truppen unterwegs fehlen daheim bei der Verteidigung.", "4 / 10"),
        Triple("5. Ausbildung", "Öffne eine Kultur und wähle einen Einheitentyp. 10, 25, 50 oder 100 Prozent der Rekruten starten eine Ausbildung. Mit dem Tageswechsel werden Aufträge weitergeführt und abgeschlossen.", "5 / 10"),
        Triple("6. Kommandanten", "Weise konkrete Soldatenmengen zu, mit Eingaben oder 0 / 25 / 50 / 75 / ALLE. Jeder Soldat hat nur ein Kommando. Freie Soldaten bleiben unter deinem persönlichen Kommando.", "6 / 10"),
        Triple("7. Missionen", "Welt → Missionen: Wähle Einheiten und optional einen Kommandanten, bezahle Versorgung und starte. Mehrtägige Einsätze bringen reale Verluste und Beute. Unterwegs können Truppen nicht doppelt eingesetzt werden.", "7 / 10"),
        Triple("8. Gefährtin", "Entscheidungen verändern Vertrauen, Respekt und Zuneigung. Pro Tag sind eine große oder zwei kleine Aktionen möglich. Deine Gefährtin kann auch eigene Truppen und Missionen führen.", "8 / 10"),
        Triple("9. Bedrohung", "Späher warnen vor Überfällen und Invasionen. Nutze den Ankunfts-Countdown: Nahrung sichern, Mauern reparieren, ausbilden, Missionen zurückrufen und Verbündete anfordern.", "9 / 10"),
        Triple("10. Schlachten", "Verteile Truppen auf Flügel, Zentrum und Reserve. Die Schlacht entwickelt sich schrittweise: Reagiere auf Ereignisse und setze Reserven ein. Nach dem Sieg entscheidest du über Verfolgung. Hilfe findest du jederzeit unter Mehr.", "10 / 10")
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
