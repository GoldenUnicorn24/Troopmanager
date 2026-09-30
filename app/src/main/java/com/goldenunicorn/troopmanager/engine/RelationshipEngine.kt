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
                        pendingEvent = events.find { it.key == key } ?: events.first()
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
                            .takeLast(80),
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
        return syncCommander(next)
    }

    fun action(state: GameState, action: String): GameEngine.ActionResult {
        if (!state.companion.met)
            return GameEngine.ActionResult(state, "Ihr habt euch noch nicht getroffen.")
        if (state.battleSession?.isActive == true || state.commanderAway(COMPANION_COMMANDER_ID))
            return GameEngine.ActionResult(state, "Alina ist im Einsatz.")
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
                        trust = (c.trust + 3).coerceAtMost(100),
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
                            if (c.relationshipStage() in listOf("Mitregentin", "Herrscherpaar"))
                                "Mitregentin"
                            else c.role,
                    )
            }
        var next =
            state.copy(
                companion = c,
                relationship =
                    state.relationship.copy(actionDay = state.day, spentActions = spent + cost),
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
        if (state.battleSession?.isActive == true || state.commanderAway(COMPANION_COMMANDER_ID))
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
                state.copy(
                    companion = nextC,
                    realm = realm,
                    relationship =
                        state.relationship.copy(
                            actionDay = state.day,
                            spentActions = spent + 2,
                            pendingEvent = null,
                        ),
                    chronicle =
                        (state.chronicle +
                                ChronicleEntry(
                                    state.day,
                                    "Gemeinsame Entscheidung",
                                    "${state.relationship.pendingEvent.title}: Vertrauen, Respekt und Zuneigung verändern sich.",
                                ))
                            .takeLast(80),
                )
            ),
            "Entscheidung angenommen.",
        )
    }

    fun syncCommander(state: GameState): GameState {
        if (!state.companion.met) return state
        val c = state.companion
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
                c.trust,
                "Gefährtin · ${c.relationshipStage()}",
                if (c.relationshipStage() in listOf("Mitregentin", "Herrscherpaar")) "Mitregentin"
                else "Kommandantin",
            )
        return state.copy(
            commanders = state.commanders.filterNot { it.id == COMPANION_COMMANDER_ID } + commander
        )
    }
}
