package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontierPersistenceTest {
    @Test
    fun previousGameStateWithoutFrontierGetsEmptyAdditiveDefaults() {
        val state = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        val json = Json { encodeDefaults = true }
        val original = json.parseToJsonElement(json.encodeToString(state)).jsonObject
        val oldPayload = JsonObject(original - "frontier").toString()
        val migrated = json.decodeFromString<GameState>(oldPayload)
        assertEquals(state.armySize, migrated.armySize)
        assertTrue(migrated.frontier.hordes.isEmpty())
        assertTrue(migrated.frontier.reinforcements.isEmpty())
        assertEquals(3, migrated.frontier.allies.size)
        assertEquals(0, migrated.frontier.bond.sessions)
    }

    @Test
    fun travellingHelpAndWeaponConstructionSurviveSaveAndResume() {
        val original = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
        val ordered = FrontierEngine.buyReinforcements(original, AllyPeople.GOLD_ELVES, 20).state
        val building = FrontierEngine.buildWallWeapon(ordered, WallWeaponType.BALLISTA).state
        val restored = SaveCodec.decode(SaveCodec.encode(building))
        assertEquals(building.frontier, restored.frontier)
        assertEquals(building.armySize, restored.armySize)
        val movement = restored.frontier.reinforcements.single()
        val arrived = FrontierEngine.tick(restored.copy(day = movement.arrivalDay))
        assertEquals(original.armySize + 20, arrived.armySize)
        assertTrue(arrived.frontier.reinforcements.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAmmoCannotEnterSaveState() {
        val state = GameState(player = CharacterProfile("Leon"), frontier = FrontierState(
            weapons = listOf(WallWeaponStock(WallWeaponType.BALLISTA, count = 0, ammunition = 5)),
        ))
        FrontierEngine.validate(state)
    }
}
