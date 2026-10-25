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
                item { HelpCard("Reichsbereiche", "Schnellzugriff auf alle Bereiche. Warnungen oben und Hinweise auf „Heute“ führen direkt zu dringenden Orten.") }
                items(
                    listOf(
                        "Kronlande-Karte & Akte" to GameDestination.KINGDOM,
                        "Front & Mauer" to GameDestination.FRONTIER,
                        "Partnerin & Saga" to GameDestination.COURT,
                        "Herrscherpaar & gemeinsame Zeit" to GameDestination.RULERS,
                        "Familie & Dynastie" to GameDestination.FAMILY,
                        "Profil & Fertigkeiten" to GameDestination.CHARACTER,
                        "Forschung" to GameDestination.RESEARCH,
                        "Rat" to GameDestination.COUNCIL,
                        "Ämter & Hof" to GameDestination.PALACE,
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
                    "Navigation" to "Fünf Bereiche unten mit je höchstens vier Reitern: Heute (Inbox, Lage, Aufgaben, Chronik), Reich (Karte, Könige, Handel, Edikte), Heer (Front, Truppen, Feldherren, Kampfkunst), Hof (Partnerin, Familie, Rat, Herrscher) und Stadt (Stadt, Großbauten, Forschung). Manche Reiter haben eine kleine Umschaltung darunter. Jede Entscheidung landet in der Inbox auf „Heute“. Optionen, Hilfe und Spielstand liegen oben rechts unter den drei Punkten.",
                    "Akte & Endgegner" to "Akt 1: Sichere die Grenzmark, stelle 1.500 Soldaten auf und gewinne deine Feldherrin – Endgegner Gor-Mukar mit seinen Orks. Akt 2: Werde König, 18 Provinzen, 12.000 Soldaten, zwei Könige auf deiner Seite – Endgegner Skarnag mit den Uruk-hai. Akt 3: 30 Provinzen, drei im Hordenland, 40.000 Soldaten, vier Könige – Endgegner die Tao-Tei-Königin. Der Berater zeigt vor dem Endkampf, wie stark du im Vergleich bist.",
                    "Kronlande" to "Unter Reich → Kronlande: 42 Provinzen in sieben Ländern. Kaufe freies Land, schicke Gesandte für einen friedlichen Anschluss oder führe Feldzüge (einer pro Tag, ab Akt 2 zwei). Unterwegs können Ereignisse eintreten, bei denen du entscheidest. Jede Provinz liefert Gold, Nahrung, Waffen und täglich neue Rekruten. Feinde starten Gegenoffensiven – stationiere Besatzungen, befestige oder schicke ein Entsatzheer.",
                    "Rivalen-Könige" to "Fünf Herrscher regieren die Nachbarländer, jeder mit eigener Persönlichkeit. Sie fordern Tribut, bieten Bündnisse an oder brechen Verträge. Eroberst du ihre Hauptstadt, ist ihr Reich besiegt; Verbündete greifen dich nicht an.",
                    "Partnerin & Heldinnen" to "Deine Partnerin gestaltest du bei der Charaktererstellung, kennenlernen tut ihr euch nach etwa einer Woche. Sie kämpft in deinen Feldzügen mit einer eigenen Spezialfähigkeit und kann verwundet werden. Die anderen drei Heldinnen kannst du als Feldherrinnen eigener Heere anwerben.",
                    "Dein Grenzreich" to "Du beginnst mit einem eigenen Gebiet, einer Grenzfeste, ausgebauten Produktionsgebäuden, Vorräten und einer einsatzfähigen Armee. Entwickle dein Reich bis zum Hochkönig; danach warten Koalitionen, versorgte Grenzforts, Wiederaufbau und optionale Nachfolge im Aufgabenjournal.",
                    "Wirtschaft & Unterhalt" to "Produktion läuft beim Tageswechsel. Arbeiter, Gebäude und Provinzen liefern Gold, Nahrung, Holz, Stein und Eisen; die Werkstätten jeder Kronprovinz schmieden täglich Waffen und Rüstungen. Jeder Soldat isst – ohne Nahrung desertieren täglich 2 % des Heeres. Gold brauchst du für Ausbildung, Großbauten, Königsfeste, Bestechungen und die Ausrüstung deiner Partnerin.",
                    "Bevölkerung" to "Gesamtbevölkerung teilt sich in Zivilisten, Soldaten und Auszubildende. Zivilisten arbeiten, stehen als Rekruten bereit oder bleiben frei. Soldaten arbeiten nicht gleichzeitig in der Wirtschaft. Fehlende Arbeiter senken die Produktion.",
                    "Gebäude & Festung" to "Tippe ein Gebäude auf der Festungskarte an, um Details und den Ausbau zu öffnen. Bauprojekte kosten sofort Ressourcen und dauern Tage. Wohnviertel und Lager schaffen Kapazität. Arbeiterprioritäten, Steuern und Stadtwerte beeinflussen die Wirtschaft. Bauernhof, Sägewerk, Steinbruch, Mine und Markt erzeugen Ressourcen. Kasernen beschleunigen Ausbildung; Mauern und Türme helfen gegen Belagerungen. Ein eigener Siedlungsname bleibt beim Ausbau erhalten.",
                    "Armee & Ausbildung" to "Die vier Kulturkarten zeigen Gesamtbestände und Ausbildung. Jeder Einheitentyp besitzt einen gemeinsamen Truppenbestand mit Moral, Erfahrung und Ausrüstung; diese Werte verändern die Kampfkraft. Die Ausbildung verbraucht Rekruten, Gold und Eisen und endet erst nach mehreren Tagen.",
                    "Kommandanten & persönliches Kommando" to "Weise einem Kommandanten konkrete Soldatenzahlen zu. Die Zuweisungsseite zeigt Gesamtbestand, daheim, unterwegs, frei und aktuell zugewiesen. Eingaben und 0 / 25 / 50 / 75 / ALLE verteilen nur verfügbare Soldaten. Doppelzuweisungen sind ausgeschlossen. Freie Soldaten kämpfen unter deinem persönlichen Kommando; Kommandantenboni gelten für ihre Kontingente.",
                    "Missionen mit Truppen" to "Unter Heer → Feldherren → Missionen wählst du Verfügbar, Unterwegs oder Abgeschlossen. Bereite eine Mission mit konkreten Einheiten und optionalem Kommandanten vor. Nahrung bezahlt die Versorgung. Dauer, Risiko und Spezialisierung beeinflussen den Ausgang. Erst der Tageswechsel bringt Fortschritt, Verluste und Belohnungen.",
                    "Soldaten unterwegs" to "Missionstruppen und ihre Kommandanten fehlen in der Festung, bei neuen Zuweisungen und bei weiteren Missionen. Ein Rückruf benötigt Rückreisezeit. Missionen können großen Erfolg, Erfolg, Teilerfolg, Scheitern oder eine Katastrophe bringen. Erfahrung, Moral, Truppentyp und Führung verändern die Chancen.",
                    "Invasionen vorbereiten" to "40% Bedrohung bringt Späherwarnungen, 60% Grenzüberfälle, 75% Heeresbewegungen und 90% eine angekündigte Invasion. Bei voller Bedrohung beginnt ein Angriff. Der Countdown lässt Zeit für Vorräte, Mauerreparaturen, Ausbildung, Rückrufe und verbündete Hilfe. Nur Soldaten daheim verteidigen deine Festung.",
                    "Schlachten" to "Jede Schlacht hat drei Entscheidungen: 1. den Schlachtplan (z. B. Mauer halten, Pfeilhagel, Ausfall), 2. den Wendepunkt, wenn die Mauer fällt oder die Linien aufeinanderprallen, 3. das Ende – Verfolgen oder Formation halten, bei Gefahr Rückzug oder letztes Aufgebot. Dazwischen siehst du die Schlacht als Simulation; Pause, Tempo und „Zum Ende“ stehen unten. Wer jeden Befehl selbst geben will, öffnet oben „Details“.",
                    "Gefährtin & Beziehungen" to "Vertrauen, Respekt und Zuneigung entwickeln sich durch Ereignisse und Entscheidungen. Pro Tag sind höchstens eine große oder zwei kleine Beziehungsaktionen möglich. Freundschaft und freiwillige Romanze verlaufen getrennt; Romantik ist in den Optionen abschaltbar. Die Gefährtin kann als Kommandantin Truppen führen und Missionen übernehmen.",
                    "Kulturen & Charakter" to "Halbelben starten mit allen vier Kulturen. Menschen und Elben benötigen echte Bevölkerung durch diplomatische Begegnungen, Bündnisse und Immigration, bevor fremde Kulturen ausgebildet werden. Missionen, Schlachten und Ereignisse geben Erfahrung; neue Level ermöglichen die Verteilung von Skillpunkten auf wirksame Charakterwerte.",
                    "Inbox & Wochenrhythmus" to "Alle offenen Entscheidungen – Kriegsrat, Grenze, Rat, Volk, Familie, Partnerin, Feldherren – stehen als Karten in der Inbox. Was du drei Tage liegen lässt, entscheiden die Räte vorsichtig selbst. Jede Woche bringt ein Ereignis: Ork-Sturm, Erntefest, Turnier, Händlerzug, Fieber oder Gesandte.",
                    "Großbauten & Erfolge" to "Unter Stadt → Großbauten errichtest du acht Wunder wie die Große Mauer II oder die Sternenschmiede. Dort richtest du auch Königsfeste aus und siehst deine 30 Erfolge.",
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
        "Heute" to "Unten links: Deine Inbox. Hier stehen alle Entscheidungen des Tages als Karten, darunter der Reichsberater mit Gefahren und Chancen. Tippe einen Hinweis an und du landest genau dort. Mit „Tag beenden“ geht die Zeit weiter.",
        "Reich" to "Die Karte der Kronlande: 42 Provinzen in sieben Ländern. Tippe eine Provinz an, um sie zu kaufen, friedlich anzuschließen oder zu erobern. Jede Provinz bringt Gold, Nahrung, Waffen und täglich neue Rekruten. Rote Pfeile sind anrückende Feinde.",
        "Heer" to "Ganz vorne die Front: Mauer, Horden und angegriffene Provinzen. Unter „Truppen“ bildest du aus, rufst den Heerbann und wirbst Söldner an; dazu Feldherren, Missionen und deine Kampfkunst.",
        "Hof" to "Deine Partnerin, die Heldinnen, Familie, Ämter und dein eigenes Profil. Etwa eine Woche nach Spielbeginn begegnest du deiner zukünftigen Partnerin – danach entscheidest du, wohin eure Geschichte führt.",
        "Stadt" to "Deine Festung: Gebäude ausbauen, Lager und Wohnraum schaffen, Steuern und Arbeiter steuern. Unter „Großbauten“ warten acht Wunder und das Königsfest, unter „Forschung“ neue Techniken.",
        "Akte & Ziele" to "Dein Weg hat drei Akte: Die Grenzmark sichern, die Krone erringen, der große Krieg. Jeder Akt endet mit einem Endgegner. Das nächste Ziel steht immer oben auf „Heute“, alle Ziele unter Reich → Karte.",
        "Schlachten" to "Du entscheidest dreimal: Schlachtplan, Wendepunkt (wenn die Mauer fällt) und das Ende. Dazwischen läuft die Schlacht als Simulation vor deinen Augen. Vor einem Feldzug kannst du den feindlichen Champion zum Duell fordern – Angriff schlägt Finte, Parade schlägt Angriff, Finte schlägt Parade.",
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
