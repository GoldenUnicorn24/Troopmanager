package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Validate additive domains without requiring them in an older payload. */
object V065StateValidation {
    fun validate(state: GameState) {
        val r = state.relationship
        require(r.personality.traits.size in 3..5 && r.personality.traits.distinct().size == r.personality.traits.size) { "Ungültige Persönlichkeit." }
        require(r.issues.map { it.id }.distinct().size == r.issues.size && r.issues.all {
            it.severity in 0..100 && it.startedDay in 0..state.day && it.ignoredCount >= 0
        }) { "Ungültige Beziehungsthemen." }
        require(r.arcs.map { it.id }.distinct().size == r.arcs.size && r.arcs.all {
            it.stage >= 0 && it.startedDay in 0..state.day && it.nextDay >= 0
        }) { "Ungültige persönliche Aufgaben." }
        require(r.delayedConsequences.map { it.id }.distinct().size == r.delayedConsequences.size && r.delayedConsequences.all { it.dueDay >= 0 }) { "Ungültige verzögerte Folgen." }
        val d = state.coRuler.delegation
        require(d.maxGoldPerAction in 0..500 && d.maxGoldPerDay in 0..500 && state.coRuler.goldSpentToday >= 0 && state.coRuler.regencyEfficiency in 0..100) { "Ungültige Regierungsbefugnisse." }
        state.dynasty.members.forEach { member ->
            require(listOf(member.stewardship, member.medicine, member.tactics, member.mentorBond).all { it in 0..100 }) { "Ungültige Ausbildungswerte." }
        }
        require(state.journal.active.map { it.id }.distinct().size == state.journal.active.size) { "Doppelte Journalaufgaben." }
        require((state.journal.active + state.journal.history).all { it.startedDay in 0..state.day && (it.closedDay == null || it.closedDay in it.startedDay..state.day) }) { "Ungültige Journalzeitpunkte." }
    }
}
