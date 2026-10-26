# Project Rebirth – Audit (Phase 0)

Stand: 25.10.2026

## Ausgangslage

| Punkt | Befund |
|---|---|
| Repository | `GoldenUnicorn24/Troopmanager` |
| Geprüfte Basis | `main` = `af694d7eab9f301178ed94ade0b5daa7fabc6eb4` („feat(ui): v2.1 – Karte zuerst“) |
| Version der Basis | 2.1.0 (versionCode 51), Save-Schema 5 |
| Version 2.2? | Auf keinem Branch, Tag oder Release vorhanden. `main`, `claude/v1.1-koenigreich-overhaul` und der Tag `dev-latest` zeigen alle auf `af694d7`. Die übrigen Branches (`codex/v0.x`, `codex/v1.0-realm-war-overhaul`, `feature/next-level-gameplay-ux`) sind älter. `codex/audit-combat-stability` ist ein paralleler offener Stabilitäts-PR von Codex. |
| Feature-Branch | `claude/project-rebirth-rpg` (der CI-Workflow baut nur Pushes auf `main` und `claude/**`; ein `feat/…`-Name wäre ungebaut geblieben) |
| Neue Version | 2.2.0 (versionCode 52), Save-Schema 6 |

## Arbeitsumgebung

- Kein Android SDK in der Cloud-Umgebung (dl.google.com/maven.google.com gesperrt). Gebaut, gelintet, getestet und per Emulator fotografiert wird deshalb in GitHub Actions (`.github/workflows/android.yml`: Unit-Tests → Lint → Debug-APK → Emulator-UI-Tests mit Screenshots auf dem Branch `ui-screenshots`).
- Zusätzlich lokal: Kotlin 2.0.21 + aus Quellcode gebautes kotlinx-serialization 1.7.3 + JUnit-Stubs. Damit laufen alle Modell-/Engine-/Daten-Tests (ohne die drei UI-Projektionstests und den Audio-Test) auf der JVM.

## Vorhandene Systeme (bewahrt)

| Bereich | Dateien | Bemerkung für Rebirth |
|---|---|---|
| Spielzustand | `model/GameModels.kt` (`GameState`, 40+ Teilzustände) | Additiv um `life: LifeState` ergänzt |
| Spielstart | `ui/App.kt` `CharacterCreation`, `engine/GameEngine.newGame` | Nur „Herrscher erschaffen“, immer mit Festung und Armee. 100 Attributpunkte + 30 Fertigkeitspunkte vorhanden und wiederverwendet |
| Porträts | `ui/DrawnPortrait.kt` (`PortraitSpec`, Kreise/Ellipsen auf 100×100-Raster), gemalte WebP-Porträts in `res/drawable-nodpi` | Stil zu simpel (siehe Vergleich in TEST-REPORT). `PortraitSpec` bleibt als Kompatibilitätsformat |
| Partnerin | `HeroineSagaEngine`, `PartnerProfile`, `RelationshipEngine` (Einwilligungsprofil, Romantikstufen) | Gefährtin wird schon heute frei gestaltet, aber nur als Schablone einer der vier Heldinnen; keine „Nein“-Option beim Start |
| Welt | `CrownState` (42 Kronprovinzen als Diff-Overlay), `WorldEngine`, `DiplomacyEngine`, `RivalEngine`, `ActEngine` | Kronprovinzen sind die autoritative Weltkarte – Rebirth nutzt genau diese |
| Zeit | `GameEngine.advanceDay` (20+ Engines pro Tag) | Wird für landlose Charaktere nicht ausgeführt (ruhendes Reich) |
| Speichern | `data/SaveCodec.kt` (versioniert, Prüfsumme, Validierung, Migrationen 1→5) | Schema 6 mit additiver Migration |
| Navigation | `MainArea`/`Spot`, `GameShell` | Unverändert für Herrscher; landlose Charaktere erhalten `LifeShell` |
| Tests | 55 Testdateien, ~500 Tests | Alle grün; 4 Stellen mit fest verdrahteter Schema-Nummer 5 auf `CURRENT_VERSION` umgestellt |

## Risiken und Entscheidungen

1. **Landloser Start ohne zweites Weltmodell.** Statt das Reich aus dem Zustand zu entfernen (riskant für 20+ Engines und Validierungen), bleibt die Grenzfeste als „ruhendes Lehen“ im Spielstand. Sie wird nicht simuliert und nicht gezeigt, bis der Charakter den Lehnseid leistet. Die Welt läuft über einen leichten `LifeEngine.worldPulse` auf denselben Kronprovinzen weiter.
2. **Schema 6 additiv.** Fehlt `life`, gilt `LifeState()` = klassischer Grenzherr mit Akten. Kein Feld eines alten Spielstands geht verloren (Test `SchemaSixMigrationTest`).
3. **Porträtqualität.** In dieser Umgebung steht kein Bildgenerator zur Verfügung. Deshalb: neuer, deutlich detaillierterer Vektor-Renderer *plus* fertige Ebenen-Asset-Pipeline mit dokumentiertem Dateivertrag. Keine Behauptung „Premium-Art fertig“.
4. **Erwachsene Inhalte.** Alle Personen ≥ 18 (Generierung, Editor, Save-Validierung). Einwilligung explizit, kein Sex bei starker Alkoholisierung, Fade-to-Black ohne explizite Darstellung. Minderjährige Erben (Dynastie) bekommen keine Romantikoptionen.
