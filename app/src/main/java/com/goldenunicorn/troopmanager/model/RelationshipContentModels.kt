package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompanionTrait(val label: String) {
    COMPASSIONATE("Mitfühlend"), PROUD("Stolz"), BRAVE("Mutig"), CAUTIOUS("Vorsichtig"),
    DIPLOMATIC("Diplomatisch"), MARTIAL("Kriegerisch"), PRAGMATIC("Pragmatisch"),
}

@Serializable
enum class CompanionPriority(val label: String) {
    SUPPLY("Versorgung"), EXPANSION("Expansion"), WOUNDED("Verwundete"), DEFENSE("Verteidigung"),
    DIPLOMACY("Diplomatie"), INTEGRATION("Integration"), TRADE("Handel"), FAMILY("Familie"), RECOGNITION("Anerkennung"),
}

@Serializable
data class CompanionPersonalityProfile(
    val traits: List<CompanionTrait> = listOf(CompanionTrait.COMPASSIONATE, CompanionTrait.PROUD, CompanionTrait.BRAVE, CompanionTrait.PRAGMATIC),
    val priorities: List<CompanionPriority> = listOf(CompanionPriority.WOUNDED, CompanionPriority.SUPPLY, CompanionPriority.DEFENSE),
    val personalGoals: List<String> = listOf("Die Grenzlande befrieden", "Ein verantwortliches eigenes Kommando führen", "Am Hof als eigenständige Stimme anerkannt werden"),
    val redLines: Set<String> = setOf("unnötige Kriege", "Verwundete vernachlässigen", "gebrochene Versprechen", "ihre Rolle dauerhaft ignorieren"),
)

@Serializable
data class RelationshipIssue(
    val id: String,
    val topic: String,
    val severity: Int,
    val startedDay: Int,
    val resolved: Boolean = false,
    val memoryIds: List<String> = emptyList(),
    val ignoredCount: Int = 0,
    val lastDiscussedDay: Int = startedDay,
)

@Serializable
data class RelationshipArcState(
    val id: String,
    val stage: Int = 0,
    val startedDay: Int,
    val nextDay: Int = startedDay,
    val completed: Boolean = false,
    val decisions: List<String> = emptyList(),
    val memoryIds: List<String> = emptyList(),
)

@Serializable
data class RelationshipEffect(
    val trust: Int = 0, val respect: Int = 0, val affection: Int = 0,
    val conflict: Int = 0, val loyalty: Int = 0, val satisfaction: Int = 0,
    val gold: Int = 0, val food: Int = 0, val tradeDays: Int = 0,
    val topic: String? = null, val issueSeverity: Int = 0,
)

@Serializable
data class RelationshipChoice(
    val id: String,
    val label: String,
    val reaction: String,
    val consequenceHint: String = "",
    val effect: RelationshipEffect = RelationshipEffect(),
    val priority: CompanionPriority? = null,
    val resolvesIssue: Boolean = false,
    val boundary: Boolean = false,
    val delayedEffect: RelationshipEffect? = null,
    val delayDays: Int = 0,
    val delayedText: String = "",
)

@Serializable
data class RelationshipDelayedConsequence(
    val id: String,
    val dueDay: Int,
    val text: String,
    val effect: RelationshipEffect,
    val sourceMemoryId: String,
)

/** Catalog content is immutable; only event/arc progress and cooldowns are persisted. */
data class RelationshipScenario(
    val key: String, val title: String, val text: String,
    val category: String, val topic: String, val family: String = category,
    val requirements: Set<String> = emptySet(),
    val weight: Int = 10, val cooldownDays: Int = 16,
    val preferredPriority: CompanionPriority? = null,
    val variants: List<String> = emptyList(),
    val options: List<RelationshipChoice>,
)

data class RelationshipArcDefinition(
    val id: String, val title: String, val description: String,
    val requirements: Set<String> = emptySet(),
    val stages: List<RelationshipScenario>,
    val stageIntervals: List<Int> = listOf(8, 10, 11),
)

data class RelationshipActivity(
    val id: String, val title: String, val description: String,
    val group: String = "Gemeinsame Zeit", val actionCost: Int = 1,
    val goldCost: Int = 0, val foodCost: Int = 0,
    val requirements: Set<String> = emptySet(),
    val seasons: Set<Season> = Season.entries.toSet(),
    val minimumTrust: Int = 0,
    val effect: RelationshipEffect = RelationshipEffect(),
    val tags: Set<String> = emptySet(),
)

data class RelationshipActivityAvailability(
    val activity: RelationshipActivity,
    val available: Boolean,
    val reason: String? = null,
)

fun derivedRelationshipMood(state: GameState): String = when {
    state.relationship.issues.any { !it.resolved && it.severity >= 60 } || state.relationship.conflict >= 65 -> "angespannt"
    state.relationship.conflict >= 35 -> "unsicher"
    state.companion.trust >= 85 && state.companion.respect >= 75 -> "sehr vertraut"
    state.companion.trust >= 65 && state.companion.affection >= 60 -> "verbunden"
    state.companion.trust >= 45 -> "stabil"
    else -> "vorsichtig zugewandt"
}
