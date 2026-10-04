package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V062FeaturesTest {
    private fun humanStart() =
        GameEngine.newGame(
            "Leon",
            23,
            Species.HUMAN,
            null,
            startingCultures = setOf(Culture.HUMAN),
        )

    @Test
    fun recruitmentConsumesExplicitMilitaryGoodsInsteadOfRawIron() {
        val state = humanStart()
        val beforeStock = state.militaryStock
        val beforeIron = state.resources.iron
        val result = GameEngine.recruit(state, UnitType.HUMAN_SWORD, 10).state
        val amount = result.trainingQueue.single().amount

        assertEquals(16, amount)
        assertEquals(beforeIron, result.resources.iron)
        assertEquals(beforeStock.swords - amount, result.militaryStock.swords)
        assertEquals(beforeStock.armor - amount, result.militaryStock.armor)
        assertEquals(beforeStock.shields - amount, result.militaryStock.shields)
    }

    @Test
    fun recruitmentIsBlockedWhenRequiredEquipmentIsMissing() {
        val state =
            humanStart().copy(
                militaryStock =
                    MilitaryStock(
                        swords = 0,
                        spears = 0,
                        bows = 0,
                        arrows = 0,
                        armor = 0,
                        shields = 0,
                        horses = 0,
                        siegeParts = 0,
                        medicine = 0,
                    )
            )
        val result = GameEngine.recruit(state, UnitType.HUMAN_SWORD, 10)

        assertEquals(state, result.state)
        assertTrue(result.message.contains("Militärgüter fehlen"))
    }

    @Test
    fun nonFoundingCultureNeedsRealPopulationBaseBeforeTrainingUnlocks() {
        val state = humanStart()
        val tinyMinority =
            state.copy(population = state.population.copy(goldElf = 1, goldElfRecruits = 1))
        val established =
            state.copy(population = state.population.copy(goldElf = 60, goldElfRecruits = 10))

        assertFalse(GameEngine.isUnitUnlocked(tinyMinority, UnitType.GOLD_SPEAR))
        assertTrue(GameEngine.isUnitUnlocked(established, UnitType.GOLD_SPEAR))
    }

    @Test
    fun originsHaveDistinctBonusesWithoutRestrictingStartingCultures() {
        val human =
            GameEngine.newGame(
                "Leon",
                23,
                Species.HUMAN,
                null,
                startingCultures = setOf(Culture.HUMAN, Culture.GOLD_ELF),
            )
        val elf =
            GameEngine.newGame(
                "Leon",
                23,
                Species.ELF,
                null,
                startingCultures = setOf(Culture.HUMAN, Culture.GOLD_ELF),
            )
        val halfElf =
            GameEngine.newGame(
                "Leon",
                23,
                Species.HALF_ELF,
                null,
                startingCultures = setOf(Culture.HUMAN, Culture.GOLD_ELF),
            )

        assertEquals(human.foundingCultures, elf.foundingCultures)
        assertEquals(elf.foundingCultures, halfElf.foundingCultures)
        assertTrue(EconomyEngine.production(human).gross.gold > EconomyEngine.production(elf).gross.gold)
        assertEquals(8, OriginEngine.diplomacyBonus(halfElf))
        assertEquals(1.10, OriginEngine.forestMarchFactor(elf, WorldTerrain.FOREST), 0.0001)
    }

    @Test
    fun doctrineAndResearchModifyRealSystems() {
        val base =
            humanStart().copy(
                realm =
                    humanStart().realm.copy(
                        buildings =
                            humanStart().realm.buildings +
                                (BuildingType.ACADEMY to 2) +
                                (BuildingType.STABLES to 1)
                    )
            )
        val doctrine = DoctrineEngine.set(base, MilitaryDoctrine.MASS_ARMY).state
        assertEquals(MilitaryDoctrine.MASS_ARMY, doctrine.doctrine)
        assertTrue(
            GameEngine.trainingDays(doctrine, UnitType.KNIGHT) <
                GameEngine.trainingDays(base, UnitType.KNIGHT)
        )

        val research = ResearchEngine.start(base, ResearchTech.SUPPLY_TRAINS).state
        assertNotNull(research.research.active)
        var completed = research
        repeat(research.research.active!!.remainingDays) {
            completed = ResearchEngine.tick(completed)
        }
        assertTrue(ResearchTech.SUPPLY_TRAINS in completed.research.completed)
        assertTrue(EconomyEngine.upkeep(completed) < EconomyEngine.upkeep(base))
    }

    @Test
    fun battleLoadsAndConsumesArrowSupplies() {
        val base =
            humanStart().copy(
                armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 100)),
                population = Population(human = 500),
                commanderAssignments = emptyList(),
                militaryStock = humanStart().militaryStock.copy(arrows = 10),
            )
        val started =
            BattleEngine.start(
                base,
                EnemyType.ORC,
                Tactic.RANGED,
                deployments =
                    listOf(
                        BattleDeployment(
                            null,
                            BattleSection.CENTER,
                            listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 100)),
                        )
                    ),
                seed = 620,
                enemyStrength = 50,
            ).state

        assertEquals(0, started.militaryStock.arrows)
        assertEquals(0.025, started.battleSession!!.rangedSupplyFactor, 0.0001)
        assertEquals(10, started.battleSession!!.battleArrowsRemaining)
    }

    @Test
    fun dailyAdvanceProducesCommandReport() {
        val next = GameEngine.advanceDay(humanStart()).state
        assertEquals(next.day, next.dailyReport.day)
        assertTrue(next.dailyReport.entries.isNotEmpty())
    }
}
