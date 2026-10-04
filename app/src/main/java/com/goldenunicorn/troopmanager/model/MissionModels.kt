package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class MissionStatus {
    ACTIVE,
    RETURNING,
    COMPLETE,
    FAILED;

    val isAway: Boolean
        get() = this == ACTIVE || this == RETURNING
}

@Serializable
enum class MissionOutcome(val label: String) {
    GREAT_SUCCESS("Großer Erfolg"),
    SUCCESS("Erfolg"),
    PARTIAL("Teilerfolg"),
    FAILED("Gescheitert"),
    CATASTROPHIC("Katastrophal"),
}

@Serializable
enum class MissionPhase { OUTBOUND, OPERATING, RETURNING, DONE }

@Serializable
enum class MissionDecisionKind { ROUTE, OPERATION, EXTRACTION }

@Serializable
data class MissionDecision(
    val title: String,
    val text: String,
    val options: List<String>,
    val kind: MissionDecisionKind = MissionDecisionKind.ROUTE,
)

@Serializable
data class ActiveMission(
    val id: Long,
    val missionType: MissionType,
    val commanderId: Long?,
    val units: List<UnitAllocation>,
    val startDay: Int,
    val remainingDays: Int,
    val supplyCost: Int,
    val duration: Int,
    val status: MissionStatus = MissionStatus.ACTIVE,
    val outcome: MissionOutcome? = null,
    val losses: Int = 0,
    val reward: Resources = Resources(0, 0, 0, 0, 0),
    val renownReward: Int = 0,
    val xpReward: Int = 0,
    val regionId: String? = null,
    val quality: List<ArmyUnitPool> = emptyList(),
    val phase: MissionPhase = MissionPhase.OPERATING,
    val operationDaysRemaining: Int = 0,
    val pendingDecision: MissionDecision? = null,
    val routeDecisionMade: Boolean = false,
    val riskFactor: Double = 1.0,
    val originalTotal: Int = 0,
    val lastTickDay: Int = 0,
    /** All NPC commanders temporarily attached to this mission. commanderId remains the legacy/primary leader. */
    val commanderIds: List<Long> = emptyList(),
    /** The ruler can personally accompany the mission and counts as one of at most three leaders. */
    val playerParticipates: Boolean = false,
    /** v0.61.3 separates permanent deaths from temporary medical casualties. */
    val dead: Int = 0,
    val wounded: Int = 0,
    /** Expected day on which the last wounded cohort from this mission is fit for duty. */
    val woundedRecoveryDay: Int = 0,
    val experienceVersion: Int = 1,
    val operationDecisionMade: Boolean = false,
    val extractionDecisionMade: Boolean = false,
    val rewardFactor: Double = 1.0,

) {
    val total: Int
        get() = units.sumOf { it.amount }

    /** Old saves stored all mission casualties only in losses; treat those as fallen in reports. */
    val reportedDead: Int
        get() = if (dead == 0 && wounded == 0 && losses > 0) losses else dead

    val reportedWounded: Int
        get() = wounded

    val deployedTotal: Int
        get() = if (originalTotal > 0) originalTotal else total + losses

    val readyReturned: Int
        get() = (deployedTotal - reportedDead - reportedWounded).coerceAtLeast(0)

    /** Backward compatible view: older saves only stored commanderId. */
    val allCommanderIds: List<Long>
        get() = (commanderIds + listOfNotNull(commanderId)).distinct()
}

data class MissionSpec(
    val days: Int,
    val minimum: Int,
    val recommended: Int,
    val risk: String,
    val difficulty: Int,
)

fun MissionType.spec(): MissionSpec =
    when (this) {
        MissionType.PATROL -> MissionSpec(1, 50, 150, "Niedrig", 75)
        MissionType.ESCORT -> MissionSpec(2, 100, 250, "Niedrig", 150)
        MissionType.BANDITS -> MissionSpec(3, 200, 400, "Mittel", 280)
        MissionType.HUNT -> MissionSpec(4, 150, 350, "Hoch", 330)
        MissionType.RELIEF -> MissionSpec(3, 300, 600, "Mittel", 400)
        MissionType.SCOUT -> MissionSpec(2, 30, 100, "Niedrig", 55)
    }
