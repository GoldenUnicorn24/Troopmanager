package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V065RelationshipTest {
    private fun state(day: Int = 10): GameState {
        val initial = GameEngine.newGame("Mira", 24, Species.HUMAN, null)
        return initial.copy(day = day, companion = initial.companion.copy(met = true, trust = 65, respect = 60, affection = 60))
    }

    @Test fun contentHasAuthoredBreadthAndCompleteArcDurations() {
        val catalog = RelationshipContentCatalog.scenarios
        assertTrue(catalog.size >= 50)
        assertTrue("Frontier and allied scenes need their own eight authored events", catalog.count { it.category == "frontier" } >= 8)
        assertEquals(catalog.size, catalog.map { it.key }.toSet().size)
        assertEquals(catalog.size, catalog.map { it.title }.toSet().size)
        assertEquals(catalog.size, catalog.map { it.text }.toSet().size)
        assertTrue(catalog.all { it.options.size in 3..5 && it.variants.size >= 2 })
        assertTrue(catalog.all { it.options.map { option -> option.label }.distinct().size == it.options.size })
        assertTrue(catalog.all { it.options.any { option -> option.boundary } })
        assertTrue(RelationshipContentCatalog.arcs.size >= 4)
        assertTrue(RelationshipContentCatalog.arcs.all { it.stages.size in 3..5 && it.stageIntervals.sum() in 20..50 })
        assertTrue(RelationshipContentCatalog.activities.size >= 12)
    }

    @Test fun selectionSurvivesReloadAndDoesNotRotateByListIndex() {
        val initial = state(6)
        val first = RelationshipEventDirector.day(initial)
        val restored = SaveCodec.decode(SaveCodec.encode(initial))
        assertEquals(first.relationship.pendingEvent, RelationshipEventDirector.day(restored).relationship.pendingEvent)
        assertNotNull(first.relationship.pendingEvent)
        assertTrue(first.relationship.pendingEvent!!.options.all { it.label.isNotBlank() && it.reaction.isNotBlank() })
        assertEquals(first, RelationshipEventDirector.day(first))
    }

    @Test fun eventAndCategoryCooldownsApplyToExplicitTriggers() {
        val opened = RelationshipEngine.onEvent(state(), "embers")
        val closed = RelationshipEngine.choose(opened, 2).state
        assertNull(closed.relationship.pendingEvent)
        assertNull(RelationshipEngine.onEvent(closed.copy(day = closed.day + 1), "embers").relationship.pendingEvent)
        assertNull(RelationshipEngine.onEvent(closed.copy(day = closed.day + 1), "breakfast").relationship.pendingEvent)
        assertNotNull(RelationshipEngine.onEvent(closed.copy(day = closed.day + 16), "embers").relationship.pendingEvent)
    }

    @Test fun personalityChangesWeightsAndOpinionWithoutChangingIdentityOrRomance() {
        val caring = state().copy(war = WarState(wounded = listOf(WoundedCohort("w", UnitType.HUMAN_SWORD, 20, 30))))
        val martial = caring.copy(relationship = caring.relationship.copy(personality = CompanionPersonalityProfile(
            traits = listOf(CompanionTrait.MARTIAL, CompanionTrait.BRAVE, CompanionTrait.PROUD),
            priorities = listOf(CompanionPriority.EXPANSION, CompanionPriority.DEFENSE, CompanionPriority.RECOGNITION))))
        val hospital = RelationshipContentCatalog.scenarios.first { it.key == "beds_short" }
        assertTrue(RelationshipEventDirector.weight(caring, hospital) > RelationshipEventDirector.weight(martial, hospital))
        assertTrue(RelationshipEventDirector.currentOpinion(caring).contains("Verwundeten"))
        assertTrue(RelationshipEventDirector.currentOpinion(martial).contains("Feldzug"))
        assertEquals(caring.relationship.personality, RelationshipEngine.day(caring).relationship.personality)
        assertEquals(RomanceStage.NONE, RelationshipEngine.day(caring).relationship.romanceStage)
    }

    @Test fun refusingAProposalHasNoHiddenStatBudgetCooldownOrNegativeMemoryPenalty() {
        val initial = state().copy(companion = state().companion.copy(trust = 20), settings = state().settings.copy(romance = RomanceMode.MATURE))
        val refused = RelationshipEngine.action(initial, "confess").state
        assertEquals(initial.companion.trust, refused.companion.trust)
        assertEquals(initial.companion.respect, refused.companion.respect)
        assertEquals(initial.companion.affection, refused.companion.affection)
        assertEquals(initial.relationship.conflict, refused.relationship.conflict)
        assertEquals(initial.relationship.spentActions, refused.relationship.spentActions)
        assertEquals(initial.relationship.lastRomanceDay, refused.relationship.lastRomanceDay)
        assertEquals(0, refused.relationship.memories.last().emotionalWeight)
        assertTrue("boundary" in refused.relationship.memories.last().tags)
        assertEquals(refused, RelationshipEngine.action(refused, "confess").state)
    }

    @Test fun aBoundaryChoiceIsNeutralEvenWhenTimeBudgetIsAlreadySpent() {
        val issue = RelationshipIssue("tax_issue", "Steuern", 20, 10)
        val initial = state().copy(relationship = state().relationship.copy(issues = listOf(issue), conflict = 15, spentActions = 2, actionDay = 10))
        val opened = RelationshipEngine.onEvent(initial, "tax_bread")
        val after = RelationshipEngine.choose(opened, 2).state
        assertNull(after.relationship.pendingEvent)
        assertEquals(initial.companion, after.companion)
        assertEquals(initial.relationship.conflict, after.relationship.conflict)
        assertEquals(initial.relationship.spentActions, after.relationship.spentActions)
        assertEquals(initial.relationship.issues, after.relationship.issues)
        assertTrue(after.relationship.memories.last().emotionalWeight >= 0)
    }

    @Test fun decliningPrivateTimeRemainsPossibleAfterTheDayBudgetIsSpent() {
        val initial = state().copy(companion = state().companion.copy(trust = 95, respect = 95, affection = 95),
            settings = state().settings.copy(romance = RomanceMode.MATURE),
            relationship = state().relationship.copy(romanceStage = RomanceStage.PARTNERSHIP, commitment = 60,
                consent = ConsentProfile(intimacyAllowed = true)))
        val invitation = RelationshipEngine.action(initial, "intimacy").state
        assertNotNull(invitation.relationship.pendingEvent)
        val busy = invitation.copy(relationship = invitation.relationship.copy(spentActions = 2, actionDay = invitation.day))
        val declined = RelationshipEngine.choose(busy, 5).state
        assertNull(declined.relationship.pendingEvent)
        assertEquals(busy.companion, declined.companion)
        assertEquals(busy.relationship.spentActions, declined.relationship.spentActions)
        assertEquals(busy.relationship.conflict, declined.relationship.conflict)
        assertEquals(busy.relationship.lastIntimacyDay, declined.relationship.lastIntimacyDay)
        assertEquals(0, declined.relationship.memories.last().emotionalWeight)
    }

    @Test fun explicitIssueDiscussionResolvesTopicAndPreservesLinkedMemory() {
        val initial = state().copy(relationship = state().relationship.copy(issues = listOf(RelationshipIssue("tax_issue", "Steuern", 20, 10)), conflict = 20))
        val after = RelationshipEngine.action(initial, "issue:tax_issue").state
        val issue = after.relationship.issues.single()
        assertTrue(issue.resolved)
        assertEquals(0, issue.severity)
        assertTrue(after.relationship.memories.any { it.id in issue.memoryIds && it.text.contains("Steuern") })
        val later = RelationshipEngine.action(after.copy(day = 11), "issue:tax_issue").state
        assertEquals(issue, later.relationship.issues.single())
    }

    @Test fun everyArcMovesThroughRealTicksChoicesMemoriesAndTwentyToFiftyDays() {
        for (definition in RelationshipContentCatalog.arcs) {
            var current = state().copy(relationship = state().relationship.copy(arcs = listOf(RelationshipArcState(definition.id, startedDay = 10))))
            var previousMemory: RelationshipMemory? = null
            repeat(definition.stages.size) { stage ->
                current = RelationshipEngine.day(current)
                val event = current.relationship.pendingEvent!!
                assertEquals(definition.id, event.arcId)
                assertEquals(stage, event.arcStage)
                previousMemory?.let { assertTrue(event.text.contains(it.text)) }
                current = RelationshipEngine.choose(current, stage % 2).state
                val arc = current.relationship.arcs.single { it.id == definition.id }
                assertEquals(stage + 1, arc.stage)
                assertEquals(stage + 1, arc.decisions.size)
                assertEquals(stage + 1, arc.memoryIds.size)
                previousMemory = current.relationship.memories.last()
                if (!arc.completed) current = current.copy(day = arc.nextDay)
            }
            val arc = current.relationship.arcs.single { it.id == definition.id }
            assertTrue(arc.completed)
            assertTrue(current.day - arc.startedDay in 20..50)
            assertEquals(RomanceStage.NONE, current.relationship.romanceStage)
            assertTrue(arc.memoryIds.all { id -> current.relationship.memories.any { it.id == id && definition.id in it.tags } })
        }
    }

    @Test fun politicalAnswersCreatePersistedDelayedConsequencesExactlyOnce() {
        val opened = RelationshipEngine.onEvent(state(), "trade_toll")
        val chosen = RelationshipEngine.choose(opened, 1).state
        assertEquals(1, chosen.relationship.delayedConsequences.size)
        val due = chosen.relationship.delayedConsequences.single().dueDay
        val restored = SaveCodec.decode(SaveCodec.encode(chosen))
        val settled = RelationshipEngine.day(restored.copy(day = due))
        assertTrue(settled.relationship.delayedConsequences.isEmpty())
        assertEquals(1, settled.relationship.memories.count { it.type == "consequence" })
        assertTrue(settled.city.satisfaction > chosen.city.satisfaction)
        assertEquals(settled, RelationshipEngine.day(settled))
    }

    @Test fun sharedTimeRequiresBothPersonsAtTheSamePlaceAndHospitalVisitsStayOptional() {
        val initial = state()
        val separate = initial.copy(presence = PresenceState(player = PersonPresence(PresenceLocation.CITY, "Markt", true, bindingId = "visit")))
        assertEquals(separate, RelationshipEngine.action(separate, "talk").state)
        assertEquals(separate, RelationshipEngine.action(separate, "activity:quiet").state)
        val wounded = initial.copy(war = initial.war.copy(commanderConditions = listOf(CommanderCondition(COMPANION_COMMANDER_ID, CombatantStatus.WOUNDED, 30))))
        assertEquals(wounded, RelationshipEngine.action(wounded, "train").state)
        assertTrue(RelationshipEngine.activities(wounded).first { it.activity.id == "hospital" }.available)
        val visited = RelationshipEngine.action(wounded, "activity:hospital").state
        assertTrue(visited.relationship.memories.last().tags.contains("wounded"))
        assertEquals(RomanceStage.NONE, visited.relationship.romanceStage)
        val captured = wounded.copy(war = wounded.war.copy(commanderConditions = listOf(CommanderCondition(COMPANION_COMMANDER_ID, CombatantStatus.CAPTURED))))
        assertFalse(RelationshipEngine.activities(captured).any { it.available })
    }

    @Test fun weatherSeasonWarAndCostGateActivitiesAndFriendshipCanWalk() {
        val initial = state().copy(world = WorldState(weather = WeatherState(season = Season.WINTER)))
        assertFalse(RelationshipEngine.activities(initial).first { it.activity.id == "ride" }.available)
        assertTrue(RelationshipEngine.activities(initial).first { it.activity.id == "walk" }.available)
        val walked = RelationshipEngine.action(initial, "walk").state
        assertEquals(RomanceStage.NONE, walked.relationship.romanceStage)
        assertTrue(walked.relationship.memories.any { it.type == "activity:walk" })
        val poor = initial.copy(resources = initial.resources.copy(gold = 0))
        assertFalse(RelationshipEngine.activities(poor).first { it.activity.id == "gift" }.available)
        val invasion = initial.copy(invasion = Invasion(EnemyType.ORC, 20, 100, 10))
        assertFalse(RelationshipEngine.activities(invasion).first { it.activity.id == "travel" }.available)
    }

    @Test fun friendshipBoundariesDoNotAwardOrAdvanceRomanceAndPartnershipDoesNotGovern() {
        var initial = state(14).copy(companion = state().companion.copy(trust = 95, respect = 95, affection = 95))
        initial = initial.copy(relationship = initial.relationship.copy(romanceStage = RomanceStage.PARTNERSHIP))
        val daily = RelationshipEngine.day(initial)
        assertEquals(initial.realm.tradeBonusDays, daily.realm.tradeBonusDays)
        assertEquals(initial.city.satisfaction, daily.city.satisfaction)
        val friends = RelationshipEngine.action(initial, "friendship").state
        assertEquals(RomanceStage.NONE, friends.relationship.romanceStage)
        assertFalse(friends.relationship.consent.romanceAllowed)
        assertEquals(initial.relationship.spentActions, friends.relationship.spentActions)
        assertEquals(initial.companion.trust, friends.companion.trust)
        assertEquals(RomanceStage.NONE, RelationshipEngine.action(friends.copy(day = 20), "confess").state.relationship.romanceStage)
    }
}
