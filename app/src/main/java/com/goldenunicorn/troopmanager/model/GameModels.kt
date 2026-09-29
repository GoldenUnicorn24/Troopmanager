package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class Species(val label: String) {
    HUMAN("Mensch"),
    ELF("Elb"),
    HALF_ELF("Halbelb")
}

@Serializable
enum class Culture(val label: String) {
    HUMAN("Menschen"),
    WOOD_ELF("Waldelben"),
    GOLD_ELF("Goldelben"),
    WALL("Mauerlegion")
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
    val trainingDays: Int
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
    DRAGON_ARTILLERY("Drachenartillerie", Culture.WALL, 3, 5, 14, 22, 6, 14)
}

@Serializable
enum class BuildingType(val label: String) {
    FARM("Bauernhof"),
    SAWMILL("Sägewerk"),
    IRONWORKS("Eisenwerk"),
    MARKET("Markt"),
    BARRACKS("Kaserne"),
    WALL("Mauer"),
    TOWER("Wehrturm"),
    PALACE("Residenz")
}

@Serializable
enum class EnemyType(val label: String) {
    ORC("Ork-Raubzug"),
    URUK("Uruk-hai-Kriegsheer"),
    TAO_TEI("Tao-Tei-Schwarm")
}

@Serializable
enum class Tactic(val label: String) {
    HOLD("Stellung halten"),
    AGGRESSIVE("Massiver Angriff"),
    RANGED("Fernkampf priorisieren"),
    FLANK("Flankenangriff"),
    FORTIFY("Hinter Mauern verteidigen")
}

@Serializable
enum class MissionType(val label: String) {
    PATROL("Grenzpatrouille"),
    ESCORT("Karawane eskortieren"),
    HUNT("Monsterjagd"),
    RELIEF("Dorfverteidigung")
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
    val sword: Int = 55,
    val bow: Int = 48,
    val riding: Int = 42,
    val leadership: Int = 25,
    val tactics: Int = 22,
    val diplomacy: Int = 20
)

@Serializable
data class Companion(
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
    val role: String = "Gefährtin"
)

@Serializable
data class Commander(
    val id: Long,
    val name: String,
    val culture: Culture,
    val portraitKey: String,
    val level: Int = 1,
    val sword: Int = 60,
    val bow: Int = 55,
    val leadership: Int = 50,
    val tactics: Int = 50,
    val siege: Int = 35,
    val loyalty: Int = 70,
    val trait: String = "Loyal",
    val rank: String = "Hauptmann"
)

@Serializable
data class Regiment(
    val id: Long,
    val name: String,
    val type: UnitType,
    val soldiers: Int,
    val maxSoldiers: Int,
    val experience: Int = 0,
    val morale: Int = 70,
    val commanderId: Long? = null
) {
    val power: Int
        get() {
            val base = type.attack + type.defense + type.ranged
            val experienceMultiplier = 1.0 + experience.coerceAtMost(100) / 200.0
            return (soldiers * base * experienceMultiplier).toInt()
        }
}

@Serializable
data class TrainingOrder(
    val id: Long,
    val type: UnitType,
    val amount: Int,
    val daysRemaining: Int
)

@Serializable
data class Resources(
    val gold: Int = 800,
    val food: Int = 900,
    val wood: Int = 450,
    val stone: Int = 350,
    val iron: Int = 180
)

@Serializable
data class Population(
    val human: Int = 180,
    val woodElf: Int = 55,
    val goldElf: Int = 15,
    val wall: Int = 0,
    val humanRecruits: Int = 42,
    val woodElfRecruits: Int = 12,
    val goldElfRecruits: Int = 4,
    val wallRecruits: Int = 0
) {
    val total: Int get() = human + woodElf + goldElf + wall
    val totalRecruits: Int get() = humanRecruits + woodElfRecruits + goldElfRecruits + wallRecruits

    fun recruits(culture: Culture): Int = when (culture) {
        Culture.HUMAN -> humanRecruits
        Culture.WOOD_ELF -> woodElfRecruits
        Culture.GOLD_ELF -> goldElfRecruits
        Culture.WALL -> wallRecruits
    }
}

@Serializable
data class Realm(
    val territory: Int = 0,
    val settlementName: String = "Grenzlager",
    val buildings: Map<BuildingType, Int> = mapOf(
        BuildingType.FARM to 1,
        BuildingType.BARRACKS to 1
    ),
    val threat: Int = 12,
    val wallIntegrity: Int = 100
) {
    fun level(type: BuildingType): Int = buildings[type] ?: 0
}

@Serializable
data class ChronicleEntry(
    val day: Int,
    val title: String,
    val text: String
)

@Serializable
data class GameState(
    val version: Int = 1,
    val day: Int = 1,
    val player: CharacterProfile,
    val companion: Companion = Companion(),
    val resources: Resources = Resources(),
    val population: Population = Population(),
    val realm: Realm = Realm(),
    val regiments: List<Regiment> = emptyList(),
    val trainingQueue: List<TrainingOrder> = emptyList(),
    val commanders: List<Commander> = emptyList(),
    val renown: Int = 0,
    val rank: String = "Rekrut",
    val title: String = "Landlos",
    val victories: Int = 0,
    val defeats: Int = 0,
    val completedRealm: Boolean = false,
    val chronicle: List<ChronicleEntry> = listOf(
        ChronicleEntry(1, "Ein unbekannter Name", "Du besitzt kaum mehr als deine Ausrüstung. Noch kennt niemand deinen Namen.")
    )
) {
    val armySize: Int get() = regiments.sumOf { it.soldiers }
    val armyPower: Int get() = regiments.sumOf { it.power }
}
