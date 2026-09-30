package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

data class DailyProduction(val gross: Resources, val upkeep: Int, val net: Resources)

object EconomyEngine {
    fun production(state: GameState): DailyProduction {
        val r = state.realm
        val efficiency =
            (state.workers.toDouble() / state.workerDemand.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        fun income(base: Int, building: BuildingType, perLevel: Int, perTerritory: Int): Int =
            base +
                ((r.level(building) * perLevel + r.territory * perTerritory) * efficiency).toInt()
        val gross =
            Resources(
                income(200, BuildingType.MARKET, 100, 75) +
                    (if (r.tradeBonusDays > 0)
                        100 +
                            state.player.diplomacy * 2 +
                            (if (state.companion.met && state.companion.trust >= 45)
                                state.companion.diplomacy
                            else 0)
                    else 0),
                income(400, BuildingType.FARM, 150, 100),
                income(200, BuildingType.SAWMILL, 100, 50),
                income(160, BuildingType.QUARRY, 100, 40),
                income(120, BuildingType.IRONWORKS, 75, 25),
            )
        val upkeep =
            (state.armyPools.sumOf { it.soldiers.toLong() * it.foodPerSoldier } +
                    state.trainingSize)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        return DailyProduction(gross, upkeep, gross.copy(food = gross.food - upkeep))
    }

    fun add(r: Resources, delta: Resources): Resources {
        fun safe(a: Int, b: Int) = (a.toLong() + b).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        return Resources(
            safe(r.gold, delta.gold),
            safe(r.food, delta.food),
            safe(r.wood, delta.wood),
            safe(r.stone, delta.stone),
            safe(r.iron, delta.iron),
        )
    }

    fun day(state: GameState): GameState {
        val production = production(state)
        val starving = state.resources.food.toLong() + production.gross.food < production.upkeep
        var next =
            state.copy(
                day = state.day + 1,
                resources = add(state.resources, production.net),
                realm =
                    state.realm.copy(
                        tradeBonusDays = (state.realm.tradeBonusDays - 1).coerceAtLeast(0),
                        scoutingDays = (state.realm.scoutingDays - 1).coerceAtLeast(0),
                    ),
            )
        if (starving) {
            next =
                next.copy(
                    armyPools =
                        next.armyPools.map { it.copy(morale = (it.morale - 8).coerceAtLeast(10)) },
                    chronicle =
                        (next.chronicle +
                                ChronicleEntry(
                                    next.day,
                                    "Versorgungsnot",
                                    "Nahrung fehlt. Moral sinkt; Ausbildung läuft nur jeden zweiten Tag, Zuzug bleibt aus.",
                                ))
                            .takeLast(80),
                )
        }
        val trainingProgress = !starving || next.day % 2 == 0
        if (trainingProgress) {
            val finished = next.trainingQueue.filter { it.daysRemaining <= 1 }
            next =
                next.copy(
                    trainingQueue =
                        next.trainingQueue
                            .filter { it.daysRemaining > 1 }
                            .map { it.copy(daysRemaining = it.daysRemaining - 1) }
                )
            finished.forEach {
                next =
                    ArmyEngine.add(
                        next,
                        it.type,
                        it.amount,
                        morale = (70 + next.realm.level(BuildingType.BARRACKS) * 3).coerceAtMost(95),
                    )
            }
            if (finished.isNotEmpty())
                next =
                    next.copy(
                        chronicle =
                            (next.chronicle +
                                    ChronicleEntry(
                                        next.day,
                                        "Ausbildung abgeschlossen",
                                        finished.joinToString { "${it.amount} ${it.type.label}" },
                                    ))
                                .takeLast(80)
                    )
        }
        if (!starving && next.day % 7 == 0) {
            var pop = next.population
            Culture.entries
                .filter { ArmyEngine.population(pop, it) > 0 }
                .forEach { c ->
                    val growth = (if (c == Culture.GOLD_ELF) 2 else 12) * (1 + next.realm.territory)
                    val before = ArmyEngine.population(pop, c)
                    pop = ArmyEngine.adjustPopulation(pop, c, growth)
                    pop =
                        ArmyEngine.adjustRecruits(
                            pop,
                            c,
                            (ArmyEngine.population(pop, c) - before) / 3,
                        )
                }
            next = next.copy(population = pop)
        }
        return next
    }
}
