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

Der neue Satz umfasst Personen und die vier begehbaren Orte; ältere Kriegs-, Stadt- und
Menüillustrationen gehören nicht zu diesen 686 Dateien.

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
  Erster Satz: **30.008.516 Byte**. Nach der visuellen Haar-/Augenkorrektur erneut streng
  geprüft: **686/686**, 0 Fehler, **29.337.390 Byte** Bilddaten vor APK-Verpackung.
- GitHub-PR-Lauf [40465815951](https://github.com/GoldenUnicorn24/Troopmanager/actions/runs/40465815951),
  Code-/Asset-Commit `c3efc4d325cf65f86abe5ce36e7367d5ab62c2d2`: **593 Unit-Tests**, keine Fehler;
  Lint **0 Fehler, 22 Warnungen**. APK gebaut und durch `apksigner`/Paket-Metadaten geprüft.
  Verifikationsartefakt `12716796991` heruntergeladen und XML-/Asset-Berichte ausgewertet.
- APK-Artefakt `12716936101`: **62,877,379 Byte**, SHA256 `800a67296e0035babd6fe2bc5bf896e58e5fbe7c6afcb49fd638302b0c18f4b2`.
  Gegenüber der vorherigen Feature-APK (32.441.767 Byte): **+30,435,612 Byte**
  (**+93.816%**). Gegenüber Rebirth (32.264.361 Byte): **+30,613,018 Byte**.
  Quellenarchive sind nicht im APK enthalten; alle 686 Laufzeit-WebPs sind enthalten.
- Erste Emulatorläufe `40465815951` und `40465808490`: **23 Tests, 1 Fehler**.
  Die vier neuen Foto-Renderer-Tests bestanden; die Tour brach beim ersten Tavernenladen ab,
  weil der neue Test zusätzlich die gesamte kalte Dateiladung auf zwei Sekunden begrenzte.
  Das war kein fehlgeschlagener Zeichenzeit-Test. Korrektur: begrenzte Vorbereitung mit zwei
  Arbeitern, synchronisierter Decode-Cache gegen doppelte Dekodierung; separat bis zu zehn
  Sekunden für Dateiladen, weiterhin unverändert maximal zwei Sekunden fürs Zeichnen/Aufnehmen.
- Die ersten 20 Android-Porträts und zwölf Figurenansichten wurden angesehen. Dabei wurde eine
  fehlerhafte Haarregistrierung bei Dutt/Pferdeschwanz gefunden (Augen verdeckt). Registrierung
  erfolgt jetzt am tatsächlichen Gesichtsausschnitt statt am asymmetrischen Perückenrahmen.
  Zu große Augen wurden ebenfalls auf natürlichere Proportionen verkleinert. Die sechs
  Pferdeschwanz-/Duttvarianten lassen beide Augenreferenzpunkte frei (lokal an allen Varianten
  geprüft und als Android-Regression ergänzt). Abschließende Tour und Sichtprüfung laufen noch.
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
