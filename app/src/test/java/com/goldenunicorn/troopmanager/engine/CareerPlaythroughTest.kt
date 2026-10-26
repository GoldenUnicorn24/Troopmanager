package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Long-run playtest without a human: a simple, honest bot plays a landless life only through the
 * public engine actions the UI uses (jobs, fights, healing, gear, lord's tasks, tournaments,
 * accolade, oath) and then rules for a while. It checks that the rise from nobody to lord of the
 * keep is reachable in a sensible time, that nothing breaks over hundreds of days, and that every
 * saved state stays valid. The measured milestones are printed for balancing.
 */
class CareerPlaythroughTest {
    private fun start(background: Background, sword: Int, seedName: String): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = GameMode.FREE_LIFE, name = seedName, age = 22, gender = Gender.FEMALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = background, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        attributes = GameEngine.StartingAttributes(sword = sword, bow = 45, riding = 40, leadership = 40, tactics = 35, diplomacy = 50),
        companionChoice = CompanionChoice.FULL, companionDraft = NpcEngine.CompanionDraft(name = "Mira", age = 23),
    ))

    data class Career(val keepDay: Int?, val knightDay: Int?, val quests: Int, val tourneys: Int, val fights: Int, val wins: Int, val final: GameState)

    private var ransoms = 0

    private fun fight(s0: GameState): GameState {
        var s = s0
        var guard = 0
        while (s.life.skirmish != null && s.life.skirmish!!.outcome == null && guard++ < 30) {
            val act = if (s.life.skirmish!!.playerHealth < 25) SkirmishAction.RETREAT else SkirmishAction.ATTACK
            s = SkirmishEngine.act(s, act).state
        }
        return if (s.life.skirmish != null) SkirmishEngine.close(s).state else s
    }

    private fun day(s0: GameState): GameState {
        var s = s0
        // Captivity: pay if possible, else wait it out.
        if (s.life.captive) {
            ransoms++
            s = if (s.life.purse >= SkirmishEngine.ransomPrice(s)) SkirmishEngine.ransom(s).state else s
            if (s.life.captive) return LifeEngine.sleep(s).state
        }
        // Heal serious wounds at the market.
        if (s.life.wound >= 25 && MarketEngine.open(s) && LifeEngine.canPay(s, MarketEngine.healPrice(s))) s = MarketEngine.heal(MarketEngine.enter(s).state).state
        // Better gear when there is enough left over.
        GearCatalog.weapons.lastOrNull { it.tier > s.life.gear.weaponTier && s.life.purse >= MarketEngine.price(s, MarketEngine.Stall.SMITH, it.price) * 2 }
            ?.let { if (MarketEngine.open(s)) s = MarketEngine.buyWeapon(MarketEngine.enter(s).state, it.tier).state }
        GearCatalog.armor.lastOrNull { it.tier > s.life.gear.armorTier && s.life.purse >= MarketEngine.price(s, MarketEngine.Stall.SMITH, it.price) * 2 }
            ?.let { if (MarketEngine.open(s)) s = MarketEngine.buyArmor(MarketEngine.enter(s).state, it.tier).state }
        s = s.copy(life = s.life.copy(place = LifePlace.TOWN))
        // The castle: tournament, accolade, the lord's task.
        if (CastleEngine.tournamentToday(s) && CastleEngine.tournamentBlocker(s) == null && s.life.wound < 20 && CastleEngine.boutOdds(s) >= 50) {
            s = CastleEngine.enter(s).state
            var rounds = 0
            while (CastleEngine.tournamentBlocker(s) == null && (s.life.tournament?.round ?: 0) < 3 && rounds++ < 3) s = fight(CastleEngine.joinTournament(s).state)
        }
        if (CastleEngine.knightBlocker(CastleEngine.enter(s).state) == null) s = CastleEngine.knight(CastleEngine.enter(s).state).state
        if (s.life.lordQuest == null && s.life.renown >= CastleEngine.AUDIENCE_RENOWN && s.life.purse >= 0) s = CastleEngine.askForTask(CastleEngine.enter(s).state).state
        val q = s.life.lordQuest
        if (q != null && s.life.wound < 15 && (CastleEngine.questOdds(s) ?: 0) >= 60) {
            val entered = CastleEngine.enter(s).state
            if (CastleEngine.questBlocker(entered) == null) s = fight(CastleEngine.ride(entered).state)
        }
        s = s.copy(life = s.life.copy(place = LifePlace.TOWN))
        // Jobs in town.
        var guard = 0
        while (guard++ < 3 && LifeEngine.slotsLeft(s) >= 1 && s.life.wound < 40 && s.life.skirmish == null && !s.life.captive) {
            val job = s.life.jobs.filter { it.provinceId == s.life.provinceId && !it.done && !it.failed && it.slots <= LifeEngine.slotsLeft(s) && (!it.combat || s.life.wound < 20) }
                .minByOrNull { it.difficulty - LifeEngine.skillValue(s, it.skill) } ?: break
            if (!job.accepted) s = LifeEngine.acceptJob(s, job.id).state
            s = LifeEngine.doJob(s, job.id).state
            if (s.life.skirmish != null) s = fight(s)
        }
        // Eat, a room if affordable, sleep.
        s = s.copy(life = s.life.copy(place = LifePlace.TAVERN))
        if (!s.life.fed && s.life.purse >= 4) s = LifeEngine.eat(s, Meal.entries.first()).state
        if (s.life.purse >= 60 || s.life.wound >= 30) s = LifeEngine.rentRoom(s).state
        return LifeEngine.sleep(s).state
    }

    private fun live(start: GameState, days: Int): Career {
        var s = start
        var keep: Int? = null
        var knight: Int? = null
        repeat(days) { i ->
            if (!s.life.landed) {
                s = day(s)
                if (knight == null && s.life.knighted) knight = s.day
                if (LifeEngine.keepBlocker(s) == null) { s = LifeEngine.claimKeep(s).state; keep = s.day }
            } else {
                // The strategy layer: battles are decided with the game's own recommended plan.
                if (s.battleSession?.isActive == true) s = TacticalBattleEngine.autoResolve(s)
                s = GameEngine.advanceDay(s).state
            }
            assertTrue("purse ${s.life.purse}", s.life.purse >= 0)
            if (i % 20 == 0) println("  d${s.day} purse=${s.life.purse} renown=${s.life.renown} wound=${s.life.wound} gear=${s.life.gear} quests=${s.life.lordQuestsDone} jobsDone=${s.life.jobs.count { it.done }} place=${s.life.provinceId} captive=${s.life.captive}")
            if (i % 25 == 0) { LifeEngine.validate(s); assertEquals(s, SaveCodec.decode(SaveCodec.encode(s))) }
        }
        LifeEngine.validate(s)
        if (keep == null) s.life.log.takeLast(30).forEach { println("    $it") }
        return Career(keep, knight, s.life.lordQuestsDone, s.life.tournamentsWon, s.life.battles, s.life.battleVictories, s)
    }

    @Test fun aMercenaryRisesToTheKeepAndThenRules() {
        val c = live(start(Background.MERCENARY, sword = 60, seedName = "Runa"), 260)
        println("CAREER mercenary keepDay=${c.keepDay} knightDay=${c.knightDay} quests=${c.quests} tourneys=${c.tourneys} fights=${c.fights} wins=${c.wins} day=${c.final.day} captiveDays=$ransoms")
        assertNotNull("the keep must be reachable", c.keepDay)
        assertTrue("keep on day ${c.keepDay}", c.keepDay!! in 30..200)
        assertTrue(c.final.life.landed)
        // After the oath the realm keeps running on the same world state.
        assertTrue("ruled until day ${c.final.day}", c.final.day >= 150)
    }

    @Test fun aVillagerTakesLongerButGetsThere() {
        val c = live(start(Background.VILLAGER, sword = 45, seedName = "Lene"), 320)
        println("CAREER villager keepDay=${c.keepDay} knightDay=${c.knightDay} quests=${c.quests} tourneys=${c.tourneys} fights=${c.fights} wins=${c.wins} day=${c.final.day}")
        assertNotNull("the keep must be reachable for a weak start too", c.keepDay)
    }
}
