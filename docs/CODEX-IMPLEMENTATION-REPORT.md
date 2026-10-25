# Implementierungsbericht – erste Stabilisierung

Stand: 25.10.2026. Basis **2.1.0 / 51**, `af694d7`.
Entwicklungsbranch: `codex/audit-combat-stability`.
Pull Request: [#13](https://github.com/GoldenUnicorn24/Troopmanager/pull/13).

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
