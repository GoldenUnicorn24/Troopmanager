package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object BattleReportEngine {
    fun grade(battle: BattleSession, victory: Boolean): BattleOutcomeGrade {
        val losses = (battle.ownStart - battle.ownRemaining).toDouble() / battle.ownStart.coerceAtLeast(1)
        val initialWall = battle.replayStart?.realm?.wallIntegrity ?: 100
        return when {
            battle.orderedRetreat -> BattleOutcomeGrade.TACTICAL_WITHDRAWAL
            victory && (losses >= .25 || battle.tactic == Tactic.FORTIFY && initialWall - battle.wallIntegrity >= 35) -> BattleOutcomeGrade.COSTLY_VICTORY
            victory && losses <= .06 && battle.minute <= 45 -> BattleOutcomeGrade.DECISIVE_VICTORY
            victory -> BattleOutcomeGrade.VICTORY
            losses >= .70 -> BattleOutcomeGrade.CRUSHING_DEFEAT
            battle.fightingRemaining < battle.ownRemaining / 3 || battle.morale < 20 -> BattleOutcomeGrade.ROUT
            else -> BattleOutcomeGrade.DEFEAT
        }
    }

    /** Keeps recorded real formations and front counts in agreement after pursuit. */
    fun rosterAfterFrontLosses(roster: List<EnemyBattleUnit>, fronts: List<BattleFront>): List<EnemyBattleUnit> {
        val result = roster.toMutableList()
        fronts.forEach { front ->
            val indices = roster.indices.filter { roster[it].section == front.section }
            val units = indices.map { roster[it] }
            val removed = (units.sumOf { it.soldiers } - front.enemySoldiers).coerceAtLeast(0)
            val losses = BattleResolutionEngine.allocate(removed, units.map { it.soldiers }, units.map { it.soldiers.toDouble() })
            indices.forEachIndexed { index, original -> result[original] = units[index].copy(soldiers = units[index].soldiers - losses[index]) }
        }
        return result
    }
}
