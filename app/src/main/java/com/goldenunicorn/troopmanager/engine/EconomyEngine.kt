package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

data class DailyProduction(val gross: Resources, val upkeep: Int, val net: Resources)

object EconomyEngine {
    fun regionalYield(type: RegionType): Resources = when (type) {
        RegionType.MINE -> Resources(0, 0, 0, 0, 75)
        RegionType.FOREST -> Resources(0, 0, 100, 0, 0)
        RegionType.VILLAGE -> Resources(75, 0, 0, 0, 0)
        else -> Resources(0, 0, 0, 0, 0)
    }
    fun workerFactor(state: GameState, kind: ResourceKind): Double {
        val efficiency = (state.workers.toDouble() / state.workerDemand.coerceAtLeast(1)).coerceIn(0.7, 1.0)
        if (state.city.workerPriority == WorkerPriority.BALANCED) return efficiency
        fun priority(k: ResourceKind) = when (k) {
            ResourceKind.GOLD -> WorkerPriority.TRADE
            ResourceKind.FOOD -> WorkerPriority.FOOD
            ResourceKind.WOOD -> WorkerPriority.WOOD
            ResourceKind.STONE -> WorkerPriority.STONE
            ResourceKind.IRON -> WorkerPriority.IRON
        }
        fun demand(k: ResourceKind) = 1 + state.realm.level(productionBuilding(k))
        fun weight(k: ResourceKind) = if (priority(k) == state.city.workerPriority) 1.5 else 0.85
        val totalDemand = ResourceKind.entries.sumOf { demand(it) }
        val weighted = ResourceKind.entries.sumOf { demand(it) * weight(it) }
        return efficiency * weight(kind) * totalDemand / weighted
    }

    fun productionBuilding(kind: ResourceKind): BuildingType = when (kind) {
        ResourceKind.GOLD -> BuildingType.MARKET
        ResourceKind.FOOD -> BuildingType.FARM
        ResourceKind.WOOD -> BuildingType.SAWMILL
        ResourceKind.STONE -> BuildingType.QUARRY
        ResourceKind.IRON -> BuildingType.IRONWORKS
    }

    fun upkeep(state: GameState): Int {
        val stableDiscount = if (state.realm.level(BuildingType.STABLES) > 0) 1 else 0
        val food = state.armyPools.sumOf { state.homeSoldiers(it.type).toLong() * (it.foodPerSoldier - if (it.type == UnitType.KNIGHT) stableDiscount else 0) } + state.trainingSize
        val winter = if (Season.forDay(state.day) == Season.WINTER) 1.15 else 1.0
        val logistics =
            if (ResearchTech.SUPPLY_TRAINS in state.research.completed) 0.90 else 1.0
        return (food * winter * logistics)
            .toLong()
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun breakdown(state: GameState, kind: ResourceKind): ResourceBreakdown {
        val r = state.realm
        val (base, perLevel, perTerritory) = when (kind) {
            ResourceKind.GOLD -> Triple(450, 180, 120)
            ResourceKind.FOOD -> Triple(900, 320, 180)
            ResourceKind.WOOD -> Triple(700, 260, 90)
            ResourceKind.STONE -> Triple(480, 200, 70)
            ResourceKind.IRON -> Triple(320, 150, 45)
        }
        fun safe(value: Long) = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val building = safe(r.level(productionBuilding(kind)).toLong() * perLevel)
        var territory = safe(r.territory.toLong() * perTerritory)
        val regionBonus = state.regions.filter { it.owned }.sumOf { kind.value(regionalYield(it.type)).toLong() }
        territory = safe(territory.toLong() + regionBonus)
        val worker = workerFactor(state, kind)
        val c = state.companion
        val trade = if (kind == ResourceKind.GOLD && r.tradeBonusDays > 0)
            100 + state.player.diplomacy * 2 + if (c.met && c.trust >= 45) c.diplomacy else 0
            else 0
        val companion = if (kind == ResourceKind.GOLD && c.met && c.role == "Mitregentin") c.diplomacy / 2 else 0
        val embassy = if (kind == ResourceKind.GOLD) r.level(BuildingType.EMBASSY) * 25 else 0
        val event = safe(trade.toLong() + companion + embassy + CharacterEngine.bonuses(state).gold)
        val untaxed = safe(base.toLong() + ((building.toLong() + territory) * worker).toLong() + event)
        val socialFactor = if (kind == ResourceKind.GOLD) (1.0 + (state.city.prosperity - 50) / 500.0 + (state.city.satisfaction - 50) / 1000.0) else 1.0
        val seasonFactor = if (kind == ResourceKind.FOOD) Season.forDay(state.day).harvestFactor else 1.0
        val difficultyFactor =
            if (kind == ResourceKind.FOOD && state.settings.difficulty == Difficulty.STORY) 1.2
            else 1.0
        val researchFactor =
            when {
                kind == ResourceKind.FOOD &&
                    ResearchTech.CROP_ROTATION in state.research.completed -> 1.08
                kind == ResourceKind.GOLD &&
                    ResearchTech.CIVIC_ADMINISTRATION in state.research.completed -> 1.05
                else -> 1.0
            }
        val gross =
            (untaxed *
                    (if (kind == ResourceKind.GOLD) state.city.taxLevel.goldFactor else 1.0) *
                    socialFactor *
                    seasonFactor *
                    difficultyFactor *
                    researchFactor *
                    (if (kind == ResourceKind.GOLD) OriginEngine.goldFactor(state) else 1.0))
                .toLong()
                .coerceIn(0, Int.MAX_VALUE.toLong())
                .toInt()
        val taxBonus = gross - untaxed
        val consumption = if (kind == ResourceKind.FOOD) upkeep(state) else 0
        val net = gross - consumption
        val capacity = kind.value(state.city.storageCapacity)
        val overflow = (kind.value(state.resources).toLong() + net - maxOf(kind.value(state.resources), capacity)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        return ResourceBreakdown(base, building, territory, event, worker, taxBonus, gross, consumption, net, capacity, overflow)
    }

    fun production(state: GameState): DailyProduction {
        var gross = Resources(0, 0, 0, 0, 0)
        ResourceKind.entries.forEach { gross = it.withValue(gross, breakdown(state, it).gross) }
        val upkeep = upkeep(state)
        return DailyProduction(gross, upkeep, gross.copy(food = gross.food - upkeep))
    }

    /** Overflow is discarded from today's positive output, never from pre-existing stock. */
    fun addCapped(resources: Resources, delta: Resources, capacity: Resources): Resources {
        var result = resources
        ResourceKind.entries.forEach { kind ->
            val old = kind.value(resources)
            val change = kind.value(delta)
            val ceiling = maxOf(old, kind.value(capacity))
            val value = (old.toLong() + change).coerceIn(0, if (change > 0) ceiling.toLong() else Int.MAX_VALUE.toLong()).toInt()
            result = kind.withValue(result, value)
        }
        return result
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
                resources = addCapped(state.resources, production.net, state.city.storageCapacity),
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
                            .takeLast(2000),
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
                                .takeLast(2000)
                    )
        }
        if (!starving && next.day % 7 == 0) {
            var pop = next.population
            Culture.entries
                .filter { ArmyEngine.population(pop, it) > 0 }
                .forEach { c ->
                    val favor = next.culturePatronage[c] ?: 0
                    val top = next.culturePatronage.values.maxOrNull() ?: 0
                    val bias = (1.0 + favor * 0.55) * (if (top >= 3 && favor < top) 0.4 else 1.0)
                    val baseline = ((if (c == Culture.GOLD_ELF) 6 else 12) * (1 + next.realm.territory) * bias).toInt()
                    val social = (0.5 + next.city.satisfaction / 100.0) * (0.75 + next.city.security / 200.0)
                    val desired = (baseline * next.city.taxLevel.growthFactor * social).toInt().coerceAtLeast(0)
                    val growth = minOf(desired, (next.city.housingCapacity - pop.total).coerceAtLeast(0))
                    val before = ArmyEngine.population(pop, c)
                    pop = ArmyEngine.adjustPopulation(pop, c, growth)
                    val newcomers = ArmyEngine.population(pop, c) - before
                    val volunteer = ((ArmyEngine.population(pop, c) / (if (c == Culture.GOLD_ELF) 18 else 40)) * (1 + favor)).coerceAtMost(8 + favor * 6)
                    val reserved = next.armyPools.filter { it.type.culture == c }.sumOf { it.soldiers.toLong() } +
                        next.trainingQueue.filter { it.type.culture == c }.sumOf { it.amount.toLong() } +
                        next.war.wounded.filter { it.type.culture == c }.sumOf { it.soldiers.toLong() } +
                        next.war.captives.filter { it.own && it.type?.culture == c }.sumOf { it.soldiers.toLong() } + pop.recruits(c)
                    val available = (ArmyEngine.population(pop, c).toLong() - reserved).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                    pop = ArmyEngine.adjustRecruits(pop, c, minOf(available, (newcomers / 2 + volunteer).coerceAtLeast(0)))
                }
            next = next.copy(population = pop)
        }
        return next
    }
}
