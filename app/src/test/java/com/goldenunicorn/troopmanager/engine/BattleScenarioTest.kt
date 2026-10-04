package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Fixed-seed acceptance scenarios, expressed through the public battle API. */
class BattleScenarioTest {
    private fun defenders(arrows: Int = 2400) = GameEngine.newGame("Mauerwacht", 23, Species.HUMAN, null).copy(
        armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 300, experience = 30, morale = 80)),
        militaryStock = MilitaryStock(arrows = arrows),
        invasion = Invasion(EnemyType.ORC, 1, 300, 1, devices = emptyList()),
    )

    @Test fun pureMeleeCannotHurtIntactWallBeforeContact() {
        var state = BattleEngine.start(defenders(), EnemyType.ORC, Tactic.FORTIFY,
            seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state
        repeat(2) { state = BattleEngine.advance(state).state }
        val battle = state.battleSession!!
        assertEquals("An intact wall prevents all personnel damage from distant melee troops", 300, battle.ownRemaining)
        assertTrue("Defending archers can shoot approaching infantry", battle.enemyRemaining < 300)
    }

    @Test fun emptyAmmunitionCannotProvideFullRangedPower() {
        fun first(arrows: Int) = BattleEngine.advance(BattleEngine.start(defenders(arrows), EnemyType.ORC,
            Tactic.FORTIFY, seed = 90, enemyStrength = 300,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300))).state).state.battleSession!!
        assertTrue(first(2400).enemyRemaining < first(0).enemyRemaining)
    }
}
