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
| `ui/TavernScreen.kt` | Begehbare Taverne, Gespräch, Wirt, Würfeln, Zimmer, Fade-to-Black |
| `ui/App.kt` | Routing: landlos → `LifeShell`, Herrscher in Taverne → `TavernScreen`, sonst `GameShell`; Tavernen-Knopf in der Kopfleiste |

## Speicherung und Migration

- `SaveCodec.CURRENT_VERSION = 6`. Schema 4 und 5 werden auf 6 gehoben; `life` fehlt → Standard = klassischer Grenzherr mit Akten.
- `LifeEngine.validate` prüft Börse, Rausch/Erschöpfung, eindeutige Personen-Ids, Alter ≥ 18 aller Personen, gültige Gefährtin-Referenz.
- Ältere Herrscher-Spielstände bekommen ihre Stadtbewohner beim ersten Tavernenbesuch (`LifeEngine.ensurePeople`), deterministisch.

## Bekannte Grenzen dieser Ausbaustufe

- Begehbar sind Taverne und Markt. Kaserne und Burghof sind Daten-Orte (`LifePlace`) ohne eigene Szene.
- Persönliche Gefechte laufen über die eigene `SkirmishEngine`; die großen Schlachten der Combat Engine 3.0 kennen den Spieler als Person noch nicht.
- Der Weltpuls ist bewusst leichtgewichtig; erst nach dem Lehnseid laufen die vollen KI-Engines.
- Personen an Sitzplätzen werden stehend gezeichnet; Sitz-Posen fehlen.
