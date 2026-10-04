package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class WarExperienceIntegrationTest {
    private fun state() = GameEngine.newGame("Friedensrat", 30, Species.HUMAN, null)
    private fun war() = DiplomacyEngine.declareWar(state(), "ash_covenant").state

    @Test fun regionGoalsRequireKnowledgeAndActualOwnership() {
        val base = war()
        val region = base.world.places.first { it.ownerId == "ash_covenant" }
        val unknown = base.copy(world = base.world.copy(knowledge = base.world.knowledge.map {
            if (it.factionId == PLAYER_FACTION) it.copy(exploredRegions = it.exploredRegions - region.id) else it }))
        assertEquals(unknown, WarGoalsEngine.set(unknown, "ash_covenant", WarGoal.SECURE_REGION, region.id).state)
        val oldKnowledge = unknown.world.knowledgeFor(PLAYER_FACTION)
        val knowledge = oldKnowledge.copy(exploredRegions = (oldKnowledge.exploredRegions + region.id).distinct())
        val known = unknown.copy(world = unknown.world.copy(knowledge = unknown.world.knowledge.filterNot { it.factionId == PLAYER_FACTION } + knowledge))
        val chosen = WarGoalsEngine.set(known, "ash_covenant", WarGoal.SECURE_REGION, region.id).state
        assertEquals(WarGoal.SECURE_REGION, DiplomacyEngine.relation(chosen, PLAYER_FACTION, "ash_covenant").playerWarGoal)
        assertTrue(WarGoalsEngine.progress(chosen, "ash_covenant").contains("noch nicht"))
        val captured = chosen.copy(world = chosen.world.copy(places = chosen.world.places.map { if (it.id == region.id) it.copy(ownerId = PLAYER_FACTION) else it }))
        assertTrue(WarGoalsEngine.progress(captured, "ash_covenant").contains("gesichert"))
        assertEquals(chosen, SaveCodec.decode(SaveCodec.encode(chosen)))
    }

    @Test fun realCasualtiesAndExhaustionChangePeaceNegotiations() {
        val base = war()
        val battle = BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300,
            enemyFactionId = "ash_covenant").state.battleSession!!.copy(status = BattleStatus.VICTORY, outcomeGrade = BattleOutcomeGrade.COSTLY_VICTORY,
            fronts = BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300).state.battleSession!!.fronts.map { it.copy(enemySoldiers = 0) },
            casualties = CasualtyReport(dead = 10, wounded = 20))
        val after = WarGoalsEngine.recordBattle(base, battle)
        val relation = DiplomacyEngine.relation(after, PLAYER_FACTION, "ash_covenant")
        assertEquals(300, relation.warLosses["ash_covenant"])
        assertEquals(30, relation.warLosses[PLAYER_FACTION])
        assertTrue(WarGoalsEngine.peacePressure(after, "ash_covenant") > WarGoalsEngine.peacePressure(base, "ash_covenant"))
        val offerBefore = DiplomacyEngine.propose(base, "ash_covenant", TreatyKind.PEACE).state.diplomacy.proposals.last()
        val offerAfter = DiplomacyEngine.propose(after, "ash_covenant", TreatyKind.PEACE).state.diplomacy.proposals.last()
        assertTrue(offerAfter.offered.gold <= offerBefore.offered.gold)
        assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
    }

    @Test fun costlyVictoryHasDifferentPoliticalConsequencesFromDecisiveVictory() {
        val base = war().copy(society = war().society.copy(warExhaustion = 20))
        val battle = BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY, seed = 90).state.battleSession!!.copy(status = BattleStatus.VICTORY)
        val costly = BattleConsequencesEngine.apply(base, battle.copy(outcomeGrade = BattleOutcomeGrade.COSTLY_VICTORY))
        val decisive = BattleConsequencesEngine.apply(base, battle.copy(outcomeGrade = BattleOutcomeGrade.DECISIVE_VICTORY))
        assertEquals(32, costly.society.warExhaustion)
        assertEquals(17, decisive.society.warExhaustion)
        assertTrue(costly.city.satisfaction < decisive.city.satisfaction)
        assertTrue(costly.society.groups.first { it.kind == PoliticalGroupKind.MILITARY }.loyalty < decisive.society.groups.first { it.kind == PoliticalGroupKind.MILITARY }.loyalty)
    }

    @Test fun anAllyNeedsAnAgreementForOutpostSupplies() {
        val initial = state()
        val post = FrontierOutpost(1, "village", "Brückenwacht", 2, stores = 600)
        val friendly = DiplomacyEngine.changeRelation(initial, PLAYER_FACTION, "copper_league", 100, 60, "Freundliche Beziehungen")
            .copy(frontier = initial.frontier.copy(outposts = listOf(post), nextOutpostId = 2))
        val army = WorldArmy("ally", "copper_league", "Bündnisheer", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100)), "village", supplyFood = 0)
        assertEquals(600, OutpostEngine.interact(friendly, army).state.frontier.outposts.single().stores)
        val allied = friendly.copy(diplomacy = friendly.diplomacy.copy(treaties = friendly.diplomacy.treaties +
            Treaty("defence", TreatyKind.DEFENSIVE_ALLIANCE, PLAYER_FACTION, army.factionId, friendly.day, friendly.day + 60)))
        val result = OutpostEngine.interact(allied, army)
        assertEquals(300, result.army.supplyFood)
        assertEquals(300, result.state.frontier.outposts.single().stores)
    }

    @Test fun closingAGateDoesNotRemoveEnemiesAlreadyInside() {
        val base = BattleEngine.start(state(), EnemyType.ORC, Tactic.FORTIFY, seed = 90).state
        val battle = base.battleSession!!.copy(fronts = base.battleSession!!.fronts.map { it.copy(enemyDistance = 0) },
            siegeDevices = emptyList(), segments = base.battleSession!!.segments.map { if (it.section == BattleSection.CENTER)
                it.copy(gateOpen = true, contactState = BattleContactState.BREACHED) else it })
        val after = SiegeEngine.advance(base, battle, BattleDecision.HOLD_GATE, BattleSection.CENTER).battle
        assertFalse(after.segment(BattleSection.CENTER)!!.gateOpen)
        assertTrue(after.segment(BattleSection.CENTER)!!.contactState.allowsMelee)
        assertFalse(after.segment(BattleSection.LEFT)!!.contactState.allowsMelee)
    }

    @Test fun migrationLeavesAnAffordableReactionForLegacyEvents() {
        val base = BattleEngine.start(state(), EnemyType.ORC, Tactic.FORTIFY, seed = 90).state
        val legacy = base.battleSession!!.copy(combatVersion = 1, battleArrowsRemaining = -1,
            pendingEvent = BattleEvent("Alter Kontakt", "Alte zeitbasierte Reaktion", BattleSection.CENTER, listOf(BattleDecision.CAVALRY_CHARGE)), commandPoints = 0)
        val migrated = BattleStateEngine.initialize(base, legacy)
        assertTrue(BattleDecision.HOLD in migrated.pendingEvent!!.options)
        assertFalse(BattleDecision.CAVALRY_CHARGE in migrated.pendingEvent!!.options)
        assertEquals(migrated, BattleStateEngine.initialize(base, migrated))
    }

    @Test fun aStoryFamilyDoesNotRepeatDuringItsOwnCooldown() {
        val initial = state().copy(day = 28, society = state().society.copy(politicalLoyalty = 20,
            story = StoryState(familyCooldowns = mapOf(StoryKind.LOYALTY_CRISIS to 100))))
        assertNotEquals(StoryKind.LOYALTY_CRISIS, StoryDirector.tick(initial).society.story.pending?.kind)
    }

    @Test fun highKingGoalsAskForCoalitionsAndRealFrontierGarrisons() {
        val late = state().copy(completedRealm = true)
        val tasks = QuestJournalEngine.tasks(late)
        assertTrue(tasks.any { it.id == "endgame:coalition" })
        assertTrue(tasks.any { it.id == "endgame:frontier" })
        assertEquals(tasks, QuestJournalEngine.tasks(late))
    }

    @Test fun localFortificationSurvivesARoundedGlobalWallAverageOfZero() {
        val initial = state().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 300), ArmyUnitPool(UnitType.HUMAN_SWORD, 100)))
        var protectedLosses = 0
        var uncoveredLosses = 0
        repeat(10) { seed ->
            val base = BattleEngine.start(initial, EnemyType.ORC, Tactic.HOLD,
                listOf(BattleDeployment(null, BattleSection.LEFT, listOf(UnitAllocation(UnitType.HUMAN_ARCHER, 300), UnitAllocation(UnitType.HUMAN_SWORD, 100)))),
                seed = seed, enemyStrength = 900, enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 900)), enemyFortification = 100).state
            val battle = base.battleSession!!.copy(enemyFortification = 0, siegeDevices = emptyList(), segments = base.battleSession!!.segments.map {
                if (it.section == BattleSection.LEFT) it.copy(integrity = 2, gateIntegrity = 2) else it.copy(integrity = 0, gateIntegrity = 0, breachWidth = 80) })
            assertTrue(BattleEngine.canOrder(battle, BattleDecision.SCALE_WALL, BattleSection.LEFT))
            val covered = SiegeEngine.advance(base, battle, null, null)
            val uncovered = covered.copy(battle = covered.battle.copy(segments = covered.battle.segments.map { it.copy(cover = 0.0) }))
            protectedLosses += BattleResolutionEngine.resolve(base, covered.battle, covered, null, null).report.enemyLosses
            uncoveredLosses += BattleResolutionEngine.resolve(base, uncovered.battle, uncovered, null, null).report.enemyLosses
        }
        assertTrue("A remaining segment retains cover after the integer global average reaches zero", protectedLosses < uncoveredLosses)
    }
}
