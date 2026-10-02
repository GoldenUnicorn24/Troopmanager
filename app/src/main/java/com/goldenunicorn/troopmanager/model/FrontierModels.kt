package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class AllyPeople(val label: String, val culture: Culture, val description: String) {
    GOLD_ELVES(
        "Reich der Goldelben",
        Culture.GOLD_ELF,
        "Friedlicher Elbenhof. Verkauft Speerwache und schickt im Notfall eine goldene Garde.",
    ),
    FREE_HOLDS(
        "Freie Grenzmarken",
        Culture.HUMAN,
        "Bauern und Milizen. Gegen Gold stellen sie Verstärkung, keine Elitetruppen.",
    ),
    WALL_ENVOYS(
        "Gesandte der Mauerlegion",
        Culture.WALL,
        "Alte Verbündete der Mauer. Liefern Kranichgarde und Wissen über Wandgeschütze.",
    ),
}

@Serializable
data class AllyPact(
    val people: AllyPeople,
    val trust: Int = 55,
    val stock: Int = 180,
    val lastAidDay: Int = -30,
)

@Serializable
enum class HordeKind(val label: String, val hostileToAll: Boolean) {
    ORC("Orks", false),
    URUK("Uruk-hai", false),
    TAO_TEI("Tao Tei", true),
}

@Serializable
data class HordeBanner(
    val id: String,
    val kind: HordeKind,
    val name: String,
    val soldiers: Int,
    val origin: String,
    val daysToArrival: Int,
    val jointWith: String? = null,
    val target: String = "Grenzfeste",
)

@Serializable
data class BorderPatrol(
    val soldiers: Int,
    val daysLeft: Int,
)

@Serializable
enum class WallWeaponType(
    val label: String,
    val filmRole: String,
    val gold: Int,
    val wood: Int,
    val iron: Int,
    val days: Int,
    val defense: Int,
) {
    BALLISTA("Mauerballiste", "Schwere Bolzen wie auf der Großen Mauer", 220, 80, 40, 4, 18),
    REPEATER("Repetierarmbrust", "Schnellfeuer auf der Mauerkrone", 180, 50, 30, 3, 12),
    BLACK_POWDER("Schwarzpulvergeschütz", "Donnerrohr gegen Schwärme", 420, 40, 90, 6, 28),
    FIRE_OIL("Feueröl und Brandpfeile", "Flammender Graben vor den Zinnen", 160, 30, 10, 2, 10),
    CRANE_WINCH("Kranichwinde", "Seilt Soldaten über die Mauer, wie das Kranichkorps", 300, 120, 50, 5, 8),
}

@Serializable
data class WallWeaponStock(val type: WallWeaponType, val count: Int = 0, val daysRemaining: Int = 0)

@Serializable
data class CustomUnitDesign(
    val id: Long,
    val name: String,
    val culture: Culture,
    val attack: Int,
    val defense: Int,
    val ranged: Int,
    val portraitUri: String? = null,
    val soldiers: Int = 0,
    val trainingDaysLeft: Int = 0,
    val trainingAmount: Int = 0,
) {
    val goldCost: Int get() = (6 + attack + defense + ranged / 2).coerceIn(8, 40)
    val trainingDays: Int get() = (5 + (attack + defense + ranged) / 4).coerceIn(4, 24)
    val powerEach: Int get() = attack + defense + ranged
}

@Serializable
data class BondVisual(
    val sessions: Int = 0,
    val lastTrainDay: Int = 0,
    val stage: String = "Bekanntschaft",
)

@Serializable
data class FrontierState(
    val allies: List<AllyPact> = AllyPeople.entries.map { AllyPact(it) },
    val hordes: List<HordeBanner> = emptyList(),
    val patrol: BorderPatrol? = null,
    val weapons: List<WallWeaponStock> = WallWeaponType.entries.map { WallWeaponStock(it) },
    val designs: List<CustomUnitDesign> = emptyList(),
    val bond: BondVisual = BondVisual(),
    val childSessions: Map<String, Int> = emptyMap(),
    val emergencyUsed: Set<String> = emptySet(),
    val lastRaidDay: Int = 0,
    val nextDesignId: Long = 1,
)
