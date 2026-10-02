package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Versioned campaign seed content. Runtime ownership, rulers and migrated places remain save data. */
@Serializable
data class WorldContentCatalog(
    val version: Int,
    val places: List<WorldPlace>,
    val roads: List<WorldRoad>,
    val factions: List<WorldFaction>,
    val enemyCommanders: List<EnemyCommander>,
    val armies: List<WorldArmy>,
) {
    companion object {
        private val stableId = Regex("[a-z][a-z0-9_]{0,127}")
        private val strictJson = Json {
            ignoreUnknownKeys = false
            isLenient = false
            coerceInputValues = false
            allowSpecialFloatingPointValues = false
        }
        private val bundled: WorldContentCatalog by lazy {
            val stream = requireNotNull(WorldContentCatalog::class.java.getResourceAsStream("/content/world-v1.json")) {
                "Campaign content /content/world-v1.json is missing."
            }
            stream.bufferedReader(Charsets.UTF_8).use { decode(it.readText()) }
        }

        fun load(): WorldContentCatalog = bundled

        fun decode(raw: String): WorldContentCatalog =
            strictJson.decodeFromString<WorldContentCatalog>(raw).also { it.validate() }

        private fun uniqueIds(kind: String, ids: List<String>) {
            require(ids.all { stableId.matches(it) }) { "$kind contains an invalid stable ID." }
            require(ids.distinct().size == ids.size) { "$kind contains duplicate IDs." }
        }
    }

    private fun validate() {
        require(version == 1) { "Unsupported campaign content version $version." }
        require(places.isNotEmpty() && factions.isNotEmpty()) { "Campaign places and factions must not be empty." }
        uniqueIds("Places", places.map { it.id })
        uniqueIds("Roads", roads.map { it.id })
        uniqueIds("Factions", factions.map { it.id })
        uniqueIds("Commanders", enemyCommanders.map { it.id })
        uniqueIds("Armies", armies.map { it.id })
        val placeIds = places.map { it.id }.toSet()
        val factionIds = factions.map { it.id }.toSet()
        val commanderIds = enemyCommanders.map { it.id }.toSet()
        require("keep" in placeIds && PLAYER_FACTION in factionIds && NEUTRAL_FACTION in factionIds) {
            "Campaign home and core faction IDs must remain stable."
        }
        require(factions.first { it.id == PLAYER_FACTION }.capitalId == "keep" &&
            places.first { it.id == "keep" }.ownerId == PLAYER_FACTION) {
            "The player campaign home must be keep."
        }
        places.forEach { place ->
            require(place.name.isNotBlank() && place.ownerId in factionIds) { "Invalid place ${place.id}." }
            require(place.x.isFinite() && place.y.isFinite() && place.x in 0f..1f && place.y in 0f..1f) {
                "Invalid map coordinates for ${place.id}."
            }
            require(place.population >= 0 && place.fortification in 0..100 && place.prosperity in 0..100) {
                "Invalid place resources for ${place.id}."
            }
        }
        roads.forEach { road ->
            require(road.from in placeIds && road.to in placeIds && road.from != road.to) {
                "Invalid road endpoints for ${road.id}."
            }
            require(road.distance > 0 && road.quality in 0..100) { "Invalid road dimensions for ${road.id}." }
        }
        require(roads.map { setOf(it.from, it.to) }.distinct().size == roads.size) { "Duplicate campaign road connections." }
        require(roads.sumOf { it.distance.toLong() } <= Int.MAX_VALUE) { "Campaign road distances overflow route calculations." }
        factions.forEach { faction ->
            require(faction.name.isNotBlank() && faction.ruler.isNotBlank() && faction.militaryStyle.isNotBlank() && faction.longTermGoal.isNotBlank()) {
                "Incomplete faction ${faction.id}."
            }
            require(faction.capitalId in placeIds) { "Unknown capital for ${faction.id}." }
            require(faction.gold >= 0 && faction.food >= 0 && faction.iron >= 0 && faction.population >= 0 && faction.buildings >= 0) {
                "Invalid faction resources for ${faction.id}."
            }
            require(faction.aggression in 0..100 && faction.rulerAge in 0..150 && faction.successionCount >= 0 && faction.lastDecisionDay >= 0) {
                "Invalid faction development for ${faction.id}."
            }
            require(faction.relations.all { (id, value) -> id in factionIds && value in -100..100 }) {
                "Invalid faction relations for ${faction.id}."
            }
            require(faction.wars.distinct().size == faction.wars.size && faction.wars.all { it in factionIds && it != faction.id }) {
                "Invalid faction wars for ${faction.id}."
            }
        }
        enemyCommanders.forEach { commander ->
            require(commander.factionId in factionIds && commander.factionId != PLAYER_FACTION &&
                commander.name.isNotBlank() && commander.portraitKey.isNotBlank() && commander.trait.isNotBlank() && commander.rank.isNotBlank()) {
                "Invalid campaign commander ${commander.id}."
            }
            require(commander.experience >= 0 && commander.victories >= 0 && commander.defeats >= 0 && commander.injuries >= 0 &&
                commander.rivalry in 0..100 && commander.rulerLoyalty in 0..100 &&
                (commander.revengeTarget == null || commander.revengeTarget in factionIds)) {
                "Invalid campaign commander development for ${commander.id}."
            }
        }
        armies.forEach { army ->
            require(army.factionId in factionIds && army.factionId != PLAYER_FACTION && army.regionId in placeIds && army.name.isNotBlank()) {
                "Invalid campaign army ${army.id}. Player armies reserve existing troops at runtime."
            }
            require(army.commanderId == null && army.missionId == null && army.status in listOf(WorldArmyStatus.HOLDING, WorldArmyStatus.MARCHING)) {
                "Campaign seed armies must not reference runtime player commands, missions or battles."
            }
            require(army.enemyCommanderId == null || army.enemyCommanderId in commanderIds &&
                enemyCommanders.first { it.id == army.enemyCommanderId }.factionId == army.factionId) {
                "Invalid army commander for ${army.id}."
            }
            require(army.units.isNotEmpty() && army.units.all { it.amount > 0 } &&
                army.units.map { it.type }.distinct().size == army.units.size &&
                army.units.sumOf { it.amount.toLong() } <= Int.MAX_VALUE) {
                "Invalid troop allocations for ${army.id}."
            }
            require(army.supplyFood >= 0 && army.morale in 0..100 && army.legProgress >= 0 && army.routeIndex >= 0 &&
                army.lastMovedDay >= 0 && army.lastLosses in 0..army.total &&
                (army.arrivalDay == null || army.arrivalDay >= 1) &&
                (army.destinationId == null || army.destinationId in placeIds)) {
                "Invalid campaign army resources for ${army.id}."
            }
            require(army.route.all { it in placeIds }) { "Unknown route place for ${army.id}." }
            if (army.route.isEmpty()) require(army.routeIndex == 0 && army.legProgress == 0 && army.status != WorldArmyStatus.MARCHING) {
                "Marching campaign armies need a route."
            } else {
                require(army.routeIndex in army.route.indices && army.route[army.routeIndex] == army.regionId &&
                    army.route.last() == army.destinationId &&
                    army.route.zipWithNext().all { (from, to) -> roads.any { it.connects(from) && it.other(from) == to } }) {
                    "Invalid campaign army route for ${army.id}."
                }
                if (army.status == WorldArmyStatus.MARCHING) require(army.routeIndex < army.route.lastIndex) {
                    "Marching campaign army ${army.id} has already reached its destination."
                }
            }
        }
        val commandedArmies = armies.mapNotNull { it.enemyCommanderId }
        require(commandedArmies.distinct().size == commandedArmies.size) { "A campaign commander leads multiple armies." }
        factions.forEach { faction ->
            val factionArmies = armies.filter { it.factionId == faction.id }
            require(factionArmies.sumOf { it.supplyFood.toLong() } <= faction.food.toLong() &&
                factionArmies.sumOf { it.total.toLong() } <= faction.population.toLong()) {
                "Campaign army resources exceed ${faction.id}'s economy."
            }
        }
    }
}
