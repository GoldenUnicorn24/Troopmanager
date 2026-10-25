package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class BattleMutationSafetyTest {
    private fun province(): GameState = CrownEngine.acquire(
        GameEngine.newGame("Mauerwacht", 24, Species.HUMAN, null), "gm_eschenhof", "Kauf")

    @Test fun garrisonTransfersCannotChangeAnActiveBattleArmy() {
        val garrisoned = CrownWarEngine.station(province(), "gm_eschenhof", 100).state
        val started = BattleEngine.start(garrisoned, EnemyType.ORC, Tactic.FORTIFY,
            seed = 731, enemyStrength = 300).state
        for (status in listOf(BattleStatus.ACTIVE, BattleStatus.PURSUIT)) {
            val state = started.copy(battleSession = started.battleSession!!.copy(status = status))
            assertSame(status.name, state, CrownWarEngine.station(state, "gm_eschenhof", 50).state)
            assertSame(status.name, state, CrownWarEngine.withdraw(state, "gm_eschenhof").state)
        }
    }

    @Test fun garrisonTransfersResumeAfterTheBattleEndsAndPreserveSoldiers() {
        val started = BattleEngine.start(province(), EnemyType.ORC, Tactic.FORTIFY,
            seed = 731, enemyStrength = 300).state
        val ended = BattleEngine.order(started, BattleDecision.ORDERED_RETREAT, BattleSection.CENTER).state
        assertFalse(ended.battleSession!!.isActive)
        val stationed = CrownWarEngine.station(ended, "gm_eschenhof", 100).state
        assertEquals(ended.homeArmySize - 100, stationed.homeArmySize)
        val returned = CrownWarEngine.withdraw(stationed, "gm_eschenhof").state
        assertEquals(ended.homeArmySize, returned.homeArmySize)
        assertEquals(0, CrownEngine.province(returned, "gm_eschenhof")!!.garrison)
        assertEquals(returned, SaveCodec.decode(SaveCodec.encode(returned)))
    }
}
