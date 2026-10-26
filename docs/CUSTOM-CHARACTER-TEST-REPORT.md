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
- Lint-Abschluss und gesamter Gradle-Exitstatus stehen beim Zwischenstand noch aus.
- Erstlauf fand einen falschen CrownCatalog-Paketverweis; nach Korrektur erneut gebaut.
`git diff --check` wurde vor dem ersten Build ohne Meldung ausgeführt (Exit 0).

## Einschränkungen

- Build und Unit-Ergebnisse siehe oben; Lint-Abschluss und CI stehen noch aus.
- Emulator-Tour und Sichtprüfung stehen aus. Das 2-s-Limit ist noch nicht nachgewiesen.
- Keine automatische Gesichts-, Augen- oder Alterserkennung. Die Person bestätigt die Maske manuell.
- Keine Offline-Freistellung; transparente Ganzkörperbilder werden verlangt.
- Eigene Ganzkörperbilder haben eine feste Pose; keine künstliche Gehanimation des Fotos.
- Noch keine 82 gemalten Porträtebenen, Figurenebenen oder gemalten Ortsbilder ausgeliefert.
- Paketimport/-export, Szenenlayout und Produktionswerkzeuge folgen den weiteren Schritten.
- APK-Zuwachs noch nicht gemessen; es liegt kein neues APK vor.
