package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.PortraitSpec

/**
 * Procedural portrait: every character gets an own face from a tiny recipe (skin, hair, eyes,
 * ears, beard, armor, markings). Drawn resolution-independently on a 100×100 grid.
 */
internal object PortraitPalette {
    val skin = listOf(Color(0xFFF2D3BD), Color(0xFFE2B48E), Color(0xFFB98058), Color(0xFFD9A77A), Color(0xFF8D5A3B), Color(0xFF5E3A26))
    val skinNames = listOf("Hell", "Warm", "Bronze", "Oliv", "Dunkel", "Tief")
    val hairColor = listOf(Color(0xFF1C1814), Color(0xFF4A2F1E), Color(0xFF7A4A28), Color(0xFF2E3B2A), Color(0xFFA0472A), Color(0xFFE7D9A6), Color(0xFF8C8C8C), Color(0xFFC08A3E))
    val hairColorNames = listOf("Schwarz", "Dunkelbraun", "Braun", "Waldgrün", "Kupferrot", "Platin", "Grau", "Goldblond")
    val hairNames = listOf("Kurz", "Lang", "Zopf", "Dutt", "Wild", "Kahl")
    val eyes = listOf(Color(0xFF5A3A22), Color(0xFF2B2B2B), Color(0xFF3D7A4A), Color(0xFF4A6FA5), Color(0xFFC79A2E), Color(0xFF7A3FA0))
    val eyeNames = listOf("Braun", "Dunkel", "Grün", "Blau", "Gold", "Violett")
    val beardNames = listOf("Keiner", "Stoppeln", "Vollbart", "Kinnbart")
    val armorNames = listOf("Platte", "Leder", "Robe", "Schuppen")
    val armorColor = listOf(Color(0xFF3A5A8C), Color(0xFF3F6B3A), Color(0xFFC9A34A), Color(0xFF8A2E2E), Color(0xFF5C5C66), Color(0xFF1F1F24))
    val armorColorNames = listOf("Blau", "Grün", "Gold", "Rot", "Stahl", "Schwarz")
    val markingNames = listOf("Keine", "Narbe", "Kriegsbemalung", "Elbenzeichen")
    val background = listOf(Color(0xFF243447), Color(0xFF26402E), Color(0xFF4A3A1E), Color(0xFF3A2438), Color(0xFF1E3E44), Color(0xFF3B2A22))
}

@Composable
internal fun DrawnPortrait(spec: PortraitSpec, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(CircleShape).semantics { contentDescription = "Gezeichnetes Porträt" }) {
        val u = size.minDimension / 100f
        // Everything below is drawn on a 100×100 grid; light falls from the upper left.
        scale(u, u, pivot = Offset.Zero) { paintPortrait(spec) }
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun androidx.compose.ui.graphics.drawscope.DrawScope.oval(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(brush, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun androidx.compose.ui.graphics.drawscope.DrawScope.paintPortrait(spec: PortraitSpec) {
    val p = PortraitPalette
    val skin = p.skin[spec.skin.mod(p.skin.size)]
    val hairC = p.hairColor[spec.hairColor.mod(p.hairColor.size)]
    val arm = p.armorColor[spec.armorColor.mod(p.armorColor.size)]
    val bg = p.background[spec.background.mod(p.background.size)]
    val eye = p.eyes[spec.eyes.mod(p.eyes.size)]
    val black = Color.Black
    val gold = Color(0xFFC9A34A)

    // Background with soft light and vignette, plus a faint ray.
    drawRect(Brush.radialGradient(0f to bg.lighten(.35f), .6f to bg, 1f to bg.darken(.55f), center = Offset(36f, 30f), radius = 86f), Offset.Zero, Size(100f, 100f))
    drawPath(Path().apply { moveTo(10f, 0f); lineTo(40f, 0f); lineTo(80f, 100f); lineTo(55f, 100f); close() }, Color(0x0FFFF0C8))

    // Long hair falls behind the shoulders.
    if (spec.hair == 1 || spec.hair == 2 || spec.hair == 4) {
        val bottom = if (spec.hair == 1) 90f else 78f
        drawPath(Path().apply {
            moveTo(28f, 36f); cubicTo(22f, 60f, 24f, bottom - 4f, 30f, bottom); lineTo(70f, bottom); cubicTo(76f, bottom - 4f, 78f, 60f, 72f, 36f); close()
        }, Brush.verticalGradient(listOf(hairC, hairC.darken(.35f)), 28f, 85f))
    }

    // Cloak and shoulders.
    drawPath(Path().apply { moveTo(4f, 100f); cubicTo(8f, 82f, 24f, 74f, 50f, 73f); cubicTo(76f, 74f, 92f, 82f, 96f, 100f); close() }, arm.darken(.5f))
    drawPath(Path().apply { moveTo(12f, 100f); cubicTo(14f, 82f, 30f, 75f, 50f, 75f); cubicTo(70f, 75f, 86f, 82f, 88f, 100f); close() },
        Brush.linearGradient(0f to arm.lighten(.25f), .5f to arm, 1f to arm.darken(.45f), start = Offset(20f, 70f), end = Offset(80f, 100f)))
    when (spec.armor) {
        0 -> {
            listOf(10f, 62f).forEach { px ->
                oval(Brush.radialGradient(0f to arm.lighten(.6f), .4f to arm.lighten(.15f), 1f to arm.darken(.35f), center = Offset(px + 9f, 79f), radius = 16f), px + 14f, 85f, 14f, 9f)
                drawOval(black.copy(alpha = .35f), Offset(px, 76f), Size(28f, 18f), style = Stroke(.8f))
            }
            drawLine(arm.lighten(.5f), Offset(50f, 82f), Offset(50f, 100f), 1.6f)
            drawCircle(gold, 2.2f, Offset(50f, 84f))
        }
        1 -> {
            drawLine(arm.darken(.4f), Offset(28f, 78f), Offset(64f, 100f), 3f)
            drawCircle(gold, 2.2f, Offset(43f, 87f))
        }
        2 -> {
            val collar = Path().apply { moveTo(36f, 74f); lineTo(50f, 94f); lineTo(64f, 74f); close() }
            drawPath(collar, arm.darken(.3f))
            drawPath(Path().apply { moveTo(36f, 74f); lineTo(50f, 94f); lineTo(64f, 74f) }, gold, style = Stroke(1f))
        }
        else -> for (row in 0 until 3) for (i in 0 until 7)
            drawArc(arm.lighten(.3f), 0f, 180f, false, Offset(15f + i * 10f + (row % 2) * 5f, 79f + row * 6f), Size(10f, 10f), style = Stroke(1.2f))
    }

    // Neck with the shadow under the jaw.
    drawPath(Path().apply { moveTo(42f, 58f); lineTo(41f, 76f); quadraticBezierTo(50f, 79f, 59f, 76f); lineTo(58f, 58f); close() },
        Brush.verticalGradient(listOf(skin.darken(.35f), skin.darken(.12f)), 58f, 76f))

    // Ears.
    val earColor = skin.darken(.06f)
    if (spec.elfEars) {
        listOf(false, true).forEach { mirrored ->
            withTransform({ if (mirrored) scale(-1f, 1f, pivot = Offset(50f, 50f)) }) {
                drawPath(Path().apply { moveTo(32f, 44f); quadraticBezierTo(22f, 36f, 16f, 28f); quadraticBezierTo(24f, 44f, 32f, 54f); close() }, earColor)
                drawPath(Path().apply { moveTo(30f, 45f); quadraticBezierTo(24f, 40f, 20f, 33f); quadraticBezierTo(25f, 44f, 30f, 51f); close() }, Color(0x407A3B2A))
            }
        }
    } else {
        oval(earColor, 31.5f, 47f, 3.8f, 6.5f)
        oval(earColor, 68.5f, 47f, 3.8f, 6.5f)
    }

    // Face with a real jaw line.
    val face = Path().apply {
        moveTo(50f, 21f); cubicTo(63f, 21f, 70f, 31f, 69.5f, 43f); cubicTo(69f, 54f, 62f, 66f, 50f, 68.5f)
        cubicTo(38f, 66f, 31f, 54f, 30.5f, 43f); cubicTo(30f, 31f, 37f, 21f, 50f, 21f); close()
    }
    drawPath(face, Brush.radialGradient(0f to skin.lighten(.18f), .65f to skin, 1f to skin.darken(.22f), center = Offset(42f, 36f), radius = 34f))
    clipPath(face) {
        oval(black.copy(alpha = .10f), 70f, 48f, 12f, 26f)
        oval(black.copy(alpha = .08f), 50f, 70f, 20f, 6f)
    }
    drawPath(face, skin.darken(.6f).copy(alpha = .35f), style = Stroke(.6f))
    oval(Color(0x29D0605A), 39f, 52f, 4.5f, 2.6f)
    oval(Color(0x29D0605A), 61f, 52f, 4.5f, 2.6f)

    // Eyes and brows.
    listOf(41.5f, 58.5f).forEach { x ->
        oval(skin.darken(.5f).copy(alpha = .18f), x, 41.2f, 5.6f, 3.6f)
        oval(Color(0xFFF7F1EA), x, 42.5f, 4.4f, 2.5f)
        drawCircle(Brush.radialGradient(listOf(eye.lighten(.35f), eye.darken(.25f)), center = Offset(x - .5f, 42f), radius = 2.6f), 2.3f, Offset(x, 42.5f))
        drawCircle(Color(0xFF111111), 1f, Offset(x, 42.5f))
        drawCircle(Color.White.copy(alpha = .9f), .55f, Offset(x - .8f, 41.6f))
        drawArc(hairC.darken(.2f), 189f, 162f, false, Offset(x - 4.6f, 39.9f), Size(9.2f, 5.2f), style = Stroke(.9f, cap = StrokeCap.Round))
        val dir = if (x < 50f) 1f else -1f
        drawPath(Path().apply { moveTo(x - 5f * dir, 36.6f); quadraticBezierTo(x, 34.6f, x + 5f * dir, 36f) }, hairC.darken(.05f), style = Stroke(1.7f, cap = StrokeCap.Round))
    }

    // Nose and mouth.
    drawPath(Path().apply { moveTo(51f, 43f); quadraticBezierTo(52.5f, 49f, 51f, 51.5f); quadraticBezierTo(49.5f, 52.6f, 47.8f, 51.6f) },
        skin.darken(.45f).copy(alpha = .7f), style = Stroke(.9f, cap = StrokeCap.Round))
    oval(Color.White.copy(alpha = .18f), 49.6f, 47f, .8f, 3f)
    drawPath(Path().apply { moveTo(44.5f, 57f); quadraticBezierTo(50f, 55.6f, 55.5f, 57f); quadraticBezierTo(50f, 60.5f, 44.5f, 57f); close() }, Color(0xFFAC5D55))
    drawPath(Path().apply { moveTo(44.5f, 57f); quadraticBezierTo(50f, 57.8f, 55.5f, 57f) }, Color(0xCC6E2E2A), style = Stroke(.7f, cap = StrokeCap.Round))

    // Beard.
    when (spec.beard) {
        1 -> clipPath(face) { oval(hairC.copy(alpha = .30f), 50f, 62f, 18f, 10f) }
        2 -> {
            drawPath(Path().apply {
                moveTo(31f, 46f); cubicTo(32f, 66f, 42f, 74f, 50f, 74f); cubicTo(58f, 74f, 68f, 66f, 69f, 46f)
                cubicTo(64f, 58f, 56f, 55f, 50f, 59f); cubicTo(44f, 55f, 36f, 58f, 31f, 46f); close()
            }, Brush.verticalGradient(listOf(hairC, hairC.darken(.3f)), 48f, 72f))
            for (i in 0 until 6) drawLine(hairC.lighten(.4f).copy(alpha = .35f), Offset(40f + i * 4f, 62f), Offset(41f + i * 4f, 71f), .6f)
        }
        3 -> drawPath(Path().apply { moveTo(45f, 61f); quadraticBezierTo(50f, 74f, 55f, 61f); close() }, hairC)
    }

    // Markings.
    when (spec.marking) {
        1 -> drawLine(Color(0xFFB0605A), Offset(57f, 33f), Offset(63f, 49f), 1.2f, cap = StrokeCap.Round)
        2 -> {
            drawLine(Color(0xBF8A1F1F), Offset(35f, 47f), Offset(45f, 49f), 2.2f)
            drawLine(Color(0xBF8A1F1F), Offset(55f, 49f), Offset(65f, 47f), 2.2f)
        }
        3 -> {
            drawCircle(Color(0xD96FD0C0), 1.2f, Offset(50f, 30.5f))
            drawArc(Color(0xD96FD0C0), 207f, 126f, false, Offset(45f, 27f), Size(10f, 10f), style = Stroke(.8f))
        }
    }

    // Hair on top with clipped highlight strands.
    if (spec.hair != 5) {
        val hair = Path().apply {
            if (spec.hair == 4) {
                moveTo(28f, 40f)
                for (i in 0 until 8) { lineTo(30f + i * 5.6f, 14f + (i % 2) * 6f); lineTo(33f + i * 5.6f, 30f) }
                lineTo(72f, 40f); cubicTo(70f, 24f, 62f, 16f, 50f, 16f); cubicTo(38f, 16f, 30f, 24f, 28f, 40f); close()
            } else {
                moveTo(29f, 42f); cubicTo(27f, 24f, 38f, 15f, 50f, 15f); cubicTo(62f, 15f, 73f, 24f, 71f, 42f)
                cubicTo(68f, 33f, 62f, 27f, 55f, 26f); cubicTo(50f, 30f, 40f, 31f, 35f, 33f); cubicTo(32f, 36f, 30f, 39f, 29f, 42f); close()
            }
        }
        val hairBrush = Brush.linearGradient(0f to hairC.lighten(.22f), .5f to hairC, 1f to hairC.darken(.3f), start = Offset(30f, 14f), end = Offset(70f, 40f))
        drawPath(hair, hairBrush)
        clipPath(hair) {
            for (i in 0 until 6) drawPath(Path().apply { moveTo(40f + i * 5f, 16f); quadraticBezierTo(37f + i * 5f, 22f, 33f + i * 5f, 30f) },
                hairC.lighten(.55f).copy(alpha = .45f), style = Stroke(.9f, cap = StrokeCap.Round))
        }
        when (spec.hair) {
            1 -> listOf(27f, 67f).forEach { x ->
                drawPath(Path().apply { moveTo(x, 32f); cubicTo(x - 1f, 48f, x + 1f, 58f, x + 3f, 66f); lineTo(x + 6f, 64f); cubicTo(x + 5f, 52f, x + 5f, 42f, x + 6f, 34f); close() },
                    Brush.verticalGradient(listOf(hairC, hairC.darken(.3f)), 30f, 70f))
            }
            2 -> for (i in 0 until 5) {
                oval(hairC, 70f, 40f + i * 7f, 4.2f, 4.4f)
                drawOval(hairC.darken(.4f).copy(alpha = .6f), Offset(65.8f, 35.6f + i * 7f), Size(8.4f, 8.8f), style = Stroke(.6f))
            }
            3 -> {
                drawCircle(hairC, 8f, Offset(50f, 13f))
                drawArc(hairC.lighten(.5f).copy(alpha = .4f), 180f, 144f, false, Offset(45f, 8f), Size(10f, 10f), style = Stroke(.9f))
            }
        }
    } else {
        withTransform({ rotate(-17f, pivot = Offset(46f, 25f)) }) { oval(Color.White.copy(alpha = .12f), 46f, 25f, 8f, 3f) }
    }

    // Rim light along the upper left edge.
    drawArc(Color(0x59FFE6B4), 189f, 90f, false, Offset(1f, 1f), Size(98f, 98f), style = Stroke(1.2f))
}

private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** Chips-based builder for a [PortraitSpec] with a live preview. */
@Composable
internal fun LookEditor(spec: PortraitSpec, onChange: (PortraitSpec) -> Unit, allowBeard: Boolean = true) {
    val p = PortraitPalette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DrawnPortrait(spec, Modifier.size(112.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gesicht gestalten", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Wähle unten, wie diese Person aussieht. Ein eigenes Foto ist jederzeit möglich.", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    val r = kotlin.random.Random(System.nanoTime())
                    onChange(spec.copy(skin = r.nextInt(6), hair = r.nextInt(6), hairColor = r.nextInt(8), eyes = r.nextInt(6),
                        beard = if (allowBeard) r.nextInt(4) else 0, armor = r.nextInt(4), armorColor = r.nextInt(6), marking = r.nextInt(4), background = r.nextInt(6)))
                }) { Text("Zufällig") }
            }
        }
        LookRow("Haut", p.skinNames, spec.skin) { onChange(spec.copy(skin = it)) }
        LookRow("Frisur", p.hairNames, spec.hair) { onChange(spec.copy(hair = it)) }
        LookRow("Haarfarbe", p.hairColorNames, spec.hairColor) { onChange(spec.copy(hairColor = it)) }
        LookRow("Augen", p.eyeNames, spec.eyes) { onChange(spec.copy(eyes = it)) }
        LookRow("Ohren", listOf("Rund", "Spitz (Elb)"), if (spec.elfEars) 1 else 0) { onChange(spec.copy(elfEars = it == 1)) }
        if (allowBeard) LookRow("Bart", p.beardNames, spec.beard) { onChange(spec.copy(beard = it)) }
        LookRow("Rüstung", p.armorNames, spec.armor) { onChange(spec.copy(armor = it)) }
        LookRow("Farbe", p.armorColorNames, spec.armorColor) { onChange(spec.copy(armorColor = it)) }
        LookRow("Zeichen", p.markingNames, spec.marking) { onChange(spec.copy(marking = it)) }
        LookRow("Hintergrund", listOf("Nacht", "Wald", "Sonne", "Purpur", "See", "Glut"), spec.background) { onChange(spec.copy(background = it)) }
    }
}

@Composable
private fun LookRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, color = Mist, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, name -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(name) }) }
        }
    }
}

/** Dialog wrapper so looks can be changed later in the game. */
@Composable
internal fun LookEditorDialog(title: String, initial: PortraitSpec, onDismiss: () -> Unit, onSave: (PortraitSpec) -> Unit) {
    var spec by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { LookEditor(spec, { spec = it }) } },
        confirmButton = { TextButton(onClick = { onSave(spec) }) { Text("Übernehmen", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

