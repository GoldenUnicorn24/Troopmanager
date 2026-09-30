package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.GameState

fun customizePlayer(
    state: GameState,
    name: String,
    age: Int,
    armorStyle: String,
    weapon: String
): GameState = state.copy(
    player = state.player.copy(
        name = name.ifBlank { state.player.name },
        age = age.coerceIn(18, 120),
        armorStyle = armorStyle.ifBlank { state.player.armorStyle },
        weapon = weapon.ifBlank { state.player.weapon }
    )
)

fun customizeCompanion(
    state: GameState,
    name: String,
    age: Int,
    armorStyle: String,
    weapon: String
): GameState = RelationshipEngine.syncCommander(state.copy(
    companion = state.companion.copy(
        name = name.ifBlank { state.companion.name },
        age = age.coerceIn(18, 120),
        armorStyle = armorStyle.ifBlank { state.companion.armorStyle },
        weapon = weapon.ifBlank { state.companion.weapon }
    )
))

fun renameSettlement(state: GameState, name: String): GameState {
    val cleaned = name.trim().take(28)
    return if (cleaned.isBlank()) state else state.copy(realm = state.realm.copy(customSettlementName = cleaned))
}


fun customizeCommanderPortrait(state: GameState, id: Long, portraitUri: String?): GameState =
    if (id == com.goldenunicorn.troopmanager.model.COMPANION_COMMANDER_ID)
        RelationshipEngine.syncCommander(state.copy(companion = state.companion.copy(portraitUri = portraitUri)))
    else state.copy(commanders = state.commanders.map { if (it.id == id) it.copy(portraitUri = portraitUri) else it })
