package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V06CampaignTest {
    private fun settleBattle(initial: GameState): GameState {
        var state = initial
        var steps = 0
        while (state.battleSession?.isActive == true) {
            val event = state.battleSession!!.pendingEvent
            val decision = event?.options?.firstOrNull { it == BattleDecision.HOLD || it == BattleDecision.HOLD_FORMATION }
                ?: event?.options?.firstOrNull()
            state = BattleEngine.advance(state, decision).state
            require(++steps < 100) { "Schlacht endet nicht." }
        }
        state = WorldEngine.reconcileBattle(state)
        return state.copy(battleSession = null)
    }

    private fun campaign(days: Int, species: Species): GameState {
        var state = GameEngine.newGame("Langzeit", 28, species, "content://portrait/persisted")
        repeat(days) { index ->
            state = settleBattle(state)
            state = GameEngine.advanceDay(state).state
            assertEquals(index + 2, state.day)
            assertEquals(state.world.armies.size, state.world.armies.map { it.id }.distinct().size)
            UnitType.entries.forEach { type -> assertTrue("Doppelbelegung am Tag ${state.day}", state.away(type) + state.assigned(type) <= state.soldiers(type)) }
            val loaded = SaveCodec.decode(SaveCodec.encode(state))
            assertEquals("Save am Tag ${state.day}", state, loaded)
            assertTrue(loaded.resources.gold >= 0 && loaded.resources.food >= 0)
            state = loaded
        }
        return settleBattle(state)
    }

    @Test fun hundredDaysRetainEveryOrigin() {
        Species.entries.forEach { assertEquals(101, campaign(100, it).day) }
    }

    @Test fun yearRetainsWorldAndCampaign() {
        val state = campaign(365, Species.HALF_ELF)
        assertEquals(366, state.day)
        assertTrue(state.world.initialized)
        assertTrue(state.world.factions.size >= 5)
        assertEquals("content://portrait/persisted", state.player.portraitUri)
    }

    @Test fun thousandDaysRemainSerializableAndConserved() {
        val state = campaign(1000, Species.HUMAN)
        assertEquals(1001, state.day)
        assertTrue(state.world.factions.filter { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }.any { it.lastDecisionDay > 1 })
    }

    @Test fun hundredThousandSoldiersRemainAggregated() {
        val state = GameEngine.newGame("Großheer", 28, Species.HUMAN, null).copy(
            population = Population(human = 200_000, woodElf = 0, goldElf = 0, wall = 0, humanRecruits = 1000, woodElfRecruits = 0, goldElfRecruits = 0, wallRecruits = 0),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100_000), ArmyUnitPool(UnitType.KNIGHT, 4000)),
            resources = Resources(1_000_000, 1_000_000, 50_000, 50_000, 50_000))
        val next = GameEngine.advanceDay(state).state
        assertEquals(104_000, next.armySize)
        assertEquals(2, next.armyPools.size)
        assertEquals(next, SaveCodec.decode(SaveCodec.encode(next)))
    }

    @Test fun prepaidExpeditionDoesNotPayHomeFoodAgain() {
        val state = GameEngine.newGame("Nachschub", 28, Species.HUMAN, null)
        val home = EconomyEngine.upkeep(state)
        val dispatched = WorldEngine.dispatch(state, "village", null, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100))).state
        assertEquals(100, dispatched.awayArmySize)
        assertEquals(home - 100, EconomyEngine.upkeep(dispatched))
        assertEquals(state.armySize, dispatched.armySize)
    }

    @Test fun alliedAidMovesExistingArmyWithoutDeletingEnemyTroops() {
        var state = DiplomacyEngine.declareWar(GameEngine.newGame("Bündnis", 28, Species.HUMAN, null), "ash_covenant").state
        state = WorldEngine.bindInvasion(state.copy(invasion = Invasion(EnemyType.ORC, state.day + 20, 350, state.day)))
        val ally = state.world.armies.first { it.factionId == "copper_league" }
        state = state.copy(
            diplomacy = state.diplomacy.copy(treaties = state.diplomacy.treaties + Treaty("aid-test", TreatyKind.DEFENSIVE_ALLIANCE, PLAYER_FACTION, "copper_league", state.day, state.day + 60)),
            world = state.world.copy(armies = state.world.armies.map { if (it.id == ally.id) it.copy(regionId = "village", supplyFood = 0, status = WorldArmyStatus.HOLDING, destinationId = null, route = emptyList()) else it }))
        val troops = state.world.armies.associate { it.id to it.units }
        val enemyStrength = state.invasion!!.strength
        val beforeAlly = state.world.faction("copper_league")!!
        val after = GameEngine.requestAllies(state).state
        assertTrue(after.invasion!!.alliesRequested)
        assertEquals(enemyStrength, after.invasion!!.strength)
        assertEquals(troops, after.world.armies.associate { it.id to it.units })
        val mobilized = after.world.armies.first { it.id == ally.id }
        assertEquals(WorldArmyStatus.MARCHING, mobilized.status)
        assertEquals(beforeAlly.food - mobilized.supplyFood, after.world.faction("copper_league")!!.food)
        assertEquals(state.resources.gold - after.resources.gold, after.world.faction("copper_league")!!.gold - beforeAlly.gold)
        assertTrue(DiplomacyEngine.atWar(after, "copper_league", "ash_covenant"))
        assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
    }
}
