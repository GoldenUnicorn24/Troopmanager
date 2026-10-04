package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.max

/**
 * v0.83 campaign fun layer.
 *
 * The existing simulation remains authoritative. This engine reads the state produced by those
 * systems, turns it into a visible pressure/momentum loop and injects one compact ruler dilemma
 * every few days. Decisions have immediate trade-offs plus short-lived follow-up effects.
 */
object CampaignPulseEngine {
    data class Choice(val id: String, val label: String, val detail: String)

    fun setFocus(state: GameState, focus: CampaignFocus): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Den Reichsschwerpunkt änderst du nach der laufenden Schlacht.")
        if (state.campaign.focus == focus)
            return GameEngine.ActionResult(state, "${focus.label} ist bereits dein Reichsschwerpunkt.")
        return GameEngine.ActionResult(
            state.copy(
                campaign = state.campaign.copy(focus = focus),
                chronicle = (state.chronicle + ChronicleEntry(
                    state.day,
                    "Neuer Reichsschwerpunkt",
                    "${focus.label}: ${focus.description}",
                )).takeLast(2000),
            ),
            "Reichsschwerpunkt: ${focus.label}.",
        )
    }

    fun choices(state: GameState, decision: CampaignDecision): List<Choice> =
        when (decision.kind) {
            CampaignDecisionKind.FOOD_SHORTAGE -> listOf(
                Choice("import", "Notimporte sichern", "Kostet Gold, bringt sofort große Nahrungsreserven."),
                Choice("ration", "Rationen organisieren", "Kurzfristige Versorgungsreserve, aber die Stimmung leidet."),
                Choice("forage", "Versorgungstrupps aussenden", "Nahrung und Ruhm, dafür steigt das Grenzrisiko leicht."),
            )
            CampaignDecisionKind.BORDER_CRISIS -> listOf(
                Choice("mobilize", "Grenze mobilisieren", "Gold einsetzen, Bedrohung senken und Bereitschaft erhöhen."),
                Choice("fortify", "Mauern verstärken", "Stein investieren, Mauer und Verteidigungsbereitschaft stärken."),
                Choice("scout", "Aufklärung erzwingen", "Ohne Materialkosten: bessere Aufklärung und etwas weniger Druck."),
            )
            CampaignDecisionKind.PUBLIC_UNREST -> listOf(
                Choice("reform", "Zugeständnisse machen", "Gold gegen deutlich mehr Zufriedenheit."),
                Choice("festival", "Reichsfest ausrufen", "Gold und Nahrung gegen starke Stimmung und etwas weniger Konflikt."),
                Choice("enforce", "Ordnung durchsetzen", "Sicherheit steigt, Zufriedenheit sinkt."),
            )
            CampaignDecisionKind.RULING_PAIR_TENSION -> listOf(
                Choice("compromise", "Kompromiss suchen", "Konflikt sinkt stark; Vertrauen wächst."),
                Choice("delegate", "Verantwortung abgeben", "Vertrauen und Respekt wachsen, du teilst sichtbar Macht."),
                Choice("overrule", "Entscheidung durchsetzen", "Mehr Autorität und Respekt, aber auch mehr Konflikt."),
            )
            CampaignDecisionKind.OPPORTUNITY -> listOf(
                Choice("fair", "Großen Markt ausrichten", "Investition für mehrere Tage zusätzliche Einnahmen."),
                Choice("drill", "Offizierstag ansetzen", "Kommandanten lernen, das Reich gewinnt etwas Ruhm."),
                Choice("expedition", "Rohstoffexpedition senden", "Sofortige Rohstoffe, dafür etwas mehr Grenzaktivität."),
            )
        }

    fun tick(before: GameState, state: GameState): GameState {
        var next = applyOngoingEffects(state)

        val newPressure = pressure(next)
        val oldPressure = before.campaign.pressure
        val score = dayScore(before, next, oldPressure, newPressure)
        val oldPulse = next.campaign
        val streak = if (score > 0) oldPulse.streak + 1 else if (score < 0) 0 else oldPulse.streak
        next = next.copy(
            campaign = oldPulse.copy(
                momentum = (oldPulse.momentum + score).coerceIn(0, 100),
                pressure = newPressure,
                streak = streak,
                bestStreak = max(oldPulse.bestStreak, streak),
            )
        )
        next = applyFocus(next)

        val expired = next.campaign.pendingDecision?.takeIf { it.expiresDay < next.day }
        if (expired != null) {
            val fallback = choices(next, expired).last().id
            next = applyChoice(next, expired, fallback, automatic = true).state
        }

        val canCreate =
            next.campaign.pendingDecision == null &&
                next.society.story.pending == null &&
                next.day - next.campaign.lastDecisionDay >= 4 &&
                next.battleSession?.isActive != true
        if (canCreate) {
            val decision = createDecision(next, newPressure)
            next = next.copy(
                campaign = next.campaign.copy(
                    pendingDecision = decision,
                    lastDecisionDay = next.day,
                ),
                chronicle = (next.chronicle + ChronicleEntry(next.day, decision.title, decision.text)).takeLast(2000),
            )
        }

        if (
            next.campaign.streak > 0 &&
            next.campaign.streak % 7 == 0 &&
            next.campaign.streak != before.campaign.streak
        ) {
            next = next.copy(
                renown = safeAdd(next.renown, 3),
                resources = next.resources.copy(gold = safeAdd(next.resources.gold, 120)),
                campaign = next.campaign.copy(
                    recentOutcomes = remember(
                        next.campaign.recentOutcomes,
                        "7-Tage-Initiative: +3 Ruhm und +120 Gold aus stabiler Verwaltung.",
                    )
                ),
            )
        }
        return next
    }

    fun resolve(state: GameState, choiceId: String): GameEngine.ActionResult {
        val decision = state.campaign.pendingDecision
            ?: return GameEngine.ActionResult(state, "Aktuell wartet keine Kampagnenentscheidung.")
        if (choices(state, decision).none { it.id == choiceId })
            return GameEngine.ActionResult(state, "Diese Entscheidung ist nicht verfügbar.")
        return applyChoice(state, decision, choiceId, automatic = false)
    }

    private fun applyOngoingEffects(state: GameState): GameState {
        val pulse = state.campaign
        var resources = state.resources
        var city = state.city
        var realm = state.realm

        if (pulse.prosperityDays > 0) {
            val income = 70 + realm.level(BuildingType.MARKET).coerceAtMost(8) * 25
            resources = resources.copy(gold = safeAdd(resources.gold, income))
        }
        if (pulse.supplyReliefDays > 0) {
            val food = max(100, state.population.total / 18)
            resources = resources.copy(food = safeAdd(resources.food, food))
        }
        if (pulse.defenseReadinessDays > 0) {
            city = city.copy(security = (city.security + 1).coerceAtMost(100))
            realm = realm.copy(threat = (realm.threat - 1).coerceAtLeast(0))
        }
        return state.copy(
            resources = resources,
            city = city,
            realm = realm,
            campaign = pulse.copy(
                prosperityDays = (pulse.prosperityDays - 1).coerceAtLeast(0),
                supplyReliefDays = (pulse.supplyReliefDays - 1).coerceAtLeast(0),
                defenseReadinessDays = (pulse.defenseReadinessDays - 1).coerceAtLeast(0),
            ),
        )
    }

    private fun applyFocus(state: GameState): GameState =
        when (state.campaign.focus) {
            CampaignFocus.BALANCED -> state
            CampaignFocus.PROSPERITY -> {
                val bonus = 18 + state.realm.level(BuildingType.MARKET).coerceAtMost(6) * 6
                state.copy(resources = state.resources.copy(gold = safeAdd(state.resources.gold, bonus)))
            }
            CampaignFocus.DEFENSE -> state.copy(
                city = state.city.copy(security = (state.city.security + 1).coerceAtMost(100)),
                realm = state.realm.copy(
                    threat = if (state.day % 2 == 0) (state.realm.threat - 1).coerceAtLeast(0)
                    else state.realm.threat
                ),
            )
            CampaignFocus.PEOPLE -> state.copy(
                city = state.city.copy(satisfaction = (state.city.satisfaction + 1).coerceAtMost(100))
            )
            CampaignFocus.RULING_PAIR -> {
                if (!state.companion.met) state
                else state.copy(
                    companion = state.companion.copy(
                        trust =
                            if (state.day % 3 == 0) (state.companion.trust + 1).coerceAtMost(100)
                            else state.companion.trust
                    ),
                    relationship = state.relationship.copy(
                        conflict = (state.relationship.conflict - 1).coerceAtLeast(0)
                    ),
                )
            }
            CampaignFocus.EXPANSION ->
                if (state.day % 3 == 0) state.copy(renown = safeAdd(state.renown, 1)) else state
        }

    private fun dayScore(
        before: GameState,
        after: GameState,
        oldPressure: Int,
        newPressure: Int,
    ): Int {
        var score = 0
        if (after.victories > before.victories) score += 5
        if (after.defeats > before.defeats) score -= 7
        if (after.city.satisfaction > before.city.satisfaction) score += 1
        if (after.city.satisfaction < before.city.satisfaction) score -= 1
        if (after.city.security > before.city.security) score += 1
        if (after.city.security < before.city.security) score -= 1
        if (newPressure < oldPressure) score += 2
        if (newPressure > oldPressure + 8) score -= 2
        if (after.resources.food == 0) score -= 3
        return score.coerceIn(-8, 8)
    }

    private fun pressure(state: GameState): Int {
        var value = state.realm.threat.coerceIn(0, 100) / 3
        val people = state.population.total.coerceAtLeast(1)
        if (state.resources.food < people / 2) value += 28
        else if (state.resources.food < people) value += 16

        if (state.city.satisfaction < 40) value += 20
        else if (state.city.satisfaction < 55) value += 10

        if (state.city.security < 40) value += 18
        else if (state.city.security < 55) value += 8

        if (state.realm.wallIntegrity < 50) value += 12
        if (state.invasion != null) value += 22
        val wounded = state.war.wounded.sumOf { it.soldiers }
        if (wounded > max(30, state.armySize / 8)) value += 10
        if (state.companion.met && state.relationship.conflict >= 60) value += 10
        return value.coerceIn(0, 100)
    }

    private fun createDecision(state: GameState, pressure: Int): CampaignDecision {
        val people = state.population.total.coerceAtLeast(1)
        val kind = when {
            state.resources.food < people ||
                (EconomyEngine.production(state).net.food < 0 && state.resources.food < people * 2) ->
                CampaignDecisionKind.FOOD_SHORTAGE
            state.invasion != null || state.realm.threat >= 55 ||
                state.frontier.hordes.any { it.discovered && it.daysToArrival <= 5 } ->
                CampaignDecisionKind.BORDER_CRISIS
            state.city.satisfaction < 52 || state.society.crime >= 60 ->
                CampaignDecisionKind.PUBLIC_UNREST
            state.companion.met && state.relationship.conflict >= 45 ->
                CampaignDecisionKind.RULING_PAIR_TENSION
            else -> CampaignDecisionKind.OPPORTUNITY
        }

        val (title, text) = when (kind) {
            CampaignDecisionKind.FOOD_SHORTAGE ->
                "Der Vorratsrat schlägt Alarm" to
                    "Die Versorgung wird knapp. Du kannst teuer absichern, rationieren oder Trupps ins Grenzland schicken."
            CampaignDecisionKind.BORDER_CRISIS ->
                "Unruhe an der Grenze" to
                    "Späher melden wachsenden Druck. Mobilisierung, Befestigung oder Aufklärung setzen unterschiedliche Prioritäten."
            CampaignDecisionKind.PUBLIC_UNREST ->
                "Stimmung kippt in den Vierteln" to
                    "Der Hof verlangt eine sichtbare Antwort auf Unzufriedenheit und Unsicherheit im Reich."
            CampaignDecisionKind.RULING_PAIR_TENSION ->
                "${state.companion.name} widerspricht im Rat" to
                    "Eine politische Frage wird persönlich. Wie ihr sie löst, prägt Vertrauen, Respekt und eure gemeinsame Herrschaft."
            CampaignDecisionKind.OPPORTUNITY ->
                "Ein freies Zeitfenster" to
                    "Keine akute Krise bindet den Hof. Nutze die Initiative für Wohlstand, Offiziere oder eine riskantere Rohstoffexpedition."
        }
        return CampaignDecision(
            id = "pulse_${state.day}_${kind.name.lowercase()}",
            kind = kind,
            title = title,
            text = text,
            createdDay = state.day,
            expiresDay = state.day + 2,
            intensity = pressure.coerceIn(10, 100),
        )
    }

    private fun applyChoice(
        state: GameState,
        decision: CampaignDecision,
        choiceId: String,
        automatic: Boolean,
    ): GameEngine.ActionResult {
        var next = state

        fun spend(gold: Int = 0, food: Int = 0, stone: Int = 0): Boolean {
            if (
                next.resources.gold < gold ||
                next.resources.food < food ||
                next.resources.stone < stone
            ) return false
            next = next.copy(
                resources = next.resources.copy(
                    gold = next.resources.gold - gold,
                    food = next.resources.food - food,
                    stone = next.resources.stone - stone,
                )
            )
            return true
        }

        fun pulseUpdate(
            momentum: Int = 2,
            prosperityDays: Int? = null,
            supplyReliefDays: Int? = null,
            defenseReadinessDays: Int? = null,
        ) {
            next = next.copy(
                campaign = next.campaign.copy(
                    momentum = (next.campaign.momentum + momentum).coerceIn(0, 100),
                    prosperityDays = prosperityDays ?: next.campaign.prosperityDays,
                    supplyReliefDays = supplyReliefDays ?: next.campaign.supplyReliefDays,
                    defenseReadinessDays = defenseReadinessDays ?: next.campaign.defenseReadinessDays,
                )
            )
        }

        when (decision.kind) {
            CampaignDecisionKind.FOOD_SHORTAGE -> when (choiceId) {
                "import" -> {
                    val cost = 280 + decision.intensity * 6
                    if (!spend(gold = cost))
                        return GameEngine.ActionResult(state, "Notimporte benötigen $cost Gold.")
                    next = next.copy(
                        resources = next.resources.copy(
                            food = safeAdd(next.resources.food, 1200 + decision.intensity * 18)
                        ),
                        city = next.city.copy(
                            satisfaction = (next.city.satisfaction + 2).coerceAtMost(100)
                        ),
                    )
                    pulseUpdate(momentum = 4)
                }
                "ration" -> {
                    next = next.copy(
                        city = next.city.copy(
                            satisfaction = (next.city.satisfaction - 5).coerceAtLeast(0)
                        )
                    )
                    pulseUpdate(momentum = 1, supplyReliefDays = 6)
                }
                else -> {
                    next = next.copy(
                        resources = next.resources.copy(
                            food = safeAdd(next.resources.food, 650 + decision.intensity * 10)
                        ),
                        realm = next.realm.copy(
                            threat = (next.realm.threat + 4).coerceAtMost(100)
                        ),
                        renown = safeAdd(next.renown, 3),
                    )
                    pulseUpdate(momentum = 2)
                }
            }

            CampaignDecisionKind.BORDER_CRISIS -> when (choiceId) {
                "mobilize" -> {
                    val cost = 260 + decision.intensity * 4
                    if (!spend(gold = cost))
                        return GameEngine.ActionResult(state, "Mobilisierung benötigt $cost Gold.")
                    next = next.copy(
                        realm = next.realm.copy(
                            threat = (next.realm.threat - 12).coerceAtLeast(0)
                        ),
                        city = next.city.copy(
                            security = (next.city.security + 6).coerceAtMost(100)
                        ),
                    )
                    pulseUpdate(momentum = 3, defenseReadinessDays = 6)
                }
                "fortify" -> {
                    val stone = 180 + decision.intensity * 3
                    if (!spend(stone = stone))
                        return GameEngine.ActionResult(state, "Befestigung benötigt $stone Stein.")
                    next = next.copy(
                        realm = next.realm.copy(
                            wallIntegrity = (next.realm.wallIntegrity + 18).coerceAtMost(100),
                            threat = (next.realm.threat - 5).coerceAtLeast(0),
                        )
                    )
                    pulseUpdate(momentum = 3, defenseReadinessDays = 8)
                }
                else -> {
                    next = next.copy(
                        realm = next.realm.copy(
                            scoutingDays = max(next.realm.scoutingDays, 6),
                            threat = (next.realm.threat - 6).coerceAtLeast(0),
                        ),
                        renown = safeAdd(next.renown, 2),
                    )
                    pulseUpdate(momentum = 2)
                }
            }

            CampaignDecisionKind.PUBLIC_UNREST -> when (choiceId) {
                "reform" -> {
                    val cost = 220 + decision.intensity * 3
                    if (!spend(gold = cost))
                        return GameEngine.ActionResult(state, "Zugeständnisse benötigen $cost Gold.")
                    next = next.copy(
                        city = next.city.copy(
                            satisfaction = (next.city.satisfaction + 12).coerceAtMost(100)
                        )
                    )
                    pulseUpdate(momentum = 4)
                }
                "festival" -> {
                    val gold = 280 + decision.intensity * 2
                    val food = 180 + decision.intensity * 2
                    if (!spend(gold = gold, food = food))
                        return GameEngine.ActionResult(
                            state,
                            "Das Reichsfest benötigt $gold Gold und $food Nahrung.",
                        )
                    next = next.copy(
                        city = next.city.copy(
                            satisfaction = (next.city.satisfaction + 15).coerceAtMost(100)
                        ),
                        relationship = next.relationship.copy(
                            conflict = (next.relationship.conflict - 4).coerceAtLeast(0)
                        ),
                    )
                    pulseUpdate(momentum = 5)
                }
                else -> {
                    next = next.copy(
                        city = next.city.copy(
                            security = (next.city.security + 10).coerceAtMost(100),
                            satisfaction = (next.city.satisfaction - 8).coerceAtLeast(0),
                        ),
                        renown = (next.renown - 1).coerceAtLeast(0),
                    )
                    pulseUpdate(momentum = -1)
                }
            }

            CampaignDecisionKind.RULING_PAIR_TENSION -> when (choiceId) {
                "compromise" -> {
                    next = next.copy(
                        companion = next.companion.copy(
                            trust = (next.companion.trust + 5).coerceAtMost(100)
                        ),
                        relationship = next.relationship.copy(
                            conflict = (next.relationship.conflict - 16).coerceAtLeast(0)
                        ),
                    )
                    pulseUpdate(momentum = 4)
                }
                "delegate" -> {
                    next = next.copy(
                        companion = next.companion.copy(
                            trust = (next.companion.trust + 7).coerceAtMost(100),
                            respect = (next.companion.respect + 5).coerceAtMost(100),
                        ),
                        relationship = next.relationship.copy(
                            conflict = (next.relationship.conflict - 8).coerceAtLeast(0)
                        ),
                    )
                    pulseUpdate(momentum = 4)
                }
                else -> {
                    next = next.copy(
                        companion = next.companion.copy(
                            respect = (next.companion.respect + 3).coerceAtMost(100)
                        ),
                        relationship = next.relationship.copy(
                            conflict = (next.relationship.conflict + 7).coerceAtMost(100)
                        ),
                        renown = safeAdd(next.renown, 2),
                    )
                    pulseUpdate(momentum = 1)
                }
            }

            CampaignDecisionKind.OPPORTUNITY -> when (choiceId) {
                "fair" -> {
                    val cost = 300
                    if (!spend(gold = cost))
                        return GameEngine.ActionResult(state, "Der große Markt benötigt $cost Gold.")
                    pulseUpdate(momentum = 4, prosperityDays = 7)
                    next = next.copy(
                        city = next.city.copy(
                            satisfaction = (next.city.satisfaction + 3).coerceAtMost(100)
                        )
                    )
                }
                "drill" -> {
                    next = next.copy(
                        commanders = next.commanders.map {
                            it.copy(
                                leadership = (it.leadership + 1).coerceAtMost(100),
                                tactics = (it.tactics + 1).coerceAtMost(100),
                            )
                        },
                        renown = safeAdd(next.renown, 2),
                    )
                    pulseUpdate(momentum = 3)
                }
                else -> {
                    next = next.copy(
                        resources = next.resources.copy(
                            wood = safeAdd(next.resources.wood, 320),
                            stone = safeAdd(next.resources.stone, 280),
                            iron = safeAdd(next.resources.iron, 120),
                        ),
                        realm = next.realm.copy(
                            threat = (next.realm.threat + 3).coerceAtMost(100)
                        ),
                    )
                    pulseUpdate(momentum = 2)
                }
            }
        }

        val choice = choices(state, decision).first { it.id == choiceId }
        val outcome =
            (if (automatic) "Automatisch: " else "") + decision.title + " — " + choice.label
        next = next.copy(
            campaign = next.campaign.copy(
                pendingDecision = null,
                lastDecisionDay = state.day,
                recentOutcomes = remember(next.campaign.recentOutcomes, outcome),
            ),
            chronicle = (
                next.chronicle +
                    ChronicleEntry(
                        state.day,
                        decision.title,
                        "${choice.label}. ${choice.detail}",
                    )
                ).takeLast(2000),
        )
        return GameEngine.ActionResult(
            next,
            if (automatic) "Die Frist ist verstrichen: ${choice.label}."
            else "${decision.title}: ${choice.label}.",
        )
    }

    private fun remember(current: List<String>, text: String): List<String> =
        (current + text).takeLast(5)

    private fun safeAdd(value: Int, delta: Int): Int =
        (value.toLong() + delta.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
