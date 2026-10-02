package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class WorldContentCatalogTest {
    private fun encoded(catalog: WorldContentCatalog = WorldContentCatalog.load()) = Json.encodeToString(catalog)

    private fun reject(catalog: WorldContentCatalog) {
        assertThrows(IllegalArgumentException::class.java) { WorldContentCatalog.decode(encoded(catalog)) }
    }

    @Test
    fun bundledContentPreservesCampaignIdsAndOriginalStartingStrength() {
        val catalog = WorldContentCatalog.load()
        assertEquals(1, catalog.version)
        assertEquals(setOf(
            "keep", "village", "forest", "mine", "ruin", "orc", "monsters",
            "region_north_pass_01", "capital_ash_01", "capital_verdant_01", "capital_copper_01",
            "temple_moon_01", "village_salt_01", "ruin_crown_01", "port_dawn_01",
        ), catalog.places.map { it.id }.toSet())
        assertEquals(15, catalog.places.size)
        assertEquals(21, catalog.roads.size)
        assertEquals(setOf(PLAYER_FACTION, NEUTRAL_FACTION, "ash_covenant", "verdant_compact", "copper_league"), catalog.factions.map { it.id }.toSet())
        assertEquals(setOf("enemy_veyk_01", "enemy_nera_01", "enemy_oren_01"), catalog.enemyCommanders.map { it.id }.toSet())
        assertEquals(setOf("army_ash_01", "army_verdant_01", "army_copper_01"), catalog.armies.map { it.id }.toSet())
        assertEquals(980, catalog.armies.sumOf { it.total })
        assertEquals(7700, catalog.armies.sumOf { it.supplyFood })
        assertEquals(listOf(UnitAllocation(UnitType.HUMAN_SWORD, 300), UnitAllocation(UnitType.KNIGHT, 60)), catalog.armies.first { it.id == "army_ash_01" }.units)
        assertEquals("Veyk Aschenhand", catalog.enemyCommanders.first { it.id == "enemy_veyk_01" }.name)
        assertEquals("Die alten Pässe beherrschen", catalog.factions.first { it.id == "ash_covenant" }.longTermGoal)
        assertEquals("Spieler", catalog.factions.first { it.id == PLAYER_FACTION }.ruler)
        assertEquals("Grenzfeste", catalog.places.first { it.id == "keep" }.name)
        assertFalse(catalog.armies.any { it.factionId == PLAYER_FACTION })
        assertEquals(catalog, WorldContentCatalog.decode(encoded(catalog)))
    }

    @Test
    fun corruptAndIncompleteJsonIsRejected() {
        listOf("{", "[]", "null", "{\"version\":1}", "{version:1}").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { WorldContentCatalog.decode(raw) }
        }
        val root = Json.parseToJsonElement(encoded()).jsonObject
        val unknownField = JsonObject(root + ("unexpected" to JsonPrimitive(true)))
        assertThrows(IllegalArgumentException::class.java) { WorldContentCatalog.decode(unknownField.toString()) }
        val invalidTerrain = encoded().replace("\"CITY\"", "\"UNKNOWN_TERRAIN\"")
        assertThrows(IllegalArgumentException::class.java) { WorldContentCatalog.decode(invalidTerrain) }
    }

    @Test
    fun unsupportedContentVersionsAreRejected() {
        val catalog = WorldContentCatalog.load()
        reject(catalog.copy(version = 0))
        reject(catalog.copy(version = 2))
    }

    @Test
    fun duplicatedIdsAreRejectedInEveryCatalogSection() {
        val catalog = WorldContentCatalog.load()
        reject(catalog.copy(places = catalog.places + catalog.places.first()))
        reject(catalog.copy(roads = catalog.roads + catalog.roads.first()))
        reject(catalog.copy(factions = catalog.factions + catalog.factions.first()))
        reject(catalog.copy(enemyCommanders = catalog.enemyCommanders + catalog.enemyCommanders.first()))
        reject(catalog.copy(armies = catalog.armies + catalog.armies.first()))
        reject(catalog.copy(places = listOf(catalog.places.first().copy(id = "random place!")) + catalog.places.drop(1)))
    }

    @Test
    fun missingCoreIdsAndDanglingReferencesAreRejected() {
        val catalog = WorldContentCatalog.load()
        reject(catalog.copy(places = catalog.places.filterNot { it.id == "keep" }))
        reject(catalog.copy(factions = catalog.factions.filterNot { it.id == NEUTRAL_FACTION }))
        reject(catalog.copy(places = listOf(catalog.places.first().copy(ownerId = "missing_faction")) + catalog.places.drop(1)))
        reject(catalog.copy(roads = listOf(catalog.roads.first().copy(to = "missing_place")) + catalog.roads.drop(1)))
        reject(catalog.copy(factions = listOf(catalog.factions.first().copy(capitalId = "missing_place")) + catalog.factions.drop(1)))
        reject(catalog.copy(enemyCommanders = listOf(catalog.enemyCommanders.first().copy(factionId = "missing_faction")) + catalog.enemyCommanders.drop(1)))
        reject(catalog.copy(armies = listOf(catalog.armies.first().copy(regionId = "missing_place")) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(catalog.armies.first().copy(enemyCommanderId = "missing_commander")) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(catalog.armies.first().copy(enemyCommanderId = "enemy_nera_01")) + catalog.armies.drop(1)))
    }

    @Test
    fun invalidCoordinatesRoadsAndPoliticalValuesAreRejected() {
        val catalog = WorldContentCatalog.load()
        reject(catalog.copy(places = listOf(catalog.places.first().copy(x = 1.1f)) + catalog.places.drop(1)))
        reject(catalog.copy(places = listOf(catalog.places.first().copy(y = -.1f)) + catalog.places.drop(1)))
        reject(catalog.copy(roads = listOf(catalog.roads.first().copy(distance = 0)) + catalog.roads.drop(1)))
        reject(catalog.copy(roads = listOf(catalog.roads.first().copy(quality = 101)) + catalog.roads.drop(1)))
        reject(catalog.copy(roads = catalog.roads + catalog.roads.first().copy(id = "road_duplicate_connection")))
        reject(catalog.copy(factions = listOf(catalog.factions.first().copy(relations = mapOf("missing_faction" to 0))) + catalog.factions.drop(1)))
        reject(catalog.copy(factions = listOf(catalog.factions.first().copy(relations = mapOf("ash_covenant" to 101))) + catalog.factions.drop(1)))
        reject(catalog.copy(factions = listOf(catalog.factions.first().copy(wars = listOf(PLAYER_FACTION))) + catalog.factions.drop(1)))
    }

    @Test
    fun invalidTroopAllocationsAndRuntimePlayerArmySeedsAreRejected() {
        val catalog = WorldContentCatalog.load()
        val army = catalog.armies.first()
        reject(catalog.copy(armies = listOf(army.copy(units = emptyList())) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(army.copy(units = listOf(UnitAllocation(UnitType.HUMAN_SWORD, -1)))) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(army.copy(units = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 30), UnitAllocation(UnitType.HUMAN_SWORD, 20)))) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(army.copy(units = listOf(UnitAllocation(UnitType.HUMAN_SWORD, Int.MAX_VALUE), UnitAllocation(UnitType.KNIGHT, 1)))) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(army.copy(factionId = PLAYER_FACTION, regionId = "keep", enemyCommanderId = null)) + catalog.armies.drop(1)))
        reject(catalog.copy(armies = listOf(army.copy(missionId = 1)) + catalog.armies.drop(1)))
    }

    @Test
    fun armiesCannotReceiveUnfundedFoodOrExceedFactionPopulation() {
        val catalog = WorldContentCatalog.load()
        val ash = catalog.factions.first { it.id == "ash_covenant" }
        reject(catalog.copy(factions = catalog.factions.map { if (it.id == ash.id) it.copy(food = 3359) else it }))
        reject(catalog.copy(factions = catalog.factions.map { if (it.id == ash.id) it.copy(population = 359) else it }))
        reject(catalog.copy(factions = catalog.factions.map { if (it.id == ash.id) it.copy(gold = -1) else it }))
        reject(catalog.copy(armies = listOf(catalog.armies.first().copy(supplyFood = -1)) + catalog.armies.drop(1)))
    }

    @Test
    fun marchingArmyNeedsAConnectedRouteAndMatchingDestination() {
        val catalog = WorldContentCatalog.load()
        val army = catalog.armies.first()
        val marching = army.copy(status = WorldArmyStatus.MARCHING, route = listOf("capital_ash_01", "orc"), destinationId = "orc", arrivalDay = 2)
        val valid = catalog.copy(armies = listOf(marching) + catalog.armies.drop(1))
        assertEquals(valid, WorldContentCatalog.decode(encoded(valid)))
        reject(catalog.copy(armies = listOf(army.copy(status = WorldArmyStatus.MARCHING)) + catalog.armies.drop(1)))
        reject(valid.copy(armies = listOf(marching.copy(destinationId = "keep")) + catalog.armies.drop(1)))
        reject(valid.copy(armies = listOf(marching.copy(route = listOf("capital_ash_01", "keep"), destinationId = "keep")) + catalog.armies.drop(1)))
        reject(valid.copy(armies = listOf(marching.copy(routeIndex = 8)) + catalog.armies.drop(1)))
    }
}
