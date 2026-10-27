# Eigene Charaktere und Grafik: Arbeits- und Testbericht

> Historischer Bericht der Import-Pipeline vor der Fotoproduktion. Aktueller Bildsatz und neue Prüfungen: [PHOTOREALISTIC-ASSET-REPORT.md](PHOTOREALISTIC-ASSET-REPORT.md).

Basis: `claude/project-rebirth-rpg` @ `7bc6af5`, PR #14 war zum Arbeitsbeginn offen.
Feature-Branch: `codex/custom-character-art`. Kein Schreiben auf main.

## Aktueller Nachweis (26. Oktober 2026)

Remote-CI für `27038ef`: [Lauf 40407467626](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40407467626).
Build-Job erfolgreich: 593 Unit-Tests in 59 Suites, keine Fehler oder übersprungenen Tests;
Lint 0 Fehler / 22 Warnungen; APK-Bau und Signaturprüfung erfolgreich. Auch die letzten
Android-26-Tönungs- und Decode-Begrenzungsänderungen sind in diesem Stand enthalten.
Android 35 mit KVM: **19 Instrumentierungstests bestanden**, keine Fehler und keine übersprungenen
Tests (XML-Laufzeit 81,539 s). **69 Screenshots** erzeugt; `TourTest.everyScreen` bestand einschließlich
der Vierergruppe `i-custom-*` und der Assertion von höchstens 2000 ms pro Screenshot-Aufnahme.
Das prüft den Capture-/Zeichenpfad nach dem Laden, nicht eine garantierte Kaltstartzeit.

Sichtprüfung: alle 69 Aufnahmen in sechs Kontaktbögen; zusätzlich Import, Zuschnitt, Porträtvergleich,
eigene Figur in der Taverne sowie Markt, Kaserne, Burghof und normale Taverne einzeln in voller Größe
angesehen. Die Testbilder erscheinen im richtigen Rahmen; die Augen-Führung liegt auf der Augenlinie.
Die synthetische Ganzkörper-Testfigur ist sichtbar und bleibt absichtlich in ihrer festen Pose.
Zwei nahe Tavernen-Namensschilder überlappten. `de06583` begrenzt lange Beschriftungen und staffelt
kollidierende Schilder vertikal, ohne Figuren oder Raster zu verschieben. Die frischen Prüfungen
dieser anschließenden Korrektur sind unter [PR #15 / Checks](https://github.com/GoldenUnicorn24/Troopmanager/pull/15/checks)
auffindbar; der eingefrorene Nachweis hier bezieht sich ausdrücklich auf `27038ef`.

[Maschinenlesbarer CI- und Sichtprüfungsnachweis](evidence/custom-art/remote-ci.json),
[UI-Artefakt](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40407467626/artifacts/12692372522).

Frisches CI-Debug-APK: **32.425.383 Byte**, SHA-256
`6f0afebe2a715738906509eeeeabea0f7f4d24193c4545fcb5ea6d7e4e4f1fc7`.
Gegenüber dem Basis-APK (32.264.361 Byte): **+161.022 Byte / +0,499 %**.
Die lokale inkrementelle APK (35.721.397 Byte) enthält größere ZIP-Lücken und wird nicht als
finaler Größenvergleich verwendet. Dokumentationsbilder und Instrumentierungs-Testbilder sind
im App-APK nicht enthalten. Produktionsgrafiken: weiterhin **0/686**; das 2-s-Limit mit einem
vollständigen gemalten Asset-Satz und die Android-26-Laufzeit sind noch nicht nachgewiesen.

Die folgenden Abschnitte protokollieren frühere Zwischenstände einschließlich fehlgeschlagener Läufe.

## Schritt 1 – lokaler Zwischenstand, noch nicht freigegeben

Schema 7 ergänzt `customCharacters` und optionale Bild-IDs. Schema 4–6 bleibt lesbar;
Content-URIs werden in app-eigene WebP-Dateien migriert oder auf den Fallback zurückgesetzt.
Die eigene Charakterseite verbindet Pflichtalter, Herkunft/Rechte, normale NPC-Identität,
Photo Picker, Zuschnitt, sRGB-Normalisierung und interne Speicherung. Bestehende NPC-Beziehungen
und Erinnerungen werden bei Bildersetzung erhalten. Ganzkörperbilder benötigen Transparenz.

Neue Unit-Tests: `CharacterImageGeometryTest`, `SchemaSevenMigrationTest`.
Frischer Lauf: Gradle 8.9 / Temurin 17, `:app:testDebugUnitTest :app:lintDebug
:app:assembleDebug :app:assembleDebugAndroidTest`, JVM-Heap 3072 MiB, zwei Worker.
- Kotlin-Hauptquellen kompiliert, Debug-APK und Android-Test-APK gebaut.
- 563 Unit-Tests in 56 XML-Suites, 0 Fehler (einschließlich aller 9 neuen Unit-Tests).
- Gesamter Gradle-Lauf: **Exit 1**, Lint meldete 3 `ProduceStateDoesNotAssignValue`-Fehler und 21 Warnungen. Die Bildlade-Stellen wurden anschließend auf `remember` + `LaunchedEffect` umgestellt; erneute Prüfung läuft.
- Erstlauf fand einen falschen CrownCatalog-Paketverweis; nach Korrektur erneut gebaut.
`git diff --check` wurde vor dem ersten Build ohne Meldung ausgeführt (Exit 0).

## Einschränkungen

- Build und Unit-Ergebnisse: aktuelle Nachweise oben; historische Fehlversuche unten.
- Emulator-Tour und Sichtprüfung auf Android 35 bestanden, siehe aktuellen Nachweis. Android 26 und
  das 2-s-Limit mit vollständigen gemalten Produktionsassets bleiben ungetestet.
- Keine automatische Gesichts-, Augen- oder Alterserkennung. Die Person bestätigt die Maske manuell.
- Keine Offline-Freistellung; transparente Ganzkörperbilder werden verlangt.
- Eigene Ganzkörperbilder haben eine feste Pose; keine künstliche Gehanimation des Fotos.
- Noch keine 82 gemalten Porträtebenen, Figurenebenen oder gemalten Ortsbilder ausgeliefert.
- Paketimport/-export und Szenen-/Produktionspipeline sind implementiert; aktuelle Nachweise siehe unten.
- APK-Zuwachs Schritt 1 ist gemessen; Endstand nach den weiteren Änderungen wird neu gemessen.

## APK-Vergleich Schritt 1

Heruntergeladenes `dev-latest`-APK von Basiscommit `7bc6af5`: 32.264.361 Byte.
Lokales Debug-APK Schritt 1: 32.353.131 Byte, Zuwachs 88.770 Byte (+0,275 %).
Build-Umgebungen können ZIP-/DEX-Größen geringfügig beeinflussen; es wurden keine Produktions-Artworks hinzugefügt.
Signaturprüfung mit Build-Tools 35 `apksigner verify` endete mit Exit 0;
`aapt dump badging` bestätigt Paket, minSdk 26, targetSdk 35, Version 2.2.0/52.
[Maschinenlesbarer Nachweis](evidence/custom-art/step1.json).

## Schritt 2 – Paketimport/-export in Prüfung

Versionsprüfung und begrenztes Lesen eines ZIP ohne Pfadextraktion sind implementiert.
Der Import befüllt den Entwurf und verlangt erneut die Rechte-/Erwachsenenbestätigung.
Export über FileProvider und Android-Teilen-Menü, nur aus dem dafür vorgesehenen Cache-Unterordner.
Ein echtes Beispiel-ZIP mit selbst erstellten Testbildern ist unter `docs/character-pack/` enthalten;
sein Manifest wurde lokal mit `jsonschema.validate` erfolgreich gegen das mitgelieferte JSON-Schema geprüft.
Die 14 Kotlin-Pakettests liegen mit 0 Fehlern vor (XML-Bericht 2026-10-26 20:13:43 UTC). Der erneute vollständige Android-/Lint-Lauf läuft noch.

## Schritt 3 – Pipeline und feste Raster

Vier `layout.json` mit je 108 Zellen, Vordergrundzeilen und Bodenpolygon; neue Kotlin-Tests
vergleichen diese Daten einschließlich Zellmittelpunkten mit den tatsächlichen Engine-Rastern.
Figurenstandard: 768 × 1536, Mittelachse 384, Fußlinie 1480, stehend/sitzend/vier Gehphasen.
Unvollständige Figurenposen verwenden die Vektorfigur. Importierte Ganzkörperbilder behalten ihre feste Pose.
Tageszeit-Grading und Bodenschatten gelten auch für eigene Figuren; Wetter bleibt prozedural.

9 Python-Tests des Prüfwerkzeugs: erfolgreich, Exit 0. Beispielpaket erneut gegen JSON-Schema,
Dateinamen, tatsächliches WebP-Encoding, Maße und Alpha geprüft: erfolgreich, Exit 0.
`make_asset_guides` erzeugte 686 Hilfsraster in einem temporären Verzeichnis, Exit 0.
`check_assets --allow-missing --json`: **0/686** Produktionsbilder vorhanden, 686 fehlen, 0 ungültige vorhandene Dateien.
Das ist ein bewusst zugelassener Fallback-Build, **kein** bestandener vollständiger Asset-Standard.
Ein Standardaufruf ohne `--allow-missing` muss bis zur Produktion der fehlenden Bilder fehlschlagen.

## Prüflauf nach Schritt 2

577 Unit-Tests, 0 Fehler (57 Suites). APK und Test-APK gebaut. Gesamt-Exit 1 wegen
`SuspiciousIndentation`; während dieses Lint-Laufs wurden bereits weitere Quelltexte geändert,
sodass seine Zeilenbezüge nicht zum aktuellen Stand passten. Dieser Lauf zählt nicht als Freigabenachweis.
Der nächste vollständige Lauf erfolgt ohne parallele Quelländerungen.

## Lokaler Emulatorversuch

Android 35 x86_64, Software-Emulation ohne KVM. ADB wurde erreichbar, Androids Systemstart
war jedoch nicht abgeschlossen. Die APK-Installation scheiterte in Androids StorageManagerService
(`PackageManagerInternal.freeStorage` auf null). Das ist kein bestandener Instrumentierungstest.
Es liegen aus diesem Versuch keine App-Screenshots und kein Zwei-Sekunden-Nachweis vor.

## Schritt 4 – Bildproduktion: ausdrücklich unvollständig

Eine selbst erzeugte, anschließend malerisch überarbeitete erwachsene Stilreferenz liegt in
`docs/assets/style-reference.png`; beide Prompts und Herkunft sind dokumentiert. Der Generator
lieferte 1122 × 1402 statt 1024 × 1280 und keine garantierte Augenregistrierung. Die Illustration
bleibt deshalb außerhalb der Laufzeit-Assets. Sie ist kein Ersatz für 82 isolierte, deckungsgleiche Ebenen.
Die konsistente gemalte Asset-Produktion und ihre tatsächliche Motivabnahme stehen weiterhin aus.
Gemalte Assets unterstützen derzeit diskrete Varianten; kontinuierliche Gesichtsdeformationen
wie im Vektor-Renderer sind für Rasterebenen nicht nachgewiesen.

## CI

Der Feature-Branch ist für die bestehende Unit-/Lint-/APK-/Emulator-Pipeline vorgesehen.
Zusätzlich werden Python-Prüfwerkzeug und Beispielpaket geprüft, fehlende Produktionsdateien
als Fallback-Bericht hochgeladen. APK-/Screenshot-Artefakte dienen der Prüfung.
Ein grüner Remote-CI-Lauf ist zum Zeitpunkt dieses Eintrags noch nicht nachgewiesen.

## Lokale Abschlussprüfungen vor Remote-CI

- Stand `62204c7`: kompletter Lauf mit Unit, Lint, App- und Test-APK **Exit 0**, 587 Tests ohne Fehler, 9m 53s.
- Editor-/Identitätskorrekturen: kompletter Lauf **Exit 0**, 592 Tests ohne Fehler, 9m 23s.
- Zusätzlicher Test für dieselbe alte Foto-URI bei verschiedenen Personen: Unit, App- und Test-APK **Exit 0**, 593 Tests ohne Fehler, 3m 36s.
- Danach ergänzt: Alpha-kompatible Tönung auf Android 26–28 und eine engere Obergrenze für dekodierte Zwischenbilder; frischer Remote-CI-Nachweis folgt.

[Nachweis Schritt 3](evidence/custom-art/step3-local.json), [Nachweis Identitätskorrekturen](evidence/custom-art/step4-local.json),
[Nachweis getrennter Fotoimporte](evidence/custom-art/step5-local.json).

Die Korrekturen verhindern das Überschreiben einer neuen Bild-ID durch einen alten Editorentwurf,
erhalten Fotos beim Rücksetzen der Vektormerkmale, entfernen eigene Bildreferenzen beim Wechsel
zum Standardporträt konsistent und exportieren aktuelle Namen und Altersangaben.
Neue nullable `npcIdentityId`-Felder verknüpfen normale NPCs mit Hofdarstellungen, auch wenn ein Bild
erst nach dem Hofwechsel importiert wird. Alte Verknüpfungen werden nur bei eindeutiger Namens-/Kultur-
oder Artübereinstimmung mit expliziter Hof-Erinnerung rekonstruiert; mehrdeutige Fälle bleiben unverknüpft.
Solche alten Mehrdeutigkeiten erfordern die explizite Auswahl der Person beim Import.
