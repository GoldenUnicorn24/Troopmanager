package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

const val PLAYER_FACTION = "last_wall"
const val NEUTRAL_FACTION = "free_holds"

@Serializable
enum class Season(val label: String, val marchFactor: Double, val harvestFactor: Double) {
    SPRING("Frühling", 0.9, 1.1), SUMMER("Sommer", 1.0, 1.2),
    AUTUMN("Herbst", 0.9, 1.4), WINTER("Winter", 0.6, 0.65);
    companion object { fun forDay(day: Int) = entries[((day.coerceAtLeast(1) - 1) / 30) % 4] }
}

@Serializable
enum class WeatherKind(val label: String, val marchFactor: Double, val sight: Int, val rangedFactor: Double, val cavalryFactor: Double) {
    CLEAR("Sonnig", 1.0, 2, 1.0, 1.0), RAIN("Regen", 0.8, 1, 0.8, 0.75),
    STORM("Sturm", 0.5, 1, 0.6, 0.55), FOG("Nebel", 0.9, 0, 0.65, 0.9),
    SNOW("Schnee", 0.6, 1, 0.8, 0.6), FROST("Frost", 0.75, 2, 0.95, 0.75), HEAT("Hitze", 0.85, 2, 1.0, 0.85);
}

@Serializable
data class RegionalWeather(val regionId: String, val kind: WeatherKind, val untilDay: Int)

@Serializable
data class WeatherState(val season: Season = Season.SPRING, val regions: List<RegionalWeather> = emptyList()) {
    fun at(regionId: String): WeatherKind = regions.firstOrNull { it.regionId == regionId }?.kind ?: WeatherKind.CLEAR
}

@Serializable
enum class WorldTerrain(val label: String, val movementCost: Int) {
    PLAIN("Ebene", 18), FOREST("Wald", 25), HILLS("Hügel", 25), MOUNTAIN("Bergpass", 34),
    RIVER("Flussfurt", 28), MARSH("Moor", 32), CITY("Stadt", 18), COAST("Küste", 20);
}

@Serializable
enum class PlaceKind(val label: String) {
    KEEP("Burg"), VILLAGE("Dorf"), FOREST("Forst"), MINE("Mine"), RUIN("Ruine"),
    TEMPLE("Tempel"), PASS("Pass"), CAPITAL("Hauptstadt"), PORT("Hafen"), MONSTER_DEN("Kreaturengebiet");
}

@Serializable
data class WorldPlace(
    val id: String, val name: String, val kind: PlaceKind, val terrain: WorldTerrain,
    val x: Float, val y: Float, val ownerId: String = NEUTRAL_FACTION,
    val population: Int = 250, val fortification: Int = 0, val prosperity: Int = 50,
)

@Serializable
data class WorldRoad(val id: String, val from: String, val to: String, val distance: Int, val quality: Int = 70) {
    fun connects(placeId: String) = from == placeId || to == placeId
    fun other(placeId: String) = if (from == placeId) to else from
}

@Serializable
enum class FactionPersonality(val label: String) {
    AGGRESSIVE("Aggressiv"), CAUTIOUS("Vorsichtig"), OPPORTUNISTIC("Opportunistisch"),
    HONORABLE("Ehrenhaft"), CUNNING("Hinterlistig"), MERCANTILE("Kaufmännisch"),
    EXPANSIONIST("Expansionistisch"), ISOLATIONIST("Isolationistisch"), DIPLOMATIC("Diplomatisch"), PARANOID("Paranoid");
}

@Serializable
data class WorldFaction(
    val id: String, val name: String, val culture: Culture, val capitalId: String, val ruler: String,
    val personality: FactionPersonality, val militaryStyle: String, val longTermGoal: String,
    val gold: Int = 3000, val food: Int = 12000, val population: Int = 4000,
    val iron: Int = 2000,
    val aggression: Int = 40, val relations: Map<String, Int> = emptyMap(), val wars: List<String> = emptyList(),
    val rulerAge: Int = 40, val successionCount: Int = 0, val buildings: Int = 1,
    val lastDecision: String = "Grenzen sichern", val lastDecisionDay: Int = 0,
)

@Serializable
data class EnemyCommander(
    val id: String, val factionId: String, val name: String, val culture: Culture,
    val portraitKey: String, val personality: FactionPersonality, val trait: String,
    val rank: String = "Hauptmann", val experience: Int = 0, val victories: Int = 0,
    val defeats: Int = 0, val injuries: Int = 0, val rivalry: Int = 0, val epithet: String? = null,
    val revengeTarget: String? = null, val rulerLoyalty: Int = 70,
    val memories: List<String> = emptyList(),
)

@Serializable
enum class WorldArmyStatus(val label: String) {
    MARCHING("Marschiert"), HOLDING("Sichert Stellung"), RETURNING("Kehrt zurück"),
    MISSION("Mission im Einsatz"), ENGAGED("In der Schlacht"), HOME("Heimgekehrt"), DESTROYED("Aufgelöst");
    val isAway: Boolean get() = this in listOf(MARCHING, HOLDING, RETURNING, MISSION)
}

@Serializable
enum class MarchPolicy(val label: String, val speed: Double, val risk: Double) {
    SAFE("Sicherer Marsch", 0.85, 0.0), NORMAL("Normaler Marsch", 1.0, 0.01), FORCED("Gewaltmarsch", 1.4, 0.025);
}

@Serializable
data class WorldArmy(
    val id: String, val factionId: String, val name: String, val units: List<UnitAllocation>,
    val regionId: String, val commanderId: Long? = null, val enemyCommanderId: String? = null,
    val destinationId: String? = null, val route: List<String> = emptyList(), val routeIndex: Int = 0,
    val legProgress: Int = 0, val arrivalDay: Int? = null, val supplyFood: Int = 0,
    val morale: Int = 80, val marchPolicy: MarchPolicy = MarchPolicy.NORMAL,
    val status: WorldArmyStatus = WorldArmyStatus.HOLDING, val missionId: Long? = null,
    val lastMovedDay: Int = 0, val lastLosses: Int = 0,
    val preparationUntilDay: Int? = null,
    val delayUntilDay: Int = 0,
    val lastSupplyOutpostId: Long? = null,
    val lastOutpostRestDay: Int = 0,
) {
    val total: Int get() = units.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val cultures: Map<Culture, Int> get() = units.groupBy { it.type.culture }.mapValues { (_, troops) -> troops.sumOf { it.amount } }
    val dailyFood: Int get() = units.sumOf { it.amount.toLong() * if (it.type == UnitType.KNIGHT || it.type == UnitType.DRAGON_ARTILLERY) 3 else if (it.type.culture == Culture.GOLD_ELF) 2 else 1 }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val supplyDays: Int get() = supplyFood / dailyFood.coerceAtLeast(1)
}

@Serializable
data class ArmyObservation(
    val armyId: String, val regionId: String, val day: Int, val minimum: Int, val maximum: Int,
    val exact: Boolean = false, val factionId: String = "", val name: String = "Unbekanntes Heer",
)

@Serializable
data class FactionKnowledge(
    val factionId: String, val exploredRegions: List<String> = emptyList(),
    val visibleRegions: List<String> = emptyList(), val observations: List<ArmyObservation> = emptyList(),
)

@Serializable
data class SupplyDepot(val id: String, val regionId: String, val factionId: String, val food: Int, val capacity: Int = 10000)

@Serializable
data class SupplyConvoy(
    val id: String, val factionId: String, val destinationArmyId: String, val food: Int,
    val regionId: String, val route: List<String>, val routeIndex: Int = 0, val arrivalDay: Int,
    val lost: Boolean = false, val complete: Boolean = false,
)

@Serializable
data class WorldRecruitment(
    val id: String, val factionId: String, val armyId: String,
    val type: UnitType, val amount: Int, val completionDay: Int,
)

@Serializable
data class WorldStory(
    val id: String, val key: String, val stage: Int, val startedDay: Int, val nextDay: Int,
    val title: String, val text: String, val regionId: String? = null,
    val options: List<String> = emptyList(), val resolved: Boolean = false, val choice: Int? = null,
)

@Serializable
data class WorldEncounter(val playerArmyId: String, val enemyArmyId: String, val regionId: String, val day: Int)

@Serializable
data class WorldState(
    val initialized: Boolean = false, val places: List<WorldPlace> = emptyList(),
    val roads: List<WorldRoad> = emptyList(), val factions: List<WorldFaction> = emptyList(),
    val enemyCommanders: List<EnemyCommander> = emptyList(), val armies: List<WorldArmy> = emptyList(),
    val knowledge: List<FactionKnowledge> = emptyList(), val weather: WeatherState = WeatherState(),
    val depots: List<SupplyDepot> = emptyList(), val convoys: List<SupplyConvoy> = emptyList(),
    val recruitments: List<WorldRecruitment> = emptyList(),
    val stories: List<WorldStory> = emptyList(), val encounter: WorldEncounter? = null,
    val invasionArmyId: String? = null,
    val nextArmyNumber: Long = 1, val nextConvoyNumber: Long = 1, val lastTickDay: Int = 0,
    val regionReputation: Map<String, Int> = emptyMap(),
) {
    val playerFieldArmies: List<WorldArmy> get() = armies.filter { it.factionId == PLAYER_FACTION && it.missionId == null && it.status.isAway }
    fun knowledgeFor(factionId: String) = knowledge.firstOrNull { it.factionId == factionId } ?: FactionKnowledge(factionId)
    fun place(id: String) = places.firstOrNull { it.id == id }
    fun faction(id: String) = factions.firstOrNull { it.id == id }
}
