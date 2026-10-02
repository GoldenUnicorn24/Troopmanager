package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.engine.PresenceEngine
import com.goldenunicorn.troopmanager.model.*

internal data class PalacePresentation(
    val title: String,
    val playerPresent: Boolean,
    val companionPresent: Boolean,
    val sharedInsignia: Boolean,
    val jointCourt: Boolean,
    val crisis: Boolean,
    val celebration: Boolean,
)

/** Presentation follows explicit relationship stages and live military/health bindings. */
internal fun palacePresentation(state: GameState): PalacePresentation {
    val presence = PresenceEngine.presence(state)
    val stage = state.relationship.romanceStage
    val celebration = state.relationship.memories.any {
        it.day in (state.day - 1)..state.day &&
            (it.type in setOf("marry", "co_ruler", "feast", "victory_celebration") ||
                it.tags.any { tag -> tag in setOf("festival", "celebration") })
    }
    return PalacePresentation(
        title = when (stage) {
            RomanceStage.CO_RULERS -> "Das Herrscherpaar"
            RomanceStage.MARRIED -> "Gemeinsames Haus"
            RomanceStage.PARTNERSHIP, RomanceStage.ENGAGED -> "Ein gemeinsamer Weg"
            else -> "Der Herrscherhof"
        },
        playerPresent = presence.player.location == PresenceLocation.PALACE && presence.player.available,
        companionPresent = state.companion.met && presence.companion.location == PresenceLocation.PALACE && presence.companion.available,
        sharedInsignia = stage >= RomanceStage.MARRIED,
        jointCourt = stage == RomanceStage.CO_RULERS,
        crisis = cityInDefense(state) || state.resources.food == 0 || state.city.satisfaction < 35 || state.city.security < 30,
        celebration = celebration,
    )
}
