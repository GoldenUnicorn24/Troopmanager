package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * Three acts give the campaign a red thread. Each act has concrete goals; when they are met the
 * act's boss appears and must be beaten in a live battle. Defeat allows a retry after a few days.
 */
object ActEngine {
    private const val RETRY_DAYS = 5

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)

    data class Goal(val text: String, val current: Int, val target: Int) {
        val done: Boolean get() = current >= target
    }

    fun current(state: GameState): ActCatalog.ActDef? = ActCatalog.act(state.crown.act.act)

    fun finished(state: GameState): Boolean = state.crown.act.act > ActCatalog.acts.size

    private val royal = setOf("König", "Hochkönig", "Großkönig", "Kaiser der Kronlande")

    fun goals(state: GameState): List<Goal> {
        val crown = state.crown
        return when (crown.act.act) {
            1 -> listOf(
                Goal("Provinzen der Grenzmark", crown.ownedProvinces.count { it.land == CrownLand.GRENZMARK }, 4),
                Goal("Soldaten", state.armySize, 1_500),
                Goal("Feldherrin an deiner Seite", if (state.companion.met) 1 else 0, 1),
            )
            2 -> listOf(
                Goal("Titel König", if (state.title in royal) 1 else 0, 1),
                Goal("Kronprovinzen", crown.ownedProvinces.size, 18),
                Goal("Soldaten", state.armySize, 12_000),
                Goal("Rivalen-Könige unterworfen oder verbündet", RivalEngine.subdued(state), 2),
            )
            3 -> listOf(
                Goal("Kronprovinzen", crown.ownedProvinces.size, 30),
                Goal("Provinzen im Hordenland", crown.ownedProvinces.count { it.land == CrownLand.ASCHESTEPPE }, 3),
                Goal("Soldaten", state.armySize, 40_000),
                Goal("Rivalen-Könige unterworfen oder verbündet", RivalEngine.subdued(state), 4),
            )
            else -> emptyList()
        }
    }

    fun goalsMet(state: GameState): Boolean = goals(state).let { it.isNotEmpty() && it.all { g -> g.done } }

    fun bossStrength(state: GameState, boss: ActCatalog.BossDef): Int {
        val difficulty = when (state.settings.difficulty) { Difficulty.STORY -> 0.75; Difficulty.VETERAN -> 1.3; else -> 1.0 }
        return (boss.strength * difficulty).toInt()
    }

    fun bossReady(state: GameState): Boolean =
        goalsMet(state) && !state.crown.act.bossBattleActive && state.day >= state.crown.act.bossRetryDay

    // ------------------------------------------------------------ daily

    fun tick(state: GameState): GameState {
        if (!state.life.storyActs) return state
        val act = current(state) ?: return state
        val a = state.crown.act
        if (a.bossAnnounced || a.bossBattleActive || !goalsMet(state) || state.crown.saga.pendingScene != null) return state
        val boss = act.boss
        val scene = SagaScene("boss_${act.number}", "boss", "${boss.name} – ${boss.title}", boss.location, boss.intro,
            listOf(
                SagaOption("fight", "Zur Schlacht! (${String.format(java.util.Locale.GERMANY, "%,d", bossStrength(state, boss))} Feinde)", "Live-Schlacht · Endkampf von Akt ${act.number}"),
                SagaOption("prepare", "Erst rüsten", "Der Endkampf wartet unter Reich → Kronlande"),
            ),
            speaker = boss.name, portraitKey = boss.portraitKey)
        var next = state.copy(crown = state.crown.copy(act = a.copy(bossAnnounced = true), saga = state.crown.saga.copy(pendingScene = scene)))
        next = CrownEngine.chronicle(next, "Der Endkampf naht", "${boss.name}, ${boss.title}, zieht gegen dein Reich. Akt ${act.number} entscheidet sich in einer Schlacht.")
        return next
    }

    fun choose(state: GameState, option: String): GameEngine.ActionResult =
        if (option == "fight") startBoss(state) else ok(state, "Rüste dein Heer. Der Endkampf wartet unter Reich → Kronlande.")

    fun startBoss(state: GameState): GameEngine.ActionResult {
        val act = current(state) ?: return ok(state, "Alle Akte sind abgeschlossen.")
        if (!goalsMet(state)) return ok(state, "Erfülle zuerst die Ziele von Akt ${act.number}.")
        if (state.battleSession != null) return ok(state, "Beende zuerst die laufende Schlacht.")
        if (state.day < state.crown.act.bossRetryDay) return ok(state, "Dein Heer sammelt sich noch. Neuer Versuch ab Tag ${state.crown.act.bossRetryDay}.")
        if (state.homeArmySize <= 0) return ok(state, "Keine Soldaten daheim.")
        val boss = act.boss
        val result = BattleEngine.start(
            state, boss.enemy, Tactic.HOLD,
            enemyStrength = bossStrength(state, boss), location = boss.location,
            enemyArmyName = "${boss.name}, ${boss.title}", enemyFactionName = CrownCatalog.hordeName(boss.enemy),
            enemyExperience = 5 + act.number * 10, enemyMorale = 85,
        )
        if (result.state.battleSession == null) return ok(state, result.message)
        val next = result.state.copy(crown = result.state.crown.copy(act = result.state.crown.act.copy(
            bossBattleActive = true, bossAttempts = state.crown.act.bossAttempts + 1)))
        return ok(next, "Die Schlacht gegen ${boss.name} beginnt!")
    }

    /** Applies a finished boss battle exactly once. */
    fun reconcile(state: GameState): GameState {
        val a = state.crown.act
        if (!a.bossBattleActive) return state
        val session = state.battleSession
        if (session?.isActive == true) return state
        val act = current(state) ?: return state.copy(crown = state.crown.copy(act = a.copy(bossBattleActive = false)))
        if (session == null) return state.copy(crown = state.crown.copy(act = a.copy(bossBattleActive = false, bossRetryDay = state.day + 1)))
        return if (session.status == BattleStatus.VICTORY) complete(state, act)
        else CrownEngine.chronicle(
            state.copy(crown = state.crown.copy(act = a.copy(bossBattleActive = false, bossRetryDay = state.day + RETRY_DAYS))),
            "${act.boss.name} siegt", "Dein Heer muss sich zurückziehen. In $RETRY_DAYS Tagen kannst du erneut gegen ${act.boss.name} antreten.",
        )
    }

    /** Developer helper: completes the current act as if the boss had been beaten. */
    fun forceComplete(state: GameState): GameState = current(state)?.let { complete(state, it) } ?: state

    private fun complete(state: GameState, act: ActCatalog.ActDef): GameState {
        var next = state.copy(
            resources = state.resources.copy(gold = (state.resources.gold.toLong() + act.rewardGold).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
            renown = state.renown + act.rewardRenown,
            crown = state.crown.copy(act = ActState(act = act.number + 1, startedDay = state.day)),
        )
        next = CelebrationEngine.add(next, CelebrationKind.BOSS, "${act.boss.name} ist gefallen",
            "Akt ${act.number} „${act.title}“ ist vollendet. +${act.rewardGold} Gold, +${act.rewardRenown} Ruhm.")
        val following = ActCatalog.act(act.number + 1)
        next = if (following != null) CelebrationEngine.add(next, CelebrationKind.ACT, "Akt ${following.number}: ${following.title}", following.summary)
            else CelebrationEngine.add(next, CelebrationKind.ACT, "Herrscher der Kronlande", "Alle drei Akte sind geschafft. Die Kronlande gehören dir – doch die Horden schlafen nie.")
        return CrownEngine.chronicle(next, "Akt ${act.number} vollendet", "${act.boss.name} ist besiegt. ${following?.let { "Akt ${it.number}: ${it.title} beginnt." } ?: "Dein Name wird in Liedern weiterleben."}")
    }
}
