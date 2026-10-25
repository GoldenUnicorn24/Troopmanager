package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.HeroineSagaEngine
import com.goldenunicorn.troopmanager.model.GameState
import com.goldenunicorn.troopmanager.model.SagaScene
import kotlin.math.sin

/** Story scenes as full-screen visual-novel moments with a painted backdrop, portrait and choices. */
@Composable
internal fun SagaStageDialog(state: GameState, scene: SagaScene, apply: (GameEngine.ActionResult) -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            SceneBackdrop(scene.location + " " + scene.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x22000000), Color(0xEE05080B), Color(0xFF05080B)))))
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(scene.location.uppercase(), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onLater) { Text("Später", color = Mist) }
                }
                Spacer(Modifier.weight(0.15f))
                val def = HeroineSagaEngine.def(state, scene.heroineId)
                val heroine = def != null
                val uri = if (heroine) partnerPortrait(state, scene.heroineId) ?: if (state.crown.saga.companionId == scene.heroineId) state.companion.portraitUri else null else null
                val look = scene.look ?: if (heroine) HeroineSagaEngine.look(state, scene.heroineId) else null
                Box(Modifier.align(Alignment.CenterHorizontally).border(3.dp, Gold.copy(alpha = .7f), CircleShape)) {
                    Portrait(scene.portraitKey ?: def?.portraitKey ?: "companion", uri, 168, look)
                }
                Text(
                    scene.speaker ?: def?.name ?: state.companion.name,
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = Cinzel,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(scene.title, color = PaleGold, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xE60C1218))
                        .border(1.dp, Gold.copy(alpha = .25f), RoundedCornerShape(22.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(scene.text, color = Ivory, fontSize = 19.sp, lineHeight = 26.sp, fontFamily = Cormorant)
                    scene.options.forEach { option ->
                        OutlinedButton(
                            onClick = { apply(HeroineSagaEngine.choose(state, option.id)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Gold.copy(alpha = .45f)),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(option.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                if (option.hint.isNotBlank()) Text(option.hint, color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Backdrop { FORD, TOURNAMENT, WALL, ELF_COURT, CAMP, HALL, DUSK, HORDE }

private fun backdropFor(text: String): Backdrop {
    val t = text.lowercase()
    return when {
        "ork" in t || "uruk" in t || "tao-tei" in t || "hordenland" in t || "kriegshäuptling" in t -> Backdrop.HORDE
        "marsch nach" in t -> Backdrop.CAMP
        "furt" in t || "nebel" in t -> Backdrop.FORD
        "turnier" in t || "aldenburg" in t || "markt" in t -> Backdrop.TOURNAMENT
        "kranich" in t || "wall" in t || "kriegsrat" in t || "rat am" in t -> Backdrop.WALL
        "sonnenhain" in t || "elben" in t || "klingen" in t -> Backdrop.ELF_COURT
        "lager" in t || "feuer" in t || "schlacht" in t || "banner" in t -> Backdrop.CAMP
        "saal" in t || "thron" in t || "gemächer" in t || "krönung" in t || "kartenraum" in t || "hafen" in t -> Backdrop.HALL
        else -> Backdrop.DUSK
    }
}

/** Procedural, resolution-independent backdrops so every location has its own mood. */
@Composable
private fun SceneBackdrop(key: String, modifier: Modifier) {
    val kind = backdropFor(key)
    Canvas(modifier) {
        when (kind) {
            Backdrop.FORD -> ford()
            Backdrop.TOURNAMENT -> tournament()
            Backdrop.WALL -> greatWall()
            Backdrop.ELF_COURT -> elfCourt()
            Backdrop.CAMP -> camp()
            Backdrop.HALL -> hall()
            Backdrop.DUSK -> dusk()
            Backdrop.HORDE -> horde()
        }
    }
}

private fun DrawScope.sky(top: Color, bottom: Color) =
    drawRect(Brush.verticalGradient(listOf(top, bottom)), size = size)

private fun DrawScope.hills(color: Color, base: Float, amplitude: Float, waves: Float) {
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, size.height * base)
        var x = 0f
        while (x <= size.width) {
            lineTo(x, size.height * base - amplitude * size.height * (0.5f + 0.5f * sin(x / size.width * waves * 6.28f)))
            x += size.width / 40f
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.ford() {
    sky(Color(0xFF4F6670), Color(0xFF1E2B31))
    hills(Color(0xFF223238), 0.42f, 0.06f, 1.3f)
    drawRect(Color(0xFF2E4A55), Offset(0f, size.height * 0.48f), Size(size.width, size.height * 0.12f))
    for (i in 0 until 26) {
        val x = size.width * (i / 26f) + (i % 3) * 6f
        drawLine(Color(0xFF3D5A3A), Offset(x, size.height * 0.62f), Offset(x + 6f, size.height * (0.50f + (i % 4) * 0.015f)), strokeWidth = 4f)
    }
    drawRect(Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x55D8E4E8), Color(0x00FFFFFF))), Offset(0f, size.height * 0.34f), Size(size.width, size.height * 0.2f))
}

private fun DrawScope.tournament() {
    sky(Color(0xFF6E8FB0), Color(0xFFD9C38E))
    hills(Color(0xFF55703F), 0.55f, 0.03f, 0.8f)
    val tribune = size.height * 0.40f
    drawRect(Color(0xFF5A3B2A), Offset(size.width * 0.05f, tribune), Size(size.width * 0.9f, size.height * 0.12f))
    val colors = listOf(Color(0xFFB8322E), Color(0xFFE0BA63), Color(0xFF2F5C8F))
    for (i in 0 until 9) {
        val x = size.width * (0.08f + i * 0.105f)
        drawLine(Color(0xFF3A2A20), Offset(x, tribune), Offset(x, tribune - size.height * 0.12f), strokeWidth = 4f)
        val flag = Path().apply {
            moveTo(x, tribune - size.height * 0.12f)
            lineTo(x + size.width * 0.06f, tribune - size.height * 0.10f)
            lineTo(x, tribune - size.height * 0.075f)
            close()
        }
        drawPath(flag, colors[i % colors.size])
    }
}

private fun DrawScope.greatWall() {
    sky(Color(0xFF2B3B55), Color(0xFFB57E52))
    hills(Color(0xFF2A3341), 0.38f, 0.12f, 1.6f)
    val top = size.height * 0.46f
    drawRect(Color(0xFF6B6259), Offset(0f, top), Size(size.width, size.height * 0.18f))
    val merlon = size.width / 18f
    for (i in 0 until 18 step 2) drawRect(Color(0xFF6B6259), Offset(i * merlon, top - merlon * 0.6f), Size(merlon, merlon * 0.6f))
    drawRect(Color(0xFF4A433D), Offset(size.width * 0.44f, top - size.height * 0.16f), Size(size.width * 0.12f, size.height * 0.16f))
}

private fun DrawScope.elfCourt() {
    sky(Color(0xFF1C1A3A), Color(0xFF5B4B85))
    for (i in 0 until 7) {
        val x = size.width * (0.06f + i * 0.15f)
        val h = size.height * (0.35f + (i % 3) * 0.06f)
        drawRect(Brush.verticalGradient(listOf(Color(0xCCF5D98B), Color(0x33F5D98B))), Offset(x, size.height * 0.62f - h), Size(size.width * 0.035f, h))
    }
    for (i in 0 until 40) {
        val x = (i * 97 % 100) / 100f * size.width
        val y = (i * 53 % 60) / 100f * size.height
        drawCircle(Color(0x99FFF3C4), 2f + (i % 3), Offset(x, y))
    }
}

private fun DrawScope.camp() {
    sky(Color(0xFF0B1220), Color(0xFF2A1F2A))
    hills(Color(0xFF111A22), 0.55f, 0.05f, 1.1f)
    for (i in 0 until 50) drawCircle(Color(0x88FFFFFF), 1.5f, Offset((i * 37 % 100) / 100f * size.width, (i * 61 % 45) / 100f * size.height))
    val fire = Offset(size.width * 0.5f, size.height * 0.58f)
    drawCircle(Brush.radialGradient(listOf(Color(0xCCFFB347), Color(0x00FF6A00)), center = fire, radius = size.width * 0.35f), size.width * 0.35f, fire)
    for (i in 0 until 3) {
        val tent = size.width * (0.15f + i * 0.33f)
        val path = Path().apply {
            moveTo(tent - size.width * 0.08f, size.height * 0.56f)
            lineTo(tent, size.height * 0.46f)
            lineTo(tent + size.width * 0.08f, size.height * 0.56f)
            close()
        }
        drawPath(path, Color(0xFF3A2E26))
    }
}

private fun DrawScope.hall() {
    sky(Color(0xFF231915), Color(0xFF4A2F1E))
    for (i in 0 until 5) {
        val x = size.width * (0.08f + i * 0.21f)
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF3B2A20), Color(0xFF6A4E36), Color(0xFF3B2A20)), startX = x, endX = x + size.width * 0.07f),
            Offset(x, size.height * 0.08f), Size(size.width * 0.07f, size.height * 0.6f))
    }
    drawRect(Color(0xFF7A1F1F), Offset(size.width * 0.42f, size.height * 0.1f), Size(size.width * 0.16f, size.height * 0.5f))
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFD27F), Color(0x00FFD27F))), size.width * 0.4f, Offset(size.width * 0.5f, size.height * 0.2f))
}

private fun DrawScope.dusk() {
    sky(Color(0xFF2C3E58), Color(0xFFC77C4E))
    hills(Color(0xFF2A2F3A), 0.5f, 0.08f, 1.2f)
    hills(Color(0xFF1A1E26), 0.6f, 0.05f, 2.1f)
}

private fun DrawScope.horde() {
    sky(Color(0xFF2A0E0C), Color(0xFF7A2A14))
    hills(Color(0xFF1A0B0A), 0.5f, 0.10f, 1.4f)
    for (i in 0 until 9) {
        val x = size.width * (0.05f + i * 0.11f)
        val base = size.height * (0.52f + (i % 3) * 0.02f)
        drawLine(Color(0xFF120706), Offset(x, base), Offset(x, base - size.height * 0.10f), strokeWidth = 5f)
        drawPath(Path().apply {
            moveTo(x, base - size.height * 0.10f); lineTo(x + size.width * 0.05f, base - size.height * 0.085f); lineTo(x, base - size.height * 0.07f); close()
        }, Color(0xFF8A1F1F))
    }
    for (i in 0 until 4) {
        val c = Offset(size.width * (0.15f + i * 0.25f), size.height * 0.46f)
        drawCircle(Brush.radialGradient(listOf(Color(0x99FF6A00), Color(0x00FF6A00)), center = c, radius = size.width * 0.18f), size.width * 0.18f, c)
    }
}
