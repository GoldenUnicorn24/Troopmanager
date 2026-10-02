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
        val chosen = next.copy(dynasty = next.dynasty.copy(heirId = memberId))
        val recognized = if (next.dynasty.legitimacyCauses.none { it.cause.startsWith("Öffentlich geklärte Nachfolge:") })
            legitimacy(chosen, "Öffentlich geklärte Nachfolge: ${member.name}", 2) else chosen
        return GameEngine.ActionResult(chronicle(recognized, "Nachfolge bestimmt", "${member.name} ist nun designierter Erbe${if (member.age(next.day) < 18) "; bei früher Nachfolge übernimmt eine Regentschaft" else ""}."), "Erbe bestimmt.")
    }

    fun tick(state: GameState): GameState {
        if (state.dynasty.lastTickDay >= state.day) return state
        if (!state.settings.dynasty) {
            if (state.dynasty.members.isEmpty()) return state
            val paused = (state.day - state.dynasty.lastTickDay).coerceAtLeast(0)
            return state.copy(dynasty = state.dynasty.copy(lastTickDay = state.day,
                members = state.dynasty.members.map { if (it.alive) it.copy(bornDay = it.bornDay + paused,
                    lastEducationDay = if (it.lastEducationDay >= 0) it.lastEducationDay + paused else -1) else it },
                plannedBirthDay = state.dynasty.plannedBirthDay?.plus(paused),
                lastFamilyEventDay = state.dynasty.lastFamilyEventDay + paused,
                familyEventCooldowns = state.dynasty.familyEventCooldowns.mapValues { it.value + paused },
                lastLegitimacyReviewDay = state.dynasty.lastLegitimacyReviewDay + paused,
                lastRegencyReviewDay = state.dynasty.lastRegencyReviewDay + paused),
                court = state.court.copy(characters = state.court.characters.map { it.copy(lastAgedDay = it.lastAgedDay + paused) }))
        }
        var next = initialize(state)
        // Adult children remain the same people in the family and in military/court APIs.
        next = next.copy(dynasty = next.dynasty.copy(members = next.dynasty.members.map { member ->
            val dead = member.adultCommanderId?.let { id -> next.war.commanderConditions.any { it.commanderId == id && it.status == CombatantStatus.DEAD } ||
                next.court.characters.any { it.commanderId == id && !it.alive } } == true
            if (member.alive && dead) member.copy(alive = false, deathDay = next.day) else member
        }))
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
                val names = listOf("Mira", "Elian", "Leander", "Liora", "Rian", "Serena", "Arvid", "Neria", "Lenn", "Talia", "Eren", "Amira")
                val member = FamilyMember(nextMemberId(next), next.dynasty.plannedChildName.trim().ifBlank { names[members.count { it.parents.isNotEmpty() } % names.size] }.take(24),
                    next.day, parents = next.dynasty.planningParents, traits = setOf(ChildTrait.CURIOUS), lastEducationDay = next.day,
                    educationLog = listOf(FamilyDevelopmentEntry(next.day, "Familienzuwachs; ein eigener Name kann im Profil vergeben werden.")))
                members = members + member
                next = chronicle(legitimacy(next.copy(dynasty = next.dynasty.copy(members = members, heirId = next.dynasty.heirId ?: member.id,
                    plannedBirthDay = null, planningParents = emptyList(), plannedChildName = "")), "Familienzuwachs und gesicherte nächste Generation", 5), "Familienzuwachs", "${member.name} kommt in die Familie. Die Chronik bewahrt diesen Tag.")
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
        if (current.age(next.day) >= 18 && next.dynasty.regentCommanderId != null &&
            (next.dynasty.regencyForMemberId == null || next.dynasty.regencyForMemberId == current.id))
            next = chronicle(next.copy(dynasty = next.dynasty.copy(regentCommanderId = null, regencyForMemberId = null, regencyReason = "", regencyConflict = null)), "Regentschaft endet", "${current.name} übernimmt selbst die Regierung.")
        if (next.day % 7 == 0) {
            val regent = if (current.age(next.day) < 18) next.commanders.firstOrNull { it.id == next.dynasty.regentCommanderId } else null
            val control = (next.dynasty.legitimacy - 50) / 25
            next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction + control).coerceIn(0, 100)),
                realm = if (regent != null && regent.loyalty >= 40) next.realm.copy(tradeBonusDays = maxOf(next.realm.tradeBonusDays, 2)) else next.realm)
        }
        next = developChildren(next)
        next = reviewLegitimacy(next)
        next = reviewRegency(next)
        next = offerFamilyEvent(next)
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
        val candidates = regentCandidates(state)
        val regent = if (age < 18) candidates.firstOrNull { it.id == state.dynasty.regentCommanderId }?.id
            ?: candidates.maxByOrNull { it.loyalty + it.leadership }?.id else null
        var next = state.copy(player = state.player.copy(name = successor.name, age = age, level = 1, experience = 0, skillPoints = 0,
            diplomacy = successor.diplomacy, leadership = successor.leadership, tactics = successor.tactics),
            relationship = state.relationship.copy(romanceStage = RomanceStage.NONE, commitment = 0, attraction = 0, intimacy = 0, intimacyConsentDay = null,
                pendingEvent = null, consent = state.relationship.consent.copy(romanceAllowed = false, intimacyAllowed = false, wantsChildren = false)),
            court = state.court.copy(perks = emptySet(), characters = state.court.characters.map { if (it.commanderId == old.adultCommanderId) it.copy(alive = false) else it },
                offices = state.court.offices.filterValues { it != old.adultCommanderId }),
            war = state.war.copy(playerCondition = CombatantStatus.ACTIVE,
                commanderConditions = if (old.adultCommanderId != null) state.war.commanderConditions.filterNot { it.commanderId == old.adultCommanderId } + CommanderCondition(old.adultCommanderId, CombatantStatus.DEAD) else state.war.commanderConditions),
            dynasty = state.dynasty.copy(members = members, rulerId = successor.id, heirId = null, regentCommanderId = regent,
                plannedBirthDay = null, planningParents = emptyList(), successionCount = state.dynasty.successionCount + 1,
                regencyReason = if (age < 18) "${successor.name} ist $age Jahre alt. ${state.commanders.firstOrNull { it.id == regent }?.name ?: "Der erwachsene Hofrat"} führt bis zum 18. Geburtstag die Regierung." else "",
                regencyForMemberId = if (age < 18) successor.id else null,
                regencyConflict = null))
        next = legitimacy(next, if (age < 18) "Minderjährige Nachfolge braucht eine Regentschaft" else "Geordneter Herrscherwechsel", if (age < 18) -15 else -5)
        next = chronicle(next, "Nachfolge", "${old.name} wird von ${successor.name} abgelöst.${if (age < 18) " Ein erwachsener Rat übernimmt die Regentschaft." else ""}")
        return next
    }

    fun setBirthName(state: GameState, name: String): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val clean = name.trim().take(24)
        if (clean.isBlank()) return GameEngine.ActionResult(state, "Bitte gib einen Namen ein.")
        return GameEngine.ActionResult(state.copy(dynasty = state.dynasty.copy(plannedChildName = clean)), "Der Name $clean ist für den Familienzuwachs vorgemerkt.")
    }

    fun renameMember(state: GameState, memberId: String, name: String): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val member = state.dynasty.members.firstOrNull { it.id == memberId && it.alive && it.parents.isNotEmpty() }
            ?: return GameEngine.ActionResult(state, "Dieses Familienmitglied kann hier nicht umbenannt werden.")
        val clean = name.trim().take(24)
        if (clean.isBlank()) return GameEngine.ActionResult(state, "Bitte gib einen Namen ein.")
        val next = state.copy(dynasty = state.dynasty.copy(members = state.dynasty.members.map { if (it.id == memberId) it.copy(name = clean) else it }),
            player = if (state.dynasty.rulerId == memberId) state.player.copy(name = clean) else state.player,
            commanders = state.commanders.map { if (it.id == member.adultCommanderId) it.copy(name = clean) else it })
        return GameEngine.ActionResult(next, "Das Familienprofil heißt jetzt $clean.")
    }

    fun mentorOptions(state: GameState): List<Pair<String, String>> = listOf("player" to "${state.player.name} · Herrscher", "teacher" to "Hoflehrer") +
        (if (state.companion.met && state.companion.age >= 18 && state.dynasty.members.none { it.id == "companion" && !it.alive }) listOf("companion" to state.companion.name) else emptyList()) +
        state.commanders.filter { c -> c.id != COMPANION_COMMANDER_ID && c.id != state.dynasty.members.firstOrNull { it.id == state.dynasty.rulerId }?.adultCommanderId && state.court.characters.any { it.commanderId == c.id && it.alive && it.age >= 18 } }
            .map { "commander:${it.id}" to it.name }

    fun mentorName(state: GameState, mentorId: String): String = mentorOptions(state).firstOrNull { it.first == mentorId }?.second ?: "Ehemaliger Mentor"

    fun mentorBlocker(state: GameState, mentorId: String): String? = when {
        mentorId == "teacher" -> null
        mentorId == "player" && (state.player.age < 18 || state.war.playerCondition != CombatantStatus.ACTIVE || state.battleSession?.isActive == true) -> "Der Herrscher ist derzeit nicht als Mentor verfügbar."
        mentorId == "player" -> null
        mentorId == "companion" && (!state.companion.met || state.companion.age < 18 || state.commanderAway(COMPANION_COMMANDER_ID) || state.war.unavailableCommander(COMPANION_COMMANDER_ID) || state.dynasty.members.any { it.id == "companion" && !it.alive }) -> "Die Gefährtin ist derzeit nicht als Mentorin verfügbar."
        mentorId == "companion" -> null
        mentorId.startsWith("commander:") -> {
            val id = mentorId.substringAfter(":").toLongOrNull()
            if (id == null || state.commanders.none { it.id == id } || state.court.characters.none { it.commanderId == id && it.alive && it.age >= 18 }) "Ein erwachsener, lebender Mentor wird benötigt."
            else if (state.commanderAway(id) || state.war.unavailableCommander(id)) "Dieser Mentor ist auf Einsatz, verwundet oder gefangen."
            else null
        }
        else -> "Dieser Mentor ist nicht verfügbar."
    }

    fun assignMentor(state: GameState, memberId: String, mentorId: String): GameEngine.ActionResult {
        val member = educateableChild(state, memberId) ?: return GameEngine.ActionResult(state, "Mentoren begleiten Kinder von 6 bis 17 Jahren bei aktiver Dynastie.")
        mentorBlocker(state, mentorId)?.let { return GameEngine.ActionResult(state, it) }
        if (member.mentorId == mentorId) return GameEngine.ActionResult(state, "Dieser Mentor begleitet das Kind bereits.")
        val text = "${mentorName(state, mentorId)} übernimmt die Begleitung von ${member.name}."
        val next = replaceMember(state, member.copy(mentorId = mentorId, educationLog = (member.educationLog + FamilyDevelopmentEntry(state.day, text)).takeLast(60)))
        return GameEngine.ActionResult(chronicle(next, "Mentor bestimmt", text), text)
    }

    fun chooseEducation(state: GameState, memberId: String, path: EducationPath): GameEngine.ActionResult {
        val member = educateableChild(state, memberId) ?: return GameEngine.ActionResult(state, "Ausbildungswege stehen Kindern von 6 bis 17 Jahren offen.")
        if (member.educationPath == path) return GameEngine.ActionResult(state, "Dieser Ausbildungsweg ist bereits gewählt.")
        val text = "${member.name} lernt künftig ${path.label}. Jährliche Entwicklung verbindet diesen Weg mit den Fähigkeiten des Mentors."
        var next = replaceMember(state, member.copy(educationPath = path, educationLog = (member.educationLog + FamilyDevelopmentEntry(state.day, text)).takeLast(60)))
        if (state.companion.met && state.dynasty.members.none { it.id == "companion" && !it.alive }) {
            val preferred = when (state.relationship.personality.priorities.firstOrNull()) {
                CompanionPriority.WOUNDED -> EducationPath.MEDICINE
                CompanionPriority.DEFENSE, CompanionPriority.EXPANSION -> EducationPath.MILITARY
                CompanionPriority.DIPLOMACY, CompanionPriority.INTEGRATION, CompanionPriority.RECOGNITION -> EducationPath.DIPLOMACY
                else -> EducationPath.ADMINISTRATION
            }
            val opinion = if (preferred == path) "${state.companion.name}: ${path.label} passt zu den Aufgaben, die mir wichtig sind. Lassen wir ${member.name} auch eigene Fragen stellen."
                else "${state.companion.name}: Ich hätte ${preferred.label} bevorzugt. ${path.label} ist ein eigener Weg; wir sollten ${member.name} zuhören und die Entwicklung gemeinsam begleiten."
            next = next.copy(dynasty = next.dynasty.copy(companionFamilyOpinion = opinion))
        }
        return GameEngine.ActionResult(chronicle(next, "Ausbildung gewählt", text), text)
    }

    private fun educateableChild(state: GameState, memberId: String): FamilyMember? =
        if (!state.settings.dynasty) null else state.dynasty.members.firstOrNull { it.id == memberId && it.alive && it.parents.isNotEmpty() && it.age(state.day) in 6..17 }

    private fun replaceMember(state: GameState, member: FamilyMember): GameState = state.copy(dynasty = state.dynasty.copy(
        members = state.dynasty.members.map { if (it.id == member.id) member else it }))

    fun eligibleFamilyEvents(state: GameState, member: FamilyMember): List<FamilyEventTemplate> =
        FamilyEducationCatalog.events.filter { event -> member.alive && member.parents.isNotEmpty() && member.age(state.day) in event.minAge..event.maxAge &&
            state.day - (state.dynasty.familyEventCooldowns["${member.id}:${event.id}"] ?: -365) >= 365 &&
            (event.id != "heir_responsibility" || member.id == state.dynasty.heirId || member.id == state.dynasty.rulerId) }

    private fun offerFamilyEvent(state: GameState): GameState {
        val pending = state.dynasty.pendingFamilyEvent
        if (pending != null) {
            val child = state.dynasty.members.firstOrNull { it.id == pending.memberId && it.alive }
            val template = FamilyEducationCatalog.events.firstOrNull { it.id == pending.id }
            if (child != null && template != null && child.age(state.day) in template.minAge..template.maxAge) return state
            return offerFamilyEvent(state.copy(dynasty = state.dynasty.copy(pendingFamilyEvent = null)))
        }
        if (state.day - state.dynasty.lastFamilyEventDay < 30) return state
        val candidates = state.dynasty.members.flatMap { child -> eligibleFamilyEvents(state, child).map { child to it } }
        if (candidates.isEmpty()) return state
        // Prefer events that have never been told, then rotate children deterministically.
        val untold = candidates.filter { (_, event) -> state.dynasty.familyEventHistory.none { it.eventId == event.id } }.ifEmpty { candidates }
        val (child, event) = untold[(state.day / 30) % untold.size]
        return state.copy(dynasty = state.dynasty.copy(lastFamilyEventDay = state.day,
            pendingFamilyEvent = FamilyEducationEvent(event.id, child.id, event.title, event.text.replace("{child}", child.name), state.day, event.choices)))
    }

    fun chooseFamilyEvent(state: GameState, choiceId: String): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val event = state.dynasty.pendingFamilyEvent ?: return GameEngine.ActionResult(state, "Es steht kein Familienereignis an.")
        val member = state.dynasty.members.firstOrNull { it.id == event.memberId && it.alive }
            ?: return GameEngine.ActionResult(state.copy(dynasty = state.dynasty.copy(pendingFamilyEvent = null)), "Das Familienereignis ist nicht mehr verfügbar.")
        val template = FamilyEducationCatalog.events.firstOrNull { it.id == event.id }
        if (template == null || member.age(state.day) !in template.minAge..template.maxAge) return GameEngine.ActionResult(
            state.copy(dynasty = state.dynasty.copy(pendingFamilyEvent = null)), "Das Kind hat inzwischen eine andere Lebensphase erreicht.")
        val choice = event.choices.firstOrNull { it.id == choiceId } ?: return GameEngine.ActionResult(state, "Diese Antwort ist nicht verfügbar.")
        if (state.resources.gold < choice.goldCost) return GameEngine.ActionResult(state, "Für diese Hilfe fehlen ${choice.goldCost} Gold.")
        val grown = member.copy(diplomacy = (member.diplomacy + choice.diplomacy).coerceIn(0, 100),
            leadership = (member.leadership + choice.leadership).coerceIn(0, 100), stewardship = (member.stewardship + choice.stewardship).coerceIn(0, 100),
            medicine = (member.medicine + choice.medicine).coerceIn(0, 100), tactics = (member.tactics + choice.tactics).coerceIn(0, 100),
            traits = (member.traits + listOfNotNull(choice.trait)).toList().takeLast(4).toSet(),
            mentorBond = (member.mentorBond + choice.mentorBond).coerceIn(0, 100),
            educationLog = (member.educationLog + FamilyDevelopmentEntry(state.day, "${event.title}: ${choice.consequence}")).takeLast(60))
        var next = replaceMember(state, grown)
        if (member.id == next.dynasty.rulerId) next = next.copy(player = next.player.copy(
            leadership = grown.leadership, diplomacy = grown.diplomacy, tactics = grown.tactics))
        next = next.copy(resources = next.resources.copy(gold = next.resources.gold - choice.goldCost), dynasty = next.dynasty.copy(
            pendingFamilyEvent = null, familyEventCooldowns = next.dynasty.familyEventCooldowns + ("${member.id}:${event.id}" to state.day),
            familyEventHistory = (next.dynasty.familyEventHistory + FamilyEventOutcome(state.day, event.id, member.id, choice.consequence)).takeLast(120)))
        if (choice.legitimacy != 0) next = legitimacy(next, "${event.title}: ${choice.consequence}", choice.legitimacy)
        next = chronicle(next, event.title, "${member.name}: ${choice.consequence}")
        return GameEngine.ActionResult(next, choice.consequence)
    }

    private fun developChildren(state: GameState): GameState {
        var next = state
        state.dynasty.members.filter { it.alive && it.parents.isNotEmpty() }.forEach { original ->
            var member = next.dynasty.members.first { it.id == original.id }
            val learningStart = maxOf(member.lastEducationDay, member.bornDay + ((6 - member.initialAge).coerceAtLeast(0) * 365))
            val adultDay = member.bornDay + ((18 - member.initialAge).coerceAtLeast(0) * 365)
            val learningEnd = minOf(next.day, adultDay)
            val years = ((learningEnd - learningStart).coerceAtLeast(0) / 365).coerceAtMost(12)
            if (years > 0 && member.age(next.day) >= 6 && member.adultCommanderId == null) {
                val available = mentorBlocker(next, member.mentorId) == null
                val mentorStats = when {
                    !available -> listOf(25, 25, 25, 25, 25)
                    member.mentorId == "player" -> listOf(next.player.leadership, next.player.diplomacy, 35, 25, next.player.tactics)
                    member.mentorId == "companion" -> listOf(next.companion.leadership, next.companion.diplomacy, 40, 55, next.companion.tactics)
                    member.mentorId.startsWith("commander:") -> {
                        val id = member.mentorId.substringAfter(":").toLongOrNull()
                        val c = next.commanders.firstOrNull { it.id == id }
                        val d = next.court.characters.firstOrNull { it.commanderId == id }
                        listOf(c?.leadership ?: 25, d?.diplomacy ?: 25, d?.stewardship ?: 25, d?.medicine ?: 25, c?.tactics ?: 25)
                    }
                    else -> listOf(35, 45, 45, 45, 35)
                }
                val path = member.educationPath
                val mentorTrait = when {
                    !available -> null
                    member.mentorId == "teacher" -> ChildTrait.CURIOUS
                    member.mentorId == "player" -> if (next.player.diplomacy >= next.player.leadership) ChildTrait.CALM else ChildTrait.BRAVE
                    member.mentorId == "companion" -> if (CompanionTrait.COMPASSIONATE in next.relationship.personality.traits) ChildTrait.COMPASSIONATE else ChildTrait.BRAVE
                    else -> when (next.court.characters.firstOrNull { it.commanderId == member.mentorId.substringAfter(":").toLongOrNull() }?.personality) {
                        CharacterPersonality.AMBITIOUS -> ChildTrait.AMBITIOUS
                        CharacterPersonality.COMPASSIONATE -> ChildTrait.COMPASSIONATE
                        CharacterPersonality.DISCIPLINED -> ChildTrait.PATIENT
                        CharacterPersonality.CAUTIOUS -> ChildTrait.CALM
                        CharacterPersonality.MERCANTILE -> ChildTrait.CURIOUS
                        else -> ChildTrait.BRAVE
                    }
                }
                val pathBonus = listOf(if (path == EducationPath.MILITARY) 3 else 0, if (path == EducationPath.DIPLOMACY) 3 else 0,
                    if (path == EducationPath.ADMINISTRATION) 3 else 0, if (path == EducationPath.MEDICINE) 3 else 0, if (path == EducationPath.MILITARY) 3 else 0)
                fun gain(index: Int) = years * (1 + pathBonus[index] + mentorStats[index] / 30 + if (available && member.mentorBond >= 70) 1 else 0)
                val note = "${member.name}: $years Ausbildungsjahr${if (years > 1) "e" else ""} · ${path?.label ?: "Grundbildung"} · ${if (available) mentorName(next, member.mentorId) else "Hoflehrer vertritt den abwesenden Mentor"}."
                member = member.copy(leadership = (member.leadership + gain(0)).coerceAtMost(100), diplomacy = (member.diplomacy + gain(1)).coerceAtMost(100),
                    stewardship = (member.stewardship + gain(2)).coerceAtMost(100), medicine = (member.medicine + gain(3)).coerceAtMost(100), tactics = (member.tactics + gain(4)).coerceAtMost(100),
                    traits = (member.traits + listOfNotNull(mentorTrait)).toList().takeLast(4).toSet(),
                    lastEducationDay = learningStart + years * 365, educationLog = (member.educationLog + FamilyDevelopmentEntry(next.day, note)).takeLast(60))
                next = replaceMember(next, member)
                if (member.id == next.dynasty.rulerId) next = next.copy(player = next.player.copy(
                    leadership = member.leadership, diplomacy = member.diplomacy, tactics = member.tactics))
            }
            if (member.age(next.day) >= 18 && member.adultCommanderId == null && member.id != next.dynasty.rulerId) next = enterAdultCourt(next, member)
        }
        return next
    }

    private fun enterAdultCourt(state: GameState, member: FamilyMember): GameState {
        val id = (state.commanders.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(0) + 1
        val commander = Commander(id, member.name, Culture.HUMAN, "knight", sword = (30 + member.tactics / 2).coerceAtMost(100),
            bow = 30, leadership = member.leadership, tactics = member.tactics, loyalty = 75,
            trait = member.traits.firstOrNull()?.label ?: "Bedacht")
        fun ancestors(currentId: String, seen: Set<String> = emptySet()): Set<String> {
            if (currentId in seen) return emptySet()
            val person = state.dynasty.members.firstOrNull { it.id == currentId } ?: return setOf(currentId)
            return setOf(currentId) + person.parents.flatMap { ancestors(it, seen + currentId) }
        }
        val detail = CharacterDetails(id, age = member.age(state.day), origin = "Dynastie", family = ancestors(member.id).toList(),
            personality = when {
                ChildTrait.COMPASSIONATE in member.traits -> CharacterPersonality.COMPASSIONATE
                ChildTrait.AMBITIOUS in member.traits -> CharacterPersonality.AMBITIOUS
                ChildTrait.CALM in member.traits -> CharacterPersonality.CAUTIOUS
                else -> CharacterPersonality.HONORABLE
            }, courage = if (ChildTrait.BRAVE in member.traits) 70 else 50, diplomacy = member.diplomacy,
            stewardship = member.stewardship, medicine = member.medicine, consent = ConsentProfile(romanceAllowed = true),
            serviceHistory = listOf(CareerEntry(state.day, "adult", "Mit 18 Jahren tritt ${member.name} als erwachsener Hofcharakter ein; Ausbildung: ${member.educationPath?.label ?: "Grundbildung"}.")),
            lastAgedDay = state.day)
        var next = replaceMember(state, member.copy(adultCommanderId = id, educationLog = (member.educationLog + FamilyDevelopmentEntry(state.day, "Eintritt in den erwachsenen Hof.")).takeLast(60)))
        next = next.copy(commanders = next.commanders + commander, court = next.court.copy(characters = next.court.characters + detail))
        val mentorId = when { member.mentorId == "companion" -> COMPANION_COMMANDER_ID; member.mentorId.startsWith("commander:") -> member.mentorId.substringAfter(":").toLongOrNull(); else -> null }
        if (mentorId != null && next.court.characters.any { it.commanderId == mentorId && it.alive })
            next = addFamilyFriendship(next, id, mentorId, "Vertrauen aus der Ausbildung bei ${mentorName(next, member.mentorId)}", directed = true)
        next.dynasty.members.filter { sibling -> sibling.id != member.id && sibling.adultCommanderId != null && sibling.alive && sibling.parents.any { it in member.parents } }.forEach { sibling ->
            next = addFamilyFriendship(next, id, sibling.adultCommanderId!!, "Gemeinsame Kindheit in der Dynastie")
        }
        return chronicle(next, "Ein eigener Platz am Hof", "${member.name} ist jetzt erwachsen. Ausbildung, Eigenschaften und gewachsene Beziehungen bestimmen den Start am Hof.")
    }

    private fun addFamilyFriendship(state: GameState, first: Long, second: Long, cause: String, directed: Boolean = false): GameState {
        if (first == second) return state
        val pair = listOf(first, second).sorted()
        val old = state.court.socialLinks.firstOrNull { it.firstId == pair[0] && it.secondId == pair[1] }
        if (old != null) return state
        val link = NpcSocialLink(pair[0], pair[1], SocialKind.FRIENDSHIP, state.day, 35, cause, directed = directed, sourceId = first)
        return state.copy(court = state.court.copy(socialLinks = (state.court.socialLinks + link).takeLast(200), characters = state.court.characters.map {
            when (it.commanderId) { first -> it.copy(friends = it.friends + second); second -> if (directed) it else it.copy(friends = it.friends + first); else -> it }
        }))
    }

    fun regentCandidates(state: GameState): List<Commander> = state.commanders.filter { c -> c.loyalty >= 40 && c.id != state.dynasty.members.firstOrNull { it.id == state.dynasty.rulerId }?.adultCommanderId &&
        state.court.characters.any { it.commanderId == c.id && it.alive && it.age >= 18 } && !state.commanderAway(c.id) && !state.war.unavailableCommander(c.id) }

    fun selectRegent(state: GameState, commanderId: Long): GameEngine.ActionResult {
        if (!state.settings.dynasty) return GameEngine.ActionResult(state, "Dynastie ist ausgeschaltet.")
        val ward = state.dynasty.members.firstOrNull { it.id == state.dynasty.rulerId && it.age(state.day) < 18 }
            ?: state.dynasty.members.firstOrNull { it.id == state.dynasty.heirId && it.age(state.day) < 18 }
            ?: return GameEngine.ActionResult(state, "Ein erwachsener Herrscher oder Erbe benötigt keine Regentschaft.")
        val regent = regentCandidates(state).firstOrNull { it.id == commanderId }
            ?: return GameEngine.ActionResult(state, "Der Regent muss erwachsen, lebend, verfügbar und mindestens 40% loyal sein.")
        val interest = state.court.characters.first { it.commanderId == commanderId }.personality.label
        val reason = "${regent.name} schützt ${ward.name} bis zum 18. Geburtstag. Loyalität ${regent.loyalty}%; Haltung: $interest."
        return GameEngine.ActionResult(chronicle(state.copy(dynasty = state.dynasty.copy(regentCommanderId = commanderId, regencyForMemberId = ward.id, regencyReason = reason, regencyConflict = null)), "Regentschaft bestimmt", reason), reason)
    }

    fun successionSummary(state: GameState): String {
        val heir = state.dynasty.members.firstOrNull { it.id == state.dynasty.heirId && it.alive }
        val regent = state.commanders.firstOrNull { it.id == state.dynasty.regentCommanderId }
        return when {
            heir == null -> "Kein Erbe bestimmt: Nach dem Herrscher folgt das älteste lebende Kind; ohne Kind bestimmt der Rat einen verfügbaren Erwachsenen nach Loyalität und Führung."
            heir.age(state.day) >= 18 -> "${heir.name} folgt als erwachsener Erbe. Ausbildung und Fähigkeiten gehen in die neue Regierung ein."
            else -> "${heir.name} ist ${heir.age(state.day)} Jahre alt. Bis 18 führt ${regent?.name ?: "ein erwachsener Regent oder der Hofrat"} die Regierung. Bei automatischer Wahl entscheiden Loyalität und Führung."
        }
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
        state.court.socialLinks.filter { it.directed }.forEach { link -> require(link.sourceId == link.firstId || link.sourceId == link.secondId) { "Ungültige Richtung einer Hofbeziehung." } }
    }

    private fun chronicle(state: GameState, title: String, text: String): GameState = state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(2000))
}
