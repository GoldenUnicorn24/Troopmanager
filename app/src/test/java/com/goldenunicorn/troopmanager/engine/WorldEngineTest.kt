package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class WorldEngineTest {
    private fun campaign() = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
    private fun tick(state: GameState) = WorldEngine.tick(state.copy(day = state.day + 1))

    @Test fun bootstrapPreservesExistingStateAndStableRegionIds() {
        val legacy = GameState(player = CharacterProfile("Ada"), regions = initialRegions().map { if (it.id == "mine") it.copy(owned = true) else it },
            invasion = Invasion(EnemyType.ORC, 7, 450, 1))
        val migrated = WorldEngine.initialize(legacy)
        assertEquals(legacy.player, migrated.player)
        assertEquals(legacy.invasion, migrated.invasion)
        assertEquals(legacy.regions, migrated.regions)
        assertEquals(legacy.armyPools, migrated.armyPools)
        assertTrue(migrated.world.places.map { it.id }.containsAll(legacy.regions.map { it.id }))
        assertEquals(PLAYER_FACTION, migrated.world.place("mine")!!.ownerId)
        assertEquals(migrated, WorldEngine.initialize(migrated))
        assertEquals(3, migrated.world.enemyCommanders.size)
        assertEquals(3, migrated.world.factions.count { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION })
        WorldEngine.validate(migrated)
    }

    @Test fun fieldReservationsAndMissionReservationsUseOnePlayerPool() {
        val start = campaign()
        val sent = WorldEngine.dispatch(start, "village", 1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100))).state
        assertEquals(start.armyPools, sent.armyPools)
        assertEquals(100, sent.awayArmySize)
        assertEquals(80, sent.homeSoldiers(UnitType.HUMAN_SWORD))
        assertTrue(sent.commanderAway(1))
        assertEquals(sent, WorldEngine.dispatch(sent, "village", 1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50))).state)
        val mission = MissionEngine.start(sent, MissionType.PATROL, null, listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 50))).state
        assertEquals(150, mission.awayArmySize)
        assertEquals(1, mission.world.armies.count { it.missionId != null })
        assertEquals(start.armySize, mission.armySize)
        WorldEngine.validate(mission)
    }

    @Test fun marchesTakeTimeAndReturningArmyNeverDuplicatesSoldiers() {
        val start = campaign()
        var state = WorldEngine.dispatch(start, "village", null, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 60)), supplyDays = 20).state
        val id = state.world.playerFieldArmies.single().id
        assertEquals("keep", state.world.playerFieldArmies.single().regionId)
        assertTrue(state.world.playerFieldArmies.single().arrivalDay!! > state.day)
        repeat(8) { state = tick(state) }
        assertEquals("village", state.world.armies.first { it.id == id }.regionId)
        assertEquals(start.armySize, state.armySize)
        state = WorldEngine.recall(state, id).state
        repeat(8) { state = tick(state) }
        assertEquals(WorldArmyStatus.HOME, state.world.armies.first { it.id == id }.status)
        assertEquals(0, state.awayArmySize)
        assertTrue(state.armySize <= start.armySize) // Hunger can remove soldiers; returning cannot add them.
        WorldEngine.validate(state)
    }

    @Test fun noFoodProducesRealLossesAndMoraleDamage() {
        var state = WorldEngine.dispatch(campaign(), "village", null, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)), supplyDays = 1).state
        val original = state.armySize
        val id = state.world.playerFieldArmies.single().id
        repeat(3) { state = tick(state) }
        assertTrue(state.armySize < original)
        assertTrue(state.world.armies.first { it.id == id }.morale < 80)
        assertTrue(state.chronicle.any { it.title == "Marschverluste" })
        WorldEngine.validate(state)
    }

    @Test fun fourSeasonsAndPersistentRegionalWeatherAffectMarching() {
        assertEquals(Season.SPRING, Season.forDay(1))
        assertEquals(Season.SUMMER, Season.forDay(31))
        assertEquals(Season.AUTUMN, Season.forDay(61))
        assertEquals(Season.WINTER, Season.forDay(91))
        assertEquals(Season.SPRING, Season.forDay(121))
        val state = campaign()
        val tomorrow = tick(state)
        state.world.weather.regions.forEach { old ->
            assertEquals(old, tomorrow.world.weather.regions.first { it.regionId == old.regionId })
        }
        val army = WorldArmy("test", PLAYER_FACTION, "Test", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)), "keep")
        val clear = state.copy(world = state.world.copy(weather = WeatherState(Season.SUMMER)))
        val winter = state.copy(world = state.world.copy(weather = WeatherState(Season.WINTER, listOf(RegionalWeather("village", WeatherKind.SNOW, 99)))))
        assertTrue(WorldEngine.movementPerDay(winter, army, "village") < WorldEngine.movementPerDay(clear, army, "village"))
    }

    @Test fun fogDoesNotExposeRemoteEnemyAndScoutingHasCostAndExactReport() {
        val start = campaign()
        assertTrue(WorldEngine.observations(start).none { it.armyId == "army_ash_01" })
        assertFalse("capital_ash_01" in start.world.knowledgeFor(PLAYER_FACTION).visibleRegions)
        val scouted = WorldEngine.scout(start, "ruin").state
        assertEquals(start.resources.gold - 75, scouted.resources.gold)
        assertEquals(start.resources.food - 100, scouted.resources.food)
        assertTrue("ruin" in scouted.world.knowledgeFor(PLAYER_FACTION).exploredRegions)
        // A distant capital is still unreachable until intermediate regions have been mapped.
        assertEquals(start, WorldEngine.scout(start, "capital_ash_01").state)
        assertTrue(start.world.knowledge.filter { it.factionId != PLAYER_FACTION }.any { "keep" !in it.exploredRegions })
    }

    @Test fun depotAndConvoyConsumeResourcesAndDeliverToTheExistingArmy() {
        val base = campaign()
        val withDepot = WorldEngine.buildDepot(base, "keep", 1000).state
        assertEquals(base.resources.food - 1000, withDepot.resources.food)
        assertEquals(1000, withDepot.world.depots.single().food)
        var state = WorldEngine.dispatch(withDepot, "village", null, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50)), supplyDays = 2).state
        val id = state.world.playerFieldArmies.single().id
        state = WorldEngine.sendConvoy(state, id, 500).state
        repeat(3) { state = tick(state) }
        assertTrue(state.world.convoys.single().complete)
        assertEquals(1, state.world.armies.count { it.id == id })
        WorldEngine.validate(state)
    }

    @Test fun storyChainRemembersChoiceAndAppliesConsequences() {
        var state = campaign().copy(day = 12)
        state = WorldEngine.tick(state)
        val initial = state.world.stories.single()
        assertEquals("border_signal", initial.key)
        assertTrue(initial.options.isEmpty())
        state = WorldEngine.tick(state.copy(day = 14))
        val pending = state.world.stories.single()
        assertEquals(1, pending.stage)
        assertEquals(3, pending.options.size)
        val oldFood = state.resources.food
        val resolved = WorldEngine.chooseStory(state, pending.id, 0).state
        assertEquals(oldFood - 400, resolved.resources.food)
        assertTrue(resolved.realm.scoutingDays >= 7)
        assertEquals(0, resolved.world.stories.single().choice)
        assertTrue(resolved.world.stories.single().resolved)
        assertEquals(resolved, SaveCodec.decode(SaveCodec.encode(resolved)))
    }

    @Test fun invalidReservationsAndReferencesAreRejected() {
        val state = campaign()
        val bad = WorldArmy("invalid", PLAYER_FACTION, "Invalid", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 999)), "keep")
        assertThrows(IllegalArgumentException::class.java) { WorldEngine.validate(state.copy(world = state.world.copy(armies = state.world.armies + bad))) }
        assertThrows(IllegalArgumentException::class.java) { WorldEngine.validate(state.copy(world = state.world.copy(roads = state.world.roads + WorldRoad("bad", "keep", "missing", 10)))) }
    }

    @Test fun invasionBindsExistingHostileArmyWithoutAnExtraForce() {
        var state = campaign()
        state = DiplomacyEngine.changeRelation(state, PLAYER_FACTION, "ash_covenant", -10, -10, "Testkrieg", atWar = true)
        val count = state.world.armies.size
        val actual = state.world.armies.first { it.factionId == "ash_covenant" }.total
        state = WorldEngine.bindInvasion(state.copy(invasion = Invasion(EnemyType.ORC, state.day + 6, 9000, state.day)))
        assertEquals(count, state.world.armies.size)
        assertEquals(actual, state.invasion!!.strength)
        assertEquals(state.invasion!!.worldArmyId, state.world.invasionArmyId)
        assertEquals("keep", state.world.armies.first { it.id == state.world.invasionArmyId }.destinationId)
    }
}
