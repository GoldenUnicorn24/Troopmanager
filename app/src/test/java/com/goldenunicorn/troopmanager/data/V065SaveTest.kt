package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.engine.CharacterEngine
import com.goldenunicorn.troopmanager.engine.DynastyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.RelationshipEngine
import com.goldenunicorn.troopmanager.model.*
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Old releases share schema 4; compatibility must not depend on incrementing the schema number. */
class V065SaveTest {
    private fun game(): GameState {
        var state = GameEngine.newGame("Leon", 26, Species.HUMAN, "content://local/leon")
        state = state.copy(day = 70, companion = state.companion.copy(met = true, trust = 88, respect = 79, affection = 72),
            settings = state.settings.copy(dynasty = true))
        return DynastyEngine.initialize(CharacterEngine.initialize(RelationshipEngine.syncCommander(state)))
    }

    private val newRelationshipFields = setOf("personality", "issues", "arcs", "eventLastDay", "familyLastDay", "delayedConsequences", "lastDirectorDay", "lastReactionDay", "observedPolicy")
    private val newDynastyFields = setOf("plannedChildName", "pendingFamilyEvent", "familyEventHistory", "familyEventCooldowns", "lastFamilyEventDay", "legitimacyCauses", "observedVictories", "lastLegitimacyReviewDay", "regencyReason", "regencyForMemberId", "regencyConflict", "lastRegencyReviewDay")
    private val newChildFields = setOf("traits", "stewardship", "medicine", "tactics", "mentorId", "mentorBond", "educationPath", "lastEducationDay", "educationLog", "adultCommanderId")
    private val newEventFields = setOf("options", "category", "topic", "variantId", "arcId", "arcStage", "createdDay")

    private fun checked(payload: JsonObject): String {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return JsonObject(payload + ("_checksum" to JsonPrimitive(digest))).toString()
    }

    private fun legacyV4(state: GameState, release: Int): String {
        val encoded = Json.parseToJsonElement(SaveCodec.encode(state)).jsonObject
        var payload = JsonObject(encoded.filterKeys { it !in setOf("_checksum", "frontier", "coRuler", "presence", "journal") })
        if (release == 61) payload = JsonObject(payload.filterKeys { it !in setOf("foundingCultures", "militaryStock", "doctrine", "research", "dailyReport", "commanderEvents", "occupations") })
        val relationship = payload.getValue("relationship").jsonObject
        var oldRelationship = JsonObject(relationship.filterKeys { it !in newRelationshipFields })
        (oldRelationship["pendingEvent"] as? JsonObject)?.let { event ->
            oldRelationship = JsonObject(oldRelationship + ("pendingEvent" to JsonObject(event.filterKeys { it !in newEventFields })))
        }
        val dynasty = payload.getValue("dynasty").jsonObject
        val members = dynasty.getValue("members").jsonArray.map { JsonObject(it.jsonObject.filterKeys { key -> key !in newChildFields }) }
        val oldDynasty = JsonObject(dynasty.filterKeys { it !in newDynastyFields } + ("members" to JsonArray(members)))
        val court = payload.getValue("court").jsonObject
        val oldCourt = JsonObject(court + ("socialLinks" to JsonArray(court.getValue("socialLinks").jsonArray.map { JsonObject(it.jsonObject - "cause") })))
        return checked(JsonObject(payload + mapOf("relationship" to oldRelationship, "dynasty" to oldDynasty, "court" to oldCourt)))
    }

    @Test fun v61V62AndV63PayloadsLoadWithDefaultsAndKeepAllThirtyFreeSkillPoints() {
        val child = FamilyMember("family_3", "Mira", 1, initialAge = 10, parents = listOf("player", "companion"))
        val original = game().let { it.copy(
            relationship = it.relationship.copy(pendingEvent = RelationshipEvent("politics", "Ein Gesandter", "Ihre Rolle wird öffentlich angezweifelt.")),
            dynasty = it.dynasty.copy(members = it.dynasty.members + child, heirId = child.id)) }
        for (release in listOf(61, 62, 63)) {
            val raw = legacyV4(original, release)
            val restored = SaveCodec.decode(raw)
            assertEquals("v0.$release must keep the explicit free-point grant", 30, restored.player.skillPoints)
            assertEquals(original.resources, restored.resources)
            assertEquals(original.armyPools, restored.armyPools)
            assertEquals(original.player, restored.player)
            assertEquals(original.companion, restored.companion)
            assertEquals(FrontierState(), restored.frontier)
            assertEquals(CoRulerState(), restored.coRuler)
            assertEquals(PresenceState(), restored.presence)
            assertEquals(QuestJournalState(), restored.journal)
            assertTrue(restored.relationship.issues.isEmpty())
            assertTrue(restored.relationship.arcs.isEmpty())
            assertEquals(CompanionPersonalityProfile(), restored.relationship.personality)
            assertEquals("politics", restored.relationship.pendingEvent!!.key)
            assertTrue(restored.relationship.pendingEvent!!.options.isEmpty())
            val restoredChild = restored.dynasty.members.single { it.id == child.id }
            assertEquals("Mira", restoredChild.name)
            assertEquals(10, restoredChild.age(restored.day))
            assertEquals("teacher", restoredChild.mentorId)
            assertNull(restoredChild.educationPath)
            assertTrue(restoredChild.educationLog.isEmpty())
            assertEquals(restored, SaveCodec.decode(SaveCodec.encode(restored)))
        }
    }

    @Test fun legacyCoRulersAndEstablishedBoundariesArePreservedWithoutAutonomousPermission() {
        val original = game().let { it.copy(relationship = it.relationship.copy(romanceStage = RomanceStage.CO_RULERS, commitment = 90,
            consent = it.relationship.consent.copy(intimacyAllowed = false, wantsChildren = false,
                boundaries = setOf("ask_each_time", "no_intimacy", "no_children")))) }
        val restored = SaveCodec.decode(legacyV4(original, 63))
        assertEquals(RomanceStage.CO_RULERS, restored.relationship.romanceStage)
        assertEquals(original.relationship.consent, restored.relationship.consent)
        assertFalse(restored.coRuler.delegation.enabled)
        assertTrue(restored.coRuler.delegation.allowedActions.isEmpty())
        assertNull(restored.relationship.intimacyConsentDay)
    }

    @Test fun expandedCampaignRoundTripsEveryNewDomainAndPendingChoice() {
        val base = game()
        val memory = RelationshipMemory("memory_68_oath", 68, "old_oath", text = "Ein Versprechen wurde erneuert.", tags = setOf("relationship"), emotionalWeight = 4)
        val issue = RelationshipIssue("issue_command", "Eigene Verantwortung", 28, 60, memoryIds = listOf(memory.id), ignoredCount = 1, lastDiscussedDay = 68)
        val choices = listOf(
            RelationshipChoice("listen", "Den Kameraden zuerst anhören", "Ihre alte Verpflichtung wird ernst genommen.", priority = CompanionPriority.RECOGNITION),
            RelationshipChoice("resources", "Die Hilfe mit dem Rat planen", "Eine verlässliche Hilfe entsteht gemeinsam.", consequenceHint = "Später braucht die Reise Vorräte.", delayedEffect = RelationshipEffect(food = -20), delayDays = 4, delayedText = "Der Hilfszug erhält Vorräte."),
            RelationshipChoice("later", "Heute Abstand und Ruhe lassen", "Eure Grenze wird ohne Vorwurf respektiert.", boundary = true),
        )
        val event = RelationshipEvent("oath_arrival", "Ein Kamerad aus früheren Tagen", "Ein altes Versprechen braucht eine neue Antwort.",
            options = choices, category = "personal", topic = issue.topic, arcId = "old_oath", arcStage = 1, createdDay = 70)
        val child = FamilyMember("family_3", "Mira", 1, initialAge = 12, parents = listOf("player", "companion"),
            traits = setOf(ChildTrait.CURIOUS, ChildTrait.COMPASSIONATE), educationPath = EducationPath.MEDICINE,
            mentorId = "companion", mentorBond = 73, medicine = 45, stewardship = 36, lastEducationDay = 65,
            educationLog = listOf(FamilyDevelopmentEntry(65, "Die Vorräte im Lazarett gemeinsam erfasst.")))
        val familyEvent = FamilyEducationEvent("hospital_visit", child.id, "Im Lazarett", "Mira hört den Verwundeten zu.", 70,
            listOf(FamilyEducationChoice("care", "Eine sichere Pflegeaufgabe begleiten", "Fürsorge wächst.", medicine = 4),
                FamilyEducationChoice("logistics", "Verbände zählen", "Versorgung wird verständlich.", stewardship = 4),
                FamilyEducationChoice("letters", "Briefe schreiben", "Die Familien werden gehört.", diplomacy = 4)))
        val wall = WallWeaponStock(WallWeaponType.BALLISTA, count = 2, ammunition = 7, integrity = 84, reloadRounds = 1, section = BattleSection.LEFT)
        val design = CustomUnitDesign(4, "Miras Schildwache", Culture.HUMAN, 8, 10, 1, colorHex = "#5189B7", soldiers = 12)
        val activeTask = JournalTask("arc_old_oath", "Eine alte Verpflichtung", "Ein früherer Kamerad wartet auf eine Antwort.", JournalCategory.PERSONAL, GameDestination.RULERS, 60, 78)
        val closedTask = JournalTask("wall_complete", "Eine Mauerballiste", "Der Bau wurde abgeschlossen.", JournalCategory.MILITARY, GameDestination.FRONTIER, 60, closedDay = 64, outcome = "Einsatzbereit")
        val record = GovernmentDecisionRecord("grain", 68, "Vorräte für die nächste Woche", "400 Nahrung einkaufen", base.companion.name, "Die sichere Versorgung hat Vorrang.", autonomous = true, goldSpent = 200)
        val expanded = base.copy(
            relationship = base.relationship.copy(romanceStage = RomanceStage.CO_RULERS, commitment = 90, conflict = 28, pendingEvent = event, memories = listOf(memory),
                personality = CompanionPersonalityProfile(listOf(CompanionTrait.DIPLOMATIC, CompanionTrait.CAUTIOUS, CompanionTrait.PRAGMATIC),
                    listOf(CompanionPriority.FAMILY, CompanionPriority.TRADE, CompanionPriority.DIPLOMACY), listOf("Die Herkunft mit dem neuen Hof verbinden"), setOf("gebrochene Hilfsversprechen")),
                issues = listOf(issue), arcs = listOf(RelationshipArcState("old_oath", 1, 60, 70, decisions = listOf("listen"), memoryIds = listOf(memory.id))),
                eventLastDay = mapOf("old_oath" to 68), familyLastDay = mapOf("personal" to 68), lastDirectorDay = 70,
                delayedConsequences = listOf(RelationshipDelayedConsequence("supply_oath", 74, "Der Hilfszug erhält Vorräte.", RelationshipEffect(food = -20), memory.id))),
            frontier = base.frontier.copy(weapons = listOf(wall), designs = listOf(design), nextDesignId = 5,
                reinforcements = listOf(AllyReinforcement(7, AllyPeople.GOLD_ELVES, UnitType.GOLD_SPEAR, 20, "Goldelbenhof", "keep", 5, 75, departureDay = 70)), nextReinforcementId = 8,
                hordes = listOf(HordeBanner("raid-66", HordeKind.ORC, "Orks aus dem Grenzwald", 48, "Grenzwald", 2, estimateMinimum = 36, estimateMaximum = 60)),
                patrol = BorderPatrol(10, 3, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 10))), bond = BondVisual(5, 69, "Eingespieltes Duo", 1)),
            coRuler = base.coRuler.copy(portfolio = CoRulerPortfolio.ECONOMY,
                delegation = DelegationPolicy(true, setOf(DelegatedAction.SUPPLY_PURCHASE), 250, 500),
                pendingCaseIds = listOf("tax"), resolvedDays = mapOf("grain" to 68), decisions = listOf(record), lastTickDay = 69,
                spendingDay = 68, goldSpentToday = 200, actingRuler = base.companion.name),
            presence = PresenceState(PersonPresence(PresenceLocation.TRAVEL, "Gesandtenreise", false, 73, "travel:70"),
                PersonPresence(PresenceLocation.PALACE, "Regiert im Ressort Wirtschaft", true), 70),
            dynasty = base.dynasty.copy(members = base.dynasty.members + child, heirId = child.id, pendingFamilyEvent = familyEvent,
                plannedChildName = "Leander", familyEventCooldowns = mapOf("family_3:market_lesson" to 63),
                familyEventHistory = listOf(FamilyEventOutcome(63, "market_lesson", child.id, "Die Vorräte gemeinsam gezählt.")),
                legitimacyCauses = listOf(LegitimacyCause(64, "Stabile Nachfolge", 2))),
            journal = QuestJournalState(listOf(activeTask), listOf(closedTask)),
        )
        val restored = SaveCodec.decode(SaveCodec.encode(expanded))
        assertEquals(expanded, restored)
        assertEquals("later", restored.relationship.pendingEvent!!.options[2].id)
        assertTrue(restored.relationship.pendingEvent!!.options[2].boundary)
        assertEquals(73, restored.dynasty.members.single { it.id == child.id }.mentorBond)
        assertEquals(7, restored.frontier.weapons.single().ammunition)
        assertEquals(73, restored.presence.player.returnDay)
        assertEquals(GameDestination.RULERS, restored.journal.active.single().destination)
        assertEquals(30, restored.player.skillPoints)
    }
}
