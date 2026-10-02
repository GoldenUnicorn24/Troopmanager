package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class BattleSection(val label: String) {
    LEFT("Linker Flügel"),
    CENTER("Zentrum"),
    RIGHT("Rechter Flügel"),
    RESERVE("Reserve"),
}

@Serializable
enum class BattlePhase(val label: String) {
    FORMATION("Aufstellung"),
    RANGED("Fernkampf"),
    CONTACT("Erster Kontakt"),
    MAIN("Hauptkampf"),
    RESERVES("Reserven"),
    CRITICAL("Kritische Phase"),
    DECISION("Entscheidung"),
    PURSUIT("Flucht / Verfolgung"),
}

@Serializable
enum class BattleStatus {
    ACTIVE,
    PURSUIT,
    VICTORY,
    DEFEAT,
}

@Serializable
enum class BattleDecision(val label: String) {
    SEND_RESERVE("Reserve schicken"),
    RETREAT_LINE("Linie zurücknehmen"),
    FOCUS_ARCHERS("Bogenschützen konzentrieren"),
    CAVALRY_CHARGE("Kavallerie angreifen lassen"),
    HOLD("Position halten"),
    PURSUE("Gegner verfolgen"),
    HOLD_FORMATION("Formation halten"),
    RELOCATE_RESERVE("Reserve verlegen"),
    STRENGTHEN_SECTION("Abschnitt verstärken"),
    ORDERED_RETREAT("Geordnet zurückziehen"),
    ARTILLERY_TARGET("Artillerie auf Belagerungsgerät richten"),
    HOLD_GATE("Tor halten"),
    OPEN_GATE("Tor für Kavallerieausfall öffnen"),
    RESCUE_COMMANDER("Verwundeten Kommandanten retten"),
    ADVANCE("Vorrücken"),
    ARROW_VOLLEY("Pfeilsalve"),
    FOCUS_FIRE("Fokusfeuer"),
    RALLY("Schlachtrede"),
    FEIGNED_RETREAT("Falscher Rückzug"),
    SCALE_WALL("Leiterangriff"),
    BREACH_GATE("Rammbock einsetzen"),
    TOWER_ASSAULT("Belagerungsturm vorschieben"),
    UNDERMINE("Tunnel vorantreiben"),
}

/** A consumer can react to a newly persisted minute; no audio files are required. */
@Serializable
enum class BattleSoundCue {
    HORN,
    SWORDS,
    ARROWS,
    MONSTERS,
    WALL_BREAK,
    ARTILLERY,
    HORSES,
    FIRE,
    GATE,
}

@Serializable
data class BattleDeployment(
    val commanderId: Long? = null,
    val section: BattleSection,
    val units: List<UnitAllocation>,
)

@Serializable
data class BattleContingent(
    val type: UnitType,
    val commanderId: Long?,
    val section: BattleSection,
    val soldiers: Int,
    val startSoldiers: Int,
    val experience: Int,
    val morale: Int,
    val equipment: Int,
    val commanderWounded: Boolean = false,
    val commanderRescued: Boolean = false,
    val routed: Boolean = false,
)

@Serializable
data class BattleFront(
    val section: BattleSection,
    val enemySoldiers: Int,
    val enemyStart: Int,
    val morale: Int = 80,
    val position: Int = 50,
)

@Serializable
data class BattleEvent(
    val title: String,
    val text: String,
    val section: BattleSection,
    val options: List<BattleDecision>,
)

@Serializable
data class BattleLogEntry(
    val minute: Int,
    val text: String,
    val ownLosses: Int = 0,
    val enemyLosses: Int = 0,
)

@Serializable
data class BattleSession(
    val enemy: EnemyType,
    val tactic: Tactic,
    val seed: Int,
    val ownStart: Int,
    val enemyStart: Int,
    val contingents: List<BattleContingent>,
    val fronts: List<BattleFront>,
    val minute: Int = 0,
    val step: Int = 0,
    val phase: BattlePhase = BattlePhase.FORMATION,
    val status: BattleStatus = BattleStatus.ACTIVE,
    val pendingEvent: BattleEvent? = null,
    val devices: List<SiegeDevice> = emptyList(),
    val wallIntegrity: Int = 100,
    val log: List<BattleLogEntry> =
        listOf(BattleLogEntry(0, "Die Truppen nehmen ihre Aufstellung ein.")),
    val lastSounds: List<BattleSoundCue> = listOf(BattleSoundCue.HORN),
    val lootGold: Int = 0,
    val lootFood: Int = 0,
    val xpReward: Int = 0,
    val renownReward: Int = 0,
    /** Average equipment points worn by surviving deployed troops, not the whole pool. */
    val equipmentDamage: Int = 0,
    val commanderEvents: List<String> = emptyList(),
    val orderedRetreat: Boolean = false,
    val terrain: Map<BattleSection, BattleTerrain> = emptyMap(),
    val commandPoints: Int = 20,
    val maxCommandPoints: Int = 20,
    val participation: BattleParticipation = BattleParticipation.COMMAND,
    val casualties: CasualtyReport = CasualtyReport(),
    val replayStart: BattleReplayStart? = null,
    val inputs: List<BattleInput> = emptyList(),
    val heroPerk: Boolean = false,
    val reservePerk: Boolean = false,
    val feignedRetreatPerk: Boolean = false,
    val rallyPerk: Boolean = false,
    val moraleBonus: Int = 0,
    val rangedWeather: Double = 1.0,
    val cavalryWeather: Double = 1.0,
    val seasonPenalty: Double = 1.0,
    val healingBonus: Int = 0,
    val enemyFactionName: String? = null,
    val enemyUnits: List<UnitAllocation> = emptyList(),
    val location: String? = null,
    val enemyFactionId: String? = null,
    val enemyArmyName: String? = null,
    val enemyFortification: Int = 0,
    val deployedMorale: Int? = null,
    val enemyExperience: Int = 0,
    /** Fraction of the desired arrow load available when the battle started. */
    val rangedSupplyFactor: Double = 1.0,
) {
    val isActive: Boolean
        get() = status == BattleStatus.ACTIVE || status == BattleStatus.PURSUIT

    val ownRemaining: Int
        get() = contingents.sumOf { it.soldiers }

    val fightingRemaining: Int
        get() = contingents.filterNot { it.routed }.sumOf { it.soldiers }

    val enemyRemaining: Int
        get() = fronts.sumOf { it.enemySoldiers }

    val morale: Int
        get() =
            if (ownRemaining == 0) 0
            else (contingents.sumOf { it.soldiers.toLong() * it.morale } / ownRemaining).toInt()

    fun fighting(section: BattleSection): Int =
        contingents.filter { it.section == section && !it.routed }.sumOf { it.soldiers }

    fun soldiers(section: BattleSection): Int =
        contingents.filter { it.section == section }.sumOf { it.soldiers }
}
