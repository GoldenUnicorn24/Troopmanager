package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class V06WarTest {
    private fun army(size: Int = 3000) = GameState(
        player = CharacterProfile("Mira", leadership = 60, tactics = 60),
        population = Population(human = 25_000, wall = 5000),
        armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, size, 40), ArmyUnitPool(UnitType.HUMAN_ARCHER, 800, 40), ArmyUnitPool(UnitType.KNIGHT, 600, 40), ArmyUnitPool(UnitType.DRAGON_ARTILLERY, 100, 40)),
    )
    private fun finished(state: GameState): GameState {
        var current = state
        repeat(24) {
            val session = current.battleSession!!
            if (!session.isActive) return current
            val event = session.pendingEvent
            val choice = event?.options?.firstOrNull { BattleEngine.canOrder(session, it, event.section) && BattleEngine.orderCost(session, it) <= session.commandPoints }
            val next = BattleEngine.advance(current, choice).state
            assertNotEquals("Every legal exchange must progress", current, next)
            current = next
        }
        fail("Battle failed to terminate in bounded exchanges")
        return current
    }

    @Test fun terrainChangesActualCavalryAndArcherCombat() {
        val cavalry = army().copy(armyPools = listOf(ArmyUnitPool(UnitType.KNIGHT, 3000)), population = Population(human = 5000))
        val deployments = listOf(BattleDeployment(null, BattleSection.CENTER, listOf(UnitAllocation(UnitType.KNIGHT, 3000))))
        fun step(terrain: BattleTerrain): BattleSession {
            val started = BattleEngine.start(cavalry, EnemyType.URUK, Tactic.AGGRESSIVE, deployments, 11, 3000, BattleSection.entries.associateWith { terrain }).state
            val contact = started.copy(battleSession = started.battleSession!!.copy(fronts = started.battleSession!!.fronts.map { it.copy(enemyDistance = 0) }))
            return BattleEngine.advance(contact).state.battleSession!!
        }
        assertTrue(step(BattleTerrain.PLAIN).enemyRemaining < step(BattleTerrain.FOREST).enemyRemaining)
        assertTrue(BattleEngine.terrainMultiplier(BattleTerrain.HILL, UnitType.HUMAN_ARCHER, BattlePhase.RANGED) > BattleEngine.terrainMultiplier(BattleTerrain.PLAIN, UnitType.HUMAN_ARCHER, BattlePhase.RANGED))
    }

    @Test fun proactiveOrdersConsumePointsAndCannotOverspend() {
        val started = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 22, enemyStrength = 5000).state
        val original = started.battleSession!!
        val scarce = started.copy(battleSession = original.copy(commandPoints = 0))
        assertEquals(scarce, BattleEngine.order(scarce, BattleDecision.CAVALRY_CHARGE, BattleSection.CENTER).state)
        val charged = BattleEngine.order(started, BattleDecision.CAVALRY_CHARGE, BattleSection.CENTER).state.battleSession!!
        assertEquals(original.commandPoints - BattleEngine.orderCost(original, BattleDecision.CAVALRY_CHARGE) + BattleEngine.commandRegeneration(started, original), charged.commandPoints)
        assertEquals(5, charged.minute)
        assertEquals(BattleDecision.CAVALRY_CHARGE, charged.inputs.single().decision)
    }

    @Test fun zeroPointHoldPreventsAnEventDeadlockAndRegenerates() {
        val started = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 31, enemyStrength = 5000).state
        val pending = BattleEvent("Linie", "Entscheidung", BattleSection.CENTER, listOf(BattleDecision.HOLD, BattleDecision.ORDERED_RETREAT))
        val scarce = started.copy(battleSession = started.battleSession!!.copy(commandPoints = 0, pendingEvent = pending))
        val after = BattleEngine.advance(scarce, BattleDecision.HOLD).state
        assertEquals(5, after.battleSession!!.minute)
        assertTrue(after.battleSession!!.commandPoints > 0)
    }

    @Test fun moraleRoutesLivingSoldiersAndEndsAFormationCollapse() {
        val weak = army().copy(armyPools = army().armyPools.map { it.copy(morale = 8) })
        val after = BattleEngine.advance(BattleEngine.start(weak, EnemyType.URUK, Tactic.HOLD, seed = 40, enemyStrength = 10_000).state).state
        assertEquals(BattleStatus.DEFEAT, after.battleSession!!.status)
        assertTrue(after.battleSession!!.contingents.any { it.routed && it.soldiers > 0 })
        assertTrue(after.armySize > 0)
    }

    @Test fun visualGroupsAreBoundedAndConserveBothArmies() {
        val huge = army(100_000).copy(population = Population(human = 200_000, wall = 5000))
        val session = BattleEngine.start(huge, EnemyType.URUK, Tactic.HOLD, seed = 17, enemyStrength = 150_000).state.battleSession!!
        val groups = BattleEngine.visualGroups(session)
        assertTrue(groups.size in 50..300)
        assertEquals(session.ownRemaining, groups.filterNot { it.enemy }.sumOf { it.soldiers })
        assertEquals(session.enemyRemaining, groups.filter { it.enemy }.sumOf { it.soldiers })
    }

    @Test fun woundedRecoverOnceWithoutCreatingCitizensOrSoldiersTwice() {
        val hospital = army().copy(realm = army().realm.copy(buildings = army().realm.buildings + (BuildingType.HOSPITAL to 4)))
        val report = finished(BattleEngine.start(hospital, EnemyType.URUK, Tactic.HOLD, seed = 58, enemyStrength = 10_000).state)
        val wounded = report.war.wounded.sumOf { it.soldiers }
        assertTrue(wounded > 0)
        assertEquals(report.battleSession!!.ownStart - report.battleSession!!.ownRemaining, report.battleSession!!.casualties.total)
        val dueDay = report.war.wounded.maxOf { it.recoveryDay }
        val healed = WarEngine.tick(report.copy(day = dueDay, battleSession = null))
        assertEquals(report.armySize + wounded, healed.armySize)
        assertEquals(report.population, healed.population)
        assertTrue(healed.war.wounded.isEmpty())
        assertEquals(healed, WarEngine.tick(healed))
        WarEngine.validate(healed)
    }

    @Test fun siegeDamagePersistsAndRepairsCostRealResources() {
        val report = finished(BattleEngine.start(army(), EnemyType.URUK, Tactic.FORTIFY, seed = 66, enemyStrength = 10_000).state)
        assertTrue(report.war.buildingDamage.values.any { it > 0 })
        val damage = report.war.buildingDamage[BuildingType.WALL]!!
        val repaired = WarEngine.repairBuilding(report, BuildingType.WALL).state
        assertFalse(repaired.war.buildingDamage.containsKey(BuildingType.WALL))
        assertEquals(report.resources.gold - damage * 3, repaired.resources.gold)
        assertEquals(report.resources.stone - damage * 2, repaired.resources.stone)
        assertEquals(report.resources.wood - damage * 2, repaired.resources.wood)
        val poor = report.copy(resources = Resources(0, 0, 0, 0, 0))
        assertEquals(poor, WarEngine.repairBuilding(poor, BuildingType.WALL).state)
    }

    @Test fun replayRunsEngineAndExactlyReconstructsThePersistedBattle() {
        var state = BattleEngine.start(army(), EnemyType.URUK, Tactic.FORTIFY, seed = 77, enemyStrength = 6000).state
        state = BattleEngine.order(state, BattleDecision.ARTILLERY_TARGET, BattleSection.CENTER).state
        val report = finished(state)
        val saved = SaveCodec.decode(SaveCodec.encode(report))
        assertEquals(report.battleSession, BattleEngine.replay(saved.war.history.single()))
        assertEquals(report, BattleEngine.advance(report).state)
        assertEquals(1, report.war.history.size)
    }

    @Test fun personalParticipationRaisesMoraleAndUnavailablePlayersCannotEnter() {
        val command = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 81).state
        val hero = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 81, participation = BattleParticipation.PERSONAL).state
        assertEquals(command.battleSession!!.morale + 8, hero.battleSession!!.morale)
        val wounded = army().copy(war = WarState(playerCondition = CombatantStatus.WOUNDED))
        assertEquals(wounded, BattleEngine.start(wounded, EnemyType.URUK, Tactic.HOLD, participation = BattleParticipation.PERSONAL).state)
    }

    @Test fun learnedPerksUnlockGenuineBattleOptionsAndChangeReserveCapacity() {
        val skilled = army().copy(court = CourtState(perks = setOf(PlayerPerk.LEADERSHIP_RESERVE, PlayerPerk.WARFARE_FEIGNED_RETREAT, PlayerPerk.WARFARE_RALLY)))
        val state = BattleEngine.start(skilled, EnemyType.URUK, Tactic.HOLD, seed = 84, enemyStrength = 8000).state
        val session = state.battleSession!!
        assertFalse(BattleEngine.canOrder(session, BattleDecision.FEIGNED_RETREAT, BattleSection.CENTER))
        val contact = session.copy(segments = session.segments.map { it.copy(contactState = BattleContactState.FIELD_CONTACT) })
        assertTrue(BattleEngine.canOrder(contact, BattleDecision.FEIGNED_RETREAT, BattleSection.CENTER))
        val queued = BattleEngine.order(state, BattleDecision.SEND_RESERVE, BattleSection.CENTER).state
        assertEquals(session.soldiers(BattleSection.RESERVE), queued.battleSession!!.soldiers(BattleSection.RESERVE))
        val reinforced = BattleEngine.advance(queued).state.battleSession!!
        assertEquals(0, reinforced.soldiers(BattleSection.RESERVE))
        assertEquals(2, BattleEngine.orderCost(session, BattleDecision.SEND_RESERVE))
        assertFalse(BattleEngine.canOrder(BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD).state.battleSession!!, BattleDecision.FEIGNED_RETREAT, BattleSection.CENTER))
    }

    @Test fun captiveExchangeReturnsExistingPeopleAndRejectsDuplicateCollection() {
        val state = army().copy(war = WarState(captives = listOf(CaptiveGroup("own", EnemyType.ORC, 40, true, UnitType.HUMAN_SWORD, 1), CaptiveGroup("enemy", EnemyType.ORC, 100, capturedDay = 1))))
        val exchanged = WarEngine.captiveAction(state, "enemy", CaptiveAction.EXCHANGE).state
        assertEquals(state.armySize + 40, exchanged.armySize)
        assertEquals(state.population, exchanged.population)
        assertTrue(exchanged.war.captives.none { it.own })
        assertEquals(60, exchanged.war.captives.single().soldiers)
        assertEquals(exchanged, WarEngine.captiveAction(exchanged, "enemy", CaptiveAction.EXCHANGE).state)
    }

    @Test fun arsenalProducesRepairsAndQualityChangesCombat() {
        val base = army().copy(realm = army().realm.copy(buildings = army().realm.buildings + (BuildingType.ARSENAL to 2)), armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 100, equipment = 50)))
        val repaired = WarEngine.tick(base)
        assertTrue(repaired.armyPools.single().equipment > 50)
        assertEquals(base.resources.iron, repaired.resources.iron)
        assertTrue(repaired.militaryStock.swords < base.militaryStock.swords)
        assertTrue(repaired.militaryStock.armor < base.militaryStock.armor)
        assertEquals(repaired, WarEngine.tick(repaired))
        val stocked = repaired.copy(war = repaired.war.copy(equipment = listOf(EquipmentBatch(UnitType.HUMAN_SWORD, stock = 100))))
        val upgraded = WarEngine.upgradeEquipment(stocked, UnitType.HUMAN_SWORD).state
        assertEquals(EquipmentQuality.GOOD, upgraded.war.equipment.single().quality)
        assertTrue(upgraded.resources.gold < stocked.resources.gold)
    }

    @Test fun enemyRosterChangesCombatAndKeepsFactionIdentityInReplay() {
        val infantry = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 6000))
        val archers = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 6000))
        fun start(units: List<UnitAllocation>) = BattleEngine.start(army(), EnemyType.TAO_TEI, Tactic.HOLD, seed = 90, enemyStrength = 6000, enemyFactionName = "Hainpakt", enemyUnits = units, location = "Nordpass").state
        assertTrue(BattleEngine.advance(start(archers)).state.battleSession!!.ownRemaining < BattleEngine.advance(start(infantry)).state.battleSession!!.ownRemaining)
        val report = finished(start(infantry))
        assertEquals("Hainpakt", report.war.history.single().enemyFactionName)
        assertEquals("Nordpass", report.war.history.single().place)
        assertEquals(report.battleSession, BattleEngine.replay(report.war.history.single()))
    }

    @Test fun eliteIdentitiesNeverExceedTheirUnderlyingPoolAfterLosses() {
        val army = army()
        val elite = WarEngine.createElite(army, UnitType.HUMAN_SWORD, "1. Sonnenwache", 3000).state
        val first = BattleEngine.advance(BattleEngine.start(elite, EnemyType.URUK, Tactic.HOLD, seed = 99, enemyStrength = 10_000).state).state
        assertTrue(first.war.eliteUnits.single().soldiers < 3000)
        assertEquals(first.soldiers(UnitType.HUMAN_SWORD), first.war.eliteUnits.single().soldiers)
        val report = finished(first)
        assertEquals(1, report.war.eliteUnits.single().battles)
        WarEngine.validate(report)
    }

    @Test fun assaultDevicesConsumeResourcesAndPersistEnemyWallDamage() {
        val started = BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 100, enemyStrength = 9000, enemyFortification = 100).state
        val attacked = BattleEngine.order(started, BattleDecision.UNDERMINE, BattleSection.CENTER).state
        assertTrue(attacked.battleSession!!.enemyFortification < 100)
        assertEquals(started.resources.wood - 100, attacked.resources.wood)
        assertEquals(started.resources.iron - 40, attacked.resources.iron)
        val poor = started.copy(resources = Resources(0, 1000, 0, 0, 0))
        assertEquals(poor, BattleEngine.order(poor, BattleDecision.UNDERMINE, BattleSection.CENTER).state)
        val report = finished(attacked)
        assertEquals(report.battleSession, BattleEngine.replay(report.war.history.single()))
    }

    @Test fun captiveRansomDebitsPayerAndCannotInventGold() {
        val initialized = WorldEngine.initialize(army())
        val faction = initialized.world.factions.first { it.id == "ash_covenant" }
        val state = initialized.copy(world = initialized.world.copy(factions = initialized.world.factions.map { if (it.id == faction.id) it.copy(gold = 24) else it }), war = WarState(captives = listOf(CaptiveGroup("prisoners", EnemyType.ORC, 100, capturedDay = 1, factionId = faction.id))))
        val paid = WarEngine.captiveAction(state, "prisoners", CaptiveAction.RANSOM).state
        assertEquals(state.resources.gold + 24, paid.resources.gold)
        assertEquals(0, paid.world.faction(faction.id)!!.gold)
        assertEquals(94, paid.war.captives.single().soldiers)
        assertEquals(paid, WarEngine.captiveAction(paid, "prisoners", CaptiveAction.RANSOM).state)
    }

    @Test fun newerBattleWoundsDoNotPostponeOlderRecovery() {
        val first = finished(BattleEngine.start(army(), EnemyType.URUK, Tactic.HOLD, seed = 104, enemyStrength = 20_000).state)
        val oldWounded = first.war.wounded.sumOf { it.soldiers }
        val firstRecovery = first.war.wounded.maxOf { it.recoveryDay }
        val second = finished(BattleEngine.start(first.copy(day = 2, battleSession = null), EnemyType.URUK, Tactic.HOLD, seed = 105, enemyStrength = 20_000).state)
        val healed = WarEngine.tick(second.copy(day = firstRecovery, battleSession = null))
        assertEquals(second.armySize + oldWounded, healed.armySize)
        assertTrue(healed.war.wounded.isNotEmpty())
        assertTrue(healed.war.wounded.all { it.recoveryDay > firstRecovery })
    }

    @Test fun fatalPlayerModeRequiresDynastyAndImmediatelyAppointsAChildlessSuccessor() {
        // Select a deterministic seed that reaches the injury roll, avoids capture and hits the fatal roll.
        val seed = (1..10_000).first { value ->
            val rng = Random(value xor 0x574152)
            rng.nextDouble() < 0.10 && rng.nextDouble() >= 0.25 && rng.nextDouble() < 0.04
        }
        fun report(dynasty: Boolean) = finished(BattleEngine.start(army().copy(settings = GameSettings(permadeath = true, dynasty = dynasty)), EnemyType.URUK, Tactic.HOLD, seed = seed, enemyStrength = 100_000, participation = BattleParticipation.PERSONAL).state)
        val protected = report(false)
        assertEquals("Mira", protected.player.name)
        assertNotEquals(CombatantStatus.DEAD, protected.war.playerCondition)
        val succession = report(true)
        assertEquals(1, succession.dynasty.successionCount)
        assertEquals(CombatantStatus.ACTIVE, succession.war.playerCondition)
        assertNotEquals("Mira", succession.player.name)
        assertEquals(0, succession.player.experience)
    }

    @Test fun emergencyImprovisedEquipmentUsesWoodAndTradesQualityForCondition() {
        val state = army().copy(armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 1000, equipment = 20)), war = WarState(equipment = listOf(EquipmentBatch(UnitType.HUMAN_SWORD, EquipmentQuality.GOOD))))
        val equipped = WarEngine.improviseEquipment(state, UnitType.HUMAN_SWORD).state
        assertEquals(45, equipped.armyPools.single().equipment)
        assertEquals(EquipmentQuality.IMPROVISED, equipped.war.equipment.single().quality)
        assertEquals(state.resources.wood - 100, equipped.resources.wood)
        assertTrue(EquipmentQuality.IMPROVISED.power < EquipmentQuality.NORMAL.power)
    }

    @Test fun partialHomeRepairsUseRealGoodsAndImproveWholeEquipmentPoints() {
        val state = army().copy(realm = army().realm.copy(buildings = army().realm.buildings + (BuildingType.ARSENAL to 2)), armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 1000, equipment = 99)), activeMissions = listOf(ActiveMission(1, MissionType.PATROL, null, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 500)), 1, 1, 100, 1)), war = WarState(equipment = listOf(EquipmentBatch(UnitType.HUMAN_SWORD, stock = 100))))
        val after = WarEngine.tick(state)
        assertEquals(100, after.armyPools.single().equipment)
        assertTrue(after.militaryStock.swords < state.militaryStock.swords)
        assertTrue(after.militaryStock.armor < state.militaryStock.armor)
        assertEquals(100, after.war.equipment.single().stock)
    }

    @Test fun fieldInfantryCanWinAndCannotTeleportFortressReinforcements() {
        val initialized = WorldEngine.initialize(army())
        val commander = Commander(1, "Aren", Culture.HUMAN, "human")
        val own = WorldArmy("expedition", PLAYER_FACTION, "Nordheer", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1500)), "forest", commanderId = 1, status = WorldArmyStatus.ENGAGED)
        val enemy = WorldArmy("enemy_test", "ash_covenant", "Grenzräuber", listOf(UnitAllocation(UnitType.HUMAN_SWORD, 30)), "forest")
        val field = initialized.copy(commanders = listOf(commander), commanderAssignments = listOf(CommanderAssignment(1, own.units)), world = initialized.world.copy(armies = initialized.world.armies + own + enemy, encounter = WorldEncounter(own.id, enemy.id, "forest", 1)))
        val deployments = listOf(BattleDeployment(1, BattleSection.CENTER, own.units))
        val partial = listOf(BattleDeployment(1, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1499))))
        assertEquals(field, BattleEngine.start(field, EnemyType.ORC, Tactic.HOLD, partial, seed = 103, enemyStrength = 30).state)
        val started = BattleEngine.start(field, EnemyType.ORC, Tactic.HOLD, deployments, seed = 103, enemyStrength = 30, enemyUnits = enemy.units).state
        assertEquals(0, started.battleSession!!.fronts.first { it.section == BattleSection.LEFT }.enemySoldiers)
        assertEquals(30, started.battleSession!!.fronts.first { it.section == BattleSection.CENTER }.enemySoldiers)
        val report = finished(started)
        assertEquals(BattleStatus.VICTORY, report.battleSession!!.status)
        assertEquals(report.battleSession, BattleEngine.replay(report.war.history.single()))
        val teleported = listOf(BattleDeployment(1, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 3000))))
        assertEquals(started, BattleEngine.redeploy(started, teleported).state)
        val direct = listOf(BattleDeployment(null, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 100))))
        assertEquals(started, BattleEngine.redeploy(started, direct).state)
    }

    @Test fun olderBattleSummariesSurviveReplayRetentionAndSaveLoad() {
        val first = finished(BattleEngine.start(army(), EnemyType.ORC, Tactic.HOLD, seed = 120, enemyStrength = 100).state)
        val earlier = first.war.history.single()
        val campaign = first.copy(battleSession = null, war = first.war.copy(history = (1..24).map { earlier.copy(id = "earlier-$it") }))
        val latest = finished(BattleEngine.start(campaign, EnemyType.ORC, Tactic.HOLD, seed = 121, enemyStrength = 100).state)
        assertEquals(25, latest.war.history.size)
        assertEquals("earlier-1", latest.war.history.first().id)
        assertEquals(earlier.casualties, latest.war.history.first().casualties)
        assertNull(latest.war.history.first().replay)
        assertTrue(latest.war.history.first().inputs.isEmpty())
        assertEquals(24, latest.war.history.count { it.replay != null })
        assertEquals(latest, SaveCodec.decode(SaveCodec.encode(latest)))
    }

    @Test fun siegeDepotTransfersFoodOnceAndUnavailableCommandersCannotDeploy() {
        val stored = WarEngine.prepareSiege(army(), "food").state
        assertEquals(400, stored.war.siegeFoodStored)
        val started = BattleEngine.start(stored, EnemyType.URUK, Tactic.FORTIFY, seed = 101).state
        assertEquals(0, started.war.siegeFoodStored)
        assertEquals(army().resources.food, started.resources.food)
        val redeployed = BattleEngine.redeploy(started, BattleEngine.defaultDeployments(started)).state
        assertEquals(started.resources.food, redeployed.resources.food)
        val unavailable = army().copy(commanders = listOf(Commander(1, "Aren", Culture.HUMAN, "human")), war = WarState(commanderConditions = listOf(CommanderCondition(1, CombatantStatus.CAPTURED))))
        assertTrue(unavailable.commanderAway(1))
        assertEquals(unavailable, BattleEngine.start(unavailable, EnemyType.URUK, Tactic.HOLD, listOf(BattleDeployment(1, BattleSection.CENTER, listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1))))).state)
    }
}
