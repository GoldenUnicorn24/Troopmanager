package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object ArmyEngine {
    fun normalize(units: List<UnitAllocation>): List<UnitAllocation> =
        units
            .groupBy { it.type }
            .mapNotNull { (type, rows) ->
                val total = rows.sumOf { it.amount.toLong() }
                if (total in 1..Int.MAX_VALUE.toLong()) UnitAllocation(type, total.toInt())
                else null
            }

    fun add(
        state: GameState,
        type: UnitType,
        amount: Int,
        experience: Int = 0,
        morale: Int = 80,
    ): GameState {
        if (amount <= 0) return state
        val old = state.armyPools.firstOrNull { it.type == type }
        val pool =
            if (old == null) ArmyUnitPool(type, amount, experience, morale)
            else {
                val total = old.soldiers.toLong() + amount
                old.copy(
                    soldiers = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    experience =
                        ((old.experience.toLong() * old.soldiers + experience.toLong() * amount) /
                                total)
                            .toInt(),
                    morale =
                        ((old.morale.toLong() * old.soldiers + morale.toLong() * amount) / total)
                            .toInt(),
                )
            }
        return state.copy(armyPools = state.armyPools.filterNot { it.type == type } + pool)
    }

    fun allocation(
        state: GameState,
        commanderId: Long,
        requested: List<UnitAllocation>,
    ): GameEngine.ActionResult {
        val commander =
            state.commanders.find { it.id == commanderId }
                ?: return GameEngine.ActionResult(state, "Kommandant nicht gefunden.")
        if (state.battleSession?.isActive == true || state.commanderAway(commanderId))
            return GameEngine.ActionResult(state, "Dieses Kommando ist im Einsatz.")
        if (
            requested.any { it.amount < 0 } ||
                requested
                    .groupBy { it.type }
                    .any { (_, rows) -> rows.sumOf { it.amount.toLong() } > Int.MAX_VALUE }
        )
            return GameEngine.ActionResult(state, "Ungültige Soldatenzahl.")
        val units = normalize(requested)
        for (u in units) {
            val available = freeForCommander(state, commanderId, u.type)
            if (u.amount > available)
                return GameEngine.ActionResult(state, "${u.type.label}: nur $available verfügbar.")
        }
        return GameEngine.ActionResult(
            state.copy(
                commanderAssignments =
                    state.commanderAssignments.filterNot { it.commanderId == commanderId } +
                        CommanderAssignment(commanderId, units)
            ),
            "${commander.name} führt ${units.sumOf { it.amount }} Soldaten.",
        )
    }

    fun freeForCommander(state: GameState, commanderId: Long, type: UnitType): Int =
        if (state.commanderAway(commanderId) || state.battleSession?.isActive == true) 0
        else
            (state.homeSoldiers(type) - state.assigned(type) + state.assignedTo(commanderId, type))
                .coerceAtLeast(0)

    fun clampAssignments(state: GameState): GameState {
        val left = UnitType.entries.associateWith { state.homeSoldiers(it) }.toMutableMap()
        val seen = mutableSetOf<Long>()
        val assignments =
            state.commanderAssignments.mapNotNull { assignment ->
                if (
                    state.commanders.none { it.id == assignment.commanderId } ||
                        state.commanderAway(assignment.commanderId) ||
                        !seen.add(assignment.commanderId)
                )
                    return@mapNotNull null
                val units =
                    normalize(assignment.units).mapNotNull { u ->
                        val count = u.amount.coerceIn(0, left.getValue(u.type))
                        left[u.type] = left.getValue(u.type) - count
                        if (count > 0) UnitAllocation(u.type, count) else null
                    }
                CommanderAssignment(assignment.commanderId, units)
            }
        return state.copy(commanderAssignments = assignments)
    }

    fun applyLosses(state: GameState, losses: List<UnitAllocation>): GameState {
        var population = state.population
        val byType = normalize(losses).associate { it.type to it.amount }
        val pools =
            state.armyPools.mapNotNull { p ->
                val count = (byType[p.type] ?: 0).coerceIn(0, p.soldiers)
                population = adjustPopulation(population, p.type.culture, -count)
                if (p.soldiers == count) null else p.copy(soldiers = p.soldiers - count)
            }
        return clampAssignments(state.copy(armyPools = pools, population = population))
    }

    fun adjustPopulation(p: Population, culture: Culture, delta: Int): Population {
        val old = population(p, culture)
        val total = p.human.toLong() + p.woodElf + p.goldElf + p.wall
        val value =
            (old.toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong() - (total - old)).toInt()
        return when (culture) {
            Culture.HUMAN -> p.copy(human = value)
            Culture.WOOD_ELF -> p.copy(woodElf = value)
            Culture.GOLD_ELF -> p.copy(goldElf = value)
            Culture.WALL -> p.copy(wall = value)
        }
    }

    fun adjustRecruits(p: Population, culture: Culture, delta: Int): Population {
        val value =
            (p.recruits(culture).toLong() + delta)
                .coerceIn(0, population(p, culture).toLong())
                .toInt()
        return when (culture) {
            Culture.HUMAN -> p.copy(humanRecruits = value)
            Culture.WOOD_ELF -> p.copy(woodElfRecruits = value)
            Culture.GOLD_ELF -> p.copy(goldElfRecruits = value)
            Culture.WALL -> p.copy(wallRecruits = value)
        }
    }

    fun population(p: Population, culture: Culture): Int =
        when (culture) {
            Culture.HUMAN -> p.human
            Culture.WOOD_ELF -> p.woodElf
            Culture.GOLD_ELF -> p.goldElf
            Culture.WALL -> p.wall
        }

    fun recruitable(state: GameState, culture: Culture): Int =
        minOf(
            state.population.recruits(culture),
            (population(state.population, culture) -
                    state.armyPools.filter { it.type.culture == culture }.sumOf { it.soldiers } -
                    state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount })
                .coerceAtLeast(0),
        )

    fun equipmentRepairCost(state: GameState, type: UnitType): Resources {
        val pool = state.armyPools.find { it.type == type } ?: return Resources(0,0,0,0,0)
        val points = minOf(20, 100 - pool.equipment)
        if (points <= 0) return Resources(0,0,0,0,0)
        val discount = 100 - state.realm.level(BuildingType.ARSENAL).coerceAtMost(5) * 8
        val iron = maxOf(1L, pool.soldiers.toLong() * points * discount / 2000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Resources((iron.toLong()*2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 0, 0, 0, iron)
    }

    fun repairEquipment(state: GameState, type: UnitType): GameEngine.ActionResult {
        val pool =
            state.armyPools.find { it.type == type }
                ?: return GameEngine.ActionResult(state, "Keine Einheit vorhanden.")
        if (state.battleSession?.isActive == true || state.away(type) > 0)
            return GameEngine.ActionResult(state, "Ausrüstung wird nach der Rückkehr erneuert.")
        val points = minOf(20, 100 - pool.equipment)
        if (points <= 0) return GameEngine.ActionResult(state, "Ausrüstung bereits vollständig.")
        val discount = 100 - state.realm.level(BuildingType.ARSENAL).coerceAtMost(5) * 8
        val requiredIron = maxOf(1L, pool.soldiers.toLong() * points * discount / 2000)
        if(requiredIron * 2 > Int.MAX_VALUE) return GameEngine.ActionResult(state, "Diese Erneuerung übersteigt das maximale Goldbudget.")
        val cost = equipmentRepairCost(state, type)
        val iron = cost.iron
        val gold = cost.gold.toLong()
        if (state.resources.iron < iron || state.resources.gold < gold)
            return GameEngine.ActionResult(state, "Erneuerung kostet $gold Gold und $iron Eisen.")
        return GameEngine.ActionResult(
            state.copy(
                resources =
                    state.resources.copy(
                        iron = state.resources.iron - iron,
                        gold = state.resources.gold - gold.toInt(),
                    ),
                armyPools =
                    state.armyPools.map {
                        if (it.type == type) it.copy(equipment = it.equipment + points) else it
                    },
            ),
            "Ausrüstung um $points verbessert.",
        )
    }
}
