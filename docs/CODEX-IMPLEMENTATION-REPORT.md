# Implementierungsbericht – Stabilisierungsphasen

Stand: 25.10.2026. Basis **2.1.0 / 51**, `af694d7`.
Entwicklungsbranch: `codex/audit-combat-stability`.
Pull Request: [#13](https://github.com/GoldenUnicorn24/Troopmanager/pull/13).

Die folgenden Abschnitte bis zur zweiten Stabilisierung dokumentieren die erste
Lieferung. Die Fortsetzung am Ende erweitert deren Umfang und beschreibt das
zusätzliche optionale Replay-Feld ausdrücklich.

## Ergebnis und Grenzen

Die erste Phase korrigiert drei zusammenhängende Fehler der taktischen
Schlachtvorbereitung. Bestandsaufnahme und Folgeplanung liegen in
[CODEX-FULL-AUDIT.md](CODEX-FULL-AUDIT.md) und [CODEX-ROADMAP.md](CODEX-ROADMAP.md).
Weitere strategische Systeme, UI, Assets und Balancing wurden nicht umgebaut.

1. **Speicherbarkeit mit Großer Mauer II:** Die Planwahl begrenzt Deckung auf 0,85,
   entsprechend bestehender Validierung und maximaler Wirkung von `SiegeEngine.exposure`.
   Vormals mögliche 0,95 verhinderten die Validierung beim Autosave.
2. **Konsistenter Replay-Start:** Nach der einmaligen Planwahl werden die tatsächlich
   verwendeten Truppen, Fronten, Mauersegmente, Helden-/Reserve-/Sammel-Perks und der
   Moralbonus in den bestehenden `BattleReplayStart` übernommen. Das lädt keine
   Munition nach und verändert keine Verlustformel.
3. **Kein nachträglicher Planbonus:** Planwahl erfordert aktive Schlacht, Schritt 0,
   Minute 0 und unveränderte Planphase. Auch ein über die Detailsteuerung begonnener
   oder bereits beendeter Kampf kann dadurch nicht nachträglich verstärkt werden.
   Bereits über „Details“ gestartete Spielstände werden als laufende Simulation
   angezeigt und bleiben bis zum Ende fortsetzbar. Wendepunkt und Schlussentscheidung
   übernehmen bei Mauerschlachten den richtigen Befestigungskontext.

Der Replay-Nachweis dieser Phase betrifft den Start und den ersten Austausch für
alle acht Pläne. Spätere Befehlspunkt-Auffüllungen durch taktische Wendepunkte sind
ein separater offener Befund (Roadmap B09), kein erledigter Gesamtreplay-Nachweis.

## Geänderte Dateien

| Datei | Änderung |
| --- | --- |
| `app/src/main/java/com/goldenunicorn/troopmanager/engine/TacticalBattleEngine.kt` | Grenzwert, Vorbedingungen, Replay-Snapshot und sichere Rückkehr aus der Detailsteuerung |
| `app/src/test/java/com/goldenunicorn/troopmanager/engine/TacticalBattlePersistenceTest.kt` | Acht Regressionstests an der öffentlichen Engine-/Save-Schnittstelle |
| `docs/CODEX-FULL-AUDIT.md` | Versions-/Branchprüfung, Architektur, Systeminventar, Fehler, Risiken und Prüflücken |
| `docs/CODEX-ROADMAP.md` | Priorität, Aufwand, Abhängigkeit und Status der angefragten Entwicklungsbereiche |
| `docs/CODEX-IMPLEMENTATION-REPORT.md` | Umsetzung und tatsächliche Prüfergebnisse |
| `docs/evidence/codex-audit/tactical-before.json` | Aus den aktuellen JUnit-XML-Berichten abgeleiteter Regressionsnachweis |
| `docs/evidence/codex-audit/source-fingerprints.json` | Prüffingerabdruck des getesteten Quellstands einschließlich Build-/CI-Konfiguration |
| `docs/evidence/codex-audit/tactical-after.json` | Aus JUnit-/Lint-/Artefaktdaten abgeleitete Ergebnisse des korrigierten Quellstands |

## Kompatibilität

Save-Schema **5**, Kampfversion **3**, Replay-Regelversion **3** und APK-Version
**2.1.0 / 51** bleiben unverändert. Keine neuen serialisierten Felder, Abhängigkeiten
oder Assets. Alte Speicherstände behalten ihre Felder/Defaults; es wird keine
zusätzliche Migration benötigt. Bestehende Replays werden nicht rückwirkend
rekonstruiert. Ein vormals fehlgeschlagener Autosave lässt sich nicht nachträglich
wiederherstellen. Offlinebetrieb bleibt erhalten.

## Frische Verifikation

Lokal sind JDK 21 und Git vorhanden, aber Gradle/Android SDK/Emulator fehlen. Deshalb
werden Android-Gradle-Prüfungen über die **bestehende GitHub-Actions-Pipeline**
ausgeführt; lokale Builds oder Gerätetests werden nicht behauptet.

### Reproduktion vor der Korrektur

Testcommit `b0f0fb5` enthält nur neue Tests, Produktionscode entspricht `af694d7`.
[CI-Lauf 40215775836](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40215775836):

- `gradle :app:testDebugUnitTest --stacktrace`: **Exit 1**.
- **488 Tests, 5 Fehler, 0 Errors, 0 übersprungene Tests**, 47 Suiten.
- Genau fünf neue Regressionen schlagen als `AssertionError` fehl: Deckungsgrenze,
  Replay-Startzustand, erster Replay-Austausch, Plan nach Beginn und Plan nach Ende.
- Alle 482 bisherigen Tests sowie der neue Kontrollfall für einmalige Planwahl
  ohne Großbaubonus bestehen. Kein Compiler-/Umgebungsfehler als Reproduktion.
- Lint, APK und Emulator wurden wegen der erwarteten roten Tests übersprungen.
- Maschinenlesbare Zusammenfassung: [tactical-before.json](evidence/codex-audit/tactical-before.json).

### Nach der Korrektur

Der erste Implementierungscommit `9cc4c90` bestand die 488 Unit-Tests.
Das unabhängige Review identifizierte anschließend einen Fortsetzungsfall: Ein
bereits über Details gestarteter Kampf muss ohne neue Planboni weiterlaufen können,
auch als Mauerschlacht am Wendepunkt. Zwei weitere Tests und die entsprechende
Phasenprojektion/Kontextübernahme ergänzen die Korrektur. Endgültiger Quellstand:
`9deb73e1d99c8aed6de91683fb6ab679432348b7`.
[CI-Lauf 40216568844](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40216568844):

| Prüfung | Ergebnis |
| --- | --- |
| `gradle :app:testDebugUnitTest --stacktrace` | Exit 0; **490 Tests, 0 Failures, 0 Errors, 0 übersprungen** in 47 Suiten, einschließlich aller acht neuen Tests |
| `gradle :app:lintDebug --stacktrace` | Exit 0; **0 Fehler, 20 Warnungen, 12 Hinweise**; kein vollständig warnungsfreier Stand |
| `gradle :app:assembleDebug --stacktrace` | Exit 0; installierbare Debug-APK erstellt |
| `apksigner verify --verbose` und `aapt dump badging` | Exit 0; v2-Signatur gültig; Paket `com.goldenunicorn.troopmanager`, 2.1.0 / 51 |
| `gradle :app:connectedDebugAndroidTest --stacktrace` | Exit 0; **10/10 Tests** auf Android 35, keine Fehler/übersprungenen Tests |
| Lokale Dokument-/Diffprüfung | Relative Dokumentlinks vorhanden, `git diff --check` erfolgreich |

Die Test-/Lint-Berichte wurden aus dem CI-Artefakt heruntergeladen und lokal
ausgewertet; Zahlen sind nicht aus Testdeklarationen geschätzt. Die Archivhashes
stimmen mit den GitHub-Artefaktdigests überein.

APK-Artefakt: [Realm-of-the-Last-Wall-v2.1.0-debug](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40216568844/artifacts/12607678244).
APK: **31.559.853 Bytes**, SHA-256
`785b664b3533827447d78d0d19bf7fc738e32c456ece734082a70911c52ca27b`.
Lokale Kopie im Arbeitsbereich: `build/apk-after/app-debug.apk` (nicht eingecheckt).
Ein Build-/Signaturnachweis ist kein physischer Gerätetest.

Der erfolgreiche Emulatorlauf umfasst kleine Hochformatansichten, Querformat-
Vorschau, große Schrift, reale Salven-/Verlustdarstellung, Plansteuerung, den
taktischen Ablauf bis zu einer Schlüsselentscheidung und einen Screenshot-Rundgang
durch die Navigation. Das beweist weder produktive Activity-Rotation noch
Spielbalance über ganze Kampagnen. [UI-Berichte und Screenshots](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40216568844/artifacts/12608167943)
liegen als eigenes Artefakt vor. Kein physisches Gerät wurde getestet.
Die aktuellen Screenshots der taktischen Planansicht und der kleinen
Hochformat-Detailansicht mit 160-%-Schrift wurden zusätzlich visuell gesichtet;
dies ist keine manuelle Abnahme sämtlicher Screens. CI-Artefakte haben 30 Tage
Aufbewahrungszeit; die kompakten Ergebnisdaten/Fingerabdrücke bleiben im Repository.

Verbleibende Lint-Warnungen betreffen Ziel-API/Abhängigkeitsstände, große
Vektorressourcen, feste Activity-Ausrichtung, Modifier-Konventionen und einen
überflüssigen Ressourcenqualifizierer. Keine Abhängigkeiten wurden pauschal
aktualisiert; diese Befunde bleiben in den jeweiligen Folgephasen zu bewerten.

Eine unabhängige rein lesende Abschlussprüfung von `9deb73e` meldet keine weiteren
blockierenden Befunde. Dies ist ein Quellreview und ersetzt keine Ausführung.

Abnahmekriterien der acht Tests:

- Große Mauer mit ausgebauten Wällen/Türmen: Save/Load identisch, nächster taktischer
  Schritt identisch, Kampagnenvorräte und geladene Munition durch Planwahl unverändert.
- Alle acht Pläne: tatsächliche Startbedingungen im Replay, vollständiger Save-Roundtrip.
- Alle acht Pläne: erster Kernaustausch mit identischen Kontingenten, Fronten,
  Segmenten, Austauschberichten, Pfeilbestand und Verlusten im Replay.
- Über Detailsteuerung begonnene sowie abgeschlossene Schlachten: Zustand unverändert.
- Plan nur einmal; ohne Großbau kein zusätzlicher Deckungs-/Kohäsionsbonus.
- Gespeicherte Detail-Feld-/Mauerschlacht: laufende Taktikphase ohne neue Planwahl,
  erfolgreiche Schnellauflösung, unveränderter Planbonus und speicherbares Ende.
- Tatsächlicher Leiterkontakt aus der Detailsimulation: Mauerschlacht-Wendepunkt
  mit richtiger Klassifikation und speicherbarer Entscheidung.

| Anforderung | Geänderte Schnittstelle | Ausführungsnachweis | Status |
| --- | --- | --- | --- |
| B01: speicherbare Große-Mauer-Planwahl | `choosePlan`, Deckungsgrenze | `greatWallPlanCanBeSavedAndResumed`, Save/Load und identischer Folgeschritt | Bestätigt |
| B02: konsistente Startbedingungen | `choosePlan`, bestehender Replay-Snapshot | Snapshot-/Save-/erster-Austausch-Tests für alle acht Pläne | Bestätigt für diesen Umfang |
| B03: kein nachträglicher Bonus, Detailfortsetzung bleibt möglich | `choosePlan`, `stage`, Wendepunkt-/Schlussübergang | Ablehnung laufender/beendeter Schlacht, Abschluss Feld/Mauer und echter Leiterkontakt | Bestätigt |
| Bestehende Kampagnen erhalten | Keine Feld-/Schemaänderung | Alle vorhandenen JVM-Suiten einschließlich Migration/Speicherung erfolgreich | Bestätigt für vorhandene Tests; kein beliebiger realer Altsave garantiert |

## Verbleibende Prüfungen und Aufgaben

- Vollständiger taktischer Replay-Ablauf einschließlich Wendepunkt-Befehlspunkten.
- Handels-/Söldnerfehler, Armee-Mutationsgrenzen und Wirtschaftskapazitäten.
- Tatsächliche UI-Thread-Laufzeiten, Animation/Haptik, TalkBack und Querformat.
- Physisches Samsung Galaxy S26 Ultra, APK-Installation/Update auf einem realen
  Gerät, Akkumessung und lange Kampagnen über Generationen.
- `main` bleibt erhalten; kein Merge, Release-Publishing oder Austausch von `dev-latest`
  ist Teil dieser Lieferung. Die vorhandenen PR-Jobs veröffentlichen nur Prüfartefakte.

## Zweite Stabilisierung – Wirtschaft, Armeesicherheit, Replay und UI

Fortsetzung auf demselben GitHub-Branch und PR. Produktionsstand zur Prüfung:
`c680c5d8a10268c6ab0688c08ac64f777fa369c0`.

### Tatsächlich implementiert

| Bereich | Änderung | Dateien unter `app/src/main/java/com/goldenunicorn/troopmanager/` |
| --- | --- | --- |
| Handelsbuchung | Der Hafenbonus erhöht tatsächlich gutgeschriebenes Exportgold; Bericht/Profit berücksichtigen Schatzkammer- und Int-Grenzen. Die bisherige Warenbewertung der Gold-Kaufroute bleibt erhalten. | `engine/CrownEngine.kt` |
| Söldner | Desertion wird je Vertrags-ID berechnet und je Typ summiert. Heimtruppen, Verträge und Bevölkerung verlieren dieselbe tatsächlich verfügbare Zahl. | `engine/CrownEngine.kt` |
| Große Vorräte | Das 35-%-Holzbudget der Arsenalproduktion wird mit Long berechnet. | `engine/MilitaryEconomyEngine.kt` |
| Schlachtsnapshots | Stationieren, Besatzung zurückholen, Söldner anwerben/entlassen verändern während ACTIVE/PURSUIT keine eingesetzten Armeepools; nach Ende wieder zulässig. | `engine/CrownWarEngine.kt`, `engine/CrownEngine.kt` |
| Replays | Befehlspunkte vor jedem Austausch bzw. Live-Planwechsel werden aufgezeichnet und vor Reproduktion restauriert/validiert. Dies bildet auch Auffüllungen an taktischen Wendepunkten ab. | `model/WarModels.kt`, `engine/BattleEngine.kt`, `engine/WarEngine.kt` |
| Haptik | GoldButton respektiert den aktuellen Schalter über einen CompositionLocal; taktische Entscheidungen/Ticks prüfen die aktuellen Einstellungen. | `ui/Components.kt`, `ui/App.kt`, `ui/TacticalBattleScreen.kt` |
| Animationen | Kronlandfarben/Puls und taktische Stärke-/Entscheidungsanimationen werden bei ausgeschalteten Animationen statisch. | `ui/CrownMap.kt`, `ui/TacticalBattleScreen.kt` |
| Bedienbarkeit | Unterreiter mindestens 48 × 48 dp; alle Provinzen als benannte zugängliche Auswahlaktionen samt angesagter aktueller Auswahl verfügbar. | `ui/MainHubs.kt`, `ui/CrownMap.kt` |
| Berechnung | Automatische Kampfschritte und Schnellauflösung auf Default-Dispatcher. Pause/Details verwerfen ausstehende Ergebnisse; veränderte Ausgangszustände werden nicht überschrieben. | `ui/TacticalBattleScreen.kt` |
| CI | Release und gemeinsam genutzter Screenshot-Branch werden nur noch durch Push auf `main` veröffentlicht. PR-Artefakte bleiben verfügbar. | `.github/workflows/android.yml` |

`CODEX.md` verweist jetzt auf den tatsächlichen Arbeitsbranch, die aktuelle
Versionsbasis, den Replay-Kompatibilitätsvertrag und diese Berichte. Alte
Branchanweisungen sind ausdrücklich historisch eingeordnet.

Zusätzliche Tests:

- `engine/EconomyStabilityTest.kt`: zehn Fälle für Gold-/Lagerbuchung, gleiche und
  ungleiche Verträge, mehrere unbezahlte Tage, große Holzvorräte und Militärsperren.
- `engine/BattleMutationSafetyTest.kt`: zwei Fälle für Garnisonssperren und
  Soldatenerhaltung nach einem tatsächlichen Schlachtende.
- `engine/TacticalReplayContinuityTest.kt`: komplette taktische Auflösung aller
  acht Pläne mit Save/Load und reproduzierten Kampfzuständen/Befehlspunkten.
- `data/ReplayInputCompatibilityTest.kt`: drei Fälle für Alt-JSON ohne neues Feld,
  Save-Roundtrip und Zurückweisung ungültiger Befehlspunkte.
- `androidTest/.../ui/UiPreferencesTest.kt`: fünf Fälle für Haptik samt Laufzeitwechsel,
  taktische Planwahl, asynchrone Schnellauflösung, Touchziele und Provinzaktionen.

### Speicher-/Replay-Kompatibilität

Schema **5** und Regeln **3** bleiben erhalten. `BattleInput.commandPointsBefore`
ist ein zusätzliches optionales Feld mit Default `null`. Alte Inputs werden ohne
das Feld geladen und behalten die bisherigen Replay-Regeln; beim Schreiben wird
der Default ausgelassen. Neue Inputs sichern den echten Befehlspunktestand vor
Kosten/Regeneration. Aktive und historische Inputs werden validiert, der Replay-
Interpreter prüft zusätzlich gegen das tatsächliche Maximum.

Bestehende Kampagnen werden nicht neu initialisiert oder umgeschrieben. Bereits
historisch fehlende Informationen über taktische Auffüllungen lassen sich nicht
rückwirkend zuverlässig rekonstruieren. Der neue Nachweis betrifft mechanische
Kampfzustände und Befehlspunkte; UI-Stufen und erläuternde Logzeilen sind kein
vollständig rekonstruierter Bildschirmablauf.

### Frische Reproduktion und laufende Verifikation

Testcommit `9176e30` enthält ausschließlich neue JVM-Tests; Produktionscode ist
noch `46cf63f`. [CI-Lauf 40218539418](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40218539418):
**503 Tests, elf Fehler, keine Errors/übersprungenen Tests**, Exit 1.
Alle Fehler liegen in den neuen Regressionen; insbesondere konnte der komplette
PINCER-Replay nicht erzeugt werden. Maschinenlesbarer Nachweis:
[phase2-before.json](evidence/codex-stabilization/phase2-before.json).

Der integrierte Produktionsstand wurde in
[CI 40218952639](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40218952639)
geprüft:

| Prüfung | Frisches Ergebnis |
| --- | --- |
| `gradle :app:testDebugUnitTest --stacktrace` | Exit 0; **506 Tests, 0 Failures, 0 Errors, 0 übersprungen**, 51 Suiten |
| `gradle :app:lintDebug --stacktrace` | Exit 0; **0 Fehler, 20 Warnungen, 12 Hinweise** |
| `gradle :app:assembleDebug --stacktrace` | Exit 0; APK erstellt |
| APK-Signatur und Metadaten | Exit 0; gültige v2-Signatur, Paket `com.goldenunicorn.troopmanager`, 2.1.0 / 51 |
| `gradle :app:connectedDebugAndroidTest --stacktrace` | Exit 0; **15/15 Android-35-UI-Tests**, keine Fehler oder übersprungenen Tests |

JUnit-/Lint-Berichte wurden aus den CI-Artefakten lokal ausgewertet; ZIP-Hashes
stimmen mit den veröffentlichten Digests überein.
[phase2-after.json](evidence/codex-stabilization/phase2-after.json) und
[Quellfingerabdruck](evidence/codex-stabilization/source-fingerprints.json) halten
Ergebnisse und geprüften Stand fest. Lokale Gradle-/SDK-/Geräteprüfungen werden
weiterhin nicht behauptet.

[Neue Test-APK](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40218952639/artifacts/12608970759):
**31.576.237 Bytes**, SHA-256
`18f06e66cb5dbb3943c0e7fcb49b4ef10211842996de71a743c9fde85413d2b3`.
Lokale Kopie: `build/phase2-apk/app-debug.apk`, nicht eingecheckt.

[UI-Berichte und Screenshots](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40218952639/artifacts/12608852601)
enthalten die vorhandenen zehn Prüfungen und fünf neue Bedien-/Einstellungsfälle.
Die asynchrone Schnellauflösung liefert im Test genau einen abgeschlossenen
Schlachtzustand. Haptik ist auch nach einem Einstellungswechsel korrekt, alle
Provinzen lassen sich über die Semantikaktion auswählen, Unterreiter erreichen
48 dp. Das ist ein Emulator-/Semantiknachweis, keine reale TalkBack-Abnahme oder
Messung eines physisch spürbaren Vibrationssignals.

Eine unabhängige rein lesende Integrationsprüfung fand keine verbleibenden
blockierenden Befunde. Die abschließenden Guards berücksichtigen Pause/Details
auch vor der nächsten Recomposition; eine anderweitige Zustandsänderung beendet
die automatische Simulation nicht dauerhaft. Abbruchrennen wurden durch Review
geprüft, nicht durch einen gezielt verzögerten Concurrency-Test nachgestellt.
Nachfolgende Dokumentationscommits ändern den getesteten Quellfingerabdruck nicht.

### Verbleibender Umfang

Diese Fortsetzung schließt konkrete Stabilitäts- und Bedienfehler; sie ist keine
fertige Umsetzung aller langfristigen Strategie-RPG-Erweiterungen. Insbesondere
Crown-/World-Verbindung, Produktions-/Lagervereinheitlichung, neue räumliche
Kampfmanöver/Formationen, große Politik-/Dynastie-/Endgame-Erweiterungen und echte
Langzeitsimulationen bleiben in der Roadmap offen. Direkte Einzelbefehle und andere
UI-Rechenpfade benötigen weiterhin Laufzeitprofiling; eine FPS-/Akkumessung wurde
nicht vorgenommen. Keine reale TalkBack-Bedienung oder physisches S26 Ultra getestet.
