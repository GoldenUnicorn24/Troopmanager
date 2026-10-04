package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class CampaignExperienceTest {
    private fun state(): GameState = GameEngine.newGame("Grenzwacht", 28, Species.HUMAN, null).copy(
        resources = Resources(100_000, 100_000, 50_000, 50_000, 50_000),
        population = Population(human = 12_000, woodElf = 1000, goldElf = 500, wall = 1000),
        armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 1000, 50), ArmyUnitPool(UnitType.HUMAN_ARCHER, 500, 50)),
        commanderAssignments = emptyList())
    private fun post(state: GameState, stores: Int = 250, level: Int = 1) = state.copy(frontier = state.frontier.copy(
        outposts = listOf(FrontierOutpost(1, "village", "Grenzwacht", level, stores = stores)), nextOutpostId = 2))

    @Test fun fullAndLegacyOverfullOutpostsNeverWasteCityFood() {
        val full = post(state())
        assertEquals(full, FrontierEngine.stockOutpost(full, 1).state)
        val legacy = post(state(), 1000)
        assertEquals(legacy, FrontierEngine.stockOutpost(legacy, 1).state)
        val almost = post(state(), 240)
        val stocked = FrontierEngine.stockOutpost(almost, 1).state
        assertEquals(250, stocked.frontier.outposts.single().stores)
        assertEquals(almost.resources.food - 10, stocked.resources.food)
        assertEquals(stocked, SaveCodec.decode(SaveCodec.encode(stocked)))
    }

    @Test fun resupplyTransfersActualStoresWithoutCreatingFood() {
        val base = post(state(), 600, 2)
        val army = WorldArmy("test", PLAYER_FACTION, "Versorgung", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)), "village", supplyFood = 10)
        val result = OutpostEngine.interact(base, army)
        assertEquals(1L, result.army.lastSupplyOutpostId)
        assertEquals(300, result.army.supplyFood)
        assertEquals(310, result.state.frontier.outposts.single().stores)
        assertEquals(610, result.army.supplyFood + result.state.frontier.outposts.single().stores)
        assertEquals(base.resources, result.state.resources)
        assertEquals(result, OutpostEngine.interact(result.state, result.army))
    }

    @Test fun aGarrisonMustMarchAndRemainsReservedUntilItReturns() {
        var current = post(state(), 600, 2)
        current = WorldEngine.dispatch(current, "village", null, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)), supplyDays = 20).state
        val id = current.world.playerFieldArmies.single().id
        assertEquals(current, OutpostEngine.assignGarrison(current, 1, id).state)
        repeat(12) { current = WorldEngine.tick(current.copy(day = current.day + 1)) }
        assertEquals("village", current.world.armies.first { it.id == id }.regionId)
        current = OutpostEngine.assignGarrison(current, 1, id).state
        assertEquals(100, OutpostEngine.garrison(current, current.frontier.outposts.single())!!.total)
        assertEquals(100, current.awayArmySize)
        assertEquals(1400, current.homeArmySize)
        val recall = WorldEngine.recall(current, id).state
        assertEquals(100, recall.awayArmySize)
        assertEquals(WorldArmyStatus.RETURNING, recall.world.armies.first { it.id == id }.status)
        assertNull(OutpostEngine.garrison(recall, recall.frontier.outposts.single()))
        assertEquals(recall, SaveCodec.decode(SaveCodec.encode(recall)))
    }

    @Test fun holdingAnActualRaidDelaysTheSameEnemyAndConservesOwnPools() {
        var base = DiplomacyEngine.declareWar(state(), "ash_covenant").state
        val guard = WorldArmy("guard", PLAYER_FACTION, "Grenzgarde", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300)), "village", supplyFood = 900)
        base = post(base, 600, 3).copy(world = base.world.copy(armies = base.world.armies + guard))
        base = OutpostEngine.assignGarrison(base, 1, guard.id).state
        val enemy = WorldArmy("raid", "ash_covenant", "Angreifer", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 150)), "village", arrivalDay = base.day + 4)
        val result = OutpostEngine.interact(base, enemy)
        assertEquals(base.day + 4, result.army.delayUntilDay)
        assertEquals(enemy.arrivalDay!! + 4, result.army.arrivalDay)
        val stationed = result.state.world.armies.first { it.id == guard.id }
        assertEquals(base.soldiers(UnitType.HUMAN_SWORD) - result.state.soldiers(UnitType.HUMAN_SWORD), guard.total - stationed.total)
        assertTrue(result.state.frontier.outposts.single().integrity < 100)
        assertEquals(result, OutpostEngine.interact(result.state, result.army))
        assertEquals(result, OutpostEngine.interact(base, enemy))
        assertEquals(result.state, SaveCodec.decode(SaveCodec.encode(result.state)))
    }

    @Test fun aBypassedPostCannotDelayOrDamageAnEnemy() {
        val base = post(DiplomacyEngine.declareWar(state(), "ash_covenant").state)
        val enemy = WorldArmy("raid", "ash_covenant", "Umgehung", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)), "keep")
        assertEquals(OutpostEngine.Interaction(base, enemy), OutpostEngine.interact(base, enemy))
    }

    @Test fun forecastsAreBoundedAndNeverAdvanceTheCampaign() {
        val base = state()
        val raw = SaveCodec.encode(base)
        for (days in listOf(7, 14)) {
            val f = CampaignInsightsEngine.forecast(base, days)
            ResourceKind.entries.forEach {
                assertTrue(it.value(f.lower) <= it.value(f.expected))
                assertTrue(it.value(f.expected) <= it.value(f.upper))
                assertTrue(it.value(f.overflow) >= 0)
            }
        }
        assertEquals(raw, SaveCodec.encode(base))
    }

    @Test fun theDailyCenterCombinesSupplyAndPoliticsAndCapsSituations() {
        val base = state().copy(resources = Resources(100, 0, 100, 100, 100), society = SocietyState(hunger = 70))
        val rows = CampaignInsightsEngine.situations(base)
        assertTrue(rows.size in 1..5)
        assertEquals("Versorgung & Gesellschaft", rows.first().category)
        assertTrue(rows.first().detail.contains("Bauernloyalität"))
        assertTrue(rows.zipWithNext().all { (a,b) -> a.urgency >= b.urgency })
    }

    @Test fun expeditionsOfferRealChoicesAndBuildRegionalKnowledge() {
        var current = state()
        val region = current.regions.first { it.mission == MissionType.RELIEF }.id
        current = MissionEngine.start(current, MissionType.RELIEF, null,
            listOf(UnitAllocation(UnitType.HUMAN_SWORD, 500)), region).state
        val id = current.activeMissions.single().id
        val decisions = mutableSetOf<MissionDecisionKind>()
        repeat(60) {
            current.activeMissions.first { it.id == id }.pendingDecision?.let {
                decisions += it.kind
                current = MissionEngine.chooseRoute(current, id, 0).state
            }
            current = MissionEngine.tick(current.copy(day = current.day + 1), 1.25)
        }
        assertTrue(decisions.containsAll(listOf(MissionDecisionKind.ROUTE, MissionDecisionKind.OPERATION)))
        assertFalse(current.activeMissions.first { it.id == id }.status.isAway)
        assertTrue((current.world.regionReputation[region] ?: 0) > 0)
        assertTrue(region in current.world.knowledgeFor(PLAYER_FACTION).exploredRegions)
        assertEquals(current, SaveCodec.decode(SaveCodec.encode(current)))
    }
}
