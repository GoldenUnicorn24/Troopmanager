package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Milestone 4: the castle courtyard, the lord's tasks on the shared crown map, the accolade, tournaments. */
class RebirthCastleTest {
    private fun start(sword: Int = 80): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = GameMode.FREE_LIFE, name = "Leon", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = Background.MERCENARY, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        attributes = GameEngine.StartingAttributes(sword = sword, bow = 40, riding = 40, leadership = 40, tactics = 30, diplomacy = 60),
        companionChoice = CompanionChoice.NONE,
    ))

    private fun rt(s: GameState) = SaveCodec.decode(SaveCodec.encode(s))

    private fun at(s: GameState, slot: TimeSlot, day: Int = s.day) = s.copy(day = day, life = s.life.copy(timeSlot = slot))

    /** Resolves the open fight with a fixed outcome (rewards are SkirmishEngine's business, tested elsewhere). */
    private fun settle(s: GameState, outcome: SkirmishOutcome): GameState =
        SkirmishEngine.close(s.copy(life = s.life.copy(skirmish = s.life.skirmish!!.copy(outcome = outcome)))).state

    @Test fun everyTownHasAnAdultLordAndAWalkableCourtyard() {
        val s = CastleEngine.enter(at(start(), TimeSlot.NOON)).state
        assertEquals(LifePlace.CASTLE, s.life.place)
        Towns.all.forEach { t ->
            val lord = s.life.npc(CastleEngine.lordId(t.provinceId))!!
            assertEquals(NpcRole.LORD, lord.role)
            assertTrue(lord.age >= 34)
            assertFalse(lord.romanceable)
        }
        val l = CastleEngine.layout
        assertTrue(TavernEngine.path(l.entry, l.audience, l = l).isNotEmpty())
        assertTrue(TavernEngine.path(l.entry, l.listsFront, l = l).isNotEmpty())
        l.standing.forEach { assertTrue("$it", TavernEngine.path(l.entry, it, l = l).isNotEmpty()) }
        assertTrue(CastleEngine.placements(s).any { it.lord })
        assertTrue(CastleEngine.placements(at(s, TimeSlot.NIGHT)).isEmpty())
        assertEquals(s, rt(s))
        // Idempotent and deterministic.
        assertEquals(s.life.npcs, CastleEngine.ensureLords(s).life.npcs)
    }

    @Test fun theLordReceivesOnlyNamedSoberPeople() {
        var s = CastleEngine.enter(at(start(), TimeSlot.MORNING)).state
        assertNotNull(CastleEngine.audienceBlocker(s.copy(life = s.life.copy(renown = 0))))
        assertNotNull(CastleEngine.audienceBlocker(s.copy(life = s.life.copy(renown = 50, intoxication = 50))))
        assertNotNull(CastleEngine.audienceBlocker(at(s.copy(life = s.life.copy(renown = 50)), TimeSlot.NIGHT)))
        s = s.copy(life = s.life.copy(renown = 50))
        assertNull(CastleEngine.audienceBlocker(s))
    }

    @Test fun aLordsTaskFreesABanditProvinceOnTheSharedCrownMap() {
        var s = CastleEngine.enter(at(start(), TimeSlot.MORNING)).state
        s = s.copy(life = s.life.copy(renown = 40))
        s = CastleEngine.askForTask(s).state
        val q = s.life.lordQuest!!
        val target = CrownEngine.province(s, q.targetProvinceId)!!
        assertEquals(ProvinceOwner.BANDITS, target.owner)
        assertTrue(s.life.npc(q.lordId)!!.remembers("quest_${q.id}"))
        // Asking twice does not stack tasks.
        assertEquals(q, CastleEngine.askForTask(s).state.life.lordQuest)

        s = CastleEngine.ride(s).state
        assertEquals(q.id, s.life.skirmish!!.jobId)
        assertEquals(SkirmishKind.CAMPAIGN, s.life.skirmish!!.kind)

        // A lost assault changes nothing on the map and the task stays open.
        val lost = settle(s, SkirmishOutcome.DEFEAT)
        assertEquals(ProvinceOwner.BANDITS, CrownEngine.province(lost, q.targetProvinceId)!!.owner)
        assertEquals(q, lost.life.lordQuest)

        val won = settle(s, SkirmishOutcome.VICTORY)
        assertEquals(ProvinceOwner.FREE, CrownEngine.province(won, q.targetProvinceId)!!.owner)
        assertNull(won.life.lordQuest)
        assertEquals(1, won.life.lordQuestsDone)
        assertTrue(won.life.news.any { it.provinceId == q.targetProvinceId })
        assertTrue(won.chronicle.any { it.title.contains("befreit") })
        assertTrue(won.life.npc(q.lordId)!!.remembers("quest_done_${q.id}"))
        assertTrue(won.life.npc(q.lordId)!!.relation.trust > s.life.npc(q.lordId)!!.relation.trust)
        assertEquals(won, rt(won))
    }

    @Test fun theTaskIsFoughtPersonallyAndDeterministically() {
        var s = CastleEngine.enter(at(start(sword = 90), TimeSlot.MORNING)).state
        s = CastleEngine.askForTask(s.copy(life = s.life.copy(renown = 40, gear = PlayerGear(3, 4)))).state
        s = CastleEngine.ride(s).state
        fun fight(x: GameState): GameState {
            var f = x; var guard = 0
            while (f.life.skirmish?.outcome == null && guard++ < 25) f = SkirmishEngine.act(f, SkirmishAction.ATTACK).state
            return f
        }
        val a = fight(s); val b = fight(s)
        assertNotNull(a.life.skirmish!!.outcome)
        assertEquals(a.life.skirmish, b.life.skirmish)
        val closed = SkirmishEngine.close(a).state
        if (a.life.skirmish!!.outcome == SkirmishOutcome.VICTORY) assertEquals(1, closed.life.lordQuestsDone)
        else assertEquals(0, closed.life.lordQuestsDone)
    }

    @Test fun twoDeedsAndRenownEarnTheAccoladeWhichLowersTheKeepPrice() {
        var s = CastleEngine.enter(at(start(), TimeSlot.MORNING)).state
        s = s.copy(life = s.life.copy(renown = 150, lordQuestsDone = CastleEngine.KNIGHT_QUESTS - 1))
        assertNotNull(CastleEngine.knightBlocker(s))
        s = s.copy(life = s.life.copy(lordQuestsDone = CastleEngine.KNIGHT_QUESTS, renown = CastleEngine.KNIGHT_RENOWN - 10))
        assertNotNull(CastleEngine.knightBlocker(s))
        s = s.copy(life = s.life.copy(renown = CastleEngine.KNIGHT_RENOWN + 10))
        assertNull(CastleEngine.knightBlocker(s))
        assertEquals(LifeEngine.KEEP_GOLD, LifeEngine.keepGold(s))
        val noble = s.life.npcs.first { it.role == NpcRole.NOBLE }
        val k = CastleEngine.knight(s).state
        assertTrue(k.life.knighted)
        assertEquals(CareerRank.KNIGHT, k.life.career)
        assertEquals(CastleEngine.KNIGHT_KEEP_GOLD, LifeEngine.keepGold(k))
        assertTrue(k.life.npc(noble.id)!!.relation.affinity > noble.relation.affinity)
        assertTrue(k.chronicle.any { it.title == "Ritterschlag" })
        assertNotNull(CastleEngine.knightBlocker(k))
        // The keep becomes affordable with 400 gold as a knight.
        val rich = k.copy(life = k.life.copy(purse = 450, renown = LifeEngine.KEEP_RENOWN))
        assertNull(LifeEngine.keepBlocker(rich))
        assertNotNull(LifeEngine.keepBlocker(s.copy(life = s.life.copy(purse = 450, renown = LifeEngine.KEEP_RENOWN))))
        assertEquals(k, rt(k))
    }

    @Test fun tournamentsRunOnTheirDaysOverThreeBouts() {
        var s = CastleEngine.enter(at(start(), TimeSlot.MORNING, day = 13)).state
        assertFalse(CastleEngine.tournamentToday(s))
        assertEquals(14, CastleEngine.nextTournamentDay(s))
        assertNotNull(CastleEngine.tournamentBlocker(s))
        s = at(s, TimeSlot.MORNING, day = 14).let { it.copy(life = it.life.copy(purse = 50)) }
        assertTrue(CastleEngine.tournamentToday(s))
        assertFalse(CastleEngine.tournamentToday(at(s, TimeSlot.EVENING)))
        assertTrue(CastleEngine.tournamentToday(s, "al_aldenburg") == (14 % 5 == 0))

        s = CastleEngine.joinTournament(s).state
        assertEquals(40, s.life.purse)
        assertEquals(SkirmishKind.DUEL, s.life.skirmish!!.kind)
        val firstPower = s.life.skirmish!!.enemyPower
        s = settle(s, SkirmishOutcome.VICTORY)
        assertEquals(1, s.life.tournament!!.round)
        s = CastleEngine.joinTournament(s).state
        assertEquals(40, s.life.purse) // the fee is paid once
        assertTrue(s.life.skirmish!!.enemyPower > firstPower)
        s = settle(s, SkirmishOutcome.VICTORY)
        s = CastleEngine.joinTournament(s).state
        assertTrue(s.life.skirmish!!.title.contains("3/3"))
        s = settle(s, SkirmishOutcome.VICTORY)
        assertEquals(1, s.life.tournamentsWon)
        assertTrue(s.chronicle.any { it.title == "Turniersieg" })
        assertNotNull(CastleEngine.tournamentBlocker(s) ?: CastleEngine.joinTournament(s).message.takeIf { it.contains("bereits") })
        assertEquals(s, rt(s))
    }

    @Test fun aLostBoutEndsTheTournamentForTheDay() {
        var s = CastleEngine.enter(at(start(), TimeSlot.MORNING, day = 14)).state
        s = CastleEngine.joinTournament(s.copy(life = s.life.copy(purse = 50))).state
        s = settle(s, SkirmishOutcome.RETREAT)
        assertTrue(s.life.tournament!!.lost)
        assertNotNull(CastleEngine.tournamentBlocker(s))
        assertEquals(0, s.life.tournamentsWon)
    }

    @Test fun personalGearAndWoundsCountInTheGreatBattles() {
        val s = start()
        assertEquals(1.10, BattleResolutionEngine.personalFactor(s, false), 1e-9)
        assertEquals(1.22, BattleResolutionEngine.personalFactor(s, true), 1e-9)
        val armed = s.copy(life = s.life.copy(gear = PlayerGear(3, 4)))
        assertTrue(BattleResolutionEngine.personalFactor(armed, false) > 1.10)
        val hurt = s.copy(life = s.life.copy(wound = 60))
        assertTrue(BattleResolutionEngine.personalFactor(hurt, false) < 1.10)
    }

    @Test fun couplesSitTogetherAndRivalsClash() {
        var s = NpcSocialEngine.ensureBonds(start())
        s = at(s, TimeSlot.EVENING).let { it.copy(life = it.life.copy(place = LifePlace.TAVERN)) }
        // Search a few evenings for a couple both in the tavern.
        var checked = false
        for (d in 1..40) {
            val e = s.copy(day = d)
            val placed = TavernEngine.placements(e).associateBy { it.npc.id }
            e.life.bonds.filter { it.kind == BondKind.COUPLE && it.a in placed && it.b in placed }.forEach { b ->
                assertTrue("day $d ${placed[b.a]!!.cell} ${placed[b.b]!!.cell}", placed[b.a]!!.cell.dist(placed[b.b]!!.cell) <= 2)
                checked = true
            }
        }
        assertTrue("no couple met in the tavern", checked)

        // Rivals: force two rivals into the same evening by bond on two present people.
        val people = TavernEngine.present(s)
        assertTrue(people.size >= 2)
        val x = people.first { it.role != NpcRole.INNKEEPER }
        val y = people.last { it.role != NpcRole.INNKEEPER && it.id != x.id }
        s = s.copy(life = s.life.copy(bonds = s.life.bonds.filterNot { it.involves(x.id) && it.involves(y.id) } + NpcBond(x.id, y.id, BondKind.RIVALS, 60, 0)))
        assertNotNull(TavernEngine.rivalsPresent(s, people))
        val (a, b) = TavernEngine.rivalsPresent(s, people)!!
        // Find the evening slot where the rivals scene comes up.
        var ev = TavernEngine.event(s)
        var t = s
        var guard = 0
        while (ev?.key != "rivals" && guard++ < 10) {
            t = t.copy(life = t.life.copy(eventsDone = t.life.eventsDone + "${t.day}:${t.life.timeSlot.ordinal}:${t.life.provinceId}:${ev?.key}"))
            ev = TavernEngine.event(t)
        }
        assertEquals("rivals", ev?.key)
        val sided = TavernEngine.resolve(t, "rivals", "side_a").state
        assertTrue(sided.life.npc(a.id)!!.relation.affinity > t.life.npc(a.id)!!.relation.affinity)
        assertTrue(sided.life.npc(b.id)!!.relation.affinity < t.life.npc(b.id)!!.relation.affinity)
        assertTrue(sided.life.npc(b.id)!!.remembers("sided_against_${a.id}"))
        assertNull(TavernEngine.event(sided)?.takeIf { it.key == "rivals" })
        assertEquals(sided, rt(sided))
    }

    @Test fun invalidCastleDataIsRejected() {
        val s = start()
        assertThrows(IllegalArgumentException::class.java) {
            LifeEngine.validate(s.copy(life = s.life.copy(tournament = TournamentRun("gm_rabenfurt", 1, round = 7))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LifeEngine.validate(s.copy(life = s.life.copy(lordQuestsDone = -1)))
        }
    }
}
