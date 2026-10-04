package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class BattleAmmunitionTest {
    private fun army(parts: Int) = GameState(player = CharacterProfile("Ladungen"),
        armyPools = listOf(ArmyUnitPool(UnitType.DRAGON_ARTILLERY, 30)),
        militaryStock = MilitaryStock(arrows = 0, siegeParts = parts))
    private fun start(parts: Int) = BattleEngine.start(army(parts), EnemyType.URUK, Tactic.FORTIFY,
        listOf(BattleDeployment(null, BattleSection.LEFT, listOf(UnitAllocation(UnitType.DRAGON_ARTILLERY, 30)))),
        seed = 90, enemyStrength = 900,
        enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 900))).state

    @Test fun emptyArtilleryCannotFireOrTargetDevices() {
        val base = start(0)
        assertEquals(0, base.battleSession!!.battleArtilleryRemaining)
        assertFalse(BattleEngine.canOrder(base.battleSession!!, BattleDecision.ARTILLERY_TARGET, BattleSection.LEFT))
        val next = BattleEngine.advance(base).state
        assertEquals(0, next.battleSession!!.exchanges.single().enemyLosses)
        assertEquals(0, next.battleSession!!.exchanges.single().fronts.sumOf { it.artilleryChargesUsed })
    }

    @Test fun targetingUsesSeparateRealChargesAndDamagesOneLocalDevice() {
        val base = start(18)
        assertEquals(0, base.militaryStock.siegeParts)
        assertEquals(0, base.battleSession!!.battleArrowsLoaded)
        val next = BattleEngine.order(base, BattleDecision.ARTILLERY_TARGET, BattleSection.LEFT).state
        assertEquals(15, next.battleSession!!.battleArtilleryRemaining)
        assertEquals(3, next.battleSession!!.lastReport(BattleSection.LEFT)!!.artilleryChargesUsed)
        assertTrue(next.battleSession!!.siegeDevices.first { it.type == SiegeDevice.CATAPULT }.integrity in 1..99)
        assertTrue(next.battleSession!!.siegeDevices.filter { it.section != BattleSection.LEFT }.all { it.integrity == 100 })
        assertEquals(next, SaveCodec.decode(SaveCodec.encode(next)))
    }

    @Test fun repeatedPreparationRefundsChargesAndPreservesPersonalPosition() {
        var current = start(18)
        current = BattleEngine.configure(current, personalSection = BattleSection.RIGHT).state
        val original = current.militaryStock.siegeParts + current.battleSession!!.battleArtilleryRemaining
        repeat(4) { current = BattleEngine.configure(current, participation = BattleParticipation.PERSONAL).state }
        assertEquals(original, current.militaryStock.siegeParts + current.battleSession!!.battleArtilleryRemaining)
        val deployments = listOf(BattleDeployment(null, BattleSection.LEFT, listOf(UnitAllocation(UnitType.DRAGON_ARTILLERY, 30))))
        current = BattleEngine.redeploy(current, deployments).state
        assertEquals(BattleSection.RIGHT, current.battleSession!!.personalSection)
        assertEquals(original, current.militaryStock.siegeParts + current.battleSession!!.battleArtilleryRemaining)
    }

    @Test fun siegeResearchUnlocksARealCounterTunnelOrder() {
        val untrained = start(18)
        assertFalse(untrained.battleSession!!.counterTunnelUnlocked)
        val researched = army(18).copy(research = ResearchState(completed = setOf(ResearchTech.SIEGE_ENGINEERING)))
        assertTrue(BattleEngine.start(researched, EnemyType.URUK, Tactic.FORTIFY, seed = 90).state.battleSession!!.counterTunnelUnlocked)
    }

    @Test fun legacyReplayRemainsDeterministicWhenAnOldTimeBasedOrderIsUnavailable() {
        val started = start(18)
        val b = started.battleSession!!
        val legacy = b.replayStart!!.copy(combatVersion = 1, initialSegments = emptyList(), initialSiegeDevices = emptyList(),
            initialEnemyRoster = emptyList(), arrowsLoaded = -1, artilleryLoaded = -1, enemyArtilleryLoaded = -1)
        val event = BattleEvent("Alte Phase", "Ein früher zeitgesteuerter Befehl.", BattleSection.CENTER, listOf(BattleDecision.ARTILLERY_TARGET))
        val record = BattleRecord("legacy", 1, "Grenzfeste", b.enemy, b.seed, b.tactic, b.ownStart, b.enemyStart,
            b.ownStart, b.enemyStart, false, 5, CasualtyReport(), emptyList(), emptyList(), b.terrain, b.participation,
            replay = legacy, inputs = listOf(BattleInput(BattleDecision.ARTILLERY_TARGET, event)), enemyUnits = b.enemyUnits)
        val replay = BattleEngine.replay(record)
        assertNotNull(replay)
        assertEquals(5, replay!!.minute)
        assertEquals(replay, BattleEngine.replay(record))
    }
}
