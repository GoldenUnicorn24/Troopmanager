package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** War objectives refer to existing territory, agreements and armies; they create no rewards or units. */
object WarGoalsEngine {
    fun set(state: GameState, enemyId: String, goal: WarGoal, regionId: String? = null, allyId: String? = null): GameEngine.ActionResult {
        val relation = DiplomacyEngine.relation(state, PLAYER_FACTION, enemyId)
        if (!relation.atWar || state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Ein Kriegsziel kann nur außerhalb einer laufenden Schlacht im Krieg festgelegt werden.")
        if (goal == WarGoal.SECURE_REGION) {
            val region = state.world.place(regionId ?: "")
            if (region == null || region.ownerId != enemyId || region.id !in state.world.knowledgeFor(PLAYER_FACTION).exploredRegions)
                return GameEngine.ActionResult(state, "Wähle eine bekannte Region dieses Gegners.")
        }
        if (goal == WarGoal.DEFEND_ALLY && (allyId == null || allyId == enemyId ||
                !DiplomacyEngine.hasTreaty(state, PLAYER_FACTION, allyId, TreatyKind.DEFENSIVE_ALLIANCE) ||
                !DiplomacyEngine.atWar(state, allyId, enemyId)))
            return GameEngine.ActionResult(state, "Der Verbündete braucht ein gültiges Verteidigungsbündnis und muss gegen diesen Gegner kämpfen.")
        val updated = relation.copy(playerWarGoal = goal,
            warGoalRegionId = regionId.takeIf { goal == WarGoal.SECURE_REGION },
            warGoalAllyId = allyId.takeIf { goal == WarGoal.DEFEND_ALLY },
            history = (relation.history + DiplomaticMemory(state.day, "Kriegsziel: ${goal.label}.")).takeLast(40))
        return GameEngine.ActionResult(state.copy(diplomacy = state.diplomacy.copy(relations =
            state.diplomacy.relations.filterNot { it.connects(PLAYER_FACTION, enemyId) } + updated)), "Kriegsziel festgelegt: ${goal.label}.")
    }

    fun progress(state: GameState, enemyId: String): String {
        val r = DiplomacyEngine.relation(state, PLAYER_FACTION, enemyId)
        return when (r.playerWarGoal) {
            WarGoal.SECURE_REGION -> state.world.place(r.warGoalRegionId ?: "")?.let {
                "${it.name}: ${if (it.ownerId == PLAYER_FACTION) "gesichert" else "noch nicht unter unserer Kontrolle"}"
            } ?: "Zielregion nicht festgelegt"
            WarGoal.TRIBUTE -> if (state.diplomacy.treaties.any { it.connects(PLAYER_FACTION, enemyId) &&
                    it.kind in listOf(TreatyKind.TRIBUTE, TreatyKind.VASSAL, TreatyKind.PROTECTION) &&
                    it.payerFactionId == enemyId && it.tributeGold > 0 && it.expiresDay > state.day })
                "Tribut vereinbart; Zahlungen alle sieben Tage" else "Zuerst Frieden und einen zahlungspflichtigen Vertrag aushandeln"
            WarGoal.FORCE_PEACE -> if (!r.atWar) "Frieden geschlossen" else "Krieg dauert an; Verluste und Erschöpfung beeinflussen Verhandlungen"
            WarGoal.DEFEND_ALLY -> {
                val ally = r.warGoalAllyId ?: return "Verbündeten wählen"
                val name = state.world.faction(ally)?.name ?: ally
                if (!DiplomacyEngine.atWar(state, ally, enemyId)) "$name: Krieg beendet"
                else {
                    val intruders = state.world.knowledgeFor(PLAYER_FACTION).observations.count { it.factionId == enemyId && state.day - it.day <= 1 && state.world.place(it.regionId)?.ownerId == ally }
                    "$name: $intruders bekannte gegnerische Heere im eigenen Gebiet; Krieg noch offen"
                }
            }
            null -> "Noch kein eigenes Kriegsziel"
        }
    }

    /** The target knows its own casualties and exhaustion; no hidden player army is consulted. */
    fun peacePressure(state: GameState, enemyId: String): Int {
        val r = DiplomacyEngine.relation(state, PLAYER_FACTION, enemyId)
        if (!r.atWar) return 0
        val lost = r.warLosses[enemyId] ?: 0
        val enemyPopulation = state.world.faction(enemyId)?.population ?: 1
        val losses = (lost.toLong() * 80 / (enemyPopulation.toLong() + lost).coerceAtLeast(1)).coerceAtMost(30).toInt()
        val exhaustion = (state.diplomacy.politics.firstOrNull { it.factionId == enemyId }?.warExhaustion ?: 0) / 4
        val occupation = if (r.playerWarGoal == WarGoal.SECURE_REGION && state.world.place(r.warGoalRegionId ?: "")?.ownerId == PLAYER_FACTION) 12 else 0
        return (losses + exhaustion + occupation - state.society.warExhaustion / 6).coerceIn(-16, 60)
    }

    /** Only the finish path calls this, once per actual battle. */
    fun recordBattle(state: GameState, battle: BattleSession): GameState {
        val enemyId = battle.enemyFactionId ?: return state
        val r = DiplomacyEngine.relation(state, PLAYER_FACTION, enemyId)
        if (!r.atWar) return state
        val enemyLost = (battle.enemyStart - battle.enemyRemaining).coerceAtLeast(0)
        fun total(id: String, losses: Int) = ((r.warLosses[id] ?: 0).toLong() + losses).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val memory = DiplomaticMemory(state.day, "${battle.outcomeGrade?.label ?: if (battle.status == BattleStatus.VICTORY) "Sieg" else "Niederlage"}: ${battle.casualties.total} eigene und $enemyLost gegnerische Ausfälle bei ${battle.location ?: state.realm.settlementName}.")
        val updated = r.copy(warLosses = r.warLosses + mapOf(PLAYER_FACTION to total(PLAYER_FACTION, battle.casualties.total), enemyId to total(enemyId, enemyLost)),
            respect = (r.respect + if (battle.status == BattleStatus.VICTORY) 4 else -2).coerceIn(0, 100),
            fear = (r.fear + if (battle.status == BattleStatus.VICTORY) 5 else -3).coerceIn(0, 100),
            history = (r.history + memory).takeLast(40))
        return state.copy(diplomacy = state.diplomacy.copy(relations = state.diplomacy.relations.filterNot { it.connects(PLAYER_FACTION, enemyId) } + updated,
            politics = state.diplomacy.politics.map { if (it.factionId == enemyId) it.copy(warExhaustion =
                (it.warExhaustion + if (enemyLost > 0) 4 + (enemyLost.toLong() * 20 / battle.enemyStart.coerceAtLeast(1)).toInt() else 0).coerceAtMost(100)) else it }))
    }
}
