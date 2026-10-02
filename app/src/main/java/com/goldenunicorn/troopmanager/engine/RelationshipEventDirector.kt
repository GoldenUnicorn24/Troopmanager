package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/** Deterministic context selection; saves persist every cooldown, consequence and arc step. */
object RelationshipEventDirector {
    private val legacyKeys = mapOf("conversation" to "embers", "training" to "training_partner", "argument" to "supply_arguments", "attack" to "eve_battle", "mission" to "mission_return", "wounded" to "beds_short", "feast" to "feast_court", "politics" to "alliance_clause", "defeat" to "defeat_map")

    fun options(event: RelationshipEvent): List<RelationshipChoice> {
        if (event.options.isNotEmpty()) return event.options
        if (event.key == "intimacy") return listOf(
            RelationshipChoice("private", "Den privaten Abend gemeinsam verbringen", "Ihr zieht euch gemeinsam zurück.", consequenceHint = "Freiwillige private Nähe; Fade-to-black."),
            RelationshipChoice("conversation", "Über den Feldzug und eure Sorgen sprechen", "Ihr nehmt euch Zeit für ein Gespräch."),
            RelationshipChoice("rest", "Heute Ruhe und Abstand wählen", "Ein Nein wird ohne Vorwurf angenommen.", boundary = true))
        val source = RelationshipContentCatalog.scenarios.firstOrNull { it.key == (legacyKeys[event.key] ?: event.key) }
        return source?.options ?: listOf(
            RelationshipChoice("listen", "Ihre Sicht anhören und die nächsten Schritte gemeinsam prüfen", "Ihr hört einander zu.", effect = RelationshipEffect(trust = 3)),
            RelationshipChoice("explain", "Deine Prioritäten erklären und einen anderen Weg beraten", "Ihr besprecht verschiedene legitime Ziele.", effect = RelationshipEffect(respect = 3)),
            RelationshipChoice("later", "Heute keine gemeinsame Entscheidung treffen", "Ihr gebt einander Zeit ohne Vorwurf.", boundary = true))
    }

    fun open(state: GameState, key: String): GameState {
        if (!state.companion.met || state.relationship.pendingEvent != null) return state
        val scenario = RelationshipContentCatalog.scenarios.firstOrNull { it.key == (legacyKeys[key] ?: key) } ?: return state
        if (PresenceEngine.sharedActivityBlocker(state, scenario.category == "hospital") != null) return state
        if (state.day - (state.relationship.eventLastDay[scenario.key] ?: -1000) < scenario.cooldownDays) return state
        if (state.day - (state.relationship.familyLastDay[scenario.family] ?: -1000) < 4) return state
        return present(state, scenario)
    }

    fun day(state: GameState): GameState {
        if (!state.companion.met || state.relationship.lastDirectorDay == state.day) return state
        var next = state.copy(relationship = state.relationship.copy(lastDirectorDay = state.day))
        val due = next.relationship.delayedConsequences.filter { it.dueDay <= next.day }
        for (effect in due) {
            next = applyEffect(next, effect.effect)
            next = RelationshipEngine.remember(next, "consequence", effect.text, 2, setOf("relationship", "delayed", effect.sourceMemoryId))
            next = next.copy(chronicle = (next.chronicle + ChronicleEntry(next.day, "Eine frühere Entscheidung wirkt nach", effect.text)).takeLast(2000))
        }
        next = next.copy(relationship = next.relationship.copy(delayedConsequences = next.relationship.delayedConsequences.filter { it.dueDay > next.day }))
        next = observeWorld(next)
        next = startArc(next)
        if (next.relationship.pendingEvent != null) return next
        val arc = next.relationship.arcs.firstOrNull { !it.completed && it.nextDay <= next.day }
        if (arc != null) {
            val definition = RelationshipContentCatalog.arcs.firstOrNull { it.id == arc.id }
            val stage = definition?.stages?.getOrNull(arc.stage)
            if (stage != null && PresenceEngine.sharedActivityBlocker(next) == null)
                return present(next, stage, arc.id, arc.stage)
        }
        if (next.day % 3 != 0) return next
        val eligible = eligibleScenarios(next)
        if (eligible.isEmpty()) return next
        val weighted = eligible.map { it to weight(next, it) }
        var draw = Random(seed(next, "relationship")).nextInt(weighted.sumOf { it.second })
        for ((scenario, w) in weighted) {
            if (draw < w) return present(next, scenario)
            draw -= w
        }
        return next
    }

    fun eligibleScenarios(state: GameState): List<RelationshipScenario> = RelationshipContentCatalog.scenarios.filter { scenario ->
        scenario.requirements.all { meets(state, it) } &&
            PresenceEngine.sharedActivityBlocker(state, scenario.category == "hospital") == null &&
            state.day - (state.relationship.eventLastDay[scenario.key] ?: -1000) >= scenario.cooldownDays &&
            state.day - (state.relationship.familyLastDay[scenario.family] ?: -1000) >= 4
    }

    fun weight(state: GameState, scenario: RelationshipScenario): Int {
        val personality = state.relationship.personality
        val priority = scenario.preferredPriority
        val priorityBonus = if (priority != null && priority in personality.priorities) 10 else 0
        val issueBonus = if (state.relationship.issues.any { !it.resolved && it.topic == scenario.topic }) 18 else 0
        val traitBonus = when {
            scenario.category == "hospital" && CompanionTrait.COMPASSIONATE in personality.traits -> 8
            scenario.category == "court" && CompanionTrait.PROUD in personality.traits -> 8
            scenario.category == "war" && CompanionTrait.MARTIAL in personality.traits -> 8
            scenario.category == "politics" && CompanionTrait.DIPLOMATIC in personality.traits -> 8
            scenario.topic == "Verteidigung" && CompanionTrait.CAUTIOUS in personality.traits -> 6
            else -> 0
        }
        return (scenario.weight + priorityBonus + issueBonus + traitBonus).coerceAtLeast(1)
    }

    fun meets(state: GameState, requirement: String): Boolean {
        val recent = state.war.history.filter { state.day - it.day in 0..12 }
        val companionCondition = state.war.commanderConditions.firstOrNull { it.commanderId == COMPANION_COMMANDER_ID }?.status
        val war = state.invasion != null || state.diplomacy.relations.any { it.atWar } || state.world.factions.any { PLAYER_FACTION in it.wars }
        val home = state.world.places.firstOrNull { it.ownerId == PLAYER_FACTION && it.kind in setOf(PlaceKind.KEEP, PlaceKind.CAPITAL) }?.id
        val weather = home?.let { state.world.weather.at(it) } ?: WeatherKind.CLEAR
        return when (requirement) {
            "war" -> war || recent.isNotEmpty()
            "peace" -> !war && state.battleSession?.isActive != true
            "victory" -> recent.any { it.victory }
            "defeat" -> recent.any { !it.victory }
            "losses" -> recent.any { it.casualties.total >= 15 }
            "siege" -> state.invasion != null || state.war.siegeFoodStored > 0
            "wounded" -> state.war.wounded.isNotEmpty() || companionCondition == CombatantStatus.WOUNDED || state.war.playerCondition == CombatantStatus.WOUNDED
            "companion_wounded" -> companionCondition == CombatantStatus.WOUNDED
            "player_wounded" -> state.war.playerCondition == CombatantStatus.WOUNDED
            "healthy" -> state.war.playerCondition == CombatantStatus.ACTIVE && !state.war.unavailableCommander(COMPANION_COMMANDER_ID)
            "high_tax" -> state.city.taxLevel == TaxLevel.HIGH
            "refugees" -> state.society.lastMigration > 0 || state.society.totalImmigrants > 0
            "occupation" -> state.occupations.isNotEmpty()
            "captives" -> state.war.captives.isNotEmpty()
            "diplomacy" -> state.diplomacy.treaties.isNotEmpty() || state.diplomacy.proposals.isNotEmpty() || state.world.factions.isNotEmpty()
            "family" -> state.dynasty.members.any { it.alive }
            "expecting" -> state.dynasty.plannedBirthDay != null
            "frontier" -> state.frontier.hordes.isNotEmpty() || state.frontier.patrol != null || state.frontier.allies.any { it.lastAidDay >= 0 || it.contributions > 0 }
            "co_rulers" -> state.relationship.romanceStage == RomanceStage.CO_RULERS
            "romance" -> state.relationship.romanceStage != RomanceStage.NONE && state.settings.romance != RomanceMode.OFF && state.player.age >= 18 && state.companion.age >= 18
            "conflict" -> state.relationship.conflict > 0 || state.relationship.issues.any { !it.resolved }
            "rain" -> weather in setOf(WeatherKind.RAIN, WeatherKind.STORM)
            "cold" -> state.world.weather.season == Season.WINTER || weather in setOf(WeatherKind.SNOW, WeatherKind.FROST)
            "outdoor" -> weather !in setOf(WeatherKind.STORM, WeatherKind.SNOW, WeatherKind.HEAT)
            else -> false
        }
    }

    private fun seed(state: GameState, salt: String): Int = "$salt:${state.player.name}:${state.day}:${state.relationship.memories.size}".hashCode()

    private fun present(state: GameState, scenario: RelationshipScenario, arcId: String? = null, arcStage: Int? = null): GameState {
        val variant = Random(seed(state, scenario.key)).nextInt(scenario.variants.size + 1)
        val memory = state.relationship.memories.lastOrNull { scenario.topic in it.tags || it.type == scenario.key || arcId != null && arcId in it.tags }
        val callback = memory?.let { "\nIhr erinnert euch an Tag ${it.day}: ${it.text}" }.orEmpty()
        val actualContext = when {
            scenario.category == "hospital" -> "Im Lazarett bleiben die Gespräche kurz und die Heilung hat Vorrang."
            state.city.taxLevel == TaxLevel.HIGH && scenario.category == "politics" -> "Die hohen Abgaben sind am Ratstisch heute konkret spürbar."
            state.resources.food == 0 -> "Die leeren Vorratskammern machen jede Zusage schwierig."
            state.relationship.issues.any { !it.resolved && it.topic == scenario.topic } -> "Das Thema ist zwischen euch noch offen."
            else -> "${state.world.weather.season.label} · ${PresenceEngine.presence(state).companion.location.label}"
        }
        val variantText = if (variant == 0) "" else "\n${scenario.variants[variant - 1]}"
        val event = RelationshipEvent(scenario.key, scenario.title,
            "${scenario.text}$variantText\n$actualContext$callback".replace("{name}", state.companion.name),
            options = scenario.options.map { it.copy(reaction = it.reaction.replace("{name}", state.companion.name)) },
            category = scenario.category, topic = scenario.topic, variantId = "${scenario.key}:$variant", arcId = arcId, arcStage = arcStage, createdDay = state.day)
        return state.copy(relationship = state.relationship.copy(pendingEvent = event,
            eventLastDay = state.relationship.eventLastDay + (scenario.key to state.day),
            familyLastDay = state.relationship.familyLastDay + (scenario.family to state.day)))
    }

    private fun startArc(state: GameState): GameState {
        if (state.day < 7 || state.relationship.arcs.any { !it.completed }) return state
        val lastCompleted = state.relationship.arcs.maxOfOrNull { it.nextDay } ?: -100
        if (state.day - lastCompleted < 12) return state
        val candidates = RelationshipContentCatalog.arcs.filter { arc -> state.relationship.arcs.none { it.id == arc.id } && arc.requirements.all { meets(state, it) } }
        if (candidates.isEmpty()) return state
        val arc = candidates[Random(seed(state, "arc")).nextInt(candidates.size)]
        return state.copy(relationship = state.relationship.copy(arcs = state.relationship.arcs + RelationshipArcState(arc.id, startedDay = state.day)))
    }

    fun choose(state: GameState, index: Int): GameEngine.ActionResult {
        val event = state.relationship.pendingEvent ?: return GameEngine.ActionResult(state, "Kein Beziehungsereignis offen.")
        val choice = options(event).getOrNull(index) ?: return GameEngine.ActionResult(state, "Ungültige Entscheidung.")
        PresenceEngine.sharedActivityBlocker(state, event.category == "hospital")?.let { return GameEngine.ActionResult(state, it) }
        val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (!choice.boundary && spent + 2 > 2) return GameEngine.ActionResult(state, "Diese Entscheidung braucht eure gemeinsame Zeit für einen Tag.")
        val effect = if (choice.boundary) RelationshipEffect() else choice.effect
        if (state.resources.gold < -effect.gold || state.resources.food < -effect.food) return GameEngine.ActionResult(state, "Für diese Zusage fehlen die genannten Vorräte.")
        var next = applyEffect(state, effect)
        if (!choice.boundary && choice.priority in state.relationship.personality.priorities)
            next = next.copy(companion = next.companion.copy(trust = (next.companion.trust + 1).coerceAtMost(100)))
        next = next.copy(relationship = next.relationship.copy(pendingEvent = null, actionDay = state.day,
            spentActions = if (choice.boundary) spent else spent + 2))
        next = RelationshipEngine.remember(next, event.key, "${event.title}: ${choice.label} ${choice.reaction}",
            if (choice.boundary) 0 else if (event.arcId != null) 4 else 2,
            setOf(event.category, event.topic, "relationship") + listOfNotNull(event.arcId) + if (choice.boundary) setOf("boundary") else emptySet())
        val memoryId = next.relationship.memories.last().id
        next = next.copy(relationship = next.relationship.copy(memories = next.relationship.memories.dropLast(1) + next.relationship.memories.last().copy(eventId = event.key)))
        if (!choice.boundary && choice.resolvesIssue) next = addressIssue(next, event.topic, memoryId, 12)
        if (!choice.boundary && choice.delayedEffect != null && choice.delayDays > 0)
            next = next.copy(relationship = next.relationship.copy(delayedConsequences = next.relationship.delayedConsequences + RelationshipDelayedConsequence(
                "${memoryId}_consequence", state.day + choice.delayDays, choice.delayedText.ifBlank { choice.reaction }, choice.delayedEffect, memoryId)))
        val arcId = event.arcId
        if (arcId != null) {
            val definition = RelationshipContentCatalog.arcs.firstOrNull { it.id == arcId }
            val arc = next.relationship.arcs.firstOrNull { it.id == arcId }
            if (arc != null && definition != null && event.arcStage == arc.stage) {
                val following = arc.stage + 1
                val complete = following >= definition.stages.size
                val updated = arc.copy(stage = following, nextDay = if (complete) state.day else state.day + (definition.stageIntervals.getOrNull(arc.stage) ?: 10),
                    completed = complete, decisions = arc.decisions + choice.id, memoryIds = arc.memoryIds + memoryId)
                next = next.copy(relationship = next.relationship.copy(arcs = next.relationship.arcs.map { if (it.id == arcId) updated else it }))
            }
        }
        next = next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, event.title, "${choice.label} ${choice.reaction}")).takeLast(2000))
        return GameEngine.ActionResult(RelationshipEngine.syncCommander(next), choice.reaction)
    }

    fun applyEffect(state: GameState, effect: RelationshipEffect): GameState {
        val c = state.companion
        var next = state.copy(companion = c.copy(trust = (c.trust + effect.trust).coerceIn(0, 100), respect = (c.respect + effect.respect).coerceIn(0, 100), affection = (c.affection + effect.affection).coerceIn(0, 100)),
            relationship = state.relationship.copy(conflict = (state.relationship.conflict + effect.conflict).coerceIn(0, 100), loyalty = (state.relationship.loyalty + effect.loyalty).coerceIn(0, 100)),
            resources = state.resources.copy(gold = (state.resources.gold.toLong() + effect.gold).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), food = (state.resources.food.toLong() + effect.food).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()),
            city = state.city.copy(satisfaction = (state.city.satisfaction + effect.satisfaction).coerceIn(0, 100)),
            realm = state.realm.copy(tradeBonusDays = maxOf(state.realm.tradeBonusDays, effect.tradeDays)))
        if (effect.topic != null && effect.issueSeverity > 0) next = addIssue(next, effect.topic, effect.issueSeverity)
        if (effect.topic != null && effect.issueSeverity < 0) next = addressIssue(next, effect.topic, null, -effect.issueSeverity)
        return next
    }

    fun addIssue(state: GameState, topic: String, severity: Int, memoryId: String? = null): GameState {
        val old = state.relationship.issues.firstOrNull { !it.resolved && it.topic == topic }
        val issue = old?.copy(severity = (old.severity + severity).coerceIn(0, 100), ignoredCount = old.ignoredCount + 1,
            memoryIds = old.memoryIds + listOfNotNull(memoryId)) ?: RelationshipIssue("issue:${topic.hashCode()}:${state.day}", topic, severity.coerceIn(0, 100), state.day, memoryIds = listOfNotNull(memoryId))
        val all = state.relationship.issues.filterNot { it.id == issue.id } + issue
        return state.copy(relationship = state.relationship.copy(issues = all.takeLast(100)))
    }

    fun addressIssue(state: GameState, topic: String, memoryId: String?, amount: Int): GameState {
        val issues = state.relationship.issues.map { issue ->
            if (!issue.resolved && issue.topic == topic) {
                val severity = (issue.severity - amount).coerceAtLeast(0)
                issue.copy(severity = severity, resolved = severity == 0, lastDiscussedDay = state.day, memoryIds = issue.memoryIds + listOfNotNull(memoryId))
            } else issue
        }
        return state.copy(relationship = state.relationship.copy(issues = issues,
            conflict = (state.relationship.conflict - if (issues != state.relationship.issues) amount / 3 else 0).coerceAtLeast(0)))
    }

    fun currentOpinion(state: GameState): String {
        val priorities = state.relationship.personality.priorities
        return when {
            state.resources.food == 0 -> "Ohne gesicherte Versorgung widerspreche ich einem weiteren Feldzug."
            CompanionPriority.WOUNDED in priorities && state.war.wounded.isNotEmpty() -> "Die Verwundeten brauchen Versorgung und Ablösung, bevor wir neue Truppen anfordern."
            CompanionPriority.SUPPLY in priorities && state.city.taxLevel == TaxLevel.HIGH -> "Hohe Abgaben brauchen überprüfbare Härtefallregeln für die Bevölkerung."
            CompanionPriority.DIPLOMACY in priorities -> "Ich möchte verlässliche Partner und begrenzte Bündnisverpflichtungen statt eines offenen Kriegsversprechens."
            CompanionPriority.EXPANSION in priorities && CompanionTrait.MARTIAL in state.relationship.personality.traits -> "Ein begrenzter Feldzug kann unsere Wege sichern, wenn Versorgung und Rückzug vorab geklärt sind."
            CompanionPriority.FAMILY in priorities -> "Familie und Amt brauchen eigene verlässliche Zeit, auch wenn der Rat viele Aufgaben hat."
            CompanionPriority.TRADE in priorities -> "Verlässliche Handelswege geben dem Reich Handlungsspielraum."
            else -> "Eine sichere Grenze und eine versorgte Bevölkerung bleiben mein Maßstab."
        }
    }

    private fun observeWorld(state: GameState): GameState {
        var next = state
        val observed = state.relationship.observedPolicy
        val current = mapOf("tax" to state.city.taxLevel.ordinal, "occupied" to state.occupations.size,
            "captives" to state.war.captives.sumOf { it.soldiers }, "refugees" to state.society.totalImmigrants,
            "treaties" to state.diplomacy.treaties.size, "territory" to state.realm.territory)
        val reactions = mutableListOf<Pair<String, String>>()
        if (observed.isNotEmpty()) {
            if (current.getValue("tax") != observed["tax"]) reactions += "Steuern" to if (state.city.taxLevel == TaxLevel.HIGH) "${state.companion.name}: Hohe Abgaben brauchen einen klaren Schutz für versorgungsarme Haushalte." else "${state.companion.name}: Die neue Abgabenlinie gibt den Haushalten mehr Planungssicherheit."
            if (current.getValue("occupied") > (observed["occupied"] ?: 0)) reactions += "Integration" to "${state.companion.name}: Im besetzten Ort müssen Versorgung und die Stimmen der Bewohner Teil unserer Verantwortung werden."
            if (current.getValue("captives") != observed["captives"]) reactions += "Gefangene" to "${state.companion.name}: Die Gefangenenliste hat sich geändert. Anhörung, Versorgung und ein möglicher Austausch brauchen klare Regeln."
            if (current.getValue("refugees") > (observed["refugees"] ?: 0)) reactions += "Flüchtlinge" to "${state.companion.name}: Die Neuankömmlinge brauchen sichere Aufnahme und eine Perspektive in unserer Stadt."
            if (current.getValue("treaties") > (observed["treaties"] ?: 0)) reactions += "Bündnis" to "${state.companion.name}: Der neue Vertrag ist ein Anfang; wir müssen auch über seine Grenzen sprechen."
            if (current.getValue("territory") > (observed["territory"] ?: 0)) reactions += "Expansion" to "${state.companion.name}: Mehr Land bedeutet mehr Menschen, die wir schützen und versorgen müssen."
        }
        for ((topic, text) in reactions) {
            next = RelationshipEngine.remember(next, "policy_reaction", text, 2, setOf("politics", topic))
            if (topic in setOf("Integration", "Gefangene", "Expansion") || topic == "Steuern" && state.city.taxLevel == TaxLevel.HIGH)
                next = addIssue(next, topic, 8, next.relationship.memories.last().id)
            next = next.copy(relationship = next.relationship.copy(politicalOpinion = text.substringAfter(": ")))
        }
        val lastBattle = observed["battle_day"] ?: -1
        for (battle in state.war.history.filter { it.day > lastBattle && COMPANION_COMMANDER_ID in it.commanders }) {
            val text = "Du und ${state.companion.name} erinnert euch an ${battle.place}: ${if (battle.victory) "ein gemeinsam getragener Sieg" else "eine Niederlage und der Weg zurück"}; ${battle.casualties.total} Menschen kehrten verwundet, vermisst oder nicht zurück."
            next = RelationshipEngine.remember(next, if (battle.victory) "shared_victory" else "shared_defeat", text, 4, setOf("war", "milestone"))
            next = next.copy(relationship = next.relationship.copy(memories = next.relationship.memories.dropLast(1) + next.relationship.memories.last().copy(location = battle.place, eventId = battle.id)))
            if (battle.casualties.total >= 15) next = addIssue(next, "Verwundete", 12, next.relationship.memories.last().id)
        }
        return next.copy(relationship = next.relationship.copy(observedPolicy = current + ("battle_day" to (state.war.history.maxOfOrNull { it.day } ?: lastBattle)), lastReactionDay = state.day))
    }
}
