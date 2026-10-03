package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class V06PeopleTest {
    private fun friends(mode: RomanceMode = RomanceMode.MATURE): GameState {
        val initial = GameEngine.newGame("Liora", 24, Species.HUMAN, null)
        return CharacterEngine.initialize(RelationshipEngine.syncCommander(initial.copy(day = 10,
            companion = initial.companion.copy(met = true, trust = 95, respect = 95, affection = 95),
            settings = initial.settings.copy(romance = mode))))
    }

    private fun partners(): GameState {
        val state = friends()
        return state.copy(relationship = state.relationship.copy(romanceStage = RomanceStage.PARTNERSHIP, attraction = 80,
            commitment = 65, consent = state.relationship.consent.copy(intimacyAllowed = true)))
    }

    @Test fun perfectFriendshipScoresNeverCreateRomance() {
        var state = friends(RomanceMode.OFF)
        repeat(8) { state = RelationshipEngine.day(state.copy(day = state.day + 1)) }
        assertEquals(RomanceStage.NONE, state.relationship.romanceStage)
        assertEquals("Vertrauensperson", state.relationshipStage())
        assertFalse(state.companion.role == "Mitregentin")
        assertEquals(RomanceStage.NONE, RelationshipEngine.action(state, "confess").state.relationship.romanceStage)
    }

    @Test fun eachRomanticStageNeedsItsOwnVoluntaryDecision() {
        var state = friends()
        val actions = listOf("confess", "kiss", "partner", "propose", "marry", "co_ruler")
        state = state.copy(companion = state.companion.copy(diplomacy = 75))
        actions.forEachIndexed { index, action ->
            state = RelationshipEngine.action(state.copy(day = 10 + index * 4), action).state
            assertEquals(RomanceStage.entries[index + 1], state.relationship.romanceStage)
        }
        assertEquals(6, state.relationship.memories.size)
        assertEquals("Herrscherpaar", state.relationshipStage())
        assertEquals("Mitregentin", state.companion.role)
    }

    @Test fun refusalAndPermanentBoundaryCannotBePurchasedAway() {
        val state = friends().copy(companion = friends().companion.copy(trust = 35))
        val refused = RelationshipEngine.action(state, "confess").state
        assertEquals(RomanceStage.NONE, refused.relationship.romanceStage)
        assertTrue(refused.relationship.memories.any { it.type == "refusal" })
        assertEquals(refused, RelationshipEngine.action(refused, "confess").state)
        val boundary = friends().copy(relationship = friends().relationship.copy(consent = ConsentProfile(romanceAllowed = false, boundaries = setOf("no_romance"))))
        assertEquals(boundary, RelationshipEngine.action(boundary, "confess").state)
        assertEquals(boundary, RelationshipEngine.action(boundary, "gift").state)
    }

    @Test fun minorsCannotCreateRomanceIntimacyOrFamilyPlanning() {
        listOf(true, false).forEach { playerMinor ->
            val initial = partners()
            val state = initial.copy(player = initial.player.copy(age = if (playerMinor) 17 else 24),
                companion = initial.companion.copy(age = if (playerMinor) 21 else 17),
                settings = initial.settings.copy(dynasty = true))
            assertNull(RelationshipEngine.action(state, "intimacy").state.relationship.pendingEvent)
            assertNull(DynastyEngine.planFamily(state).state.dynasty.plannedBirthDay)
            assertEquals(RomanceStage.NONE, RelationshipEngine.normalize(state).relationship.romanceStage)
            assertThrows(IllegalArgumentException::class.java) { CharacterEngine.validate(state) }
        }
    }

    @Test fun intimacyRequiresMatureSettingPartnershipBoundariesAndFreshConsent() {
        val partner = partners()
        listOf(
            partner.copy(settings = partner.settings.copy(romance = RomanceMode.OFF)),
            partner.copy(settings = partner.settings.copy(romance = RomanceMode.ROMANCE)),
            partner.copy(relationship = partner.relationship.copy(romanceStage = RomanceStage.ROMANCE)),
            partner.copy(relationship = partner.relationship.copy(consent = partner.relationship.consent.copy(romanceAllowed = false))),
            partner.copy(relationship = partner.relationship.copy(conflict = 50)),
            partner.copy(war = partner.war.copy(commanderConditions = listOf(CommanderCondition(COMPANION_COMMANDER_ID, CombatantStatus.CAPTURED)))),
        ).forEach { blocked -> assertNull(RelationshipEngine.action(blocked, "intimacy").state.relationship.pendingEvent) }
        val invited = RelationshipEngine.action(partner, "intimacy").state
        assertTrue(invited.relationship.pendingEvent!!.adultsOnly)
        assertTrue(invited.relationship.pendingEvent!!.consentRequired)
        assertEquals("EXPLICIT", invited.relationship.pendingEvent!!.presentation)
        val stale = RelationshipEngine.choose(invited.copy(day = invited.day + 1), 0).state
        assertNull(stale.relationship.pendingEvent)
        assertEquals(0, stale.relationship.intimacy)
    }

    @Test fun voluntaryIntimacyIsNarrativeAndCooldownPreventsFarming() {
        val partner = partners()
        val invited = RelationshipEngine.action(partner, "intimacy").state
        val completed = RelationshipEngine.choose(invited, 0).state
        assertEquals(partner.companion.affection, completed.companion.affection)
        assertNull(completed.relationship.intimacyConsentDay)
        assertEquals(1, completed.relationship.memories.count { it.type == "private_evening" })
        assertTrue(completed.relationship.memories.last().tags.contains("EXPLICIT"))
        assertNull(RelationshipEngine.action(completed.copy(day = partner.day + 1), "intimacy").state.relationship.pendingEvent)
        assertNotNull(RelationshipEngine.action(completed.copy(day = partner.day + 2), "intimacy").state.relationship.pendingEvent)
    }

    @Test fun decliningIntimacyDoesNotPunishTheRelationship() {
        val partner = partners()
        val invited = RelationshipEngine.action(partner, "intimacy").state
        val declined = RelationshipEngine.choose(invited, 5).state
        assertEquals(partner.companion.trust, declined.companion.trust)
        assertEquals(partner.companion.affection, declined.companion.affection)
        assertEquals(partner.relationship.commitment, declined.relationship.commitment)
        assertEquals(0, declined.relationship.intimacy)
        assertNull(declined.relationship.intimacyConsentDay)
        assertEquals("respected_boundary", declined.relationship.memories.last().type)
    }

    @Test fun turningMatureModeOffRevokesPendingEncounter() {
        val invited = RelationshipEngine.action(partners(), "intimacy").state
        val off = RelationshipEngine.normalize(invited.copy(settings = invited.settings.copy(romance = RomanceMode.OFF)))
        assertNull(off.relationship.pendingEvent)
        assertNull(off.relationship.intimacyConsentDay)
        assertEquals(0, RelationshipEngine.choose(off, 0).state.relationship.intimacy)
    }

    @Test fun playerAndNpcCanEndRelationshipsAndRevokeFamilyConsent() {
        val initial = partners().copy(dynasty = DynastyState(plannedBirthDay = 270, planningParents = listOf("player", "companion")))
        val broken = RelationshipEngine.action(initial, "breakup").state
        assertEquals(RomanceStage.NONE, broken.relationship.romanceStage)
        assertFalse(broken.relationship.consent.intimacyAllowed)
        assertFalse(broken.relationship.consent.wantsChildren)
        assertNull(broken.dynasty.plannedBirthDay)
        val npc = RelationshipEngine.day(partners().copy(day = 14, resources = partners().resources.copy(food = 0),
            relationship = partners().relationship.copy(conflict = 79))).relationship
        assertEquals(RomanceStage.NONE, npc.romanceStage)
        assertTrue(npc.memories.any { it.type == "npc_breakup" })
    }

    @Test fun persistentCourtSkillsAndMemoriesSurviveSaveLoad() {
        var state = partners().copy(player = partners().player.copy(skillPoints = 4))
        state = CharacterEngine.unlockPerk(state, PlayerPerk.ECONOMY_STEWARDSHIP).state
        state = CharacterEngine.assignOffice(state, CourtOffice.TREASURER, 1).state
        state = RelationshipEngine.choose(RelationshipEngine.action(state, "intimacy").state, 0).state
        val restored = SaveCodec.decode(SaveCodec.encode(state))
        assertEquals(state, restored)
        assertEquals(1, restored.relationship.memories.size)
        assertEquals(1L, restored.court.offices[CourtOffice.TREASURER])
        assertTrue(CharacterEngine.bonuses(restored).gold > 40)
    }

    @Test fun migrationPreservesOldScoresWithoutInventingMarriage() {
        val state = friends()
        val root = Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject
        val legacy = JsonObject(root.filterKeys { it !in setOf("court", "dynasty", "settings", "world", "war", "society", "presentation", "diplomacy", "espionage", "_checksum") } + ("version" to JsonPrimitive(3)))
        val restored = SaveCodec.decode(legacy.toString())
        assertEquals(95, restored.companion.trust)
        assertEquals(95, restored.companion.respect)
        assertEquals(95, restored.companion.affection)
        assertEquals(RomanceStage.NONE, restored.relationship.romanceStage)
        assertEquals(RomanceMode.OFF, restored.settings.romance)
        assertEquals(state.armyPools.sumOf { it.soldiers }, restored.armyPools.sumOf { it.soldiers })
    }

    @Test fun courtOfficesUseRelevantSkillsAndPauseWhileHolderIsAway() {
        var state = friends()
        state = CharacterEngine.assignOffice(state, CourtOffice.TREASURER, 1).state
        val assigned = CharacterEngine.bonuses(state)
        assertTrue(assigned.gold > 0)
        assertEquals(0, assigned.healing)
        val unavailable = state.copy(war = state.war.copy(commanderConditions = listOf(CommanderCondition(1, CombatantStatus.WOUNDED, 20))))
        assertEquals(0, CharacterEngine.bonuses(unavailable).gold)
        val transferred = CharacterEngine.assignOffice(state, CourtOffice.MARSHAL, 1).state
        assertFalse(transferred.court.offices.containsKey(CourtOffice.TREASURER))
        assertTrue(CharacterEngine.bonuses(transferred).morale > 0)
    }

    @Test fun careerPromotionHasMeritCostLeadershipAndRivalConsequences() {
        val initial = friends()
        var state = initial.copy(commanders = initial.commanders.map { if (it.id == 1L) it.copy(victories = 3) else it } + Commander(2, "Rowan", Culture.HUMAN, "knight", victories = 3, trait = "Mutig"))
        state = CharacterEngine.initialize(state)
        val old = state.commanders.first { it.id == 1L }
        val promoted = CharacterEngine.promote(state, 1).state
        assertEquals(350, state.resources.gold - promoted.resources.gold)
        assertEquals(old.leadership + 3, promoted.commanders.first { it.id == 1L }.leadership)
        assertEquals(CommanderRank.GENERAL, promoted.court.characters.first { it.commanderId == 1L }.rank)
        assertTrue(promoted.court.characters.first { it.commanderId == 2L }.rivals.contains(1))
        assertTrue(promoted.commanders.first { it.id == 2L }.loyalty < state.commanders.first { it.id == 2L }.loyalty)
    }

    @Test fun legendsArePersistentAndCannotBeGrantedTwice() {
        val initial = friends()
        val veteran = initial.copy(commanders = initial.commanders.map { if (it.id == 1L) it.copy(victories = 10) else it })
        val once = CharacterEngine.tick(veteran)
        val twice = CharacterEngine.tick(once.copy(day = once.day + 1))
        assertEquals(1, twice.court.legends.size)
        assertTrue(CharacterEngine.bonuses(twice).morale >= 2)
        assertEquals(10, twice.commanders.first { it.id == 1L }.victories)
    }

    @Test fun dynastyOffFreezesAndAdoptionWorksWithoutRomance() {
        val off = friends(RomanceMode.OFF)
        assertEquals(off, DynastyEngine.tick(off))
        val enabled = DynastyEngine.tick(off.copy(settings = off.settings.copy(dynasty = true)))
        val adopted = DynastyEngine.adoptHeir(enabled, "Mira").state
        assertTrue(adopted.dynasty.members.any { it.name == "Mira" && it.adopted && it.age(adopted.day) == 10 })
        val paused = DynastyEngine.tick(adopted.copy(day = adopted.day + 365, settings = adopted.settings.copy(dynasty = false)))
        assertEquals(24, paused.dynasty.members.first { it.id == "player" }.age(paused.day))
        val resumed = DynastyEngine.tick(paused.copy(day = paused.day + 1, settings = paused.settings.copy(dynasty = true)))
        assertEquals(24, resumed.player.age)
        assertEquals(10, resumed.dynasty.members.first { it.name == "Mira" }.age(resumed.day))
    }

    @Test fun familyPlanningNeedsIndependentAdultConsentAndProducesPersistentChild() {
        val initial = partners().copy(settings = partners().settings.copy(dynasty = true))
        assertNull(DynastyEngine.planFamily(initial).state.dynasty.plannedBirthDay)
        var state = DynastyEngine.agreeFamilyPlanning(initial).state
        state = DynastyEngine.planFamily(state).state
        assertEquals(state.day + 270, state.dynasty.plannedBirthDay)
        state = DynastyEngine.tick(state.copy(day = state.day + 270))
        val child = state.dynasty.members.first { it.parents.isNotEmpty() }
        assertEquals(0, child.age(state.day))
        assertEquals(child.id, state.dynasty.heirId)
        assertNull(state.dynasty.plannedBirthDay)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun rulerDeathContinuesCampaignWithMinorHeirAndAdultRegency() {
        val initial = friends(RomanceMode.OFF).copy(player = friends().player.copy(age = 84), settings = friends().settings.copy(romance = RomanceMode.OFF, dynasty = true))
        var state = DynastyEngine.tick(initial)
        state = DynastyEngine.adoptHeir(state, "Mira").state
        state = DynastyEngine.tick(state.copy(day = state.day + 365))
        assertEquals("Mira", state.player.name)
        assertEquals(11, state.player.age)
        assertEquals(1, state.dynasty.successionCount)
        assertNotNull(state.dynasty.regentCommanderId)
        assertEquals(RomanceStage.NONE, state.relationship.romanceStage)
        assertFalse(state.relationship.consent.romanceAllowed)
        assertTrue(state.dynasty.members.first { it.id == "player" }.alive.not())
        val edited = customizePlayer(state, "Mira", 24, "Grenzrüstung", "Langschwert")
        assertEquals(11, edited.player.age)
        assertEquals(11, edited.dynasty.members.first { it.id == edited.dynasty.rulerId }.age(edited.day))
        assertEquals(RomanceStage.NONE, RelationshipEngine.action(edited, "confess").state.relationship.romanceStage)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun dayTickIsIdempotentAndNoNpcMinorRomanceCanEmerge() {
        val initial = friends()
        var state = initial.copy(day = 140, commanders = initial.commanders + Commander(2, "Junges Talent", Culture.HUMAN, "knight"))
        state = CharacterEngine.initialize(state)
        state = state.copy(court = state.court.copy(characters = state.court.characters.map { if (it.commanderId == 2L) it.copy(age = 16) else it }))
        val tick = CharacterEngine.tick(state)
        assertEquals(tick, CharacterEngine.tick(tick))
        assertTrue(tick.court.socialLinks.all { it.kind !in listOf(SocialKind.ROMANCE, SocialKind.MARRIAGE) })
    }

    @Test fun casualMemoriesStayBoundedWithoutErasingFirstKissOrMarriage() {
        var state = RelationshipEngine.remember(friends(), "kiss", "Euer erster Kuss.", 5)
        state = RelationshipEngine.remember(state, "marry", "Ihr versprecht euch die gemeinsame Zukunft.", 5)
        repeat(250) { index -> state = RelationshipEngine.remember(state.copy(day = 11 + index), "walk", "Ein ruhiger Spaziergang.") }
        assertEquals(200, state.relationship.memories.size)
        assertTrue(state.relationship.memories.any { it.type == "kiss" })
        assertTrue(state.relationship.memories.any { it.type == "marry" })
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test fun fallenCompanionKeepsCareerAndGriefWhileRevokingIntimacy() {
        val initial = partners()
        val fallen = initial.copy(war = initial.war.copy(commanderConditions = listOf(CommanderCondition(COMPANION_COMMANDER_ID, CombatantStatus.DEAD))))
        val state = CharacterEngine.recordBattle(fallen, listOf(COMPANION_COMMANDER_ID), false, 100)
        assertFalse(state.court.characters.first { it.commanderId == COMPANION_COMMANDER_ID }.alive)
        assertEquals(RomanceStage.NONE, state.relationship.romanceStage)
        assertTrue(state.relationship.memories.any { it.type == "bereavement" })
        assertFalse(state.relationship.consent.intimacyAllowed)
        assertNull(RelationshipEngine.action(state, "intimacy").state.relationship.pendingEvent)
        assertEquals(initial.commanders.first { it.id == COMPANION_COMMANDER_ID }.battlesFought, state.commanders.first { it.id == COMPANION_COMMANDER_ID }.battlesFought)
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }
}
