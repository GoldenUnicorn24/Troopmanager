package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Milestone 2: following, market, gear, gifts, personal fights, wounds, captivity and the handover at the keep. */
class RebirthRoadTest {
    private fun start(choice: CompanionChoice = CompanionChoice.FULL, sword: Int = 70): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = GameMode.FREE_LIFE, name = "Leon", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = Background.MERCENARY, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        attributes = GameEngine.StartingAttributes(sword = sword, bow = 40, riding = 40, leadership = 40, tactics = 30, diplomacy = 40),
        companionChoice = choice, companionDraft = NpcEngine.CompanionDraft(name = "Mira", age = 24,
            personality = NpcPersonality(warmth = 70, boldness = 75, honor = 60, humor = 55, curiosity = 70, temper = 25)),
    ))

    private fun rt(s: GameState) = SaveCodec.decode(SaveCodec.encode(s))

    private fun befriend(s: GameState, id: String, trust: Int = 60, affinity: Int = 60): GameState {
        val n = s.life.npc(id)!!
        return LifeEngine.updateNpc(s, n.copy(relation = n.relation.copy(stage = SocialStage.FRIEND, trust = trust, affinity = affinity, timesMet = 4)))
    }

    private fun fightToEnd(s0: GameState, action: SkirmishAction = SkirmishAction.ATTACK): GameState {
        var s = s0
        var guard = 0
        while (s.life.skirmish?.outcome == null && guard++ < 20) s = SkirmishEngine.act(s, action).state
        return s
    }

    @Test fun friendsJoinOnlyWhenTheyWantToAndFollowThePlayer() {
        var s = start()
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN))
        // A stranger declines.
        val cold = DialogueEngine.choose(befriend(s, "companion", trust = 25, affinity = 5), "companion", "invite")
        assertEquals(DialogueEngine.Outcome.DECLINED, cold.outcome)
        assertFalse(cold.state.life.inParty("companion"))
        // A friend joins and then goes where the player goes.
        s = befriend(s, "companion")
        val warm = DialogueEngine.choose(s, "companion", "invite")
        assertEquals(DialogueEngine.Outcome.SUCCESS, warm.outcome)
        s = warm.state
        assertTrue(s.life.inParty("companion"))
        s = LifeEngine.travel(s.copy(life = s.life.copy(place = LifePlace.TOWN, purse = 200)), "gm_eschenhof").state
        if (s.life.skirmish != null) s = SkirmishEngine.close(fightToEnd(s)).state
        assertEquals("gm_eschenhof", s.life.provinceId)
        val mira = s.life.companion!!
        assertEquals(s.life.provinceId to s.life.place, NpcEngine.placeOf(mira, s.day, s.life.timeSlot, s))
        assertTrue(mira.remembers("travel_gm_eschenhof"))
        assertEquals(s, rt(s))
    }

    @Test fun sellswordsCostWagesAndLeaveWhenUnpaid() {
        var s = start(CompanionChoice.NONE).let { it.copy(life = it.life.copy(purse = 100, timeSlot = TimeSlot.NOON)) }
        val captain = s.life.npcs.first { it.role == NpcRole.MERCENARY_CAPTAIN && it.homeProvinceId == s.life.provinceId }
        repeat(3) { s = DialogueEngine.choose(s, captain.id, "hire").state }
        assertEquals(3, PartyEngine.sellswords(s))
        assertEquals(100 - 3 * PartyEngine.HIRE_FEE, s.life.purse)
        assertEquals(CareerRank.MERCENARY, s.life.career)
        s = GameEngine.advanceDay(s).state
        assertEquals(40 - 3 * PartyEngine.SELLSWORD_WAGE, s.life.purse)
        s = GameEngine.advanceDay(s.copy(life = s.life.copy(purse = 1))).state
        assertEquals(0, PartyEngine.sellswords(s))
        assertTrue(s.life.npcs.count { it.role == NpcRole.SELLSWORD && it.departed } == 3)
        assertEquals(s, rt(s))
    }

    @Test fun marketGearChangesLookAndStrengthAndHealingWorks() {
        var s = MarketEngine.enter(start().let { it.copy(life = it.life.copy(purse = 500, timeSlot = TimeSlot.MORNING)) }).state
        assertTrue(MarketEngine.open(s))
        val keepers = MarketEngine.placements(s).filter { it.stall != null }
        assertEquals(3, keepers.size)
        MarketEngine.placements(s).forEach { assertFalse(MarketEngine.layout.walkable(it.cell) && it.stall != null) }
        s = MarketEngine.buyArmor(s, 2).state
        assertEquals(2, s.life.gear.armorTier)
        assertEquals(2, s.life.appearance!!.armor)
        assertEquals(s.life.appearance!!.toPortraitSpec(), s.crown.playerLook)
        s = MarketEngine.buyWeapon(s, 2).state
        assertEquals(12, SkirmishEngine.weaponBonus(s))
        s = s.copy(life = s.life.copy(wound = 50))
        s = MarketEngine.heal(s).state
        assertEquals(20, s.life.wound)
        assertTrue(TavernEngine.path(MarketEngine.layout.entry, MarketEngine.layout.frontSpots.getValue(MarketEngine.Stall.SMITH), l = MarketEngine.layout).isNotEmpty())
        val closed = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING))
        assertTrue(MarketEngine.placements(closed).isEmpty())
        assertEquals(s, rt(s))
    }

    @Test fun giftsThatMatchTasteMatterMore() {
        var s = start().let { it.copy(life = it.life.copy(purse = 200, timeSlot = TimeSlot.MORNING)) }
        val reader = s.life.npcs.first { !it.designedByPlayer && it.role == NpcRole.REGULAR }.let { it.copy(likes = listOf("Bücher"), interests = emptyList()) }
        s = LifeEngine.updateNpc(s, reader)
        s = MarketEngine.buyGift(MarketEngine.buyGift(s, GiftKind.BOOK).state, GiftKind.FLOWERS).state
        assertTrue(DialogueEngine.options(s, reader.id).any { it.topic.id == "gift_BOOK" })
        val reply = DialogueEngine.choose(s, reader.id, "gift_BOOK")
        assertEquals(DialogueEngine.Outcome.SUCCESS, reply.outcome)
        val gained = reply.state.life.npc(reader.id)!!.relation.affinity - reader.relation.affinity
        assertEquals(MarketEngine.giftValue(reader, GiftKind.BOOK), gained)
        assertTrue(gained > MarketEngine.giftValue(reader, GiftKind.FLOWERS))
        assertEquals(0, reply.state.life.gifts[GiftKind.BOOK])
        // Only one gift per person per day.
        val again = DialogueEngine.choose(reply.state, reader.id, "gift_FLOWERS")
        assertEquals(DialogueEngine.Outcome.FAILURE, again.outcome)
    }

    @Test fun combatJobIsFoughtPersonallyWithConsequences() {
        var s = start(sword = 90).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING, purse = 500)) }
        s = MarketEngine.buyWeapon(MarketEngine.buyArmor(s, 3).state, 3).state
        val job = LifeJob("jobX", "Wölfe am Weidezaun", "", s.life.npcs.first().id, s.life.provinceId, "bow", 50, 30, 4, 1, s.day, accepted = true, combat = true, enemy = "Wölfe")
        s = s.copy(life = s.life.copy(jobs = listOf(job)))
        s = LifeEngine.doJob(s, "jobX").state
        val fight = s.life.skirmish!!
        assertEquals(SkirmishKind.BEASTS, fight.kind)
        assertEquals(s, rt(s))
        val purse = s.life.purse
        s = fightToEnd(s)
        val done = s.life.skirmish!!
        assertNotNull(done.outcome)
        if (done.outcome == SkirmishOutcome.VICTORY) {
            assertEquals(purse + 30, s.life.purse)
            assertTrue(s.life.jobs.first().done)
        } else assertTrue(s.life.jobs.first().failed)
        s = SkirmishEngine.close(s).state
        assertNull(s.life.skirmish)
        // Deterministic: the same fight from the same state ends the same way.
        val again = fightToEnd(LifeEngine.doJob(start(sword = 90).let { st ->
            MarketEngine.buyWeapon(MarketEngine.buyArmor(st.copy(life = st.life.copy(timeSlot = TimeSlot.MORNING, purse = 500)), 3).state, 3).state
        }.let { it.copy(life = it.life.copy(jobs = listOf(job))) }, "jobX").state)
        assertEquals(done.outcome, again.life.skirmish!!.outcome)
    }

    @Test fun followersFightAlongAndRememberIt() {
        var s = start().let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING, purse = 500)) }
        s = befriend(s, "companion")
        s = PartyEngine.invite(s, "companion").state
        s = SkirmishEngine.start(s, SkirmishKind.CAMPAIGN, "Scharmützel", "Plünderer", 3, 40, 20, 5)
        assertEquals(listOf("companion"), s.life.skirmish!!.allyIds)
        assertTrue(SkirmishEngine.alliesPower(s, s.life.skirmish!!) > 0)
        s = fightToEnd(s)
        val mira = s.life.companion!!
        assertTrue(mira.memories.any { it.key.startsWith("fight_") })
        assertTrue(mira.relation.trust > 60)
    }

    @Test fun defeatWoundsAndCaptivityCanBeEscapedOrWaitedOut() {
        var s = start(CompanionChoice.NONE, sword = 30).let { it.copy(life = it.life.copy(purse = 80)) }
        s = SkirmishEngine.start(s, SkirmishKind.AMBUSH, "Hinterhalt", "Wegelagerer", 6, 150, 10, 3)
        s = fightToEnd(s)
        val outcome = s.life.skirmish!!.outcome
        assertTrue(outcome == SkirmishOutcome.DEFEAT || outcome == SkirmishOutcome.CAPTURED)
        assertTrue(s.life.wound >= 30)
        assertTrue(LifeEngine.dexterity(s) < LifeEngine.dexterity(s.copy(life = s.life.copy(wound = 0))))
        s = SkirmishEngine.close(s).state
        if (outcome == SkirmishOutcome.CAPTURED) {
            assertTrue(s.life.captive)
            assertEquals(40, s.life.purse)
            assertNotNull(LifeEngine.travelBlocker(s, "gm_eschenhof"))
            repeat(3) { s = GameEngine.advanceDay(s).state }
            assertFalse(s.life.captive)
        }
        val wound = s.life.wound
        s = GameEngine.advanceDay(s).state
        assertTrue(s.life.wound < wound || wound == 0)
        assertEquals(s, rt(s))
    }

    @Test fun ransomFreesImmediately() {
        var s = start(CompanionChoice.NONE).let { it.copy(life = it.life.copy(purse = 200, captiveUntil = 5, captor = "Räuber", place = LifePlace.AWAY)) }
        val price = SkirmishEngine.ransomPrice(s)
        s = SkirmishEngine.ransom(s).state
        assertFalse(s.life.captive)
        assertEquals(200 - price, s.life.purse)
    }

    @Test fun duelIsNonLethalAndRemembered() {
        var s = start(sword = 85).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        val captain = s.life.npcs.first { it.role == NpcRole.MERCENARY_CAPTAIN && it.homeProvinceId == s.life.provinceId }
        s = befriend(s, captain.id, trust = 20, affinity = 20)
        val r = DialogueEngine.choose(s, captain.id, "duel")
        assertEquals(DialogueEngine.Outcome.SUCCESS, r.outcome)
        s = fightToEnd(r.state, SkirmishAction.ATTACK)
        assertNotEquals(SkirmishOutcome.CAPTURED, s.life.skirmish!!.outcome)
        assertTrue(s.life.npc(captain.id)!!.memories.any { it.key.startsWith("duel_") })
    }

    @Test fun ambushesHappenOnDangerousRoadsAndTheJourneyContinues() {
        var s = start(CompanionChoice.NONE).let { it.copy(life = it.life.copy(purse = 300)) }
        assertTrue(LifeEngine.ambushChance(s, "as_freistatt") > LifeEngine.ambushChance(s, "gm_eschenhof") - 1)
        var ambushed = false
        for (i in 0 until 20) {
            val t = LifeEngine.travel(s.copy(life = s.life.copy(nextId = s.life.nextId + i)), "al_aldenburg").state
            if (t.life.skirmish != null) {
                ambushed = true
                assertEquals("al_aldenburg", t.life.skirmish!!.travelTo)
                var f = fightToEnd(t)
                if (f.life.skirmish!!.outcome != SkirmishOutcome.CAPTURED) {
                    f = SkirmishEngine.close(f).state
                    assertEquals("al_aldenburg", f.life.provinceId)
                    assertTrue(f.day > s.day)
                }
                break
            }
        }
        assertTrue(ambushed)
    }

    @Test fun claimingTheKeepHandsTheFollowingOverToTheRealm() {
        var s = start().let { it.copy(life = it.life.copy(purse = 900, renown = 160, timeSlot = TimeSlot.NOON)) }
        s = befriend(s, "companion", trust = 80, affinity = 70)
        s = PartyEngine.invite(s, "companion").state
        val captain = s.life.npcs.first { it.role == NpcRole.MERCENARY_CAPTAIN && it.homeProvinceId == s.life.provinceId }
        s = PartyEngine.hire(s, captain.id).state
        val commanders = s.commanders.size
        s = LifeEngine.claimKeep(s).state
        assertTrue(s.life.landed)
        assertTrue(s.companion.met)
        assertEquals("Mira", s.companion.name)
        assertTrue(s.commanders.any { it.id == COMPANION_COMMANDER_ID })
        assertEquals(commanders + 2, s.commanders.size)
        assertTrue(s.life.party.isEmpty())
        val next = GameEngine.advanceDay(s).state
        assertEquals(next, rt(next))
    }
}
