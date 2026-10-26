# Charaktererstellung, Gefährtin und Porträts

## Startassistent

`MODUS → HERKUNFT → AUSSEHEN → FÄHIGKEITEN → GEFÄHRTIN → (REICH) → AUFBRUCH`

| Schritt | Inhalt | Prüfung |
|---|---|---|
| Modus | Sandbox, Geschichte, Grenzherr, Freies Leben (Bildkarten) | – |
| Herkunft | Name, Alter, Geschlecht, Volk, Kultur; landlos zusätzlich Herkunft (9), Beruf, Vermögen (nur Sandbox), Startregion (6 Städte), Hintergrundgeschichte | Name, Alter ≥ 18 |
| Aussehen | `CharacterEditor` | – |
| Fähigkeiten | Bestehendes System: 6 Attribute ab 30, 100 Punkte; Herkunft +5 auf zwei passende; 30 Fertigkeitspunkte beim Start | alle Punkte verteilt |
| Gefährtin | **Pflichtfrage** mit A/B/C (siehe unten) | Antwort gegeben, bei A Name und Alter ≥ 18 |
| Reich | nur Grenzherr/Geschichte: Startkulturen (fairer Start wie bisher), Akte an/aus | ≥ 1 Kultur |
| Aufbruch | Zusammenfassung mit beiden Porträts, Ironman | – |

## Die Gefährtin-Frage

> „Möchtest du eine mögliche spätere Gefährtin bzw. Ehefrau schon jetzt erstellen?“

- **A · vollständig:** Name, Alter (≥ 18), Volk, Kultur, Herkunft, Beruf, Kampffähigkeit, Stand, Moral, Interessen, sechs Persönlichkeitsregler, Geschichte und das komplette Aussehen im selben Editor wie der Spieler.
- **B · teilweise:** Aussehen, Volk und drei Grundzüge; Name, Alter, Herkunft usw. erzeugt die Welt passend (deterministisch).
- **C · nein:** keine Vorfestlegung. Alle ≈ 60 romantisch offenen Personen der Welt bleiben möglich.

**Keine automatische Beziehung.** Sie startet als `SocialStage.STRANGER` mit Anziehung 0 und `timesMet = 0`. Landlos lebt sie als eigenständiger NPC in der Startstadt (Markt tagsüber, Taverne abends; an Tag 2 abends sicher in der Taverne, an Tag 1 wahrscheinlich). Sie kann Nein sagen; nach drei abgelehnten Annäherungen setzt sie eine dauerhafte Grenze. Im Grenzherr-/Geschichtsmodus folgt sie der bestehenden Heldinnen-Saga (Begegnung an ihrem Ort nach etwa einer Woche, zuerst Freundschaft); ihre Identität bleibt zusätzlich als Datensatz erhalten.

## Aussehen (`CharacterAppearance`)

| Gruppe | Merkmale |
|---|---|
| Gesicht | Gesichtsform (5), Kiefer 0–100, Wangenknochen 0–100, Nasenform (4), Nasengröße 0–100, Ohren (rund/spitz/lang) |
| Augen & Mund | Augenform (4), Augenfarbe (6), Brauen (4), Lippen 0–100, Mundbreite 0–100, Ausdruck (4) |
| Haut & Zeichen | Hautton (6), Narben (5 Lagen), Tätowierungen (4), Bart (5) |
| Haare | Frisur (8), Länge 0–100, Farbe (8), Struktur (glatt/wellig/lockig) |
| Körper | Größe 0–100, Körperbau (4), Muskulatur 0–100, Haltung (4) |
| Ausrüstung | Kleidung (5), Rüstung (5), Umhang (4), Schmuck (5), Waffe (6) |
| Farben | Haupt- und Akzentfarbe (8), Hintergrund (6) |

`toPortraitSpec()` bildet das Aussehen auf das alte `PortraitSpec` ab, damit bestehende Bildschirme (Hof, Saga, Familie) dieselbe Person zeigen.

## Editor-Funktionen

Große Live-Vorschau (Kopf / Brustbild / Ganzkörper), Ausdrucks-Auswahl, 7 Reiter, Slider mit Beschriftung, „↺ zurück“ je Merkmal, Rückgängig (40 Schritte), Alles zurücksetzen, **kontrollierter Zufall** (10 Sperren: Gesicht, Augen, Mund, Haut, Frisur, Haarfarbe, Bart, Körper, Ausrüstung, Farben), Vorlagen speichern/laden/löschen (bis 20, lokal).

## Porträt-Pipeline

### Was ausgeliefert ist

`PaintedPortrait` – ein neuer Vektor-Renderer statt der Kreis-Gesichter:
- anatomische Gesichtskontur aus Bézierkurven, parametrisiert durch Form, Kiefer, Wangen
- gerichtetes Licht von oben links, Formschatten, Wangenschatten, Kinnschatten, Rouge
- Augenhöhle, Lid mit Lidstrich und Falte, Iris mit Radialverlauf, Iris-Fasern, Pupille, Glanzlicht, Wimpern
- Brauen aus Einzelhaaren, Nase mit Rücken, Nasenflügeln, Spitzenlicht, Lippen mit Amorbogen, Glanz und Schatten
- Haare aus Masse + 150 deterministisch gelegten Strähnen, Glanzband, Zopf/Dutt/Geflecht, Hinterhaar
- Bart als Masse mit Strähnen oder Stoppeln, Narben mit Wulst, Tätowierungen
- Kleidung mit Ausschnitt, Schnürung, Steppung, Robe; Leder mit Naht, Kettenhemd aus Ringen, Schuppen, Plattenpanzer mit Grat und Nieten; Umhang, Pelzkragen, Elbenkapuze, Schmuck
- Ganzkörperfigur für Editor und Taverne (Größe, Statur, Haltung, Waffe, Gehanimation)

Ehrliche Einordnung: Das ist deutlich detaillierter als v2.1, bleibt aber prozedurale Vektormalerei und **kein** gemaltes semi-realistisches Artwork. Der Editor sagt das in einer Zeile selbst.

### Asset-Vertrag für echte gemalte Ebenen

`PortraitAssets` verwendet jede erfolgreich geladene gemalte Ebene einzeln. Eine fehlende oder beschädigte Ebene wird durch dieselbe prozedurale Komponente ersetzt; ein Basisgesicht ist keine Voraussetzung für andere Ebenen. Alle Dateien: WebP mit Alpha, **1024 × 1280 px**, identische Kopfposition (Augenlinie bei y = 560, Gesichtsmitte x = 512), Licht von oben links, gemalter semi-realistischer Fantasy-Stil, neutraler Grauton für eingefärbte Ebenen.

| Ordner | Dateien | Anzahl | Einfärbung |
|---|---|---|---|
| `base/` | `female_0..4.webp`, `male_0..4.webp` (Haut + Gesichtsform) | 10 | Hautton (Multiply mit Alpha-Maske) |
| `eyes/` | `0..3.webp` (Augenform), `iris_mask.webp` | 5 | Iris: Augenfarbe |
| `brows/` | `0..3.webp` | 4 | Haarfarbe |
| `nose/` | `0..3.webp` | 4 | – |
| `mouth/` | `0..3.webp` (Ausdruck) | 4 | – |
| `hair_back/` | `0..7.webp` | 8 | Haarfarbe |
| `hair_front/` | `{0..7}_{0..2}.webp` (Frisur × Struktur) | 24 | Haarfarbe |
| `beard/` | `1..4.webp` | 4 | Haarfarbe |
| `marks/` | `scar_1..4.webp`, `tattoo_1..3.webp` | 7 | – |
| `outfit/` | `0..4.webp` | 5 | Hauptfarbe |
| `armor/` | `1..4.webp` | 4 | – |
| `cloak/` | `1..3.webp` | 3 | Akzentfarbe |

Summe: **82 Ebenen** (≈ 6–9 MB als WebP q80). Der Ganzkörper-Standard (768 × 1536, Fußlinie y=1480, sechs Posen) und die Produktionsliste stehen in `ASSET-PRODUCTION.md`. Lizenz: nur eigens für das Projekt erstellte oder nachweislich frei nutzbare Bilder; Herkunft in `CREDITS.md` eintragen.
