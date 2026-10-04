package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Roles, field decisions and local reputation extend the existing physical expedition lifecycle. */
object MissionExperienceEngine {
    private data class Leader(val name: String, val command: Int, val tactics: Int, val bow: Int, val diplomacy: Int)
    private fun leaders(state: GameState, ids: List<Long>, player: Boolean) = buildList {
        if (player) add(Leader(state.player.name, state.player.leadership, state.player.tactics, state.player.bow, state.player.diplomacy))
        ids.distinct().forEach { id -> state.commanders.firstOrNull { it.id == id }?.let {
            add(Leader(it.name, it.leadership, it.tactics, it.bow, it.diplomacy)) } }
    }
    fun roleLabels(state: GameState, ids: List<Long>, player: Boolean): List<String> =
        leaders(state, ids, player).mapIndexed { index, leader -> "${leader.name}: ${when(index) {
            0 -> "Kommandeur · Führung ${leader.command}"; 1 -> "Vorhut · Taktik ${leader.tactics}, Bogen ${leader.bow}"
            else -> "Diplomat / Quartiermeister · Diplomatie ${leader.diplomacy}" }}" }
    fun roleFactor(state: GameState, ids: List<Long>, player: Boolean): Double {
        val leaders = leaders(state, ids, player)
        return 1 + (leaders.firstOrNull()?.command ?: 0) / 1000.0 +
            ((leaders.getOrNull(1)?.tactics ?: 0) + (leaders.getOrNull(1)?.bow ?: 0)) / 1600.0 +
            (leaders.getOrNull(2)?.diplomacy ?: 0) / 1200.0
    }
    fun supplyFactor(state: GameState, ids: List<Long>, player: Boolean): Double =
        (1 - (leaders(state, ids, player).getOrNull(2)?.diplomacy ?: 0) / 1000.0).coerceIn(.85, 1.0)

    fun offer(mission: ActiveMission): ActiveMission {
        if (mission.experienceVersion < 2 || mission.regionId == null || mission.phase != MissionPhase.OPERATING || mission.pendingDecision != null) return mission
        if (!mission.operationDecisionMade) return mission.copy(pendingDecision = MissionDecision(
            "Am Einsatzort", "Die Vorhut prüft Gelände und Feindkontakte. Erkundung kostet einen Versorgungstag, ein schneller Angriff bringt mehr Beute bei höherem Risiko; lokale Absprachen kosten Gold.",
            listOf("Erst erkunden · +1 Tag, Risiko −18 %", "Schnell handeln · Risiko +12 %, Beute +15 %", "Lokale Absprachen · 120 Gold, Risiko −10 %"), MissionDecisionKind.OPERATION))
        if (!mission.extractionDecisionMade && mission.remainingDays <= 1 && mission.missionType in listOf(MissionType.HUNT, MissionType.BANDITS))
            return mission.copy(pendingDecision = MissionDecision("Letzter Einsatzabschnitt",
                "Die Expedition kann zusätzliche Beute verfolgen oder den Heimweg sichern. Verletztenversorgung benötigt Zeit und Vorräte.",
                listOf("Verwundete sichern · +1 Tag, Risiko −15 %", "Beute verfolgen · Risiko +15 %, Beute +15 %", "Rückweg sichern · Beute −20 %, örtlicher Ruf +2"), MissionDecisionKind.EXTRACTION))
        return mission
    }

    fun choose(state: GameState, mission: ActiveMission, choice: Int): GameEngine.ActionResult {
        val kind = mission.pendingDecision?.kind ?: return GameEngine.ActionResult(state, "Keine Einsatzentscheidung offen.")
        val gold = if (kind == MissionDecisionKind.OPERATION && choice == 2) 120 else 0
        if (state.resources.gold < gold) return GameEngine.ActionResult(state, "$gold Gold benötigt.")
        val delay = if (choice == 0) 1 else 0
        val risk = if (kind == MissionDecisionKind.OPERATION) listOf(.82, 1.12, .90)[choice] else listOf(.85, 1.15, .90)[choice]
        val reward = if (choice == 1) 1.15 else if (kind == MissionDecisionKind.EXTRACTION && choice == 2) .80 else 1.0
        val updated = mission.copy(pendingDecision = null, operationDecisionMade = mission.operationDecisionMade || kind == MissionDecisionKind.OPERATION,
            extractionDecisionMade = mission.extractionDecisionMade || kind == MissionDecisionKind.EXTRACTION,
            remainingDays = mission.remainingDays + delay, operationDaysRemaining = mission.operationDaysRemaining + delay,
            riskFactor = (mission.riskFactor * risk).coerceIn(.4, 2.0), rewardFactor = (mission.rewardFactor * reward).coerceIn(.5, 1.5))
        var next = state.copy(resources = state.resources.copy(gold = state.resources.gold - gold),
            activeMissions = state.activeMissions.map { if (it.id == mission.id) updated else it })
        if (choice == 0 && kind == MissionDecisionKind.OPERATION) next = reveal(next, mission.regionId)
        if (kind == MissionDecisionKind.EXTRACTION && choice == 2) next = reputation(next, mission.regionId, 2)
        return GameEngine.ActionResult(next, "${mission.pendingDecision!!.options[choice]}. Das Heer bleibt am tatsächlichen Einsatzort; Vorräte werden täglich verbraucht.")
    }

    fun completed(state: GameState, mission: ActiveMission, success: Boolean): GameState {
        var next = reputation(state, mission.regionId, if (success) 3 else -2)
        if (success && mission.missionType == MissionType.SCOUT) next = reveal(next, mission.regionId)
        return next
    }
    private fun reputation(state: GameState, regionId: String?, delta: Int): GameState {
        if (regionId == null || state.world.place(regionId) == null) return state
        return state.copy(world = state.world.copy(regionReputation = state.world.regionReputation +
            (regionId to ((state.world.regionReputation[regionId] ?: 0) + delta).coerceIn(-50, 50))))
    }
    private fun reveal(state: GameState, regionId: String?): GameState {
        if (regionId == null || state.world.place(regionId) == null) return state
        val old = state.world.knowledgeFor(PLAYER_FACTION)
        val areas = (listOf(regionId) + state.world.roads.filter { it.connects(regionId) }.map { it.other(regionId) }).distinct()
        val armies = state.world.armies.filter { it.regionId in areas && it.status.isAway }
        val intel = old.copy(exploredRegions = (old.exploredRegions + areas).distinct(),
            visibleRegions = (old.visibleRegions + areas).distinct(), observations = old.observations.filterNot { it.armyId in armies.map { a -> a.id } } +
                armies.map { ArmyObservation(it.id, it.regionId, state.day, it.total, it.total, true, it.factionId, it.name) })
        return state.copy(world = state.world.copy(knowledge = state.world.knowledge.filterNot { it.factionId == PLAYER_FACTION } + intel))
    }
}
