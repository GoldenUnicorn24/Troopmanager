package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GovernanceEngineTest {
    private fun realm(): GameState {
        val state=GameEngine.newGame("Leon",24,Species.HUMAN,null)
        return CharacterEngine.initialize(RelationshipEngine.syncCommander(state.copy(day=6,
            companion=state.companion.copy(met=true,trust=90,respect=90,affection=90),
            relationship=state.relationship.copy(romanceStage=RomanceStage.CO_RULERS),
            settings=state.settings.copy(romance=RomanceMode.MATURE))))
    }

    private fun delegated(state: GameState, portfolio: CoRulerPortfolio=CoRulerPortfolio.ECONOMY,
        action: DelegatedAction=DelegatedAction.SUPPLY_PURCHASE,limit: Int=250): GameState = state.copy(
        coRuler=state.coRuler.copy(portfolio=portfolio,delegation=DelegationPolicy(true,setOf(action),limit,limit)))

    @Test fun catalogContainsDistinctGoverningDecisionsAndCourtConflicts() {
        val cases=CoRulerEngine.catalog(realm())
        assertTrue(cases.count { it.kind==CouncilCaseKind.GOVERNMENT }>=10)
        assertTrue(cases.count { it.kind==CouncilCaseKind.COURT_CONFLICT }>=10)
        assertEquals(cases.size,cases.map { it.id }.toSet().size)
        assertTrue(cases.all { it.options.size>=3 && it.options.map { o->o.id }.distinct().size==it.options.size })
    }

    @Test fun authorityIsOptInAndNeedsConsciousCoRulerStage() {
        val state=realm().copy(resources=realm().resources.copy(food=300))
        val disabled=CoRulerEngine.day(state)
        assertEquals(state.resources,disabled.resources)
        assertTrue(disabled.coRuler.decisions.isEmpty())
        val friendship=delegated(state).copy(relationship=state.relationship.copy(romanceStage=RomanceStage.NONE))
        assertTrue(CoRulerEngine.day(friendship).coRuler.decisions.isEmpty())
        assertEquals(friendship,CoRulerEngine.setDelegation(friendship,DelegationPolicy(enabled=true)).state)
    }

    @Test fun autonomousSupplyPurchasePaysAndReportsReasonWithoutWar() {
        val state=delegated(realm().copy(resources=realm().resources.copy(food=300)))
        val next=CoRulerEngine.day(state)
        assertEquals(state.resources.gold-200,next.resources.gold)
        assertEquals(state.resources.food+400,next.resources.food)
        assertEquals(200,next.coRuler.goldSpentToday)
        val report=next.coRuler.decisions.single()
        assertTrue(report.autonomous)
        assertEquals(state.companion.name,report.actor)
        assertTrue(report.reason.contains("Vollmacht"))
        assertEquals(state.world.factions.map { it.wars },next.world.factions.map { it.wars })
        assertEquals(state.world.armies,next.world.armies)
        assertEquals(next,CoRulerEngine.day(next))
    }

    @Test fun invalidCapsAndInsufficientMoneyCannotGrantOrSpendAuthority() {
        val state=realm()
        assertEquals(state,CoRulerEngine.setDelegation(state,DelegationPolicy(true,maxGoldPerAction=501,maxGoldPerDay=501)).state)
        val capped=delegated(state.copy(resources=state.resources.copy(food=300)),limit=100)
        assertEquals(capped.resources,CoRulerEngine.day(capped).resources)
        val poor=delegated(state.copy(resources=state.resources.copy(food=300,gold=10)))
        assertTrue(CoRulerEngine.day(poor).coRuler.decisions.isEmpty())
    }

    @Test fun companionAbsencePausesPersonalDelegationAndRulerTravelEnablesRegency() {
        val state=delegated(realm().copy(resources=realm().resources.copy(food=300)))
        val wounded=state.copy(war=state.war.copy(commanderConditions=listOf(CommanderCondition(COMPANION_COMMANDER_ID,CombatantStatus.WOUNDED,10))))
        assertTrue(CoRulerEngine.day(wounded).coRuler.decisions.isEmpty())
        val travel=PresenceEngine.travel(state).state
        assertTrue(CoRulerEngine.regency(travel).companionActing)
        val next=CoRulerEngine.day(travel)
        assertEquals(state.companion.name,next.coRuler.actingRuler)
        assertEquals(100,next.coRuler.regencyEfficiency)
        assertTrue(next.coRuler.decisions.single().autonomous)
    }

    @Test fun bothAbsentUseCouncilWithinExistingBudgetAtLowerEfficiency() {
        val state=delegated(realm().copy(resources=realm().resources.copy(food=300)))
        val travel=PresenceEngine.travel(state,true).state
        val next=CoRulerEngine.day(travel)
        assertEquals("Hofrat",next.coRuler.actingRuler)
        assertEquals(60,next.coRuler.regencyEfficiency)
        assertEquals("Hofrat",next.coRuler.decisions.single().actor)
        assertEquals(travel.resources.food+240,next.resources.food)
        assertEquals(travel.resources.gold-200,next.resources.gold)
    }

    @Test fun respectfulOverruleHasNoAutomaticRelationshipPenalty() {
        val state=realm().copy(coRuler=CoRulerState(pendingCaseIds=listOf("recognition")))
        val result=CoRulerEngine.decide(state,"recognition","hear")
        assertEquals(state.relationship.conflict,result.state.relationship.conflict)
        assertEquals(state.companion.trust,result.state.companion.trust)
        assertEquals(0,result.state.coRuler.disrespectfulOverrides)
        assertEquals(state.day,result.state.coRuler.resolvedDays["recognition"])
        assertEquals(result.state,CoRulerEngine.decide(result.state,"recognition","charter").state)
    }

    @Test fun fieldSupplyTransfersExistingFoodAndDoesNotCreateAnArmy() {
        val state=realm()
        val army=WorldArmy("own-field",PLAYER_FACTION,"Wacht",listOf(UnitAllocation(UnitType.HUMAN_SWORD,20)),"village",
            commanderId=state.commanders.first { it.id!=COMPANION_COMMANDER_ID }.id,supplyFood=10)
        val prepared=state.copy(world=state.world.copy(armies=state.world.armies+army),coRuler=CoRulerState(pendingCaseIds=listOf("field_supply")))
        val next=CoRulerEngine.decide(prepared,"field_supply","convoy").state
        assertEquals(prepared.resources.food-200,next.resources.food)
        assertEquals(210,next.world.armies.first { it.id==army.id }.supplyFood)
        assertEquals(prepared.world.armies.size,next.world.armies.size)
    }

    @Test fun medicalDecisionChangesRealRecoveryDateAndCannotBeRepeated() {
        val state=realm().copy(war=realm().war.copy(wounded=listOf(WoundedCohort("patient",UnitType.HUMAN_SWORD,20,12))),
            coRuler=CoRulerState(pendingCaseIds=listOf("hospital")))
        val next=CoRulerEngine.decide(state,"hospital","healers").state
        assertEquals(11,next.war.wounded.single().recoveryDay)
        assertEquals(state.resources.gold-180,next.resources.gold)
        assertEquals(next,CoRulerEngine.decide(next,"hospital","healers").state)
        assertTrue(CoRulerEngine.councilCases(next).isEmpty())
    }

    @Test fun personalityAndOfficeExplainDifferentRecommendations() {
        val state=realm().copy(relationship=realm().relationship.copy(personality=CompanionPersonalityProfile(
            priorities=listOf(CompanionPriority.WOUNDED),traits=listOf(CompanionTrait.COMPASSIONATE))),
            war=realm().war.copy(wounded=listOf(WoundedCohort("patient",UnitType.HUMAN_SWORD,20,12))))
        val case=CoRulerEngine.catalog(state).first { it.id=="office_budget" }
        val vote=CoRulerEngine.recommendations(state,case).first { it.member==state.companion.name }
        assertEquals("health",vote.optionId)
        assertTrue(vote.reason.contains("Verwundete"))
        assertTrue(vote.reason.contains("mitfühlend"))
    }

    @Test fun governmentAndPresencePersistAndLegacySavesGetSafeDefaults() {
        val state=CoRulerEngine.day(delegated(realm().copy(resources=realm().resources.copy(food=300))))
        assertEquals(state.coRuler,SaveCodec.decode(SaveCodec.encode(state)).coRuler)
        val root=Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject
        val old=JsonObject(root.filterKeys { it !in setOf("coRuler","presence","_checksum") }+("version" to JsonPrimitive(3)))
        val restored=SaveCodec.decode(old.toString())
        assertFalse(restored.coRuler.delegation.enabled)
        assertTrue(restored.coRuler.decisions.isEmpty())
        assertEquals(PresenceLocation.PALACE,PresenceEngine.presence(restored).player.location)
    }
    @Test fun cultureCouncilCaseChangesTheActualTargetCulture() {
        val base = realm()
        val prepared = base.copy(
            resources = base.resources.copy(gold = 5000),
            frontier = base.frontier.copy(
                cultureStanding = mapOf(Culture.HUMAN to 20),
                cultureIntegration = mapOf(Culture.HUMAN to 30),
            ),
            coRuler = base.coRuler.copy(pendingCaseIds = listOf("culture_compact"), agendaDay = 0),
        )
        val case = CoRulerEngine.councilCases(prepared).first { it.id == "culture_compact" }
        assertTrue(case.title.contains("Menschen"))
        val decided = CoRulerEngine.decide(prepared, "culture_compact", "hearing").state
        assertEquals(26, FrontierEngine.cultureStanding(decided, Culture.HUMAN))
        assertEquals(32, FrontierEngine.cultureIntegration(decided, Culture.HUMAN))
        assertEquals(prepared.resources.gold - 80, decided.resources.gold)
        assertTrue(decided.coRuler.decisions.any { it.caseId == "culture_compact" })
    }

}
