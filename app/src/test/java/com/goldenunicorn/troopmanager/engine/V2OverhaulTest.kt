package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V2OverhaulTest {
    private fun game() = GameEngine.newGame("Leon", 25, Species.HALF_ELF, null)

    private fun withArmy(state: GameState, soldiers: Int): GameState =
        CrownEngine.addSoldiers(state, UnitType.HUMAN_SWORD, soldiers, 30, 80)

    // ------------------------------------------------------------ navigation

    @Test
    fun everySpotPointsAtAnExistingTabAndSubView() {
        Spot.entries.forEach { spot ->
            val tabs = Navigation.tabs.getValue(spot.area)
            assertTrue("$spot tab", spot.tab in tabs.indices)
            assertTrue("At most four tabs in ${spot.area}", tabs.size <= 4)
            val subs = tabs[spot.tab].subs
            if (subs.isEmpty()) assertEquals("$spot has no sub views", 0, spot.sub) else assertTrue("$spot sub", spot.sub in subs.indices)
            assertTrue(Navigation.label(spot).isNotBlank())
        }
        // Every tab and sub view is reachable by exactly one spot.
        Navigation.tabs.forEach { (area, tabs) ->
            tabs.forEachIndexed { t, tab ->
                val count = if (tab.subs.isEmpty()) 1 else tab.subs.size
                (0 until count).forEach { sub -> assertEquals("$area/$t/$sub", 1, Spot.entries.count { it.area == area && it.tab == t && it.sub == sub }) }
            }
        }
        GameDestination.entries.forEach { assertNotNull(Navigation.spotFor(it)) }
    }

    @Test
    fun advisorHintsJumpToTheMatchingPlace() {
        var state = game().copy(crown = game().crown.copy(arts = game().crown.arts.copy(lastTrainingDay = -1)))
        val training = AdvisorEngine.advice(state).first { it.title == "Training möglich" }
        assertEquals(Spot.ARTS, training.spot)
        state = CrownEngine.acquire(state, "gm_eschenhof", "Kauf")
        val levy = AdvisorEngine.advice(state).firstOrNull { it.title == "Heerbann bereit" }
        if (levy != null) assertEquals(Spot.LEVY, levy.spot)
    }

    // ------------------------------------------------------------ inbox

    @Test
    fun inboxCollectsDecisionsAndAnswersThem() {
        val event = EventEngine.catalogue.first()
        val state = game().copy(pendingRealmEvent = event)
        val item = InboxEngine.items(state).first { it.source == InboxEngine.Source.REALM }
        assertEquals(event.title, item.title)
        assertTrue(item.options.isNotEmpty())
        val answered = InboxEngine.choose(state, item.key, item.options.last().id).state
        assertNull(answered.pendingRealmEvent)
        assertTrue(InboxEngine.items(answered).none { it.key == item.key })
    }

    @Test
    fun ignoredDecisionsSettleThemselvesAfterThreeDays() {
        var state = game().copy(pendingRealmEvent = EventEngine.catalogue.first())
        state = InboxEngine.tick(state)
        val key = InboxEngine.items(state).first { it.source == InboxEngine.Source.REALM }.key
        assertEquals(state.day, state.reign.inboxSince[key])
        state = InboxEngine.tick(state.copy(day = state.day + 2))
        assertNotNull(state.pendingRealmEvent)
        state = InboxEngine.tick(state.copy(day = state.day + 1))
        assertNull(state.pendingRealmEvent)
        assertEquals(1, state.reign.autoSettled)
        assertTrue(state.chronicle.last().title.startsWith("Selbst entschieden"))
        assertFalse(key in state.reign.inboxSince)
    }

    @Test
    fun privateRelationshipMomentsAreOnlyLinkedFromTheInbox() {
        val event = RelationshipEvent("intimacy", "Eine Nacht", "…", adultsOnly = true, consentRequired = true, presentation = "FADE_TO_BLACK")
        val state = game().copy(relationship = game().relationship.copy(pendingEvent = event))
        val item = InboxEngine.items(state).first { it.source == InboxEngine.Source.RELATIONSHIP }
        assertTrue(item.options.isEmpty())
        assertEquals(Spot.PAIR, item.openSpot)
        assertFalse(item.autoSettles)
    }

    // ------------------------------------------------------------ weekly, wonders, feasts

    @Test
    fun everySeventhDayBringsAWeeklyEvent() {
        var state = game().copy(day = 7)
        state = ReignEngine.tick(state)
        val weekly = state.reign.weekly
        assertNotNull(weekly)
        assertTrue(InboxEngine.items(state).any { it.source == InboxEngine.Source.WEEKLY })
        val option = ReignEngine.weeklyOptions(state, weekly!!).last()
        val done = ReignEngine.chooseWeekly(state, option.id).state
        assertTrue(done.reign.weekly!!.resolved)
        assertEquals(done, SaveCodec.decode(SaveCodec.encode(done)))
    }

    @Test
    fun wondersCostResourcesTakeDaysAndUnlockTheirEffect() {
        val rich = game().copy(resources = Resources(200_000, 50_000, 50_000, 50_000, 50_000))
        val started = ReignEngine.startWonder(rich, WonderKind.GRANARY).state
        assertEquals(rich.resources.gold - WonderKind.GRANARY.gold, started.resources.gold)
        assertNotNull(ReignEngine.wonderBlocker(started, WonderKind.CATHEDRAL))
        assertFalse(ReignEngine.has(started, WonderKind.GRANARY))
        val finished = ReignEngine.tick(started.copy(day = started.day + WonderKind.GRANARY.days))
        assertTrue(ReignEngine.has(finished, WonderKind.GRANARY))
        assertEquals(3, ReignEngine.foodStorageFactor(finished))
        assertTrue(finished.crown.celebrations.any { it.kind == CelebrationKind.WONDER })
        assertEquals(finished, SaveCodec.decode(SaveCodec.encode(finished)))
    }

    @Test
    fun feastsRaiseMoraleAndHaveACooldown() {
        val state = withArmy(game(), 2_000).copy(resources = Resources(100_000, 100_000, 1_000, 1_000, 1_000))
        val before = state.armyPools.first { it.type == UnitType.HUMAN_SWORD }.morale
        val feast = ReignEngine.holdFeast(state).state
        assertTrue(feast.resources.gold < state.resources.gold)
        assertTrue(feast.armyPools.first { it.type == UnitType.HUMAN_SWORD }.morale > before.coerceAtMost(85))
        assertEquals(7, ReignEngine.feastCooldown(feast))
        assertSame(feast, ReignEngine.holdFeast(feast).state)
    }

    // ------------------------------------------------------------ tactical battles

    @Test
    fun tacticalBattleHasThreeDecisionsAndASimulation() {
        var state = withArmy(game(), 3_000)
        state = TacticalBattleEngine.start(state, TacticalBattleEngine.Kind.STORM, "Ork-Sturm", EnemyType.ORC, 1_200).state
        var battle = state.battleSession!!
        assertEquals(TacticalBattleEngine.Stage.PLAN, TacticalBattleEngine.stage(battle))
        assertSame(state, TacticalBattleEngine.step(state).state)
        val plans = TacticalBattleEngine.plans(battle)
        assertEquals(4, plans.size)
        assertTrue(TacticalBattleEngine.odds(battle).percent in 3..97)
        state = TacticalBattleEngine.choosePlan(state, plans.first()).state
        battle = state.battleSession!!
        assertEquals(TacticalBattleEngine.Stage.RUNNING, TacticalBattleEngine.stage(battle))
        var decisions = 0
        var guard = 0
        while (state.battleSession!!.isActive && guard++ < 300) {
            val b = state.battleSession!!
            val stage = TacticalBattleEngine.stage(b)
            state = if (stage == TacticalBattleEngine.Stage.MIDDLE || stage == TacticalBattleEngine.Stage.FINAL) {
                decisions++
                val choice = TacticalBattleEngine.choices(state, b).firstOrNull()?.decision ?: BattleDecision.HOLD
                TacticalBattleEngine.decide(state, choice).state
            } else TacticalBattleEngine.step(state).state
        }
        assertFalse(state.battleSession!!.isActive)
        assertTrue("middle and/or final decision offered", decisions in 1..2)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun quickResolveFinishesAnyBattle() {
        var state = withArmy(game(), 5_000)
        state = TacticalBattleEngine.start(state, TacticalBattleEngine.Kind.FIELD, "Feldschlacht", EnemyType.URUK, 2_000).state
        state = TacticalBattleEngine.autoResolve(state)
        assertFalse(state.battleSession!!.isActive)
        assertNotNull(state.battleSession!!.tacticalPlan)
    }

    @Test
    fun aLargerArmyWinsTheBossBattleInsteadOfTimingOut() {
        var state = HeroineSagaEngine.recruitImmediately(withArmy(game(), 9_000), "alina")
        val boss = ActCatalog.act(1)!!.boss
        state = BattleEngine.start(state, boss.enemy, Tactic.HOLD, enemyStrength = boss.strength, enemyExperience = 15, enemyMorale = 85, seed = 7).state
        state = TacticalBattleEngine.autoResolve(state)
        assertEquals(BattleStatus.VICTORY, state.battleSession!!.status)
    }

    // ------------------------------------------------------------ army growth and progress

    @Test
    fun massTrainingUsesEveryAffordableRecruit() {
        val base = game()
        val state = base.copy(resources = base.resources.copy(gold = 1_000_000),
            population = base.population.copy(human = base.population.human + 5_000, humanRecruits = 2_000),
            militaryStock = MilitaryStock(100_000, 100_000, 100_000, 1_000_000, 100_000, 100_000, 10_000, 1_000, 1_000))
        val recruits = ArmyEngine.recruitable(state, Culture.HUMAN)
        assertTrue(recruits > 0)
        val trained = CrownEngine.trainAll(state, mapOf(Culture.HUMAN to UnitType.HUMAN_SWORD)).state
        assertEquals(recruits, trained.trainingQueue.filter { it.type == UnitType.HUMAN_SWORD }.sumOf { it.amount } -
            state.trainingQueue.filter { it.type == UnitType.HUMAN_SWORD }.sumOf { it.amount })
        // With armor for only a hundred soldiers, exactly a hundred start training.
        val scarce = state.copy(militaryStock = state.militaryStock.copy(armor = 100))
        val partial = CrownEngine.trainAll(scarce, mapOf(Culture.HUMAN to UnitType.HUMAN_SWORD)).state
        assertEquals(100, partial.trainingQueue.filter { it.type == UnitType.HUMAN_SWORD }.sumOf { it.amount } -
            state.trainingQueue.filter { it.type == UnitType.HUMAN_SWORD }.sumOf { it.amount })
    }

    @Test
    fun crownProvincesForgeEquipmentEveryDay() {
        var state = game()
        assertEquals(0, ReignEngine.workshopOutput(state).armor)
        state = CrownEngine.acquire(state, "gm_eschenhof", "Kauf")
        val output = ReignEngine.workshopOutput(state)
        assertTrue(output.armor > 0 && output.swords + output.bows + output.spears > 0)
        val next = ReignEngine.tick(state)
        assertEquals(state.militaryStock.armor + output.armor, next.militaryStock.armor)
    }

    @Test
    fun achievementsUnlockOnceWithReward() {
        val state = game().copy(victories = 1)
        val first = AchievementEngine.check(state)
        assertTrue("first_victory" in first.reign.achievements)
        assertTrue(first.resources.gold > state.resources.gold)
        val again = AchievementEngine.check(first)
        assertEquals(first.resources.gold, again.resources.gold)
    }

    @Test
    fun companionGearMakesHerStronger() {
        val met = HeroineSagaEngine.recruitImmediately(game(), "alina").copy(resources = game().resources.copy(gold = 100_000))
        val geared = ReignEngine.upgradeCompanionGear(met).state
        assertEquals(1, geared.reign.companionGear)
        assertTrue(geared.companion.sword > met.companion.sword)
        assertTrue(geared.resources.gold < met.resources.gold)
    }

    @Test
    fun marchEventsRespectCooldownAndNeverRepeatWithinThirtyDays() {
        val keys = (0 until 60).mapNotNull { offset ->
            var s = game().copy(day = 20 + offset, crown = game().crown.copy(campaignVictories = 5))
            s = CrownEngine.acquire(s, "gm_eschenhof", "Kauf")
            val target = s.crown.provinces.first { it.owner != ProvinceOwner.PLAYER && it.garrison > 0 }
            MarchEngine.maybeStart(s.copy(reign = s.reign.copy(lastMarchDay = -99)), target.id, 50)?.state?.crown?.saga?.pendingScene?.key
        }
        assertTrue("events happen sometimes", keys.isNotEmpty())
        assertTrue("there are many different events", keys.toSet().size >= 6)
        val s = game().copy(day = 30, crown = game().crown.copy(campaignVictories = 5), reign = ReignState(lastMarchDay = 27))
        val target = s.crown.provinces.first { it.owner != ProvinceOwner.PLAYER && it.garrison > 0 }
        assertNull(MarchEngine.maybeStart(s, target.id, 50))
    }

    @Test
    fun bribesImproveAKingsAttitude() {
        var state = game().copy(resources = game().resources.copy(gold = 100_000))
        state = RivalEngine.tick(state)
        val rival = state.crown.rivals.firstOrNull() ?: return
        val bribed = ReignEngine.bribe(state, rival.id).state
        assertEquals((rival.attitude + 30).coerceAtMost(100), bribed.crown.rivals.first { it.id == rival.id }.attitude)
        assertEquals(1, bribed.reign.bribes)
    }
}
