package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Android-independent, versioned save format. Invalid data is never silently a new game. */
object SaveCodec {
    const val CURRENT_VERSION = 2
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String {
        require(state.version == CURRENT_VERSION) {
            "Unbekannte Spielstandversion ${state.version}."
        }
        validate(state)
        return json.encodeToString(state)
    }

    fun decode(raw: String): GameState {
        try {
            val root =
                json.parseToJsonElement(raw) as? JsonObject
                    ?: throw SaveFormatException("Der Spielstand ist kein JSON-Objekt.")
            val version =
                root["version"]?.let {
                    (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull
                        ?: throw SaveFormatException("Die Spielstandversion ist ungültig.")
                } ?: 1
            if (version > CURRENT_VERSION)
                throw SaveFormatException(
                    "Spielstandversion $version benötigt eine neuere App. Der Spielstand bleibt erhalten.",
                    true,
                )
            if (version < 1) throw SaveFormatException("Die Spielstandversion ist ungültig.")
            requireCoreStructure(root, version)
            val state =
                if (version == 1) migrate(root) else json.decodeFromJsonElement<GameState>(root)
            validate(state)
            return state
        } catch (error: SaveFormatException) {
            throw error
        } catch (error: Exception) {
            throw SaveFormatException(
                "Der Spielstand ist beschädigt: ${error.message ?: "ungültige Daten"}",
                cause = error,
            )
        }
    }

    private fun requireCoreStructure(root: JsonObject, version: Int) {
        require(root["day"] is JsonPrimitive) { "Tag fehlt." }
        require((root["player"] as? JsonObject)?.get("name") is JsonPrimitive) { "Spieler fehlt." }
        // These fields used to have much smaller defaults. Never fill damaged legacy saves with
        // new-game grants.
        val resources = root["resources"] as? JsonObject ?: error("Ressourcen fehlen.")
        require(
            listOf("gold", "food", "wood", "stone", "iron").all { resources[it] is JsonPrimitive }
        ) {
            "Ressourcen sind unvollständig."
        }
        val population = root["population"] as? JsonObject ?: error("Bevölkerung fehlt.")
        require(
            listOf(
                    "human",
                    "woodElf",
                    "goldElf",
                    "wall",
                    "humanRecruits",
                    "woodElfRecruits",
                    "goldElfRecruits",
                    "wallRecruits",
                )
                .all { population[it] is JsonPrimitive }
        ) {
            "Bevölkerung ist unvollständig."
        }
        val realm = root["realm"] as? JsonObject ?: error("Gebiet fehlt.")
        require(realm["territory"] is JsonPrimitive && realm["buildings"] is JsonObject) {
            "Gebiet ist unvollständig."
        }
        require(root[if (version == 1) "regiments" else "armyPools"] is JsonArray) {
            "Armeebestände fehlen."
        }
    }

    @Serializable
    private data class LegacyRegiment(
        val type: UnitType,
        val soldiers: Int,
        val experience: Int = 0,
        val morale: Int = 70,
        val commanderId: Long? = null,
    )

    private fun migrate(root: JsonObject): GameState {
        val regiments = json.decodeFromJsonElement<List<LegacyRegiment>>(root.getValue("regiments"))
        require(
            regiments.all { it.soldiers >= 0 && it.experience in 0..100 && it.morale in 0..100 }
        ) {
            "Ungültige alte Armeebestände."
        }
        val pools =
            regiments
                .groupBy { it.type }
                .mapNotNull { (type, units) ->
                    val count = units.sumOf { it.soldiers.toLong() }
                    if (count == 0L) null
                    else
                        ArmyUnitPool(
                            type,
                            checkedCount(count),
                            (units.sumOf { it.experience.toLong() * it.soldiers } / count).toInt(),
                            (units.sumOf { it.morale.toLong() * it.soldiers } / count).toInt(),
                            80,
                        )
                }
        val oldRealm = root.getValue("realm").jsonObject
        val territory = oldRealm.getValue("territory").jsonPrimitive.int
        val buildings =
            json.decodeFromJsonElement<Map<BuildingType, Int>>(oldRealm.getValue("buildings"))
        val palace = buildings[BuildingType.PALACE] ?: 0
        val tier =
            when {
                palace >= 4 || territory >= 7 -> SettlementTier.CAPITAL
                palace >= 2 || territory >= 4 -> SettlementTier.FORTRESS
                territory >= 2 -> SettlementTier.WALL_CITY
                territory >= 1 -> SettlementTier.CASTLE
                else -> SettlementTier.BORDER_KEEP
            }
        val oldName = oldRealm["settlementName"]?.jsonPrimitive?.contentOrNull
        val genericNames =
            setOf(
                "Grenzlager",
                "Grenzfeste",
                "Burgsiedlung",
                "Mauerstadt",
                "Große Grenzfestung",
                "Großfestung",
                "Kronenfeste",
                "Königliche Hauptstadt",
            )
        val customName =
            oldRealm["customSettlementName"]
                ?: oldName?.takeUnless { it.isBlank() || it in genericNames }?.let(::JsonPrimitive)
                ?: JsonNull
        val upgraded =
            root.toMutableMap().apply {
                remove("regiments")
                put("version", JsonPrimitive(CURRENT_VERSION))
                put("armyPools", json.encodeToJsonElement(pools))
                put(
                    "realm",
                    JsonObject(
                        oldRealm.toMutableMap().apply {
                            remove("settlementName")
                            put("customSettlementName", customName)
                            put("settlementTier", json.encodeToJsonElement(tier))
                        }
                    ),
                )
            }
        var state = json.decodeFromJsonElement<GameState>(JsonObject(upgraded))
        // In v1 population represented civilians; v2 population includes soldiers and trainees.
        fun military(culture: Culture): Long =
            pools.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } +
                state.trainingQueue
                    .filter { it.type.culture == culture }
                    .sumOf { it.amount.toLong() }
        val p = state.population
        state =
            state.copy(
                population =
                    p.copy(
                        human = checkedCount(p.human.toLong() + military(Culture.HUMAN)),
                        woodElf = checkedCount(p.woodElf.toLong() + military(Culture.WOOD_ELF)),
                        goldElf = checkedCount(p.goldElf.toLong() + military(Culture.GOLD_ELF)),
                        wall = checkedCount(p.wall.toLong() + military(Culture.WALL)),
                    )
            )
        if (state.commanderAssignments.isEmpty()) {
            state =
                state.copy(
                    commanderAssignments =
                        regiments
                            .filter { it.commanderId != null }
                            .groupBy { it.commanderId!! }
                            .map { (id, units) ->
                                CommanderAssignment(
                                    id,
                                    units
                                        .groupBy { it.type }
                                        .map { (type, regs) ->
                                            UnitAllocation(
                                                type,
                                                checkedCount(regs.sumOf { it.soldiers.toLong() }),
                                            )
                                        },
                                )
                            }
                )
        }
        return clampLegacyAssignments(state)
    }

    private fun clampLegacyAssignments(state: GameState): GameState {
        val remaining = UnitType.entries.associateWith { state.homeSoldiers(it) }.toMutableMap()
        val assignments =
            state.commanderAssignments
                .groupBy { it.commanderId }
                .mapNotNull { (id, grouped) ->
                    if (state.commanders.none { it.id == id }) return@mapNotNull null
                    if (state.commanderAway(id)) return@mapNotNull null
                    val units =
                        grouped
                            .flatMap { it.units }
                            .groupBy { it.type }
                            .mapNotNull { (type, allocations) ->
                                require(allocations.all { it.amount >= 0 }) {
                                    "Negative Kommandantenzuweisung."
                                }
                                val amount =
                                    allocations
                                        .sumOf { it.amount.toLong() }
                                        .coerceAtMost(remaining.getValue(type).toLong())
                                        .toInt()
                                remaining[type] = remaining.getValue(type) - amount
                                if (amount == 0) null else UnitAllocation(type, amount)
                            }
                    CommanderAssignment(id, units)
                }
        return state.copy(commanderAssignments = assignments)
    }

    private fun checkedCount(value: Long): Int {
        require(value in 0..Int.MAX_VALUE.toLong()) { "Ungültige Anzahl." }
        return value.toInt()
    }

    private fun validate(state: GameState) {
        require(state.day >= 1 && state.player.name.isNotBlank()) { "Ungültiger Spieler oder Tag." }
        require(
            state.relationship.actionDay in 0..state.day && state.relationship.spentActions in 0..2
        ) {
            "Ungültiges Beziehungsaktionsbudget."
        }
        require(
            listOf(
                    state.resources.gold,
                    state.resources.food,
                    state.resources.wood,
                    state.resources.stone,
                    state.resources.iron,
                )
                .all { it >= 0 }
        ) {
            "Negative Ressourcen."
        }
        val p = state.population
        val cultures =
            mapOf(
                Culture.HUMAN to p.human,
                Culture.WOOD_ELF to p.woodElf,
                Culture.GOLD_ELF to p.goldElf,
                Culture.WALL to p.wall,
            )
        checkedCount(cultures.values.sumOf { it.toLong() })
        require(cultures.values.all { it >= 0 } && Culture.entries.all { p.recruits(it) >= 0 }) {
            "Negative Bevölkerung."
        }
        require(
            state.realm.territory >= 0 &&
                state.realm.buildings.values.all { it >= 0 } &&
                state.realm.wallIntegrity in 0..100
        ) {
            "Ungültiges Gebiet."
        }
        require(state.armyPools.map { it.type }.distinct().size == state.armyPools.size) {
            "Doppelte Armeepools."
        }
        require(
            state.armyPools.all {
                it.soldiers >= 0 &&
                    it.experience in 0..100 &&
                    it.morale in 0..100 &&
                    it.equipment in 0..100
            }
        ) {
            "Ungültiger Armeepool."
        }
        checkedCount(state.armyPools.sumOf { it.soldiers.toLong() })
        checkedCount(state.trainingQueue.sumOf { it.amount.toLong() })
        require(
            state.trainingQueue.map { it.id }.distinct().size == state.trainingQueue.size &&
                state.trainingQueue.all { it.amount > 0 && it.daysRemaining >= 0 }
        ) {
            "Ungültige Ausbildung."
        }
        require(state.commanders.map { it.id }.distinct().size == state.commanders.size) {
            "Doppelte Kommandanten."
        }
        require(
            state.commanderAssignments.map { it.commanderId }.distinct().size ==
                state.commanderAssignments.size
        ) {
            "Doppelte Zuweisung."
        }
        require(state.activeMissions.map { it.id }.distinct().size == state.activeMissions.size) {
            "Doppelte Missionen."
        }
        state.activeMissions.forEach { mission ->
            require(
                mission.startDay in 1..state.day &&
                    mission.duration > 0 &&
                    mission.supplyCost >= 0 &&
                    mission.losses >= 0
            ) {
                "Ungültige Mission."
            }
            validateAllocations(mission.units)
            require(mission.losses <= mission.total) {
                "Missionsverluste überschreiten die Truppenstärke."
            }
            if (mission.quality.isNotEmpty()) {
                require(
                    mission.quality.map { it.type }.distinct().size == mission.quality.size &&
                        mission.quality.map { it.type }.toSet() ==
                            mission.units.map { it.type }.toSet()
                ) {
                    "Missionsqualität stimmt nicht mit den Truppen überein."
                }
                require(
                    mission.quality.all { pool ->
                        pool.soldiers == mission.units.first { it.type == pool.type }.amount &&
                            pool.experience in 0..100 &&
                            pool.morale in 0..100 &&
                            pool.equipment in 0..100
                    }
                ) {
                    "Ungültige Missionsqualität."
                }
            }

            if (mission.status.isAway) {
                require(
                    mission.units.isNotEmpty() && mission.remainingDays in 1..mission.duration
                ) {
                    "Ungültige laufende Mission."
                }
                require(mission.losses == 0 && mission.outcome == null) {
                    "Laufende Mission besitzt bereits ein Ergebnis."
                }
                if (mission.commanderId != null)
                    require(state.commanders.any { it.id == mission.commanderId }) {
                        "Missionskommandant fehlt."
                    }
            } else
                require(mission.remainingDays == 0) { "Abgeschlossene Mission ist noch unterwegs." }
        }
        val awayCommanders =
            state.activeMissions.filter { it.status.isAway }.mapNotNull { it.commanderId }
        require(awayCommanders.distinct().size == awayCommanders.size) {
            "Kommandant auf mehreren Missionen."
        }
        state.commanderAssignments.forEach { assignment ->
            val commander =
                state.commanders.firstOrNull { it.id == assignment.commanderId }
                    ?: error("Kommandant fehlt.")
            validateAllocations(assignment.units)
            require(!state.commanderAway(commander.id)) { "Ungültige Kommandantenzuweisung." }
        }
        UnitType.entries.forEach { type ->
            val away =
                state.activeMissions
                    .filter { it.status.isAway }
                    .sumOf { m -> m.units.filter { it.type == type }.sumOf { it.amount.toLong() } }
            val assigned =
                state.commanderAssignments.sumOf { a ->
                    a.units.filter { it.type == type }.sumOf { it.amount.toLong() }
                }
            require(away + assigned <= state.soldiers(type).toLong()) {
                "Soldaten sind doppelt oder überzählig zugewiesen."
            }
        }
        state.battleSession?.let { battle ->
            require(
                battle.minute in 0..95 &&
                    battle.step >= 0 &&
                    battle.ownStart >= 0 &&
                    battle.enemyStart >= 0 &&
                    battle.wallIntegrity in 0..100 &&
                    battle.lootGold >= 0
            ) {
                "Ungültige Schlacht."
            }
            require(
                battle.contingents.all {
                    it.soldiers in 0..it.startSoldiers &&
                        it.experience in 0..100 &&
                        it.morale in 0..100 &&
                        it.equipment in 0..100
                }
            ) {
                "Ungültige Schlachttruppen."
            }
            require(
                battle.contingents.all { contingent ->
                    contingent.commanderId == null ||
                        state.commanders.any { it.id == contingent.commanderId }
                }
            ) {
                "Schlachtkommandant fehlt."
            }
            require(
                battle.fronts.map { it.section }.distinct().size == battle.fronts.size &&
                    battle.fronts.all {
                        it.enemySoldiers in 0..it.enemyStart &&
                            it.morale in 0..100 &&
                            it.position in 0..100
                    }
            ) {
                "Ungültige Schlachtfront."
            }
            require(
                battle.contingents.sumOf { it.startSoldiers.toLong() } ==
                    battle.ownStart.toLong() &&
                    battle.fronts.sumOf { it.enemyStart.toLong() } == battle.enemyStart.toLong()
            ) {
                "Schlachtbestände stimmen nicht überein."
            }
            if (battle.isActive) {
                require(
                    battle.enemyStart > 0 &&
                        battle.fronts.map { it.section }.toSet() ==
                            setOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT)
                ) {
                    "Aktive Schlacht benötigt drei gültige Fronten."
                }
                if (battle.status == BattleStatus.PURSUIT)
                    require(
                        battle.pendingEvent?.options?.toSet() ==
                            setOf(BattleDecision.PURSUE, BattleDecision.HOLD_FORMATION)
                    ) {
                        "Verfolgungsentscheidung fehlt."
                    }
                battle.contingents
                    .groupBy { it.commanderId to it.type }
                    .forEach { (command, troops) ->
                        val (commanderId, type) = command
                        val available =
                            if (commanderId == null) state.directCommand(type)
                            else {
                                require(!state.commanderAway(commanderId)) {
                                    "Schlachtkommandant ist unterwegs."
                                }
                                state.assignedTo(commanderId, type)
                            }
                        require(troops.sumOf { it.soldiers.toLong() } <= available.toLong()) {
                            "Schlachttruppen überschreiten ihr Kommando."
                        }
                    }
                UnitType.entries.forEach { type ->
                    require(
                        battle.contingents
                            .filter { it.type == type }
                            .sumOf { it.soldiers.toLong() } <= state.homeSoldiers(type).toLong()
                    ) {
                        "Schlachttruppen überschreiten die Heimatbestände."
                    }
                }
            }
        }
        cultures.forEach { (culture, people) ->
            val military =
                state.armyPools
                    .filter { it.type.culture == culture }
                    .sumOf { it.soldiers.toLong() } +
                    state.trainingQueue
                        .filter { it.type.culture == culture }
                        .sumOf { it.amount.toLong() }
            require(military + p.recruits(culture) <= people.toLong()) {
                "Armee und Rekruten überschreiten die Bevölkerung."
            }
        }
    }

    private fun validateAllocations(units: List<UnitAllocation>) {
        require(
            units.all { it.amount > 0 } && units.map { it.type }.distinct().size == units.size
        ) {
            "Ungültige Einheitenzuweisung."
        }
        checkedCount(units.sumOf { it.amount.toLong() })
    }
}

class SaveFormatException(
    message: String,
    val futureVersion: Boolean = false,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
