package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class CampaignFocus(val label: String, val description: String) {
    BALANCED("Ausgewogen", "Keine harte Spezialisierung. Das Reich reagiert flexibel."),
    PROSPERITY("Wohlstand", "Markt und Verwaltung drücken stärker auf laufende Einnahmen."),
    DEFENSE("Verteidigung", "Sicherheit, Bereitschaft und Grenzdruck stehen im Mittelpunkt."),
    PEOPLE("Volk", "Zufriedenheit und gesellschaftliche Stabilität haben Vorrang."),
    RULING_PAIR("Herrscherpaar", "Gemeinsame Regierung und Konfliktabbau werden priorisiert."),
    EXPANSION("Expansion", "Ruhm, Initiative und Außenwirkung werden stärker gewichtet."),
}

@Serializable
enum class CampaignDecisionKind {
    FOOD_SHORTAGE,
    BORDER_CRISIS,
    PUBLIC_UNREST,
    RULING_PAIR_TENSION,
    OPPORTUNITY,
}

@Serializable
data class CampaignDecision(
    val id: String,
    val kind: CampaignDecisionKind,
    val title: String,
    val text: String,
    val createdDay: Int,
    val expiresDay: Int,
    val intensity: Int,
)

@Serializable
data class CampaignPulseState(
    val momentum: Int = 50,
    val pressure: Int = 20,
    val focus: CampaignFocus = CampaignFocus.BALANCED,
    val pendingDecision: CampaignDecision? = null,
    val lastDecisionDay: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val prosperityDays: Int = 0,
    val supplyReliefDays: Int = 0,
    val defenseReadinessDays: Int = 0,
    val recentOutcomes: List<String> = emptyList(),
)
