package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class CoRulerPortfolio(val label: String) {
    DIPLOMACY("Diplomatie"), INTERIOR("Innere Angelegenheiten"), MILITARY("Militär"),
    ECONOMY("Wirtschaft"), COURT("Hof"),
}

@Serializable
enum class GovernmentStance(val label: String) {
    CAUTIOUS("Vorsichtig"), BALANCED("Ausgewogen"), ASSERTIVE("Entschlossen"),
}

@Serializable
enum class GovernmentFocus(val label: String) {
    SOCIAL("Sozial"), FISCAL("Fiskalisch"), MILITARY("Militärisch"),
}

/** This allowlist intentionally has no declaration of war, offensive expedition or execution. */
@Serializable
enum class DelegatedAction(val label: String) {
    SUPPLY_PURCHASE("Versorgung einkaufen"), HUMANITARIAN_AID("Versorgung und Heilkunde"),
    DIPLOMATIC_CONTACT("Handelskontakte"), DEFENSIVE_REPAIR("Defensive Instandsetzung"),
    COURT_MEDIATION("Hof vermitteln"), CIVIL_ADMINISTRATION("Innere Verwaltung"),
}

@Serializable
data class DelegationPolicy(
    val enabled: Boolean = false,
    val allowedActions: Set<DelegatedAction> = emptySet(),
    val maxGoldPerAction: Int = 250,
    val maxGoldPerDay: Int = 500,
    val stance: GovernmentStance = GovernmentStance.BALANCED,
    val focus: GovernmentFocus = GovernmentFocus.SOCIAL,
)

@Serializable
data class GovernmentDecisionRecord(
    val caseId: String,
    val day: Int,
    val title: String,
    val choice: String,
    val actor: String,
    val reason: String,
    val autonomous: Boolean = false,
    val goldSpent: Int = 0,
    val efficiency: Int = 100,
)

@Serializable
data class CoRulerState(
    val portfolio: CoRulerPortfolio = CoRulerPortfolio.INTERIOR,
    val delegation: DelegationPolicy = DelegationPolicy(),
    val pendingCaseIds: List<String> = emptyList(),
    val agendaDay: Int = 0,
    val resolvedDays: Map<String, Int> = emptyMap(),
    val decisions: List<GovernmentDecisionRecord> = emptyList(),
    val lastTickDay: Int = 0,
    val spendingDay: Int = 0,
    val goldSpentToday: Int = 0,
    val actingRuler: String = "Leon",
    val regencyEfficiency: Int = 100,
    val disrespectfulOverrides: Int = 0,
)

@Serializable
enum class PresenceLocation(val label: String) {
    PALACE("Palast"), CITY("Stadt"), WALL("Mauer"), FIELD_ARMY("Feldheer"),
    MISSION("Mission"), HOSPITAL("Lazarett"), CAPTIVITY("Gefangenschaft"),
    TRAVEL("Reise"), DEAD("Verstorben"),
}

@Serializable
data class PersonPresence(
    val location: PresenceLocation = PresenceLocation.PALACE,
    val detail: String = "",
    val available: Boolean = true,
    val returnDay: Int? = null,
    val bindingId: String? = null,
)

@Serializable
data class PresenceState(
    val player: PersonPresence = PersonPresence(),
    val companion: PersonPresence = PersonPresence(),
    val lastTickDay: Int = 0,
)

data class RegencyStatus(val actor: String, val efficiency: Int, val reason: String, val companionActing: Boolean = false)

enum class CouncilCaseKind(val label: String) { GOVERNMENT("Reichsentscheidung"), COURT_CONFLICT("Hofkonflikt") }

data class CouncilEffects(
    val satisfaction: Int = 0, val security: Int = 0, val prosperity: Int = 0,
    val morale: Int = 0, val loyalty: Int = 0, val culturalTension: Int = 0,
    val occupationUnrest: Int = 0, val warExhaustion: Int = 0,
    val taxLevel: TaxLevel? = null, val priority: WorkerPriority? = null,
    val wallRepair: Int = 0, val healingDays: Int = 0, val fieldSupply: Int = 0,
    val diplomaticRelation: Int = 0, val companionRespect: Int = 0,
    val cultureStanding: Int = 0, val cultureIntegration: Int = 0,
    val cultureTarget: Culture? = null,
)

data class CouncilOption(
    val id: String, val label: String, val explanation: String,
    val cost: Resources = Resources(0, 0, 0, 0, 0),
    val reward: Resources = Resources(0, 0, 0, 0, 0),
    val effects: CouncilEffects = CouncilEffects(),
    val delegationAction: DelegatedAction? = null,
)

data class CouncilRecommendation(val member: String, val role: String, val optionId: String, val reason: String)

data class CouncilCase(
    val id: String, val kind: CouncilCaseKind, val portfolio: CoRulerPortfolio,
    val title: String, val situation: String, val options: List<CouncilOption>,
    val recommendations: List<CouncilRecommendation> = emptyList(),
)
