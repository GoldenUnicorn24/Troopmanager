package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class LivingRealmTest {
    private fun state(
        season: Season,
        food: Int,
        foodCapacity: Int = 24_000,
        threat: Int = 12,
    ): GameState {
        val base = GameEngine.newGame("Marktstadt", 28, Species.HUMAN, null)
        return base.copy(
            resources = base.resources.copy(food = food, gold = 100_000),
            city = base.city.copy(
                season = season,
                storageCapacity = base.city.storageCapacity.copy(food = foodCapacity),
                prosperity = 50,
            ),
            realm = base.realm.copy(threat = threat),
        )
    }

    @Test fun foodPriceReflectsSeasonAndScarcityWithoutArbitrage() {
        val scarceWinter = state(Season.WINTER, food = 1_000)
        val harvestSurplus = state(Season.AUTUMN, food = 22_000)
        assertTrue(
            CityEngine.buyPrice(scarceWinter, ResourceKind.FOOD) >
                CityEngine.buyPrice(harvestSurplus, ResourceKind.FOOD)
        )
        assertTrue(
            CityEngine.sellPrice(scarceWinter, ResourceKind.FOOD) <
                CityEngine.buyPrice(scarceWinter, ResourceKind.FOOD)
        )
        assertTrue(CityEngine.marketReason(scarceWinter, ResourceKind.FOOD).contains("Knappheit"))
        assertTrue(CityEngine.marketReason(scarceWinter, ResourceKind.FOOD).contains("Winter"))
    }

    @Test fun tradeUsesTheVisibleDynamicPrice() {
        val before = state(Season.WINTER, food = 1_000)
        val amount = 100
        val price = CityEngine.buyPrice(before, ResourceKind.FOOD)
        val result = CityEngine.trade(before, ResourceKind.FOOD, amount, true)
        assertEquals(before.resources.food + amount, result.state.resources.food)
        assertEquals(before.resources.gold - amount * price, result.state.resources.gold)
    }

    @Test fun warDemandRaisesIronValue() {
        val peace = state(Season.SUMMER, food = 12_000, threat = 20)
        val war = state(Season.SUMMER, food = 12_000, threat = 80)
        assertTrue(
            CityEngine.buyPrice(war, ResourceKind.IRON) >=
                CityEngine.buyPrice(peace, ResourceKind.IRON)
        )
        assertTrue(
            CityEngine.sellPrice(war, ResourceKind.IRON) >=
                CityEngine.sellPrice(peace, ResourceKind.IRON)
        )
    }
}
