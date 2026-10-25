package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * v2.0 decision inbox: every waiting decision of every subsystem as one card list on "Heute".
 * Ignored decisions settle themselves after [AUTO_DAYS] days with their cautious default,
 * so nothing piles up silently in a tab the player never opens.
 */
object InboxEngine {
    const val AUTO_DAYS = 3

    enum class Source(val label: String) {
        WEEKLY("Diese Woche"), CAMPAIGN("Reich"), REALM("Kriegsrat"), FRONTIER("Grenze"), WORLD("Grenzland"), COMMANDER("Feldherren"),
        RELATIONSHIP("Partnerin"), FAMILY("Familie"), STORY("Volk"), DEMAND("Volksforderung"), COUNCIL("Rat"),
    }

    data class Option(val id: String, val label: String, val detail: String = "")

    data class Item(
        val key: String,
        val source: Source,
        val title: String,
        val text: String,
        val options: List<Option>,
        val urgent: Boolean = false,
        /** When set the decision is only shown here; it is answered in its own screen. */
        val openSpot: Spot? = null,
        val portraitKey: String? = null,
        val autoSettles: Boolean = true,
        val defaultOption: String? = options.lastOrNull()?.id,
    )

    fun items(state: GameState): List<Item> {
        val list = mutableListOf<Item>()
        state.reign.weekly?.takeIf { !it.resolved }?.let { w ->
            list += Item("weekly:${w.kind}:${w.startedDay}", Source.WEEKLY, w.kind.label, w.kind.text,
                ReignEngine.weeklyOptions(state, w).map { Option(it.id, it.label, it.detail) }, urgent = w.kind == WeeklyKind.ORC_STORM)
        }
        state.campaign.pendingDecision?.let { d ->
            list += Item("campaign:${d.id}", Source.CAMPAIGN, d.title, d.text,
                CampaignPulseEngine.choices(state, d).map { Option(it.id, it.label, it.detail) },
                urgent = d.expiresDay - state.day <= 2, autoSettles = false)
        }
        state.pendingRealmEvent?.let { e ->
            list += Item("realm:${e.key}", Source.REALM, e.title, e.text,
                EventEngine.choices(e).mapIndexed { i, label -> Option("$i", label) })
        }
        state.frontier.pendingDecision?.let { d ->
            list += Item("frontier:${d.id}", Source.FRONTIER, d.title, d.text, d.choices.map { Option(it.id, it.label, it.detail) }, urgent = true)
        }
        state.world.stories.filter { !it.resolved && it.options.isNotEmpty() }.forEach { s ->
            list += Item("world:${s.id}", Source.WORLD, s.title, s.text, s.options.mapIndexed { i, label -> Option("$i", label) })
        }
        state.commanderEvents.pending?.let { e ->
            val commander = state.commanders.firstOrNull { it.id == e.commanderId }
            list += Item("commander:${e.id}", Source.COMMANDER, e.title, e.text,
                CommanderEventEngine.choices(e).mapIndexed { i, label -> Option("$i", label) }, portraitKey = commander?.portraitKey)
        }
        state.relationship.pendingEvent?.let { e ->
            val private = e.key == "intimacy" || e.adultsOnly || e.consentRequired
            list += Item("rel:${e.key}:${e.createdDay}", Source.RELATIONSHIP, e.title, e.text,
                if (private) emptyList() else RelationshipEventDirector.options(e).mapIndexed { i, c -> Option("$i", c.label, c.consequenceHint) },
                openSpot = if (private) Spot.PAIR else null, portraitKey = "companion", autoSettles = false)
        }
        state.dynasty.pendingFamilyEvent?.let { e ->
            list += Item("family:${e.id}:${e.openedDay}", Source.FAMILY, e.title, e.text,
                e.choices.map { Option(it.id, it.label, it.consequence) }, autoSettles = false)
        }
        state.society.story.pending?.let { e ->
            list += Item("story:${e.id}", Source.STORY, e.title, e.text, StoryDirector.choices(e).map { Option(it.id, it.label) }, autoSettles = false)
        }
        state.society.demands.forEach { d ->
            list += Item("demand:${d.id}", Source.DEMAND, "${d.group.label}: ${d.kind.label}",
                "Frist bis Tag ${d.deadlineDay}." + if (d.kind.cost > 0) " Kosten: ${d.kind.cost} Gold." else "",
                listOf(Option("accept", if (d.kind.cost > 0) "Erfüllen · ${d.kind.cost} Gold" else "Erfüllen"), Option("decline", "Begründet ablehnen")),
                urgent = d.deadlineDay - state.day <= 1, autoSettles = false)
        }
        CoRulerEngine.councilCases(state).firstOrNull()?.let { c ->
            val advice = c.recommendations.firstOrNull()
            list += Item("council:${c.id}", Source.COUNCIL, c.title,
                c.situation + (advice?.let { "\nRat: ${it.member} empfiehlt „${c.options.firstOrNull { o -> o.id == it.optionId }?.label ?: it.optionId}“." } ?: ""),
                c.options.map { Option(it.id, it.label, it.explanation) }, autoSettles = false)
        }
        return list.sortedByDescending { it.urgent }
    }

    fun count(state: GameState): Int = items(state).size

    fun choose(state: GameState, key: String, optionId: String): GameEngine.ActionResult {
        val item = items(state).firstOrNull { it.key == key } ?: return GameEngine.ActionResult(state, "Diese Entscheidung ist nicht mehr offen.")
        if (item.options.none { it.id == optionId }) return GameEngine.ActionResult(state, "Ungültige Wahl.")
        val id = key.substringAfter(':')
        val result = when (item.source) {
            Source.WEEKLY -> ReignEngine.chooseWeekly(state, optionId)
            Source.CAMPAIGN -> CampaignPulseEngine.resolve(state, optionId)
            Source.REALM -> EventEngine.choose(state, optionId.toInt())
            Source.FRONTIER -> FrontierEngine.resolveFrontierDecision(state, optionId)
            Source.WORLD -> WorldEngine.chooseStory(state, id, optionId.toInt())
            Source.COMMANDER -> CommanderEventEngine.resolve(state, optionId.toInt())
            Source.RELATIONSHIP -> RelationshipEngine.choose(state, optionId.toInt())
            Source.FAMILY -> DynastyEngine.chooseFamilyEvent(state, optionId)
            Source.STORY -> StoryDirector.resolve(state, id, optionId)
            Source.DEMAND -> SocietyEngine.resolveDemand(state, id, optionId == "accept")
            Source.COUNCIL -> CoRulerEngine.decide(state, id, optionId)
        }
        return GameEngine.ActionResult(forget(result.state, key), result.message)
    }

    private fun forget(state: GameState, key: String): GameState =
        if (key in state.reign.inboxSince) state.copy(reign = state.reign.copy(inboxSince = state.reign.inboxSince - key)) else state

    /** Daily bookkeeping: remember when each card appeared and settle the ones ignored for too long. */
    fun tick(state: GameState): GameState {
        var next = state
        val current = items(next)
        val since = next.reign.inboxSince.filterKeys { k -> current.any { it.key == k } } +
            current.filter { it.key !in next.reign.inboxSince }.associate { it.key to next.day }
        next = next.copy(reign = next.reign.copy(inboxSince = since))
        if (next.battleSession?.isActive == true) return next
        current.filter { it.autoSettles && it.openSpot == null && next.day - (since[it.key] ?: next.day) >= AUTO_DAYS }.forEach { item ->
            val option = item.defaultOption ?: return@forEach
            val before = next
            val result = choose(next, item.key, option)
            next = if (result.state === before || items(result.state).any { it.key == item.key }) clear(before, item) else result.state
            next = next.copy(
                reign = next.reign.copy(inboxSince = next.reign.inboxSince - item.key, autoSettled = next.reign.autoSettled + 1),
                chronicle = next.chronicle + ChronicleEntry(next.day, "Selbst entschieden: ${item.title}",
                    "Weil niemand entschied, wählten die Räte „${item.options.firstOrNull { it.id == option }?.label ?: option}“."),
            )
        }
        return next
    }

    /** Fallback when the default answer could not be paid: the matter simply lapses. */
    private fun clear(state: GameState, item: Item): GameState = when (item.source) {
        Source.REALM -> state.copy(pendingRealmEvent = null)
        Source.WEEKLY -> state.copy(reign = state.reign.copy(weekly = state.reign.weekly?.copy(resolved = true)))
        Source.FRONTIER -> state.copy(frontier = state.frontier.copy(pendingDecision = null))
        Source.COMMANDER -> state.copy(commanderEvents = state.commanderEvents.copy(pending = null))
        Source.WORLD -> state.copy(world = state.world.copy(stories = state.world.stories.map { if ("world:${it.id}" == item.key) it.copy(resolved = true) else it }))
        else -> state
    }
}
