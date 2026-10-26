package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*

/**
 * The third walkable place: the town barracks with a drill sergeant, training dummies, archery
 * butts, a sparring ring and a horse pen. Training raises the existing attributes slowly (with
 * diminishing returns and a daily limit), enlisting in the town guard gives a steady wage and a
 * path from recruit to soldier, and sellswords can be hired here as well.
 */
object BarracksEngine {
    enum class Station(val label: String, val skill: String) {
        DUMMIES("Übungspuppen", "sword"), BUTTS("Schießstand", "bow"), PEN("Pferdekoppel", "riding"),
        RING("Übungsring", "leadership"), TABLE("Kartentisch", "tactics"),
    }

    data class Layout(
        override val width: Int,
        override val height: Int,
        val stations: Map<Station, List<Cell>>,
        val fronts: Map<Station, Cell>,
        val sergeant: Cell,
        val racks: List<Cell>,
        val entry: Cell,
        val standing: List<Cell>,
    ) : TavernEngine.WalkGrid {
        val blocked: Set<Cell> = stations.filterKeys { it != Station.RING }.values.flatten().toSet() + racks + (0 until width).map { Cell(it, 0) } + sergeant
        override fun walkable(c: Cell): Boolean = c.x in 0 until width && c.y in 0 until height && c !in blocked
    }

    val layout = Layout(
        width = 12, height = 9,
        stations = mapOf(
            Station.DUMMIES to listOf(Cell(1, 3), Cell(3, 3)),
            Station.BUTTS to listOf(Cell(8, 1), Cell(10, 1)),
            Station.PEN to listOf(Cell(10, 6), Cell(11, 6), Cell(10, 7), Cell(11, 7)),
            Station.RING to listOf(Cell(5, 5), Cell(6, 5), Cell(5, 6), Cell(6, 6)),
            Station.TABLE to listOf(Cell(2, 6), Cell(3, 6)),
        ),
        fronts = mapOf(
            Station.DUMMIES to Cell(2, 4), Station.BUTTS to Cell(9, 4), Station.PEN to Cell(9, 6),
            Station.RING to Cell(6, 7), Station.TABLE to Cell(2, 7),
        ),
        sergeant = Cell(6, 2),
        racks = listOf(Cell(4, 1), Cell(5, 1)),
        entry = Cell(6, 8),
        standing = listOf(Cell(7, 3), Cell(4, 4), Cell(8, 5), Cell(3, 5), Cell(7, 6), Cell(8, 7), Cell(1, 5), Cell(10, 3)),
    )

    const val TRAINING_PRICE = 4
    const val SESSIONS_PER_DAY = 2
    const val GUARD_WAGE = 6

    fun open(state: GameState): Boolean = state.life.timeSlot != TimeSlot.NIGHT

    fun sergeant(state: GameState): NpcIdentity? = state.life.npc("${state.life.provinceId}:sergeant")

    fun ensureSergeants(state: GameState): GameState {
        val missing = Towns.all.mapNotNull { t ->
            val id = "${t.provinceId}:sergeant"
            if (state.life.npc(id) != null) null
            else NpcEngine.generate(id, NpcRole.SERGEANT, t.provinceId).let { it.copy(romanceable = false, attractedTo = emptySet(), age = it.age.coerceAtLeast(32)) }
        }
        return if (missing.isEmpty()) state else state.copy(life = state.life.copy(npcs = state.life.npcs + missing))
    }

    fun enter(state: GameState): LifeEngine.Result {
        if (LifeEngine.town(state) == null) return LifeEngine.Result(state, "Hier gibt es keine Kaserne.")
        val ready = ensureSergeants(LifeEngine.ensurePeople(state))
        val next = ready.copy(life = ready.life.copy(place = LifePlace.BARRACKS))
        return LifeEngine.Result(next, if (open(next)) "Holzschwerter klappern, ein Ausbilder brüllt Befehle." else "Die Kaserne schläft. Nur die Wache am Tor ist wach.")
    }

    fun leave(state: GameState): LifeEngine.Result = LifeEngine.Result(state.copy(life = state.life.copy(place = LifePlace.TOWN)), "Du verlässt die Kaserne.")

    data class Placement(val npc: NpcIdentity, val cell: Cell, val sergeant: Boolean)

    fun placements(state: GameState): List<Placement> {
        if (!open(state)) return emptyList()
        val result = mutableListOf<Placement>()
        sergeant(state)?.let { result += Placement(it, layout.sergeant, true) }
        val others = NpcEngine.presentAt(state, state.life.provinceId, LifePlace.BARRACKS).filter { it.id != sergeant(state)?.id }.sortedBy { it.id }.take(layout.standing.size)
        val free = layout.standing.toMutableList()
        others.forEach { npc ->
            if (free.isEmpty()) return@forEach
            val spot = free[Math.floorMod(npc.id.hashCode() + state.day * 5 + state.life.timeSlot.ordinal, free.size)]
            free.remove(spot)
            result += Placement(npc, spot, false)
        }
        return result
    }

    // ------------------------------------------------------------ training

    fun sessionsLeft(state: GameState): Int = SESSIONS_PER_DAY - if (state.life.trainingDay == state.day) state.life.trainingSessions else 0

    /** Gain shrinks with skill: quick progress for novices, hard-won for veterans. */
    fun gain(value: Int): Int = when { value < 50 -> 2; value < 80 -> 1; value < 95 -> if (value % 2 == 0) 1 else 0; else -> 0 }

    fun trainBlocker(state: GameState, station: Station): String? = when {
        !open(state) -> "Nachts wird nicht geübt."
        sessionsLeft(state) <= 0 -> "Genug für heute – deine Arme zittern."
        state.life.wound >= 40 -> "Mit diesen Wunden? Die Heilerin zuerst."
        state.life.intoxication >= 40 -> "Betrunken an die Waffen? Der Ausbilder schickt dich weg."
        state.life.timeSlot == TimeSlot.NIGHT || LifeEngine.slotsLeft(state) < 1 -> "Es ist zu spät dafür."
        !state.life.enlisted && !LifeEngine.canPay(state, LifeEngine.priced(state, TRAINING_PRICE)) -> "Der Ausbilder will $TRAINING_PRICE Gold."
        else -> null
    }

    fun train(state: GameState, station: Station): LifeEngine.Result {
        trainBlocker(state, station)?.let { return LifeEngine.Result(state, it) }
        val before = LifeEngine.skillValue(state, station.skill).let { if (station.skill == "diplomacy") state.player.diplomacy else it }
        val g = gain(before)
        var next = if (state.life.enlisted) state else LifeEngine.pay(state, LifeEngine.priced(state, TRAINING_PRICE))
        next = LifeEngine.passTime(next, 1).state
        next = next.copy(
            player = raise(next.player, station.skill, g),
            life = next.life.copy(
                trainingDay = next.day,
                trainingSessions = (if (state.life.trainingDay == state.day) state.life.trainingSessions else 0) + 1,
                fatigue = (next.life.fatigue + 15).coerceAtMost(100),
            ),
        )
        val label = LifeEngine.skillLabel(station.skill)
        val text = when {
            g == 0 -> "Schweiß, Schwielen – aber hier lernst du nichts mehr dazu. ($label bleibt ${before})"
            else -> "${station.label}: eine Stunde harter Drill. $label +$g."
        }
        return LifeEngine.Result(LifeEngine.log(next, text), text)
    }

    fun raise(p: CharacterProfile, skill: String, by: Int): CharacterProfile = if (by == 0) p else when (skill) {
        "sword" -> p.copy(sword = (p.sword + by).coerceAtMost(100))
        "bow" -> p.copy(bow = (p.bow + by).coerceAtMost(100))
        "riding" -> p.copy(riding = (p.riding + by).coerceAtMost(100))
        "leadership" -> p.copy(leadership = (p.leadership + by).coerceAtMost(100))
        "tactics" -> p.copy(tactics = (p.tactics + by).coerceAtMost(100))
        "diplomacy" -> p.copy(diplomacy = (p.diplomacy + by).coerceAtMost(100))
        else -> p
    }

    // ------------------------------------------------------------ town guard

    fun enlistBlocker(state: GameState): String? = when {
        state.life.landed -> "Als Herrscher dienst du niemandem."
        state.life.enlisted -> "Du stehst bereits im Dienst der Stadtwache."
        CrownEngine.province(state, state.life.provinceId)?.owner == ProvinceOwner.BANDITS -> "Hier herrschen Räuber. Eine Stadtwache gibt es nicht."
        else -> null
    }

    fun enlist(state: GameState): LifeEngine.Result {
        enlistBlocker(state)?.let { return LifeEngine.Result(state, it) }
        val career = if (state.life.career == CareerRank.COMMONER) CareerRank.RECRUIT else state.life.career
        var next = state.copy(life = state.life.copy(enlisted = true, career = career))
        sergeant(next)?.let { s -> next = LifeEngine.updateNpc(next, NpcEngine.remember(s, "enlisted", next.day, "du hast dich bei mir für die Stadtwache eingeschrieben", 2)) }
        next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, "In der Stadtwache", "${next.player.name} schwört der Stadtwache von ${LifeEngine.locationName(next)} Treue."))
        return LifeEngine.Result(LifeEngine.log(next, "In die Stadtwache eingetreten."),
            "Ein Schwur, ein Wappenrock, ein Speer. Training ist für dich jetzt umsonst; Wachdienst bringt $GUARD_WAGE Gold am Tag.")
    }

    fun guardBlocker(state: GameState): String? = when {
        !state.life.enlisted -> "Erst einschreiben."
        state.life.lastGuardDay == state.day -> "Deine Schicht heute ist vorbei."
        LifeEngine.slotsLeft(state) < 2 -> "Für eine Schicht ist es zu spät."
        state.life.wound >= 50 -> "Verwundet schickt dich der Ausbilder heim."
        else -> null
    }

    /** A shift on the walls. Sometimes thieves at the gate turn it into a fight. */
    fun guardDuty(state: GameState): LifeEngine.Result {
        guardBlocker(state)?.let { return LifeEngine.Result(state, it) }
        var next = LifeEngine.passTime(state, 2).state
        next = LifeEngine.addRenown(LifeEngine.earn(next, GUARD_WAGE), 1)
        next = next.copy(life = next.life.copy(lastGuardDay = next.day))
        val trouble = Math.floorMod(next.day * 131 + next.life.provinceId.hashCode(), 100) < 22
        if (trouble) {
            next = SkirmishEngine.start(next, SkirmishKind.CAMPAIGN, "Diebe am Tor", "Diebe", 3, 34 + next.day / 4, 8, 3)
            return LifeEngine.Result(LifeEngine.log(next, "Wachdienst: Diebe am Tor."), "Mitten in der Schicht: Diebe am Tor!")
        }
        return LifeEngine.Result(LifeEngine.log(next, "Wachdienst geleistet (+$GUARD_WAGE Gold)."), "Eine ruhige Schicht. +$GUARD_WAGE Gold, +1 Ruf.")
    }

    fun resign(state: GameState): LifeEngine.Result =
        LifeEngine.Result(state.copy(life = state.life.copy(enlisted = false)), "Du gibst Speer und Wappenrock zurück.")
}
