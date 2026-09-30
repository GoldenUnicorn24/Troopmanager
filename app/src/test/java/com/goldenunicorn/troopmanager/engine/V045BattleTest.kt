package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import com.goldenunicorn.troopmanager.data.SaveCodec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.*
import org.junit.Test

class V045BattleTest {
    private fun army() = GameState(
        player = CharacterProfile("Leon"),
        population = Population(human = 2000),
        armyPools = listOf(
            ArmyUnitPool(UnitType.HUMAN_SWORD, 1000),
            ArmyUnitPool(UnitType.KNIGHT, 400),
            ArmyUnitPool(UnitType.DRAGON_ARTILLERY, 80),
        ),
    )

    private fun event(state: GameState, decision: BattleDecision, minute: Int = 10, section: BattleSection = BattleSection.CENTER): GameState {
        val session = state.battleSession!!
        return state.copy(battleSession = session.copy(minute = minute,
            pendingEvent = BattleEvent("Test", "Konkreter Befehl", section, listOf(decision))))
    }

    @Test
    fun orderedRetreatEndsImmediatelyWithLimitedAdditionalLossesAndRealPoolWear() {
        val started = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 27, enemyStrength = 1200).state
        val before = event(started, BattleDecision.ORDERED_RETREAT)
        val after = BattleEngine.advance(before, BattleDecision.ORDERED_RETREAT).state
        val report = after.battleSession!!
        assertEquals(BattleStatus.DEFEAT, report.status)
        assertTrue(report.orderedRetreat)
        assertFalse(report.isActive)
        assertTrue(before.armySize - after.armySize in 1..16)
        assertEquals(400, report.soldiers(BattleSection.RESERVE))
        assertTrue(report.equipmentDamage > 0)
        assertTrue(after.armyPools.all { it.equipment < 80 })
        assertEquals(30, report.xpReward)
        assertEquals(4, report.renownReward)
        assertEquals(1, after.defeats)
        assertEquals(after, BattleEngine.advance(after).state)
    }

    @Test
    fun artilleryTargetDestroysStrongestRealDeviceAndNeedsArtillery() {
        val started = BattleEngine.start(army(), EnemyType.URUK, Tactic.FORTIFY, seed = 43).state
        val before = event(started, BattleDecision.ARTILLERY_TARGET)
        assertTrue(SiegeDevice.CATAPULT in before.battleSession!!.devices)
        val after = BattleEngine.advance(before, BattleDecision.ARTILLERY_TARGET).state
        assertFalse(SiegeDevice.CATAPULT in after.battleSession!!.devices)
        assertTrue(after.battleSession!!.log.last().text.contains("Gezieltes Artilleriefeuer"))
        val unavailable = before.copy(battleSession = before.battleSession!!.copy(
            contingents = before.battleSession!!.contingents.filter { it.type != UnitType.DRAGON_ARTILLERY }))
        assertEquals(unavailable, BattleEngine.advance(unavailable, BattleDecision.ARTILLERY_TARGET).state)
    }

    @Test
    fun gateOrdersOnlyAvailableDuringSiegesAndReduceWallDamage() {
        val started = BattleEngine.start(army(), EnemyType.URUK, Tactic.FORTIFY, seed = 53).state
        val held = BattleEngine.advance(event(started, BattleDecision.HOLD_GATE), BattleDecision.HOLD_GATE).state.battleSession!!
        val ordinary = BattleEngine.advance(event(started, BattleDecision.HOLD), BattleDecision.HOLD).state.battleSession!!
        assertTrue(held.wallIntegrity > ordinary.wallIntegrity)
        val open = BattleEngine.advance(event(started, BattleDecision.OPEN_GATE), BattleDecision.OPEN_GATE).state.battleSession!!
        assertEquals(0, open.soldiers(BattleSection.RESERVE))
        assertTrue(open.contingents.any { it.type == UnitType.KNIGHT && it.section == BattleSection.CENTER })
        assertTrue(open.log.last().text.contains("Kavallerieausfall"))
        val field = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 53).state
        val illegal = event(field, BattleDecision.OPEN_GATE)
        assertEquals(illegal, BattleEngine.advance(illegal, BattleDecision.OPEN_GATE).state)
    }

    @Test
    fun reserveRelocationHasLimitedMovementAndNeverLosesInitialTotals() {
        val before = event(BattleEngine.start(army(), EnemyType.ORC, Tactic.HOLD, seed = 15).state, BattleDecision.RELOCATE_RESERVE)
        val after = BattleEngine.advance(before, BattleDecision.RELOCATE_RESERVE).state.battleSession!!
        assertEquals(300, after.soldiers(BattleSection.RESERVE))
        assertEquals(before.battleSession!!.ownStart, after.contingents.sumOf { it.startSoldiers })
        assertTrue(after.contingents.any { it.type == UnitType.KNIGHT && it.section == BattleSection.CENTER && it.morale > 80 })
    }

    @Test
    fun rescueMovesTwentyReservesAndRestoresPartialCommandEffectiveness() {
        val owned = army().copy(
            commanders = listOf(Commander(1, "Marcus", Culture.HUMAN, "human")),
            commanderAssignments = listOf(CommanderAssignment(1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300), UnitAllocation(UnitType.DRAGON_ARTILLERY, 10)))),
        )
        val started = BattleEngine.start(owned, EnemyType.ORC, Tactic.HOLD, seed = 16).state
        val wounded = started.copy(battleSession = started.battleSession!!.copy(
            contingents = started.battleSession!!.contingents.map { if (it.commanderId == 1L && it.type == UnitType.HUMAN_SWORD) it.copy(commanderWounded = true) else it }))
        val before = event(wounded, BattleDecision.RESCUE_COMMANDER, section = BattleSection.LEFT)
        val after = BattleEngine.advance(before, BattleDecision.RESCUE_COMMANDER).state.battleSession!!
        assertEquals(380, after.soldiers(BattleSection.RESERVE))
        assertTrue(after.contingents.filter { it.commanderId == 1L }.all { it.commanderRescued && it.commanderWounded })
        assertTrue(after.commanderEvents.single().contains("Marcus"))
        val rescued = BattleEngine.advance(before, BattleDecision.RESCUE_COMMANDER).state
        assertEquals(rescued, SaveCodec.decode(SaveCodec.encode(rescued)))
        val reloaded = Json.decodeFromString<GameState>(Json.encodeToString(before))
        assertEquals(BattleEngine.advance(before, BattleDecision.RESCUE_COMMANDER).state,
            BattleEngine.advance(reloaded, BattleDecision.RESCUE_COMMANDER).state)
    }

    @Test
    fun reportHistoryOnlyCountsDeployedCommandersAndRetainsIdentity() {
        val commander = Commander(1, "Marcus", Culture.HUMAN, "human", portraitUri = "content://custom", sword = 81,
            trait = "Erfahren", battlesFought = 7, casualties = 20)
        val idle = Commander(2, "Dorian", Culture.HUMAN, "human")
        val owned = army().copy(commanders = listOf(commander, idle),
            commanderAssignments = listOf(CommanderAssignment(1, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 500)))))
        val before = event(BattleEngine.start(owned, EnemyType.ORC, Tactic.HOLD, seed = 23).state, BattleDecision.ORDERED_RETREAT)
        val after = BattleEngine.advance(before, BattleDecision.ORDERED_RETREAT).state
        val deployedLoss = after.battleSession!!.contingents.filter { it.commanderId == 1L }.sumOf { it.startSoldiers - it.soldiers }
        assertEquals(commander.copy(battlesFought = 8, casualties = 20 + deployedLoss), after.commanders.first { it.id == 1L })
        assertEquals(idle, after.commanders.first { it.id == 2L })
    }

    @Test
    fun equipmentWearIsWeightedToDeployedSurvivorsOnly() {
        val owned = army().copy(activeMissions = listOf(ActiveMission(1, MissionType.PATROL, null,
            listOf(UnitAllocation(UnitType.HUMAN_SWORD, 900)), 1, 1, 100, 1)))
        val deployments = listOf(BattleDeployment(null, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100))))
        val before = event(BattleEngine.start(owned, EnemyType.ORC, Tactic.HOLD, deployments, seed = 29).state, BattleDecision.ORDERED_RETREAT)
        val after = BattleEngine.advance(before, BattleDecision.ORDERED_RETREAT).state
        assertEquals(80, after.armyPools.first { it.type == UnitType.KNIGHT }.equipment)
        assertTrue(after.armyPools.first { it.type == UnitType.HUMAN_SWORD }.equipment >= 79)
        assertEquals(900, after.away(UnitType.HUMAN_SWORD))
    }

    @Test
    fun newSessionFieldsDeserializeAbsentFromV04Save() {
        val before = BattleEngine.start(army(), EnemyType.ORC, Tactic.HOLD, seed = 31).state
        val encoded = Json.encodeToString(before)
        val after = Json.decodeFromString<GameState>(encoded)
        assertEquals(before, after)
        assertEquals(0, after.battleSession!!.xpReward)
        assertFalse(after.battleSession!!.orderedRetreat)
    }

    @Test
    fun versionTwoActiveBattleRetainsPendingOrdersAndContinuesAfterMigration() {
        var original = BattleEngine.start(army(), EnemyType.URUK, Tactic.FORTIFY, seed = 39).state
        repeat(2) { original = BattleEngine.advance(original).state }
        original = original.copy(battleSession = original.battleSession!!.copy(
            pendingEvent = original.battleSession!!.pendingEvent!!.copy(options = listOf(BattleDecision.HOLD, BattleDecision.SEND_RESERVE))))
        val root = Json.parseToJsonElement(SaveCodec.encode(original)).jsonObject.toMutableMap()
        root["version"] = JsonPrimitive(2)
        root.remove("city")
        val battle = root.getValue("battleSession").jsonObject.toMutableMap()
        listOf("lootFood", "xpReward", "renownReward", "equipmentDamage", "commanderEvents", "orderedRetreat").forEach { battle.remove(it) }
        battle["contingents"] = JsonArray(battle.getValue("contingents").jsonArray.map { c -> JsonObject(c.jsonObject.filterKeys { it != "commanderRescued" }) })
        root["battleSession"] = JsonObject(battle)
        val migrated = SaveCodec.decode(JsonObject(root).toString())
        assertEquals(original.battleSession, migrated.battleSession)
        assertEquals(BattleEngine.advance(original.copy(city = migrated.city), BattleDecision.HOLD).state,
            BattleEngine.advance(migrated, BattleDecision.HOLD).state)
    }
}
