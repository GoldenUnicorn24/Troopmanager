package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

/*
 * v1.3: acts with boss battles, rival kings, celebration moments, campaign events and drawn portraits.
 * All additive with safe defaults.
 */

/** Drawn portrait recipe. Indices point into small palettes/styles in the UI, so the save stays tiny. */
@Serializable
data class PortraitSpec(
    val skin: Int = 1,
    val hair: Int = 0,
    val hairColor: Int = 0,
    val eyes: Int = 0,
    val elfEars: Boolean = false,
    val beard: Int = 0,
    val armor: Int = 0,
    val armorColor: Int = 0,
    val marking: Int = 0,
    val background: Int = 0,
)

@Serializable
enum class CelebrationKind { TITLE, MILESTONE, HEROINE, ACT, BOSS, KING, WEDDING, CORONATION, WONDER, FEAST, ACHIEVEMENT, VICTORY }

@Serializable
data class Celebration(val kind: CelebrationKind, val title: String, val text: String, val day: Int)

@Serializable
enum class RivalStatus(val label: String) {
    HOSTILE("Feindselig"), NEUTRAL("Abwartend"), ALLIED("Verbündet"), DEFEATED("Besiegt"),
}

@Serializable
data class RivalState(
    val id: String,
    val attitude: Int = 0,
    val status: RivalStatus = RivalStatus.NEUTRAL,
    val lastEventDay: Int = 0,
    val events: Int = 0,
)

@Serializable
data class ActState(
    val act: Int = 1,
    val startedDay: Int = 1,
    val bossAnnounced: Boolean = false,
    val bossRetryDay: Int = 0,
    val bossBattleActive: Boolean = false,
    val bossAttempts: Int = 0,
)

/** A campaign that met an event on the march; it resolves once the player has chosen. */
@Serializable
data class PendingMarch(
    val provinceId: String,
    val share: Int,
    val ratioFactor: Double = 1.0,
    val lossFactor: Double = 1.0,
    val companionRisk: Boolean = false,
)
