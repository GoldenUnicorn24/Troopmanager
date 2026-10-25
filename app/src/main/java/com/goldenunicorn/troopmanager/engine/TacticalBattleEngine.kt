package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * v2.0 tactical battle flow on top of the combat engine: exactly three decisions –
 * the battle plan, the turning point (wall overcome / lines clash) and the end (pursuit or retreat).
 * Everything in between runs as a simulation; small incidents are answered by the plan's doctrine.
 */
object TacticalBattleEngine {
    enum class Kind { STORM, FIELD }

    enum class Stage { PLAN, RUNNING, MIDDLE, AFTER_MIDDLE, FINAL, AFTER_FINAL }

    enum class Plan(
        val label: String,
        val description: String,
        val wall: Boolean,
        val tactic: Tactic?,
        val doctrine: List<BattleDecision>,
    ) {
        HOLD_WALL("Mauer halten", "Jeder Abschnitt bleibt auf der Mauer. Wenig Verluste, der Feind blutet an den Zinnen aus.", true, Tactic.FORTIFY,
            listOf(BattleDecision.REPEL_LADDERS, BattleDecision.FIRE_OIL, BattleDecision.HOLD_GATE, BattleDecision.HOLD_BREACH, BattleDecision.STRENGTHEN_SECTION, BattleDecision.HOLD)),
        ARROW_STORM("Pfeilhagel", "Alle Schützen feuern so früh wie möglich. Stark gegen große Horden, kostet viele Pfeile.", true, Tactic.FORTIFY,
            listOf(BattleDecision.ARROW_VOLLEY, BattleDecision.FOCUS_ARCHERS, BattleDecision.PRIORITIZE_DEVICES, BattleDecision.ARTILLERY_TARGET, BattleDecision.FOCUS_FIRE, BattleDecision.HOLD)),
        DEEP_RESERVE("Starke Reserve", "Frische Truppen ersetzen müde Abschnitte. Hält am längsten durch.", true, Tactic.FORTIFY,
            listOf(BattleDecision.ROTATE_RESERVE, BattleDecision.STRENGTHEN_SECTION, BattleDecision.SEND_RESERVE, BattleDecision.HOLD_BREACH, BattleDecision.HOLD)),
        SALLY("Ausfall", "Das Tor öffnet sich, Ritter und Fußvolk stürmen hinaus. Riskant, aber schnell entschieden.", true, Tactic.AGGRESSIVE,
            listOf(BattleDecision.CAVALRY_CHARGE, BattleDecision.COUNTERATTACK, BattleDecision.ADVANCE, BattleDecision.SEND_RESERVE, BattleDecision.HOLD)),
        SHIELD_WALL("Schildwall", "Die Linie hält und lässt den Feind anrennen. Sicher und geduldig.", false, Tactic.HOLD,
            listOf(BattleDecision.HOLD, BattleDecision.ROTATE_RESERVE, BattleDecision.STRENGTHEN_SECTION, BattleDecision.COUNTERATTACK)),
        VOLLEYS("Pfeilhagel", "Bogenschützen dezimieren den Feind, bevor die Linien sich treffen.", false, Tactic.RANGED,
            listOf(BattleDecision.ARROW_VOLLEY, BattleDecision.FOCUS_ARCHERS, BattleDecision.FOCUS_FIRE, BattleDecision.HOLD)),
        PINCER("Zange", "Die Flügel umfassen den Feind, die Reiterei schlägt in die Flanke.", false, Tactic.FLANK,
            listOf(BattleDecision.CAVALRY_CHARGE, BattleDecision.ADVANCE, BattleDecision.SEND_RESERVE, BattleDecision.COUNTERATTACK, BattleDecision.HOLD)),
        ASSAULT("Sturmangriff", "Alles nach vorn. Bricht schwache Gegner sofort, kostet aber Blut.", false, Tactic.AGGRESSIVE,
            listOf(BattleDecision.ADVANCE, BattleDecision.COUNTERATTACK, BattleDecision.CAVALRY_CHARGE, BattleDecision.SEND_RESERVE, BattleDecision.HOLD)),
    }

    data class Choice(val decision: BattleDecision, val label: String, val detail: String)

    data class Odds(val own: Long, val enemy: Long, val ratio: Double, val label: String, val percent: Int)

    /** Rough fighting value of both sides before the first blow, shown with the battle plan. */
    fun odds(session: BattleSession): Odds {
        fun value(type: UnitType, soldiers: Int, experience: Int, morale: Int) =
            soldiers.toLong() * (type.attack + type.defense + type.ranged) * (100 + experience) / 100 * (50 + morale) / 100
        val own = session.contingents.filter { !it.routed }.sumOf { value(it.type, it.soldiers, it.experience, it.morale) }
        val enemy = if (session.enemyRoster.isNotEmpty()) session.enemyRoster.sumOf { value(it.type, it.soldiers, it.experience, session.fronts.firstOrNull { f -> f.section == it.section }?.morale ?: 80) }
            else session.enemyRemaining.toLong() * 20
        val wallBonus = if (isWall(session)) 1.4 else 1.0
        val ratio = if (enemy <= 0) 9.9 else own * wallBonus / enemy
        val percent = (100 / (1 + Math.pow(1 / ratio.coerceAtLeast(0.05), 2.2))).toInt().coerceIn(3, 97)
        val label = when {
            ratio >= 2.0 -> "Überwältigend"
            ratio >= 1.3 -> "Klarer Vorteil"
            ratio >= 0.95 -> "Ausgeglichen"
            ratio >= 0.7 -> "Schwierig"
            else -> "Aussichtslos"
        }
        return Odds(own, enemy, ratio, label, percent)
    }

    fun stage(session: BattleSession): Stage =
        // Detailed controls can advance a battle without selecting a cinematic plan.
        // Resume those saves in the simulation, without granting preparation bonuses.
        if (session.tacticalStage == Stage.PLAN.ordinal && (session.step > 0 || session.minute > 0)) Stage.RUNNING
        else Stage.entries[session.tacticalStage.coerceIn(0, Stage.entries.lastIndex)]

    fun isWall(session: BattleSession): Boolean =
        if (session.tacticalStage == 0) session.tactic == Tactic.FORTIFY else session.tacticalWall

    fun plans(session: BattleSession): List<Plan> = Plan.entries.filter { it.wall == isWall(session) }

    fun plan(session: BattleSession): Plan? = session.tacticalPlan?.let { runCatching { Plan.valueOf(it) }.getOrNull() }

    /** Starts a battle that is not tied to an invasion, province or boss (weekly storm, sorties). */
    fun start(state: GameState, kind: Kind, title: String, enemy: EnemyType, strength: Int): GameEngine.ActionResult {
        if (state.battleSession != null) return GameEngine.ActionResult(state, "Beende zuerst die laufende Schlacht.")
        if (state.homeArmySize <= 0) return GameEngine.ActionResult(state, "Keine Soldaten daheim.")
        val result = BattleEngine.start(state, enemy, if (kind == Kind.STORM) Tactic.HOLD else Tactic.AGGRESSIVE,
            enemyStrength = strength.coerceAtLeast(1), location = title, enemyArmyName = title, enemyFactionName = CrownCatalog.hordeName(enemy))
        val session = result.state.battleSession ?: return result
        return GameEngine.ActionResult(result.state.copy(battleSession = session.copy(tacticalTitle = title)), "$title beginnt.")
    }

    /** Decision 1: the battle plan. */
    fun choosePlan(state: GameState, plan: Plan): GameEngine.ActionResult {
        val session = state.battleSession ?: return GameEngine.ActionResult(state, "Keine Schlacht aktiv.")
        if (session.status != BattleStatus.ACTIVE || session.step != 0 || session.minute != 0 || session.tacticalStage != 0)
            return GameEngine.ActionResult(state, "Der Schlachtplan kann nur vor Schlachtbeginn gewählt werden.")
        val wall = isWall(session)
        if (plan.wall != wall) return GameEngine.ActionResult(state, "Dieser Plan passt nicht zu dieser Schlacht.")
        val partner = session.contingents.any { it.commanderId == COMPANION_COMMANDER_ID && it.soldiers > 0 } || state.companion.met
        val moraleBoost = 4 + state.player.leadership / 25 + if (partner) 4 else 0
        val updated = session.copy(
            tactic = plan.tactic ?: session.tactic,
            tacticalPlan = plan.name,
            tacticalStage = Stage.RUNNING.ordinal,
            tacticalWall = wall,
            reservePerk = session.reservePerk || plan == Plan.DEEP_RESERVE || plan == Plan.SHIELD_WALL,
            heroPerk = session.heroPerk || plan == Plan.SALLY || plan == Plan.ASSAULT,
            rallyPerk = true,
            moraleBonus = session.moraleBonus + moraleBoost,
            contingents = session.contingents.map { c ->
                val wallBonus = if (wall && ReignEngine.has(state, WonderKind.GREAT_WALL)) 15 else 0
                c.copy(morale = (c.morale + moraleBoost).coerceAtMost(100), cohesion = (c.cohesion + wallBonus).coerceAtMost(100))
            },
            segments = if (wall && ReignEngine.has(state, WonderKind.GREAT_WALL)) session.segments.map { it.copy(cover = (it.cover + 0.1).coerceAtMost(0.85)) } else session.segments,
            fronts = if (ReignEngine.ballistaeMorale(state) > 0 && wall) session.fronts.map { it.copy(morale = (it.morale - ReignEngine.ballistaeMorale(state)).coerceAtLeast(5)) } else session.fronts,
            log = (session.log + BattleLogEntry(0, "Schlachtplan: ${plan.label}. ${plan.description}")).takeLast(60),
        )
        // Plan selection is preparation, just like redeployment: replay begins with the
        // actual army and fortifications, without reapplying bonuses or debiting supplies.
        val replay = updated.replayStart?.copy(
            initialContingents = updated.contingents,
            initialFronts = updated.fronts,
            initialSegments = updated.segments,
            heroPerk = updated.heroPerk,
            reservePerk = updated.reservePerk,
            rallyPerk = updated.rallyPerk,
            moraleBonus = updated.moraleBonus,
        )
        return GameEngine.ActionResult(state.copy(battleSession = updated.copy(replayStart = replay)), "Schlachtplan „${plan.label}“ ausgegeben.")
    }

    /** One simulation tick: advances five battle minutes or opens the next key decision. */
    fun step(state: GameState): GameEngine.ActionResult = track(state, rawStep(state))

    private fun track(before: GameState, result: GameEngine.ActionResult): GameEngine.ActionResult {
        val was = before.battleSession ?: return result
        val now = result.state.battleSession ?: return result
        if (!was.isActive || now.isActive || now.status != BattleStatus.VICTORY || was.tacticalPlan == null) return result
        return result.copy(state = result.state.copy(reign = result.state.reign.copy(tacticalVictories = result.state.reign.tacticalVictories + 1)))
    }

    private fun rawStep(state: GameState): GameEngine.ActionResult {
        val session = state.battleSession ?: return GameEngine.ActionResult(state, "Keine Schlacht aktiv.")
        if (!session.isActive) return GameEngine.ActionResult(state, "Die Schlacht ist entschieden.")
        when (stage(session)) {
            Stage.PLAN -> return GameEngine.ActionResult(state, "Wähle zuerst einen Schlachtplan.")
            Stage.MIDDLE, Stage.FINAL -> return GameEngine.ActionResult(state, "Deine Feldherren warten auf deinen Befehl.")
            else -> Unit
        }
        if (session.status == BattleStatus.PURSUIT) {
            return if (session.tacticalStage < Stage.FINAL.ordinal) GameEngine.ActionResult(openFinal(state, session, winning = true), "Der Feind wankt!")
            else BattleEngine.advance(state, BattleDecision.HOLD_FORMATION)
        }
        val event = session.pendingEvent
        if (event != null) {
            val answered = BattleEngine.advance(state, autoAnswer(session, event))
            return if (answered.state !== state) answered else BattleEngine.advance(state.copy(battleSession = session.copy(pendingEvent = null)))
        }
        if (session.tacticalStage < Stage.MIDDLE.ordinal && middleReached(session)) return GameEngine.ActionResult(openMiddle(state, session), "Der Wendepunkt der Schlacht!")
        if (session.tacticalStage in Stage.AFTER_MIDDLE.ordinal until Stage.FINAL.ordinal && losing(session))
            return GameEngine.ActionResult(openFinal(state, session, winning = false), "Die Lage wird kritisch.")
        return BattleEngine.advance(state)
    }

    /** Decisions 2 and 3. */
    fun decide(state: GameState, decision: BattleDecision): GameEngine.ActionResult {
        val session = state.battleSession ?: return GameEngine.ActionResult(state, "Keine Schlacht aktiv.")
        val stage = stage(session)
        if (stage != Stage.MIDDLE && stage != Stage.FINAL) return GameEngine.ActionResult(state, "Gerade ist kein Befehl gefragt.")
        val result = BattleEngine.advance(state, decision)
        val after = result.state.battleSession ?: return result
        if (result.state === state) return result
        val label = choices(state, session).firstOrNull { it.decision == decision }?.label ?: decision.label
        return track(state, GameEngine.ActionResult(result.state.copy(battleSession = after.copy(tacticalStage = if (stage == Stage.MIDDLE) Stage.AFTER_MIDDLE.ordinal else Stage.AFTER_FINAL.ordinal,
            log = (after.log + BattleLogEntry(after.minute, "Befehl: $label")).takeLast(60))), label))
    }

    /** Runs the rest of the battle without further decisions (used for "Schnell entscheiden"). */
    fun autoResolve(state: GameState): GameState {
        var next = state
        var guard = 0
        while (next.battleSession?.isActive == true && guard++ < 400) {
            val s = next.battleSession!!
            next = when (stage(s)) {
                Stage.PLAN -> choosePlan(next, recommendedPlan(next, s)).state
                Stage.MIDDLE, Stage.FINAL -> {
                    val first = choices(next, s).firstOrNull()?.decision
                    if (first == null) next.copy(battleSession = s.copy(pendingEvent = null, tacticalStage = s.tacticalStage + 1)) else decide(next, first).state
                }
                else -> step(next).state.let { if (it === next) it.copy(battleSession = s.copy(pendingEvent = null)) else it }
            }
        }
        return next
    }

    fun recommendedPlan(state: GameState, session: BattleSession): Plan {
        val archers = session.contingents.filter { it.type.ranged >= 8 }.sumOf { it.soldiers }
        val knights = session.contingents.filter { it.type == UnitType.KNIGHT }.sumOf { it.soldiers }
        val ratio = session.ownStart.toDouble() / session.enemyStart.coerceAtLeast(1)
        return if (isWall(session)) when {
            archers * 3 > session.ownStart -> Plan.ARROW_STORM
            ratio > 1.6 && knights > 0 -> Plan.SALLY
            ratio < 0.8 -> Plan.DEEP_RESERVE
            else -> Plan.HOLD_WALL
        } else when {
            archers * 3 > session.ownStart -> Plan.VOLLEYS
            ratio > 1.8 -> Plan.ASSAULT
            knights > 0 -> Plan.PINCER
            else -> Plan.SHIELD_WALL
        }
    }

    /** Options for the open key decision, each explained in one line. */
    fun choices(state: GameState, session: BattleSession): List<Choice> {
        val event = session.pendingEvent ?: return emptyList()
        val partner = state.companion.met && session.contingents.any { it.soldiers > 0 && (it.commanderId == COMPANION_COMMANDER_ID) }
        return event.options.filter { BattleEngine.canOrder(session, it, event.section) }.map { d ->
            Choice(d, labelFor(d, partner, state), detailFor(d, session))
        }
    }

    private fun labelFor(d: BattleDecision, partner: Boolean, state: GameState): String = when (d) {
        BattleDecision.HOLD_BREACH -> "Bresche halten"
        BattleDecision.COUNTERATTACK -> "Gegenstoß"
        BattleDecision.SECOND_LINE -> "Zweite Linie bilden"
        BattleDecision.FALL_BACK_COURTYARD -> "In den Innenhof locken"
        BattleDecision.REPEL_LADDERS -> "Leitern zurückstoßen"
        BattleDecision.FIRE_OIL -> "Siedendes Öl"
        BattleDecision.STRENGTHEN_SECTION -> "Reserve auf die Mauer"
        BattleDecision.ROTATE_RESERVE -> "Frische Truppen nach vorn"
        BattleDecision.ADVANCE -> "Vorrücken"
        BattleDecision.CAVALRY_CHARGE -> "Reiterangriff"
        BattleDecision.SEND_RESERVE -> "Reserve werfen"
        BattleDecision.HOLD -> "Linie halten"
        BattleDecision.RALLY -> if (partner) "${state.companion.name} sammelt die Reihen" else "Schlachtrede"
        BattleDecision.PURSUE -> "Verfolgen"
        BattleDecision.HOLD_FORMATION -> "Formation halten"
        BattleDecision.ORDERED_RETREAT -> "Geordneter Rückzug"
        else -> d.label
    }

    private fun detailFor(d: BattleDecision, session: BattleSession): String = when (d) {
        BattleDecision.HOLD_BREACH -> "Die besten Krieger verschließen die Lücke. Sicher, aber langsam."
        BattleDecision.COUNTERATTACK -> "Hinein in den Feind, solange er ungeordnet ist. Hohe Wirkung, höhere Verluste."
        BattleDecision.SECOND_LINE -> "Hinter der Bresche entsteht eine neue Linie. Der Feind läuft in eine Falle."
        BattleDecision.FALL_BACK_COURTYARD -> "Die Mauer wird aufgegeben, in den Gassen zählt die Überzahl weniger."
        BattleDecision.REPEL_LADDERS -> "Die Leitern fallen, bevor der Feind die Zinnen erreicht."
        BattleDecision.FIRE_OIL -> "Kessel kippen über dem Tor. Verheerend auf engem Raum."
        BattleDecision.STRENGTHEN_SECTION, BattleDecision.SEND_RESERVE -> "Die Reserve rückt in den bedrohten Abschnitt."
        BattleDecision.ROTATE_RESERVE -> "Erschöpfte Kämpfer werden gegen frische ersetzt."
        BattleDecision.ADVANCE -> "Die ganze Linie drückt nach vorn."
        BattleDecision.CAVALRY_CHARGE -> "Ritter brechen in Flanke und Rücken des Feindes."
        BattleDecision.HOLD -> "Keine Experimente, die Linie steht."
        BattleDecision.RALLY -> "Moral +18 im Abschnitt, geflohene Soldaten kehren zurück."
        BattleDecision.PURSUE -> "Mehr Beute und gefallene Feinde, aber die Linie löst sich auf."
        BattleDecision.HOLD_FORMATION -> "Der Sieg ist sicher, keine weiteren Verluste."
        BattleDecision.ORDERED_RETREAT -> "Die Schlacht ist verloren, aber das Heer bleibt erhalten."
        else -> "${d.label} im Abschnitt ${session.pendingEvent?.section?.label ?: ""}."
    }

    // ------------------------------------------------------------------ internals

    private fun middleReached(session: BattleSession): Boolean {
        val wall = session.tacticalWall
        val states = session.segments.map { it.contactState }
        return if (wall) BattleContactState.BREACHED in states || BattleContactState.COURTYARD in states ||
            BattleContactState.WALL_ASSAULT in states || session.minute >= 45
        else BattleContactState.FIELD_CONTACT in states || BattleContactState.WALL_ASSAULT in states || session.minute >= 30
    }

    private fun losing(session: BattleSession): Boolean {
        val own = session.fightingRemaining.toDouble() / session.ownStart.coerceAtLeast(1)
        val enemy = session.enemyRemaining.toDouble() / session.enemyStart.coerceAtLeast(1)
        return (own < 0.5 && own < enemy) || session.morale < 32
    }

    private fun hotSection(session: BattleSession): BattleSection {
        val breach = session.segments.firstOrNull { it.contactState == BattleContactState.BREACHED || it.contactState == BattleContactState.COURTYARD }
            ?: session.segments.firstOrNull { it.contactState == BattleContactState.WALL_ASSAULT || it.contactState == BattleContactState.FIELD_CONTACT }
        if (breach != null) return breach.section
        return session.fronts.filter { it.enemySoldiers > 0 }.maxByOrNull { it.enemySoldiers }?.section ?: BattleSection.CENTER
    }

    private fun openMiddle(state: GameState, session: BattleSession): GameState {
        val section = hotSection(session)
        val wall = session.tacticalWall
        val contact = session.segment(section)?.contactState
        val candidates = when {
            wall && (contact == BattleContactState.BREACHED || contact == BattleContactState.COURTYARD) ->
                listOf(BattleDecision.HOLD_BREACH, BattleDecision.COUNTERATTACK, BattleDecision.SECOND_LINE, BattleDecision.FALL_BACK_COURTYARD, BattleDecision.RALLY)
            wall -> listOf(BattleDecision.REPEL_LADDERS, BattleDecision.FIRE_OIL, BattleDecision.STRENGTHEN_SECTION, BattleDecision.ROTATE_RESERVE, BattleDecision.COUNTERATTACK, BattleDecision.RALLY, BattleDecision.HOLD)
            else -> listOf(BattleDecision.CAVALRY_CHARGE, BattleDecision.ADVANCE, BattleDecision.COUNTERATTACK, BattleDecision.SEND_RESERVE, BattleDecision.RALLY, BattleDecision.HOLD)
        }
        val ready = session.copy(commandPoints = session.maxCommandPoints)
        val options = candidates.filter { BattleEngine.canOrder(ready, it, section) }.take(4).ifEmpty { listOf(BattleDecision.HOLD) }
        val (title, text) = when {
            wall && contact in listOf(BattleContactState.BREACHED, BattleContactState.COURTYARD) -> "Die Mauer ist gefallen!" to "Am Abschnitt ${section.label} strömt der Feind durch die Bresche. Was jetzt geschieht, entscheidet die Schlacht."
            wall -> "Der Feind ist an den Zinnen!" to "Leitern und Kletterer erreichen ${section.label}. Jetzt zählt jeder Befehl."
            else -> "Die Linien prallen aufeinander!" to "Am Abschnitt ${section.label} beginnt der Nahkampf. Wie greifst du ein?"
        }
        return state.copy(battleSession = ready.copy(
            pendingEvent = BattleEvent(title, text, section, options),
            tacticalStage = Stage.MIDDLE.ordinal,
        ))
    }

    private fun openFinal(state: GameState, session: BattleSession, winning: Boolean): GameState {
        val ready = session.copy(commandPoints = session.maxCommandPoints)
        if (winning) {
            val event = session.pendingEvent ?: BattleEvent("Der Gegner flieht", "", BattleSection.CENTER, listOf(BattleDecision.PURSUE, BattleDecision.HOLD_FORMATION))
            return state.copy(battleSession = ready.copy(tacticalStage = Stage.FINAL.ordinal,
                pendingEvent = event.copy(title = "Der Feind flieht!", text = "${session.enemyArmyName ?: "Der Gegner"} bricht. Verfolgen bringt Beute und Ruhm, kostet aber Soldaten.")))
        }
        val section = hotSection(session)
        val options = listOf(BattleDecision.RALLY, BattleDecision.ORDERED_RETREAT, BattleDecision.HOLD).filter { BattleEngine.canOrder(ready, it, section) }
        return state.copy(battleSession = ready.copy(tacticalStage = Stage.FINAL.ordinal,
            pendingEvent = BattleEvent("Die Lage kippt", "Nur noch ${session.fightingRemaining} deiner Soldaten stehen, die Moral liegt bei ${session.morale} %. Letztes Aufgebot oder Rückzug?", section, options)))
    }

    /** Answers incidents between the key decisions with the plan's doctrine. */
    private fun autoAnswer(session: BattleSession, event: BattleEvent): BattleDecision {
        val doctrine = plan(session)?.doctrine ?: listOf(BattleDecision.HOLD)
        fun ok(d: BattleDecision) = d in event.options && BattleEngine.canOrder(session, d, event.section) && BattleEngine.orderCost(session, d) <= session.commandPoints
        return doctrine.firstOrNull(::ok) ?: event.options.firstOrNull(::ok) ?: BattleDecision.HOLD.takeIf { it in event.options } ?: event.options.first()
    }
}
