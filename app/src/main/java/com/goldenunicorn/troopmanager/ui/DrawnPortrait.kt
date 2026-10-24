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
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun sz(w: Float, h: Float) = Size(w * u, h * u)
        val p = PortraitPalette
        val skin = p.skin[spec.skin.mod(p.skin.size)]
        val hairC = p.hairColor[spec.hairColor.mod(p.hairColor.size)]
        val armorC = p.armorColor[spec.armorColor.mod(p.armorColor.size)]
        val shade = Color.Black.copy(alpha = .18f)

        val bg = p.background[spec.background.mod(p.background.size)]
        drawRect(Brush.radialGradient(listOf(bg.lighten(.22f), bg), center = o(50f, 35f), radius = 70 * u), size = size)

        // Long hair falls behind the shoulders.
        if (spec.hair == 1 || spec.hair == 2 || spec.hair == 4) drawRoundRect(hairC, o(26f, 30f), sz(48f, if (spec.hair == 1) 52f else 40f), CornerRadius(20 * u))

        // Shoulders and armor.
        val body = Path().apply {
            moveTo(10 * u, 100 * u); cubicTo(12 * u, 80 * u, 28 * u, 72 * u, 50 * u, 72 * u)
            cubicTo(72 * u, 72 * u, 88 * u, 80 * u, 90 * u, 100 * u); close()
        }
        drawPath(body, armorC)
        when (spec.armor) {
            0 -> { // plate: pauldrons and a center ridge
                drawOval(armorC.lighten(.22f), o(12f, 76f), sz(26f, 16f))
                drawOval(armorC.lighten(.22f), o(62f, 76f), sz(26f, 16f))
                drawLine(armorC.lighten(.35f), o(50f, 80f), o(50f, 100f), strokeWidth = 2.2f * u)
            }
            1 -> { // leather: straps
                drawLine(armorC.darken(.3f), o(30f, 76f), o(62f, 100f), strokeWidth = 3f * u)
                drawCircle(Color(0xFFB08A3E), 2f * u, o(44f, 86f))
            }
            2 -> { // robe: high collar
                drawPath(Path().apply { moveTo(38 * u, 72 * u); lineTo(50 * u, 90 * u); lineTo(62 * u, 72 * u); close() }, armorC.darken(.25f))
            }
            else -> { // scales
                for (row in 0 until 3) for (i in 0 until 6) drawArc(armorC.lighten(.18f), 0f, 180f, false, o(22f + i * 10f + (row % 2) * 5f, 80f + row * 6f), sz(10f, 8f), style = Stroke(1.4f * u))
            }
        }
        // Neck and head.
        drawRoundRect(skin.darken(.08f), o(43f, 58f), sz(14f, 18f), CornerRadius(5 * u))
        if (spec.elfEars) {
            drawPath(Path().apply { moveTo(31 * u, 46 * u); lineTo(19 * u, 33 * u); lineTo(32 * u, 54 * u); close() }, skin)
            drawPath(Path().apply { moveTo(69 * u, 46 * u); lineTo(81 * u, 33 * u); lineTo(68 * u, 54 * u); close() }, skin)
        } else {
            drawOval(skin, o(28f, 42f), sz(7f, 12f)); drawOval(skin, o(65f, 42f), sz(7f, 12f))
        }
        drawOval(skin, o(31f, 22f), sz(38f, 46f))
        drawOval(shade, o(31f, 52f), sz(38f, 16f), alpha = .25f)

        // Eyes, brows, nose, mouth.
        val eye = p.eyes[spec.eyes.mod(p.eyes.size)]
        listOf(42f, 58f).forEach { x ->
            drawOval(Color.White, o(x - 4.5f, 40f), sz(9f, 5f))
            drawCircle(eye, 2.3f * u, o(x, 42.5f))
            drawCircle(Color.Black, 1.0f * u, o(x, 42.5f))
            drawLine(hairC.darken(.1f), o(x - 5f, 36f), o(x + 5f, 35.2f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        }
        drawLine(skin.darken(.25f), o(50f, 44f), o(48.5f, 51f), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        drawArc(Color(0xFF8A4A42), 20f, 140f, false, o(44f, 52f), sz(12f, 6f), style = Stroke(1.6f * u, cap = StrokeCap.Round))

        // Beard.
        when (spec.beard) {
            1 -> drawOval(hairC.copy(alpha = .35f), o(35f, 48f), sz(30f, 20f))
            2 -> drawPath(Path().apply { moveTo(33 * u, 46 * u); cubicTo(34 * u, 70 * u, 66 * u, 70 * u, 67 * u, 46 * u); cubicTo(60 * u, 58 * u, 40 * u, 58 * u, 33 * u, 46 * u); close() }, hairC)
            3 -> drawRoundRect(hairC, o(45f, 58f), sz(10f, 11f), CornerRadius(4 * u))
        }

        // Markings.
        when (spec.marking) {
            1 -> drawLine(Color(0xFFB0605A), o(56f, 33f), o(63f, 50f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            2 -> { drawLine(Color(0xFF8A1F1F).copy(alpha = .8f), o(36f, 46f), o(46f, 48f), strokeWidth = 2.4f * u); drawLine(Color(0xFF8A1F1F).copy(alpha = .8f), o(54f, 48f), o(64f, 46f), strokeWidth = 2.4f * u) }
            3 -> { drawCircle(Color(0xFF6FD0C0).copy(alpha = .8f), 1.4f * u, o(50f, 31f)); drawArc(Color(0xFF6FD0C0).copy(alpha = .8f), 200f, 140f, false, o(44f, 28f), sz(12f, 8f), style = Stroke(1.0f * u)) }
        }

        // Hair on top.
        when (spec.hair) {
            0 -> drawArc(hairC, 180f, 180f, true, o(30f, 18f), sz(40f, 26f))
            1 -> { drawArc(hairC, 180f, 180f, true, o(29f, 17f), sz(42f, 28f)); drawRoundRect(hairC, o(29f, 28f), sz(6f, 30f), CornerRadius(3 * u)); drawRoundRect(hairC, o(65f, 28f), sz(6f, 30f), CornerRadius(3 * u)) }
            2 -> { drawArc(hairC, 180f, 180f, true, o(30f, 18f), sz(40f, 26f)); drawRoundRect(hairC, o(66f, 40f), sz(7f, 34f), CornerRadius(3.5f * u)) }
            3 -> { drawArc(hairC, 180f, 180f, true, o(30f, 18f), sz(40f, 24f)); drawCircle(hairC, 8f * u, o(50f, 14f)) }
            4 -> for (i in 0 until 7) drawPath(Path().apply { val x = 29f + i * 7f; moveTo(x * u, 32 * u); lineTo((x + 3.5f) * u, 12f * u + (i % 2) * 4 * u); lineTo((x + 7f) * u, 32 * u); close() }, hairC)
            else -> drawOval(Color.White.copy(alpha = .10f), o(40f, 23f), sz(14f, 6f))
        }
    }
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

