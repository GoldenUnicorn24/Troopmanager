package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/** An outpost transfers existing food and uses an existing, physically present world army. */
object OutpostEngine {
    data class Interaction(val state: GameState, val army: WorldArmy)

    fun garrison(state: GameState, post: FrontierOutpost): WorldArmy? = state.world.armies.firstOrNull {
        it.id == post.garrisonArmyId && it.factionId == PLAYER_FACTION && it.missionId == null &&
            it.status == WorldArmyStatus.HOLDING && it.regionId == post.regionId && it.total > 0
    }

    fun assignGarrison(state: GameState, postId: Long, armyId: String): GameEngine.ActionResult {
        val post = state.frontier.outposts.firstOrNull { it.id == postId }
            ?: return GameEngine.ActionResult(state, "Außenposten fehlt.")
        val army = state.world.armies.firstOrNull { it.id == armyId && it.factionId == PLAYER_FACTION &&
            it.missionId == null && it.regionId == post.regionId && it.status == WorldArmyStatus.HOLDING && it.total > 0 }
            ?: return GameEngine.ActionResult(state, "Die Garnison muss zuerst zum Posten marschieren und dort halten.")
        if (state.battleSession?.isActive == true || post.level < 2 || post.integrity == 0)
            return GameEngine.ActionResult(state, "Garnisonen benötigen eine gesicherte Wacht ab Stufe 2.")
        val next = state.copy(frontier = state.frontier.copy(outposts = state.frontier.outposts.map {
            when { it.id == postId -> it.copy(garrisonArmyId = army.id)
                it.garrisonArmyId == army.id -> it.copy(garrisonArmyId = null)
                else -> it }
        }))
        return GameEngine.ActionResult(next, "${army.total} reale Soldaten halten ${post.name}. Rückruf erfolgt über den Rückmarsch des Heeres.")
    }

    fun repair(state: GameState, id: Long): GameEngine.ActionResult {
        val post = state.frontier.outposts.firstOrNull { it.id == id }
            ?: return GameEngine.ActionResult(state, "Außenposten fehlt.")
        val damage = 100 - post.integrity
        if (state.battleSession?.isActive == true || damage == 0 || state.resources.gold < damage * 2 || state.resources.stone < damage)
            return GameEngine.ActionResult(state, "Reparatur benötigt ${damage * 2} Gold und $damage Stein.")
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - damage * 2,
            stone = state.resources.stone - damage), frontier = state.frontier.copy(outposts = state.frontier.outposts.map {
                if (it.id == id) it.copy(integrity = 100) else it })), "${post.name} repariert; verlorene Vorräte werden nicht ersetzt.")
    }

    fun interact(state: GameState, original: WorldArmy): Interaction {
        val post = state.frontier.outposts.firstOrNull { it.regionId == original.regionId && it.integrity > 0 }
            ?: return Interaction(state, original)
        val suppliedAlly = original.factionId != PLAYER_FACTION &&
            DiplomacyEngine.hasTreaty(state, PLAYER_FACTION, original.factionId, TreatyKind.DEFENSIVE_ALLIANCE)
        if (original.factionId == PLAYER_FACTION || suppliedAlly) {
            val wanted = (original.dailyFood.toLong() * (post.level + 1) - original.supplyFood).coerceAtLeast(0)
            val taken = minOf(wanted, post.stores.toLong()).toInt()
            val resting = original.status == WorldArmyStatus.HOLDING && post.level >= 2 && original.supplyFood + taken >= original.dailyFood && original.lastOutpostRestDay < state.day
            val army = original.copy(supplyFood = (original.supplyFood.toLong() + taken).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                lastSupplyOutpostId = if (taken > 0) post.id else original.lastSupplyOutpostId,
                morale = (original.morale + if (resting) 1 else 0).coerceAtMost(100),
                lastOutpostRestDay = if (resting) state.day else original.lastOutpostRestDay)
            val next = if (taken == 0) state else replace(state, post.copy(stores = post.stores - taken,
                suppliedFood = (post.suppliedFood.toLong() + taken).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            return Interaction(next, army)
        }
        val hostile = DiplomacyEngine.atWar(state, PLAYER_FACTION, original.factionId) ||
            state.frontier.hordes.any { it.kind.hostileToAll && it.worldArmyId == original.id }
        if (!hostile || state.day - (post.raidDays[original.id] ?: -100) < 14) return Interaction(state, original)
        val guard = garrison(state, post)
        val guardPower = guard?.units?.sumOf { it.amount.toDouble() * (it.type.attack + it.type.defense) } ?: 0.0
        val enemyPower = original.units.sumOf { it.amount.toDouble() * (it.type.attack + it.type.defense) }.coerceAtLeast(1.0)
        val fed = guard != null && guard.supplyFood.toLong() + post.stores >= guard.dailyFood
        val ratio = guardPower * (1 + post.level * .65) * (.4 + post.integrity / 165.0) * (if (fed) 1.0 else .55) / enemyPower
        val held = guard != null && ratio >= .60
        val days = if (held) post.level + 1 else if (guard != null) 1 else 0
        val rng = Random(original.id.hashCode() xor post.id.toInt() xor state.day)
        fun casualties(army: WorldArmy, rate: Double): List<UnitAllocation> {
            val budget = BattleResolutionEngine.stochasticRound(army.total * rate, rng)
            val counts = BattleResolutionEngine.allocate(budget, army.units.map { it.amount }, army.units.map { it.amount.toDouble() })
            return army.units.mapIndexed { index, unit -> unit.copy(amount = counts[index]) }
        }
        fun remaining(army: WorldArmy, losses: List<UnitAllocation>) = army.copy(units = army.units.mapNotNull { unit ->
            val count = unit.amount - losses.first { it.type == unit.type }.amount
            if (count == 0) null else unit.copy(amount = count)
        })
        val enemyLosses = casualties(original, if (held) .04 else if (guard != null) .01 else 0.0)
        val enemy = remaining(original, enemyLosses).copy(delayUntilDay = maxOf(original.delayUntilDay, state.day + days),
            arrivalDay = original.arrivalDay?.plus(days), morale = (original.morale - if (held) 6 else 0).coerceAtLeast(0))
        var next = state
        if (guard != null) {
            val ownLosses = casualties(guard, if (held) .025 else .12)
            val survivors = remaining(guard, ownLosses)
            next = next.copy(world = next.world.copy(armies = next.world.armies.map {
                if (it.id == guard.id) survivors.copy(status = if (survivors.total == 0) WorldArmyStatus.DESTROYED else it.status) else it }))
            next = ArmyEngine.applyLosses(next, ownLosses)
        }
        val killed = enemyLosses.sumOf { it.amount }
        next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == enemy.factionId)
            it.copy(population = (it.population - killed).coerceAtLeast(0)) else it }))
        val used = if (guard == null) 0 else minOf(post.stores.toLong(), guard.dailyFood.toLong() * days.coerceAtLeast(1)).toInt()
        val hp = (post.integrity - if (held) 12 else 55).coerceAtLeast(0)
        val text = if (held) "Tag ${state.day}: ${guard!!.name} hält ${original.name} $days Tage auf; $used Nahrung verbraucht."
            else "Tag ${state.day}: ${original.name} überrennt die Wacht; Zustand $hp %, Vorräte gehen verloren."
        next = replace(next, post.copy(integrity = hp, stores = if (hp == 0) 0 else if (held) post.stores - used else (post.stores - used) / 2,
            raidDays = (post.raidDays + (original.id to state.day)).entries.sortedByDescending { it.value }.take(40).associate { it.key to it.value }, lastDefense = text))
        next = next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, if (held) "Grenzwacht hält" else "Außenposten angegriffen", text)).takeLast(2000))
        enemy.enemyCommanderId?.let { id -> next = next.copy(world = next.world.copy(enemyCommanders = next.world.enemyCommanders.map {
            if (it.id == id) it.copy(memories = (it.memories + "${post.name}: ${if (held) "aufgehalten" else "verwüstet"} an Tag ${state.day}").takeLast(20)) else it })) }
        return Interaction(next, enemy)
    }

    private fun replace(state: GameState, post: FrontierOutpost) = state.copy(frontier = state.frontier.copy(
        outposts = state.frontier.outposts.map { if (it.id == post.id) post else it }))
}
