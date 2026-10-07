package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Fixtures were encoded by the unchanged v0.97 sources, before any Sprint A source edits. */
class SchemaFiveMigrationTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun running() = fixture("actual-v097-running-save.json")
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

    private fun preservesOldFields(old: JsonElement, current: JsonElement, path: String = "") {
        if (path in setOf("version", "_checksum", "battleSession.combatVersion")) return
        when (old) {
            is JsonObject -> old.forEach { (key, value) ->
                assertTrue("$path.$key still exists", key in current.jsonObject)
                preservesOldFields(value, current.jsonObject.getValue(key), if (path.isEmpty()) key else "$path.$key")
            }
            is JsonArray -> {
                assertEquals(path, old.size, current.jsonArray.size)
                old.forEachIndexed { index, value -> preservesOldFields(value, current.jsonArray[index], "$path[$index]") }
            }
            else -> assertEquals(path, old, current)
        }
    }

    @Test fun realV097RunningBattleMigratesEveryExistingFieldAndNeverDebitsSuppliesAgain() {
        val original = Json.parseToJsonElement(running()).jsonObject
        assertEquals(4, original.getValue("version").jsonPrimitive.int)
        val migrated = SaveCodec.decode(running())
        assertEquals(5, migrated.version)
        assertEquals(3, migrated.battleSession!!.combatVersion)
        preservesOldFields(original, Json.parseToJsonElement(SaveCodec.encode(migrated)))
        assertEquals(migrated, SaveCodec.decode(SaveCodec.encode(migrated)))
        assertEquals(migrated.militaryStock, BattleStateEngine.migrate(migrated).militaryStock)
        assertEquals(migrated, BattleStateEngine.migrate(migrated))
        val resumed = BattleEngine.advance(migrated, BattleDecision.HOLD).state
        assertEquals(migrated.battleSession.minute + 5, resumed.battleSession!!.minute)
        assertEquals(resumed, SaveCodec.decode(SaveCodec.encode(resumed)))
    }

    @Test fun oldHistoryAndReplaysRemainReadableAndHistoricalResultsStayUntouched() {
        val raw = fixture("actual-v097-history-save.json")
        val migrated = SaveCodec.decode(raw)
        preservesOldFields(Json.parseToJsonElement(raw), Json.parseToJsonElement(SaveCodec.encode(migrated)))
        val historical = migrated.war.history.last()
        assertEquals(2, historical.replay!!.rulesVersion)
        val replay = BattleEngine.replay(historical)
        assertNotNull(replay)
        assertEquals(replay, BattleEngine.replay(historical))
        assertEquals(historical, migrated.war.history.last())
    }

    @Test fun eachSlotKeepsByteExactSchemaFourMigrationBackupThroughAutosaveAndRestart() {
        val storage = Storage()
        for (slot in 1..3) {
            val suffix = if (slot == 1) "" else "_slot_$slot"
            storage.values["game_state_v1$suffix"] = running()
        }
        val saves = SaveRepository(storage)
        for (slot in 1..3) {
            saves.selectSlot(slot)
            val state = saves.load()!!
            val key = "migration_backup_v4" + if (slot == 1) "" else "_slot_$slot"
            assertEquals(running(), storage.values[key])
            repeat(3) { saves.save(state) }
            assertEquals(running(), storage.values[key])
            val restarted = SaveRepository(storage)
            restarted.selectSlot(slot)
            assertEquals(state, restarted.load())
        }
    }

    @Test fun failedMigrationCommitPreservesAllOriginalBytes() {
        val storage = Storage()
        storage.values["game_state_v1"] = running()
        val original = storage.values.toMap()
        storage.reject = true
        val saves = SaveRepository(storage)
        assertEquals(5, saves.load()!!.version)
        assertNotNull(saves.lastError)
        assertEquals(original, storage.values)
    }

    @Test fun schemaFourCannotLoseItsChecksumRequirementDuringUpgrade() {
        val root = Json.parseToJsonElement(running()).jsonObject
        for (bad in listOf(JsonObject(root - "_checksum"), JsonObject(root + ("day" to JsonPrimitive(999))))) {
            try { SaveCodec.decode(bad.toString()); fail("Corrupted schema four must be rejected") }
            catch (_: SaveFormatException) { }
        }
    }

    @Test fun schemaFiveReencodesAndRejectsUnknownFutureSchemaWithoutOverwriting() {
        val state = SaveCodec.decode(running())
        assertEquals(5, Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject.getValue("version").jsonPrimitive.int)
        val storage = Storage()
        storage.values["game_state_v1"] = """{"version":6}"""
        val before = storage.values.toMap()
        val saves = SaveRepository(storage)
        assertNull(saves.load())
        saves.save(state)
        assertEquals(before, storage.values)
    }
}
