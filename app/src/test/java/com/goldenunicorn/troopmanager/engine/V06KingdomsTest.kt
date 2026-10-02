package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V06KingdomsTest {
    private fun game(): GameState = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)
    private fun target(state: GameState): WorldFaction = state.world.factions.first { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }

    private fun friendly(state: GameState, factionId: String): GameState {
        val relation = DiplomacyEngine.relation(state, PLAYER_FACTION, factionId)
        return state.copy(diplomacy = state.diplomacy.copy(relations = state.diplomacy.relations.filterNot {
            it.connects(PLAYER_FACTION, factionId) } + relation.copy(relation = 80, trust = 90, respect = 90,
                atWar = false, warStartedDay = null)))
    }

    @Test fun treatySigningTransfersActualResourcesAndPersistsHistory() {
        val original = game()
        val faction = target(original)
        val before = friendly(original, faction.id)
        val result = DiplomacyEngine.propose(before, faction.id, TreatyKind.NON_AGGRESSION,
            offered = Resources(200, 100, 0, 0, 0)).state
        assertTrue(DiplomacyEngine.hasTreaty(result, PLAYER_FACTION, faction.id, TreatyKind.NON_AGGRESSION))
        assertEquals(before.resources.gold - 200, result.resources.gold)
        assertEquals(before.resources.food - 100, result.resources.food)
        assertEquals(faction.gold + 200, result.world.faction(faction.id)!!.gold)
        assertEquals(faction.food + 100, result.world.faction(faction.id)!!.food)
        assertTrue(DiplomacyEngine.relation(result, PLAYER_FACTION, faction.id).history.isNotEmpty())
        assertEquals(result, SaveCodec.decode(SaveCodec.encode(result)))
        assertEquals(before.armyPools, result.armyPools)
    }

    @Test fun counterOfferDoesNotChargeUntilAcceptedAndCannotBeAcceptedTwice() {
        val original = game()
        val faction = target(original)
        val r = DiplomacyEngine.relation(original, PLAYER_FACTION, faction.id)
        val before = original.copy(player = original.player.copy(diplomacy = 0),
            world = original.world.copy(factions = original.world.factions.map {
                if (it.id == faction.id) it.copy(personality = FactionPersonality.PARANOID) else it }),
            diplomacy = original.diplomacy.copy(relations = original.diplomacy.relations.filterNot {
                it.connects(PLAYER_FACTION, faction.id) } + r.copy(relation = 0, trust = 20, respect = 20,
                    fear = 0, atWar = false, warStartedDay = null)))
        val counter = DiplomacyEngine.propose(before, faction.id, TreatyKind.MILITARY_ACCESS).state
        val proposal = counter.diplomacy.proposals.last()
        assertEquals(ProposalStatus.COUNTER_OFFER, proposal.status)
        assertEquals(before.resources, counter.resources)
        assertTrue(proposal.offered.gold > 0)
        val accepted = DiplomacyEngine.acceptCounter(counter, proposal.id).state
        assertEquals(counter.resources.gold - proposal.offered.gold, accepted.resources.gold)
        assertTrue(DiplomacyEngine.canEnterTerritory(accepted, PLAYER_FACTION, faction.id))
        assertEquals(accepted, DiplomacyEngine.acceptCounter(accepted, proposal.id).state)
    }

    @Test fun treatyBreachMobilizesDefensiveAlliesAndReducesTrust() {
        val original = game()
        val target = target(original)
        val ally = original.world.factions.first { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION && it.id != target.id }
        val before = friendly(original, target.id).let { state -> state.copy(diplomacy = state.diplomacy.copy(treaties = listOf(
            Treaty("pact", TreatyKind.NON_AGGRESSION, PLAYER_FACTION, target.id, state.day, state.day + 60),
            Treaty("defense", TreatyKind.DEFENSIVE_ALLIANCE, target.id, ally.id, state.day, state.day + 60)))) }
        val after = DiplomacyEngine.declareWar(before, target.id).state
        assertTrue(DiplomacyEngine.atWar(after, PLAYER_FACTION, target.id))
        assertTrue(DiplomacyEngine.atWar(after, PLAYER_FACTION, ally.id))
        assertTrue(after.world.faction(target.id)!!.wars.contains(PLAYER_FACTION))
        assertFalse(DiplomacyEngine.hasTreaty(after, PLAYER_FACTION, target.id, TreatyKind.NON_AGGRESSION))
        assertTrue(DiplomacyEngine.relation(after, PLAYER_FACTION, target.id).trust < DiplomacyEngine.relation(before, PLAYER_FACTION, target.id).trust)
    }

    @Test fun weeklyTributeConservesGoldAndDailyTickIsIdempotent() {
        val original = game()
        val faction = target(original)
        val before = original.copy(day = 8, diplomacy = original.diplomacy.copy(lastAiDecisionDay = 8,
            treaties = listOf(Treaty("tribute", TreatyKind.TRIBUTE, PLAYER_FACTION, faction.id, 1, 61,
                PLAYER_FACTION, 50, 1))))
        val after = DiplomacyEngine.tick(before)
        assertEquals(before.resources.gold - 50, after.resources.gold)
        assertEquals(faction.gold + 50, after.world.faction(faction.id)!!.gold)
        assertEquals(after, DiplomacyEngine.tick(after))
    }

    @Test fun spiesRespectTravelTimeAndDestroyExistingStoresWithoutChangingArmyPools() {
        val original = game()
        val faction = target(original)
        val hired = EspionageEngine.recruitAgent(original).state
        val before = EspionageEngine.startMission(hired, hired.espionage.agents.single().id,
            SpyMissionKind.DESTROY_STORES, faction.id).state
        assertEquals(original.resources.gold - 300 - SpyMissionKind.DESTROY_STORES.cost, before.resources.gold)
        val early = EspionageEngine.tick(before.copy(day = before.day + 4), roll = 0.0)
        assertEquals(faction.food, early.world.faction(faction.id)!!.food)
        assertEquals(1, early.espionage.missions.size)
        val completed = EspionageEngine.tick(early.copy(day = before.day + 5), roll = 0.0)
        assertTrue(completed.world.faction(faction.id)!!.food < faction.food)
        assertTrue(completed.espionage.missions.isEmpty())
        assertTrue(completed.espionage.reports.last().success)
        assertEquals(original.armyPools, completed.armyPools)
        assertEquals(completed, EspionageEngine.tick(completed, roll = 0.0))
    }

    @Test fun failedSpyMissionCapturesAgentAndDamagesTrust() {
        val original = game()
        val faction = target(original)
        val hired = EspionageEngine.recruitAgent(original).state
        val before = EspionageEngine.startMission(hired, hired.espionage.agents.single().id,
            SpyMissionKind.DESTROY_STORES, faction.id).state
        val after = EspionageEngine.tick(before.copy(day = before.day + 5), roll = 1.0)
        assertEquals(faction.id, after.espionage.agents.single().capturedByFactionId)
        assertEquals(faction.food, after.world.faction(faction.id)!!.food)
        assertTrue(DiplomacyEngine.relation(after, PLAYER_FACTION, faction.id).trust < DiplomacyEngine.relation(before, PLAYER_FACTION, faction.id).trust)
        assertEquals(after, EspionageEngine.startMission(after, after.espionage.agents.single().id,
            SpyMissionKind.SCOUT_ARMY, faction.id).state)
    }

    @Test fun hungerCrimeAndWarFatigueHaveActualBoundedConsequences() {
        val original = game()
        val faction = target(original)
        val before = DiplomacyEngine.changeRelation(original, PLAYER_FACTION, faction.id, 0, 0, "Krieg", true).copy(day = 7,
            resources = original.resources.copy(food = 0), society = original.society.copy(crime = 80,
                hunger = 80, warExhaustion = 80), city = original.city.copy(security = 10))
        val after = SocietyEngine.tick(before)
        assertTrue(after.resources.gold < before.resources.gold)
        assertTrue(after.city.satisfaction < before.city.satisfaction)
        assertTrue(after.armyPools.first().morale < before.armyPools.first().morale)
        assertEquals(before.armySize, after.armySize)
        assertEquals(after, SocietyEngine.tick(after))
        val relief = SocietyEngine.fundPolicy(after.copy(resources = after.resources.copy(food = 600)), SocietyPolicy.FOOD_RELIEF).state
        assertEquals(300, relief.resources.food)
        assertTrue(relief.society.hunger < after.society.hunger)
    }

    @Test fun unmetPoliticalDemandPenalizesLoyaltyOnce() {
        val original = game()
        val before = original.copy(day = 20, society = original.society.copy(demands = listOf(
            PoliticalDemand("farmers", PoliticalGroupKind.FARMERS, PoliticalDemandKind.FOOD_RELIEF, 1, 20))))
        val after = SocietyEngine.tick(before)
        assertTrue(after.society.demands.isEmpty())
        assertTrue(after.society.groups.first { it.kind == PoliticalGroupKind.FARMERS }.loyalty <
            before.society.groups.first { it.kind == PoliticalGroupKind.FARMERS }.loyalty)
        assertEquals(after, SocietyEngine.tick(after))
    }

    @Test fun migrationCannotConsumeAnotherCulturesMilitaryReservations() {
        val original = game()
        val before = original.copy(population = Population(human = 1000, woodElf = 100, goldElf = 0, wall = 0,
            humanRecruits = 0, woodElfRecruits = 0, goldElfRecruits = 0, wallRecruits = 0),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 1000)), trainingQueue = emptyList(),
            society = original.society.copy(hunger = 80), resources = original.resources.copy(food = 0),
            city = original.city.copy(housingCapacity = 2400), realm = original.realm.copy(buildings = emptyMap()))
        val after = SocietyEngine.tick(before)
        assertEquals(1000, after.population.human)
        assertEquals(92, after.population.woodElf)
        assertEquals(1000, after.armySize)
        WarEngine.validate(after)
    }

    @Test fun refugeeChoiceSchedulesConsequencesWeeksLaterAndPreservesTheArmy() {
        val original = game()
        val event = StoryEvent("story_1", StoryKind.REFUGEES, "Flüchtlinge", "Familien bitten um Schutz", 1, 15, "chain_1")
        val before = original.copy(society = original.society.copy(story = StoryState(pending = event, nextId = 2)))
        val accepted = StoryDirector.resolve(before, event.id, "accept").state
        assertEquals(before.population.human + 80, accepted.population.human)
        assertEquals(before.resources.food - 160, accepted.resources.food)
        assertNull(accepted.society.story.pending)
        assertEquals(15, accepted.society.story.scheduled.single().dueDay)
        val early = StoryDirector.tick(accepted.copy(day = 14))
        assertNull(early.society.story.pending)
        val later = StoryDirector.tick(early.copy(day = 15))
        assertEquals(StoryKind.REFUGEE_HOUSING, later.society.story.pending!!.kind)
        val housed = StoryDirector.resolve(later, later.society.story.pending!!.id, "support").state
        assertEquals(later.city.housingCapacity + 160, housed.city.housingCapacity)
        assertEquals(36, housed.society.story.scheduled.single().dueDay)
        assertEquals(before.armyPools, housed.armyPools)
        assertEquals(housed, SaveCodec.decode(SaveCodec.encode(housed)))
    }

    @Test fun aiRulersAgeAndSuccessionChangesTheExistingCourtWithoutCreatingTroops() {
        val original = game()
        val faction = target(original)
        val before = original.copy(day = 366, world = original.world.copy(factions = original.world.factions.map {
            if (it.id == faction.id) it.copy(rulerAge = 95) else it }))
        val after = DiplomacyEngine.tick(before)
        val successor = after.world.faction(faction.id)!!
        assertEquals(30, successor.rulerAge)
        assertEquals(faction.successionCount + 1, successor.successionCount)
        assertNotEquals(faction.ruler, successor.ruler)
        assertEquals(before.world.armies, after.world.armies)
        assertEquals(before.resources, after.resources)
        assertTrue(after.diplomacy.politics.first { it.factionId == faction.id }.history.any { it.text.contains("Herrscherwechsel") })
        assertEquals(after, DiplomacyEngine.tick(after))
        assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
    }

    @Test fun disloyalCommanderCanStageACoupWithFiniteTreasuryCost() {
        val original = game()
        val faction = target(original)
        val commander = original.world.enemyCommanders.first { it.factionId == faction.id }
        val before = original.copy(day = 7, diplomacy = original.diplomacy.copy(lastAiDecisionDay = 7,
            politics = original.diplomacy.politics.map { if (it.factionId == faction.id) it.copy(stability = 10) else it }),
            world = original.world.copy(enemyCommanders = original.world.enemyCommanders.map {
                if (it.id == commander.id) it.copy(rulerLoyalty = 10) else it }))
        val after = DiplomacyEngine.tick(before)
        assertEquals(commander.name, after.world.faction(faction.id)!!.ruler)
        assertEquals(faction.gold - 100, after.world.faction(faction.id)!!.gold)
        assertEquals(faction.successionCount + 1, after.world.faction(faction.id)!!.successionCount)
        assertEquals(before.world.armies.sumOf { it.total }, after.world.armies.sumOf { it.total })
        assertTrue(after.world.armies.first { it.factionId == faction.id }.morale < before.world.armies.first { it.factionId == faction.id }.morale)
        DiplomacyEngine.validate(after)
        WorldEngine.validate(after)
    }

    @Test fun rebellionTransfersExistingTerritoryArmyCommanderAndPopulationAndCancelsRecruitment() {
        val original = game()
        val faction = target(original)
        val commander = original.world.enemyCommanders.first { it.factionId == faction.id }
        val army = original.world.armies.first { it.factionId == faction.id }
        val province = original.world.places.first { it.ownerId == faction.id && it.id != faction.capitalId }
        val before = original.copy(day = 7, diplomacy = original.diplomacy.copy(lastAiDecisionDay = 7,
            politics = original.diplomacy.politics.map { if (it.factionId == faction.id) it.copy(stability = 10, hungerDays = 30) else it }),
            world = original.world.copy(factions = original.world.factions.map { if (it.id == faction.id) it.copy(gold = 0, food = 0) else it },
                armies = original.world.armies.map { if (it.id == army.id) it.copy(regionId = province.id) else it },
                enemyCommanders = original.world.enemyCommanders.map { if (it.id == commander.id) it.copy(rulerLoyalty = 30) else it },
                recruitments = listOf(WorldRecruitment("rebel_training", faction.id, army.id, army.units.first().type, 30, 20))))
        val after = DiplomacyEngine.tick(before)
        assertEquals(NEUTRAL_FACTION, after.world.place(province.id)!!.ownerId)
        assertEquals(NEUTRAL_FACTION, after.world.armies.first { it.id == army.id }.factionId)
        assertEquals(NEUTRAL_FACTION, after.world.enemyCommanders.first { it.id == commander.id }.factionId)
        assertTrue(after.world.recruitments.isEmpty())
        assertEquals(before.world.armies.sumOf { it.total }, after.world.armies.sumOf { it.total })
        assertTrue(after.world.faction(faction.id)!!.population < before.world.faction(faction.id)!!.population)
        assertTrue(after.world.faction(NEUTRAL_FACTION)!!.population > before.world.faction(NEUTRAL_FACTION)!!.population)
        assertTrue(DiplomacyEngine.atWar(after, faction.id, NEUTRAL_FACTION))
        DiplomacyEngine.validate(after)
        WorldEngine.validate(after)
        assertEquals(after, SaveCodec.decode(SaveCodec.encode(after)))
    }

    @Test fun mistreatedAiVassalEndsTributeAndRevoltsWithoutNewSoldiers() {
        val original = game()
        val faction = target(original)
        val before = DiplomacyEngine.changeRelation(original, PLAYER_FACTION, faction.id, 0, -30, "Misstrauen").let { state ->
            state.copy(day = 7, diplomacy = state.diplomacy.copy(lastAiDecisionDay = 7, treaties = listOf(
                Treaty("vassal", TreatyKind.VASSAL, PLAYER_FACTION, faction.id, 1, 100, faction.id, 50, 1)))) }
        val after = DiplomacyEngine.tick(before)
        assertFalse(DiplomacyEngine.hasTreaty(after, PLAYER_FACTION, faction.id, TreatyKind.VASSAL))
        assertTrue(DiplomacyEngine.atWar(after, PLAYER_FACTION, faction.id))
        assertEquals(before.world.armies, after.world.armies)
        assertTrue(after.diplomacy.politics.first { it.factionId == faction.id }.history.any { it.text.contains("Vasallenaufstand") })
    }

    @Test fun allianceSwitchUsesKnownPartnerAndTransfersFiniteGold() {
        val original = game()
        val faction = target(original)
        val former = original.world.factions.first { it.id !in listOf(PLAYER_FACTION, NEUTRAL_FACTION, faction.id) }
        val alternative = original.world.factions.first { it.id !in listOf(PLAYER_FACTION, NEUTRAL_FACTION, faction.id, former.id) }
        var before = DiplomacyEngine.changeRelation(original, faction.id, former.id, 0, -30, "Misstrauen")
        before = DiplomacyEngine.changeRelation(before, faction.id, alternative.id, 40, 10, "Gemeinsame Interessen")
        val knowledge = before.world.knowledgeFor(faction.id).copy(exploredRegions =
            (before.world.knowledgeFor(faction.id).exploredRegions + alternative.capitalId).distinct())
        before = before.copy(day = 7, diplomacy = before.diplomacy.copy(lastAiDecisionDay = 7,
            treaties = listOf(Treaty("old_alliance", TreatyKind.DEFENSIVE_ALLIANCE, faction.id, former.id, 1, 60))),
            world = before.world.copy(knowledge = before.world.knowledge.filterNot { it.factionId == faction.id } + knowledge))
        val after = DiplomacyEngine.tick(before)
        assertFalse(DiplomacyEngine.hasTreaty(after, faction.id, former.id, TreatyKind.DEFENSIVE_ALLIANCE))
        assertTrue(DiplomacyEngine.hasTreaty(after, faction.id, alternative.id, TreatyKind.DEFENSIVE_ALLIANCE))
        assertEquals(before.world.faction(faction.id)!!.gold - 150, after.world.faction(faction.id)!!.gold)
        assertEquals(before.world.faction(alternative.id)!!.gold + 150, after.world.faction(alternative.id)!!.gold)
        assertEquals(before.world.armies, after.world.armies)
    }
}
