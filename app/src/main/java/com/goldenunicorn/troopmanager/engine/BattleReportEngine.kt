package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object BattleReportEngine {
    data class PhaseLoss(val label: String, val own: Int, val enemy: Int)

    fun enemyCasualties(battle: BattleSession): CasualtyReport {
        val lost = battle.enemyStart - battle.enemyRemaining
        val captured = if (battle.status == BattleStatus.VICTORY) lost /
            if (battle.inputs.lastOrNull()?.decision == BattleDecision.PURSUE) 8 else 15 else 0
        val wounded = (lost - captured) * 30 / 100
        return CasualtyReport(dead = lost - captured - wounded, wounded = wounded, captured = captured)
    }

    fun lossesByPhase(battle: BattleSession): List<PhaseLoss> {
        val fronts = battle.exchanges.flatMap { it.fronts }
        fun melee(states: Set<BattleContactState>) = PhaseLoss("", fronts.filter { it.contactState in states }.sumOf { it.ownDamage.melee },
            fronts.filter { it.contactState in states }.sumOf { it.enemyDamage.melee })
        return listOf(
            PhaseLoss("Fernkampf", fronts.sumOf { it.ownDamage.ranged }, fronts.sumOf { it.enemyDamage.ranged }),
            melee(setOf(BattleContactState.WALL_ASSAULT)).copy(label = "Mauerangriff"),
            melee(setOf(BattleContactState.FIELD_CONTACT)).copy(label = "Nahkampf im Feld"),
            PhaseLoss("Artillerie / Mauerwaffen", fronts.sumOf { it.ownDamage.splash }, fronts.sumOf { it.enemyDamage.splash + it.enemyDamage.wallWeapons }),
            melee(setOf(BattleContactState.BREACHED, BattleContactState.COURTYARD)).copy(label = "Bresche / Innenhof"),
            PhaseLoss("Rückzug / Verfolgung", (battle.ownStart - battle.ownRemaining - fronts.sumOf { it.ownDamage.total }).coerceAtLeast(0),
                (battle.enemyStart - battle.enemyRemaining - fronts.sumOf { it.enemyDamage.total }).coerceAtLeast(0)),
        )
    }

    fun tacticEffect(battle: BattleSession): String {
        val bonus = when (battle.plan.doctrine) {
            BattleDoctrine.RANGED_SUPERIORITY -> "+18% Fernkampfkraft"
            BattleDoctrine.KILL_ZONE -> "+40% Fernkampfkraft ab 110 m"
            BattleDoctrine.PRESERVE_TROOPS -> "−10% Fernkampfkraft, geschonte Truppen"
            BattleDoctrine.COUNTERATTACK -> "+20% Nahkampfkraft, schwächere Defensive"
            else -> "gedeckte Verteidigung"
        }
        val suppression = battle.exchanges.flatMap { it.fronts }.sumOf { it.suppression }
        return "${battle.plan.doctrine.label}: $bonus · Feuerrate ×${battle.plan.ammunitionPolicy.fireRate} · Pfeilverbrauch ×${battle.plan.ammunitionPolicy.consumption} · $suppression Unterdrückung erzeugt."
    }
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
