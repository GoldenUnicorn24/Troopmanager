package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** A live projection: missions, armies and health always outrank a saved visit/travel marker. */
object PresenceEngine {
    fun presence(state: GameState): PresenceState = PresenceState(
        player = person(state, true), companion = person(state, false), lastTickDay = state.day,
    )

    fun day(state: GameState): GameState = state.copy(presence = presence(state))

    private fun person(state: GameState, player: Boolean): PersonPresence {
        val saved = if (player) state.presence.player else state.presence.companion
        val condition = if (player) state.war.playerCondition else state.war.commanderConditions
            .firstOrNull { it.commanderId == COMPANION_COMMANDER_ID }?.status ?: CombatantStatus.ACTIVE
        val recovery = if (player) state.war.playerRecoveryDay else state.war.commanderConditions
            .firstOrNull { it.commanderId == COMPANION_COMMANDER_ID }?.untilDay ?: 0
        when (condition) {
            CombatantStatus.DEAD -> return PersonPresence(PresenceLocation.DEAD, "Verstorben", false)
            CombatantStatus.CAPTURED -> return PersonPresence(PresenceLocation.CAPTIVITY, "Eine Befreiung ist erforderlich", false)
            CombatantStatus.UNCONSCIOUS -> return PersonPresence(PresenceLocation.HOSPITAL, "Bewusstlos; Ruhe und Heilkunde", false, recovery.takeIf { it > 0 })
            CombatantStatus.WOUNDED -> return PersonPresence(PresenceLocation.HOSPITAL, "Verwundet; Genesung", false, recovery.takeIf { it > 0 })
            CombatantStatus.ACTIVE -> Unit
        }
        if (!player && !state.companion.met) return PersonPresence(PresenceLocation.TRAVEL, "Noch nicht begegnet", false)
        if (!player && state.court.characters.any { it.commanderId == COMPANION_COMMANDER_ID && !it.alive })
            return PersonPresence(PresenceLocation.DEAD, "Verstorben", false)
        val mission = state.activeMissions.firstOrNull {
            it.status.isAway && if (player) it.playerParticipates else COMPANION_COMMANDER_ID in it.allCommanderIds
        }
        if (mission != null) return PersonPresence(PresenceLocation.MISSION,
            "${mission.missionType.label} · ${if (mission.status == MissionStatus.RETURNING) "Rückkehr" else "im Einsatz"}",
            false, state.day + mission.remainingDays.coerceAtLeast(0), "mission:${mission.id}")
        val army = state.world.armies.firstOrNull {
            it.factionId == PLAYER_FACTION && it.missionId == null &&
                (it.status.isAway || it.status == WorldArmyStatus.ENGAGED) &&
                if (player) it.commanderId == null else it.commanderId == COMPANION_COMMANDER_ID
        }
        if (army != null) {
            val place = state.world.place(army.regionId)?.name ?: army.regionId
            val destination = army.destinationId?.let { state.world.place(it)?.name ?: it }
            val arrival = if (army.status == WorldArmyStatus.MARCHING && army.arrivalDay != null)
                " · Ziel ${destination ?: "Grenzland"}, Ankunft Tag ${army.arrivalDay}" else ""
            return PersonPresence(PresenceLocation.FIELD_ARMY, "${army.name} · $place$arrival", false,
                army.arrivalDay.takeIf { army.status == WorldArmyStatus.RETURNING }, "army:${army.id}")
        }
        val battle = state.battleSession
        if (battle?.isActive == true && (player || battle.contingents.any { it.commanderId == COMPANION_COMMANDER_ID }))
            return PersonPresence(PresenceLocation.FIELD_ARMY, "Laufende Schlacht", false, bindingId = "battle:${battle.seed}")
        if (saved.location == PresenceLocation.TRAVEL && saved.bindingId?.startsWith("travel:") == true &&
            (saved.returnDay ?: state.day) > state.day) return saved.copy(available = false)
        if (saved.location in listOf(PresenceLocation.CITY, PresenceLocation.WALL) && saved.bindingId == "visit")
            return saved.copy(available = true, returnDay = null)
        return PersonPresence(PresenceLocation.PALACE, state.realm.settlementName, true)
    }

    private fun incapacitated(state: GameState, player: Boolean): Boolean =
        if (player) state.war.playerCondition == CombatantStatus.UNCONSCIOUS else
            state.war.commanderConditions.any { it.commanderId == COMPANION_COMMANDER_ID && it.status == CombatantStatus.UNCONSCIOUS }

    fun sharedActivityBlocker(state: GameState, allowHospitalVisit: Boolean = false): String? {
        if (!state.companion.met) return "Ihr habt euch noch nicht getroffen."
        val p = presence(state)
        if (p.player.location in listOf(PresenceLocation.CAPTIVITY, PresenceLocation.DEAD) ||
            p.companion.location in listOf(PresenceLocation.CAPTIVITY, PresenceLocation.DEAD))
            return "Gefangenschaft oder Tod verhindert eine gemeinsame Begegnung."
        if (state.battleSession?.isActive == true) return "Das Gespräch wartet bis nach der Schlacht."
        if (allowHospitalVisit) {
            val patient = when {
                p.player.location == PresenceLocation.HOSPITAL && p.companion.available -> true
                p.companion.location == PresenceLocation.HOSPITAL && p.player.available -> false
                else -> null
            }
            if (patient != null) return if (incapacitated(state, patient)) "Die verletzte Person ist bewusstlos und braucht Ruhe." else null
        }
        if (!p.player.available) return "${state.player.name} ist ${p.player.location.label.lowercase()}: ${p.player.detail}."
        if (!p.companion.available) return "${state.companion.name} ist ${p.companion.location.label.lowercase()}: ${p.companion.detail}."
        if (p.player.location != p.companion.location) return "Ihr seid an verschiedenen Orten: ${p.player.location.label} und ${p.companion.location.label}."
        return null
    }

    /** Short local visits; military bindings cannot be dismissed by changing this marker. */
    fun visit(state: GameState, location: PresenceLocation, together: Boolean = false): GameEngine.ActionResult {
        if (location !in listOf(PresenceLocation.PALACE, PresenceLocation.CITY, PresenceLocation.WALL))
            return GameEngine.ActionResult(state, "Dieser Ort benötigt eine Mission, Genesung oder Reise.")
        val p = presence(state)
        if (!p.player.available) return GameEngine.ActionResult(state, "Du bist derzeit ${p.player.location.label.lowercase()} gebunden.")
        if (together) sharedActivityBlocker(state)?.let { return GameEngine.ActionResult(state, it) }
        val marker = PersonPresence(location, state.realm.settlementName, true, bindingId = "visit")
        return GameEngine.ActionResult(state.copy(presence = p.copy(player = marker, companion = if (together) marker else p.companion)),
            "${if (together) "Ihr besucht" else "Du besuchst"} ${location.label}.")
    }

    fun travel(state: GameState, together: Boolean = false, days: Int = 3): GameEngine.ActionResult {
        if (days !in 1..7) return GameEngine.ActionResult(state, "Reisen dauern einen bis sieben Tage.")
        val p = presence(state)
        if (!p.player.available) return GameEngine.ActionResult(state, "Du bist derzeit gebunden und kannst keine Reise beginnen.")
        if (together) sharedActivityBlocker(state)?.let { return GameEngine.ActionResult(state, it) }
        val cost = days * if (together) 40 else 25
        if (state.resources.gold < cost) return GameEngine.ActionResult(state, "Die Reise benötigt $cost Gold.")
        val marker = PersonPresence(PresenceLocation.TRAVEL, "Reise durch die Grenzlande", false,
            state.day + days, "travel:${state.day}")
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost),
            presence = p.copy(player = marker, companion = if (together) marker else p.companion)),
            "Rückkehr an Tag ${marker.returnDay}; die Regierung bleibt am Hof.")
    }
}
