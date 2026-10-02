package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil
import kotlin.random.Random

/** Campaign simulation. Quantities reference armyPools: expedition rows are reservations, never extra troops. */
object WorldEngine {
    fun initialize(state: GameState): GameState {
        if (state.world.initialized) return synchronizeRegions(state)
        val content = WorldContentCatalog.load()
        val places = content.places.map { original ->
            val p = if (original.id == "keep") original.copy(name = state.realm.settlementName) else original
            if (state.regions.any { it.id == p.id && it.owned }) p.copy(ownerId = PLAYER_FACTION) else p
        }
        val extra = state.regions.filter { r -> places.none { it.id == r.id } }.mapIndexed { i, r ->
            WorldPlace(r.id, r.name, PlaceKind.VILLAGE, WorldTerrain.PLAIN, .12f + i % 5 * .13f, .87f,
                if (r.owned) PLAYER_FACTION else NEUTRAL_FACTION)
        }
        val roads = content.roads + extra.map { WorldRoad("road_keep_${it.id}", "keep", it.id, 24) }
        val factions = content.factions.map { f -> if (f.id == PLAYER_FACTION) f.copy(ruler = state.player.name, population = state.population.total) else f }
        val commanders = content.enemyCommanders
        val armies = content.armies
        var next = state.copy(world = WorldState(
            initialized = true, places = places + extra,
            roads = roads,
            factions = factions.map { f -> f.copy(food = f.food - armies.filter { it.factionId == f.id }.sumOf { it.supplyFood }) },
            enemyCommanders = commanders, armies = armies,
            weather = weather(WeatherState(), places + extra, state.day), lastTickDay = state.day,
        ))
        // Old missions migrate to one persistent world army per existing mission, preserving their reserved quantities.
        next.activeMissions.filter { it.status.isAway }.forEach { m -> next = attachMission(next, m, migrating = true) }
        if (next.invasion != null) next = bindInvasion(next, migrating = true)
        return refreshKnowledge(synchronizeRegions(next))
    }

    private fun synchronizeRegions(state: GameState): GameState {
        val places = state.world.places.map { p ->
            val old = state.regions.firstOrNull { it.id == p.id }
            if (old?.owned == true && p.ownerId == NEUTRAL_FACTION) p.copy(ownerId = PLAYER_FACTION) else p
        }
        val extra = state.regions.filter { r -> places.none { it.id == r.id } }.map { r ->
            WorldPlace(r.id, r.name, PlaceKind.VILLAGE, WorldTerrain.PLAIN, .18f, .9f, if (r.owned) PLAYER_FACTION else NEUTRAL_FACTION)
        }
        return state.copy(world = state.world.copy(places = places + extra,
            roads = state.world.roads + extra.map { WorldRoad("road_keep_${it.id}", "keep", it.id, 24) }))
    }

    fun route(world: WorldState, from: String, to: String): List<String> {
        if (world.place(from) == null || world.place(to) == null) return emptyList()
        val cost = mutableMapOf(from to 0)
        val previous = mutableMapOf<String, String>()
        val todo = world.places.map { it.id }.toMutableSet()
        while (todo.isNotEmpty()) {
            val current = todo.minByOrNull { cost[it] ?: Int.MAX_VALUE } ?: break
            if ((cost[current] ?: Int.MAX_VALUE) == Int.MAX_VALUE) break
            if (current == to) {
                val path = mutableListOf(to)
                var p = to
                while (p != from) { p = previous[p] ?: return emptyList(); path.add(p) }
                return path.reversed()
            }
            todo.remove(current)
            world.roads.filter { it.connects(current) }.forEach { road ->
                val other = road.other(current)
                val next = cost.getValue(current) + road.distance
                if (next < (cost[other] ?: Int.MAX_VALUE)) { cost[other] = next; previous[other] = current }
            }
        }
        return emptyList()
    }

    fun movementPerDay(state: GameState, army: WorldArmy, destinationId: String): Int {
        val target = state.world.place(destinationId) ?: return 1
        val road = state.world.roads.firstOrNull { it.connects(army.regionId) && it.other(army.regionId) == destinationId }
        val commander = state.commanders.firstOrNull { it.id == army.commanderId }
        val rival = state.world.enemyCommanders.firstOrNull { it.id == army.enemyCommanderId }
        val leadership = commander?.leadership ?: if (army.factionId == PLAYER_FACTION) state.player.leadership else (40 + (rival?.experience ?: 0) / 10 - (rival?.injuries ?: 0) * 2).coerceIn(20, 85)
        val terrain = 18.0 / target.terrain.movementCost
        val weather = state.world.weather.at(destinationId)
        val size = if (army.total > 10000) 0.65 else if (army.total > 2000) 0.85 else 1.0
        val doctrine =
            if (army.factionId == PLAYER_FACTION) DoctrineEngine.marchFactor(state) else 1.0
        val origin =
            if (army.factionId == PLAYER_FACTION)
                OriginEngine.forestMarchFactor(state, target.terrain)
            else 1.0
        return (30 * terrain * (0.7 + (road?.quality ?: 20) / 200.0) *
            state.world.weather.season.marchFactor * weather.marchFactor * size * army.marchPolicy.speed *
            (0.8 + army.morale / 400.0) * (0.9 + leadership / 500.0) * doctrine * origin)
            .toInt()
            .coerceAtLeast(3)
    }

    fun travelDays(state: GameState, army: WorldArmy, route: List<String>): Int =
        route.zipWithNext().mapIndexed { index, (from, to) ->
            val road = state.world.roads.firstOrNull { it.connects(from) && it.other(from) == to }
            val progress = if (index == 0 && from == army.regionId) army.legProgress else 0
            val remaining = ((road?.distance ?: 30) - progress).coerceAtLeast(0)
            ceil(remaining.toDouble() / movementPerDay(state, army.copy(regionId = from), to)).toInt()
        }.sum()

    /** Cheapest physically usable route; a peaceful realm's road cannot block an otherwise open detour. */
    fun routeForFaction(state: GameState, factionId: String, from: String, to: String): List<String> {
        val accessible = state.world.places.filter { it.id == from || it.ownerId == factionId || it.ownerId == NEUTRAL_FACTION || DiplomacyEngine.canEnterTerritory(state, factionId, it.ownerId) }.map { it.id }.toSet()
        val graph = state.world.copy(places = state.world.places.filter { it.id in accessible }, roads = state.world.roads.filter { it.from in accessible && it.to in accessible })
        return route(graph, from, to)
    }

    fun dispatch(state: GameState, destinationId: String, commanderId: Long?, units: List<UnitAllocation>, policy: MarchPolicy = MarchPolicy.NORMAL, supplyDays: Int = 6): GameEngine.ActionResult {
        val base = initialize(state)
        if (base.battleSession?.isActive == true) return result(state, "Die laufende Schlacht bindet alle Befehle.")
        if (base.world.place(destinationId) == null || destinationId !in base.world.knowledgeFor(PLAYER_FACTION).exploredRegions)
            return result(state, "Das Ziel muss zuerst aufgeklärt werden.")
        if (destinationId == "keep") return result(state, "Wähle ein Ziel außerhalb der Feste.")
        val presence = PresenceEngine.presence(base)
        if (commanderId == null && !presence.player.available)
            return result(state, "Du bist derzeit ${presence.player.location.label.lowercase()} gebunden.")
        if (commanderId == COMPANION_COMMANDER_ID && !presence.companion.available)
            return result(state, "${base.companion.name} ist derzeit ${presence.companion.location.label.lowercase()} gebunden.")
        if (commanderId != null && (base.commanders.none { it.id == commanderId } || base.commanderAway(commanderId) || base.war.unavailableCommander(commanderId))) return result(state, "Kommandant ist nicht verfügbar.")
        if (units.any { it.amount < 0 } || units.sumOf { it.amount.toLong() } > Int.MAX_VALUE) return result(state, "Ungültige Truppenzahl.")
        val selected = ArmyEngine.normalize(units)
        if (selected.sumOf { it.amount } < 30) return result(state, "Mindestens 30 Soldaten benötigt.")
        if (selected.any { it.amount > MissionEngine.available(base, commanderId, it.type) }) return result(state, "Diese Soldaten sind bereits gebunden.")
        val path = routeForFaction(base, PLAYER_FACTION, "keep", destinationId)
        if (path.size < 2 || !allowedRoute(base, PLAYER_FACTION, path)) return result(state, "Kein zugänglicher Marschweg. Militärzugang oder Kriegserklärung nötig.")
        val army = WorldArmy("army_player_${base.world.nextArmyNumber}", PLAYER_FACTION,
            base.commanders.firstOrNull { it.id == commanderId }?.let { "${it.name}s Banner" } ?: "Banner der Letzten Mauer",
            selected, "keep", commanderId = commanderId, destinationId = destinationId, route = path,
            marchPolicy = policy, status = WorldArmyStatus.MARCHING)
        val food = army.dailyFood.toLong() * supplyDays.coerceIn(1, 30)
        if (food > base.resources.food) return result(state, "Der Feldzug benötigt $food Nahrung für den gewählten Vorrat.")
        val days = travelDays(base, army, path)
        val next = base.copy(resources = base.resources.copy(food = base.resources.food - food.toInt()),
            commanderAssignments = base.commanderAssignments.filterNot { it.commanderId == commanderId },
            world = base.world.copy(armies = base.world.armies + army.copy(supplyFood = food.toInt(), arrivalDay = base.day + days), nextArmyNumber = base.world.nextArmyNumber + 1))
        return result(refreshKnowledge(log(next, "Feldzug begonnen", "${army.name}: ${army.total} Soldaten nach ${base.world.place(destinationId)?.name}; geschätzte Ankunft Tag ${base.day + days}.")), "Heer aufgebrochen. Reise etwa $days Tage; Versorgung ${supplyDays.coerceIn(1,30)} Tage.")
    }

    fun redirect(state: GameState, armyId: String, destinationId: String, policy: MarchPolicy = MarchPolicy.NORMAL): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return result(state, "Befehle nach der Schlacht möglich.")
        val army = state.world.armies.firstOrNull { it.id == armyId && it.factionId == PLAYER_FACTION && it.missionId == null && it.status.isAway }
            ?: return result(state, "Dieses Heer ist nicht verfügbar.")
        if (destinationId !in state.world.knowledgeFor(PLAYER_FACTION).exploredRegions) return result(state, "Das Ziel ist nicht aufgeklärt.")
        val path = routeForFaction(state, PLAYER_FACTION, army.regionId, destinationId)
        if (path.isEmpty() || !allowedRoute(state, PLAYER_FACTION, path)) return result(state, "Kein zugänglicher Marschweg.")
        val reverseProgress = if (path.size == 1 && destinationId == "keep") army.legProgress else 0
        val moved = army.copy(destinationId = destinationId, route = path, routeIndex = 0, legProgress = reverseProgress,
            arrivalDay = state.day + if (reverseProgress > 0) 1 else travelDays(state, army.copy(marchPolicy = policy), path), marchPolicy = policy,
            status = if (destinationId == "keep") WorldArmyStatus.RETURNING else if (path.size == 1) WorldArmyStatus.HOLDING else WorldArmyStatus.MARCHING)
        return result(replaceArmy(state, moved), "Marschziel gesetzt; Prognose Tag ${moved.arrivalDay}, Wetter kann die Ankunft ändern.")
    }

    fun recall(state: GameState, armyId: String): GameEngine.ActionResult {
        val army = state.world.armies.firstOrNull { it.id == armyId } ?: return result(state, "Heer nicht gefunden.")
        if (army.missionId != null) return MissionEngine.recall(state, army.missionId)
        if (army.regionId == "keep" && army.legProgress == 0) return result(homeArmy(state, army), "Heer ist wieder in der Feste verfügbar.")
        return redirect(state, armyId, "keep", MarchPolicy.SAFE)
    }

    fun scout(state: GameState, regionId: String): GameEngine.ActionResult {
        val base = initialize(state)
        if (base.battleSession?.isActive == true) return result(state, "Aufklärung nach der Schlacht möglich.")
        val intel = base.world.knowledgeFor(PLAYER_FACTION)
        if (base.world.place(regionId) == null || (regionId !in intel.exploredRegions && base.world.roads.none { it.connects(regionId) && it.other(regionId) in intel.exploredRegions }))
            return result(state, "Kundschafter erreichen nur bekannte Orte und ihre Nachbarn.")
        val scoutGold = kotlin.math.ceil(75 * OriginEngine.scoutingCostFactor(base)).toInt()
        if (base.resources.gold < scoutGold || base.resources.food < 100)
            return result(state, "Aufklärung kostet $scoutGold Gold und 100 Nahrung.")
        val updated = intel.copy(exploredRegions = (intel.exploredRegions + regionId).distinct(), visibleRegions = (intel.visibleRegions + regionId).distinct(),
            observations = intel.observations.filterNot { o -> base.world.armies.any { it.id == o.armyId && it.regionId == regionId } } +
                base.world.armies.filter { it.regionId == regionId && it.status != WorldArmyStatus.DESTROYED }.map { observation(it, base.day, true) })
        return result(base.copy(resources = base.resources.copy(gold = base.resources.gold - scoutGold, food = base.resources.food - 100),
            world = base.world.copy(knowledge = base.world.knowledge.filterNot { it.factionId == PLAYER_FACTION } + updated)), "Kundschafterbericht von ${base.world.place(regionId)?.name}: genaue Stärken an Tag ${base.day}.")
    }

    fun observations(state: GameState): List<ArmyObservation> = state.world.knowledgeFor(PLAYER_FACTION).observations.filter { it.factionId != PLAYER_FACTION && state.day - it.day <= 15 }

    fun buildDepot(state: GameState, regionId: String, food: Int = 2000): GameEngine.ActionResult {
        val place = state.world.place(regionId) ?: return result(state, "Ort nicht gefunden.")
        if (state.battleSession?.isActive == true || place.ownerId != PLAYER_FACTION) return result(state, "Nachschublager benötigen eigenes, gesichertes Gebiet.")
        if (food !in 1..10000 || state.resources.food < food || state.resources.gold < 300 || state.resources.wood < 150) return result(state, "Lager kostet 300 Gold, 150 Holz und $food Nahrung.")
        if (state.world.depots.any { it.regionId == regionId && it.factionId == PLAYER_FACTION }) return result(state, "Hier besteht bereits ein Depot.")
        val depot = SupplyDepot("depot_${PLAYER_FACTION}_$regionId", regionId, PLAYER_FACTION, food)
        return result(state.copy(resources = state.resources.copy(gold = state.resources.gold - 300, wood = state.resources.wood - 150, food = state.resources.food - food),
            world = state.world.copy(depots = state.world.depots + depot)), "Depot mit $food Nahrung eingerichtet.")
    }

    fun sendConvoy(state: GameState, armyId: String, food: Int = 1000): GameEngine.ActionResult {
        val army = state.world.armies.firstOrNull { it.id == armyId && it.factionId == PLAYER_FACTION && it.status.isAway }
            ?: return result(state, "Heer nicht verfügbar.")
        if (state.battleSession?.isActive == true || food <= 0 || food > state.resources.food || state.resources.gold < 100) return result(state, "Konvoi benötigt 100 Gold und $food Nahrung.")
        val path = routeForFaction(state, PLAYER_FACTION, "keep", army.regionId)
        if (path.isEmpty() || !allowedRoute(state, PLAYER_FACTION, path)) return result(state, "Der Nachschubweg ist unterbrochen.")
        val convoy = SupplyConvoy("convoy_${state.world.nextConvoyNumber}", PLAYER_FACTION, armyId, food, "keep", path,
            arrivalDay = state.day + (path.size - 1).coerceAtLeast(1))
        return result(state.copy(resources = state.resources.copy(food = state.resources.food - food, gold = state.resources.gold - 100),
            world = state.world.copy(convoys = (state.world.convoys.filterNot { it.complete || it.lost } + convoy), nextConvoyNumber = state.world.nextConvoyNumber + 1)), "Nachschub ist unterwegs. Unbewachte feindliche Straßen gefährden den Konvoi.")
    }

    fun attachMission(state: GameState, mission: ActiveMission, migrating: Boolean = false): GameState {
        if (state.world.armies.any { it.missionId == mission.id }) return state
        val target = mission.regionId ?: "keep"
        val path = if (migrating) listOf(target) else route(state.world, "keep", target).ifEmpty { listOf("keep") }
        val army = WorldArmy("army_mission_${mission.id}", PLAYER_FACTION, mission.missionType.label, mission.units,
            if (migrating) target else "keep", mission.commanderId, destinationId = target, route = path,
            arrivalDay = state.day + travelDays(state, WorldArmy("temp", PLAYER_FACTION, "", mission.units, "keep"), path),
            supplyFood = mission.supplyCost, status = if (path.size > 1) WorldArmyStatus.MARCHING else WorldArmyStatus.MISSION,
            missionId = mission.id)
        return state.copy(world = state.world.copy(armies = state.world.armies + army))
    }

    fun returnMission(state: GameState, missionId: Long): GameState {
        val army = state.world.armies.firstOrNull { it.missionId == missionId } ?: return state
        val path = route(state.world, army.regionId, "keep")
        return replaceArmy(state, army.copy(destinationId = "keep", route = path, routeIndex = 0, legProgress = if (path.size == 1) army.legProgress else 0,
            arrivalDay = state.day + if (path.size == 1 && army.legProgress > 0) 1 else travelDays(state, army, path), status = if (army.regionId == "keep" && army.legProgress == 0) WorldArmyStatus.HOME else WorldArmyStatus.RETURNING))
    }

    fun completeMission(state: GameState, mission: ActiveMission, losses: List<UnitAllocation>): GameState {
        val army = state.world.armies.firstOrNull { it.missionId == mission.id } ?: return state
        val units = army.units.mapNotNull { u -> (u.amount - (losses.firstOrNull { it.type == u.type }?.amount ?: 0)).coerceAtLeast(0).let { if (it > 0) u.copy(amount = it) else null } }
        return replaceArmy(state, army.copy(units = units, status = WorldArmyStatus.HOME, supplyFood = 0))
    }

    fun tick(state: GameState): GameState {
        var next = reconcileBattle(initialize(state))
        if (next.world.lastTickDay >= next.day) return next
        next = synchronizeRegions(next)
        next = next.copy(world = next.world.copy(weather = weather(next.world.weather, next.world.places, next.day), lastTickDay = next.day))
        next = tickFactionEconomy(next)
        next = tickRecruitment(next)
        next = refreshKnowledge(next)
        next = decideArmies(next)
        next.world.armies.toList().filter { it.status.isAway }.forEach { a -> next = moveArmy(next, a.id) }
        next = tickConvoys(next)
        next = resolveAiEncounters(next)
        next = refreshKnowledge(next)
        next = storyTick(next)
        val pendingMissionIds = next.activeMissions.filter { it.status.isAway }.map { it.id }.toSet()
        val queuedArmyIds = next.world.recruitments.map { it.armyId }.toSet()
        val live = next.world.armies.filter { it.status.isAway || it.status == WorldArmyStatus.ENGAGED || it.missionId in pendingMissionIds || it.id in queuedArmyIds }
        val liveIds = live.map { it.id }.toSet()
        val historical = next.world.armies.filterNot { it.id in liveIds }.takeLast(35)
        return ArmyEngine.clampAssignments(next.copy(world = next.world.copy(armies = live + historical)))
    }

    private fun weather(old: WeatherState, places: List<WorldPlace>, day: Int): WeatherState {
        val season = Season.forDay(day)
        return WeatherState(season, places.map { p ->
            val previous = old.regions.firstOrNull { it.regionId == p.id }
            if (previous != null && previous.untilDay > day && old.season == season) previous
            else {
                val random = Random(p.id.hashCode() xor (day / 3 * 7919))
                val candidates = when (season) {
                    Season.SPRING -> listOf(WeatherKind.CLEAR, WeatherKind.RAIN, WeatherKind.RAIN, WeatherKind.FOG, WeatherKind.STORM)
                    Season.SUMMER -> listOf(WeatherKind.CLEAR, WeatherKind.CLEAR, WeatherKind.HEAT, WeatherKind.RAIN)
                    Season.AUTUMN -> listOf(WeatherKind.CLEAR, WeatherKind.RAIN, WeatherKind.FOG, WeatherKind.STORM)
                    Season.WINTER -> listOf(WeatherKind.SNOW, WeatherKind.FROST, WeatherKind.FOG, WeatherKind.CLEAR)
                }
                RegionalWeather(p.id, candidates[random.nextInt(candidates.size)], day + random.nextInt(3, 6))
            }
        })
    }

    private fun refreshKnowledge(state: GameState): GameState {
        val knowledge = state.world.factions.map { f ->
            val old = state.world.knowledgeFor(f.id)
            val homes = state.world.places.filter { it.ownerId == f.id }.map { it.id }
            val ownArmies = state.world.armies.filter { it.factionId == f.id && it.status != WorldArmyStatus.DESTROYED && it.status != WorldArmyStatus.HOME }
            val sight = if (f.id == PLAYER_FACTION && state.realm.scoutingDays > 0) 2 else 1
            val visible = (homes + ownArmies.map { it.regionId }).toMutableSet()
            repeat(sight) {
                val frontier = visible.toList()
                state.world.roads.filter { it.from in frontier || it.to in frontier }.forEach { road ->
                    val origin = if (road.from in frontier) road.from else road.to
                    val weatherSight = state.world.weather.at(origin).sight
                    if (weatherSight > 0 || origin in homes) visible.add(road.other(origin))
                }
            }
            // Tower investment buys actual wider reports; it never reveals the entire map.
            if (f.id == PLAYER_FACTION && state.realm.level(BuildingType.TOWER) >= 3) {
                state.world.roads.filter { it.from in visible || it.to in visible }.forEach { visible.add(it.from); visible.add(it.to) }
            }
            val seen = state.world.armies.filter { it.regionId in visible && it.status != WorldArmyStatus.DESTROYED && it.status != WorldArmyStatus.HOME }
            val reports = old.observations.filterNot { o -> o.regionId in visible || seen.any { it.id == o.armyId } }.filter { state.day - it.day <= 30 } + seen.map { a ->
                observation(a, state.day, a.factionId == f.id || (f.id == PLAYER_FACTION && state.realm.scoutingDays > 0) || ownArmies.any { it.regionId == a.regionId })
            }
            old.copy(exploredRegions = (old.exploredRegions + visible).distinct(), visibleRegions = visible.toList(), observations = reports)
        }
        return state.copy(world = state.world.copy(knowledge = knowledge))
    }

    private fun observation(army: WorldArmy, day: Int, exact: Boolean): ArmyObservation = ArmyObservation(army.id, army.regionId, day,
        if (exact) army.total else (army.total * .8).toInt() / 10 * 10,
        if (exact) army.total else ceil(army.total * 1.2 / 10).toInt() * 10, exact, army.factionId, army.name)

    fun routeAllowed(state: GameState, faction: String, path: List<String>): Boolean = path.isNotEmpty() && allowedRoute(state, faction, path)

    private fun allowedRoute(state: GameState, faction: String, path: List<String>): Boolean = path.drop(1).all { id ->
        val owner = state.world.place(id)?.ownerId ?: return@all false
        owner == faction || owner == NEUTRAL_FACTION || DiplomacyEngine.canEnterTerritory(state, faction, owner)
    }

    private fun tickFactionEconomy(state: GameState): GameState {
        var next = state
        val factions = state.world.factions.map { f ->
            if (f.id == PLAYER_FACTION || f.id == NEUTRAL_FACTION) return@map f
            val holdings = state.world.places.filter { it.ownerId == f.id }
            val soldiers = state.world.armies.filter { it.factionId == f.id && it.status != WorldArmyStatus.DESTROYED }.sumOf { it.total }
            val goldIncome = holdings.size * 35 + f.buildings * 10 - soldiers / 25
            val harvest = (holdings.sumOf { it.population / 8 + 50 } * state.world.weather.season.harvestFactor).toInt()
            val gold = (f.gold.toLong() + goldIncome).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            val food = (f.food.toLong() + harvest).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            val ironIncome = holdings.count { it.kind == PlaceKind.MINE } * 15L + f.buildings * 3L
            val iron = (f.iron.toLong() + ironIncome).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            val build = state.day % 20 == 0 && gold >= 1000
            val updated = f.copy(gold = gold - if (build) 1000 else 0, food = food, iron = iron, buildings = f.buildings + if (build) 1 else 0)
            if (build) {
                next = next.copy(world = next.world.copy(places = next.world.places.map { if (it.id == f.capitalId && it.ownerId == f.id) it.copy(fortification = (it.fortification + 5).coerceAtMost(100)) else it }))
                next = log(next, "${f.name} baut aus", "${f.ruler} finanziert eine Grenzbefestigung und Werkstätten für 1.000 Gold.")
            }
            updated
        }
        return next.copy(world = next.world.copy(factions = factions))
    }

    private fun tickRecruitment(state: GameState): GameState {
        var next = state
        val remaining = state.world.recruitments.filter { order ->
            val army = next.world.armies.firstOrNull { it.id == order.armyId && it.status != WorldArmyStatus.DESTROYED }
            val capital = next.world.faction(order.factionId)?.capitalId
            if (army == null || army.factionId != order.factionId || capital == null || next.world.place(capital)?.ownerId != order.factionId) {
                next = log(next, "Ausbildung unterbrochen", "${next.world.faction(order.factionId)?.name}: das Ausbildungszentrum oder das Zielheer ist verloren; bereits gezahlte Ausbildungskosten bleiben verbraucht.")
                return@filter false
            }
            if (order.completionDay > state.day || army.regionId != capital || army.status != WorldArmyStatus.HOLDING) return@filter true
            next = replaceArmy(next, army.copy(units = ArmyEngine.normalize(army.units + UnitAllocation(order.type, order.amount))))
            next = log(next, "Ausbildung abgeschlossen", "${next.world.faction(order.factionId)?.name}: ${order.amount} ${order.type.label} schließen sich ${army.name} an.")
            false
        }
        return next.copy(world = next.world.copy(recruitments = remaining))
    }

    private fun decideArmies(state: GameState): GameState {
        var next = state
        state.world.factions.filter { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }.forEach { faction ->
            val current = next.world.faction(faction.id) ?: faction
            val intel = next.world.knowledgeFor(current.id)
            val armies = next.world.armies.filter { it.factionId == current.id && it.status != WorldArmyStatus.DESTROYED && it.missionId == null }
            // Border scouting is an explicit local action, shared rules with player scouts.
            if (state.day % DifficultyEngine.scoutInterval(state) == 0 &&
                current.gold >= 75 &&
                current.food >= 100
            ) {
                val frontier = next.world.roads.flatMap { listOf(it.from to it.to, it.to to it.from) }.firstOrNull { it.first in intel.exploredRegions && it.second !in intel.exploredRegions }
                if (frontier != null) {
                    val known = intel.copy(exploredRegions = (intel.exploredRegions + frontier.second).distinct())
                    next = next.copy(world = next.world.copy(knowledge = next.world.knowledge.filterNot { it.factionId == current.id } + known,
                        factions = next.world.factions.map { if (it.id == current.id) it.copy(gold = it.gold - 75, food = it.food - 100, lastDecision = "Kundschafter nach ${next.world.place(frontier.second)?.name}", lastDecisionDay = state.day) else it }))
                }
            }
            armies.filter { it.status == WorldArmyStatus.HOLDING }.forEach { army ->
                var f = next.world.faction(current.id) ?: current
                var a = next.world.armies.first { it.id == army.id }
                if (a.id == next.world.invasionArmyId) return@forEach
                if (
                    a.regionId == f.capitalId &&
                    a.total < 1500 &&
                    state.day % DifficultyEngine.recruitmentInterval(state) == 0 &&
                    a.units.isNotEmpty() &&
                    next.world.recruitments.none { it.armyId == a.id }
                ) {
                    val recruitType = a.units.first().type
                    val amount = 50
                    val goldCost = recruitType.goldCost.toLong() * amount
                    val ironCost = recruitType.ironCost.toLong() * amount
                    val reserved = next.world.armies.filter { it.factionId == f.id && it.status != WorldArmyStatus.DESTROYED }.sumOf { it.total.toLong() } + next.world.recruitments.filter { it.factionId == f.id }.sumOf { it.amount.toLong() }
                    if (f.gold >= goldCost && f.iron >= ironCost && f.population.toLong() - reserved >= amount) {
                        val order = WorldRecruitment("recruit_${f.id}_${state.day}", f.id, a.id, recruitType, amount, state.day + recruitType.trainingDays)
                        f = f.copy(gold = f.gold - goldCost.toInt(), iron = f.iron - ironCost.toInt(),
                            lastDecision = "$amount ${recruitType.label} in Ausbildung: $goldCost Gold, $ironCost Eisen; Abschluss Tag ${order.completionDay}", lastDecisionDay = state.day)
                        next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == f.id) f else it }, recruitments = next.world.recruitments + order))
                    }
                }
                if (next.world.recruitments.any { it.armyId == a.id }) return@forEach
                val rival = next.world.enemyCommanders.firstOrNull { it.id == a.enemyCommanderId }
                if ((rival?.rulerLoyalty ?: 70) < 15) {
                    next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == f.id) it.copy(lastDecision = "${rival?.name} verweigert den Marsch wegen gebrochener Loyalität", lastDecisionDay = state.day) else it }))
                    return@forEach
                }
                val known = next.world.knowledgeFor(f.id)
                val threats =
                    known.observations.filter {
                        it.day >= state.day - DifficultyEngine.intelMemoryDays(state) &&
                            it.factionId in f.wars
                    }
                val threatHere = threats.firstOrNull { it.regionId == a.regionId && it.minimum > a.total * 1.2 }
                val suppliesLow = a.supplyDays < 2 && a.regionId != f.capitalId
                val target = when {
                    suppliesLow || threatHere != null -> f.capitalId
                    threats.isNotEmpty() && f.aggression + (rival?.rivalry ?: 0) / 5 > 55 -> threats.filter { it.maximum < a.total * 1.4 }.minByOrNull { route(next.world, a.regionId, it.regionId).size }?.regionId
                    state.day % DifficultyEngine.attackInterval(state, f.aggression > 50) == 0 ->
                        next.world.places.filter { p ->
                        p.id in known.exploredRegions && p.ownerId != f.id &&
                            (p.ownerId == NEUTRAL_FACTION || p.ownerId in f.wars) &&
                            route(next.world, a.regionId, p.id).let { it.size in 2..4 && allowedRoute(next, f.id, it) } &&
                            known.observations.none { o -> o.regionId == p.id && o.factionId != f.id && o.maximum > a.total }
                    }.minByOrNull { p -> route(next.world, a.regionId, p.id).size * 10 - if (p.kind == PlaceKind.VILLAGE || p.kind == PlaceKind.MINE) 5 else 0 }?.id
                    else -> null
                }
                if (target != null && target != a.regionId) {
                    val path = route(next.world, a.regionId, target)
                    if (allowedRoute(next, f.id, path)) {
                        val moved = a.copy(destinationId = target, route = path, routeIndex = 0, legProgress = 0,
                            arrivalDay = state.day + travelDays(next, a, path), status = WorldArmyStatus.MARCHING)
                        next = replaceArmy(next, moved)
                        next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == f.id) it.copy(lastDecision = "${a.name}: ${if (suppliesLow) "Versorgung sichern in" else "Marsch nach"} ${next.world.place(target)?.name}; nur bestätigte Grenzberichte", lastDecisionDay = state.day) else it }))
                    }
                }
            }
        }
        return next
    }

    private fun moveArmy(state: GameState, id: String): GameState {
        var next = state
        var army = state.world.armies.firstOrNull { it.id == id } ?: return state
        if (army.lastMovedDay >= state.day) return state
        val waiting = army.missionId?.let { missionId -> state.activeMissions.firstOrNull { it.id == missionId }?.pendingDecision != null } == true
        val logisticsFactor =
            if (
                army.factionId == PLAYER_FACTION &&
                    ResearchTech.SUPPLY_TRAINS in state.research.completed
            ) 0.88
            else 1.0
        val consumption =
            ((army.dailyFood.toLong() *
                if (state.world.weather.season == Season.WINTER) 12 else 10) /
                10 * logisticsFactor)
                .toLong()
                .coerceAtLeast(1)
        if (army.supplyFood < consumption) {
            val depot = next.world.depots.firstOrNull { it.regionId == army.regionId && it.factionId == army.factionId && it.food > 0 }
            if (depot != null) {
                val taken = minOf(depot.food.toLong(), army.dailyFood.toLong() * 5).toInt()
                army = army.copy(supplyFood = (army.supplyFood.toLong() + taken).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                next = next.copy(world = next.world.copy(depots = next.world.depots.map { if (it.id == depot.id) it.copy(food = it.food - taken) else it }))
            } else if (army.factionId != PLAYER_FACTION && next.world.place(army.regionId)?.ownerId == army.factionId) {
                val faction = next.world.faction(army.factionId)
                if (faction != null) {
                    val taken = minOf(faction.food.toLong(), army.dailyFood.toLong() * 7).toInt()
                    army = army.copy(supplyFood = (army.supplyFood.toLong() + taken).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == faction.id) it.copy(food = it.food - taken) else it }))
                }
            }
        }
        val rival = next.world.enemyCommanders.firstOrNull { it.id == army.enemyCommanderId }
        val dividedCommand = (rival?.rulerLoyalty ?: 70) < 40
        val hungry = army.supplyFood < consumption
        val unsafe = next.world.place(army.regionId)?.ownerId?.let { it != army.factionId && it != NEUTRAL_FACTION && DiplomacyEngine.atWar(next, army.factionId, it) } == true
        val rate = (if (hungry) .025 else 0.0) + (if (unsafe) army.marchPolicy.risk else 0.0) + (if (army.marchPolicy == MarchPolicy.FORCED) .005 else 0.0)
        val losses = army.units.map { UnitAllocation(it.type, if (rate > 0) ceil(it.amount * rate).toInt().coerceAtMost(it.amount) else 0) }
        val surviving = army.units.mapNotNull { u -> val amount = u.amount - (losses.first { it.type == u.type }.amount); if (amount > 0) u.copy(amount = amount) else null }
        army = army.copy(units = surviving, supplyFood = (army.supplyFood.toLong() - consumption).coerceAtLeast(0).toInt(),
            morale = (army.morale + (if (hungry) -8 else if (army.marchPolicy == MarchPolicy.FORCED) -2 else 1) - (if (dividedCommand) 3 else 0)).coerceIn(0, 100),
            lastMovedDay = state.day, lastLosses = losses.sumOf { it.amount })
        if (army.lastLosses > 0 && army.factionId == PLAYER_FACTION) {
            // Reduce the reservation before pools, so clamping never consumes a different army's soldiers.
            next = replaceArmy(next, army)
            if (army.missionId != null) next = next.copy(activeMissions = next.activeMissions.map { if (it.id == army.missionId) it.copy(units = surviving, quality = it.quality.mapNotNull { p -> surviving.firstOrNull { it.type == p.type }?.let { u -> p.copy(soldiers = u.amount) } }, losses = it.losses + army.lastLosses) else it })
            next = ArmyEngine.applyLosses(next, losses)
            next = log(next, "Marschverluste", "${army.name}: ${army.lastLosses} Verluste (${if (hungry) "Nahrungsmangel" else "erzwungener oder feindlicher Marsch"}).")
        }
        if (army.lastLosses > 0 && army.factionId != PLAYER_FACTION) next = reduceFactionPopulation(next, army.factionId, army.lastLosses)
        if (army.total == 0) {
            if (army.missionId != null) next = next.copy(activeMissions = next.activeMissions.map { if (it.id == army.missionId) it.copy(status = MissionStatus.FAILED, phase = MissionPhase.DONE, remainingDays = 0, outcome = MissionOutcome.CATASTROPHIC, pendingDecision = null) else it })
            return replaceArmy(next, army.copy(status = WorldArmyStatus.DESTROYED))
        }
        if (!waiting && army.status == WorldArmyStatus.RETURNING && army.route.size == 1 && army.regionId == "keep") {
            army = army.copy(legProgress = (army.legProgress - 20).coerceAtLeast(0))
            if (army.legProgress == 0) {
                if (army.missionId == null) return homeArmy(next, army)
                return replaceArmy(next, army.copy(status = WorldArmyStatus.HOME, arrivalDay = state.day))
            }
        }
        if (!waiting && army.status in listOf(WorldArmyStatus.MARCHING, WorldArmyStatus.RETURNING) && army.routeIndex + 1 < army.route.size) {
            val to = army.route[army.routeIndex + 1]
            if (!allowedRoute(next, army.factionId, listOf(army.regionId, to))) return replaceArmy(next, army.copy(status = WorldArmyStatus.HOLDING, arrivalDay = null))
            val road = next.world.roads.firstOrNull { it.connects(army.regionId) && it.other(army.regionId) == to }
            val distance = road?.distance ?: 30
            val progress = army.legProgress + movementPerDay(next, army, to)
            if (progress >= distance) army = army.copy(regionId = to, routeIndex = army.routeIndex + 1, legProgress = 0)
            else army = army.copy(legProgress = progress)
            val remaining = army.route.drop(army.routeIndex)
            val forecast = state.day + travelDays(next, army, remaining)
            val oldForecast = army.arrivalDay
            val newForecast = if (army.id == next.world.invasionArmyId) maxOf(army.preparationUntilDay ?: next.invasion?.announcedDay?.plus(6) ?: state.day, forecast) else forecast
            if (army.id == next.world.invasionArmyId && oldForecast != null && newForecast > oldForecast)
                next = log(next, "Invasionsmarsch verzögert", "${army.name} bei ${next.world.place(army.regionId)?.name}: ${next.world.weather.at(to).label}, ${next.world.weather.season.label}, Moral ${army.morale}; neue Ankunftsprognose Tag $newForecast statt $oldForecast.")
            army = army.copy(arrivalDay = newForecast)
            if (army.routeIndex >= army.route.lastIndex) {
                army = army.copy(status = if (army.missionId != null && army.destinationId != "keep") WorldArmyStatus.MISSION else if (army.destinationId == "keep" && army.factionId == PLAYER_FACTION) WorldArmyStatus.HOME else WorldArmyStatus.HOLDING, arrivalDay = if (army.id == next.world.invasionArmyId) maxOf(state.day, army.preparationUntilDay ?: state.day) else state.day)
                if (army.status == WorldArmyStatus.HOME && army.missionId == null) return homeArmy(replaceArmy(next, army), army)
                if (army.factionId != PLAYER_FACTION) {
                    val place = next.world.place(army.regionId)
                    val unopposed = next.world.armies.none { it.factionId != army.factionId && it.regionId == army.regionId && it.status.isAway && it.total > 0 }
                    if (place != null && place.id != "keep" && unopposed && (place.ownerId == NEUTRAL_FACTION || DiplomacyEngine.atWar(next, army.factionId, place.ownerId))) {
                        next = changeOwnership(next, place.id, army.factionId)
                        next = log(next, "${place.name} wechselt die Herrschaft", "${next.world.faction(army.factionId)?.name} besetzt den Ort mit ${army.name}.")
                    }
                }
            }
        }
        return replaceArmy(next, army)
    }

    private fun homeArmy(state: GameState, army: WorldArmy): GameState {
        var next = replaceArmy(state, army.copy(status = WorldArmyStatus.HOME, supplyFood = 0))
        if (army.supplyFood > 0) next = next.copy(resources = EconomyEngine.add(next.resources, Resources(0, army.supplyFood, 0, 0, 0)))
        if (army.commanderId != null) next = ArmyEngine.clampAssignments(next.copy(commanderAssignments = next.commanderAssignments.filterNot { it.commanderId == army.commanderId } + CommanderAssignment(army.commanderId, army.units)))
        return log(next, "Heer heimgekehrt", "${army.name}: ${army.total} Soldaten sind wieder an der Feste verfügbar.")
    }

    private fun tickConvoys(state: GameState): GameState {
        var next = state
        val convoys = state.world.convoys.map { c ->
            if (c.complete || c.lost) return@map c
            val index = minOf(c.routeIndex + 1, c.route.lastIndex)
            val region = c.route.getOrNull(index) ?: c.regionId
            val hostile = next.world.armies.any { it.regionId == region && it.status.isAway && DiplomacyEngine.atWar(next, c.factionId, it.factionId) }
            val escort = next.world.armies.any { it.regionId == region && it.status.isAway && it.factionId == c.factionId }
            if (hostile && !escort) {
                next = log(next, "Nachschub verloren", "Ein ungeschützter Konvoi mit ${c.food} Nahrung wurde bei ${next.world.place(region)?.name} abgefangen.")
                return@map c.copy(regionId = region, routeIndex = index, lost = true)
            }
            val target = next.world.armies.firstOrNull { it.id == c.destinationArmyId && it.status.isAway }
            if (target != null && target.regionId == region) {
                next = replaceArmy(next, target.copy(supplyFood = (target.supplyFood.toLong() + c.food).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
                return@map c.copy(regionId = region, routeIndex = index, complete = true)
            }
            if (index == c.route.lastIndex && target != null) {
                val route = route(next.world, region, target.regionId)
                return@map c.copy(regionId = region, route = route, routeIndex = 0, arrivalDay = state.day + route.size)
            }
            if (target == null) {
                next = next.copy(resources = EconomyEngine.add(next.resources, Resources(0, c.food, 0, 0, 0)))
                return@map c.copy(complete = true)
            }
            c.copy(regionId = region, routeIndex = index)
        }
        return next.copy(world = next.world.copy(convoys = convoys.takeLast(40)))
    }

    private fun resolveAiEncounters(state: GameState): GameState {
        var next = state
        val resolved = mutableSetOf<String>()
        state.world.armies.filter { it.factionId != PLAYER_FACTION && it.status.isAway }.forEach { a ->
            val b = next.world.armies.firstOrNull { it.id != a.id && it.id !in resolved && it.factionId != PLAYER_FACTION && it.status.isAway && it.regionId == a.regionId && DiplomacyEngine.atWar(next, a.factionId, it.factionId) } ?: return@forEach
            if (a.id in resolved) return@forEach
            val first = next.world.armies.first { it.id == a.id }
            val powerA = first.units.sumOf { it.amount.toDouble() * (it.type.attack + it.type.defense + it.type.ranged) } * (.5 + first.morale / 100.0) * (1 + (next.world.enemyCommanders.firstOrNull { it.id == first.enemyCommanderId }?.experience ?: 0).coerceAtMost(100) / 200.0)
            val powerB = b.units.sumOf { it.amount.toDouble() * (it.type.attack + it.type.defense + it.type.ranged) } * (.5 + b.morale / 100.0) * (1 + (next.world.enemyCommanders.firstOrNull { it.id == b.enemyCommanderId }?.experience ?: 0).coerceAtMost(100) / 200.0)
            val winner = if (powerA >= powerB) first else b
            val loser = if (winner.id == first.id) b else first
            val winning = winner.copy(units = winner.units.map { it.copy(amount = (it.amount * .8).toInt()) }.filter { it.amount > 0 }, status = WorldArmyStatus.HOLDING)
            val losing = loser.copy(units = loser.units.map { it.copy(amount = (it.amount * .4).toInt()) }.filter { it.amount > 0 }, morale = (loser.morale - 20).coerceAtLeast(0), status = WorldArmyStatus.HOLDING)
            next = replaceArmy(replaceArmy(next, winning), losing)
            next = reduceFactionPopulation(reduceFactionPopulation(next, winner.factionId, winner.total - winning.total), loser.factionId, loser.total - losing.total)
            next = updateNemesis(next, winner.enemyCommanderId, true, false)
            next = updateNemesis(next, loser.enemyCommanderId, false, false)
            next = log(next, "Schlacht bei ${next.world.place(a.regionId)?.name}", "${winner.name} schlägt ${loser.name}. Die überlebenden Kommandanten werden sich wieder begegnen.")
            resolved.add(a.id); resolved.add(b.id)
        }
        return next
    }

    fun engage(state: GameState, armyId: String, enemyArmyId: String): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return result(state, "Eine Schlacht läuft bereits.")
        val own = state.world.armies.firstOrNull { it.id == armyId && it.factionId == PLAYER_FACTION && it.missionId == null && it.status.isAway } ?: return result(state, "Eigenes Heer ist nicht einsatzbereit.")
        val enemy = state.world.armies.firstOrNull { it.id == enemyArmyId && it.factionId != PLAYER_FACTION && it.status.isAway && it.regionId == own.regionId } ?: return result(state, "Das feindliche Heer muss zuerst erreicht werden.")
        if (state.world.knowledgeFor(PLAYER_FACTION).observations.none { it.armyId == enemy.id && it.day == state.day }) return result(state, "Ein aktueller Kundschafterbericht ist nötig.")
        if (!DiplomacyEngine.atWar(state, PLAYER_FACTION, enemy.factionId)) return result(state, "Vor dem Angriff muss Krieg erklärt werden.")
        val engagedAssignments = if (own.commanderId == null) state.commanderAssignments else state.commanderAssignments.filterNot { it.commanderId == own.commanderId } + CommanderAssignment(own.commanderId, own.units)
        val released = replaceArmy(state, own.copy(status = WorldArmyStatus.ENGAGED)).copy(commanderAssignments = engagedAssignments, world = state.world.copy(
            armies = state.world.armies.map { if (it.id == own.id) it.copy(status = WorldArmyStatus.ENGAGED) else it },
            encounter = WorldEncounter(own.id, enemy.id, own.regionId, state.day)))
        val profile = when (enemy.factionId) { "verdant_compact" -> EnemyType.TAO_TEI; "copper_league" -> EnemyType.URUK; else -> EnemyType.ORC }
        val battle = BattleEngine.start(released, profile, Tactic.HOLD,
            listOf(BattleDeployment(own.commanderId, BattleSection.CENTER, own.units)),
            seed = (own.id.hashCode() xor enemy.id.hashCode() xor state.day), enemyStrength = enemy.total,
            terrain = BattleSection.entries.associateWith { section ->
                when (state.world.place(own.regionId)?.terrain) {
                    WorldTerrain.FOREST -> BattleTerrain.FOREST
                    WorldTerrain.HILLS -> BattleTerrain.HILL
                    WorldTerrain.MOUNTAIN -> BattleTerrain.PASS
                    WorldTerrain.RIVER -> if (section == BattleSection.CENTER) BattleTerrain.BRIDGE else BattleTerrain.RIVER
                    WorldTerrain.MARSH -> BattleTerrain.MUD
                    WorldTerrain.CITY -> BattleTerrain.STREET
                    else -> BattleTerrain.PLAIN
                }
            },
            rangedWeather = state.world.weather.at(own.regionId).rangedFactor,
            cavalryWeather = state.world.weather.at(own.regionId).cavalryFactor,
            seasonPenalty = if (state.world.weather.season == Season.WINTER) .8 else 1.0,
            enemyFactionName = state.world.faction(enemy.factionId)?.name,
            enemyFactionId = enemy.factionId, enemyArmyName = enemy.name, enemyUnits = enemy.units,
            location = state.world.place(own.regionId)?.name,
            ownMorale = own.morale, enemyMorale = enemy.morale,
            enemyFortification = state.world.place(own.regionId)?.let { if (it.ownerId == enemy.factionId) it.fortification else 0 } ?: 0,
            enemyExperience = state.world.enemyCommanders.firstOrNull { it.id == enemy.enemyCommanderId }?.experience ?: 0)
        if (battle.state.battleSession?.isActive != true) return result(state, battle.message)
        return battle
    }

    /** An invasion is a command to an existing enemy banner, never a second enemy force. */
    fun bindInvasion(state: GameState, migrating: Boolean = false): GameState {
        val invasion = state.invasion ?: return state
        if (!state.world.initialized) return state
        val boundId = invasion.worldArmyId ?: state.world.invasionArmyId
        val bound = state.world.armies.firstOrNull { it.id == boundId }
        if (bound != null && bound.status != WorldArmyStatus.DESTROYED) {
            if (migrating) return state
            if (!DiplomacyEngine.atWar(state, bound.factionId, PLAYER_FACTION)) return log(state.copy(invasion = null, world = state.world.copy(invasionArmyId = null)), "Invasion beendet", "Ein Friedensvertrag beendet den angekündigten Angriff.")
            if (bound.regionId != "keep" && (bound.status == WorldArmyStatus.HOLDING || !allowedRoute(state, bound.factionId, bound.route.drop(bound.routeIndex)))) {
                val revised = routeForFaction(state, bound.factionId, bound.regionId, "keep")
                if (revised.isEmpty()) return log(state.copy(invasion = null, world = state.world.copy(invasionArmyId = null)), "Invasionsweg unterbrochen", "${bound.name} erreicht die Mauer nicht: neutrale Grenzen blockieren alle zugänglichen Wege.")
                val moved = bound.copy(route = revised, routeIndex = 0, legProgress = 0, destinationId = "keep", status = WorldArmyStatus.MARCHING,
                    arrivalDay = maxOf(bound.preparationUntilDay ?: invasion.announcedDay + 6, state.day + travelDays(state, bound.copy(legProgress = 0), revised)))
                val replanned = log(replaceArmy(state, moved), "Invasion umgeleitet", "${bound.name} nimmt wegen geänderter Grenzzugänge einen anderen Weg; Prognose Tag ${moved.arrivalDay}.")
                return replanned.copy(invasion = invasion.copy(worldArmyId = moved.id, strength = moved.total, arrivalDay = moved.arrivalDay!!))
            }
            return state.copy(invasion = invasion.copy(worldArmyId = bound.id, strength = bound.total, arrivalDay = bound.arrivalDay ?: invasion.arrivalDay))
        }
        val preferred = when (invasion.enemy) { EnemyType.ORC -> "ash_covenant"; EnemyType.URUK -> "copper_league"; EnemyType.TAO_TEI -> "verdant_compact" }
        val candidates = state.world.armies.filter { it.factionId !in listOf(PLAYER_FACTION, NEUTRAL_FACTION) && it.status.isAway && it.total > 0 }
        val army = candidates.firstOrNull { it.factionId == preferred && (migrating || DiplomacyEngine.atWar(state, it.factionId, PLAYER_FACTION)) }
            ?: candidates.firstOrNull { DiplomacyEngine.atWar(state, it.factionId, PLAYER_FACTION) }
            ?: (if (migrating) candidates.firstOrNull { it.factionId == preferred } else null)
            ?: return state.copy(invasion = null, world = state.world.copy(invasionArmyId = null))
        val path = if (migrating) route(state.world, army.regionId, "keep") else routeForFaction(state, army.factionId, army.regionId, "keep")
        if (path.isEmpty()) return state.copy(invasion = null, world = state.world.copy(invasionArmyId = null))
        var next = state
        val units = if (migrating) listOf(UnitAllocation(army.units.first().type, invasion.strength)) else army.units
        val migratedStrength = units.sumOf { it.amount.toLong() }
        if (migrating) {
            val f = state.world.faction(army.factionId)
            if (f != null) next = next.copy(world = next.world.copy(factions = next.world.factions.map {
                if (it.id == f.id) it.copy(population = maxOf(it.population.toLong(), migratedStrength).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else it
            }))
            // Grandfather the provisions already committed by a pre-world save; no new player resources are granted.
        }
        val moving = army.copy(units = units, destinationId = "keep", route = path, routeIndex = 0, legProgress = 0,
            preparationUntilDay = if (migrating) invasion.arrivalDay else maxOf(invasion.arrivalDay, invasion.announcedDay + 6),
            arrivalDay = if (migrating) invasion.arrivalDay else maxOf(invasion.announcedDay + 6, invasion.arrivalDay, state.day + travelDays(state, army, path)),
            supplyFood = if (migrating) (migratedStrength * 14).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else army.supplyFood,
            status = if (migrating && state.battleSession?.isActive == true) WorldArmyStatus.ENGAGED else WorldArmyStatus.MARCHING)
        next = replaceArmy(next, moving)
        next = next.copy(world = next.world.copy(invasionArmyId = moving.id))
        return if (migrating) next else next.copy(invasion = invasion.copy(worldArmyId = moving.id, strength = moving.total, arrivalDay = moving.arrivalDay ?: invasion.arrivalDay))
    }

    fun reconcileInvasion(state: GameState): GameState {
        val id = state.world.invasionArmyId ?: return state
        val army = state.world.armies.firstOrNull { it.id == id && it.status == WorldArmyStatus.ENGAGED } ?: return state
        val battle = state.battleSession?.takeIf { !it.isActive } ?: return state
        val units = army.units.map { u -> u.copy(amount = if (army.total > 0) (u.amount.toLong() * battle.enemyRemaining / army.total).toInt() else 0) }.filter { it.amount > 0 }
        val capital = state.world.faction(army.factionId)?.capitalId ?: army.regionId
        val path = route(state.world, army.regionId, capital)
        val survivors = army.copy(units = units, destinationId = capital, route = path, routeIndex = 0, legProgress = 0,
            status = if (units.isEmpty()) WorldArmyStatus.DESTROYED else WorldArmyStatus.MARCHING,
            arrivalDay = state.day + travelDays(state, army, path))
        var next = reduceFactionPopulation(replaceArmy(state, survivors), army.factionId, (army.total - survivors.total).coerceAtLeast(0))
        next = updateNemesis(next, army.enemyCommanderId, battle.status != BattleStatus.VICTORY, true)
        return next.copy(world = next.world.copy(invasionArmyId = null))
    }

    fun reconcileBattle(state: GameState): GameState {
        val prepared = reconcileInvasion(state)
        val encounter = prepared.world.encounter ?: return prepared
        val battle = prepared.battleSession?.takeIf { !it.isActive } ?: return prepared
        val army = prepared.world.armies.firstOrNull { it.id == encounter.playerArmyId } ?: return prepared.copy(world = prepared.world.copy(encounter = null))
        val losses = battle.contingents.groupBy { it.type }.map { (type, groups) -> UnitAllocation(type, groups.sumOf { it.startSoldiers - it.soldiers }) }
        val units = army.units.mapNotNull { u -> val n = (u.amount - (losses.firstOrNull { it.type == u.type }?.amount ?: 0)).coerceAtLeast(0); if (n > 0) u.copy(amount = n) else null }
        var next = replaceArmy(prepared, army.copy(units = units, status = if (units.isEmpty()) WorldArmyStatus.DESTROYED else WorldArmyStatus.HOLDING, morale = if (battle.status == BattleStatus.VICTORY) 85 else 45))
        val enemy = next.world.armies.firstOrNull { it.id == encounter.enemyArmyId }
        if (enemy != null) {
            val remaining = battle.enemyRemaining
            val survivors = enemy.units.map { u -> u.copy(amount = if (enemy.total > 0) (u.amount.toLong() * remaining / enemy.total).toInt() else 0) }.filter { it.amount > 0 }
            next = replaceArmy(next, enemy.copy(units = survivors, status = if (remaining == 0) WorldArmyStatus.DESTROYED else WorldArmyStatus.HOLDING))
            next = reduceFactionPopulation(next, enemy.factionId, (enemy.total - survivors.sumOf { it.amount }).coerceAtLeast(0))
            next = updateNemesis(next, enemy.enemyCommanderId, battle.status != BattleStatus.VICTORY, true)
            val place = next.world.place(encounter.regionId)
            if (place?.ownerId == enemy.factionId) {
                next = next.copy(world = next.world.copy(places = next.world.places.map { if (it.id == place.id) it.copy(fortification = battle.enemyFortification) else it }))
                if (battle.status == BattleStatus.VICTORY && units.isNotEmpty()) next = changeOwnership(next, place.id, PLAYER_FACTION)
            }
        }
        return ArmyEngine.clampAssignments(next.copy(world = next.world.copy(encounter = null)))
    }

    private fun reduceFactionPopulation(state: GameState, factionId: String, losses: Int): GameState = state.copy(world = state.world.copy(factions = state.world.factions.map {
        if (it.id == factionId) it.copy(population = (it.population - losses).coerceAtLeast(0)) else it
    }))

    private fun changeOwnership(state: GameState, regionId: String, ownerId: String): GameState {
        val place = state.world.place(regionId) ?: return state
        if (place.ownerId == ownerId) return state
        val previousOwnerId = place.ownerId
        val wasOwned = previousOwnerId == PLAYER_FACTION
        val becomesOwned = ownerId == PLAYER_FACTION
        val oldRegion = state.regions.firstOrNull { it.id == regionId }
        val regions =
            if (oldRegion != null)
                state.regions.map {
                    if (it.id == regionId) it.copy(owned = becomesOwned) else it
                }
            else if (becomesOwned)
                state.regions + WorldRegion(regionId, place.name, RegionType.OWN, null, true)
            else state.regions
        val delta = (if (becomesOwned) 1 else 0) - (if (wasOwned) 1 else 0)
        val changed =
            state.copy(
                regions = regions,
                realm =
                    state.realm.copy(
                        territory =
                            (state.realm.territory.toLong() + delta)
                                .coerceIn(1, Int.MAX_VALUE.toLong())
                                .toInt()
                    ),
                world =
                    state.world.copy(
                        places =
                            state.world.places.map {
                                if (it.id == regionId) it.copy(ownerId = ownerId) else it
                            }
                    ),
            )
        return OccupationEngine.onOwnershipChanged(
            changed,
            regionId,
            previousOwnerId,
            ownerId,
        )
    }

    private fun updateNemesis(state: GameState, id: String?, won: Boolean, player: Boolean): GameState = state.copy(world = state.world.copy(enemyCommanders = state.world.enemyCommanders.map { c ->
        if (c.id != id) c else c.copy(experience = (c.experience + 15).coerceAtMost(10000), victories = c.victories + if (won) 1 else 0,
            defeats = c.defeats + if (won) 0 else 1, injuries = c.injuries + if (won) 0 else 1,
            rivalry = (c.rivalry + if (player) 15 else 0).coerceAtMost(100), revengeTarget = if (player && !won) PLAYER_FACTION else c.revengeTarget,
            epithet = if (!won) "die gezeichnete Klinge" else if (c.victories >= 2) "Grenzbezwinger" else c.epithet,
            rank = if (c.victories >= 2) "General" else c.rank)
    }))

    private fun storyTick(state: GameState): GameState {
        var next = state
        val due = state.world.stories.filter { !it.resolved && it.options.isEmpty() && it.nextDay <= state.day }
        due.forEach { story ->
            val continuation = if (story.key == "border_signal") story.copy(stage = 1, nextDay = state.day + 3,
                title = "Die Warnfeuer", text = "Das vermisste Grenzsignal führt zu einem Dorf voller Flüchtlinge. Schutz benötigt Vorräte; Händler bieten eine Eskorte an.",
                options = listOf("Schutz und 400 Nahrung senden", "Händlereskorte finanzieren (250 Gold)", "Nur beobachten"))
                else story.copy(resolved = true, stage = story.stage + 1)
            next = next.copy(world = next.world.copy(stories = next.world.stories.map { if (it.id == story.id) continuation else it }))
        }
        if (next.world.stories.none { !it.resolved } && state.day % 12 == 0) {
            val hungry = next.resources.food < next.homeArmySize * 3
            val key = if (hungry) "granary_crisis" else "border_signal"
            val story = WorldStory("story_${key}_${state.day}", key, 0, state.day, state.day + 2,
                if (hungry) "Leere Speicher" else "Ein verstummtes Grenzsignal",
                if (hungry) "Ein schlechter Erntetag bedroht die Stadt. Händler verkaufen Notvorräte; der Hof kann auch die Last verteilen." else "Aus Eichenfurt bleibt das erwartete Feuer aus. Die Wachen vermuten mehr als einen ausgefallenen Boten.",
                "village", if (hungry) listOf("500 Nahrung kaufen (300 Gold)", "Rationen teilen", "Krise aussitzen") else emptyList())
            next = next.copy(world = next.world.copy(stories = (next.world.stories + story).takeLast(40)))
            next = log(next, story.title, story.text)
        }
        return next
    }

    fun chooseStory(state: GameState, storyId: String, choice: Int): GameEngine.ActionResult {
        val story = state.world.stories.firstOrNull { it.id == storyId && !it.resolved && it.options.isNotEmpty() } ?: return result(state, "Diese Situation ist bereits beendet.")
        if (choice !in story.options.indices || state.battleSession?.isActive == true) return result(state, "Diese Entscheidung ist gerade nicht möglich.")
        var next = state
        val text: String
        if (story.key == "granary_crisis") {
            when (choice) {
                0 -> { if (state.resources.gold < 300) return result(state, "300 Gold benötigt."); next = next.copy(resources = EconomyEngine.add(next.resources.copy(gold = next.resources.gold - 300), Resources(0, 500, 0, 0, 0))); text = "Händler öffnen ihre Speicher. Der Kauf stabilisiert die Versorgung." }
                1 -> { next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction - 4).coerceAtLeast(0)), resources = EconomyEngine.add(next.resources, Resources(0, 250, 0, 0, 0))); text = "Die Haushalte teilen Reserven und verlangen eine gerechte Erntepolitik." }
                else -> { next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction - 10).coerceAtLeast(0))); text = "Die Not bleibt ungelöst. Die Bevölkerung verliert Vertrauen." }
            }
        } else {
            when (choice) {
                0 -> { if (state.resources.food < 400) return result(state, "400 Nahrung benötigt."); next = next.copy(resources = next.resources.copy(food = next.resources.food - 400), renown = (next.renown.toLong() + 20).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), realm = next.realm.copy(scoutingDays = maxOf(7, next.realm.scoutingDays))); text = "Die Flüchtlinge danken mit sicheren Grenzberichten. Aus dem Warnfeuer wird ein Schutzversprechen." }
                1 -> { if (state.resources.gold < 250) return result(state, "250 Gold benötigt."); next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 250), realm = next.realm.copy(tradeBonusDays = maxOf(7, next.realm.tradeBonusDays))); text = "Die Händler bringen Familien und Ware sicher zur Mauer. Der neue Handelsweg bleibt bestehen." }
                else -> { next = next.copy(realm = next.realm.copy(threat = (next.realm.threat + 5).coerceAtMost(100))); text = "Die verlassene Grenze wird unsicher. Ein späterer Feldzug wird die Folgen tragen." }
            }
        }
        next = next.copy(world = next.world.copy(stories = next.world.stories.map { if (it.id == story.id) it.copy(resolved = true, stage = it.stage + 1, choice = choice, text = text) else it }))
        return result(log(next, story.title, text), text)
    }

    /** Save boundary invariants; no repair or silent loss of valid campaign data. */
    fun validate(state: GameState) {
        val world = state.world
        if (!world.initialized) {
            require(world.places.isEmpty() && world.armies.isEmpty() && world.factions.isEmpty()) { "Uninitialized world contains live state" }
            return
        }
        fun <T> unique(rows: List<T>, id: (T) -> String) { require(rows.map(id).toSet().size == rows.size) { "Duplicate world ID" } }
        unique(world.places) { it.id }; unique(world.roads) { it.id }; unique(world.factions) { it.id }
        unique(world.armies) { it.id }; unique(world.enemyCommanders) { it.id }; unique(world.depots) { it.id }
        require(world.roads.sumOf { it.distance.toLong() } <= Int.MAX_VALUE) { "World road costs overflow" }
        require(world.knowledge.map { it.factionId }.distinct().size == world.knowledge.size)
        unique(world.convoys) { it.id }; unique(world.stories) { it.id }; unique(world.recruitments) { it.id }
        require(world.place("keep") != null && world.faction(PLAYER_FACTION) != null) { "World lacks canonical home" }
        require(world.lastTickDay in 0..state.day && world.nextArmyNumber > 0 && world.nextConvoyNumber > 0)
        val placeIds = world.places.map { it.id }.toSet()
        val factionIds = world.factions.map { it.id }.toSet()
        world.places.forEach { require(it.id.isNotBlank() && it.ownerId in factionIds && it.population >= 0 && it.fortification in 0..100 && it.prosperity in 0..100 && it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f) }
        world.roads.forEach { require(it.from in placeIds && it.to in placeIds && it.from != it.to && it.distance > 0 && it.quality in 0..100) }
        world.factions.forEach { require(it.capitalId in placeIds && it.gold >= 0 && it.food >= 0 && it.iron >= 0 && it.population >= 0 && it.aggression in 0..100 && it.buildings >= 0 && it.wars.all { id -> id in factionIds && id != it.id } && it.relations.all { (id, value) -> id in factionIds && value in -100..100 }) }
        world.enemyCommanders.forEach { require(it.factionId in factionIds && it.experience >= 0 && it.victories >= 0 && it.defeats >= 0 && it.injuries >= 0 && it.rivalry in 0..100 && it.rulerLoyalty in 0..100) }
        val missionReservations = mutableSetOf<Long>()
        world.armies.forEach { army ->
            require(army.factionId in factionIds && army.regionId in placeIds && army.supplyFood >= 0 && army.morale in 0..100 && army.legProgress >= 0 && army.lastMovedDay in 0..state.day && (army.preparationUntilDay == null || army.preparationUntilDay >= 0))
            require(army.units.all { it.amount > 0 } && army.units.map { it.type }.toSet().size == army.units.size && army.units.sumOf { it.amount.toLong() } <= Int.MAX_VALUE)
            require(army.route.all { it in placeIds } && (army.route.isEmpty() && army.routeIndex == 0 || army.routeIndex in army.route.indices))
            require(army.destinationId == null || army.destinationId in placeIds)
            require(army.commanderId == null || state.commanders.any { it.id == army.commanderId })
            require(army.enemyCommanderId == null || world.enemyCommanders.any { it.id == army.enemyCommanderId && it.factionId == army.factionId })
            if (army.missionId != null && army.status.isAway) {
                require(missionReservations.add(army.missionId)) { "Mission has duplicated world armies" }
                val mission = state.activeMissions.firstOrNull { it.id == army.missionId && it.status.isAway }
                require(mission != null && mission.units == army.units && army.factionId == PLAYER_FACTION && army.commanderId == mission.commanderId) { "Mission/world army diverged" }
            }
        }
        state.activeMissions.filter { it.status.isAway }.forEach { mission ->
            require(world.armies.count { it.missionId == mission.id && (it.status.isAway || it.status == WorldArmyStatus.HOME || it.status == WorldArmyStatus.DESTROYED) } == 1) { "Away mission lacks world identity" }
            require(mission.riskFactor.isFinite() && mission.riskFactor in .1..3.0 && mission.operationDaysRemaining >= 0 && mission.lastTickDay in 0..state.day)
        }
        UnitType.entries.forEach { type ->
            val reserved = state.activeMissions.filter { it.status.isAway }.sumOf { m -> m.units.filter { it.type == type }.sumOf { it.amount.toLong() } } +
                world.playerFieldArmies.sumOf { army -> army.units.filter { it.type == type }.sumOf { it.amount.toLong() } }
            require(reserved <= state.soldiers(type)) { "World reservations exceed player troops" }
        }
        world.recruitments.forEach { order -> require(order.factionId in factionIds && order.factionId != PLAYER_FACTION && order.amount > 0 && order.completionDay > 0 && world.armies.any { it.id == order.armyId && it.factionId == order.factionId }) }
        world.factions.filter { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }.forEach { faction ->
            val reserved = world.armies.filter { it.factionId == faction.id && it.status != WorldArmyStatus.DESTROYED }.sumOf { it.total.toLong() } + world.recruitments.filter { it.factionId == faction.id }.sumOf { it.amount.toLong() }
            require(reserved <= faction.population) { "AI army and recruitment exceed finite faction population" }
        }
        val playerCommanders = world.armies.filter { it.factionId == PLAYER_FACTION && (it.status.isAway || it.status == WorldArmyStatus.ENGAGED) }.mapNotNull { it.commanderId }
        require(playerCommanders.distinct().size == playerCommanders.size) { "Commander leads multiple expedition armies" }
        world.encounter?.let { encounter ->
            val engaged = world.armies.firstOrNull { it.id == encounter.playerArmyId }
            val session = state.battleSession
            if (engaged != null && session?.isActive == true) {
                require(session.contingents.all { it.commanderId == engaged.commanderId }) { "Battle commander differs from expedition" }
                UnitType.entries.forEach { type -> require(session.contingents.filter { it.type == type }.sumOf { it.startSoldiers.toLong() } == engaged.units.filter { it.type == type }.sumOf { it.amount.toLong() }) { "Battle deployment differs from expedition reservation" } }
            }
        }
        world.knowledge.forEach { intel ->
            require(intel.factionId in factionIds && intel.exploredRegions.all { it in placeIds } && intel.visibleRegions.all { it in placeIds })
            require(intel.observations.all { it.regionId in placeIds && it.factionId in factionIds && it.day in 0..state.day && it.minimum >= 0 && it.maximum >= it.minimum && (!it.exact || it.minimum == it.maximum) })
        }
        world.weather.regions.forEach { require(it.regionId in placeIds && it.untilDay >= 0) }
        world.depots.forEach { require(it.regionId in placeIds && it.factionId in factionIds && it.food in 0..it.capacity && it.capacity > 0) }
        world.convoys.forEach { require(it.factionId in factionIds && it.regionId in placeIds && it.food > 0 && it.route.all { id -> id in placeIds } && (it.route.isEmpty() && it.routeIndex == 0 || it.routeIndex in it.route.indices) && it.arrivalDay >= 0) }
        world.stories.forEach { require(it.stage >= 0 && it.startedDay in 0..state.day && it.nextDay >= 0 && (it.regionId == null || it.regionId in placeIds) && (it.choice == null || it.choice >= 0)) }
        world.encounter?.let { encounter -> require(encounter.regionId in placeIds && world.armies.any { it.id == encounter.playerArmyId && it.status == WorldArmyStatus.ENGAGED } && world.armies.any { it.id == encounter.enemyArmyId } && state.battleSession != null) }
    }

    private fun replaceArmy(state: GameState, army: WorldArmy): GameState = state.copy(world = state.world.copy(armies = state.world.armies.map { if (it.id == army.id) army else it }))
    private fun result(state: GameState, message: String) = GameEngine.ActionResult(state, message)
    private fun log(state: GameState, title: String, text: String): GameState = state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(2000))
}
