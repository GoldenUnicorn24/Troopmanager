package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class V065IntegrationTest {
    private fun game() = GameEngine.newGame("Leon", 23, Species.HALF_ELF, null)

    @Test fun commandJournalLinksActualActionsAndRetainsResolvedHistory() {
        val initial = game().copy(companion = CompanionProfile(met = true),
            relationship = RelationshipState(pendingEvent = RelationshipEvent("conversation", "Am Feuer", "Eine offene Frage", createdDay = 1)))
        val withTask = QuestJournalEngine.refresh(initial)
        val task = withTask.journal.active.single { it.id.startsWith("relationship:") }
        assertEquals(GameDestination.RULERS, task.destination)
        val answered = RelationshipEngine.choose(withTask, 2).state
        assertNull(answered.relationship.pendingEvent)
        val refreshed = QuestJournalEngine.refresh(answered)
        assertTrue(refreshed.journal.active.none { it.id.startsWith("relationship:") })
        assertEquals(task.id, refreshed.journal.history.single().id)
        assertEquals(1, refreshed.journal.history.single().closedDay)
        assertEquals(refreshed, QuestJournalEngine.refresh(refreshed))
    }

    @Test fun personalQuestAndFamilyChoiceAppearInOwnDomain() {
        val initial = game().copy(relationship = RelationshipState(arcs = listOf(RelationshipArcState("past", startedDay = 1, nextDay = 12))),
            dynasty = DynastyState(pendingFamilyEvent = FamilyEducationEvent("learn", "child", "Eine Frage", "Ein Lernweg", 1, emptyList())))
        val tasks = QuestJournalEngine.tasks(initial)
        assertTrue(tasks.map { it.destination }.containsAll(setOf(GameDestination.RULERS, GameDestination.FAMILY)))
    }

    @Test fun patrolRemovesOnlyReservedSoldiersFromAllHomeViews() {
        val initial = game()
        val type = initial.armyPools.first().type
        val reserved = initial.copy(frontier = initial.frontier.copy(patrol = BorderPatrol(20, 4, listOf(UnitAllocation(type, 20)))))
        assertEquals(initial.homeArmySize - 20, reserved.homeArmySize)
        assertEquals(initial.homeSoldiers(type) - 20, reserved.homeSoldiers(type))
        assertEquals(initial.directCommand(type) - 20, reserved.directCommand(type))
        assertEquals(initial.armySize, reserved.armySize)
    }

    @Test fun customTrainingConsumesPopulationCapacityExactlyOnce() {
        val initial = game()
        val design = CustomUnitDesign(1, "Stadtwache", Culture.HUMAN, 8, 8, 1, trainingDaysLeft = 8, trainingAmount = 30)
        val training = initial.copy(frontier = initial.frontier.copy(designs = listOf(design)),
            population = initial.population.copy(humanRecruits = 10000))
        val baseline = initial.copy(population = initial.population.copy(humanRecruits = 10000))
        assertEquals(initial.trainingSize + 30, training.trainingSize)
        assertEquals(initial.civilianPopulation - 30, training.civilianPopulation)
        assertEquals(ArmyEngine.recruitable(baseline, Culture.HUMAN) - 30, ArmyEngine.recruitable(training, Culture.HUMAN))
    }

    @Test fun dailyReportNamesDecisionActorAndPresenceWithDeepLink() {
        val before = game().copy(companion = CompanionProfile(met = true))
        val decision = GovernmentDecisionRecord("supply", 1, "Versorgung sichern", "Einkaufen", "Alina", "Vorräte reichen nicht.", true, 180)
        val after = before.copy(coRuler = before.coRuler.copy(decisions = listOf(decision)))
        val report = DailyReportEngine.recordAction(before, after).dailyReport
        val row = report.entries.single { it.category == ReportCategory.RULERS }
        assertTrue(row.title.contains("Alina"))
        assertTrue(row.detail.contains("180 Gold"))
        assertEquals(GameDestination.COUNCIL, row.destination)
    }

    @Test fun independentStartSkillBudgetRemainsThirty() {
        assertEquals(30, game().player.skillPoints)
    }

    @Test fun frontierRaidIsActionableWithoutDuplicatingItsWorldInvasion() {
        val raid = HordeBanner("raid-1", HordeKind.ORC, "Späher melden Orks", 1234, "Wald", 3,
            estimateMinimum = 900, estimateMaximum = 1500)
        val projection = raid.copy(id = "invasion-1", worldArmyId = "enemy-army")
        val initial = game().copy(invasion = Invasion(EnemyType.ORC, 4, 1234, 1, worldArmyId = "enemy-army"),
            frontier = FrontierState(hordes = listOf(raid, projection, raid.copy(id = "hidden", discovered = false))))
        val tasks = QuestJournalEngine.tasks(initial).filter { it.destination == GameDestination.FRONTIER }
        assertEquals("900–1500 geschätzte Gegner", initial.invasionStrengthEstimate())
        val announced = DailyReportEngine.recordAction(game(), initial).dailyReport.entries.single { it.title == "Invasion angekündigt" }
        assertTrue(announced.detail.contains("900–1500"))
        assertFalse(announced.detail.contains("1234"))
        assertEquals(2, tasks.size)
        val warning = tasks.single { it.id == "horde:raid-1" }
        assertEquals(4, warning.dueDay)
        assertTrue(warning.detail.contains("900–1500"))
        assertFalse(tasks.any { it.detail.contains("1234") || it.id.contains("hidden") })
        val tracked = QuestJournalEngine.refresh(initial)
        val tomorrow = tracked.copy(day = 2, frontier = tracked.frontier.copy(hordes = listOf(raid.copy(daysToArrival = 2), projection.copy(daysToArrival = 2))))
        assertEquals(warning, QuestJournalEngine.tasks(tomorrow).single { it.id == warning.id })
        val closed = QuestJournalEngine.refresh(tracked.copy(frontier = tracked.frontier.copy(hordes = listOf(projection))))
        assertEquals("Grenzbedrohung beendet", closed.journal.history.single().outcome)
    }

    @Test fun frontierReportUsesDiscoveryAndUncertainty() {
        val hidden = HordeBanner("hidden", HordeKind.URUK, "Verdecktes Banner", 1234, "Pass", 3, discovered = false,
            estimateMinimum = 900, estimateMaximum = 1500)
        val before = game()
        val concealed = before.copy(frontier = before.frontier.copy(hordes = listOf(hidden)))
        assertTrue(DailyReportEngine.recordAction(before, concealed).dailyReport.entries.none { it.category == ReportCategory.FRONTIER })
        val discovered = concealed.copy(frontier = concealed.frontier.copy(hordes = listOf(hidden.copy(discovered = true))))
        val report = DailyReportEngine.recordAction(concealed, discovered).dailyReport.entries.single { it.category == ReportCategory.FRONTIER }
        assertTrue(report.detail.contains("900–1500"))
        assertFalse(report.detail.contains("1234"))
    }

    @Test fun frontierConstructionAndTrainingKeepTheirJournalDeadlines() {
        val initial = game().copy(frontier = FrontierState(weapons = listOf(WallWeaponStock(WallWeaponType.BALLISTA, daysRemaining = 4)),
            designs = listOf(CustomUnitDesign(1, "Grenzkompanie", Culture.HUMAN, 8, 8, 1, trainingDaysLeft = 4, trainingAmount = 20))))
        val tracked = QuestJournalEngine.refresh(initial)
        val tomorrow = tracked.copy(day = 2, frontier = tracked.frontier.copy(
            weapons = tracked.frontier.weapons.map { it.copy(daysRemaining = 3) },
            designs = tracked.frontier.designs.map { it.copy(trainingDaysLeft = 3) }))
        val tasks = QuestJournalEngine.tasks(tomorrow).filter { it.destination == GameDestination.FRONTIER }
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.dueDay == 5 && it.startedDay == 1 })
        assertEquals(tracked.journal.active.filter { it.destination == GameDestination.FRONTIER }.map { it.id }, tasks.map { it.id })
    }

    @Test fun openRealmAndStoryChoicesLinkToDecisionPage() {
        val initial = game().copy(pendingRealmEvent = RealmEvent("tax", "Abgaben", "Eine offene Entscheidung", "Reich"))
        val withStory = initial.copy(society = initial.society.copy(story = initial.society.story.copy(
            pending = StoryEvent("story-1", StoryKind.entries.first(), "Eine Begegnung", "Eine zweite Frage", 1, 8))))
        val tasks = QuestJournalEngine.tasks(withStory).filter { it.id.startsWith("realm:") || it.id.startsWith("story:") }
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.destination == GameDestination.DECISIONS })
    }

    @Test fun fortifiedWallBattleReplaysItsRealWeaponState() {
        val initial = game().copy(frontier = FrontierState(weapons = listOf(WallWeaponStock(WallWeaponType.BALLISTA, count = 2, ammunition = 10))))
        var battle = BattleEngine.start(initial, EnemyType.ORC, Tactic.FORTIFY, seed = 420, enemyStrength = 80).state
        while (battle.battleSession!!.isActive) {
            val current = battle.battleSession!!
            val decision = current.pendingEvent?.options?.firstOrNull()
            val result = BattleEngine.advance(battle, decision)
            assertNotEquals(battle, result.state)
            battle = result.state
        }
        val record = battle.war.history.last()
        val replay = BattleEngine.replay(record)!!
        assertEquals(record.enemyRemaining, replay.enemyRemaining)
        assertEquals(record.ownRemaining, replay.ownRemaining)
        assertTrue(battle.frontier.weapons.single().ammunition < 10)
    }
}
