package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object DailyReportEngine {
    fun recordAction(before: GameState, after: GameState): GameState {
        if (before.day != after.day) return after
        val reports = build(before, after).entries.filter { it.important }
        if (reports.isEmpty()) return after
        return after.copy(dailyReport = after.dailyReport.copy(day = after.day,
            entries = (reports + after.dailyReport.entries).distinct().take(32)))
    }

    fun build(before: GameState, after: GameState): DailyReport {
        val rows = mutableListOf<DailyReportEntry>()

        fun add(
            category: ReportCategory,
            title: String,
            detail: String,
            important: Boolean = false,
            destination: GameDestination? = null,
        ) {
            rows += DailyReportEntry(category, title, detail, important, destination)
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
                "Ankunft Tag ${after.invasion.arrivalDay} · ${after.invasionStrengthEstimate()}.",
                true,
                GameDestination.FRONTIER,
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

        after.campaign.pendingDecision
            ?.takeIf { it.id != before.campaign.pendingDecision?.id }
            ?.let {
                add(
                    ReportCategory.WARNING,
                    it.title,
                    "${it.text} · Entscheidung bis Tag ${it.expiresDay}.",
                    true,
                    GameDestination.DECISIONS,
                )
            }

        if (after.campaign.pressure >= 65 && before.campaign.pressure < 65)
            add(
                ReportCategory.WARNING,
                "Der Druck auf das Reich steigt",
                "Kampagnendruck ${after.campaign.pressure}/100 · Momentum ${after.campaign.momentum}/100. Prioritäten im Kommandobereich prüfen.",
                true,
                GameDestination.DECISIONS,
            )
        else if (after.campaign.momentum >= 75 && before.campaign.momentum < 75)
            add(
                ReportCategory.COURT,
                "Das Reich hat Initiative",
                "Momentum ${after.campaign.momentum}/100 · Serie ${after.campaign.streak} Tage · Schwerpunkt ${after.campaign.focus.label}.",
                false,
                GameDestination.DECISIONS,
            )

        val newOccupations =
            after.occupations.filter { occupation ->
                before.occupations.none { it.regionId == occupation.regionId }
            }
        newOccupations.forEach { occupation ->
            add(
                ReportCategory.WORLD,
                "Neues Besatzungsgebiet",
                "${after.world.place(occupation.regionId)?.name ?: occupation.regionId} · Unruhe ${occupation.unrest} %",
                true,
            )
        }
        after.occupations.filter { it.unrest >= 75 }.forEach { occupation ->
            add(
                ReportCategory.WARNING,
                "Hohe Unruhe",
                "${after.world.place(occupation.regionId)?.name ?: occupation.regionId}: ${occupation.unrest} % · Garnison oder Politik anpassen.",
                true,
            )
        }
        if (after.occupations.size < before.occupations.size) {
            val integrated =
                before.occupations.filter { old ->
                    after.occupations.none { it.regionId == old.regionId } &&
                        after.world.place(old.regionId)?.ownerId == PLAYER_FACTION
                }
            integrated.forEach {
                add(
                    ReportCategory.WORLD,
                    "Gebiet integriert",
                    after.world.place(it.regionId)?.name ?: it.regionId,
                    true,
                )
            }
        }

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

        after.relationship.pendingEvent?.takeIf { it.key != before.relationship.pendingEvent?.key }?.let {
            add(ReportCategory.RULERS, "${after.companion.name} möchte sprechen", it.title, true, GameDestination.RULERS)
        }
        after.coRuler.decisions.filter { it.day == after.day && it !in before.coRuler.decisions }.forEach {
            add(ReportCategory.RULERS, "${it.actor}: ${it.title}", "${it.choice} · ${it.reason}${if (it.goldSpent > 0) " · ${it.goldSpent} Gold" else ""}",
                true, GameDestination.COUNCIL)
        }
        if (after.companion.met) {
            val presence = PresenceEngine.presence(after)
            val previous = PresenceEngine.presence(before)
            if (presence.player != previous.player || presence.companion != previous.companion)
                add(ReportCategory.RULERS, "Aufenthalt & Regierung",
                    "${after.player.name}: ${presence.player.location.label} · ${after.companion.name}: ${presence.companion.location.label} · ${after.coRuler.actingRuler} führt die Regierung (${after.coRuler.regencyEfficiency} %).", true, GameDestination.RULERS)
        }
        after.dynasty.members.filter { member -> before.dynasty.members.none { it.id == member.id } && member.parents.isNotEmpty() }.forEach {
            add(ReportCategory.FAMILY, "Familienzuwachs", "${it.name} gehört nun zur Familie.", true, GameDestination.FAMILY)
        }
        after.frontier.hordes.filter { horde -> horde.discovered && before.frontier.hordes.none { it.id == horde.id && it.discovered } }.forEach {
            add(ReportCategory.FRONTIER, "Grenzbericht: ${it.name}", "${it.estimatedStrengthLabel} · Ankunft in ${it.daysToArrival} Tagen.", true, GameDestination.FRONTIER)
        }
        if (after.frontier.patrol == null && before.frontier.patrol != null)
            add(ReportCategory.FRONTIER, "Patrouille zurück", "Die Grenztruppen sind wieder in der Festung verfügbar.", true, GameDestination.FRONTIER)
        before.frontier.reinforcements.filter { aid -> after.frontier.reinforcements.none { it.id == aid.id } }.forEach {
            add(ReportCategory.FRONTIER, "Verbündete angekommen", "${it.amount} ${it.type.label} aus ${it.origin} sind nun einsatzbereit.", true, GameDestination.FRONTIER)
        }
        after.frontier.weapons.filter { weapon -> weapon.count > (before.frontier.weapons.firstOrNull { it.type == weapon.type }?.count ?: 0) }.forEach {
            add(ReportCategory.FRONTIER, "Mauerwaffe einsatzbereit", "${it.type.label} am ${it.section.label} · ${it.ammunition} Ladungen", true, GameDestination.FRONTIER)
        }
        if (rows.isEmpty())
            add(
                ReportCategory.ECONOMY,
                "Ruhiger Tag",
                "Keine besonderen Meldungen. Wirtschaft und Verwaltung arbeiten planmäßig.",
            )

        val trends = mapOf("gold" to gold, "food" to food, "population" to after.population.total - before.population.total,
            "satisfaction" to after.city.satisfaction - before.city.satisfaction,
            "security" to after.city.security - before.city.security,
            "conflict" to after.relationship.conflict - before.relationship.conflict)
        return DailyReport(after.day, rows.sortedByDescending { it.important }.take(32), trends)
    }

    private fun signed(value: Int): String =
        if (value >= 0) "+$value" else value.toString()
}
