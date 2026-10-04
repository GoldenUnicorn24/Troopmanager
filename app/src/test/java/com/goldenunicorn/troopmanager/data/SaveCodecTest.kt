package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.model.*
import com.goldenunicorn.troopmanager.engine.BattleStateEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SaveCodecTest {
    private val commander = Commander(10, "Aron", Culture.HUMAN, "human")

    private fun state() =
        GameState(
            player = CharacterProfile("Leon"),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 300)),
            commanders = listOf(commander),
            commanderAssignments =
                listOf(CommanderAssignment(10, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)))),
        )

    // A realistic v0.3 fixture uses the original small resources and civilian population.
    private fun legacy(name: String = "Eichenwacht", assignments: String = "[]"): String =
        """
        {"version":1,"day":17,"player":{"name":"Leon","level":3},
         "resources":{"gold":120,"food":710,"wood":90,"stone":42,"iron":13},
         "population":{"human":180,"woodElf":55,"goldElf":15,"wall":0,
            "humanRecruits":42,"woodElfRecruits":12,"goldElfRecruits":4,"wallRecruits":0},
         "realm":{"territory":2,"settlementName":"$name","buildings":{"FARM":1,"BARRACKS":2,"PALACE":1},"threat":18,"wallIntegrity":95},
         "regiments":[
            {"id":1,"name":"A","type":"HUMAN_SWORD","soldiers":100,"maxSoldiers":100,"experience":20,"morale":80,"commanderId":10},
            {"id":2,"name":"B","type":"HUMAN_SWORD","soldiers":300,"maxSoldiers":400,"experience":60,"morale":40,"commanderId":10},
            {"id":3,"name":"C","type":"WOOD_RANGER","soldiers":20,"maxSoldiers":30,"experience":10,"morale":90}],
         "trainingQueue":[{"id":5,"type":"HUMAN_ARCHER","amount":21,"daysRemaining":3}],
         "commanders":[{"id":10,"name":"Aron","culture":"HUMAN","portraitKey":"human"}],
         "commanderAssignments":$assignments,"renown":137,"victories":2,"tutorialSeen":true,
         "chronicle":[{"day":16,"title":"Verteidigung","text":"Das Land ist sicher."}]}
    """
            .trimIndent()

    @Test
    fun migrationAggregatesWeightsAndPreservesLegacyProgress() {
        val loaded = SaveCodec.decode(legacy())
        assertEquals(SaveCodec.CURRENT_VERSION, loaded.version)
        assertEquals(2, loaded.armyPools.size)
        val pool = loaded.armyPools.first { it.type == UnitType.HUMAN_SWORD }
        assertEquals(400, pool.soldiers)
        assertEquals(50, pool.experience)
        assertEquals(50, pool.morale)
        assertEquals(80, pool.equipment)
        assertEquals(Resources(120, 710, 90, 42, 13), loaded.resources)
        assertEquals(601, loaded.population.human) // 180 civilians + 400 army + 21 training
        assertEquals(75, loaded.population.woodElf)
        assertEquals(250, loaded.civilianPopulation)
        assertEquals(400, loaded.assignedTo(10, UnitType.HUMAN_SWORD))
        assertEquals("Eichenwacht", loaded.realm.customSettlementName)
        assertEquals(SettlementTier.WALL_CITY, loaded.realm.settlementTier)
        assertEquals(2, loaded.realm.territory)
        assertEquals(137, loaded.renown)
        assertEquals("Das Land ist sicher.", loaded.chronicle.single().text)
        val raw = SaveCodec.encode(loaded)
        assertFalse(Json.parseToJsonElement(raw).jsonObject.containsKey("regiments"))
        assertEquals(loaded, SaveCodec.decode(raw))
    }

    @Test
    fun genericSettlementNamesBecomeTierWithoutOverwritingCustomName() {
        assertNull(SaveCodec.decode(legacy("Mauerstadt")).realm.customSettlementName)
        assertEquals(
            "Eichenwacht",
            SaveCodec.decode(SaveCodec.encode(SaveCodec.decode(legacy())))
                .realm
                .customSettlementName,
        )
    }

    @Test
    fun existingAssignmentsTakePrecedenceAndAreClampedOnce() {
        val assignments = """[{"commanderId":10,"units":[{"type":"HUMAN_SWORD","amount":900}]}]"""
        val loaded = SaveCodec.decode(legacy(assignments = assignments))
        assertEquals(400, loaded.assigned(UnitType.HUMAN_SWORD))
        assertEquals(1, loaded.commanderAssignments.size)
    }

    @Test
    fun futureCorruptAndOverallocatedSavesAreRejected() {
        assertTrue(expectInvalid("""{"version":99}""").futureVersion)
        expectInvalid("{")
        expectInvalid("""{"version":2,"player":{"name":"Leon"}}""")
        val root = Json.parseToJsonElement(SaveCodec.encode(state())).jsonObject
        expectInvalid(
            JsonObject(
                    root +
                        ("armyPools" to
                            Json.parseToJsonElement("""[{"type":"HUMAN_SWORD","soldiers":-1}]"""))
                )
                .toString()
        )
        val assignment =
            Json.parseToJsonElement(
                """[{"commanderId":10,"units":[{"type":"HUMAN_SWORD","amount":301}]}]"""
            )
        expectInvalid(JsonObject(root + ("commanderAssignments" to assignment)).toString())
        expectInvalid(JsonObject(root - "resources").toString())
    }

    @Test
    fun missionAndMidBattleSurviveRestartWithDecisionsIntact() {
        val mission =
            ActiveMission(
                7,
                MissionType.SCOUT,
                null,
                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 40)),
                startDay = 1,
                remainingDays = 1,
                supplyCost = 80,
                duration = 2,
            )
        val battle =
            BattleSession(
                EnemyType.ORC,
                Tactic.HOLD,
                seed = 42,
                ownStart = 260,
                enemyStart = 500,
                contingents =
                    listOf(
                        BattleContingent(
                            UnitType.HUMAN_SWORD,
                            10,
                            BattleSection.CENTER,
                            100,
                            100,
                            15,
                            75,
                            80,
                        ),
                        BattleContingent(
                            UnitType.HUMAN_SWORD,
                            null,
                            BattleSection.LEFT,
                            140,
                            160,
                            15,
                            75,
                            80,
                        ),
                    ),
                fronts =
                    listOf(
                        BattleFront(BattleSection.LEFT, 140, 150),
                        BattleFront(BattleSection.CENTER, 200, 200),
                        BattleFront(BattleSection.RIGHT, 140, 150),
                    ),
                minute = 5,
                step = 1,
                pendingEvent =
                    BattleEvent(
                        "Angriff",
                        "Die Linie hält.",
                        BattleSection.CENTER,
                        listOf(BattleDecision.HOLD),
                    ),
                log = listOf(BattleLogEntry(5, "Erster Kontakt", 20, 20)),
            )
        val saved =
            state()
                .copy(
                    day = 2,
                    armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 280)),
                    activeMissions = listOf(mission),
                    battleSession = battle,
                )
        val storage = MemoryStorage()
        SaveRepository(storage).save(saved)
        val restarted = SaveRepository(storage).load()
        assertEquals(BattleStateEngine.migrate(saved), restarted)
        assertEquals(240, restarted!!.homeArmySize)
        assertEquals(BattleDecision.HOLD, restarted.battleSession!!.pendingEvent!!.options.single())
    }

    @Test
    fun corruptMissionsAndRelationshipBudgetsAreRejected() {
        val mission =
            ActiveMission(
                7,
                MissionType.SCOUT,
                null,
                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 40)),
                startDay = 1,
                remainingDays = 2,
                supplyCost = 160,
                duration = 2,
            )
        val invalidMissions =
            listOf(
                mission.copy(units = emptyList()),
                mission.copy(remainingDays = 0),
                mission.copy(remainingDays = 3),
                mission.copy(losses = 41),
                mission.copy(startDay = 2),
                mission.copy(status = MissionStatus.COMPLETE, outcome = MissionOutcome.SUCCESS),
            )
        invalidMissions.forEach { expectInvalid(raw(state().copy(activeMissions = listOf(it)))) }
        listOf(
                RelationshipState(actionDay = 1, spentActions = -1),
                RelationshipState(actionDay = 1, spentActions = 3),
                RelationshipState(actionDay = 2),
                RelationshipState(actionDay = -1),
            )
            .forEach { expectInvalid(raw(state().copy(relationship = it))) }
    }

    @Test
    fun missionQualitySnapshotsPersistAndRejectChangedTroopCounts() {
        val snapshot =
            ArmyUnitPool(UnitType.HUMAN_SWORD, 40, experience = 27, morale = 91, equipment = 73)
        val mission =
            ActiveMission(
                7,
                MissionType.SCOUT,
                null,
                listOf(UnitAllocation(UnitType.HUMAN_SWORD, 40)),
                startDay = 1,
                remainingDays = 2,
                supplyCost = 160,
                duration = 2,
                quality = listOf(snapshot),
            )
        val saved = state().copy(activeMissions = listOf(mission))
        assertEquals(saved, SaveCodec.decode(SaveCodec.encode(saved)))
        val completed =
            saved.copy(
                day = 3,
                activeMissions =
                    listOf(
                        mission.copy(
                            status = MissionStatus.COMPLETE,
                            remainingDays = 0,
                            outcome = MissionOutcome.SUCCESS,
                            losses = 4,
                        )
                    ),
            )
        assertEquals(completed, SaveCodec.decode(SaveCodec.encode(completed)))
        listOf(
                listOf(snapshot.copy(soldiers = 39)),
                listOf(snapshot, snapshot),
                listOf(snapshot.copy(type = UnitType.HUMAN_ARCHER)),
                listOf(snapshot.copy(morale = -1)),
                listOf(snapshot.copy(experience = 101)),
                listOf(snapshot.copy(equipment = 101)),
            )
            .forEach {
                expectInvalid(raw(saved.copy(activeMissions = listOf(mission.copy(quality = it)))))
            }
    }

    @Test
    fun corruptBattleSnapshotsRecoverHealthyBackup() {
        val healthy =
            state()
                .copy(
                    battleSession =
                        BattleSession(
                            EnemyType.ORC,
                            Tactic.HOLD,
                            42,
                            100,
                            300,
                            listOf(
                                BattleContingent(
                                    UnitType.HUMAN_SWORD,
                                    10,
                                    BattleSection.CENTER,
                                    100,
                                    100,
                                    15,
                                    80,
                                    80,
                                )
                            ),
                            listOf(
                                BattleFront(BattleSection.LEFT, 100, 100),
                                BattleFront(BattleSection.CENTER, 100, 100),
                                BattleFront(BattleSection.RIGHT, 100, 100),
                            ),
                        )
                )
        val battle = healthy.battleSession!!
        val invalid =
            listOf(
                battle.copy(fronts = listOf(BattleFront(BattleSection.CENTER, 300, 300))),
                battle.copy(status = BattleStatus.PURSUIT),
                battle.copy(contingents = battle.contingents.map { it.copy(commanderId = 999) }),
                battle.copy(
                    ownStart = 101,
                    contingents =
                        battle.contingents.map { it.copy(soldiers = 101, startSoldiers = 101) },
                ),
                battle.copy(
                    ownStart = 201,
                    contingents =
                        battle.contingents.map {
                            it.copy(commanderId = null, soldiers = 201, startSoldiers = 201)
                        },
                ),
                battle.copy(
                    enemyStart = 0,
                    fronts = battle.fronts.map { it.copy(enemySoldiers = 0, enemyStart = 0) },
                ),
                battle.copy(
                    fronts =
                        battle.fronts.map {
                            if (it.section == BattleSection.LEFT)
                                it.copy(section = BattleSection.RESERVE)
                            else it
                        }
                ),
            )
        invalid.forEach { broken ->
            val corrupted = raw(healthy.copy(battleSession = broken))
            expectInvalid(corrupted)
            val storage = MemoryStorage()
            val repository = SaveRepository(storage)
            repository.save(healthy)
            storage.values[PRIMARY] = corrupted
            assertEquals(BattleStateEngine.migrate(healthy), SaveRepository(storage).load())
            assertEquals(BattleStateEngine.migrate(healthy), SaveCodec.decode(storage.values.getValue(PRIMARY)))
        }
    }

    @Test
    fun splitCommanderContingentsAndPendingPursuitRoundTrip() {
        val battle =
            BattleSession(
                EnemyType.ORC,
                Tactic.HOLD,
                42,
                100,
                300,
                listOf(
                    BattleContingent(
                        UnitType.HUMAN_SWORD,
                        10,
                        BattleSection.CENTER,
                        60,
                        60,
                        15,
                        80,
                        80,
                    ),
                    BattleContingent(
                        UnitType.HUMAN_SWORD,
                        10,
                        BattleSection.RESERVE,
                        40,
                        40,
                        15,
                        80,
                        80,
                    ),
                ),
                listOf(
                    BattleFront(BattleSection.LEFT, 10, 100),
                    BattleFront(BattleSection.CENTER, 10, 100),
                    BattleFront(BattleSection.RIGHT, 10, 100),
                ),
                status = BattleStatus.PURSUIT,
                phase = BattlePhase.PURSUIT,
                pendingEvent =
                    BattleEvent(
                        "Verfolgung",
                        "Der Gegner flieht.",
                        BattleSection.CENTER,
                        listOf(BattleDecision.PURSUE, BattleDecision.HOLD_FORMATION),
                    ),
            )
        val saved = state().copy(battleSession = battle)
        assertEquals(BattleStateEngine.migrate(saved), SaveCodec.decode(SaveCodec.encode(saved)))
    }

    private fun raw(state: GameState): String {
        // Give intentionally invalid domain fixtures a valid envelope so semantic validation runs.
        val payload = Json { encodeDefaults = true }.encodeToJsonElement(state).jsonObject
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return JsonObject(payload + ("_checksum" to JsonPrimitive(digest))).toString()
    }

    @Test
    fun backupRecoversCorruptPrimaryAndSurvivesFurtherSave() {
        val storage = MemoryStorage()
        val repo = SaveRepository(storage)
        val first = state()
        repo.save(first)
        val second = first.copy(day = 2)
        repo.save(second)
        storage.values[PRIMARY] = "{broken"
        val restarted = SaveRepository(storage)
        assertEquals(first, restarted.load())
        assertTrue(restarted.lastError!!.contains("Backup"))
        assertEquals(first, SaveCodec.decode(storage.values.getValue(PRIMARY)))
        storage.values[PRIMARY] = "{broken again"
        repo.save(second)
        assertEquals(first, SaveCodec.decode(storage.values.getValue(BACKUP)))
    }

    @Test
    fun futureSaveIsNeverOverwrittenAndDeleteClearsBothCopies() {
        val storage = MemoryStorage()
        val repo = SaveRepository(storage)
        repo.save(state())
        val backup = storage.values.getValue(BACKUP)
        storage.values[PRIMARY] = """{"version":12}"""
        assertNull(repo.load())
        assertNotNull(repo.lastError)
        repo.save(state().copy(day = 2))
        assertEquals("""{"version":12}""", storage.values[PRIMARY])
        assertEquals(backup, storage.values[BACKUP])
        assertNotNull(repo.lastError)
        repo.delete()
        assertFalse(repo.hasSave())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun futureBackupIsProtectedDuringMigrationAndSaving() {
        val storage = MemoryStorage()
        storage.values[PRIMARY] = legacy()
        storage.values[BACKUP] = """{"version":12}"""
        val repo = SaveRepository(storage)
        assertNotNull(repo.load())
        assertEquals(legacy(), storage.values[PRIMARY])
        assertNotNull(repo.lastError)
        repo.save(state())
        assertEquals(legacy(), storage.values[PRIMARY])
        assertEquals("""{"version":12}""", storage.values[BACKUP])
        assertNotNull(repo.lastError)
    }

    @Test
    fun failedCommitIsReportedAndOldSaveRemainsLoadable() {
        val storage = MemoryStorage()
        val repo = SaveRepository(storage)
        repo.save(state())
        storage.failCommit = true
        repo.save(state().copy(day = 2))
        assertNotNull(repo.lastError)
        assertEquals(state(), SaveRepository(storage).load())
    }

    @Test
    fun legacyLoadPersistsCurrentSchemaAndKeepsOriginalBackup() {
        val storage = MemoryStorage()
        storage.values[PRIMARY] = legacy()
        assertNotNull(SaveRepository(storage).load())
        assertEquals(
            SaveCodec.CURRENT_VERSION,
            Json.parseToJsonElement(storage.values.getValue(PRIMARY))
                .jsonObject
                .getValue("version")
                .jsonPrimitive
                .int,
        )
        assertEquals(legacy(), storage.values[BACKUP])
        assertEquals(SaveCodec.decode(legacy()), SaveRepository(storage).load())
    }

    private fun expectInvalid(raw: String): SaveFormatException {
        try {
            SaveCodec.decode(raw)
            fail("Invalid save was accepted")
        } catch (error: SaveFormatException) {
            return error
        }
        throw AssertionError("Invalid save was accepted")
    }

    private class MemoryStorage : SaveStorage {
        val values = mutableMapOf<String, String>()
        var failCommit = false

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun read(key: String): String? = values[key]

        override fun commit(values: Map<String, String?>): Boolean {
            if (failCommit) return false
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key) else this.values[key] = value
            }
            return true
        }
    }

    companion object {
        private const val PRIMARY = "game_state_v1"
        private const val BACKUP = "game_state_backup_v2"
    }
}
