package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class BattleBackdropSlot(val file: String) {
    FIELD("field.webp"), FOREST("forest.webp"), PASS("pass.webp"), FORTRESS("fortress.webp"), NIGHT("night.webp")
}
private data class BattalionMotion(val before: BattleBattalion?, val after: BattleBattalion?)
private data class ExchangeDrawing(
    val front: BattleSceneFront, val effects: BattleExchangeEffects,
    val ownBow: BattlePoint, val enemyBow: BattlePoint, val ownTarget: BattlePoint,
    val enemyTarget: BattlePoint, val artillery: BattlePoint, val deviceTarget: BattlePoint?,
)

/** One finite animation per new exchange. Loading a save and editing a plan never replay damage. */
@Composable
internal fun SessionBattleField(session: BattleSession, animations: Boolean, battleSpeed: Float,
    wallWeapons: List<WallWeaponStock> = emptyList(), modifier: Modifier = Modifier.fillMaxWidth().height(300.dp),
    selected: BattleSection = BattleSection.CENTER, compactLegend: Boolean = false,
    onSelect: (BattleSection) -> Unit = {}) {
    val scene = remember(session) { BattleSceneProjection.project(session) }
    val pulse = remember(session.seed) { Animatable(1f) }
    var lastScene by remember(session.seed) { mutableStateOf(scene) }
    var previous by remember(session.seed) { mutableStateOf(scene) }
    LaunchedEffect(scene, animations) {
        val newExchange = scene.step == lastScene.step + 1 && scene.minute > lastScene.minute
        previous = if (newExchange) lastScene else scene
        lastScene = scene
        if (animations && newExchange) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween((1800 / battleSpeed.coerceIn(.5f, 3f)).toInt(), easing = LinearEasing))
        } else pulse.snapTo(1f)
    }
    val motions = remember(scene, previous) {
        val old = previous.battalions.toMutableList()
        val next = scene.battalions.map { after ->
            val before = old.firstOrNull { it.key == after.key } ?: old.firstOrNull {
                it.key.section == after.key.section && it.key.type == after.key.type && it.key.enemy == after.key.enemy
            }
            old.remove(before)
            BattalionMotion(before, after)
        }
        (next + old.map { BattalionMotion(it, null) })
            .sortedBy { (it.after ?: it.before)!!.position.y }
    }
    val volleys = remember(scene, previous) {
        scene.fronts.map { front ->
            fun anchor(enemy: Boolean, role: BattleRole? = null, target: UnitType? = null, current: Boolean = false): BattlePoint {
                // A shooter or target may have died in this exchange; retain its pre-step position.
                val groups = (if (current) scene.battalions + previous.battalions else previous.battalions + scene.battalions)
                    .filter { it.key.section == front.section && it.key.enemy == enemy && !it.key.routed }
                val candidates = groups.filter { (role == null || it.role == role) && (target == null || it.key.type == target) }
                return candidates.ifEmpty { groups }.firstOrNull()?.position
                    ?: BattlePoint((front.section.ordinal + .5f) / 3f, if (scene.defender(enemy)) .68f else .40f)
            }
            val device = scene.devices.firstOrNull { it.id == front.report?.targetDeviceId }
            ExchangeDrawing(front, BattleSceneProjection.effects(front), anchor(false, BattleRole.ARCHERS),
                anchor(true, BattleRole.ARCHERS), anchor(false, current = true), anchor(true, target = front.report?.targetType, current = true),
                anchor(false, BattleRole.ARTILLERY), device?.let { devicePoint(it, scene) })
        }
    }
    val deviceMotions = remember(scene, previous) {
        scene.devices.map { it to (previous.devices.firstOrNull { old -> old.id == it.id } ?: it) }
    }
    val weapons = remember(wallWeapons, scene.fortified, scene.defenderEnemy) {
        wallWeapons.filter { it.count > 0 }.take(15).takeIf { scene.fortified && !scene.defenderEnemy }.orEmpty()
    }
    val context = LocalContext.current
    val slot = when {
        session.night -> BattleBackdropSlot.NIGHT
        scene.fortified -> BattleBackdropSlot.FORTRESS
        session.terrain.values.count { it == BattleTerrain.FOREST } >= 2 -> BattleBackdropSlot.FOREST
        session.terrain.values.any { it == BattleTerrain.PASS } -> BattleBackdropSlot.PASS
        else -> BattleBackdropSlot.FIELD
    }
    val backdrop = remember(context, slot) {
        val files = context.assets.list("battle-backdrops").orEmpty()
        val file = if (slot.file in files) slot.file else "ground.webp".takeIf { it in files }
        file?.let { "file:///android_asset/battle-backdrops/$it" }
    }
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B2423))) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (backdrop != null) AsyncImage(model = backdrop, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // Clock is read in draw only: projection and grouping do not run at animation framerate.
            val frame = pulse.value
            val unit = minOf(size.width / 150f, size.height / 92f)
            battleGround(session, scene, selected, backdrop != null)
            if (scene.fortified) scene.fronts.forEach { battleRampart(it, unit) }
            weapons.forEachIndexed { index, weapon ->
                val p = point(BattlePoint((weapon.section.ordinal + .22f + index % 3 * .22f) / 3f, BattleSceneProjection.WALL_Y - .035f))
                battleDevice(SiegeDevice.CATAPULT, p, unit * .65f, weapon.integrity, weapon.ammunition > 0)
            }
            deviceMotions.forEach { (device, old) ->
                val from = point(devicePoint(old, previous))
                val to = point(devicePoint(device, scene))
                battleDevice(device.type, from + (to - from) * movement(frame), unit, device.integrity, !device.disabled && device.crew > 0)
            }
            motions.forEach { motion ->
                val troop = if (frame < .72f) motion.before ?: return@forEach else motion.after ?: return@forEach
                val from = point((motion.before ?: troop).position)
                val to = point((motion.after ?: troop).position)
                battleBattalion(troop, from + (to - from) * movement(frame), unit, scene.defender(troop.key.enemy), frame,
                    motion.before != null && motion.after != null && motion.before.position != motion.after.position)
            }
            scene.hero?.let { drawCircle(Color.White, unit * 4f, point(it), style = Stroke(unit * .6f)) }
            scene.reserve?.let { reserve ->
                val from = point(BattlePoint(.5f, if (scene.defender(false)) .88f else .08f))
                val to = point(BattlePoint((reserve.section.ordinal + .5f) / 3f, if (scene.defender(false)) .76f else .23f))
                repeat(7) { index ->
                    val p = from + (to - from) * (index / 7f)
                    drawLine(ModernBlue.copy(alpha = .7f), p, p + (to - from) / 14f, unit * .65f)
                }
                // Route markers are not extra soldiers. Troops stay in reserve until the engine moves them.
                drawCircle(ModernBlue, unit * 2f, to, style = Stroke(unit * .6f))
            }
            if (animations && frame < 1f) volleys.forEach { exchange(it, frame, unit) }
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Transparent, Color.Black.copy(alpha = .16f))))
        }
        Row(Modifier.matchParentSize()) {
            scene.fronts.forEach { front ->
                val formations = scene.battalions.filter { it.key.section == front.section && !it.key.enemy }
                    .map { it.key.formation.label }.distinct().joinToString()
                Box(Modifier.weight(1f).fillMaxHeight().testTag("battle_scene_${front.section.name}").semantics {
                    contentDescription = "${front.section.label}: ${front.own} eigene, ${front.enemy} Gegner. $formations. " +
                        "${front.distance} Meter, ${front.contact.label}. " +
                        (if (scene.fortified) "Mauer ${front.segment?.integrity ?: 0} Prozent, Bresche ${front.segment?.breachWidth ?: 0}." else "Feldschlacht.")
                }.clickable(role = Role.Button, onClickLabel = "Abschnitt auswählen") { onSelect(front.section) })
            }
        }
        }
        val front = scene.fronts.firstOrNull { it.section == selected }
        val formations = scene.battalions.filter { !it.key.enemy && it.key.section == selected }.map { it.key.formation }.distinct()
        val formation = when (formations.size) { 0 -> "Keine Truppen"; 1 -> formations.single().label; else -> "Gemischte Formation" }
        val fire = when {
            front?.segment?.rangedOrder == RangedOrder.HOLD -> "Feuer halten"
            session.battleArrowsRemaining == 0 -> "Pfeile erschöpft"
            (front?.report?.arrowsUsed ?: 0) > 0 -> "${front!!.report!!.arrowsUsed} Pfeile abgefeuert"
            else -> front?.contact?.label ?: "Aufstellung"
        }
        Text("$formation · $fire", color = Color(0xFFE0D7C2), fontSize = if (compactLegend) 10.sp else 11.sp,
            lineHeight = 13.sp, modifier = Modifier.fillMaxWidth()
                .background(Color(0xE611191C)).padding(horizontal = 10.dp, vertical = 5.dp).testTag("battle_scene_caption"))
    }
}
private fun movement(frame: Float) = (frame / .72f).coerceIn(0f, 1f)
private fun DrawScope.point(p: BattlePoint) = Offset(size.width * p.x, size.height * p.y)
private fun devicePoint(d: SiegeDeviceState, scene: BattleScene) = BattlePoint(
    (d.section.ordinal + when (d.type) {
        SiegeDevice.RAM, SiegeDevice.TUNNEL -> .5f; SiegeDevice.TOWER -> .8f; SiegeDevice.CATAPULT -> .22f; else -> .15f
    }) / 3f,
    if (scene.defenderEnemy) .68f + .24f * d.distance / 400f
    else BattleSceneProjection.WALL_Y - .045f - .40f * d.distance / 400f)

private fun DrawScope.exchange(e: ExchangeDrawing, frame: Float, u: Float) {
    val effects = e.effects
    fun volley(count: Int, start: BattlePoint, end: BattlePoint, tint: Color) {
        repeat(minOf(12, count)) { i ->
            val t = BattleSceneProjection.flightProgress(frame, i) ?: return@repeat
            val from = point(start) + Offset((i % 6 - 2.5f) * u * 1.5f, (i / 6) * u)
            val to = point(end) + Offset((i * 7 % 9 - 4) * u, (i * 3 % 5 - 2) * u)
            val delta = to - from
            val height = minOf(size.height * .09f, u * 18f)
            val p = from + delta * t - Offset(0f, sin(t * PI.toFloat()) * height)
            val tangent = delta - Offset(0f, cos(t * PI.toFloat()) * PI.toFloat() * height)
            val dir = tangent / tangent.getDistance().coerceAtLeast(1f)
            drawLine(Color.Black.copy(alpha = .4f), p - dir * u * 3f + Offset(u * .4f, u * .4f), p, u * .8f)
            drawLine(tint, p - dir * u * 3f, p, u * .55f)
            drawCircle(BattleSteel, u * .4f, p)
        }
    }
    if (effects.arrows > 0) {
        val markers = minOf(12, effects.arrows)
        // Device damage is ambiguous when artillery also fired. Do not attribute it to arrows then.
        val deviceMarkers = if (e.deviceTarget != null && effects.deviceDamage > 0 && effects.artillery == 0)
            if (effects.arrowHits == 0) markers else markers / 2 else 0
        volley(markers - deviceMarkers, e.ownBow, e.enemyTarget, Color(0xFFEDCD87))
        if (deviceMarkers > 0) volley(deviceMarkers, e.ownBow, e.deviceTarget!!, Color(0xFFEDCD87))
    }
    if (effects.enemyArrows > 0) volley(effects.enemyArrows, e.enemyBow, e.ownTarget, Color(0xFFD88A73))
    if (effects.artillery > 0) BattleSceneProjection.flightProgress(frame, 0)?.let { t ->
        val from = point(e.artillery)
        val to = point(if (effects.artilleryHits > 0) e.enemyTarget else e.deviceTarget ?: e.enemyTarget)
        drawCircle(BattleEarth, u * 1.5f, from + (to - from) * t - Offset(0f, sin(t * PI.toFloat()) * size.height * .13f))
    }
    val impact = ((frame - .70f) / .30f).takeIf { it in 0f..1f } ?: return
    fun hit(where: BattlePoint, strength: Int, color: Color) {
        if (strength <= 0) return
        val p = point(where)
        drawCircle(color.copy(alpha = (1 - impact) * .38f), u * (2f + impact * 5f), p, style = Stroke(u * .6f))
        repeat(minOf(5, strength)) { i ->
            val direction = Offset(cos(i * 1.27f * PI.toFloat()), sin(i * 1.27f * PI.toFloat()))
            drawLine(color.copy(alpha = 1 - impact), p + direction * u * (1 + impact * 3), p + direction * u * (2 + impact * 6), u * .55f)
        }
    }
    hit(e.enemyTarget, effects.arrowHits, Gold)
    hit(e.ownTarget, effects.enemyArrowHits, Color(0xFFD88A73))
    hit(e.enemyTarget, effects.artilleryHits, BattleEarth)
    hit(e.enemyTarget, effects.wallWeaponHits, BattleEarth)
    e.deviceTarget?.let { hit(it, effects.deviceDamage, BattleEarth) }
    hit(BattlePoint((e.front.section.ordinal + .5f) / 3f, BattleSceneProjection.WALL_Y), effects.wallDamage + effects.gateDamage, BattleStone)
    if (effects.meleeHits > 0)
        hit(BattlePoint((e.ownTarget.x + e.enemyTarget.x) / 2, (e.ownTarget.y + e.enemyTarget.y) / 2), effects.meleeHits, BattleSteel)
}
