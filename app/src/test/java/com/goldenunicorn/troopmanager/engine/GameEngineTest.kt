package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    private fun human() = GameEngine.newGame("Leon", 23, Species.HUMAN, null)

    private fun allocation(type: UnitType, count: Int) = listOf(UnitAllocation(type, count))

    private fun day(state: GameState, roll: Double = 1.0) =
        MissionEngine.tick(EconomyEngine.day(state), roll)

    @Test
    fun everyOriginStartsWithLandBuildingsResourcesAndCorrectArmy() {
        val sizes = mapOf(Species.HUMAN to 330, Species.ELF to 290, Species.HALF_ELF to 380)
        Species.entries.forEach { species ->
            val state = GameEngine.newGame("Leon", 23, species, null)
            assertEquals(1, state.realm.territory)
            assertEquals("Grenzfeste", state.realm.settlementName)
            assertEquals(sizes.getValue(species), state.armySize)
            assertEquals(5000, state.resources.gold)
            assertEquals(8000, state.resources.food)
            assertEquals(3500, state.resources.wood)
            assertEquals(2500, state.resources.stone)
            assertEquals(1500, state.resources.iron)
            assertEquals(2, state.realm.level(BuildingType.FARM))
            assertEquals(2, state.realm.level(BuildingType.BARRACKS))
            BuildingType.entries
                .filter { it != BuildingType.FARM && it != BuildingType.BARRACKS }
                .forEach { assertEquals(if(it.ordinal<=BuildingType.PALACE.ordinal) 1 else 0, state.realm.level(it)) }
            assertEquals(state.armyPools.size, state.armyPools.map { it.type }.distinct().size)
            assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
        }
        assertEquals(180, human().soldiers(UnitType.HUMAN_SWORD))
        assertEquals(120, human().soldiers(UnitType.HUMAN_ARCHER))
        assertEquals(30, human().soldiers(UnitType.KNIGHT))
    }

    @Test
    fun dailyProductionMatchesSpecifiedRatesAndIncludesMaintenance() {
        val state = human()
        val p = EconomyEngine.production(state)
        assertEquals(Resources(375, 800, 350, 300, 220), p.gross)
        assertEquals(390, p.upkeep)
        assertEquals(410, p.net.food)
        val after = EconomyEngine.day(state)
        assertEquals(state.resources.gold + 375, after.resources.gold)
        assertEquals(state.resources.food + 410, after.resources.food)
        assertEquals(state.resources.stone + 300, after.resources.stone)
    }

    @Test
    fun quarryUpgradeAddsOneHundredStonePerDay() {
        val before = human()
        val queued = GameEngine.build(before, BuildingType.QUARRY).state
        assertEquals(before.realm.level(BuildingType.QUARRY), queued.realm.level(BuildingType.QUARRY))
        assertEquals(1, queued.city.constructionQueue.size)
        val after = GameEngine.advanceDay(queued).state
        assertEquals(
            100,
            EconomyEngine.production(after).gross.stone -
                EconomyEngine.production(before).gross.stone,
        )
        assertTrue(after.resources.gold < before.resources.gold)
    }

    @Test
    fun recruitingReducesCivilianWorkersWhenReserveRunsOut() {
        var state = human().copy(population = Population(330 + 120 + 50, 0, 0, 0, 120, 0, 0, 0))
        assertEquals(50, state.workers)
        val after = GameEngine.recruit(state, UnitType.HUMAN_SWORD, 100).state
        assertEquals(120, after.trainingSize)
        assertEquals(50, after.workers)
        assertEquals(state.population.total, after.population.total)
        assertEquals(state.civilianPopulation - 120, after.civilianPopulation)
        assertTrue(EconomyEngine.production(after).gross.stone < 300)
        assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
    }

    @Test
    fun trainingMergesIntoExistingPoolRatherThanCreatingDuplicates() {
        var state = GameEngine.recruit(human(), UnitType.HUMAN_SWORD, 50).state
        assertEquals(60, state.trainingSize)
        repeat(3) { state = EconomyEngine.day(state) }
        assertEquals(240, state.soldiers(UnitType.HUMAN_SWORD))
        assertEquals(1, state.armyPools.count { it.type == UnitType.HUMAN_SWORD })
        assertTrue(state.trainingQueue.isEmpty())
    }

    @Test
    fun starvationReducesMoraleSlowsTrainingAndBlocksPopulationGrowth() {
        val state =
            human()
                .copy(
                    day = 6,
                    resources = human().resources.copy(food = 0),
                    armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 2000)),
                    population = Population(2500, 0, 0, 0, 20, 0, 0, 0),
                    trainingQueue = listOf(TrainingOrder(2, UnitType.HUMAN_ARCHER, 10, 3)),
                )
        val after = EconomyEngine.day(state)
        assertEquals(0, after.resources.food)
        assertEquals(72, after.armyPools.first().morale)
        assertEquals(3, after.trainingQueue.first().daysRemaining)
        assertEquals(state.population, after.population)
        val even = EconomyEngine.day(after)
        assertEquals(2, even.trainingQueue.first().daysRemaining)
    }

    @Test
    fun emptyCultureDoesNotUnlockJustFromRenown() {
        val state = human().copy(renown = 50000)
        assertFalse(GameEngine.isUnitUnlocked(state, UnitType.WOOD_RANGER))
        val event = EventEngine.catalogue.first { it.key == "wood_envoy" }
        val allied = EventEngine.choose(state.copy(pendingRealmEvent = event), 0).state
        assertEquals(250, allied.population.woodElf)
        assertEquals(40, allied.population.woodElfRecruits)
        assertTrue(GameEngine.isUnitUnlocked(allied, UnitType.WOOD_RANGER))
    }

    @Test
    fun commandersOwnSpecificAmountsAndCannotDoubleAssign() {
        var state =
            human()
                .copy(
                    commanders = human().commanders + Commander(2, "Edric", Culture.HUMAN, "knight")
                )
        state =
            GameEngine.setCommanderAllocation(state, 1, allocation(UnitType.HUMAN_SWORD, 100)).state
        state =
            GameEngine.setCommanderAllocation(state, 2, allocation(UnitType.HUMAN_SWORD, 60)).state
        assertEquals(20, state.directCommand(UnitType.HUMAN_SWORD))
        assertEquals(
            state,
            GameEngine.setCommanderAllocation(state, 2, allocation(UnitType.HUMAN_SWORD, 81)).state,
        )
        assertEquals(80, GameEngine.freeSoldiersForCommander(state, 2, UnitType.HUMAN_SWORD))
        assertEquals(
            state,
            GameEngine.setCommanderAllocation(state, 2, allocation(UnitType.HUMAN_SWORD, -1)).state,
        )
    }

    @Test
    fun duplicateInputRowsAggregateAndRespectAvailableCount() {
        val state = human()
        val after =
            GameEngine.setCommanderAllocation(
                    state,
                    1,
                    listOf(
                        UnitAllocation(UnitType.HUMAN_SWORD, 40),
                        UnitAllocation(UnitType.HUMAN_SWORD, 60),
                    ),
                )
                .state
        assertEquals(100, after.assignedTo(1, UnitType.HUMAN_SWORD))
        assertEquals(1, after.commanderAssignments.first().units.size)
        assertEquals(
            state,
            GameEngine.setCommanderAllocation(
                    state,
                    1,
                    listOf(
                        UnitAllocation(UnitType.HUMAN_SWORD, Int.MAX_VALUE),
                        UnitAllocation(UnitType.HUMAN_SWORD, Int.MAX_VALUE),
                    ),
                )
                .state,
        )
    }

    @Test
    fun patrolConsumesSupplyAndCannotRewardBeforeNextDay() {
        val before = human()
        val started =
            MissionEngine.start(
                    before,
                    MissionType.PATROL,
                    1,
                    allocation(UnitType.HUMAN_SWORD, 150),
                )
                .state
        assertEquals(1, started.activeMissions.size)
        assertEquals(before.resources.food - 300, started.resources.food)
        assertEquals(before.resources.gold, started.resources.gold)
        assertEquals(before.renown, started.renown)
        assertEquals(150, started.awayArmySize)
        assertEquals(180, started.homeArmySize)
        assertEquals(
            started,
            GameEngine.setCommanderAllocation(started, 1, allocation(UnitType.HUMAN_ARCHER, 20))
                .state,
        )
        assertEquals(
            started,
            MissionEngine.start(
                    started,
                    MissionType.PATROL,
                    1,
                    allocation(UnitType.HUMAN_ARCHER, 50),
                )
                .state,
        )
        assertEquals(
            started,
            MissionEngine.start(
                    started,
                    MissionType.PATROL,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 50),
                )
                .state,
        )
        assertEquals(started, SaveCodec.decode(SaveCodec.encode(started)))
        val ended = day(started, 0.55)
        assertEquals(MissionStatus.COMPLETE, ended.activeMissions.first().status)
        assertEquals(0, ended.awayArmySize)
        assertTrue(ended.activeMissions.first().losses > 0)
        assertTrue(ended.activeMissions.first().reward.gold > 0)
        assertTrue(ended.renown > started.renown)
        assertEquals(before.armySize - ended.activeMissions.first().losses, ended.armySize)
        assertEquals(ended, SaveCodec.decode(SaveCodec.encode(ended)))
    }

    @Test
    fun commanderCannotStealAnotherCommandersTroopsForMission() {
        val state =
            GameEngine.setCommanderAllocation(human(), 1, allocation(UnitType.HUMAN_SWORD, 150))
                .state
        assertEquals(
            state,
            MissionEngine.start(
                    state,
                    MissionType.PATROL,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 50),
                )
                .state,
        )
        val sent =
            MissionEngine.start(state, MissionType.PATROL, 1, allocation(UnitType.HUMAN_SWORD, 150))
                .state
        assertEquals(0, sent.assigned(UnitType.HUMAN_SWORD))
        val end = day(sent, 1.0)
        assertEquals(
            150 - end.activeMissions.first().losses,
            end.assignedTo(1, UnitType.HUMAN_SWORD),
        )
    }

    @Test
    fun twoDayMissionReturnsOnlyAfterTwoTransitionsAndRewardOnce() {
        var state =
            MissionEngine.start(
                    human(),
                    MissionType.ESCORT,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 150),
                )
                .state
        state = day(state, 1.2)
        assertEquals(MissionStatus.ACTIVE, state.activeMissions.first().status)
        assertEquals(1, state.activeMissions.first().remainingDays)
        state = day(state, 1.2)
        assertFalse(state.activeMissions.first().status.isAway)
        val repeatTick = MissionEngine.tick(state, 1.2)
        assertEquals(state, repeatTick)
    }

    @Test
    fun catastropheCausesRealLossesAndNoLoot() {
        val state =
            MissionEngine.start(
                    human(),
                    MissionType.PATROL,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 50),
                )
                .state
        val end = day(state, 0.1)
        assertEquals(MissionOutcome.CATASTROPHIC, end.activeMissions.first().outcome)
        assertEquals(10, end.activeMissions.first().losses)
        assertEquals(320, end.armySize)
        assertEquals(0, end.activeMissions.first().reward.gold)
        assertEquals(state.population.total - 10, end.population.total)
    }

    @Test
    fun recallHasTravelTimeAndNoLoot() {
        val sent =
            MissionEngine.start(
                    human(),
                    MissionType.ESCORT,
                    1,
                    allocation(UnitType.HUMAN_SWORD, 150),
                )
                .state
        val recalled = MissionEngine.recall(sent, sent.activeMissions.first().id).state
        assertEquals(MissionStatus.RETURNING, recalled.activeMissions.first().status)
        assertEquals(150, recalled.awayArmySize)
        val returned = day(recalled)
        assertEquals(0, returned.awayArmySize)
        assertEquals(0, returned.activeMissions.first().reward.gold)
        assertEquals(330, returned.armySize)
        assertEquals(150, returned.assignedTo(1, UnitType.HUMAN_SWORD))
    }

    @Test
    fun missionRejectsInvalidTooSmallUnaffordableAndWrongRegion() {
        val state = human()
        assertEquals(
            state,
            MissionEngine.start(
                    state,
                    MissionType.BANDITS,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 100),
                )
                .state,
        )
        assertEquals(
            state,
            MissionEngine.start(
                    state,
                    MissionType.PATROL,
                    null,
                    allocation(UnitType.HUMAN_SWORD, -100),
                )
                .state,
        )
        val poor = state.copy(resources = state.resources.copy(food = 0))
        assertEquals(
            poor,
            MissionEngine.start(
                    poor,
                    MissionType.PATROL,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 100),
                )
                .state,
        )
        assertEquals(
            state,
            MissionEngine.start(
                    state,
                    MissionType.PATROL,
                    null,
                    allocation(UnitType.HUMAN_SWORD, 100),
                    "forest",
                )
                .state,
        )
    }

    @Test
    fun threatAnnouncesInvasionAndGuaranteesSixDayPreparation() {
        var state = human().copy(realm = human().realm.copy(threat = 89))
        state = GameEngine.advanceDay(state).state
        assertNotNull(state.invasion)
        val arrival = state.invasion!!.arrivalDay
        assertEquals(state.day + 6, arrival)
        while (state.day < arrival - 1) {
            state = GameEngine.advanceDay(state).state
            assertNull(state.battleSession)
        }
        state = GameEngine.advanceDay(state).state
        assertEquals(arrival, state.day)
        assertEquals(100, state.realm.threat)
        assertTrue(state.battleSession!!.isActive)
        assertEquals(state, GameEngine.advanceDay(state).state)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun raidsActuallyCostResources() {
        val state = human().copy(realm = human().realm.copy(threat = 59))
        val after = InvasionEngine.day(state)
        assertEquals(state.resources.gold - 150, after.resources.gold)
        assertEquals(state.resources.food - 200, after.resources.food)
    }

    @Test
    fun absentArmyCannotDefendButDoesNotLoseMissionTroops() {
        val before =
            human()
                .copy(
                    activeMissions =
                        listOf(
                            ActiveMission(
                                1,
                                MissionType.HUNT,
                                null,
                                human().armyPools.map { UnitAllocation(it.type, it.soldiers) },
                                1,
                                3,
                                1980,
                                3,
                            )
                        ),
                    invasion = Invasion(EnemyType.URUK, 1, 400, 1),
                )
        val after = InvasionEngine.day(before)
        assertEquals(330, after.armySize)
        assertEquals(0, after.homeArmySize)
        assertEquals(1, after.defeats)
        assertNull(after.invasion)
        assertTrue(after.realm.wallIntegrity < before.realm.wallIntegrity)
        assertTrue(after.resources.gold < before.resources.gold)
    }

    @Test
    fun relationshipLimitsTwoSmallOrOneLargeActionPerDay() {
        var state = human()
        repeat(4) { state = GameEngine.advanceDay(state).state }
        assertTrue(state.companion.met)
        assertTrue(state.commanders.any { it.id == COMPANION_COMMANDER_ID })
        state = GameEngine.companionAction(state, "talk").state
        state = GameEngine.companionAction(state, "train").state
        assertEquals(state, GameEngine.companionAction(state, "talk").state)
        state = GameEngine.advanceDay(state).state
        val large = GameEngine.companionAction(state, "command").state
        assertEquals(2, large.relationship.spentActions)
        assertEquals(large, GameEngine.companionAction(large, "talk").state)
        assertEquals(
            large.companion.leadership,
            large.commanders.first { it.id == COMPANION_COMMANDER_ID }.leadership,
        )
    }

    @Test
    fun relationshipDecisionCannotBeRepeatedOrBypassDailyBudget() {
        var state =
            RelationshipEngine.syncCommander(human().copy(companion = CompanionProfile(met = true)))
        state = RelationshipEngine.onEvent(state, "defeat")
        val end = RelationshipEngine.choose(state, 0).state
        assertNull(end.relationship.pendingEvent)
        assertEquals(end, RelationshipEngine.choose(end, 0).state)
        assertEquals(end, GameEngine.companionAction(end, "talk").state)
    }

    @Test
    fun customSettlementNameSurvivesBuildExpansionAndSave() {
        var state = renameSettlement(human(), "Sternwacht")
        state = GameEngine.build(state, BuildingType.PALACE).state
        state = GameEngine.buyLand(state).state
        assertEquals("Sternwacht", state.realm.settlementName)
        assertEquals(SettlementTier.CASTLE, state.realm.settlementTier)
        assertEquals("Sternwacht", SaveCodec.decode(SaveCodec.encode(state)).realm.settlementName)
    }

    @Test
    fun levelUpGivesThreeSpendablePoints() {
        val state = ProgressionEngine.awardXp(human(), 100)
        assertEquals(2, state.player.level)
        assertEquals(3, state.player.skillPoints)
        val end = ProgressionEngine.spendPoint(state, "leadership").state
        assertEquals(state.player.leadership + 1, end.player.leadership)
        assertEquals(2, end.player.skillPoints)
        assertEquals(end, ProgressionEngine.spendPoint(end, "unknown").state)
    }

    @Test
    fun eventCatalogueHasThirtyTwoFunctionalChoicesAndCannotBeSpammed() {
        assertEquals(32, EventEngine.catalogue.size)
        assertEquals(8, EventEngine.catalogue.map { it.category }.distinct().size)
        EventEngine.catalogue.forEach { event ->
            val before = human().copy(pendingRealmEvent = event)
            assertEquals(3, EventEngine.choices(event).size)
            val after = EventEngine.choose(before, 0).state
            assertNull(event.title, after.pendingRealmEvent)
            assertEquals(after, EventEngine.choose(after, 0).state)
            assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
        }
    }

    @Test
    fun equipmentRepairCostsResourcesAndImprovesPower() {
        val before = human()
        val after = ArmyEngine.repairEquipment(before, UnitType.HUMAN_SWORD).state
        assertTrue(after.resources.iron < before.resources.iron)
        assertTrue(after.armyPower > before.armyPower)
    }

    @Test
    fun halfElfAllCulturesAndEarlyCommanderStillWork() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        UnitType.entries.forEach { assertTrue(GameEngine.isUnitUnlocked(state, it)) }
        assertEquals(1, state.commanders.size)
        assertEquals(2, GameEngine.promoteCommander(state).state.commanders.size)
        assertTrue(GameEngine.markTutorialSeen(state).tutorialSeen)
    }
}
