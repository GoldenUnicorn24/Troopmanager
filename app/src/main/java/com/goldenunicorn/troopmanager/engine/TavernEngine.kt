package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.abs
import kotlin.math.max

/**
 * The walkable tavern: a tile layout with furniture, seats, a door and a bar, A* pathfinding for
 * tap-to-move, deterministic seating of whoever is present, plus tavern-only activities (dice,
 * brawls, events). Rendering lives in the UI; everything here is plain, testable logic.
 */
object TavernEngine {
    data class Cell(val x: Int, val y: Int) {
        operator fun plus(o: Cell) = Cell(x + o.x, y + o.y)
        fun dist(o: Cell) = max(abs(x - o.x), abs(y - o.y))
    }

    enum class SeatKind { BAR, TABLE, FIRE, STAGE, STAND }

    /** Anything you can walk on: the tavern, the market square, later more places. */
    interface WalkGrid {
        val width: Int
        val height: Int
        fun walkable(c: Cell): Boolean
    }

    data class Seat(val cell: Cell, val kind: SeatKind)

    data class Layout(
        override val width: Int,
        override val height: Int,
        val counter: List<Cell>,
        val tables: List<Cell>,
        val hearth: List<Cell>,
        val stairs: List<Cell>,
        val barrels: List<Cell>,
        val door: Cell,
        val innkeeper: Cell,
        val barFront: Cell,
        val stairFoot: Cell,
        val seats: List<Seat>,
    ) : WalkGrid {
        val blocked: Set<Cell> = (counter + tables + hearth + stairs + barrels).toSet() +
            (0 until width).map { Cell(it, 0) } + // back wall
            (1..4).map { Cell(it, 1) } // behind the bar: staff only

        override fun walkable(c: Cell): Boolean = c.x in 0 until width && c.y in 0 until height && c !in blocked
    }

    /** One hand-designed common room, 12×9 tiles, used by every tavern (names and people differ). */
    val layout = Layout(
        width = 12,
        height = 9,
        counter = (1..4).map { Cell(it, 2) },
        tables = listOf(Cell(3, 6), Cell(7, 5), Cell(9, 7)),
        hearth = listOf(Cell(8, 1), Cell(9, 1)),
        stairs = listOf(Cell(11, 4), Cell(11, 5)),
        barrels = listOf(Cell(0, 2), Cell(0, 3)),
        door = Cell(6, 8),
        innkeeper = Cell(2, 1),
        barFront = Cell(3, 3),
        stairFoot = Cell(10, 5),
        seats = listOf(
            Seat(Cell(1, 3), SeatKind.BAR), Seat(Cell(2, 3), SeatKind.BAR), Seat(Cell(4, 3), SeatKind.BAR),
            Seat(Cell(2, 6), SeatKind.TABLE), Seat(Cell(4, 6), SeatKind.TABLE), Seat(Cell(3, 7), SeatKind.TABLE), Seat(Cell(3, 5), SeatKind.TABLE),
            Seat(Cell(6, 5), SeatKind.TABLE), Seat(Cell(8, 5), SeatKind.TABLE), Seat(Cell(7, 4), SeatKind.TABLE), Seat(Cell(7, 6), SeatKind.TABLE),
            Seat(Cell(8, 7), SeatKind.TABLE), Seat(Cell(10, 7), SeatKind.TABLE), Seat(Cell(9, 6), SeatKind.TABLE),
            Seat(Cell(7, 2), SeatKind.FIRE), Seat(Cell(10, 2), SeatKind.FIRE),
            Seat(Cell(5, 1), SeatKind.STAGE),
            Seat(Cell(1, 7), SeatKind.STAND), Seat(Cell(9, 3), SeatKind.STAND),
        ),
    )

    // ------------------------------------------------------------ pathfinding

    private val steps = listOf(Cell(1, 0), Cell(-1, 0), Cell(0, 1), Cell(0, -1), Cell(1, 1), Cell(1, -1), Cell(-1, 1), Cell(-1, -1))

    /**
     * A* over the tile grid with diagonal steps that never cut furniture corners.
     * Returns the cells after [from] up to [to]; empty if unreachable or already there.
     */
    fun path(from: Cell, to: Cell, occupied: Set<Cell> = emptySet(), l: WalkGrid = layout): List<Cell> {
        if (from == to || !l.walkable(to)) return emptyList()
        fun free(c: Cell) = l.walkable(c) && (c !in occupied || c == to)
        val open = java.util.PriorityQueue<Pair<Cell, Int>>(compareBy { it.second })
        val g = hashMapOf(from to 0)
        val came = hashMapOf<Cell, Cell>()
        open += from to 0
        while (open.isNotEmpty()) {
            val (current, _) = open.poll()!!
            if (current == to) break
            for (d in steps) {
                val n = current + d
                if (!free(n)) continue
                if (d.x != 0 && d.y != 0 && (!free(Cell(current.x + d.x, current.y)) || !free(Cell(current.x, current.y + d.y)))) continue
                val cost = g.getValue(current) + if (d.x != 0 && d.y != 0) 14 else 10
                if (cost < (g[n] ?: Int.MAX_VALUE)) {
                    g[n] = cost
                    came[n] = current
                    open += n to cost + 10 * n.dist(to)
                }
            }
        }
        if (to !in came) return emptyList()
        val result = ArrayDeque<Cell>()
        var c = to
        while (c != from) { result.addFirst(c); c = came.getValue(c) }
        return result.toList()
    }

    /** Closest free tile next to a person or object, to walk up and talk. */
    fun approach(target: Cell, from: Cell, occupied: Set<Cell>, l: WalkGrid = layout): Cell? =
        steps.map { target + it }.filter { l.walkable(it) && it !in occupied }
            .minByOrNull { abs(it.x - from.x) + abs(it.y - from.y) }

    // ------------------------------------------------------------ who sits where

    data class Placement(val npc: NpcIdentity, val cell: Cell, val kind: SeatKind)

    fun present(state: GameState): List<NpcIdentity> =
        NpcEngine.presentAt(state, state.life.provinceId, LifePlace.TAVERN)
            .sortedBy { it.id }
            // A full room in the evening, a few early drinkers at noon.
            .let { all ->
                val cap = when (state.life.timeSlot) {
                    TimeSlot.MORNING -> 3; TimeSlot.NOON -> 5; TimeSlot.AFTERNOON -> 5; TimeSlot.EVENING -> 11; TimeSlot.NIGHT -> 7
                }
                val keep = all.filter { it.role == NpcRole.INNKEEPER || it.designedByPlayer || it.relation.stage.romantic }
                (keep + all.filterNot { it in keep }).take(cap + 1)
            }

    fun placements(state: GameState): List<Placement> {
        val people = present(state)
        val result = mutableListOf<Placement>()
        val free = layout.seats.toMutableList()
        fun take(npc: NpcIdentity, prefer: SeatKind?) {
            // Couples sit together: next to the partner if the partner already has a seat.
            val partnerCell = NpcSocialEngine.partnerOf(state, npc.id)?.let { p -> result.firstOrNull { it.npc.id == p.id }?.cell }
            // Side by side if possible; never directly in front of or behind each other (figures would overlap).
            val pool = (partnerCell?.let { pc ->
                free.filter { it.cell.y == pc.y && it.cell.dist(pc) == 1 }
                    .ifEmpty { free.filter { it.cell.dist(pc) <= 1 && it.cell.x != pc.x } }
                    .ifEmpty { free.filter { it.cell.dist(pc) <= 2 && it.cell.x != pc.x } }
            } ?: emptyList())
                .ifEmpty { free.filter { prefer == null || it.kind == prefer } }.ifEmpty { free }
            if (pool.isEmpty()) return
            val seat = pool[Math.floorMod(npc.id.hashCode() + state.day * 7 + state.life.timeSlot.ordinal * 3, pool.size)]
            free.remove(seat)
            result += Placement(npc, seat.cell, seat.kind)
        }
        people.forEach { npc ->
            when (npc.role) {
                NpcRole.INNKEEPER -> result += Placement(npc, layout.innkeeper, SeatKind.BAR)
                NpcRole.BARD -> take(npc, SeatKind.STAGE)
                NpcRole.COMPANION -> take(npc, SeatKind.FIRE)
                NpcRole.MERCENARY_CAPTAIN, NpcRole.NOBLE -> take(npc, SeatKind.TABLE)
                else -> take(npc, if (Math.floorMod(npc.id.hashCode() + state.day, 3) == 0) SeatKind.BAR else null)
            }
        }
        return result
    }

    // ------------------------------------------------------------ entering and leaving

    fun enter(state: GameState): LifeEngine.Result {
        if (LifeEngine.town(state) == null) return LifeEngine.Result(state, "Hier gibt es keine Taverne.")
        if (state.life.travel != null) return LifeEngine.Result(state, "Du bist unterwegs.")
        val next = NpcSocialEngine.ensureBonds(state.copy(life = state.life.copy(place = LifePlace.TAVERN)))
        val town = LifeEngine.town(next)!!
        val count = present(next).size
        val mood = when {
            LifeEngine.threatened(next, next.life.provinceId) -> "Die Stimmung ist gedrückt; man spricht leise über Krieg."
            next.life.timeSlot == TimeSlot.EVENING -> "Die Schankstube ist voll und laut."
            next.life.timeSlot == TimeSlot.MORNING -> "Es riecht nach kaltem Rauch und frischem Brot."
            else -> "Ein paar Gäste sitzen bei Bier und Würfeln."
        }
        return LifeEngine.Result(next, "„${town.tavernName}“ · $count Leute. $mood")
    }

    fun leave(state: GameState): LifeEngine.Result =
        LifeEngine.Result(state.copy(life = state.life.copy(place = LifePlace.TOWN)), "Du trittst hinaus auf den Platz von ${LifeEngine.locationName(state)}.")

    // ------------------------------------------------------------ dice

    data class DiceRound(val state: GameState, val player: Pair<Int, Int>, val opponent: Pair<Int, Int>, val won: Boolean?, val message: String)

    /** Two dice each; a sober, nimble hand may re-roll its worst die. Deterministic per round. */
    fun dice(state: GameState, stake: Int): DiceRound {
        if (!LifeEngine.canPay(state, stake)) return DiceRound(state, 0 to 0, 0 to 0, null, "Dafür reicht dein Geld nicht.")
        val round = state.life.diceWins + state.life.diceLosses
        val seed = kotlin.random.Random(state.day * 1009L + state.life.timeSlot.ordinal * 101 + round * 13 + state.life.provinceId.hashCode())
        var a = seed.nextInt(1, 7)
        var b = seed.nextInt(1, 7)
        val c = seed.nextInt(1, 7)
        val d = seed.nextInt(1, 7)
        val reroll = seed.nextInt(1, 7)
        if (LifeEngine.dexterity(state) >= 55 && minOf(a, b) < reroll) { if (a < b) a = reroll else b = reroll }
        val mine = a + b
        val theirs = c + d
        val won: Boolean? = when { mine > theirs -> true; mine < theirs -> false; else -> null }
        var next = when (won) { true -> LifeEngine.earn(state, stake); false -> LifeEngine.pay(state, stake); null -> state }
        next = next.copy(life = next.life.copy(
            diceWins = next.life.diceWins + if (won == true) 1 else 0,
            diceLosses = next.life.diceLosses + if (won == true) 0 else 1,
        ))
        val msg = when (won) {
            true -> "$a + $b gegen $c + $d – du gewinnst $stake Gold!"
            false -> "$a + $b gegen $c + $d – $stake Gold sind weg."
            null -> "$a + $b gegen $c + $d – Gleichstand. Der Einsatz bleibt liegen."
        }
        return DiceRound(LifeEngine.log(next, msg), a to b, c to d, won, msg)
    }

    // ------------------------------------------------------------ brawl

    data class Brawl(val state: GameState, val won: Boolean, val text: String)

    fun brawl(state: GameState, npcId: String): Brawl {
        val npc = state.life.npc(npcId) ?: return Brawl(state, false, "")
        val me = (state.player.sword * 2 + LifeEngine.dexterity(state) + (state.life.appearance?.muscle ?: 50)) / 4
        val them = (npc.personality.boldness + npc.personality.temper + (npc.appearance.muscle) +
            if (npc.role == NpcRole.MERCENARY_CAPTAIN) 60 else 20) / 3
        val variance = Math.floorMod(npc.id.hashCode() + state.day * 11, 21) - 10
        val won = me + variance >= them
        var next = state.copy(life = state.life.copy(brawls = state.life.brawls + 1, fatigue = (state.life.fatigue + if (won) 15 else 30).coerceAtMost(100)))
        next = NpcSocialEngine.ripple(next, npc.id, -12)
        val keeper = next.life.npcs.firstOrNull { it.role == NpcRole.INNKEEPER && it.homeProvinceId == next.life.provinceId }
        if (keeper != null) next = LifeEngine.updateNpc(next, NpcEngine.remember(keeper.copy(relation = keeper.relation.copy(affinity = keeper.relation.affinity - 8)),
            "brawl_${next.day}", next.day, "du hast in meiner Schänke eine Schlägerei angefangen", -2))
        return if (won) {
            next = LifeEngine.addRenown(next, 2)
            Brawl(LifeEngine.log(next, "Schlägerei gegen ${npc.name} gewonnen."), true,
                "Ein Hieb, ein Ausweichen, ein zweiter Hieb – ${npc.firstName} landet zwischen den Bänken. Die halbe Schänke johlt, der Wirt flucht.")
        } else {
            val fine = minOf(5, LifeEngine.money(next))
            next = LifeEngine.pay(next, fine)
            next = next.copy(life = next.life.copy(place = LifePlace.TOWN))
            Brawl(LifeEngine.log(next, "Schlägerei gegen ${npc.name} verloren."), false,
                "Du siehst die Faust zu spät. Als du wieder klar siehst, liegst du draußen im Matsch. Der Wirt behält $fine Gold für den zerbrochenen Stuhl.")
        }
    }

    // ------------------------------------------------------------ events

    data class EventOption(val id: String, val label: String, val hint: String)
    data class TavernEvent(val key: String, val title: String, val text: String, val npcId: String?, val options: List<EventOption>)

    private fun eventKey(state: GameState, key: String) = "${state.day}:${state.life.timeSlot.ordinal}:${state.life.provinceId}:$key"

    /** The scene of the moment, reacting to who is here, the time and the state of the world. */
    fun event(state: GameState): TavernEvent? {
        if (state.life.place != LifePlace.TAVERN) return null
        val people = present(state)
        val slot = state.life.timeSlot
        val threatened = LifeEngine.threatened(state, state.life.provinceId)
        val candidates = buildList {
            val captain = people.firstOrNull { it.role == NpcRole.MERCENARY_CAPTAIN }
            if (captain != null && threatened) add(TavernEvent("recruit", "Die Kompanie sucht Klingen",
                "${captain.name} steigt auf einen Tisch: „Die Horden stehen vor ${LifeEngine.locationName(state)}! Wer kämpfen kann, verdient bei mir gutes Gold.“",
                captain.id, listOf(EventOption("join", "Anheuern", "Neuer Auftrag: Feldzug (2 Tageszeiten, viel Gold und Ruf)"), EventOption("ignore", "Nicht jetzt", "Nichts passiert"))))
            val bard = people.firstOrNull { it.role == NpcRole.BARD }
            if (bard != null && slot >= TimeSlot.EVENING) add(TavernEvent("song", "${bard.firstName} spielt auf",
                "Eine Laute, eine raue Stimme, ein Lied über die letzte Mauer. Die halbe Schänke singt den Kehrreim mit.",
                bard.id, listOf(EventOption("sing", "Mitsingen", "Alle Anwesenden mögen dich etwas mehr"), EventOption("coin", "Eine Münze werfen (1 Gold)", "${bard.firstName} wird es sich merken"))))
            val hothead = people.firstOrNull { it.personality.temper >= 60 && it.role == NpcRole.REGULAR }
            if (hothead != null && slot >= TimeSlot.EVENING) add(TavernEvent("quarrel", "Streit am Tresen",
                "${hothead.name} brüllt einen Fischer an. Ein Becher fliegt. Gleich kracht es.",
                hothead.id, listOf(EventOption("calm", "Schlichten", "Diplomatie gegen Hitzkopf"), EventOption("join", "Mitmischen", "Schlägerei"), EventOption("ignore", "Wegsehen", "Nichts passiert"))))
            rivalsPresent(state, people)?.let { (x, y) ->
                if (slot >= TimeSlot.AFTERNOON) add(TavernEvent("rivals", "Alte Feindschaft",
                    "${x.name} und ${y.name} stehen sich gegenüber. Jeder hier weiß, dass die beiden sich nicht ausstehen können. Ein falsches Wort, und es fliegen Fäuste.",
                    x.id, listOf(EventOption("calm", "Dazwischengehen", "Diplomatie gegen beider Temperament – gelingt es, wird die Feindschaft milder"),
                        EventOption("side_a", "${x.firstName} beistehen", "${x.firstName} mag dich mehr, ${y.firstName} weniger"),
                        EventOption("side_b", "${y.firstName} beistehen", "${y.firstName} mag dich mehr, ${x.firstName} weniger"),
                        EventOption("ignore", "Wegsehen", "Nichts passiert"))))
            }
            val noble = people.firstOrNull { it.role == NpcRole.NOBLE }
            if (noble != null) add(TavernEvent("incognito", "Ein zu feiner Mantel",
                "Am Ecktisch sitzt jemand, der sich Mühe gibt, gewöhnlich zu wirken. Die Stiefel verraten es: zu teuer für diese Schänke.",
                noble.id, listOf(EventOption("ignore", "Beobachten", "Vielleicht ergibt sich später ein Gespräch"))))
            val traveler = people.firstOrNull { it.role == NpcRole.TRAVELER }
            if (traveler != null && slot != TimeSlot.MORNING) add(TavernEvent("story", "Eine Geschichte vom Weg",
                "${traveler.name} erzählt von ${LifeEngine.rumors(state).firstOrNull()?.title ?: "den Straßen im Süden"}. Alle hören zu.",
                traveler.id, listOf(EventOption("listen", "Zuhören", "Du erfährst Neuigkeiten"))))
        }.filter { eventKey(state, it.key) !in state.life.eventsDone }
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(state.day * 5 + slot.ordinal, candidates.size)]
    }

    /** Two people present who cannot stand each other. */
    fun rivalsPresent(state: GameState, people: List<NpcIdentity>): Pair<NpcIdentity, NpcIdentity>? {
        val ids = people.map { it.id }.toSet()
        val bond = state.life.bonds.filter { it.kind == BondKind.RIVALS && it.a in ids && it.b in ids }.minByOrNull { it.a + it.b } ?: return null
        return (state.life.npc(bond.a) ?: return null) to (state.life.npc(bond.b) ?: return null)
    }

    fun resolve(state: GameState, eventKey: String, optionId: String): LifeEngine.Result {
        val event = event(state)?.takeIf { it.key == eventKey } ?: return LifeEngine.Result(state, "Der Moment ist vorbei.")
        var next = state.copy(life = state.life.copy(eventsDone = (state.life.eventsDone + eventKey(state, eventKey)).takeLast(30)))
        val npc = event.npcId?.let { next.life.npc(it) }
        fun bump(n: NpcIdentity, aff: Int, memory: String? = null, text: String = "", weight: Int = 1): NpcIdentity {
            val changed = n.copy(relation = n.relation.copy(affinity = (n.relation.affinity + aff).coerceIn(-100, 100)))
            return if (memory != null) NpcEngine.remember(changed, memory, next.day, text, weight) else changed
        }
        val message: String = when (eventKey to optionId) {
            "recruit" to "join" -> {
                val job = LifeJob("job${next.life.nextId}", "Feldzug mit der Kompanie", "Mit ${npc?.name ?: "der Kompanie"} gegen die Plünderer vor der Stadt.",
                    npc?.id ?: "", next.life.provinceId, "sword", 60, 60, 12, 2, next.day, accepted = true, combat = true, enemy = "Plünderer")
                next = next.copy(life = next.life.copy(jobs = next.life.jobs + job, nextId = next.life.nextId + 1,
                    career = if (next.life.career == CareerRank.COMMONER || next.life.career == CareerRank.RECRUIT) CareerRank.MERCENARY else next.life.career))
                if (npc != null) next = LifeEngine.updateNpc(next, bump(npc, 8, "joined", "du hast dich meiner Kompanie angeschlossen", 2))
                "Handschlag. Du bist jetzt Söldner der Kompanie. Der Auftrag steht in deiner Liste."
            }
            "song" to "sing" -> {
                present(next).forEach { p -> next = LifeEngine.updateNpc(next, bump(p, 2)) }
                "Du singst falsch, aber laut. Für einen Abend gehörst du dazu."
            }
            "song" to "coin" -> {
                if (!LifeEngine.canPay(next, 1)) "Deine Taschen sind leer."
                else {
                    next = LifeEngine.pay(next, 1)
                    if (npc != null) next = LifeEngine.updateNpc(next, bump(npc, 6, "coin", "du hast mir beim Spielen eine Münze geschenkt"))
                    "Die Münze landet klirrend im Lautenkasten. ${npc?.firstName} zwinkert dir zu."
                }
            }
            "quarrel" to "calm" -> {
                val ok = LifeEngine.socialSkill(next) + 20 >= (npc?.personality?.temper ?: 60)
                if (ok) {
                    next = LifeEngine.addRenown(next, 2)
                    if (npc != null) next = LifeEngine.updateNpc(next, bump(npc, 4, "calmed", "du hast mich vor einer Dummheit bewahrt"))
                    next.life.npcs.firstOrNull { it.role == NpcRole.INNKEEPER && it.homeProvinceId == next.life.provinceId }?.let { k ->
                        next = LifeEngine.updateNpc(next, bump(k, 6, "peacekeeper_${next.day}", "du hast in meiner Schänke Frieden gestiftet", 2))
                    }
                    "Ein paar ruhige Worte, eine Hand auf der Schulter – der Streit verraucht. Der Wirt nickt dir anerkennend zu."
                } else {
                    if (npc != null) next = LifeEngine.updateNpc(next, bump(npc, -4))
                    "„Halt dich da raus!“ Der Streit tobt weiter, bis die Wache kommt."
                }
            }
            "rivals" to "calm", "rivals" to "side_a", "rivals" to "side_b" -> {
                val (x, y) = rivalsPresent(next, present(next)) ?: return LifeEngine.Result(next, "Die beiden sind fort.")
                when (optionId) {
                    "calm" -> {
                        val ok = LifeEngine.socialSkill(next) + 15 >= (x.personality.temper + y.personality.temper) / 2 + 10
                        if (ok) {
                            next = LifeEngine.addRenown(next, 2)
                            next = LifeEngine.updateNpc(next, bump(next.life.npc(x.id)!!, 4, "parted_${y.id}", "du hast mich und ${y.firstName} auseinandergebracht"))
                            next = LifeEngine.updateNpc(next, bump(next.life.npc(y.id)!!, 4, "parted_${x.id}", "du hast mich und ${x.firstName} auseinandergebracht"))
                            next = next.copy(life = next.life.copy(bonds = next.life.bonds.mapNotNull { b ->
                                if (b.kind == BondKind.RIVALS && b.involves(x.id) && b.involves(y.id)) b.copy(strength = b.strength - 15).takeIf { it.strength > 0 } else b
                            }))
                            "Du stellst dich zwischen die beiden und redest, bis sie sich murrend abwenden. Ihre Feindschaft hat heute etwas von ihrer Schärfe verloren."
                        } else {
                            next = LifeEngine.updateNpc(next, bump(next.life.npc(x.id)!!, -3))
                            next = LifeEngine.updateNpc(next, bump(next.life.npc(y.id)!!, -3))
                            "„Misch dich nicht ein!“ – ausnahmsweise sind sich die beiden einig. Gegen dich."
                        }
                    }
                    else -> {
                        val (friend, foe) = if (optionId == "side_a") x to y else y to x
                        next = LifeEngine.updateNpc(next, bump(next.life.npc(friend.id)!!, 8, "sided_${foe.id}", "du hast mir gegen ${foe.firstName} beigestanden", 2))
                        next = LifeEngine.updateNpc(next, bump(next.life.npc(foe.id)!!, -10, "sided_against_${friend.id}", "du hast dich auf die Seite von ${friend.firstName} geschlagen", 2))
                        next = NpcSocialEngine.ripple(next, friend.id, 4)
                        "Du stellst dich neben ${friend.firstName}. ${foe.firstName} spuckt aus und geht – das wird man dir nicht vergessen."
                    }
                }
            }
            "quarrel" to "join" -> if (npc != null) brawl(next, npc.id).also { next = it.state }.text else "Niemand da."
            "story" to "listen" -> "Du hörst aufmerksam zu. ${LifeEngine.rumors(next).firstOrNull()?.text ?: ""}"
            else -> "Du lässt den Moment verstreichen."
        }
        return LifeEngine.Result(LifeEngine.log(next, "${event.title}: $message"), message)
    }
}
