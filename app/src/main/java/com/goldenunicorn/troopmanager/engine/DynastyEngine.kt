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
        return state.copy(dynasty = state.dynasty.copy(members = members, activeSinceDay = state.dynasty.activeSinceDay ?: state.day))
    }

    fun agreeFamilyPlanning(state: GameState): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist in den Einstellungen ausgeschaltet.")
        if (state.player.age < 18 || state.companion.age < 18 || !state.companion.met || state.relationship.romanceStage < RomanceStage.PARTNERSHIP)
            return GameEngine.ActionResult(state, "Familienplanung benötigt zwei Erwachsene in einer freiwilligen Partnerschaft.")
        if (state.relationship.conflict > 15 || state.companion.trust < 85 || state.companion.respect < 70 || "no_children" in state.relationship.consent.boundaries)
            return GameEngine.ActionResult(RelationshipEngine.remember(state, "family_refusal", "${state.companion.name} möchte derzeit keine Familienplanung.", -1), "${state.companion.name} möchte derzeit keine Familienplanung. Diese Grenze wird respektiert.")
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
        val member = FamilyMember(id, name.trim().ifBlank { "Hoffnung" }.take(24), next.day, 10, listOf(next.dynasty.rulerId), adopted = true)
        val dynasty = next.dynasty.copy(members = next.dynasty.members + member, heirId = next.dynasty.heirId ?: id)
        return GameEngine.ActionResult(chronicle(next.copy(resources = next.resources.copy(gold = next.resources.gold - 300), dynasty = dynasty), "Aufnahme in die Familie", "${member.name} wird als Kind aufgenommen und geschützt; alle romantischen Systeme bleiben für Minderjährige gesperrt."), "${member.name} ist Teil deiner Familie.")
    }

    fun selectHeir(state: GameState, memberId: String): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val next = initialize(state)
        val member = next.dynasty.members.firstOrNull { it.id == memberId && it.alive && it.id != next.dynasty.rulerId }
            ?: return GameEngine.ActionResult(state, "Dieser Erbe ist nicht verfügbar.")
        return GameEngine.ActionResult(chronicle(next.copy(dynasty = next.dynasty.copy(heirId = memberId)), "Nachfolge bestimmt", "${member.name} ist nun designierter Erbe${if (member.age(next.day) < 18) "; bei früher Nachfolge übernimmt eine Regentschaft" else ""}."), "Erbe bestimmt.")
    }

    fun tick(state: GameState): GameState {
        if (state.dynasty.lastTickDay >= state.day) return state
        if (!state.settings.dynasty) {
            if (state.dynasty.members.isEmpty()) return state
            val paused = (state.day - state.dynasty.lastTickDay).coerceAtLeast(0)
            return state.copy(dynasty = state.dynasty.copy(lastTickDay = state.day,
                members = state.dynasty.members.map { it.copy(bornDay = it.bornDay + paused) },
                plannedBirthDay = state.dynasty.plannedBirthDay?.plus(paused)),
                court = state.court.copy(characters = state.court.characters.map { it.copy(lastAgedDay = it.lastAgedDay + paused) }))
        }
        var next = initialize(state)
        var members = next.dynasty.members
        val ruler = members.first { it.id == next.dynasty.rulerId }
        val companion = members.firstOrNull { it.id == "companion" }
        next = next.copy(player = next.player.copy(age = ruler.age(next.day)),
            companion = if (companion != null) next.companion.copy(age = companion.age(next.day)) else next.companion,
            dynasty = next.dynasty.copy(lastTickDay = next.day, lastAgingDay = next.day),
            court = next.court.copy(characters = next.court.characters.map { d ->
                val years = ((next.day - d.lastAgedDay).coerceAtLeast(0) / 365)
                if (d.commanderId == COMPANION_COMMANDER_ID && companion != null) d.copy(age = companion.age(next.day), lastAgedDay = d.lastAgedDay + years * 365)
                else d.copy(age = (d.age + years).coerceAtMost(150), lastAgedDay = d.lastAgedDay + years * 365)
            }))
        if (next.dynasty.plannedBirthDay != null) {
            val parentsValid = next.dynasty.planningParents.all { id -> members.any { it.id == id && it.alive && it.age(next.day) >= 18 } }
            if (!parentsValid || next.relationship.romanceStage < RomanceStage.PARTNERSHIP || !next.relationship.consent.wantsChildren) {
                next = next.copy(dynasty = next.dynasty.copy(plannedBirthDay = null, planningParents = emptyList()))
            } else if (next.day >= next.dynasty.plannedBirthDay!!) {
                val member = FamilyMember(nextMemberId(next), "Kind ${members.count { it.parents.isNotEmpty() } + 1}", next.day, parents = next.dynasty.planningParents)
                members = members + member
                next = chronicle(next.copy(dynasty = next.dynasty.copy(members = members, heirId = next.dynasty.heirId ?: member.id,
                    plannedBirthDay = null, planningParents = emptyList(), legitimacy = (next.dynasty.legitimacy + 5).coerceAtMost(100))), "Familienzuwachs", "${member.name} kommt in die Familie. Die Chronik bewahrt diesen Tag.")
                next = RelationshipEngine.remember(next, "child_arrival", "${member.name} verändert euren gemeinsamen Alltag.", 5)
            }
        }
        val deaths = next.dynasty.members.filter { it.alive && it.age(next.day) >= 85 }
        if (deaths.isNotEmpty()) {
            val ids = deaths.map { it.id }.toSet()
            next = next.copy(dynasty = next.dynasty.copy(members = next.dynasty.members.map { if (it.id in ids) it.copy(alive = false, deathDay = next.day) else it }))
            deaths.forEach { dead -> next = chronicle(next, "Tod in der Familie", "${dead.name} stirbt im Alter von ${dead.age(next.day)} Jahren.") }
            if ("companion" in ids) {
                next = RelationshipEngine.remember(next, "bereavement", "Du trauerst um ${next.companion.name}.", -10)
                next = next.copy(relationship = next.relationship.copy(romanceStage = RomanceStage.NONE, commitment = 0, intimacyConsentDay = null,
                    pendingEvent = null, consent = next.relationship.consent.copy(romanceAllowed = false, intimacyAllowed = false, wantsChildren = false)),
                    war = next.war.copy(commanderConditions = next.war.commanderConditions.filterNot { it.commanderId == COMPANION_COMMANDER_ID } + CommanderCondition(COMPANION_COMMANDER_ID, CombatantStatus.DEAD)),
                    court = next.court.copy(characters = next.court.characters.map { if (it.commanderId == COMPANION_COMMANDER_ID) it.copy(alive = false) else it }, offices = next.court.offices.filterValues { it != COMPANION_COMMANDER_ID }))
            }
        }
        if (next.dynasty.members.first { it.id == next.dynasty.rulerId }.alive.not() || next.war.playerCondition == CombatantStatus.DEAD)
            next = succeed(next)
        val retired = next.court.characters.filter { it.alive && it.age >= 85 }.map { it.commanderId }.toSet()
        if (retired.isNotEmpty()) {
            next = next.copy(court = next.court.copy(characters = next.court.characters.map { if (it.commanderId in retired) it.copy(alive = false, serviceHistory = (it.serviceHistory + CareerEntry(next.day, "death", "Nach langer Dienstzeit im Alter verstorben.")).takeLast(80)) else it },
                offices = next.court.offices.filterValues { it !in retired }),
                war = next.war.copy(commanderConditions = next.war.commanderConditions.filterNot { it.commanderId in retired } + retired.map { CommanderCondition(it, CombatantStatus.DEAD) }))
            next = chronicle(next, "Abschied vom Hof", "${next.commanders.filter { it.id in retired }.joinToString { it.name }} sterben nach langer Dienstzeit.")
        }
        val heir = next.dynasty.members.firstOrNull { it.id == next.dynasty.heirId }
        if (heir?.alive == false) next = next.copy(dynasty = next.dynasty.copy(heirId = next.dynasty.members.filter { it.alive && it.id != next.dynasty.rulerId && it.id != "companion" }.maxByOrNull { it.age(next.day) }?.id))
        val current = next.dynasty.members.first { it.id == next.dynasty.rulerId }
        if (current.age(next.day) >= 18 && next.dynasty.regentCommanderId != null) next = chronicle(next.copy(dynasty = next.dynasty.copy(regentCommanderId = null)), "Regentschaft endet", "${current.name} übernimmt selbst die Regierung.")
        if (next.day % 7 == 0) {
            val regent = next.commanders.firstOrNull { it.id == next.dynasty.regentCommanderId }
            val control = (next.dynasty.legitimacy - 50) / 25
            next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction + control).coerceIn(0, 100)),
                realm = if (regent != null && regent.loyalty >= 40) next.realm.copy(tradeBonusDays = maxOf(next.realm.tradeBonusDays, 2)) else next.realm)
        }
        return RelationshipEngine.normalize(next)
    }

    private fun succeed(state: GameState): GameState {
        val old = state.dynasty.members.first { it.id == state.dynasty.rulerId }
        val eligible = state.dynasty.members.filter { it.alive && it.id != old.id && it.id != "companion" }
        var heir = eligible.firstOrNull { it.id == state.dynasty.heirId } ?: eligible.maxByOrNull { it.age(state.day) }
        var members = state.dynasty.members.map { if (it.id == old.id) it.copy(alive = false, deathDay = state.day) else it }
        if (heir == null) {
            // A childless ruler is followed by a council-appointed adult, preserving the campaign.
            val commander = state.commanders.filter { c -> state.court.characters.any { it.commanderId == c.id && it.alive && it.age >= 18 } && !state.war.unavailableCommander(c.id) }.maxByOrNull { it.loyalty + it.leadership }
            val id = "successor_${state.dynasty.successionCount + 1}"
            heir = FamilyMember(id, commander?.name ?: "Hüter des Reiches", state.day,
                state.court.characters.firstOrNull { it.commanderId == commander?.id }?.age ?: 30, diplomacy = 35, leadership = commander?.leadership ?: 35)
            members = members + heir
        }
        val successor = heir
        val age = successor.age(state.day)
        val regent = if (age < 18) state.commanders.filter { c -> c.loyalty >= 40 && state.court.characters.any { it.commanderId == c.id && it.alive && it.age >= 18 } && !state.war.unavailableCommander(c.id) }.maxByOrNull { it.loyalty + it.leadership }?.id else null
        var next = state.copy(player = state.player.copy(name = successor.name, age = age, level = 1, experience = 0, skillPoints = 0,
            diplomacy = successor.diplomacy, leadership = successor.leadership),
            relationship = state.relationship.copy(romanceStage = RomanceStage.NONE, commitment = 0, attraction = 0, intimacy = 0, intimacyConsentDay = null,
                pendingEvent = null, consent = state.relationship.consent.copy(romanceAllowed = false, intimacyAllowed = false, wantsChildren = false)),
            court = state.court.copy(perks = emptySet()),
            war = state.war.copy(playerCondition = CombatantStatus.ACTIVE),
            dynasty = state.dynasty.copy(members = members, rulerId = successor.id, heirId = null, regentCommanderId = regent,
                plannedBirthDay = null, planningParents = emptyList(), successionCount = state.dynasty.successionCount + 1,
                legitimacy = (state.dynasty.legitimacy - if (age < 18) 15 else 5).coerceAtLeast(0)))
        next = chronicle(next, "Nachfolge", "${old.name} wird von ${successor.name} abgelöst.${if (age < 18) " Ein erwachsener Rat übernimmt die Regentschaft." else ""}")
        return next
    }

    private fun nextMemberId(state: GameState): String {
        var number = state.dynasty.members.size + 1
        while (state.dynasty.members.any { it.id == "family_$number" }) number++
        return "family_$number"
    }

    private fun chronicle(state: GameState, title: String, text: String): GameState = state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(2000))
}
