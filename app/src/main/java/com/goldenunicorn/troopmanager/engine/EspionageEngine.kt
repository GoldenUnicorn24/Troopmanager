package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Finite missions never grant soldiers; intelligence and sabotage alter existing world state. */
object EspionageEngine {
    private fun busy(state: GameState) = state.battleSession?.isActive == true

    fun recruitAgent(state: GameState): GameEngine.ActionResult {
        if (busy(state)) return GameEngine.ActionResult(state, "Agenten nach der Schlacht anwerben.")
        if (state.espionage.agents.size >= 8) return GameEngine.ActionResult(state, "Maximal acht Agenten.")
        if (state.resources.gold < 300) return GameEngine.ActionResult(state, "Anwerbung kostet 300 Gold.")
        val id = state.espionage.nextId
        val names = listOf("Mira", "Tarin", "Liora", "Naren", "Sera", "Edan", "Veyla", "Corin")
        val agent = SpyAgent("agent_$id", names[((id - 1) % names.size).toInt()],
            (35 + state.realm.level(BuildingType.ACADEMY) * 3).coerceAtMost(70))
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - 300),
            espionage = state.espionage.copy(agents = state.espionage.agents + agent, nextId = id + 1)),
            "${agent.name} tritt dem Nachrichtendienst bei.")
    }

    fun startMission(state: GameState, agentId: String, kind: SpyMissionKind,
        targetFactionId: String, targetProvinceId: String? = null): GameEngine.ActionResult {
        val agent = state.espionage.agents.firstOrNull { it.id == agentId }
            ?: return GameEngine.ActionResult(state, "Agent nicht gefunden.")
        if (busy(state) || agent.capturedByFactionId != null || state.espionage.missions.any { it.agentId == agentId })
            return GameEngine.ActionResult(state, "Agent ist nicht verfügbar.")
        val target = state.world.faction(targetFactionId)
            ?: return GameEngine.ActionResult(state, "Zielreich ist unbekannt.")
        if ((kind == SpyMissionKind.COUNTERINTELLIGENCE) != (targetFactionId == PLAYER_FACTION))
            return GameEngine.ActionResult(state, "Gegenaufklärung gehört ins eigene Reich; andere Missionen benötigen ein fremdes Ziel.")
        val province = targetProvinceId?.let { state.world.place(it) }
        if (targetProvinceId != null && (province == null || province.ownerId != targetFactionId))
            return GameEngine.ActionResult(state, "Zielort gehört nicht zum Zielreich.")
        if (state.resources.gold < kind.cost) return GameEngine.ActionResult(state, "Mission kostet ${kind.cost} Gold.")
        val difficulty = (25 + target.buildings.coerceAtMost(10) * 3 +
            if (target.personality == FactionPersonality.PARANOID) 20 else 0).coerceAtMost(80)
        val id = state.espionage.nextId
        val mission = SpyMission("spy_mission_$id", agentId, kind, targetFactionId,
            province?.id ?: target.capitalId, state.day, state.day + kind.duration, difficulty)
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - kind.cost),
            espionage = state.espionage.copy(missions = state.espionage.missions + mission, nextId = id + 1)),
            "${agent.name}: ${kind.label}, Abschluss Tag ${mission.completesDay}.")
    }

    fun ransomAgent(state: GameState, agentId: String): GameEngine.ActionResult {
        val agent = state.espionage.agents.firstOrNull { it.id == agentId }
            ?: return GameEngine.ActionResult(state, "Agent nicht gefunden.")
        val captor = agent.capturedByFactionId ?: return GameEngine.ActionResult(state, "Agent ist frei.")
        if (state.resources.gold < 350) return GameEngine.ActionResult(state, "Auslösung kostet 350 Gold.")
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - 350),
            espionage = state.espionage.copy(agents = state.espionage.agents.map {
                if (it.id == agentId) it.copy(capturedByFactionId = null) else it }),
            world = state.world.copy(factions = state.world.factions.map {
                if (it.id == captor) it.copy(gold = (it.gold.toLong() + 350).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else it })),
            "${agent.name} wurde ausgelöst.")
    }

    /** A stable mission roll makes a saved campaign reproducible without reloading for free rerolls. */
    fun tick(state: GameState, roll: Double? = null): GameState {
        if (state.espionage.lastTickDay >= state.day) return state
        val completed = state.espionage.missions.filter { it.completesDay <= state.day }
        var next = state.copy(espionage = state.espionage.copy(lastTickDay = state.day,
            missions = state.espionage.missions.filter { it.completesDay > state.day }))
        completed.forEach { mission ->
            val agent = next.espionage.agents.firstOrNull { it.id == mission.agentId } ?: return@forEach
            val chance = ((55 + agent.skill - mission.difficulty + CharacterEngine.bonuses(next).counterintelligence / 2)
                .coerceIn(15, 95)) / 100.0
            val actualRoll = roll?.coerceIn(0.0, 1.0) ?: deterministicRoll(mission.id, mission.completesDay)
            val localOperation = mission.kind in listOf(SpyMissionKind.SABOTAGE_GATE, SpyMissionKind.DESTROY_STORES, SpyMissionKind.SPREAD_RUMORS)
            val validTarget = next.world.faction(mission.targetFactionId) != null &&
                (!localOperation || next.world.place(mission.targetProvinceId ?: "")?.ownerId == mission.targetFactionId)
            val success = validTarget && (mission.kind == SpyMissionKind.COUNTERINTELLIGENCE || actualRoll < chance)
            var report: String
            if (!validTarget) {
                report = "Das Ziel hat die Herrschaft gewechselt. ${agent.name} bricht die Operation ohne Angriff ab."
            } else if (success) {
                val outcome = applySuccess(next, mission)
                next = outcome.first
                report = outcome.second
            } else {
                val captured = actualRoll >= (chance + 0.22).coerceAtMost(0.98)
                next = next.copy(espionage = next.espionage.copy(agents = next.espionage.agents.map {
                    if (it.id == agent.id && captured) it.copy(capturedByFactionId = mission.targetFactionId) else it }))
                next = DiplomacyEngine.changeRelation(next, PLAYER_FACTION, mission.targetFactionId, -12, -15,
                    "Agent bei ${mission.kind.label} entdeckt.")
                report = if (captured) "${agent.name} wurde entdeckt und gefangen genommen." else
                    "${agent.name} entkommt, die Mission wurde entdeckt. Vertrauen sinkt."
            }
            next = next.copy(espionage = next.espionage.copy(agents = next.espionage.agents.map {
                if (it.id == agent.id) it.copy(experience = (it.experience + if (success) 15 else 4).coerceAtMost(100000),
                    skill = (it.skill + if (success) 2 else 1).coerceAtMost(95)) else it },
                reports = (next.espionage.reports + IntelligenceReport(state.day, mission.targetFactionId,
                    mission.kind, success, report)).takeLast(40)), chronicle = (next.chronicle +
                ChronicleEntry(state.day, "Nachrichtendienst: ${mission.kind.label}", report)).takeLast(2000))
        }
        return hostileOperations(next)
    }

    private fun deterministicRoll(id: String, day: Int): Double =
        ((id.fold(17L) { acc, c -> (acc * 31 + c.code) % 1000003 } + day * 97L) % 1000) / 1000.0

    private fun applySuccess(state: GameState, mission: SpyMission): Pair<GameState, String> {
        val target = state.world.faction(mission.targetFactionId)
            ?: return state to "Das Zielreich existiert nicht mehr."
        val province = state.world.place(mission.targetProvinceId ?: target.capitalId)
        return when (mission.kind) {
            SpyMissionKind.SCOUT_ARMY -> {
                val armies = state.world.armies.filter { it.factionId == target.id && it.total > 0 && it.status != WorldArmyStatus.DESTROYED }
                val knowledge = state.world.knowledgeFor(PLAYER_FACTION)
                val updated = knowledge.copy(exploredRegions = (knowledge.exploredRegions + armies.map { it.regionId }).distinct(),
                    observations = (knowledge.observations.filterNot { old -> armies.any { it.id == old.armyId } } +
                        armies.map { ArmyObservation(it.id, it.regionId, state.day, it.total, it.total, true, it.factionId, it.name) }))
                state.copy(world = state.world.copy(knowledge = state.world.knowledge.filterNot { it.factionId == PLAYER_FACTION } + updated)) to
                    "${target.name}: ${armies.size} Heere exakt aufgeklärt (${armies.sumOf { it.total.toLong() }} Soldaten)."
            }
            SpyMissionKind.MAP_REGION -> {
                val region = province?.id ?: return state to "Zielort nicht mehr vorhanden."
                val adjacent = state.world.roads.filter { it.connects(region) }.map { it.other(region) }
                val knowledge = state.world.knowledgeFor(PLAYER_FACTION)
                val updated = knowledge.copy(exploredRegions = (knowledge.exploredRegions + region + adjacent).distinct())
                state.copy(world = state.world.copy(knowledge = state.world.knowledge.filterNot { it.factionId == PLAYER_FACTION } + updated)) to
                    "${province.name} und ${adjacent.size} Verbindungen kartografiert."
            }
            SpyMissionKind.SABOTAGE_GATE -> {
                val location = province ?: return state to "Zielort nicht mehr vorhanden."
                val damage = minOf(25, location.fortification)
                state.copy(world = state.world.copy(places = state.world.places.map {
                    if (it.id == location.id) it.copy(fortification = (it.fortification - damage).coerceAtLeast(0)) else it })) to
                    "${location.name}: Befestigung um $damage geschwächt; künftige Belagerungen treffen diese Schäden."
            }
            SpyMissionKind.DESTROY_STORES -> {
                val lost = minOf(target.food, 600 + target.food / 10)
                state.copy(world = state.world.copy(factions = state.world.factions.map {
                    if (it.id == target.id) it.copy(food = it.food - lost) else it }, depots = state.world.depots.map {
                    if (it.factionId == target.id && it.regionId == province?.id) it.copy(food = it.food * 3 / 4) else it })) to
                    "${target.name} verliert $lost Nahrung; lokale Depots verlieren ein Viertel ihrer Vorräte."
            }
            SpyMissionKind.SPREAD_RUMORS -> {
                var next = state
                state.world.factions.filter { it.id != PLAYER_FACTION && it.id != target.id && it.id != NEUTRAL_FACTION }.forEach {
                    next = DiplomacyEngine.changeRelation(next, target.id, it.id, -8, -5, "Misstrauen nach Gerüchten am Hof.")
                }
                next.copy(world = next.world.copy(places = next.world.places.map {
                    if (it.id == province?.id) it.copy(prosperity = (it.prosperity - 8).coerceAtLeast(0)) else it })) to
                    "Gerüchte schwächen Vertrauen zwischen ${target.name} und seinen Nachbarn sowie den lokalen Handel."
            }
            SpyMissionKind.UNDERMINE_LOYALTY -> state.copy(world = state.world.copy(enemyCommanders =
                state.world.enemyCommanders.map { if (it.factionId == target.id) it.copy(rulerLoyalty =
                    (it.rulerLoyalty - 18).coerceAtLeast(0)) else it }, armies = state.world.armies.map {
                    if (it.factionId == target.id) it.copy(morale = (it.morale - 8).coerceAtLeast(0)) else it })) to
                "${target.name}: Herrscherloyalität der Kommandanten und Moral der vorhandenen Heere sinken."
            SpyMissionKind.STEAL_TREATIES -> {
                val treaties = state.diplomacy.treaties.filter { it.firstFactionId == target.id || it.secondFactionId == target.id }
                state.copy(espionage = state.espionage.copy(knownTreatyIds = (state.espionage.knownTreatyIds + treaties.map { it.id }).distinct())) to
                    "${treaties.size} Verträge von ${target.name} aufgedeckt: ${treaties.joinToString { it.kind.label }}."
            }
            SpyMissionKind.COUNTERINTELLIGENCE -> state.copy(espionage = state.espionage.copy(
                counterintelligenceUntilDay = maxOf(state.espionage.counterintelligenceUntilDay, state.day + 21)),
                society = state.society.copy(crime = (state.society.crime - 5).coerceAtLeast(0))) to
                "Gegenaufklärung schützt das Reich bis Tag ${state.day + 21}; kriminelle Netzwerke werden geschwächt."
        }
    }

    /** Hostile agents spend the same finite gold as the player and cannot bypass active defenses. */
    private fun hostileOperations(state: GameState): GameState {
        if (state.day % 14 != 0) return state
        val enemy = state.world.factions.filter { it.personality in listOf(FactionPersonality.CUNNING, FactionPersonality.PARANOID) &&
            it.gold >= 150 && DiplomacyEngine.atWar(state, it.id, PLAYER_FACTION) }.sortedBy { it.id }.firstOrNull() ?: return state
        val defense = state.city.security + CharacterEngine.bonuses(state).counterintelligence +
            (if (state.espionage.counterintelligenceUntilDay > state.day) 45 else 0)
        val blocked = defense >= 75
        val lost = if (blocked) 0 else minOf(250, state.resources.food)
        val report = if (blocked) "Eine bezahlte Sabotageoperation von ${enemy.name} wurde abgewehrt." else
            "Agenten von ${enemy.name} vernichten $lost Nahrung; bessere Sicherheit und Gegenaufklärung schützen Vorräte."
        return state.copy(resources = state.resources.copy(food = state.resources.food - lost),
            world = state.world.copy(factions = state.world.factions.map { if (it.id == enemy.id) it.copy(gold = it.gold - 150) else it }),
            espionage = state.espionage.copy(reports = (state.espionage.reports + IntelligenceReport(state.day,
                enemy.id, SpyMissionKind.COUNTERINTELLIGENCE, blocked, report)).takeLast(40)),
            chronicle = (state.chronicle + ChronicleEntry(state.day, "Fremde Agenten", report)).takeLast(2000))
    }
}
