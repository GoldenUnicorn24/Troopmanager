package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class PalacePresentationTest {
    private fun game() = GameEngine.newGame("Leon", 23, Species.HUMAN, null).let {
        it.copy(day = 12, companion = it.companion.copy(met = true))
    }

    @Test fun highTrustNeverPresentsAnAutomaticMarriageOrJointRule() {
        val start = game().let { it.copy(companion = it.companion.copy(trust = 100, respect = 100, affection = 100)) }
        val view = palacePresentation(start)
        assertTrue(view.playerPresent)
        assertTrue(view.companionPresent)
        assertFalse(view.sharedInsignia)
        assertFalse(view.jointCourt)
    }

    @Test fun woundedCoRulerRetainsHerInsigniaButHerFigureLeavesThePalace() {
        val start = game().let {
            it.copy(relationship = it.relationship.copy(romanceStage = RomanceStage.CO_RULERS),
                war = it.war.copy(commanderConditions = listOf(CommanderCondition(COMPANION_COMMANDER_ID, CombatantStatus.WOUNDED, 18))))
        }
        val view = palacePresentation(start)
        assertTrue(view.sharedInsignia)
        assertTrue(view.jointCourt)
        assertTrue(view.playerPresent)
        assertFalse(view.companionPresent)
    }

    @Test fun bothTravellersAreAbsentAndAMarriageMemoryDecoratesOnlyTheCeremonyDays() {
        val start = game().let {
            it.copy(relationship = it.relationship.copy(romanceStage = RomanceStage.MARRIED,
                memories = listOf(RelationshipMemory("wedding", 12, "marry", text = "Ein gemeinsames Haus.", emotionalWeight = 5))),
                presence = PresenceState(
                    player = PersonPresence(PresenceLocation.TRAVEL, available = false, returnDay = 17, bindingId = "travel:12"),
                    companion = PersonPresence(PresenceLocation.TRAVEL, available = false, returnDay = 17, bindingId = "travel:12")))
        }
        val view = palacePresentation(start)
        assertFalse(view.playerPresent)
        assertFalse(view.companionPresent)
        assertTrue(view.celebration)
        assertFalse(palacePresentation(start.copy(day = 14)).celebration)
    }

    @Test fun unmetCompanionNeverAppearsAndHungerMakesTheCrisisVisible() {
        val start = game().let { it.copy(companion = it.companion.copy(met = false), resources = it.resources.copy(food = 0)) }
        val view = palacePresentation(start)
        assertFalse(view.companionPresent)
        assertTrue(view.crisis)
    }

    @Test fun albumFiltersKeepLegacyBattleAndFamilyMilestonesDiscoverable() {
        assertTrue(memoryMatches(RelationshipMemory("battle", 2, "mission", text = "Gemeinsamer Sieg."), "Krieg"))
        assertTrue(memoryMatches(RelationshipMemory("birth", 4, "child_arrival", emotionalWeight = 5), "Familie"))
        assertTrue(memoryMatches(RelationshipMemory("kiss", 6, "kiss", emotionalWeight = 5), "Wichtigste"))
        assertFalse(memoryMatches(RelationshipMemory("walk", 7, "walk", tags = setOf("travel")), "Politik"))
    }
}
