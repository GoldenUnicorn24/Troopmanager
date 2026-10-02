package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class SettlementTier(val label: String) {
    BORDER_KEEP("Grenzfeste"),
    CASTLE("Burgsiedlung"),
    WALL_CITY("Mauerstadt"),
    FORTRESS("Großfestung"),
    CAPITAL("Königliche Hauptstadt"),
}

@Serializable
enum class RegionType(val label: String) {
    OWN("Eigenes Gebiet"),
    VILLAGE("Neutrales Dorf"),
    FOREST("Waldgebiet"),
    MINE("Mine"),
    RUIN("Ruine"),
    ORC("Aschenbund-Grenze"),
    MONSTER("Monstergebiet"),
}

@Serializable
data class WorldRegion(
    val id: String,
    val name: String,
    val type: RegionType,
    val mission: MissionType?,
    val owned: Boolean = false,
)

fun initialRegions(): List<WorldRegion> =
    listOf(
        WorldRegion("keep", "Grenzfeste", RegionType.OWN, MissionType.PATROL, true),
        WorldRegion("village", "Eichenfurt", RegionType.VILLAGE, MissionType.RELIEF),
        WorldRegion("forest", "Silberwald", RegionType.FOREST, MissionType.SCOUT),
        WorldRegion("mine", "Erzpass", RegionType.MINE, MissionType.ESCORT),
        WorldRegion("ruin", "Alte Wacht", RegionType.RUIN, MissionType.BANDITS),
        WorldRegion("orc", "Aschehügel", RegionType.ORC, MissionType.BANDITS),
        WorldRegion("monsters", "Nebelklamm", RegionType.MONSTER, MissionType.HUNT),
    )

@Serializable
enum class SiegeDevice(val label: String) {
    RAM("Rammbock"),
    TOWER("Belagerungsturm"),
    LADDERS("Leitern"),
    CATAPULT("Katapulte"),
    CLIMBERS("Nebelklammer"),
    TUNNEL("Belagerungstunnel"),
}

@Serializable
data class Invasion(
    val enemy: EnemyType,
    val arrivalDay: Int,
    val strength: Int,
    val announcedDay: Int,
    val devices: List<SiegeDevice> = emptyList(),
    val alliesRequested: Boolean = false,
    val worldArmyId: String? = null,
)

@Serializable
data class RealmEvent(val key: String, val title: String, val text: String, val category: String)
