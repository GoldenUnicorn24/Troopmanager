package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * Life after the coronation: the queen brings her own wishes, disputes and goals to the throne
 * every ten days or so. Choices cost and earn real resources, loyalty and her trust.
 */
object RoyalCourtEngine {
    private const val INTERVAL = 10
    private const val POOL = 8

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)
    private fun saga(state: GameState) = state.crown.saga
    private fun withSaga(state: GameState, s: HeroineSagaState) = state.copy(crown = state.crown.copy(saga = s))

    fun active(state: GameState): Boolean =
        saga(state).companionId != null && state.companion.met &&
            (saga(state).chapter == HeroineSagaEngine.FINAL_CHAPTER || saga(state).chapter == HeroineSagaEngine.FRIENDSHIP_COMPLETE)

    /** Daily: check the current goal and offer a new request when due. */
    fun tick(state: GameState): GameState {
        if (!active(state)) return state
        var next = checkGoal(state)
        val s = saga(next)
        if (s.pendingScene == null && next.day - s.lastRoyalDay >= INTERVAL) {
            next = withSaga(next, s.copy(pendingScene = scene(next, s.royalCount % POOL), lastRoyalDay = next.day))
        }
        return next
    }

    private fun queenTitle(state: GameState): String =
        if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "Königin ${state.companion.name}" else state.companion.name

    private fun homeTarget(state: GameState): CrownProvince? {
        val land = HeroineSagaEngine.companionDef(state)?.land ?: CrownLand.ALDMARK
        return state.crown.provinces.filter { it.land == land && it.owner != ProvinceOwner.PLAYER }.maxByOrNull { it.wealth }
            ?: state.crown.provinces.filter { it.owner != ProvinceOwner.PLAYER && CrownEngine.reachable(state, it.land) }.minByOrNull { it.garrison }
    }

    fun scene(state: GameState, index: Int): SagaScene {
        val q = queenTitle(state)
        val id = saga(state).companionId ?: "legacy"
        return when (index) {
            0 -> SagaScene("royal_academy", id, "Die Akademie der Feldherren", "Thronsaal",
                "$q legt Pläne auf den Tisch: eine Akademie, in der Offiziere aus allen Völkern lernen. „Unsere Heere sind groß geworden. Jetzt müssen sie klug werden.“",
                listOf(SagaOption("build", "Die Akademie stiften (5.000 Gold)", "Alle Truppen +5 Erfahrung, Taktik +3"), SagaOption("later", "Erst nach dem nächsten Feldzug", "Sie ist enttäuscht")))
            1 -> SagaScene("royal_mercy", id, "Gnade oder Strenge", "Gerichtssaal",
                "Gefangene Aufständische knien vor dem Thron. $q sieht dich an: „Wenn wir sie hängen, fürchten sie uns. Wenn wir sie begnadigen, vertrauen sie uns vielleicht.“",
                listOf(SagaOption("mercy", "Gnade gewähren", "Unruhe −25, Loyalität +6 in allen Provinzen"), SagaOption("harsh", "Ein Exempel statuieren", "Unruhe −15, Loyalität −6, ihr Respekt sinkt")))
            2 -> SagaScene("royal_heir", id, "Ein Erbe für die Krone", "Private Gemächer",
                "Spät am Abend fragt $q leise, ob du dir eine Familie vorstellen kannst – oder ob ihr ein Kind des Reiches annehmen wollt.",
                listOf(SagaOption("family", "Gemeinsam eine Familie planen", "Aktiviert Dynastie · nur mit beidseitigem Einverständnis"),
                    SagaOption("adopt", "Ein Waisenkind als Erben annehmen", "Aktiviert Dynastie · Adoption"),
                    SagaOption("later", "Noch nicht", "Kein Druck")))
            3 -> {
                val target = homeTarget(state)
                SagaScene("royal_homeland", id, "Ihre Heimat ruft", "Kartenraum",
                    "$q tippt auf ${target?.name ?: "ein fernes Land"}. „Dort habe ich gelernt zu kämpfen. Es leidet unter fremder Herrschaft. Versprich mir, dass wir es befreien.“",
                    listOf(SagaOption("promise", "Versprechen: in 40 Tagen ist es frei", "Ziel · Belohnung 8.000 Gold, 300 Ruhm"), SagaOption("crown", "Die Krone geht vor", "Zuneigung −5")))
            }
            4 -> SagaScene("royal_feast", id, "Ein Fest für das Volk", "Marktplatz",
                "Die Ernte war gut, die Grenzen ruhig. $q möchte, dass das Volk feiert – mit Brot, Musik und einem Turnier.",
                listOf(SagaOption("feast", "Großes Reichsfest (4.000 Gold)", "Zufriedenheit +15, Provinzloyalität +8"), SagaOption("small", "Kleines Hoffest (800 Gold)", "Zufriedenheit +5")))
            5 -> SagaScene("royal_council", id, "Streit im Thronrat", "Ratssaal",
                "Der Schatzmeister will die Heeresausgaben kürzen. $q widerspricht scharf vor dem ganzen Rat. Alle Blicke richten sich auf dich.",
                listOf(SagaOption("her", "Ihr öffentlich beistehen", "Respekt +8, Zufriedenheit −3"), SagaOption("council", "Dem Rat recht geben", "Vertrauen −6, +2.000 Gold"),
                    SagaOption("compromise", "Einen Kompromiss vermitteln (1.500 Gold)", "Konflikte sinken")))
            6 -> SagaScene("royal_envoys", id, "Gesandte aus Saltmere", "Hafen",
                "Kaufleute der Hanse bieten der Krone einen großen Kredit an – oder ein dauerhaftes Bündnis der Händler. $q kennt ihre Tricks.",
                listOf(SagaOption("loan", "Kredit nehmen (+12.000 Gold)", "Beziehung zu allen Partnern −10"), SagaOption("trade", "Handelsfreundschaft vertiefen", "Beziehung zu allen Partnern +15")))
            else -> SagaScene("royal_evening", id, "Ein Abend nur für euch", "Burgmauer",
                "Kein Rat, keine Boten, keine Schlacht. Nur ihr beide, der Wind und die Lichter eures Reiches unter euch.",
                listOf(SagaOption("stay", "Die Krone für eine Nacht vergessen", "Zuneigung +10, Konflikte −15"), SagaOption("plans", "Über die Zukunft des Reiches sprechen", "Vertrauen +8, Respekt +4")))
        }
    }

    private fun adjust(state: GameState, trust: Int = 0, respect: Int = 0, affection: Int = 0, conflict: Int = 0): GameState {
        val c = state.companion
        return state.copy(
            companion = c.copy(
                trust = (c.trust + trust).coerceIn(0, 100),
                respect = (c.respect + respect).coerceIn(0, 100),
                affection = (c.affection + affection).coerceIn(0, 100),
            ),
            relationship = state.relationship.copy(conflict = (state.relationship.conflict + conflict).coerceIn(0, 100)),
        )
    }

    private fun provinces(state: GameState, change: (CrownProvince) -> CrownProvince): GameState =
        state.copy(crown = state.crown.withProvinces(state.crown.provinces.map { if (it.owner == ProvinceOwner.PLAYER) change(it) else it }))

    private fun gold(state: GameState, delta: Int): GameState =
        state.copy(resources = state.resources.copy(gold = (state.resources.gold + delta).coerceAtLeast(0)))

    private fun satisfaction(state: GameState, delta: Int): GameState =
        state.copy(city = state.city.copy(satisfaction = (state.city.satisfaction + delta).coerceIn(0, 100)))

    /** Resolves a royal request; called by [HeroineSagaEngine.choose] for scenes starting with "royal_". */
    fun choose(state: GameState, scene: SagaScene, option: String): GameEngine.ActionResult {
        var next = state
        val text: String
        fun needs(cost: Int): Boolean = next.resources.gold >= cost
        when (scene.key to option) {
            "royal_academy" to "build" -> {
                if (!needs(5000)) return retryLater(state, "Für die Akademie fehlen 5.000 Gold.")
                next = gold(next, -5000)
                next = next.copy(
                    armyPools = next.armyPools.map { it.copy(experience = (it.experience + 5).coerceAtMost(100)) },
                    player = next.player.copy(tactics = (next.player.tactics + 3).coerceAtMost(100)),
                    companion = next.companion.copy(tactics = (next.companion.tactics + 3).coerceAtMost(100)),
                )
                next = adjust(next, respect = 6, trust = 4)
                text = "Die Akademie der Feldherren öffnet ihre Tore. Offiziere aller Völker lernen gemeinsam."
            }
            "royal_mercy" to "mercy" -> {
                next = provinces(next) { it.copy(unrest = (it.unrest - 25).coerceAtLeast(0), loyalty = (it.loyalty + 6).coerceAtMost(100)) }
                next = adjust(next, trust = 6, affection = 4)
                text = "Die Begnadigten weinen vor dem Thron. Die Nachricht deiner Gnade erreicht jede Provinz."
            }
            "royal_mercy" to "harsh" -> {
                next = provinces(next) { it.copy(unrest = (it.unrest - 15).coerceAtLeast(0), loyalty = (it.loyalty - 6).coerceAtLeast(0)) }
                next = adjust(next, respect = -6, conflict = 8)
                text = "Das Urteil wird vollstreckt. Die Provinzen schweigen – aus Angst."
            }
            "royal_heir" to "family" -> {
                next = next.copy(settings = next.settings.copy(dynasty = true))
                val agreed = DynastyEngine.agreeFamilyPlanning(next)
                next = agreed.state
                val planned = DynastyEngine.planFamily(next)
                next = planned.state
                text = if (planned.state.dynasty.plannedBirthDay != null) planned.message else agreed.message
            }
            "royal_heir" to "adopt" -> {
                next = next.copy(settings = next.settings.copy(dynasty = true))
                val adopted = DynastyEngine.adoptHeir(next)
                next = adjust(adopted.state, affection = 6)
                text = adopted.message
            }
            "royal_homeland" to "promise" -> {
                val target = homeTarget(next)
                if (target == null) {
                    text = "Ihre Heimat ist längst frei. Sie lächelt nur."
                } else {
                    next = next.copy(crown = next.crown.copy(saga = next.crown.saga.copy(goal = RoyalGoal(
                        "Versprechen an ${next.companion.name}: ${target.name} befreien", target.id, next.day + 40, 8000, 300))))
                    next = adjust(next, trust = 5, affection = 5)
                    text = "Du gibst dein Wort. ${target.name} soll in 40 Tagen frei sein."
                }
            }
            "royal_homeland" to "crown" -> { next = adjust(next, affection = -5); text = "Sie nickt stumm. Die Krone geht vor – heute." }
            "royal_feast" to "feast" -> {
                if (!needs(4000)) return retryLater(state, "Für das Reichsfest fehlen 4.000 Gold.")
                next = provinces(satisfaction(gold(next, -4000), 15)) { it.copy(loyalty = (it.loyalty + 8).coerceAtMost(100)) }
                next = adjust(next, affection = 6)
                text = "Drei Tage lang feiert das ganze Reich. Man singt Lieder über das Königspaar."
            }
            "royal_feast" to "small" -> {
                if (!needs(800)) return retryLater(state, "Selbst das kleine Fest kostet 800 Gold.")
                next = satisfaction(gold(next, -800), 5)
                text = "Ein fröhliches Hoffest. Nicht groß, aber herzlich."
            }
            "royal_council" to "her" -> { next = satisfaction(adjust(next, respect = 8, trust = 4), -3); text = "Du stehst an ihrer Seite. Der Rat murrt, das Heer jubelt." }
            "royal_council" to "council" -> { next = gold(adjust(next, trust = -6, conflict = 10), 2000); text = "Die Kürzung spart Gold. Sie sagt den ganzen Abend kein Wort." }
            "royal_council" to "compromise" -> {
                if (!needs(1500)) return retryLater(state, "Der Kompromiss kostet 1.500 Gold.")
                next = adjust(gold(next, -1500), respect = 3, conflict = -10)
                text = "Beide Seiten geben nach. Ein mühsamer, aber guter Frieden im Rat."
            }
            "royal_envoys" to "loan" -> {
                next = gold(next, 12000)
                next = next.copy(crown = next.crown.copy(partners = next.crown.partners.map { it.copy(relation = (it.relation - 10).coerceAtLeast(0)) }))
                text = "12.000 Gold fließen in die Schatzkammer. Die Händler werden sich daran erinnern."
            }
            "royal_envoys" to "trade" -> {
                next = next.copy(crown = next.crown.copy(partners = next.crown.partners.map { it.copy(relation = (it.relation + 15).coerceAtMost(100)) }))
                text = "Die Hanse verbürgt sich für eure Krone. Alle Handelspartner rücken näher."
            }
            "royal_evening" to "stay" -> { next = adjust(next, affection = 10, conflict = -15); text = "Für eine Nacht seid ihr nur ihr selbst." }
            "royal_evening" to "plans" -> { next = adjust(next, trust = 8, respect = 4); text = "Ihr zeichnet Pläne in den Sternenhimmel – für ein Reich, das euch überdauert." }
            else -> text = "Die Krone hat entschieden."
        }
        next = next.copy(crown = next.crown.copy(saga = next.crown.saga.copy(royalCount = next.crown.saga.royalCount + 1, lastOutcome = text)))
        next = RelationshipEngine.remember(next, "royal_court", text, 2, setOf("court"))
        return ok(CrownEngine.chronicle(next, scene.title, text), text)
    }

    private fun retryLater(state: GameState, message: String): GameEngine.ActionResult =
        ok(withSaga(state, saga(state).copy(lastRoyalDay = state.day - INTERVAL + 3, lastOutcome = message)), message)

    private fun checkGoal(state: GameState): GameState {
        val goal = saga(state).goal ?: return state
        val owner = CrownEngine.province(state, goal.provinceId)?.owner
        return when {
            owner == ProvinceOwner.PLAYER -> {
                var next = withSaga(state, saga(state).copy(goal = null, lastOutcome = "Versprechen gehalten!"))
                next = gold(next, goal.rewardGold).copy(renown = next.renown + goal.rewardRenown)
                next = adjust(next, trust = 10, affection = 10, respect = 6)
                CrownEngine.chronicle(next, "Versprechen gehalten", "${goal.title} – erfüllt. ${state.companion.name} wird diesen Tag nie vergessen.")
            }
            state.day > goal.deadlineDay -> {
                val next = adjust(withSaga(state, saga(state).copy(goal = null, lastOutcome = "Versprechen gebrochen.")), trust = -8, affection = -6, conflict = 8)
                CrownEngine.chronicle(next, "Versprechen gebrochen", "${goal.title} – die Frist ist verstrichen.")
            }
            else -> state
        }
    }
}
