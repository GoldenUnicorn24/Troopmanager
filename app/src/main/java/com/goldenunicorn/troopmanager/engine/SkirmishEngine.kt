package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * Personal fights: a campaign skirmish with the company, an ambush on the road, wolves at the
 * fold or a duel in the tavern. Short (≤ 8 rounds), saved between rounds and deterministic per
 * fight and round. The player's own sword, bow, leadership and riding, the bought gear, wounds,
 * drink and fatigue, and the followers at their side decide — and everything has consequences:
 * loot and renown, wounds that last days, captivity, and memories of fighting side by side.
 */
object SkirmishEngine {
    const val MAX_ROUNDS = 8

    fun start(
        state: GameState,
        kind: SkirmishKind,
        title: String,
        enemyName: String,
        enemyCount: Int,
        enemyPower: Int,
        reward: Int,
        renown: Int,
        jobId: String? = null,
        opponentNpcId: String? = null,
        travelTo: String? = null,
    ): GameState {
        val allies = if (kind == SkirmishKind.DUEL) emptyList() else state.life.party.map { it.npcId }
        val fight = Skirmish(
            id = "fight${state.life.nextId}", kind = kind, title = title, provinceId = state.life.provinceId,
            enemyName = enemyName, enemyCount = enemyCount, enemyPower = enemyPower.coerceIn(10, 160),
            allyIds = allies, reward = reward, renown = renown, jobId = jobId, opponentNpcId = opponentNpcId, travelTo = travelTo,
            log = listOf(opening(kind, enemyName, enemyCount, allies.size)),
        )
        return state.copy(life = state.life.copy(skirmish = fight, nextId = state.life.nextId + 1))
    }

    /**
     * Honest odds before a fight: the same rules played through 16 times with straightforward
     * tactics (attack, fall back when badly hurt). Shown to the player as a judgement of the
     * situation, so nobody rides blindly into a hopeless assault.
     */
    fun odds(state: GameState, kind: SkirmishKind, enemyCount: Int, enemyPower: Int): Int {
        var wins = 0
        val runs = 16
        for (i in 0 until runs) {
            var s = start(state.copy(life = state.life.copy(skirmish = null, nextId = 100_000L + i * 7919L)), kind, "", "", enemyCount, enemyPower, 0, 0)
            var guard = 0
            while (s.life.skirmish?.outcome == null && guard++ < 14) {
                val f = s.life.skirmish ?: break
                s = act(s, if (f.playerHealth < 25) SkirmishAction.RETREAT else SkirmishAction.ATTACK).state
            }
            if (s.life.skirmish?.outcome == SkirmishOutcome.VICTORY) wins++
        }
        return wins * 100 / runs
    }

    fun oddsLabel(percent: Int): String = when {
        percent >= 80 -> "sehr gut"
        percent >= 60 -> "gut"
        percent >= 40 -> "offen"
        percent >= 20 -> "schlecht"
        else -> "aussichtslos"
    }

    private fun opening(kind: SkirmishKind, enemy: String, count: Int, allies: Int): String {
        val with = if (allies > 0) " $allies Gefährte${if (allies == 1) "" else "n"} an deiner Seite." else " Du bist allein."
        return when (kind) {
            SkirmishKind.AMBUSH -> "Aus dem Unterholz brechen $count $enemy hervor!$with"
            SkirmishKind.CAMPAIGN -> "Die Linien stehen. Vor euch: $count $enemy.$with"
            SkirmishKind.DUEL -> "Ein Kreis aus Neugierigen bildet sich. $enemy zieht die Klinge."
            SkirmishKind.BEASTS -> "Im Zwielicht glühen Augen: $enemy.$with"
        }
    }

    // ------------------------------------------------------------ strengths

    fun weaponBonus(state: GameState) = GearCatalog.weapons[state.life.gear.weaponTier.coerceIn(0, GearCatalog.weapons.lastIndex)].bonus
    fun armorBonus(state: GameState) = GearCatalog.armor[state.life.gear.armorTier.coerceIn(0, GearCatalog.armor.lastIndex)].bonus

    private fun condition(state: GameState): Int =
        state.life.wound / 2 + state.life.intoxication / 3 + state.life.fatigue / 5 + if (state.life.hangover) 5 else 0

    fun allies(state: GameState, fight: Skirmish): List<Pair<NpcIdentity, PartyMember?>> =
        fight.allyIds.mapNotNull { id -> state.life.npc(id)?.let { it to state.life.party.firstOrNull { m -> m.npcId == id } } }

    fun alliesPower(state: GameState, fight: Skirmish): Int =
        allies(state, fight).sumOf { (npc, m) -> PartyEngine.power(npc, m) } * (100 + fight.rallied * 15) / 100 * 30 / 100

    /** Rough odds for the UI: own side vs. enemy, before the dice. */
    fun estimate(state: GameState, fight: Skirmish): Int {
        val me = state.player.sword + weaponBonus(state) + armorBonus(state) / 2 - condition(state) + alliesPower(state, fight)
        return (me * 100 / (fight.enemyPower * fight.enemyCount.coerceAtMost(6) / 2 + fight.enemyPower).coerceAtLeast(1)).coerceIn(5, 300)
    }

    // ------------------------------------------------------------ rounds

    fun act(state: GameState, action: SkirmishAction): LifeEngine.Result {
        val fight = state.life.skirmish ?: return LifeEngine.Result(state, "Kein Kampf.")
        if (fight.outcome != null) return LifeEngine.Result(state, "Der Kampf ist vorbei.")
        val r = Random(fight.id.hashCode() * 31L + fight.round * 7919L + action.ordinal)
        val p = state.player
        val cond = condition(state)
        val allyPower = alliesPower(state, fight)
        val enemy = fight.enemyPower * (50 + fight.enemyHealth / 2) / 100 * (60 + fight.enemyMorale * 4 / 10) / 100 +
            (fight.enemyCount - 1).coerceIn(0, 5) * fight.enemyPower / 12
        val early = fight.round < 2
        val armor = armorBonus(state)
        var enemyHealth = fight.enemyHealth
        var enemyMorale = fight.enemyMorale
        var playerHealth = fight.playerHealth
        var rallied = fight.rallied
        val line: String
        val variance = r.nextInt(-18, 19)
        fun hitEnemy(strength: Int, factor: Int): Int = ((15 + (strength + variance - enemy) / 2).coerceIn(4, 45) * factor / 100)
        fun hitMe(factor: Int): Int {
            val raw = (7 + (enemy - p.sword / 2 - armor + r.nextInt(-8, 9)) / 4).coerceIn(2, 28) * factor / 100
            // Followers draw part of the attacks onto themselves.
            return (raw * 100 / (100 + fight.allyIds.size * 25)).coerceAtLeast(1)
        }
        when (action) {
            SkirmishAction.ATTACK -> {
                val dmg = hitEnemy(p.sword + weaponBonus(state) * 3 / 2 - cond + allyPower, 100)
                val taken = hitMe(100)
                enemyHealth -= dmg; enemyMorale -= dmg * 7 / 10; playerHealth -= taken
                line = "Du greifst an und triffst hart (−$dmg). ${hurtLine(taken)}"
            }
            SkirmishAction.DEFEND -> {
                val dmg = hitEnemy(p.sword / 2 + armor - cond + allyPower, 45)
                val taken = hitMe(40)
                enemyHealth -= dmg; enemyMorale -= dmg / 2 + 3; playerHealth -= taken
                line = "Du hältst stand und lässt sie anrennen (−$dmg). ${hurtLine(taken)}"
            }
            SkirmishAction.RANGED -> {
                // Rain, storm and fog spoil the aim (the same factors the great battles use).
                val weather = ((1.0 - TownWeather.here(state).rangedFactor) * 40).toInt()
                val dmg = hitEnemy(p.bow + weaponBonus(state) / 2 + (if (early) 18 else -15) - cond - weather + allyPower / 2, 100)
                val taken = hitMe(if (early) 45 else 110)
                enemyHealth -= dmg; enemyMorale -= dmg * 6 / 10; playerHealth -= taken
                line = (if (early) "Pfeil um Pfeil schlägt in ihre Reihen (−$dmg). " else "Zu nah für den Bogen – du triffst trotzdem (−$dmg). ") + hurtLine(taken)
            }
            SkirmishAction.RALLY -> {
                val shock = p.leadership / 4 + fight.allyIds.size * 5 + variance / 2
                val taken = hitMe(85)
                rallied = (rallied + 1).coerceAtMost(3)
                enemyMorale -= shock.coerceAtLeast(3); enemyHealth -= 3; playerHealth -= taken
                line = if (fight.allyIds.isEmpty()) "Dein Kriegsschrei lässt sie zögern (Moral −$shock). ${hurtLine(taken)}"
                    else "„Zu mir!“ Deine Leute rücken zusammen und drängen vor (Moral −$shock). ${hurtLine(taken)}"
            }
            SkirmishAction.RETREAT -> {
                val chance = p.riding + fight.allyIds.size * 5 + variance - cond
                if (chance >= enemy * 7 / 10 || fight.kind == SkirmishKind.DUEL) {
                    return finish(state, fight.copy(round = fight.round + 1, outcome = SkirmishOutcome.RETREAT,
                        log = fight.log + "Du ziehst dich zurück. Niemand folgt dir weit."))
                }
                val taken = hitMe(120)
                playerHealth -= taken
                line = "Der Rückzug misslingt – sie setzen nach. ${hurtLine(taken)}"
            }
        }
        var next = fight.copy(
            round = fight.round + 1, enemyHealth = enemyHealth.coerceAtLeast(0), enemyMorale = enemyMorale.coerceAtLeast(0),
            playerHealth = playerHealth.coerceAtLeast(0), rallied = rallied, log = (fight.log + line).takeLast(14),
        )
        next = when {
            next.enemyHealth <= 0 -> next.copy(outcome = SkirmishOutcome.VICTORY, log = next.log + "${fight.enemyName} liegen geschlagen.")
            next.enemyMorale <= 0 -> next.copy(outcome = SkirmishOutcome.VICTORY, log = next.log + "${fight.enemyName} brechen und fliehen!")
            next.playerHealth <= 0 -> {
                val captured = fight.kind in setOf(SkirmishKind.AMBUSH, SkirmishKind.CAMPAIGN) && r.nextInt(100) < 55
                next.copy(outcome = if (captured) SkirmishOutcome.CAPTURED else SkirmishOutcome.DEFEAT,
                    log = next.log + if (captured) "Ein Schlag, Dunkelheit. Du erwachst in Fesseln." else "Du gehst zu Boden.")
            }
            next.round >= MAX_ROUNDS -> if (next.enemyHealth < next.playerHealth)
                next.copy(outcome = SkirmishOutcome.VICTORY, log = next.log + "${fight.enemyName} geben auf und ziehen ab.")
                else next.copy(outcome = SkirmishOutcome.RETREAT, log = next.log + "Beide Seiten weichen erschöpft zurück.")
            else -> next
        }
        return if (next.outcome != null) finish(state, next) else LifeEngine.Result(state.copy(life = state.life.copy(skirmish = next)), line)
    }

    private fun hurtLine(taken: Int) = when {
        taken >= 22 -> "Ein schwerer Treffer (−$taken)!"
        taken >= 10 -> "Du steckst einen Hieb ein (−$taken)."
        else -> "Kaum ein Kratzer (−$taken)."
    }

    // ------------------------------------------------------------ consequences

    private fun finish(state: GameState, fight: Skirmish): LifeEngine.Result {
        val outcome = fight.outcome!!
        val damage = 100 - fight.playerHealth
        var next = state.copy(life = state.life.copy(skirmish = fight, battles = state.life.battles + 1,
            battleVictories = state.life.battleVictories + if (outcome == SkirmishOutcome.VICTORY) 1 else 0))
        val woundAdd = when (outcome) {
            SkirmishOutcome.VICTORY -> damage / 3
            SkirmishOutcome.RETREAT -> damage / 3 + 3
            SkirmishOutcome.DEFEAT, SkirmishOutcome.CAPTURED -> (damage / 2 + 10).coerceAtLeast(30)
        }
        next = next.copy(life = next.life.copy(wound = (next.life.wound + woundAdd).coerceAtMost(95), fatigue = (next.life.fatigue + 20).coerceAtMost(100)))
        var message = when (outcome) {
            SkirmishOutcome.VICTORY -> "Sieg! +${fight.reward} Gold, +${fight.renown} Ruf."
            SkirmishOutcome.RETREAT -> "Rückzug. Du lebst – mehr nicht."
            SkirmishOutcome.DEFEAT -> "Niederlage. Wunden, die Tage brauchen werden."
            SkirmishOutcome.CAPTURED -> "Gefangen von ${fight.enemyName}."
        }
        if (outcome == SkirmishOutcome.VICTORY) next = LifeEngine.addRenown(LifeEngine.earn(next, fight.reward), fight.renown)
        if (outcome == SkirmishOutcome.DEFEAT && fight.kind != SkirmishKind.DUEL) next = LifeEngine.addRenown(next, -1)
        // Jobs.
        fight.jobId?.let { jobId ->
            next = next.copy(life = next.life.copy(jobs = next.life.jobs.map {
                if (it.id == jobId) it.copy(done = outcome == SkirmishOutcome.VICTORY, failed = outcome != SkirmishOutcome.VICTORY) else it
            }))
            next.life.jobs.firstOrNull { it.id == jobId }?.let { job ->
                next.life.npc(job.giverId)?.let { giver ->
                    val good = outcome == SkirmishOutcome.VICTORY
                    next = LifeEngine.updateNpc(next, NpcEngine.remember(giver.copy(relation = giver.relation.copy(
                        affinity = (giver.relation.affinity + if (good) 10 else -5).coerceIn(-100, 100),
                        trust = (giver.relation.trust + if (good) 8 else 0).coerceAtMost(100))),
                        "job_${job.id}", next.day, if (good) "du hast „${job.title}“ mit der Klinge erledigt" else "du bist bei „${job.title}“ gescheitert", if (good) 2 else -1))
                }
            }
        }
        // Followers remember the fight; some take wounds.
        val r = Random(fight.id.hashCode().toLong())
        allies(next, fight).forEach { (npc, member) ->
            val rel = npc.relation
            val bond = if (outcome == SkirmishOutcome.VICTORY) 8 else 4
            next = LifeEngine.updateNpc(next, NpcEngine.remember(npc.copy(relation = rel.copy(
                trust = (rel.trust + bond).coerceAtMost(100), affinity = (rel.affinity + bond / 2).coerceAtMost(100),
                attraction = if (npc.romanceable && next.life.gender in npc.attractedTo && outcome == SkirmishOutcome.VICTORY) (rel.attraction + 3).coerceAtMost(100) else rel.attraction,
            )), "fight_${fight.id}", next.day, "wir haben Seite an Seite gegen ${fight.enemyName} gekämpft", 3))
            if (member != null && r.nextInt(100) < (if (outcome == SkirmishOutcome.VICTORY) 25 else 55)) {
                val w = r.nextInt(15, 45)
                next = next.copy(life = next.life.copy(party = next.life.party.map { if (it.npcId == npc.id) it.copy(wounded = (it.wounded + w).coerceAtMost(90)) else it }))
                message += " ${npc.firstName} ist verwundet."
            }
        }
        // Duel opponent.
        fight.opponentNpcId?.let { id ->
            next.life.npc(id)?.let { opp ->
                val won = outcome == SkirmishOutcome.VICTORY
                val respect = if (opp.personality.boldness >= 50) 8 else 3
                next = LifeEngine.updateNpc(next, NpcEngine.remember(opp.copy(relation = opp.relation.copy(
                    affinity = (opp.relation.affinity + if (won) respect else respect / 2).coerceAtMost(100),
                    rivalry = (opp.relation.rivalry + if (won) 6 else 0).coerceAtMost(100))),
                    "duel_${fight.id}", next.day, if (won) "du hast mich im Duell besiegt" else "ich habe dich im Duell besiegt", 2))
            }
        }
        if (outcome == SkirmishOutcome.CAPTURED) {
            val lost = next.life.purse / 2
            next = next.copy(life = next.life.copy(purse = next.life.purse - lost, captiveUntil = next.day + 2, captor = fight.enemyName,
                travel = null, place = LifePlace.AWAY))
            message += " Sie nehmen dir $lost Gold ab."
            next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, "In Gefangenschaft", "${next.player.name} fällt ${fight.enemyName} in die Hände."))
        }
        if (outcome == SkirmishOutcome.VICTORY && fight.kind != SkirmishKind.DUEL && fight.renown >= 5)
            next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, fight.title, "Gegen ${fight.enemyCount} ${fight.enemyName} siegreich."))
        next = PartyEngine.promote(next)
        next = LifeEngine.log(next, "${fight.title}: ${outcome.label}.")
        return LifeEngine.Result(next, message)
    }

    /** Closes the result screen; an interrupted journey continues. */
    fun close(state: GameState): LifeEngine.Result {
        val fight = state.life.skirmish ?: return LifeEngine.Result(state, "")
        if (fight.outcome == null) return LifeEngine.Result(state, "Der Kampf läuft noch.")
        var next = state.copy(life = state.life.copy(skirmish = null))
        next = CastleEngine.afterBout(CastleEngine.afterFight(next, fight), fight)
        if (fight.travelTo != null && fight.outcome != SkirmishOutcome.CAPTURED) return LifeEngine.completeTravel(next, fight.travelTo)
        if (next.life.place == LifePlace.AWAY && next.life.captiveUntil == null) next = next.copy(life = next.life.copy(place = LifePlace.TOWN))
        return LifeEngine.Result(next, "")
    }

    // ------------------------------------------------------------ captivity

    fun escape(state: GameState): LifeEngine.Result {
        if (state.life.captiveUntil == null) return LifeEngine.Result(state, "")
        val roll = Random(state.day * 53L + state.life.nextId).nextInt(-15, 16)
        val score = LifeEngine.dexterity(state) + state.player.riding / 3 + roll
        return if (score >= 70) {
            LifeEngine.Result(free(state), "Ein loser Pflock, eine dunkle Nacht – du bist frei.")
        } else LifeEngine.Result(state.copy(life = state.life.copy(wound = (state.life.wound + 10).coerceAtMost(95),
            captiveUntil = state.life.captiveUntil!! + 1, nextId = state.life.nextId + 1)), "Sie erwischen dich. Ein weiterer Tag in Ketten – und Prügel.")
    }

    fun ransomPrice(state: GameState) = (30 + state.life.renown / 10).coerceAtMost(80)

    fun ransom(state: GameState): LifeEngine.Result {
        val price = ransomPrice(state)
        if (state.life.purse < price) return LifeEngine.Result(state, "Dein letztes Gold haben sie schon. ($price nötig)")
        return LifeEngine.Result(free(state.copy(life = state.life.copy(purse = state.life.purse - price))), "Du kaufst dich für $price Gold frei.")
    }

    fun free(state: GameState): GameState =
        LifeEngine.log(state.copy(life = state.life.copy(captiveUntil = null, captor = null, place = LifePlace.TOWN)), "Aus der Gefangenschaft entkommen.")

    // ------------------------------------------------------------ duels

    fun duelStrength(npc: NpcIdentity): Int =
        30 + npc.personality.boldness / 3 + npc.appearance.muscle / 4 + when (npc.role) {
            NpcRole.MERCENARY_CAPTAIN -> 22; NpcRole.SELLSWORD -> 14; NpcRole.COMPANION -> 16; NpcRole.TRAVELER -> 6; else -> 0
        }

    fun duel(state: GameState, npcId: String): LifeEngine.Result {
        val npc = state.life.npc(npcId) ?: return LifeEngine.Result(state, "")
        if (state.life.wound >= 50) return LifeEngine.Result(state, "Mit diesen Wunden? Erst heilen.")
        val next = start(state, SkirmishKind.DUEL, "Duell gegen ${npc.firstName}", npc.name, 1, duelStrength(npc), 0, 3, opponentNpcId = npcId)
        return LifeEngine.Result(next, "${npc.firstName} grinst. „Bis zum ersten Blut.“")
    }
}
