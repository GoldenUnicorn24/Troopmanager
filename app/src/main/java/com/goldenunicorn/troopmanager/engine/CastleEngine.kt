package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * The fourth walkable place: the courtyard of the local lord's castle. An audience with the
 * Burgvogt can bring a task that is fought out personally but changes the strategic map (a bandit
 * province becomes free again). Two such deeds and enough renown earn the accolade, which opens
 * the border keep for less gold. Tournaments are held here on fixed days.
 */
object CastleEngine {
    data class Layout(
        override val width: Int,
        override val height: Int,
        val dais: List<Cell>,
        val lordSpot: Cell,
        val guards: List<Cell>,
        val tables: List<Cell>,
        val banners: List<Cell>,
        val lists: List<Cell>,
        val entry: Cell,
        val audience: Cell,
        val listsFront: Cell,
        val standing: List<Cell>,
    ) : TavernEngine.WalkGrid {
        val blocked: Set<Cell> = (dais + guards + tables + banners + lists + lordSpot).toSet() + (0 until width).map { Cell(it, 0) }
        override fun walkable(c: Cell): Boolean = c.x in 0 until width && c.y in 0 until height && c !in blocked
    }

    val layout = Layout(
        width = 12, height = 9,
        dais = listOf(Cell(5, 1), Cell(6, 1), Cell(7, 1)),
        lordSpot = Cell(6, 2),
        guards = listOf(Cell(4, 2), Cell(8, 2)),
        tables = listOf(Cell(1, 4), Cell(2, 4), Cell(1, 6), Cell(2, 6)),
        banners = listOf(Cell(0, 1), Cell(11, 1)),
        lists = listOf(Cell(9, 5), Cell(10, 5), Cell(11, 5)),
        entry = Cell(6, 8),
        audience = Cell(6, 3),
        listsFront = Cell(10, 6),
        standing = listOf(Cell(3, 3), Cell(9, 3), Cell(4, 5), Cell(8, 6), Cell(3, 7), Cell(8, 7), Cell(5, 6), Cell(9, 7)),
    )

    const val TOURNAMENT_FEE = 10
    const val AUDIENCE_RENOWN = 15
    const val KNIGHT_RENOWN = 120
    const val KNIGHT_QUESTS = 3
    /** Days the lord needs before he has the next task: deeds are rare, not a daily job. */
    const val QUEST_PAUSE = 5
    const val KNIGHT_KEEP_GOLD = 400

    fun open(state: GameState): Boolean = state.life.timeSlot != TimeSlot.NIGHT

    fun lordId(provinceId: String) = "$provinceId:lord"
    fun lord(state: GameState): NpcIdentity? = state.life.npc(lordId(state.life.provinceId))

    fun ensureLords(state: GameState): GameState {
        val missing = Towns.all.mapNotNull { t ->
            if (state.life.npc(lordId(t.provinceId)) != null) null
            else NpcEngine.generate(lordId(t.provinceId), NpcRole.LORD, t.provinceId).let {
                it.copy(age = it.age.coerceIn(34, 64), socialStanding = "Hochadel", romanceable = false, attractedTo = emptySet(),
                    backstory = "${it.firstName} verwaltet ${t.let { tw -> CrownCatalog.info(tw.provinceId).name }} im Namen der Krone – streng, aber nicht ungerecht.")
            }
        }
        return if (missing.isEmpty()) state else state.copy(life = state.life.copy(npcs = state.life.npcs + missing))
    }

    fun enter(state: GameState): LifeEngine.Result {
        if (LifeEngine.town(state) == null) return LifeEngine.Result(state, "Hier gibt es keine Burg.")
        val ready = ensureLords(LifeEngine.ensurePeople(state))
        val next = ready.copy(life = ready.life.copy(place = LifePlace.CASTLE))
        val t = if (tournamentToday(next)) " Heute ist Turniertag – Banner, Lanzen, Gedränge!" else ""
        return LifeEngine.Result(next, (if (open(next)) "Wachen mustern dich am Tor des Burghofs." else "Das Tor ist für die Nacht verschlossen.") + t)
    }

    fun leave(state: GameState): LifeEngine.Result = LifeEngine.Result(state.copy(life = state.life.copy(place = LifePlace.TOWN)), "Du verlässt den Burghof.")

    data class Placement(val npc: NpcIdentity, val cell: Cell, val lord: Boolean)

    fun placements(state: GameState): List<Placement> {
        if (!open(state)) return emptyList()
        val result = mutableListOf<Placement>()
        lord(state)?.let { result += Placement(it, layout.lordSpot, true) }
        val visitors = (NpcEngine.presentAt(state, state.life.provinceId, LifePlace.CASTLE) +
            state.life.npcs.filter { it.role == NpcRole.NOBLE && it.homeProvinceId == state.life.provinceId && !it.departed &&
                NpcEngine.placeOf(it, state.day, state.life.timeSlot, state).second != LifePlace.AWAY })
            .distinctBy { it.id }.filter { it.id != lordId(state.life.provinceId) }.sortedBy { it.id }
        val free = layout.standing.toMutableList()
        visitors.forEach { npc ->
            if (free.isEmpty()) return@forEach
            val spot = free[Math.floorMod(npc.id.hashCode() + state.day * 7, free.size)]
            free.remove(spot)
            result += Placement(npc, spot, false)
        }
        return result
    }

    // ------------------------------------------------------------ audience and lord's tasks

    fun audienceBlocker(state: GameState): String? = when {
        !open(state) -> "Der Burgvogt empfängt nachts niemanden."
        state.life.landed -> "Als Herrscher verhandelst du über Gesandte, nicht im Burghof."
        state.life.intoxication >= 30 -> "Betrunken vor den Burgvogt? Die Wachen lassen dich nicht vor."
        state.life.renown < AUDIENCE_RENOWN && state.life.background !in setOf(Background.POOR_NOBLE, Background.NOBLE_HEIR) ->
            "Der Burgvogt empfängt nur Leute mit Namen (Ruf ${state.life.renown}/$AUDIENCE_RENOWN)."
        else -> null
    }

    /** The nearest bandit-held province becomes the target. */
    fun questTarget(state: GameState): CrownProvince? {
        val here = CrownCatalog.info(state.life.provinceId).land
        return state.crown.provinces.filter { it.owner == ProvinceOwner.BANDITS }
            .minByOrNull { p -> (if (p.land == here) 0 else if (p.land in (CrownCatalog.neighbors[here] ?: emptySet())) 10 else 30) * 1000 + p.garrison }
    }

    fun askForTask(state: GameState): LifeEngine.Result {
        audienceBlocker(state)?.let { return LifeEngine.Result(state, it) }
        if (state.life.lordQuest != null) return LifeEngine.Result(state, "„Erledige erst, was ich dir aufgetragen habe.“")
        if (state.life.lordQuestsDone > 0 && state.day - state.life.lastQuestDoneDay < QUEST_PAUSE)
            return LifeEngine.Result(state, "„Gute Arbeit, letztes Mal. Komm in ${QUEST_PAUSE - (state.day - state.life.lastQuestDoneDay)} Tagen wieder, dann habe ich Neues.“")
        val lord = lord(state) ?: return LifeEngine.Result(state, "")
        val target = questTarget(state) ?: return LifeEngine.Result(state, "„Die Räuber sind vertrieben. Für den Moment habe ich nichts für dich.“")
        val count = (target.garrison / 120).coerceIn(4, 9)
        val power = (36 + target.garrisonQuality * 2 + state.life.lordQuestsDone * 5).coerceAtMost(90)
        val quest = LordQuest("lq${state.life.nextId}", lord.id, target.id, count, power, 60 + target.wealth * 12, 15, state.day)
        var next = state.copy(life = state.life.copy(lordQuest = quest, nextId = state.life.nextId + 1, lastAudienceDay = state.day))
        next = LifeEngine.updateNpc(next, NpcEngine.remember(lord, "quest_${quest.id}", state.day, "ich habe dir aufgetragen, ${target.name} von den Räubern zu befreien", 1))
        return LifeEngine.Result(LifeEngine.log(next, "Lehnsauftrag: ${target.name} befreien."),
            "„Räuberbarone halten ${target.name}. Bring mir die Stadt zurück, und die Krone wird es nicht vergessen. ${quest.reward} Gold.“")
    }

    fun questBlocker(state: GameState): String? = when {
        state.life.lordQuest == null -> "Kein Auftrag."
        state.life.wound >= 40 -> "Erst die Wunden heilen."
        LifeEngine.slotsLeft(state) < 2 -> "Für den Ritt dorthin ist es zu spät. Morgen früh."
        state.life.intoxication >= 40 -> "Nüchtern werden."
        else -> null
    }

    fun ride(state: GameState): LifeEngine.Result {
        questBlocker(state)?.let { return LifeEngine.Result(state, it) }
        val q = state.life.lordQuest!!
        val name = CrownCatalog.info(q.targetProvinceId).name
        val next = SkirmishEngine.start(LifeEngine.passTime(state, 2).state, SkirmishKind.CAMPAIGN, "Sturm auf $name", "Räuber", q.enemyCount, q.enemyPower,
            q.reward, q.renown, jobId = q.id)
        return LifeEngine.Result(next, "Ihr reitet nach $name. Die Räuber erwarten euch.")
    }

    /** Called when a fight closes: a won lord's task frees the province on the strategic map. */
    fun afterFight(state: GameState, fight: Skirmish): GameState {
        val q = state.life.lordQuest ?: return state
        if (fight.jobId != q.id) return state
        if (fight.outcome != SkirmishOutcome.VICTORY) return state
        val province = CrownEngine.province(state, q.targetProvinceId)
        var next = state
        if (province != null && province.owner == ProvinceOwner.BANDITS) {
            val freed = province.copy(owner = ProvinceOwner.FREE, garrison = (province.garrison * 0.3).toInt(), unrest = (province.unrest - 20).coerceAtLeast(0))
            next = next.copy(crown = next.crown.withProvinces(next.crown.provinces.map { if (it.id == freed.id) freed else it }))
            next = next.copy(life = next.life.copy(news = (next.life.news + WorldNews(next.day, freed.id, "${freed.name} ist frei",
                "${next.player.name} hat die Räuberbarone aus ${freed.name} vertrieben. Man trinkt auf den Namen.", 3)).takeLast(30)),
                chronicle = next.chronicle + ChronicleEntry(next.day, "${freed.name} befreit", "Im Auftrag des Burgvogts vertreibt ${next.player.name} die Räuberbarone aus ${freed.name}."))
        }
        next.life.npc(q.lordId)?.let { lord ->
            next = LifeEngine.updateNpc(next, NpcEngine.remember(lord.copy(relation = lord.relation.copy(
                affinity = (lord.relation.affinity + 15).coerceAtMost(100), trust = (lord.relation.trust + 15).coerceAtMost(100))),
                "quest_done_${q.id}", next.day, "du hast ${CrownCatalog.info(q.targetProvinceId).name} für die Krone befreit", 4))
        }
        return next.copy(life = next.life.copy(lordQuest = null, lordQuestsDone = next.life.lordQuestsDone + 1, lastQuestDoneDay = next.day))
    }

    // ------------------------------------------------------------ rulers: the court day

    const val COURT_DAY_COOLDOWN = 7

    fun courtBlocker(state: GameState): String? {
        val lord = lord(state)
        return when {
            !state.life.landed -> "Nur Herrscher halten Hoftag mit dem Burgvogt."
            !open(state) -> "Der Burgvogt empfängt nachts niemanden."
            lord == null -> "Kein Burgvogt hier."
            state.life.intoxication >= 30 -> "Betrunken? Nicht vor dem Burgvogt."
            state.day - state.life.lastCourtDay < COURT_DAY_COOLDOWN ->
                "Der nächste Hoftag ist an Tag ${state.life.lastCourtDay + COURT_DAY_COOLDOWN}."
            lord.relation.affinity < -10 -> "${lord.firstName} lässt ausrichten, er habe keine Zeit für dich."
            else -> null
        }
    }

    /** Volunteers the lord sends to a befriended ruler's keep; more for knights and old allies. */
    fun courtVolunteers(state: GameState): Int {
        val lord = lord(state) ?: return 0
        return (30 + lord.relation.affinity.coerceAtLeast(0) / 2 + (if (state.life.knighted) 20 else 0) + state.life.lordQuestsDone * 10).coerceAtMost(120)
    }

    /** A ruler's visit: the lord, a peer now, sends volunteers to the keep once a week. */
    fun courtDay(state: GameState): LifeEngine.Result {
        courtBlocker(state)?.let { return LifeEngine.Result(state, it) }
        val lord = lord(state)!!
        val culture = CrownCatalog.info(state.life.provinceId).culture
        val n = courtVolunteers(state)
        var population = ArmyEngine.adjustPopulation(state.population, culture, n)
        population = ArmyEngine.adjustRecruits(population, culture, n)
        var next = state.copy(population = population, life = state.life.copy(lastCourtDay = state.day))
        next = LifeEngine.updateNpc(next, NpcEngine.remember(lord.copy(relation = lord.relation.copy(
            affinity = (lord.relation.affinity + 3).coerceAtMost(100), trust = (lord.relation.trust + 2).coerceAtMost(100))),
            "court_${state.day}", state.day, "wir haben Hoftag gehalten", 1))
        next = LifeEngine.passTime(next, 1).state
        return LifeEngine.Result(LifeEngine.log(next, "Hoftag in ${LifeEngine.locationName(next)}: $n Freiwillige."),
            "„Die Grenze ist auch unsere Sache.“ ${lord.firstName} schickt $n Freiwillige (${culture.label}) zur Grenzfeste.")
    }

    // ------------------------------------------------------------ the accolade

    fun knightBlocker(state: GameState): String? = when {
        state.life.knighted -> "Du bist bereits Ritter."
        state.life.landed -> "Als Herrscher brauchst du keinen Ritterschlag."
        state.life.lordQuestsDone < KNIGHT_QUESTS -> "Erst ${KNIGHT_QUESTS} Taten für die Krone (${state.life.lordQuestsDone}/$KNIGHT_QUESTS)."
        state.life.renown < KNIGHT_RENOWN -> "Dein Name ist noch nicht groß genug (Ruf ${state.life.renown}/$KNIGHT_RENOWN)."
        audienceBlocker(state) != null -> audienceBlocker(state)
        else -> null
    }

    fun knight(state: GameState): LifeEngine.Result {
        knightBlocker(state)?.let { return LifeEngine.Result(state, it) }
        var next = state.copy(life = state.life.copy(knighted = true, career = CareerRank.KNIGHT), title = "Ritter", rank = "Ritter")
        next = LifeEngine.addRenown(next, 10)
        next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, "Ritterschlag",
            "Im Burghof von ${LifeEngine.locationName(next)} kniet ${next.player.name} nieder – und erhebt sich als Ritter."))
        // Nobles everywhere hear of it.
        next = next.copy(life = next.life.copy(npcs = next.life.npcs.map {
            if (it.role == NpcRole.NOBLE || it.role == NpcRole.LORD) it.copy(relation = it.relation.copy(affinity = (it.relation.affinity + 10).coerceAtMost(100))) else it
        }))
        return LifeEngine.Result(LifeEngine.log(next, "Zum Ritter geschlagen."), "Die Klinge berührt deine Schultern. „Erhebt Euch, Ritter.“")
    }

    // ------------------------------------------------------------ tournaments

    /** Aldenburg, the tournament town, every fifth day; elsewhere every seventh. */
    fun tournamentToday(state: GameState, provinceId: String = state.life.provinceId): Boolean =
        state.life.timeSlot in setOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON) &&
            (if (provinceId == "al_aldenburg") state.day % 5 == 0 else state.day % 7 == 0)

    fun nextTournamentDay(state: GameState): Int {
        val every = if (state.life.provinceId == "al_aldenburg") 5 else 7
        return ((state.day / every) + 1) * every
    }

    fun tournamentBlocker(state: GameState): String? = when {
        !tournamentToday(state) -> "Das nächste Turnier ist an Tag ${nextTournamentDay(state)}."
        state.life.tournament?.day == state.day -> if (state.life.tournament?.lost == true) "Für dich ist das Turnier heute vorbei." else null
        state.life.wound >= 30 && state.life.tournament?.day != state.day -> "Verwundet lässt dich der Herold nicht antreten."
        state.life.tournament?.day != state.day && !LifeEngine.canPay(state, TOURNAMENT_FEE) -> "Das Startgeld beträgt $TOURNAMENT_FEE Gold."
        else -> null
    }

    private val knightNames = listOf("Ritter Gerwald vom Stein", "Dame Adela von Hohenlicht", "Ritter Konrad Eisenhall", "Dame Runa vom Rotfeld",
        "Ritter Bertram der Graue", "Dame Isilme Sonnenglanz", "Ritter Odo von Weizenau", "Ritter Han Tao vom Tigerpass")

    fun joinTournament(state: GameState): LifeEngine.Result {
        tournamentBlocker(state)?.let { return LifeEngine.Result(state, it) }
        val run = state.life.tournament?.takeIf { it.day == state.day && !it.lost }
        if (run != null && run.round >= 3) return LifeEngine.Result(state, "Du hast das Turnier bereits gewonnen.")
        val r = Random(state.day * 41L + state.life.provinceId.hashCode())
        val opponents = run?.opponents ?: knightNames.shuffled(r).take(3)
        var next = if (run == null) LifeEngine.pay(state, TOURNAMENT_FEE) else state
        val round = run?.round ?: 0
        next = next.copy(life = next.life.copy(tournament = TournamentRun(state.life.provinceId, state.day, round, opponents)))
        val power = boutPower(state, round)
        next = SkirmishEngine.start(next, SkirmishKind.DUEL, "Turnier · Runde ${round + 1}/3", opponents[round], 1, power,
            if (round == 2) 60 else 0, if (round == 2) 10 else 2)
        return LifeEngine.Result(next, "Der Herold ruft: „${opponents[round]}!“")
    }

    fun boutPower(state: GameState, round: Int): Int = 46 + round * 10 + state.life.tournamentsWon * 3

    /** Odds for the next bout (or the first one), in percent. */
    fun boutOdds(state: GameState): Int {
        val run = state.life.tournament?.takeIf { it.day == state.day && !it.lost }
        return SkirmishEngine.odds(state, SkirmishKind.DUEL, 1, boutPower(state, run?.round ?: 0))
    }

    fun questOdds(state: GameState): Int? = state.life.lordQuest?.let { SkirmishEngine.odds(state, SkirmishKind.CAMPAIGN, it.enemyCount, it.enemyPower) }

    /** After a bout: advance, or leave the lists. */
    fun afterBout(state: GameState, fight: Skirmish): GameState {
        val run = state.life.tournament ?: return state
        if (fight.kind != SkirmishKind.DUEL || !fight.title.startsWith("Turnier")) return state
        val won = fight.outcome == SkirmishOutcome.VICTORY
        var next = state.copy(life = state.life.copy(tournament = run.copy(round = run.round + if (won) 1 else 0, lost = !won)))
        if (won && run.round + 1 >= 3) {
            next = next.copy(life = next.life.copy(tournamentsWon = next.life.tournamentsWon + 1),
                chronicle = next.chronicle + ChronicleEntry(next.day, "Turniersieg", "${next.player.name} gewinnt das Turnier von ${LifeEngine.locationName(next)}."))
            // Whoever watched is impressed.
            next = next.copy(life = next.life.copy(npcs = next.life.npcs.map { n ->
                if (n.homeProvinceId == next.life.provinceId && n.relation.timesMet > 0)
                    n.copy(relation = n.relation.copy(affinity = (n.relation.affinity + 4).coerceAtMost(100),
                        attraction = if (n.romanceable && next.life.gender in n.attractedTo) (n.relation.attraction + 4).coerceAtMost(100) else n.relation.attraction))
                else n
            }))
        }
        return next
    }
}
