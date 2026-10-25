# Technische Bestandsaufnahme – Troopmanager

Stand: 25.10.2026, Basis `af694d7eab9f301178ed94ade0b5daa7fabc6eb4`.
Auftrag: repositoryweite Systemprüfung und eine abgeschlossene erste Stabilisierung.

## Prüfumfang und Aussagegrenzen

Geprüft wurden Git-Referenzen, Buildkonfiguration, CI, Modelle, Engine-Einstiegspunkte,
Systemverbindungen, produktive Navigation, Speicherpfad, Darstellung und vorhandene Tests.
Zwei rein lesende Teilprüfungen behandelten UI/Speicherung und strategische Spielsysteme.
Das ist keine Behauptung, jede Codezeile oder jeden Bildschirm auf einem Gerät getestet
zu haben. Quelltextbefunde, Risiken und frisch ausgeführte Prüfungen werden getrennt.
Ausführungsnachweise stehen in [CODEX-IMPLEMENTATION-REPORT.md](CODEX-IMPLEMENTATION-REPORT.md).
Historische Dateien unter `docs/evidence` sind keine aktuellen Testergebnisse.

## 1. Tatsächlicher Entwicklungsstand

- `origin/main`, `origin/claude/v1.1-koenigreich-overhaul` und Tag `dev-latest` zeigen
  auf `af694d7`. APK-Metadaten: **2.1.0 / 51**, Speicherformat **5**.
- Eine Version **2.2** existiert in den abgerufenen Branches/Tags nicht. Ein Vergleich
  2.2 gegen Vorgänger wäre spekulativ. Verfügbarer Vergleich: 2.0 (`886bf62`) gegen
  2.1 (`af694d7`), 39 Dateien, 1.056 Einfügungen / 383 Löschungen.
- 2.0 führte unter anderem Inbox, neue Navigation, taktischen Ablauf, Großbauten und
  Balanceänderungen ein. 2.1 ergänzt Karten-/UI-Gestaltung, Schriften, Morgendämmerung,
  Audio und Bedienkorrekturen. `docs/V2.0-UEBERARBEITUNG.md` und
  `docs/V2.1-LOOK-AND-FEEL.md` beschreiben diese Stände.
- `CODEX.md` bezeichnet sich noch als v1.3 und enthält ausdrücklich historische
  Branchhinweise. Builddatei und Git-Historie sind die belastbaren Versionsquellen.
- Arbeitsbranch dieser Phase: `codex/audit-combat-stability`; kein Merge nach `main`.

### Relevante Branches

Zahlen: Commits nur in `main` / nur im jeweiligen Branch, vor Beginn dieser Arbeit.
Ein abweichender alter Branch ist nicht automatisch ein neuerer Entwicklungsstand.

| Referenz | Abstand | Einordnung |
| --- | --- | --- |
| `main`, `claude/v1.1-koenigreich-overhaul`, `dev-latest` | 0 / 0 | Aktuelle Version 2.1 |
| `codex/battle-formations-and-wall-defense` | 25 / 0 | Integrierter Kampf-/Darstellungsstand |
| `codex/v1.0-realm-war-overhaul` | 29 / 0 | Integrierter Vorgänger |
| `codex/v0.97-battle-tactics-ui-overhaul` | 32 / 0 | Integrierter Vorgänger |
| `codex/v0.96-visual-world-overhaul` | 33 / 0 | Integrierter Vorgänger |
| `codex/v0.95-living-realm-overhaul` | 48 / 0 | Integrierter Vorgänger |
| `codex/v0.90-war-experience-overhaul` | 60 / 0 | Integrierter Vorgänger |
| `codex/v0.85-modern-ui` | 70 / 0 | Integrierter Vorgänger |
| `codex/v0.84-ui-overhaul` | 84 / 0 | Integrierter Vorgänger |
| `codex/v0.83-fun-overhaul` | 95 / 0 | Integrierter Vorgänger |
| `codex/v0.82-overhaul` | 109 / 0 | Integrierter Vorgänger |
| `codex/v0.82` | 169 / 38 | Alter Parallelzweig; Features teils anderweitig integriert |
| `codex/v0.81` | 169 / 0 | Integrierter Vorgänger |
| `codex/v0.68` | 170 / 5 | Alter Parallelzweig mit Release-/Census-Änderungen |
| `codex/v0.66` | 170 / 3 | Alter Parallelzweig |
| `codex/v0.65` | 170 / 0 | Integrierter Vorgänger |
| `codex/v0.63-frontier` | 171 / 0 | Integrierter Vorgänger |
| `codex/v0.62` | 173 / 0 | Integrierter Vorgänger |
| `codex/v0.61` | 229 / 0 | Integrierter Vorgänger |
| `codex/v0.6-aaa-overhaul` | 275 / 0 | Integrierter Vorgänger |
| `codex/v0.45-city-ui-overhaul` | 276 / 0 | Integrierter Vorgänger |
| `codex/v0.4.0-core-overhaul` | 278 / 0 | Integrierter Vorgänger |
| `feature/next-level-gameplay-ux` | 29 / 12 | Älterer Parallelzweig: nächste Aktion, Tests, Arbeitsanweisungen; kein pauschaler Merge |
| `ui-screenshots` | unabhängige Historie | Generierte Bilder zu `af694d7`, kein Spielquellstand |

Es gibt nur den beweglichen Tag `dev-latest`; unveränderliche Versions-Tags fehlen.
Der Feature-Zweig enthält ein eigenes `AGENTS.md`; im ausgecheckten Hauptstand gibt
es keines. Arbeitsanweisungen eines fremden Branches wurden nicht übernommen.

## 2. Architektur und Datenfluss

Ein Android-Modul `:app`; Kotlin 2.0.21, Compose, Material 3, Lifecycle, Serialization
und Coil. AGP 8.7.3, Gradle in CI 8.9, JDK-Ziel 17; minSdk 26, compile/targetSdk 35.
Kein Gradle-Wrapper im Repository. Im Startzustand dieser Umgebung fehlen Gradle,
Android SDK und Emulator; JDK 21 ist vorhanden. Die vorhandene GitHub-Pipeline stellt
die benötigte Android-Umgebung bereit.

152 Kotlin-Produktionsdateien, ca. 2,30 MB Quelltext; 46 vorhandene JVM-Testdateien
mit 482 `@Test`-Deklarationen und zwei Instrumentierungsdateien vor dieser Phase.
Das sind statische Inventarzahlen, keine ausgeführten oder bestandenen Tests.

- `model/`: serialisierbarer unveränderlicher `GameState` und Fachmodelle/Kataloge.
- `engine/`: überwiegend Android-unabhängige Zustandsübergänge; `ActionResult` trägt
  neuen Zustand und Meldung. Zufall ist bei Kämpfen über Seeds/Schritte gesteuert.
- `GameEngine.advanceDay`: geordnete Integration von Stadt, Wirtschaft, Rüstung,
  Welt, Missionen, Krieg, Hof, Dynastie, Diplomatie, Gesellschaft, Grenze und Krone.
- `ui/GameViewModel.kt`: StateFlow, Aktionsserialisierung und Prüfung des erwarteten
  Ausgangszustands; Tagesberechnung auf Default, Speicherung auf IO.
- `data/`: JSON/Checksumme/Validierung/Migration sowie atomare Preferences-Speicherung.
- `ui/App.kt`, `MainHubs.kt`, `model/NavModels.kt`: fünf Hauptbereiche, `Spot`-Ziele,
  gemeinsame Fantasy-Komponenten und aktuelle taktische Schlacht als überlagerter Ablauf.

**Größte Integrationsschuld:** `WorldState` und `CrownState` bilden zwei strategische
Ebenen. Kronlande sind die sichtbare Hauptkarte; ältere Weltorte, Fraktionen,
Feldarmeen und Konvois werden weiter simuliert. Kronlanderoberung verändert nicht
automatisch Besitz/Diplomatie alter Weltorte. Vor weiteren Karten-, KI- oder
Nachschubfunktionen muss deren verbindliche Zuordnung festgelegt werden.

## 3. Systeminventar und Weiterverwendung

Alle Pfade beziehen sich auf `app/src/main/java/com/goldenunicorn/troopmanager/`.

| Bereich | Vorhanden / maßgebliche Komponenten | Lücke und nächster sinnvoller Ausbau |
| --- | --- | --- |
| Weltkarte/Provinzen | `CrownEngine`, `CrownWarEngine`, `CrownCatalog`, `ui/CrownMap`: Touch-Hexkarte, Besitzer, Kultur, Bevölkerung, Ausbau, Loyalität, Unruhe, Gegenangriffe | Physische Märsche, Konvois und Wetter aus `WorldEngine` an Kronlande anbinden; Filter und zugängliche Auswahl |
| Schlachten | `BattleEngine`, `BattleResolutionEngine`, `BattleStateEngine`: persistierte Fronten, Distanz, Kontakt, begrenzte Kampfbreite, Moral, Kohäsion, Ermüdung, Ausrüstung, Erfahrung, Munition und Verlustbudgets | Taktischen v2-Ablauf mit Kernzustand/Replay konsistent halten; räumliches Manöver und Umfassung sind noch abstrakt |
| Formationen/Befehle | `CombatModels`, `BattlePlan`, `BattleCommandEngine`: Linie, offene/dichte Ordnung, Schildwall, verzögerte Formationswechsel/Reserve, Führungswirkung, Fehler, Sammeln/Rückzug | Speerwall und Keil fehlen als eigene Formationen; taktischer Planname allein bildet keine echte Formation |
| Fernkampf/Sicht | Reichweite je Typ, Distanz, Nacht/Wetter/Sicht, Höhe der Mauer, Schützenplätze und reale Munition in `BattleResolutionEngine` | Sicht ist skalar und kein geometrischer Sichtlinientest; Reserve und Deckung weiter anhand echter Zustände darstellen |
| Belagerungen | `SiegeEngine`: Annäherung, Gerätebesatzungen/-munition, Leiter/Turm/Ramme/Katapult/Tunnel, lokale Mauer-/Torschäden, Bresche, Innenhof, Gegenmaßnahmen | Strategische Aushungerung/Kapitulation und Entsatz konsistent über beide Weltebenen führen |
| Verluste/Berichte | `BattleReportEngine`, `WarEngine`, `BattleConsequencesEngine`: Ursachen, Tote, Verwundete, Gefangene, Flüchtende, Heilung, Kriegsmüdigkeit, Beziehungen, Replay | Erhaltung über taktischen Ablauf und Kampagnenabgleich testen; keine zweite Verlustberechnung ergänzen |
| Armeen/Regimenter | `ArmyEngine`, `FrontierEngine`: Pools, Zuweisungen, mehrere Feldarmeen, benannte Regimenter, Hauptleute, Veteranen, Geschichte, Arsenal | Individuelle Regimenter sind teilweise Metadaten über Pools; Garnison/Feldheer/Verträge an einer Mutationsgrenze schützen |
| Kommandanten/Charaktere | `CharacterEngine`, `CommanderEventEngine`: Persönlichkeit, Führung, Taktik, Fertigkeiten, Loyalität, Ambition, Rang, Rivalität, Ämter und Erinnerungen | Auswirkungen der mehreren Führungsmodelle transparent verbinden; Rang-/Karrierefolgen gezielt testen |
| Wirtschaft/Produktion | `EconomyEngine`, `CityEngine`, `MilitaryEconomyEngine`: Prioritäten, Steuern, Arbeitskräfte, Jahreszeit, Lager, Forschung, Güter und Rekrutierung | Kronland-Sonderproduktion umgeht Kosten/Lager; Eisen/Holz → Arsenal → Ausrüstung → Truppe ausbauen statt ersetzen |
| Handel/Versorgung | Dynamische Preise, Routen/Abkommen, Unterhalt/Hunger/Desertion; `WorldEngine` bietet Feldproviant, Depots, reale abfangbare Konvois | Kronlandrouten/Besatzungen an physische Versorgung anbinden; Handelsanzeige und echte Buchung korrigieren |
| Diplomatie/Politik | `DiplomacyEngine`, `EspionageEngine`: Nichtangriff, Handel, Angriffs-/Verteidigungsbündnis, Vasall/Tribut, Gegenangebote, Zugang, Krieg/Frieden, Gedächtnis, Agenten; `RivalEngine` mit Persönlichkeiten | Rivalenkönige und Fraktionsverträge zusammenführen; gemeinsame Bedrohungswahrnehmung/Koalitionen |
| Partnerin/Beziehungen | `RelationshipEngine`, `RelationshipEventDirector`, `HeroineSagaEngine`, `CoRulerEngine`: Begegnungen, Quests, Karriere, Erinnerungen, Konflikte, politische Haltung, gemeinsame Regierung/Delegation | Überlappende Saga-/Beziehungszustände vor neuem Inhalt durch Integrationstests sichern |
| Dynastie/Nachfolge | `DynastyEngine`: Geburt/Adoption, Mentoren, Erziehung, erwachsene Hofcharaktere, Erben, Spannungen, Regentschaft, tatsächliche Nachfolge, Verträge | Generationswechsel über lange Kampagnen testen; Maßstab 365 Tage mit Aktfortschritt abstimmen |
| Städtebau/Bevölkerung | `CityEngine`, `SocietyEngine`, `ReignEngine`: Bauplätze/-zeiten/-stufen, Wohlstand/Sicherheit/Zufriedenheit, Hunger/Krankheit/Kriminalität/Migration, acht Großbauten; `ui/CityScene` projiziert Zustand | Kosten/Kapazitäten vereinheitlichen; sichtbaren Baufortschritt/Schaden prüfen, freie Platzierung erst danach |
| Völker/Kulturen | Vier militärische Kulturgruppen, unterschiedliche Einheiten/Güter, Gründungskulturen, Förderung, Integration, Loyalität und politische Reaktionen | Heimat-, Kronland- und Weltkultur verbinden; kulturspezifische Balance über gleiche Szenarien testen |
| Story/Quests | `ActEngine`, `MarchEngine`, `StoryDirector`, `QuestJournalEngine`: drei Akte/Bosse, Marschentscheidungen, verknüpfte Folgen, Aufgaben, Chronik | Konsequenzen systematisch prüfen; Rivalitäten und langfristige Geschichten auf vorhandenen Ereignissen aufbauen |
| Strategie-/Charakter-KI | `WorldEngine` mit Finanzen/Rekrutierung/Versorgung/Aufklärung; `CrownWarEngine` mit Zielwahl; Charakter-/Partner-/Rivalenereignisse | Kronland-KI hat keine vergleichbar vollständige Finanz-/Nachschubökonomie; kein Cloud-Modell nötig |
| Taktik-KI | `BattleAiEngine`: Annäherung, Fernkampf, Sturm, Tor, Artillerie, Rückzug, deterministische Wahl | Flanken/Umfassung, verschiedene Persönlichkeiten und gegnerische Reserve fehlen als vollständige räumliche Systeme |
| Navigation/UI | Fünf Bereiche: Heute, Reich, Heer, Hof, Stadt; zentrale Sprungziele, Inbox, Plan/Wendepunkt/Ende, Detailsteuerung | Querformat, große Schrift, TalkBack, Touchziele und respektierte Einstellungen; keine weitere Menüebene |
| Grafik/Animation/Audio | Lokale WebP, Canvas-Stadt/Schlacht, Banner, Porträts, Schriften, SoundPool/MediaPlayer; `BattleSceneProjection` begrenzt Marker auf 720 | Originalgrafik erhalten; Ereigniseffekte müssen Berichte/Distanzen abbilden; Laufzeitmessung steht aus |
| Balancing/Endgame | Kampfbreite, Deckung, Ausrüstung, Veteranen, drei Akte, Bosse, Erfolge, fortlaufende Bedrohungen/Ziele | Kein vollständig belegtes dynamisches Großreich-Endgame; Wirtschaftsexploits vor neuen Boni schließen |

## 4. Bestätigte Quelltextfehler

Eine Quelltextbestätigung beschreibt den Mechanismus. Dynamische Bestätigung ist
jeweils zusätzlich erforderlich und wird im Implementierungsbericht ausgewiesen.

| ID / Priorität | Mechanismus und Auswirkung | Fundstelle / Behandlung |
| --- | --- | --- |
| B01 / P1 | Große Mauer addiert 0,1 Deckung bis 0,95; Validierung erlaubt höchstens 0,85. `SaveCodec.encode` validiert vor Speicherung: Planwahl kann Autosave verhindern. | `TacticalBattleEngine.choosePlan`; `BattleStateEngine.validate`; erste Phase |
| B02 / P1 | Planwahl ändert Moral, Kohäsion, gegnerische Moral, Deckung und Perks, belässt aber den alten `replayStart`. Replay startet dadurch mit anderen Kampfbedingungen. | `TacticalBattleEngine.choosePlan`; erste Phase für Startzustand/ersten Austausch |
| B03 / P1 | Planwahl prüft nur `tacticalStage == 0`; über Detailsteuerung gestartete oder beendete Schlachten können diesen Wert noch besitzen und nachträglich Boni bekommen. | `TacticalBattleEngine.choosePlan`; erste Phase |
| B04 / P1 | Hafenfaktor wird erst nach Goldgutschrift auf `gained` angewandt: Handelsbericht steigt, tatsächliches Gold nicht. | `CrownEngine.tradeDay`, etwa Zeilen 252–259; Folgephase |
| B05 / P1 | `mercenaryDay` überschreibt Desertionen gleicher Einheitentypen mit `associate`, bucht beim Pool einmal und bei jedem Vertrag. Zwei Verträge à 500: Pool −25, Verträge zusammen −50. | `CrownEngine`, etwa Zeilen 205–210; Folgephase |
| B06 / P2 | `raw.wood * 35` als Int kann ab 61.356.676 Holz überlaufen und Produktionsmöglichkeiten negativ machen. | `MilitaryEconomyEngine`, etwa Zeile 188; Grenzwertkorrektur |
| B07 / P2 | Zentrale Gold-Schaltflächen und taktische Entscheidungen lösen Haptik unabhängig von der Einstellung aus. | `ui/Components.kt`, `ui/TacticalBattleScreen.kt`; UI-Folgephase |
| B08 / P2 | Kronlandkarte animiert Farben/Puls auch bei ausgeschalteten Animationen. | `ui/CrownMap.kt`; UI-Folgephase |

## 5. Risiken und unvollständige Anforderungen

- **Replay nach Wendepunkten:** `openMiddle`/`openFinal` füllen Befehlspunkte außerhalb
  normaler `BattleInput`-Übergänge auf. Vollständige Reproduktion des taktischen
  Gesamtablaufs muss separat abgesichert werden; die Startzustandskorrektur allein
  ist kein Nachweis für alle späteren Ereignisse. Historische Replays werden nicht
  nachträglich umgeschrieben.
- **Militärgrenze:** `CrownWarEngine.station/withdraw` prüfen keine aktive Schlacht,
  während andere Militäraktionen es tun. Snapshot-/Doppelzählungsrisiko; noch kein
  dynamisch bestätigter Verlust-/Duplikationsfall.
- **Wirtschaft:** Kronland-Arsenal und Provinzwerkstätten produzieren außerhalb
  normaler Kosten-/Kapazitätsregeln; Handelsimporte können trotz Exportmenge 0
  entstehen. Regeln vor einer Balancekorrektur ausdrücklich definieren.
- **Hauptthread:** `TacticalBattleScreen` berechnet `step` und Schnellauflösung vor
  dem ViewModel-Aufruf synchron. ANR/Framedrops sind Risiken, bisher nicht gemessen.
- **Querformat:** Manifest erzwingt `portrait`. Querformat-Compose-Tests belegen
  Vorschaugrößen, keine produktive Activity-Rotation.
- **Zugänglichkeit:** Kronland-Canvas hat keine individuellen Provinzaktionen für
  TalkBack; Unterreiter mit 36-dp-Mindesthöhe sichern kein 48-dp-Ziel.
- **Performance:** Kartenpuls wird bei Composition gelesen; animierte Farbenliste
  wird neu aufgebaut. Produktionswerte werden teils mehrfach berechnet. Erst
  Profiling, dann gezielte Änderungen; keine behauptete FPS-/Akkuverbesserung.
- **Langzeit:** `ActsAndRivalsTest.longCampaignSaveStaysSmall` führt nur einen Tag
  mit vorgefüllter Chronik aus. Kein Ersatz für 200/1.000/10.000 simulierte Tage.

## 6. Speicherstände und Offline-Verhalten

`SaveCodec`: Schema 5, SHA-256-Prüfsumme, Struktur-/Domänenvalidierung, Migrationen
1–4, zukünftige Versionen werden zurückgewiesen. `SaveRepository`: drei Slots,
atomarer Preferences-Commit, rollierendes Backup, geschützte Migrationskopien,
Ironman-Schutz und sichtbare Fehlermeldungen. Keine neue Speicherschicht einführen.

Die erste Phase soll nur vorhandene Felder korrekt befüllen und erlaubte Werte
erzeugen: keine neue Schema- oder Regelversion, kein Löschen/Migrieren vorhandener
Kampagnen. Bereits verhinderte Autosaves können nicht rückwirkend hergestellt
werden; ein Neustart lädt den letzten erfolgreich gespeicherten Stand.

Manifest ohne Internetberechtigung; Assets und Audio lokal. Kein kostenpflichtiger
Dienst und keine Cloud-Laufzeit notwendig. `CREDITS.md`, OFL-Lizenzen und
`THIRD-PARTY-NOTICES.md` dokumentieren Herkunft; Audio wird als prozedural/CC0
ausgewiesen. Die alte Audioanzahl und Versionsüberschrift sind nicht mehr aktuell.
Eine unabhängige juristische Lizenzprüfung wurde nicht vorgenommen.

## 7. Tests, CI und fehlende Nachweise

Vorhandene Tests decken Kampf, Munition, Deckung/Breschen, Replay, Projektionen,
Wirtschaft, Welt/Krone, Beziehungen/Dynastie und Speichermigration ab.
`BattleLayoutTest` prüft kompakte Formate, Querformatvorschau und große Schrift;
`TourTest` erstellt Screenshots der `Spot`s, aber keine umfassenden Bedienassertionen.

`.github/workflows/android.yml`: Unit-Tests → Lint → APK → Signatur/Metadaten →
Artefakte; abhängiger Emulatorjob mit Android 35. APK-Version wird hart geprüft.
Signing-Key wird stabil zwischengespeichert. Bei jedem erlaubten Push wird der
bewegliche Release `dev-latest` gelöscht/neu erstellt; mehrere Branches können ihn
somit ersetzen. Screenshot-Push überschreibt ebenfalls einen gemeinsamen Branch.
Diese Veröffentlichungsgrenzen sollten vor Erweiterung der Push-Trigger auf `main`
begrenzt werden. PR-Läufe veröffentlichen diese Ressourcen nicht.

Nicht belegt: Samsung Galaxy S26 Ultra, reale Rotation, vollständiger TalkBack-Ablauf,
Geräteleistung/Akkulaufzeit, generationslange Kampagne, vollständige taktische
Replays nach jedem UI-Wendepunkt und Installation/Spieltest einer neuen APK.

Die priorisierten Folgearbeiten einschließlich Aufwand und Abhängigkeiten stehen
in [CODEX-ROADMAP.md](CODEX-ROADMAP.md).
