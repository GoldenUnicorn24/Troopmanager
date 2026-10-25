package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Actual resource and soldier conservation through the existing daily engines. */
class EconomyStabilityTest {
    private fun game() = GameEngine.newGame("Mauerwacht", 24, Species.HUMAN, null).copy(day = 2)

    private fun trader(harbor: Boolean = false, gold: Int = 1000, goldCapacity: Int = 20000): GameState {
        val base = game()
        return base.copy(
            resources = base.resources.copy(gold = gold, wood = 10000),
            city = base.city.copy(storageCapacity = base.city.storageCapacity.copy(gold = goldCapacity)),
            crown = base.crown.copy(partners = base.crown.partners.map {
                it.copy(unlocked = it.id == "saltmere", routes = if (it.id == "saltmere") 1 else 0)
            }),
            reign = base.reign.copy(wonders = if (harbor)
                listOf(WonderProject(WonderKind.GREAT_HARBOR, 0, 1, finished = true)) else emptyList()),
        )
    }

    @Test fun harborBonusIsCreditedAsGoldAndMatchesTheTradeReport() {
        val normal = CrownEngine.tick(trader())
        val before = trader(harbor = true)
        val next = CrownEngine.tick(before)
        val expectedGold = (normal.crown.partner("saltmere")!!.lastIncome * 1.35).toInt()
        assertTrue(expectedGold > normal.crown.lastTradeIncome)
        assertEquals(expectedGold, next.resources.gold - before.resources.gold)
        assertEquals(expectedGold, next.crown.lastTradeIncome)
        assertEquals(expectedGold, next.crown.partner("saltmere")!!.lastIncome)
        assertEquals(expectedGold.toLong(), next.crown.partner("saltmere")!!.totalProfit)
        assertEquals(next, SaveCodec.decode(SaveCodec.encode(next)))
    }

    @Test fun tradeReportsOnlyGoldThatFitsInTheTreasury() {
        val before = trader(harbor = true, gold = 19990)
        val next = CrownEngine.tick(before)
        assertEquals(20000, next.resources.gold)
        assertEquals(10, next.crown.lastTradeIncome)
        assertEquals(10, next.crown.partner("saltmere")!!.lastIncome)
        assertEquals(10L, next.crown.partner("saltmere")!!.totalProfit)
    }

    @Test fun tradeAtTheIntegerLimitDoesNotReportUncreditedGold() {
        val before = trader(harbor = true, gold = Int.MAX_VALUE - 4, goldCapacity = Int.MAX_VALUE)
        val next = CrownEngine.tick(before)
        assertEquals(Int.MAX_VALUE, next.resources.gold)
        assertEquals(4, next.crown.lastTradeIncome)
        assertEquals(4L, next.crown.partner("saltmere")!!.totalProfit)
    }

    private fun unpaidContracts(): GameState {
        var state = game().let { it.copy(resources = it.resources.copy(gold = 20000)) }
        state = CrownEngine.hire(state, "graue_kompanie").state
        state = CrownEngine.hire(state, "graue_kompanie").state
        assertEquals(2, state.crown.mercenaries.size)
        return state.copy(resources = state.resources.copy(gold = 0))
    }

    @Test fun duplicatedMercenaryCompaniesLoseExactlyTheirCombinedDeserters() {
        val before = unpaidContracts()
        val next = CrownEngine.tick(before)
        assertEquals(50, before.soldiers(UnitType.HUMAN_SWORD) - next.soldiers(UnitType.HUMAN_SWORD))
        assertEquals(listOf(475, 475), next.crown.mercenaries.map { it.soldiers })
        assertEquals(50, before.population.human - next.population.human)
        assertEquals(next, SaveCodec.decode(SaveCodec.encode(next)))
    }

    @Test fun unequalContractsOfOneTypeKeepTheirOwnDesertionAmounts() {
        val base = unpaidContracts()
        val before = base.copy(crown = base.crown.copy(mercenaries = base.crown.mercenaries.mapIndexed { index, c ->
            if (index == 0) c.copy(soldiers = 20) else c
        }))
        val next = CrownEngine.tick(before)
        assertEquals(26, before.soldiers(UnitType.HUMAN_SWORD) - next.soldiers(UnitType.HUMAN_SWORD))
        assertEquals(listOf(19, 475), next.crown.mercenaries.map { it.soldiers })
        assertEquals(26, before.population.human - next.population.human)
    }

    @Test fun successiveUnpaidDaysConservePoolContractAndPopulationLosses() {
        var state = unpaidContracts()
        repeat(3) {
            val next = CrownEngine.tick(state)
            val contractLoss = state.crown.mercenaries.sumOf { it.soldiers } - next.crown.mercenaries.sumOf { it.soldiers }
            assertEquals(contractLoss, state.soldiers(UnitType.HUMAN_SWORD) - next.soldiers(UnitType.HUMAN_SWORD))
            assertEquals(contractLoss, state.population.human - next.population.human)
            assertTrue(contractLoss > 0)
            state = next.copy(day = next.day + 1)
        }
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    private fun contractedBattle(): GameState {
        val state = unpaidContracts().let { it.copy(resources = it.resources.copy(gold = 20000)) }
        return BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, seed = 182, enemyStrength = 300).state
    }

    @Test fun hiringCannotChangeTheArmyDuringCombatOrPursuit() {
        val base = contractedBattle()
        val company = CrownCatalog.company("graue_kompanie")!!
        for (status in listOf(BattleStatus.ACTIVE, BattleStatus.PURSUIT)) {
            val state = base.copy(battleSession = base.battleSession!!.copy(status = status))
            assertNotNull(CrownEngine.companyBlocker(state, company))
            assertSame(state, CrownEngine.hire(state, company.id).state)
        }
    }

    @Test fun dismissalCannotRemoveDeployedSoldiersDuringCombatOrPursuit() {
        val base = contractedBattle()
        val contract = base.crown.mercenaries.first()
        for (status in listOf(BattleStatus.ACTIVE, BattleStatus.PURSUIT)) {
            val state = base.copy(battleSession = base.battleSession!!.copy(status = status))
            assertSame(state, CrownEngine.dismiss(state, contract.id).state)
        }
    }

    @Test fun mercenaryHiringAndDismissalRemainAvailableAfterTheBattleEnds() {
        val ended = BattleEngine.order(contractedBattle(), BattleDecision.ORDERED_RETREAT, BattleSection.CENTER).state
        assertFalse(ended.battleSession!!.isActive)
        val hired = CrownEngine.hire(ended, "graue_kompanie").state
        assertEquals(ended.armySize + 500, hired.armySize)
        val dismissed = CrownEngine.dismiss(hired, hired.crown.mercenaries.last().id).state
        assertEquals(ended.armySize, dismissed.armySize)
        assertEquals(ended.crown.mercenaries, dismissed.crown.mercenaries)
        assertEquals(dismissed, SaveCodec.decode(SaveCodec.encode(dismissed)))
    }

    @Test fun largeWoodReservesDoNotDisableOtherwiseIdenticalArsenalProduction() {
        val base = game().copy(
            realm = Realm(buildings = mapOf(BuildingType.ARSENAL to 1, BuildingType.IRONWORKS to 1, BuildingType.SAWMILL to 1)),
            militaryStock = MilitaryStock(0, 0, 0, 0, 0, 0, 0, 0, 0),
            resources = Resources(gold = 10000, food = 10000, wood = 10000, stone = 10000, iron = 10000),
        )
        val moderate = MilitaryEconomyEngine.tick(base)
        assertTrue(moderate.militaryStock.swords > 0)
        for (wood in listOf(61356676, 100000000, Int.MAX_VALUE)) {
            val abundant = MilitaryEconomyEngine.tick(base.copy(resources = base.resources.copy(wood = wood)))
            assertEquals("Production with $wood wood", moderate.militaryStock, abundant.militaryStock)
            assertEquals(base.resources.wood - moderate.resources.wood, wood - abundant.resources.wood)
            assertTrue(abundant.resources.wood >= 0 && abundant.resources.iron >= 0)
        }
    }
}
