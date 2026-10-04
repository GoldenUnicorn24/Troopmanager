package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Called exactly once by BattleEngine.finish, after the central medical report exists. */
object BattleConsequencesEngine {
    fun apply(state: GameState, battle: BattleSession): GameState {
        val grade = battle.outcomeGrade ?: return state
        val exhaustion = when (grade) {
            BattleOutcomeGrade.DECISIVE_VICTORY -> -3
            BattleOutcomeGrade.VICTORY -> 1
            BattleOutcomeGrade.COSTLY_VICTORY -> 12
            BattleOutcomeGrade.TACTICAL_WITHDRAWAL -> 4
            BattleOutcomeGrade.DEFEAT -> 10
            BattleOutcomeGrade.ROUT -> 16
            BattleOutcomeGrade.CRUSHING_DEFEAT -> 20
        }
        val satisfaction = when (grade) {
            BattleOutcomeGrade.DECISIVE_VICTORY -> 4; BattleOutcomeGrade.VICTORY -> 2
            BattleOutcomeGrade.COSTLY_VICTORY -> -4; BattleOutcomeGrade.TACTICAL_WITHDRAWAL -> -1
            else -> -6
        }
        val soldierLoyalty = when (grade) {
            BattleOutcomeGrade.DECISIVE_VICTORY -> 4; BattleOutcomeGrade.VICTORY -> 2
            BattleOutcomeGrade.COSTLY_VICTORY -> -5; BattleOutcomeGrade.TACTICAL_WITHDRAWAL -> -1
            else -> -6
        }
        var next = state.copy(society = state.society.copy(warExhaustion = (state.society.warExhaustion + exhaustion).coerceIn(0, 100),
            groups = state.society.groups.map { if (it.kind == PoliticalGroupKind.MILITARY)
                it.copy(loyalty = (it.loyalty + soldierLoyalty).coerceIn(0, 100)) else it }),
            city = state.city.copy(satisfaction = (state.city.satisfaction + satisfaction).coerceIn(0, 100)),
            armyPools = if (grade == BattleOutcomeGrade.COSTLY_VICTORY) state.armyPools.map { it.copy(morale = (it.morale - 5).coerceAtLeast(0)) } else state.armyPools)
        val shared = battle.contingents.any { it.commanderId == COMPANION_COMMANDER_ID && it.startSoldiers > 0 }
        val rescued = battle.contingents.any { it.commanderId == COMPANION_COMMANDER_ID && it.commanderRescued }
        if (state.companion.met && shared) {
            val trust = if (rescued) 4 else when (grade) {
                BattleOutcomeGrade.DECISIVE_VICTORY, BattleOutcomeGrade.VICTORY -> 2
                BattleOutcomeGrade.COSTLY_VICTORY, BattleOutcomeGrade.ROUT, BattleOutcomeGrade.CRUSHING_DEFEAT -> -2
                else -> 0
            }
            next = next.copy(companion = next.companion.copy(trust = (next.companion.trust + trust).coerceIn(0, 100),
                respect = (next.companion.respect + if (rescued || battle.orderedRetreat) 2 else 0).coerceAtMost(100)))
            next = RelationshipEngine.remember(next, "shared_battle", "${grade.label} bei ${battle.location ?: state.realm.settlementName}: gemeinsam geführt, ${battle.casualties.total} Ausfälle${if (rescued) ", Bergung aus der Front" else ""}.",
                if (rescued) 5 else 3, setOf("government", "war", "shared"))
        }
        val effects = listOf("Kriegsmüdigkeit ${signed(next.society.warExhaustion - state.society.warExhaustion)}",
            "Stadtzufriedenheit ${signed(next.city.satisfaction - state.city.satisfaction)}",
            "Soldatenloyalität ${signed((next.society.groups.firstOrNull { it.kind == PoliticalGroupKind.MILITARY }?.loyalty ?: 0) - (state.society.groups.firstOrNull { it.kind == PoliticalGroupKind.MILITARY }?.loyalty ?: 0))}") +
            if (shared) listOf("Vertrauen ${signed(next.companion.trust - state.companion.trust)}") else emptyList()
        val recordId = next.war.history.lastOrNull()?.id
        return next.copy(war = next.war.copy(history = next.war.history.map { if (it.id == recordId) it.copy(aftermath = effects) else it }),
            chronicle = (next.chronicle + ChronicleEntry(state.day, "Folgen: ${grade.label}", effects.joinToString(" · "))).takeLast(2000))
    }
    private fun signed(value: Int) = if (value >= 0) "+$value" else value.toString()
}
