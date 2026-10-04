package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class Species(val label: String) {
    HUMAN("Mensch"),
    ELF("Elb"),
    HALF_ELF("Halbelb"),
}

@Serializable
enum class Culture(val label: String) {
    HUMAN("Menschen"),
    WOOD_ELF("Waldelben"),
    GOLD_ELF("Goldelben"),
    WALL("Mauerlegion"),
}

@Serializable
enum class UnitType(
    val label: String,
    val culture: Culture,
    val attack: Int,
    val defense: Int,
    val ranged: Int,
    val goldCost: Int,
    val ironCost: Int,
    val trainingDays: Int,
) {
    HUMAN_SWORD("Schwertkämpfer", Culture.HUMAN, 7, 7, 1, 7, 2, 5),
    HUMAN_ARCHER("Bogenschützen", Culture.HUMAN, 3, 4, 8, 8, 1, 7),
    KNIGHT("Schwere Ritter", Culture.HUMAN, 10, 10, 2, 18, 5, 12),
    WOOD_RANGER("Waldelben-Waldläufer", Culture.WOOD_ELF, 6, 5, 10, 14, 2, 10),
    WOOD_BLADE("Waldklingen", Culture.WOOD_ELF, 9, 7, 3, 15, 3, 10),
    GOLD_SPEAR("Goldene Speerwache", Culture.GOLD_ELF, 11, 12, 4, 24, 5, 16),
    GOLD_ARCHER("Goldene Bogengarde", Culture.GOLD_ELF, 7, 8, 13, 25, 4, 16),
    CRANE_GUARD("Blaue Kranichgarde", Culture.WALL, 8, 8, 6, 13, 3, 9),
    EAGLE_CORPS("Adlerkorps", Culture.WALL, 4, 5, 9, 11, 2, 8),
    TIGER_CORPS("Tigerkorps", Culture.WALL, 10, 9, 2, 14, 4, 10),
    BEAR_CORPS("Bärenkorps", Culture.WALL, 8, 12, 1, 15, 5, 11),
    DEER_CORPS("Hirschkorps", Culture.WALL, 5, 8, 4, 10, 2, 8),
    DRAGON_ARTILLERY("Drachenartillerie", Culture.WALL, 3, 5, 14, 22, 6, 14),
}

@Serializable
enum class BuildingType(val label: String) {
    FARM("Bauernhof"),
    SAWMILL("Sägewerk"),
    QUARRY("Steinbruch"),
    IRONWORKS("Eisenmine & Schmiede"),
    MARKET("Markt"),
    BARRACKS("Kaserne"),
    WALL("Mauer"),
    TOWER("Wehrturm"),
    PALACE("Residenz"),
    RESIDENTIAL("Wohnviertel"),
    WAREHOUSE("Lagerhaus"),
    HOSPITAL("Lazarett"),
    ACADEMY("Offiziersschule"),
    STABLES("Stallungen"),
    ARSENAL("Arsenal"),
    EMBASSY("Botschaft"),
}

@Serializable
enum class EnemyType(val label: String) {
    ORC("Aschebund-Raubzug"),
    URUK("Eisenpakt-Kriegsheer"),
    TAO_TEI("Nebelbrut-Schwarm"),
}

@Serializable
enum class Tactic(val label: String) {
    HOLD("Stellung halten"),
    AGGRESSIVE("Massiver Angriff"),
    RANGED("Fernkampf priorisieren"),
    FLANK("Flankenangriff"),
    FORTIFY("Hinter Mauern verteidigen"),
}

@Serializable
enum class MissionType(val label: String) {
    PATROL("Grenzpatrouille"),
    ESCORT("Karawane eskortieren"),
    HUNT("Monsterjagd"),
    RELIEF("Dorfverteidigung"),
    BANDITS("Banditenlager vernichten"),
    SCOUT("Aufklärung"),
}

@Serializable
data class CharacterProfile(
    val name: String,
    val age: Int = 23,
    val species: Species = Species.HALF_ELF,
    val portraitUri: String? = null,
    val armorStyle: String = "Grenzwächter",
    val weapon: String = "Langschwert",
    val level: Int = 1,
    val experience: Int = 0,
    val skillPoints: Int = 0,
    val sword: Int = 55,
    val bow: Int = 48,
    val riding: Int = 42,
    val leadership: Int = 25,
    val tactics: Int = 22,
    val diplomacy: Int = 20,
)

@Serializable
data class CompanionProfile(
    val met: Boolean = false,
    val name: String = "Alina",
    val age: Int = 21,
    val species: Species = Species.HALF_ELF,
    val portraitUri: String? = null,
    val armorStyle: String = "Leichte Grenzrüstung",
    val weapon: String = "Elbenbogen",
    val level: Int = 1,
    val sword: Int = 58,
    val bow: Int = 72,
    val riding: Int = 55,
    val leadership: Int = 35,
    val tactics: Int = 40,
    val diplomacy: Int = 48,
    val trust: Int = 20,
    val respect: Int = 20,
    val affection: Int = 10,
    val role: String = "Gefährtin",
)

@Serializable
data class Commander(
    val id: Long,
    val name: String,
    val culture: Culture,
    val portraitKey: String,
    val portraitUri: String? = null,
    val level: Int = 1,
    val sword: Int = 60,
    val bow: Int = 55,
    val leadership: Int = 50,
    val tactics: Int = 50,
    val siege: Int = 35,
    val loyalty: Int = 70,
    val trait: String = "Loyal",
    val rank: String = "Hauptmann",
    val missionsCompleted: Int = 0,
    val battlesFought: Int = 0,
    val victories: Int = 0,
    val casualties: Int = 0,
    val diplomacy: Int = 35,
)

@Serializable data class UnitAllocation(val type: UnitType, val amount: Int)

@Serializable
data class CommanderAssignment(
    val commanderId: Long,
    val units: List<UnitAllocation> = emptyList(),
) {
    val total: Int
        get() = units.sumOf { it.amount }
}

@Serializable
data class TrainingOrder(val id: Long, val type: UnitType, val amount: Int, val daysRemaining: Int)

@Serializable
data class Resources(
    val gold: Int = 5000,
    val food: Int = 8000,
    val wood: Int = 3500,
    val stone: Int = 2500,
    val iron: Int = 1500,
)

@Serializable
data class Population(
    val human: Int = 900,
    val woodElf: Int = 450,
    val goldElf: Int = 150,
    val wall: Int = 350,
    val humanRecruits: Int = 42,
    val woodElfRecruits: Int = 40,
    val goldElfRecruits: Int = 20,
    val wallRecruits: Int = 45,
) {
    val total: Int
        get() = human + woodElf + goldElf + wall

    val totalRecruits: Int
        get() = humanRecruits + woodElfRecruits + goldElfRecruits + wallRecruits

    fun recruits(culture: Culture): Int =
        when (culture) {
            Culture.HUMAN -> humanRecruits
            Culture.WOOD_ELF -> woodElfRecruits
            Culture.GOLD_ELF -> goldElfRecruits
            Culture.WALL -> wallRecruits
        }

    fun count(culture: Culture): Int = when (culture) {
        Culture.HUMAN -> human
        Culture.WOOD_ELF -> woodElf
        Culture.GOLD_ELF -> goldElf
        Culture.WALL -> wall
    }

    fun takeRecruits(culture: Culture, amount: Int): Population = when (culture) {
        Culture.HUMAN -> copy(human = (human - amount).coerceAtLeast(0), humanRecruits = (humanRecruits - amount).coerceAtLeast(0))
        Culture.WOOD_ELF -> copy(woodElf = (woodElf - amount).coerceAtLeast(0), woodElfRecruits = (woodElfRecruits - amount).coerceAtLeast(0))
        Culture.GOLD_ELF -> copy(goldElf = (goldElf - amount).coerceAtLeast(0), goldElfRecruits = (goldElfRecruits - amount).coerceAtLeast(0))
        Culture.WALL -> copy(wall = (wall - amount).coerceAtLeast(0), wallRecruits = (wallRecruits - amount).coerceAtLeast(0))
    }
}

@Serializable
data class Realm(
    val territory: Int = 1,
    val customSettlementName: String? = null,
    val settlementTier: SettlementTier = SettlementTier.BORDER_KEEP,
    val buildings: Map<BuildingType, Int> =
        BuildingType.entries.associateWith {
            if (it == BuildingType.FARM || it == BuildingType.BARRACKS) 2 else if (it.ordinal <= BuildingType.PALACE.ordinal) 1 else 0
        },
    val threat: Int = 12,
    val wallIntegrity: Int = 100,
    val tradeBonusDays: Int = 0,
    val scoutingDays: Int = 0,
) {
    val settlementName: String
        get() = customSettlementName ?: settlementTier.label

    fun level(type: BuildingType): Int = buildings[type] ?: 0
}

@Serializable data class ChronicleEntry(val day: Int, val title: String, val text: String)

@Serializable
data class GameState(
    val version: Int = 4,
    val day: Int = 1,
    val player: CharacterProfile,
    val companion: CompanionProfile = CompanionProfile(),
    val resources: Resources = Resources(),
    val population: Population = Population(),
    val realm: Realm = Realm(),
    val city: CityState = CityState(),
    val world: WorldState = WorldState(),
    val war: WarState = WarState(),
    val court: CourtState = CourtState(),
    val dynasty: DynastyState = DynastyState(),
    val frontier: FrontierState = FrontierState(),
    val coRuler: CoRulerState = CoRulerState(),
    val presence: PresenceState = PresenceState(),
    val journal: QuestJournalState = QuestJournalState(),
    val diplomacy: DiplomacyState = DiplomacyState(),
    val espionage: EspionageState = EspionageState(),
    val society: SocietyState = SocietyState(),
    val presentation: PresentationState = PresentationState(),
    val campaign: CampaignPulseState = CampaignPulseState(),
    val settings: GameSettings = GameSettings(),
    /** Cultures deliberately chosen when the realm was founded. Empty only on legacy saves. */
    val foundingCultures: Set<Culture> = emptySet(),
    val culturePatronage: Map<Culture, Int> = emptyMap(),
    val militaryStock: MilitaryStock = MilitaryStock(),
    val doctrine: MilitaryDoctrine = MilitaryDoctrine.BALANCED,
    val research: ResearchState = ResearchState(),
    val dailyReport: DailyReport = DailyReport(),
    val commanderEvents: CommanderEventState = CommanderEventState(),
    val occupations: List<OccupiedRegion> = emptyList(),
    val armyPools: List<ArmyUnitPool> = emptyList(),
    val trainingQueue: List<TrainingOrder> = emptyList(),
    val commanders: List<Commander> = emptyList(),
    val commanderAssignments: List<CommanderAssignment> = emptyList(),
    val activeMissions: List<ActiveMission> = emptyList(),
    val battleSession: BattleSession? = null,
    val invasion: Invasion? = null,
    val relationship: RelationshipState = RelationshipState(),
    val pendingRealmEvent: RealmEvent? = null,
    val regions: List<WorldRegion> = initialRegions(),
    val renown: Int = 0,
    val rank: String = "Grenzhauptmann",
    val title: String = "Grenzherr",
    val victories: Int = 0,
    val defeats: Int = 0,
    val completedRealm: Boolean = false,
    val tutorialSeen: Boolean = false,
    val chronicle: List<ChronicleEntry> =
        listOf(
            ChronicleEntry(
                1,
                "Die Grenzfeste",
                "Ein eigenes Gebiet, eine stehende Armee und eine arbeitende Siedlung: Dein Reich beginnt hier.",
            )
        ),
) {
    val armySize: Int
        get() = armyPools.sumOf { it.soldiers }

    val armyPower: Int
        get() = armyPools.sumOf { it.power.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    val awayArmySize: Int
        get() = activeMissions.filter { it.status.isAway }.sumOf { it.total } +
            world.playerFieldArmies.sumOf { it.total } + (frontier.patrol?.soldiers ?: 0)

    val homeArmySize: Int
        get() = (armySize - awayArmySize).coerceAtLeast(0)

    val trainingSize: Int
        get() = trainingQueue.sumOf { it.amount } + frontier.designs.sumOf { it.trainingAmount }

    val civilianPopulation: Int
        get() = (population.total.toLong() - armySize - trainingSize -
            war.wounded.sumOf { it.soldiers.toLong() } - war.captives.filter { it.own }.sumOf { it.soldiers.toLong() })
            .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    val workerDemand: Int
        get() = 20 + realm.buildings.values.sum() * 25 + realm.territory * 20

    val workers: Int
        get() = (civilianPopulation - population.totalRecruits).coerceIn(0, workerDemand)

    val freePopulation: Int
        get() = (civilianPopulation - workers - population.totalRecruits).coerceAtLeast(0)

    fun soldiers(type: UnitType): Int = armyPools.firstOrNull { it.type == type }?.soldiers ?: 0

    fun away(type: UnitType): Int =
        activeMissions
            .filter { it.status.isAway }
            .sumOf { m -> m.units.filter { it.type == type }.sumOf { it.amount } } +
            world.playerFieldArmies.sumOf { a -> a.units.filter { it.type == type }.sumOf { it.amount } } +
            (frontier.patrol?.units?.filter { it.type == type }?.sumOf { it.amount } ?: 0)

    fun homeSoldiers(type: UnitType): Int = (soldiers(type) - away(type)).coerceAtLeast(0)

    fun assigned(type: UnitType): Int =
        commanderAssignments
            .filterNot { commanderAway(it.commanderId) }
            .sumOf { a -> a.units.filter { it.type == type }.sumOf { it.amount } }

    fun directCommand(type: UnitType): Int = (homeSoldiers(type) - assigned(type)).coerceAtLeast(0)

    fun assignedTo(commanderId: Long, type: UnitType): Int =
        commanderAssignments
            .firstOrNull { it.commanderId == commanderId }
            ?.units
            ?.firstOrNull { it.type == type }
            ?.amount ?: 0

    val playerAwayOnMission: Boolean
        get() = activeMissions.any { it.playerParticipates && it.status.isAway }

    fun commanderAway(id: Long): Boolean =
        activeMissions.any { id in it.allCommanderIds && it.status.isAway } ||
            world.playerFieldArmies.any { it.commanderId == id } || war.unavailableCommander(id)
}
