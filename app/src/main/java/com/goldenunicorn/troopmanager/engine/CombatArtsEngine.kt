package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Personal combat arts and round-based duels.
 * Duels use attack / parry / feint like rock-paper-scissors; your arts decide how hard you hit,
 * how much you endure and how reliably you read your opponent's intent.
 */
object CombatArtsEngine {
    const val MAX_LEVEL = 10
    private const val MAX_ROUNDS = 12

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)
    private fun arts(state: GameState) = state.crown.arts

    fun xpNeeded(level: Int): Int = 60 + level * 40

    fun trainingCost(state: GameState, art: CombatArt): Int = 120 * (arts(state).level(art) + 1)

    fun train(state: GameState, art: CombatArt): GameEngine.ActionResult {
        val a = arts(state)
        if (a.lastTrainingDay == state.day) return ok(state, "Du hast heute bereits trainiert. Morgen geht es weiter.")
        val level = a.level(art)
        if (level >= MAX_LEVEL) return ok(state, "${art.label} ist gemeistert.")
        val cost = trainingCost(state, art)
        if (state.resources.gold < cost) return ok(state, "Lehrmeister und Ausrüstung kosten $cost Gold.")
        val teacher = if (state.companion.met) 8 else 0
        val gain = 34 + statFor(state.player, art) / 10 + teacher
        val result = addXp(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost)), art, gain)
        val next = result.copy(crown = result.crown.copy(arts = result.crown.arts.copy(lastTrainingDay = state.day)))
        val newLevel = arts(next).level(art)
        val text = if (newLevel > level) "${art.label} steigt auf Stufe $newLevel!" +
            (if (newLevel == 3) " Neue Technik: ${art.technique}." else "")
            else "${art.label}: +$gain Erfahrung."
        return ok(next, text)
    }

    private fun statFor(p: CharacterProfile, art: CombatArt): Int = when (art) {
        CombatArt.BLADE -> p.sword
        CombatArt.SHIELD -> (p.sword + p.tactics) / 2
        CombatArt.BOW -> p.bow
        CombatArt.RIDING -> p.riding
        CombatArt.WARCRY -> p.leadership
        CombatArt.STRATEGY -> p.tactics
    }

    fun addXp(state: GameState, art: CombatArt, amount: Int): GameState {
        var a = arts(state)
        var level = a.level(art)
        var xp = a.xp(art) + amount
        var player = state.player
        while (level < MAX_LEVEL && xp >= xpNeeded(level)) {
            xp -= xpNeeded(level)
            level++
            player = when (art) {
                CombatArt.BLADE -> player.copy(sword = (player.sword + 2).coerceAtMost(100))
                CombatArt.SHIELD -> player.copy(sword = (player.sword + 1).coerceAtMost(100), tactics = (player.tactics + 1).coerceAtMost(100))
                CombatArt.BOW -> player.copy(bow = (player.bow + 2).coerceAtMost(100))
                CombatArt.RIDING -> player.copy(riding = (player.riding + 2).coerceAtMost(100))
                CombatArt.WARCRY -> player.copy(leadership = (player.leadership + 2).coerceAtMost(100))
                CombatArt.STRATEGY -> player.copy(tactics = (player.tactics + 2).coerceAtMost(100))
            }
        }
        if (level >= MAX_LEVEL) xp = 0
        a = a.copy(levels = a.levels + (art to level), experience = a.experience + (art to xp))
        return state.copy(player = player, crown = state.crown.copy(arts = a))
    }

    // ------------------------------------------------------------ duel values

    fun attackValue(state: GameState): Int {
        val a = arts(state)
        return state.player.sword + a.level(CombatArt.BLADE) * 6 + a.level(CombatArt.RIDING) * 2 + a.level(CombatArt.BOW) * 2
    }

    fun defenseValue(state: GameState): Int {
        val a = arts(state)
        return (state.player.sword + state.player.tactics) / 2 + a.level(CombatArt.SHIELD) * 6 + a.level(CombatArt.RIDING) * 2
    }

    /** Chance in percent that the shown hint reveals the true intent. */
    fun readChance(state: GameState): Int =
        (45 + arts(state).level(CombatArt.STRATEGY) * 5 + state.player.tactics / 5).coerceIn(45, 95)

    fun techniqueUnlocked(state: GameState): Boolean = CombatArt.entries.any { arts(state).level(it) >= 3 }

    fun duelRating(state: GameState): Int = (attackValue(state) + defenseValue(state)) / 2

    // ------------------------------------------------------------ duels

    private fun intent(seed: Int, skill: Int): DuelStance {
        val r = Random(seed).nextInt(100)
        // Stronger opponents feint more often.
        val feint = (25 + skill / 10).coerceAtMost(40)
        return when {
            r < 40 -> DuelStance.ATTACK
            r < 100 - feint -> DuelStance.DEFEND
            else -> DuelStance.FEINT
        }
    }

    private fun hintText(stance: DuelStance, name: String): String = when (stance) {
        DuelStance.ATTACK -> "$name spannt die Schultern – ein schwerer Hieb kommt."
        DuelStance.DEFEND -> "$name zieht die Klinge eng an den Körper und wartet."
        DuelStance.FEINT -> "Blick und Klinge von $name zeigen in verschiedene Richtungen."
        DuelStance.TECHNIQUE -> "$name sammelt sich für etwas Besonderes."
    }

    private fun prepareRound(state: GameState, duel: DuelSession): DuelSession {
        val seed = state.day * 131 + duel.round * 17 + duel.context.hashCode() + duel.opponentHp
        val stance = intent(seed, duel.opponentSkill)
        val truthful = Random(seed * 3 + 7).nextInt(100) < readChance(state)
        val shown = if (truthful) stance else DuelStance.entries.filter { it != stance && it != DuelStance.TECHNIQUE }[Random(seed).nextInt(2)]
        return duel.copy(intendedStance = stance, intentHint = hintText(shown, duel.opponentName.substringBefore(' ')))
    }

    fun startDuel(state: GameState, context: String, name: String, title: String, skill: Int): GameState {
        val duel = prepareRound(state, DuelSession(context, name, title, skill.coerceIn(20, 200),
            log = listOf("$name ($title) tritt dir gegenüber. Wähle deine Haltung.")))
        return state.copy(crown = state.crown.copy(duel = duel))
    }

    fun move(state: GameState, stance: DuelStance): GameEngine.ActionResult {
        val duel = state.crown.duel ?: return ok(state, "Kein Duell aktiv.")
        if (duel.finished) return ok(state, "Das Duell ist entschieden.")
        if (stance == DuelStance.TECHNIQUE && (duel.focus < 3 || !techniqueUnlocked(state)))
            return ok(state, if (!techniqueUnlocked(state)) "Eine Technik erfordert eine Kampfkunst auf Stufe 3." else "Zu wenig Fokus (3 benötigt).")
        val atk = attackValue(state).toDouble()
        val def = defenseValue(state).toDouble()
        val skill = duel.opponentSkill.toDouble()
        fun playerHit(mult: Double) = (26.0 * mult * atk / (atk + skill)).roundToInt()
        fun enemyHit(mult: Double) = (26.0 * mult * skill / (skill + def)).roundToInt()
        val e = duel.intendedStance
        val foe = duel.opponentName.substringBefore(' ')
        var toEnemy = 0
        var toPlayer = 0
        val line: String
        when (stance) {
            DuelStance.ATTACK -> when (e) {
                DuelStance.ATTACK -> { toEnemy = playerHit(1.0); toPlayer = enemyHit(1.0); line = "Klinge trifft Klinge – ihr beide landet Treffer." }
                DuelStance.DEFEND -> { toEnemy = playerHit(0.35); toPlayer = enemyHit(1.15); line = "$foe pariert und kontert hart." }
                else -> { toEnemy = playerHit(1.5); line = "Du durchschaust die Finte und triffst voll." }
            }
            DuelStance.DEFEND -> when (e) {
                DuelStance.ATTACK -> { toEnemy = playerHit(1.2); toPlayer = enemyHit(0.35); line = "Perfekte Parade! Dein Konter sitzt." }
                DuelStance.DEFEND -> { line = "Ihr umkreist einander. Du sammelst Fokus." }
                else -> { toPlayer = enemyHit(1.5); line = "$foe täuscht dich und trifft." }
            }
            DuelStance.FEINT -> when (e) {
                DuelStance.ATTACK -> { toPlayer = enemyHit(1.5); line = "Deine Finte ist zu langsam – $foe trifft dich mitten im Ansatz." }
                DuelStance.DEFEND -> { toEnemy = playerHit(1.5); line = "Die Finte öffnet die Deckung. Treffer!" }
                else -> { toEnemy = playerHit(0.5); toPlayer = enemyHit(0.5); line = "Zwei Finten, zwei Streifschläge." }
            }
            DuelStance.TECHNIQUE -> {
                val best = CombatArt.entries.maxByOrNull { arts(state).level(it) } ?: CombatArt.BLADE
                if (e == DuelStance.DEFEND) { toEnemy = playerHit(1.0); toPlayer = enemyHit(0.5); line = "${best.technique}! $foe fängt einen Teil ab." }
                else { toEnemy = playerHit(2.2); line = "${best.technique}! Ein verheerender Treffer." }
            }
        }
        val focus = when (stance) {
            DuelStance.TECHNIQUE -> duel.focus - 3 + 1
            DuelStance.DEFEND -> if (e == DuelStance.DEFEND) duel.focus + 2 else duel.focus + 1
            else -> duel.focus + 1
        }.coerceIn(0, 5)
        val playerHp = (duel.playerHp - toPlayer).coerceAtLeast(0)
        val opponentHp = (duel.opponentHp - toEnemy).coerceAtLeast(0)
        val entry = "Runde ${duel.round}: ${stance.label} gegen ${e.label}. $line" +
            (if (toEnemy > 0) " (−$toEnemy für $foe)" else "") + (if (toPlayer > 0) " (−$toPlayer für dich)" else "")
        val finished = playerHp <= 0 || opponentHp <= 0 || duel.round >= MAX_ROUNDS
        val won = opponentHp <= 0 || (finished && playerHp > opponentHp)
        var nextDuel = duel.copy(playerHp = playerHp, opponentHp = opponentHp, focus = focus, round = duel.round + 1,
            log = (duel.log + entry).takeLast(14), finished = finished, won = won)
        if (finished) nextDuel = nextDuel.copy(log = (nextDuel.log + if (won) "Sieg! $foe ist geschlagen." else "Niederlage. $foe behält die Oberhand.").takeLast(14), intentHint = "")
        else nextDuel = prepareRound(state, nextDuel)
        val next = state.copy(crown = state.crown.copy(duel = nextDuel))
        return ok(next, entry)
    }

    /** Applies rewards for a finished duel and closes it. */
    fun finish(state: GameState): GameEngine.ActionResult {
        val duel = state.crown.duel ?: return ok(state, "Kein Duell aktiv.")
        if (!duel.finished) return ok(state, "Das Duell läuft noch.")
        var next = state.copy(crown = state.crown.copy(duel = null, arts = state.crown.arts.copy(
            duelsWon = state.crown.arts.duelsWon + if (duel.won) 1 else 0,
            duelsLost = state.crown.arts.duelsLost + if (duel.won) 0 else 1,
        )))
        next = addXp(next, CombatArt.BLADE, if (duel.won) 30 else 15)
        next = addXp(next, CombatArt.SHIELD, if (duel.won) 15 else 10)
        val context = duel.context
        val text: String
        when {
            context == "tournament" -> {
                val tier = next.crown.arts.tournamentWins
                if (duel.won) {
                    val gold = 600 + tier * 400
                    val renown = 25 + tier * 10
                    next = next.copy(
                        resources = next.resources.copy(gold = next.resources.gold + gold),
                        renown = next.renown + renown,
                        crown = next.crown.copy(arts = next.crown.arts.copy(tournamentWins = tier + 1)),
                    )
                    next = addXp(next, CombatArt.RIDING, 25)
                    next = addXp(next, CombatArt.WARCRY, 15)
                    text = "Turniersieg! +$gold Gold, +$renown Ruhm."
                    next = CrownEngine.chronicle(next, "Turniersieg", "${next.player.name} besiegt ${duel.opponentName} vor jubelnden Rängen.")
                } else {
                    next = next.copy(renown = next.renown + 3)
                    text = "Im Turnier unterlegen – aber das Volk hat deinen Mut gesehen."
                }
            }
            context.startsWith("campaign:") -> {
                val provinceId = context.removePrefix("campaign:")
                next = if (duel.won) next.copy(renown = next.renown + 10, crown = next.crown.copy(championBonusProvinceId = provinceId, championPenaltyProvinceId = null))
                    else next.copy(crown = next.crown.copy(championPenaltyProvinceId = provinceId, championBonusProvinceId = null))
                text = if (duel.won) "Der feindliche Champion fällt. Die Verteidiger verlieren den Mut (−20 % Stärke beim nächsten Feldzug)."
                    else "Dein Champion-Duell ist verloren. Die Verteidiger schöpfen Mut (+10 % Stärke)."
            }
            context.startsWith("rival:") -> {
                next = RivalEngine.duelFinished(next, context.removePrefix("rival:"), duel.won)
                text = if (duel.won) "Der Champion liegt im Staub. Respekt aus ganz ${ActCatalog.rival(context.removePrefix("rival:"))?.land?.label ?: "den Kronlanden"}." else "Der Champion behält die Oberhand."
            }
            context.startsWith("trial:") || context.startsWith("saga:") -> {
                val heroineId = context.substringAfter(':')
                next = HeroineSagaEngine.duelFinished(next, heroineId, duel.won)
                text = next.crown.saga.lastOutcome ?: if (duel.won) "Sieg!" else "Niederlage."
            }
            else -> text = if (duel.won) "Sieg!" else "Niederlage."
        }
        return ok(next, text)
    }

    fun tournamentCooldown(state: GameState): Int = (arts(state).lastTournamentDay + 3 - state.day).coerceAtLeast(0)

    fun tournamentOpponent(state: GameState): Pair<String, Int> {
        val tier = arts(state).tournamentWins
        val names = listOf("Ser Brandt der Junge", "Hauptmann Odo", "Ritterin Mira Falkenhorst", "Graf Vellmar", "Der Eisenhengst",
            "Klingenmeister Torvald", "Ser Aldric der Unbesiegte", "Die Rote Lanze", "Champion von Saltmere", "Großmeister Kaelen")
        return names[tier % names.size] + (if (tier >= names.size) " ${tier / names.size + 1}" else "") to (50 + tier * 5).coerceAtMost(135)
    }

    fun tournament(state: GameState): GameEngine.ActionResult {
        if (state.crown.duel != null) return ok(state, "Ein Duell läuft bereits.")
        val wait = tournamentCooldown(state)
        if (wait > 0) return ok(state, "Das nächste Turnier beginnt in $wait Tagen.")
        if (state.resources.gold < 200) return ok(state, "Das Startgeld beträgt 200 Gold.")
        val (name, skill) = tournamentOpponent(state)
        var next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - 200),
            crown = state.crown.copy(arts = state.crown.arts.copy(lastTournamentDay = state.day)),
        )
        next = startDuel(next, "tournament", name, "Turnierkämpfer", skill)
        return ok(next, "Die Fanfaren erklingen. Dein Gegner: $name.")
    }

    fun championDuel(state: GameState, provinceId: String): GameEngine.ActionResult {
        if (state.crown.duel != null) return ok(state, "Ein Duell läuft bereits.")
        val p = CrownEngine.province(state, provinceId) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner == ProvinceOwner.PLAYER) return ok(state, "Hier gibt es niemanden herauszufordern.")
        if (state.crown.championBonusProvinceId == provinceId) return ok(state, "Der Champion von ${p.name} ist bereits gefallen.")
        val skill = p.garrisonQuality * 4 + 20 + p.wealth * 2
        val name = when (p.owner) {
            ProvinceOwner.HORDE -> when (p.horde) {
                EnemyType.ORC -> "Ork-Häuptling von ${p.name}"
                EnemyType.URUK -> "Uruk-hai-Kriegsführer von ${p.name}"
                EnemyType.TAO_TEI -> "Tao-Tei-Alphatier von ${p.name}"
            }
            ProvinceOwner.BANDITS, ProvinceOwner.REBELS -> "Räuberhauptmann von ${p.name}"
            else -> "Champion von ${p.name}"
        }
        return ok(startDuel(state, "campaign:$provinceId", name, p.ownerLabel, skill), "$name nimmt deine Herausforderung an.")
    }
}
