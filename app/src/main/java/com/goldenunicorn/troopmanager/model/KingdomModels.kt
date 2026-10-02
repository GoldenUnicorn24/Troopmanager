package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class TreatyKind(val label: String) {
    NON_AGGRESSION("Nichtangriff"), TRADE("Handelsvertrag"), MILITARY_ACCESS("Militärzugang"),
    DEFENSIVE_ALLIANCE("Verteidigungsbündnis"), OFFENSIVE_ALLIANCE("Offensivbündnis"),
    TRIBUTE("Tribut"), PROTECTION("Schutzvertrag"), VASSAL("Vasallentreue"),
    PEACE("Frieden"), DYNASTIC_ALLIANCE("Dynastisches Bündnis"),
}

@Serializable
data class DiplomaticMemory(val day: Int, val text: String)

@Serializable
data class DiplomacyRelation(
    val firstFactionId: String,
    val secondFactionId: String,
    val relation: Int = 0,
    val trust: Int = 35,
    val fear: Int = 10,
    val respect: Int = 30,
    val atWar: Boolean = false,
    val warStartedDay: Int? = null,
    val history: List<DiplomaticMemory> = emptyList(),
) {
    fun connects(first: String, second: String): Boolean =
        (firstFactionId == first && secondFactionId == second) ||
            (firstFactionId == second && secondFactionId == first)
}

@Serializable
data class Treaty(
    val id: String,
    val kind: TreatyKind,
    val firstFactionId: String,
    val secondFactionId: String,
    val signedDay: Int,
    val expiresDay: Int,
    val payerFactionId: String? = null,
    val tributeGold: Int = 0,
    val lastPaymentDay: Int = 0,
) {
    fun connects(first: String, second: String): Boolean =
        (firstFactionId == first && secondFactionId == second) ||
            (firstFactionId == second && secondFactionId == first)
}

@Serializable
enum class ProposalStatus { COUNTER_OFFER, ACCEPTED, DECLINED, EXPIRED }

@Serializable
data class DiplomaticProposal(
    val id: String,
    val targetFactionId: String,
    val kind: TreatyKind,
    val offered: Resources = Resources(0, 0, 0, 0, 0),
    val requested: Resources = Resources(0, 0, 0, 0, 0),
    val durationDays: Int = 60,
    val tributeGold: Int = 0,
    val playerPaysTribute: Boolean = true,
    val createdDay: Int,
    val expiresDay: Int,
    val status: ProposalStatus = ProposalStatus.COUNTER_OFFER,
    val explanation: String = "",
)

@Serializable
data class DiplomacyState(
    val initialized: Boolean = false,
    val relations: List<DiplomacyRelation> = emptyList(),
    val treaties: List<Treaty> = emptyList(),
    val proposals: List<DiplomaticProposal> = emptyList(),
    val lastTickDay: Int = 0,
    val lastAiDecisionDay: Int = 0,
    val nextId: Long = 1,
    val politics: List<FactionPolitics> = emptyList(),
)

@Serializable
data class FactionPolitics(
    val factionId: String,
    val stability: Int = 65,
    val warExhaustion: Int = 0,
    val hungerDays: Int = 0,
    val lastAgedDay: Int = 1,
    val lastCrisisDay: Int = 0,
    val cooldownUntilDay: Int = 0,
    val disputedSuccessionUntilDay: Int = 0,
    val history: List<DiplomaticMemory> = emptyList(),
)

@Serializable
enum class SpyMissionKind(val label: String, val cost: Int, val duration: Int) {
    SCOUT_ARMY("Armee auskundschaften", 90, 3), MAP_REGION("Region kartografieren", 80, 3),
    SABOTAGE_GATE("Befestigung sabotieren", 250, 5), DESTROY_STORES("Vorräte zerstören", 220, 5),
    SPREAD_RUMORS("Gerüchte verbreiten", 150, 4), UNDERMINE_LOYALTY("Loyalität schwächen", 200, 6),
    STEAL_TREATIES("Verträge ausspionieren", 120, 4), COUNTERINTELLIGENCE("Gegenaufklärung", 100, 4),
}

@Serializable
data class SpyAgent(
    val id: String,
    val name: String,
    val skill: Int = 40,
    val experience: Int = 0,
    val capturedByFactionId: String? = null,
)

@Serializable
data class SpyMission(
    val id: String,
    val agentId: String,
    val kind: SpyMissionKind,
    val targetFactionId: String,
    val targetProvinceId: String? = null,
    val startedDay: Int,
    val completesDay: Int,
    val difficulty: Int,
)

@Serializable
data class IntelligenceReport(
    val day: Int,
    val targetFactionId: String,
    val kind: SpyMissionKind,
    val success: Boolean,
    val text: String,
)

@Serializable
data class EspionageState(
    val agents: List<SpyAgent> = emptyList(),
    val missions: List<SpyMission> = emptyList(),
    val reports: List<IntelligenceReport> = emptyList(),
    val counterintelligenceUntilDay: Int = 0,
    val knownTreatyIds: List<String> = emptyList(),
    val lastTickDay: Int = 0,
    val nextId: Long = 1,
)

@Serializable
enum class PoliticalGroupKind(val label: String) {
    MILITARY("Militär"), MERCHANTS("Händler"), NOBILITY("Adel"), FARMERS("Bauern"),
    CULTURAL_REPRESENTATIVES("Kultureller Rat"),
}

@Serializable
enum class PoliticalDemandKind(val label: String, val cost: Int) {
    SOLDIER_PAY("Sold ausgleichen", 300), PROTECT_TRADE("Handelswachen finanzieren", 220),
    LOWER_TAXES("Steuern senken", 0), FOOD_RELIEF("Nahrungshilfe", 400),
    CULTURAL_COUNCIL("Kulturellen Rat einsetzen", 180),
}

@Serializable
data class PoliticalDemand(
    val id: String,
    val group: PoliticalGroupKind,
    val kind: PoliticalDemandKind,
    val createdDay: Int,
    val deadlineDay: Int,
)

@Serializable
data class PoliticalGroup(
    val kind: PoliticalGroupKind,
    val loyalty: Int = 60,
    val influence: Int = 20,
    val lastDemandDay: Int = 0,
)

@Serializable
enum class SocietyPolicy(val label: String) {
    PATROLS("Stadtwachen verstärken"), FAIR_WAGES("Faire Löhne"), CLINICS("Heiler finanzieren"),
    FOOD_RELIEF("Nahrung verteilen"), INTEGRATION("Kulturfest & Integration"),
}

@Serializable
enum class StoryKind { REFUGEES, REFUGEE_HOUSING, REFUGEE_COUNCIL, CORRUPTION, LOYALTY_CRISIS, BORDER_MEDIATION }

@Serializable
data class StoryEvent(
    val id: String,
    val kind: StoryKind,
    val title: String,
    val text: String,
    val createdDay: Int,
    val expiresDay: Int,
    val chainId: String? = null,
)

@Serializable
data class ScheduledStory(val kind: StoryKind, val dueDay: Int, val chainId: String)

@Serializable
data class StoryState(
    val pending: StoryEvent? = null,
    val scheduled: List<ScheduledStory> = emptyList(),
    val cooldownUntilDay: Int = 0,
    val lastTickDay: Int = 0,
    val nextId: Long = 1,
    val resolved: List<String> = emptyList(),
)

@Serializable
data class SocietyState(
    val crime: Int = 10,
    val inequality: Int = 20,
    val disease: Int = 0,
    val hunger: Int = 0,
    val culturalTension: Int = 10,
    val politicalLoyalty: Int = 60,
    val warExhaustion: Int = 0,
    val lastMigration: Int = 0,
    val totalImmigrants: Int = 0,
    val totalEmigrants: Int = 0,
    val groups: List<PoliticalGroup> = PoliticalGroupKind.entries.map { PoliticalGroup(it) },
    val demands: List<PoliticalDemand> = emptyList(),
    val policyUntilDay: Map<SocietyPolicy, Int> = emptyMap(),
    val story: StoryState = StoryState(),
    val lastTickDay: Int = 0,
)
