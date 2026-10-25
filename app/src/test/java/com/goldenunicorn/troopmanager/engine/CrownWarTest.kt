package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class CrownWarTest {
    private fun game() = GameEngine.newGame("Leon", 25, Species.HALF_ELF, null)

    private fun withProvince(id: String): GameState = CrownEngine.acquire(game().let { it.copy(day = 12) }, id, "Kauf")

    @Test
    fun hostileNeighboursLaunchAndResolveCounterattacks() {
        var state = withProvince("gm_eschenhof")
        state = CrownWarEngine.tick(state)
        val threat = state.crown.threats.single()
        assertEquals("gm_eschenhof", threat.targetId)
        assertTrue(threat.arrivalDay > state.day)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
        // An unprotected province falls when the army arrives.
        val weak = state.copy(crown = state.crown.copy(threats = listOf(threat.copy(soldiers = 50_000))))
        val lost = CrownWarEngine.tick(weak.copy(day = threat.arrivalDay))
        assertNotEquals(ProvinceOwner.PLAYER, CrownEngine.province(lost, "gm_eschenhof")!!.owner)
        assertTrue(lost.crown.threats.isEmpty())
        // A strong garrison holds.
        val garrisoned = CrownWarEngine.station(state, "gm_eschenhof", 300).state
        assertEquals(300, CrownEngine.province(garrisoned, "gm_eschenhof")!!.garrison)
        val held = CrownWarEngine.tick(garrisoned.copy(day = threat.arrivalDay, crown = garrisoned.crown.copy(threats = listOf(threat.copy(soldiers = 100)))))
        assertEquals(ProvinceOwner.PLAYER, CrownEngine.province(held, "gm_eschenhof")!!.owner)
    }

    @Test
    fun stationingMovesSoldiersAndWithdrawingBringsThemHome() {
        var state = withProvince("gm_eschenhof")
        val army = state.armySize
        state = CrownWarEngine.station(state, "gm_eschenhof", 200).state
        assertEquals(army - 200, state.armySize)
        state = CrownWarEngine.withdraw(state, "gm_eschenhof").state
        assertEquals(army, state.armySize)
        assertEquals(0, CrownEngine.province(state, "gm_eschenhof")!!.garrison)
    }

    @Test
    fun reliefArmyCanDestroyAMarchingEnemy() {
        var state = CrownWarEngine.tick(withProvince("gm_eschenhof"))
        val threat = state.crown.threats.single()
        state = state.copy(crown = state.crown.copy(threats = listOf(threat.copy(soldiers = 20))))
        state = CrownWarEngine.relieve(state, threat.id, 100).state
        assertTrue(state.crown.threats.isEmpty())
    }

    @Test
    fun liveCampaignBattleConquersTheProvinceOnVictory() {
        var state = CrownWarEngine.startLiveCampaign(game(), "gm_rabenfurt").state
        assertNotNull(state.battleSession)
        assertEquals("gm_rabenfurt", state.crown.liveCampaignProvinceId)
        assertEquals(state, CrownWarEngine.reconcile(state))
        val won = state.copy(battleSession = state.battleSession!!.copy(status = BattleStatus.VICTORY))
        val after = CrownWarEngine.reconcile(won)
        assertEquals(ProvinceOwner.PLAYER, CrownEngine.province(after, "gm_rabenfurt")!!.owner)
        assertNull(after.crown.liveCampaignProvinceId)
        assertEquals(after, CrownWarEngine.reconcile(after))
    }

    @Test
    fun advisorNamesBottlenecksAndOpportunities() {
        var state = game()
        state = state.copy(resources = state.resources.copy(food = 100))
        state = state.copy(armyPools = state.armyPools.map { it.copy(soldiers = it.soldiers * 30) })
        val advice = AdvisorEngine.advice(state)
        assertTrue(advice.any { it.title.startsWith("Nahrung") && it.severity == AdvisorEngine.Severity.CRITICAL })
        assertTrue(advice.any { it.severity == AdvisorEngine.Severity.OPPORTUNITY })
        assertEquals(advice.sortedBy { it.severity.ordinal }, advice)
    }

    @Test
    fun crownedQueenBringsRequestsAndKeepsPromises() {
        var state = HeroineSagaEngine.recruitImmediately(game(), "alina")
        state = state.copy(day = 40, crown = state.crown.copy(saga = state.crown.saga.copy(chapter = HeroineSagaEngine.FINAL_CHAPTER, lastRoyalDay = 20, royalCount = 3)))
        state = HeroineSagaEngine.tick(state)
        assertEquals("royal_homeland", state.crown.saga.pendingScene?.key)
        state = HeroineSagaEngine.choose(state, "promise").state
        val goal = state.crown.saga.goal!!
        val gold = state.resources.gold
        state = CrownEngine.acquire(state, goal.provinceId, "Eroberung")
        state = RoyalCourtEngine.tick(state)
        assertNull(state.crown.saga.goal)
        assertTrue(state.resources.gold >= gold + goal.rewardGold)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }
}
