package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** City actions use campaign days; existing buildings are never converted to projects. */
object CityEngine {
    /** v0.61: every realm starts with three parallel construction slots. */
    fun constructionSlots(state: GameState): Int = 3

    fun buildingDays(state: GameState, type: BuildingType): Int {
        val target = state.realm.level(type) + 1
        val major =
            when (type) {
                BuildingType.PALACE -> 3
                BuildingType.WALL,
                BuildingType.TOWER,
                BuildingType.ACADEMY -> 2
                else -> 1
            }
        return (major + (target - 1) / 2).coerceAtLeast(1)
    }

    fun startConstruction(state: GameState, type: BuildingType): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Bauen nach der Schlacht möglich.")
        if (state.realm.level(type) >= 100)
            return GameEngine.ActionResult(state, "Maximale Gebäudestufe erreicht.")
        if (state.city.constructionQueue.any { it.type == type })
            return GameEngine.ActionResult(state, "Dieses Gebäude wird bereits ausgebaut.")
        if (state.city.constructionQueue.size >= constructionSlots(state))
            return GameEngine.ActionResult(state, "Alle Bauplätze sind belegt.")
        val cost = GameEngine.buildingCost(state, type)
        if (ResourceKind.entries.any { it.value(state.resources) < it.value(cost) })
            return GameEngine.ActionResult(
                state,
                "Benötigt: ${cost.gold} Gold / ${cost.wood} Holz / ${cost.stone} Stein / ${cost.iron} Eisen.",
            )
        var resources = state.resources
        ResourceKind.entries.forEach {
            resources = it.withValue(resources, it.value(resources) - it.value(cost))
        }
        val days = buildingDays(state, type)
        val id =
            maxOf(
                state.day.toLong() * 1000,
                (state.city.constructionQueue.maxOfOrNull { it.id } ?: 0L) + 1,
            )
        val order = ConstructionOrder(id, type, state.realm.level(type) + 1, days, days)
        val next =
            state.copy(
                resources = resources,
                city =
                    state.city.copy(
                        constructionQueue = state.city.constructionQueue + order,
                        lastCompletedBuildings = emptyList(),
                    ),
            )
        return GameEngine.ActionResult(
            next,
            "${type.label}: Ausbau auf Stufe ${order.targetLevel} in $days Tagen begonnen.",
        )
    }

    /** Expand only to architectural capacity. Migration separately protects all current stock. */
    fun ensureCapacity(state: GameState): GameState {
        val city = state.city
        val r = state.realm
        fun safe(value: Long) = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val housing =
            safe(
                2400L +
                    r.settlementTier.ordinal * 600L +
                    (r.territory - 1).coerceAtLeast(0) * 700L +
                    r.level(BuildingType.RESIDENTIAL) * 600L
            )
        val warehouse = r.level(BuildingType.WAREHOUSE) * 5000L
        val territory = (r.territory - 1).coerceAtLeast(0) * 2000L
        val base = CityState().storageCapacity
        var capacity = city.storageCapacity
        ResourceKind.entries.forEach { kind ->
            capacity =
                kind.withValue(
                    capacity,
                    maxOf(
                        kind.value(capacity),
                        safe(kind.value(base).toLong() + warehouse + territory),
                    ),
                )
        }
        return state.copy(
            city =
                city.copy(
                    housingCapacity = maxOf(city.housingCapacity, housing),
                    storageCapacity = capacity,
                )
        )
    }

    fun migrationDefaults(state: GameState): CityState {
        val derived = ensureCapacity(state.copy(city = CityState())).city
        var capacity = derived.storageCapacity
        ResourceKind.entries.forEach { kind ->
            capacity =
                kind.withValue(capacity, maxOf(kind.value(capacity), kind.value(state.resources)))
        }
        return derived.copy(
            housingCapacity = maxOf(derived.housingCapacity, state.population.total),
            storageCapacity = capacity,
        )
    }

    fun tick(state: GameState): GameState {
        val finished = state.city.constructionQueue.filter { it.daysRemaining <= 1 }
        val queue =
            state.city.constructionQueue
                .filter { it.daysRemaining > 1 }
                .map { it.copy(daysRemaining = it.daysRemaining - 1) }
        var next =
            state.copy(
                city =
                    state.city.copy(
                        constructionQueue = queue,
                        lastCompletedBuildings = finished.map { it.type },
                    )
            )
        finished.forEach { order ->
            // Apply upgrades on top of grandfathered migration capacity as well.
            var city = next.city
            fun increase(value: Int, amount: Int) =
                (value.toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (order.type == BuildingType.RESIDENTIAL)
                city = city.copy(housingCapacity = increase(city.housingCapacity, 600))
            if (order.type == BuildingType.WAREHOUSE) {
                var capacity = city.storageCapacity
                ResourceKind.entries.forEach { kind ->
                    capacity = kind.withValue(capacity, increase(kind.value(capacity), 5000))
                }
                city = city.copy(storageCapacity = capacity)
            }
            next =
                next.copy(
                    city = city,
                    realm =
                        next.realm.copy(
                            buildings = next.realm.buildings + (order.type to order.targetLevel),
                            wallIntegrity =
                                if (order.type == BuildingType.WALL) 100
                                else next.realm.wallIntegrity,
                        ),
                    chronicle =
                        (next.chronicle +
                                ChronicleEntry(
                                    state.day + 1,
                                    "Bau abgeschlossen",
                                    "${order.type.label} erreicht Stufe ${order.targetLevel}.",
                                ))
                            .takeLast(2000),
                )
        }
        // Capacity also grows after new territory or a settlement-tier promotion.
        next = ensureCapacity(ProgressionEngine.update(next))
        val taxChange =
            when (next.city.taxLevel) {
                TaxLevel.LOW -> 1
                TaxLevel.NORMAL -> 0
                TaxLevel.HIGH -> -2
            }
        val securityTarget =
            (50 +
                    WarEngine.effectiveLevel(next, BuildingType.WALL) * 3 +
                    WarEngine.effectiveLevel(next, BuildingType.TOWER) * 2 -
                    next.realm.threat / 2 -
                    (100 - next.realm.wallIntegrity) / 4)
                .coerceIn(0, 100)
        fun approach(value: Int, target: Int) = value + target.compareTo(value)
        val production = EconomyEngine.production(next)
        val enoughFood = next.resources.food.toLong() + production.gross.food >= production.upkeep
        val prosperityTarget =
            (50 +
                    (WarEngine.effectiveLevel(next, BuildingType.MARKET) - 1) * 4 +
                    WarEngine.effectiveLevel(next, BuildingType.EMBASSY) * 3 +
                    (if (next.realm.tradeBonusDays > 0) 8 else 0) - (if (enoughFood) 0 else 20))
                .coerceIn(0, 100)
        val city =
            next.city.copy(
                satisfaction =
                    (next.city.satisfaction + taxChange - (if (enoughFood) 0 else 3)).coerceIn(
                        0,
                        100,
                    ),
                security = approach(next.city.security, securityTarget),
                prosperity = approach(next.city.prosperity, prosperityTarget),
                season = Season.forDay(next.day + 1),
            )
        val hospital = WarEngine.effectiveLevel(next, BuildingType.HOSPITAL)
        return next.copy(
            city = city,
            armyPools =
                if (hospital > 0)
                    next.armyPools.map { pool ->
                        val home = next.homeSoldiers(pool.type)
                        val homeRecovery = minOf(hospital.coerceAtMost(3), 100 - pool.morale)
                        val gain =
                            if (pool.soldiers > 0) homeRecovery.toLong() * home / pool.soldiers
                            else 0L
                        pool.copy(morale = (pool.morale + gain.toInt()).coerceAtMost(100))
                    }
                else next.armyPools,
        )
    }

    fun setWorkerPriority(state: GameState, priority: WorkerPriority): GameEngine.ActionResult =
        GameEngine.ActionResult(
            state.copy(city = state.city.copy(workerPriority = priority)),
            "Arbeiterpriorität: ${priority.label}.",
        )

    fun setTaxLevel(state: GameState, level: TaxLevel): GameEngine.ActionResult =
        GameEngine.ActionResult(
            state.copy(city = state.city.copy(taxLevel = level)),
            "Steuern: ${level.label}.",
        )

    fun buyPrice(kind: ResourceKind): Int =
        when (kind) {
            ResourceKind.IRON -> 5
            ResourceKind.STONE -> 4
            else -> 3
        }

    fun sellPrice(kind: ResourceKind): Int =
        when (kind) {
            ResourceKind.IRON -> 3
            ResourceKind.STONE -> 2
            else -> 1
        }

    fun trade(
        state: GameState,
        kind: ResourceKind,
        amount: Int,
        buy: Boolean,
    ): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Handel nach der Schlacht möglich.")
        if (WarEngine.effectiveLevel(state, BuildingType.MARKET) < 1)
            return GameEngine.ActionResult(state, "Ein Markt wird benötigt.")
        if (amount <= 0 || kind == ResourceKind.GOLD)
            return GameEngine.ActionResult(state, "Ungültige Handelsmenge.")
        val total = amount.toLong() * (if (buy) buyPrice(kind) else sellPrice(kind))
        if (total > Int.MAX_VALUE) return GameEngine.ActionResult(state, "Handelsmenge zu groß.")
        val stock = kind.value(state.resources)
        if (
            buy &&
                (state.resources.gold < total ||
                    stock.toLong() + amount > kind.value(state.city.storageCapacity))
        )
            return GameEngine.ActionResult(state, "Gold oder Lagerkapazität reicht nicht aus.")
        if (
            !buy &&
                (stock < amount ||
                    state.resources.gold.toLong() + total > state.city.storageCapacity.gold)
        )
            return GameEngine.ActionResult(
                state,
                "Ressourcen oder Goldlagerkapazität reicht nicht aus.",
            )
        val resources =
            kind
                .withValue(state.resources, stock + if (buy) amount else -amount)
                .copy(gold = (state.resources.gold + if (buy) -total else total).toInt())
        return GameEngine.ActionResult(
            state.copy(resources = resources),
            "$amount ${kind.label} ${if (buy) "gekauft" else "verkauft"}: $total Gold.",
        )
    }
}
