package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goldenunicorn.troopmanager.model.Celebration
import com.goldenunicorn.troopmanager.model.CelebrationKind
import kotlin.math.cos
import kotlin.math.sin

/** Full-screen moment for big achievements: animated emblem, light rays and falling sparks. */
@Composable
internal fun CelebrationOverlay(celebration: Celebration, remaining: Int, animations: Boolean, onContinue: () -> Unit) {
    val accent = when (celebration.kind) {
        CelebrationKind.BOSS, CelebrationKind.KING -> Crimson
        CelebrationKind.HEROINE, CelebrationKind.WEDDING -> Color(0xFFE58FB0)
        CelebrationKind.ACT, CelebrationKind.ACHIEVEMENT -> ModernBlue
        CelebrationKind.VICTORY -> Emerald
        else -> Gold
    }
    val eyebrow = when (celebration.kind) {
        CelebrationKind.TITLE -> "NEUER TITEL"
        CelebrationKind.MILESTONE -> "MEILENSTEIN"
        CelebrationKind.HEROINE -> "NEUE FELDHERRIN"
        CelebrationKind.ACT -> "NEUES KAPITEL"
        CelebrationKind.BOSS -> "ENDGEGNER BESIEGT"
        CelebrationKind.KING -> "EIN REICH FÄLLT"
        CelebrationKind.WEDDING -> "HOCHZEIT"
        CelebrationKind.CORONATION -> "KRÖNUNG"
        CelebrationKind.WONDER -> "GROSSBAU VOLLENDET"
        CelebrationKind.FEAST -> "KÖNIGSFEST"
        CelebrationKind.ACHIEVEMENT -> "ERFOLG"
        CelebrationKind.VICTORY -> "SIEG"
    }
    val entry = remember(celebration) { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(celebration) { if (animations) entry.animateTo(1f, spring(dampingRatio = .55f, stiffness = 180f)) }
    val infinite = rememberInfiniteTransition(label = "celebration")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rays")
    val fall by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "sparks")

    Dialog(onDismissRequest = onContinue, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0050709)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                rays(accent, if (animations) spin else 0f)
                sparks(accent, if (animations) fall else .3f)
            }
            Column(
                Modifier.fillMaxWidth().padding(28.dp).alpha(entry.value.coerceIn(0f, 1f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Canvas(Modifier.size(150.dp).scale(0.6f + 0.4f * entry.value)) { emblem(celebration.kind, accent) }
                Text(eyebrow, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(celebration.title, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(celebration.text, color = Mist, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                GoldButton(if (remaining > 1) "Weiter (${remaining - 1} mehr)" else "Weiter", onContinue, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun DrawScope.rays(color: Color, angle: Float) {
    val center = Offset(size.width / 2, size.height * 0.38f)
    rotate(angle, center) {
        for (i in 0 until 16) {
            val a = Math.toRadians(i * 22.5).toFloat()
            val b = Math.toRadians(i * 22.5 + 8).toFloat()
            val r = size.maxDimension
            drawPath(Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + r * cos(a), center.y + r * sin(a))
                lineTo(center.x + r * cos(b), center.y + r * sin(b))
                close()
            }, Brush.radialGradient(listOf(color.copy(alpha = .22f), Color.Transparent), center = center, radius = r * .6f))
        }
    }
}

private fun DrawScope.sparks(color: Color, t: Float) {
    for (i in 0 until 46) {
        val seed = i * 9973
        val x = ((seed % 1000) / 1000f) * size.width
        val speed = 0.6f + (seed % 7) / 10f
        val y = (((seed % 613) / 613f + t * speed) % 1f) * size.height
        val r = 2f + (seed % 5)
        drawCircle((if (i % 3 == 0) Color.White else color).copy(alpha = .75f), r, Offset(x, y))
    }
}

private fun DrawScope.emblem(kind: CelebrationKind, color: Color) {
    val w = size.width
    val c = Offset(w / 2, w / 2)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .45f), Color.Transparent)), w / 2, c)
    drawCircle(color, w * 0.36f, c, style = Stroke(w * 0.035f))
    when (kind) {
        CelebrationKind.BOSS, CelebrationKind.KING -> { // crossed swords
            listOf(-1f, 1f).forEach { s ->
                drawLine(Color.White, Offset(c.x - s * w * 0.22f, c.y + w * 0.22f), Offset(c.x + s * w * 0.18f, c.y - w * 0.18f), strokeWidth = w * 0.05f)
                drawLine(color, Offset(c.x - s * w * 0.27f, c.y + w * 0.12f), Offset(c.x - s * w * 0.12f, c.y + w * 0.27f), strokeWidth = w * 0.05f)
            }
        }
        CelebrationKind.HEROINE, CelebrationKind.WEDDING -> { // heart
            drawPath(Path().apply {
                moveTo(c.x, c.y + w * 0.2f)
                cubicTo(c.x - w * 0.35f, c.y - w * 0.02f, c.x - w * 0.15f, c.y - w * 0.3f, c.x, c.y - w * 0.1f)
                cubicTo(c.x + w * 0.15f, c.y - w * 0.3f, c.x + w * 0.35f, c.y - w * 0.02f, c.x, c.y + w * 0.2f)
                close()
            }, Color.White)
        }
        else -> { // crown
            drawPath(Path().apply {
                moveTo(c.x - w * 0.24f, c.y + w * 0.14f)
                lineTo(c.x - w * 0.26f, c.y - w * 0.14f)
                lineTo(c.x - w * 0.12f, c.y)
                lineTo(c.x, c.y - w * 0.22f)
                lineTo(c.x + w * 0.12f, c.y)
                lineTo(c.x + w * 0.26f, c.y - w * 0.14f)
                lineTo(c.x + w * 0.24f, c.y + w * 0.14f)
                close()
            }, Color.White)
            drawCircle(color, w * 0.03f, Offset(c.x, c.y - w * 0.22f))
        }
    }
}
