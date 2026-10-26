package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Milestone 5: the person stays a person after the oath; deeds are known; rulers walk their towns. */
class RebirthRulerTest {
    private fun start(choice: CompanionChoice = CompanionChoice.FULL, mode: GameMode = GameMode.FREE_LIFE): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = mode, name = "Leon", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = Background.MERCENARY, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        attributes = GameEngine.StartingAttributes(sword = 70, bow = 40, riding = 40, leadership = 40, tactics = 30, diplomacy = 60),
        companionChoice = choice, companionDraft = NpcEngine.CompanionDraft(name = "Mira", age = 24,
            personality = NpcPersonality(warmth = 70, boldness = 75, honor = 60, humor = 55, curiosity = 70, temper = 25)),
    ))

    private fun rt(s: GameState) = SaveCodec.decode(SaveCodec.encode(s))

    private fun befriend(s: GameState, id: String, trust: Int = 80, affinity: Int = 70): GameState {
        val n = s.life.npc(id)!!
        return LifeEngine.updateNpc(s, n.copy(relation = n.relation.copy(stage = SocialStage.FRIEND, trust = trust, affinity = affinity, timesMet = 4)))
    }

    /** A landless hero with a friend and a sellsword who takes the keep. */
    private fun ruler(knighted: Boolean = false): GameState {
        var s = start().let { it.copy(life = it.life.copy(purse = 900, renown = 160, timeSlot = TimeSlot.NOON, knighted = knighted)) }
        s = befriend(s, "companion")
        s = PartyEngine.invite(s, "companion").state
        val captain = s.life.npcs.first { it.role == NpcRole.MERCENARY_CAPTAIN && it.homeProvinceId == s.life.provinceId }
        s = PartyEngine.hire(s, captain.id).state
        return LifeEngine.claimKeep(s).state
    }

    private fun anyEvening(s: GameState) = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING))

    @Test fun followersMoveToTheKeepAndNoLongerSitInTheTavern() {
        val s = ruler()
        assertTrue("landed", s.life.landed)
        val mira = s.life.npc("companion")!!
        assertTrue("mira departed ${s.companion}", mira.departed)
        assertTrue(mira.remembers("court"))
        // The hired sellsword went along as a captain.
        val captain = s.life.npcs.first { it.role == NpcRole.SELLSWORD && it.remembers("court") }
        assertTrue("captain", captain.departed)
        // Over many evenings, neither shows up in any tavern.
        for (d in s.day..s.day + 20) {
            val e = anyEvening(s.copy(day = d)).let { it.copy(life = it.life.copy(place = LifePlace.TAVERN)) }
            val here = TavernEngine.present(e).map { it.id }
            assertFalse(mira.id in here)
            assertFalse(captain.id in here)
        }
        assertTrue("news", s.life.news.any { it.title == "Neuer Herr der Grenzfeste" })
        assertEquals(s, rt(s))
    }

    @Test fun aKnightTakingTheKeepIsRememberedAsKnight() {
        val s = ruler(knighted = true)
        assertTrue(s.chronicle.last { it.title == "Herr der Grenzfeste" }.text.startsWith("Ritter "))
        assertTrue(s.life.knighted)
    }

    @Test fun rulersPayFromTheTreasuryAndTheirGearCountsInBattle() {
        var s = start(CompanionChoice.NONE, GameMode.BORDER_LORD)
        assertTrue(s.life.landed)
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.MORNING), resources = s.resources.copy(gold = 5000))
        val before = BattleResolutionEngine.personalFactor(s, false)
        s = MarketEngine.enter(s).state
        assertEquals(LifePlace.MARKET, s.life.place)
        val gold = s.resources.gold
        s = MarketEngine.buyArmor(s, 3).state
        s = MarketEngine.buyWeapon(s, 2).state
        assertTrue(s.resources.gold < gold)
        assertEquals(0, s.life.purse)
        assertTrue(BattleResolutionEngine.personalFactor(s, false) > before)
        // Barracks and castle are open to rulers too.
        assertEquals(LifePlace.BARRACKS, BarracksEngine.enter(s).state.life.place)
        assertEquals(LifePlace.CASTLE, CastleEngine.enter(s).state.life.place)
    }

    @Test fun theCourtDaySendsVolunteersOnceAWeek() {
        var s = start(CompanionChoice.NONE, GameMode.BORDER_LORD).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING)) }
        s = CastleEngine.enter(s).state
        // Landless characters cannot hold court.
        assertNotNull(CastleEngine.courtBlocker(start().let { CastleEngine.enter(it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING))).state }))
        assertNull(CastleEngine.courtBlocker(s))
        val culture = CrownCatalog.info(s.life.provinceId).culture
        val recruits = s.population.recruits(culture)
        val expected = CastleEngine.courtVolunteers(s)
        val held = CastleEngine.courtDay(s).state
        assertEquals(recruits + expected, held.population.recruits(culture))
        assertNotNull(CastleEngine.courtBlocker(held))
        assertNotNull(CastleEngine.courtBlocker(held.copy(day = held.day + 6)))
        val week = held.copy(day = held.day + 7, life = held.life.copy(timeSlot = TimeSlot.MORNING))
        assertNull(CastleEngine.courtBlocker(week))
        // Knights and old allies of the crown get more.
        assertTrue(CastleEngine.courtVolunteers(week.copy(life = week.life.copy(knighted = true, lordQuestsDone = 2))) > CastleEngine.courtVolunteers(week))
        assertEquals(held, rt(held))
        LifeEngine.validate(held)
    }

    @Test fun peopleHaveHeardOfTheDeedsAndSayItOnce() {
        var s = start(CompanionChoice.NONE).let { it.copy(life = it.life.copy(knighted = true, tournamentsWon = 1, timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        val npc = s.life.npcs.first { it.role == NpcRole.REGULAR && it.homeProvinceId == s.life.provinceId }
        val first = DialogueEngine.open(s, npc.id)
        assertTrue(first.npcLine, first.npcLine.contains("Ritter Leon"))
        s = first.state
        assertTrue(s.life.npc(npc.id)!!.remembers("heard_knight"))
        val second = DialogueEngine.open(s.copy(day = s.day + 1), npc.id)
        assertFalse(second.npcLine.contains("Ritter Leon"))
        assertTrue(second.npcLine, second.npcLine.contains("Schranken"))
        val third = DialogueEngine.open(second.state.copy(day = s.day + 2), npc.id)
        assertFalse(third.npcLine.contains("Schranken"))
        // The blank 'heard' notes never show up as a recalled memory.
        assertFalse(third.npcLine.contains("Weißt du noch? ."))
    }

    @Test fun rulersAreGreetedAsLordsAndCanHearPetitions() {
        var s = start(CompanionChoice.NONE, GameMode.BORDER_LORD).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        s = LifeEngine.ensurePeople(s)
        val npc = s.life.npcs.first { it.role == NpcRole.REGULAR && it.homeProvinceId == s.life.provinceId }
        val greet = DialogueEngine.open(s, npc.id)
        assertTrue(greet.npcLine, greet.npcLine.contains("Herr"))
        s = greet.state
        val opts = DialogueEngine.options(s, npc.id).map { it.topic.id }
        assertTrue("petition" in opts)
        assertTrue("brag" !in opts || DialogueEngine.options(s, npc.id).first { it.topic.id == "brag" }.enabled.not() || s.life.npc(npc.id)!!.relation.stage.ordinal >= SocialStage.ACQUAINTANCE.ordinal)
        val gold = s.resources.gold
        val r = DialogueEngine.choose(s, npc.id, "petition")
        assertEquals(DialogueEngine.Outcome.SUCCESS, r.outcome)
        assertFalse(r.npcLine, r.npcLine.contains("{"))
        assertTrue(r.state.resources.gold < gold)
        assertTrue(r.state.life.npc(npc.id)!!.relation.affinity > s.life.npc(npc.id)!!.relation.affinity)
        assertFalse("petition" in DialogueEngine.options(r.state.copy(day = r.state.day + 1), npc.id).map { it.topic.id })
        // A landless commoner has nobody to petition.
        val commoner = start(CompanionChoice.NONE).let { LifeEngine.ensurePeople(it) }
        val c = commoner.life.npcs.first { it.role == NpcRole.REGULAR }
        assertFalse("petition" in DialogueEngine.options(commoner, c.id).map { it.topic.id })
    }

    @Test fun braggingNeedsADeedAndTellsIt() {
        var s = start(CompanionChoice.NONE).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        val npc = s.life.npcs.first { it.role == NpcRole.REGULAR && it.homeProvinceId == s.life.provinceId }
        s = befriend(s, npc.id, trust = 40, affinity = 40)
        assertFalse("brag" in DialogueEngine.options(s, npc.id).map { it.topic.id })
        s = s.copy(life = s.life.copy(tournamentsWon = 1))
        assertTrue("brag" in DialogueEngine.options(s, npc.id).map { it.topic.id })
        val r = DialogueEngine.choose(s, npc.id, "brag")
        assertNotEquals(DialogueEngine.Outcome.UNAVAILABLE, r.outcome)
        assertFalse(r.npcLine, r.npcLine.contains("{"))
        val mem = r.state.life.npc(npc.id)!!.memories.first { it.key == "bragged" }
        assertFalse(mem.text, mem.text.contains("{"))
    }

    @Test fun askingAboutTournamentsGivesTheRealDay() {
        var s = start(CompanionChoice.NONE).let { it.copy(day = 10, life = it.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        val keeper = s.life.npcs.first { it.role == NpcRole.INNKEEPER && it.homeProvinceId == s.life.provinceId }
        val r = DialogueEngine.choose(s, keeper.id, "ask_tourney")
        assertTrue(r.npcLine, r.npcLine.contains("Tag 14"))
    }

    @Test fun nobodyStandsDirectlyInFrontOfSomeoneElseInTheTavern() {
        val base = NpcSocialEngine.ensureBonds(start()).let { it.copy(life = it.life.copy(place = LifePlace.TAVERN)) }
        for (d in 1..30) for (slot in listOf(TimeSlot.AFTERNOON, TimeSlot.EVENING, TimeSlot.NIGHT)) {
            val p = TavernEngine.placements(base.copy(day = d, life = base.life.copy(timeSlot = slot)))
            p.forEach { a -> p.forEach { b ->
                if (a !== b) assertFalse("day $d ${a.cell} ${b.cell}", a.cell.x == b.cell.x && kotlin.math.abs(a.cell.y - b.cell.y) == 1)
            } }
        }
    }
}
