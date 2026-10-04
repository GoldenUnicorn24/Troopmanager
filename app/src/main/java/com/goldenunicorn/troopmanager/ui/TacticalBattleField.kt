package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.BattleEngine
import com.goldenunicorn.troopmanager.engine.BattleStateEngine
import com.goldenunicorn.troopmanager.model.*

/** Bounded formation rendering. Position and attack effects use persisted engine state only. */
@Composable
internal fun SessionBattleField(session: BattleSession, animations: Boolean, battleSpeed: Float,
    wallWeapons: List<WallWeaponStock> = emptyList(), modifier: Modifier = Modifier.fillMaxWidth().height(300.dp),
    selected: BattleSection = BattleSection.CENTER, onSelect: (BattleSection) -> Unit = {}) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(session.step, animations) {
        if (animations && session.step > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween((260 / battleSpeed.coerceIn(.5f, 3f)).toInt().coerceIn(100, 300)))
        } else pulse.snapTo(1f)
    }
    val groups = remember(session.contingents, session.fronts) { BattleEngine.visualGroups(session) }
    val fort = session.tactic == Tactic.FORTIFY || session.enemyFortification > 0
    val frame = pulse.value
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(Color(0xFF172B31), Color(0xFF111A21), Ink)))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val wallY = h * .62f
            BattleStateEngine.sections.forEachIndexed { index, section ->
                val x0 = w * index / 3f
                val x1 = w * (index + 1) / 3f
                val segment = session.segment(section)
                val front = session.fronts.firstOrNull { it.section == section }
                val enemyDistance = front?.enemyDistance ?: 180
                val contact = segment?.contactState?.allowsMelee == true
                drawRect(if (selected == section) ModernBlue.copy(alpha = .08f) else Color.Transparent,
                    Offset(x0, 0f), Size(w / 3f, h))
                if (index > 0) drawLine(Mist.copy(alpha = .12f), Offset(x0, 0f), Offset(x0, h), 1.dp.toPx())
                when (session.terrain[section]) {
                    BattleTerrain.FOREST -> repeat(10) { n ->
                        drawCircle(Color(0xFF284638).copy(alpha = .45f), 8.dp.toPx(), Offset(x0 + w / 3 * ((n * 17 % 93) / 100f), h * (.1f + (n % 4) * .1f)))
                    }
                    BattleTerrain.RIVER, BattleTerrain.BRIDGE -> {
                        drawRect(ModernBlue.copy(alpha = .18f), Offset(x0, h * .42f), Size(w / 3, h * .09f))
                        if (session.terrain[section] == BattleTerrain.BRIDGE) drawRect(Color(0xFF80664B), Offset(x0 + w / 9, h * .39f), Size(w / 9, h * .15f))
                    }
                    BattleTerrain.HILL, BattleTerrain.PASS -> repeat(3) { n ->
                        drawArc(Mist.copy(alpha = .09f), 190f, 160f, false, Offset(x0 + w / 12, h * (.1f + n * .15f)), Size(w / 5, h * .15f), style = Stroke(2.dp.toPx()))
                    }
                    else -> Unit
                }
                val ownY = if (fort && session.tactic == Tactic.FORTIFY) wallY + if (contact) h * .025f else h * .075f else h * .66f
                val enemyY = when (segment?.contactState) {
                    BattleContactState.COURTYARD -> h * .72f
                    BattleContactState.BREACHED -> wallY + h * .025f
                    BattleContactState.WALL_ASSAULT -> wallY - h * .025f
                    else -> (if (fort) wallY - h * .035f else h * .60f) - h * .43f * (enemyDistance / 400f).coerceIn(0f, 1f)
                }
                if (fort) {
                    val integrity = segment?.integrity ?: session.wallIntegrity
                    val center = Offset(x0 + w / 6, wallY)
                    drawRoundRect(if (integrity < 30) Color(0xFF6B4038) else Color(0xFF59606A),
                        Offset(x0 + 5.dp.toPx(), wallY - 9.dp.toPx()), Size(w / 3 - 10.dp.toPx(), 18.dp.toPx()), 4.dp.toPx().let { androidx.compose.ui.geometry.CornerRadius(it) })
                    repeat(6) { n -> drawRect(Color(0xFF7D8890), Offset(x0 + 12.dp.toPx() + n * (w / 3 - 25.dp.toPx()) / 6, wallY - 15.dp.toPx()), Size(7.dp.toPx(), 7.dp.toPx())) }
                    if (integrity == 0 || (segment?.breachWidth ?: 0) > 0) {
                        val gap = w / 3 * ((segment?.breachWidth ?: 50) / 180f).coerceIn(.18f, .6f)
                        drawRect(Ink, center - Offset(gap / 2, 18.dp.toPx()), Size(gap, 36.dp.toPx()))
                        repeat(4) { n -> drawCircle(Color(0xFF987862), 3.dp.toPx(), center + Offset((n - 1.5f) * 7.dp.toPx(), (n % 2 - .5f) * 12.dp.toPx())) }
                    }
                    if (section == BattleSection.CENTER) {
                        val open = segment?.gateOpen == true || segment?.gateIntegrity == 0
                        drawRect(if (open) Ink else Color(0xFF80684B), center - Offset(14.dp.toPx(), 13.dp.toPx()), Size(28.dp.toPx(), 26.dp.toPx()))
                        if (!open) repeat(3) { n -> drawLine(Gold.copy(alpha = .45f), center + Offset(-12.dp.toPx(), (n - 1) * 7.dp.toPx()), center + Offset(12.dp.toPx(), (n - 1) * 7.dp.toPx()), 2.dp.toPx()) }
                    }
                    drawLine(if (integrity < 30) Danger else Success, Offset(x0 + 10.dp.toPx(), wallY + 18.dp.toPx()),
                        Offset(x0 + 10.dp.toPx() + (w / 3 - 20.dp.toPx()) * integrity / 100f, wallY + 18.dp.toPx()), 3.dp.toPx())
                }
                fun formations(enemy: Boolean, y: Float) {
                    val rows = groups.filter { it.section == section && it.enemy == enemy }.take(12)
                    rows.forEachIndexed { n, group ->
                        val position = Offset(x0 + w / 3 * (.17f + (n % 4) * .2f), y + (n / 4 - 1) * 16.dp.toPx())
                        formation(position, if (enemy) Danger else if (group.type?.culture == Culture.WOOD_ELF) Success else Gold,
                            group.type == UnitType.KNIGHT, group.routed, 5.dp.toPx())
                        if (!enemy && session.participation == BattleParticipation.PERSONAL && session.personalSection == section && n == 0)
                            drawCircle(Color.White, 10.dp.toPx(), position, style = Stroke(1.5.dp.toPx()))
                    }
                }
                formations(true, enemyY)
                formations(false, ownY)
                val devices = session.siegeDevices.filter { it.section == section && it.detected }.take(6)
                devices.forEachIndexed { n, device ->
                    val position = Offset(x0 + w / 3 * (.2f + n * .12f), wallY - h * .43f * (device.distance / 400f).coerceIn(0f, 1f))
                    siegeDevice(device.type, position, if (device.disabled) Mist.copy(alpha = .4f) else Color(0xFFC29762))
                    drawLine(if (device.disabled) Mist else Danger, position + Offset(-8.dp.toPx(), 16.dp.toPx()),
                        position + Offset(-8.dp.toPx() + 16.dp.toPx() * device.integrity / 100f, 16.dp.toPx()), 2.dp.toPx())
                }
                wallWeapons.filter { it.section == section && it.count > 0 }.take(5).forEachIndexed { n, weapon ->
                    val position = Offset(x0 + w / 3 * (.15f + n * .16f), wallY - 5.dp.toPx())
                    val ready = weapon.ammunition > 0 && weapon.integrity > 0 && weapon.reloadRounds == 0
                    drawCircle(if (ready) Gold else Mist, 4.dp.toPx(), position)
                    drawLine(if (ready) Gold else Mist, position, position + Offset(0f, -12.dp.toPx()), 3.dp.toPx())
                }
                val report = session.lastReport(section)
                if (animations && frame < 1f && report != null) {
                    if (report.arrowsUsed > 0) repeat(4) { n ->
                        val x = x0 + w / 3 * (.22f + n * .17f)
                        val y = ownY + (enemyY - ownY) * frame
                        drawLine(Gold, Offset(x, y), Offset(x, y + 7.dp.toPx()), 1.5.dp.toPx())
                    }
                    if (report.enemyArrowsUsed > 0) repeat(3) { n ->
                        val x = x0 + w / 3 * (.26f + n * .2f)
                        val y = enemyY + (ownY - enemyY) * frame
                        drawLine(Danger, Offset(x, y), Offset(x, y - 7.dp.toPx()), 1.5.dp.toPx())
                    }
                    if (contact && report.ownDamage.melee + report.enemyDamage.melee > 0)
                        drawCircle(Color.White.copy(alpha = (1f - frame) * .7f), (6 + frame * 10).dp.toPx(), Offset(x0 + w / 6, (ownY + enemyY) / 2), style = Stroke(2.dp.toPx()))
                }
            }
            groups.filter { it.section == BattleSection.RESERVE && !it.enemy }.take(12).forEachIndexed { n, group ->
                formation(Offset(w * (.2f + (n % 6) * .12f), h * .90f + (n / 6) * 13.dp.toPx()), ModernBlue, group.type == UnitType.KNIGHT, group.routed, 4.dp.toPx())
            }
            drawLine(ModernBlue.copy(alpha = .25f), Offset(w * .12f, h * .84f), Offset(w * .88f, h * .84f), 1.dp.toPx())
        }
        Row(Modifier.matchParentSize()) {
            BattleStateEngine.sections.forEach { section ->
                val segment = session.segment(section)
                Box(Modifier.weight(1f).fillMaxHeight().semantics {
                    contentDescription = "${section.label}: ${session.soldiers(section)} eigene, ${session.fronts.firstOrNull { it.section == section }?.enemySoldiers ?: 0} Gegner. ${segment?.contactState?.label}. Mauer ${segment?.integrity ?: 0} Prozent."
                }.clickable(role = Role.Button, onClickLabel = "Abschnitt auswählen") { onSelect(section) })
            }
        }
        androidx.compose.material3.Text("■ Heer   ◆ Reiter   ▰ Geräte   Blau: Reserve", color = Mist, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

private fun DrawScope.formation(position: Offset, color: Color, cavalry: Boolean, routed: Boolean, extent: Float) {
    val tint = if (routed) color.copy(alpha = .35f) else color
    if (cavalry) {
        val diamond = Path().apply { moveTo(position.x, position.y - extent * 1.3f); lineTo(position.x + extent, position.y); lineTo(position.x, position.y + extent * 1.3f); lineTo(position.x - extent, position.y); close() }
        drawPath(diamond, tint)
    } else {
        drawRect(tint, position - Offset(extent, extent), Size(extent * 2, extent * 2))
        drawLine(Ink.copy(alpha = .5f), position - Offset(extent, 0f), position + Offset(extent, 0f), 1f)
        drawLine(Ink.copy(alpha = .5f), position - Offset(0f, extent), position + Offset(0f, extent), 1f)
    }
    drawLine(tint, position + Offset(extent, extent), position + Offset(extent, -extent * 2.3f), 1.5.dp.toPx())
    drawRect(tint, position + Offset(extent, -extent * 2.3f), Size(extent, extent * .6f))
}

private fun DrawScope.siegeDevice(type: SiegeDevice, position: Offset, color: Color) {
    val r = 8.dp.toPx()
    when (type) {
        SiegeDevice.TOWER -> {
            drawRect(color, position - Offset(r, r * 1.8f), Size(r * 2, r * 3))
            repeat(3) { n -> drawLine(Ink, position + Offset(-r, (n - 1) * r), position + Offset(r, (n - 1) * r), 2.dp.toPx()) }
        }
        SiegeDevice.RAM -> { drawRect(color, position - Offset(r * 1.5f, r * .4f), Size(r * 3, r * .8f)); drawLine(color, position + Offset(0f, -r), position + Offset(0f, r), 2.dp.toPx()) }
        SiegeDevice.LADDERS, SiegeDevice.CLIMBERS -> {
            drawLine(color, position + Offset(-r * .4f, -r), position + Offset(-r * .4f, r), 2.dp.toPx())
            drawLine(color, position + Offset(r * .4f, -r), position + Offset(r * .4f, r), 2.dp.toPx())
            repeat(4) { n -> drawLine(color, position + Offset(-r * .4f, (n / 2f - 1) * r), position + Offset(r * .4f, (n / 2f - 1) * r), 1.dp.toPx()) }
        }
        SiegeDevice.CATAPULT -> { drawCircle(color, r * .8f, position, style = Stroke(2.dp.toPx())); drawLine(color, position + Offset(-r, r), position + Offset(r, -r), 3.dp.toPx()) }
        SiegeDevice.TUNNEL -> { drawCircle(color, r, position, style = Stroke(2.dp.toPx())); drawLine(color, position + Offset(-r, r), position + Offset(r, -r), 2.dp.toPx()) }
    }
}
