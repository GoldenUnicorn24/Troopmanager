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
- Begehbar: Taverne, Markt (M2), Kaserne (M3), Burghof (M4).
- Sitz-Pose seit M4 (einfache Frontalansicht).
- Große Schlachten: Ausrüstung und Wunden wirken als Faktor (M4); der Spieler ist dort keine einzeln gesteuerte Figur.
- Der Weltpuls für landlose Spiele ist leichtgewichtig; die vollen KI-Engines laufen erst nach dem Lehnseid.
- Der CI-Push auf `claude/**` aktualisiert die öffentliche Testversion `dev-latest` und den Screenshot-Branch – so ist der Workflow eingerichtet.

## 5. Meilenstein 2 (Gefolge, Markt, Kämpfe)

| Prüfung | Ergebnis |
|---|---|
| CI Unit-Tests, Lint, APK (Push- und PR-Lauf) | ✅ |
| Emulator-Tour inkl. neuer Seiten `e-market`, `e-skirmish`, `e-party`, `e-captive` | ✅ |
| Lokale JVM-Tests | ✅ 489 grün |

Neue Tests in `RebirthRoadTest` (11): Freund lehnt ab bzw. schließt sich an und folgt auf Reisen · Söldner kosten Handgeld und Sold und ziehen unbezahlt ab · gekaufte Rüstung ändert Aussehen und Porträt, Waffe die Kampfkraft, Heilerin senkt Wunden · Geschenke nach Geschmack wirken stärker, eins pro Tag · Wolfsjagd wird persönlich ausgefochten, deterministisch, mit Lohn/Scheitern · Gefährten kämpfen mit und erinnern sich · Niederlage verwundet, Gefangenschaft blockiert Reisen und endet nach Ablauf · Lösegeld befreit sofort · Duell ist nicht tödlich und wird erinnert · Hinterhalte auf gefährlichen Straßen, Reise geht nach dem Kampf weiter · Lehnseid übergibt Gefährtin (Kommandantin) und Söldner (Hauptleute).

Kampfbalance per Simulation (40 Läufe je Zeile, 4 Wegelagerer): Schwert 60 allein gewinnt gegen Stärke 40–55 fast immer und verliert gegen 70; mit zwei Gefährten werden auch Stärke-70-Gegner besiegbar. Das Gefolge ist bewusst spielentscheidend, kostet aber Sold.

## 6. Meilenstein 3 (Menschen untereinander, Kaserne)

Neue Tests in `RebirthPeopleTest` (9): Beziehungsnetz pro Stadt (Paare nur bei gegenseitiger Offenheit, deterministisch, gespeichert) · Weitergabe von Sympathie an Freunde und umgekehrt an Rivalen · Eifersucht und Bindungsabzug beim Flirt mit Vergebenen · Reisende ziehen um, Klatsch entsteht · nie besuchte Gefährtin zieht weiter und wird erwähnt · befreundete Gefährtin sucht den Spieler auf · Training mit abnehmendem Ertrag und Tageslimit · Stadtwache mit Sold, Ruf und Beförderung · Rollen-Themen lehren und erzählen ohne offene Platzhalter.

Lokal: **498 Tests grün**. CI- und Emulator-Ergebnis siehe PR #14.

## 7. Meilenstein 4 (Burghof, Ritterschaft, Turniere)

Neue Tests in `RebirthCastleTest` (10): jede Stadt hat einen erwachsenen, nicht romantisch verfügbaren Burgvogt, der Burghof ist von der Pforte aus vollständig begehbar und nachts geschlossen · Audienz nur nüchtern, tagsüber und mit Ruf · Lehnsauftrag zielt auf eine Räuberprovinz; Niederlage lässt die Karte unverändert, Sieg setzt die Provinz auf der gemeinsamen Kronkarte frei (Nachricht, Chronik, Erinnerung und Vertrauen beim Burgvogt), speichersicher · der Sturm wird persönlich und deterministisch ausgefochten · Ritterschlag nur nach drei Aufträgen und Ruf 120, senkt den Goldbedarf der Grenzfeste auf 400 · Turnier nur an Turniertagen, Startgeld einmal, drei stärker werdende Gegner, Sieg wird gezählt und chronikiert · verlorene Runde beendet das Turnier für den Tag · persönlicher Schlachtfaktor exakt 1,10/1,22 ohne Ausrüstung/Wunden, höher mit Ausrüstung, niedriger mit Wunden · Paare sitzen in der Taverne nebeneinander, Rivalen erzeugen „Alte Feindschaft“ mit Folgen · ungültige Turnier-/Auftragsdaten werden abgelehnt.

Alle bisherigen Kampf- und Kampagnentests (Combat Engine 3.0, V06/V10-Abnahme) bleiben unverändert grün – die Kopplung ändert nichts, solange keine Ausrüstung gekauft wurde.

Lokal: **508 Tests grün**. CI- und Emulator-Ergebnis (neue Seiten `g-castle`, `g-tavern` mit Sitz-Pose) siehe PR #14.

## 8. Meilenstein 5 (Herrscher bleibt Person, Ruf, Hoftag)

Neue Tests in `RebirthRulerTest` (9): Gefährtin und angeworbene Söldner ziehen an den Hof und erscheinen an 20 Abenden in keiner Taverne mehr, Nachricht für alle Städte · ein Ritter wird als Ritter chronikiert · Herrscher zahlen am Markt aus der Schatzkammer, Ausrüstung erhöht den Schlachtfaktor, Kaserne und Burghof sind zugänglich · Hoftag: nur für Herrscher, genau die angekündigten Freiwilligen, 7 Tage Pause, mehr für Ritter/Lehnsaufträge · Leute bemerken jede Tat genau einmal, leere „gehört“-Notizen erscheinen nie als Erinnerung · Herrscher werden mit „Herr“ begrüßt, Bittgesuch kostet Gold, wirkt und ist einmalig, für Landlose nicht verfügbar · Prahlen nur mit Taten, ohne offene Platzhalter · Turniertermin stimmt mit der Engine überein · in 90 Tavernenszenen steht niemand direkt vor jemand anderem.

Lokal: **517 Tests grün**. CI- und Emulator-Ergebnis (neue Seite `h-ruler-castle`) siehe PR #14.

## 9. Meilenstein 6 (Fertigstellung: Wetter, Preise, Aussichten, Balancing)

Neue Tests in `RebirthWorldTest` (7): Wetter deterministisch, zweitägig, jahreszeitlich (kein Schnee im Sommer, Schnee im Winter, an der Küste mehr Stürme) · bei Regen ist nachmittags niemand der Stammgäste/Reisenden auf Straße oder Markt · Sturm schließt den Markt und verhindert den Aufbruch, Schnee verteuert, Nebel gefährdet die Reise · Preise je Stadt, Kauf kostet genau den angezeigten Preis, Handelstipp nennt eine echte Stadt · Aussichten sind deterministisch, steigen mit Stärke und verändern den Spielstand nicht · Wetter als Gesprächsthema · Lehnsaufträge mit Pause, der nächste zielt auf eine andere Provinz.

**Langzeit-Spieltest** `CareerPlaythroughTest` (2): Ein Bot spielt ausschließlich über die Aktionen der Oberfläche (Aufträge, Kämpfe, Heilerin, Ausrüstung, Burgvogt, Turniere, Ritterschlag, Lehnseid) und regiert danach mit den empfohlenen Schlachtplänen weiter. Alle 25 Tage werden der Spielstand geprüft und Speichern/Laden verglichen.

| Start | Ritterschlag | Grenzfeste | Gefechte (Siege) | Gefangenschaft | Ende |
|---|---|---|---|---|---|
| Söldnerin, Schwert 60 | Tag 15 | Tag 33 | 17 (17) | 0 Tage | Tag 261, Reich stabil |
| Dorfbewohnerin, Schwert 45 | Tag 28 | Tag 32 | 16 (16) | 0 Tage | Tag 321, Reich stabil |

Vor dem Balancing erreichte derselbe Bot die Grenzfeste **nie**: Er kam mit 136 Gefechten nur auf 56 Siege, war oft in Gefangenschaft, und das an den Ruf gekoppelte Lösegeld (bis über 100 Gold) fraß jedes Ersparte. Die Änderungen stehen in der Roadmap (Meilenstein 6). Der Bot nutzt die neue Anzeige „Aussichten“ und reitet nur bei mindestens 60 %. Genau diese Information bekommt jetzt auch der Mensch.

Lokal: **526 Tests grün**.
