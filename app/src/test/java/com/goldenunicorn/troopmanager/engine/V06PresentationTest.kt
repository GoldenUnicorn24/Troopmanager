package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.audio.AudioDirector
import com.goldenunicorn.troopmanager.audio.MusicMood
import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V06PresentationTest {
    private fun campaign() = GameEngine.newGame("Mira", 24, Species.HUMAN, null)
    private fun battle(id: String, own: Int, enemy: Int, victory: Boolean, losses: CasualtyReport = CasualtyReport()) =
        BattleRecord(id, 5, "Nordpass", EnemyType.ORC, 901, Tactic.HOLD, own, enemy,
            own - losses.dead - losses.wounded - losses.missing - losses.captured,
            if (victory) 0 else enemy / 2, victory, 45, losses)

    @Test fun achievementsUseRealThresholdsAndAwardRenownOnce() {
        val state = campaign().copy(day = 365, title = "Hochkönig", victories = 1,
            population = Population(human = 130_000), armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100_000)))
        val rewarded = PresentationEngine.tick(state)
        val earned = rewarded.presentation.achievements.map { it.achievement }.toSet()
        assertTrue(earned.containsAll(setOf(Achievement.FIRST_VICTORY, Achievement.ARMY_10K, Achievement.ARMY_100K,
            Achievement.KING, Achievement.HIGH_KING, Achievement.DAYS_100, Achievement.DAYS_365)))
        assertEquals(state.renown + earned.sumOf { it.renown }, rewarded.renown)
        assertEquals(rewarded, PresentationEngine.tick(rewarded))
        assertEquals(100_000, rewarded.presentation.records.largestArmy)
        assertEquals(130_950, rewarded.presentation.records.highestPopulation)
    }

    @Test fun losslessAchievementIncludesAllCasualtyCategoriesAndUnderdogNeedsVictory() {
        val woundedWin = battle("wounded", 500, 1000, true, CasualtyReport(wounded = 1))
        val defeat = battle("defeat", 200, 2000, false)
        val initial = campaign().copy(war = WarState(history = listOf(woundedWin, defeat)))
        val first = PresentationEngine.tick(initial)
        assertTrue(first.presentation.achievements.any { it.achievement == Achievement.OUTNUMBERED_VICTORY })
        assertFalse(first.presentation.achievements.any { it.achievement == Achievement.LOSSLESS_VICTORY })
        val earned = PresentationEngine.tick(first.copy(war = first.war.copy(history = first.war.history + battle("clean", 1000, 500, true))))
        assertTrue(earned.presentation.achievements.any { it.achievement == Achievement.LOSSLESS_VICTORY })
        assertEquals(1000, earned.presentation.records.largestVictory)
        assertEquals(200, earned.presentation.records.largestDefeat)
        assertEquals(45, earned.presentation.records.longestBattleMinutes)
    }

    @Test fun generalStatuesImproveOnlyHomeMoraleOncePerCampaignDay() {
        val initial = campaign().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 1000, morale = 60)),
            commanders = listOf(Commander(9, "Aren", Culture.HUMAN, "human", level = 6, victories = 10, battlesFought = 14)),
            world = campaign().world.copy(armies = listOf(WorldArmy("player_away", PLAYER_FACTION, "Vorhut",
                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 600)), "keep", status = WorldArmyStatus.MARCHING))))
        val next = PresentationEngine.tick(initial)
        // 400 / 1,000 home soldiers receive one point; the aggregated pool rounds down.
        assertEquals(60, next.armyPools.single().morale)
        assertEquals(1, next.presentation.legends.count { it.kind == LegendKind.GENERAL })
        val allHome = next.copy(world = next.world.copy(armies = emptyList()), day = next.day + 1)
        val boosted = PresentationEngine.tick(allHome)
        assertEquals(61, boosted.armyPools.single().morale)
        assertEquals(boosted, PresentationEngine.tick(boosted))
    }

    @Test fun recordsSurviveLossesAndHeraldryAndTutorialsSurviveSaveLoad() {
        val initial = PresentationEngine.tick(campaign().copy(population = Population(human = 15_000),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 10_000))))
        val heraldry = Heraldry(PresentationEngine.bannerPalette[2], PresentationEngine.bannerPalette[6], HeraldicSymbol.TREE)
        val customized = PresentationEngine.markTutorialSeen(PresentationEngine.setHeraldry(initial, heraldry).state, "city")
        val reloaded = SaveCodec.decode(SaveCodec.encode(customized))
        assertEquals(heraldry, reloaded.presentation.heraldry)
        assertNull(PresentationEngine.tutorial(reloaded, "city"))
        assertNotNull(PresentationEngine.tutorial(reloaded, "army"))
        val depleted = PresentationEngine.tick(reloaded.copy(armyPools = emptyList()))
        assertEquals(10_000, depleted.presentation.records.largestArmy)
        assertEquals(customized.presentation.achievements, reloaded.presentation.achievements)
        assertEquals(reloaded, PresentationEngine.setHeraldry(reloaded, heraldry.copy(secondaryArgb = heraldry.primaryArgb)).state)
    }

    @Test fun cityActivityFallsWithHungerAndNightAndGuardsIncreaseWithWar() {
        val prosperous = campaign().copy(city = CityState(prosperity = 90, satisfaction = 90), resources = Resources(food = 100_000))
        val bustling = PresentationEngine.cityActivity(prosperous, false)
        val hungry = PresentationEngine.cityActivity(prosperous.copy(resources = Resources(food = 0),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 50_000))), false)
        val war = PresentationEngine.cityActivity(prosperous.copy(realm = prosperous.realm.copy(threat = 80)), false)
        assertTrue(bustling.citizens > hungry.citizens)
        assertTrue(bustling.wagons > hungry.wagons)
        assertTrue(war.soldiers > bustling.soldiers)
        assertTrue(PresentationEngine.cityActivity(prosperous, true).citizens < bustling.citizens)
        assertTrue(war.groups <= 45)
        assertEquals(listOf(CityTime.MORNING, CityTime.DAY, CityTime.EVENING, CityTime.NIGHT), (1..4).map { CityTime.at(it) })
    }

    @Test fun musicUsesActualThreatSceneAndDominantCulture() {
        val state = campaign()
        assertEquals(MusicMood.MENU, AudioDirector.mood(null, "menu"))
        assertEquals(MusicMood.MENU, AudioDirector.mood(state.copy(realm = state.realm.copy(threat = 70)), "menu"))
        assertEquals(MusicMood.CITY, AudioDirector.mood(state, "city"))
        assertEquals(MusicMood.WORLD, AudioDirector.mood(state, "world"))
        assertEquals(MusicMood.POLITICS, AudioDirector.mood(state, "court"))
        assertEquals(MusicMood.WAR, AudioDirector.mood(state.copy(realm = state.realm.copy(threat = 70)), "city"))
        assertEquals("wood", AudioDirector.culture(state.copy(population = Population(human = 10, woodElf = 1000))))
        assertEquals("gates", AudioDirector.cue(BattleSoundCue.WALL_BREAK))
    }
}
