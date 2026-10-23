package com.goldenunicorn.troopmanager.model

/** Static, deterministic content for the v1.1 crown layer. Nothing here is persisted. */
object CrownCatalog {

    /** Which lands touch which. A land is reachable from Grenzmark or from any land where you own a province. */
    val neighbors: Map<CrownLand, Set<CrownLand>> = mapOf(
        CrownLand.GRENZMARK to setOf(CrownLand.ALDMARK, CrownLand.WALDHAIN),
        CrownLand.ALDMARK to setOf(CrownLand.GRENZMARK, CrownLand.KUESTENREICH, CrownLand.KRANICHWALL),
        CrownLand.WALDHAIN to setOf(CrownLand.GRENZMARK, CrownLand.SONNENHAIN, CrownLand.KUESTENREICH),
        CrownLand.SONNENHAIN to setOf(CrownLand.WALDHAIN, CrownLand.ASCHESTEPPE),
        CrownLand.KUESTENREICH to setOf(CrownLand.ALDMARK, CrownLand.WALDHAIN),
        CrownLand.KRANICHWALL to setOf(CrownLand.ALDMARK, CrownLand.ASCHESTEPPE),
        CrownLand.ASCHESTEPPE to setOf(CrownLand.KRANICHWALL, CrownLand.SONNENHAIN),
    )

    private data class P(
        val id: String,
        val name: String,
        val terrain: String,
        val culture: Culture,
        val population: Int,
        val wealth: Int,
        val garrison: Int,
        val quality: Int,
        val owner: ProvinceOwner,
    )

    private val raw: Map<CrownLand, List<P>> = mapOf(
        CrownLand.GRENZMARK to listOf(
            P("gm_rabenfurt", "Rabenfurt", "Furt & Schilf", Culture.HUMAN, 2600, 3, 160, 8, ProvinceOwner.FREE),
            P("gm_eschenhof", "Eschenhof", "Weideland", Culture.HUMAN, 3400, 3, 0, 8, ProvinceOwner.FREE),
            P("gm_kraehenstein", "Krähenstein", "Felsenburg", Culture.HUMAN, 4100, 4, 420, 10, ProvinceOwner.BANDITS),
            P("gm_moorwacht", "Moorwacht", "Moor", Culture.WOOD_ELF, 3000, 3, 380, 9, ProvinceOwner.BANDITS),
            P("gm_schwarzbach", "Schwarzbach", "Bergbau", Culture.HUMAN, 5200, 5, 640, 10, ProvinceOwner.BANDITS),
            P("gm_grauheide", "Grauheide", "Heide", Culture.WALL, 4600, 4, 720, 11, ProvinceOwner.BANDITS),
        ),
        CrownLand.ALDMARK to listOf(
            P("al_aldenburg", "Aldenburg", "Turnierstadt", Culture.HUMAN, 28000, 8, 4200, 16, ProvinceOwner.RIVAL),
            P("al_aldenstein", "Burg Aldenstein", "Ritterburg", Culture.HUMAN, 9000, 6, 2400, 18, ProvinceOwner.RIVAL),
            P("al_weizenau", "Weizenau", "Kornkammer", Culture.HUMAN, 17000, 6, 1300, 13, ProvinceOwner.FREE),
            P("al_hohenlicht", "Hohenlicht", "Klosterland", Culture.HUMAN, 12000, 5, 1600, 14, ProvinceOwner.RIVAL),
            P("al_eisenhall", "Eisenhall", "Erzgebirge", Culture.HUMAN, 15000, 7, 2800, 16, ProvinceOwner.RIVAL),
            P("al_rotfeld", "Rotfeld", "Schlachtfeld", Culture.HUMAN, 8000, 4, 1900, 15, ProvinceOwner.BANDITS),
        ),
        CrownLand.WALDHAIN to listOf(
            P("wh_silberblatt", "Silberblatt", "Urwald", Culture.WOOD_ELF, 9000, 5, 1400, 16, ProvinceOwner.RIVAL),
            P("wh_mondquell", "Mondquell", "Quellwald", Culture.WOOD_ELF, 7000, 5, 900, 15, ProvinceOwner.FREE),
            P("wh_dornenhag", "Dornenhag", "Dornenwald", Culture.WOOD_ELF, 6500, 4, 1800, 17, ProvinceOwner.RIVAL),
            P("wh_hirschtal", "Hirschtal", "Jagdgrund", Culture.WOOD_ELF, 8200, 5, 1200, 15, ProvinceOwner.RIVAL),
            P("wh_nebelhain", "Nebelhain", "Nebelwald", Culture.WOOD_ELF, 5600, 4, 2300, 18, ProvinceOwner.BANDITS),
            P("wh_wipfelruh", "Wipfelruh", "Baumstadt", Culture.WOOD_ELF, 13000, 7, 3200, 18, ProvinceOwner.RIVAL),
        ),
        CrownLand.SONNENHAIN to listOf(
            P("sh_sonnenhof", "Hof von Sonnenhain", "Elbenhauptstadt", Culture.GOLD_ELF, 18000, 10, 7800, 26, ProvinceOwner.RIVAL),
            P("sh_lichtfall", "Lichtfall", "Wasserfälle", Culture.GOLD_ELF, 8000, 8, 3200, 24, ProvinceOwner.RIVAL),
            P("sh_goldwiesen", "Goldwiesen", "Blütenland", Culture.GOLD_ELF, 9500, 8, 2600, 22, ProvinceOwner.FREE),
            P("sh_sternwarte", "Sternwarte", "Kristallturm", Culture.GOLD_ELF, 6000, 9, 4100, 27, ProvinceOwner.RIVAL),
            P("sh_morgentau", "Morgentau", "Terrassengärten", Culture.GOLD_ELF, 11000, 8, 3500, 24, ProvinceOwner.RIVAL),
            P("sh_aschegrat", "Aschegrat", "Grenzfeste", Culture.GOLD_ELF, 7000, 6, 5200, 25, ProvinceOwner.HORDE),
        ),
        CrownLand.KUESTENREICH to listOf(
            P("ks_saltmere", "Saltmere", "Freihafen", Culture.HUMAN, 36000, 10, 5200, 14, ProvinceOwner.RIVAL),
            P("ks_moewenkap", "Möwenkap", "Leuchtturmkap", Culture.HUMAN, 12000, 6, 2100, 13, ProvinceOwner.FREE),
            P("ks_bernsteinbucht", "Bernsteinbucht", "Bucht", Culture.HUMAN, 19000, 8, 3300, 14, ProvinceOwner.RIVAL),
            P("ks_tangfels", "Tangfels", "Piratenfelsen", Culture.HUMAN, 9000, 7, 2900, 15, ProvinceOwner.BANDITS),
            P("ks_velmarsund", "Velmarsund", "Meerenge", Culture.HUMAN, 24000, 9, 4400, 15, ProvinceOwner.RIVAL),
            P("ks_salzmarsch", "Salzmarsch", "Salzgärten", Culture.HUMAN, 14000, 7, 1800, 13, ProvinceOwner.RIVAL),
        ),
        CrownLand.KRANICHWALL to listOf(
            P("kw_kranichtor", "Kranichtor", "Wallfestung", Culture.WALL, 26000, 7, 9000, 19, ProvinceOwner.RIVAL),
            P("kw_jadehain", "Jadehain", "Teegärten", Culture.WALL, 18000, 8, 4200, 17, ProvinceOwner.FREE),
            P("kw_drachensturz", "Drachensturz", "Schlucht", Culture.WALL, 15000, 6, 6800, 19, ProvinceOwner.RIVAL),
            P("kw_tigerpass", "Tigerpass", "Pass", Culture.WALL, 12000, 6, 7600, 20, ProvinceOwner.RIVAL),
            P("kw_lotusdelta", "Lotusdelta", "Flussdelta", Culture.WALL, 32000, 8, 5600, 17, ProvinceOwner.RIVAL),
            P("kw_wachtfeuer", "Wachtfeuer", "Signaltürme", Culture.WALL, 10000, 5, 8400, 21, ProvinceOwner.HORDE),
        ),
        CrownLand.ASCHESTEPPE to listOf(
            P("as_glutmark", "Glutmark", "Lavafelder", Culture.HUMAN, 9000, 6, 14000, 13, ProvinceOwner.HORDE),
            P("as_knochenfeld", "Knochenfeld", "Totenebene", Culture.HUMAN, 7000, 5, 18000, 13, ProvinceOwner.HORDE),
            P("as_eisenpakt", "Feste des Eisenpakts", "Schmiedefestung", Culture.HUMAN, 15000, 9, 26000, 15, ProvinceOwner.HORDE),
            P("as_schattengrube", "Schattengrube", "Nebelkrater", Culture.HUMAN, 6000, 6, 22000, 14, ProvinceOwner.HORDE),
            P("as_aschethron", "Aschethron", "Hordenhauptstadt", Culture.HUMAN, 24000, 10, 40000, 16, ProvinceOwner.HORDE),
            P("as_freistatt", "Freistatt der Entflohenen", "Lagerstadt", Culture.HUMAN, 11000, 4, 3500, 11, ProvinceOwner.FREE),
        ),
    )

    data class ProvinceInfo(val name: String, val land: CrownLand, val terrain: String, val culture: Culture, val wealth: Int)

    private val infos: Map<String, ProvinceInfo> = raw.flatMap { (land, list) ->
        list.map { it.id to ProvinceInfo(it.name, land, it.terrain, it.culture, it.wealth) }
    }.toMap()

    private val unknown = ProvinceInfo("Unbekanntes Land", CrownLand.GRENZMARK, "Ödland", Culture.HUMAN, 1)

    fun info(id: String): ProvinceInfo = infos[id] ?: unknown

    /** Start state of every province; never owned by the player, so changes are always persisted. */
    val startProvinces: List<CrownProvince> by lazy { initialProvinces() }
    private val startById: Map<String, CrownProvince> by lazy { startProvinces.associateBy { it.id } }
    fun startProvince(id: String): CrownProvince? = startById[id]

    fun initialProvinces(): List<CrownProvince> = raw.flatMap { (land, list) ->
        list.map {
            CrownProvince(
                id = it.id, owner = it.owner, population = it.population, garrison = it.garrison,
                baseGarrison = it.garrison, garrisonQuality = it.quality,
                loyalty = if (it.owner == ProvinceOwner.FREE) 55 else 40,
                fortification = when (land) {
                    CrownLand.KRANICHWALL -> 2
                    CrownLand.SONNENHAIN, CrownLand.ALDMARK -> 1
                    else -> 0
                },
            )
        }
    }

    // ---------------------------------------------------------------- trade

    data class TradePartnerDef(
        val id: String,
        val name: String,
        val land: CrownLand,
        val motto: String,
        val buys: ResourceKind,
        val sells: ResourceKind,
        val goods: List<Pair<MilitaryGood, Int>>,
        val packagePrice: Int,
        val renownRequired: Int,
        val needsLand: Boolean,
    )

    val partners: List<TradePartnerDef> = listOf(
        TradePartnerDef("saltmere", "Hanse von Saltmere", CrownLand.KUESTENREICH,
            "Freie Kaufleute mit hundert Schiffen. Kaufen Holz, liefern Getreide und Fisch.",
            ResourceKind.WOOD, ResourceKind.FOOD,
            listOf(MilitaryGood.ARROWS to 6000, MilitaryGood.MEDICINE to 300), 4200, 0, false),
        TradePartnerDef("khazrim", "Zwergenbund von Khazrim", CrownLand.ALDMARK,
            "Die Schmieden unter dem Berg. Kaufen Nahrung, liefern Eisen und Waffen in Massen.",
            ResourceKind.FOOD, ResourceKind.IRON,
            listOf(MilitaryGood.SWORDS to 800, MilitaryGood.ARMOR to 800, MilitaryGood.SHIELDS to 800, MilitaryGood.SPEARS to 600), 16000, 120, false),
        TradePartnerDef("sonnengilde", "Sonnengilde der Goldelben", CrownLand.SONNENHAIN,
            "Elbische Meisterhandwerker. Kaufen Stein für ihre Türme, liefern Gold und Bögen.",
            ResourceKind.STONE, ResourceKind.GOLD,
            listOf(MilitaryGood.BOWS to 900, MilitaryGood.ARROWS to 20000), 14000, 300, true),
        TradePartnerDef("jade", "Jadehandel am Kranichwall", CrownLand.KRANICHWALL,
            "Händler der Mauerlegion. Kaufen Eisen, liefern Stein, Belagerungsteile und Heilmittel.",
            ResourceKind.IRON, ResourceKind.STONE,
            listOf(MilitaryGood.SIEGE_PARTS to 400, MilitaryGood.MEDICINE to 900), 11000, 450, true),
        TradePartnerDef("steppe", "Karawanen der freien Steppe", CrownLand.ASCHESTEPPE,
            "Nomaden, die dem Aschebund entkommen sind. Kaufen Gold-Waren, liefern Pferde in Herden.",
            ResourceKind.GOLD, ResourceKind.FOOD,
            listOf(MilitaryGood.HORSES to 700), 12000, 600, false),
        TradePartnerDef("velmar", "Inselprinzen von Velmar", CrownLand.KUESTENREICH,
            "Unermesslich reiche Luxushändler. Kaufen Nahrung und Holz zu Höchstpreisen.",
            ResourceKind.FOOD, ResourceKind.GOLD,
            listOf(MilitaryGood.ARMOR to 1500, MilitaryGood.HORSES to 400), 28000, 900, false),
    )

    fun partner(id: String): TradePartnerDef? = partners.firstOrNull { it.id == id }

    // ---------------------------------------------------------- mercenaries

    data class MercenaryCompanyDef(
        val id: String,
        val name: String,
        val type: UnitType,
        val soldiers: Int,
        val cost: Int,
        val upkeep: Int,
        val renownRequired: Int,
        val partnerId: String?,
        val description: String,
    )

    val companies: List<MercenaryCompanyDef> = listOf(
        MercenaryCompanyDef("graue_kompanie", "Graue Kompanie", UnitType.HUMAN_SWORD, 500, 5200, 90, 0, null,
            "Erfahrene Schwertkämpfer, die für jeden Herrn kämpfen, der pünktlich zahlt."),
        MercenaryCompanyDef("nordbogen", "Nordmärker Langbögen", UnitType.HUMAN_ARCHER, 450, 5400, 90, 0, null,
            "Bogenschützen aus den Nordmarken mit eigenen Pfeilvorräten."),
        MercenaryCompanyDef("waldschatten", "Waldschatten", UnitType.WOOD_RANGER, 350, 6800, 120, 150, null,
            "Waldläufer ohne Sippe. Lautlos, tödlich, teuer."),
        MercenaryCompanyDef("eisenreiter", "Eisenreiter von Aldmark", UnitType.KNIGHT, 300, 9800, 190, 300, null,
            "Verarmte Ritter in schwerer Platte, die Ruhm und Sold suchen."),
        MercenaryCompanyDef("jadetiger", "Jadetiger", UnitType.TIGER_CORPS, 700, 10500, 170, 400, null,
            "Veteranen der Mauerlegion, die den Wall verlassen haben."),
        MercenaryCompanyDef("khazrim_schildwall", "Schildwall von Khazrim", UnitType.BEAR_CORPS, 900, 15500, 260, 0, "khazrim",
            "Zwergische Schildträger. Nur für Partner des Zwergenbunds."),
        MercenaryCompanyDef("sonnenlanzen", "Sonnenlanzen", UnitType.GOLD_SPEAR, 600, 17000, 300, 0, "sonnengilde",
            "Goldelbische Speerwache im Dienst der Gilde."),
        MercenaryCompanyDef("drachenbatterie", "Drachenbatterie", UnitType.DRAGON_ARTILLERY, 250, 13000, 220, 0, "jade",
            "Feuerrohre und Mannschaften vom Kranichwall."),
        MercenaryCompanyDef("steppenreiter", "Steppenreiter", UnitType.KNIGHT, 1200, 26000, 420, 0, "steppe",
            "Ein ganzer Reiterclan der freien Steppe."),
        MercenaryCompanyDef("verlorene_legion", "Legion der Verlorenen", UnitType.HUMAN_SWORD, 5000, 42000, 800, 1200, null,
            "Fünftausend Söldner aus allen Völkern. Ein Heer für sich."),
        MercenaryCompanyDef("goldene_garde", "Goldene Garde von Velmar", UnitType.GOLD_ARCHER, 1500, 38000, 650, 0, "velmar",
            "Die prunkvolle Leibgarde der Inselprinzen, ausgeliehen gegen Gold."),
    )

    fun company(id: String): MercenaryCompanyDef? = companies.firstOrNull { it.id == id }

    // ------------------------------------------------------------- heroines

    enum class TrialKind { OWN_PROVINCES, DUEL, ARMY_SIZE, RENOWN, WIN_BATTLES }

    data class HeroineDef(
        val id: String,
        val name: String,
        val epithet: String,
        val species: Species,
        val age: Int,
        val culture: Culture,
        val portraitKey: String,
        val weapon: String,
        val armor: String,
        val sword: Int,
        val bow: Int,
        val riding: Int,
        val leadership: Int,
        val tactics: Int,
        val diplomacy: Int,
        val location: String,
        val land: CrownLand,
        val travelDays: Int,
        val rumor: String,
        val unlockHint: String,
        val trial: TrialKind,
        val trialAmount: Int,
        val trialText: String,
        val personalQuestTitle: String,
        val personalQuestText: String,
        val duelSkill: Int,
    )

    val heroines: List<HeroineDef> = listOf(
        HeroineDef(
            "alina", "Alina Sturmblatt", "Grenzläuferin", Species.HALF_ELF, 24, Culture.WOOD_ELF, "companion",
            "Elbenbogen", "Leichte Grenzrüstung", 62, 84, 64, 52, 56, 48,
            "Rabenfurt im Grenzwald", CrownLand.GRENZMARK, 2,
            "Händler berichten von einer Halbelbin, die bei Rabenfurt allein Orkspäher jagt. Niemand weiß, für wen sie kämpft.",
            "Schon bekannt.",
            TrialKind.OWN_PROVINCES, 1,
            "Ihr Dorf liegt schutzlos in der Grenzmark. Sichere eine Provinz der Kronlande, dann folgt sie dir.",
            "Die Asche von Birkenau",
            "Alinas Heimatdorf wurde vom Aschebund niedergebrannt. Besitze drei Provinzen, damit sie die Überlebenden in Sicherheit bringen kann.",
            68,
        ),
        HeroineDef(
            "seraphine", "Seraphine von Aldmark", "Verstoßene Ritterin", Species.HUMAN, 27, Culture.HUMAN, "knight",
            "Bastardschwert", "Geschwärzte Plattenrüstung", 86, 42, 82, 64, 58, 40,
            "Turnierplatz von Aldenburg", CrownLand.ALDMARK, 4,
            "In Aldenburg besiegt eine Ritterin in geschwärzter Rüstung jeden Herausforderer. Man sagt, ihr eigenes Haus habe sie verstoßen.",
            "Erscheint ab 50 Ruhm oder ab Tag 6.",
            TrialKind.DUEL, 1,
            "Seraphine dient nur jemandem, der sie im Zweikampf bezwingt. Fordere sie heraus und gewinne.",
            "Burg Aldenstein",
            "Ihr Bruder hält die Stammburg ihres Hauses. Erobere Burg Aldenstein in der Aldmark und gib ihr die Heimat zurück.",
            78,
        ),
        HeroineDef(
            "liang", "Liang Mei", "Strategin der Kranichgarde", Species.HUMAN, 28, Culture.WALL, "wall_guard",
            "Kranichspeer", "Blaue Lamellenrüstung", 64, 62, 52, 80, 90, 66,
            "Kranichtor am großen Wall", CrownLand.KRANICHWALL, 5,
            "Am Kranichtor soll eine Strategin jede Belagerung vorausgesagt haben. Der Wallrat hört nicht mehr auf sie.",
            "Erscheint ab Tag 10 oder mit 1.500 Soldaten.",
            TrialKind.ARMY_SIZE, 3000,
            "Liang Mei folgt nur einem Herrscher, dessen Heer eine echte Belagerung halten könnte: mindestens 3.000 Soldaten.",
            "Der Verrat am Wall",
            "Der Wallrat hat Liang verraten. Baue ein Heer von 15.000 Soldaten auf, damit sie den Wall mit dir neu ordnen kann.",
            70,
        ),
        HeroineDef(
            "ithiliel", "Ithiliel Sonnenklinge", "Klingentänzerin", Species.ELF, 31, Culture.GOLD_ELF, "gold_elf",
            "Zwillingsklingen", "Goldener Schuppenpanzer", 82, 72, 60, 62, 68, 76,
            "Hof von Sonnenhain", CrownLand.SONNENHAIN, 6,
            "Am Hof von Sonnenhain tanzt eine Klingenmeisterin für die Königin. Sie soll sich nach etwas sehnen, das kein Elbenhof bieten kann.",
            "Erscheint ab 300 Ruhm oder mit einer Provinz im Waldhain.",
            TrialKind.RENOWN, 700,
            "Die Goldelben folgen Namen, nicht Gold. Erreiche 700 Ruhm, dann darf Ithiliel dir dienen.",
            "Das gebrochene Gelübde",
            "Ithiliel verließ ihr Gelübde für dich. Erreiche 2.000 Ruhm, damit der Elbenhof eure Verbindung anerkennt.",
            80,
        ),
    )

    fun heroine(id: String): HeroineDef? = heroines.firstOrNull { it.id == id }

    // ------------------------------------------------------------ titles

    data class TitleStep(val title: String, val requirement: String)

    val titleLadder: List<TitleStep> = listOf(
        TitleStep("Grenzherr", "Startpunkt"),
        TitleStep("Landherr", "2 Gebiete"),
        TitleStep("Regionaler Herrscher", "3 Gebiete"),
        TitleStep("Fürst", "4 Gebiete"),
        TitleStep("König", "5 Gebiete"),
        TitleStep("Hochkönig", "8 Gebiete · 10.000 Soldaten · Mauer Stufe 5"),
        TitleStep("Großkönig", "15 Kronprovinzen · 40.000 Soldaten"),
        TitleStep("Kaiser der Kronlande", "28 Kronprovinzen · 100.000 Soldaten · gekrönte Königin"),
    )
}
