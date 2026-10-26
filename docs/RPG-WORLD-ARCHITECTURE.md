# RPG-Weltarchitektur (Project Rebirth, Schema 6)

## Datenmodell

```
GameState
 ├─ … (alle bisherigen Felder, unverändert)
 └─ life: LifeState                       model/LifeModels.kt
     ├─ mode: GameMode                     SANDBOX | STORY | BORDER_LORD | FREE_LIFE
     ├─ landed: Boolean                    false = Reich ruht, wird nicht simuliert
     ├─ storyActs: Boolean                 schaltet ActEngine.tick
     ├─ background, profession, gender, culture, origin, backstory
     ├─ appearance: CharacterAppearance    32 Merkmale, siehe CHARACTER-CREATION-DESIGN.md
     ├─ purse, renown, career              persönliche Börse/Ruf/Laufbahn
     ├─ provinceId, place: LifePlace       wo der Charakter gerade ist (TOWN/TAVERN/…)
     ├─ timeSlot: TimeSlot                 Morgen, Mittag, Nachmittag, Abend, Nacht
     ├─ intoxication, hangover, fatigue, fed, roomPaidDay
     ├─ travel: LifeTravel?
     ├─ npcs: List<NpcIdentity>            alle Personen der Welt (≈ 70), persistent
     ├─ companionChoice, companionNpcId    Ergebnis der Gefährtin-Frage
     ├─ jobs, news, eventsDone, pendingGossip, log
     ├─ party, gear, gifts, wound, captiveUntil, skirmish   (Meilenstein 2)
     ├─ bonds, trainingDay, enlisted, …                     (Meilenstein 3)
     └─ lordQuest, lordQuestsDone, knighted, tournament, tournamentsWon, lastAudienceDay   (Meilenstein 4)
```

`NpcIdentity` (persistent, nie zufällig verändert): Name, Geschlecht, Alter ≥ 18, Volk, Kultur, Herkunft, Beruf, Stand, Hintergrund, Aussehen, Persönlichkeit, Interessen, Vorlieben, Abneigungen, Ziel, Moral, Kampfstil, Rolle, Heimatprovinz, Routine, Romantik-Offenheit (`romanceable`, `attractedTo`), `NpcRelation` (Stufe, Sympathie, Vertrauen, Anziehung, Rivalität, Grenzen, Nächte, Kränkung) und `memories`.

## Engines

| Engine | Aufgabe |
|---|---|
| `LifeEngine` | Spielstart in vier Modi, Börse, Essen/Trinken/Schlafen, Zeitfenster, landloser Tag, Reisen, Aufträge, Weltpuls, Gerüchte, Lehnseid, Validierung |
| `NpcEngine` | Deterministische Personengenerierung (Seed = Id), Gefährtin aus Entwurf (voll/teilweise), Anwesenheit nach Routine + Tagesvariation, Erinnerungen |
| `DialogueEngine` | Interpretiert `DialogueCatalog` (Daten), Begrüßung mit Erinnerungen, Optionen mit ehrlichen Sperrgründen, soziale Prüfung, Einwilligungsentscheidung, Affären-Folgen, Fade-to-Black |
| `PartyEngine` | Gefolge: Einladung (eigene Entscheidung der Person), Söldner mit Handgeld/Sold, Abzug ohne Sold, Bindung durch Reisen, Kampfkraft, Beförderung zum Hauptmann |
| `SkirmishEngine` | Persönliche Gefechte (Feldzug, Hinterhalt, Jagd, Duell), Runden mit fünf Aktionen, Wunden, Gefangenschaft, Flucht/Lösegeld |
| `MarketEngine` | Begehbarer Marktplatz (`WalkGrid`), Schmied/Krämer/Heilerin, Ausrüstung (sichtbar am Charakter), Geschenke |
| `NpcSocialEngine` | Beziehungsnetz (`NpcBond`), Weitergabe von Sympathie, Eifersucht, Bindungsdynamik als Klatsch, wandernde Reisende, eigenes Leben der Gefährtin |
| `BarracksEngine` | Begehbarer Kasernenhof, Training mit abnehmendem Ertrag, Stadtwache, Wachdienst |
| `CastleEngine` | Begehbarer Burghof, Burgvogt je Stadt, Audienz, Lehnsauftrag (befreit eine Räuberprovinz auf der gemeinsamen Kronkarte), Ritterschlag, Turniere (drei Zweikämpfe) |
| `TavernEngine` | 12×9-Kachel-Raum, A*-Wegfindung ohne Ecken-Schneiden, Sitzplatzvergabe, Betreten/Verlassen, Würfeln, Schlägerei, Tavernenereignisse |

### Tagesablauf

- **Landlos:** `GameEngine.advanceDay` leitet auf `LifeEngine.advanceLandlessDay` um: Morgen-Reset (Rausch → Kater), Reiseankunft, Weltpuls, Klatsch, neue Aufträge. Keine Wirtschafts-, Heer-, Invasions- oder Dynastie-Engines.
- **Herrscher:** unveränderte Pipeline, am Ende `LifeEngine.dailyLife` (Tageszeit/Rausch zurücksetzen).
- **Innerhalb des Tages:** `LifeEngine.passTime` (Gespräche, Aufträge, Dates verbrauchen Zeitfenster). Schlafen beendet den Tag.

### Weltpuls (landlos)

Deterministisch je Tag: Hordenüberfälle (Garnison/Bevölkerung sinken, Unruhe steigt), Räuberbarone besetzen freies Land, Befreiungen, Annexionen durch fremde Kronen, Markt- und Turniertage. Höchstens ein Besitzerwechsel je 3 Tage, nie Spielerprovinzen, nie Städte mit Taverne. Ergebnisse landen als `WorldNews` in Gerüchten, Begrüßungen und der Chronik.

`LifeEngine.threatened(state, province)` verbindet Welt und Ort: frische schwere Nachricht oder Kron-Bedrohung → höhere Preise, Söldnerwerber in der Taverne, gedrückte Stimmung, eigener Auftrag „Flüchtlinge geleiten“.

### Präsenz statt Echtzeitsimulation

Niemand wird außerhalb des geladenen Ortes simuliert. `NpcEngine.placeOf(npc, day, slot)` berechnet den Aufenthalt aus Routine + Regelmäßigkeit + deterministischer Tagesvariation; Reisende sind nur an manchen Tagen da, Adlige selten, Söldnerführer bei Krieg in der Taverne, die selbst gestaltete Gefährtin an den ersten Abenden in der Starttaverne.

## UI

| Datei | Inhalt |
|---|---|
| `ui/NewGameWizard.kt` | Startassistent (ersetzt `CharacterCreation`) |
| `ui/CharacterEditor.kt` | Editor, Vorlagen (`LookTemplates`, SharedPreferences) |
| `ui/CharacterArt.kt` | `CharacterPortrait`, Vektor-Renderer, Ganzkörperfigur, `PortraitAssets` (Ebenen-Pipeline) |
| `ui/LifeShell.kt` | Hauptansicht landloser Charaktere: Ort, Welt, Leute, Ich |
| `ui/CastleScreen.kt` | Begehbarer Burghof: Burgvogt auf dem Thron, Wachen, Banner, Turnierschranken; Audienz- und Turnier-Panel |
| `ui/TavernScreen.kt` | Begehbare Taverne, Gespräch, Wirt, Würfeln, Zimmer, Fade-to-Black |
| `ui/App.kt` | Routing: landlos → `LifeShell`, Herrscher in Taverne → `TavernScreen`, sonst `GameShell`; Tavernen-Knopf in der Kopfleiste |

### Person und Reich (Meilenstein 4)

- Ein Lehnsauftrag wird persönlich als `SkirmishEngine`-Gefecht ausgefochten. Ein Sieg setzt die Zielprovinz in `state.crown.provinces` von `BANDITS` auf `FREE` – dieselbe Karte, die nach dem Lehnseid regiert wird. Nachricht und Chronik entstehen dabei.
- Ritterschlag (2 Aufträge, Ruf 100) senkt den Goldbedarf für die Grenzfeste von 800 auf 400.
- In großen Schlachten (`BattleResolutionEngine.personalFactor`) zählen gekaufte Waffe und Rüstung sowie offene Wunden, wenn der Herrscher persönlich kämpft. Ohne Ausrüstung und Wunden bleibt der Faktor exakt 1,10/1,22. Rüstung senkt die Verwundungswahrscheinlichkeit (`WarEngine`), eine Verwundung in der Schlacht ist dieselbe Wunde, die der Charakter in der Stadt trägt.
- Taverne: Paare sitzen nebeneinander; verfeindete Anwesende erzeugen die Szene „Alte Feindschaft“ (schlichten oder Partei ergreifen, mit Erinnerung und Weitergabe).

## Speicherung und Migration

- `SaveCodec.CURRENT_VERSION = 6`. Schema 4 und 5 werden auf 6 gehoben; `life` fehlt → Standard = klassischer Grenzherr mit Akten.
- `LifeEngine.validate` prüft Börse, Rausch/Erschöpfung, eindeutige Personen-Ids, Alter ≥ 18 aller Personen, gültige Gefährtin-Referenz, Gefolge, Ausrüstung, Beziehungen, Lehnsauftrag und Turnierstand.
- Alle Felder ab Meilenstein 2 sind additiv mit Standardwerten – Schema 6 bleibt, ältere Schema-6-Stände laden unverändert.
- Ältere Herrscher-Spielstände bekommen ihre Stadtbewohner beim ersten Tavernenbesuch (`LifeEngine.ensurePeople`), deterministisch.

## Bekannte Grenzen dieser Ausbaustufe

- Begehbar sind Taverne, Markt, Kaserne und Burghof. Eigene Innenräume (Thronsaal, Zimmer) fehlen.
- Persönliche Gefechte laufen über die eigene `SkirmishEngine`; in großen Schlachten wirken Ausrüstung und Wunden als Faktor, aber der Spieler ist dort keine einzeln gesteuerte Figur.
- Der Weltpuls ist bewusst leichtgewichtig; erst nach dem Lehnseid laufen die vollen KI-Engines.
- Sitz-Pose ist eine einfache Frontalansicht mit Hocker; keine Tischinteraktion (Trinken, Gestik).
