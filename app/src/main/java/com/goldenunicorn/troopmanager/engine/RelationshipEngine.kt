package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object RelationshipEngine {
    private val events =
        listOf(
            RelationshipEvent(
                "conversation",
                "Am Feuer",
                "Alina spricht über die Zukunft der Grenzfeste.",
            ),
            RelationshipEvent(
                "training",
                "Gemeinsames Training",
                "Sie will eine neue Taktik erproben.",
            ),
            RelationshipEvent(
                "argument",
                "Ein offener Streit",
                "Die Versorgung der Armee belastet eure Beziehung.",
            ),
            RelationshipEvent(
                "attack",
                "Vor dem Angriff",
                "Alina fragt, ob sie den gefährlichen Flügel führen soll.",
            ),
            RelationshipEvent(
                "mission",
                "Nach der Mission",
                "Die Rückkehrer feiern. Wie würdigst du Alinas Einsatz?",
            ),
            RelationshipEvent(
                "wounded",
                "Verwundete Kameraden",
                "Alina verlangt Zeit für die verwundeten Heimkehrer.",
            ),
            RelationshipEvent(
                "feast",
                "Das Festessen",
                "Der Hof erwartet ein gemeinsames Zeichen der Zuversicht.",
            ),
            RelationshipEvent(
                "politics",
                "Politischer Konflikt",
                "Eine fremde Gesandtschaft zweifelt an ihrer Stellung.",
            ),
            RelationshipEvent(
                "defeat",
                "Nach der Niederlage",
                "Alina sorgt sich um die Zukunft eures Reiches.",
            ),
        )

    fun onEvent(state: GameState, key: String): GameState =
        if (!state.companion.met || state.relationship.pendingEvent != null) state
        else
            state.copy(
                relationship =
                    state.relationship.copy(
                        pendingEvent = (events.find { it.key == key } ?: events.first()).let { event ->
                            val context = when {
                                state.resources.food == 0 -> "Die knappen Vorräte beschäftigen euch beide."
                                state.relationship.conflict >= 40 -> "Euer letzter Streit ist noch nicht vergessen."
                                state.relationship.memories.isNotEmpty() -> "Ihr erinnert euch an Tag ${state.relationship.memories.last().day}: ${state.relationship.memories.last().text}"
                                else -> ""
                            }
                            val moment = when (CityTime.at(state.day)) {
                                CityTime.MORNING -> "Am Morgen, während der Hof erwacht, nehmt ihr euch einen Augenblick Zeit."
                                CityTime.DAY -> "Im belebten Burghof besprecht ihr die Aufgaben des Tages."
                                CityTime.EVENING -> "Beim Abendlicht wird es ruhig genug für ein persönliches Gespräch."
                                CityTime.NIGHT -> "Unter den Sternen wacht die Garnison, während ihr leise miteinander sprecht."
                            }
                            event.copy(text = "$moment ${event.text.replace("Alina", state.companion.name)} $context".trim())
                        }
                    )
            )

    fun day(state: GameState): GameState {
        var next =
            state.copy(
                relationship = state.relationship.copy(actionDay = state.day, spentActions = 0)
            )
        if (!next.companion.met && next.day >= 5)
            next =
                next.copy(
                    companion = next.companion.copy(met = true),
                    chronicle =
                        (next.chronicle +
                                ChronicleEntry(
                                    next.day,
                                    "Alina schließt sich an",
                                    "Eine erfahrene Soldatin erreicht die Grenzfeste. Sie kann eigene Truppen führen.",
                                ))
                            .takeLast(2000),
                )
        if (next.day % 4 == 0 && next.companion.met) {
            val eligible =
                events.filter { e ->
                    when (e.key) {
                        "politics" -> next.companion.trust >= 60
                        "feast" -> next.companion.trust >= 30
                        else -> true
                    }
                }
            next = onEvent(next, eligible[(next.day / 4) % eligible.size].key)
        }
        next = normalize(next)
        if (next.companion.met && next.day % 7 == 0 && next.relationship.lastAutonomyDay < next.day) {
            val pressure = if (next.resources.food == 0 || next.city.taxLevel == TaxLevel.HIGH) 6 else -2
            next = next.copy(relationship = next.relationship.copy(
                conflict = (next.relationship.conflict + pressure).coerceIn(0, 100),
                jealousy = (next.relationship.jealousy - 2).coerceAtLeast(0),
                lastAutonomyDay = next.day,
                politicalOpinion = if (pressure > 0) "Versorgung und faire Abgaben gehen vor einem weiteren Feldzug." else "Handel und ein starker Hof sichern unsere Zukunft.",
            ))
            if (next.relationship.romanceStage >= RomanceStage.PARTNERSHIP && next.relationship.conflict >= 80) {
                next = endRomance(next, "${next.companion.name} beendet die Partnerschaft nach anhaltenden Konflikten.", true)
            } else if (next.relationship.romanceStage >= RomanceStage.PARTNERSHIP && !next.commanderAway(COMPANION_COMMANDER_ID) && !next.war.unavailableCommander(COMPANION_COMMANDER_ID)) {
                next = if (pressure > 0) next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction + next.companion.diplomacy / 25).coerceAtMost(100)))
                else next.copy(realm = next.realm.copy(tradeBonusDays = maxOf(2, next.realm.tradeBonusDays)))
                next = next.copy(chronicle = (next.chronicle + ChronicleEntry(next.day, "Eigenständiger Rat", "${next.companion.name}: ${next.relationship.politicalOpinion}")).takeLast(2000))
            }
        }
        return syncCommander(next)
    }

    fun action(state: GameState, action: String): GameEngine.ActionResult {
        if (!state.companion.met)
            return GameEngine.ActionResult(state, "Ihr habt euch noch nicht getroffen.")
        if (state.battleSession?.isActive == true || state.commanderAway(COMPANION_COMMANDER_ID) || state.war.unavailableCommander(COMPANION_COMMANDER_ID))
            return GameEngine.ActionResult(state, "Alina ist im Einsatz.")
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
        if (state.relationship.pendingEvent == null)
            return GameEngine.ActionResult(state, "Kein Beziehungsereignis offen.")
        if (choice !in 0..2) return GameEngine.ActionResult(state, "Ungültige Entscheidung.")
        if (state.relationship.pendingEvent.key == "intimacy") return chooseIntimacy(state, choice)
        if (state.battleSession?.isActive == true || state.commanderAway(COMPANION_COMMANDER_ID) || state.war.unavailableCommander(COMPANION_COMMANDER_ID))
            return GameEngine.ActionResult(state, "Das Gespräch wartet bis zur Rückkehr.")
        val spent =
            if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (spent + 2 > 2)
            return GameEngine.ActionResult(
                state,
                "Diese große Entscheidung benötigt eure gemeinsame Zeit für einen Tag.",
            )
        val c = state.companion
        var nextC =
            when (choice) {
                0 ->
                    c.copy(
                        trust = (c.trust + 5).coerceAtMost(100),
                        affection = (c.affection + 3).coerceAtMost(100),
                    )
                1 ->
                    c.copy(
                        respect = (c.respect + 5).coerceAtMost(100),
                        trust = (c.trust - 2).coerceAtLeast(0),
                        tactics = (c.tactics + 1).coerceAtMost(100),
                    )
                else ->
                    c.copy(
                        affection = (c.affection - 3).coerceAtLeast(0),
                        trust = (c.trust - 2).coerceAtLeast(0),
                    )
            }
        val key = state.relationship.pendingEvent.key
        if (choice == 0)
            nextC =
                when (key) {
                    "defeat",
                    "wounded" -> nextC.copy(trust = (nextC.trust + 2).coerceAtMost(100))
                    "training" ->
                        nextC.copy(
                            sword = (nextC.sword + 2).coerceAtMost(100),
                            bow = (nextC.bow + 2).coerceAtMost(100),
                        )
                    "attack" -> nextC.copy(leadership = (nextC.leadership + 2).coerceAtMost(100))
                    "mission" -> nextC.copy(respect = (nextC.respect + 3).coerceAtMost(100))
                    "feast" -> nextC.copy(affection = (nextC.affection + 2).coerceAtMost(100))
                    "politics" -> nextC.copy(diplomacy = (nextC.diplomacy + 2).coerceAtMost(100))
                    else -> nextC
                }
        val realm =
            if (choice == 0 && key == "politics")
                state.realm.copy(tradeBonusDays = maxOf(5, state.realm.tradeBonusDays))
            else state.realm
        return GameEngine.ActionResult(
            syncCommander(
                remember(state.copy(
                    companion = nextC,
                    realm = realm,
                    relationship =
                        state.relationship.copy(
                            actionDay = state.day,
                            spentActions = spent + 2,
                            pendingEvent = null,
                            conflict = (state.relationship.conflict + when (choice) { 0 -> -5; 1 -> 2; else -> 0 }).coerceIn(0, 100),
                        ),
                    chronicle =
                        (state.chronicle +
                                ChronicleEntry(
                                    state.day,
                                    "Gemeinsame Entscheidung",
                                    "${state.relationship.pendingEvent.title}: Vertrauen, Respekt und Zuneigung verändern sich.",
                                ))
                            .takeLast(2000),
                ), key, "${state.relationship.pendingEvent.title}: ${when (choice) { 0 -> "Ihr unterstützt einander."; 1 -> "Ihr diskutiert verschiedene Wege."; else -> "Ihr gebt einander Zeit." }}", if (choice == 0) 2 else 0)
            ),
            "Entscheidung angenommen.",
        )
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

    private val romanceActions = setOf("confess", "kiss", "partner", "propose", "marry", "co_ruler", "boundaries", "intimacy", "friendship", "breakup", "apologize", "reconcile", "walk", "dinner", "gift", "ride", "open_relationship", "monogamy")

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
        if (spent + cost > 2) return GameEngine.ActionResult(state, "Heute braucht ihr Zeit füreinander. Morgen könnt ihr weiter sprechen.")
        if (action in setOf("friendship", "breakup")) {
            val text = if (action == "friendship") "Ihr vereinbart, euren gemeinsamen Weg als Freunde fortzusetzen." else "Ihr beendet die Partnerschaft respektvoll."
            val next = endRomance(spend(state, cost), text, false)
            return GameEngine.ActionResult(syncCommander(next), text)
        }
        if (action in setOf("apologize", "reconcile")) {
            val next = spend(state, cost)
            val amount = if (action == "reconcile" && state.companion.trust >= 50) 15 else 8
            return GameEngine.ActionResult(remember(next.copy(relationship = next.relationship.copy(conflict = (next.relationship.conflict - amount).coerceAtLeast(0)), companion = next.companion.copy(trust = (next.companion.trust + 2).coerceAtMost(100))), "reconciliation", "Ihr hört einander zu und besprecht den Streit."), "Ein offenes Gespräch entschärft den Konflikt.")
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
        if (action in setOf("walk", "dinner", "gift", "ride")) {
            if (r.romanceStage == RomanceStage.NONE) return GameEngine.ActionResult(state, "Ein romantischer Ausflug benötigt gegenseitiges Interesse. Gemeinsame Gespräche und Training bleiben freundschaftlich möglich.")
            val giftCost = if (action == "dinner") 40 else if (action == "gift") 60 else 0
            if (state.resources.gold < giftCost) return GameEngine.ActionResult(state, "$giftCost Gold benötigt.")
            val next = spend(state, cost).copy(resources = state.resources.copy(gold = state.resources.gold - giftCost), companion = c.copy(trust = (c.trust + 2).coerceAtMost(100)), relationship = spend(state, cost).relationship.copy(conflict = (r.conflict - 3).coerceAtLeast(0)))
            return GameEngine.ActionResult(remember(next, action, "Ihr nehmt euch Zeit für ${when(action) { "walk" -> "einen Spaziergang"; "ride" -> "einen gemeinsamen Ritt"; "gift" -> "ein persönliches Geschenk"; else -> "ein Abendessen" }}."), "Gemeinsame Zeit; Geschenke verändern keine Zustimmung.")
        }
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
        val next = spend(state, 2).copy(relationship = spend(state, 2).relationship.copy(lastRomanceDay = state.day, intimacyConsentDay = null))
        return GameEngine.ActionResult(remember(next, "refusal", text, -1, setOf("boundary")), text)
    }

    private fun intimacyBlocker(state: GameState): String? = when {
        state.settings.romance != RomanceMode.MATURE -> "Intime Ereignisse sind in den Einstellungen ausgeschaltet."
        state.player.age < 18 || state.companion.age < 18 -> "Private Intimität ist ausschließlich Erwachsenen ab 18 erlaubt."
        state.relationship.romanceStage < RomanceStage.PARTNERSHIP -> "Ein privater Abend benötigt eine freiwillige feste Partnerschaft."
        !state.relationship.consent.romanceAllowed || !state.relationship.consent.intimacyAllowed || "no_intimacy" in state.relationship.consent.boundaries -> "Die vereinbarten persönlichen Grenzen erlauben diese Begegnung nicht."
        state.companion.trust < 80 || state.companion.respect < 70 || state.relationship.commitment < 45 || state.relationship.conflict > 20 -> "Heute braucht ihr ein Gespräch und Abstand; es gibt kein gemeinsames Ja."
        state.day - state.relationship.lastIntimacyDay < 7 -> "Private Nähe entsteht aus eurer Geschichte. Lasst euch Zeit."
        state.battleSession?.isActive == true || state.commanderAway(COMPANION_COMMANDER_ID) || state.war.unavailableCommander(COMPANION_COMMANDER_ID) || state.war.playerCondition != CombatantStatus.ACTIVE -> "Im Einsatz, bei Gefangenschaft oder Verletzung findet keine intime Begegnung statt."
        else -> null
    }

    private fun chooseIntimacy(state: GameState, choice: Int): GameEngine.ActionResult {
        val blocker = intimacyBlocker(state)
        if (blocker != null || state.relationship.intimacyConsentDay != state.day) return GameEngine.ActionResult(state.copy(relationship = state.relationship.copy(pendingEvent = null, intimacyConsentDay = null)), blocker ?: "Die Begegnung braucht ein neues gemeinsames Ja.")
        val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
        if (spent + 2 > 2) return GameEngine.ActionResult(state, "Heute ist eure gemeinsame Zeit ausgeschöpft.")
        var next = spend(state, 2)
        next = next.copy(relationship = next.relationship.copy(pendingEvent = null, intimacyConsentDay = null))
        val text = when (choice) {
            0 -> "Ihr zieht euch gemeinsam zurück. Der Rest der Nacht gehört nur euch.\nAm nächsten Morgen sprecht ihr leise über eure gemeinsame Zukunft."
            1 -> "Ihr sprecht über den Feldzug und die Sorgen der Menschen. Nähe braucht kein Versprechen für diese Nacht."
            else -> "Ihr lasst einander Ruhe. Ein Nein wird ohne Vorwurf angenommen."
        }
        if (choice == 0) next = next.copy(relationship = next.relationship.copy(lastIntimacyDay = state.day, intimacy = (next.relationship.intimacy + 1).coerceAtMost(100)))
        next = remember(next, if (choice == 0) "private_evening" else "respected_boundary", text, if (choice == 0) 5 else 2, if (choice == 0) setOf("FADE_TO_BLACK", "consensual", "adults") else setOf("boundary"))
        return GameEngine.ActionResult(next, text)
    }

    private fun endRomance(state: GameState, text: String, npcInitiated: Boolean): GameState {
        val previous = state.relationship.romanceStage
        var next = state.copy(relationship = state.relationship.copy(romanceStage = RomanceStage.NONE, commitment = 0, intimacyConsentDay = null,
            pendingEvent = state.relationship.pendingEvent?.takeUnless { it.adultsOnly }, lastRomanceDay = state.day,
            consent = state.relationship.consent.copy(intimacyAllowed = false, wantsChildren = false)),
            companion = state.companion.copy(role = "Gefährtin"),
            dynasty = state.dynasty.copy(plannedBirthDay = null, planningParents = emptyList()),
            realm = state.realm.copy(tradeBonusDays = if (previous == RomanceStage.CO_RULERS) 0 else state.realm.tradeBonusDays))
        next = remember(next, if (npcInitiated) "npc_breakup" else "breakup", text, -4)
        if (previous != RomanceStage.NONE) next = next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, "Getrennte Wege", text)).takeLast(2000))
        return next
    }
}
