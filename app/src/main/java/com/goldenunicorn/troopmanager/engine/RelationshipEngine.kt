package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object RelationshipEngine {
    fun onEvent(state: GameState, key: String): GameState = RelationshipEventDirector.open(state, key)

    fun day(state: GameState): GameState {
        var next =
            state.copy(
                relationship = state.relationship.copy(actionDay = state.day, spentActions = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0)
            )
        if (!next.companion.met && next.day >= 5)
            next =
                next.copy(
                    companion = next.companion.copy(met = true),
                    chronicle =
                        (next.chronicle +
                                ChronicleEntry(
                                    next.day,
                                    "${next.companion.name} schließt sich an",
                                    "Eine erfahrene Soldatin erreicht die Grenzfeste. Sie kann eigene Truppen führen.",
                                ))
                            .takeLast(2000),
                )
        if (!state.companion.met && next.companion.met)
            next = remember(next, "first_meeting", "${next.companion.name} erreicht ${next.realm.settlementName}; ihr beginnt euren gemeinsamen Weg als Gefährten.", 4, setOf("relationship", "milestone"))
        next = RelationshipEventDirector.day(next)
        next = normalize(next)
        if (next.companion.met && next.day % 7 == 0 && next.relationship.lastAutonomyDay < next.day) {
            val pressure = if (next.resources.food == 0 || next.city.taxLevel == TaxLevel.HIGH) 6 else -2
            next = next.copy(relationship = next.relationship.copy(
                conflict = (next.relationship.conflict + pressure).coerceIn(0, 100),
                jealousy = (next.relationship.jealousy - 2).coerceAtLeast(0),
                lastAutonomyDay = next.day,
                politicalOpinion = RelationshipEventDirector.currentOpinion(next),
            ))
            if (next.relationship.romanceStage >= RomanceStage.PARTNERSHIP && next.relationship.conflict >= 80) {
                next = endRomance(next, "${next.companion.name} beendet die Partnerschaft nach anhaltenden Konflikten.", true)
            }
            // Government effects belong to CoRulerEngine: partnership itself grants no realm bonus.
            if (pressure > 0) {
                val topic = if (next.resources.food == 0) "Versorgung" else "Steuern"
                next = RelationshipEventDirector.addIssue(next, topic, 8)
            }

        }
        return syncCommander(next)
    }

    fun action(state: GameState, action: String): GameEngine.ActionResult {
        if (!state.companion.met)
            return GameEngine.ActionResult(state, "Ihr habt euch noch nicht getroffen.")
        if (action.startsWith("activity:")) return activityAction(state, action.removePrefix("activity:"))
        if (action in setOf("walk", "dinner", "gift", "ride")) {
            // The old short names were romantic invitations. Explicit activity:<id> is shared friendship time.
            if (!state.relationship.consent.romanceAllowed || "no_romance" in state.relationship.consent.boundaries || state.relationship.consent.relationshipStyle == RelationshipStyle.FRIENDSHIP)
                return GameEngine.ActionResult(state, "Eine romantische Einladung würde eure vereinbarte Grenze überschreiten. Freundschaftliche gemeinsame Zeit könnt ihr ausdrücklich als Aktivität wählen.")
            return activityAction(state, action)
        }
        if (action in setOf("friendship", "breakup")) return romanceAction(state, action)
        PresenceEngine.sharedActivityBlocker(state)?.let { return GameEngine.ActionResult(state, it) }
        if (action.startsWith("issue:")) return issueAction(state, action.removePrefix("issue:"))
        if (action in romanceActions) return romanceAction(state, action)
        if (action !in listOf("talk", "train", "command", "court"))
            return GameEngine.ActionResult(state, "Unbekannte Aktion.")
        val cost = if (action == "talk" || action == "train") 1 else 2
        val spent =
            if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (spent + cost > 2)
            return GameEngine.ActionResult(
                state,
                "Heute ist eure gemeinsame Zeit ausgeschöpft. Am nächsten Tag geht es weiter.",
            )
        if (action == "court" && state.companion.trust < 45)
            return GameEngine.ActionResult(state, "Gemeinsame Reichsführung benötigt 45 Vertrauen.")
        var c = state.companion
        c =
            when (action) {
                "talk" ->
                    c.copy(
                        trust = (c.trust + if (CharacterEngine.hasPerk(state, PlayerPerk.PERSONAL_EMPATHY)) 5 else 3).coerceAtMost(100),
                        affection = (c.affection + 2).coerceAtMost(100),
                    )
                "train" ->
                    c.copy(
                        level = c.level + 1,
                        sword = (c.sword + 1).coerceAtMost(100),
                        bow = (c.bow + 1).coerceAtMost(100),
                        respect = (c.respect + 3).coerceAtMost(100),
                    )
                "command" ->
                    c.copy(
                        leadership = (c.leadership + 2).coerceAtMost(100),
                        tactics = (c.tactics + 2).coerceAtMost(100),
                        trust = (c.trust + 2).coerceAtMost(100),
                        role = "Kommandantin",
                    )
                else ->
                    c.copy(
                        diplomacy = (c.diplomacy + 2).coerceAtMost(100),
                        affection = (c.affection + 3).coerceAtMost(100),
                        role =
                            if (state.relationship.romanceStage == RomanceStage.CO_RULERS)
                                "Mitregentin"
                            else c.role,
                    )
            }
        var next =
            state.copy(
                companion = c,
                relationship =
                    state.relationship.copy(actionDay = state.day, spentActions = spent + cost,
                        conflict = (state.relationship.conflict - if (action == "talk") {
                            if (CharacterEngine.hasPerk(state, PlayerPerk.PERSONAL_EMPATHY)) 8 else 4
                        } else if (action == "train" && CharacterEngine.hasPerk(state, PlayerPerk.PERSONAL_RESILIENCE)) 5 else 0).coerceAtLeast(0),
                        loyalty = (state.relationship.loyalty + if (action == "train" && CharacterEngine.hasPerk(state, PlayerPerk.PERSONAL_RESILIENCE)) 2 else 0).coerceAtMost(100)),
            )
        if (action == "court")
            next =
                next.copy(
                    realm = next.realm.copy(tradeBonusDays = maxOf(3, next.realm.tradeBonusDays))
                )
        return GameEngine.ActionResult(
            syncCommander(next),
            "Gemeinsame Zeit stärkt Vertrauen und Fähigkeiten.",
        )
    }

    fun choose(state: GameState, choice: Int): GameEngine.ActionResult {
        val event = state.relationship.pendingEvent ?: return GameEngine.ActionResult(state, "Kein Beziehungsereignis offen.")
        if (choice !in RelationshipEventDirector.options(event).indices) return GameEngine.ActionResult(state, "Ungültige Entscheidung.")
        return if (event.key == "intimacy") chooseIntimacy(state, choice) else RelationshipEventDirector.choose(state, choice)
    }

    fun activities(state: GameState): List<RelationshipActivityAvailability> = RelationshipContentCatalog.activities.map { activity ->
        RelationshipActivityAvailability(activity, activityBlocker(state, activity) == null, activityBlocker(state, activity))
    }

    private fun activityBlocker(state: GameState, activity: RelationshipActivity): String? {
        PresenceEngine.sharedActivityBlocker(state, activity.id == "hospital")?.let { return it }
        if (activity.id == "gift" && "no_gifts" in state.relationship.consent.boundaries) return "Die vereinbarte persönliche Grenze erlaubt keine Geschenke."
        if (state.world.weather.season !in activity.seasons) return "${activity.title} ist in ${state.world.weather.season.label} nicht passend."
        if (state.companion.trust < activity.minimumTrust) return "Diese gemeinsame Aufgabe benötigt ${activity.minimumTrust} Vertrauen."
        if (state.resources.gold < activity.goldCost || state.resources.food < activity.foodCost) return "Benötigt ${activity.goldCost} Gold und ${activity.foodCost} Nahrung."
        if (!activity.requirements.all { RelationshipEventDirector.meets(state, it) }) return when {
            "outdoor" in activity.requirements && !RelationshipEventDirector.meets(state, "outdoor") -> "Das Wetter erlaubt diesen Ausflug heute nicht."
            "peace" in activity.requirements && !RelationshipEventDirector.meets(state, "peace") -> "Dieser Ausflug braucht eine sichere Friedensphase."
            "wounded" in activity.requirements -> "Zurzeit gibt es keinen passenden Lazarettbesuch."
            "family" in activity.requirements -> "Zurzeit wartet keine eigene Familienrunde auf euch."
            "victory" in activity.requirements -> "Eine Siegesfeier braucht einen kürzlich erreichten Sieg."
            "diplomacy" in activity.requirements -> "Für den Empfang braucht ihr diplomatische Kontakte."
            else -> "Gesundheit oder aktueller Kontext erlauben diese Aktivität heute nicht."
        }
        val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (spent + activity.actionCost > 2) return "Heute ist eure gemeinsame Zeit ausgeschöpft."
        return null
    }

    private fun activityAction(state: GameState, id: String): GameEngine.ActionResult {
        val activity = RelationshipContentCatalog.activities.firstOrNull { it.id == id } ?: return GameEngine.ActionResult(state, "Unbekannte gemeinsame Aktivität.")
        activityBlocker(state, activity)?.let { return GameEngine.ActionResult(state, it) }
        var next = spend(state, activity.actionCost).copy(resources = state.resources.copy(gold = state.resources.gold - activity.goldCost, food = state.resources.food - activity.foodCost))
        next = RelationshipEventDirector.applyEffect(next, activity.effect)
        next = remember(next, "activity:$id", activity.description, 1, activity.tags + "relationship")
        return GameEngine.ActionResult(syncCommander(next), "${activity.title}: ${activity.description} Geschenke und gemeinsame Zeit ersetzen keine Zustimmung.")
    }

    private fun issueAction(state: GameState, id: String): GameEngine.ActionResult {
        val issue = state.relationship.issues.firstOrNull { it.id == id || it.id == "issue:$id" }
            ?: return GameEngine.ActionResult(state, "Dieses Konfliktthema ist nicht mehr offen.")
        if (issue.resolved) return GameEngine.ActionResult(state, "Das Thema ${issue.topic} ist bereits geklärt; die Erinnerung bleibt erhalten.")
        val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (spent + 1 > 2) return GameEngine.ActionResult(state, "Für das Gespräch braucht ihr morgen wieder gemeinsame Zeit.")
        var next = remember(spend(state, 1), "issue_discussion", "Ihr besprecht ausdrücklich ${issue.topic} und benennt einen tragbaren nächsten Schritt.", 2, setOf("relationship", issue.topic))
        next = RelationshipEventDirector.addressIssue(next, issue.topic, next.relationship.memories.last().id, 25)
        return GameEngine.ActionResult(syncCommander(next), "Ihr klärt ${issue.topic}. Gelöste Themen bleiben als Erinnerung, ohne dauerhafte Belastung.")
    }

    fun syncCommander(state: GameState): GameState {
        if (!state.companion.met) return state
        val c = state.companion
        val previous = state.commanders.find { it.id == COMPANION_COMMANDER_ID }
        val commander =
            Commander(
                COMPANION_COMMANDER_ID,
                c.name,
                if (c.species == Species.HUMAN) Culture.HUMAN else Culture.WOOD_ELF,
                "companion",
                c.portraitUri,
                c.level,
                c.sword,
                c.bow,
                c.leadership,
                c.tactics,
                (c.tactics + c.leadership) / 2,
                state.relationship.loyalty,
                "Gefährtin · ${state.relationshipStage()}",
                if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "Mitregentin"
                else previous?.rank?.takeUnless { it == "Mitregentin" } ?: state.court.characters.firstOrNull { it.commanderId == COMPANION_COMMANDER_ID }?.rank?.label ?: "Kommandantin",
                missionsCompleted = previous?.missionsCompleted ?: 0,
                battlesFought = previous?.battlesFought ?: 0,
                victories = previous?.victories ?: 0,
                casualties = previous?.casualties ?: 0,
            )
        return state.copy(
            commanders = state.commanders.filterNot { it.id == COMPANION_COMMANDER_ID } + commander
        )
    }

    private val romanceActions = setOf("confess", "kiss", "partner", "propose", "marry", "co_ruler", "boundaries", "intimacy", "friendship", "breakup", "apologize", "reconcile", "open_relationship", "monogamy")

    fun normalize(state: GameState): GameState {
        val adults = state.player.age >= 18 && state.companion.age >= 18
        var r = state.relationship
        if (!adults) r = r.copy(romanceStage = RomanceStage.NONE, intimacyConsentDay = null, consent = r.consent.copy(intimacyAllowed = false, wantsChildren = false))
        if ((!adults || state.settings.romance != RomanceMode.MATURE || r.intimacyConsentDay != state.day) && r.pendingEvent?.key == "intimacy")
            r = r.copy(pendingEvent = null, intimacyConsentDay = null)
        if (state.settings.romance == RomanceMode.OFF) r = r.copy(intimacyConsentDay = null)
        return state.copy(relationship = r)
    }

    fun remember(state: GameState, type: String, text: String, weight: Int = 1, tags: Set<String> = emptySet()): GameState {
        val serial = (state.relationship.memories.count { it.day == state.day && it.type == type } + 1)
        var id = "memory_${state.day}_${type}_$serial"
        var suffix = serial
        while (state.relationship.memories.any { it.id == id }) { suffix++; id = "memory_${state.day}_${type}_$suffix" }
        val memory = RelationshipMemory(id, state.day, type, emotionalWeight = weight, text = text, tags = tags, location = state.realm.settlementName)
        val all = state.relationship.memories + memory
        val preserved = if (all.size <= 200) all else {
            val milestones = all.filter { it.emotionalWeight >= 4 || it.type in setOf("kiss", "marry", "bereavement", "npc_breakup", "child_arrival") }.take(40)
            val ids = milestones.map { it.id }.toSet()
            val keep = ids + all.filterNot { it.id in ids }.takeLast(200 - milestones.size).map { it.id }
            all.filter { it.id in keep }
        }
        return state.copy(relationship = state.relationship.copy(memories = preserved))
    }

    private fun spend(state: GameState, cost: Int): GameState = state.copy(relationship = state.relationship.copy(actionDay = state.day, spentActions = (if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0) + cost))

    private fun romanceAction(state: GameState, action: String): GameEngine.ActionResult {
        val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        val cost = if (action in setOf("walk", "ride", "apologize")) 1 else 2
        if (action !in setOf("friendship", "breakup") && spent + cost > 2) return GameEngine.ActionResult(state, "Heute braucht ihr Zeit füreinander. Morgen könnt ihr weiter sprechen.")
        if (action in setOf("friendship", "breakup")) {
            val text = if (action == "friendship") "Ihr vereinbart, euren gemeinsamen Weg als Freunde fortzusetzen." else "Ihr beendet die Partnerschaft respektvoll."
            var next = endRomance(state, text, false)
            if (action == "friendship") next = next.copy(relationship = next.relationship.copy(consent = next.relationship.consent.copy(romanceAllowed = false, relationshipStyle = RelationshipStyle.FRIENDSHIP)))
            return GameEngine.ActionResult(syncCommander(next), text)
        }
        if (action in setOf("apologize", "reconcile")) {
            val issue = state.relationship.issues.filterNot { it.resolved }.maxByOrNull { it.severity }
            val amount = if (action == "reconcile" && state.companion.trust >= 50) 25 else 15
            val text = if (issue != null) "Ihr hört einander zu und besprecht ausdrücklich ${issue.topic}." else "Ihr hört einander zu und besprecht eure Bedürfnisse ohne ein erzwungenes Versprechen."
            var next = remember(spend(state, cost), "reconciliation", text, 2, setOf("relationship") + listOfNotNull(issue?.topic))
            next = if (issue != null) RelationshipEventDirector.addressIssue(next, issue.topic, next.relationship.memories.last().id, amount)
                else next.copy(relationship = next.relationship.copy(conflict = (next.relationship.conflict - amount / 2).coerceAtLeast(0)))
            return GameEngine.ActionResult(syncCommander(next), text)
        }

        if (state.settings.romance == RomanceMode.OFF) return GameEngine.ActionResult(state, "Romanze ist in den Einstellungen ausgeschaltet.")
        if (state.player.age < 18 || state.companion.age < 18) return GameEngine.ActionResult(state, "Romantische Begegnungen benötigen zwei erwachsene Personen ab 18.")
        if (state.war.playerCondition != CombatantStatus.ACTIVE) return GameEngine.ActionResult(state, "Beziehungsentscheidungen warten, bis du gesund und frei verfügbar bist.")
        val r = state.relationship
        if (!r.consent.romanceAllowed || "no_romance" in r.consent.boundaries || r.consent.relationshipStyle == RelationshipStyle.FRIENDSHIP)
            return GameEngine.ActionResult(state, "Diese Grenze bleibt bestehen: ${state.companion.name} möchte Freundschaft.")
        if (state.day - r.lastRomanceDay < 3 && action !in setOf("walk", "dinner", "gift", "ride", "boundaries", "intimacy"))
            return GameEngine.ActionResult(state, "Lasst einander Zeit; eine neue Beziehungsentscheidung braucht drei Tage Abstand.")
        val c = state.companion
        if (action == "boundaries") {
            if (state.settings.romance != RomanceMode.MATURE) return GameEngine.ActionResult(state, "Private Begegnungen sind nur im Modus reife Romanze aktiviert.")
            if (r.romanceStage < RomanceStage.PARTNERSHIP || c.trust < 80 || c.respect < 70 || r.conflict > 20 || "no_intimacy" in r.consent.boundaries)
                return refuse(state, "${c.name} möchte diese Grenze derzeit nicht öffnen.")
            val next = spend(state, 2).copy(relationship = spend(state, 2).relationship.copy(consent = r.consent.copy(intimacyAllowed = true)))
            return GameEngine.ActionResult(remember(next, "boundaries", "Ihr besprecht Privatsphäre und persönliche Grenzen. Jede Begegnung braucht ein neues gemeinsames Ja."), "Ihr vereinbart Grenzen; Zustimmung wird für jede Begegnung neu gefragt.")
        }
        if (action == "intimacy") {
            val blocker = intimacyBlocker(state)
            if (blocker != null) return GameEngine.ActionResult(state, blocker)
            if (r.pendingEvent != null) return GameEngine.ActionResult(state, "Besprecht zuerst das offene Ereignis.")
            val event = RelationshipEvent("intimacy", "Ein Abend für euch", "Der lange Feldzug liegt hinter euch. Zum ersten Mal seit Wochen gehört der Abend nur euch. Ihr könnt ihn gemeinsam verbringen, über den Krieg sprechen oder einander Ruhe lassen.", adultsOnly = true, consentRequired = true, presentation = "FADE_TO_BLACK")
            return GameEngine.ActionResult(state.copy(relationship = r.copy(pendingEvent = event, intimacyConsentDay = state.day)), "${c.name} stimmt einem privaten Abend zu. Du entscheidest freiwillig, ob ihr ihn gemeinsam verbringt.")
        }
        if (action in setOf("open_relationship", "monogamy")) {
            if (r.romanceStage < RomanceStage.PARTNERSHIP || c.trust < 85 || r.conflict > 15) return refuse(state, "Über Beziehungsregeln besteht derzeit kein gemeinsames Einverständnis.")
            val style = if (action == "open_relationship") RelationshipStyle.OPEN else RelationshipStyle.MONOGAMOUS
            // Her established preference is monogamy. The player cannot overwrite it with rank or money.
            if (style == RelationshipStyle.OPEN && "open_relationship" !in r.consent.boundaries)
                return refuse(state, "${c.name} möchte eine treue Partnerschaft. Diese Grenze bleibt bestehen.")
            return GameEngine.ActionResult(remember(spend(state, cost).copy(relationship = spend(state, cost).relationship.copy(consent = r.consent.copy(relationshipStyle = style))), "relationship_rules", "Ihr vereinbart: ${style.label}."), "Beziehungsregeln gemeinsam vereinbart.")
        }
        val target = when (action) {
            "confess" -> RomanceStage.INTEREST
            "kiss" -> RomanceStage.ROMANCE
            "partner" -> RomanceStage.PARTNERSHIP
            "propose" -> RomanceStage.ENGAGED
            "marry" -> RomanceStage.MARRIED
            "co_ruler" -> RomanceStage.CO_RULERS
            else -> return GameEngine.ActionResult(state, "Unbekannte Beziehungsentscheidung.")
        }
        if (r.romanceStage.ordinal != target.ordinal - 1) return GameEngine.ActionResult(state, "Diese Entscheidung benötigt zuerst ${RomanceStage.entries[target.ordinal - 1].label}.")
        val threshold = when (target) { RomanceStage.INTEREST -> 60; RomanceStage.ROMANCE -> 65; RomanceStage.PARTNERSHIP -> 75; RomanceStage.ENGAGED -> 80; else -> 85 }
        if (c.trust < threshold || c.respect < threshold - 15 || c.affection < threshold - 10 || r.conflict > 25 || (target == RomanceStage.CO_RULERS && c.diplomacy < 60))
            return refuse(state, "${c.name} braucht mehr Vertrauen und Zeit. Ihr könnt Freunde bleiben.")
        val text = when (target) {
            RomanceStage.INTEREST -> "Du sprichst offen über deine Gefühle. ${c.name} erwidert dein Interesse."
            RomanceStage.ROMANCE -> "Ihr fragt einander und teilt euren ersten Kuss."
            RomanceStage.PARTNERSHIP -> "Ihr entscheidet euch freiwillig für eine feste Partnerschaft."
            RomanceStage.ENGAGED -> "${c.name} nimmt deinen Antrag aus freiem Willen an."
            RomanceStage.MARRIED -> "Ihr versprecht euch eine gemeinsame Zukunft als Lebenspartner."
            else -> "Ihr entscheidet gemeinsam, das Reich als Herrscherpaar zu führen."
        }
        val nextR = r.copy(actionDay = state.day, spentActions = spent + cost, romanceStage = target, attraction = maxOf(r.attraction, 60), commitment = maxOf(r.commitment, target.ordinal * 15), lastRomanceDay = state.day)
        var next = state.copy(relationship = nextR, companion = c.copy(role = if (target == RomanceStage.CO_RULERS) "Mitregentin" else c.role))
        next = remember(next, action, text, 5, setOf("voluntary", "adults"))
        next = next.copy(chronicle = (next.chronicle + ChronicleEntry(next.day, target.label, text)).takeLast(2000))
        return GameEngine.ActionResult(syncCommander(next), text)
    }

    private fun refuse(state: GameState, text: String): GameEngine.ActionResult {
        // A refusal records a neutral boundary. It consumes no budget and creates no cooldown or stat penalty.
        val next = state.copy(relationship = state.relationship.copy(intimacyConsentDay = null))
        // Repeating an identical question cannot overwrite a boundary or fill the album with duplicate refusals.
        if (next.relationship.memories.any { it.day == state.day && it.type == "refusal" && it.text == text })
            return GameEngine.ActionResult(next, text)
        return GameEngine.ActionResult(remember(next, "refusal", text, 0, setOf("boundary", "relationship")), text)
    }

    private fun intimacyBlocker(state: GameState): String? = when {
        state.settings.romance != RomanceMode.MATURE -> "Intime Ereignisse sind in den Einstellungen ausgeschaltet."
        state.player.age < 18 || state.companion.age < 18 -> "Private Intimität ist ausschließlich Erwachsenen ab 18 erlaubt."
        state.relationship.romanceStage < RomanceStage.PARTNERSHIP -> "Ein privater Abend benötigt eine freiwillige feste Partnerschaft."
        !state.relationship.consent.romanceAllowed || !state.relationship.consent.intimacyAllowed || "no_intimacy" in state.relationship.consent.boundaries -> "Die vereinbarten persönlichen Grenzen erlauben diese Begegnung nicht."
        state.companion.trust < 80 || state.companion.respect < 70 || state.relationship.commitment < 45 || state.relationship.conflict > 20 -> "Heute braucht ihr ein Gespräch und Abstand; es gibt kein gemeinsames Ja."
        state.day - state.relationship.lastIntimacyDay < 7 -> "Private Nähe entsteht aus eurer Geschichte. Lasst euch Zeit."
        PresenceEngine.sharedActivityBlocker(state) != null -> "Im Einsatz, bei Gefangenschaft, Verletzung oder an verschiedenen Orten findet keine intime Begegnung statt."
        else -> null
    }

    private fun chooseIntimacy(state: GameState, choice: Int): GameEngine.ActionResult {
        val blocker = intimacyBlocker(state)
        if (blocker != null || state.relationship.intimacyConsentDay != state.day) return GameEngine.ActionResult(state.copy(relationship = state.relationship.copy(pendingEvent = null, intimacyConsentDay = null)), blocker ?: "Die Begegnung braucht ein neues gemeinsames Ja.")
        val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (choice != 2 && spent + 2 > 2) return GameEngine.ActionResult(state, "Heute ist eure gemeinsame Zeit ausgeschöpft.")
        var next = if (choice == 2) state else spend(state, 2)
        next = next.copy(relationship = next.relationship.copy(pendingEvent = null, intimacyConsentDay = null))
        val text = when (choice) {
            0 -> "Ihr zieht euch gemeinsam zurück. Der Rest der Nacht gehört nur euch.\nAm nächsten Morgen sprecht ihr leise über eure gemeinsame Zukunft."
            1 -> "Ihr sprecht über den Feldzug und die Sorgen der Menschen. Nähe braucht kein Versprechen für diese Nacht."
            else -> "Ihr lasst einander Ruhe. Ein Nein wird ohne Vorwurf angenommen."
        }
        if (choice == 0) next = next.copy(relationship = next.relationship.copy(lastIntimacyDay = state.day, intimacy = (next.relationship.intimacy + 1).coerceAtMost(100)))
        next = remember(next, if (choice == 0) "private_evening" else "respected_boundary", text, if (choice == 0) 5 else if (choice == 2) 0 else 2, if (choice == 0) setOf("FADE_TO_BLACK", "consensual", "adults") else setOf("boundary"))
        return GameEngine.ActionResult(next, text)
    }

    private fun endRomance(state: GameState, text: String, npcInitiated: Boolean): GameState {
        val previous = state.relationship.romanceStage
        var next = state.copy(relationship = state.relationship.copy(romanceStage = RomanceStage.NONE, commitment = 0, intimacyConsentDay = null,
            pendingEvent = state.relationship.pendingEvent?.takeUnless { it.adultsOnly }, lastRomanceDay = if (npcInitiated) state.day else state.relationship.lastRomanceDay,
            consent = state.relationship.consent.copy(intimacyAllowed = false, wantsChildren = false)),
            companion = state.companion.copy(role = "Gefährtin"),
            dynasty = state.dynasty.copy(plannedBirthDay = null, planningParents = emptyList()))
        next = remember(next, if (npcInitiated) "npc_breakup" else "breakup", text, if (npcInitiated) -4 else 0)
        if (previous != RomanceStage.NONE) next = next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, "Getrennte Wege", text)).takeLast(2000))
        return next
    }
}
