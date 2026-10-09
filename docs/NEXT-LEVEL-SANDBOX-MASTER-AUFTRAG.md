# TROOPMANAGER — MASTER-AUFTRAG FÜR CODEX + CLAUDE
## Realm of the Last Wall · Next-Level Sandbox & Visual Overhaul

**Auftraggeber-Ziel:** Eine deutlich hochwertigere, spaßigere, vollständig offline nutzbare Android-Fantasy-Strategie/RPG-Sandbox mit offenen Entscheidungen, interaktiver Welt, lebendiger Stadt, nachvollziehbaren Kriegen und außergewöhnlich viel besseren Schlachten und Grafiken. **Keine lineare Kampagne. Keine reine Text-/Menü-Simulation. Keine wertlosen Feature-Listen anstelle von Code.**

**Produkt-/Entwicklungsbasis:**
- Repository: https://github.com/GoldenUnicorn24/Troopmanager
- Verbindliche Basis: `codex/v1.0-realm-war-overhaul` (v1.0.0 / versionCode 46 / Save-Schema 5 / Combat Engine 3.0, Sprint A).
- Bereits separat erarbeiteter UX-Zweig: `feature/next-level-gameplay-ux`, PR #10. Diesen nach CI-Prüfung berücksichtigen; keine identischen Features parallel neu erfinden.
- Veraltetes `main` NICHT als Spielbasis nehmen.
- Implementierung: Android, Kotlin, Jetpack Compose, JDK 17, minSdk 26, compile/target SDK 35; bestehende lokale Saves, Assets und Engines weiterverwenden.
- Zuerst lesen: `README.md`, `CODEX.md`, `CLAUDE.md` (falls vorhanden), `docs/V1.0-REALM-WAR-OVERHAUL.md`, `docs/V1.0-SPRINT-A.md` sowie die betroffenen Engines, Models, Tests und UI-Screens.
- Frühere Planungen sind technische Dokumentation, **keine Pflicht zur linearen Story**. Die nachfolgende Sandbox-Definition hat für neue Spielgestaltung Vorrang.

# 0. ROLLE UND ARBEITSMODUS

Du bist nicht nur Berater, sondern verantwortlicher Senior-Game-Director, Android-Lead, Gameplay-/Combat-Engineer, Technical Artist und Quality Owner. Prüfe den echten Repositoryzustand, treffe begründete technische Entscheidungen, implementiere sie und liefere lauffähige, getestete Änderungen als kleine überprüfbare Pull Requests. Kein kompletter Rewrite ohne Nachweis, dass die Altarchitektur unrettbar ist.

**Arbeite in Lieferabschnitten.** Jeder Abschnitt endet mit:
1. konkret implementierten Dateien und sichtbaren Verhaltensänderungen,
2. deterministischen Engine-Tests, Lint und Android-Debug-Build,
3. Prüfung des lokalen Speicherstands und bei UI-Änderungen geeigneten Screenshots,
4. präziser Beschreibung, was wirklich fertig ist, was offen ist und welche Risiken verbleiben,
5. Pull Request auf den aktuellen v1.0-Basiszweig, **nicht auf `main`**.

Falls ein Arbeitspaket zu groß ist: zuerst den kleinsten vollständigen vertikalen Schnitt bauen (Simulation → ViewModel/State → Darstellung → Bedienung → Test), statt zehn halbfertiger Systemansätze. Keine Fortschrittsbehauptungen ohne Prüfbelege. Keine APK als erfolgreich bezeichnen, solange sie nicht gebaut und signaturgeprüft ist.

# 1. NICHT VERHANDELBARE SPIELVISION

## 1.1 Echte Sandbox, niemals erzwungene Kampagne
- Es gibt **keine geskriptete Pflicht-Hauptquest, keine starren Kapitel, keinen gewaltsam gesteuerten Siegpfad, keine unsichtbaren Ereignisse nach X Tagen, die Spielerfreiheit vortäuschen**.
- Die Spielwelt simuliert Interessen, Macht, Versorgung, Reisezeiten, Kultur, Bevölkerung, Diplomatie, Herrschaft, Gefährtin, Außenposten und Feinde. Die Welt **antwortet** auf Entscheidungen; sie befiehlt nicht die nächste Mission.
- Der Spieler darf Diplomatie, Krieg, Handel, Isolationismus, Herrschaft, Aufbau oder persönliche Entwicklung priorisieren und dabei lange Phasen in Ruhe bleiben.
- Entscheidungen sind nicht bloß „A/B/C“-Textkarten: Wo möglich lösen Spielerhandlungen Zustandsänderungen in existierenden Engines aus. Eine Krise darf mehrere gültige Lösungswege haben, einschließlich Ignorieren mit logischen Folgen.
- Das Reich lebt auch ohne ständige Spieleraktionen weiter. Keine künstlich übertriebene Ereignisfrequenz, kein bestrafendes Spam-Benachrichtigungssystem.
- Optional: frei gewählte Spielziele (Handelsmacht, Grenzverteidigung, Elbenreich, militärische Hegemonie, Familien-/Hofleben, Hochkönigtum) sind **Ziele, keine Questketten**.
- Nach einem großen Sieg, Frieden oder Hochkönigtum bleibt die offene Welt spielbar.

## 1.2 Was „mehr Spaß“ messbar bedeutet
- Der Spieler versteht den Zustand und die Auswirkung einer Aktion in maximal wenigen Sekunden.
- Jeder relevante Knopfdruck hat eine sichtbare Reaktion oder ein erklärtes zukünftiges Ergebnis.
- Eine Schlacht fühlt sich wie ein Gefecht an, nicht wie eine animierte Zahlentabelle.
- Eine Stadt spiegelt baulichen Fortschritt, Bedrohung, Wohlstand, Kultur und Schäden sichtbar wider.
- Gegner wirken wie strategische Fraktionen: sie lernen, marschieren, handeln, ziehen sich zurück und reagieren auf Verluste.
- Es gibt spielerische Entscheidungen mit Tradeoffs und Überraschungen, **ohne** künstliches Ressourcen-Grinding.
- Casual-Spieler erhalten verständliche Standardvorschläge, Experten haben tiefere Kontrollen.
- Angriffe kleiner, schlecht gerüsteter Orkhorden auf eine überlegene intakte Garnison führen zu plausibler Abwehr, nicht automatisch zu absurden Verteidigerverlusten.

# 2. SPIELERWERTE UND BESTEHENDE SYSTEME SCHÜTZEN

Unbedingt erhalten: **100 frei verteilbare Start-Attributpunkte**, **30 Start-Fertigkeitspunkte**, **3 parallele Bauplätze**, **1–3 Kommandanten/Begleiter pro Mission**, temporäre Missionszuteilung mit sauberer Rückgabe, dauerhaft zugeteilte Truppen ausschließlich in der Armeeübersicht, Kultur- und Starttruppenwahl, Menschen/Waldelben/Goldelben/Mauerlegion als unterscheidbare Kulturen; Verwundete vs. Gefallene; Lazarett; Diplomatie, Forschung, Wirtschaft, Familien-/Dynastieoption, Gefährtin/Mitregentin und ihre bestehende Zustimmung-/Romantiklogik, Freundschaft als gleichwertiger Weg; manuelle Avatar-Imports; vollständig offline, keine Accounts, In-App-Käufe, Abos oder Pay-to-Win.

Save-Schema 5 ist aktueller Zielstand. Schema-4-Migration und geschützte Alt-Backups nicht verändern oder zerstören. Keine bestehenden Schlachtberichte überschreiben; Replays wiederholbar halten. **Nicht still vorhandene Truppen, Pfeile, Gold oder Charaktere vervielfachen oder löschen.** Beim Übergang neue Save-Modelle nur additiv mit Defaultwerten; Migrationen und Backups explizit prüfen.

Bestehende Engines vor Modifikation verstehen: `BattleEngine`, `BattleResolutionEngine`, `BattleStateEngine`, `SiegeEngine`, `BattleAiEngine`, `BattleReportEngine`, `WorldEngine`, `WarEngine`, `FrontierEngine`, `CityEngine`, `EconomyEngine`, `ArmyEngine`, `GameEngine`, `DiplomacyEngine`, `RelationshipEngine`, `CoRulerEngine`, `DynastyEngine`, `SaveCodec`, `SaveRepository`, `CampaignInsightsEngine`. Keine zweite „God Engine“ einführen.

# 3. VISUELLER QUALITÄTSSPRUNG — HÖCHSTE PRIORITÄT

## 3.1 Ein gemeinsames, professionelles Art-Bible
- **Dark-Fantasy-Realismus:** glaubwürdige Stein- und Holzarchitektur, Metall, Leder, Stoff, stimmige Landschaften, gedämpfte Farbpalette mit Goldakzenten, atmosphärisches Wetter und Tageszeit.
- Keine heterogenen Stockbilder, Pixelmischungen oder falsche Perspektiven, keine 2D-Illustration hinter einer fremdartigen, draufgeklebten Stadt.
- Fraktionsidentität: Menschen = robuste Grenzfestung und Stahl/Leder; Waldelben = organisch/grün, leichte Silhouetten; Goldelben = elegante Gold-/Elfenarchitektur; Mauerlegion = geometrische Steinfortifikation, Blau/Stahl, spezialisierte Korps. Gegner visuell eigenständig.
- Einheitlicher Qualitätsmaßstab für Menü, Weltkarte, Stadt, Schlacht, Porträts, Einheiten und Events. Kein großer Art-Sprung von Screen zu Screen.
- Designsystem für Typografie, UI-Karten, Status, Icon-Stil, Buttons, Sheet-/Dialog-Flächen, Kontrast und Safe Areas; **keine winzige Schrift**. Informationen niemals nur aus ästhetischen Gründen verstecken.
- Originale oder eindeutig lizenzierte Assets. Keine fremden Film-/Serien-/Spielebilder, Marken oder ikonischen Designs kopieren.
- Runtime-Bilder (WebP/optimierte Android-Ressourcen) klar trennen von Konzeptbildern. Jede Asset-Änderung mit Quellenangabe/Lizenz, Zielauflösung, Größe, Renderkontext, Alpha, Fallback, Speicher- und Performancebudget dokumentieren.
- Assets, die hier nicht generiert werden können, als präzisen Produktionsauftrag mit Zielpfad und Größen-/Perspektivvorgaben dokumentieren; **nicht so tun, als seien sie bereits erstellt**.
- Qualitätskontrolle für menschliche Anatomie, korrekte Waffenhaltung, glaubwürdige Rüstung, unverzerrte Hände und gesichts-/kulturkonsistente Porträts.

## 3.2 Stadt: wirkliche interaktive Spielwelt
Bestehende `CityScene.kt` / `CityScreen.kt` weiterentwickeln, nicht auf ein statisches Stadtbild zurückgehen.
- Einheitliche 2.5D-/isometrische Szene mit gemeinsamem Koordinatensystem für Boden, Gebäude, Straße, Mauern, Tor, Markt, Residenz, Fabrikation, Felder, Bevölkerung.
- Jedes tatsächlich gebaute/verbesserte Gebäude sichtbar. Ausbau verändert Stufe, Silhouette, Baustellenzustand und Umgebung; zerstörte Mauern sind **erkennbar** beschädigt.
- Tag/Nacht, Jahreszeiten, Versorgungsknappheit, Wohlstand, Feuer, Rauch, zivile Bewegung, Marktbelebung, Wachposten; nur Effekte, die aus vorhandenem/ergänztem State herleitbar sind.
- Palast, Kaserne, Arsenal, Lazarett, Markt, Schmiede, Forschungsakademie, Residenz und Mauern zuverlässig anwählbar; antippbare Hotspots mit sinnvollen, direkt ausführbaren Aktionen.
- Zoom/Pan stabil mit denselben Transformationsdaten für Hit-Testing und Darstellung; kleine Displays, 160 % Textskalierung und große Touch-Ziele berücksichtigen.
- Performance: Layering, Bitmap-Caching, Keine Decode-Arbeit in jedem Compose-Recompose/Canvas-Frame, klare Animationsbudgets und Qualitätseinstellungen.

## 3.3 Weltkarte: lebendige statt dekorative Karte
Bestehende `CampaignMapScreen.kt`, `WorldEngine.kt` und Raum-/Reise-Geometrie **in einem Koordinatensystem** halten.
- Gelände, Regionengrenzen, begehbare Wege, Armeen, Handelszüge, Wetter, Fronten, Sicht/Nebel und Eigentum konsistent.
- Armeen zeigen tatsächliche Position, Marschrichtung, Ankunftsprognose, bekannte Stärke und Versorgungsstatus; Unbekanntes wird nur bei ausreichender Aufklärung sichtbar.
- Eigene Taktikentscheidungen (patrouillieren, abfangen, belagern, sich zurückziehen, schützen, eskortieren, umgruppieren) als intuitiv auswählbare Aktionen.
- Diplomatieauswirkungen auf Farbe/Kontrolle/Grenzen. Regionshistorie statt „Orkhorde aus dem Nichts“.
- Lesbarkeit auch bei starken visuellen Effekten; Karteninfos bei Bedarf in einklappbaren Panels.

## 3.4 Figuren und Fraktionsporträts
- Einheitlicher Porträtstil für Spieler, Begleiterin, Kommandanten, Menschen/Elben/Wachen/Feinde.
- Individuelle Kultur- und Rollensilhouetten, erkennbare Rangunterschiede und Ausrüstungsänderungen.
- Für wichtige NPCs wiedererkennbare, konsistente Gesichter; Spielerimport erhalten.
- Keine generische Wiederverwendung desselben Porträts für alle Einheiten.

# 4. SCHLACHTEN 3.0 → SPIELERISCHES HIGHLIGHT — HÖCHSTE PRIORITÄT

## 4.1 Visualisierung echter Kampfsituationen
Nutze bestehende `LiveBattleScreen.kt`, `TacticalBattleField.kt`, `BattlePlanPanel.kt` und Combat Engine 3.0. Erstelle **keine dekorative Ersatzsimulation**.
- Klar unterscheidbare Bataillone und Kontingente mit Formationen; Einheitenpositionen und Schusslinien an echte Front-Distanz/Formation koppeln.
- Bogenschützen schießen sichtbare Salven; Pfeilflug, Ankunft und Trefferreaktion müssen aus dem echten Kampftakt/Schaden folgen.
- Nahkampf beginnt **erst bei physischem Kontakt**. Schildwälle, Speerkampf, Ansturm, Gegenstoß und Rückzug sind durch Animation/Silhouetten klar unterscheidbar.
- Belagerung ist mehrphasig: Annäherung, Beschuss, Leitern/Geräte, Tor-/Mauerkontakt, Bresche, Hofkampf, Rückzug.
- Mauersegmente, Schaden, Breschen, Tore, Leitern, Katapulte, Mauerwaffen, Kavallerieausfälle und Reserven sind eindeutig erkennbar.
- Animierte Pfeile, Staub, Funken, Rauch, Fahnen, Wetter und Licht, aber nicht in einer Menge, die Android-Framerates zerstört.
- Unterschiedliche Maßstäbe: strategische Übersicht für Tausende Soldaten (Kohorten/Aggregation) und taktische Detaildarstellung ohne Tausende einzelne Android-Objekte.
- Visuelles Feedback für Moralbruch, Flucht, Stopp des Beschusses, Munitionsmangel, überrannte Flanke, frische Reserven und verletzte Kommandanten.
- Hinterher Schlachtbericht mit klaren, nachvollziehbaren Ursachenzahlen und spielerisch sinnvollen Folgehandlungen.

## 4.2 Entscheidungen statt passiver Animation
Vor der Schlacht: Formation je Front, Prioritätsziele, Pfeilfreigabe-Distanz, Reserven, Risiko, Mauerschützen, Katapulte, Torpolitik und Rückzug festlegen. Bestehende sechs Profile plus freie Planung erhalten.
Währenddessen: Befehle mit realer Reaktions-/Laufzeit, begrenzten Kommandopunkten und sichtbarer Befehlsbestätigung. Schnelligkeit 0.5×–3× gemäß Einstellungen; Pause/Lesbarkeit sinnvoll behandeln.
Nachher: direkte Reparatur-, Behandlung-, Rekrutierungs-, Verfolgungs- oder Friedensoptionen je tatsächlichem Zustand.

## 4.3 Glaubwürdiges Balancing
Pflichttests und dokumentierte Szenarien:
1. **200 Orks gegen 1.000 Garnisonssoldaten, davon 600 Bogenschützen, intakte starke Mauer, gute Moral, ausreichend Munition:** Angreifer können vor Kontakt gebrochen werden; minimale Verteidigerverluste vor Kontakt. Keine generierten Pflichtverluste.
2. **1.500 besser gerüstete Angreifer mit Geräten:** reale Chance auf Mauerkontakt/Bresche, nicht automatisch niedergemäht.
3. Kein Nahkampfschaden ohne Kontakt.
4. Kein Pfeilschaden ohne Munition/Schussfenster.
5. Nacht, schlechter Sichtwert, Deckung, Panzerung, Höhenunterschied wirken plausibel.
6. Reserven brauchen echte Marsch- und Reaktionszeit.
7. Breschen ändern Frontbreite und Verteidigungswert.
8. Zusammengebrochene Moral/Flucht verändert Kampfwirkung.
9. Verluste werden in tot, verwundet, gefangen, geflohen und nach Ursachen getrennt.
10. Wiederholung mit identischem Seed, Ausgangsstate und Befehlen liefert reproduzierbare Resultate.

Wichtig: Kleine Angriffe können auch **ohne** „epischen Showkampf“ logisch schnell scheitern. Inszenierung soll die Simulation unterstreichen, nicht jede Schlacht künstlich gleich hart machen.

# 5. INTELLIGENTE, OFFENE WELT UND ENTFALTETES SPIEL

## 5.1 Gegner und Diplomatie
- Fraktionen besitzen nachhaltige Interessen (Territorium, Nahrung, Handel, Ideologie, Bedrohungsgefühl, Rivalitäten), Erinnerung, Streitkräfte und Ressourcen. Aktionen richten sich nach Lage, nicht allein Spieltag.
- Feinde können angreifen, plündern, zurückweichen, Frieden ersuchen, versorgen, Verbündete rufen oder Rache vorbereiten; Kriegsziel und Risiko müssen nachvollziehbar sein.
- Beliebiges Töten der Gegner erhöht nicht mechanisch automatisch alle Gefahren; nachvollziehbare Reaktion abhängig von beobachteten Handlungen und Reputation.
- Verträge, Bündnisse und Handelsbeziehungen haben sichtbare reale Effekte; Verhandlungen sind handlungsfähig und keine toten Dialogbuttons.

## 5.2 Armee, Ausrüstung und Logistik
- Klar trennen: verfügbare Truppen, Garnison, Feldheere, Missionen, Verwundete, Rekruten, temporäre Missionskontingente und dauerhaft zugewiesene Einheiten.
- Schwerter, Bögen, Pfeile, Artilleriematerial, Medizin, Nahrung, Reparatur-/Belagerungsbedarf an Produktion und konkrete Einsätze koppeln.
- Versorgung erfolgt mit Konvois, Depots, Außenposten und Feldheeren über reale Strecken/Zeit.
- Niedrige Vorräte beeinflussen Marschtempo/Moral/Fernkampf; klare Warnungen ohne Zwang zu Mikromanagement.
- Standardprozesse automatisierbar, manuelle Priorität für Experten.

## 5.3 Siedlungs-/Reichsaufbau
- Spieler setzt Prioritäten frei: Landwirtschaft, Mauern, Offiziere, Bevölkerung, Handel, Forschung, Armee und Kulturintegration.
- Baumaßnahmen verändern **Optik**, Werte, Fähigkeiten und später mögliche Entscheidungen.
- Entscheidungsvorschau zeigt Kosten, Chancen, Dauer, Folgen, Ressourcen nach Durchführung.
- Forschung schafft spannende Spezialisierungen statt nur passive +2 %-Werte.
- Spieler kann eine friedliche Handelsmacht betreiben, defensive Festung bauen oder aktiv expandieren; alle Wege wirtschaftlich spielbar.

## 5.4 Reaktive Gefährtin, Hof und Dynastie
- Die Begleiterin/Mitregentin ist eigenständige erwachsene Figur mit Prinzipien, Kontext, Erinnerungen, Loyalität, Konflikten und gültiger räumlicher Präsenz.
- Entscheidungen über Krieg, Verletzte, Kultur, Handel, Milde/Härte und Delegation führen zu ihrer **situativen** Haltung und realen Verwaltungsfolgen.
- Gemeinsam regieren zeigt wer, wann und warum gehandelt hat.
- Freundschaft vollwertig; Romanze optional und nur mit gegenseitiger Zustimmung sowie bestehenden Sicherheits-/Altersregeln; Familie/Dynastie optional.
- Interaktionen mehrdimensional: nicht bloß Geschenke +X Zuneigung, sondern auch Meinungsverschiedenheiten, Anerkennung, Versöhnung, eigene Projekte und langfristige Beziehungen.

## 5.5 Ereignisse ohne Plot-Zwang
- Events entstehen aus aktuellen Bedingungen, nicht per unabhängigem Zufallstimer.
- Nachwirkungen werden gespeichert: gerettetes Dorf, gefallener Kommandant, Friedensvertrag, verarmte Familien, wiederaufgebaute Brücke, politische Versprechen.
- Bei ruhigem Reich weniger Ereignisse; wenn Krise droht gezielte relevante Entscheidungen. Spieler kann viele Ereignisse ablehnen oder ignorieren mit plausibler Weltfolge.

# 6. GAME FEEL: WARUM MAN WEITERSPIELT

Baue eine durchgängige Gameplay-Schleife:
**Lage erkennen → eigene Absicht wählen → Aktion ausführen → sichtbare unmittelbare Reaktion → Weltfolgen → neue freie Möglichkeit**.

Wichtig:
- Keine Aufforderung, immer „Nächste Mission“ anzuklicken; Empfehlungen sind **optional**.
- Gute Warnungen zeigen Ursache und Risiko. „Dein nächster Zug“ darf eine Empfehlung sein, niemals ein Pflichtziel.
- Kurze, gut animierte Erfolgs-/Misserfolgssignale; Frontbericht, Neubau, Beförderung, Geländegewinn, Bevölkerungsreaktion.
- Keine Füllmenüs. Bisher verteilte Aktionen in einen konsistenten Ort bringen.
- Unterschiede zwischen Kulturen müssen taktisch und wirtschaftlich interessant sein, ohne eine Kultur generell unbrauchbar zu machen.
- Wahrnehmbarer Fortschritt in kleinen (Ausbau/Beförderung), mittleren (Außenposten/Armee) und großen (neue Region/Krieg/Frieden) Zeitskalen.
- Echte Kontrolle über Tempo: Spieltag überspringen nur wenn gewünscht; anstehende Gefahren werden sichtbar.

# 7. MOBILE UI / UX

Technisches UI-Ziel: Jetpack Compose auf Android-Telefonen, insbesondere hohe DPI, kleine Viewports, Hoch-/Querformat wo unterstützt, Systemschrift 160 %.
- 5 sinnvolle Hauptbereiche (z. B. Kommando, Stadt, Welt, Armee, Hof) mit konsistenter Navigation; sekundäre Screens kontextuell.
- Ausgangspunkt zeigt **wichtige Lage**, **eine optionale nächste Handlung**, **Hauptwerte** und sichtbaren Zugang zu Welt und Stadt.
- Szene bleibt im Fokus: Stadt und Schlacht dürfen nicht von einem endlosen vertikalen Formular erdrückt werden.
- Geste/Zoom, Bottom Sheets, groß genug beschriftete Touch-Flächen, zurück-/abbrechen-safe.
- Statusfarben, Icons und visuelle Sprache konsistent; bedeutende Informationen auch textlich (nicht nur per Farbe).
- Keine Ellipsis für entscheidende Einheitenzahlen/Befehle auf wichtigen Kampfscreens.
- Screens für Soldatenzuteilung, Mission, Bau, Beziehung, Krieg und Diplomatie auf doppelte Aktionen, tote Wege und fehlerhafte Rücknavigation prüfen.
- Keine pauschalen „responsive“ Claims ohne Layout-Test.

# 8. PERFORMANCE, AUDIO, FEEDBACK

- Bilder effizient als WebP/Drawable; große Bilder nur passend skaliert laden, Images/Skia/Canvas-Meshes cachen.
- Keine schweren KI- oder Netzwerkaufrufe zur Laufzeit: Das komplette Spiel bleibt offline.
- Viele tausend Soldaten werden als Kohorten/Regimenter dargestellt. Budget für Partikel, Sprites, Recompose und aktives Audio setzen; Animationsreduzierung unterstützt.
- Wetter/Tag-Nacht, Pfeilsalven, Treffer, Baulärm, Musikwechsel erhalten ein **konsistentes** Audio-Feedback. Bereits vorhandene Audioressourcen zunächst prüfen, nicht unnötig ersetzen.
- Framerate und Speichern/Laden auf echten oder reproduzierbar simulierten Android-Konfigurationen evaluieren. Keine pauschale 60-FPS-Garantie erfinden.
- Battery-Mode/Animation-Optionen und verlässliches Pausieren/Resume.

# 9. KONKRETER LIEFERPLAN (PRs statt MONSTER-COMMIT)

## PR-A — Basisprüfung + Visual QA
- Branch-Baseline, aktuelle Tests, Screenshot-/Asset-Inventar; Risiken/Performance dokumentieren.
- Art-Bible + exakte UI-Komponentenregeln und Asset-Katalog mit Pfaden/Zielauflösungen/Lizenzen.
- Defekte und doppelte Inhalte priorisieren, einheitliche Screenshot-Testfälle definieren.

## PR-B — Erster visueller vertikaler Schnitt
- Einen kompletten Screen (idealerweise **Stadt**) inklusive Assets/Interaktion/State/Performance wirklich fertigstellen.
- Mindestens zwei erkennbare Bauzustände, klickbare Gebäudefunktion, Stadtbeleuchtung nach State.
- Foto-/Pappmaché-Effekt entfernen, keine statischen Pseudo-Hotspots.
- Vorher/Nachher Screenshots.

## PR-C — Schlacht-Darstellung 4.0
- Bestehendes Combat 3.0 mit glaubwürdigen tatsächlichen Einheitensilhouetten, Frontbewegung, Pfeilsalven, Mauerkontakt, Verstärkungen, Rückzug und Sieg-/Niederlage-Feedback visualisieren.
- Dokumentierte Szenario- und Balance-Tests, echte Befehlskette bis Anzeige.

## PR-D — Weltkarte und strategische KI
- Echte Armeepositionen/Abfangen/Scouting/Logistik und glaubwürdige KI-Ziele über existierende State-Simulation; für Spieler verständlich visualisieren.

## PR-E — Reich, Handel, Kultur, Wirtschaft
- Gebäude-/Ressourcen-Konsequenzen, Automatisierung, Forschung, Stadt-/Armee-Interaktion, Handel; sichtbare Folgen, keine isolierten Menüs.

## PR-F — Beziehung, Entscheidungen und Gesellschaft
- Freies situatives Auftreten, eigenständige Gefährtin und Hofpolitik, Folgen und optionale persönliche Wege.

## PR-G — Finish / Content / Android Release
- Leistungsoptimierung, Audio-Polish, Tutorial nur bei Bedarf, Save-Fuzz-/Migrationsfälle, Layout-/Usability-Checks.
- Signierte oder klar als Debug gekennzeichnete APK, Changelog, Versionierung, Installationsanweisungen.

**Prioritäten bei wenig Zeit/Budget:** 1. Schlachtbild und Entscheidungen, 2. Stadtbild, 3. Weltkarte, 4. Navigation, 5. KI/Reichstiefe, 6. Narrative. Nicht „alles ein bisschen“.

# 10. CLAUDE + CODEX ZUSAMMENARBEIT

Claude und Codex können über **GitHub-Arbeitsteilung** kooperieren, wenn beide extern gestartet/angeschlossen werden. Diese Datei aktiviert **keinen** fremden Agenten von selbst.
- **Codex / GPT-6 Astra als verantwortlicher Integrator:** Architekturentscheidungen, Kampfengine-/UI-Integration, Android-Build, CI-Tests, PR Reviews, Releasequalität.
- **Claude Code auf eigenem Branch (optional):** Gameplay-Reaktivität, Gegner-KI, Dialog-/Hoflogik und überprüfbare autonome Module; oder Art-Direction/Asset-Katalog. Keine parallelen Änderungen an denselben Dateien.
- Jede KI liest Basis-Branch + vorhandene PRs. Niemals PRs „blind“ zusammenführen. Bei Konflikten entscheidet Tests + Spielverhalten.
- Kein Agent darf ungefragt `main` überschreiben, alte Spielstände verwerfen oder die Strategie-Engine durch Demo-Screens ersetzen.
- Klare Änderungs- und Testberichte nach jedem Abschnitt; unvollständige Ergebnisse ehrlich kennzeichnen.

# 11. TECHNISCHE GATES / AUTOMATISIERBARE ABNAHME

Für jeden relevanten PR:
1. `gradle :app:testDebugUnitTest` **grün**, zusätzliche Regressionstests für Änderungen.
2. `gradle :app:lintDebug` ohne neue Fehler.
3. `gradle :app:assembleDebug`; Package-/Version-Information und APK-Signatur prüfen.
4. Eventuelle UI-Tests gezielt bei kritischen Screens; 320 × 568/160 % Schrift, Standardgerät und passende Landscape-Bildschirme.
5. Schema-4-Backup erhalten, Schema-5-GameState laden und erneut speichern, aktuelle Saves unverändert, deterministischer Battle-Replay.
6. Keine Ressourcenverdopplung; Einheitentruppen nach Missionen korrekt zurück.
7. Grafikassets mit Lizenzen, mobilem Ressourcenbudget und Visual QA.
8. Eine saubere PR-Beschreibung: Szenario vorher/nachher, Testresultate, potenzielles Risiko, Screenshots/Bilder, offene Restpunkte.

Qualitätsmetrik: **spielbar, schön, verständlich, reaktiv, stabil**. Ein technischer Vollzug ohne spürbar mehr Spielspaß gilt als nicht fertig.

# 12. SOFORT NÄCHSTE KONKRETE AKTIONEN FÜR AUSFÜHRENDEN AGENTEN

1. Prüfe die Branches `codex/v1.0-realm-war-overhaul` und `feature/next-level-gameplay-ux`; nutze eine frische Feature-Branch ab der richtigen Basis und berücksichtige den existierenden PR #10.
2. Inventarisiere in wenigen klaren Kategorien die aktuellen grafischen Assets (Menü, City, Map, Units, Battle), verlinke ihre wirklichen Dateipfade.
3. Lies den tatsächlichen Combat-/City-/World-/UI-Code. Identifiziere **drei wichtigste** für den Spieler sichtbare Defekte oder Qualitätslücken, die du in einem PR vollständig beheben kannst.
4. Implementiere sofort **einen** vollständigen vertikalen Grafik-/Gameplay-Schnitt (am liebsten Kampfdarstellung bei Fernkampf/Mauer) mit echten Engine-Daten, gezielten Tests und Screenshot.
5. Führe Build/Tests aus, erstelle einen Draft-PR und beschreibe das Ergebnis sowie den **nächsten** vollständigen Schnitt.
6. Arbeite die folgenden PRs in dieser Reihenfolge weiter ab, solange die eigentliche Entwicklerumgebung/Task aktiv ist. Keine Abschlussmeldung, die aus reiner Planung besteht.

## ABSCHLIESSENDE PRODUKTREGEL
Nicht „größerer Kampagnenmodus“, sondern **eine freie, optisch eindrucksvolle, tief reagierende Fantasy-Reichssimulation, deren Schlachten endlich wie richtige Schlachten aussehen**. Entscheidungen bleiben beim Spieler; Systeme und Welt antworten darauf. Oberste Priorität sind *sichtbar mehr Spaß und nachweisbar höhere Qualität*.
