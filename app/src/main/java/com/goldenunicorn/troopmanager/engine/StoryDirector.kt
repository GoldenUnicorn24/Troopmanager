package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Contextual local event chains warn of pressures and leave the consequential choice to the ruler. */
object StoryDirector {
    data class Choice(val id: String, val label: String)

    fun choices(event: StoryEvent): List<Choice> = when (event.kind) {
        StoryKind.REFUGEES -> listOf(Choice("accept", "80 Flüchtlinge aufnehmen · 160 Nahrung"), Choice("decline", "Grenze geschlossen halten"))
        StoryKind.REFUGEE_HOUSING -> listOf(Choice("support", "Unterkünfte bauen · 240 Gold, 120 Holz"), Choice("decline", "Bestehenden Wohnraum teilen"))
        StoryKind.REFUGEE_COUNCIL -> listOf(Choice("support", "Vertreter in den Rat holen · 120 Gold"), Choice("decline", "Politische Vertretung ablehnen"))
        StoryKind.CORRUPTION -> listOf(Choice("investigate", "Rechnungen prüfen · 200 Gold"), Choice("tolerate", "Verwaltern vertrauen"))
        StoryKind.LOYALTY_CRISIS -> listOf(Choice("support", "Hof und Veteranen versöhnen · 250 Gold"), Choice("decline", "Befehle durchsetzen"))
        StoryKind.BORDER_MEDIATION -> listOf(Choice("mediate", "Grenzgespräche finanzieren · 120 Gold"), Choice("prepare", "Grenze defensiv sichern · 120 Nahrung"))
    }

    fun tick(state: GameState): GameState {
        val story = state.society.story
        if (story.lastTickDay >= state.day) return state
        var nextStory = story.copy(lastTickDay = state.day)
        if (story.pending != null && story.pending.expiresDay <= state.day) {
            val missed = resolve(state, story.pending.id, choices(story.pending).last().id)
            if (missed.state != state) return missed.state.copy(society = missed.state.society.copy(story =
                missed.state.society.story.copy(lastTickDay = state.day)))
            nextStory = nextStory.copy(pending = null, cooldownUntilDay = state.day + 7)
        }
        if (nextStory.pending != null) return state.copy(society = state.society.copy(story = nextStory))
        val due = nextStory.scheduled.filter { it.dueDay <= state.day }.minByOrNull { it.dueDay }
        if (due != null) {
            val event = create(state, due.kind, due.chainId)
            nextStory = nextStory.copy(pending = event, scheduled = nextStory.scheduled - due, nextId = nextStory.nextId + 1)
        } else if (state.day >= nextStory.cooldownUntilDay && state.day % 7 == 0) {
            val foreignFamine = state.world.factions.any { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION &&
                it.food < it.population / 2 && state.world.knowledgeFor(PLAYER_FACTION).exploredRegions.any { region ->
                    state.world.place(region)?.ownerId == it.id } }
            fun available(kind: StoryKind) = state.day >= (nextStory.familyCooldowns[kind] ?: 0)
            val kind = when {
                foreignFamine && state.society.hunger < 30 && available(StoryKind.REFUGEES) -> StoryKind.REFUGEES
                state.resources.gold > 12000 && state.society.inequality > 35 && available(StoryKind.CORRUPTION) -> StoryKind.CORRUPTION
                (state.society.politicalLoyalty < 40 || state.defeats > state.victories + 2 || state.society.warExhaustion >= 50 && state.society.groups.any { it.kind == PoliticalGroupKind.MILITARY && it.loyalty < 50 }) && available(StoryKind.LOYALTY_CRISIS) -> StoryKind.LOYALTY_CRISIS
                state.day >= 28 && state.diplomacy.relations.none { it.atWar &&
                    (it.firstFactionId == PLAYER_FACTION || it.secondFactionId == PLAYER_FACTION) } && available(StoryKind.BORDER_MEDIATION) -> StoryKind.BORDER_MEDIATION
                else -> null
            }
            if (kind != null) nextStory = nextStory.copy(pending = create(state, kind, "chain_${nextStory.nextId}"), nextId = nextStory.nextId + 1)
        }
        return state.copy(society = state.society.copy(story = nextStory))
    }

    private fun create(state: GameState, kind: StoryKind, chainId: String): StoryEvent {
        val (title, text) = when (kind) {
            StoryKind.REFUGEES -> "An der Grenze warten Flüchtlinge" to
                "Nahrungsmangel in einem bekannten Nachbarreich zwingt Familien zur Flucht. Aufnahme hilft ihnen, braucht aber Versorgung und später Wohnraum."
            StoryKind.REFUGEE_HOUSING -> "Neue Nachbarn, enger Wohnraum" to
                "Die aufgenommenen Familien leben seit zwei Wochen in provisorischen Quartieren. Zusätzliche Unterkünfte verhindern Spannungen."
            StoryKind.REFUGEE_COUNCIL -> "Eine Stimme für die neuen Bürger" to
                "Aus den aufgenommenen Familien ist eine Gemeinschaft geworden. Ihr Sprecher bittet um Mitwirkung im kulturellen Rat."
            StoryKind.CORRUPTION -> "Unstimmigkeiten in den Rechnungen" to
                "Hohe Schatzreserven und ungleiche Einkommen geben Bestechung Raum. Eine Prüfung kostet Gold und kann Kriminalität begrenzen."
            StoryKind.LOYALTY_CRISIS -> "Unruhe im Kriegsrat" to
                "Kriegsmüdigkeit ${state.society.warExhaustion} %, Hunger ${state.society.hunger} % und politische Loyalität ${state.society.politicalLoyalty} % belasten Veteranen und Kommandanten. Versorgung, Aussprache und erfüllte Forderungen stabilisieren die nächste Front."
            StoryKind.BORDER_MEDIATION -> "Eine ruhige Grenze bleibt eine Aufgabe" to
                "Nach Wochen des Friedens bitten Nachbarn um Gespräche zu Handelswegen und Grenzrechten. Diplomatie oder vorsichtige Verteidigung sind möglich."
        }
        return StoryEvent("story_${state.society.story.nextId}", kind, title, text, state.day, state.day + 14, chainId)
    }

    fun resolve(state: GameState, eventId: String, choiceId: String): GameEngine.ActionResult {
        val event = state.society.story.pending
            ?: return GameEngine.ActionResult(state, "Keine offene Geschichte.")
        if (event.id != eventId || choices(event).none { it.id == choiceId } || state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Diese Entscheidung ist nicht verfügbar.")
        var next = state
        fun pay(gold: Int = 0, food: Int = 0, wood: Int = 0): Boolean {
            if (next.resources.gold < gold || next.resources.food < food || next.resources.wood < wood) return false
            next = next.copy(resources = next.resources.copy(gold = next.resources.gold - gold,
                food = next.resources.food - food, wood = next.resources.wood - wood))
            return true
        }
        var schedule: ScheduledStory? = null
        val chain = event.chainId ?: event.id
        when (event.kind) {
            StoryKind.REFUGEES -> if (choiceId == "accept") {
                if (!pay(food = 160)) return GameEngine.ActionResult(state, "160 Nahrung benötigt.")
                val room = (Int.MAX_VALUE.toLong() - state.population.total).coerceAtLeast(0).coerceAtMost(80).toInt()
                next = next.copy(population = ArmyEngine.adjustPopulation(next.population, Culture.HUMAN, room),
                    society = next.society.copy(totalImmigrants = (next.society.totalImmigrants.toLong() + room).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        culturalTension = (next.society.culturalTension + 5).coerceAtMost(100)),
                    renown = (next.renown.toLong() + 6).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                schedule = ScheduledStory(StoryKind.REFUGEE_HOUSING, state.day + 14, chain)
            }
            StoryKind.REFUGEE_HOUSING -> {
                if (choiceId == "support") {
                    if (!pay(gold = 240, wood = 120)) return GameEngine.ActionResult(state, "240 Gold und 120 Holz benötigt.")
                    next = next.copy(city = next.city.copy(housingCapacity = (next.city.housingCapacity.toLong() + 160).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                        society = next.society.copy(culturalTension = (next.society.culturalTension - 5).coerceAtLeast(0)))
                } else next = next.copy(society = next.society.copy(culturalTension = (next.society.culturalTension + 12).coerceAtMost(100)))
                schedule = ScheduledStory(StoryKind.REFUGEE_COUNCIL, state.day + 21, chain)
            }
            StoryKind.REFUGEE_COUNCIL -> {
                if (choiceId == "support") {
                    if (!pay(gold = 120)) return GameEngine.ActionResult(state, "120 Gold benötigt.")
                    next = next.copy(society = next.society.copy(culturalTension = (next.society.culturalTension - 18).coerceAtLeast(0),
                        groups = next.society.groups.map { if (it.kind == PoliticalGroupKind.CULTURAL_REPRESENTATIVES)
                            it.copy(loyalty = (it.loyalty + 15).coerceAtMost(100)) else it }))
                } else next = next.copy(society = next.society.copy(culturalTension = (next.society.culturalTension + 8).coerceAtMost(100)))
            }
            StoryKind.CORRUPTION -> {
                if (choiceId == "investigate") {
                    if (!pay(gold = 200)) return GameEngine.ActionResult(state, "200 Gold benötigt.")
                    next = next.copy(society = next.society.copy(crime = (next.society.crime - 18).coerceAtLeast(0),
                        inequality = (next.society.inequality - 8).coerceAtLeast(0)))
                } else next = next.copy(society = next.society.copy(crime = (next.society.crime + 8).coerceAtMost(100)))
            }
            StoryKind.LOYALTY_CRISIS -> {
                if (choiceId == "support") {
                    if (!pay(gold = 250)) return GameEngine.ActionResult(state, "250 Gold benötigt.")
                    next = next.copy(society = next.society.copy(groups = next.society.groups.map {
                        it.copy(loyalty = (it.loyalty + 12).coerceAtMost(100)) }, warExhaustion = (next.society.warExhaustion - 4).coerceAtLeast(0)), commanders = next.commanders.map {
                            it.copy(loyalty = (it.loyalty + 6).coerceAtMost(100)) })
                } else next = next.copy(commanders = next.commanders.map { it.copy(loyalty = (it.loyalty - 5).coerceAtLeast(0)) },
                    city = next.city.copy(satisfaction = (next.city.satisfaction - 4).coerceAtLeast(0)),
                    armyPools = next.armyPools.map { it.copy(morale = (it.morale - 5).coerceAtLeast(0)) })
            }
            StoryKind.BORDER_MEDIATION -> {
                if (choiceId == "mediate") {
                    if (!pay(gold = 120)) return GameEngine.ActionResult(state, "120 Gold benötigt.")
                    val known = state.world.knowledgeFor(PLAYER_FACTION).exploredRegions.mapNotNull { state.world.place(it)?.ownerId }.distinct()
                    known.filter { it != PLAYER_FACTION && it != NEUTRAL_FACTION }.forEach {
                        next = DiplomacyEngine.changeRelation(next, PLAYER_FACTION, it, 8, 4, "Grenzgespräche klären gemeinsame Handelsinteressen.")
                    }
                } else {
                    if (!pay(food = 120)) return GameEngine.ActionResult(state, "120 Nahrung benötigt.")
                    next = next.copy(city = next.city.copy(security = (next.city.security + 6).coerceAtMost(100)))
                }
            }
        }
        val story = next.society.story
        next = next.copy(society = next.society.copy(story = story.copy(pending = null,
            scheduled = if (schedule != null) story.scheduled + schedule else story.scheduled,
            cooldownUntilDay = state.day + 28, familyCooldowns = story.familyCooldowns + (event.kind to (state.day + 84)), resolved = (story.resolved + event.id).takeLast(60))),
            chronicle = (next.chronicle + ChronicleEntry(state.day, event.title,
                choices(event).first { it.id == choiceId }.label + if (schedule != null) "; Folgen werden in den kommenden Wochen sichtbar." else ".")).takeLast(2000))
        return GameEngine.ActionResult(next, "Entscheidung getroffen: ${event.title}.")
    }
}
