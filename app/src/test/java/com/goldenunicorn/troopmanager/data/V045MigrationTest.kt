package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class V045MigrationTest {
    // Literal v0.4 schema: no city or commander-history fields, active mission and live battle.
    // Large stock and population intentionally exceed every v0.45 new-game capacity.
    private fun v2() = """
        {"version":2,"day":82,
         "player":{"name":"Leon","age":31,"species":"HALF_ELF","portraitUri":"content://player","level":7,"experience":164,"skillPoints":4,"sword":79,"bow":61,"riding":66,"leadership":72,"tactics":71,"diplomacy":58},
         "companion":{"met":true,"name":"Alina","trust":88,"respect":86,"affection":84,"role":"Mitregentin","portraitUri":"content://alina"},
         "resources":{"gold":87001,"food":91002,"wood":83003,"stone":76004,"iron":67005},
         "population":{"human":40000,"woodElf":18000,"goldElf":7000,"wall":15000,"humanRecruits":700,"woodElfRecruits":410,"goldElfRecruits":210,"wallRecruits":380},
         "realm":{"territory":6,"customSettlementName":"Eichenkrone","settlementTier":"FORTRESS","buildings":{"FARM":9,"SAWMILL":7,"QUARRY":8,"IRONWORKS":6,"MARKET":5,"BARRACKS":7,"WALL":8,"TOWER":6,"PALACE":4},"threat":47,"wallIntegrity":73,"tradeBonusDays":3,"scoutingDays":4},
         "armyPools":[{"type":"HUMAN_SWORD","soldiers":200,"experience":48,"morale":83,"equipment":64},{"type":"HUMAN_ARCHER","soldiers":80,"experience":34,"morale":74,"equipment":91},{"type":"WOOD_RANGER","soldiers":60,"experience":76,"morale":87,"equipment":72}],
         "trainingQueue":[{"id":77,"type":"KNIGHT","amount":20,"daysRemaining":4}],
         "commanders":[{"id":10,"name":"Aron","culture":"HUMAN","portraitKey":"human","portraitUri":"content://aron","level":4,"loyalty":86,"trait":"Vorausschauend","rank":"Oberst"},{"id":20,"name":"Elion","culture":"WOOD_ELF","portraitKey":"wood_elf","level":3,"loyalty":77}],
         "commanderAssignments":[{"commanderId":10,"units":[{"type":"HUMAN_SWORD","amount":100}]}],
         "activeMissions":[{"id":910,"missionType":"ESCORT","commanderId":20,"units":[{"type":"HUMAN_ARCHER","amount":50}],"startDay":81,"remainingDays":1,"supplyCost":100,"duration":2,"status":"RETURNING","regionId":"mine","quality":[{"type":"HUMAN_ARCHER","soldiers":50,"experience":34,"morale":74,"equipment":91}]}],
         "battleSession":{"enemy":"URUK","tactic":"FORTIFY","seed":19231,"ownStart":180,"enemyStart":300,
           "contingents":[{"type":"HUMAN_SWORD","commanderId":10,"section":"CENTER","soldiers":90,"startSoldiers":100,"experience":48,"morale":78,"equipment":64,"commanderWounded":true},{"type":"HUMAN_ARCHER","commanderId":null,"section":"LEFT","soldiers":25,"startSoldiers":30,"experience":34,"morale":70,"equipment":91},{"type":"WOOD_RANGER","commanderId":null,"section":"RESERVE","soldiers":40,"startSoldiers":50,"experience":76,"morale":81,"equipment":72}],
           "fronts":[{"section":"LEFT","enemySoldiers":75,"enemyStart":100,"morale":66,"position":43},{"section":"CENTER","enemySoldiers":60,"enemyStart":100,"morale":60,"position":38},{"section":"RIGHT","enemySoldiers":40,"enemyStart":100,"morale":49,"position":71}],
           "minute":35,"step":7,"phase":"MAIN","status":"ACTIVE","pendingEvent":{"title":"Das Tor wankt","text":"Verstärkung wird benötigt.","section":"CENTER","options":["SEND_RESERVE","HOLD"]},"devices":["RAM","LADDERS"],"wallIntegrity":61,"log":[{"minute":30,"text":"Die Mauer hält.","ownLosses":6,"enemyLosses":17}],"lastSounds":["ARROWS","SWORDS"],"lootGold":125,"lootFood":45,"xpReward":260,"renownReward":80},
         "invasion":{"enemy":"URUK","arrivalDay":82,"strength":300,"announcedDay":77,"devices":["RAM","LADDERS"],"alliesRequested":true},
         "relationship":{"actionDay":82,"spentActions":1,"pendingEvent":{"key":"court","title":"Am Hof","text":"Alina bittet um Rat."}},
         "pendingRealmEvent":{"key":"harvest","title":"Ernte","text":"Die Bauern warten.","category":"Wirtschaft"},
         "regions":[{"id":"keep","name":"Grenzfeste","type":"OWN","mission":"PATROL","owned":true},{"id":"mine","name":"Erzpass","type":"MINE","mission":"ESCORT","owned":false}],
         "renown":1572,"rank":"General","title":"König","victories":21,"defeats":3,"completedRealm":false,"tutorialSeen":true,
         "chronicle":[{"day":81,"title":"Karawane","text":"Elion zieht aus."}]}
    """.trimIndent()

    @Test
    fun actualV04ApkUiSavePreservesEveryFieldAfterMigrationAndReload() {
        val raw = javaClass.getResource("/actual-v04-ui-save.json")!!.readText()
        val old = Json.parseToJsonElement(raw).jsonObject
        assertEquals(2, old.getValue("version").jsonPrimitive.int)
        val migrated = SaveCodec.decode(raw)
        val reloaded = SaveCodec.decode(SaveCodec.encode(migrated))
        assertEquals(migrated, reloaded)
        val encoded = Json.parseToJsonElement(SaveCodec.encode(reloaded)).jsonObject
        old.filterKeys { it != "version" }.forEach { (key, value) -> assertExistingFields(value, encoded.getValue(key), key) }
    }

    @Test
    fun v04SaveMigratesAndReloadsWithoutChangingAnyExistingCoreField() {
        val original = Json.parseToJsonElement(v2()).jsonObject
        val loaded = SaveCodec.decode(v2())
        assertEquals(SaveCodec.CURRENT_VERSION, loaded.version)
        assertEquals("Eichenkrone", loaded.realm.customSettlementName)
        assertTrue(loaded.city.housingCapacity >= loaded.population.total)
        ResourceKind.entries.forEach { assertTrue(it.value(loaded.city.storageCapacity) >= it.value(loaded.resources)) }
        assertEquals(TaxLevel.NORMAL, loaded.city.taxLevel)
        assertEquals(WorkerPriority.BALANCED, loaded.city.workerPriority)
        assertEquals(50, loaded.city.satisfaction)
        assertTrue(loaded.city.constructionQueue.isEmpty())
        assertEquals(0, loaded.commanders.first().battlesFought)
        val encoded = SaveCodec.encode(loaded)
        val upgraded = Json.parseToJsonElement(encoded).jsonObject
        original.filterKeys { it != "version" }.forEach { (key, value) -> assertExistingFields(value, upgraded.getValue(key), key) }
        assertEquals(loaded, SaveCodec.decode(encoded))
        assertEquals(loaded.battleSession, SaveCodec.decode(encoded).battleSession)
        assertEquals(loaded.activeMissions, SaveCodec.decode(encoded).activeMissions)
        assertEquals(loaded.resources, EconomyEngine.day(loaded).resources)
    }

    @Test
    fun migrationCreatesExactProtectedBackupThatSurvivesAutosavesAndRecovery() {
        val storage = MemoryStorage()
        storage.values[PRIMARY] = v2()
        val repository = SaveRepository(storage)
        val migrated = repository.load()!!
        assertEquals(v2(), storage.values[MIGRATION])
        assertEquals(v2(), storage.values[BACKUP])
        repeat(5) { repository.save(migrated.copy(day = migrated.day + it)) }
        assertEquals(v2(), storage.values[MIGRATION])
        storage.values[PRIMARY] = "{broken"
        storage.values[BACKUP] = "{also broken"
        assertEquals(migrated, SaveRepository(storage).load())
        assertEquals(v2(), storage.values[MIGRATION])
        assertEquals(migrated, SaveCodec.decode(storage.values.getValue(PRIMARY)))
    }

    @Test
    fun failedMigrationCommitLeavesOldSaveByteExactAndLoadable() {
        val storage = MemoryStorage()
        storage.values[PRIMARY] = v2()
        storage.failCommit = true
        val repository = SaveRepository(storage)
        assertNotNull(repository.load())
        assertNotNull(repository.lastError)
        assertEquals(v2(), storage.values[PRIMARY])
        assertFalse(storage.values.containsKey(MIGRATION))
        storage.failCommit = false
        assertNotNull(repository.load())
        assertEquals(v2(), storage.values[MIGRATION])
    }

    @Test
    fun futureSchemaCannotOverwritePrimaryOrRollingBackup() {
        val storage = MemoryStorage()
        storage.values[PRIMARY] = """{"version":99}"""
        storage.values[BACKUP] = v2()
        val repository = SaveRepository(storage)
        assertNull(repository.load())
        repository.save(SaveCodec.decode(v2()))
        assertEquals("""{"version":99}""", storage.values[PRIMARY])
        assertEquals(v2(), storage.values[BACKUP])
        assertNotNull(repository.lastError)
    }

    @Test
    fun corruptedConstructionQueueIsRejectedRatherThanSilentlyReset() {
        val root = Json.parseToJsonElement(SaveCodec.encode(SaveCodec.decode(v2()))).jsonObject
        val city = root.getValue("city").jsonObject.toMutableMap()
        city["constructionQueue"] = Json.parseToJsonElement("""[{"id":1,"type":"WALL","targetLevel":9,"daysRemaining":-1,"totalDays":5}]""")
        val damaged = JsonObject(root.toMutableMap().apply { put("city", JsonObject(city)) }).toString()
        try { SaveCodec.decode(damaged); fail("Invalid construction queue accepted") } catch (_: SaveFormatException) { }
    }

    @Test
    fun negativeNewMissionAndBattleRewardsAreRejected() {
        val root = Json.parseToJsonElement(SaveCodec.encode(SaveCodec.decode(v2()))).jsonObject
        val mission = root.getValue("activeMissions").jsonArray.single().jsonObject
        listOf("xpReward", "renownReward").forEach { key ->
            val changed = JsonObject(mission.toMutableMap().apply { put(key, JsonPrimitive(-1)) })
            expectInvalid(JsonObject(root.toMutableMap().apply { put("activeMissions", JsonArray(listOf(changed))) }).toString())
        }
        val battle = root.getValue("battleSession").jsonObject
        listOf("xpReward", "renownReward", "lootFood", "equipmentDamage").forEach { key ->
            val changed = JsonObject(battle.toMutableMap().apply { put(key, JsonPrimitive(-1)) })
            expectInvalid(JsonObject(root.toMutableMap().apply { put("battleSession", changed) }).toString())
        }
        val excessiveDamage = JsonObject(battle.toMutableMap().apply { put("equipmentDamage", JsonPrimitive(101)) })
        expectInvalid(JsonObject(root.toMutableMap().apply { put("battleSession", excessiveDamage) }).toString())
    }

    @Test
    fun rescuedCommanderRequiresWoundAndStableCommanderId() {
        val root = Json.parseToJsonElement(SaveCodec.encode(SaveCodec.decode(v2()))).jsonObject
        val battle = root.getValue("battleSession").jsonObject
        val troops = battle.getValue("contingents").jsonArray
        listOf(false, true).forEach { noCommander ->
            val changed = JsonObject(troops.first().jsonObject.toMutableMap().apply {
                put("commanderRescued", JsonPrimitive(true))
                if (noCommander) put("commanderId", JsonNull) else put("commanderWounded", JsonPrimitive(false))
            })
            val damagedBattle = JsonObject(battle.toMutableMap().apply { put("contingents", JsonArray(listOf(changed) + troops.drop(1))) })
            expectInvalid(JsonObject(root.toMutableMap().apply { put("battleSession", damagedBattle) }).toString())
        }
    }

    private fun expectInvalid(raw: String) {
        try { SaveCodec.decode(raw); fail("Invalid save accepted") } catch (_: SaveFormatException) { }
    }

    private fun assertExistingFields(before: JsonElement, after: JsonElement, path: String) {
        when (before) {
            is JsonObject -> before.forEach { (key, value) -> assertExistingFields(value, after.jsonObject.getValue(key), "$path.$key") }
            is JsonArray -> { assertEquals(path, before.size, after.jsonArray.size); before.forEachIndexed { i, value -> assertExistingFields(value, after.jsonArray[i], "$path[$i]") } }
            else -> assertEquals(path, before, after)
        }
    }

    private class MemoryStorage : SaveStorage {
        val values = mutableMapOf<String, String>()
        var failCommit = false
        override fun contains(key: String) = values.containsKey(key)
        override fun read(key: String) = values[key]
        override fun commit(values: Map<String, String?>): Boolean {
            if (failCommit) return false
            values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
            return true
        }
    }

    companion object {
        private const val PRIMARY = "game_state_v1"
        private const val BACKUP = "game_state_backup_v2"
        private const val MIGRATION = "migration_backup_v2"
    }
}
