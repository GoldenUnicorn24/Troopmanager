# Artwork credits

Realm of the Last Wall v0.90.0 uses original generated artwork created specifically for this private game prototype.

Included visual sets:
- Human army category
- Woodland Elf army category
- Gold Elf army category
- Wall Legion army category
- Main-menu fortress artwork
- World-map and isometric city background
- Human, woodland-elf, gold-elf, wall-guard and companion portraits
- Launcher/app icon

The previous Wikimedia Commons placeholder photographs are no longer used by the app build.

The artwork is intended for this game project and visually follows the user's requested medieval / elven / fortified-wall fantasy direction.

Bundled images were generated specifically for this project and encoded as WebP at their original dimensions. Category/map/city raster illustrations: 1672×941; menu: 1024×1536; portraits: 1024×1536; icon: 1254×1254. The procedural city architecture renders at device resolution independently of raster artwork. The requested 1080p raster-city minimum remains an asset quality gap; the native generated city image is 941 pixels tall.

Material icons: see [THIRD-PARTY-NOTICES.md](docs/THIRD-PARTY-NOTICES.md).

The v0.90 tactical battlefield uses original Compose Canvas geometry for walls, formations, devices, banners and exchange effects. No external battle art or third-party sprites were added.

The October 2026 battle-visual pass adds original Canvas battalions, masonry,
gate and siege-device artwork, and `assets/battle-backdrops/ground.webp`.
The ground was generated for this project with OpenAI image generation on
2026-10-09; it contains only neutral earth, moss and gravel, with no baked-in
troops or structures. The generated 1536×1024 PNG was encoded to a 1024×683,
215,876-byte WebP (quality 80). No third-party source image was used.
See [production specification and rendering budget](docs/BATTLE-VISUALS-PHASE-1.md).

## Schriften (v2.1)

- **Cinzel** (Natanael Gama) – Überschriften, SIL Open Font License 1.1, aus github.com/google/fonts, auf lateinische Zeichen reduziert.
- **Cormorant Garamond** (Christian Thalmann) – Erzähltexte, SIL Open Font License 1.1, aus github.com/google/fonts, auf lateinische Zeichen reduziert.
Lizenztexte: `docs/OFL-Cinzel.txt`, `docs/OFL-Cormorant.txt`.

## Eigene Charaktere – neue Hilfs- und Testgrafiken

- `docs/assets/portrait-guide.svg`: selbst erstellte geometrische Führungsmaske, Codex für dieses Projekt (2026-10-26); CC0-1.0.
- `docs/assets/fullbody-guide.svg`: selbst erstellte geometrische Führungsmaske, Codex für dieses Projekt (2026-10-26); CC0-1.0.

Eigene importierte Bilder stammen von Nutzenden. Herkunft und Nutzungsrechte werden beim Import
abgefragt und in den Charaktermetadaten gespeichert. Sie sind keine mitgelieferten Projekt-Assets.
- `app/src/androidTest/assets/adult-portrait.png`: selbst erstellte geometrische Testfigur (explizites Testalter 27), Pillow-Zeichnung für dieses Projekt, CC0-1.0; kein realistisches Produkt-Artwork.
- `app/src/androidTest/assets/adult-fullbody.png`: selbst erstellte transparente geometrische Testfigur (Alter 27), CC0-1.0; nur Testzwecke.
- `app/src/androidTest/assets/adult-exif.jpg`: verkleinerte, gedrehte Variante von `adult-portrait.png` mit EXIF-Orientierung 6, gleiche Herkunft und CC0-1.0.
- `docs/character-pack/example-character.zip` → `portrait.webp`: WebP-q90-Ableitung von `app/src/androidTest/assets/adult-portrait.png`, gleiche selbst erstellte Testfigur, CC0-1.0.
- `docs/character-pack/example-character.zip` → `fullbody.webp`: WebP-q90-Ableitung von `app/src/androidTest/assets/adult-fullbody.png`, gleiche selbst erstellte Testfigur, CC0-1.0.
- `docs/character-pack/example-character.zip` → `manifest.json`: selbst erstellte fiktive Identität mit Pflichtalter 27, Quelle und CC0-1.0 im Paket angegeben.
- `app/src/androidTest/assets/character-example.zip`: identische Testkopie von `docs/character-pack/example-character.zip`; enthaltene `manifest.json`, `portrait.webp` und `fullbody.webp` sind dieselben selbst erstellten CC0-1.0-Testinhalte.
- `docs/assets/tavern-layout-guide.svg`: selbst erstelltes geometrisches Hilfsraster des Tavernen-Engine-Layouts, CC0-1.0.
- `docs/assets/market-layout-guide.svg`: selbst erstelltes geometrisches Hilfsraster des Markt-Engine-Layouts, CC0-1.0.
- `docs/assets/barracks-layout-guide.svg`: selbst erstelltes geometrisches Hilfsraster des Kasernen-Engine-Layouts, CC0-1.0.
- `docs/assets/castle-layout-guide.svg`: selbst erstelltes geometrisches Hilfsraster des Burghof-Engine-Layouts, CC0-1.0.
- `docs/assets/style-reference.png`: für Troopmanager selbst erzeugte Stilreferenz mit dem OpenAI-Bildgenerator am 2026-10-26; zwei Produktionsschritte (Originalerzeugung und malerische Überarbeitung derselben Eigenproduktion), beide Prompts in `docs/assets/style-reference.prompt.txt`; keine externen Bilder, Künstlernamen oder realen Personen als Vorlage. Fiktive erwachsene Reisende (Promptalter ca. 35). Nur Stilreferenz; keine registrierte Produktions-Ebene.
