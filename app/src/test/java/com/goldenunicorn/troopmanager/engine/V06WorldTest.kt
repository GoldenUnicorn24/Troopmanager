package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V06WorldTest {
    @Test fun regionMissionTravelsDecidesOperatesAndReturnsThroughOneIdentity() {
        var state = GameEngine.newGame("Ada", 25, Species.HUMAN, null).copy(resources = Resources(food = 50000))
        val originalArmy = state.armySize
        state = MissionEngine.start(state, MissionType.RELIEF, 1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 180), UnitAllocation(UnitType.HUMAN_ARCHER, 120)), "village").state
        val id = state.activeMissions.single().id
        val armyId = state.world.armies.single { it.missionId == id }.id
        assertEquals(originalArmy, state.armySize)
        assertEquals(300, state.awayArmySize)
        assertEquals(MissionPhase.OUTBOUND, state.activeMissions.single().phase)
        var sawDecision = false
        var sawReturn = false
        repeat(35) {
            state.activeMissions.first { it.id == id }.pendingDecision?.let {
                sawDecision = true
                state = MissionEngine.chooseRoute(state, id, 0).state
            }
            state = MissionEngine.tick(state.copy(day = state.day + 1), 1.25)
            val mission = state.activeMissions.first { it.id == id }
            if (mission.phase == MissionPhase.RETURNING && mission.outcome != null) {
                sawReturn = true
                assertTrue(state.awayArmySize > 0)
                assertTrue(state.commanderAway(1))
            }
            WorldEngine.validate(state)
            state = SaveCodec.decode(SaveCodec.encode(state))
        }
        assertTrue(sawDecision)
        assertTrue(sawReturn)
        assertFalse(state.activeMissions.first { it.id == id }.status.isAway)
        assertEquals(WorldArmyStatus.HOME, state.world.armies.first { it.id == armyId }.status)
        assertEquals(originalArmy - state.activeMissions.first { it.id == id }.losses, state.armySize)
        assertEquals(0, state.awayArmySize)
        assertTrue(state.commanders.first { it.id == 1L }.missionsCompleted == 1)
    }

    @Test fun strategicAiMovesBuildsAndRecruitsFromItsFiniteTreasury() {
        var state = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
        val starting = state.world.faction("ash_covenant")!!
        repeat(45) { state = WorldEngine.tick(state.copy(day = state.day + 1)) }
        val faction = state.world.faction(starting.id)!!
        assertTrue(faction.buildings > starting.buildings)
        assertTrue(faction.lastDecisionDay > 0)
        assertTrue(state.world.armies.any { it.factionId == starting.id && (it.regionId != starting.capitalId || it.units.sumOf { u -> u.amount } > 360) })
        assertTrue(state.world.places.any { it.ownerId == starting.id && it.id != starting.capitalId && it.id != "orc" })
        assertTrue(faction.gold >= 0 && faction.food >= 0)
        assertEquals(3, state.world.enemyCommanders.size)
        WorldEngine.validate(state)
    }

    @Test fun completedFieldBattleReleasesOnlyItsSurvivorsBackToFieldReservation() {
        var state = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
        state = WorldEngine.dispatch(state, "village", 1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100))).state
        val own = state.world.playerFieldArmies.single()
        val enemy = state.world.armies.first { it.factionId == "ash_covenant" }
        state = state.copy(world = state.world.copy(armies = state.world.armies.map {
            if (it.id == enemy.id) it.copy(regionId = "keep", destinationId = null, route = emptyList(), routeIndex = 0, legProgress = 0) else it
        }))
        state = WorldEngine.tick(state.copy(day = state.day + 1))
        // Force the same observed location, independently from road timing, to test the engagement boundary.
        state = state.copy(world = state.world.copy(armies = state.world.armies.map { if (it.id == own.id) it.copy(regionId = "keep", route = emptyList(), routeIndex = 0, legProgress = 0, destinationId = null, status = WorldArmyStatus.HOLDING) else it }))
        state = DiplomacyEngine.changeRelation(state, PLAYER_FACTION, "ash_covenant", -50, -20, "Testkrieg", atWar = true)
        state = WorldEngine.scout(state, "keep").state
        state = WorldEngine.engage(state, own.id, enemy.id).state
        assertNotNull(state.battleSession)
        assertTrue(state.battleSession!!.isActive)
        assertEquals(100, state.battleSession!!.ownStart)
        assertEquals(enemy.factionId, state.battleSession!!.enemyFactionId)
        assertEquals(enemy.name, state.battleSession!!.enemyArmyName)
        assertEquals(0, state.world.playerFieldArmies.size)
        val active = state.battleSession!!
        val extraSoldier = active.copy(contingents = active.contingents.mapIndexed { index, unit -> if (index == 0) unit.copy(startSoldiers = unit.startSoldiers + 1) else unit })
        val corrupt = state.copy(battleSession = extraSoldier)
        assertThrows(IllegalArgumentException::class.java) { SaveCodec.encode(corrupt) }
        repeat(40) {
            if (state.battleSession!!.isActive) state = WorldEngine.reconcileBattle(BattleEngine.advance(state, state.battleSession!!.pendingEvent?.options?.first()).state)
        }
        assertFalse(state.battleSession!!.isActive)
        assertNull(state.world.encounter)
        val survivors = state.world.armies.first { it.id == own.id }
        assertEquals(state.battleSession!!.ownRemaining, survivors.total)
        assertTrue(state.armySize <= 330)
        assertEquals(survivors.total, state.awayArmySize)
        WorldEngine.validate(state)
    }
    @Test fun aiRecruitmentPaysUnitCostsReservesPopulationAndWaitsForTraining() {
        var state = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
        val original = state.world.faction("ash_covenant")!!
        val army = state.world.armies.first { it.factionId == original.id }
        val type = army.units.first().type
        state = WorldEngine.tick(state.copy(day = 6))
        val order = state.world.recruitments.single { it.factionId == original.id }
        assertEquals(50, order.amount)
        assertEquals(6 + type.trainingDays, order.completionDay)
        assertEquals(army.total, state.world.armies.first { it.id == army.id }.total)
        // Two owned places and one workshop fund the treasury; military maintenance is explicit.
        val goldIncome = 2 * 35 + original.buildings * 10 - army.total / 25
        assertEquals(original.gold + goldIncome - 50 * type.goldCost, state.world.faction(original.id)!!.gold)
        assertEquals(original.iron + original.buildings * 3 - 50 * type.ironCost, state.world.faction(original.id)!!.iron)
        while (state.day < order.completionDay - 1) {
            state = WorldEngine.tick(state.copy(day = state.day + 1))
            assertEquals(army.total, state.world.armies.first { it.id == army.id }.total)
        }
        state = WorldEngine.tick(state.copy(day = state.day + 1))
        assertEquals(army.total + 50, state.world.armies.first { it.id == army.id }.total)
        assertTrue(state.world.recruitments.none { it.id == order.id })
        WorldEngine.validate(state)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun aiCannotRecruitWithoutIronOrUnreservedCitizens() {
        val base = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
        val army = base.world.armies.first { it.factionId == "ash_covenant" }
        listOf(
            base.copy(world = base.world.copy(factions = base.world.factions.map { if (it.id == "ash_covenant") it.copy(iron = 0) else it })),
            base.copy(world = base.world.copy(factions = base.world.factions.map { if (it.id == "ash_covenant") it.copy(population = army.total) else it })),
        ).forEach { constrained ->
            val result = WorldEngine.tick(constrained.copy(day = 6))
            assertTrue(result.world.recruitments.none { it.factionId == "ash_covenant" })
            assertEquals(army.total, result.world.armies.first { it.id == army.id }.total)
            WorldEngine.validate(result)
        }
    }

    @Test fun invasionFindsAccessibleDetourAroundPeacefulBorderAndForecastUsesRemainingDistance() {
        var state = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
        state = DiplomacyEngine.changeRelation(state, PLAYER_FACTION, "ash_covenant", -50, -20, "Testkrieg", atWar = true)
        state = state.copy(world = state.world.copy(places = state.world.places.map { if (it.id == "village") it.copy(ownerId = "copper_league") else it }))
        val path = WorldEngine.routeForFaction(state, "ash_covenant", "capital_ash_01", "keep")
        assertTrue(path.isNotEmpty())
        assertFalse("village" in path)
        assertTrue(WorldEngine.routeAllowed(state, "ash_covenant", path))
        state = WorldEngine.bindInvasion(state.copy(invasion = Invasion(EnemyType.ORC, 7, 1000, 1)))
        assertNotNull(state.invasion)
        val invader = state.world.armies.first { it.id == state.world.invasionArmyId }
        assertFalse("village" in invader.route)
        val place = "keep"
        val route = listOf(place, "village")
        val army = WorldArmy("forecast", PLAYER_FACTION, "Forecast", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50)), place, route = route)
        val clear = state.copy(world = state.world.copy(weather = WeatherState(Season.SUMMER)))
        val distance = clear.world.roads.first { it.connects(place) && it.other(place) == "village" }.distance
        val speed = WorldEngine.movementPerDay(clear, army, "village")
        val remaining = WorldEngine.travelDays(clear, army.copy(legProgress = distance - 1), route)
        assertEquals(1, remaining)
        assertTrue(WorldEngine.travelDays(clear, army, route) >= remaining)
        assertTrue(speed > 0)
    }

    @Test fun saveValidationRejectsDuplicateCommanderReservation() {
        var state = GameEngine.newGame("Ada", 25, Species.HUMAN, null)
        state = WorldEngine.dispatch(state, "village", 1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 50))).state
        val army = state.world.playerFieldArmies.single()
        val duplicate = army.copy(id = "duplicate", units = listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 50)))
        assertThrows(IllegalArgumentException::class.java) { SaveCodec.encode(state.copy(world = state.world.copy(armies = state.world.armies + duplicate))) }
    }

}
