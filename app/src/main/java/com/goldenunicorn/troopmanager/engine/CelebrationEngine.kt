package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Queue of big moments shown as full-screen celebrations: titles, milestones, heroines, acts, bosses, wedding, coronation. */
object CelebrationEngine {
    private const val MAX = 6
    private val provinceMilestones = setOf(1, 5, 10, 15, 20, 25, 30, 35, 42)

    fun add(state: GameState, kind: CelebrationKind, title: String, text: String): GameState {
        if (state.crown.celebrations.any { it.title == title && it.day == state.day }) return state
        val list = (state.crown.celebrations + Celebration(kind, title, text, state.day)).takeLast(MAX)
        return state.copy(crown = state.crown.copy(celebrations = list))
    }

    fun dismiss(state: GameState): GameState =
        state.copy(crown = state.crown.copy(celebrations = state.crown.celebrations.drop(1)))

    /** Called after a province joins the crown. */
    fun provinceMilestone(state: GameState): GameState {
        val owned = state.crown.ownedProvinces.size
        if (owned !in provinceMilestones) return state
        val title = if (owned == 1) "Die erste Kronprovinz" else if (owned == 42) "Alle Kronlande vereint" else "$owned Kronprovinzen"
        val text = if (owned == 1) "Dein Reich wächst über die Grenzfeste hinaus. Neue Untertanen, neues Gold, neue Rekruten."
            else if (owned == 42) "Von der Grenzmark bis zum Hordenland weht dein Banner. Kein Herrscher vor dir hat das erreicht."
            else "${fmt(state.crown.subjects)} Untertanen leben jetzt unter deiner Krone."
        return add(state, CelebrationKind.MILESTONE, title, text)
    }

    /** Title changes only celebrate a rise on the ladder. */
    fun titleChange(before: String, state: GameState): GameState {
        if (before == state.title) return state
        val ladder = CrownCatalog.titleLadder.map { it.title }
        val old = ladder.indexOf(before)
        val now = ladder.indexOf(state.title)
        if (old < 0 || now <= old) return state
        return add(state, CelebrationKind.TITLE, state.title, "Ab heute nennt man ${state.player.name} „${state.title}“. Neue Edikte und größere Ziele stehen dir offen.")
    }

    private fun fmt(value: Long): String = String.format(java.util.Locale.GERMANY, "%,d", value)
}
