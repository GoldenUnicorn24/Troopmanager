package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class JournalCategory(val label: String) {
    OPEN("Offen"), PERSONAL("Persönlich"), MILITARY("Militär"), REALM("Reich"), DIPLOMACY("Diplomatie")
}

@Serializable
enum class GameDestination(val label: String) {
    COMMAND("Kommandozentrale"), DECISIONS("Offene Reichsentscheidungen"), CITY("Stadt"), MILITARY("Militär"), WORLD("Welt"), COURT("Hof"),
    RULERS("Herrscherpaar"), FAMILY("Familie"), CHARACTER("Charakter"), FRONTIER("Frontier / Mauer"),
    COUNCIL("Rat"), PALACE("Palast"), JOURNAL("Aufgabenjournal"), CHRONICLE("Chronik"),
    RESEARCH("Forschung"), MISSIONS("Missionen"), HOSPITAL("Lazarett")
}

@Serializable
data class JournalTask(
    val id: String,
    val title: String,
    val detail: String,
    val category: JournalCategory,
    val destination: GameDestination,
    val startedDay: Int,
    val dueDay: Int? = null,
    val closedDay: Int? = null,
    val outcome: String? = null,
)

@Serializable
data class QuestJournalState(
    val active: List<JournalTask> = emptyList(),
    val history: List<JournalTask> = emptyList(),
)
