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

<!-- PHOTOREALISTIC-PRODUCTION:BEGIN -->
## Fotorealistische Originalproduktion – 27.10.2026

Alle folgenden Bildquellen wurden für Troopmanager mit dem OpenAI-Bildgenerator erzeugt. Keine fremden Fotos, keine realen Personen als Vorlagen; dargestellte Menschen sind erfundene Erwachsene (35/38 Jahre). Die Bildrechte werden als eigene Projektproduktion geführt; es wird keine fremde CC-Lizenz behauptet. Vollständige Prompts, eigene Referenzbeziehungen, native Maße und SHA256: `art/photorealistic/production-prompts.json`.

Runtime-Dateien werden ausschließlich mechanisch aus diesen Quellen registriert und als WebP exportiert. 40 rasierte Haarebenen und 15 objektlose Szenenzeilen sind transparente technische Flächen. Quellenarchive gehören nicht zum APK.

| Quelldatei | Herkunft |
|---|---|
| `art/photorealistic/sources/portrait-female-master.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `portrait-female-master` |
| `art/photorealistic/sources/portrait-male-master.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `portrait-male-master` |
| `art/photorealistic/sources/outfit-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `outfit-atlas` |
| `art/photorealistic/sources/hair-short-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-short-atlas` |
| `art/photorealistic/sources/tavern-background.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `tavern-background` |
| `art/photorealistic/sources/market-background.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `market-background` |
| `art/photorealistic/sources/barracks-background.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `barracks-background` |
| `art/photorealistic/sources/castle-background.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `castle-background` |
| `art/photorealistic/sources/armor-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `armor-atlas` |
| `art/photorealistic/sources/cloak-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `cloak-atlas` |
| `art/photorealistic/sources/female-blank-base.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `female-blank-base` |
| `art/photorealistic/sources/male-blank-base.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `male-blank-base` |
| `art/photorealistic/sources/eyes-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `eyes-atlas` |
| `art/photorealistic/sources/nose-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `nose-atlas` |
| `art/photorealistic/sources/mouth-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `mouth-atlas` |
| `art/photorealistic/sources/brows-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `brows-atlas` |
| `art/photorealistic/sources/female-bases-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `female-bases-atlas` |
| `art/photorealistic/sources/male-bases-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `male-bases-atlas` |
| `art/photorealistic/sources/hair-1-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-1-atlas` |
| `art/photorealistic/sources/hair-2-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-2-atlas` |
| `art/photorealistic/sources/hair-3-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-3-atlas` |
| `art/photorealistic/sources/hair-4-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-4-atlas` |
| `art/photorealistic/sources/hair-5-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-5-atlas` |
| `art/photorealistic/sources/hair-6-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-6-atlas` |
| `art/photorealistic/sources/beard-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `beard-atlas` |
| `art/photorealistic/sources/marks-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `marks-atlas` |
| `art/photorealistic/sources/brows-transparent.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `brows-transparent` |
| `art/photorealistic/sources/body-female-0-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-female-0-atlas` |
| `art/photorealistic/sources/body-female-1-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-female-1-atlas` |
| `art/photorealistic/sources/body-female-2-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-female-2-atlas` |
| `art/photorealistic/sources/body-female-3-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-female-3-atlas` |
| `art/photorealistic/sources/body-male-0-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-male-0-atlas` |
| `art/photorealistic/sources/body-male-1-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-male-1-atlas` |
| `art/photorealistic/sources/body-male-2-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-male-2-atlas` |
| `art/photorealistic/sources/body-male-3-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `body-male-3-atlas` |
| `art/photorealistic/sources/figure-outfit-0-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-outfit-0-atlas` |
| `art/photorealistic/sources/figure-outfit-1-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-outfit-1-atlas` |
| `art/photorealistic/sources/figure-outfit-2-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-outfit-2-atlas` |
| `art/photorealistic/sources/figure-outfit-3-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-outfit-3-atlas` |
| `art/photorealistic/sources/figure-outfit-4-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-outfit-4-atlas` |
| `art/photorealistic/sources/tavern-props-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `tavern-props-atlas` |
| `art/photorealistic/sources/market-props-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `market-props-atlas` |
| `art/photorealistic/sources/barracks-props-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `barracks-props-atlas` |
| `art/photorealistic/sources/castle-props-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `castle-props-atlas` |
| `art/photorealistic/sources/weapons-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `weapons-atlas` |
| `art/photorealistic/sources/contact-shadow.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `contact-shadow` |
| `art/photorealistic/sources/tavern-background-clean.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `tavern-background-clean` |
| `art/photorealistic/sources/hearth-well-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hearth-well-atlas` |
| `art/photorealistic/sources/ground-details-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `ground-details-atlas` |
| `art/photorealistic/sources/hood-on-model.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hood-on-model` |
| `art/photorealistic/sources/hood-registered.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hood-registered` |
| `art/photorealistic/sources/hair-4-complete.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `hair-4-complete` |
| `art/photorealistic/sources/figure-cloak-1-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-cloak-1-atlas` |
| `art/photorealistic/sources/figure-cloak-2-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-cloak-2-atlas` |
| `art/photorealistic/sources/figure-cloak-3-atlas.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-cloak-3-atlas` |
| `art/photorealistic/sources/figure-cloak-1-wool.webp` | Eigene OpenAI-Bildgenerierung, 27.10.2026; Prompt `figure-cloak-1-wool` |

| Laufzeitdatei | Herkunft |
|---|---|
| `app/src/main/assets/figures/female/sitting/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/sitting/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/sitting/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/sitting/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/sitting/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-0-atlas` |
| `app/src/main/assets/figures/female/sitting/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-1-atlas` |
| `app/src/main/assets/figures/female/sitting/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-2-atlas` |
| `app/src/main/assets/figures/female/sitting/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-3-atlas` |
| `app/src/main/assets/figures/female/sitting/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/female/sitting/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/female/sitting/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/female/sitting/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/female/sitting/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/sitting/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/sitting/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/sitting/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/female/sitting/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/female/sitting/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/female/sitting/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/female/sitting/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/female/sitting/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/female/sitting/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/sitting/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/sitting/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/sitting/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/sitting/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/sitting/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/standing/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/standing/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/standing/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/standing/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/standing/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-0-atlas` |
| `app/src/main/assets/figures/female/standing/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-1-atlas` |
| `app/src/main/assets/figures/female/standing/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-2-atlas` |
| `app/src/main/assets/figures/female/standing/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-3-atlas` |
| `app/src/main/assets/figures/female/standing/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/female/standing/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/female/standing/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/female/standing/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/female/standing/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/standing/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/standing/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/standing/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/female/standing/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/female/standing/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/female/standing/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/female/standing/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/female/standing/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/female/standing/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/standing/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/standing/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/standing/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/standing/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/standing/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_0/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_0/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_0/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_0/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_0/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-0-atlas` |
| `app/src/main/assets/figures/female/walk_0/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-1-atlas` |
| `app/src/main/assets/figures/female/walk_0/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-2-atlas` |
| `app/src/main/assets/figures/female/walk_0/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-3-atlas` |
| `app/src/main/assets/figures/female/walk_0/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/female/walk_0/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/female/walk_0/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/female/walk_0/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/female/walk_0/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_0/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_0/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_0/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/female/walk_0/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/female/walk_0/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/female/walk_0/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/female/walk_0/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/female/walk_0/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/female/walk_0/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_0/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_0/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_0/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_0/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_0/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_1/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_1/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_1/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_1/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_1/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-0-atlas` |
| `app/src/main/assets/figures/female/walk_1/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-1-atlas` |
| `app/src/main/assets/figures/female/walk_1/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-2-atlas` |
| `app/src/main/assets/figures/female/walk_1/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-3-atlas` |
| `app/src/main/assets/figures/female/walk_1/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/female/walk_1/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/female/walk_1/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/female/walk_1/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/female/walk_1/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_1/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_1/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_1/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/female/walk_1/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/female/walk_1/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/female/walk_1/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/female/walk_1/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/female/walk_1/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/female/walk_1/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_1/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_1/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_1/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_1/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_1/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_2/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_2/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_2/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_2/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_2/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-0-atlas` |
| `app/src/main/assets/figures/female/walk_2/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-1-atlas` |
| `app/src/main/assets/figures/female/walk_2/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-2-atlas` |
| `app/src/main/assets/figures/female/walk_2/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-3-atlas` |
| `app/src/main/assets/figures/female/walk_2/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/female/walk_2/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/female/walk_2/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/female/walk_2/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/female/walk_2/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_2/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_2/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_2/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/female/walk_2/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/female/walk_2/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/female/walk_2/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/female/walk_2/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/female/walk_2/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/female/walk_2/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_2/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_2/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_2/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_2/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_2/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_3/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_3/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_3/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_3/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/female/walk_3/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-0-atlas` |
| `app/src/main/assets/figures/female/walk_3/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-1-atlas` |
| `app/src/main/assets/figures/female/walk_3/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-2-atlas` |
| `app/src/main/assets/figures/female/walk_3/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-female-3-atlas` |
| `app/src/main/assets/figures/female/walk_3/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/female/walk_3/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/female/walk_3/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/female/walk_3/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/female/walk_3/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_3/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_3/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/female/walk_3/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/female/walk_3/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/female/walk_3/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/female/walk_3/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/female/walk_3/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/female/walk_3/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/female/walk_3/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_3/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_3/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_3/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_3/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/female/walk_3/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/sitting/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/sitting/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/sitting/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/sitting/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/sitting/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-0-atlas` |
| `app/src/main/assets/figures/male/sitting/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-1-atlas` |
| `app/src/main/assets/figures/male/sitting/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-2-atlas` |
| `app/src/main/assets/figures/male/sitting/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-3-atlas` |
| `app/src/main/assets/figures/male/sitting/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/male/sitting/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/male/sitting/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/male/sitting/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/male/sitting/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/sitting/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/sitting/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/sitting/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/male/sitting/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/male/sitting/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/male/sitting/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/male/sitting/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/male/sitting/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/male/sitting/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/sitting/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/sitting/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/sitting/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/sitting/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/sitting/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/standing/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/standing/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/standing/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/standing/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/standing/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-0-atlas` |
| `app/src/main/assets/figures/male/standing/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-1-atlas` |
| `app/src/main/assets/figures/male/standing/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-2-atlas` |
| `app/src/main/assets/figures/male/standing/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-3-atlas` |
| `app/src/main/assets/figures/male/standing/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/male/standing/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/male/standing/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/male/standing/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/male/standing/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/standing/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/standing/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/standing/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/male/standing/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/male/standing/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/male/standing/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/male/standing/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/male/standing/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/male/standing/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/standing/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/standing/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/standing/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/standing/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/standing/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_0/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_0/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_0/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_0/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_0/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-0-atlas` |
| `app/src/main/assets/figures/male/walk_0/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-1-atlas` |
| `app/src/main/assets/figures/male/walk_0/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-2-atlas` |
| `app/src/main/assets/figures/male/walk_0/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-3-atlas` |
| `app/src/main/assets/figures/male/walk_0/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/male/walk_0/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/male/walk_0/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/male/walk_0/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/male/walk_0/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_0/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_0/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_0/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/male/walk_0/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/male/walk_0/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/male/walk_0/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/male/walk_0/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/male/walk_0/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/male/walk_0/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_0/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_0/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_0/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_0/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_0/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_1/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_1/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_1/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_1/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_1/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-0-atlas` |
| `app/src/main/assets/figures/male/walk_1/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-1-atlas` |
| `app/src/main/assets/figures/male/walk_1/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-2-atlas` |
| `app/src/main/assets/figures/male/walk_1/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-3-atlas` |
| `app/src/main/assets/figures/male/walk_1/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/male/walk_1/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/male/walk_1/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/male/walk_1/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/male/walk_1/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_1/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_1/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_1/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/male/walk_1/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/male/walk_1/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/male/walk_1/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/male/walk_1/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/male/walk_1/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/male/walk_1/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_1/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_1/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_1/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_1/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_1/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_2/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_2/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_2/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_2/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_2/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-0-atlas` |
| `app/src/main/assets/figures/male/walk_2/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-1-atlas` |
| `app/src/main/assets/figures/male/walk_2/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-2-atlas` |
| `app/src/main/assets/figures/male/walk_2/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-3-atlas` |
| `app/src/main/assets/figures/male/walk_2/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/male/walk_2/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/male/walk_2/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/male/walk_2/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/male/walk_2/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_2/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_2/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_2/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/male/walk_2/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/male/walk_2/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/male/walk_2/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/male/walk_2/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/male/walk_2/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/male/walk_2/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_2/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_2/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_2/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_2/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_2/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_3/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_3/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_3/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_3/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/figures/male/walk_3/body/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-0-atlas` |
| `app/src/main/assets/figures/male/walk_3/body/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-1-atlas` |
| `app/src/main/assets/figures/male/walk_3/body/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-2-atlas` |
| `app/src/main/assets/figures/male/walk_3/body/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `body-male-3-atlas` |
| `app/src/main/assets/figures/male/walk_3/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-1-wool` |
| `app/src/main/assets/figures/male/walk_3/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-2-atlas` |
| `app/src/main/assets/figures/male/walk_3/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-cloak-3-atlas` |
| `app/src/main/assets/figures/male/walk_3/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/0_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/1_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/2_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/3_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/4_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/5_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_0.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_1.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `portraits/hair_front/6_2.webp` |
| `app/src/main/assets/figures/male/walk_3/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_3/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_3/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/figures/male/walk_3/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-0-atlas` |
| `app/src/main/assets/figures/male/walk_3/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-1-atlas` |
| `app/src/main/assets/figures/male/walk_3/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-2-atlas` |
| `app/src/main/assets/figures/male/walk_3/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-3-atlas` |
| `app/src/main/assets/figures/male/walk_3/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `figure-outfit-4-atlas` |
| `app/src/main/assets/figures/male/walk_3/shadow.webp` | Eigene Bildgenerierung; mechanischer Export aus `contact-shadow` |
| `app/src/main/assets/figures/male/walk_3/weapon/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_3/weapon/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_3/weapon/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_3/weapon/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_3/weapon/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/figures/male/walk_3/weapon/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `weapons-atlas` |
| `app/src/main/assets/portraits/armor/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/portraits/armor/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/portraits/armor/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/portraits/armor/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `armor-atlas` |
| `app/src/main/assets/portraits/base/female_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `female-bases-atlas` |
| `app/src/main/assets/portraits/base/female_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `female-bases-atlas` |
| `app/src/main/assets/portraits/base/female_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `female-bases-atlas` |
| `app/src/main/assets/portraits/base/female_3.webp` | Eigene Bildgenerierung; mechanischer Export aus `female-bases-atlas` |
| `app/src/main/assets/portraits/base/female_4.webp` | Eigene Bildgenerierung; mechanischer Export aus `female-bases-atlas` |
| `app/src/main/assets/portraits/base/male_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `male-bases-atlas` |
| `app/src/main/assets/portraits/base/male_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `male-bases-atlas` |
| `app/src/main/assets/portraits/base/male_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `male-bases-atlas` |
| `app/src/main/assets/portraits/base/male_3.webp` | Eigene Bildgenerierung; mechanischer Export aus `male-bases-atlas` |
| `app/src/main/assets/portraits/base/male_4.webp` | Eigene Bildgenerierung; mechanischer Export aus `male-bases-atlas` |
| `app/src/main/assets/portraits/beard/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `beard-atlas` |
| `app/src/main/assets/portraits/beard/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `beard-atlas` |
| `app/src/main/assets/portraits/beard/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `beard-atlas` |
| `app/src/main/assets/portraits/beard/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `beard-atlas` |
| `app/src/main/assets/portraits/brows/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `brows-transparent` |
| `app/src/main/assets/portraits/brows/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `brows-transparent` |
| `app/src/main/assets/portraits/brows/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `brows-transparent` |
| `app/src/main/assets/portraits/brows/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `brows-transparent` |
| `app/src/main/assets/portraits/cloak/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `cloak-atlas` |
| `app/src/main/assets/portraits/cloak/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `cloak-atlas` |
| `app/src/main/assets/portraits/cloak/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `hood-registered` |
| `app/src/main/assets/portraits/eyes/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `eyes-atlas` |
| `app/src/main/assets/portraits/eyes/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `eyes-atlas` |
| `app/src/main/assets/portraits/eyes/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `eyes-atlas` |
| `app/src/main/assets/portraits/eyes/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `eyes-atlas` |
| `app/src/main/assets/portraits/eyes/iris_mask.webp` | Eigene Bildgenerierung; mechanischer Export aus `eyes-atlas` |
| `app/src/main/assets/portraits/hair_back/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-short-atlas` |
| `app/src/main/assets/portraits/hair_back/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-1-atlas` |
| `app/src/main/assets/portraits/hair_back/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-2-atlas` |
| `app/src/main/assets/portraits/hair_back/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-3-atlas` |
| `app/src/main/assets/portraits/hair_back/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-4-complete` |
| `app/src/main/assets/portraits/hair_back/5.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-5-atlas` |
| `app/src/main/assets/portraits/hair_back/6.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-6-atlas` |
| `app/src/main/assets/portraits/hair_back/7.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/portraits/hair_front/0_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-short-atlas` |
| `app/src/main/assets/portraits/hair_front/0_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-short-atlas` |
| `app/src/main/assets/portraits/hair_front/0_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-short-atlas` |
| `app/src/main/assets/portraits/hair_front/1_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-1-atlas` |
| `app/src/main/assets/portraits/hair_front/1_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-1-atlas` |
| `app/src/main/assets/portraits/hair_front/1_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-1-atlas` |
| `app/src/main/assets/portraits/hair_front/2_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-2-atlas` |
| `app/src/main/assets/portraits/hair_front/2_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-2-atlas` |
| `app/src/main/assets/portraits/hair_front/2_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-2-atlas` |
| `app/src/main/assets/portraits/hair_front/3_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-3-atlas` |
| `app/src/main/assets/portraits/hair_front/3_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-3-atlas` |
| `app/src/main/assets/portraits/hair_front/3_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-3-atlas` |
| `app/src/main/assets/portraits/hair_front/4_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-4-complete` |
| `app/src/main/assets/portraits/hair_front/4_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-4-complete` |
| `app/src/main/assets/portraits/hair_front/4_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-4-complete` |
| `app/src/main/assets/portraits/hair_front/5_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-5-atlas` |
| `app/src/main/assets/portraits/hair_front/5_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-5-atlas` |
| `app/src/main/assets/portraits/hair_front/5_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-5-atlas` |
| `app/src/main/assets/portraits/hair_front/6_0.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-6-atlas` |
| `app/src/main/assets/portraits/hair_front/6_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-6-atlas` |
| `app/src/main/assets/portraits/hair_front/6_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `hair-6-atlas` |
| `app/src/main/assets/portraits/hair_front/7_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/portraits/hair_front/7_1.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/portraits/hair_front/7_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/portraits/marks/scar_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/marks/scar_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/marks/scar_3.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/marks/scar_4.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/marks/tattoo_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/marks/tattoo_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/marks/tattoo_3.webp` | Eigene Bildgenerierung; mechanischer Export aus `marks-atlas` |
| `app/src/main/assets/portraits/mouth/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `mouth-atlas` |
| `app/src/main/assets/portraits/mouth/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `mouth-atlas` |
| `app/src/main/assets/portraits/mouth/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `mouth-atlas` |
| `app/src/main/assets/portraits/mouth/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `mouth-atlas` |
| `app/src/main/assets/portraits/nose/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `nose-atlas` |
| `app/src/main/assets/portraits/nose/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `nose-atlas` |
| `app/src/main/assets/portraits/nose/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `nose-atlas` |
| `app/src/main/assets/portraits/nose/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `nose-atlas` |
| `app/src/main/assets/portraits/outfit/0.webp` | Eigene Bildgenerierung; mechanischer Export aus `outfit-atlas` |
| `app/src/main/assets/portraits/outfit/1.webp` | Eigene Bildgenerierung; mechanischer Export aus `outfit-atlas` |
| `app/src/main/assets/portraits/outfit/2.webp` | Eigene Bildgenerierung; mechanischer Export aus `outfit-atlas` |
| `app/src/main/assets/portraits/outfit/3.webp` | Eigene Bildgenerierung; mechanischer Export aus `outfit-atlas` |
| `app/src/main/assets/portraits/outfit/4.webp` | Eigene Bildgenerierung; mechanischer Export aus `outfit-atlas` |
| `app/src/main/assets/scenes/barracks/day.webp` | Eigene Bildgenerierung; mechanischer Export aus `barracks-background` |
| `app/src/main/assets/scenes/barracks/foreground_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/barracks/foreground_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `barracks-props-atlas` |
| `app/src/main/assets/scenes/barracks/foreground_2.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/barracks/foreground_3.webp` | Eigene Bildgenerierung; mechanischer Export aus `barracks-props-atlas` |
| `app/src/main/assets/scenes/barracks/foreground_4.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/barracks/foreground_5.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/barracks/foreground_6.webp` | Eigene Bildgenerierung; mechanischer Export aus `barracks-props-atlas` |
| `app/src/main/assets/scenes/barracks/foreground_7.webp` | Eigene Bildgenerierung; mechanischer Export aus `barracks-props-atlas` |
| `app/src/main/assets/scenes/barracks/foreground_8.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/castle/day.webp` | Eigene Bildgenerierung; mechanischer Export aus `castle-background` |
| `app/src/main/assets/scenes/castle/foreground_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/castle/foreground_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `castle-props-atlas` |
| `app/src/main/assets/scenes/castle/foreground_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `castle-props-atlas` |
| `app/src/main/assets/scenes/castle/foreground_3.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/castle/foreground_4.webp` | Eigene Bildgenerierung; mechanischer Export aus `castle-props-atlas` |
| `app/src/main/assets/scenes/castle/foreground_5.webp` | Eigene Bildgenerierung; mechanischer Export aus `castle-props-atlas` |
| `app/src/main/assets/scenes/castle/foreground_6.webp` | Eigene Bildgenerierung; mechanischer Export aus `castle-props-atlas` |
| `app/src/main/assets/scenes/castle/foreground_7.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/castle/foreground_8.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/market/day.webp` | Eigene Bildgenerierung; mechanischer Export aus `market-background` |
| `app/src/main/assets/scenes/market/foreground_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/market/foreground_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `market-props-atlas` |
| `app/src/main/assets/scenes/market/foreground_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `market-props-atlas` |
| `app/src/main/assets/scenes/market/foreground_3.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/market/foreground_4.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/market/foreground_5.webp` | Eigene Bildgenerierung; mechanischer Export aus `market-props-atlas` |
| `app/src/main/assets/scenes/market/foreground_6.webp` | Eigene Bildgenerierung; mechanischer Export aus `market-props-atlas` |
| `app/src/main/assets/scenes/market/foreground_7.webp` | Eigene Bildgenerierung; mechanischer Export aus `market-props-atlas` |
| `app/src/main/assets/scenes/market/foreground_8.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/tavern/day.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-background` |
| `app/src/main/assets/scenes/tavern/foreground_0.webp` | Eigene transparente technische Registrierungsebene |
| `app/src/main/assets/scenes/tavern/foreground_1.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_2.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_3.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_4.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_5.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_6.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_7.webp` | Eigene Bildgenerierung; mechanischer Export aus `tavern-props-atlas` |
| `app/src/main/assets/scenes/tavern/foreground_8.webp` | Eigene transparente technische Registrierungsebene |

<!-- PHOTOREALISTIC-PRODUCTION:END -->
