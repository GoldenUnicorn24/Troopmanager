package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** JVM baselines; scene projection cost is not a device FPS claim. */
class V06BenchmarkTest {
    @Test fun repeatableCampaignWorkloads() {
        val state = GameEngine.newGame("Benchmark", 28, Species.HUMAN, null).copy(
            population = Population(human = 200_000, woodElf = 0, goldElf = 0, wall = 0, humanRecruits = 1000, woodElfRecruits = 0, goldElfRecruits = 0, wallRecruits = 0),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100_000)),
            resources = Resources(1_000_000, 1_000_000, 50_000, 50_000, 50_000))
        val encoded = SaveCodec.encode(state)
        val fighting = BattleEngine.start(state, EnemyType.ORC, Tactic.HOLD, seed = 781, enemyStrength = 100_000).state
        fun measure(label: String, operation: () -> Any) {
            repeat(3) { operation() }
            val samples = (1..10).map { val start = System.nanoTime(); operation(); System.nanoTime() - start }.sorted()
            println("V06_BENCH $label median_us=${samples[5] / 1000} p90_us=${samples[9] / 1000} soldiers=${state.armySize}")
            assertTrue(samples.all { it > 0 })
        }
        measure("new_campaign") { GameEngine.newGame("Benchmark", 28, Species.HUMAN, null) }
        measure("city_projection") { PresentationEngine.cityActivity(state) }
        measure("battle_exchange") { BattleEngine.advance(fighting) }
        measure("save_encode") { SaveCodec.encode(state) }
        measure("save_decode") { SaveCodec.decode(encoded) }
        measure("day_tick") { GameEngine.advanceDay(state) }
        measure("ai_turn") { WorldEngine.tick(state.copy(day = 2)) }
    }
}
