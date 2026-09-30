package com.goldenunicorn.troopmanager.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.*

internal enum class CityDistrict(val label: String) {
    HOUSING("Wohnviertel"),
    MARKET("Marktviertel"),
    MILITARY("Militärviertel"),
    PRODUCTION("Produktionsviertel"),
    PALACE("Palastbezirk"),
    OUTSKIRTS("Außenland"),
}

internal data class CitySite(
    val type: BuildingType,
    val x: Float,
    val y: Float,
    val district: CityDistrict,
)

internal val citySites =
    listOf(
        CitySite(BuildingType.RESIDENTIAL, 490f, 560f, CityDistrict.HOUSING),
        CitySite(BuildingType.MARKET, 725f, 640f, CityDistrict.MARKET),
        CitySite(BuildingType.WAREHOUSE, 620f, 715f, CityDistrict.MARKET),
        CitySite(BuildingType.BARRACKS, 1010f, 580f, CityDistrict.MILITARY),
        CitySite(BuildingType.STABLES, 1050f, 705f, CityDistrict.MILITARY),
        CitySite(BuildingType.ARSENAL, 1120f, 495f, CityDistrict.MILITARY),
        CitySite(BuildingType.HOSPITAL, 850f, 510f, CityDistrict.MILITARY),
        CitySite(BuildingType.SAWMILL, 1230f, 625f, CityDistrict.PRODUCTION),
        CitySite(BuildingType.IRONWORKS, 1150f, 350f, CityDistrict.PRODUCTION),
        CitySite(BuildingType.QUARRY, 1265f, 270f, CityDistrict.OUTSKIRTS),
        CitySite(BuildingType.FARM, 270f, 705f, CityDistrict.OUTSKIRTS),
        CitySite(BuildingType.PALACE, 735f, 325f, CityDistrict.PALACE),
        CitySite(BuildingType.ACADEMY, 590f, 385f, CityDistrict.PALACE),
        CitySite(BuildingType.EMBASSY, 900f, 360f, CityDistrict.PALACE),
        CitySite(BuildingType.TOWER, 1110f, 780f, CityDistrict.MILITARY),
        CitySite(BuildingType.WALL, 780f, 820f, CityDistrict.MILITARY),
    )

/**
 * Both pointer inversion and drawing use this exact transform; empty ground never picks a building.
 */
internal data class CityProjection(
    val width: Float,
    val height: Float,
    val zoom: Float,
    val panX: Float,
    val panY: Float,
) {
    val scale: Float
        get() = min(width / 1600f, height / 1000f).coerceAtLeast(.001f) * zoom

    val originX: Float
        get() = (width - 1600f * scale) / 2f + panX

    val originY: Float
        get() = (height - 1000f * scale) / 2f + panY

    fun project(x: Float, y: Float): Pair<Float, Float> =
        (originX + x * scale) to (originY + y * scale)

    fun unproject(x: Float, y: Float): Pair<Float, Float> =
        ((x - originX) / scale) to ((y - originY) / scale)

    fun bounded(): CityProjection {
        val boundedZoom = zoom.coerceIn(.85f, 3.5f)
        val actualScale = min(width / 1600f, height / 1000f).coerceAtLeast(.001f) * boundedZoom
        val maxX = max(0f, (1600f * actualScale - width) / 2f)
        val maxY = max(0f, (1000f * actualScale - height) / 2f)
        return copy(
            zoom = boundedZoom,
            panX = panX.coerceIn(-maxX, maxX),
            panY = panY.coerceIn(-maxY, maxY),
        )
    }

    fun hit(x: Float, y: Float, minimumTouchRadius: Float): BuildingType? {
        val world = unproject(x, y)
        // Geometric ellipse plus a minimum accessible screen target; no nearest-on-empty-ground
        // fallback.
        val rx = max(53f, minimumTouchRadius / scale)
        val ry = max(40f, minimumTouchRadius / scale)
        fun distance(site: CitySite): Float {
            val dx = (world.first - site.x) / rx
            val dy = (world.second - (site.y - 20f)) / ry
            return dx * dx + dy * dy
        }
        // Accessible targets may overlap at overview scale. Resolve only genuine containing
        // targets by distance, so a tap on the palace does not accidentally open its neighbour.
        return citySites.filter { distance(it) <= 1f }.minByOrNull { distance(it) }?.type
    }
}

/** The same scene is reused by the city, the old map seam and fortified live battles. */
internal fun cityInDefense(state: GameState): Boolean = state.invasion != null ||
    state.battleSession?.let { it.isActive && it.tactic == Tactic.FORTIFY } == true

internal fun cityWallIntegrity(state: GameState): Int =
    state.battleSession?.takeIf { it.isActive && it.tactic == Tactic.FORTIFY }?.wallIntegrity ?: state.realm.wallIntegrity

@Composable
internal fun CityScene(
    state: GameState,
    modifier: Modifier = Modifier,
    night: Boolean = false,
    season: Season = Season.SUMMER,
    defenseMode: Boolean = cityInDefense(state),
    wallIntegrity: Int = cityWallIntegrity(state),
    onBuilding: (BuildingType) -> Unit = {},
) {
    val context = LocalContext.current
    val landscape =
        remember(context) {
            runCatching {
                    context.assets.open("city_landscape.webp").use { BitmapFactory.decodeStream(it) }
                }
                .getOrNull()
                ?.asImageBitmap()
        }
    val minimumTouchRadius = with(LocalDensity.current) { 24.dp.toPx() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val currentBuildingAction by rememberUpdatedState(onBuilding)
    val clock = rememberInfiniteTransition(label = "Stadtatmosphäre")
    val phase by
        clock.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
            label = "Bewohner und Fahnen",
        )
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }
    Box(modifier.background(Ink)) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription =
                        "${state.realm.settlementName}, isometrische Stadt. Zwei Finger zum Zoomen und Verschieben. Gebäude auch über die Bezirksliste erreichbar."
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, shift, zoomChange, _ ->
                        val previous =
                            CityProjection(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                zoom,
                                pan.x,
                                pan.y,
                            )
                        val world = previous.unproject(centroid.x, centroid.y)
                        val nextZoom = (zoom * zoomChange).coerceIn(.85f, 3.5f)
                        val next = previous.copy(zoom = nextZoom, panX = 0f, panY = 0f)
                        val target = next.project(world.first, world.second)
                        val bounded =
                            next
                                .copy(
                                    panX = centroid.x + shift.x - target.first,
                                    panY = centroid.y + shift.y - target.second,
                                )
                                .bounded()
                        zoom = bounded.zoom
                        pan = Offset(bounded.panX, bounded.panY)
                    }
                }
                .pointerInput(minimumTouchRadius) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = 1f
                            pan = Offset.Zero
                        }
                    ) { position ->
                        CityProjection(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                zoom,
                                pan.x,
                                pan.y,
                            )
                            .bounded()
                            .hit(position.x, position.y, minimumTouchRadius)
                            ?.let(currentBuildingAction)
                    }
                }
        ) {
            val p = CityProjection(size.width, size.height, zoom, pan.x, pan.y).bounded()
            // Full-screen atmospheric backdrop remains behind the zoomable, architecturally layered
            // city.
            landscape?.let {
                drawImage(
                    it,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = .48f,
                )
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0x99111923), Color(0x00233930), Color(0xCC0A0E12))
                )
            )
            withTransform({
                translate(p.originX, p.originY)
                scale(p.scale, p.scale, Offset.Zero)
            }) {
                landscape?.let {
                    drawImage(it, dstSize = IntSize(1600, 1000), alpha = if (night) .62f else .95f)
                }
                if (landscape == null)
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFF667260), Color(0xFF243D36)),
                            center = Offset(800f, 510f),
                            radius = 960f,
                        ),
                        size = Size(1600f, 1000f),
                    )
                if (season != Season.SUMMER)
                    drawRect(
                        if (season == Season.WINTER) Color(0x447C96A7) else Color(0x33765522),
                        size = Size(1600f, 1000f),
                    )
                val growth =
                    (state.realm.settlementTier.ordinal * .055f +
                            state.population.total.coerceAtMost(18000) / 180000f)
                        .coerceAtMost(.32f)
                val ring = 1f + growth
                drawDistricts(state, ring)
                drawFortifications(state, ring, wallIntegrity, night, phase)
                val houses =
                    (10 + state.population.total / 130 + state.realm.settlementTier.ordinal * 5)
                        .coerceIn(10, 58)
                repeat(houses) { i ->
                    val row = i / 8
                    val col = i % 8
                    val x = 360f + col * 42f + row * 21f
                    val y = 435f + row * 44f + (col % 2) * 12f
                    drawArchitecture(
                        x,
                        y,
                        22f,
                        14f,
                        if (state.population.total > 4500) 25f else 18f,
                        Color(0xFF9A8E70),
                        Color(0xFF5E473B),
                        night,
                        i % 3,
                    )
                }
                citySites
                    .sortedBy { it.y }
                    .forEach { site ->
                        val level = state.realm.level(site.type)
                        val tier =
                            when {
                                level >= 6 -> 3
                                level >= 3 -> 2
                                else -> 1
                            }
                        val built = level > 0
                        if (!built) {
                            drawOval(
                                Color(0xAA0E171A),
                                Offset(site.x - 43f, site.y - 24f),
                                Size(86f, 44f),
                            )
                            drawOval(
                                Gold.copy(alpha = .5f),
                                Offset(site.x - 43f, site.y - 24f),
                                Size(86f, 44f),
                                style = Stroke(2f),
                            )
                            drawLine(
                                Gold.copy(alpha = .6f),
                                Offset(site.x - 8f, site.y),
                                Offset(site.x + 8f, site.y),
                                2f,
                            )
                            drawLine(
                                Gold.copy(alpha = .6f),
                                Offset(site.x, site.y - 8f),
                                Offset(site.x, site.y + 8f),
                                2f,
                            )
                        } else drawSite(site, tier, night, phase, wallIntegrity)
                        if (
                            zoom >= 1.65f ||
                                site.type in
                                    listOf(
                                        BuildingType.PALACE,
                                        BuildingType.MARKET,
                                        BuildingType.BARRACKS,
                                    )
                        ) {
                            paint.color =
                                if (built) android.graphics.Color.rgb(247, 230, 185)
                                else android.graphics.Color.LTGRAY
                            paint.textSize = 11f / p.scale.coerceAtLeast(.2f)
                            drawContext.canvas.nativeCanvas.drawText(
                                "${site.type.label} · $level",
                                site.x,
                                site.y + 34f,
                                paint,
                            )
                        }
                    }
                // A fixed cap of 20 decorative groups: no persistent individual simulation.
                repeat(20) { i ->
                    val travel = (phase + i * .173f) % 1f
                    val route = i % 4
                    val x =
                        when (route) {
                            0 -> 600f + travel * 400f
                            1 -> 750f
                            2 -> 950f - travel * 250f
                            else -> 410f + travel * 360f
                        }
                    val y =
                        when (route) {
                            0 -> 670f
                            1 -> 410f + travel * 410f
                            2 -> 480f
                            else -> 745f
                        }
                    drawCitizen(x, y, i, night, phase)
                }
                if (
                    state.companion.met &&
                        (state.companion.trust >= 45 ||
                            state.companion.role.contains("regent", true))
                ) {
                    val palace = citySites.first { it.type == BuildingType.PALACE }
                    drawCitizen(palace.x + 63f, palace.y + 10f, 23, night, phase)
                    drawCircle(
                        PaleGold,
                        15f,
                        Offset(palace.x + 63f, palace.y - 7f),
                        style = Stroke(2f),
                    )
                    paint.color = android.graphics.Color.rgb(255, 226, 161)
                    paint.textSize = 10f / p.scale.coerceAtLeast(.2f)
                    drawContext.canvas.nativeCanvas.drawText(
                        state.companion.name,
                        palace.x + 63f,
                        palace.y + 38f,
                        paint,
                    )
                }
                if (defenseMode) drawSiege(state, ring, phase, wallIntegrity, night)
                if (night) {
                    drawRect(Color(0x55121C3C), size = Size(1600f, 1000f))
                    repeat(9) { i ->
                        val x = 415f + i * 84f
                        val y = if (i % 2 == 0) 650f else 490f
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0x77FFB34D), Color.Transparent),
                                center = Offset(x, y),
                                radius = 29f,
                            ),
                            29f,
                            Offset(x, y),
                        )
                        drawFire(x, y, phase + i)
                    }
                }
                // District labels are geographically attached and remain readable at overview
                // scale.
                val districtLabels =
                    listOf(
                        Triple("WOHNVIERTEL", 460f, 380f),
                        Triple("MARKTVIERTEL", 650f, 795f),
                        Triple("MILITÄRVIERTEL", 1020f, 835f),
                        Triple("PRODUKTION", 1285f, 485f),
                        Triple("PALASTBEZIRK", 745f, 195f),
                        Triple("AUSSENLAND", 265f, 835f),
                    )
                paint.color = android.graphics.Color.rgb(235, 212, 168)
                paint.textSize = 10f / p.scale.coerceAtLeast(.2f)
                districtLabels.forEach { (title, x, y) ->
                    drawContext.canvas.nativeCanvas.drawText(title, x, y, paint)
                }
            }
        }
    }
}

private fun DrawScope.polygon(points: List<Offset>, color: Color, outline: Color? = null) {
    val path =
        Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    drawPath(path, color)
    outline?.let { drawPath(path, it, style = Stroke(1.2f)) }
}

private fun DrawScope.drawDistricts(state: GameState, ring: Float) {
    // Paved islands and roads identify functional districts instead of one uniform rectangle.
    val paving = Color(0x88575C53)
    listOf(
            Triple(500f, 525f, 170f),
            Triple(735f, 675f, 135f),
            Triple(1010f, 625f, 135f),
            Triple(1155f, 415f, 110f),
            Triple(750f, 350f, 170f),
        )
        .forEach { (x, y, half) ->
            polygon(
                listOf(
                    Offset(x, y - half * .45f),
                    Offset(x + half, y),
                    Offset(x, y + half * .6f),
                    Offset(x - half, y),
                ),
                paving,
                Color(0x886D6D5A),
            )
        }
    val roadWidth =
        14f + state.realm.settlementTier.ordinal * 3f + min(12f, state.population.total / 1000f)
    val roads =
        listOf(
            Offset(780f, 860f) to Offset(745f, 310f),
            Offset(430f, 575f) to Offset(1190f, 610f),
            Offset(545f, 385f) to Offset(1110f, 440f),
            Offset(680f, 710f) to Offset(360f, 805f),
            Offset(785f, 825f) to Offset(1310f, 980f),
        )
    roads.forEach { (a, b) ->
        drawLine(Color(0x88302B25), a + Offset(3f, 5f), b + Offset(3f, 5f), roadWidth + 8f)
        drawLine(Color(0xCCA39472), a, b, roadWidth)
        val count = 22
        repeat(count) { i ->
            val pt = a + (b - a) * (i / count.toFloat())
            drawLine(Color(0x55534E42), pt + Offset(-4f, -3f), pt + Offset(4f, 3f), 1f)
        }
    }
    // Ploughed outer fields, terraces and waterways enrich the surrounding terrain.
    repeat(9) { i ->
        drawLine(
            Color(0x996F7742),
            Offset(160f + i * 12f, 640f + i * 3f),
            Offset(320f + i * 12f, 715f + i * 3f),
            7f,
        )
    }
    repeat(6) { i ->
        drawLine(Color(0xAA64795B), Offset(185f + i * 16f, 785f), Offset(330f + i * 16f, 837f), 10f)
    }
    val river =
        Path().apply {
            moveTo(1430f, 70f)
            cubicTo(1390f, 370f, 1490f, 530f, 1390f, 760f)
            cubicTo(1370f, 860f, 1400f, 970f, 1490f, 1050f)
        }
    drawPath(river, Color(0xCC38545A), style = Stroke(43f))
    drawPath(river, Color(0x885D8188), style = Stroke(15f))
}

private fun DrawScope.drawArchitecture(
    x: Float,
    y: Float,
    halfWidth: Float,
    depth: Float,
    height: Float,
    stone: Color,
    roof: Color,
    night: Boolean,
    tier: Int,
) {
    val outline = Color(0xAA272522)
    // Four faces, pitched roof, hand-laid masonry, floors, timber and lit recessed windows.
    polygon(
        listOf(
            Offset(x, y + depth),
            Offset(x - halfWidth, y),
            Offset(x - halfWidth, y - height),
            Offset(x, y - height + depth),
        ),
        stone,
        outline,
    )
    polygon(
        listOf(
            Offset(x, y + depth),
            Offset(x + halfWidth, y),
            Offset(x + halfWidth, y - height),
            Offset(x, y - height + depth),
        ),
        stone.copy(red = stone.red * .7f, green = stone.green * .7f, blue = stone.blue * .72f),
        outline,
    )
    val ridge = Offset(x, y - height - depth)
    polygon(
        listOf(Offset(x - halfWidth - 5f, y - height), ridge, Offset(x, y - height + depth + 3f)),
        roof,
        outline,
    )
    polygon(
        listOf(ridge, Offset(x + halfWidth + 5f, y - height), Offset(x, y - height + depth + 3f)),
        Color(roof.red * .65f, roof.green * .65f, roof.blue * .7f),
        outline,
    )
    repeat(4) { row ->
        val lineY = y - height + row * height / 4f
        drawLine(
            stone.copy(alpha = .3f),
            Offset(x - halfWidth, lineY),
            Offset(x, lineY + depth),
            1f,
        )
        drawLine(Color(0x44201D18), Offset(x, lineY + depth), Offset(x + halfWidth, lineY), 1f)
        repeat(3) { col ->
            val bx = x - halfWidth + col * halfWidth / 3f + (if (row % 2 == 0) 5f else 0f)
            drawLine(
                Color(0x44201D18),
                Offset(bx, lineY + (bx - x + halfWidth) / halfWidth * depth),
                Offset(bx, lineY + height / 4f + (bx - x + halfWidth) / halfWidth * depth),
                1f,
            )
        }
    }
    repeat((tier + 1).coerceAtMost(4)) { floor ->
        val windowY = y - 8f - floor * 10f
        val light = if (night) Color(0xFFFFCA73) else Color(0xFF302D28)
        drawLine(
            light,
            Offset(x - halfWidth * .58f, windowY - 5f),
            Offset(x - halfWidth * .58f, windowY),
            4f,
        )
        drawLine(
            light,
            Offset(x + halfWidth * .48f, windowY - 7f),
            Offset(x + halfWidth * .48f, windowY - 2f),
            4f,
        )
    }
    val door =
        Path().apply {
            moveTo(x - 4f, y + depth)
            lineTo(x - 4f, y + depth - 11f)
            quadraticBezierTo(x, y + depth - 16f, x + 4f, y + depth - 11f)
            lineTo(x + 4f, y + depth)
            close()
        }
    drawPath(door, Color(0xFF292A26))
    repeat(4) { i ->
        val t = (i + 1) / 5f
        drawLine(
            Color(0x335C4C40),
            Offset(x - halfWidth * t, y - height - depth * (1f - t)),
            Offset(x + halfWidth * (1f - t), y - height + depth * t),
            1.5f,
        )
    }
}

private fun DrawScope.drawSite(
    site: CitySite,
    tier: Int,
    night: Boolean,
    phase: Float,
    wallIntegrity: Int,
) {
    val x = site.x
    val y = site.y
    val stone =
        when (site.district) {
            CityDistrict.PALACE -> Color(0xFFB8A991)
            CityDistrict.MILITARY -> Color(0xFF87918C)
            else -> Color(0xFF9F977F)
        }
    val roof =
        when (site.district) {
            CityDistrict.PALACE -> Color(0xFF3A5B68)
            CityDistrict.MILITARY -> Color(0xFF38484D)
            CityDistrict.PRODUCTION -> Color(0xFF66503F)
            else -> Color(0xFF774A3F)
        }
    drawOval(Color(0x66050808), Offset(x - 57f, y - 15f), Size(130f, 50f))
    when (site.type) {
        BuildingType.WALL -> {
            drawArchitecture(x - 31f, y, 20f, 17f, 35f + tier * 15f, stone, roof, night, tier)
            drawArchitecture(x + 31f, y, 20f, 17f, 35f + tier * 15f, stone, roof, night, tier)
            polygon(
                listOf(
                    Offset(x - 13f, y + 10f),
                    Offset(x - 13f, y - 28f),
                    Offset(x + 13f, y - 28f),
                    Offset(x + 13f, y + 10f),
                ),
                Color(0xFF202827),
                Color(0xFF736C52),
            )
            repeat(5) {
                drawLine(
                    Color(0xFF8E8061),
                    Offset(x - 10f + it * 5f, y - 24f),
                    Offset(x - 10f + it * 5f, y + 8f),
                    2f,
                )
            }
            if (wallIntegrity < 65)
                drawLine(Color(0xFF222824), Offset(x + 28f, y - 40f), Offset(x + 20f, y - 17f), 4f)
        }
        BuildingType.TOWER -> {
            drawArchitecture(x, y, 26f, 22f, 52f + tier * 22f, stone, roof, night, tier)
            drawFlag(x + 3f, y - 77f - tier * 22f, Color(0xFF406B89), phase)
        }
        BuildingType.PALACE -> {
            drawArchitecture(x, y, 50f + tier * 4f, 30f, 43f + tier * 12f, stone, roof, night, tier)
            drawArchitecture(x - 53f, y - 12f, 17f, 16f, 60f + tier * 17f, stone, roof, night, tier)
            drawArchitecture(x + 53f, y - 12f, 17f, 16f, 60f + tier * 17f, stone, roof, night, tier)
            if (tier >= 2)
                drawArchitecture(x, y - 45f, 29f, 22f, 60f + tier * 14f, stone, roof, night, tier)
            if (tier >= 3) {
                drawArchitecture(x - 85f, y + 3f, 23f, 19f, 54f, stone, roof, night, 2)
                drawArchitecture(x + 85f, y + 3f, 23f, 19f, 54f, stone, roof, night, 2)
            }
            drawFlag(x, y - 125f - tier * 9f, Gold, phase)
            drawLine(Color(0xFFB7AC8D), Offset(x - 30f, y + 23f), Offset(x + 30f, y + 23f), 7f)
            drawLine(Color(0xFF958D77), Offset(x - 38f, y + 30f), Offset(x + 38f, y + 30f), 7f)
        }
        BuildingType.MARKET -> {
            drawArchitecture(x, y - 16f, 36f, 24f, 28f + tier * 13f, stone, roof, night, tier)
            repeat(3 + tier) { i ->
                val sx = x - 72f + (i % 3) * 58f
                val sy = y + 15f + (i / 3) * 28f
                drawLine(Color(0xFF6D4F35), Offset(sx - 16f, sy), Offset(sx - 16f, sy - 20f), 3f)
                drawLine(Color(0xFF6D4F35), Offset(sx + 16f, sy), Offset(sx + 16f, sy - 20f), 3f)
                polygon(
                    listOf(
                        Offset(sx - 21f, sy - 20f),
                        Offset(sx, sy - 30f),
                        Offset(sx + 21f, sy - 20f),
                        Offset(sx, sy - 12f),
                    ),
                    if (i % 2 == 0) Color(0xFFAC8249) else Color(0xFF8D4F44),
                    Color(0xFF3D3529),
                )
                drawLine(Color(0xFFAF9763), Offset(sx - 18f, sy), Offset(sx + 18f, sy), 5f)
            }
        }
        BuildingType.FARM -> {
            drawArchitecture(
                x,
                y,
                29f + tier * 8f,
                20f,
                24f + tier * 6f,
                stone,
                Color(0xFF665238),
                night,
                tier,
            )
            repeat(tier) { i ->
                drawArchitecture(
                    x + 50f + i * 29f,
                    y - 10f,
                    12f,
                    11f,
                    35f,
                    Color(0xFF938975),
                    Color(0xFF554A3B),
                    night,
                    1,
                )
            }
            repeat(4) { i ->
                drawLine(
                    Color(0xFFB8A06A),
                    Offset(x - 65f + i * 9f, y + 12f),
                    Offset(x - 65f + i * 9f, y + 30f),
                    3f,
                )
            }
        }
        BuildingType.QUARRY -> {
            repeat(6 + tier * 3) { i ->
                val sx = x + (i % 4) * 19f - 35f
                val sy = y + (i / 4) * 14f
                polygon(
                    listOf(
                        Offset(sx, sy),
                        Offset(sx + 16f, sy + 3f),
                        Offset(sx + 11f, sy - 14f),
                        Offset(sx - 5f, sy - 10f),
                    ),
                    Color(0xFF92948A),
                    Color(0xFF565C54),
                )
            }
            drawArchitecture(
                x - 15f,
                y - 18f,
                20f + tier * 3f,
                14f,
                22f + tier * 9f,
                stone,
                roof,
                night,
                tier,
            )
            if (tier >= 2) {
                drawLine(Color(0xFF705E44), Offset(x + 38f, y), Offset(x + 34f, y - 70f), 5f)
                drawLine(Color(0xFF705E44), Offset(x + 34f, y - 70f), Offset(x - 15f, y - 45f), 6f)
                drawLine(
                    Color(0xFFB4AF96),
                    Offset(x - 15f, y - 45f),
                    Offset(x - 15f, y - 20f),
                    1.5f,
                )
            }
        }
        else -> {
            drawArchitecture(x, y, 36f + tier * 7f, 24f, 27f + tier * 13f, stone, roof, night, tier)
            if (tier >= 2) drawArchitecture(x - 48f, y + 5f, 19f, 17f, 30f, stone, roof, night, 1)
            if (tier >= 3) drawArchitecture(x + 51f, y - 5f, 20f, 17f, 46f, stone, roof, night, 2)
            if (
                site.type in
                    listOf(BuildingType.IRONWORKS, BuildingType.ARSENAL, BuildingType.SAWMILL)
            ) {
                val chimneyX = x + 22f
                drawLine(
                    Color(0xFF4F554D),
                    Offset(chimneyX, y - 48f),
                    Offset(chimneyX, y - 87f - tier * 6f),
                    12f,
                )
                repeat(4) { smoke ->
                    val drift = ((phase * 3f + smoke / 4f) % 1f)
                    drawCircle(
                        Color(0xFF8C9289).copy(alpha = (1f - drift) * .4f),
                        5f + drift * 14f,
                        Offset(chimneyX + drift * 23f, y - 85f - tier * 6f - drift * 60f),
                    )
                }
                drawFire(x + 32f, y - 6f, phase)
            }
            if (site.type == BuildingType.BARRACKS || site.type == BuildingType.ACADEMY)
                drawFlag(x + 15f, y - 65f - tier * 12f, Color(0xFF477696), phase)
            if (site.type == BuildingType.HOSPITAL) {
                drawLine(Color(0xFFE8DABB), Offset(x - 6f, y - 31f), Offset(x + 6f, y - 31f), 4f)
                drawLine(Color(0xFFE8DABB), Offset(x, y - 37f), Offset(x, y - 25f), 4f)
            }
            if (site.type == BuildingType.STABLES)
                repeat(tier + 1) { i -> drawCitizen(x - 40f + i * 27f, y + 22f, 2, night, phase) }
        }
    }
}

private fun DrawScope.drawFlag(x: Float, y: Float, color: Color, phase: Float) {
    drawLine(Color(0xFFD5C7A2), Offset(x, y + 32f), Offset(x, y - 10f), 2f)
    val wave = sin(phase * 2f * PI.toFloat()) * 5f
    polygon(
        listOf(
            Offset(x, y - 10f),
            Offset(x + 24f, y - 7f + wave),
            Offset(x + 21f, y + 9f + wave),
            Offset(x, y + 7f),
        ),
        color,
        Color(0x995A594A),
    )
    drawLine(color.copy(alpha = .5f), Offset(x + 4f, y - 5f), Offset(x + 18f, y - 2f + wave), 1f)
}

private fun DrawScope.drawFire(x: Float, y: Float, phase: Float) {
    val flicker = sin(phase * 37f) * 3f
    polygon(
        listOf(
            Offset(x - 4f, y + 2f),
            Offset(x - 2f, y - 7f),
            Offset(x + 1f, y - 15f - flicker),
            Offset(x + 6f, y - 2f),
            Offset(x + 3f, y + 3f),
        ),
        Color(0xFFE8993E),
    )
    polygon(
        listOf(Offset(x - 1f, y + 1f), Offset(x + 1f, y - 8f), Offset(x + 3f, y + 1f)),
        Color(0xFFFFDC8B),
    )
}

private fun DrawScope.drawCitizen(x: Float, y: Float, kind: Int, night: Boolean, phase: Float) {
    drawOval(Color(0x66000000), Offset(x - 6f, y - 2f), Size(15f, 5f))
    when (kind % 7) {
        0 -> {
            polygon(
                listOf(
                    Offset(x - 11f, y - 7f),
                    Offset(x + 8f, y - 4f),
                    Offset(x + 15f, y - 13f),
                    Offset(x - 4f, y - 16f),
                ),
                Color(0xFF9D855E),
                Color(0xFF504B3C),
            )
            drawCircle(Color(0xFF393F38), 4f, Offset(x - 7f, y))
            drawCircle(Color(0xFF393F38), 4f, Offset(x + 9f, y - 3f))
            drawLine(Color(0xFFC7B78D), Offset(x - 4f, y - 13f), Offset(x + 10f, y - 11f), 4f)
        }
        2 -> {
            drawLine(Color(0xFF6F6453), Offset(x - 7f, y - 8f), Offset(x + 6f, y - 10f), 7f)
            drawLine(Color(0xFF6F6453), Offset(x + 6f, y - 10f), Offset(x + 8f, y - 17f), 5f)
            drawLine(Color(0xFF4C463A), Offset(x - 5f, y - 7f), Offset(x - 6f, y), 2f)
            drawLine(Color(0xFF4C463A), Offset(x + 4f, y - 7f), Offset(x + 5f, y), 2f)
        }
        else -> {
            val guard = kind % 3 == 0
            polygon(
                listOf(
                    Offset(x - 4f, y - 1f),
                    Offset(x - 3f, y - 12f),
                    Offset(x + 2f, y - 14f),
                    Offset(x + 5f, y - 2f),
                ),
                if (guard) Color(0xFF456277) else Color(0xFF8D775A),
            )
            drawCircle(Color(0xFFC7AD87), 3f, Offset(x, y - 16f))
            drawLine(
                Color(0xFF2F342F),
                Offset(x - 2f, y - 2f),
                Offset(x - 3f + sin(phase * 24f), y + 3f),
                2f,
            )
            drawLine(Color(0xFF2F342F), Offset(x + 2f, y - 2f), Offset(x + 3f, y + 3f), 2f)
            if (guard) drawLine(Color(0xFFADAE97), Offset(x + 7f, y), Offset(x + 7f, y - 24f), 1.5f)
        }
    }
}

private fun DrawScope.drawFortifications(
    state: GameState,
    ring: Float,
    integrity: Int,
    night: Boolean,
    phase: Float,
) {
    val tier =
        when {
            state.realm.level(BuildingType.WALL) >= 6 -> 3
            state.realm.level(BuildingType.WALL) >= 3 -> 2
            else -> 1
        }
    val centre = Offset(800f, 550f)
    val corners =
        listOf(
                Offset(350f, 485f),
                Offset(545f, 270f),
                Offset(950f, 255f),
                Offset(1225f, 440f),
                Offset(1220f, 705f),
                Offset(960f, 840f),
                Offset(575f, 850f),
                Offset(345f, 690f),
            )
            .map { centre + (it - centre) * ring }
    val stone = if (integrity < 50) Color(0xFF766F5B) else Color(0xFF9A9C88)
    val height = 18f + tier * 8f
    corners.indices.forEach { index ->
        val a = corners[index]
        val b = corners[(index + 1) % corners.size]
        drawLine(Color(0x66000000), a + Offset(9f, 12f), b + Offset(9f, 12f), 20f)
        polygon(
            listOf(a, b, b - Offset(0f, height), a - Offset(0f, height)),
            stone,
            Color(0xFF535A50),
        )
        drawLine(Color(0xFFBEBCA2), a - Offset(0f, height), b - Offset(0f, height), 8f + tier * 2f)
        val sections = ((b - a).getDistance() / 22f).toInt().coerceAtLeast(1)
        repeat(sections) { segment ->
            val pt = a + (b - a) * (segment / sections.toFloat())
            drawLine(
                Color(0xFFBDBBA2),
                pt - Offset(0f, height - 2f),
                pt - Offset(0f, height + 8f),
                7f,
            )
            if (segment % 3 == 0)
                drawLine(Color(0x77545950), pt - Offset(0f, 3f), pt - Offset(0f, height - 5f), 1f)
        }
        if (integrity < 80 && index == 4) {
            val middle = (a + b) / 2f
            val crack =
                Path().apply {
                    moveTo(middle.x, middle.y - height - 3f)
                    lineTo(middle.x - 8f, middle.y - 13f)
                    lineTo(middle.x + 3f, middle.y - 8f)
                    lineTo(middle.x - 9f, middle.y + 3f)
                }
            drawPath(crack, Color(0xFF252E2A), style = Stroke(if (integrity < 35) 10f else 4f))
            repeat(4) {
                drawCircle(
                    Color(0xFF8A8975),
                    4f,
                    middle + Offset(it * 9f - 17f, 9f + (it % 2) * 7f),
                )
            }
        }
    }
    corners
        .filterIndexed { index, _ -> tier >= 2 || index % 2 == 0 }
        .forEachIndexed { i, corner ->
            drawArchitecture(
                corner.x,
                corner.y,
                15f + tier * 4f,
                16f,
                35f + tier * 17f,
                stone,
                Color(0xFF414F51),
                night,
                tier,
            )
            if (i % 2 == 0)
                drawFlag(corner.x, corner.y - 60f - tier * 10f, Color(0xFF446B87), phase)
        }
}

private fun DrawScope.drawSiege(
    state: GameState,
    ring: Float,
    phase: Float,
    integrity: Int,
    night: Boolean,
) {
    // Devices mirror the announced invasion rather than manufacturing mechanics or units.
    val active = state.invasion?.let { it.arrivalDay <= state.day } ?: (state.battleSession != null)
    val enemyColor = if (active) Color(0xFF934C3B) else Color(0xFF78634F)
    repeat(6) { group ->
        val x = 920f + group * 62f
        val y = 915f + (group % 2) * 24f
        repeat(4) { rank ->
            drawLine(enemyColor, Offset(x + rank * 8f, y - 5f), Offset(x + rank * 8f, y - 15f), 4f)
            drawCircle(Color(0xFFB29C77), 2f, Offset(x + rank * 8f, y - 17f))
            drawLine(
                Color(0xFF909382),
                Offset(x + rank * 8f + 3f, y - 2f),
                Offset(x + rank * 8f + 3f, y - 24f),
                1.5f,
            )
        }
        drawFlag(x, y - 33f, Color(0xFF73362F), phase + group)
    }
    val devices = state.invasion?.devices.orEmpty()
    devices.forEachIndexed { i, device ->
        val x = 570f + i * 110f
        val y = 945f + (i % 2) * 15f
        when (device) {
            SiegeDevice.TOWER -> {
                drawArchitecture(
                    x,
                    y,
                    24f,
                    20f,
                    82f,
                    Color(0xFF7D7158),
                    Color(0xFF483E32),
                    night,
                    3,
                )
                drawLine(Color(0xFFBAA078), Offset(x - 16f, y - 55f), Offset(x + 16f, y - 62f), 3f)
            }
            SiegeDevice.RAM -> {
                drawArchitecture(
                    x,
                    y,
                    32f,
                    16f,
                    18f,
                    Color(0xFF75674E),
                    Color(0xFF4F4333),
                    night,
                    1,
                )
                drawLine(Color(0xFFAD976D), Offset(x - 42f, y - 9f), Offset(x + 45f, y - 14f), 9f)
            }
            SiegeDevice.CATAPULT -> {
                drawLine(Color(0xFF957A50), Offset(x - 30f, y), Offset(x + 25f, y - 4f), 7f)
                drawLine(Color(0xFF957A50), Offset(x, y), Offset(x + 9f, y - 53f), 7f)
                drawLine(Color(0xFFB69A65), Offset(x - 20f, y - 38f), Offset(x + 39f, y - 75f), 6f)
                drawLine(Color(0xFF5D5745), Offset(x - 20f, y - 38f), Offset(x - 15f, y), 1f)
            }
            SiegeDevice.LADDERS,
            SiegeDevice.CLIMBERS -> {
                drawLine(Color(0xFFB49F72), Offset(x - 13f, y), Offset(x + 15f, y - 67f), 3f)
                drawLine(Color(0xFFB49F72), Offset(x + 1f, y), Offset(x + 29f, y - 67f), 3f)
                repeat(6) { step ->
                    drawLine(
                        Color(0xFFB49F72),
                        Offset(x - 9f + step * 4.5f, y - 9f - step * 10f),
                        Offset(x + 5f + step * 4.5f, y - 9f - step * 10f),
                        2f,
                    )
                }
            }
        }
        drawCircle(Color(0xFF35382F), 5f, Offset(x - 18f, y + 5f))
        drawCircle(Color(0xFF35382F), 5f, Offset(x + 21f, y + 2f))
    }
    if (active) {
        repeat(5) { i -> drawCitizen(520f + i * 132f, 827f, 3, night, phase) }
        if (integrity < 60) drawFire(1190f, 725f, phase)
        val volley = phase * 11f % 1f
        repeat(5) { i ->
            val start = Offset(975f + i * 12f, 845f)
            val end = Offset(1050f + i * 12f, 935f)
            val arrow =
                start + (end - start) * volley - Offset(0f, sin(volley * PI.toFloat()) * 75f)
            drawLine(Color(0xFFD1C6A4), arrow, arrow - Offset(5f, 8f), 1.5f)
        }
    }
}
