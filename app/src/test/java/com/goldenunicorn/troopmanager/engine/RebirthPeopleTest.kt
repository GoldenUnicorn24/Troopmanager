package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Milestone 3: people among people, travellers on the move, the companion's own life, the barracks. */
class RebirthPeopleTest {
    private fun start(choice: CompanionChoice = CompanionChoice.FULL): GameState = LifeEngine.newGame(LifeEngine.StartConfig(
        mode = GameMode.FREE_LIFE, name = "Leon", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
        background = Background.VILLAGER, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
        attributes = GameEngine.StartingAttributes(sword = 45, bow = 40, riding = 40, leadership = 40, tactics = 30, diplomacy = 70),
        companionChoice = choice, companionDraft = NpcEngine.CompanionDraft(name = "Mira", age = 24),
    ))

    private fun rt(s: GameState) = SaveCodec.decode(SaveCodec.encode(s))

    @Test fun everyTownHasAWebOfFriendshipsRivalriesAndCouples() {
        val s = NpcSocialEngine.ensureBonds(start())
        assertTrue(s.life.bonds.size >= Towns.all.size * 2)
        assertTrue(s.life.bonds.any { it.kind == BondKind.COUPLE })
        assertTrue(s.life.bonds.any { it.kind == BondKind.RIVALS })
        s.life.bonds.filter { it.kind == BondKind.COUPLE }.forEach { b ->
            assertTrue(NpcSocialEngine.mutual(s.life.npc(b.a)!!, s.life.npc(b.b)!!))
        }
        // Deterministic and persistent.
        assertEquals(s.life.bonds, NpcSocialEngine.ensureBonds(start()).life.bonds)
        assertEquals(s, rt(s))
    }

    @Test fun kindnessAndViolenceRippleThroughFriendsAndRivals() {
        var s = NpcSocialEngine.ensureBonds(start())
        val friends = s.life.bonds.first { it.kind == BondKind.FRIENDS }
        val rivals = s.life.bonds.first { it.kind == BondKind.RIVALS }
        val friendBefore = s.life.npc(friends.b)!!.relation.affinity
        s = NpcSocialEngine.ripple(s, friends.a, 15)
        assertEquals(friendBefore + 5, s.life.npc(friends.b)!!.relation.affinity)
        val rivalBefore = s.life.npc(rivals.b)!!.relation.affinity
        s = NpcSocialEngine.ripple(s, rivals.a, -12)
        assertEquals(rivalBefore + 3, s.life.npc(rivals.b)!!.relation.affinity)
    }

    @Test fun flirtingWithSomeoneSpokenForMakesTheirPartnerJealous() {
        var s = NpcSocialEngine.ensureBonds(start(CompanionChoice.NONE)).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.EVENING, place = LifePlace.TAVERN)) }
        val couple = s.life.bonds.first { b -> b.kind == BondKind.COUPLE && listOf(b.a, b.b).any { Gender.MALE in s.life.npc(it)!!.attractedTo } }
        val target = listOf(couple.a, couple.b).first { Gender.MALE in s.life.npc(it)!!.attractedTo }
        val partner = couple.other(target)
        val t = s.life.npc(target)!!
        s = LifeEngine.updateNpc(s, t.copy(relation = t.relation.copy(stage = SocialStage.ACQUAINTANCE, attraction = 60, affinity = 50, trust = 40, timesMet = 3)))
        val before = s.life.npc(partner)!!.relation
        var flirted = false
        repeat(6) {
            if (flirted) return@repeat
            val r = DialogueEngine.choose(s.copy(life = s.life.copy(npcs = s.life.npcs.map { it.copy(relation = it.relation.copy(topicsThisSlot = emptyList())) })), target, "flirt")
            if (r.outcome == DialogueEngine.Outcome.SUCCESS) { s = r.state; flirted = true } else s = s.copy(day = s.day + 1)
        }
        assertTrue(flirted)
        val after = s.life.npc(partner)!!
        assertTrue(after.relation.affinity < before.affinity)
        assertTrue(after.relation.rivalry > before.rivalry)
        assertTrue(after.remembers("jealous_$target"))
        // Being spoken for makes a kiss harder.
        assertTrue(NpcSocialEngine.loyaltyPenalty(s, s.life.npc(target)!!) > 0)
    }

    @Test fun travellersMoveBetweenTownsAndBondsChangeOverTime() {
        var s = start(CompanionChoice.NONE)
        val homes = s.life.npcs.filter { it.role == NpcRole.TRAVELER }.associate { it.id to it.homeProvinceId }
        repeat(30) { s = GameEngine.advanceDay(s).state }
        val moved = s.life.npcs.count { it.role == NpcRole.TRAVELER && homes[it.id] != it.homeProvinceId }
        assertTrue("travellers moved: $moved", moved > 0)
        assertTrue(s.life.news.any { it.severity == 0 })
        assertEquals(s, rt(s))
    }

    @Test fun anUnvisitedCompanionMovesOnAndIsTalkedAbout() {
        var s = start()
        val home = s.life.companion!!.homeProvinceId
        repeat(12) { s = GameEngine.advanceDay(s).state }
        val mira = s.life.companion!!
        assertNotEquals(home, mira.homeProvinceId)
        assertTrue(s.life.news.any { it.text.contains(mira.name) })
        // She can still be found where people say she went.
        assertTrue(Towns.byProvince(mira.homeProvinceId) != null)
    }

    @Test fun aFriendlyCompanionComesLookingForThePlayer() {
        var s = start()
        val mira = s.life.companion!!
        s = LifeEngine.updateNpc(s, mira.copy(homeProvinceId = "al_aldenburg", relation = mira.relation.copy(stage = SocialStage.FRIEND, affinity = 60, trust = 50, timesMet = 4, lastTalkDay = 1)))
        var came = false
        repeat(15) { if (!came) { s = GameEngine.advanceDay(s).state; came = s.life.companion!!.homeProvinceId == s.life.provinceId } }
        assertTrue(came)
        assertTrue(s.life.companion!!.memories.any { it.key.startsWith("sought_") })
    }

    @Test fun trainingRaisesAttributesWithDiminishingReturnsAndALimit() {
        var s = BarracksEngine.enter(start().let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING, purse = 100)) }).state
        assertNotNull(BarracksEngine.sergeant(s))
        assertTrue(BarracksEngine.placements(s).any { it.sergeant })
        val sword = s.player.sword
        s = BarracksEngine.train(s, BarracksEngine.Station.DUMMIES).state
        assertEquals(sword + BarracksEngine.gain(sword), s.player.sword)
        assertEquals(100 - BarracksEngine.TRAINING_PRICE, s.life.purse)
        s = BarracksEngine.train(s, BarracksEngine.Station.BUTTS).state
        assertNotNull(BarracksEngine.trainBlocker(s, BarracksEngine.Station.DUMMIES))
        assertEquals(2, BarracksEngine.gain(40))
        assertEquals(1, BarracksEngine.gain(60))
        assertEquals(0, BarracksEngine.gain(97))
        BarracksEngine.layout.fronts.values.forEach { assertTrue(TavernEngine.path(BarracksEngine.layout.entry, it, l = BarracksEngine.layout).isNotEmpty() || it == BarracksEngine.layout.entry) }
        assertEquals(s, rt(s))
    }

    @Test fun theTownGuardPaysAndPromotes() {
        var s = BarracksEngine.enter(start().let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING, purse = 10)) }).state
        s = BarracksEngine.enlist(s).state
        assertTrue(s.life.enlisted)
        assertEquals(CareerRank.RECRUIT, s.life.career)
        // Training is free for guards.
        assertEquals(10, BarracksEngine.train(s, BarracksEngine.Station.DUMMIES).state.life.purse)
        var shifts = 0
        repeat(12) {
            s = s.copy(life = s.life.copy(timeSlot = TimeSlot.MORNING, wound = 0))
            val r = BarracksEngine.guardDuty(s)
            s = r.state
            if (s.life.skirmish != null) {
                var g = 0
                while (s.life.skirmish?.outcome == null && g++ < 20) s = SkirmishEngine.act(s, SkirmishAction.ATTACK).state
                s = SkirmishEngine.close(s).state
                if (s.life.captive) s = SkirmishEngine.free(s)
            }
            if (s.life.lastGuardDay == s.day) shifts++
            s = GameEngine.advanceDay(s).state
        }
        assertTrue(shifts >= 10)
        assertTrue(s.life.renown >= 10)
        s = s.copy(life = s.life.copy(renown = 30))
        s = LifeEngine.addRenown(s, 1)
        assertEquals(CareerRank.SOLDIER, s.life.career)
    }

    @Test fun roleTopicsTeachAndReportTheWorld() {
        var s = NpcSocialEngine.ensureBonds(BarracksEngine.ensureSergeants(start())).let { it.copy(life = it.life.copy(timeSlot = TimeSlot.MORNING, place = LifePlace.BARRACKS)) }
        val sarge = BarracksEngine.sergeant(s)!!
        s = LifeEngine.updateNpc(s, sarge.copy(relation = sarge.relation.copy(stage = SocialStage.ACQUAINTANCE, affinity = 40, trust = 30, timesMet = 2)))
        val tactics = s.player.tactics
        val r = DialogueEngine.choose(s, sarge.id, "tactics_talk")
        if (r.outcome == DialogueEngine.Outcome.SUCCESS) {
            assertEquals(tactics + 1, r.state.player.tactics)
            assertTrue(DialogueEngine.options(r.state, sarge.id).none { it.topic.id == "tactics_talk" })
        }
        val innkeeper = s.life.npcs.first { it.role == NpcRole.INNKEEPER && it.homeProvinceId == s.life.provinceId }
        val gossip = DialogueEngine.choose(DialogueEngine.open(s, innkeeper.id).state.let { st ->
            LifeEngine.updateNpc(st, st.life.npc(innkeeper.id)!!.copy(relation = st.life.npc(innkeeper.id)!!.relation.copy(stage = SocialStage.ACQUAINTANCE)))
        }, innkeeper.id, "ask_people")
        assertEquals(DialogueEngine.Outcome.SUCCESS, gossip.outcome)
        assertFalse(gossip.npcLine.contains("{"))
    }
}
