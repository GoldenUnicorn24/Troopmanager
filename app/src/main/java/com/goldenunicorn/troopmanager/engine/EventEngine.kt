package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object EventEngine {
    private data class Choice(
        val label: String,
        val resources: Resources = Resources(0, 0, 0, 0, 0),
        val culture: Culture? = null,
        val population: Int = 0,
        val recruits: Int = 0,
        val renown: Int = 0,
        val threat: Int = 0,
        val wall: Int = 0,
        val tradeDays: Int = 0,
        val scoutingDays: Int = 0,
        val desertion: Boolean = false,
    )

    private data class Definition(val event: RealmEvent, val options: List<Choice>)

    private fun r(gold: Int = 0, food: Int = 0, wood: Int = 0, stone: Int = 0, iron: Int = 0) =
        Resources(gold, food, wood, stone, iron)

    private fun event(
        key: String,
        title: String,
        text: String,
        category: String,
        vararg choices: Choice,
    ) =
        Definition(
            RealmEvent(key, title, text, category),
            choices.toList() + Choice("Abwarten / ablehnen · Ruhm −5", renown = -5),
        )

    private val definitions =
        listOf(
            event(
                "harvest",
                "Gute Ernte",
                "Die Felder tragen reichlich. Einlagern oder verkaufen?",
                "Wirtschaft",
                Choice("Einlagern · +700 Nahrung", r(food = 700)),
                Choice("Verkaufen · +450 Gold", r(gold = 450)),
            ),
            event(
                "drought",
                "Schlechte Ernte",
                "Eine Dürre gefährdet die Versorgung.",
                "Natur",
                Choice("Saatgut kaufen · −300 Gold, +500 Nahrung", r(gold = -300, food = 500)),
                Choice("Vorräte teilen · −250 Nahrung, +15 Ruhm", r(food = -250), renown = 15),
            ),
            event(
                "ore",
                "Neue Erzader",
                "Bergleute entdecken eisenhaltiges Gestein.",
                "Wirtschaft",
                Choice("Abbauen · −150 Gold, +400 Eisen", r(gold = -150, iron = 400)),
                Choice("Verkaufen · +250 Gold", r(gold = 250)),
            ),
            event(
                "quarry",
                "Steinbruchunfall",
                "Ein Gerüst bricht. Die Familien bitten um Unterstützung.",
                "Bevölkerung",
                Choice("Helfen · −200 Gold, +20 Ruhm", r(gold = -200), renown = 20),
                Choice("Gerüst ersetzen · −150 Holz, +200 Stein", r(wood = -150, stone = 200)),
            ),
            event(
                "caravan",
                "Händlerkarawane",
                "Eine Karawane sucht einen sicheren Markt.",
                "Handel",
                Choice(
                    "Handel fördern · −100 Nahrung, +350 Gold",
                    r(food = -100, gold = 350),
                    tradeDays = 5,
                ),
                Choice("Waren tauschen · −200 Holz, +250 Eisen", r(wood = -200, iron = 250)),
            ),
            event(
                "refugees",
                "Flüchtlinge an den Toren",
                "300 Menschen bitten um Aufnahme.",
                "Bevölkerung",
                Choice(
                    "Alle aufnehmen · −600 Nahrung, +300 Bevölkerung",
                    r(food = -600),
                    Culture.HUMAN,
                    300,
                    40,
                    20,
                ),
                Choice(
                    "Freiwillige aufnehmen · −120 Nahrung, +70 Bevölkerung / 60 Rekruten",
                    r(food = -120),
                    Culture.HUMAN,
                    70,
                    60,
                    -10,
                ),
            ),
            event(
                "bandits",
                "Banditen an der Straße",
                "Reisende verlieren Waren an Wegelagerer.",
                "Feinde",
                Choice("Wachen bezahlen · −250 Gold, Bedrohung −8", r(gold = -250), threat = -8),
                Choice(
                    "Zollschutz kaufen · −100 Gold, Handel 3 Tage",
                    r(gold = -100),
                    tradeDays = 3,
                ),
            ),
            event(
                "wood_envoy",
                "Waldelbenbotschafter",
                "Eine Waldelbensiedlung sucht Schutz.",
                "Diplomatie",
                Choice(
                    "Bündnis · −500 Gold, +250 Waldelben / 40 Rekruten",
                    r(gold = -500),
                    Culture.WOOD_ELF,
                    250,
                    40,
                    30,
                ),
                Choice("Handelsvertrag · −200 Gold, Handel 7 Tage", r(gold = -200), tradeDays = 7),
            ),
            event(
                "gold_envoy",
                "Goldelbe Gesandte",
                "Ein kleines Elitevolk bietet eine Allianz an.",
                "Diplomatie",
                Choice(
                    "Ansiedeln · −800 Gold, +120 Goldelben / 20 Rekruten",
                    r(gold = -800),
                    Culture.GOLD_ELF,
                    120,
                    20,
                    40,
                ),
                Choice("Waffenhandel · −400 Gold, +250 Eisen", r(gold = -400, iron = 250)),
            ),
            event(
                "wall_officer",
                "Mauerlegions-Offizier",
                "Ein Offizier bietet seine Familien und Spezialisten an.",
                "Militär",
                Choice(
                    "Aufnehmen · −350 Gold, +180 Mauerbevölkerung / 35 Rekruten",
                    r(gold = -350),
                    Culture.WALL,
                    180,
                    35,
                    20,
                ),
                Choice("Baupläne kaufen · −150 Gold, Mauer +15 %", r(gold = -150), wall = 15),
            ),
            event(
                "sickness",
                "Krankheit",
                "Heiler benötigen Mittel für die Siedlung.",
                "Natur",
                Choice("Heiler finanzieren · −250 Gold, +20 Ruhm", r(gold = -250), renown = 20),
                Choice("Kräuter tauschen · −300 Nahrung, +10 Ruhm", r(food = -300), renown = 10),
            ),
            event(
                "desertion",
                "Unruhe in den Reihen",
                "Unzuverlässige Führer verlieren die Unterstützung ihrer Männer.",
                "Militär",
                Choice("Sold auszahlen · −300 Gold, +10 Ruhm", r(gold = -300), renown = 10),
                Choice("Befehlsverweigerung hinnehmen · mögliche Desertion", desertion = true),
            ),
            event(
                "veteran",
                "Ein erfahrener Veteran",
                "Ein Veteran bringt Freiwillige aus der Grenzregion.",
                "Charaktere",
                Choice(
                    "Anwerben · −180 Gold, +45 Menschen / 35 Rekruten",
                    r(gold = -180),
                    Culture.HUMAN,
                    45,
                    35,
                    10,
                ),
                Choice(
                    "Seine Karten kaufen · −100 Gold, Aufklärung 5 Tage",
                    r(gold = -100),
                    scoutingDays = 5,
                ),
            ),
            event(
                "smith",
                "Meisterschmied",
                "Ein Schmied bietet hochwertiges Metall und Werkzeuge an.",
                "Charaktere",
                Choice("Auftrag vergeben · −500 Gold, +450 Eisen", r(gold = -500, iron = 450)),
                Choice("Werkzeuge tauschen · −250 Holz, +180 Eisen", r(wood = -250, iron = 180)),
            ),
            event(
                "scout",
                "Ein eiliger Späher",
                "Späher bieten Informationen zu Feindbewegungen an.",
                "Feinde",
                Choice(
                    "Aufklärung finanzieren · −120 Gold, 8 Tage Vorwarnung",
                    r(gold = -120),
                    scoutingDays = 8,
                ),
                Choice("Alarm auslösen · −100 Nahrung, Bedrohung −5", r(food = -100), threat = -5),
            ),
            event(
                "rebellion",
                "Dorf rebelliert",
                "Ein Dorf beklagt hohe Abgaben.",
                "Diplomatie",
                Choice(
                    "Abgaben senken · −300 Gold, Bedrohung −6",
                    r(gold = -300),
                    renown = 15,
                    threat = -6,
                ),
                Choice(
                    "Kompromiss · −200 Nahrung, +100 Menschen",
                    r(food = -200),
                    Culture.HUMAN,
                    100,
                    10,
                    5,
                ),
            ),
            event(
                "village_help",
                "Dorf bittet um Hilfe",
                "Familien benötigen Schutz und Baumaterial.",
                "Bevölkerung",
                Choice(
                    "Helfen · −200 Holz, +120 Menschen / 20 Rekruten",
                    r(wood = -200),
                    Culture.HUMAN,
                    120,
                    20,
                    20,
                ),
                Choice("Vorräte schicken · −350 Nahrung, +25 Ruhm", r(food = -350), renown = 25),
            ),
            event(
                "lumber",
                "Gefallene Baumriesen",
                "Ein Sturm legt hochwertigen Wald frei.",
                "Natur",
                Choice("Bergen · −100 Gold, +500 Holz", r(gold = -100, wood = 500)),
                Choice("Verkaufen · +200 Gold", r(gold = 200)),
            ),
            event(
                "flood",
                "Hochwasser",
                "Fluten beschädigen die Außenanlagen.",
                "Natur",
                Choice("Deich bauen · −200 Stein, +25 Ruhm", r(stone = -200), renown = 25),
                Choice("Nahrung retten · −100 Holz, +200 Nahrung", r(wood = -100, food = 200)),
            ),
            event(
                "merchant",
                "Fremder Händler",
                "Ein Händler verkauft seltene Waren.",
                "Handel",
                Choice("Stein kaufen · −300 Gold, +400 Stein", r(gold = -300, stone = 400)),
                Choice("Holz verkaufen · −300 Holz, +350 Gold", r(gold = 350, wood = -300)),
            ),
            event(
                "festival",
                "Erntefest",
                "Die Bevölkerung möchte den Frieden feiern.",
                "Bevölkerung",
                Choice("Fest ausrichten · −300 Nahrung, +30 Ruhm", r(food = -300), renown = 30),
                Choice("Händler einladen · −100 Gold, Handel 5 Tage", r(gold = -100), tradeDays = 5),
            ),
            event(
                "engineer",
                "Festungsbaumeister",
                "Ein Baumeister kann das Tor verstärken.",
                "Charaktere",
                Choice("Tor sichern · −200 Stein, Mauer +25 %", r(stone = -200), wall = 25),
                Choice(
                    "Steinbearbeitung lernen · −150 Gold, +220 Stein",
                    r(gold = -150, stone = 220),
                ),
            ),
            event(
                "wolves",
                "Wölfe im Außenland",
                "Herden werden von Wölfen bedroht.",
                "Feinde",
                Choice("Jäger bezahlen · −150 Gold, +300 Nahrung", r(gold = -150, food = 300)),
                Choice("Zäune bauen · −120 Holz, Bedrohung −4", r(wood = -120), threat = -4),
            ),
            event(
                "pilgrims",
                "Wandernde Familien",
                "Familien möchten in der Grenzfeste bleiben.",
                "Bevölkerung",
                Choice(
                    "Ansiedeln · −250 Nahrung, +140 Menschen / 20 Rekruten",
                    r(food = -250),
                    Culture.HUMAN,
                    140,
                    20,
                    10,
                ),
                Choice("Durchreise fördern · +120 Gold", r(gold = 120)),
            ),
            event(
                "garrison",
                "Freiwillige Grenzwache",
                "Eine örtliche Wehrgemeinschaft sucht Ausrüstung.",
                "Militär",
                Choice(
                    "Bewaffnen · −100 Eisen, +70 Menschen / 50 Rekruten",
                    r(iron = -100),
                    Culture.HUMAN,
                    70,
                    50,
                    10,
                ),
                Choice(
                    "Patrouillen bezahlen · −200 Gold, Bedrohung −7",
                    r(gold = -200),
                    threat = -7,
                ),
            ),
            event(
                "elves",
                "Waldelben-Familien",
                "Einige Waldelben bitten um ein geschütztes Waldviertel.",
                "Diplomatie",
                Choice(
                    "Aufnehmen · −250 Holz, +140 Waldelben / 25 Rekruten",
                    r(wood = -250),
                    Culture.WOOD_ELF,
                    140,
                    25,
                    15,
                ),
                Choice("Waldhandel · +200 Gold", r(gold = 200), tradeDays = 3),
            ),
            event(
                "embassy",
                "Politische Audienz",
                "Ein Nachbar will verlässliche Grenzverträge.",
                "Diplomatie",
                Choice(
                    "Geschenk senden · −350 Gold, Bedrohung −10",
                    r(gold = -350),
                    renown = 20,
                    threat = -10,
                ),
                Choice("Handel öffnen · −100 Nahrung, Handel 6 Tage", r(food = -100), tradeDays = 6),
            ),
            event(
                "iron_tools",
                "Neue Werkzeuge",
                "Handwerker tauschen Steine gegen Eisenwerkzeuge.",
                "Wirtschaft",
                Choice("Tauschen · −300 Stein, +250 Eisen", r(stone = -300, iron = 250)),
                Choice("Auftrag finanzieren · −200 Gold, +220 Eisen", r(gold = -200, iron = 220)),
            ),
            event(
                "grain",
                "Großer Getreidemarkt",
                "Getreide steht günstig zum Verkauf.",
                "Handel",
                Choice("Einlagern · −400 Gold, +1000 Nahrung", r(gold = -400, food = 1000)),
                Choice("Weiterverkaufen · −100 Holz, +250 Gold", r(gold = 250, wood = -100)),
            ),
            event(
                "ruins",
                "Fund in der Ruine",
                "Arbeiter entdecken altes Baumaterial.",
                "Wirtschaft",
                Choice(
                    "Bergen · −200 Nahrung, +350 Stein / 150 Eisen",
                    r(food = -200, stone = 350, iron = 150),
                ),
                Choice("Antiquitäten verkaufen · +300 Gold", r(gold = 300)),
            ),
            event(
                "military_council",
                "Der Kriegsrat",
                "Offiziere fordern bessere Grenzvorsorge.",
                "Militär",
                Choice(
                    "Späher bezahlen · −200 Gold, Aufklärung 10 Tage",
                    r(gold = -200),
                    scoutingDays = 10,
                ),
                Choice("Tor verstärken · −150 Eisen, Mauer +20 %", r(iron = -150), wall = 20),
            ),
            event(
                "monster_tracks",
                "Spuren im Nebel",
                "Monster bewegen sich nahe den Außenhöfen.",
                "Feinde",
                Choice(
                    "Warnfeuer errichten · −150 Holz, Bedrohung −6",
                    r(wood = -150),
                    threat = -6,
                ),
                Choice(
                    "Jäger informieren · −100 Gold, Aufklärung 6 Tage",
                    r(gold = -100),
                    scoutingDays = 6,
                ),
            ),
        )
    val catalogue: List<RealmEvent>
        get() = definitions.map { it.event }

    fun choices(event: RealmEvent): List<String> =
        definitions.find { it.event.key == event.key }?.options?.map { it.label } ?: emptyList()

    fun day(state: GameState): GameState =
        if (state.pendingRealmEvent != null || state.day % 3 != 0) state
        else
            state.copy(
                pendingRealmEvent = definitions[(state.day / 3 - 1) % definitions.size].event
            )

    fun choose(state: GameState, choice: Int): GameEngine.ActionResult {
        val event =
            state.pendingRealmEvent
                ?: return GameEngine.ActionResult(state, "Kein Reichsereignis offen.")
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Der Kriegsrat wartet bis nach der Schlacht.")
        val c =
            definitions.find { it.event.key == event.key }?.options?.getOrNull(choice)
                ?: return GameEngine.ActionResult(state, "Ungültige Entscheidung.")
        val r = state.resources
        val d = c.resources
        if (
            r.gold.toLong() + d.gold < 0 ||
                r.food.toLong() + d.food < 0 ||
                r.wood.toLong() + d.wood < 0 ||
                r.stone.toLong() + d.stone < 0 ||
                r.iron.toLong() + d.iron < 0
        )
            return GameEngine.ActionResult(state, "Nicht genug Vorräte für diese Entscheidung.")
        var pop = state.population
        c.culture?.let {
            val before = ArmyEngine.population(pop, it)
            pop = ArmyEngine.adjustPopulation(pop, it, c.population)
            pop =
                ArmyEngine.adjustRecruits(
                    pop,
                    it,
                    minOf(c.recruits, ArmyEngine.population(pop, it) - before),
                )
        }
        val diplomaticBenefit =
            if (event.category == "Diplomatie") state.player.diplomacy / 10 else 0
        var next =
            state.copy(
                pendingRealmEvent = null,
                population = pop,
                resources = EconomyEngine.add(r, d),
                renown = (state.renown + c.renown + diplomaticBenefit).coerceAtLeast(0),
                realm =
                    state.realm.copy(
                        threat = (state.realm.threat + c.threat).coerceIn(0, 100),
                        wallIntegrity = (state.realm.wallIntegrity + c.wall).coerceIn(0, 100),
                        tradeBonusDays = maxOf(state.realm.tradeBonusDays, c.tradeDays),
                        scoutingDays = maxOf(state.realm.scoutingDays, c.scoutingDays),
                    ),
                chronicle =
                    (state.chronicle + ChronicleEntry(state.day, event.title, c.label)).takeLast(80),
            )
        if (c.desertion) {
            val losses =
                UnitType.entries.map { type ->
                    val unreliable =
                        next.commanderAssignments
                            .filter { a ->
                                (next.commanders.find { it.id == a.commanderId }?.loyalty ?: 100) <
                                    70
                            }
                            .sumOf { a -> a.units.filter { it.type == type }.sumOf { it.amount } }
                    UnitAllocation(type, unreliable / 20)
                }
            next = ArmyEngine.applyLosses(next, losses)
        }
        return GameEngine.ActionResult(
            ProgressionEngine.update(ProgressionEngine.awardXp(next, 15)),
            "${event.title}: Entscheidung umgesetzt.",
        )
    }
}
