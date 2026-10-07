package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

/** Phase 1.8, through the real persisted battle API; no mocked damage or special horde rule. */
class RealmWarAcceptanceTest {
    private fun fortress(arrows: Int = 8000, devices: List<SiegeDevice> = emptyList()) =
        GameEngine.newGame("Zinnenwacht", 24, Species.HUMAN, null).copy(
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 600, experience = 35, morale = 85),
                ArmyUnitPool(UnitType.HUMAN_SWORD, 300, experience = 25, morale = 85),
                ArmyUnitPool(UnitType.KNIGHT, 100, morale = 85)),
            population = Population(human = 5000),
            realm = Realm(buildings = mapOf(BuildingType.WALL to 4, BuildingType.TOWER to 4)),
            militaryStock = MilitaryStock(arrows = arrows, siegeParts = 100),
            invasion = Invasion(EnemyType.ORC, 1, 1500, 1, devices = devices))

    private fun start(base: GameState = fortress(), strength: Int = 1500, seed: Int = 971,
        plan: BattlePlan = BattlePlan(), night: Boolean = false, visibility: Double = 1.0,
        roster: List<UnitAllocation> = listOf(UnitAllocation(UnitType.HUMAN_SWORD, strength))) =
        BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY, seed = seed, enemyStrength = strength,
            enemyUnits = roster, plan = plan, night = night, visibility = visibility).state

    private fun step(state: GameState): GameState = BattleEngine.advance(state,
        state.battleSession!!.pendingEvent?.options?.let {
            if (BattleDecision.HOLD in it) BattleDecision.HOLD else if (BattleDecision.HOLD_FORMATION in it) BattleDecision.HOLD_FORMATION else it.first()
        }).state

    @Test fun twoHundredOrcsBreakBeforeContactAgainstOneThousandPreparedDefenders() {
        for (seed in listOf(1, 971, 2026)) {
            var state = start(strength = 200, seed = seed)
            repeat(18) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = step(state) }
            val battle = state.battleSession!!
            assertEquals("seed $seed", BattleStatus.PURSUIT, battle.status)
            assertEquals(1000, battle.ownRemaining)
            assertTrue(battle.fronts.all { it.enemyDistance > 0 })
            assertTrue(battle.exchanges.flatMap { it.fronts }.all { !it.contactState.allowsMelee && it.ownDamage.total == 0 })
            assertTrue(battle.causes.any { it.cause == BattleDamageCause.ARROWS && it.enemy > 0 })
            println("V10_ACCEPT small_horde seed=$seed own=${battle.ownRemaining} enemy=${battle.enemyRemaining} minute=${battle.minute} contact=${battle.exchanges.flatMap { it.fronts }.any { it.contactState.allowsMelee }}")
        }
    }

    @Test fun fifteenHundredExperiencedArmoredAttackersWithDevicesCanReachAndBreach() {
        for (seed in listOf(1, 971, 2026)) {
            var state = start(fortress(devices = listOf(SiegeDevice.LADDERS, SiegeDevice.RAM, SiegeDevice.TOWER)),
                seed = seed, roster = listOf(UnitAllocation(UnitType.BEAR_CORPS, 1200), UnitAllocation(UnitType.GOLD_ARCHER, 300)))
            state = state.copy(battleSession = state.battleSession!!.copy(enemyRoster = state.battleSession.enemyRoster.map {
                it.copy(experience = 90, equipment = 95)
            }))
            repeat(18) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = step(state) }
            val battle = state.battleSession!!
            assertTrue("contact, seed $seed", battle.exchanges.flatMap { it.fronts }.any { it.contactState.allowsMelee })
            assertTrue("gate damage, seed $seed", battle.segment(BattleSection.CENTER)!!.gateIntegrity < 100)
            assertTrue(battle.siegeDevices.any { it.distance == 0 })
            assertTrue(battle.ownRemaining < battle.ownStart)
            println("V10_ACCEPT strong_siege seed=$seed own=${battle.ownRemaining} enemy=${battle.enemyRemaining} minute=${battle.minute} gate=${battle.segment(BattleSection.CENTER)!!.gateIntegrity} contact=true")
        }
    }

    @Test fun emptyMagazinesProduceNeitherArrowDamageNorArrowVolleys() {
        val battle = step(start(fortress(arrows = 0))).battleSession!!
        assertTrue(battle.exchanges.single().fronts.all { it.arrowsUsed == 0 && it.volleys == 0.0 && it.enemyDamage.ranged == 0 })
        assertFalse(BattleSoundCue.ARROWS in battle.lastSounds)
        assertEquals(1500, battle.enemyRemaining)
    }

    @Test fun nightAndPoorSightReduceRangeAndAlsoCloseRangeAccuracy() {
        fun at(distance: Int, night: Boolean, visibility: Double): BattleSession {
            val state = start(night = night, visibility = visibility)
            return step(state.copy(battleSession = state.battleSession!!.copy(fronts = state.battleSession.fronts.map {
                it.copy(enemyDistance = distance)
            }))).battleSession!!
        }
        assertEquals(1500, at(230, true, .5).enemyRemaining)
        assertTrue(at(230, false, 1.0).enemyRemaining < 1500)
        assertTrue(at(0, true, .5).enemyRemaining > at(0, false, 1.0).enemyRemaining)
    }

    @Test fun brokenOwnAndEnemyMoraleCannotInflictOrdinaryAttackDamage() {
        val state = start(roster = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 1500)))
        val broken = state.copy(battleSession = state.battleSession!!.copy(
            contingents = state.battleSession.contingents.map { it.copy(morale = 0, routed = false) },
            fronts = state.battleSession.fronts.map { it.copy(morale = 0, enemyDistance = 0) },
            segments = state.battleSession.segments.map { it.copy(integrity = 0, breachWidth = 80, contactState = BattleContactState.BREACHED) }))
        val battle = step(broken).battleSession!!
        assertTrue(battle.exchanges.single().fronts.all {
            it.ownDamage.ranged + it.ownDamage.melee + it.enemyDamage.ranged + it.enemyDamage.melee == 0
        })
        assertEquals(state.battleSession.battleArrowsRemaining, battle.battleArrowsRemaining)
    }

    @Test fun manualReserveTravelsForARealExchangeAndSurvivesSaveAndLoadInTransit() {
        val initial = start(plan = BattlePlan(reservePolicy = ReservePolicy.MANUAL))
        val queued = BattleEngine.order(initial, BattleDecision.SEND_RESERVE, BattleSection.CENTER).state
        assertEquals(100, queued.battleSession!!.fighting(BattleSection.RESERVE))
        assertNotNull(queued.battleSession.reserveReinforcement)
        val restored = SaveCodec.decode(SaveCodec.encode(queued))
        val arrived = step(restored).battleSession!!
        assertEquals(0, arrived.fighting(BattleSection.RESERVE))
        assertTrue(arrived.contingents.any { it.type == UnitType.KNIGHT && it.section == BattleSection.CENTER })
        assertEquals(initial.battleSession!!.ownStart, arrived.contingents.sumOf { it.startSoldiers })
        assertFalse(BattleEngine.canOrder(queued.battleSession, BattleDecision.SEND_RESERVE, BattleSection.RIGHT))
    }

    @Test fun aBreachChangesLocalFrontageButDoesNotCreateDistantMelee() {
        val initial = start(fortress(arrows = 0))
        val battle = initial.battleSession!!
        assertEquals(0, SiegeEngine.frontage(battle, BattleSection.CENTER))
        fun breach(distance: Int) = initial.copy(battleSession = battle.copy(
            fronts = battle.fronts.map { if (it.section == BattleSection.CENTER) it.copy(enemyDistance = distance) else it },
            segments = battle.segments.map { if (it.section == BattleSection.CENTER) it.copy(gateIntegrity = 0, breachWidth = 28) else it }))
        val distant = step(breach(350)).battleSession!!
        assertEquals(0, distant.lastReport(BattleSection.CENTER)!!.frontage)
        assertEquals(0, distant.lastReport(BattleSection.CENTER)!!.ownDamage.melee)
        val touching = step(breach(0)).battleSession!!
        assertEquals(28, touching.lastReport(BattleSection.CENTER)!!.frontage)
        assertEquals(0, touching.lastReport(BattleSection.LEFT)!!.frontage)
        assertTrue(touching.causes.any { it.cause == BattleDamageCause.BREACH })
    }

    @Test fun pursuitHasItsOwnCauseAndExchangeRatherThanRegularMelee() {
        var ready = start(strength = 1500)
        repeat(18) { if (ready.battleSession!!.status == BattleStatus.ACTIVE) ready = step(ready) }
        assertTrue("A pursuit requires surviving fugitives", ready.battleSession!!.enemyRemaining > 0)
        val chase = BattleEngine.advance(ready, BattleDecision.PURSUE).state
        val battle = chase.battleSession!!
        assertEquals(BattleStatus.VICTORY, battle.status)
        assertTrue(battle.exchanges.last().fronts.all { it.ownDamage.melee + it.enemyDamage.melee == 0 })
        assertTrue(battle.causes.any { it.cause == BattleDamageCause.PURSUIT && it.enemy > 0 })
        assertEquals(battle.ownStart - battle.ownRemaining, battle.causes.sumOf { it.own })
        assertEquals(battle.enemyStart - battle.enemyRemaining, battle.causes.sumOf { it.enemy })
        assertEquals(battle.ownStart - battle.ownRemaining, battle.casualties.total)
        assertEquals(battle.exchanges.last().ownLosses, battle.exchanges.last().ownCasualties.total)
        assertEquals(battle.exchanges.last().enemyLosses, battle.exchanges.last().enemyCasualties.total)
        assertEquals(battle.causes, chase.war.history.last().causes)
        assertEquals(chase, SaveCodec.decode(SaveCodec.encode(chase)))
    }

    @Test fun aBrokenOwnFormationDoesNotAttackAnOtherwiseHealthyEnemy() {
        val original = start()
        val broken = original.copy(battleSession = original.battleSession!!.copy(
            contingents = original.battleSession.contingents.map { it.copy(morale = 0, routed = false) }))
        val next = step(broken).battleSession!!
        assertTrue(next.exchanges.single().fronts.all { it.enemyDamage.ranged + it.enemyDamage.melee == 0 })
        assertEquals(original.battleSession.battleArrowsRemaining, next.battleArrowsRemaining)
        assertTrue(next.contingents.all { it.routed })
    }

    @Test fun aBrokenEnemyFormationCannotAttackHealthyDefenders() {
        val original = start(roster = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 1500)))
        val broken = original.copy(battleSession = original.battleSession!!.copy(
            fronts = original.battleSession.fronts.map { it.copy(morale = 0, enemyDistance = 0) }))
        assertTrue(step(broken).battleSession!!.exchanges.single().fronts.all { it.ownDamage.total == 0 })
    }

    @Test fun collapseCasualtiesOccurOnlyWhenTheStructureActuallyCollapses() {
        val original = start(fortress(arrows = 0, devices = listOf(SiegeDevice.CATAPULT)))
        val weakWall = original.copy(battleSession = original.battleSession!!.copy(
            segments = original.battleSession.segments.map { if (it.section == BattleSection.LEFT) it.copy(integrity = 3) else it }))
        val collapsed = step(weakWall)
        val losses = collapsed.battleSession!!.causes.filter { it.cause == BattleDamageCause.COLLAPSE }.sumOf { it.own }
        assertTrue(losses > 0)
        assertEquals(0, collapsed.battleSession.segment(BattleSection.LEFT)!!.integrity)
        assertEquals(losses, step(collapsed).battleSession!!.causes.filter { it.cause == BattleDamageCause.COLLAPSE }.sumOf { it.own })
        assertEquals(collapsed, SaveCodec.decode(SaveCodec.encode(collapsed)))
    }
}
