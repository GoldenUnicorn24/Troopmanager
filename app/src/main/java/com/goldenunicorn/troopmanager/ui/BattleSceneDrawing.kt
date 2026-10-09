package com.goldenunicorn.troopmanager.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.PI
import kotlin.math.sin

internal val BattleStone = Color(0xFF68716E)
internal val BattleSteel = Color(0xFFC0C9C5)
internal val BattleEarth = Color(0xFFAC9878)

/** Original procedural artwork. All geometry scales to the available field, including landscape. */
internal fun DrawScope.battleGround(session: BattleSession, scene: BattleScene, selected: BattleSection, textured: Boolean) {
    val winter = session.season == Season.WINTER
    val base = when {
        session.night -> Color(0xFF131F2A)
        winter -> Color(0xFF7C8988)
        session.season == Season.AUTUMN -> Color(0xFF534733)
        else -> Color(0xFF384639)
    }
    drawRect(Brush.verticalGradient(listOf(base, base.copy(red = base.red * .62f, green = base.green * .67f, blue = base.blue * .73f))), alpha = if (textured) .48f else 1f)
    repeat(110) { i ->
        val x = size.width * ((i * 73 + 19) % 997) / 997f
        val y = size.height * ((i * 137 + 11) % 991) / 991f
        val scale = (.4f + y / size.height) * size.width / 170f
        drawOval(if (winter) Color.White.copy(alpha = .10f) else Color(0xFFAFAB85).copy(alpha = .10f), Offset(x, y), Size(scale * 2.8f, scale * .6f))
    }
    val road = Path().apply {
        moveTo(size.width * .48f, 0f); lineTo(size.width * .51f, 0f)
        lineTo(size.width * .57f, size.height); lineTo(size.width * .41f, size.height); close()
    }
    drawPath(road, BattleEarth.copy(alpha = .10f))
    if (scene.fortified) drawRect(Color(0xFF4D5049).copy(alpha = .48f), Offset(0f, size.height * .65f), Size(size.width, size.height * .35f))
    scene.fronts.forEach { front ->
        val x0 = size.width * front.section.ordinal / 3f
        if (front.section == selected) drawRect(Color(0xFF8DB8C5).copy(alpha = .055f), Offset(x0, 0f), Size(size.width / 3f, size.height))
        if (front.section != BattleSection.LEFT) drawLine(Color(0xFFE0D7C2).copy(alpha = .09f), Offset(x0, 0f), Offset(x0, size.height * .92f), .6.dp.toPx())
        when (front.terrain) {
            BattleTerrain.FOREST -> repeat(7) { i ->
                val p = Offset(x0 + size.width / 3 * (.05f + i % 2 * .87f), size.height * (.06f + i * .057f))
                drawOval(Color.Black.copy(alpha = .25f), p, Size(size.width * .035f, size.height * .025f))
                drawCircle(Color(0xFF1A3025), size.width * .025f, p - Offset(0f, size.height * .018f))
                drawCircle(Color(0xFF355041), size.width * .017f, p - Offset(size.width * .007f, size.height * .023f))
            }
            BattleTerrain.RIVER, BattleTerrain.BRIDGE -> {
                drawLine(Color(0xFF547A84).copy(alpha = .50f), Offset(x0, size.height * .42f), Offset(x0 + size.width / 3, size.height * .47f), size.height * .055f)
                if (front.terrain == BattleTerrain.BRIDGE) repeat(9) { n ->
                    drawLine(BattleEarth, Offset(x0 + size.width * .125f, size.height * (.385f + n * .012f)),
                        Offset(x0 + size.width * .21f, size.height * (.385f + n * .012f)), size.height * .01f)
                }
            }
            BattleTerrain.HILL, BattleTerrain.PASS -> repeat(4) { i ->
                drawOval(Color(0xFF192420).copy(alpha = .20f), Offset(x0 + size.width * .02f, size.height * (.10f + i * .075f)), Size(size.width * .11f, size.height * .06f))
            }
            else -> Unit
        }
    }
    if (session.visibility < .8 || session.night) drawRect(Color(0xFF829298).copy(alpha = ((1 - session.visibility) * .16).toFloat()))
}

internal fun DrawScope.battleRampart(front: BattleSceneFront, u: Float) {
    val segment = front.segment ?: return
    val left = size.width * front.section.ordinal / 3f
    val width = size.width / 3f
    val y = size.height * BattleSceneProjection.WALL_Y
    val height = u * 9f
    val top = y - u * 3f
    drawRect(Color.Black.copy(alpha = .30f), Offset(left, y + height), Size(width, u * 5f))
    drawRect(Brush.verticalGradient(listOf(if (segment.integrity < 60) Color(0xFF655B51) else BattleStone, Color(0xFF363F3C))), Offset(left, top), Size(width, height + u * 3f))
    drawRect(Color(0xFF9B9F8E), Offset(left, top), Size(width, u * 2f))
    repeat(3) { row -> repeat(7) { col ->
        val x = left + col * width / 7f + if (row % 2 == 1) width / 14f else 0f
        drawLine(Color.Black.copy(alpha = .19f), Offset(x, y + row * u * 2.8f), Offset(minOf(x + width / 7f, left + width), y + row * u * 2.8f), u * .45f)
        drawLine(Color.Black.copy(alpha = .13f), Offset(x, y + row * u * 2.8f), Offset(x, y + (row + 1) * u * 2.8f), u * .4f)
    } }
    repeat(9) { i ->
        val x = left + (i + .15f) * width / 9f
        drawRect(Color(0xFF899287), Offset(x, top - u * 3f), Size(width / 16f, u * 3.8f))
        drawLine(BattleSteel.copy(alpha = .5f), Offset(x, top - u * 3f), Offset(x + width / 16f, top - u * 3f), u * .5f)
    }
    if (front.section != BattleSection.CENTER && segment.integrity > 0) {
        val x = if (front.section == BattleSection.LEFT) left + width * .04f else left + width * .81f
        drawRect(Color(0xFF414B48), Offset(x, top - u * 5f), Size(width * .15f, height + u * 7f))
        drawRect(Color(0xFF939B8D), Offset(x - u, top - u * 5f), Size(width * .15f + u * 2f, u * 3f))
        drawRect(Color(0xFF182423), Offset(x + width * .062f, y), Size(u, u * 3f))
    }
    val center = left + width / 2
    if (segment.breachWidth > 0 || segment.integrity == 0) {
        val gap = width * (segment.breachWidth / 180f).coerceIn(.18f, .62f)
        val breach = Path().apply {
            moveTo(center - gap / 2, top - u * 4f); lineTo(center - gap * .35f, y + u * 3)
            lineTo(center - gap / 2, y + height); lineTo(center + gap / 2, y + height)
            lineTo(center + gap * .3f, y + u * 2f); lineTo(center + gap / 2, top - u * 4f); close()
        }
        drawPath(breach, Color(0xFF303B30))
        repeat(11) { n ->
            drawRect(if (n % 2 == 0) BattleStone else BattleEarth, Offset(center + (n % 5 - 2) * gap / 6, y + (n % 3) * u * 3), Size(u * (2 + n % 3), u * 1.6f))
        }
    } else if (front.section == BattleSection.CENTER) {
        val gateWidth = width * .24f
        drawRect(Color(0xFF242C29), Offset(center - gateWidth / 2 - u, top - u), Size(gateWidth + u * 2, height + u * 4))
        if (!segment.gateOpen && segment.gateIntegrity > 0) {
            drawRect(Color(0xFF6B5037), Offset(center - gateWidth / 2, top), Size(gateWidth, height + u * 3))
            repeat(5) { n -> drawLine(Color.Black.copy(alpha = .35f), Offset(center - gateWidth / 2 + n * gateWidth / 5, top), Offset(center - gateWidth / 2 + n * gateWidth / 5, y + height), u * .6f) }
            repeat(2) { n -> drawLine(BattleSteel.copy(alpha = .55f), Offset(center - gateWidth / 2, y + (n * 5 + 1) * u), Offset(center + gateWidth / 2, y + (n * 5 + 1) * u), u) }
            if (segment.gateIntegrity < 60) crack(Offset(center, y), u * 3f)
        }
    }
    if (segment.integrity in 1..79) repeat(3) { n -> crack(Offset(left + width * (.18f + n * .29f), y + u * 3f), u * (100 - segment.integrity) / 18f) }
    if (segment.fire > 0) repeat(minOf(6, (segment.fire + 19) / 20)) { i ->
        drawCircle(Color(0xFFD6954D).copy(alpha = .7f), u * 1.8f, Offset(left + width * (.18f + i * .11f), top))
        drawCircle(Color(0xFF817C6A).copy(alpha = .35f), u * 3f, Offset(left + width * (.18f + i * .11f), top - u * 6f))
    }
}

private fun DrawScope.crack(p: Offset, u: Float) {
    val path = Path().apply { moveTo(p.x - u, p.y - u); lineTo(p.x, p.y); lineTo(p.x - u * .4f, p.y + u); lineTo(p.x + u * .5f, p.y + u * 1.7f) }
    drawPath(path, Color(0xFF202B29), style = Stroke(u * .25f))
}

internal fun DrawScope.battleBattalion(b: BattleBattalion, p: Offset, u: Float, defender: Boolean, frame: Float, moving: Boolean) {
    val cloth = if (b.key.enemy) Color(0xFFAC5950) else when (b.key.type?.culture) {
        Culture.WOOD_ELF -> Color(0xFF76A780); Culture.GOLD_ELF -> Color(0xFFD2B366)
        Culture.WALL -> Color(0xFF77A9BB); else -> Color(0xFF8EACC0)
    }
    val tint = cloth.copy(alpha = if (b.key.routed) .48f else 1f)
    val sx = u * 2.8f * b.spacing
    val sy = u * 3.7f * b.spacing
    repeat(b.markers) { i ->
        val scatter = if (b.key.formation == BattleFormation.LOOSE || b.key.routed) ((i * 17 % 5) - 2) * u * .4f else 0f
        val stride = if (moving && frame < .72f) sin((frame * 10f + i) * PI.toFloat()) * u * .35f else 0f
        val q = p + Offset((i % b.files - (b.files - 1) / 2f) * sx + scatter, (i / b.files - (b.ranks - 1) / 2f) * sy + stride)
        soldier(q, tint, b.role, u * if (b.role == BattleRole.CAVALRY) .83f else 1f, if (defender) -1f else 1f, b.key.formation == BattleFormation.SHIELD_WALL)
    }
    val flag = p + Offset(-sx * b.files / 2f - u, -sy * b.ranks / 2f - u * 2f)
    drawLine(BattleSteel.copy(alpha = tint.alpha), flag + Offset(0f, u * 6f), flag - Offset(0f, u * 3f), u * .5f)
    val banner = Path().apply {
        moveTo(flag.x, flag.y - u * 3f); lineTo(flag.x + u * 4f, flag.y - u * 2.5f)
        lineTo(flag.x + u * 3f, flag.y + u); lineTo(flag.x, flag.y + u * .5f); close()
    }
    drawPath(banner, tint)
    if (b.commander) {
        drawCircle(if (b.commanderWounded) Danger else Gold, u * 2.1f, flag + Offset(0f, u * 6f), style = Stroke(u * .7f))
        if (b.commanderWounded) drawLine(Danger, flag + Offset(-u, u * 5f), flag + Offset(u, u * 7f), u * .6f)
    }
}

private fun DrawScope.soldier(p: Offset, cloth: Color, role: BattleRole, u: Float, facing: Float, shieldWall: Boolean) {
    drawOval(Color.Black.copy(alpha = cloth.alpha * .32f), p + Offset(-u * 1.3f, u), Size(u * 3.2f, u * 1.2f))
    if (role == BattleRole.ARTILLERY) { battleDevice(SiegeDevice.CATAPULT, p, u * .42f, 100, true); return }
    if (role == BattleRole.MONSTERS) {
        drawOval(Color(0xFF73856B).copy(alpha = cloth.alpha), p - Offset(u * 1.6f, u), Size(u * 3.2f, u * 2.6f))
        repeat(2) { leg -> drawLine(cloth, p + Offset((leg * 2 - 1) * u, u), p + Offset((leg * 3 - 1.5f) * u, u * 2f), u * .6f) }
        drawCircle(BattleEarth, u, p + Offset(0f, facing * u * 1.6f)); return
    }
    if (role == BattleRole.CAVALRY) {
        drawOval(Color(0xFF8B7964).copy(alpha = cloth.alpha), p - Offset(u * 1.3f, u), Size(u * 2.6f, u * 4f))
        drawCircle(Color(0xFFB0A08B).copy(alpha = cloth.alpha), u * .85f, p + Offset(u, -u * 1.6f))
        drawLine(BattleSteel.copy(alpha = cloth.alpha), p + Offset(u * 1.5f, u), p + Offset(u * 2.1f, -u * 4.4f), u * .45f)
    }
    drawLine(Color(0xFF25302E).copy(alpha = cloth.alpha), p + Offset(-u * .5f, u), p + Offset(-u * .8f, u * 2.4f), u * .65f)
    drawLine(Color(0xFF25302E).copy(alpha = cloth.alpha), p + Offset(u * .5f, u), p + Offset(u * .8f, u * 2.4f), u * .65f)
    drawOval(cloth, p - Offset(u, u), Size(u * 2f, u * 2.7f))
    drawCircle(BattleSteel.copy(alpha = cloth.alpha), u * .74f, p - Offset(0f, u * 1.4f))
    drawLine(Color(0xFF26302F).copy(alpha = cloth.alpha), p + Offset(-u * .55f, -u * 1.3f), p + Offset(u * .55f, -u * 1.3f), u * .35f)
    when (role) {
        BattleRole.ARCHERS -> {
            drawArc(BattleEarth.copy(alpha = cloth.alpha), -80f, 160f, false, p + Offset(u * .7f, -u * 2.7f), Size(u * 1.8f, u * 3.8f), style = Stroke(u * .4f))
            drawLine(BattleSteel.copy(alpha = cloth.alpha * .7f), p + Offset(u * 1.7f, -u * 2.6f), p + Offset(u * 1.7f, u), u * .22f)
        }
        BattleRole.INFANTRY, BattleRole.SPEARS -> {
            val length = if (role == BattleRole.SPEARS) 5.5f else 2.8f
            drawLine(BattleSteel.copy(alpha = cloth.alpha), p + Offset(u * 1.2f, u), p + Offset(u * 1.5f, -u * length), u * .4f)
            drawOval(if (shieldWall) BattleSteel.copy(alpha = cloth.alpha) else cloth,
                p + Offset(-u * 1.7f, facing * u - u * .3f), Size(u * (if (shieldWall) 2.4f else 1.7f), u * 2.1f))
            drawLine(Gold.copy(alpha = cloth.alpha * .65f), p + Offset(-u * 1.4f, facing * u + u * .5f), p + Offset(-u * .4f, facing * u + u * .5f), u * .3f)
        }
        else -> Unit
    }
}

internal fun DrawScope.battleDevice(type: SiegeDevice, p: Offset, u: Float, integrity: Int, active: Boolean) {
    val wood = Color(0xFF9E7950).copy(alpha = if (active) 1f else .4f)
    val dark = Color(0xFF343A32)
    drawOval(Color.Black.copy(alpha = .3f), p + Offset(-u * 5f, u * 3f), Size(u * 10f, u * 3f))
    when (type) {
        SiegeDevice.TOWER -> {
            drawRect(dark, p - Offset(u * 3.5f, u * 10f), Size(u * 7f, u * 14f))
            repeat(4) { n -> drawRect(wood, p + Offset(-u * 3.5f, (-10f + n * 3.5f) * u), Size(u * 7f, u * 2.6f)) }
            drawLine(BattleSteel, p + Offset(-u * 4f, -u * 10f), p + Offset(u * 4f, -u * 10f), u)
        }
        SiegeDevice.RAM -> {
            val roof = Path().apply {
                moveTo(p.x - u * 5f, p.y); lineTo(p.x, p.y - u * 6f)
                lineTo(p.x + u * 5f, p.y); lineTo(p.x + u * 3f, p.y + u * 3f)
                lineTo(p.x - u * 3f, p.y + u * 3f); close()
            }
            drawPath(roof, wood)
            drawLine(dark, p - Offset(0f, u * 3f), p + Offset(0f, u * 6f), u * 2f)
            drawCircle(BattleSteel, u, p - Offset(0f, u * 4f))
        }
        SiegeDevice.LADDERS, SiegeDevice.CLIMBERS -> {
            repeat(2) { n -> drawLine(wood, p + Offset((n * 3 - 1.5f) * u, -u * 7f), p + Offset((n * 3 - 1.5f) * u, u * 4f), u * .7f) }
            repeat(6) { n -> drawLine(wood, p + Offset(-u * 1.5f, (-6 + n * 2) * u), p + Offset(u * 1.5f, (-6 + n * 2) * u), u * .5f) }
        }
        SiegeDevice.CATAPULT -> {
            drawRect(wood, p - Offset(u * 3f, u * 2f), Size(u * 6f, u * 5f))
            drawLine(dark, p + Offset(-u * 3f, u * 3f), p + Offset(u * 3f, -u * 2f), u)
            drawLine(wood, p + Offset(0f, u * 2f), p - Offset(u * 2f, u * 6f), u * 1.2f)
            drawCircle(BattleStone, u * 1.5f, p - Offset(u * 2f, u * 6f))
        }
        SiegeDevice.TUNNEL -> {
            drawOval(dark, p - Offset(u * 4f, u * 2f), Size(u * 8f, u * 4f))
            drawArc(wood, 180f, 180f, false, p - Offset(u * 3f, u * 4f), Size(u * 6f, u * 6f), style = Stroke(u))
        }
    }
    if (type in listOf(SiegeDevice.TOWER, SiegeDevice.RAM, SiegeDevice.CATAPULT)) repeat(2) { n ->
        drawCircle(dark, u * 1.2f, p + Offset((n * 7 - 3.5f) * u, u * 3f))
        drawCircle(wood, u * .5f, p + Offset((n * 7 - 3.5f) * u, u * 3f))
    }
    if (integrity < 100) {
        drawLine(dark, p + Offset(-u * 4f, u * 7f), p + Offset(u * 4f, u * 7f), u)
        drawLine(if (active) Danger else BattleStone, p + Offset(-u * 4f, u * 7f), p + Offset((-4 + 8 * integrity / 100f) * u, u * 7f), u)
    }
}
