package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Acceptance tests for the first Project Rebirth milestone (modes, creation, companion, tavern, dialogue). */
class ProjectRebirthTest {
    private val attrs = GameEngine.StartingAttributes(sword = 60, bow = 40, riding = 30, leadership = 30, tactics = 40, diplomacy = 60)

    private fun config(
        mode: GameMode = GameMode.FREE_LIFE,
        choice: CompanionChoice = CompanionChoice.NONE,
        draft: NpcEngine.CompanionDraft? = null,
        background: Background = Background.MERCENARY,
    ) = LifeEngine.StartConfig(
        mode = mode, name = "Leon Falkner", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = background, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        appearance = CharacterAppearance(faceShape = 2, jaw = 80, scar = 1, beard = 3, hairColor = 4, height = 70, build = 2, armor = 2),
        attributes = attrs, startingCultures = setOf(Culture.HUMAN), companionChoice = choice, companionDraft = draft,
    )

    private fun roundTrip(state: GameState): GameState = SaveCodec.decode(SaveCodec.encode(state))

    // ------------------------------------------------------------ A: modes

    @Test fun freeLifeStartsWithoutRealmTitleOrArmyInCommand() {
        val s = LifeEngine.newGame(config())
        assertFalse(s.life.landed)
        assertEquals(GameMode.FREE_LIFE, s.life.mode)
        assertEquals(WealthLevel.MODEST.gold, s.life.purse)
        assertEquals("gm_rabenfurt", s.life.provinceId)
        assertTrue(s.crown.ownedProvinces.isEmpty())
        assertFalse(s.life.storyActs)
        // The personal day does not run the dormant realm: treasury and army stay untouched.
        val next = GameEngine.advanceDay(s).state
        assertEquals(s.day + 1, next.day)
        assertEquals(s.resources, next.resources)
        assertEquals(s.armySize, next.armySize)
        assertEquals(TimeSlot.MORNING, next.life.timeSlot)
        assertEquals(next, roundTrip(next))
    }

    @Test fun borderLordModeKeepsTheClassicStart() {
        val s = LifeEngine.newGame(config(mode = GameMode.BORDER_LORD))
        assertTrue(s.life.landed)
        assertTrue(s.armySize > 0)
        assertTrue(s.commanders.isNotEmpty())
        val next = GameEngine.advanceDay(s).state
        assertEquals(2, next.day)
        assertTrue(next.life.landed)
        assertEquals(next, roundTrip(next))
    }

    @Test fun storyModeEnablesActsAndBorderLordCanDisableThem() {
        assertTrue(LifeEngine.newGame(config(mode = GameMode.STORY)).life.storyActs)
        val quiet = LifeEngine.newGame(config(mode = GameMode.BORDER_LORD)).let { it.copy(life = it.life.copy(storyActs = false)) }
        assertSame(quiet, ActEngine.tick(quiet))
    }

    // ------------------------------------------------------------ B: player appearance

    @Test fun playerAppearanceAndBiographyPersist() {
        val s = roundTrip(LifeEngine.newGame(config()))
        val look = s.life.appearance!!
        assertEquals(2, look.faceShape)
        assertEquals(80, look.jaw)
        assertEquals(1, look.scar)
        assertEquals(look.toPortraitSpec(), s.crown.playerLook)
        assertEquals(Background.MERCENARY, s.life.background)
        assertEquals("Söldner", s.life.profession)
        // Background nudges existing attributes instead of adding a second attribute system.
        assertEquals(65, s.player.sword)
        assertEquals(45, s.player.tactics)
    }

    // ------------------------------------------------------------ C/D: companion question

    @Test fun allThreeCompanionChoicesAreSupported() {
        assertEquals(3, CompanionChoice.entries.size)
        val none = LifeEngine.newGame(config(choice = CompanionChoice.NONE))
        assertNull(none.life.companion)
        val full = LifeEngine.newGame(config(choice = CompanionChoice.FULL, draft = NpcEngine.CompanionDraft(
            name = "Mira Taufarn", age = 25, species = Species.HALF_ELF, culture = Culture.WOOD_ELF, profession = "Grenzläuferin",
            backstory = "Sie bewacht die Furt.", appearance = CharacterAppearance(hairColor = 4, eyeColor = 2, ears = 1),
            personality = NpcPersonality(warmth = 70, boldness = 80, honor = 60, humor = 55, curiosity = 60, temper = 30),
            interests = listOf("Bogenschießen", "Sternkunde"),
        )))
        val mira = full.life.companion!!
        assertEquals("Mira Taufarn", mira.name)
        assertEquals(25, mira.age)
        assertEquals("Sie bewacht die Furt.", mira.backstory)
        assertEquals(listOf("Bogenschießen", "Sternkunde"), mira.interests)
        assertTrue(mira.designedByPlayer)
        // Designing her never creates a relationship.
        assertEquals(SocialStage.STRANGER, mira.relation.stage)
        assertEquals(0, mira.relation.attraction)
        assertEquals(0, mira.relation.timesMet)
        val partial = LifeEngine.newGame(config(choice = CompanionChoice.PARTIAL, draft = NpcEngine.CompanionDraft(
            species = Species.ELF, appearance = CharacterAppearance(hairColor = 5))))
        val generated = partial.life.companion!!
        assertEquals(Species.ELF, generated.species)
        assertEquals(5, generated.appearance.hairColor)
        assertTrue(generated.name.isNotBlank() && generated.age >= 18)
        assertEquals(CompanionChoice.PARTIAL, partial.life.companionChoice)
    }

    @Test fun companionIdentityIsStableAcrossSavesAndDays() {
        var s = LifeEngine.newGame(config(choice = CompanionChoice.FULL, draft = NpcEngine.CompanionDraft(name = "Runa Brandt", age = 27)))
        val original = s.life.companion!!
        repeat(5) { s = roundTrip(GameEngine.advanceDay(s).state) }
        val later = s.life.companion!!
        assertEquals(original.name, later.name)
        assertEquals(original.appearance, later.appearance)
        assertEquals(original.personality, later.personality)
        assertEquals(original.backstory, later.backstory)
    }

    @Test(expected = IllegalArgumentException::class)
    fun companionMustBeAdult() {
        NpcEngine.designCompanion("x", NpcEngine.CompanionDraft(age = 17), "gm_rabenfurt", Gender.MALE)
    }

    @Test fun landedCompanionFollowsTheHeroineSagaWithoutRomance() {
        val s = LifeEngine.newGame(config(mode = GameMode.BORDER_LORD, choice = CompanionChoice.FULL,
            draft = NpcEngine.CompanionDraft(name = "Selma Rabe", age = 23)).copy())
        assertEquals("Selma Rabe", s.crown.saga.partner?.name)
        assertEquals(RomanceStage.NONE, s.relationship.romanceStage)
        assertTrue(s.life.companion!!.departed)
    }

    // ------------------------------------------------------------ E: early natural meeting

    @Test fun designedCompanionCanBeMetEarlyInTheTavernAndAgainLater() {
        var s = LifeEngine.newGame(config(choice = CompanionChoice.FULL, draft = NpcEngine.CompanionDraft(name = "Mira", age = 24)))
        // Day 2 evening: she is in the start town's tavern (natural, no forced scene).
        s = GameEngine.advanceDay(s).state
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING))
        s = TavernEngine.enter(s).state
        assertTrue(TavernEngine.present(s).any { it.designedByPlayer })
        s = DialogueEngine.open(s, "companion").state
        assertEquals(1, s.life.companion!!.relation.timesMet)
        // Later she can be found again in town on other days.
        var seenAgain = false
        repeat(10) {
            s = GameEngine.advanceDay(s).state
            for (slot in TimeSlot.entries) {
                val here = NpcEngine.placeOf(s.life.companion!!, s.day, slot, s)
                if (here.first == s.life.provinceId && here.second != LifePlace.AWAY) seenAgain = true
            }
        }
        assertTrue(seenAgain)
    }

    // ------------------------------------------------------------ F: walkable tavern

    @Test fun everySeatIsReachableFromTheDoor() {
        val l = TavernEngine.layout
        for (seat in l.seats) {
            assertTrue("seat ${seat.cell} walkable", l.walkable(seat.cell))
            val p = TavernEngine.path(l.door, seat.cell)
            assertTrue("path to ${seat.cell}", p.isNotEmpty() && p.last() == seat.cell)
            p.forEach { assertTrue(l.walkable(it)) }
        }
        assertTrue(TavernEngine.path(l.door, l.barFront).isNotEmpty())
        assertTrue(TavernEngine.path(l.door, l.counter.first()).isEmpty())
    }

    @Test fun tavernFillsInTheEveningAndPlacementsAreDistinct() {
        var s = LifeEngine.newGame(config())
        s = TavernEngine.enter(s.copy(life = s.life.copy(timeSlot = TimeSlot.MORNING))).state
        val morning = TavernEngine.present(s).size
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING))
        val evening = TavernEngine.present(s).size
        assertTrue("evening $evening > morning $morning", evening > morning)
        val cells = TavernEngine.placements(s).map { it.cell }
        assertEquals(cells.size, cells.distinct().size)
        assertTrue(TavernEngine.placements(s).any { it.npc.role == NpcRole.INNKEEPER && it.cell == TavernEngine.layout.innkeeper })
    }

    @Test fun drinkingCostsGoldAndAffectsSkillsButFades() {
        var s = TavernEngine.enter(LifeEngine.newGame(config())).state
        val purse = s.life.purse
        val sober = LifeEngine.dexterity(s)
        repeat(3) { s = LifeEngine.drink(s, Drink.SPIRIT).state }
        assertEquals(purse - 3 * Drink.SPIRIT.price, s.life.purse)
        assertTrue(s.life.intoxication >= 50)
        assertTrue(LifeEngine.dexterity(s) < sober)
        s = LifeEngine.passTime(s, 1).state
        assertTrue(s.life.intoxication < 90)
        s = GameEngine.advanceDay(s).state
        assertEquals(0, s.life.intoxication)
        assertTrue(s.life.hangover)
    }

    // ------------------------------------------------------------ G: social state persists and is remembered

    @Test fun conversationChangesSavedStateAndIsRememberedLater() {
        var s = TavernEngine.enter(LifeEngine.newGame(config()).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.EVENING)) }).state
        val npc = TavernEngine.present(s).first { it.role != NpcRole.INNKEEPER }
        s = DialogueEngine.open(s, npc.id).state
        val reply = DialogueEngine.choose(s, npc.id, "buy_drink")
        assertEquals(DialogueEngine.Outcome.SUCCESS, reply.outcome)
        s = roundTrip(reply.state)
        val after = s.life.npc(npc.id)!!
        assertTrue(after.relation.affinity > 0)
        assertTrue(after.remembers("drink_bought"))
        s = GameEngine.advanceDay(s).state
        val greeting = DialogueEngine.open(s, npc.id).npcLine
        assertTrue(greeting, greeting.contains("Met"))
    }

    @Test fun romanceIsNeverAutomaticAndRespectsConsentAndSobriety() {
        var s = LifeEngine.newGame(config(choice = CompanionChoice.FULL, draft = NpcEngine.CompanionDraft(name = "Mira", age = 24,
            personality = NpcPersonality(warmth = 80, boldness = 70, honor = 50, humor = 70, curiosity = 60, temper = 20))))
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN))
        s = DialogueEngine.open(s, "companion").state
        // Strangers cannot be kissed: the option is not even offered.
        assertTrue(DialogueEngine.options(s, "companion").none { it.topic.id == "kiss" || it.topic.id == "night" })
        // Even with high attraction, a drunk player gets no intimacy.
        val mira = s.life.companion!!
        s = LifeEngine.updateNpc(s, mira.copy(relation = mira.relation.copy(stage = SocialStage.LOVER, attraction = 90, trust = 70, affinity = 60)))
        s = LifeEngine.rentRoom(s).state
        s = s.copy(life = s.life.copy(intoxication = 80))
        val drunk = DialogueEngine.options(s, "companion").first { it.topic.id == "night" }
        assertFalse(drunk.enabled)
        // Sober: she decides for herself; with this much trust and attraction she agrees, and it fades to black.
        s = s.copy(life = s.life.copy(intoxication = 0))
        val reply = DialogueEngine.choose(s, "companion", "night")
        assertEquals(DialogueEngine.Outcome.SUCCESS, reply.outcome)
        assertNotNull(reply.fadeToBlack)
        assertEquals(1, reply.state.life.companion!!.relation.intimateNights)
        assertEquals(s.day + 1, reply.state.day)
    }

    @Test fun repeatedRejectedAdvancesSetABoundary() {
        var s = LifeEngine.newGame(config(choice = CompanionChoice.FULL, draft = NpcEngine.CompanionDraft(name = "Mira", age = 24,
            personality = NpcPersonality(warmth = 20, boldness = 20, honor = 70, humor = 40, curiosity = 40, temper = 30))))
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN))
        val mira = s.life.companion!!
        s = LifeEngine.updateNpc(s, mira.copy(relation = mira.relation.copy(stage = SocialStage.FLIRT, attraction = 55, trust = 21, timesMet = 3)))
        repeat(3) {
            s = s.copy(life = s.life.copy(npcs = s.life.npcs.map { it.copy(relation = it.relation.copy(topicsThisSlot = emptyList())) }))
            val r = DialogueEngine.choose(s, "companion", "kiss")
            assertEquals(DialogueEngine.Outcome.DECLINED, r.outcome)
            s = r.state
        }
        assertTrue(s.life.companion!!.relation.romanceBoundary)
        assertTrue(DialogueEngine.options(s, "companion").none { it.topic.kind == TopicKind.ROMANCE && it.topic.id != "breakup" })
    }

    @Test fun anAffairHasConsequencesForThePartner() {
        var s = LifeEngine.newGame(config(choice = CompanionChoice.FULL, draft = NpcEngine.CompanionDraft(name = "Mira", age = 24)))
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.AFTERNOON, place = LifePlace.TAVERN))
        val mira = s.life.companion!!
        s = LifeEngine.updateNpc(s, mira.copy(relation = mira.relation.copy(stage = SocialStage.PARTNER, attraction = 80, trust = 80, affinity = 70)))
        val other = s.life.npcs.first { it.romanceable && Gender.MALE in it.attractedTo && !it.designedByPlayer && it.role != NpcRole.INNKEEPER }
        s = LifeEngine.updateNpc(s, other.copy(relation = other.relation.copy(stage = SocialStage.FLIRT, attraction = 95, trust = 80, affinity = 60)))
        val kiss = DialogueEngine.choose(s, other.id, "kiss")
        assertEquals(DialogueEngine.Outcome.SUCCESS, kiss.outcome)
        var t = kiss.state
        if (t.life.companion!!.relation.hurtByAffairDay == null) {
            assertTrue(t.life.pendingGossip.isNotEmpty())
            repeat(3) { if (t.life.companion!!.relation.hurtByAffairDay == null) t = GameEngine.advanceDay(t).state.let { x ->
                // Gossip that missed her once is not repeated; re-queue to test the delivery rule deterministically.
                if (x.life.companion!!.relation.hurtByAffairDay == null) x.copy(life = x.life.copy(pendingGossip = kiss.state.life.pendingGossip)) else x } }
        }
        val hurt = t.life.companion!!
        assertNotNull(hurt.relation.hurtByAffairDay)
        assertTrue(hurt.relation.trust < 80)
        assertTrue(hurt.memories.any { it.key.startsWith("affair_") })
    }

    // ------------------------------------------------------------ I: one world for both layers

    @Test fun livingWorldChangesTheSharedCrownProvinces() {
        var s = LifeEngine.newGame(config())
        val before = s.crown.provinces.associate { it.id to it.owner }
        repeat(30) { s = GameEngine.advanceDay(s).state }
        assertTrue(s.life.news.isNotEmpty())
        val changed = s.crown.provinces.count { before[it.id] != it.owner }
        val raided = s.life.news.any { it.severity == 2 }
        assertTrue("world should move (changed=$changed raided=$raided)", changed > 0 || raided)
        assertTrue(s.crown.provinces.none { it.owner == ProvinceOwner.PLAYER })
        // Rumors heard in the tavern are the same news the world produced.
        assertTrue(LifeEngine.rumors(s).all { it in s.life.news })
    }

    @Test fun threatenedTownsRaisePricesAndBringRecruiters() {
        var s = LifeEngine.newGame(config())
        val base = LifeEngine.priced(s, 4)
        s = s.copy(life = s.life.copy(news = listOf(WorldNews(s.day, s.life.provinceId, "Orks überfallen Rabenfurt", "…", 2)), timeSlot = TimeSlot.AFTERNOON))
        assertTrue(LifeEngine.threatened(s, s.life.provinceId))
        assertTrue(LifeEngine.priced(s, 4) > base)
        val captain = s.life.npcs.first { it.role == NpcRole.MERCENARY_CAPTAIN && it.homeProvinceId == s.life.provinceId }
        assertEquals(s.life.provinceId to LifePlace.TAVERN, NpcEngine.placeOf(captain, s.day, TimeSlot.AFTERNOON, s))
    }

    // ------------------------------------------------------------ jobs, travel, rise

    @Test fun jobsTravelAndClaimingTheKeep() {
        var s = LifeEngine.newGame(config())
        val job = s.life.jobs.first()
        s = LifeEngine.acceptJob(s, job.id).state
        s = LifeEngine.doJob(s, job.id).state
        val done = s.life.jobs.first { it.id == job.id }
        assertTrue(done.done || done.failed)
        val beforeDay = s.day
        s = LifeEngine.travel(s, "gm_eschenhof").state
        assertEquals("gm_eschenhof", s.life.provinceId)
        assertEquals(beforeDay + 1, s.day)
        assertNotNull(LifeEngine.keepBlocker(s))
        s = s.copy(life = s.life.copy(renown = LifeEngine.KEEP_RENOWN, purse = LifeEngine.KEEP_GOLD))
        val claimed = LifeEngine.claimKeep(s).state
        assertTrue(claimed.life.landed)
        assertEquals(CareerRank.LORD, claimed.life.career)
        val next = GameEngine.advanceDay(claimed).state
        assertEquals(claimed.day + 1, next.day)
        assertEquals(next, roundTrip(next))
    }

    @Test fun diceAreDeterministicPerRoundAndMoveMoney() {
        val s = TavernEngine.enter(LifeEngine.newGame(config())).state
        val a = TavernEngine.dice(s, 2)
        val b = TavernEngine.dice(s, 2)
        assertEquals(a.player, b.player)
        assertEquals(a.opponent, b.opponent)
        if (a.won == true) assertEquals(s.life.purse + 2, a.state.life.purse)
        if (a.won == false) assertEquals(s.life.purse - 2, a.state.life.purse)
    }
}
