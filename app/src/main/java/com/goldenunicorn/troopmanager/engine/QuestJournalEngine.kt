package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** One derived list of actionable campaign work; a completed task retains its original stable id. */
object QuestJournalEngine {
    fun tasks(state: GameState): List<JournalTask> {
        val tasks = mutableListOf<JournalTask>()
        fun add(id: String, title: String, detail: String, category: JournalCategory,
                destination: GameDestination, due: Int? = null) {
            val started = state.journal.active.firstOrNull { it.id == id }?.startedDay ?: state.day
            tasks += JournalTask(id, title, detail, category, destination, started, due)
        }
        state.relationship.pendingEvent?.let {
            add("relationship:${it.key}:${it.createdDay}", it.title, "${state.companion.name} möchte sprechen.", JournalCategory.PERSONAL, GameDestination.RULERS)
        }
        state.commanderEvents.pending?.let {
            add("commander:${it.id}", it.title, it.text, JournalCategory.OPEN, GameDestination.COURT, it.expiresDay)
        }
        state.society.story.pending?.let {
            add("story:${it.id}", it.title, it.text, JournalCategory.OPEN, GameDestination.DECISIONS, it.expiresDay)
        }
        state.pendingRealmEvent?.let {
            add("realm:${it.key}", it.title, it.category, JournalCategory.OPEN, GameDestination.DECISIONS)
        }
        state.invasion?.let {
            val estimate = state.invasionStrengthEstimate()
            add("invasion:${it.arrivalDay}:${it.enemy}", "${it.enemy.label} nähert sich", "Ankunft Tag ${it.arrivalDay} · $estimate",
                JournalCategory.MILITARY, GameDestination.FRONTIER, it.arrivalDay)
        }
        state.frontier.hordes.filter { it.discovered && !it.id.startsWith("invasion-") &&
            (it.worldArmyId == null || it.worldArmyId != state.invasion?.worldArmyId) }.forEach {
            add("horde:${it.id}", it.name, "${it.estimatedStrengthLabel} · Ziel ${it.target}", JournalCategory.MILITARY,
                GameDestination.FRONTIER, state.day + it.daysToArrival)
        }
        state.frontier.patrol?.let {
            val due = state.day + it.daysLeft
            add("patrol:$due", "Grenzpatrouille unterwegs", "${it.soldiers} Soldaten sichern die Grenze.",
                JournalCategory.MILITARY, GameDestination.FRONTIER, due)
        }
        state.frontier.weapons.filter { it.daysRemaining > 0 }.forEach {
            val due = state.day + it.daysRemaining
            add("wall-weapon:${it.type}:$due", "${it.type.label} errichten", "${it.daysRemaining} Tage Bauzeit", JournalCategory.MILITARY,
                GameDestination.FRONTIER, due)
        }
        state.frontier.designs.filter { it.trainingAmount > 0 }.forEach {
            val due = state.day + it.trainingDaysLeft
            add("unit-training:${it.id}:$due", "${it.name} ausbilden", "${it.trainingAmount} Soldaten · ${it.trainingDaysLeft} Tage",
                JournalCategory.MILITARY, GameDestination.FRONTIER, due)
        }
        state.activeMissions.filter { it.status.isAway }.forEach {
            add("mission:${it.id}", it.missionType.label, "${it.total} Soldaten · ${it.remainingDays} Tage${if (it.pendingDecision != null) " · Entscheidung offen" else ""}",
                JournalCategory.MILITARY, GameDestination.MISSIONS, state.day + it.remainingDays)
        }
        state.city.constructionQueue.forEach {
            add("build:${it.id}", "${it.type.label} ausbauen", "Stufe ${it.targetLevel} · ${it.daysRemaining} Tage", JournalCategory.REALM,
                GameDestination.CITY, state.day + it.daysRemaining)
        }
        state.research.active?.let {
            add("research:${it.tech}", it.tech.label, "${it.remainingDays} Tage Forschung", JournalCategory.REALM,
                GameDestination.RESEARCH, state.day + it.remainingDays)
        }
        state.occupations.filter { it.unrest >= 50 }.forEach {
            add("occupation:${it.regionId}", "Besatzung befrieden", "${state.world.place(it.regionId)?.name ?: it.regionId} · Unruhe ${it.unrest} %",
                JournalCategory.REALM, GameDestination.WORLD)
        }
        state.relationship.arcs.filterNot { it.completed }.forEach {
            val title = RelationshipContentCatalog.arcs.firstOrNull { arc -> arc.id == it.id }?.title ?: "Persönlicher Weg"
            add("arc:${it.id}:${it.stage}", title, "Station ${it.stage + 1} · nächster Schritt ab Tag ${it.nextDay}",
                JournalCategory.PERSONAL, GameDestination.RULERS)
        }
        state.dynasty.pendingFamilyEvent?.let {
            add("family:${it.id}", it.title, it.text, JournalCategory.PERSONAL, GameDestination.FAMILY)
        }
        CoRulerEngine.councilCases(state).forEach {
            add("council:${it.id}", it.title, it.situation, JournalCategory.OPEN, GameDestination.COUNCIL)
        }
        state.frontier.reinforcements.forEach {
            add("reinforcement:${it.id}", "Hilfe aus ${it.people.label}", "${it.amount} Soldaten unterwegs · Ankunft Tag ${it.arrivalDay}",
                JournalCategory.DIPLOMACY, GameDestination.FRONTIER, it.arrivalDay)
        }
        state.diplomacy.proposals.filter { it.status == ProposalStatus.COUNTER_OFFER }.forEach {
            add("proposal:${it.id}", "${it.kind.label}: Gegenangebot", it.explanation,
                JournalCategory.DIPLOMACY, GameDestination.WORLD, it.expiresDay)
        }
        return tasks.distinctBy { it.id }.sortedWith(compareBy<JournalTask> { it.dueDay ?: Int.MAX_VALUE }.thenBy { it.id })
    }

    fun refresh(state: GameState): GameState {
        val active = tasks(state)
        val ids = active.map { it.id }.toSet()
        val closed = state.journal.active.filter { it.id !in ids }.map { task ->
            val outcome = when {
                task.id.startsWith("mission:") -> state.activeMissions.firstOrNull { "mission:${it.id}" == task.id }?.outcome?.label ?: "Beendet"
                task.id.startsWith("research:") -> "Forschung abgeschlossen"
                task.id.startsWith("build:") -> "Bau abgeschlossen"
                task.id.startsWith("reinforcement:") -> "Verstärkung eingetroffen"
                task.id.startsWith("arc:") -> "Station bearbeitet"
                task.id.startsWith("invasion:") -> "Bedrohung beendet"
                task.id.startsWith("horde:") -> "Grenzbedrohung beendet"
                task.id.startsWith("wall-weapon:") -> "Mauerwaffe fertiggestellt"
                task.id.startsWith("unit-training:") -> "Ausbildung abgeschlossen"
                task.id.startsWith("patrol:") -> "Patrouille beendet"
                task.dueDay != null && task.dueDay <= state.day -> "Frist beendet"
                else -> "Entscheidung bearbeitet"
            }
            task.copy(closedDay = state.day, outcome = outcome)
        }
        return state.copy(journal = state.journal.copy(active = active, history = (state.journal.history + closed).takeLast(300)))
    }
}
