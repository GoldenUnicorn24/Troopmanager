package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class V065FamilyTest {
    private fun family(): GameState {
        val initial = GameEngine.newGame("Leon", 24, Species.HUMAN, null)
        val state = initial.copy(day = 10, settings = initial.settings.copy(dynasty = true, romance = RomanceMode.OFF),
            resources = initial.resources.copy(gold = 2000), companion = initial.companion.copy(met = true))
        return DynastyEngine.initialize(CharacterEngine.initialize(RelationshipEngine.syncCommander(state)))
    }

    private fun withChild(): GameState = DynastyEngine.adoptHeir(family(), "Mira").state

    @Test fun familyPlanningRefusalLeavesEveryRelationshipValueAndMemoryUntouched() {
        val initial = family()
        val state = initial.copy(settings = initial.settings.copy(romance = RomanceMode.ROMANCE),
            relationship = initial.relationship.copy(romanceStage = RomanceStage.PARTNERSHIP,
                consent = initial.relationship.consent.copy(boundaries = setOf("no_children"))))
        assertEquals(state, DynastyEngine.agreeFamilyPlanning(state).state)
        assertEquals(state, DynastyEngine.planFamily(state).state)
        assertTrue(state.relationship.memories.none { it.type == "family_refusal" })
    }

    @Test fun nameAtBirthAndLaterRenameRemainPersistent() {
        val initial = family()
        var state = initial.copy(settings = initial.settings.copy(romance = RomanceMode.ROMANCE),
            companion = initial.companion.copy(trust = 95, respect = 90), relationship = initial.relationship.copy(
                romanceStage = RomanceStage.PARTNERSHIP, commitment = 80, consent = initial.relationship.consent.copy(wantsChildren = true)))
        state = DynastyEngine.setBirthName(state, "  Elian  ").state
        state = DynastyEngine.planFamily(state).state
        assertNotNull(state.dynasty.plannedBirthDay)
        state = DynastyEngine.tick(state.copy(day = state.dynasty.plannedBirthDay!!))
        val child = state.dynasty.members.first { it.parents.isNotEmpty() }
        assertEquals("Elian", child.name)
        assertEquals(0, child.age(state.day))
        assertNull(child.adultCommanderId)
        state = DynastyEngine.renameMember(state, child.id, "Elian Morgenstern").state
        assertEquals("Elian Morgenstern", SaveCodec.decode(SaveCodec.encode(state)).dynasty.members.first { it.id == child.id }.name)
    }

    @Test fun eachEducationPathAndMentorHasAnObservableEffectAfterAYear() {
        val initial = withChild()
        val child = initial.dynasty.members.first { it.adopted }
        var military = DynastyEngine.chooseEducation(initial, child.id, EducationPath.MILITARY).state
        military = DynastyEngine.assignMentor(military, child.id, "commander:1").state
        military = military.copy(commanders = military.commanders.map { if (it.id == 1L) it.copy(leadership = 90, tactics = 90) else it })
        var medical = DynastyEngine.chooseEducation(initial, child.id, EducationPath.MEDICINE).state
        medical = DynastyEngine.assignMentor(medical, child.id, "teacher").state
        val trained = DynastyEngine.tick(military.copy(day = military.day + 365)).dynasty.members.first { it.id == child.id }
        val educated = DynastyEngine.tick(medical.copy(day = medical.day + 365)).dynasty.members.first { it.id == child.id }
        assertTrue(trained.leadership > educated.leadership)
        assertTrue(trained.tactics > educated.tactics)
        assertTrue(educated.medicine > trained.medicine)
        assertTrue(trained.educationLog.any { it.text.contains("Ausbildungsjahr") })
    }

    @Test fun unavailableOrMinorMentorCannotBeAssigned() {
        val initial = withChild()
        val child = initial.dynasty.members.first { it.adopted }
        val wounded = initial.copy(war = initial.war.copy(commanderConditions = listOf(CommanderCondition(1L, CombatantStatus.WOUNDED, 30))))
        assertEquals(wounded, DynastyEngine.assignMentor(wounded, child.id, "commander:1").state)
        val minor = initial.copy(court = initial.court.copy(characters = initial.court.characters.map { if (it.commanderId == 1L) it.copy(age = 17) else it }))
        assertEquals(minor, DynastyEngine.assignMentor(minor, child.id, "commander:1").state)
    }

    @Test fun educationPausesWithDynastyAndRepeatedTickCannotGrantMoreSkills() {
        var state = withChild()
        val child = state.dynasty.members.first { it.adopted }
        state = DynastyEngine.chooseEducation(state, child.id, EducationPath.ADMINISTRATION).state
        val paused = DynastyEngine.tick(state.copy(day = state.day + 365, settings = state.settings.copy(dynasty = false)))
        val resumed = DynastyEngine.tick(paused.copy(day = paused.day + 1, settings = paused.settings.copy(dynasty = true)))
        val after = resumed.dynasty.members.first { it.id == child.id }
        assertEquals(child.age(state.day), after.age(resumed.day))
        assertEquals(child.stewardship, after.stewardship)
        assertEquals(resumed, DynastyEngine.tick(resumed))
    }

    @Test fun adultCourtEntryHappensExactlyAt18AndPreservesEducationAndSiblingSafety() {
        var state = withChild()
        val child = state.dynasty.members.first { it.adopted }
        state = DynastyEngine.assignMentor(state, child.id, "commander:1").state
        state = DynastyEngine.chooseEducation(state, child.id, EducationPath.DIPLOMACY).state
        val seventeen = DynastyEngine.tick(state.copy(day = state.day + 7 * 365))
        assertNull(seventeen.dynasty.members.first { it.id == child.id }.adultCommanderId)
        val adult = DynastyEngine.tick(seventeen.copy(day = seventeen.day + 365))
        val member = adult.dynasty.members.first { it.id == child.id }
        val id = member.adultCommanderId!!
        val detail = adult.court.characters.first { it.commanderId == id }
        assertEquals(18, member.age(adult.day))
        assertEquals(18, detail.age)
        assertEquals(member.diplomacy, detail.diplomacy)
        assertEquals(member.leadership, adult.commanders.first { it.id == id }.leadership)
        assertTrue(detail.family.contains("player"))
        val mentorship = adult.court.socialLinks.first { it.firstId == minOf(id, 1L) && it.secondId == maxOf(id, 1L) }
        assertTrue(mentorship.directed)
        assertEquals(id, mentorship.sourceId)
        assertTrue(mentorship.cause.contains("Ausbildung"))
        assertEquals(adult, DynastyEngine.tick(adult))
        assertEquals(adult, SaveCodec.decode(SaveCodec.encode(adult)))
    }

    @Test fun atLeastEightDistinctFamilyEventsHaveAgeAppropriateSpecificChoices() {
        assertTrue(FamilyEducationCatalog.events.size >= 8)
        assertEquals(FamilyEducationCatalog.events.size, FamilyEducationCatalog.events.map { it.id }.distinct().size)
        FamilyEducationCatalog.events.forEach { event ->
            assertTrue(event.minAge >= 3 && event.maxAge <= 17)
            assertTrue(event.choices.size in 3..5)
            assertEquals(event.choices.size, event.choices.map { it.label }.distinct().size)
            assertTrue(event.choices.all { it.consequence.isNotBlank() })
        }
    }

    @Test fun eventChoiceChangesChildAndCooldownPreventsRepeatedReward() {
        val initial = withChild()
        val state = DynastyEngine.tick(initial.copy(day = 60))
        val event = state.dynasty.pendingFamilyEvent!!
        val child = state.dynasty.members.first { it.id == event.memberId }
        val choice = event.choices.first()
        val decided = DynastyEngine.chooseFamilyEvent(state, choice.id).state
        val developed = decided.dynasty.members.first { it.id == child.id }
        assertTrue(listOf(developed.diplomacy, developed.leadership, developed.stewardship, developed.medicine, developed.tactics) !=
            listOf(child.diplomacy, child.leadership, child.stewardship, child.medicine, child.tactics))
        assertNull(decided.dynasty.pendingFamilyEvent)
        assertEquals(decided, DynastyEngine.chooseFamilyEvent(decided, choice.id).state)
        assertFalse(DynastyEngine.eligibleFamilyEvents(decided, developed).any { it.id == event.id })
        assertTrue(decided.chronicle.any { it.title == event.title })
    }

    @Test fun chosenRegentSurvivesPreparationAndLeadsTheMinorSuccession() {
        var state = withChild()
        val child = state.dynasty.members.first { it.adopted }
        val regent = DynastyEngine.regentCandidates(state).first()
        state = DynastyEngine.selectRegent(state, regent.id).state
        state = DynastyEngine.tick(state.copy(day = state.day + 1))
        assertEquals(regent.id, state.dynasty.regentCommanderId)
        state = DynastyEngine.resolveBattleSuccession(state.copy(war = state.war.copy(playerCondition = CombatantStatus.DEAD)))
        assertEquals(child.id, state.dynasty.rulerId)
        assertEquals(regent.id, state.dynasty.regentCommanderId)
        assertTrue(state.dynasty.regencyReason.contains("18"))
        assertTrue(state.dynasty.legitimacyCauses.any { it.cause.contains("Regentschaft") && it.change < 0 })
        assertEquals(RomanceStage.NONE, state.relationship.romanceStage)
    }

    @Test fun victoriesAndCivilianCrisisExplainLegitimacyChanges() {
        val initial = family()
        val victory = DynastyEngine.tick(initial.copy(day = 50, victories = initial.victories + 1))
        assertTrue(victory.dynasty.legitimacyCauses.any { it.cause.contains("Siege") && it.change == 2 })
        val crisis = DynastyEngine.tick(victory.copy(day = 90, resources = victory.resources.copy(food = 0)))
        assertTrue(crisis.dynasty.legitimacyCauses.any { it.cause.contains("Bürgerkrise") && it.change < 0 })
    }

    @Test fun legacyModelDefaultsDoNotInventChildrenEducationOrRomance() {
        val json = Json { ignoreUnknownKeys = true }
        val dynasty = json.decodeFromString<DynastyState>("""{"members":[{"id":"player","name":"Leon","bornDay":1,"initialAge":24}],"legitimacy":57}""")
        assertEquals(57, dynasty.legitimacy)
        assertEquals(1, dynasty.members.size)
        assertNull(dynasty.pendingFamilyEvent)
        assertNull(dynasty.members.first().educationPath)
        assertNull(dynasty.members.first().adultCommanderId)
        val link = json.decodeFromString<NpcSocialLink>("""{"firstId":1,"secondId":2,"kind":"FRIENDSHIP","sinceDay":31,"strength":40}""")
        assertEquals(40, link.previousStrength)
        assertEquals(31, link.lastChangedDay)
        assertFalse(link.directed)
    }
}
