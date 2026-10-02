package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

const val COMPANION_COMMANDER_ID: Long = -1L

@Serializable
data class RelationshipState(
    val actionDay: Int = 0,
    val spentActions: Int = 0,
    val pendingEvent: RelationshipEvent? = null,
    val romanceStage: RomanceStage = RomanceStage.NONE,
    val attraction: Int = 0,
    val intimacy: Int = 0,
    val loyalty: Int = 70,
    val conflict: Int = 0,
    val jealousy: Int = 0,
    val commitment: Int = 0,
    val consent: ConsentProfile = ConsentProfile(),
    val memories: List<RelationshipMemory> = emptyList(),
    val lastRomanceDay: Int = -100,
    val lastIntimacyDay: Int = -100,
    val intimacyConsentDay: Int? = null,
    val lastAutonomyDay: Int = 0,
    val politicalOpinion: String = "Eine sichere Grenze und versorgte Bevölkerung.",
)

@Serializable
data class RelationshipEvent(
    val key: String,
    val title: String,
    val text: String,
    val adultsOnly: Boolean = false,
    val consentRequired: Boolean = false,
    val presentation: String = "DIALOGUE",
)

@Serializable
enum class RomanceStage(val label: String) {
    NONE("Freundschaft"),
    INTEREST("Gegenseitiges Interesse"),
    ROMANCE("Romanze"),
    PARTNERSHIP("Feste Partnerschaft"),
    ENGAGED("Verlobt"),
    MARRIED("Ehe / Lebenspartnerschaft"),
    CO_RULERS("Herrscherpaar"),
}

@Serializable
enum class RelationshipStyle(val label: String) {
    FRIENDSHIP("Freundschaft"), MONOGAMOUS("Treue Partnerschaft"), OPEN("Einvernehmlich offen"),
}

/** Permission to consider a proposal never substitutes for consent to that proposal. */
@Serializable
data class ConsentProfile(
    val romanceAllowed: Boolean = true,
    val intimacyAllowed: Boolean = false,
    val boundaries: Set<String> = setOf("no_coercion", "privacy", "ask_each_time"),
    val relationshipStyle: RelationshipStyle = RelationshipStyle.MONOGAMOUS,
    val wantsChildren: Boolean = false,
    val privacyLevel: Int = 80,
)

@Serializable
data class RelationshipMemory(
    val id: String,
    val day: Int,
    val type: String,
    val participants: List<String> = listOf("player", "companion"),
    val emotionalWeight: Int = 1,
    val text: String = "",
    val tags: Set<String> = emptySet(),
    val location: String? = null,
    val battleId: Long? = null,
    val eventId: String? = null,
)

/** A high friendship score cannot establish a romantic relationship. */
fun CompanionProfile.relationshipStage(): String =
    when {
        trust >= 85 && respect >= 75 -> "Vertrauensperson"
        trust >= 60 && respect >= 50 -> "Enge Freunde"
        trust >= 45 && respect >= 40 -> "Freunde"
        trust >= 30 -> "Freunde"
        trust >= 15 -> "Bekannte"
        else -> "Gefährten"
    }

fun GameState.relationshipStage(): String =
    if (relationship.romanceStage == RomanceStage.NONE) companion.relationshipStage()
    else relationship.romanceStage.label
