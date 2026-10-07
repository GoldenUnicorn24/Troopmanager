package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class CampaignIntegrationTest {
    @Test
    fun sixtyDayCampaignsRemainSaveableAcrossMissionsInvasionsAndBattles() {
        Species.entries.forEach { species ->
            var state = DiplomacyEngine.declareWar(GameEngine.newGame("Leon", 23, species, null), "ash_covenant").state
            state = GameEngine.recruit(state, state.armyPools.first().type, 25).state
            repeat(60) {
                if (state.pendingRealmEvent != null) state = EventEngine.choose(state, 0).state
                if (state.day % 5 == 1 && state.battleSession?.isActive != true) {
                    val type =
                        state.armyPools.firstOrNull { state.directCommand(it.type) >= 75 }?.type
                    if (type != null)
                        state =
                            MissionEngine.start(
                                    state,
                                    MissionType.PATROL,
                                    null,
                                    listOf(UnitAllocation(type, 75)),
                                )
                                .state
                }
                state = GameEngine.advanceDay(state).state
                state = SaveCodec.decode(SaveCodec.encode(state))
                if (state.battleSession != null) {
                    repeat(20) {
                        if (state.battleSession!!.isActive) {
                            val event = state.battleSession!!.pendingEvent
                            val command =
                                when {
                                    event == null -> null
                                    BattleDecision.HOLD_FORMATION in event.options ->
                                        BattleDecision.HOLD_FORMATION
                                    BattleDecision.SEND_RESERVE in event.options ->
                                        BattleDecision.SEND_RESERVE
                                    else -> event.options.first()
                                }
                            state = BattleEngine.advance(state, command).state
                            state = SaveCodec.decode(SaveCodec.encode(state))
                        }
                    }
                    assertFalse("Battle stalled for $species", state.battleSession!!.isActive)
                    state = WorldEngine.reconcileBattle(state).copy(battleSession = null)
                }
            }
            assertEquals(61, state.day)
            assertTrue(state.victories + state.defeats > 0)
            assertTrue(state.activeMissions.any { !it.status.isAway })
            assertTrue(state.armyPools.size <= UnitType.entries.size)
        }
    }

    @Test
    fun simultaneousMissionsReturnAndRestoreOnlyTheirSurvivors() {
        var state = GameEngine.newGame("Leon", 23, Species.HUMAN, null)
        state =
            state.copy(
                commanders = state.commanders + Commander(2, "Edric", Culture.HUMAN, "knight")
            )
        state =
            MissionEngine.start(
                    state,
                    MissionType.PATROL,
                    1,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)),
                )
                .state
        state =
            MissionEngine.start(
                    state,
                    MissionType.PATROL,
                    2,
                    listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 100)),
                )
                .state
        assertEquals(200, state.awayArmySize)
        val totalBefore = state.armySize
        state = MissionEngine.tick(EconomyEngine.day(state), 0.8)
        assertEquals(0, state.awayArmySize)
        assertEquals(totalBefore - state.activeMissions.sumOf { it.losses }, state.armySize)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun smallMissionDoesNotTrainOrDamageOneHundredThousandAbsentSoldiers() {
        val start =
            GameEngine.newGame("Leon", 23, Species.HUMAN, null)
                .copy(
                    population = Population(150000, 0, 0, 0, 120, 0, 0, 0),
                    armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100000)),
                    resources = Resources(food = 1000000),
                )
        val sent =
            MissionEngine.start(
                    start,
                    MissionType.PATROL,
                    null,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 150)),
                )
                .state
        val ended = MissionEngine.tick(EconomyEngine.day(sent), 0.55)
        val pool = ended.armyPools.first()
        assertEquals(15, pool.experience)
        assertEquals(80, pool.morale)
        assertEquals(80, pool.equipment)
        assertTrue(pool.soldiers < 100000)
    }

    @Test
    fun oneHundredThousandSoldiersStayAggregatedThroughSaveAndBattle() {
        var state =
            GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
                .copy(
                    population = Population(100000, 100000, 100000, 100000, 100, 100, 100, 100),
                    armyPools = UnitType.entries.map { ArmyUnitPool(it, 8000) },
                    resources = Resources(1000000, 1000000, 1000000, 1000000, 1000000),
                )
        assertEquals(104000, state.armySize)
        state =
            state.copy(
                commanders =
                    (1L..6L).map { id ->
                        Commander(
                            id = id,
                            name = "Testkommandant $id",
                            culture = UnitType.entries[(id.toInt() - 1) % UnitType.entries.size].culture,
                            portraitKey = "knight",
                        )
                    }
            )
        repeat(6) { index ->
            state =
                MissionEngine.start(
                        state,
                        MissionType.ESCORT,
                        commanderIds = listOf((index + 1).toLong()),
                        playerParticipates = false,
                        units = listOf(UnitAllocation(UnitType.entries[index], 200)),
                    )
                    .state
        }
        assertEquals(1200, state.awayArmySize)
        state =
            BattleEngine.start(
                    state,
                    EnemyType.URUK,
                    Tactic.FORTIFY,
                    seed = 10,
                    enemyStrength = 90000,
                )
                .state
        assertEquals(102800, state.battleSession!!.ownStart)
        repeat(5) {
            state =
                BattleEngine.advance(state, state.battleSession!!.pendingEvent?.options?.first())
                    .state
            val raw = SaveCodec.encode(state)
            assertTrue("Save should contain aggregate quantities (${raw.length} bytes)", raw.length < 100000)
            state = SaveCodec.decode(raw)
        }
        assertEquals(13, state.armyPools.size)
        assertEquals(1200, state.awayArmySize)
        assertTrue(state.battleSession!!.contingents.size < 60)
    }

    @Test
    fun populationGrowthNearIntegerLimitStaysValidAndDoesNotEraseCitizens() {
        val start =
            GameEngine.newGame("Leon", 23, Species.HUMAN, null)
                .copy(day = 6, population = Population(Int.MAX_VALUE - 5, 0, 0, 0, 120, 0, 0, 0), city = CityState(housingCapacity=Int.MAX_VALUE))
        val end = GameEngine.advanceDay(start).state
        assertEquals(Int.MAX_VALUE, end.population.human)
        assertEquals(end, SaveCodec.decode(SaveCodec.encode(end)))
    }

    @Test
    fun enormousEquipmentRepairCannotGrantGoldByPriceOverflow() {
        val start =
            GameEngine.newGame("Leon", 23, Species.HUMAN, null)
                .copy(
                    population = Population(Int.MAX_VALUE, 0, 0, 0, 0, 0, 0, 0),
                    armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 1500000000)),
                    resources = Resources(Int.MAX_VALUE, 8000, 3500, 2500, Int.MAX_VALUE),
                )
        assertEquals(start, ArmyEngine.repairEquipment(start, UnitType.HUMAN_SWORD).state)
    }

    @Test
    fun invasionFormationCanBeChangedOnlyBeforeCombatBegins() {
        val start = GameEngine.newGame("Leon", 23, Species.HUMAN, null)
        var state =
            BattleEngine.start(start, EnemyType.ORC, Tactic.FORTIFY, seed = 23, enemyStrength = 400)
                .state
        val changed =
            listOf(
                BattleDeployment(
                    null,
                    BattleSection.LEFT,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 180)),
                ),
                BattleDeployment(
                    null,
                    BattleSection.RIGHT,
                    listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 120)),
                ),
                BattleDeployment(
                    null,
                    BattleSection.CENTER,
                    listOf(UnitAllocation(UnitType.KNIGHT, 30)),
                ),
            )
        state = BattleEngine.redeploy(state, changed).state
        assertEquals(23, state.battleSession!!.seed)
        assertEquals(400, state.battleSession!!.enemyStart)
        assertEquals(180, state.battleSession!!.soldiers(BattleSection.LEFT))
        assertEquals(start.armyPools, state.armyPools)
        state = BattleEngine.advance(state).state
        assertEquals(state, BattleEngine.redeploy(state, changed).state)
    }

    @Test
    fun cavalryOnlyArmyGetsAFrontAndReserveOnlyManualSetupIsRejected() {
        val state =
            GameEngine.newGame("Leon", 23, Species.HUMAN, null)
                .copy(armyPools = listOf(ArmyUnitPool(UnitType.KNIGHT, 30)))
        assertEquals(BattleSection.CENTER, BattleEngine.defaultDeployments(state).single().section)
        val bad =
            listOf(
                BattleDeployment(
                    null,
                    BattleSection.RESERVE,
                    listOf(UnitAllocation(UnitType.KNIGHT, 30)),
                )
            )
        assertEquals(state, BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, bad).state)
    }

    @Test
    fun losingTwoFlanksCannotBecomeATimedVictoryForStrongCenter() {
        val start =
            GameEngine.newGame("Leon", 23, Species.HUMAN, null)
                .copy(
                    population = Population(6000, 0, 0, 0, 120, 0, 0, 0),
                    armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 5000)),
                )
        val deployment =
            listOf(
                BattleDeployment(
                    null,
                    BattleSection.CENTER,
                    listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 5000)),
                )
            )
        var state =
            BattleEngine.start(
                    start,
                    EnemyType.ORC,
                    Tactic.RANGED,
                    deployment,
                    seed = 19,
                    enemyStrength = 300,
                )
                .state
        repeat(18) {
            if (state.battleSession!!.isActive)
                state =
                    BattleEngine.advance(
                            state,
                            state.battleSession!!.pendingEvent?.options?.first(),
                        )
                        .state
        }
        assertEquals(BattleStatus.DEFEAT, state.battleSession!!.status)
        assertTrue(state.battleSession!!.enemyRemaining > 0)
    }
}
