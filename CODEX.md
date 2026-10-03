# Realm of the Last Wall 0.81.0

Stand vom 3. Oktober 2026. Signierte APK: versionCode 37, versionName 0.81.0, Paket com.goldenunicorn.troopmanager. Updatefähig über den v061-Signaturschlüssel.

## Spielstand

- Tagesertrag oben unter der Statuszeile. Reiter: Hof, Stadt, Heer, Grenze, Welt, Paar, Haus.
- Tagesziel mit Abendlohn. Alle drei Tage Karawane, Goldelben-Bote, kleines Lager oder Deserteure.
- Grenze: Lager wachsen, frühe Orkscharen, ab Tag 20 gemeinsame Ork/Uruk-Banner. Sturm kostet 40 Holz, scheitert bei weniger als der halben Lagerstärke, sonst 80 Gold und andere Banner +2 Tage.
- Nottruppe nur bei angekommener Belagerung ohne Soldaten an der Mauer. Danach gehen die Leute zurück in die Stadt.
- Völker: Förderung bis Stufe 5. Ab Stufe 3 wachsen ungeförderte Völker nur noch mit 40 Prozent. Nottruppe-Zahl je Volk.
- Eigene Regimenter ab Tag 40, drei Siegen oder Kaserne 4. Hauptmann für 600 Gold ab Kaserne 4, bis zu drei.
- Haus und Hofämter scrollbar. Goldelben-Rekruten höher, gedeckelt durch ihre Bevölkerung.
- Romanze ab Partnerschaft: Nacht mit Auswahl Bett, Zuber, langsam, hart. Eigenes Szenenbild. Galerie.

## Dateien in diesem Branch

Die folgenden Quellen sind der lokale 0.81-Stand und ersetzen die älteren Fassungen:

- app/src/main/java/com/goldenunicorn/troopmanager/engine/FrontierEngine.kt
- app/src/main/java/com/goldenunicorn/troopmanager/engine/EconomyEngine.kt
- app/src/main/java/com/goldenunicorn/troopmanager/engine/InvasionEngine.kt
- app/src/main/java/com/goldenunicorn/troopmanager/model/GameModels.kt
- app/src/main/java/com/goldenunicorn/troopmanager/model/FrontierModels.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/App.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/Components.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/CommandCenterScreen.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/FamilyScreen.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/PeopleCourtScreen.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/PopulationDialog.kt
- app/src/main/java/com/goldenunicorn/troopmanager/ui/FrontierScreen.kt
- app/build.gradle.kts

Codex soll auf codex/v0.81 weiterbauen, nicht auf main.
