package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Optional family simulation: campaign years contain 365 days, children never enter romance APIs. */
object DynastyEngine {
    /** Resolve a fatal battlefield outcome immediately, without advancing the campaign day. */
    fun resolveBattleSuccession(state: GameState): GameState =
        if (!state.settings.dynasty || state.war.playerCondition != CombatantStatus.DEAD) state
        else RelationshipEngine.normalize(succeed(initialize(state)))

    fun initialize(state: GameState): GameState {
        if (!state.settings.dynasty) return state
        var members = state.dynasty.members
        if (members.isEmpty()) members = listOf(FamilyMember("player", state.player.name, state.day, state.player.age,
            diplomacy = state.player.diplomacy, leadership = state.player.leadership))
        if (state.companion.met && members.none { it.id == "companion" }) members = members + FamilyMember("companion", state.companion.name, state.day, state.companion.age,
            diplomacy = state.companion.diplomacy, leadership = state.companion.leadership)
        members = members.map { member ->
            if (member.parents.isEmpty()) member else member.copy(
                traits = member.traits.ifEmpty { setOf(ChildTrait.entries[member.id.hashCode().ushr(1) % ChildTrait.entries.size]) },
                lastEducationDay = if (member.lastEducationDay < 0) state.day else member.lastEducationDay,
            )
        }
        return state.copy(dynasty = state.dynasty.copy(members = members, activeSinceDay = state.dynasty.activeSinceDay ?: state.day,
            observedVictories = if (state.dynasty.observedVictories < 0) state.victories else state.dynasty.observedVictories))
    }

    fun agreeFamilyPlanning(state: GameState): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist in den Einstellungen ausgeschaltet.")
        if (state.player.age < 18 || state.companion.age < 18 || !state.companion.met || state.relationship.romanceStage < RomanceStage.PARTNERSHIP)
            return GameEngine.ActionResult(state, "Familienplanung benötigt zwei Erwachsene in einer freiwilligen Partnerschaft.")
        if (state.relationship.conflict > 15 || state.companion.trust < 85 || state.companion.respect < 70 || "no_children" in state.relationship.consent.boundaries)
            return GameEngine.ActionResult(state, "${state.companion.name} möchte derzeit keine Familienplanung. Diese Grenze wird respektiert.")
        if (state.relationship.consent.wantsChildren) return GameEngine.ActionResult(state, "Ein gemeinsamer Kinderwunsch ist bereits vereinbart.")
        val next = state.copy(relationship = state.relationship.copy(consent = state.relationship.consent.copy(wantsChildren = true)))
        return GameEngine.ActionResult(RelationshipEngine.remember(initialize(next), "family_agreement", "Ihr sprecht freiwillig über den gemeinsamen Kinderwunsch."), "Ein gemeinsamer Kinderwunsch ist vereinbart; Familienplanung bleibt eine eigene Entscheidung.")
    }

    fun planFamily(state: GameState): GameEngine.ActionResult {
        val blocker = familyBlocker(state)
        if (blocker != null) return GameEngine.ActionResult(state, blocker)
        val next = initialize(state)
        if (next.dynasty.plannedBirthDay != null) return GameEngine.ActionResult(state, "Eure Familie erwartet bereits Zuwachs.")
        if (state.day - next.dynasty.lastFamilyPlanningDay < 365) return GameEngine.ActionResult(state, "Die Familie braucht Zeit; ein Jahr Abstand ist erforderlich.")
        if (next.dynasty.members.count { it.alive && it.id !in listOf("player", "companion") } >= 12)
            return GameEngine.ActionResult(state, "Der Hof kann derzeit keine weitere Familie versorgen.")
        val parents = listOf(next.dynasty.rulerId, "companion")
        if (parents.distinct().size != 2 || parents.any { id -> next.dynasty.members.none { it.id == id && it.alive && it.age(next.day) >= 18 } })
            return GameEngine.ActionResult(state, "Zwei erwachsene, lebende Eltern werden benötigt.")
        val planned = next.copy(dynasty = next.dynasty.copy(plannedBirthDay = next.day + 270, planningParents = parents, lastFamilyPlanningDay = next.day))
        return GameEngine.ActionResult(RelationshipEngine.remember(planned, "family_planned", "Ihr entscheidet euch gemeinsam für Familienzuwachs; der Hof bereitet sich in Ruhe vor.", 4), "Familienplanung vereinbart. Erwarteter Zuwachs in etwa 270 Tagen.")
    }

    private fun familyBlocker(state: GameState): String? = when {
        !state.settings.dynasty -> "Dynastie ist ausgeschaltet."
        state.settings.romance == RomanceMode.OFF -> "Familienplanung in einer Partnerschaft benötigt aktivierte Romanze. Adoption bleibt unabhängig möglich."
        !state.companion.met || state.player.age < 18 || state.companion.age < 18 -> "Familienplanung ist ausschließlich für Erwachsene möglich."
        state.relationship.romanceStage < RomanceStage.PARTNERSHIP || !state.relationship.consent.romanceAllowed || !state.relationship.consent.wantsChildren || "no_children" in state.relationship.consent.boundaries -> "Eine freiwillige Partnerschaft und gemeinsamer Kinderwunsch sind erforderlich."
        state.relationship.conflict > 20 || state.companion.trust < 80 || state.relationship.commitment < 60 -> "Heute besteht kein gemeinsames Einverständnis zur Familienplanung."
        state.battleSession?.isActive == true || state.commanderAway(COMPANION_COMMANDER_ID) || state.war.unavailableCommander(COMPANION_COMMANDER_ID) || state.war.playerCondition != CombatantStatus.ACTIVE -> "Familienplanung wartet bis beide Personen gesund und frei verfügbar sind."
        else -> null
    }

    /** A chosen, adopted heir keeps non-romantic campaigns viable. No intimacy event is required. */
    fun adoptHeir(state: GameState, name: String = "Hoffnung"): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        if (state.player.age < 18 || state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Adoption benötigt einen erwachsenen, verfügbaren Herrscher.")
        val next = initialize(state)
        if (next.dynasty.members.size >= 14) return GameEngine.ActionResult(state, "Die Familie hat derzeit keinen weiteren Platz.")
        if (next.resources.gold < 300) return GameEngine.ActionResult(state, "Unterkunft und Fürsorge benötigen 300 Gold.")
        val id = nextMemberId(next)
        val member = FamilyMember(id, name.trim().ifBlank { "Hoffnung" }.take(24), next.day, 10, listOf(next.dynasty.rulerId), adopted = true,
            traits = setOf(ChildTrait.CURIOUS, ChildTrait.CALM), lastEducationDay = next.day,
            educationLog = listOf(FamilyDevelopmentEntry(next.day, "Aufgenommen; der Hoflehrer begleitet den neuen Alltag.")))
        val dynasty = next.dynasty.copy(members = next.dynasty.members + member, heirId = next.dynasty.heirId ?: id)
        return GameEngine.ActionResult(chronicle(next.copy(resources = next.resources.copy(gold = next.resources.gold - 300), dynasty = dynasty), "Aufnahme in die Familie", "${member.name} wird als Kind aufgenommen und geschützt; alle romantischen Systeme bleiben für Minderjährige gesperrt."), "${member.name} ist Teil deiner Familie.")
    }

    fun selectHeir(state: GameState, memberId: String): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val next = initialize(state)
        val member = next.dynasty.members.firstOrNull { it.id == memberId && it.alive && it.id != next.dynasty.rulerId }
            ?: return GameEngine.ActionResult(state, "Dieser Erbe ist nicht verfügbar.")
        if (member.id == "companion") return GameEngine.ActionResult(state, "Die Gefährtin ist Partnerin der Regierung. Bestimme einen Nachfolger aus der nächsten Generation.")
        if (member.id == next.dynasty.heirId) return GameEngine.ActionResult(state, "${member.name} ist bereits als Erbe bestimmt.")

        val previous = next.dynasty.members.firstOrNull { it.id == next.dynasty.heirId && it.alive }
        val olderEligible = next.dynasty.members.filter {
            it.alive && it.parents.isNotEmpty() && it.id !in setOf(next.dynasty.rulerId, "companion", member.id) &&
                it.age(next.day) > member.age(next.day)
        }
        var tension = next.dynasty.successionTension
        var concern = next.dynasty.successionConcern
        if (previous != null && previous.id != member.id) {
            val ambition = if (ChildTrait.AMBITIOUS in previous.traits) 12 else 0
            tension = (tension + 18 + ambition + olderEligible.size * 3).coerceAtMost(100)
            concern = "${previous.name} wurde als bisheriger Erbe übergangen${if (ambition > 0) " und gilt als ehrgeizig" else ""}. Der Hof erwartet eine Begründung."
        } else if (previous == null && olderEligible.isNotEmpty()) {
            tension = (tension + 8 + olderEligible.size * 4).coerceAtMost(100)
            concern = "${olderEligible.joinToString { it.name }} ${if (olderEligible.size == 1) "ist älter" else "sind älter"} als der neue Erbe. Teile des Hofes verlangen eine klare Nachfolgebegründung."
        }
        var chosen = next.copy(dynasty = next.dynasty.copy(
            heirId = memberId,
            successionTension = tension,
            successionConcern = concern,
        ))
        chosen = if (next.dynasty.legitimacyCauses.none { it.cause.startsWith("Öffentlich geklärte Nachfolge:") })
            legitimacy(chosen, "Öffentlich geklärte Nachfolge: ${member.name}", if (tension >= 40) 1 else 2) else chosen
        val text = "${member.name} ist nun designierter Erbe${if (member.age(next.day) < 18) "; bei früher Nachfolge übernimmt eine Regentschaft" else ""}." +
            if (tension > 0) " Nachfolgespannung: $tension/100." else ""
        return GameEngine.ActionResult(chronicle(chosen, "Nachfolge bestimmt", text), text)
    }

    fun addressSuccession(state: GameState, method: String): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val tension = state.dynasty.successionTension
        if (tension <= 0) return GameEngine.ActionResult(state, "Die Nachfolge ist derzeit nicht umstritten.")
        var next = state
        val (drop, text) = when (method) {
            "council" -> {
                if (state.resources.gold < 120) return GameEngine.ActionResult(state, "Für einen großen Nachfolgerat fehlen 120 Gold.")
                next = next.copy(
                    resources = next.resources.copy(gold = next.resources.gold - 120),
                    society = next.society.copy(politicalLoyalty = (next.society.politicalLoyalty + 2).coerceAtMost(100)),
                )
                next = legitimacy(next, "Öffentlicher Nachfolgerat schafft Klarheit", 2)
                32 to "Ein großer Nachfolgerat hört Familie, Hof und Ämter an. Die Entscheidung wird öffentlich begründet."
            }
            "family" -> {
                if (!state.companion.met) {
                    18 to "Die Familie spricht ohne Hofzeremoniell über Erwartungen und die künftige Rolle der übrigen Angehörigen."
                } else {
                    next = next.copy(companion = next.companion.copy(respect = (next.companion.respect + 1).coerceAtMost(100)))
                    next = RelationshipEngine.remember(next, "succession_council", "Ihr besprecht die Nachfolge gemeinsam mit der Familie und gebt übergangenen Angehörigen eine klare Rolle.", 2, setOf("Familie","Politik"))
                    22 to "Ihr besprecht die Nachfolge als Familie. Übergangene Angehörige erhalten Gehör und eine klare künftige Rolle."
                }
            }
            "public" -> {
                if (state.resources.gold < 60) return GameEngine.ActionResult(state, "Für die öffentliche Erklärung fehlen 60 Gold.")
                next = next.copy(
                    resources = next.resources.copy(gold = next.resources.gold - 60),
                    city = next.city.copy(satisfaction = (next.city.satisfaction + 1).coerceAtMost(100)),
                    society = next.society.copy(politicalLoyalty = (next.society.politicalLoyalty + 1).coerceAtMost(100)),
                )
                16 to "Die Nachfolge wird vor Stadt und Garnison erklärt. Das schafft Transparenz, löst aber familiäre Enttäuschung nicht vollständig."
            }
            else -> return GameEngine.ActionResult(state, "Unbekannte Nachfolge-Maßnahme.")
        }
        val remaining = (next.dynasty.successionTension - drop).coerceAtLeast(0)
        next = next.copy(dynasty = next.dynasty.copy(
            successionTension = remaining,
            successionConcern = if (remaining <= 10) "" else next.dynasty.successionConcern,
        ))
        next = chronicle(next, "Nachfolge beraten", "$text Spannung: $tension → $remaining.")
        return GameEngine.ActionResult(next, "$text Nachfolgespannung: $remaining/100.")
    }

    fun successionSummary(state: GameState): String {
        val heir = state.dynasty.members.firstOrNull { it.id == state.dynasty.heirId && it.alive }
        val regent = state.commanders.firstOrNull { it.id == state.dynasty.regentCommanderId }
        val base = when {
            heir == null -> "Kein Erbe bestimmt: Nach dem Herrscher folgt das älteste lebende Kind; ohne Kind bestimmt der Rat einen verfügbaren Erwachsenen nach Loyalität und Führung."
            heir.age(state.day) >= 18 -> "${heir.name} folgt als erwachsener Erbe. Ausbildung und Fähigkeiten gehen in die neue Regierung ein."
            else -> "${heir.name} ist ${heir.age(state.day)} Jahre alt. Bis 18 führt ${regent?.name ?: "ein erwachsener Regent oder der Hofrat"} die Regierung. Bei automatischer Wahl entscheiden Loyalität und Führung."
        }
        return if (state.dynasty.successionTension > 0)
            "$base · Nachfolgespannung ${state.dynasty.successionTension}/100: ${state.dynasty.successionConcern.ifBlank { "Der Hof erwartet weitere Klärung." }}"
        else base
    }

    private fun legitimacy(state: GameState, cause: String, delta: Int): GameState {
        val score = (state.dynasty.legitimacy + delta).coerceIn(0, 100)
        val applied = score - state.dynasty.legitimacy
        return state.copy(dynasty = state.dynasty.copy(legitimacy = score,
            legitimacyCauses = (state.dynasty.legitimacyCauses + LegitimacyCause(state.day, cause, applied)).takeLast(80)))
    }

    private fun reviewLegitimacy(state: GameState): GameState {
        var next = state
        if (state.victories > state.dynasty.observedVictories) {
            val victories = (state.victories - state.dynasty.observedVictories).coerceAtMost(5)
            next = legitimacy(next, "$victories neue Siege sichern die Anerkennung der Dynastie", victories * 2)
        }
        next = next.copy(dynasty = next.dynasty.copy(observedVictories = next.victories))
        if (next.day - next.dynasty.lastLegitimacyReviewDay < 30) return next
        next = next.copy(dynasty = next.dynasty.copy(lastLegitimacyReviewDay = next.day))
        return when {
            next.city.satisfaction < 30 || next.resources.food == 0 -> legitimacy(next, "Bürgerkrise: Versorgung oder Zufriedenheit ist gefährdet", -3)
            next.city.satisfaction >= 70 && next.city.security >= 60 && next.dynasty.heirId != null -> legitimacy(next, "Versorgte Stadt und geklärte Nachfolge", 1)
            else -> next
        }
    }

    private fun reviewSuccession(state: GameState): GameState {
        if (state.day - state.dynasty.lastSuccessionReviewDay < 30) return state
        var next = state.copy(dynasty = state.dynasty.copy(lastSuccessionReviewDay = state.day))
        val tension = next.dynasty.successionTension
        if (tension <= 0) return next
        next = when {
            tension >= 60 -> {
                var changed = next.copy(
                    city = next.city.copy(satisfaction = (next.city.satisfaction - 2).coerceAtLeast(0)),
                    society = next.society.copy(politicalLoyalty = (next.society.politicalLoyalty - 2).coerceAtLeast(0)),
                    dynasty = next.dynasty.copy(successionTension = (tension - 2).coerceAtLeast(0)),
                )
                changed = legitimacy(changed, "Offener Nachfolgestreit belastet Hof und Stadt", -2)
                chronicle(changed, "Nachfolge belastet das Reich", next.dynasty.successionConcern.ifBlank { "Der Hof ist über die Nachfolge sichtbar gespalten." })
            }
            tension >= 30 -> {
                var changed = next.copy(
                    city = next.city.copy(satisfaction = (next.city.satisfaction - 1).coerceAtLeast(0)),
                    dynasty = next.dynasty.copy(successionTension = (tension - 3).coerceAtLeast(0)),
                )
                changed = legitimacy(changed, "Ungeklärte Nachfolgefragen schwächen die Anerkennung", -1)
                changed
            }
            else -> next.copy(dynasty = next.dynasty.copy(
                successionTension = (tension - 6).coerceAtLeast(0),
                successionConcern = if (tension <= 6) "" else next.dynasty.successionConcern,
            ))
        }
        return next
    }

    private fun reviewRegency(state: GameState): GameState {
        val ruler = state.dynasty.members.firstOrNull { it.id == state.dynasty.rulerId } ?: return state
        if (ruler.age(state.day) >= 18) return state
        val regent = regentCandidates(state).firstOrNull { it.id == state.dynasty.regentCommanderId }
        if (regent == null) {
            val replacement = regentCandidates(state).maxByOrNull { it.loyalty + it.leadership }
            val reason = if (replacement == null) "Kein verfügbarer loyaler Erwachsener: Der Hofrat trägt die Regentschaft für ${ruler.name}."
                else "${replacement.name} übernimmt die Regentschaft: höchster verfügbarer Wert aus Loyalität (${replacement.loyalty}) und Führung (${replacement.leadership})."
            val changed = state.copy(dynasty = state.dynasty.copy(regentCommanderId = replacement?.id, regencyForMemberId = ruler.id, regencyReason = reason,
                regencyConflict = if (replacement == null) "Der Rat muss ohne festen Regenten Entscheidungen abstimmen." else null))
            return if (state.dynasty.regencyReason != reason) chronicle(changed, "Regentschaft neu geordnet", reason) else changed
        }
        if (state.day - state.dynasty.lastRegencyReviewDay < 30) return state
        val detail = state.court.characters.first { it.commanderId == regent.id }
        val conflict = when {
            regent.loyalty < 60 -> "${regent.name} ist nur ${regent.loyalty}% loyal. Unsichere Unterstützung belastet die Regentschaft."
            detail.ambition >= 70 -> "${regent.name} strebt nach eigenem Einfluss (Ambition ${detail.ambition}). Der Rat beobachtet mögliche Interessenkonflikte."
            else -> null
        }
        val next = state.copy(dynasty = state.dynasty.copy(lastRegencyReviewDay = state.day, regencyConflict = conflict,
            regencyReason = "${regent.name} regiert bis ${ruler.name}s 18. Geburtstag · Loyalität ${regent.loyalty}% · ${detail.personality.label}."))
        return if (regent.loyalty < 60) legitimacy(next, "Unsichere Regententreue: ${regent.name} (${regent.loyalty}%)", -2) else next
    }

    private fun nextMemberId(state: GameState): String {
        var number = state.dynasty.members.size + 1
        while (state.dynasty.members.any { it.id == "family_$number" }) number++
        return "family_$number"
    }

    fun validate(state: GameState) {
        val dynasty = state.dynasty
        dynasty.members.forEach { member ->
            require(listOf(member.diplomacy, member.leadership, member.stewardship, member.medicine, member.tactics, member.mentorBond).all { it in 0..100 }) { "Ungültige Familienfähigkeiten." }
            require(member.lastEducationDay >= -1 && member.traits.size <= 4) { "Ungültige Kinderentwicklung." }
            member.adultCommanderId?.let { id ->
                require(state.commanders.any { it.id == id }) { "Erwachsener Hofcharakter der Familie fehlt." }
                require(member.age(state.day) >= 18) { "Minderjährige dürfen nicht als erwachsene Hofcharaktere auftreten." }
            }
        }
        dynasty.pendingFamilyEvent?.let { event ->
            require(dynasty.members.any { it.id == event.memberId }) { "Familienereignis ohne Familienmitglied." }
            require(event.choices.size >= 3 && event.choices.map { it.id }.distinct().size == event.choices.size) { "Ungültige Familienentscheidung." }
        }
        dynasty.regencyForMemberId?.let { id -> require(dynasty.members.any { it.id == id }) { "Regentschaft ohne Familienmitglied." } }
        require(dynasty.successionTension in 0..100 && dynasty.lastSuccessionReviewDay <= state.day) { "Ungültiger Nachfolgestatus." }
        state.court.socialLinks.filter { it.directed }.forEach { link -> require(link.sourceId == link.firstId || link.sourceId == link.secondId) { "Ungültige Richtung einer Hofbeziehung." } }
    }

    private fun chronicle(state: GameState, title: String, text: String): GameState = state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(2000))
}
