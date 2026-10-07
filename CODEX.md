# Aktueller Arbeitsstand: v1.0 Sprint A

Arbeitsbranch: `codex/v1.0-realm-war-overhaul`, Basis v0.97 `8359712`.
Version: **1.0.0 / 46**, Save-Schema **5** mit Schema-4-Migration.
Zuerst `docs/V1.0-REALM-WAR-OVERHAUL.md`, `docs/V0.97-BATTLE-TACTICS.md`
und `docs/V1.0-SPRINT-A.md` lesen. Die vorhandenen Engines und Saves erhalten;
Sprint B–E benötigen eigene Aufträge. Tests, Assemble und Lint prüfen.

Die folgenden v0.82-Notizen sind historisch; ihre Branch-Anweisungen sind überholt.

# Realm of the Last Wall 0.82.0

Stand vom 3. Oktober 2026. Entwicklungsbranch: `codex/v0.82-overhaul`. APK-Metadaten: versionCode 38, versionName 0.82.0, Paket `com.goldenunicorn.troopmanager`.

## v0.82 Overhaul

- 0.81-Grenzfehler behoben: Lagerwachstum/Spawns persistieren, echte Verluste bei Jagd und gescheitertem Sturm, keine vorzeitige Lagerbeute, keine 800er-Gegnerkappung.
- Tagesziele besitzen echte Zielbedingungen statt eines allgemeinen Garnisons-Checks.
- Zuzug respektiert vorhandene/geförderte Kulturen; reine Elbenreiche erhalten nicht automatisch menschliche Bevölkerung.
- Grenzereignisse sind Entscheidungen mit mindestens zwei Optionen und tatsächlichen Folgen.
- Völker besitzen sichtbare Loyalität und Integration; starke Förderung erzeugt politische Reaktionen.
- Eigene Regimenter sammeln Siege, Verluste, Veteranenstufe und Beinamen und können Hauptleuten zugeordnet werden.
- Hauptleute werden aus drei Kandidaten mit Kultur, Führung, Taktik, Loyalität, Eigenschaft und unterschiedlichem Handgeld gewählt.
- Außenposten können an geeigneten Grenzorten gebaut, ausgebaut und versorgt werden. Sie verbessern Aufklärung und hemmen lokales Lagerwachstum.
- Frontier ist ein eigener Hauptbereich; die permanente Navigation ist auf Reich, Stadt, Heer, Welt und Mehr reduziert.
- Zentrale Warnleiste führt zu Grenzbedrohung, Lazarett oder Nahrungsproblem.
- Frontier-Karte nutzt Weltkoordinaten und die echten Horde-/Verbündeten-Artworks.
- CI baut und prüft 0.82; die Menüversion kommt direkt aus BuildConfig.
- Regressionstests decken die neuen Grenz-, Kultur-, Hauptmann-, Tagesziel- und Außenpostenpfade ab.
- Grenzereignisse besitzen 60 deterministische Präsentationsvarianten; Entscheidungen bleiben save-stabil und haben echte Ressourcen-/Gesellschaftsfolgen.
- Völkerloyalität und Integration wirken wöchentlich auf Zufriedenheit, politische Loyalität, Kulturspannung, Sicherheit und Rekrutierung und sind in den gemeinsamen Herrscherrat integriert.
- Nachfolgewechsel können echte dynastische Spannungen auslösen. Familiengespräch, öffentliche Erklärung oder großer Nachfolgerat lösen sie unterschiedlich; ungeklärte Konflikte belasten monatlich Legitimität und Stadt.
- Erwachsene Familienmitglieder können bei ausreichender Beziehung und Vertrauen konkrete 720-Tage-Dynastiebündnisse mit fremden Reichen eingehen; Vertrag, Chronik und Familienprofil bleiben verknüpft.
- Nachschubkonvois sind auf der Weltkarte sichtbar und bleiben physische, abfangbare Routen. Alte Feindmeldungen bekommen mit jedem Tag größere Unsicherheit und weisen bei passenden Gegnerpersönlichkeiten auf mögliche Täuschung hin.
- Reichsübersicht bündelt Herrscherpaar, Versorgung, Garnison, Grenzbedrohung, schwächste Kultur und Nachfolgestatus.
- Stadtviertel reagieren sichtbar auf die dominante Bevölkerungsgruppe; Hunger, Jahreszeit, Belagerung und Gebäudeschäden bleiben ebenfalls unmittelbar sichtbar.

## Weiterbauen

Codex soll auf `codex/v0.82-overhaul` weiterbauen. `main` und ältere `codex/v0.xx`-Branches sind nicht der aktuelle Entwicklungsstand.

## Dateien in diesem Branch

Die folgenden Quellen sind der lokale 0.82-Stand und ersetzen die älteren Fassungen:

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

Codex soll auf codex/v0.82 weiterbauen, nicht auf main.
