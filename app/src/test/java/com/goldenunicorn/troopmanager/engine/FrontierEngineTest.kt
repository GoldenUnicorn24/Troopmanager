package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontierEngineTest {
    @Test
    fun earlyCampaignDoesNotSpawnUruksOrTaoTei() {
        var state = GameEngine.newGame("Leon", 23, com.goldenunicorn.troopmanager.model.Species.HALF_ELF, null)
        repeat(12) { state = GameEngine.advanceDay(state).state }
        assertTrue(state.frontier.hordes.all { it.kind == HordeKind.ORC })
        assertTrue(state.frontier.hordes.all { it.soldiers <= 80 })
        val observed = FrontierEngine.tick(state(day = 8))
        assertTrue(observed.frontier.hordes.any { it.kind == HordeKind.ORC && it.soldiers in 1..80 })
    }

    @Test
    fun customUnitsStayLockedAtTheStart() {
        val state = GameEngine.newGame("Leon", 23, com.goldenunicorn.troopmanager.model.Species.HALF_ELF, null)
        assertFalse(FrontierEngine.customUnitsUnlocked(state))
        val denied = FrontierEngine.designUnit(state, "Garde", com.goldenunicorn.troopmanager.model.Culture.HUMAN, 8, 8, 4, null)
        assertTrue(denied.state.frontier.designs.isEmpty())
    }

    @Test
    fun allySaleTravelsBeforeAddingSoldiersExactlyOnce() {
        val state = GameEngine.newGame("Leon", 23, com.goldenunicorn.troopmanager.model.Species.HALF_ELF, null)
        val before = state.armySize
        val bought = GameEngine.buyReinforcements(state, AllyPeople.GOLD_ELVES, 40).state
        assertEquals(before, bought.armySize)
        assertEquals(40, bought.frontier.reinforcements.single().amount)
        val movement = bought.frontier.reinforcements.single()
        assertTrue(movement.arrivalDay > bought.day)
        val onRoad = FrontierEngine.tick(bought.copy(day = movement.arrivalDay - 1))
        assertEquals(before, onRoad.armySize)
        val arrived = FrontierEngine.tick(onRoad.copy(day = movement.arrivalDay))
        assertEquals(before + 40, arrived.armySize)
        assertTrue(arrived.frontier.reinforcements.isEmpty())
        assertEquals(arrived.armySize, FrontierEngine.tick(arrived).armySize)
    }

    private fun state(day: Int = 1) = GameState(
        day = day, player = CharacterProfile("Leon"), armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100)),
        companion = CompanionProfile(met = true),
    )

    @Test
    fun patrolReservesRealTroopsAndReturnsThemWithoutDuplication() {
        val original = state()
        val dispatched = FrontierEngine.sendPatrol(original, 60, 2).state
        assertEquals(100, dispatched.armySize)
        assertEquals(40, dispatched.homeArmySize)
        assertEquals(40, dispatched.homeSoldiers(UnitType.HUMAN_SWORD))
        assertEquals(60, dispatched.frontier.patrol!!.units.sumOf { it.amount })
        val first = FrontierEngine.tick(dispatched.copy(day = 2))
        val returned = FrontierEngine.tick(first.copy(day = 3))
        assertEquals(100, returned.armySize)
        assertEquals(100, returned.homeArmySize)
        assertEquals(null, returned.frontier.patrol)
    }

    @Test
    fun patrolCannotMakeLargeHordeDisappear() {
        val dispatched = FrontierEngine.sendPatrol(state(), 60, 5).state
        val large = HordeBanner("large", HordeKind.URUK, "Großhorde", 900, "Grenzland", 1)
        val arrived = FrontierEngine.tick(dispatched.copy(day = 2, frontier = dispatched.frontier.copy(hordes = listOf(large))))
        assertEquals(100, arrived.armySize)
        assertEquals(60, arrived.frontier.patrol!!.soldiers)
        assertEquals(900, arrived.battleSession!!.enemyStart)
    }

    @Test
    fun patrolInterceptsSmallRaidWithRealLosses() {
        val dispatched = FrontierEngine.sendPatrol(state(), 60, 5).state
        val small = HordeBanner("small", HordeKind.ORC, "Überfall", 30, "Grenzland", 1)
        val caught = FrontierEngine.tick(dispatched.copy(day = 2, frontier = dispatched.frontier.copy(hordes = listOf(small))))
        assertTrue(caught.frontier.hordes.isEmpty())
        assertTrue(caught.armySize < dispatched.armySize)
        assertTrue(caught.frontier.patrol!!.soldiers < 60)
        assertEquals(40, caught.homeArmySize)
        assertEquals(null, caught.battleSession)
    }

    @Test
    fun invasionBannerReferencesOneExistingWorldArmy() {
        val original = WorldEngine.initialize(state(50))
        val army = original.world.armies.first { it.factionId == "ash_covenant" }
        val withInvasion = original.copy(invasion = Invasion(EnemyType.URUK, 58, army.total, 50, emptyList(), worldArmyId = army.id))
        val projected = FrontierEngine.tick(withInvasion)
        assertEquals(original.world.armies.size, projected.world.armies.size)
        val banner = projected.frontier.hordes.single()
        assertEquals(army.id, banner.worldArmyId)
        assertEquals(army.total, banner.soldiers)
        assertTrue(banner.jointWith != null)
    }

    @Test
    fun taoTeiThreatensAnotherRealmWithoutDuplicatingArmies() {
        val original = WorldEngine.initialize(state(90))
        val army = original.world.armies.first { it.factionId == "verdant_compact" }
        val place = original.world.place(army.regionId)!!
        val owner = original.world.faction(place.ownerId)!!
        val threatened = FrontierEngine.tick(original.copy(invasion = Invasion(EnemyType.TAO_TEI, 98, army.total, 90, emptyList(), worldArmyId = army.id)))
        assertEquals(original.world.armies.size, threatened.world.armies.size)
        assertTrue(threatened.world.faction(owner.id)!!.food < owner.food)
        assertTrue(threatened.chronicle.any { it.title == "Tao Tei bedrohen anderes Reich" })
        val repeated = FrontierEngine.tick(threatened.copy(day = 91))
        assertEquals(threatened.world.faction(owner.id)!!.food, repeated.world.faction(owner.id)!!.food)
    }

    @Test
    fun aidRequestsCostTrustAndContributionImprovesTerms() {
        val emergency = state().copy(realm = state().realm.copy(threat = 70))
        val helped = FrontierEngine.requestAid(emergency, AllyPeople.GOLD_ELVES).state
        val trust = helped.frontier.allies.first { it.people == AllyPeople.GOLD_ELVES }.trust
        assertTrue(trust < 55)
        assertEquals(emergency.armySize, helped.armySize)
        val contributed = FrontierEngine.contributeToAlly(helped, AllyPeople.GOLD_ELVES).state
        assertEquals(trust + 8, contributed.frontier.allies.first { it.people == AllyPeople.GOLD_ELVES }.trust)
        assertTrue(FrontierEngine.reinforcementPrice(contributed, AllyPeople.GOLD_ELVES) <= FrontierEngine.reinforcementPrice(helped, AllyPeople.GOLD_ELVES))
    }

    @Test
    fun weaponNeedsMaterialsAndBuildTimeThenConsumesFiniteCharges() {
        val original = state()
        val emptyGoods = original.copy(militaryStock = original.militaryStock.copy(siegeParts = 0))
        assertEquals(emptyGoods, FrontierEngine.buildWallWeapon(emptyGoods, WallWeaponType.BALLISTA).state)
        var building = FrontierEngine.buildWallWeapon(original, WallWeaponType.BALLISTA).state
        assertEquals(0, building.frontier.weapons.first { it.type == WallWeaponType.BALLISTA }.count)
        repeat(WallWeaponType.BALLISTA.days) { building = FrontierEngine.tick(building.copy(day = building.day + 1)) }
        val stock = building.frontier.weapons.first { it.type == WallWeaponType.BALLISTA }
        assertEquals(1, stock.count)
        assertTrue(stock.ammunition > 0)
        var battle = BattleEngine.start(building, EnemyType.ORC, Tactic.FORTIFY, seed = 123, enemyStrength = 1000).state.battleSession!!
        val enemyStart = battle.enemyRemaining
        var firing = building
        repeat(30) {
            val volley = FrontierEngine.fireWallWeapons(firing, battle)
            firing = volley.state
            battle = volley.battle
        }
        assertTrue(battle.enemyRemaining < enemyStart)
        assertEquals(0, firing.frontier.weapons.first { it.type == WallWeaponType.BALLISTA }.ammunition)
        val dryVolley = FrontierEngine.fireWallWeapons(firing, battle)
        assertEquals(battle.enemyRemaining, dryVolley.battle.enemyRemaining)
        assertTrue(battle.log.any { "Mauerballiste" in it.text && it.enemyLosses > 0 })
    }

    @Test
    fun wallWeaponsDoNotFireInFieldBattlesAndNeedRepairAfterSiege() {
        val original = state().copy(frontier = FrontierState(weapons = listOf(WallWeaponStock(WallWeaponType.BALLISTA, count = 1, ammunition = 5))))
        val field = BattleEngine.start(original, EnemyType.ORC, Tactic.FORTIFY, seed = 2, enemyStrength = 500, location = "Offenes Feld").state.battleSession!!
        assertEquals(field, FrontierEngine.fireWallWeapons(original, field).battle)
        val siege = field.copy(location = original.realm.settlementName, status = BattleStatus.VICTORY)
        val damaged = FrontierEngine.afterBattle(original, siege)
        assertTrue(damaged.frontier.weapons.single().integrity < 100)
        val repaired = FrontierEngine.repairWallWeapon(damaged, WallWeaponType.BALLISTA).state
        assertEquals(100, repaired.frontier.weapons.single().integrity)
        assertTrue(repaired.militaryStock.siegeParts < damaged.militaryStock.siegeParts)
    }

    @Test
    fun craneWinchNeedsRealDeployedCraneGuard() {
        val original = state().copy(frontier = FrontierState(weapons = listOf(WallWeaponStock(WallWeaponType.CRANE_WINCH, count = 1, ammunition = 5))))
        val infantryBattle = BattleEngine.start(original, EnemyType.ORC, Tactic.FORTIFY, seed = 4, enemyStrength = 500).state.battleSession!!
        assertTrue(infantryBattle.contingents.none { it.type == UnitType.CRANE_GUARD })
        val unused = FrontierEngine.fireWallWeapons(original, infantryBattle)
        assertEquals(infantryBattle.enemyRemaining, unused.battle.enemyRemaining)
        assertEquals(5, unused.state.frontier.weapons.single().ammunition)
        val withGuard = ArmyEngine.add(original, UnitType.CRANE_GUARD, 20)
        val guardBattle = BattleEngine.start(withGuard, EnemyType.ORC, Tactic.FORTIFY, seed = 4, enemyStrength = 500).state.battleSession!!
        assertTrue(guardBattle.contingents.any { it.type == UnitType.CRANE_GUARD && it.soldiers > 0 })
        val used = FrontierEngine.fireWallWeapons(withGuard, guardBattle)
        assertTrue(used.battle.enemyRemaining < guardBattle.enemyRemaining)
        assertEquals(4, used.state.frontier.weapons.single().ammunition)
    }

    @Test
    fun customEquipmentIsBoundedAndTrainingCreatesOneRealPool() {
        val sentinel = HordeBanner("test-sentinel", HordeKind.ORC, "Fernes Testlager", 1, "Fernland", 100, jointWith = "test")
        val original = state(40).copy(frontier = FrontierState(hordes = listOf(sentinel), lastRaidDay = 40))
        val designed = FrontierEngine.designUnit(original, "Grenzgarde", Culture.HUMAN, CustomUnitRole.INFANTRY, CustomWeapon.SWORD, CustomArmor.PLATE, true).state
        val design = designed.frontier.designs.single()
        assertEquals(original.armySize, designed.armySize)
        var training = FrontierEngine.trainCustomUnit(designed, design.id, 20).state
        assertEquals(original.armySize, training.armySize)
        assertEquals(20, training.frontier.designs.single().trainingAmount)
        assertTrue(training.militaryStock.armor < original.militaryStock.armor)
        repeat(design.trainingDays) { training = FrontierEngine.tick(training.copy(day = training.day + 1)) }
        assertEquals(original.armySize + 20, training.armySize)
        assertEquals(20, training.frontier.designs.single().soldiers)
        assertTrue(FrontierEngine.customUnitPowerFactor(training, UnitType.HUMAN_SWORD) > 1.0)
        assertEquals(training.armySize, FrontierEngine.tick(training).armySize)
        val exploit = FrontierEngine.designUnit(original, "Exploit", Culture.HUMAN, Int.MAX_VALUE, 100, 100, null).state
        assertTrue(exploit.frontier.designs.isEmpty())
    }

    @Test
    fun customDesignLossesAreProportionalToPoolRatherThanWholeAwayDesign() {
        val original = state(25)
        val design = FrontierEngine.customUnitPreview("Garde", Culture.HUMAN, CustomUnitRole.INFANTRY, CustomWeapon.SWORD, CustomArmor.PLATE, true).copy(id = 1, soldiers = 100)
        val afterLosses = original.copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 90)), frontier = FrontierState(designs = listOf(design)))
        assertEquals(90, FrontierEngine.afterTroopLosses(original, afterLosses).frontier.designs.single().soldiers)
    }

    @Test
    fun twoDesignsCanTrainConcurrentlyUsingExactlyTheirAvailableRecruits() {
        val original = state(40)
        var designed = FrontierEngine.designUnit(original, "Garde eins", Culture.HUMAN, CustomUnitRole.INFANTRY, CustomWeapon.SWORD, CustomArmor.MAIL, true).state
        designed = FrontierEngine.designUnit(designed, "Garde zwei", Culture.HUMAN, CustomUnitRole.INFANTRY, CustomWeapon.SPEAR, CustomArmor.MAIL, true).state
        // Two designs consume two founding recruits each, leaving 38 of the original 42 for training.
        val first = FrontierEngine.trainCustomUnit(designed, designed.frontier.designs[0].id, 19).state
        val second = FrontierEngine.trainCustomUnit(first, first.frontier.designs[1].id, 19).state
        assertEquals(38, second.frontier.designs.sumOf { it.trainingAmount })
        assertEquals(0, second.population.humanRecruits)
        assertEquals(0, ArmyEngine.recruitable(second, Culture.HUMAN))
    }

    @Test
    fun patrolLossesAlsoReduceNamedCustomSubsets() {
        val original = state(25)
        val design = FrontierEngine.customUnitPreview("Garde", Culture.HUMAN, CustomUnitRole.INFANTRY, CustomWeapon.SWORD, CustomArmor.MAIL, true).copy(id = 1, soldiers = 100)
        val designed = original.copy(frontier = FrontierState(designs = listOf(design)))
        val patrol = FrontierEngine.sendPatrol(designed, 60, 5).state
        val raid = HordeBanner("raid", HordeKind.ORC, "Überfall", 30, "Grenzland", 1)
        val after = FrontierEngine.tick(patrol.copy(day = 26, frontier = patrol.frontier.copy(hordes = listOf(raid))))
        assertEquals(after.armySize, after.frontier.designs.single().soldiers)
    }

    @Test
    fun simultaneousRaidsSeeUpdatedPatrolReservationsAfterLosses() {
        val original = state(25).copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 61)))
        val patrol = FrontierEngine.sendPatrol(original, 60, 5).state
        val hordes = listOf(HordeBanner("small", HordeKind.ORC, "Überfall", 30, "Grenzland", 1),
            HordeBanner("large", HordeKind.URUK, "Horde", 200, "Grenzland", 1))
        val after = FrontierEngine.tick(patrol.copy(day = 26, frontier = patrol.frontier.copy(hordes = hordes)))
        assertEquals(1, after.homeArmySize)
        assertTrue(after.battleSession != null)
        assertFalse(after.chronicle.any { it.title == "Grenzüberfall plündert Vorräte" })
    }

    @Test
    fun bondTrainingRequiresPresenceAndNeverStartsRomance() {
        var trained = state()
        repeat(10) { trained = FrontierEngine.trainWithCompanion(trained.copy(day = it + 1)).state }
        assertEquals(10, trained.frontier.bond.sessions)
        assertEquals(trained.companion.sword, trained.commanders.first { it.id == COMPANION_COMMANDER_ID }.sword)
        assertEquals(trained.companion.tactics, trained.commanders.first { it.id == COMPANION_COMMANDER_ID }.tactics)
        assertEquals(RomanceStage.NONE, trained.relationship.romanceStage)
        assertEquals("Kampfgefährten", trained.frontier.bond.stage)
        assertEquals(trained, FrontierEngine.trainWithCompanion(trained).state)
        val injured = state().copy(war = WarState(playerCondition = CombatantStatus.WOUNDED))
        assertFalse(FrontierEngine.canTrainWithCompanion(injured))
        val travelling = PresenceEngine.travel(state(), together = true).state
        assertFalse(FrontierEngine.canTrainWithCompanion(travelling))
    }
    @Test
    fun hordeGrowthAndSpawnsArePersisted() {
        val horde = HordeBanner("grow", HordeKind.ORC, "Wachsendes Lager", 40, "Vorland", 3, regionId = "keep")
        val original = state(10).copy(frontier = FrontierState(hordes = listOf(horde), lastTickDay = 9))
        val next = FrontierEngine.tick(original)
        assertTrue(next.frontier.hordes.first { it.id == "grow" }.soldiers > 40)
    }

    @Test
    fun failedCampAssaultConsumesWoodAndRealSoldiersButKeepsCamp() {
        val original = state(40).copy(
            resources = state(40).resources.copy(wood = 500),
            frontier = FrontierState(hordes = listOf(HordeBanner("camp", HordeKind.ORC, "Großes Lager", 500, "Vorland", 4))),
        )
        val result = FrontierEngine.assaultCamp(original, "camp").state
        assertEquals(original.resources.wood - 40, result.resources.wood)
        assertTrue(result.armySize < original.armySize)
        assertTrue(result.frontier.hordes.any { it.id == "camp" })
        assertEquals(original.resources.gold, result.resources.gold)
    }

    @Test
    fun campLootIsGrantedOnlyAfterVictory() {
        val original = state(40).copy(
            resources = state(40).resources.copy(wood = 500),
            frontier = FrontierState(hordes = listOf(HordeBanner("camp", HordeKind.ORC, "Kleines Lager", 40, "Vorland", 4))),
        )
        val started = FrontierEngine.assaultCamp(original, "camp").state
        assertEquals(original.resources.gold, started.resources.gold)
        assertTrue(started.frontier.hordes.any { it.id == "camp" })
        val battle = started.battleSession!!.copy(status = BattleStatus.VICTORY)
        val won = FrontierEngine.afterBattle(started, battle)
        assertEquals(original.resources.gold + 80, won.resources.gold)
        assertFalse(won.frontier.hordes.any { it.id == "camp" })
    }

    @Test
    fun huntRemovesRealTroopsAndReducesHorde() {
        val original = state(40).copy(frontier = FrontierState(hordes = listOf(HordeBanner("hunt", HordeKind.ORC, "Jagdgruppe", 60, "Vorland", 2))))
        val hunted = FrontierEngine.huntHorde(original, "hunt").state
        assertTrue(hunted.armySize < original.armySize)
        assertTrue(hunted.frontier.hordes.first { it.id == "hunt" }.soldiers < 60)
    }

    @Test
    fun dailyGoalUsesItsActualObjectiveInsteadOfAnyStandingArmy() {
        val original = state().copy(frontier = FrontierState(dailyGoal = "Patrouille", dailyGoalKey = "patrol"))
        val denied = FrontierEngine.claimDailyGoal(original)
        assertEquals(original.resources.gold, denied.state.resources.gold)
        val patrolling = FrontierEngine.sendPatrol(original, 10, 5).state.copy(frontier = FrontierEngine.sendPatrol(original, 10, 5).state.frontier.copy(dailyGoal = "Patrouille", dailyGoalKey = "patrol"))
        val paid = FrontierEngine.claimDailyGoal(patrolling).state
        assertTrue(paid.resources.gold > patrolling.resources.gold)
        assertTrue(paid.frontier.dailyGoalClaimed)
    }

    @Test
    fun strongCulturePatronageChangesStandingAndIntegration() {
        var current = state().copy(resources = state().resources.copy(gold = 10000))
        repeat(3) { current = FrontierEngine.patronize(current, Culture.HUMAN).state }
        assertTrue(FrontierEngine.cultureStanding(current, Culture.HUMAN) > 50)
        assertTrue(FrontierEngine.cultureIntegration(current, Culture.HUMAN) > 50)
        assertTrue(FrontierEngine.cultureStanding(current, Culture.WOOD_ELF) < 50)
    }

    @Test
    fun captainHiringOffersRealCandidates() {
        val original = state().copy(
            realm = state().realm.copy(buildings = state().realm.buildings + (BuildingType.BARRACKS to 4)),
            resources = state().resources.copy(gold = 5000),
        )
        val candidates = FrontierEngine.captainCandidates(original)
        assertEquals(3, candidates.size)
        assertTrue(candidates.map { it.name }.distinct().size >= 2)
        val hired = FrontierEngine.hireCaptain(original, 1).state
        assertEquals(original.commanders.size + 1, hired.commanders.size)
        assertEquals(candidates[1].name, hired.commanders.last().name)
    }

    @Test
    fun frontierDecisionRequiresAChoiceAndThenClears() {
        val decision = FrontierDecision("caravan", "Karawane", "Test", listOf(
            FrontierDecisionChoice("toll", "Zoll", "+Gold"),
            FrontierDecisionChoice("escort", "Geleit", "+Vertrauen"),
        ))
        val original = state().copy(frontier = FrontierState(pendingDecision = decision))
        val resolved = FrontierEngine.resolveFrontierDecision(original, "toll").state
        assertEquals(null, resolved.frontier.pendingDecision)
        assertTrue(resolved.resources.gold > original.resources.gold)
    }

    @Test
    fun outpostSuppressesGrowthInItsRegion() {
        val world = WorldEngine.initialize(state(40))
        val place = world.world.places.first { it.id != "keep" && it.ownerId in setOf(PLAYER_FACTION, NEUTRAL_FACTION) }
        val built = FrontierEngine.buildOutpost(world.copy(resources = world.resources.copy(gold = 5000, wood = 5000, stone = 5000)), place.id).state
        val horde = HordeBanner("post-test", HordeKind.ORC, "Lager", 40, place.name, 4, regionId = place.id)
        val withHorde = built.copy(day = 41, frontier = built.frontier.copy(hordes = listOf(horde), lastTickDay = 40))
        val next = FrontierEngine.tick(withHorde)
        assertTrue(next.frontier.hordes.first { it.id == "post-test" }.soldiers <= 42)
    }

    @Test
    fun lowCultureStandingCreatesRealWeeklyConsequences() {
        val sentinel = HordeBanner("culture-sentinel", HordeKind.ORC, "Fernes Lager", 1, "Fernland", 100, jointWith = "test")
        val original = state(7).copy(
            frontier = FrontierState(
                hordes = listOf(sentinel),
                lastTickDay = 6,
                lastRaidDay = 7,
                cultureStanding = mapOf(Culture.HUMAN to 20),
                cultureIntegration = mapOf(Culture.HUMAN to 30),
            ),
        )
        val satisfaction = original.city.satisfaction
        val recruits = original.population.humanRecruits
        val next = FrontierEngine.tick(original)
        assertEquals(satisfaction - 2, next.city.satisfaction)
        assertTrue(next.society.culturalTension > original.society.culturalTension)
        assertTrue(next.population.humanRecruits < recruits)
    }

    @Test
    fun frontierStoryPresentationsRotateWithoutChangingDecisionIds() {
        val titles = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        listOf(30, 40, 50, 60, 70, 80).forEach { day ->
            val sentinel = HordeBanner("story-sentinel-$day", HordeKind.ORC, "Fernes Lager", 1, "Fernland", 1000, jointWith = "test")
            val original = state(day).copy(
                frontier = FrontierState(hordes = listOf(sentinel), lastTickDay = day - 1, lastRaidDay = day, lastStoryDay = day - 4),
            )
            val next = FrontierEngine.tick(original)
            val event = next.frontier.pendingDecision!!
            titles += event.title
            ids += event.id
        }
        assertEquals(1, ids.size)
        assertTrue(titles.size >= 4)
    }

}
