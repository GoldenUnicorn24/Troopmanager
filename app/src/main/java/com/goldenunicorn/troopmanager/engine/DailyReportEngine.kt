package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object DailyReportEngine {
    fun build(before: GameState, after: GameState): DailyReport {
        val rows = mutableListOf<DailyReportEntry>()

        fun add(
            category: ReportCategory,
            title: String,
            detail: String,
            important: Boolean = false,
        ) {
            rows += DailyReportEntry(category, title, detail, important)
        }

        val gold = after.resources.gold - before.resources.gold
        val food = after.resources.food - before.resources.food
        val wood = after.resources.wood - before.resources.wood
        val iron = after.resources.iron - before.resources.iron
        if (gold != 0 || food != 0 || wood != 0 || iron != 0)
            add(
                ReportCategory.ECONOMY,
                "Tagesbilanz",
                "Gold ${signed(gold)} · Nahrung ${signed(food)} · Holz ${signed(wood)} · Eisen ${signed(iron)}",
            )

        val completedBuildings =
            after.city.lastCompletedBuildings.filter { it !in before.city.lastCompletedBuildings }
        if (completedBuildings.isNotEmpty())
            add(
                ReportCategory.CITY,
                "Bau abgeschlossen",
                completedBuildings.joinToString { it.label },
                true,
            )

        val beforeOrders = before.trainingQueue.associateBy { it.id }
        val finishedTraining =
            before.trainingQueue.filter { old ->
                after.trainingQueue.none { it.id == old.id }
            }
        val finishedCount = finishedTraining.sumOf { it.amount }
        if (finishedCount > 0)
            add(
                ReportCategory.MILITARY,
                "Ausbildung abgeschlossen",
                "$finishedCount Soldaten sind einsatzbereit.",
                true,
            )

        val woundedBefore = before.war.wounded.sumOf { it.soldiers }
        val woundedAfter = after.war.wounded.sumOf { it.soldiers }
        val woundedDelta = woundedAfter - woundedBefore
        if (woundedDelta > 0)
            add(
                ReportCategory.MILITARY,
                "Neue Verwundete",
                "$woundedDelta Soldaten befinden sich neu in Behandlung.",
                true,
            )
        else if (woundedDelta < 0)
            add(
                ReportCategory.MILITARY,
                "Lazarett",
                "${-woundedDelta} Soldaten sind wieder einsatzbereit.",
            )

        val newMissionReports =
            after.activeMissions.filter { mission ->
                !mission.status.isAway &&
                    before.activeMissions.firstOrNull { it.id == mission.id }?.status?.isAway == true
            }
        newMissionReports.forEach {
            add(
                ReportCategory.MILITARY,
                "${it.missionType.label} beendet",
                "${it.readyReturned} einsatzbereit · ${it.reportedWounded} verwundet · ${it.reportedDead} gefallen.",
                it.reportedDead > 0 || it.reportedWounded > 0,
            )
        }

        if (before.research.active != null && after.research.active == null)
            add(
                ReportCategory.CITY,
                "Forschung abgeschlossen",
                before.research.active.tech.label,
                true,
            )
        else
            after.research.active?.let {
                add(
                    ReportCategory.CITY,
                    "Forschung",
                    "${it.tech.label} · noch ${it.remainingDays} Tage",
                )
            }

        if (after.invasion != null && before.invasion == null)
            add(
                ReportCategory.WARNING,
                "Invasion angekündigt",
                "Ankunft Tag ${after.invasion.arrivalDay} · Stärke ${after.invasion.strength}.",
                true,
            )

        if (after.commanderEvents.pending != null && before.commanderEvents.pending == null)
            add(
                ReportCategory.COURT,
                after.commanderEvents.pending.title,
                after.commanderEvents.pending.text,
                true,
            )

        if (after.pendingRealmEvent != null && before.pendingRealmEvent == null)
            add(
                ReportCategory.CITY,
                after.pendingRealmEvent.title,
                after.pendingRealmEvent.category,
                true,
            )

        val newTreaties =
            after.diplomacy.treaties.filter { treaty ->
                before.diplomacy.treaties.none { it.id == treaty.id }
            }
        newTreaties.forEach {
            add(
                ReportCategory.DIPLOMACY,
                "Neuer Vertrag",
                it.kind.label,
                true,
            )
        }

        if (rows.isEmpty())
            add(
                ReportCategory.ECONOMY,
                "Ruhiger Tag",
                "Keine besonderen Meldungen. Wirtschaft und Verwaltung arbeiten planmäßig.",
            )

        return DailyReport(after.day, rows.take(16))
    }

    private fun signed(value: Int): String =
        if (value >= 0) "+$value" else value.toString()
}
