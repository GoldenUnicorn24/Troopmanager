package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BattleEngineTest {
    private fun army() =
        GameState(
            player = CharacterProfile("Leon"),
            armyPools =
                listOf(
                    ArmyUnitPool(UnitType.HUMAN_SWORD, 300),
                    ArmyUnitPool(UnitType.HUMAN_ARCHER, 200),
                    ArmyUnitPool(UnitType.KNIGHT, 30),
                ),
        )

    @Test
    fun startHasNoPrecomputedOutcomeOrCasualties() {
        val before = army()
        val state = BattleEngine.start(before, EnemyType.ORC, Tactic.HOLD, seed = 42).state
        val session = state.battleSession!!
        assertEquals(BattlePhase.FORMATION, session.phase)
        assertEquals(0, session.minute)
        assertEquals(before.armySize, session.ownRemaining)
        assertEquals(session.enemyStart, session.enemyRemaining)
        assertEquals(before.armyPools, state.armyPools)
        assertEquals(0, state.victories)
        assertEquals(0, state.defeats)
        assertTrue(session.isActive)
        assertEquals(1, session.log.size)
    }

    @Test
    fun oneStepAppliesRealCasualtiesAndPopulationLosses() {
        val before =
            BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 42, enemyStrength = 600)
                .state
        val after = BattleEngine.advance(before).state
        assertEquals(5, after.battleSession!!.minute)
        val casualties = before.armySize - after.armySize
        assertTrue(casualties > 0)
        assertEquals(casualties, before.population.total - after.population.total)
        assertEquals(after.homeArmySize, after.battleSession!!.ownRemaining)
    }

    @Test
    fun persistedSessionContinuesDeterministically() {
        val started = BattleEngine.start(army(), EnemyType.ORC, Tactic.FORTIFY, seed = 128).state
        val first = BattleEngine.advance(started).state
        val reloaded = Json.decodeFromString<GameState>(Json.encodeToString(first))
        assertEquals(BattleEngine.advance(first).state, BattleEngine.advance(reloaded).state)
    }

    @Test
    fun unopposedFrontDoesNotLoseSoldiers() {
        val started =
            BattleEngine.start(army(), EnemyType.ORC, Tactic.HOLD, seed = 19, enemyStrength = 300)
                .state
        val session = started.battleSession!!
        val leftClear =
            started.copy(
                battleSession =
                    session.copy(
                        fronts =
                            session.fronts.map {
                                if (it.section == BattleSection.LEFT) it.copy(enemySoldiers = 0)
                                else it
                            }
                    )
            )
        val after = BattleEngine.advance(leftClear).state.battleSession!!
        assertEquals(session.soldiers(BattleSection.LEFT), after.soldiers(BattleSection.LEFT))
    }

    @Test
    fun eventBlocksAdvanceUntilAListedDecision() {
        var state = BattleEngine.start(army(), EnemyType.ORC, Tactic.HOLD, seed = 25).state
        state = BattleEngine.advance(state).state
        state = BattleEngine.advance(state).state
        assertNotNull(state.battleSession!!.pendingEvent)
        assertEquals(state, BattleEngine.advance(state).state)
        assertEquals(state, BattleEngine.advance(state, BattleDecision.PURSUE).state)
        val after = BattleEngine.advance(state, BattleDecision.HOLD).state
        assertEquals(15, after.battleSession!!.minute)
    }

    @Test
    fun reserveOrderActuallyMovesSoldiersIntoAFront() {
        var state = BattleEngine.start(army(), EnemyType.ORC, Tactic.HOLD, seed = 25).state
        repeat(2) { state = BattleEngine.advance(state).state }
        assertEquals(30, state.battleSession!!.soldiers(BattleSection.RESERVE))
        val section = state.battleSession!!.pendingEvent!!.section
        val queued = BattleEngine.advance(state, BattleDecision.SEND_RESERVE).state
        assertEquals(30, queued.battleSession!!.soldiers(BattleSection.RESERVE))
        assertNotNull(queued.battleSession.reserveReinforcement)
        val after = BattleEngine.advance(queued).state.battleSession!!
        assertEquals(0, after.soldiers(BattleSection.RESERVE))
        assertTrue(after.contingents.any { it.type == UnitType.KNIGHT && it.section == section })
        assertEquals(state.battleSession!!.ownStart, after.contingents.sumOf { it.startSoldiers })
    }

    @Test
    fun missionTroopsCannotEnterBattle() {
        val mission =
            ActiveMission(
                1,
                MissionType.PATROL,
                null,
                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)),
                1,
                1,
                100,
                1,
            )
        val state = army().copy(activeMissions = listOf(mission))
        val bad =
            listOf(
                BattleDeployment(
                    null,
                    BattleSection.CENTER,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300)),
                )
            )
        assertNull(BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, bad).state.battleSession)
        val good = BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, seed = 7).state
        assertEquals(state.homeArmySize, good.battleSession!!.ownStart)
        val after = BattleEngine.advance(good).state
        assertEquals(100, after.away(UnitType.HUMAN_SWORD))
        assertTrue(after.soldiers(UnitType.HUMAN_SWORD) >= 100)
    }

    @Test
    fun commanderAndDirectCommandCannotDoubleDeploySameTroops() {
        val state =
            army()
                .copy(
                    commanders = listOf(Commander(1, "Marcus", Culture.HUMAN, "human")),
                    commanderAssignments =
                        listOf(
                            CommanderAssignment(
                                1,
                                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 250)),
                            )
                        ),
                )
        val duplicate =
            listOf(
                BattleDeployment(
                    1,
                    BattleSection.LEFT,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 200)),
                ),
                BattleDeployment(
                    1,
                    BattleSection.RIGHT,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50)),
                ),
            )
        assertNull(
            BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, duplicate).state.battleSession
        )
        val stealing =
            listOf(
                BattleDeployment(
                    null,
                    BattleSection.CENTER,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)),
                )
            )
        assertNull(
            BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, stealing).state.battleSession
        )
    }

    @Test
    fun commanderBonusDoesNotAffectOtherFronts() {
        val state =
            army()
                .copy(
                    commanders =
                        listOf(
                            Commander(
                                1,
                                "Marcus",
                                Culture.HUMAN,
                                "human",
                                leadership = 1,
                                tactics = 1,
                                sword = 1,
                                bow = 1,
                            )
                        ),
                    commanderAssignments =
                        listOf(
                            CommanderAssignment(
                                1,
                                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)),
                            )
                        ),
                )
        val skilled =
            state.copy(
                commanders =
                    state.commanders.map {
                        it.copy(leadership = 100, tactics = 100, sword = 100, bow = 100)
                    }
            )
        val weakBattle =
            BattleEngine.advance(
                    BattleEngine.start(
                            state,
                            EnemyType.ORC,
                            Tactic.HOLD,
                            seed = 7,
                            enemyStrength = 1000,
                        )
                        .state
                )
                .state
                .battleSession!!
        val strongBattle =
            BattleEngine.advance(
                    BattleEngine.start(
                            skilled,
                            EnemyType.ORC,
                            Tactic.HOLD,
                            seed = 7,
                            enemyStrength = 1000,
                        )
                        .state
                )
                .state
                .battleSession!!
        assertEquals(
            weakBattle.fronts.first { it.section == BattleSection.CENTER },
            strongBattle.fronts.first { it.section == BattleSection.CENTER },
        )
        assertEquals(
            weakBattle.contingents.filter { it.section == BattleSection.RIGHT },
            strongBattle.contingents.filter { it.section == BattleSection.RIGHT },
        )
    }

    @Test
    fun hungerAndEquipmentChangeCombatLosses() {
        val state = army()
        fun step(input: GameState) =
            BattleEngine.advance(
                    BattleEngine.start(
                            input,
                            EnemyType.URUK,
                            Tactic.HOLD,
                            seed = 42,
                            enemyStrength = 900,
                        )
                        .state
                )
                .state
                .battleSession!!
        assertTrue(
            step(state.copy(resources = state.resources.copy(food = 0))).ownRemaining <
                step(state).ownRemaining
        )
        assertTrue(
            step(state.copy(armyPools = state.armyPools.map { it.copy(equipment = 0) }))
                .ownRemaining < step(state).ownRemaining
        )
    }

    @Test
    fun victoryRequiresPursuitChoiceAndRewardsOnlyOnce() {
        val mighty = army().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 5000)))
        var pursuit =
            BattleEngine.start(mighty, EnemyType.ORC, Tactic.RANGED, seed = 7, enemyStrength = 30)
                .state
        repeat(18) {
            if (pursuit.battleSession!!.status == BattleStatus.ACTIVE)
                pursuit =
                    BattleEngine.advance(
                            pursuit,
                            pursuit.battleSession!!.pendingEvent?.options?.first(),
                        )
                        .state
        }
        assertEquals(BattleStatus.PURSUIT, pursuit.battleSession!!.status)
        assertEquals(0, pursuit.victories)
        val held = BattleEngine.advance(pursuit, BattleDecision.HOLD_FORMATION).state
        val chased = BattleEngine.advance(pursuit, BattleDecision.PURSUE).state
        assertEquals(BattleStatus.VICTORY, held.battleSession!!.status)
        assertEquals(1, held.victories)
        assertEquals(pursuit.armySize, held.armySize)
        assertTrue(chased.resources.gold > held.resources.gold)
        assertTrue(chased.armySize < held.armySize)
        assertEquals(held, BattleEngine.advance(held, BattleDecision.PURSUE).state)
    }

    @Test
    fun reinforcingAfterAContingentIsDestroyedPreservesSaveableCasualtyHistory() {
        val before =
            GameEngine.newGame("Leon", 23, Species.HUMAN, null)
                .copy(
                    population = Population(6000, 0, 0, 0, 120, 0, 0, 0),
                    armyPools =
                        listOf(
                            ArmyUnitPool(UnitType.HUMAN_SWORD, 2000),
                            ArmyUnitPool(UnitType.HUMAN_ARCHER, 1),
                            ArmyUnitPool(UnitType.KNIGHT, 30),
                        ),
                )
        val deployments =
            listOf(
                BattleDeployment(
                    null,
                    BattleSection.LEFT,
                    listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 1)),
                ),
                BattleDeployment(
                    null,
                    BattleSection.CENTER,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1000)),
                ),
                BattleDeployment(
                    null,
                    BattleSection.RIGHT,
                    listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1000)),
                ),
                BattleDeployment(
                    null,
                    BattleSection.RESERVE,
                    listOf(UnitAllocation(UnitType.KNIGHT, 30)),
                ),
            )
        var state =
            BattleEngine.start(
                    before,
                    EnemyType.URUK,
                    Tactic.HOLD,
                    deployments,
                    seed = 12,
                    enemyStrength = 1500,
                )
                .state
        repeat(2) { state = BattleEngine.advance(state).state }
        assertEquals(
            0,
            state.battleSession!!.contingents.first { it.type == UnitType.HUMAN_ARCHER }.soldiers,
        )
        state = BattleEngine.advance(state, BattleDecision.SEND_RESERVE).state
        assertEquals(
            state,
            com.goldenunicorn.troopmanager.data.SaveCodec.decode(
                com.goldenunicorn.troopmanager.data.SaveCodec.encode(state)
            ),
        )
    }

    @Test
    fun siegeDamageChangesRealWallAndNoHomeArmyLosesWithoutAStall() {
        var state =
            BattleEngine.start(
                    army(),
                    EnemyType.URUK,
                    Tactic.FORTIFY,
                    seed = 99,
                    enemyStrength = 300,
                )
                .state
        repeat(3) {
            state =
                BattleEngine.advance(state, state.battleSession!!.pendingEvent?.options?.first())
                    .state
        }
        assertTrue(state.realm.wallIntegrity < 100)
        assertEquals(state.realm.wallIntegrity, state.battleSession!!.wallIntegrity)
        val empty =
            army().copy(armyPools = emptyList(), invasion = Invasion(EnemyType.URUK, 1, 300, 1))
        val lost =
            BattleEngine.start(empty, EnemyType.URUK, Tactic.FORTIFY, enemyStrength = 300).state
        assertEquals(BattleStatus.DEFEAT, lost.battleSession!!.status)
        assertEquals(1, lost.defeats)
        assertNull(lost.invasion)
    }
}
