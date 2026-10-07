package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object BattleReportEngine {
    data class PhaseLoss(val label: String, val own: Int, val enemy: Int)

    fun addCauses(previous: List<BattleCauseBreakdown>, added: List<BattleCauseBreakdown>): List<BattleCauseBreakdown> =
        (previous + added).groupBy { Triple(it.section, it.type, it.cause) }.map { (key, rows) ->
            val own = rows.sumOf { it.own.toLong() }
            val enemy = rows.sumOf { it.enemy.toLong() }
            require(own in 0..Int.MAX_VALUE.toLong() && enemy in 0..Int.MAX_VALUE.toLong())
            BattleCauseBreakdown(key.first, key.second, key.third, own.toInt(), enemy.toInt())
        }.filter { it.own + it.enemy.toLong() > 0 }

    /** A source budget is distributed across real lost formations with exact conservation. */
    fun causes(section: BattleSection, units: List<Pair<UnitType, Int>>, damage: BattleDamageSources,
        enemy: Boolean, contact: BattleContactState): List<BattleCauseBreakdown> {
        val remaining = units.map { it.second }.toMutableList()
        require(remaining.sumOf { it.toLong() } == damage.total.toLong())
        val budgets = linkedMapOf(BattleDamageCause.ARROWS to damage.ranged,
            BattleDamageCause.ARTILLERY to damage.artillery + damage.splash,
            BattleDamageCause.WALL_WEAPONS to damage.wallWeapons,
            (if (contact in listOf(BattleContactState.BREACHED, BattleContactState.COURTYARD)) BattleDamageCause.BREACH else BattleDamageCause.MELEE) to damage.melee,
            BattleDamageCause.PURSUIT to damage.pursuit, BattleDamageCause.COLLAPSE to damage.collapse)
        return budgets.flatMap { (cause, budget) ->
            val allocated = BattleResolutionEngine.allocate(budget, remaining, remaining.map { it.toDouble() })
            allocated.mapIndexedNotNull { index, count ->
                remaining[index] -= count
                if (count == 0) null else BattleCauseBreakdown(section, units[index].first, cause,
                    own = if (enemy) 0 else count, enemy = if (enemy) count else 0)
            }
        }
    }

    fun finalExchange(state: GameState, original: BattleSession, troops: List<BattleContingent>, fronts: List<BattleFront>): BattleSession {
        val roster = rosterAfterFrontLosses(original.enemyRoster, fronts)
        val causes = mutableListOf<BattleCauseBreakdown>()
        val reports = BattleSection.entries.map { section ->
            val own = original.contingents.indices.filter { original.contingents[it].section == section }.map {
                original.contingents[it].type to (original.contingents[it].soldiers - troops[it].soldiers)
            }
            val enemy = original.enemyRoster.indices.filter { original.enemyRoster[it].section == section }.map {
                original.enemyRoster[it].type to (original.enemyRoster[it].soldiers - roster[it].soldiers)
            }
            val ownDamage = BattleDamageSources(pursuit = own.sumOf { it.second })
            val enemyDamage = BattleDamageSources(pursuit = enemy.sumOf { it.second })
            causes += causes(section, own, ownDamage, false, BattleContactState.DISTANT)
            causes += causes(section, enemy, enemyDamage, true, BattleContactState.DISTANT)
            FrontExchangeReport(section, BattleContactState.DISTANT, ownDamage = ownDamage, enemyDamage = enemyDamage)
        }
        val phaseCauses = addCauses(emptyList(), causes)
        val phase = original.copy(contingents = troops, fronts = fronts, enemyRoster = roster, causes = phaseCauses)
        val own = WarEngine.projectedCasualties(state, phase)
        val wounded = enemyWounded(phase)
        val enemy = CasualtyReport(dead = phaseCauses.sumOf { it.enemy } - wounded, wounded = wounded)
        val total = phase.copy(causes = addCauses(original.causes, phaseCauses))
        return total.copy(casualties = WarEngine.projectedCasualties(state, total),
            exchanges = (original.exchanges + BattleExchangeReport(original.minute + 5, reports, causes = phaseCauses,
                ownCasualties = own, enemyCasualties = enemy)).takeLast(20),
            segments = original.segments.map { it.copy(siegeStage = if (original.orderedRetreat) SiegeStage.WITHDRAWAL else SiegeStage.PURSUIT) })
    }

    fun enemyWounded(battle: BattleSession): Int = (battle.causes.sumOf { row ->
        val formation = battle.enemyRoster.filter { it.section == row.section && it.type == row.type }
        val initial = formation.sumOf { it.startSoldiers.toLong() }.coerceAtLeast(1)
        val armor = (formation.sumOf { it.startSoldiers.toLong() * it.equipment } / initial / 10).toInt()
        val terrainPenalty = if (battle.terrain[row.section] in listOf(BattleTerrain.MUD, BattleTerrain.RIVER)) 5 else 0
        row.enemy.toLong() * (row.cause.woundedPercent + armor - terrainPenalty).coerceIn(0, 85)
    } / 100).toInt()

    fun enemyCasualties(battle: BattleSession): CasualtyReport {
        val lost = battle.enemyStart - battle.enemyRemaining
        val captured = if (battle.status == BattleStatus.VICTORY) lost /
            if (battle.inputs.lastOrNull()?.decision == BattleDecision.PURSUE) 8 else 15 else 0
        val wounded = if (battle.causes.isEmpty()) (lost - captured) * 30 / 100 else
            minOf(lost - captured, enemyWounded(battle))
        return CasualtyReport(dead = lost - captured - wounded, wounded = wounded, captured = captured)
    }

    fun lossesByPhase(battle: BattleSession): List<PhaseLoss> {
        if (battle.causes.isNotEmpty()) return BattleDamageCause.entries.map { cause ->
            PhaseLoss(cause.label, battle.causes.filter { it.cause == cause }.sumOf { it.own },
                battle.causes.filter { it.cause == cause }.sumOf { it.enemy })
        }.filter { it.own > 0 || it.enemy > 0 }
        val fronts = battle.exchanges.flatMap { it.fronts }
        fun melee(states: Set<BattleContactState>) = PhaseLoss("", fronts.filter { it.contactState in states }.sumOf { it.ownDamage.melee },
            fronts.filter { it.contactState in states }.sumOf { it.enemyDamage.melee })
        return listOf(
            PhaseLoss("Fernkampf", fronts.sumOf { it.ownDamage.ranged }, fronts.sumOf { it.enemyDamage.ranged }),
            melee(setOf(BattleContactState.WALL_ASSAULT)).copy(label = "Mauerangriff"),
            melee(setOf(BattleContactState.FIELD_CONTACT)).copy(label = "Nahkampf im Feld"),
            PhaseLoss("Artillerie / Mauerwaffen", fronts.sumOf { it.ownDamage.splash + it.ownDamage.artillery }, fronts.sumOf { it.enemyDamage.splash + it.enemyDamage.artillery + it.enemyDamage.wallWeapons }),
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
