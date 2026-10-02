package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object CommanderEventEngine {
    fun tick(state: GameState): GameState {
        if (state.commanderEvents.pending != null || state.commanders.isEmpty()) return state
        val interval = if (state.commanders.size >= 3) 10 else 14
        if (state.day - state.commanderEvents.lastGeneratedDay < interval) return state
        val available = state.commanders.filter { !state.commanderAway(it.id) }
        if (available.isEmpty()) return state
        val first = available[(state.day + available.size) % available.size]
        val other = available.firstOrNull { it.id != first.id }
        val kind = CommanderEventKind.entries[(state.day / interval) % CommanderEventKind.entries.size]
        val pair = when (kind) {
            CommanderEventKind.RIVALRY ->
                "Rivalität im Kriegsrat" to
                    "${first.name} fordert eine klare Entscheidung über Rang und Einsatzverteilung."
            CommanderEventKind.FRIENDSHIP ->
                "Gemeinsamer Dienst" to
                    "${first.name}${other?.let { " und ${it.name}" } ?: ""} haben im Dienst enger zusammengefunden."
            CommanderEventKind.PROMOTION ->
                "Anspruch auf Verantwortung" to
                    "${first.name} hält sich für bereit, mehr Verantwortung zu übernehmen."
            CommanderEventKind.WOUND ->
                "Belastung des Dienstes" to
                    "${first.name} bittet nach den letzten Einsätzen um eine ruhigere Verwendung."
            CommanderEventKind.LOYALTY ->
                "Treue auf dem Prüfstand" to
                    "${first.name} erwartet Anerkennung für geleisteten Dienst."
            CommanderEventKind.DISAGREEMENT ->
                "Uneinigkeit im Kriegsrat" to
                    "${first.name}${other?.let { " und ${it.name}" } ?: ""} vertreten gegensätzliche Pläne."
        }
        return state.copy(
            commanderEvents =
                state.commanderEvents.copy(
                    pending =
                        PendingCommanderEvent(
                            id = "commander_${state.day}_${first.id}",
                            kind = kind,
                            commanderId = first.id,
                            otherCommanderId = other?.id,
                            title = pair.first,
                            text = pair.second,
                            expiresDay = state.day + 5,
                        ),
                    lastGeneratedDay = state.day,
                )
        )
    }

    fun choices(event: PendingCommanderEvent): List<String> = when (event.kind) {
        CommanderEventKind.RIVALRY -> listOf("Beide anhören", "Klare Rangordnung durchsetzen")
        CommanderEventKind.FRIENDSHIP -> listOf("Gemeinsamen Einsatz fördern", "Professionelle Distanz wahren")
        CommanderEventKind.PROMOTION -> listOf("Verantwortung übertragen", "Noch warten")
        CommanderEventKind.WOUND -> listOf("Erholung gewähren", "Im Dienst belassen")
        CommanderEventKind.LOYALTY -> listOf("Öffentlich anerkennen", "Pflicht betonen")
        CommanderEventKind.DISAGREEMENT -> listOf("Kompromiss suchen", "Eine Linie vorgeben")
    }

    fun resolve(state: GameState, choice: Int): GameEngine.ActionResult {
        val event =
            state.commanderEvents.pending
                ?: return GameEngine.ActionResult(state, "Kein Kommandantenereignis offen.")
        val commander =
            state.commanders.firstOrNull { it.id == event.commanderId }
                ?: return GameEngine.ActionResult(
                    state.copy(commanderEvents = state.commanderEvents.copy(pending = null)),
                    "Die Person ist nicht mehr verfügbar.",
                )
        val positive = choice == 0
        var next =
            state.copy(
                commanders =
                    state.commanders.map {
                        if (it.id == commander.id)
                            it.copy(
                                loyalty = (it.loyalty + if (positive) 5 else -4).coerceIn(0, 100),
                                leadership =
                                    (it.leadership +
                                            if (
                                                event.kind == CommanderEventKind.PROMOTION &&
                                                    positive
                                            )
                                                2
                                            else 0)
                                        .coerceAtMost(100),
                            )
                        else it
                    }
            )

        val other = event.otherCommanderId
        if (
            other != null &&
                event.kind in
                    listOf(
                        CommanderEventKind.RIVALRY,
                        CommanderEventKind.FRIENDSHIP,
                        CommanderEventKind.DISAGREEMENT,
                    )
        ) {
            val socialKind =
                if (event.kind == CommanderEventKind.FRIENDSHIP || positive)
                    SocialKind.FRIENDSHIP
                else SocialKind.RIVALRY
            val existing =
                next.court.socialLinks.filterNot {
                    (it.firstId == commander.id && it.secondId == other) ||
                        (it.firstId == other && it.secondId == commander.id)
                }
            next =
                next.copy(
                    court =
                        next.court.copy(
                            socialLinks =
                                (existing +
                                        NpcSocialLink(
                                            commander.id,
                                            other,
                                            socialKind,
                                            state.day,
                                            if (positive) 35 else 30,
                                        ))
                                    .takeLast(80)
                        )
                )
        }

        val history =
            (next.commanderEvents.history +
                    CommanderDevelopmentEvent(
                        event.kind,
                        event.commanderId,
                        event.otherCommanderId,
                        state.day,
                    ))
                .takeLast(100)
        next =
            next.copy(
                commanderEvents =
                    next.commanderEvents.copy(
                        pending = null,
                        history = history,
                    ),
                chronicle =
                    (next.chronicle +
                            ChronicleEntry(
                                state.day,
                                event.title,
                                if (positive)
                                    "Du hast vermittelt und Verantwortung übernommen."
                                else "Du hast eine klare, aber härtere Linie gewählt.",
                            ))
                        .takeLast(2000),
            )
        return GameEngine.ActionResult(next, "${event.title}: Entscheidung umgesetzt.")
    }

    fun expire(state: GameState): GameState {
        val event = state.commanderEvents.pending ?: return state
        if (event.expiresDay >= state.day) return state
        return state.copy(
            commanders =
                state.commanders.map {
                    if (it.id == event.commanderId)
                        it.copy(loyalty = (it.loyalty - 3).coerceAtLeast(0))
                    else it
                },
            commanderEvents = state.commanderEvents.copy(pending = null),
        )
    }
}
