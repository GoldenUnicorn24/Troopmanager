# Fotorealistische Bilder – Prüfung und Grenzen

Stand: 27.10.2026, Feature-Branch `codex/custom-character-art`, PR #15.
Der Nutzer hat den gemalten Stil durch realistische Bilder ersetzt. Die bisherigen
Import-/Save-Schema-7-Funktionen bleiben erhalten; dieser Schritt liefert die echten Bilddateien.

## Gelieferter Bildsatz

56 selbst erzeugte fotorealistische Quellen mit vollständigen Prompts, Referenzbeziehungen,
nativen Bildmaßen und SHA256 in `art/photorealistic/production-prompts.json`.
Daraus exportiert: 82 Porträt-, 564 Figuren- und 40 Szenenebenen, insgesamt 686 WebP-Dateien.
55 davon sind technisch notwendige transparente Varianten (rasierte Haare / Zeilen ohne Möbel),
keine abgebildeten Motive. Die übrigen 631 Dateien enthalten sichtbare Bildinhalte.
Jede Datei ist einzeln in `CREDITS.md` aufgeführt. Es gibt keine Cloud-Aufrufe im Spiel.

Die vier Orte verwenden echte Tageshintergründe, registrierte Möbelzeilen und Tageszeit-Tönung.
Die sechs Figurenposen enthalten vier unterschiedliche Gehphasen. Die Körperköpfe übernehmen
dieselben Gesichtsebenen wie die Personenporträts. Cache und Zielgrößendekodierung bleiben aktiv.

## Frische Nachweise

- Python-Prüfwerkzeug: 13 Tests erfolgreich (27.10.2026), einschließlich ungültiger leerer
  Körperbasis und falsch deklarierter leerer Möbelzeilen.
- Kotlin-Kompilierung einschließlich Android-Testcode erfolgreich (`compileDebugKotlin`,
  `compileDebugAndroidTestKotlin`). Der erste Lauf fand einen überzähligen Parameter im neuen
  Nacht-Testbildschirm; nach Korrektur war der zweite Lauf erfolgreich.
- Strenger finaler Asset-Check (`python3 scripts/check_assets --json`): **686/686**, keine fehlenden
  oder ungültigen Dateien. Auch nach der abschließenden Handmasken-Korrektur erfolgreich.
  Finale Bilddaten: **30.008.516 Byte** vor APK-Verpackung: Porträts 5.432.152,
  Figuren 21.174.342, Szenen 3.402.022.
- Unit, Lint, APK und Emulator-Tour: noch in Prüfung.
  Ergebnisse werden nach dem tatsächlichen Lauf ergänzt.
- Bisherige grüne Läufe aus `CUSTOM-CHARACTER-TEST-REPORT.md` betreffen die frühere Import-Pipeline
  ohne diesen Bildsatz und werden hier nicht als Nachweis für die neuen Bilder verwendet.

## Einschränkungen

- Fotorealistisch generierte Originale sind keine Fotos realer Menschen. Gesichter bestehen aus
  austauschbaren Fotolagen; manche Kombinationen wirken stärker wie Spielavatare als vollständige Fotos.
- Die Ausgabeauflösungen erfüllen den Vertrag. Quellenfelder sind teilweise kleiner und werden
  hochskaliert. Native Quellgrößen werden im Manifest unverändert ausgewiesen.
- Kleidung ist zwischen den Basen geteilt und wird an die Körperbreite angepasst. Körper, Hände,
  Gesicht und Kleidung sind modulare Bildlagen, kein durchgehendes 3D-Modell. Sitzende Figuren verwenden Hände aus der
  stehenden Aufnahme an registrierten Ärmelöffnungen. Anatomie und Kragen-/Handanschlüsse können bei extremen Kombinationen ungenau sein.
- Die vier generierten Gehphasen sind keine Motion-Capture-Animation. Lange Umhänge verdecken
  einen Teil der Beinbewegung. Alle Ansichten bleiben frontal.
- Kontinuierliche Proportionsregler verformen Fotolagen nicht beliebig wie ein 3D-Morphsystem;
  die gezeichneten Fallbacks behalten ihre bestehenden Detailregler.
- Augenmasken trennen Farbe nach Bildchrominanz; die erklärten Registrierungen werden zusätzlich
  durch echte Android-Ausgaben visuell geprüft, nicht durch automatische Gesichtserkennung.
- Android 26 ist weiterhin nicht auf einem realen/emulierten Gerät nachgewiesen.
- Eigene Ganzkörperbilder bleiben in ihrer importierten Pose. Keine automatische Freistellung;
  transparente PNG/WebP-Datei erforderlich. Gesichtsausrichtung und Volljährigkeit werden vom
  Nutzer bestätigt; keine automatische Alters-/Gesichtserkennung.
- Der JSON-Spielstandexport enthält keine eigenen Bilddateien und weist darauf hin.
  Charakterpakete enthalten ihre Bilder und können offline geteilt werden.
