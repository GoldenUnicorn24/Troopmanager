package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test

class BattleSceneProjectionTest {
    private fun fortress(arrows: Int = 8000, strength: Int = 200): GameState {
        val base = GameEngine.newGame("Zinnenwacht", 24, Species.HUMAN, null).copy(
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 600, experience = 35, morale = 85),
                ArmyUnitPool(UnitType.HUMAN_SWORD, 300, experience = 25, morale = 85), ArmyUnitPool(UnitType.KNIGHT, 100, morale = 85)),
            population = Population(human = 5000),
            realm = Realm(buildings = mapOf(BuildingType.WALL to 4, BuildingType.TOWER to 4)),
            militaryStock = MilitaryStock(arrows = arrows))
        return BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY, seed = 971, enemyStrength = strength,
            enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, strength))).state
    }

    @Test fun allSoldiersAndDistinctFormationsSurviveProjectionWithoutChangingTheSave() {
        val initial = fortress()
        val b = initial.battleSession!!
        val state = initial.copy(battleSession = b.copy(contingents = b.contingents.mapIndexed { i, c ->
            c.copy(formation = BattleFormation.entries[i % 4])
        }))
        val bytes = SaveCodec.encode(state)
        val scene = BattleSceneProjection.project(state.battleSession!!)
        assertEquals(1000, scene.battalions.filterNot { it.key.enemy }.sumOf { it.soldiers })
        assertEquals(200, scene.battalions.filter { it.key.enemy }.sumOf { it.soldiers })
        assertEquals(state.battleSession.contingents.map { it.formation }.toSet(), scene.battalions.filterNot { it.key.enemy }.map { it.key.formation }.toSet())
        assertEquals(bytes, SaveCodec.encode(state))
        assertEquals(scene, BattleSceneProjection.project(SaveCodec.decode(bytes).battleSession!!))
    }

    @Test fun archersDefendTheWallButAttackingArchersNeverBorrowItsPosition() {
        val battle = fortress().battleSession!!
        val ownFort = BattleSceneProjection.project(battle)
        assertTrue(ownFort.battalions.any { !it.key.enemy && it.role == BattleRole.ARCHERS && it.onWall })
        assertTrue(ownFort.battalions.filter { it.key.enemy }.none { it.onWall })
        val assault = BattleSceneProjection.project(battle.copy(tactic = Tactic.AGGRESSIVE, enemyFortification = 100,
            enemyRoster = battle.enemyRoster.map { it.copy(type = UnitType.GOLD_ARCHER) }))
        assertTrue(assault.battalions.filterNot { it.key.enemy }.none { it.onWall })
        assertTrue(assault.battalions.filter { it.key.enemy }.all { it.onWall })
    }

    @Test fun localBreachAndFallbackRemoveOnlyTheirOwnWallPositions() {
        val battle = fortress().battleSession!!
        val scene = BattleSceneProjection.project(battle.copy(segments = battle.segments.map {
            when (it.section) {
                BattleSection.CENTER -> it.copy(integrity = 0, breachWidth = 80, contactState = BattleContactState.BREACHED)
                BattleSection.RIGHT -> it.copy(fallenBack = true)
                else -> it
            }
        }))
        assertTrue(scene.battalions.filter { it.key.section == BattleSection.LEFT && it.role == BattleRole.ARCHERS }.any { it.onWall })
        assertTrue(scene.battalions.filter { it.key.section in listOf(BattleSection.CENTER, BattleSection.RIGHT) }.none { it.onWall })
    }

    @Test fun formationsHaveDistinctRankGeometryAndSpritesStayBoundedForHugeArmies() {
        val b = fortress().battleSession!!
        val variants = BattleFormation.entries.map { formation -> BattleSceneProjection.project(b.copy(
            contingents = b.contingents.map { it.copy(formation = formation, soldiers = 1_000_000, startSoldiers = 1_000_000) }
        )) }
        variants.forEach { scene ->
            assertTrue(scene.battalions.sumOf { it.markers } <= BattleSceneProjection.MAX_MARKERS)
            assertTrue(scene.battalions.all { it.markers in 1..BattleSceneProjection.MAX_MARKERS_PER_BATTALION })
        }
        val line = variants[0].battalions.first { !it.key.enemy }
        val loose = variants[1].battalions.first { !it.key.enemy }
        val dense = variants[2].battalions.first { !it.key.enemy }
        assertTrue(line.files > dense.files)
        assertTrue(loose.spacing > line.spacing && line.spacing > dense.spacing)
    }

    @Test fun realSmallAssaultHasArrowsButNoInventedMeleeOrDefenderHits() {
        val state = BattleEngine.advance(fortress()).state
        val scene = BattleSceneProjection.project(state.battleSession!!)
        val effects = scene.fronts.map(BattleSceneProjection::effects)
        assertTrue(effects.sumOf { it.arrows } > 0)
        assertTrue(effects.sumOf { it.arrowHits } > 0)
        assertEquals(0, effects.sumOf { it.meleeHits + it.enemyArrowHits + it.wallDamage + it.gateDamage })
        assertEquals(1000, scene.battalions.filterNot { it.key.enemy }.sumOf { it.soldiers })
    }

    @Test fun noMagazineOrHeldFireProducesNoArrowFlights() {
        val empty = BattleEngine.advance(fortress(arrows = 0)).state.battleSession!!
        val initial = fortress()
        val held = BattleEngine.configurePlan(initial, initial.battleSession!!.plan.copy(holdFire = true)).state
        listOf(empty, BattleEngine.advance(held).state.battleSession!!).forEach { b ->
            assertTrue(BattleSceneProjection.project(b).fronts.map(BattleSceneProjection::effects).all { it.arrows == 0 && it.arrowHits == 0 })
        }
    }

    @Test fun unrelatedDamageDoesNotTurnAnArrowMissIntoAHitAndOldReportsDoNotReplay() {
        val b = fortress().battleSession!!
        val report = FrontExchangeReport(BattleSection.CENTER, BattleContactState.APPROACH, arrowsUsed = 20,
            enemyDamage = BattleDamageSources(melee = 9, artillery = 4, wallWeapons = 8))
        val current = b.copy(step = 1, minute = 5, exchanges = listOf(BattleExchangeReport(5, listOf(report))))
        val effects = BattleSceneProjection.effects(BattleSceneProjection.project(current).fronts.first { it.section == BattleSection.CENTER })
        assertEquals(20, effects.arrows)
        assertEquals(0, effects.arrowHits)
        assertEquals(0, effects.meleeHits)
        assertEquals(0, effects.artilleryHits)
        assertTrue(BattleSceneProjection.project(current.copy(step = 2, minute = 10)).fronts.all { it.report == null })
    }

    @Test fun flightWindowHasAnActualLaunchArrivalAndRestState() {
        assertNull(BattleSceneProjection.flightProgress(0f, 0))
        assertEquals(.5f, BattleSceneProjection.flightProgress(.40f, 0)!!, .001f)
        assertNull(BattleSceneProjection.flightProgress(1f, 0))
    }

    @Test fun pendingFormationAndReserveKeepTheirAuthoritativePositionsUntilArrival() {
        val initial = fortress(strength = 1500)
        val queued = BattleEngine.order(initial, BattleDecision.SEND_RESERVE, BattleSection.CENTER).state
        val b = queued.battleSession!!
        assertNotNull(b.reserveReinforcement)
        val scene = BattleSceneProjection.project(b.copy(contingents = b.contingents.map {
            it.copy(formation = BattleFormation.LINE, pendingFormation = BattleFormation.DENSE, formationReadyStep = b.step + 1)
        }))
        assertEquals(b.fighting(BattleSection.RESERVE), scene.battalions.filter { it.key.section == BattleSection.RESERVE }.sumOf { it.soldiers })
        assertTrue(scene.battalions.filterNot { it.key.enemy }.all { it.key.formation == BattleFormation.LINE })
    }
}
