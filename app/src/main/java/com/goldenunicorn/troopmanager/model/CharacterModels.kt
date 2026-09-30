package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

const val COMPANION_COMMANDER_ID: Long = -1L

@Serializable
data class RelationshipState(
    val actionDay: Int = 0,
    val spentActions: Int = 0,
    val pendingEvent: RelationshipEvent? = null,
)

@Serializable data class RelationshipEvent(val key: String, val title: String, val text: String)

fun CompanionProfile.relationshipStage(): String =
    when {
        trust >= 95 && respect >= 95 && affection >= 95 -> "Herrscherpaar"
        trust >= 85 && respect >= 80 && affection >= 80 -> "Mitregentin"
        trust >= 75 && affection >= 70 -> "Partner"
        trust >= 60 && affection >= 55 -> "Beziehung"
        trust >= 45 && respect >= 40 -> "Enge Gefährten"
        trust >= 30 -> "Freunde"
        else -> "Gefährten"
    }
