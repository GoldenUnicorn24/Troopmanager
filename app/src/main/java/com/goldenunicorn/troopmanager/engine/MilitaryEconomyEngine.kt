package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.roundToInt

object MilitaryEconomyEngine {
    private fun ceilDiv(value: Int, divisor: Int): Int =
        if (value <= 0) 0
        else ((value.toLong() + divisor - 1) / divisor)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    fun requirements(type: UnitType, amount: Int): Map<MilitaryGood, Int> {
        val n = amount.coerceAtLeast(0)
        if (n == 0) return emptyMap()
        return when (type) {
            UnitType.HUMAN_SWORD ->
                mapOf(
                    MilitaryGood.SWORDS to n,
                    MilitaryGood.ARMOR to n,
                    MilitaryGood.SHIELDS to n,
                )
            UnitType.HUMAN_ARCHER ->
                mapOf(
                    MilitaryGood.BOWS to n,
                    MilitaryGood.ARROWS to n * 24,
                    MilitaryGood.ARMOR to ceilDiv(n, 2),
                )
            UnitType.KNIGHT ->
                mapOf(
                    MilitaryGood.SWORDS to n,
                    MilitaryGood.ARMOR to n,
                    MilitaryGood.SHIELDS to n,
                    MilitaryGood.HORSES to n,
                )
            UnitType.WOOD_RANGER ->
                mapOf(
                    MilitaryGood.BOWS to n,
                    MilitaryGood.ARROWS to n * 30,
                    MilitaryGood.ARMOR to ceilDiv(n, 3),
                )
            UnitType.WOOD_BLADE ->
                mapOf(
                    MilitaryGood.SWORDS to n,
                    MilitaryGood.ARMOR to n,
                )
            UnitType.GOLD_SPEAR ->
                mapOf(
                    MilitaryGood.SPEARS to n,
                    MilitaryGood.ARMOR to n,
                    MilitaryGood.SHIELDS to n,
                )
            UnitType.GOLD_ARCHER ->
                mapOf(
                    MilitaryGood.BOWS to n,
                    MilitaryGood.ARROWS to n * 36,
                    MilitaryGood.ARMOR to n,
                )
            UnitType.CRANE_GUARD ->
                mapOf(
                    MilitaryGood.SPEARS to n,
                    MilitaryGood.ARMOR to n,
                    MilitaryGood.SHIELDS to n,
                )
            UnitType.EAGLE_CORPS ->
                mapOf(
                    MilitaryGood.BOWS to n,
                    MilitaryGood.ARROWS to n * 28,
                    MilitaryGood.ARMOR to ceilDiv(n, 2),
                )
            UnitType.TIGER_CORPS ->
                mapOf(
                    MilitaryGood.SWORDS to n,
                    MilitaryGood.ARMOR to n,
                    MilitaryGood.SHIELDS to ceilDiv(n, 2),
                )
            UnitType.BEAR_CORPS ->
                mapOf(
                    MilitaryGood.SPEARS to n,
                    MilitaryGood.ARMOR to n,
                    MilitaryGood.SHIELDS to n,
                )
            UnitType.DEER_CORPS ->
                mapOf(
                    MilitaryGood.SPEARS to n,
                    MilitaryGood.ARMOR to ceilDiv(n, 2),
                    MilitaryGood.SHIELDS to ceilDiv(n, 2),
                )
            UnitType.DRAGON_ARTILLERY ->
                mapOf(
                    MilitaryGood.SIEGE_PARTS to ceilDiv(n, 4),
                    MilitaryGood.ARROWS to n * 20,
                    MilitaryGood.ARMOR to ceilDiv(n, 2),
                )
        }
    }

    fun missing(
        stock: MilitaryStock,
        requirements: Map<MilitaryGood, Int>,
    ): Map<MilitaryGood, Int> =
        requirements.mapNotNull { (good, needed) ->
            val amount = (needed - stock.amount(good)).coerceAtLeast(0)
            if (amount > 0) good to amount else null
        }.toMap()

    fun consume(
        stock: MilitaryStock,
        requirements: Map<MilitaryGood, Int>,
    ): MilitaryStock {
        var next = stock
        requirements.forEach { (good, amount) ->
            next = next.withAmount(good, next.amount(good) - amount)
        }
        return next
    }

    fun add(stock: MilitaryStock, good: MilitaryGood, amount: Int): MilitaryStock =
        stock.withAmount(
            good,
            (stock.amount(good).toLong() + amount)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
        )

    fun startingStock(units: List<UnitAllocation>): MilitaryStock {
        val required = mutableMapOf<MilitaryGood, Long>()
        units.forEach { allocation ->
            requirements(allocation.type, allocation.amount).forEach { (good, amount) ->
                required[good] = (required[good] ?: 0L) + amount
            }
        }
        fun start(good: MilitaryGood, minimum: Int): Int {
            val need = required[good] ?: 0L
            return maxOf(minimum.toLong(), need * 5L / 4L + 25L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
        return MilitaryStock(
            swords = start(MilitaryGood.SWORDS, 180),
            spears = start(MilitaryGood.SPEARS, 120),
            bows = start(MilitaryGood.BOWS, 160),
            arrows = start(MilitaryGood.ARROWS, 4000),
            armor = start(MilitaryGood.ARMOR, 220),
            shields = start(MilitaryGood.SHIELDS, 180),
            horses = start(MilitaryGood.HORSES, 60),
            siegeParts = start(MilitaryGood.SIEGE_PARTS, 50),
            medicine = start(MilitaryGood.MEDICINE, 120),
        )
    }

    fun storageCap(state: GameState, good: MilitaryGood): Int {
        val base =
            1500 +
                state.realm.level(BuildingType.WAREHOUSE) * 700 +
                state.realm.level(BuildingType.ARSENAL) * 500
        return if (good == MilitaryGood.ARROWS) base * 20 else base
    }

    fun tick(state: GameState): GameState {
        if (state.battleSession?.isActive == true) return state
        val arsenal = WarEngine.effectiveLevel(state, BuildingType.ARSENAL).coerceAtLeast(0)
        val ironworks = WarEngine.effectiveLevel(state, BuildingType.IRONWORKS).coerceAtLeast(0)
        val sawmill = WarEngine.effectiveLevel(state, BuildingType.SAWMILL).coerceAtLeast(0)
        val stables = WarEngine.effectiveLevel(state, BuildingType.STABLES).coerceAtLeast(0)
        val hospital = WarEngine.effectiveLevel(state, BuildingType.HOSPITAL).coerceAtLeast(0)
        if (arsenal + ironworks + sawmill + stables + hospital == 0) return state

        var raw = state.resources
        var stock = state.militaryStock
        val forgeResearch = ResearchTech.FORGE_STANDARDIZATION in state.research.completed
        val bowResearch = ResearchTech.COMPOSITE_BOWS in state.research.completed
        val siegeResearch = ResearchTech.SIEGE_ENGINEERING in state.research.completed

        fun produce(
            good: MilitaryGood,
            wanted: Int,
            iron: Int = 0,
            wood: Int = 0,
            food: Int = 0,
            gold: Int = 0,
        ) {
            if (wanted <= 0) return
            val room = (storageCap(state, good) - stock.amount(good)).coerceAtLeast(0)
            if (room <= 0) return
            var possible = minOf(wanted, room)
            if (iron > 0) possible = minOf(possible, raw.iron / iron)
            if (wood > 0) possible = minOf(possible, raw.wood / wood)
            if (food > 0) possible = minOf(possible, raw.food / food)
            if (gold > 0) possible = minOf(possible, raw.gold / gold)
            if (possible <= 0) return
            raw =
                raw.copy(
                    iron = raw.iron - possible * iron,
                    wood = raw.wood - possible * wood,
                    food = raw.food - possible * food,
                    gold = raw.gold - possible * gold,
                )
            stock = add(stock, good, possible)
        }

        val forge = ironworks * 4 + arsenal * 6
        val standardized = if (forgeResearch) 1.25 else 1.0
        produce(
            MilitaryGood.SWORDS,
            (forge * standardized).roundToInt(),
            iron = 2,
            wood = 1,
        )
        produce(
            MilitaryGood.SPEARS,
            (forge * standardized).roundToInt(),
            iron = 1,
            wood = 1,
        )
        produce(
            MilitaryGood.ARMOR,
            ((arsenal * 5 + ironworks * 2) * standardized).roundToInt(),
            iron = 3,
        )
        produce(
            MilitaryGood.SHIELDS,
            ((arsenal * 4 + sawmill * 2) * standardized).roundToInt(),
            iron = 1,
            wood = 2,
        )

        val bowFactor = if (bowResearch) 1.25 else 1.0
        produce(
            MilitaryGood.BOWS,
            ((arsenal * 4 + sawmill * 3) * bowFactor).roundToInt(),
            wood = 3,
            iron = 1,
        )
        produce(
            MilitaryGood.ARROWS,
            ((arsenal * 80 + sawmill * 60) * bowFactor).roundToInt(),
            wood = 1,
        )

        produce(MilitaryGood.HORSES, stables * 3, food = 10, gold = 2)
        val siegeFactor = if (siegeResearch) 1.3 else 1.0
        produce(
            MilitaryGood.SIEGE_PARTS,
            (arsenal * 2 * siegeFactor).roundToInt(),
            iron = 4,
            wood = 5,
        )
        produce(MilitaryGood.MEDICINE, hospital * 6, food = 3, gold = 1)

        return state.copy(resources = raw, militaryStock = stock)
    }

    fun requirementText(type: UnitType, amount: Int): String =
        requirements(type, amount)
            .entries
            .joinToString(" · ") { "${it.value} ${it.key.label}" }
}
