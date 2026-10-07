package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.data.SaveCodec
import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Acceptance scenarios exercise the saved public battle API, with fixed seeds. */
class BattleTacticsTest {
    private fun fortress(arrows: Int = 8000, devices: List<SiegeDevice> = emptyList()) =
        GameEngine.newGame("Zinnenwacht", 24, Species.HUMAN, null).copy(
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_ARCHER, 600, experience = 35, morale = 85),
                ArmyUnitPool(UnitType.HUMAN_SWORD, 300, experience = 25, morale = 85), ArmyUnitPool(UnitType.KNIGHT, 100, morale = 85)),
            population = Population(human = 5000),
            realm = Realm(buildings = mapOf(BuildingType.WALL to 4, BuildingType.TOWER to 4), wallIntegrity = 100),
            militaryStock = MilitaryStock(arrows = arrows, siegeParts = 100),
            invasion = Invasion(EnemyType.ORC, 1, 300, 1, devices = devices),
        )

    private fun start(state: GameState = fortress(), strength: Int = 300,
        roster: List<UnitAllocation> = listOf(UnitAllocation(UnitType.HUMAN_SWORD, strength)), plan: BattlePlan = BattlePlan()) =
        BattleEngine.start(state, EnemyType.ORC, Tactic.FORTIFY, seed = 971, enemyStrength = strength,
            enemyUnits = roster, plan = plan).state

    private fun step(state: GameState): GameState = BattleEngine.advance(state,
        state.battleSession!!.pendingEvent?.options?.let { options ->
            if (BattleDecision.HOLD in options) BattleDecision.HOLD else options.first()
        }).state

    @Test fun distantMeleeCannotCauseAnyPersonnelDamage() {
        val initial = start().let { it.copy(battleSession = it.battleSession!!.copy(
            fronts = it.battleSession.fronts.map { f -> f.copy(enemyDistance = 400) })) }
        val next = step(initial).battleSession!!
        assertEquals(initial.battleSession!!.ownRemaining, next.ownRemaining)
        assertTrue(next.exchanges.single().fronts.all { it.ownDamage.melee == 0 && it.enemyDamage.melee == 0 })
    }

    @Test fun smallPureMeleeAssaultBreaksBeforeWallContactWithoutDefenderLosses() {
        for (strength in listOf(150, 220, 300)) {
            var state = start(strength = strength)
            repeat(10) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = step(state) }
            val battle = state.battleSession!!
            assertEquals("Horde $strength should break", BattleStatus.PURSUIT, battle.status)
            assertEquals(1000, battle.ownRemaining)
            assertTrue(battle.exchanges.flatMap { it.fronts }.all { !it.contactState.allowsMelee })
            assertTrue("Attack breaks before reaching wall", battle.fronts.all { it.enemyDistance > 0 })
            assertTrue(battle.exchanges.flatMap { it.fronts }.sumOf { it.suppression } > 0)
        }
    }

    @Test fun enemyArchersCauseNonzeroLossesReducedByIntactCover() {
        val roster = listOf(UnitAllocation(UnitType.GOLD_ARCHER, 300))
        val covered = step(start(roster = roster)).battleSession!!
        val damagedState = fortress().copy(realm = fortress().realm.copy(wallIntegrity = 0))
        val uncovered = step(start(state = damagedState, roster = roster)).battleSession!!
        assertTrue(covered.ownStart > covered.ownRemaining)
        assertTrue(covered.ownStart - covered.ownRemaining < uncovered.ownStart - uncovered.ownRemaining)
        assertTrue(covered.exchanges.single().fronts.all { it.ownDamage.melee == 0 })
    }

    @Test fun artilleryDamagesStructuresBeforeMelee() {
        val battle = step(start(fortress(devices = listOf(SiegeDevice.CATAPULT)))).battleSession!!
        assertTrue(battle.segment(BattleSection.LEFT)!!.integrity < 100)
        assertTrue(battle.exchanges.single().fronts.all { it.ownDamage.melee == 0 })
    }

    @Test fun ammunitionPolicyChangesRealConsumptionAndDamage() {
        val normal = step(start(strength = 1500)).battleSession!!
        val sparse = step(start(strength = 1500, plan = BattlePlan(ammunitionPolicy = AmmunitionPolicy.SPARING))).battleSession!!
        val volley = step(start(strength = 1500, plan = BattlePlan(ammunitionPolicy = AmmunitionPolicy.VOLLEY))).battleSession!!
        assertTrue(sparse.battleArrowsRemaining > normal.battleArrowsRemaining)
        assertTrue(volley.battleArrowsRemaining < normal.battleArrowsRemaining)
        assertTrue(volley.enemyRemaining < normal.enemyRemaining)
        assertTrue(sparse.enemyRemaining > normal.enemyRemaining)
        val empty = step(start(fortress(arrows = 0), strength = 1500)).battleSession!!
        assertEquals(1500, empty.enemyRemaining)
        val low = step(start(fortress(arrows = 40), strength = 1500)).battleSession!!
        assertEquals(0, low.battleArrowsRemaining)
        assertTrue(low.enemyRemaining > normal.enemyRemaining)
        assertEquals(40, low.exchanges.single().fronts.sumOf { it.arrowsUsed })
    }

    @Test fun rangedPriorityChangesWhichRealFormationTakesLosses() {
        val roster = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 900), UnitAllocation(UnitType.GOLD_ARCHER, 600))
        val light = step(start(strength = 1500, roster = roster,
            plan = BattlePlan(rangedPriority = TargetPriority.LIGHT_INFANTRY))).battleSession!!
        val ranged = step(start(strength = 1500, roster = roster,
            plan = BattlePlan(rangedPriority = TargetPriority.RANGED))).battleSession!!
        fun archers(b: BattleSession) = b.enemyRoster.filter { it.type == UnitType.GOLD_ARCHER }.sumOf { it.soldiers }
        assertTrue(archers(ranged) < archers(light))
        assertTrue(ranged.exchanges.single().fronts.any { it.targetType == UnitType.GOLD_ARCHER })
    }

    @Test fun killZoneWaitsUntilTheChosenRangeBeforeConsumingArrows() {
        val state = start(strength = 1500, plan = BattlePlan.profile(BattleDoctrine.KILL_ZONE))
        val waiting = step(state)
        assertEquals(state.battleSession!!.battleArrowsRemaining, waiting.battleSession!!.battleArrowsRemaining)
        val firing = step(waiting)
        assertTrue(firing.battleSession!!.battleArrowsRemaining < waiting.battleSession.battleArrowsRemaining)
    }

    @Test fun strongSiegeForceStillReachesAndThreatensTheWall() {
        var state = start(fortress(arrows = 1200, devices = listOf(SiegeDevice.LADDERS, SiegeDevice.RAM, SiegeDevice.TOWER)), strength = 1500)
        repeat(12) { if (state.battleSession!!.status == BattleStatus.ACTIVE) state = step(state) }
        assertTrue(state.battleSession!!.exchanges.flatMap { it.fronts }.any { it.contactState.allowsMelee })
        assertTrue(state.battleSession!!.segment(BattleSection.CENTER)!!.gateIntegrity < 100)
    }

    @Test fun automaticReserveArrivesAfterOneExchangeAndManualPolicyKeepsItBack() {
        fun threatened(policy: ReservePolicy): GameState {
            val state = start(strength = 1500, plan = BattlePlan(reservePolicy = policy))
            return state.copy(battleSession = state.battleSession!!.copy(segments = state.battleSession.segments.map {
                if (it.section == BattleSection.CENTER) it.copy(gateIntegrity = 45) else it
            }))
        }
        val queued = step(threatened(ReservePolicy.THREATENED_GATE))
        assertEquals(100, queued.battleSession!!.fighting(BattleSection.RESERVE))
        assertEquals(BattleSection.CENTER, queued.battleSession.reserveReinforcement!!.section)
        val arrived = step(queued)
        assertEquals(0, arrived.battleSession!!.fighting(BattleSection.RESERVE))
        assertTrue(arrived.battleSession.contingents.any { it.type == UnitType.KNIGHT && it.section == BattleSection.CENTER })
        assertEquals(100, step(step(threatened(ReservePolicy.MANUAL))).battleSession!!.fighting(BattleSection.RESERVE))
        assertEquals(arrived, SaveCodec.decode(SaveCodec.encode(arrived)))
    }

    @Test fun freePreparationAndPaidLiveDoctrineChangePersistWithoutReloadingAmmo() {
        val prepared = start(strength = 1500)
        val plan = BattlePlan.profile(BattleDoctrine.RANGED_SUPERIORITY)
        val free = BattleEngine.configurePlan(prepared, plan).state
        assertEquals(prepared.battleSession!!.commandPoints, free.battleSession!!.commandPoints)
        assertEquals(prepared.militaryStock, free.militaryStock)
        val running = step(free)
        val changed = BattleEngine.configurePlan(running, BattlePlan.profile(BattleDoctrine.FLEXIBLE_DEFENSE)).state
        assertEquals(running.battleSession!!.commandPoints - 3, changed.battleSession!!.commandPoints)
        assertEquals(running.battleSession.battleArrowsRemaining, changed.battleSession.battleArrowsRemaining)
        assertEquals(BattleDoctrine.FLEXIBLE_DEFENSE, changed.battleSession.plan.doctrine)
        assertEquals(changed, SaveCodec.decode(SaveCodec.encode(changed)))
        val denied = BattleEngine.configurePlan(changed.copy(battleSession = changed.battleSession.copy(commandPoints = 0)), plan)
        assertEquals(0, denied.state.battleSession!!.commandPoints)
        assertEquals(changed.battleSession.plan, denied.state.battleSession.plan)
    }

    @Test fun v096SchemaFourSaveDecodesWithSafeDefaultsAndCanEnterBattle() {
        val original = start()
        val root = Json.parseToJsonElement(SaveCodec.encode(original)).jsonObject.toMutableMap()
        root.remove("_checksum")
        root["version"] = JsonPrimitive(4)
        root.remove("defensePlan")
        val savedBattle = root.getValue("battleSession").jsonObject.filterKeys {
            it !in setOf("plan", "reserveReinforcement", "night", "visibility", "season")
        }.toMutableMap()
        savedBattle["replayStart"] = JsonObject(savedBattle.getValue("replayStart").jsonObject.filterKeys {
            it !in setOf("plan", "night", "visibility", "season", "rulesVersion")
        })
        root["battleSession"] = JsonObject(savedBattle)
        val payload = JsonObject(root).toString()
        val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
            .joinToString("") { "%02x".format(it) }
        root["_checksum"] = JsonPrimitive(checksum)
        val decoded = SaveCodec.decode(JsonObject(root).toString())
        assertEquals(5, decoded.version)
        assertEquals(BattlePlan(), decoded.defensePlan)
        assertEquals(BattlePlan(), decoded.battleSession!!.plan)
        assertEquals(1, decoded.battleSession.replayStart!!.rulesVersion)
        assertEquals(decoded, SaveCodec.decode(SaveCodec.encode(decoded)))
        assertEquals(5, step(decoded).battleSession!!.minute)
    }

    @Test fun wallPriorityUsesFiniteChargesAndTargetsDevices() {
        val stock = WallWeaponStock(WallWeaponType.BALLISTA, count = 3, ammunition = 15)
        val base = fortress(devices = listOf(SiegeDevice.RAM)).copy(frontier = fortress().frontier.copy(weapons = listOf(stock)))
        val started = start(base, strength = 1500, plan = BattlePlan(wallWeaponPriority = WallTargetPriority.DEVICES))
        val battle = step(started).battleSession!!
        assertEquals(12, battle.wallWeapons.single().ammunition)
        assertTrue(battle.siegeDevices.single().integrity < 100)
        assertEquals(3, battle.weaponChargesUsed.getValue(WallWeaponType.BALLISTA))
        val noDevices = step(start(base.copy(invasion = base.invasion!!.copy(devices = emptyList())),
            strength = 1500, plan = BattlePlan(wallWeaponPriority = WallTargetPriority.DEVICES))).battleSession!!
        assertEquals(15, noDevices.wallWeapons.single().ammunition)
    }

    @Test fun nightAndPoorVisibilityReduceUsefulRange() {
        val normal = step(start(strength = 1500)).battleSession!!
        val base = fortress()
        val night = step(BattleEngine.start(base, EnemyType.ORC, Tactic.FORTIFY, seed = 971,
            enemyStrength = 1500, enemyUnits = listOf(UnitAllocation(UnitType.HUMAN_SWORD, 1500)),
            night = true, visibility = .5).state).battleSession!!
        assertTrue(night.enemyRemaining > normal.enemyRemaining)
    }

    @Test fun gateDefenseReducesRealRamDamage() {
        val base = fortress(arrows = 0, devices = listOf(SiegeDevice.RAM))
        var closed = start(base, strength = 1500)
        var defended = start(base, strength = 1500, plan = BattlePlan(gatePolicy = GatePolicy.GATE_DEFENSE))
        repeat(6) { closed = step(closed); defended = step(defended) }
        assertTrue(defended.battleSession!!.segment(BattleSection.CENTER)!!.gateIntegrity >
            closed.battleSession!!.segment(BattleSection.CENTER)!!.gateIntegrity)
    }

    @Test fun fallbackMovesAnExhaustedBreachedFrontIntoTheCourtyard() {
        fun pressured(policy: FallbackPolicy): GameState {
            val state = start(fortress(arrows = 0), strength = 1500, plan = BattlePlan(fallbackPolicy = policy))
            return state.copy(battleSession = state.battleSession!!.copy(
                fronts = state.battleSession.fronts.map { it.copy(enemyDistance = 0) },
                contingents = state.battleSession.contingents.map { if (it.section == BattleSection.CENTER) it.copy(morale = 40, cohesion = 40) else it },
                segments = state.battleSession.segments.map { if (it.section == BattleSection.CENTER)
                    it.copy(gateIntegrity = 0, breachWidth = 28, contactState = BattleContactState.BREACHED) else it }))
        }
        assertEquals(BattleContactState.COURTYARD, step(pressured(FallbackPolicy.EARLY)).battleSession!!.segment(BattleSection.CENTER)!!.contactState)
        assertEquals(BattleContactState.BREACHED, step(pressured(FallbackPolicy.HOLD_LINE)).battleSession!!.segment(BattleSection.CENTER)!!.contactState)
    }

    @Test fun heldPositionAndAggressivePursuitHaveDifferentSavedOutcomes() {
        var ready = start()
        repeat(10) { if (ready.battleSession!!.status == BattleStatus.ACTIVE) ready = step(ready) }
        val held = BattleEngine.advance(ready, BattleDecision.HOLD_FORMATION).state
        val chase = BattleEngine.configurePlan(ready, ready.battleSession!!.plan.copy(pursuitPolicy = PursuitPolicy.AGGRESSIVE)).state
        val pursued = BattleEngine.advance(chase, BattleDecision.PURSUE).state
        assertTrue(pursued.battleSession!!.enemyRemaining < held.battleSession!!.enemyRemaining)
        assertEquals(1000, held.battleSession.ownRemaining)
        assertEquals(pursued.battleSession.enemyStart - pursued.battleSession.enemyRemaining, pursued.war.history.last().enemyCasualties.total)
        assertEquals(held.battleSession.enemyRemaining, held.war.history.last().enemyFled)
    }

    @Test fun aPaidPlanChangeIsReconstructedByTheSameReplayEngine() {
        var state = step(start(strength = 1500))
        state = BattleEngine.configurePlan(state, BattlePlan.profile(BattleDoctrine.FLEXIBLE_DEFENSE)).state
        state = BattleEngine.configurePlan(state, state.battleSession!!.plan.copy(ammunitionPolicy = AmmunitionPolicy.SPARING)).state
        repeat(20) {
            val battle = state.battleSession!!
            if (battle.status == BattleStatus.PURSUIT) state = BattleEngine.advance(state, BattleDecision.HOLD_FORMATION).state
            else if (battle.status == BattleStatus.ACTIVE) state = step(state)
        }
        val decoded = SaveCodec.decode(SaveCodec.encode(state))
        assertEquals(decoded.battleSession, BattleEngine.replay(decoded.war.history.last()))
        assertEquals(decoded.battleSession!!.ownStart - decoded.battleSession.ownRemaining, BattleReportEngine.lossesByPhase(decoded.battleSession).sumOf { it.own })
        assertEquals(decoded.battleSession.enemyStart - decoded.battleSession.enemyRemaining, BattleReportEngine.lossesByPhase(decoded.battleSession).sumOf { it.enemy })
    }

    @Test fun wallWeaponFrontPriorityDoesNotMoveWeaponsOrSpendOtherFrontCharges() {
        val weapon = WallWeaponStock(WallWeaponType.REPEATER, count = 2, ammunition = 10, section = BattleSection.LEFT)
        val base = fortress().copy(frontier = fortress().frontier.copy(weapons = listOf(weapon)))
        val ignored = step(start(base, strength = 1500, plan = BattlePlan(wallWeaponPriority = WallTargetPriority.FRONT, wallWeaponFront = BattleSection.RIGHT))).battleSession!!
        assertEquals(10, ignored.wallWeapons.single().ammunition)
        assertEquals(BattleSection.LEFT, ignored.wallWeapons.single().section)
        val fired = step(start(base, strength = 1500, plan = BattlePlan(wallWeaponPriority = WallTargetPriority.FRONT, wallWeaponFront = BattleSection.LEFT))).battleSession!!
        assertEquals(8, fired.wallWeapons.single().ammunition)
    }

    @Test fun v096ReplayFinishesSafelyWhenNewMoraleRulesBreakAnAssaultEarlier() {
        val battle = start().battleSession!!
        val historical = BattleRecord("v096", 1, "Zinnenwacht", battle.enemy, battle.seed, battle.tactic,
            battle.ownStart, battle.enemyStart, battle.ownStart, 50, true, 35, CasualtyReport(),
            emptyList(), emptyList(), battle.terrain, battle.participation,
            replay = battle.replayStart!!.copy(rulesVersion = 1), enemyUnits = battle.enemyUnits,
            inputs = List(6) { BattleInput() } + BattleInput(BattleDecision.HOLD_FORMATION))
        val replay = BattleEngine.replay(historical)
        assertNotNull(replay)
        assertEquals(BattleStatus.VICTORY, replay!!.status)
        assertTrue(replay.minute < historical.minute)
        assertEquals(replay, BattleEngine.replay(historical))
        assertEquals(35, historical.minute)
        assertEquals(50, historical.enemyRemaining)
        // A malformed current replay remains an error rather than receiving legacy coercion.
        assertNull(BattleEngine.replay(historical.copy(replay = battle.replayStart)))
    }
}
