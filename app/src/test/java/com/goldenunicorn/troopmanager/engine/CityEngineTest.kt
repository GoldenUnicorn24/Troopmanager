package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class CityEngineTest {
    private fun human() = GameEngine.newGame("Leon", 23, Species.HUMAN, null)

    @Test
    fun constructionPaysOnceAndBuildingProducesOnlyAfterCompletion() {
        val initial = human()
        val cost = GameEngine.buildingCost(initial, BuildingType.QUARRY)
        val started = CityEngine.startConstruction(initial, BuildingType.QUARRY).state
        assertEquals(initial.resources.gold - cost.gold, started.resources.gold)
        assertEquals(initial.realm.level(BuildingType.QUARRY), started.realm.level(BuildingType.QUARRY))
        assertEquals(300, EconomyEngine.production(started).gross.stone)
        assertEquals(started, CityEngine.startConstruction(started, BuildingType.QUARRY).state)
        val parallel = CityEngine.startConstruction(started, BuildingType.FARM).state
        assertEquals(2, parallel.city.constructionQueue.size)
        val threeSlots = CityEngine.startConstruction(parallel, BuildingType.SAWMILL).state
        assertEquals(3, threeSlots.city.constructionQueue.size)
        assertEquals(threeSlots, CityEngine.startConstruction(threeSlots, BuildingType.MARKET).state)
        val finished = CityEngine.tick(started)
        assertEquals(2, finished.realm.level(BuildingType.QUARRY))
        assertEquals(400, EconomyEngine.production(finished).gross.stone)
        assertEquals(started.resources, finished.resources)
        assertTrue(finished.city.constructionQueue.isEmpty())
        assertEquals(listOf(BuildingType.QUARRY), finished.city.lastCompletedBuildings)
    }

    @Test
    fun longProjectAndSaveReloadKeepRemainingDaysAndStableId() {
        var state = CityEngine.startConstruction(human(), BuildingType.WALL).state
        val project = state.city.constructionQueue.single()
        assertTrue(project.totalDays >= 2)
        state = CityEngine.tick(state)
        assertEquals(1, state.realm.level(BuildingType.WALL))
        val reloaded = SaveCodec.decode(SaveCodec.encode(state))
        assertEquals(project.id, reloaded.city.constructionQueue.single().id)
        assertEquals(project.daysRemaining - 1, reloaded.city.constructionQueue.single().daysRemaining)
        state = CityEngine.tick(reloaded)
        assertEquals(2, state.realm.level(BuildingType.WALL))
    }

    @Test
    fun threeConstructionSlotsAreAvailableFromTheBeginning() {
        val expanded = human().copy(realm = human().realm.copy(buildings = human().realm.buildings +
            (BuildingType.PALACE to 3) + (BuildingType.ACADEMY to 2)))
        assertEquals(3, CityEngine.constructionSlots(human()))
        assertEquals(3, CityEngine.constructionSlots(expanded))
    }

    @Test
    fun foodPriorityRaisesFoodAndReducesOtherOutputWithoutCreatingWorkers() {
        val balanced = human()
        val focused = CityEngine.setWorkerPriority(balanced, WorkerPriority.FOOD).state
        assertEquals(balanced.workers, focused.workers)
        assertTrue(EconomyEngine.production(focused).gross.food > 800)
        assertTrue(EconomyEngine.production(focused).gross.wood < 350)
        assertTrue(EconomyEngine.breakdown(focused, ResourceKind.FOOD).workerFactor > 1.0)
    }

    @Test
    fun taxesTradeGrowthForGoldAndSatisfaction() {
        val initial = human()
        val low = CityEngine.setTaxLevel(initial, TaxLevel.LOW).state
        val high = CityEngine.setTaxLevel(initial, TaxLevel.HIGH).state
        assertTrue(EconomyEngine.production(high).gross.gold > EconomyEngine.production(initial).gross.gold)
        assertTrue(EconomyEngine.production(low).gross.gold < EconomyEngine.production(initial).gross.gold)
        assertEquals(51, CityEngine.tick(low).city.satisfaction)
        assertEquals(48, CityEngine.tick(high).city.satisfaction)
        val lowGrowth = EconomyEngine.day(low.copy(day = 6)).population.total - low.population.total
        val highGrowth = EconomyEngine.day(high.copy(day = 6)).population.total - high.population.total
        assertTrue(lowGrowth > highGrowth)
    }

    @Test
    fun housingCapsOnlyNewGrowthAndResidentialExpandsCapacity() {
        val initial = human().copy(day = 6, city = human().city.copy(housingCapacity = human().population.total + 5))
        assertEquals(initial.population.total + 5, EconomyEngine.day(initial).population.total)
        val developed = initial.copy(realm = initial.realm.copy(buildings = initial.realm.buildings + (BuildingType.RESIDENTIAL to 1)))
        assertTrue(CityEngine.ensureCapacity(developed).city.housingCapacity > initial.city.housingCapacity)
    }

    @Test
    fun overflowDoesNotTrimExistingStockAndBreakdownExplainsLostOutput() {
        val initial = human().copy(resources = Resources(20010, 24000, 15990, 16000, 12000))
        val detail = EconomyEngine.breakdown(initial, ResourceKind.WOOD)
        assertEquals(340, detail.overflow)
        val next = EconomyEngine.day(initial)
        assertEquals(20010, next.resources.gold)
        assertEquals(24000, next.resources.food)
        assertEquals(16000, next.resources.wood)
        assertEquals(16000, next.resources.stone)
    }

    @Test
    fun marketRejectsOverflowAndCannotGenerateGoldByRoundTrip() {
        val initial = human()
        val purchased = CityEngine.trade(initial, ResourceKind.WOOD, 100, buy = true).state
        assertEquals(initial.resources.wood + 100, purchased.resources.wood)
        assertEquals(initial.resources.gold - 300, purchased.resources.gold)
        val sold = CityEngine.trade(purchased, ResourceKind.WOOD, 100, buy = false).state
        assertEquals(initial.resources.wood, sold.resources.wood)
        assertTrue(sold.resources.gold < initial.resources.gold)
        val full = initial.copy(resources = initial.resources.copy(wood = initial.city.storageCapacity.wood))
        assertEquals(full, CityEngine.trade(full, ResourceKind.WOOD, 1, buy = true).state)
        assertEquals(initial, CityEngine.trade(initial, ResourceKind.IRON, Int.MAX_VALUE, buy = true).state)
    }

    @Test
    fun newBuildingsHaveRealCapacityMoraleAndCavalryEffects() {
        val initial = human()
        val developed = initial.copy(realm = initial.realm.copy(buildings = initial.realm.buildings +
            (BuildingType.WAREHOUSE to 1) + (BuildingType.HOSPITAL to 1) +
            (BuildingType.STABLES to 1) + (BuildingType.EMBASSY to 1)))
        assertEquals(360, EconomyEngine.upkeep(developed))
        assertEquals(420, EconomyEngine.production(developed).gross.gold)
        val next = CityEngine.tick(developed)
        assertEquals(initial.city.storageCapacity.wood + 5000, next.city.storageCapacity.wood)
        assertEquals(initial.armyPools.first().morale + 1, next.armyPools.first().morale)
    }

    @Test
    fun normalTaxesDoNotPunishASettlementFedByTodaysProduction() {
        val initial = human().copy(resources = human().resources.copy(food = 0))
        assertTrue(EconomyEngine.production(initial).gross.food > EconomyEngine.upkeep(initial))
        assertEquals(50, CityEngine.tick(initial).city.satisfaction)
    }

    @Test
    fun hospitalRestoresOnlyWeightedHomeTroopsAndLeavesMissionQualityAlone() {
        val initial = human().copy(
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100, morale = 60)),
            realm = human().realm.copy(buildings = human().realm.buildings + (BuildingType.HOSPITAL to 3)),
            activeMissions = listOf(ActiveMission(1, MissionType.PATROL, null,
                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 40)), 1, 1, 40, 1,
                quality = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 40, morale = 60)))),
        )
        val healed = CityEngine.tick(initial)
        assertEquals(61, healed.armyPools.single().morale) // 3 * 60/100, integer pool precision
        assertEquals(initial.activeMissions, healed.activeMissions)
        val allAway = initial.copy(activeMissions = initial.activeMissions.map { it.copy(units = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100))) })
        assertEquals(60, CityEngine.tick(allAway).armyPools.single().morale)
        val nearFull = initial.copy(armyPools = initial.armyPools.map { it.copy(morale = 99) })
        assertEquals(99, CityEngine.tick(nearFull).armyPools.single().morale)
    }

    @Test
    fun housingAndWarehouseUpgradesExpandGrandfatheredCapacityImmediately() {
        val migrated = human().copy(
            city = human().city.copy(housingCapacity = 80000, storageCapacity = Resources(90000, 90000, 90000, 90000, 90000)),
        )
        val housed = CityEngine.tick(CityEngine.startConstruction(migrated, BuildingType.RESIDENTIAL).state)
        assertEquals(80600, housed.city.housingCapacity)
        val stored = CityEngine.tick(CityEngine.startConstruction(migrated, BuildingType.WAREHOUSE).state)
        ResourceKind.entries.forEach { assertEquals(95000, it.value(stored.city.storageCapacity)) }
    }

    @Test
    fun ownedRegionsAndCoRegentProduceVisibleBonuses() {
        val initial = human()
        val developed = initial.copy(regions = initial.regions.map { it.copy(owned = true) }, companion = CompanionProfile(met = true, role = "Mitregentin", diplomacy = 48))
        assertEquals(295, EconomyEngine.production(developed).gross.iron)
        assertEquals(450, EconomyEngine.production(developed).gross.wood)
        assertEquals(497, EconomyEngine.production(developed).gross.gold)
        assertEquals(24, EconomyEngine.breakdown(developed, ResourceKind.GOLD).eventBonus)
    }
}
