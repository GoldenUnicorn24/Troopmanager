# Eigene Charaktere und Grafik: Arbeits- und Testbericht

Basis: `claude/project-rebirth-rpg` @ `7bc6af5`, PR #14 war zum Arbeitsbeginn offen.
Feature-Branch: `codex/custom-character-art`. Kein Schreiben auf main.

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

- Build und Unit-Ergebnisse siehe oben; korrigierter Lint-Lauf und CI stehen noch aus.
- Emulator-Tour und Sichtprüfung stehen aus. Das 2-s-Limit ist noch nicht nachgewiesen.
- Keine automatische Gesichts-, Augen- oder Alterserkennung. Die Person bestätigt die Maske manuell.
- Keine Offline-Freistellung; transparente Ganzkörperbilder werden verlangt.
- Eigene Ganzkörperbilder haben eine feste Pose; keine künstliche Gehanimation des Fotos.
- Noch keine 82 gemalten Porträtebenen, Figurenebenen oder gemalten Ortsbilder ausgeliefert.
- Paketimport/-export, Szenenlayout und Produktionswerkzeuge folgen den weiteren Schritten.
- APK-Zuwachs noch nicht gemessen; es liegt kein neues APK vor.

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
