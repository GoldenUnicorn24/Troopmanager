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
    val requestsWithoutReturn: Int = 0,
    val contributions: Int = 0,
)

@Serializable
data class AllyReinforcement(
    val id: Long,
    val people: AllyPeople,
    val type: UnitType,
    val amount: Int,
    val origin: String,
    val originRegionId: String,
    val daysRemaining: Int,
    val arrivalDay: Int,
    val emergency: Boolean = false,
    val departureDay: Int = 0,
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
    /** Existing invasion army, when this is a scouting projection rather than a separate raid. */
    val worldArmyId: String? = null,
    val regionId: String = "keep",
    val discovered: Boolean = true,
    val estimateMinimum: Int = 0,
    val estimateMaximum: Int = 0,
) {
    val estimatedStrengthLabel: String
        get() = if (estimateMinimum > 0 && estimateMaximum >= estimateMinimum)
            "$estimateMinimum–$estimateMaximum geschätzte Gegner" else "Stärke unklar"
}

@Serializable
data class BorderPatrol(
    val soldiers: Int,
    val daysLeft: Int,
    val units: List<UnitAllocation> = emptyList(),
)

@Serializable
enum class BorderOutpostType(val label: String, val gold: Int, val wood: Int, val stone: Int) {
    WATCHTOWER("Wachturm", 260, 180, 120),
    SUPPLY("Versorgungsposten", 320, 220, 80),
    FORTIFIED("Grenzfort", 480, 260, 240),
}

@Serializable
data class BorderOutpost(
    val id: Long,
    val type: BorderOutpostType,
    val name: String,
    val level: Int = 1,
    val integrity: Int = 100,
)

@Serializable
data class FrontierChoiceEvent(
    val key: String,
    val title: String,
    val text: String,
    val firstLabel: String,
    val secondLabel: String,
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
data class WallWeaponStock(
    val type: WallWeaponType,
    val count: Int = 0,
    val daysRemaining: Int = 0,
    val ammunition: Int = 0,
    val integrity: Int = 100,
    val reloadRounds: Int = 0,
    val section: BattleSection = BattleSection.CENTER,
)

@Serializable
enum class CustomUnitRole(val label: String) {
    INFANTRY("Infanterie"), RANGED("Fernkampf"), CAVALRY("Reiterei"), SIEGE("Belagerung"),
}

@Serializable
enum class CustomWeapon(val label: String) {
    SWORD("Schwert"), SPEAR("Speer"), BOW("Bogen"), CROSSBOW("Armbrust"), ARTILLERY("Geschütz"),
}

@Serializable
enum class CustomArmor(val label: String) {
    LIGHT("Leichte Rüstung"), MAIL("Kettenrüstung"), PLATE("Plattenrüstung"),
}

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
    val role: CustomUnitRole = CustomUnitRole.INFANTRY,
    val weapon: CustomWeapon = CustomWeapon.SWORD,
    val armor: CustomArmor = CustomArmor.MAIL,
    val shield: Boolean = true,
    val colorHex: String = "#D6B66B",
    /** Trained soldiers are a named subset of this real army pool, never a second army. */
    val unitType: UnitType = UnitType.HUMAN_SWORD,
    val captainId: Long? = null,
    val battles: Int = 0,
    val victories: Int = 0,
    val losses: Int = 0,
) {
    val veteranLevel: Int get() = ((battles + victories * 2) / 3).coerceIn(0, 5)
    val veteranTitle: String get() = when (veteranLevel) {
        0 -> "Neu aufgestellt"
        1 -> "Erprobt"
        2 -> "Veteranen"
        3 -> "Elite"
        4 -> "Garde"
        else -> "Legenden"
    }
    val goldCost: Int get() = (6 + attack + defense + ranged / 2).coerceIn(8, 40)
    val trainingDays: Int get() = (5 + (attack + defense + ranged) / 4).coerceIn(4, 24)
    val powerEach: Int get() = attack + defense + ranged
    val foodEach: Int get() = if (role == CustomUnitRole.CAVALRY || role == CustomUnitRole.SIEGE) 3 else if (culture == Culture.GOLD_ELF) 2 else 1
}

@Serializable
data class BondVisual(
    val sessions: Int = 0,
    val lastTrainDay: Int = 0,
    val stage: String = "Bekanntschaft",
    val sharedBattles: Int = 0,
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
    val reinforcements: List<AllyReinforcement> = emptyList(),
    val nextReinforcementId: Long = 1,
    val lastTickDay: Int = 0,
    val hordePressureKeys: Set<String> = emptySet(),
    val dailyGoal: String = "",
    val dailyGoalKey: String = "",
    val dailyGoalClaimed: Boolean = false,
    val lastStoryDay: Int = 0,
    val pendingCampAssaultId: String? = null,
    val pendingCampRewardGold: Int = 0,
    val outposts: List<BorderOutpost> = emptyList(),
    val nextOutpostId: Long = 1,
    val pendingChoiceEvent: FrontierChoiceEvent? = null,
)

/** Campaign forecasts reveal only what scouts have reported, including for legacy invasions. */
fun GameState.invasionStrengthEstimate(): String = frontier.hordes.firstOrNull {
    it.discovered && it.id.startsWith("invasion-") &&
        (invasion?.worldArmyId == null || it.worldArmyId == invasion?.worldArmyId)
}?.estimatedStrengthLabel ?: "Stärke unklar"
