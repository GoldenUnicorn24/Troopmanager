# Project Rebirth – Testbericht

| | |
|---|---|
| Base-Branch / Commit | `main` @ `af694d7` (v2.1.0, versionCode 51) |
| Feature-Branch | `claude/project-rebirth-rpg` |
| Neue Version | 2.2.0 (versionCode 52), Save-Schema 6 |
| Pull Request | https://github.com/GoldenUnicorn24/Troopmanager/pull/14 |
| CI | GitHub Actions „Android APK“ (Unit-Tests → Lint → Debug-APK → apksigner/aapt-Prüfung → Emulator API 35 mit Screenshot-Tour) |

Build-, Start- und Funktionstest werden getrennt ausgewiesen.

## 1. Build

| Prüfung | Ergebnis |
|---|---|
| `gradle :app:testDebugUnitTest` (CI) | ✅ grün |
| `gradle :app:lintDebug` (CI) | ✅ grün |
| `gradle :app:assembleDebug` + `apksigner verify` + `aapt` (Paket, versionCode 52, versionName 2.2.0) | ✅ grün |
| Debug-APK | CI-Artefakt `Realm-of-the-Last-Wall-v2.2.0-debug` und Release `dev-latest` („Neueste Testversion“) |

## 2. Start- und UI-Test (Emulator, Android 35, Pixel 4)

`TourTest.everyScreen` startet die echte App-Shell auf jeder Seite und speichert Screenshots (Branch `ui-screenshots`, Ordner `troopmanager-battle/tour/`). `BattleLayoutTest` (kleine Displays, Querformat, 160 % Schrift) läuft unverändert mit. Ergebnis: ✅ beide grün.

Neue Seiten (`d-*.png`): Spielmodus, Herkunft, Aussehen-Editor, Gefährtin-Frage mit Vollentwurf, Zusammenfassung, Ort, Welt, Leute, Ich, Taverne, Gespräch mit der selbst gestalteten Gefährtin, Porträtvergleich alt/neu.

Behoben während der Abnahme:
- Namenskonflikt `Stone` (Kompilierfehler, Runde 2).
- Editor-Screenshot hing beim Zeichnen: Strichmuster-Pfadeffekt bei Leinwandgröße 0 → Naht ohne Pfadeffekt, Zeichnen bei Nullgröße übersprungen (Runde 4).
- Porträt zeichnete über seine Grenzen hinaus (`clipToBounds`), Kopf im Brustbild zu groß, Brauen gepunktet, Oberlippe wirkte wie Bart, Elbenohren zu lang; Taverne zu dunkel, Figuren zu klein, Ereignisbanner verdeckte die Szene (Runde 5).
- Editor-Screenshot überschritt erneut das 2-s-Zeichenlimit des Emulators: Porträts werden jetzt einmal pro Größe/Aussehen in eine Bitmap gerendert und danach nur noch geblittet (Runde 7, danach alle Jobs grün).

## 3. Funktionstests (Unit, JVM)

Gesamt lokal: **478 Tests grün** (alle Modell-/Engine-/Datentests; die UI-Projektions- und Audiotests laufen nur in CI). Neu:

`ProjectRebirthTest` (22) und `SchemaSixMigrationTest` (2):

| Kriterium | Test(s) | Ergebnis |
|---|---|---|
| A Sandbox/Freies Leben ohne Reich, Grenzherr unverändert | `freeLifeStartsWithoutRealmTitleOrArmyInCommand`, `borderLordModeKeepsTheClassicStart`, `storyModeEnablesActsAndBorderLordCanDisableThem` | ✅ |
| B Spielerfigur gespeichert und konsistent | `playerAppearanceAndBiographyPersist` (inkl. `toPortraitSpec` für Altbildschirme) | ✅ |
| C Gefährtinfrage mit drei Optionen | `allThreeCompanionChoicesAreSupported` + Screenshot `d-wizard-4` | ✅ |
| D Gefährtin als eigenständiger NPC, keine Beziehung, persistent | `allThreeCompanionChoicesAreSupported`, `companionIdentityIsStableAcrossSavesAndDays`, `companionMustBeAdult`, `landedCompanionFollowsTheHeroineSagaWithoutRomance` | ✅ |
| E Frühe Begegnung und Wiedersehen | `designedCompanionCanBeMetEarlyInTheTavernAndAgainLater` + Screenshot `d-tavern-talk` | ✅ |
| F Begehbare Taverne | `everySeatIsReachableFromTheDoor`, `tavernFillsInTheEveningAndPlacementsAreDistinct`, `drinkingCostsGoldAndAffectsSkillsButFades`, `diceAreDeterministicPerRoundAndMoveMoney` + Screenshot `d-tavern` | ✅ |
| G Soziale Handlung verändert gespeicherten Zustand mit späterer Wirkung | `conversationChangesSavedStateAndIsRememberedLater` (Begrüßung am Folgetag erwähnt den ausgegebenen Met), `romanceIsNeverAutomaticAndRespectsConsentAndSobriety`, `repeatedRejectedAdvancesSetABoundary`, `anAffairHasConsequencesForThePartner` | ✅ |
| H Sichtbare Porträtverbesserung | Screenshot `d-portraits` (alt vs. neu) | ◐ Verbesserung sichtbar, aber prozedural; gemalte Ebenen fehlen (siehe Einschränkungen) |
| I Welteinheit | `livingWorldChangesTheSharedCrownProvinces`, `threatenedTownsRaisePricesAndBringRecruiters` | ✅ |
| J Kompatibilität | `SchemaSixMigrationTest.schemaFiveSaveBecomesClassicLandedGame`, `invalidLifeDataIsRejected`, alle bisherigen Save-Tests (Schema 1–5) grün, `legacyLandedSavesGetTownsfolkOnFirstTavernVisit`, `uiReconcileChainKeepsLandlessStateStable` | ✅ |
| K Build | siehe Abschnitt 1 | ✅ |

Bestehende Tests angepasst: vier Stellen, die die Schemanummer 5 fest erwarteten (`SchemaFiveMigrationTest`, `BattleTacticsTest`), prüfen jetzt `SaveCodec.CURRENT_VERSION`; der Test „unbekanntes Zukunftsschema“ verwendet `CURRENT_VERSION + 1`.

## 4. Bekannte Einschränkungen (ehrlich)

- **Porträtqualität:** Kein Bildgenerator verfügbar. Der neue Renderer ist deutlich detaillierter als v2.1 (Licht, Lider, Iris, Strähnen, Rüstungsmaterialien, Ganzkörper), erreicht aber **kein** semi-realistisches gemaltes Niveau. Die Ebenen-Pipeline ist fertig und wartet auf 82 Assets (`CHARACTER-CREATION-DESIGN.md`).
- **Kein manueller Playtest auf echtem Gerät** in dieser Umgebung; nur Emulator-Screenshots und automatisierte Tests. Tap-to-Move wurde logisch (Wegfindung) getestet, die Gestenbedienung nicht automatisiert.
- Nur die Taverne ist begehbar; Markt/Kaserne/Burghof fehlen.
- Personen sitzen nicht (stehen an Sitzplätzen).
- Persönliche Schlachtteilnahme/Verwundung/Gefangenschaft noch nicht an Combat Engine 3.0 gebunden.
- Der Weltpuls für landlose Spiele ist leichtgewichtig; die vollen KI-Engines laufen erst nach dem Lehnseid.
- Der CI-Push auf `claude/**` aktualisiert die öffentliche Testversion `dev-latest` und den Screenshot-Branch – so ist der Workflow eingerichtet.
