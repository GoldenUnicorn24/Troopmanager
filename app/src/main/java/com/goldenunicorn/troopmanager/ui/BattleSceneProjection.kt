package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil
import kotlin.math.sqrt

/** Read-only, bounded scene data. No random rolls, combat rules, or saved state live here. */
internal data class BattlePoint(val x: Float, val y: Float)
internal enum class BattleRole { INFANTRY, SPEARS, ARCHERS, CAVALRY, ARTILLERY, MONSTERS }
internal data class BattalionKey(
    val section: BattleSection, val enemy: Boolean, val type: UnitType?,
    val formation: BattleFormation, val routed: Boolean,
)
internal data class BattleBattalion(
    val key: BattalionKey, val soldiers: Int, val role: BattleRole, val position: BattlePoint,
    val files: Int, val ranks: Int, val markers: Int, val spacing: Float,
    val onWall: Boolean, val commander: Boolean, val commanderWounded: Boolean,
)
internal data class BattleSceneFront(
    val section: BattleSection, val distance: Int, val contact: BattleContactState,
    val segment: FortificationSegmentState?, val terrain: BattleTerrain,
    val own: Int, val enemy: Int, val report: FrontExchangeReport?,
)
internal data class BattleScene(
    val seed: Int, val step: Int, val minute: Int, val fortified: Boolean, val defenderEnemy: Boolean,
    val battalions: List<BattleBattalion>, val fronts: List<BattleSceneFront>,
    val devices: List<SiegeDeviceState>, val reserve: ReserveReinforcement?, val hero: BattlePoint?,
) {
    fun defender(enemy: Boolean) = if (fortified) enemy == defenderEnemy else !enemy
}

/** Flight and impact are separate: firing ammunition is not evidence of a hit. */
internal data class BattleExchangeEffects(
    val arrows: Int, val enemyArrows: Int, val artillery: Int,
    val arrowHits: Int, val enemyArrowHits: Int, val artilleryHits: Int,
    val meleeHits: Int, val wallDamage: Int, val gateDamage: Int,
    val deviceDamage: Int, val wallWeaponHits: Int,
)

internal object BattleSceneProjection {
    const val WALL_Y = .61f
    const val MAX_MARKERS_PER_BATTALION = 24
    // 13 types × 4 formations × 2 morale states × 4 sections; independent of army size.
    const val MAX_MARKERS = 720

    private data class Troops(
        val key: BattalionKey, val soldiers: Int, val commander: Boolean = false,
        val commanderWounded: Boolean = false,
    )

    fun role(type: UnitType?, monster: Boolean = false): BattleRole = when {
        monster -> BattleRole.MONSTERS
        type == UnitType.DRAGON_ARTILLERY -> BattleRole.ARTILLERY
        type == UnitType.KNIGHT -> BattleRole.CAVALRY
        (type?.ranged ?: 0) >= 8 -> BattleRole.ARCHERS
        type in listOf(UnitType.GOLD_SPEAR, UnitType.CRANE_GUARD, UnitType.DEER_CORPS) -> BattleRole.SPEARS
        else -> BattleRole.INFANTRY
    }

    fun project(session: BattleSession): BattleScene {
        val fortified = session.tactic == Tactic.FORTIFY || session.enemyFortification > 0 || session.segments.any { it.cover > 0 }
        val defenderEnemy = fortified && session.tactic != Tactic.FORTIFY
        val own = session.contingents.filter { it.soldiers > 0 }.map {
            Troops(BattalionKey(it.section, false, it.type, it.formation, it.routed), it.soldiers,
                it.commanderId != null, it.commanderWounded && !it.commanderRescued)
        }
        val enemies = if (session.enemyRoster.isNotEmpty()) session.enemyRoster.filter { it.soldiers > 0 }.map {
            Troops(BattalionKey(it.section, true, it.type, it.formation,
                session.fronts.firstOrNull { f -> f.section == it.section }?.intent == BattleAiIntent.WITHDRAW), it.soldiers)
        } else session.fronts.filter { it.enemySoldiers > 0 }.map {
            Troops(BattalionKey(it.section, true, null, BattleFormation.LINE, it.intent == BattleAiIntent.WITHDRAW), it.enemySoldiers)
        }
        val troops = (own + enemies).groupBy { it.key }.map { (key, values) ->
            Troops(key, values.sumOf { it.soldiers }, values.any { it.commander }, values.any { it.commanderWounded })
        }.sortedWith(compareBy<Troops>({ it.key.section.ordinal }, { it.key.enemy },
            { role(it.key.type).ordinal }, { it.key.type?.ordinal ?: -1 }, { it.key.formation.ordinal }, { it.key.routed }))
        val perBattalion = (MAX_MARKERS / troops.size.coerceAtLeast(1)).coerceIn(1, MAX_MARKERS_PER_BATTALION)
        val formations = troops.groupBy { it.key.section to it.key.enemy }.flatMap { (_, frontTroops) ->
            frontTroops.mapIndexed { index, troop ->
                val key = troop.key
                val segment = session.segment(key.section)
                val front = session.fronts.firstOrNull { it.section == key.section }
                val role = role(key.type, key.enemy && session.enemy == EnemyType.TAO_TEI)
                val defender = if (fortified) key.enemy == defenderEnemy else !key.enemy
                val contact = segment?.contactState?.allowsMelee == true && front?.enemyDistance == 0
                val onWall = fortified && defender && segment != null && !segment.fallenBack &&
                    segment.integrity > 0 && segment.contactState !in listOf(BattleContactState.BREACHED, BattleContactState.COURTYARD) &&
                    (role == BattleRole.ARCHERS || contact && role in listOf(BattleRole.INFANTRY, BattleRole.SPEARS)) && !key.routed
                val columns = if (frontTroops.size <= 3) frontTroops.size else 3
                val row = index / columns
                val x = if (key.section == BattleSection.RESERVE) .18f + .64f * (index + .5f) / frontTroops.size
                    else (key.section.ordinal + .12f + .76f * (index % columns + .5f) / columns) / 3f
                val ranged = role == BattleRole.ARCHERS || role == BattleRole.ARTILLERY
                val y = when {
                    key.section == BattleSection.RESERVE -> if (defender) .89f else .08f
                    key.routed -> if (defender) .82f else .10f
                    onWall -> WALL_Y - .025f + row * .024f
                    defender && segment?.fallenBack == true -> .77f + row * .025f
                    defender -> (if (ranged) .73f else if (contact) .65f else .70f) + row * .026f
                    else -> {
                        val contactY = when (segment?.contactState) {
                            BattleContactState.COURTYARD -> .73f
                            BattleContactState.BREACHED -> .625f
                            BattleContactState.WALL_ASSAULT -> WALL_Y - .06f
                            BattleContactState.FIELD_CONTACT -> .62f
                            else -> WALL_Y - .075f
                        }
                        contactY - .36f * ((front?.enemyDistance ?: 180) / 400f).coerceIn(0f, 1f) -
                            (if (ranged && !contact) .065f else 0f) - row * .03f
                    }
                }
                val markers = minOf(troop.soldiers, perBattalion, ceil(sqrt(troop.soldiers.toDouble()) * 1.5).toInt())
                val files = minOf(markers, when (key.formation) {
                    BattleFormation.LINE, BattleFormation.SHIELD_WALL -> 6
                    BattleFormation.DENSE -> 4
                    BattleFormation.LOOSE -> 5
                })
                BattleBattalion(key, troop.soldiers, role, BattlePoint(x, y.coerceIn(.06f, .91f)),
                    files, (markers + files - 1) / files, markers,
                    when (key.formation) { BattleFormation.DENSE -> .78f; BattleFormation.LOOSE -> 1.35f; else -> 1f },
                    onWall, troop.commander, troop.commanderWounded)
            }
        }
        val currentReport = session.exchanges.lastOrNull()?.takeIf { it.minute == session.minute && session.step > 0 }
        return BattleScene(session.seed, session.step, session.minute, fortified, defenderEnemy, formations,
            session.fronts.map { front -> BattleSceneFront(front.section, front.enemyDistance,
                session.segment(front.section)?.contactState ?: BattleContactState.DISTANT,
                session.segment(front.section), session.terrain[front.section] ?: BattleTerrain.PLAIN,
                session.soldiers(front.section), front.enemySoldiers,
                currentReport?.fronts?.firstOrNull { it.section == front.section }) },
            session.siegeDevices.filter { it.detected }, session.reserveReinforcement,
            if (session.participation == BattleParticipation.PERSONAL) formations.firstOrNull {
                !it.key.enemy && !it.key.routed && it.key.section == session.personalSection
            }?.position else null)
    }

    fun effects(front: BattleSceneFront): BattleExchangeEffects {
        val r = front.report
        return BattleExchangeEffects(r?.arrowsUsed ?: 0, r?.enemyArrowsUsed ?: 0, r?.artilleryChargesUsed ?: 0,
            if ((r?.arrowsUsed ?: 0) > 0) r!!.enemyDamage.ranged else 0,
            if ((r?.enemyArrowsUsed ?: 0) > 0) r!!.ownDamage.ranged else 0,
            if ((r?.artilleryChargesUsed ?: 0) > 0) r!!.enemyDamage.artillery else 0,
            if (r?.contactState?.allowsMelee == true && front.distance == 0) r.ownDamage.melee + r.enemyDamage.melee else 0,
            r?.structuralDamage ?: 0, r?.gateDamage ?: 0, r?.deviceDamage ?: 0, r?.enemyDamage?.wallWeapons ?: 0)
    }

    /** Flights only exist within the exchange animation, never while paused at its end. */
    fun flightProgress(frame: Float, arrow: Int): Float? {
        val launch = .16f + (arrow % 4) * .035f
        val progress = (frame - launch) / .48f
        return progress.takeIf { it >= 0f && it < 1f }
    }
}
