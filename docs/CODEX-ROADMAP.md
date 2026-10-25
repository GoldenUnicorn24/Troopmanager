# Roadmap – Realm of the Last Wall

Stand: 25.10.2026. Basis 2.1.0 / 51. Aufwand ist eine grobe Schätzung in
Entwicklungstagen einschließlich gezielter Tests, kein Liefertermin.
Status: **in Arbeit**, **offen**, **abgeschlossen** nur mit Prüfverweis.
P1 = Stabilität/korrekte Regeln; P2 = wesentliche Spielqualität; P3 = Ausbau.

| ID | Phase / Priorität | Ergebnis und Abnahmekriterium | Aufwand | Abhängigkeiten | Status |
| --- | --- | --- | --- | --- | --- |
| A01 | A / P1 | Repository, Versionen, Architektur und alle angefragten Systeme inventarisieren; Befunde/Risiken trennen | 1–2 | – | Abgeschlossen: `CODEX-FULL-AUDIT.md` (Quelltextaudit; Laufzeitgrenzen angegeben) |
| B01 | B / P1 | Große-Mauer-Plan bleibt im erlaubten Deckungsbereich; Save/Load und identische Fortsetzung | 1 | A01 | Abgeschlossen: Regression, Save/Load und CI-Lauf 40216568844 |
| B02 | B / P1 | Alle acht Pläne sichern den echten Replay-Startzustand; erster Austausch reproduzierbar | 1 | B01 | Abgeschlossen: alle acht Pläne in CI; Gesamtreplay bleibt B09 |
| B03 | B / P1 | Planwahl nur vor erstem Austausch und bei aktiver Schlacht; kein Nachbuff/Ändern fertiger Berichte | <1 | B02 | Abgeschlossen: einschließlich sicherer Detail-Fortsetzung Feld/Mauer, acht Regressionstests insgesamt |
| B04 | B / P1 | Hafenbonus tatsächlich buchen; Anzeige = Goldänderung einschließlich Rundung/Kapazität | 1 | A01 | Abgeschlossen für Exportgold: CI 40218952639; Kaufroute bleibt Warenbewertung |
| B05 | B / P1 | Söldnerverluste pro Vertrag und Einheitentyp erhalten; identische Typen kumulieren | 1 | A01 | Abgeschlossen: gleiche/ungleiche Verträge und mehrere unbezahlte Tage in CI geprüft |
| B06 | B / P1 | Alle garnisons-/armeeverändernden Aktionen gegen aktive Schlachtsnapshots sichern | 2–3 | B03 | Teilweise: Stationieren/Rückholen/Anwerben/Entlassen gesperrt und getestet; übrige Mutationspfade weiter prüfen |
| B07 | B / P2 | Ressourcenarithmetik überlaufsicher; Produktion/Lagergrenzen bei großen Vorräten | 1–2 | B04, B05 | Teilweise: Holzüberlauf korrigiert; Sonderproduktion/Kapazitäten bleiben offen |
| B08 | B / P1 | Veröffentlichungen nur aus freigegebenem Hauptstand; PRs bauen APK und sammeln Diagnose | 1 | A01 | Abgeschlossen: Veröffentlichung nur von main; PR-Artefakte und Workflow in CI erfolgreich |
| B09 | B / P1 | Vollständiger taktischer Ablauf im Replay: Befehlspunkt-Auffüllungen/Schlüsselentscheidungen erfassen, alte Inputs laden | 2–3 | B02 | Abgeschlossen für Kampfzustände/CP aller acht Pläne und Alt-JSON: CI 40218952639; historische fehlende Daten/UI-Logs nicht rekonstruiert |
| U01 | B/E / P1 | Kampfschritte/Schnellauflösung auf Hintergrunddispatcher, stale Aktionen verwerfen, Bedienung bleibt reaktionsfähig | 2–3 | B03, B06 | Teilweise: automatische Schritte/Schnellauflösung ausgelagert und gegen veraltete Ergebnisse geschützt; weitere Pfade/Profiling offen |
| U02 | B/E / P2 | Haptik-/Animationsschalter auf allen erreichbaren Screens respektieren | 1–2 | A01 | Teilweise: Haptik und Karten-/Taktikanimationen korrigiert; übrige Animationen weiter prüfen |
| U03 | B/E / P2 | Provinzen mit TalkBack auswählbar; 48-dp-Ziele, große Schrift und lesbare Folgendetails | 2–4 | U02 | Teilweise: zugängliche Provinzaktionen und 48-dp-Unterreiter; reale TalkBack-Prüfung und weitere Text-/Gerätefälle offen |
| U04 | E / P2 | Produktive Querformatrotation mit stabiler Navigation/Save; kleine und große Geräteprofile einschließlich Ultra-Klasse | 3–5 | U01, U03 | Offen; physisches S26 Ultra separat testen |
| C01 | C / P1 | Crown-/World-Zuordnung für Besitz, Fraktionen, Diplomatie, Versorgung definieren; eine vertikale Provinzverbindung | 4–8 | B04–B07 | Offen; keine dritte Weltsimulation |
| C02 | C / P2 | Schildwall/Speerwall/Keil/lockere Ordnung mit Kontaktbreite, Übergangszeit und echten Zuständen | 4–6 | B09 | Offen; Schildwall/offene Ordnung existieren, nur erweitern |
| C03 | C / P2 | Flanke, Umfassung, geordneter/Scheinrückzug, Reserven und Verstärkung räumlich wirksam und seedstabil | 6–10 | C02 | Offen; Reserve/Rückzug wiederverwenden |
| C04 | C / P2 | Sichtlinie, Gelände/Höhe/Wetter/Nacht zusammenführen, keine Angriffe durch Hindernisse | 4–7 | C02, C03 | Offen; skalare Sicht/Reichweite erhalten |
| C05 | C / P1 | Balanceszenarien: 100–1.000 Angreifer gegen starke Festung, Fern-/Nahkampf, Veteranen/Rekruten, Formationen | 2–4 | B01, C02 | Offen; vorhandene Kampfbreiten-/Deckungstests erweitern |
| C06 | C / P2 | Gegnerdoktrinen, Zielprioritäten, Reserve, Ausweichen und Rückzug; Schwierigkeit über transparente Entscheidungen | 4–6 | C03–C05 | Offen |
| C07 | C / P2 | Regimentsnamen/History/Veteranen/Ausrüstung/Kommandantenlaufbahn, Armeevorlagen, Garnison und Autoverwaltung verbinden | 4–7 | B06, C01 | Offen; bestehende Pools/Designs erhalten |
| C08 | C/E / P2 | Karte mit tatsächlichen Armee-/Karawanenrouten, Straßen/Flüssen/Pässen, Grenzen, Belagerung/Schäden und Jahreszeit | 6–10 | C01 | Offen; Touchkarte besteht |
| C09 | C/E / P2 | Kartenfilter Politik/Militär/Wirtschaft/Diplomatie/Bevölkerung/Versorgung über bestehende Daten | 2–4 | C08, U03 | Offen |
| D01 | D / P1 | Einheitliche Kette Mine → Verarbeitung → Arsenal → Armee; alle Sonderproduktionen kosten-/lagergebunden | 4–7 | B04–B07, C01 | Offen |
| D02 | D / P2 | Angebot/Nachfrage, regionale Spezialisierung, Steuern, Bevölkerung, Reserven und Kriegsfolgen sichtbar; sinnvolle Automatik | 5–8 | D01 | Offen |
| D03 | D / P2 | Physischer Nachschub auch für Kronlandfeldzüge/Besatzungen, verständliche Kosten und Unterbrechungen | 4–7 | C08, D01 | Offen |
| D04 | D / P2 | Mehrphasige Belagerung mit Hunger, Gegenmaschinen, Mauerabschnitten, Reparatur, Ausfall, Entsatz und Kapitulation | 5–8 | C03, D03 | Offen; lokale SiegeEngine erweitern |
| D05 | D / P2 | Bestehende Bündnisse/Pakte/Handel/Tribut/Vasall/Frieden/Forderungen auf Rivalenkönige übertragen | 5–8 | C01 | Offen |
| D06 | D / P2 | Intrigen/Spionage/Sabotage, Persönlichkeiten, Bedrohung und Gegenkoalitionen, effiziente zustandsbasierte KI | 4–7 | D05 | Offen |
| D07 | D / P2 | Partnerziele/Dialoge/Erinnerungen/Meinungsverschiedenheiten/gemeinsame Regierung/Initiativen mit realen Folgen | 4–7 | B09, D05 | Offen; Beziehung/Saga/CoRuler integrieren |
| D08 | D / P2 | Nachfolger entwickeln, Nachfolge/Regentschaft/Dynastieverträge und Familiengeschichten über Generationen testen | 4–6 | D07, F02 | Offen |
| D09 | D/E / P2 | Sichtbare Gebäudestufen/Baufortschritte/Schäden, Bevölkerung/Sicherheit/Wohlstand; Kosten und Zeiten transparent | 3–5 | D01 | Offen; freie Platzierung erst nach UX-Entscheid |
| D10 | D / P2 | Kulturmilitär, besondere Gebäude/Ereignisse, Loyalität/Integration, Stärken/Schwächen und Diplomatie ausbalancieren | 4–7 | C01, D05 | Offen; vorhandene Völker/Einheiten erhalten |
| D11 | D / P2 | Verknüpfte regionale/charakterbezogene militärische und diplomatische Ereignisse mit unterschiedlichen Langzeitfolgen | 4–7 | D05, D07 | Offen; StoryDirector/QuestJournal/Chronik erweitern |
| D12 | D / P2 | Endgame mit Reichsrivalität/Krisen/Koalitionen und langfristigen Entscheidungen statt bloßem Wachstum | 5–10 | D03, D06, D08, D11 | Offen |
| E01 | E / P2 | Schlachtbilder, Geschosse, Kavallerie, Befestigung/Geräte ausschließlich aus Simulationszustand projizieren | 3–5 | C03–C06 | Offen; Canvas/Projektion wiederverwenden |
| E02 | E / P3 | Einheitlicher Fantasy-Gemäldestil für Porträts/Gebäude/Karte/Banner/Icons; vorhandene Lizenzen und Audio pflegen | 3–6 | U03, D09 | Offen; keine ungeklärten Assets |
| E03 | E/F / P2 | Recomposition, Kartenrendering, Bitmap-/Audio-Speicher, Lade-/Savezeit und Akku messen und gezielt verbessern | 3–6 | U01, C08 | Offen; keine pauschale Neuschreibung |
| F01 | F / P1 | Unit-/Integrations-/Migrationsregression, Lint, APK und Emulatornachweise je größerer Änderung | laufend | jeweilige Phase | Abgeschlossen für beide Stabilisierungen: zuletzt 506 JVM- und 15 Android-Tests, Lint, signierte APK; Nachweise im Bericht |
| F02 | F / P1 | Feste Seeds: 200/1.000/10.000 Tage, Wirtschaft/KI-Expansion/Diplomatie/Hunger/Nachfolge und Erhaltungsinvarianten | 3–5 | B04–B07 | Offen; historischer Ein-Tages-Test genügt nicht |
| F03 | F / P2 | Kleine Smartphones, große Schrift, Querformat und großes Display; APK installieren, Update/Savefortsetzung und Offlinebetrieb | 2–4 | U04, F01 | Offen; erfolgreicher Build ist kein Gerätetest |

## Grenzen der ersten Lieferung

Diese Phase stabilisiert **B01–B03** und erstellt die Bestandsaufnahme. Sie erweitert
keine Wirtschaft, KI, Weltdaten oder Assets und ändert weder Speicherformat noch
Kampf-/Balanceformeln. Die bestehende Android-Pipeline liefert Prüfungen und eine
Test-APK, sofern die tatsächlichen Läufe erfolgreich sind. Ein offener Eintrag ist
keine implementierte Funktion. Prüfergebnisse und PR-Status stehen im
[Implementierungsbericht](CODEX-IMPLEMENTATION-REPORT.md).

Die zweite Stabilisierung erweitert B04–B09 und U01–U03 im oben ausdrücklich
angegebenen Umfang. Offene Teilbereiche und langfristige Gameplay-Erweiterungen
werden dadurch nicht als erledigt markiert.
