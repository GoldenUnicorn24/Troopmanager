# Project Rebirth – Roadmap

Legende: ✅ in diesem Meilenstein umgesetzt und getestet · ◐ teilweise · ☐ offen

| Phase | Inhalt | Stand |
|---|---|---|
| 0 Audit | Branches, Systeme, Risiken, Arbeitsweise | ✅ `PROJECT-REBIRTH-AUDIT.md` |
| 1 Neuer Start | Vier Modi, landloser Start, Herkunft/Beruf/Vermögen/Region, Editor, klassischer Grenzherr erhalten | ✅ |
| 2 Gefährtin | Pflichtfrage A/B/C, Voll-/Teilentwurf, stabile NPC-Identität, frühe natürliche Begegnung, kein Beziehungszwang | ✅ |
| 3 Porträts | Neuer Renderer, Ganzkörper, Ebenen-Pipeline + Asset-Vertrag | ◐ Pipeline und Renderer fertig, **gemalte Ebenen fehlen** (82 Dateien, siehe Design-Dokument) |
| 4 Taverne | Begehbare 2.5D-Szene, Tap-to-Move, Wirt, Getränke/Rausch, Essen, Zimmer, Würfeln, Aufträge, Ereignisse, Schlägerei, Verlassen | ✅ |
| 5 Soziales Leben | Datengetriebene Dialoge, Erinnerungen, Freundschaft, Flirt, Date, Kuss, Nacht (Fade-to-Black), Beziehung, Antrag, Hochzeit, Trennung, Grenzen, Affären-Folgen | ✅ Grundsystem, Beziehungsnetz der NPCs mit Eifersucht und Klatsch, Rollen-Themen (M3); ☐ mehr Inhalte |
| 6 Weltintegration | Gemeinsame Kronprovinzen, Weltpuls, Gerüchte, Preise/Werber bei Bedrohung, Reisen zwischen 7 Städten, Hinterhalte auf gefährlichen Straßen, **begehbarer Markt** (M2), **begehbare Kaserne**, wandernde Reisende (M3) | ◐ ☐ Wetter, Handelspreise je Stadt, Burghof |
| 7 Aufstieg | Laufbahn, **Gefolge** (Freunde + Söldner mit Sold), **persönliche Kämpfe** (Feldzug, Hinterhalt, Wolfsjagd, Duell), **Wunden**, **Gefangenschaft**, **Ausrüstung**, Lehnseid übergibt Gefährtin und Söldner an das Reich (M2) | ◐ ☐ Kopplung der persönlichen Teilnahme an große Schlachten (Combat Engine 3.0) |
| 8 Stabilisierung | Tests, Save-Migration, CI-Build, Emulator-Screenshots | ◐ siehe TEST-REPORT; ☐ Balancing, Langzeit-Playtests |

## Meilenstein 2 (umgesetzt)

- `PartyEngine`: Freunde ziehen mit, wenn *sie* wollen (Sympathie, Vertrauen, Wagemut, Neugier, Lebensziel); Söldner gegen 20 Gold Handgeld und 2 Gold Sold pro Tag, unbezahlt ziehen sie ab. Gemeinsame Reisen und Kämpfe schaffen Vertrauen und Erinnerungen. Mit drei Söldnern und Ruf 70 wird man Hauptmann.
- `SkirmishEngine`: persönliche Gefechte in ≤ 8 Runden mit Angriff, Verteidigung, Bogen, Anführen, Rückzug. Stärke aus Schwert/Bogen/Führung/Reiten, Ausrüstung, Wunden, Rausch, Erschöpfung und Gefolge. Folgen: Gold, Ruf, Wunden über Tage, Gefangenschaft (Flucht, Lösegeld, Warten), verwundete Gefährten.
- `MarketEngine` + begehbarer Markt: Schmied (Waffen/Rüstungen – sichtbar am Charakter), Krämer (Geschenke), Heilerin. Geschenke wirken nach Geschmack der Person.
- Lehnseid: engste Freundin bzw. die gestaltete Gefährtin wird Gefährtin am Hof und Kommandantin, Söldner werden Hauptleute.

## Meilenstein 3 (umgesetzt)

- `NpcSocialEngine`: Jede Stadt hat Freundschaften, Rivalitäten, Paare und Geschwister. Was der Spieler einer Person Gutes oder Schlechtes tut, spüren deren Freunde (⅓), Partner (½) und Rivalen (umgekehrt, ¼). Wer mit Vergebenen flirtet, macht deren Partner eifersüchtig; Vergebene sind schwerer zu gewinnen (abhängig von Bindung und Ehrgefühl). Paare trennen sich, Freunde verlieben sich, Rivalen versöhnen sich – als Klatsch in der Taverne.
- Reisende ziehen alle paar Tage weiter. Die gestaltete Gefährtin wartet nicht: Sucht sie niemand auf, zieht sie in eine noch nicht besuchte Stadt (und man erzählt davon); ist sie befreundet, sucht sie den Spieler gelegentlich auf.
- `BarracksEngine` + begehbare Kaserne: Übungspuppen, Schießstand, Koppel, Ring, Kartentisch erhöhen die vorhandenen Attribute (abnehmender Ertrag, zwei Übungen pro Tag). Stadtwache: kostenloses Training, Wachdienst mit Sold, gelegentlich Diebe am Tor, Rekrut → Soldat.
- Rollen-Themen: Stadtgeschichte (Wirt), Taktik (Ausbilder/Söldnerführer, +1), Hofpolitik (Adlige, +1), Lieder (Spielleute, +1), Handelstipps, Reisegerüchte (auch über die Gefährtin), Klatsch über die Beziehungen.

## Nächste realistische Schritte

1. **Gemalte Porträt-Ebenen** nach Vertrag erstellen (Illustrator oder lizenzierter Generator), in `assets/portraits/` legen – der Code nutzt sie ohne Änderung.
2. **Burghof** als weiterer begehbarer Ort (Adel, Audienzen, Turniere).
3. **Große Schlachten persönlich:** Spieler und Gefolge als Einheit in `TacticalBattleEngine` nach dem Lehnseid.
4. **NPC-Leben vertiefen:** eigene Ziele der NPCs sichtbar voranbringen, NPC-Begegnungen in der Szene (Paare sitzen zusammen, Rivalen streiten sichtbar).
5. **Gefährtin nach dem Lehnseid:** Grundübergang `NpcIdentity` → `CompanionProfile` ist da (M2); offen sind eigene Saga-Kapitel für landlos begonnene Spiele.
6. **Dialoginhalte:** pro Rolle eigene Themen (Wirt: Lokalgeschichte, Söldner: Taktik, Adlige: Politik), mehr Varianten je Persönlichkeit.
7. **Sitz-Posen, Animationen** (Trinken, Lachen), Musiklagen je Tageszeit in der Taverne.
