package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.CrownEngine
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kronlande map: every province is a hex tile clustered by land. Colors show the owner,
 * locked lands are dimmed, marching enemies are drawn as red arrows. Tap a tile to select it.
 */
private const val MAP_W = 19.5f
private const val MAP_H = 18f
private val SQRT3 = sqrt(3f)

/** Land cluster centers in hex-size units; the layout loosely follows the land neighborhoods. */
private val landCenters: Map<CrownLand, Offset> = mapOf(
    CrownLand.SONNENHAIN to Offset(3.6f, 3f),
    CrownLand.ASCHESTEPPE to Offset(15.9f, 3f),
    CrownLand.ALDMARK to Offset(9.4f, 6.6f),
    CrownLand.WALDHAIN to Offset(3.2f, 9.2f),
    CrownLand.KRANICHWALL to Offset(15.6f, 9.6f),
    CrownLand.GRENZMARK to Offset(9.8f, 12.8f),
    CrownLand.KUESTENREICH to Offset(3.8f, 15f),
)

/** Center plus five neighbors of a pointy-top hex. */
private val clusterOffsets = listOf(
    Offset(0f, 0f), Offset(SQRT3, 0f), Offset(-SQRT3, 0f),
    Offset(SQRT3 / 2, -1.5f), Offset(-SQRT3 / 2, -1.5f), Offset(SQRT3 / 2, 1.5f),
)

private val provinceUnits: Map<String, Offset> by lazy {
    CrownCatalog.startProvinces.groupBy { it.land }.flatMap { (land, list) ->
        val center = landCenters[land] ?: Offset(MAP_W / 2, MAP_H / 2)
        list.mapIndexed { i, p -> p.id to (center + clusterOffsets[i % clusterOffsets.size]) }
    }.toMap()
}

private fun ownerFill(owner: ProvinceOwner): Color = when (owner) {
    ProvinceOwner.PLAYER -> Emerald
    ProvinceOwner.FREE -> ModernBlue
    ProvinceOwner.RIVAL -> Gold
    else -> Crimson
}

@Composable
internal fun CrownMap(state: GameState, selectedId: String?, onSelect: (String) -> Unit) {
    val measurer = rememberTextMeasurer()
    val provinces = state.crown.provinces
    val reachable = remember(state.crown.provinceChanges) { CrownLand.entries.associateWith { CrownEngine.reachable(state, it) } }
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio(MAP_W / MAP_H)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2A30), Color(0xFF0E171C))))
            .semantics { contentDescription = "Karte der Kronlande mit ${provinces.size} Provinzen" },
    ) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(provinces) {
                detectTapGestures { tap ->
                    val s = size.width / MAP_W
                    val hit = provinceUnits.minByOrNull { (_, u) -> (u * s - tap).getDistanceSquared() }
                    if (hit != null && (hit.value * s - tap).getDistance() < s * 1.1f) onSelect(hit.key)
                }
            }
        ) {
            val s = size.width / MAP_W
            // Roads between neighboring lands.
            CrownCatalog.neighbors.forEach { (land, others) ->
                others.filter { it.ordinal > land.ordinal }.forEach { other ->
                    val a = landCenters[land] ?: return@forEach
                    val b = landCenters[other] ?: return@forEach
                    drawLine(Color(0x55C9B37A), a * s, b * s, strokeWidth = s * 0.18f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.4f, s * 0.3f)))
                }
            }
            provinces.forEach { p ->
                val center = (provinceUnits[p.id] ?: return@forEach) * s
                val open = reachable[p.land] == true || p.owner == ProvinceOwner.PLAYER
                val fill = if (open) ownerFill(p.owner) else Steel
                val hex = hexPath(center, s * 0.94f)
                drawPath(hex, fill.copy(alpha = if (open) 0.82f else 0.55f))
                drawPath(hex, Color.Black.copy(alpha = .35f), style = Stroke(width = s * 0.06f))
                if (p.owner == ProvinceOwner.PLAYER && p.fortification > 0) drawCircle(Ivory, s * (0.12f + p.fortification * 0.05f), center, style = Stroke(s * 0.07f))
                if (p.owner != ProvinceOwner.PLAYER && p.garrison > 0 && open) {
                    val r = (s * 0.12f + s * 0.28f * (p.garrison / 40000f).coerceIn(0f, 1f))
                    drawCircle(Color.Black.copy(alpha = .45f), r, center)
                }
                if (p.id == selectedId) drawPath(hexPath(center, s * 1.02f), Color.White, style = Stroke(width = s * 0.16f))
            }
            // Marching enemies.
            state.crown.threats.forEach { t ->
                val from = provinceUnits[t.sourceId] ?: return@forEach
                val to = provinceUnits[t.targetId] ?: return@forEach
                drawArrow(from * s, to * s, Crimson, s)
            }
            // Land names.
            landCenters.forEach { (land, c) ->
                val style = TextStyle(color = Ivory, fontSize = 11.sp, fontWeight = FontWeight.Black)
                val layout = measurer.measure(land.label.uppercase(), style)
                val pos = Offset(c.x * s - layout.size.width / 2f, (c.y + 2.35f) * s)
                drawText(layout, topLeft = pos)
            }
        }
    }
}

private fun hexPath(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
        val point = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, s: Float) {
    val dir = (to - from)
    val length = dir.getDistance().coerceAtLeast(1f)
    val unit = dir / length
    val end = to - unit * (s * 0.6f)
    drawLine(color, from, end, strokeWidth = s * 0.16f)
    val normal = Offset(-unit.y, unit.x)
    val head = Path().apply {
        moveTo(end.x + unit.x * s * 0.35f, end.y + unit.y * s * 0.35f)
        lineTo(end.x + normal.x * s * 0.3f, end.y + normal.y * s * 0.3f)
        lineTo(end.x - normal.x * s * 0.3f, end.y - normal.y * s * 0.3f)
        close()
    }
    drawPath(head, color)
    drawCircle(color, s * 0.55f, to, style = Stroke(width = s * 0.1f))
}
