package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
                        "Krone, Kronlande & Heldinnen" to GameDestination.KINGDOM,
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
                    "Navigation" to "Fünf Bereiche unten: Heute (Überblick und Berater), Reich (Kronlande-Karte, Weltkarte, Diplomatie, Handel, Edikte), Heer (Truppen, Heerbann, Kommandanten, Missionen, Kampfkunst, Lazarett, Arsenal, Grenze), Hof (Partnerin, Herrscherpaar, Familie, Ämter, Rat, Profil) und Stadt. Optionen, Hilfe und Spielstand liegen oben rechts unter den drei Punkten.",
                    "Akte & Endgegner" to "Akt 1: Sichere die Grenzmark und gewinne deine Feldherrin – Endgegner ist ein Ork-Kriegshäuptling. Akt 2: Werde König und unterwirf zwei Rivalen-Könige – Endgegner ist ein Uruk-hai-Kriegsheer. Akt 3: Dringe ins Hordenland vor – Endgegner ist der große Tao-Tei-Schwarm. Endgegner sind Live-Schlachten; nach einer Niederlage kannst du es nach einigen Tagen erneut versuchen.",
                    "Kronlande" to "Unter Reich → Kronlande: 42 Provinzen in sieben Ländern. Kaufe freies Land, schicke Gesandte für einen friedlichen Anschluss oder führe Feldzüge (bis zu zwei pro Tag). Unterwegs können Ereignisse eintreten, bei denen du entscheidest. Jede Provinz liefert Gold, Nahrung, Waffen und täglich neue Rekruten. Feinde starten Gegenoffensiven – stationiere Besatzungen, befestige oder schicke ein Entsatzheer.",
                    "Rivalen-Könige" to "Fünf Herrscher regieren die Nachbarländer, jeder mit eigener Persönlichkeit. Sie fordern Tribut, bieten Bündnisse an oder brechen Verträge. Eroberst du ihre Hauptstadt, ist ihr Reich besiegt; Verbündete greifen dich nicht an.",
                    "Partnerin & Heldinnen" to "Deine Partnerin gestaltest du bei der Charaktererstellung, kennenlernen tut ihr euch nach etwa einer Woche. Sie kämpft in deinen Feldzügen mit einer eigenen Spezialfähigkeit und kann verwundet werden. Die anderen drei Heldinnen kannst du als Feldherrinnen eigener Heere anwerben.",
                    "Dein Grenzreich" to "Du beginnst mit einem eigenen Gebiet, einer Grenzfeste, ausgebauten Produktionsgebäuden, Vorräten und einer einsatzfähigen Armee. Entwickle dein Reich bis zum Hochkönig; danach warten Koalitionen, versorgte Grenzforts, Wiederaufbau und optionale Nachfolge im Aufgabenjournal.",
                    "Wirtschaft & Unterhalt" to "Produktion läuft beim Tageswechsel. Arbeiter, Gebäude und Gebiete liefern Gold, Nahrung, Holz, Stein und Eisen. Die Wirtschaft zeigt Produktion, Armeeunterhalt und täglichen Nettogewinn. Ritter, Goldelben und Artillerie verbrauchen mehr Nahrung. Bei Nahrung 0 sinken Moral, Wachstum und Ausbildungstempo.",
                    "Bevölkerung" to "Gesamtbevölkerung teilt sich in Zivilisten, Soldaten und Auszubildende. Zivilisten arbeiten, stehen als Rekruten bereit oder bleiben frei. Soldaten arbeiten nicht gleichzeitig in der Wirtschaft. Fehlende Arbeiter senken die Produktion.",
                    "Gebäude & Festung" to "Tippe ein Gebäude auf der Festungskarte an, um Details und den Ausbau zu öffnen. Bauprojekte kosten sofort Ressourcen und dauern Tage. Wohnviertel und Lager schaffen Kapazität. Arbeiterprioritäten, Steuern und Stadtwerte beeinflussen die Wirtschaft. Bauernhof, Sägewerk, Steinbruch, Mine und Markt erzeugen Ressourcen. Kasernen beschleunigen Ausbildung; Mauern und Türme helfen gegen Belagerungen. Ein eigener Siedlungsname bleibt beim Ausbau erhalten.",
                    "Armee & Ausbildung" to "Die vier Kulturkarten zeigen Gesamtbestände und Ausbildung. Jeder Einheitentyp besitzt einen gemeinsamen Truppenbestand mit Moral, Erfahrung und Ausrüstung; diese Werte verändern die Kampfkraft. Die Ausbildung verbraucht Rekruten, Gold und Eisen und endet erst nach mehreren Tagen.",
                    "Kommandanten & persönliches Kommando" to "Weise einem Kommandanten konkrete Soldatenzahlen zu. Die Zuweisungsseite zeigt Gesamtbestand, daheim, unterwegs, frei und aktuell zugewiesen. Eingaben und 0 / 25 / 50 / 75 / ALLE verteilen nur verfügbare Soldaten. Doppelzuweisungen sind ausgeschlossen. Freie Soldaten kämpfen unter deinem persönlichen Kommando; Kommandantenboni gelten für ihre Kontingente.",
                    "Missionen mit Truppen" to "Unter Welt → Missionen wählst du Verfügbar, Unterwegs oder Abgeschlossen. Bereite eine Mission mit konkreten Einheiten und optionalem Kommandanten vor. Nahrung bezahlt die Versorgung. Dauer, Risiko und Spezialisierung beeinflussen den Ausgang. Erst der Tageswechsel bringt Fortschritt, Verluste und Belohnungen.",
                    "Soldaten unterwegs" to "Missionstruppen und ihre Kommandanten fehlen in der Festung, bei neuen Zuweisungen und bei weiteren Missionen. Ein Rückruf benötigt Rückreisezeit. Missionen können großen Erfolg, Erfolg, Teilerfolg, Scheitern oder eine Katastrophe bringen. Erfahrung, Moral, Truppentyp und Führung verändern die Chancen.",
                    "Invasionen vorbereiten" to "40% Bedrohung bringt Späherwarnungen, 60% Grenzüberfälle, 75% Heeresbewegungen und 90% eine angekündigte Invasion. Bei voller Bedrohung beginnt ein Angriff. Der Countdown lässt Zeit für Vorräte, Mauerreparaturen, Ausbildung, Rückrufe und verbündete Hilfe. Nur Soldaten daheim verteidigen deine Festung.",
                    "Interaktive Schlachten" to "Verteile daheim verfügbare Truppen und Kommandanten auf linken Flügel, Zentrum, rechten Flügel und Reserve. Jeder Abschnitt kämpft nach echter Entfernung und lokalem Kontakt. Intakte Mauern blockieren Nahkämpfer; Pfeile, Katapulte, Leitern, Türme und Torbruch haben getrennte Wirkungen. Schützen brauchen Pfeile, Artillerie braucht Ladungen. Halten spart Munition; der Austauschbericht erklärt die Ausfälle. Reagiere auf Ereignisse, setze Reserven ein und entscheide nach einem Sieg über Verfolgung oder Formation halten. Eine laufende Schlacht wird gespeichert.",
                    "Gefährtin & Beziehungen" to "Vertrauen, Respekt und Zuneigung entwickeln sich durch Ereignisse und Entscheidungen. Pro Tag sind höchstens eine große oder zwei kleine Beziehungsaktionen möglich. Freundschaft und freiwillige Romanze verlaufen getrennt; Romantik ist in den Optionen abschaltbar. Die Gefährtin kann als Kommandantin Truppen führen und Missionen übernehmen.",
                    "Kulturen & Charakter" to "Halbelben starten mit allen vier Kulturen. Menschen und Elben benötigen echte Bevölkerung durch diplomatische Begegnungen, Bündnisse und Immigration, bevor fremde Kulturen ausgebildet werden. Missionen, Schlachten und Ereignisse geben Erfahrung; neue Level ermöglichen die Verteilung von Skillpunkten auf wirksame Charakterwerte.",
                    "Offline & Spielstände" to "Alles bleibt auf deinem Gerät: kein Konto, kein Server, keine Echtgeldkäufe. Das Spiel speichert automatisch mit Sicherungskopie. Frühere Spielstände werden beim Laden migriert; alte Truppenbestände werden je Einheitentyp zusammengeführt. Eigene Bilder bleiben lokale Gerätedateien."
                )) { (title, body) -> HelpCard(title, body) }
                item { GoldButton("Tutorial erneut anzeigen", onReplayTutorial, Modifier.fillMaxWidth()) }
                item { HelpCard("Warum entsteht kein Nahkampfschaden?", "Der Feind muss den Abschnitt physisch erreichen. Hinter intakter Mauer brauchen Angreifer eine fertige Leiter, Kletterroute, einen angedockten Turm oder eine lokale Bresche. Die Minute allein öffnet keinen Kontakt. Tor schließen entfernt bereits eingedrungene Gegner nicht.") }
                item { HelpCard("Grenznetz und Vorhersagen", "Feldheere und Garnisonen fehlen zu Hause und reisen auch beim Rückruf. Außenposten geben nur vorhandene Nahrung ab; formale Verteidigungsbündnisse erlauben Versorgung von Partnerheeren. Ein gehaltenes Grenzfort hält dasselbe Feindheer mehrere Tage auf. Wirtschaftsprognosen für 7/14 Tage enthalten Bandbreiten, aber keine unbekannten Ereignisse.") }
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
    val raw = listOf(
        "Heute" to "Unten links: Dein Tagesüberblick. Der Reichsberater zeigt, was gerade brennt – Angriffe, Hunger, Szenen – und welche Chancen offen sind. Tippe eine Karte an und du landest genau dort. Mit „Tag beenden“ geht die Zeit weiter.",
        "Reich" to "Die Karte der Kronlande: 42 Provinzen in sieben Ländern. Tippe eine Provinz an, um sie zu kaufen, friedlich anzuschließen oder zu erobern. Jede Provinz bringt Gold, Nahrung, Waffen und täglich neue Rekruten. Rote Pfeile sind anrückende Feinde.",
        "Heer" to "Hier bildest du Truppen aus, rufst den Heerbann, wirbst Söldner an und trainierst deine Kampfkunst. Unter „Grenze“ verteidigst du deine Mauer gegen Orks, Uruk-hai und Tao Tei.",
        "Hof" to "Deine Partnerin, die Heldinnen, Familie, Ämter und dein eigenes Profil. Etwa eine Woche nach Spielbeginn begegnest du deiner zukünftigen Partnerin – danach entscheidest du, wohin eure Geschichte führt.",
        "Stadt" to "Deine Festung: Gebäude ausbauen, Lager und Wohnraum schaffen, Steuern und Arbeiter steuern. Antippen eines Gebäudes öffnet Details.",
        "Akte & Ziele" to "Dein Weg hat drei Akte: Die Grenzmark sichern, die Krone erringen, der große Krieg. Jeder Akt endet mit einem Endgegner. Ziele und Fortschritt stehen unter Reich → Kronlande.",
        "Schlachten" to "Feldzüge kannst du schnell auflösen oder als Live-Schlacht selbst führen: Fronten, Reserven und Befehle. Vor einem Feldzug kannst du den feindlichen Champion zum Duell fordern – im Duell schlägt Angriff die Finte, Parade den Angriff und Finte die Parade.",
        "Mehr" to "Oben rechts unter den drei Punkten: Optionen, Hilfe, Chronik und Spielstand (Export und Import). Diese Einführung kannst du dort jederzeit erneut öffnen.",
    )
    val steps = raw.mapIndexed { i, (title, text) -> Triple("${i + 1}. $title", text, "${i + 1} / ${raw.size}") }
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
