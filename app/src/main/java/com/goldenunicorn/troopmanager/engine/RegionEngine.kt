package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Acquisition targets existing regions, retaining their stable IDs and mission links. */
object RegionEngine {
    fun canAcquire(region: WorldRegion): Boolean =
        !region.owned && region.type in listOf(RegionType.VILLAGE, RegionType.FOREST, RegionType.MINE)

    fun purchaseCost(region: WorldRegion): Int = when (region.type) {
        RegionType.VILLAGE -> 3000
        RegionType.FOREST -> 3500
        RegionType.MINE -> 4000
        else -> 0
    }

    fun diplomacy(state: GameState): Int = state.player.diplomacy +
        (if (state.companion.met && state.companion.trust >= 45) state.companion.diplomacy / 2 else 0) + CharacterEngine.bonuses(state).diplomacy

    fun diplomaticCost(state: GameState, region: WorldRegion): Int =
        (purchaseCost(region).toLong() * (100 - diplomacy(state).coerceIn(0, 100) / 3) / 100).toInt()

    fun buy(state: GameState, regionId: String): GameEngine.ActionResult = acquire(state, regionId, false)

    fun negotiate(state: GameState, regionId: String): GameEngine.ActionResult = acquire(state, regionId, true)

    private fun acquire(state: GameState, regionId: String, negotiated: Boolean): GameEngine.ActionResult {
        val region = state.regions.firstOrNull { it.id == regionId }
            ?: return GameEngine.ActionResult(state, "Region nicht gefunden.")
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Gebietsverhandlungen nach der Schlacht möglich.")
        if (region.owned) return GameEngine.ActionResult(state, "${region.name} gehört bereits zu deinem Reich.")
        if (!canAcquire(region)) return GameEngine.ActionResult(state, "Diese Region steht nicht zum Verkauf.")
        val owner = state.world.place(regionId)?.ownerId
        if (owner != null && owner !in listOf(NEUTRAL_FACTION, PLAYER_FACTION))
            return GameEngine.ActionResult(state, "Diese Region gehört einem anderen Reich. Verhandle mit dessen Herrscher.")
        if (negotiated && diplomacy(state) < 35)
            return GameEngine.ActionResult(state, "Verhandlungen benötigen mindestens 35 gemeinsame Diplomatie.")
        val cost = if (negotiated) diplomaticCost(state, region) else purchaseCost(region)
        if (state.resources.gold < cost) return GameEngine.ActionResult(state, "Übernahme benötigt $cost Gold.")
        if (state.realm.territory == Int.MAX_VALUE)
            return GameEngine.ActionResult(state, "Die Gebietsgrenze ist erreicht.")
        val culture = when (region.type) {
            RegionType.FOREST -> Culture.WOOD_ELF
            RegionType.VILLAGE -> Culture.HUMAN
            else -> null
        }
        var population = state.population
        val incoming = if (culture == Culture.WOOD_ELF) 250 else if (culture == Culture.HUMAN) 120 else 0
        if (culture != null) {
            population = ArmyEngine.adjustPopulation(population, culture, incoming)
            population = ArmyEngine.adjustRecruits(population, culture, if (culture == Culture.WOOD_ELF) 40 else 20)
        }
        val next = ProgressionEngine.update(ProgressionEngine.awardXp(state.copy(
            resources = state.resources.copy(gold = state.resources.gold - cost),
            population = population,
            city = state.city.copy(housingCapacity = (state.city.housingCapacity.toLong() + incoming).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
            realm = state.realm.copy(territory = state.realm.territory + 1),
            regions = state.regions.map { if (it.id == region.id) it.copy(owned = true) else it },
            chronicle = (state.chronicle + ChronicleEntry(state.day, "${region.name} übernommen",
                "${if (negotiated) "Diplomatische Einigung" else "Gebietskauf"}: $cost Gold. ${bonusDescription(region)}")).takeLast(2000),
        ), 15))
        return GameEngine.ActionResult(next, "${region.name} gehört nun zu deinem Reich. ${bonusDescription(region)}")
    }

    fun bonusDescription(region: WorldRegion): String = when (region.type) {
        RegionType.MINE -> "+75 Eisen/Tag"
        RegionType.FOREST -> "+100 Holz/Tag · 250 Waldelben · 40 Rekruten bei Übernahme"
        RegionType.VILLAGE -> "+75 Gold/Tag · 120 Einwohner · 20 Rekruten bei Übernahme"
        else -> ""
    }
}
