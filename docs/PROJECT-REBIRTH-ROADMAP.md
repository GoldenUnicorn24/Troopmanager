# Project Rebirth – Roadmap

Legende: ✅ in diesem Meilenstein umgesetzt und getestet · ◐ teilweise · ☐ offen

| Phase | Inhalt | Stand |
|---|---|---|
| 0 Audit | Branches, Systeme, Risiken, Arbeitsweise | ✅ `PROJECT-REBIRTH-AUDIT.md` |
| 1 Neuer Start | Vier Modi, landloser Start, Herkunft/Beruf/Vermögen/Region, Editor, klassischer Grenzherr erhalten | ✅ |
| 2 Gefährtin | Pflichtfrage A/B/C, Voll-/Teilentwurf, stabile NPC-Identität, frühe natürliche Begegnung, kein Beziehungszwang | ✅ |
| 3 Porträts | Neuer Renderer, Ganzkörper, Ebenen-Pipeline + Asset-Vertrag | ◐ Pipeline und Renderer fertig, **gemalte Ebenen fehlen** (82 Dateien, siehe Design-Dokument) |
| 4 Taverne | Begehbare 2.5D-Szene, Tap-to-Move, Wirt, Getränke/Rausch, Essen, Zimmer, Würfeln, Aufträge, Ereignisse, Schlägerei, Verlassen | ✅ |
| 5 Soziales Leben | Datengetriebene Dialoge, Erinnerungen, Freundschaft, Flirt, Date, Kuss, Nacht (Fade-to-Black), Beziehung, Antrag, Hochzeit, Trennung, Grenzen, Affären-Folgen | ✅ Grundsystem; ☐ mehr Inhalte, NPC-NPC-Beziehungen, Eifersucht unter NPCs |
| 6 Weltintegration | Gemeinsame Kronprovinzen, Weltpuls, Gerüchte, Preise/Werber bei Bedrohung, Reisen zwischen 7 Städten | ◐ ☐ Wetter, Handelspreise je Stadt, weitere begehbare Orte |
| 7 Aufstieg | Laufbahn, Söldnerkompanie, Lehnseid → volles Strategiespiel | ◐ ☐ persönliche Schlachtteilnahme, Duelle/Verwundung/Gefangenschaft mit Combat Engine 3.0, eigene Söldnergruppe als Heer |
| 8 Stabilisierung | Tests, Save-Migration, CI-Build, Emulator-Screenshots | ◐ siehe TEST-REPORT; ☐ Balancing, Langzeit-Playtests |

## Nächste realistische Schritte

1. **Gemalte Porträt-Ebenen** nach Vertrag erstellen (Illustrator oder lizenzierter Generator), in `assets/portraits/` legen – der Code nutzt sie ohne Änderung.
2. **Taverne als Vorlage verallgemeinern:** `Layout` in Daten auslagern, Markt (Händler, Tagespreise aus Provinzwohlstand) und Kaserne (Anwerbung) als nächste begehbare Orte.
3. **Feldzug mit der Kompanie** an `TacticalBattleEngine` koppeln: Spieler als Einheit, Verwundung/Gefangenschaft als `LifeState`-Zustand.
4. **NPC-Leben:** Beziehungen zwischen NPCs, Eifersucht, Umzüge (`departed`), Gefährtin, die sich ohne Spieler weiterentwickelt (eigener Ruf, eigene Reisen).
5. **Gefährtin als Feldherrin/Mitregentin nach dem Lehnseid:** Übergang `NpcIdentity` → `CompanionProfile`/Heldinnen-Saga für landlos begonnene Spiele.
6. **Dialoginhalte:** pro Rolle eigene Themen (Wirt: Lokalgeschichte, Söldner: Taktik, Adlige: Politik), mehr Varianten je Persönlichkeit.
7. **Sitz-Posen, Animationen** (Trinken, Lachen), Musiklagen je Tageszeit in der Taverne.
