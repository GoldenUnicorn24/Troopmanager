package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class V06SaveTest {
    private class Storage : SaveStorage {
        val values = mutableMapOf<String, String>()
        var reject = false
        override fun contains(key: String) = key in values
        override fun read(key: String) = values[key]
        override fun commit(values: Map<String, String?>): Boolean {
            if (reject) return false
            values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
            return true
        }
    }
    private fun game(name: String = "Mauer") = GameEngine.newGame(name, 27, Species.HALF_ELF, "content://portrait")
    private fun legacyV3(state: GameState): String {
        val root = Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject
        return JsonObject(root.filterKeys { it !in listOf("world", "war", "court", "dynasty", "diplomacy", "espionage", "society", "presentation", "settings", "_checksum") } + ("version" to JsonPrimitive(3))).toString()
    }

    @Test fun threeSlotsAreIndependentAndMetadataSurvivesRestart() {
        val storage = Storage()
        val saves = SaveRepository(storage)
        val states = (1..3).map { game("Reich $it").copy(day = it * 10) }
        states.forEachIndexed { index, state -> saves.selectSlot(index + 1); saves.save(state); assertNull(saves.lastError) }
        val restarted = SaveRepository(storage)
        (1..3).forEach { slot -> restarted.selectSlot(slot); assertEquals(states[slot - 1], restarted.load()) }
        assertEquals(listOf("Reich 1", "Reich 2", "Reich 3"), restarted.slots().map { it!!.playerName })
        restarted.selectSlot(2); restarted.delete()
        assertNull(restarted.load())
        restarted.selectSlot(1); assertEquals(states[0], restarted.load())
        restarted.selectSlot(3); assertEquals(states[2], restarted.load())
    }

    @Test fun v45BackupIsByteExactAndProtectedAgainstRotation() {
        val storage = Storage()
        val legacy = legacyV3(game())
        storage.values["game_state_v1"] = legacy
        val saves = SaveRepository(storage)
        val loaded = saves.load()!!
        assertEquals(SaveCodec.CURRENT_VERSION, loaded.version)
        assertEquals(legacy, storage.values["migration_backup_v3"])
        repeat(4) { saves.save(loaded.copy(day = loaded.day + it)) }
        assertEquals(legacy, storage.values["migration_backup_v3"])
        assertTrue(loaded.world.initialized)
    }

    @Test fun checksumDetectsValidJsonTamperingAndRecoversBackup() {
        val storage = Storage(); val saves = SaveRepository(storage); val initial = game()
        saves.save(initial); saves.save(initial.copy(day = 2))
        val root = Json.parseToJsonElement(storage.values.getValue("game_state_v1")).jsonObject
        storage.values["game_state_v1"] = JsonObject(root + ("day" to JsonPrimitive(999))).toString()
        assertEquals(initial, saves.load())
        assertNotNull(saves.lastError)
    }

    @Test fun failedAtomicCommitAndFutureSchemaPreserveOriginalBytes() {
        val storage = Storage(); val saves = SaveRepository(storage); saves.save(game())
        val before = storage.values.toMap()
        storage.reject = true; saves.save(game("Anderes")); assertEquals(before, storage.values); assertNotNull(saves.lastError)
        storage.reject = false
        storage.values["game_state_v1"] = """{"version":99}"""
        val future = storage.values.toMap()
        assertNull(saves.load()); saves.save(game()); assertEquals(future, storage.values)
    }

    @Test fun ironmanHasOneAutosaveAndCannotImportRollback() {
        val storage = Storage(); val saves = SaveRepository(storage)
        val state = game().let { it.copy(settings = it.settings.copy(ironman = true)) }
        saves.save(state); saves.save(state.copy(day = 2))
        assertFalse(storage.contains("game_state_backup_v2"))
        assertNull(saves.importSave(SaveCodec.encode(game())))
        assertEquals(2, saves.load()!!.day)
        storage.values["game_state_v1"] = "broken"
        assertNull(saves.load())
    }

    @Test fun exportImportChecksPayloadAndSlotBoundaries() {
        val saves = SaveRepository(Storage()); saves.save(game())
        val raw = saves.exportSave()!!
        saves.selectSlot(3)
        assertEquals(game(), saves.importSave(raw))
        assertEquals(game(), saves.load())
        assertNull(saves.importSave("{"))
        assertEquals(game(), saves.load())
    }

    @Test fun futureVersionWithDifferentChecksumNeverFallsBackOrOverwrites() {
        val storage = Storage(); val saves = SaveRepository(storage); saves.save(game())
        val root = Json.parseToJsonElement(storage.values.getValue("game_state_v1")).jsonObject
        storage.values["game_state_v1"] = JsonObject(root + ("version" to JsonPrimitive(99))).toString()
        val before = storage.values.toMap()
        assertNull(saves.load())
        assertEquals(before, storage.values)
        saves.save(game("Neu"))
        assertEquals(before, storage.values)
    }

    @Test fun currentSaveWithoutChecksumCannotBeImportedAndLegacyStillMigrates() {
        val saves = SaveRepository(Storage()); val original = game(); saves.save(original)
        val root = Json.parseToJsonElement(SaveCodec.encode(original)).jsonObject
        val unsigned = JsonObject(root - "_checksum").toString()
        assertNull(saves.importSave(unsigned))
        assertEquals(original, saves.load())
        assertEquals(SaveCodec.CURRENT_VERSION, SaveCodec.decode(legacyV3(original)).version)
    }
}
