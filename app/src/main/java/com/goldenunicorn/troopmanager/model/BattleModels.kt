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
}

/** A consumer can react to a newly persisted minute; no audio files are required. */
@Serializable
enum class BattleSoundCue {
    HORN,
    SWORDS,
    ARROWS,
    MONSTERS,
    WALL_BREAK,
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
) {
    val isActive: Boolean
        get() = status == BattleStatus.ACTIVE || status == BattleStatus.PURSUIT

    val ownRemaining: Int
        get() = contingents.sumOf { it.soldiers }

    val enemyRemaining: Int
        get() = fronts.sumOf { it.enemySoldiers }

    val morale: Int
        get() =
            if (ownRemaining == 0) 0
            else (contingents.sumOf { it.soldiers.toLong() * it.morale } / ownRemaining).toInt()

    fun soldiers(section: BattleSection): Int =
        contingents.filter { it.section == section }.sumOf { it.soldiers }
}
