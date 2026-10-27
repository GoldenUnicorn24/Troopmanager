# Charaktereditor v2 – Ursachen, Änderungen, Prüfnachweise

Branch `claude/character-editor-v2`, Basis `codex/custom-character-art` @ `479cadd`.

## Gefundene Ursachen und Korrekturen

| Beobachtung | Tatsächliche Ursache | Korrektur |
|---|---|---|
| Beim Öffnen kurz alte Grafik, dann neue | `MixedPortrait` startete mit leerer Ebenenliste und zeichnete bis zum Ende des IO-Ladens das komplette **Vektorporträt**; `PaintedFigurePortrait` zeigte bis dahin die **Vektorfigur**; Szenen (`rememberFigureArt`) ebenso | Drei explizite Zustände (`ArtState`: Laden / Fehlt / Bereit). Erstes Bild aus dem Speicher-Cache (`cachedLayersFor`, `FigureAssets.cached`, `CustomCharacterImages.peek`), sonst neutraler Platzhalter. Prozedurale Kunst nur noch, wenn Fotoebenen wirklich fehlen; Szenen zeichnen während des Ladens keine Ersatzfigur |
| Eigenes Foto: zuerst fremdes Gesicht, dann Foto | `PersonImage` zeigte während des Ladens den Fallback (erzeugtes Gesicht) und lud bei **jedem Ansichtswechsel** neu (Schlüssel `framing`) | Zustand je Bild-ID und **Bildart** (Porträt/Ganzkörper); Kopf ↔ Brustbild verwenden dasselbe Bitmap; bekannte Lücken werden gemerkt |
| Rechteckige Flecken um Nase, Augen, Mund | `drawTintedArt` nutzte ab API 29 `BlendMode.Multiply` über ein Rechteck: halbtransparente Randpixel bekamen die reine Tönungsfarbe → helle Umrisse; zusätzlich harte Ebenenränder | Tönung per Farbmatrix (unvormultipliziert, alle API-Stufen gleich). Augen-, Nasen-, Mundebenen laufen weich aus (`scripts/repair_figure_layers`) |
| Ganzkörper: Teile übereinander, schwebende Waffen | Umhang wurde **vor** den Körper gezeichnet und verdeckte ihn; Waffenebenen zeigen Waffe + Scheide **neben** der Figur; Körperebene enthielt Halsrechteck und Hände mit Oberschenkelstreifen, die über die Kleidung gemalt wurden; Komposit nur 192 × 384 px (im Editor ~5× vergrößert) | Reihenfolge Schatten → Umhang → Rückenwaffe → Haut → Kleidung → Hüftwaffe → Rüstung → Gürtelwaffe → Kopf → Haar. Waffen werden aus dem Waffenblatt geschnitten und am Körper getragen (`WeaponCarry`). 48 Körperebenen repariert (weicher Hals, Hände ohne Streifen). Komposit in Vorschaugröße (bis 768 × 1536) |
| Kapuze: Hintergrund neben den Wangen, harte Kante | Rechteckige Öffnung und gerader Schnitt in `portraits/cloak/3.webp` | Öffnung trägt ein dunkles Kapuzeninneres mit Alpha 16/255; der Renderer zeichnet die Kapuze hinten (Inneres sichtbar) und vorn (Inneres entfernt). Saum blendet aus |
| Bedienelemente auf dem Gesicht | Ansichts-Chips oben **im** Bild, Ausdruck als Overlay unten im Bild | Ansichtsleiste über, Ausdruck unter der Vorschau; Test prüft die Grenzen (`editorControlsDoNotCoverThePicture`) |
| Kein Bildupload in der Spielerstellung | `LocalOpenCustomCharacters` ist ohne laufendes Spiel `null`; der Upload war nur in „Eigene Charaktere“ erreichbar | Panel „Eigenes Bild verwenden“ in jedem Editor; Spielstart registriert Spieler- und Gefährtinnenbild (`syncEditorImage`, `registerDesignedImage`) |
| Ganzkörper-Zuschnitt schnitt Kopf/Füße ab | Zuschnitt verwendete auch für Ganzkörper „Füllen“ | Ganzkörper wird bei Zoom 1 vollständig eingepasst |

Nicht als Ursache bestätigt: ein veralteter Ergebnis-Überschreiber. `LaunchedEffect` bricht bei
neuem Schlüssel ab, und `withContext` liefert nach Abbruch nichts zurück; dennoch ist jeder Zustand
jetzt an seinen Schlüssel gebunden und durch Tests abgedeckt.

## Prüfnachweise

- GitHub Actions, Commit `75fb7c5` (Lauf 40482500083): Build-Job mit Asset-Prüfung, Unit-Tests,
  Lint und APK **erfolgreich**; Android-35-Emulatorjob (alle Instrumentierungstests einschließlich
  Tour mit Screenshots) **erfolgreich**. Unit-Testquellen: 607 `@Test`-Methoden; Instrumentierung: 34.
  Exakte XML-Zählungen liegen im Verifikationsartefakt (Anmeldung bei GitHub nötig).
- `python3 scripts/check_assets`: **686/686**, 0 fehlerhaft (nach allen Reparaturen).
- Screenshots: Branch `ui-screenshots` (Ordner `tour/`, `k-*` = neuer Editor). Gesichtet: Kopf,
  Brustbild, Ganzkörper (Spieler, Gefährtin), eigenes Porträt in drei Ansichten, eigenes Ganzkörperbild,
  Bildpanel, Anforderungen, Zuschnitt, Porträtvergleich, Tavernen- und Burgszenen.
- Neue Unit-Tests: `OwnImageRegistrationTest` (Registrierung ohne Datenverlust, Ersetzen/Entfernen,
  Ganzkörper ohne Porträt, Gefährtin bleibt eigenständig), `WeaponCarryTest`, erweiterte Geometrie.
- Neue Instrumentierungstests: `CharacterArtStateTest` (kein Fremdgesicht vor eigenem Foto, kein
  Neuladen bei Kopf ↔ Brustbild, Platzhalter statt Vektorkunst, Standardfigur ohne eigenes
  Ganzkörperbild, eigenes Ganzkörperbild, Layout ohne Überdeckung, schneller Ansichtswechsel,
  Änderung während des Ladens, Hilfe erreichbar, Bildverwaltung je Art, Ablehnung zu großer und
  deckender Ganzkörperbilder).

## Grenzen

- Die Personen bestehen weiterhin aus modularen, generierten Fotolagen. Die sichtbaren Nahtfehler sind
  behoben; ein echtes Foto einer Person erreichen die Kombinationen nicht. Manche Frisuren (z. B.
  „Gescheitelt“, glatt) fallen über ein Auge – ein Freistellen dort hinterließ unnatürliche Kerben
  und wurde verworfen.
- Gehposen in Szenen verwenden weiterhin ausgeschnittene Handfragmente (bei Szenengröße kaum sichtbar).
- Keine automatische Freistellung; Ganzkörperbilder brauchen Transparenz. Eigene Ganzkörperbilder
  behalten ihre Pose. Ausdruck und Gesichtsregler wirken nicht auf eigene Fotos (so gekennzeichnet).
- Der JSON-Spielstandexport enthält keine Bilddateien; Charakterpakete (.zip) enthalten sie.
- Android 26 nicht auf Gerät/Emulator geprüft; Fotoauswahl über den System-Picker ist in der
  Instrumentierung nicht automatisierbar – der Weg ab dem gewählten Bild (Zuschnitt, Speicherung,
  Registrierung) ist getestet.
