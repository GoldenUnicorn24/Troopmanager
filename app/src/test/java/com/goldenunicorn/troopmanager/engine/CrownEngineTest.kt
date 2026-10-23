package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class CrownEngineTest {
    private fun game() = GameEngine.newGame("Leon", 25, Species.HALF_ELF, null)

    @Test
    fun newCampaignStartsCrownLayerAndSagaWithoutAutomaticCompanion() {
        var state = game()
        assertTrue(state.crown.initialized)
        assertEquals(42, state.crown.provinces.size)
        assertTrue(state.crown.saga.enabled)
        assertEquals(CrownCatalog.heroines.size, state.crown.saga.heroines.size)
        repeat(8) { state = GameEngine.advanceDay(state).state }
        assertFalse(state.companion.met)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun heroineIsMetOnTheJourneyPassesHerTrialAndBecomesFieldMarshal() {
        var state = game()
        state = HeroineSagaEngine.travel(state, "alina").state
        assertEquals(HeroineStage.TRAVELING, state.crown.heroine("alina")!!.stage)
        repeat(2) { state = GameEngine.advanceDay(state).state }
        val scene = state.crown.saga.pendingScene
        assertNotNull(scene)
        state = HeroineSagaEngine.choose(state, "fight").state
        assertEquals(HeroineStage.MET, state.crown.heroine("alina")!!.stage)
        state = HeroineSagaEngine.startTrial(state, "alina").state
        assertFalse(HeroineSagaEngine.trialProgress(state, "alina").done)
        assertEquals(state, HeroineSagaEngine.recruit(state, "alina", true).state)
        state = CrownEngine.buy(state, "gm_eschenhof").state
        assertTrue(HeroineSagaEngine.trialProgress(state, "alina").done)
        state = HeroineSagaEngine.recruit(state, "alina", true).state
        assertTrue(state.companion.met)
        assertEquals("Alina", state.companion.name)
        assertEquals("alina", state.crown.saga.companionId)
        assertTrue(state.commanders.any { it.id == COMPANION_COMMANDER_ID })
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun secondHeroineServesAsIndependentGeneral() {
        var state = HeroineSagaEngine.recruitImmediately(game(), "alina")
        state = HeroineSagaEngine.recruitImmediately(state, "liang")
        assertEquals(HeroineStage.GENERAL, state.crown.heroine("liang")!!.stage)
        val general = state.commanders.first { it.id == HeroineSagaEngine.generalId("liang") }
        assertEquals(Culture.WALL, general.culture)
        assertEquals("Alina", state.companion.name)
        SaveCodec.decode(SaveCodec.encode(state))
    }

    @Test
    fun buyingAndConqueringProvincesGrowsTerritoryPeopleAndIncome() {
        var state = game()
        val territory = state.realm.territory
        val goldBefore = EconomyEngine.production(state).net.gold
        state = CrownEngine.buy(state, "gm_eschenhof").state
        assertEquals(ProvinceOwner.PLAYER, CrownEngine.province(state, "gm_eschenhof")!!.owner)
        assertEquals(territory + 1, state.realm.territory)
        assertTrue(CrownEngine.provinceYield(state, ResourceKind.FOOD) > 0)
        assertTrue(EconomyEngine.production(state).net.gold > goldBefore)

        val preview = CrownEngine.preview(state, "gm_rabenfurt", 100)!!
        assertTrue(preview.ratio > 1.0)
        val army = state.armySize
        state = CrownEngine.conquer(state, "gm_rabenfurt", 100).state
        assertEquals(ProvinceOwner.PLAYER, CrownEngine.province(state, "gm_rabenfurt")!!.owner)
        assertTrue(state.armySize < army)
        assertEquals(1, state.crown.campaignVictories)
        assertTrue(state.crown.reports.first().victory)
        SaveCodec.decode(SaveCodec.encode(state))
    }

    @Test
    fun unreachableLandsCannotBeAttackedAndCampaignsAreLimitedPerDay() {
        var state = game()
        val far = state.crown.provinces.first { it.land == CrownLand.ASCHESTEPPE && it.owner == ProvinceOwner.HORDE }
        assertEquals(state, CrownEngine.conquer(state, far.id, 100).state)
        state = CrownEngine.conquer(state, "gm_rabenfurt", 50).state
        state = CrownEngine.conquer(state, "gm_kraehenstein", 50).state
        assertEquals(0, CrownEngine.campaignsLeft(state))
        assertEquals(state, CrownEngine.conquer(state, "gm_moorwacht", 50).state)
    }

    @Test
    fun levyMercenariesAndMassTrainingRaiseLargeArmiesQuickly() {
        var state = game().let { it.copy(resources = it.resources.copy(gold = 19000), renown = 500) }
        state = CrownEngine.buy(state, "gm_eschenhof").state
        state = CrownEngine.buy(state, "gm_rabenfurt").state
        val recruits = state.population.totalRecruits
        state = CrownEngine.levy(state).state
        assertTrue(state.population.totalRecruits > recruits)
        assertTrue(CrownEngine.levyCooldown(state) > 0)

        val army = state.armySize
        state = CrownEngine.hire(state, "graue_kompanie").state
        assertEquals(army + 500, state.armySize)
        assertEquals(1, state.crown.mercenaries.size)
        state = GameEngine.advanceDay(state).state
        assertTrue(state.crown.lastMercenaryUpkeep > 0)
        SaveCodec.decode(SaveCodec.encode(state))

        val queue = state.trainingQueue.sumOf { it.amount }
        state = CrownEngine.trainAll(state, mapOf(Culture.HUMAN to UnitType.HUMAN_SWORD)).state
        assertTrue(state.trainingQueue.sumOf { it.amount } > queue)
    }

    @Test
    fun warEconomyHalvesTraining() {
        var state = game()
        val normal = GameEngine.trainingDays(state, UnitType.KNIGHT)
        state = CrownEngine.toggleEdict(state, CrownEdict.WAR_ECONOMY).state
        assertTrue(CrownEngine.has(state, CrownEdict.WAR_ECONOMY))
        assertTrue(GameEngine.trainingDays(state, UnitType.KNIGHT) < normal)
        assertEquals(state, CrownEngine.toggleEdict(state, CrownEdict.CONSCRIPTION).state)
    }

    @Test
    fun tradeRoutesEarnGoldAndPartnersUnlockWithRenown() {
        var state = game()
        assertTrue(state.crown.partner("saltmere")!!.unlocked)
        assertFalse(state.crown.partner("khazrim")!!.unlocked)
        state = CrownEngine.openRoute(state, "saltmere").state
        assertEquals(1, state.crown.partner("saltmere")!!.routes)
        state = state.copy(renown = 200)
        state = GameEngine.advanceDay(state).state
        assertTrue(state.crown.partner("khazrim")!!.unlocked)
        assertTrue(state.crown.partner("saltmere")!!.totalProfit > 0)
        val swords = state.militaryStock.swords
        state = state.copy(resources = state.resources.copy(gold = 20000))
        state = CrownEngine.buyGoods(state, "khazrim").state
        assertTrue(state.militaryStock.swords >= swords + 800)
    }

    @Test
    fun duelsResolveAndPayTournamentRewards() {
        var state = game()
        state = CombatArtsEngine.tournament(state).state
        assertNotNull(state.crown.duel)
        var guard = 0
        while (state.crown.duel?.finished == false && guard++ < 20) {
            val intent = state.crown.duel!!.intendedStance
            val answer = when (intent) { DuelStance.ATTACK -> DuelStance.DEFEND; DuelStance.DEFEND -> DuelStance.FEINT; else -> DuelStance.ATTACK }
            state = CombatArtsEngine.move(state, answer).state
        }
        assertTrue(state.crown.duel!!.finished)
        assertTrue(state.crown.duel!!.won)
        val gold = state.resources.gold
        state = CombatArtsEngine.finish(state).state
        assertNull(state.crown.duel)
        assertEquals(1, state.crown.arts.tournamentWins)
        assertTrue(state.resources.gold > gold)
        SaveCodec.decode(SaveCodec.encode(state))
    }

    @Test
    fun trainingCombatArtsRaisesSkillsOncePerDay() {
        var state = game()
        val sword = state.player.sword
        repeat(3) {
            state = CombatArtsEngine.train(state, CombatArt.BLADE).state
            state = state.copy(day = state.day + 1)
        }
        assertTrue(state.crown.arts.level(CombatArt.BLADE) >= 1)
        assertTrue(state.player.sword > sword)
        val trained = CombatArtsEngine.train(state, CombatArt.SHIELD).state
        assertEquals(trained, CombatArtsEngine.train(trained, CombatArt.SHIELD).state)
    }

    @Test
    fun companionStoryRespectsRomanceSettingsAndConsent() {
        var state = HeroineSagaEngine.recruitImmediately(game(), "seraphine")
        state = state.copy(
            companion = state.companion.copy(trust = 70, respect = 70, affection = 70),
            crown = state.crown.copy(saga = state.crown.saga.copy(chapter = 3, chapterStartDay = state.day - 5)),
        )
        state = HeroineSagaEngine.tick(state)
        assertEquals("chapter_3", state.crown.saga.pendingScene?.key)
        // Friendship path ends the romantic chapters without touching romance at all.
        val oath = HeroineSagaEngine.choose(state, "oath").state
        assertEquals(HeroineSagaEngine.FRIENDSHIP_COMPLETE, oath.crown.saga.chapter)
        assertEquals(RomanceStage.NONE, oath.relationship.romanceStage)
        // Confessing uses the existing consent-gated relationship engine.
        val confessed = HeroineSagaEngine.choose(state, "feelings").state
        assertEquals(RomanceStage.INTEREST, confessed.relationship.romanceStage)
        assertEquals(4, confessed.crown.saga.chapter)
        // A companion who only wants friendship is never pushed.
        val boundary = state.copy(relationship = state.relationship.copy(consent = state.relationship.consent.copy(romanceAllowed = false)))
        val refused = HeroineSagaEngine.choose(boundary, "feelings").state
        assertEquals(RomanceStage.NONE, refused.relationship.romanceStage)
        assertEquals(3, refused.crown.saga.chapter)
    }

    @Test
    fun imperialTitlesNeedProvincesArmyAndQueen() {
        var state = game()
        state = state.copy(crown = state.crown.withProvinces(state.crown.provinces.map { it.copy(owner = ProvinceOwner.PLAYER) }))
        state = CrownEngine.addSoldiers(state, UnitType.HUMAN_SWORD, 120_000, 20, 80)
        assertEquals("Großkönig", CrownEngine.imperialTitle(state))
        state = state.copy(relationship = state.relationship.copy(romanceStage = RomanceStage.CO_RULERS))
        assertEquals("Kaiser der Kronlande", CrownEngine.imperialTitle(state))
    }

    @Test
    fun developerCheatsNeedUnlockAndAreCounted() {
        var state = game()
        assertEquals(state, DevCheatEngine.apply(state, DevCheatEngine.Cheat.RICHES).state)
        state = DevCheatEngine.unlock(state, true)
        state = DevCheatEngine.apply(state, DevCheatEngine.Cheat.RICHES).state
        assertTrue(state.resources.gold >= 250_000)
        state = DevCheatEngine.apply(state, DevCheatEngine.Cheat.ARMY).state
        state = DevCheatEngine.apply(state, DevCheatEngine.Cheat.HEROINES).state
        state = DevCheatEngine.apply(state, DevCheatEngine.Cheat.CONQUER_ALL).state
        assertEquals(42, state.crown.ownedProvinces.size)
        assertEquals(4, state.crown.devCheatsUsed)
        assertTrue(state.crown.saga.heroines.all { it.stage == HeroineStage.COMPANION || it.stage == HeroineStage.GENERAL })
        repeat(3) { state = GameEngine.advanceDay(state).state }
        SaveCodec.decode(SaveCodec.encode(state))
    }
}
