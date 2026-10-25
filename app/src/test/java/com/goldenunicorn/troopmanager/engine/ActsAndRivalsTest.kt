package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class ActsAndRivalsTest {
    private fun game() = GameEngine.newGame("Leon", 25, Species.HALF_ELF, null)

    @Test
    fun enemiesAreNamedOrksUrukHaiAndTaoTei() {
        assertEquals("Ork-Raubzug", EnemyType.ORC.label)
        assertEquals("Uruk-hai-Kriegsheer", EnemyType.URUK.label)
        assertEquals("Tao-Tei-Schwarm", EnemyType.TAO_TEI.label)
        val hordes = game().crown.provinces.filter { it.owner == ProvinceOwner.HORDE }.map { it.ownerLabel }.toSet()
        assertEquals(setOf("Orks", "Uruk-hai", "Tao Tei"), hordes)
        assertTrue(game().crown.provinces.none { "Asche" in it.ownerLabel || "Aschethron" in it.name })
    }

    @Test
    fun actOneBossAppearsWhenGoalsAreMetAndVictoryStartsActTwo() {
        var state = HeroineSagaEngine.recruitImmediately(game(), "alina")
        listOf("gm_eschenhof", "gm_rabenfurt", "gm_kraehenstein", "gm_moorwacht").forEach { state = CrownEngine.acquire(state, it, "Kauf") }
        state = state.copy(crown = state.crown.copy(celebrations = emptyList(), saga = state.crown.saga.copy(pendingScene = null)))
        state = CrownEngine.addSoldiers(state, UnitType.HUMAN_SWORD, (1_500 - state.armySize).coerceAtLeast(0), 30, 80)
        assertTrue(ActEngine.goalsMet(state))
        state = ActEngine.tick(state)
        assertEquals("boss_1", state.crown.saga.pendingScene?.key)
        state = HeroineSagaEngine.choose(state, "fight").state
        assertNotNull(state.battleSession)
        assertTrue(state.crown.act.bossBattleActive)
        assertEquals(EnemyType.ORC, state.battleSession!!.enemy)
        assertEquals(state, ActEngine.reconcile(state))
        val gold = state.resources.gold
        val won = ActEngine.reconcile(state.copy(battleSession = state.battleSession!!.copy(status = BattleStatus.VICTORY)))
        assertEquals(2, won.crown.act.act)
        assertTrue(won.resources.gold >= gold + 5000)
        assertTrue(won.crown.celebrations.any { it.kind == CelebrationKind.BOSS })
        assertEquals(won, SaveCodec.decode(SaveCodec.encode(won)))
        val lost = ActEngine.reconcile(state.copy(battleSession = state.battleSession!!.copy(status = BattleStatus.DEFEAT)))
        assertEquals(1, lost.crown.act.act)
        assertFalse(lost.crown.act.bossBattleActive)
        assertFalse(ActEngine.bossReady(lost))
    }

    @Test
    fun rivalKingsActAndTakingTheirCapitalDefeatsThem() {
        var state = game().copy(day = 20)
        state = CrownEngine.acquire(state, "gm_eschenhof", "Kauf")
        state = state.copy(crown = state.crown.copy(saga = state.crown.saga.copy(pendingScene = null), celebrations = emptyList()))
        state = RivalEngine.tick(state)
        val scene = state.crown.saga.pendingScene!!
        assertTrue(scene.key.startsWith("rival_"))
        assertNotNull(scene.speaker)
        state = HeroineSagaEngine.choose(state, scene.options.first().id).state
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
        // Conquering Aldenburg defeats King Gerold and frees Aldmark.
        state = CrownEngine.acquire(state, "al_aldenburg", "Eroberung")
        assertEquals(RivalStatus.DEFEATED, RivalEngine.rival(state, "gerold")!!.status)
        assertTrue(state.crown.provinces.filter { it.land == CrownLand.ALDMARK }.none { it.owner == ProvinceOwner.RIVAL })
        assertTrue(state.crown.celebrations.any { it.kind == CelebrationKind.KING })
    }

    @Test
    fun alliedKingsNeverLaunchCounterattacks() {
        var state = CrownEngine.acquire(game().copy(day = 30), "gm_eschenhof", "Kauf")
        state = CrownEngine.acquire(state, "al_weizenau", "Kauf")
        state = state.copy(crown = state.crown.copy(rivals = state.crown.rivals.map { it.copy(status = RivalStatus.ALLIED) }))
        // Only rival sources exist next to Weizenau in Aldmark; with everyone allied, only horde/bandit sources remain.
        state = CrownWarEngine.tick(state)
        state.crown.threats.forEach { t -> assertNotEquals(ProvinceOwner.RIVAL, t.attacker) }
    }

    @Test
    fun marchEventsAskForADecisionAndThenResolve() {
        var state = game().copy(day = 20)
        state = state.copy(crown = state.crown.copy(campaignVictories = 5))
        state = CrownEngine.addSoldiers(state, UnitType.KNIGHT, 3000, 50, 90)
        var started: GameEngine.ActionResult? = null
        for (day in 20..80) {
            val trial = state.copy(day = day)
            val r = CrownEngine.conquer(trial, "gm_kraehenstein", 100)
            if (r.state.crown.pendingMarch != null) { started = r; break }
        }
        assertNotNull("Mindestens ein Marsch muss ein Ereignis auslösen", started)
        state = started!!.state
        val scene = state.crown.saga.pendingScene!!
        assertTrue(scene.key.startsWith("march_"))
        assertEquals(ProvinceOwner.BANDITS, CrownEngine.province(state, "gm_kraehenstein")!!.owner)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
        state = HeroineSagaEngine.choose(state, scene.options.last().id).state
        assertNull(state.crown.pendingMarch)
        assertEquals(ProvinceOwner.PLAYER, CrownEngine.province(state, "gm_kraehenstein")!!.owner)
    }

    @Test
    fun companionFightsWithHerAbilityAndCanBeWounded() {
        var state = HeroineSagaEngine.recruitImmediately(game(), "liang")
        val ability = CrownEngine.companionAbility(state)!!
        assertEquals("Kriegslist", ability.name)
        val without = CrownEngine.preview(game(), "gm_kraehenstein", 100)!!
        val with = CrownEngine.preview(state, "gm_kraehenstein", 100)!!
        assertTrue(with.ownPower > without.ownPower)
        state = state.copy(crown = state.crown.copy(saga = state.crown.saga.copy(companionWoundedUntil = state.day + 5)))
        assertNull(CrownEngine.companionAbility(state))
        assertEquals(5, CrownEngine.companionWoundedDays(state))
        assertTrue(AdvisorEngine.advice(state).any { it.title.contains("verwundet") })
    }

    @Test
    fun titleRisesAndMilestonesBecomeCelebrations() {
        var state = CrownEngine.acquire(game(), "gm_eschenhof", "Kauf")
        assertTrue(state.crown.celebrations.any { it.kind == CelebrationKind.MILESTONE })
        assertTrue(state.crown.celebrations.any { it.kind == CelebrationKind.TITLE })
        val count = state.crown.celebrations.size
        state = CelebrationEngine.dismiss(state)
        assertEquals(count - 1, state.crown.celebrations.size)
    }

    @Test
    fun longCampaignSaveStaysSmall() {
        var state = game()
        val big = (1..900).map { ChronicleEntry(1, "Eintrag $it", "Ein langer Chroniktext, der viele Bytes belegt und sich wiederholt.") }
        state = GameEngine.advanceDay(state.copy(chronicle = big)).state
        assertTrue(state.chronicle.size <= CHRONICLE_LIMIT)
    }

    @Test
    fun daySummaryNamesConcreteChanges() {
        val state = game()
        val next = GameEngine.advanceDay(state)
        assertTrue(next.message.contains("Gold") || next.message.contains("entscheid") || next.message.contains(":"))
        assertFalse(next.message.contains("wichtige Meldungen"))
    }
}
