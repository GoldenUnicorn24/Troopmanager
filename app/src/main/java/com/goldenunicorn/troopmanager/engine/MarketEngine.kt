package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*

/**
 * The second walkable place: a market square with the smith's stall, the trader's stall and the
 * healer's booth around a well. Open from morning to afternoon. Equipment bought here changes
 * both the fighting value and the visible look of the character.
 */
object MarketEngine {
    enum class Stall(val label: String, val role: NpcRole) { SMITH("Schmiede", NpcRole.SMITH), TRADER("Krämerstand", NpcRole.MERCHANT), HEALER("Heilerin", NpcRole.HEALER) }

    data class Layout(
        override val width: Int,
        override val height: Int,
        val stalls: Map<Stall, List<Cell>>,
        val keeperSpots: Map<Stall, Cell>,
        val frontSpots: Map<Stall, Cell>,
        val well: List<Cell>,
        val crates: List<Cell>,
        val entry: Cell,
        val standing: List<Cell>,
    ) : TavernEngine.WalkGrid {
        val blocked: Set<Cell> = stalls.values.flatten().toSet() + well + crates + (0 until width).map { Cell(it, 0) } + keeperSpots.values
        override fun walkable(c: Cell): Boolean = c.x in 0 until width && c.y in 0 until height && c !in blocked
    }

    val layout = Layout(
        width = 12, height = 9,
        stalls = mapOf(
            Stall.SMITH to listOf(Cell(1, 2), Cell(2, 2), Cell(3, 2)),
            Stall.TRADER to listOf(Cell(8, 2), Cell(9, 2), Cell(10, 2)),
            Stall.HEALER to listOf(Cell(1, 6), Cell(2, 6)),
        ),
        keeperSpots = mapOf(Stall.SMITH to Cell(2, 1), Stall.TRADER to Cell(9, 1), Stall.HEALER to Cell(1, 5)),
        frontSpots = mapOf(Stall.SMITH to Cell(2, 3), Stall.TRADER to Cell(9, 3), Stall.HEALER to Cell(2, 7)),
        well = listOf(Cell(6, 4), Cell(6, 5)),
        crates = listOf(Cell(11, 6), Cell(11, 7), Cell(4, 1)),
        entry = Cell(6, 8),
        standing = listOf(Cell(5, 3), Cell(7, 3), Cell(4, 5), Cell(8, 5), Cell(5, 6), Cell(7, 6), Cell(9, 6), Cell(3, 4), Cell(10, 4), Cell(4, 7), Cell(8, 7)),
    )

    fun open(state: GameState): Boolean = state.life.timeSlot in setOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON) &&
        TownWeather.here(state) != WeatherKind.STORM

    /**
     * Every town has its own prices: armourers are cheap in the tournament town, goods in the free
     * port, herbs among the elves; the refugee camp at the edge of the horde lands pays for all.
     */
    fun townFactor(provinceId: String, stall: Stall): Double = when (provinceId) {
        "gm_rabenfurt" -> when (stall) { Stall.TRADER -> .9; else -> 1.0 }
        "gm_eschenhof" -> when (stall) { Stall.TRADER -> .9; Stall.SMITH -> 1.1; else -> 1.0 }
        "al_aldenburg" -> when (stall) { Stall.SMITH -> .85; Stall.TRADER -> 1.15; else -> 1.0 }
        "ks_saltmere" -> when (stall) { Stall.TRADER -> .8; Stall.SMITH -> 1.1; else -> 1.0 }
        "kw_jadehain" -> when (stall) { Stall.HEALER -> .8; Stall.SMITH -> .95; else -> .9 }
        "wh_mondquell" -> when (stall) { Stall.HEALER -> .75; Stall.SMITH -> 1.2; else -> 1.0 }
        "as_freistatt" -> 1.25
        else -> 1.0
    }

    fun price(state: GameState, stall: Stall, base: Int): Int =
        if (base == 0) 0 else LifeEngine.priced(state, (base * townFactor(state.life.provinceId, stall)).toInt().coerceAtLeast(1))

    /** The town with the best price for a stall, for trade tips. */
    fun cheapestTown(stall: Stall): String = Towns.all.minBy { townFactor(it.provinceId, stall) }.provinceId

    /** Smith and healer exist per town; created deterministically when first needed. */
    fun ensureStallkeepers(state: GameState): GameState {
        val missing = Towns.all.flatMap { t ->
            listOf(NpcRole.SMITH to "smith", NpcRole.HEALER to "healer").mapNotNull { (role, key) ->
                val id = "${t.provinceId}:$key"
                if (state.life.npc(id) != null) null
                else NpcEngine.generate(id, role, t.provinceId, if (role == NpcRole.HEALER) Gender.FEMALE else null)
            }
        }
        return if (missing.isEmpty()) state else state.copy(life = state.life.copy(npcs = state.life.npcs + missing))
    }

    fun keeper(state: GameState, stall: Stall): NpcIdentity? = when (stall) {
        Stall.SMITH -> state.life.npc("${state.life.provinceId}:smith")
        Stall.HEALER -> state.life.npc("${state.life.provinceId}:healer")
        Stall.TRADER -> state.life.npcs.firstOrNull { it.role == NpcRole.MERCHANT && it.homeProvinceId == state.life.provinceId && !it.departed }
    }

    data class Placement(val npc: NpcIdentity, val cell: Cell, val stall: Stall?)

    fun placements(state: GameState): List<Placement> {
        if (!open(state)) return emptyList()
        val result = mutableListOf<Placement>()
        Stall.entries.forEach { stall -> keeper(state, stall)?.let { result += Placement(it, layout.keeperSpots.getValue(stall), stall) } }
        val keepers = result.map { it.npc.id }.toSet()
        val visitors = NpcEngine.presentAt(state, state.life.provinceId, LifePlace.MARKET).filter { it.id !in keepers }.sortedBy { it.id }.take(7)
        val free = layout.standing.toMutableList()
        visitors.forEach { npc ->
            if (free.isEmpty()) return@forEach
            val spot = free[Math.floorMod(npc.id.hashCode() + state.day * 3 + state.life.timeSlot.ordinal, free.size)]
            free.remove(spot)
            result += Placement(npc, spot, null)
        }
        return result
    }

    fun enter(state: GameState): LifeEngine.Result {
        if (state.life.landed && LifeEngine.town(state) == null) return LifeEngine.Result(state, "Hier gibt es keinen Markt.")
        val ready = ensureStallkeepers(LifeEngine.ensurePeople(state))
        val next = ready.copy(life = ready.life.copy(place = LifePlace.MARKET))
        return LifeEngine.Result(next, if (open(next)) "Der Markt von ${LifeEngine.locationName(next)}: Rufe, Gerüche, Gedränge."
            else if (TownWeather.here(next) == WeatherKind.STORM) "Bei diesem Sturm haben die Händler ihre Stände abgebaut."
            else "Die Stände sind geschlossen. Nur der Brunnen plätschert.")
    }

    fun leave(state: GameState): LifeEngine.Result = LifeEngine.Result(state.copy(life = state.life.copy(place = LifePlace.TOWN)), "Du verlässt den Markt.")

    // ------------------------------------------------------------ shops

    fun buyWeapon(state: GameState, tier: Int): LifeEngine.Result {
        val item = GearCatalog.weapons.getOrNull(tier) ?: return LifeEngine.Result(state, "")
        if (tier <= state.life.gear.weaponTier) return LifeEngine.Result(state, "Du besitzt schon Besseres.")
        val price = price(state, Stall.SMITH, item.price)
        if (!LifeEngine.canPay(state, price)) return LifeEngine.Result(state, "${item.name} kostet $price Gold.")
        val next = LifeEngine.pay(state, price).let { it.copy(life = it.life.copy(gear = it.life.gear.copy(weaponTier = tier))) }
        return LifeEngine.Result(LifeEngine.log(next, "${item.name} gekauft."), "${item.name} liegt gut in der Hand. (+${item.bonus} im Kampf)")
    }

    fun buyArmor(state: GameState, tier: Int): LifeEngine.Result {
        val item = GearCatalog.armor.getOrNull(tier) ?: return LifeEngine.Result(state, "")
        if (tier <= state.life.gear.armorTier) return LifeEngine.Result(state, "Du besitzt schon Besseres.")
        val price = price(state, Stall.SMITH, item.price)
        if (!LifeEngine.canPay(state, price)) return LifeEngine.Result(state, "${item.name} kostet $price Gold.")
        var next = LifeEngine.pay(state, price)
        // The new armor is visible everywhere: editor, tavern figure, portraits.
        val look = (next.life.appearance ?: CharacterAppearance()).copy(armor = item.appearanceIndex)
        next = next.copy(life = next.life.copy(gear = next.life.gear.copy(armorTier = tier), appearance = look),
            crown = next.crown.copy(playerLook = look.toPortraitSpec()))
        return LifeEngine.Result(LifeEngine.log(next, "${item.name} gekauft."), "Die ${item.name} sitzt. (Schutz +${item.bonus})")
    }

    fun buyGift(state: GameState, kind: GiftKind): LifeEngine.Result {
        val price = price(state, Stall.TRADER, kind.price)
        if (!LifeEngine.canPay(state, price)) return LifeEngine.Result(state, "${kind.label} kostet $price Gold.")
        val next = LifeEngine.pay(state, price).let { it.copy(life = it.life.copy(gifts = it.life.gifts + (kind to ((it.life.gifts[kind] ?: 0) + 1)))) }
        return LifeEngine.Result(next, "${kind.label} eingepackt.")
    }

    fun healPrice(state: GameState) = price(state, Stall.HEALER, 6)

    fun heal(state: GameState): LifeEngine.Result {
        if (state.life.wound <= 0 && state.life.party.none { it.wounded > 0 }) return LifeEngine.Result(state, "„Dir fehlt nichts. Geh.“")
        val price = healPrice(state)
        if (!LifeEngine.canPay(state, price)) return LifeEngine.Result(state, "Die Behandlung kostet $price Gold.")
        val next = LifeEngine.pay(state, price).let { s ->
            s.copy(life = s.life.copy(wound = (s.life.wound - 30).coerceAtLeast(0), party = s.life.party.map { it.copy(wounded = (it.wounded - 30).coerceAtLeast(0)) }))
        }
        return LifeEngine.Result(LifeEngine.log(next, "Wunden versorgt."), "Kräuter, Nadel, ein bitterer Trank. Es wird besser.")
    }

    /** How much a gift means to someone: matching taste beats price. */
    fun giftValue(npc: NpcIdentity, kind: GiftKind): Int {
        val taste = (npc.likes + npc.interests).any { liked -> kind.keywords.any { it.equals(liked, ignoreCase = true) } }
        return 3 + kind.price / 4 + if (taste) 12 else 0
    }

    fun give(state: GameState, npcId: String, kind: GiftKind): DialogueEngine.Reply {
        val npc = state.life.npc(npcId) ?: return DialogueEngine.Reply(state, "", DialogueEngine.Outcome.UNAVAILABLE)
        if ((state.life.gifts[kind] ?: 0) <= 0) return DialogueEngine.Reply(state, "", DialogueEngine.Outcome.UNAVAILABLE, summary = "Du hast kein ${kind.label}.")
        if (npc.remembers("gift_${state.day}")) return DialogueEngine.Reply(state, "„Noch eins? Heute nicht, wirklich.“", DialogueEngine.Outcome.FAILURE)
        val value = giftValue(npc, kind)
        val taste = value >= 15
        val r = npc.relation
        val romantic = npc.romanceable && state.life.gender in npc.attractedTo && !r.romanceBoundary
        var updated = npc.copy(relation = r.copy(
            affinity = (r.affinity + value).coerceAtMost(100),
            trust = (r.trust + value / 4).coerceAtMost(100),
            attraction = if (romantic) (r.attraction + if (taste) 5 else 1).coerceAtMost(100) else r.attraction,
        ))
        updated = NpcEngine.remember(updated, "gift_${state.day}", state.day, "du hast mir ${kind.label} geschenkt", if (taste) 3 else 1)
        updated = updated.copy(relation = updated.relation.copy(stage = if (updated.relation.stage.romantic) updated.relation.stage else NpcEngine.stageFor(updated.relation)))
        var next = state.copy(life = state.life.copy(gifts = state.life.gifts + (kind to ((state.life.gifts[kind] ?: 1) - 1))))
        next = LifeEngine.updateNpc(next, updated)
        next = NpcSocialEngine.ripple(next, npc.id, value)
        val line = if (taste) "${npc.firstName} strahlt. „${kind.label}? Woher wusstest du…? Danke, wirklich.“"
            else "„Oh. Danke.“ ${npc.firstName} lächelt höflich."
        return DialogueEngine.Reply(LifeEngine.log(next, "${kind.label} an ${npc.name} verschenkt."), line, DialogueEngine.Outcome.SUCCESS,
            playerLine = "Du reichst ${npc.firstName} ${kind.label}.", summary = "Sympathie +$value")
    }
}
