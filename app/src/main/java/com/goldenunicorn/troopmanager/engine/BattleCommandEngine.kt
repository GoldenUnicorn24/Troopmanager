package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/** Local command timing and coordination; damage stays in BattleResolutionEngine. */
object BattleCommandEngine {
    data class Influence(val id: Long?, val name: String, val leadership: Int, val tactics: Int) {
        val range: Int get() = 60 + leadership * 2
        val reactionSteps: Int get() = if (tactics >= 55) 1 else 2
        val initiative: Int get() = (leadership + tactics) / 20
    }

    fun influence(state: GameState, battle: BattleSession, section: BattleSection): Influence {
        val wounded = battle.contingents.filter { it.commanderWounded }.mapNotNull { it.commanderId }.toSet()
        val commander = battle.contingents.filter { it.section == section && it.soldiers > 0 && !it.routed &&
            it.commanderId !in wounded && it.commanderId != null }.groupBy { it.commanderId }
            .maxByOrNull { (_, units) -> units.sumOf { it.soldiers.toLong() } }?.key
            ?.let { id -> state.commanders.firstOrNull { it.id == id } }
        return if (commander == null) Influence(null, state.player.name, state.player.leadership, state.player.tactics)
            else Influence(commander.id, commander.name, commander.leadership, commander.tactics)
    }

    fun reserveDelay(state: GameState, battle: BattleSession, section: BattleSection): Int {
        val leader = influence(state, battle, section)
        val difficult = battle.terrain[section] in listOf(BattleTerrain.FOREST, BattleTerrain.MUD, BattleTerrain.RIVER)
        return 1 + (if (difficult && leader.tactics < 65) 1 else 0) +
            (if (leader.id != null && leader.range < 120) 1 else 0) +
            (if (battle.plan.commanderRisk == CommanderRisk.CAUTIOUS) 1 else 0)
    }

    fun priority(state: GameState, battle: BattleSession, section: BattleSection): TargetPriority {
        val desired = battle.plan.priority(section)
        return if (makesMistake(state, battle, section)) TargetPriority.NEAREST else desired
    }

    fun makesMistake(state: GameState, battle: BattleSession, section: BattleSection): Boolean {
        val leader = influence(state, battle, section)
        if (leader.id == null) return false
        val risk = when (battle.plan.commanderRisk) { CommanderRisk.CAUTIOUS -> .5; CommanderRisk.BOLD -> 1.5; else -> 1.0 }
        val chance = (.18 - minOf(leader.tactics, 100) / 600.0) * risk
        return Random(battle.seed xor ((battle.step + 1) * 104729) xor section.ordinal xor leader.id.toInt()).nextDouble() < chance
    }

    fun prepare(state: GameState, battle: BattleSession): BattleSession {
        val events = mutableSetOf<String>()
        val wounded = battle.contingents.filter { it.commanderWounded }.mapNotNull { it.commanderId }.toSet()
        val troops = battle.contingents.map { original ->
            val c = if (original.commanderId in wounded) original.copy(commanderWounded = true) else original
            if (c.morale < 15 || c.cohesion < 15) {
                if (c.soldiers > 0 && !c.routed) events += "${c.displayName ?: c.type.label} verlassen ${c.section.label}: Moral ${c.morale}, Kohäsion ${c.cohesion}; keine normalen Angriffe mehr."
                return@map c.copy(routed = c.soldiers > 0)
            }
            if (c.soldiers == 0 || c.routed) return@map c
            val leader = influence(state, battle, c.section)
            var changed = c
            if (c.pendingFormation != null && c.formationReadyStep != null && battle.step >= c.formationReadyStep) {
                changed = changed.copy(formation = c.pendingFormation, pendingFormation = null, formationReadyStep = null)
                events += "${leader.name}: ${c.section.label} wechselt nach ${leader.reactionSteps} Reaktionsschritten in ${c.pendingFormation.label}."
            }
            if (c.commanderId == leader.id && leader.id != null && !c.commanderWounded && c.morale < 80 && leader.leadership >= 25) {
                val recovery = minOf(80 - c.morale, leader.leadership / 25)
                changed = changed.copy(morale = c.morale + recovery)
                events += "${leader.name} stabilisiert ${c.section.label}: +$recovery Moral durch Führung ${leader.leadership}."
            }
            if (battle.step == 0 && leader.id != null) events +=
                "${leader.name} führt ${c.section.label}: Reaktion ${leader.reactionSteps} Schritte, Befehlsreichweite ${leader.range} m, Initiative ${leader.initiative}; Reservekoordination ${reserveDelay(state, battle, c.section)} Schritte."
            if (makesMistake(state, battle, c.section)) events +=
                "${leader.name}: taktischer Fehler am ${c.section.label} (${battle.plan.commanderRisk.label}, Taktik ${leader.tactics}); nächstes erreichbares Ziel statt Schwerpunkt."
            changed
        }
        return battle.copy(contingents = troops, log = (battle.log + events.map { BattleLogEntry(battle.minute + 5, it) }).takeLast(60),
            commanderEvents = (battle.commanderEvents + events).takeLast(60))
    }

    fun retreatFactor(state: GameState, battle: BattleSession, section: BattleSection): Double {
        val leader = influence(state, battle, section)
        return 1.0 - minOf(leader.leadership, 100) / 200.0
    }
}
