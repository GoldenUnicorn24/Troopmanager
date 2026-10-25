package com.goldenunicorn.troopmanager.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.goldenunicorn.troopmanager.model.CharacterAppearance
import com.goldenunicorn.troopmanager.model.Gender
import kotlin.math.cos
import kotlin.math.sin

/**
 * Project Rebirth character art.
 *
 * Two renderers share one contract ([CharacterAppearance]):
 *  1. [LayeredAssetPortrait]: composes painted raster layers from `assets/portraits/` (see
 *     docs/CHARACTER-CREATION-DESIGN.md for the exact file list). Used automatically once a
 *     complete base set is installed.
 *  2. [PaintedPortrait]: the shipped fallback. A resolution-independent vector painting with
 *     directional light, soft shadows, lid and lip modelling, strand-based hair and fabric/metal
 *     shading. It is honest procedural art, not a painted asset.
 */
enum class Framing { HEAD, BUST, FULL }

internal object ArtPalette {
    data class Tone(val base: Color, val shadow: Color, val light: Color, val lip: Color, val blush: Color)

    val skin = listOf(
        Tone(Color(0xFFF1D2BF), Color(0xFFC99A86), Color(0xFFFFEDE2), Color(0xFFC77D78), Color(0xFFE79A92)),
        Tone(Color(0xFFE2B48E), Color(0xFFB07E5C), Color(0xFFF7D3B2), Color(0xFFB66A5E), Color(0xFFDB8E78)),
        Tone(Color(0xFFC08458), Color(0xFF8C5A38), Color(0xFFDDA67C), Color(0xFF9C5646), Color(0xFFC57A5E)),
        Tone(Color(0xFFD3A57A), Color(0xFF9F744F), Color(0xFFEBC59D), Color(0xFFA9665A), Color(0xFFCF8C70)),
        Tone(Color(0xFF8C5A3C), Color(0xFF5E3A24), Color(0xFFAE7A58), Color(0xFF6E3A34), Color(0xFF9A5A48)),
        Tone(Color(0xFF5C3A27), Color(0xFF3A2317), Color(0xFF7C5440), Color(0xFF4E2A26), Color(0xFF6E4033)),
    )
    val hair = listOf(Color(0xFF1B1714), Color(0xFF3F2818), Color(0xFF6E4324), Color(0xFF2D3A28), Color(0xFF9A4224), Color(0xFFE6D8AE), Color(0xFF8E8C88), Color(0xFFC0904A))
    val eyes = listOf(Color(0xFF5B3820), Color(0xFF2A221C), Color(0xFF3E7A4C), Color(0xFF4C77AE), Color(0xFFC0882A), Color(0xFF7A8A96))
    val fabric = listOf(Color(0xFF25365A), Color(0xFF2F4A2E), Color(0xFFB98E3A), Color(0xFF7A2430), Color(0xFF5C6068), Color(0xFF1C1C22), Color(0xFF5C4128), Color(0xFFE6DCC6))
    val backgrounds = listOf(
        listOf(Color(0xFF2B3E58), Color(0xFF0B1220)), listOf(Color(0xFF2F4A34), Color(0xFF0B140E)),
        listOf(Color(0xFF6A5126), Color(0xFF1A1208)), listOf(Color(0xFF4A2A48), Color(0xFF130A14)),
        listOf(Color(0xFF235058), Color(0xFF081619)), listOf(Color(0xFF5A2E1E), Color(0xFF160A06)),
    )
    val metal = Color(0xFF9BA3AE)
    val gold = Color(0xFFD9B25C)
    val leather = Color(0xFF6B4A2E)
}

internal fun Color.mix(other: Color, t: Float) = Color(
    red + (other.red - red) * t, green + (other.green - green) * t, blue + (other.blue - blue) * t, alpha + (other.alpha - alpha) * t,
)
private fun Color.shade(f: Float) = mix(Color.Black, f)
private fun Color.tint(f: Float) = mix(Color.White, f)

/** Tiny deterministic noise for strand placement; the same person always gets the same strands. */
private class Strands(seed: Int) {
    private var s = seed.toLong() and 0xFFFFFFFFL
    fun next(): Float { s = (s * 1103515245L + 12345L) and 0x7FFFFFFFL; return (s % 10000L) / 10000f }
    fun range(a: Float, b: Float) = a + (b - a) * next()
}

private fun seedOf(a: CharacterAppearance) = a.hashCode()

// ------------------------------------------------------------ public composables

/** The portrait used everywhere (editor, tavern dialogue, people lists). */
@Composable
fun CharacterPortrait(appearance: CharacterAppearance, gender: Gender, modifier: Modifier = Modifier, framing: Framing = Framing.BUST) {
    val context = LocalContext.current
    val layered = remember(appearance, gender, framing) { PortraitAssets.layersFor(context, appearance, gender, framing) }
    if (layered != null) LayeredAssetPortrait(layered, appearance, modifier)
    else PaintedPortrait(appearance, gender, modifier, framing)
}

@Composable
fun PaintedPortrait(appearance: CharacterAppearance, gender: Gender, modifier: Modifier = Modifier, framing: Framing = Framing.BUST) {
    Canvas(modifier.clipToBounds().semantics { contentDescription = "Porträt" }) {
        paintPortrait(appearance.normalized(), gender, framing)
    }
}

internal fun DrawScope.paintPortrait(a: CharacterAppearance, gender: Gender, framing: Framing) {
    val w = size.width
    val h = size.height
    if (w < 8f || h < 8f) return
    val bg = ArtPalette.backgrounds[a.background]
    drawRect(Brush.radialGradient(bg, Offset(w * .32f, h * .28f), maxOf(w, h) * 1.05f))
    // A soft window light from the upper left.
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .10f), Color.Transparent), Offset(w * .15f, h * .05f), w * .9f))
    when (framing) {
        Framing.HEAD -> {
            val s = w / 46f
            drawBust(a, gender, Offset(w / 2f, h * .56f), s)
        }
        Framing.BUST -> {
            val s = w / 78f
            drawBust(a, gender, Offset(w / 2f, h * .40f), s)
        }
        Framing.FULL -> drawFullFigure(a, gender, Offset(w / 2f, h * .97f), h / 146f, detailed = true)
    }
    // Vignette.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)), Offset(w / 2f, h * .42f), maxOf(w, h) * .78f))
}

// ------------------------------------------------------------ bust: shoulders, clothing, head

private fun DrawScope.drawBust(a: CharacterAppearance, gender: Gender, c: Offset, s: Float) {
    val female = gender == Gender.FEMALE
    val shoulder = (if (female) 25f else 29f) + (a.build - 1) * 2f + (a.muscle - 50) / 25f
    val neckW = (if (female) 5.2f else 6.6f) + (a.muscle - 50) / 40f
    if (a.hairStyle in setOf(2, 3, 5, 6)) drawBackHair(a, c, s, gender)
    if (a.cloak == 3) drawHood(a, c, s)
    // Neck with a shadow cast by the jaw.
    val tone = ArtPalette.skin[a.skinTone]
    val neck = Path().apply {
        moveTo(c.x - neckW * s, c.y + 12f * s)
        cubicTo(c.x - neckW * s, c.y + 22f * s, c.x - neckW * 1.2f * s, c.y + 27f * s, c.x - neckW * 1.6f * s, c.y + 31f * s)
        lineTo(c.x + neckW * 1.6f * s, c.y + 31f * s)
        cubicTo(c.x + neckW * 1.2f * s, c.y + 27f * s, c.x + neckW * s, c.y + 22f * s, c.x + neckW * s, c.y + 12f * s)
        close()
    }
    drawPath(neck, Brush.horizontalGradient(listOf(tone.base.shade(.08f), tone.base, tone.shadow), c.x - neckW * s, c.x + neckW * s))
    drawPath(neck, Brush.verticalGradient(listOf(tone.shadow.copy(alpha = .75f), Color.Transparent), c.y + 12f * s, c.y + 22f * s))
    drawTorso(a, gender, c, s, shoulder)
    drawHead(a, gender, c, s, detailed = true)
}

private fun DrawScope.drawTorso(a: CharacterAppearance, gender: Gender, c: Offset, s: Float, shoulder: Float) {
    val female = gender == Gender.FEMALE
    val fabric = ArtPalette.fabric[a.primaryColor]
    val accent = ArtPalette.fabric[a.accentColor]
    val top = c.y + 28f * s
    val bottom = c.y + 60f * s
    val body = Path().apply {
        moveTo(c.x - 7f * s, top)
        cubicTo(c.x - shoulder * .6f * s, top + 1f * s, c.x - shoulder * s, top + 3f * s, c.x - shoulder * 1.05f * s, top + 11f * s)
        lineTo(c.x - shoulder * 1.15f * s, bottom)
        lineTo(c.x + shoulder * 1.15f * s, bottom)
        lineTo(c.x + shoulder * 1.05f * s, top + 11f * s)
        cubicTo(c.x + shoulder * s, top + 3f * s, c.x + shoulder * .6f * s, top + 1f * s, c.x + 7f * s, top)
        close()
    }
    if (a.cloak in 1..3) drawCloakBack(a, c, s, shoulder, top, bottom)
    drawPath(body, Brush.linearGradient(listOf(fabric.tint(.12f), fabric, fabric.shade(.45f)), Offset(c.x - shoulder * s, top), Offset(c.x + shoulder * s, bottom)))
    // Neckline per clothing type.
    val tone = ArtPalette.skin[a.skinTone]
    when (a.clothing) {
        0 -> { // tunic, V neck
            val v = Path().apply { moveTo(c.x - 6f * s, top); lineTo(c.x, top + 9f * s); lineTo(c.x + 6f * s, top); close() }
            drawPath(v, tone.base.shade(.12f))
            drawPath(v, accent, style = Stroke(1.1f * s))
        }
        1 -> { // laced doublet
            drawPath(Path().apply { moveTo(c.x - 6f * s, top); cubicTo(c.x - 3f * s, top + 3f * s, c.x + 3f * s, top + 3f * s, c.x + 6f * s, top); close() }, tone.base.shade(.1f))
            for (i in 0..4) {
                val y = top + 6f * s + i * 4.5f * s
                drawLine(ArtPalette.leather.tint(.3f), Offset(c.x - 2f * s, y), Offset(c.x + 2f * s, y + 2.2f * s), 0.6f * s)
                drawLine(ArtPalette.leather.tint(.3f), Offset(c.x + 2f * s, y), Offset(c.x - 2f * s, y + 2.2f * s), 0.6f * s)
            }
            drawLine(fabric.shade(.5f), Offset(c.x, top + 3f * s), Offset(c.x, bottom), 0.8f * s)
        }
        2 -> { // dress, scoop neckline
            val scoop = Path().apply { moveTo(c.x - (if (female) 11f else 8f) * s, top + 1f * s); cubicTo(c.x - 6f * s, top + 11f * s, c.x + 6f * s, top + 11f * s, c.x + (if (female) 11f else 8f) * s, top + 1f * s); close() }
            drawPath(scoop, Brush.verticalGradient(listOf(tone.base, tone.shadow), top, top + 11f * s))
            drawPath(Path().apply { moveTo(c.x - 11f * s, top + 1f * s); cubicTo(c.x - 6f * s, top + 11f * s, c.x + 6f * s, top + 11f * s, c.x + 11f * s, top + 1f * s) }, accent, style = Stroke(1.2f * s))
        }
        3 -> { // quilted gambeson
            drawPath(Path().apply { moveTo(c.x - 7f * s, top - 1f * s); lineTo(c.x + 7f * s, top - 1f * s); lineTo(c.x + 6f * s, top + 4f * s); lineTo(c.x - 6f * s, top + 4f * s); close() }, fabric.tint(.15f))
            var y = top + 7f * s
            while (y < bottom) { drawLine(fabric.shade(.35f), Offset(c.x - shoulder * 1.1f * s, y), Offset(c.x + shoulder * 1.1f * s, y), .5f * s); y += 4f * s }
            var x = c.x - shoulder * s
            while (x < c.x + shoulder * s) { drawLine(fabric.shade(.25f), Offset(x, top + 5f * s), Offset(x, bottom), .35f * s); x += 5f * s }
        }
        4 -> { // noble robe, high collar with trim
            drawPath(Path().apply { moveTo(c.x - 8f * s, top - 3f * s); lineTo(c.x - 9f * s, top + 4f * s); lineTo(c.x, top + 10f * s); lineTo(c.x + 9f * s, top + 4f * s); lineTo(c.x + 8f * s, top - 3f * s); close() }, accent)
            drawLine(ArtPalette.gold, Offset(c.x, top + 10f * s), Offset(c.x, bottom), 1.4f * s)
            for (i in 0..3) drawCircle(ArtPalette.gold.tint(.2f), .8f * s, Offset(c.x, top + 14f * s + i * 6f * s))
        }
    }
    drawArmor(a, c, s, shoulder, top, bottom)
    if (a.cloak in 1..3) drawCloakFront(a, c, s, shoulder, top)
    // Jewelry on the chest.
    if (a.jewelry == 2) {
        drawPath(Path().apply { moveTo(c.x - 6f * s, top - .5f * s); cubicTo(c.x - 4f * s, top + 8f * s, c.x + 4f * s, top + 8f * s, c.x + 6f * s, top - .5f * s) }, ArtPalette.gold, style = Stroke(.5f * s))
        drawCircle(Brush.radialGradient(listOf(ArtPalette.fabric[a.accentColor].tint(.5f), ArtPalette.fabric[a.accentColor].shade(.3f)), Offset(c.x - .4f * s, top + 6f * s), 1.8f * s), 1.5f * s, Offset(c.x, top + 6.5f * s))
    }
    // Light from the left on the whole torso.
    drawPath(body, Brush.horizontalGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent, Color.Black.copy(alpha = .28f)), c.x - shoulder * s, c.x + shoulder * 1.15f * s))
}

private fun DrawScope.drawArmor(a: CharacterAppearance, c: Offset, s: Float, shoulder: Float, top: Float, bottom: Float) {
    when (a.armor) {
        1 -> { // leather: shoulder guards with stitching
            for (side in listOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(c.x + side * 9f * s, top + 1f * s)
                    cubicTo(c.x + side * (shoulder - 2f) * s, top - 1f * s, c.x + side * (shoulder + 3f) * s, top + 4f * s, c.x + side * (shoulder + 2.5f) * s, top + 14f * s)
                    cubicTo(c.x + side * (shoulder - 4f) * s, top + 12f * s, c.x + side * 14f * s, top + 9f * s, c.x + side * 9f * s, top + 1f * s)
                    close()
                }
                drawPath(p, Brush.linearGradient(listOf(ArtPalette.leather.tint(.18f), ArtPalette.leather.shade(.35f)), Offset(c.x, top), Offset(c.x + side * shoulder * s, top + 14f * s)))
                // Stitching: short strokes along the edge (no dash path effect, which degenerates at tiny sizes).
                for (k in 0 until 7) {
                    val t = k / 6f
                    val sx = c.x + side * (10f + (shoulder - 8f) * t) * s
                    val sy = top + (1f + 10f * t * t) * s
                    drawLine(ArtPalette.leather.tint(.4f), Offset(sx, sy), Offset(sx + side * .9f * s, sy + .4f * s), .3f * s)
                }
            }
            drawLine(ArtPalette.leather.shade(.3f), Offset(c.x - shoulder * .9f * s, top + 4f * s), Offset(c.x + shoulder * .3f * s, bottom), 2.2f * s)
            drawCircle(ArtPalette.gold, 1.1f * s, Offset(c.x - shoulder * .3f * s, top + 22f * s))
        }
        2 -> { // chainmail: tiny rings, darker towards the edges
            val mail = Path().apply {
                moveTo(c.x - (shoulder + 1f) * s, top + 6f * s); lineTo(c.x - (shoulder + 2f) * s, bottom)
                lineTo(c.x + (shoulder + 2f) * s, bottom); lineTo(c.x + (shoulder + 1f) * s, top + 6f * s)
                cubicTo(c.x + shoulder * .6f * s, top - 1f * s, c.x - shoulder * .6f * s, top - 1f * s, c.x - (shoulder + 1f) * s, top + 6f * s)
                close()
            }
            drawPath(mail, Brush.horizontalGradient(listOf(ArtPalette.metal.tint(.1f), ArtPalette.metal.shade(.25f), ArtPalette.metal.shade(.55f)), c.x - shoulder * s, c.x + shoulder * s))
            clipPath(mail) {
                var y = top
                var row = 0
                while (y < bottom) {
                    var x = c.x - (shoulder + 3f) * s + (if (row % 2 == 0) 0f else 0.9f * s)
                    while (x < c.x + (shoulder + 3f) * s) {
                        drawArc(Color.Black.copy(alpha = .35f), 0f, 180f, false, Offset(x, y), Size(1.8f * s, 1.4f * s), style = Stroke(.25f * s))
                        x += 1.8f * s
                    }
                    y += 1.1f * s; row++
                }
            }
        }
        3 -> { // scales
            val scale = ArtPalette.metal.mix(ArtPalette.fabric[a.accentColor], .35f)
            var y = top + 4f * s
            var row = 0
            while (y < bottom) {
                var x = c.x - (shoulder + 1f) * s + (if (row % 2 == 0) 0f else 1.6f * s)
                while (x < c.x + (shoulder + 1f) * s) {
                    val p = Path().apply { moveTo(x, y); cubicTo(x, y + 2.6f * s, x + 3.2f * s, y + 2.6f * s, x + 3.2f * s, y); close() }
                    drawPath(p, Brush.verticalGradient(listOf(scale.tint(.25f), scale.shade(.4f)), y, y + 2.6f * s))
                    drawPath(p, Color.Black.copy(alpha = .35f), style = Stroke(.2f * s))
                    x += 3.2f * s
                }
                y += 2f * s; row++
            }
        }
        4 -> { // plate: breastplate with ridge, pauldrons, rivets
            val plate = Path().apply {
                moveTo(c.x - 13f * s, top + 3f * s)
                cubicTo(c.x - 15f * s, top + 18f * s, c.x - 12f * s, bottom - 2f * s, c.x, bottom)
                cubicTo(c.x + 12f * s, bottom - 2f * s, c.x + 15f * s, top + 18f * s, c.x + 13f * s, top + 3f * s)
                cubicTo(c.x + 6f * s, top + 6f * s, c.x - 6f * s, top + 6f * s, c.x - 13f * s, top + 3f * s)
                close()
            }
            drawPath(plate, Brush.linearGradient(listOf(ArtPalette.metal.tint(.45f), ArtPalette.metal, ArtPalette.metal.shade(.55f)), Offset(c.x - 12f * s, top), Offset(c.x + 14f * s, bottom)))
            drawLine(Color.White.copy(alpha = .45f), Offset(c.x - .4f * s, top + 6f * s), Offset(c.x - .4f * s, bottom - 2f * s), .6f * s)
            drawPath(plate, Color.Black.copy(alpha = .5f), style = Stroke(.4f * s))
            for (side in listOf(-1f, 1f)) {
                val pad = Path().apply {
                    moveTo(c.x + side * 10f * s, top + 1f * s)
                    cubicTo(c.x + side * (shoulder + 1f) * s, top - 4f * s, c.x + side * (shoulder + 5f) * s, top + 6f * s, c.x + side * (shoulder + 4f) * s, top + 15f * s)
                    cubicTo(c.x + side * (shoulder - 3f) * s, top + 13f * s, c.x + side * 15f * s, top + 9f * s, c.x + side * 10f * s, top + 1f * s)
                    close()
                }
                drawPath(pad, Brush.radialGradient(listOf(ArtPalette.metal.tint(.55f), ArtPalette.metal.shade(.5f)), Offset(c.x + side * (shoulder - 2f) * s, top + 2f * s), 14f * s))
                drawPath(pad, Color.Black.copy(alpha = .5f), style = Stroke(.4f * s))
                drawPath(Path().apply {
                    moveTo(c.x + side * (shoulder - 4f) * s, top + 10f * s)
                    cubicTo(c.x + side * (shoulder) * s, top + 9f * s, c.x + side * (shoulder + 3f) * s, top + 11f * s, c.x + side * (shoulder + 3.5f) * s, top + 13f * s)
                }, ArtPalette.fabric[a.accentColor].tint(.1f), style = Stroke(.9f * s))
                for (i in 0..2) drawCircle(ArtPalette.metal.tint(.6f), .45f * s, Offset(c.x + side * (12f + i * 4f) * s, top + 3f * s + i * .6f * s))
            }
        }
    }
}

private fun DrawScope.drawCloakBack(a: CharacterAppearance, c: Offset, s: Float, shoulder: Float, top: Float, bottom: Float) {
    val color = if (a.cloak == 2) Color(0xFF4A3524) else ArtPalette.fabric[a.accentColor].shade(.15f)
    val p = Path().apply {
        moveTo(c.x - (shoulder - 2f) * s, top)
        cubicTo(c.x - (shoulder + 6f) * s, top + 8f * s, c.x - (shoulder + 9f) * s, bottom - 10f * s, c.x - (shoulder + 9f) * s, bottom)
        lineTo(c.x + (shoulder + 9f) * s, bottom)
        cubicTo(c.x + (shoulder + 9f) * s, bottom - 10f * s, c.x + (shoulder + 6f) * s, top + 8f * s, c.x + (shoulder - 2f) * s, top)
        close()
    }
    drawPath(p, Brush.horizontalGradient(listOf(color.tint(.08f), color.shade(.5f)), c.x - shoulder * s, c.x + shoulder * s))
}

private fun DrawScope.drawCloakFront(a: CharacterAppearance, c: Offset, s: Float, shoulder: Float, top: Float) {
    val color = ArtPalette.fabric[a.accentColor]
    when (a.cloak) {
        2 -> { // fur collar
            val r = Strands(seedOf(a) + 3)
            for (i in 0 until 70) {
                val t = i / 69f
                val x = c.x + (t * 2f - 1f) * (shoulder + 2f) * s
                val y = top + 2f * s + (1f - (t * 2f - 1f) * (t * 2f - 1f)) * -2f * s + r.range(0f, 3f) * s
                val fur = Color(0xFF7A5B3E).mix(Color(0xFFD9C4A2), r.next() * .6f)
                drawLine(fur, Offset(x, y), Offset(x + r.range(-1.5f, 1.5f) * s, y + r.range(2f, 5f) * s), r.range(.6f, 1.2f) * s, StrokeCap.Round)
            }
        }
        else -> {
            for (side in listOf(-1f, 1f)) {
                drawPath(Path().apply {
                    moveTo(c.x + side * 8f * s, top)
                    cubicTo(c.x + side * (shoulder - 2f) * s, top - 1f * s, c.x + side * (shoulder + 3f) * s, top + 4f * s, c.x + side * (shoulder + 4f) * s, top + 9f * s)
                    lineTo(c.x + side * (shoulder + 1f) * s, top + 6f * s)
                    cubicTo(c.x + side * (shoulder - 4f) * s, top + 2f * s, c.x + side * 12f * s, top + 3f * s, c.x + side * 8f * s, top + 3f * s)
                    close()
                }, Brush.verticalGradient(listOf(color.tint(.15f), color.shade(.3f)), top, top + 9f * s))
            }
            if (a.cloak == 3) for (i in 0..5) drawCircle(ArtPalette.gold.copy(alpha = .8f), .5f * s, Offset(c.x - (shoulder - 2f) * s + i * 2.6f * s, top + 1.2f * s + i * .4f * s))
        }
    }
    // Clasp.
    drawCircle(Brush.radialGradient(listOf(ArtPalette.gold.tint(.4f), ArtPalette.gold.shade(.35f)), Offset(c.x - 9.5f * s, top + 2.5f * s), 2.2f * s), 1.8f * s, Offset(c.x - 9f * s, top + 3f * s))
}

private fun DrawScope.drawHood(a: CharacterAppearance, c: Offset, s: Float) {
    val color = ArtPalette.fabric[a.accentColor].shade(.45f)
    val p = Path().apply {
        moveTo(c.x - 20f * s, c.y + 30f * s)
        cubicTo(c.x - 25f * s, c.y - 4f * s, c.x - 18f * s, c.y - 29f * s, c.x, c.y - 30f * s)
        cubicTo(c.x + 18f * s, c.y - 29f * s, c.x + 25f * s, c.y - 4f * s, c.x + 20f * s, c.y + 30f * s)
        close()
    }
    drawPath(p, Brush.radialGradient(listOf(color.shade(.15f), color.shade(.65f)), Offset(c.x - 6f * s, c.y - 18f * s), 34f * s))
}

// ------------------------------------------------------------ head

private class FaceGeometry(a: CharacterAppearance, gender: Gender, val c: Offset, val s: Float) {
    val female = gender == Gender.FEMALE
    val halfW = (if (female) 14.2f else 15.4f) + (a.cheekbones - 50) / 40f + when (a.faceShape) { 1 -> 1.4f; 2 -> .8f; 4 -> -1.6f; else -> 0f }
    val jawW = halfW * ((if (female) .58f else .7f) + (a.jaw - 50) / 260f + when (a.faceShape) { 2 -> .12f; 3 -> -.12f; 1 -> .06f; 4 -> -.06f; else -> 0f })
    val halfH = 19.5f + when (a.faceShape) { 4 -> 2.2f; 1 -> -1.4f; 3 -> .4f; else -> 0f }
    val chinW = halfW * (if (a.faceShape == 2) .36f else if (a.faceShape == 3) .16f else .24f)
    val top = c.y - halfH * 1.02f * s
    val browY = c.y - halfH * .2f * s
    val eyeY = c.y - halfH * .04f * s
    val noseY = c.y + halfH * .34f * s
    val mouthY = c.y + halfH * .56f * s
    val chinY = c.y + halfH * s
    fun x(f: Float) = c.x + f * halfW * s
}

private fun faceOutline(g: FaceGeometry): Path = Path().apply {
    val s = g.s
    moveTo(g.c.x, g.top)
    cubicTo(g.x(.62f), g.top, g.x(1f), g.top + 6f * s, g.x(1.0f), g.c.y - g.halfH * .25f * s)
    cubicTo(g.x(1.02f), g.c.y + g.halfH * .1f * s, g.c.x + g.halfW * .98f * s, g.c.y + g.halfH * .38f * s, g.c.x + g.jawW * s, g.c.y + g.halfH * .7f * s)
    cubicTo(g.c.x + g.jawW * .8f * s, g.c.y + g.halfH * .9f * s, g.c.x + g.chinW * 1.4f * s, g.chinY - .2f * s, g.c.x, g.chinY)
    cubicTo(g.c.x - g.chinW * 1.4f * s, g.chinY - .2f * s, g.c.x - g.jawW * .8f * s, g.c.y + g.halfH * .9f * s, g.c.x - g.jawW * s, g.c.y + g.halfH * .7f * s)
    cubicTo(g.c.x - g.halfW * .98f * s, g.c.y + g.halfH * .38f * s, g.x(-1.02f), g.c.y + g.halfH * .1f * s, g.x(-1f), g.c.y - g.halfH * .25f * s)
    cubicTo(g.x(-1f), g.top + 6f * s, g.x(-.62f), g.top, g.c.x, g.top)
    close()
}

internal fun DrawScope.drawHead(a: CharacterAppearance, gender: Gender, c: Offset, s: Float, detailed: Boolean) {
    val g = FaceGeometry(a, gender, c, s)
    val tone = ArtPalette.skin[a.skinTone]
    drawEars(a, g, tone, front = false)
    val face = faceOutline(g)
    drawPath(face, Brush.radialGradient(listOf(tone.light, tone.base, tone.shadow), Offset(g.x(-.35f), g.c.y - g.halfH * .35f * s), g.halfW * 2.1f * s))
    clipPath(face) {
        // Form shadow on the far side and under the cheekbones.
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, tone.shadow.copy(alpha = .55f)), g.x(.2f), g.x(1.05f)))
        for (side in listOf(-1f, 1f)) {
            drawOval(Brush.radialGradient(listOf(tone.shadow.copy(alpha = .3f), Color.Transparent), Offset(g.x(side * .62f), g.noseY + 1.5f * s), 5.5f * s),
                Offset(g.x(side * .62f) - 5.5f * s, g.noseY - 4f * s), Size(11f * s, 11f * s))
            drawCircle(Brush.radialGradient(listOf(tone.blush.copy(alpha = if (g.female) .38f else .18f), Color.Transparent), Offset(g.x(side * .55f), g.noseY - 1f * s), 5f * s),
                5f * s, Offset(g.x(side * .55f), g.noseY - 1f * s))
        }
        // Jaw and chin shading.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, tone.shadow.copy(alpha = .35f)), g.mouthY, g.chinY))
        if (a.beard == 1 || (a.beard >= 2 && !g.female)) stubble(a, g)
    }
    drawEars(a, g, tone, front = true)
    drawEyes(a, g, tone, detailed)
    drawBrows(a, g)
    drawNose(a, g, tone)
    drawMouth(a, g, tone)
    drawMarks(a, g, tone)
    if (a.beard >= 2) drawBeard(a, g)
    drawFrontHair(a, g, gender)
    if (a.jewelry == 4) drawDiadem(a, g)
    if (a.jewelry == 3) for (side in listOf(-1f, 1f)) {
        val ear = Offset(g.x(side * 1.03f), g.noseY + 1f * s)
        drawLine(ArtPalette.gold, ear, ear + Offset(0f, 2.2f * s), .35f * s)
        drawCircle(Brush.radialGradient(listOf(ArtPalette.gold.tint(.5f), ArtPalette.gold.shade(.3f)), ear + Offset(-.3f * s, 2.8f * s), 1.2f * s), .9f * s, ear + Offset(0f, 3f * s))
    }
}

private fun DrawScope.stubble(a: CharacterAppearance, g: FaceGeometry) {
    val r = Strands(seedOf(a) + 11)
    val color = ArtPalette.hair[a.hairColor].copy(alpha = .13f)
    repeat(220) {
        val t = r.range(-1f, 1f)
        val x = g.c.x + t * g.jawW * 1.05f * g.s
        val yTop = g.noseY + 3.5f * g.s
        val y = r.range(yTop, g.chinY)
        if (kotlin.math.abs(x - g.c.x) < 3.4f * g.s && y in (g.mouthY - 1.5f * g.s)..(g.mouthY + 2f * g.s)) return@repeat
        drawCircle(color, r.range(.12f, .24f) * g.s, Offset(x, y))
    }
}

private fun DrawScope.drawEars(a: CharacterAppearance, g: FaceGeometry, tone: ArtPalette.Tone, front: Boolean) {
    if (front) return
    val s = g.s
    for (side in listOf(-1f, 1f)) {
        val bx = g.x(side * .97f)
        val p = Path()
        when (a.ears) {
            0 -> {
                p.moveTo(bx, g.eyeY - 1f * s)
                p.cubicTo(bx + side * 4.2f * s, g.eyeY - 3f * s, bx + side * 4.6f * s, g.noseY + 2f * s, bx + side * .4f * s, g.noseY + 3.5f * s)
                p.close()
            }
            else -> {
                val len = if (a.ears == 2) 9.5f else 5.5f
                p.moveTo(bx, g.eyeY)
                p.cubicTo(bx + side * 3f * s, g.eyeY - 2f * s, bx + side * (len * .7f) * s, g.eyeY - len * .6f * s, bx + side * len * s, g.eyeY - len * .95f * s)
                p.cubicTo(bx + side * (len * .6f) * s, g.eyeY + 1f * s, bx + side * 4.6f * s, g.noseY + 2f * s, bx + side * .4f * s, g.noseY + 3.5f * s)
                p.close()
            }
        }
        drawPath(p, Brush.radialGradient(listOf(tone.base.mix(tone.blush, .25f), tone.shadow), Offset(bx + side * 1.5f * s, g.eyeY + 2f * s), 9f * s))
        drawPath(Path().apply {
            moveTo(bx + side * 1.2f * s, g.eyeY + 1f * s)
            cubicTo(bx + side * 3f * s, g.eyeY, bx + side * 3f * s, g.noseY, bx + side * 1f * s, g.noseY + 1.5f * s)
        }, tone.shadow.shade(.2f).copy(alpha = .6f), style = Stroke(.45f * s, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEyes(a: CharacterAppearance, g: FaceGeometry, tone: ArtPalette.Tone, detailed: Boolean) {
    val s = g.s
    val ew = g.halfW * .3f * s * (if (a.eyeShape == 2) 1.05f else 1f)
    val eh = ew * when (a.eyeShape) { 1 -> .62f; 2 -> .36f; else -> .48f }
    val tilt = if (a.eyeShape == 3) .9f * s else 0f
    val iris = ArtPalette.eyes[a.eyeColor]
    val squint = when (a.expression) { 1 -> .85f; 3 -> .82f; else -> 1f }
    for (side in listOf(-1f, 1f)) {
        val cx = g.x(side * .42f)
        val cy = g.eyeY
        val inner = Offset(cx - side * ew, cy + tilt * .4f)
        val outer = Offset(cx + side * ew, cy - tilt)
        // Socket shadow.
        drawOval(Brush.radialGradient(listOf(tone.shadow.copy(alpha = .4f), Color.Transparent), Offset(cx, cy - eh * .6f), ew * 1.5f),
            Offset(cx - ew * 1.6f, cy - eh * 2.6f), Size(ew * 3.2f, eh * 4f))
        val eye = Path().apply {
            moveTo(inner.x, inner.y)
            cubicTo(cx - side * ew * .45f, cy - eh * 1.5f * squint, cx + side * ew * .55f, cy - eh * 1.45f * squint - tilt * .5f, outer.x, outer.y)
            cubicTo(cx + side * ew * .5f, cy + eh * .95f, cx - side * ew * .4f, cy + eh * 1.05f, inner.x, inner.y)
            close()
        }
        drawPath(eye, Brush.verticalGradient(listOf(Color(0xFFD9D2C8), Color(0xFFF4EFE8)), cy - eh, cy + eh))
        clipPath(eye) {
            val ir = eh * 1.05f
            val ic = Offset(cx + side * ew * .05f, cy - eh * .05f)
            drawCircle(Brush.radialGradient(listOf(iris.tint(.35f), iris, iris.shade(.55f)), ic, ir), ir, ic)
            if (detailed) for (k in 0 until 12) {
                val ang = k / 12f * 6.283f
                drawLine(iris.shade(.4f).copy(alpha = .5f), ic + Offset(cos(ang) * ir * .35f, sin(ang) * ir * .35f), ic + Offset(cos(ang) * ir * .95f, sin(ang) * ir * .95f), .12f * s)
            }
            drawCircle(Color(0xFF0C0A09), ir * .42f, ic)
            drawCircle(Color.Black.copy(alpha = .35f), ir * 1.4f, ic + Offset(0f, -ir * 1.3f))
            drawCircle(Color.White.copy(alpha = .9f), ir * .2f, ic + Offset(-ir * .35f, -ir * .35f))
        }
        // Upper lid line, lashes and crease.
        val lid = Path().apply {
            moveTo(inner.x, inner.y)
            cubicTo(cx - side * ew * .45f, cy - eh * 1.5f * squint, cx + side * ew * .55f, cy - eh * 1.45f * squint - tilt * .5f, outer.x, outer.y)
        }
        drawPath(lid, Color(0xFF1C1310), style = Stroke((if (g.female) .85f else .6f) * s, cap = StrokeCap.Round))
        if (g.female && detailed) for (k in 0..3) {
            val t = .55f + k * .13f
            val px = cx - side * ew + side * ew * 2f * t
            val py = cy - eh * 1.25f * squint * (1f - (t - .5f) * (t - .5f) * 2.5f) - tilt * t
            drawLine(Color(0xFF1C1310), Offset(px, py), Offset(px + side * .9f * s, py - .9f * s), .3f * s, StrokeCap.Round)
        }
        drawPath(Path().apply {
            moveTo(inner.x + side * ew * .2f, cy - eh * 1.7f)
            cubicTo(cx, cy - eh * 2.5f, cx + side * ew * .6f, cy - eh * 2.3f, outer.x, outer.y - eh * 1.1f)
        }, tone.shadow.copy(alpha = .55f), style = Stroke(.35f * s, cap = StrokeCap.Round))
        drawPath(Path().apply {
            moveTo(inner.x + side * ew * .3f, cy + eh * .9f)
            cubicTo(cx, cy + eh * 1.3f, cx + side * ew * .5f, cy + eh * 1.2f, outer.x, outer.y)
        }, tone.shadow.copy(alpha = .45f), style = Stroke(.3f * s, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawBrows(a: CharacterAppearance, g: FaceGeometry) {
    val s = g.s
    val color = ArtPalette.hair[a.hairColor].let { if (a.hairColor == 5) it.shade(.35f) else it.shade(.1f) }
    val thick = when (a.browStyle) { 2 -> 1.5f; 3 -> .7f; else -> 1f } * (if (g.female) .85f else 1.1f)
    val arch = when (a.browStyle) { 1 -> 1.9f; 0 -> .5f; 2 -> .9f; else -> 1.3f }
    val lift = when (a.expression) { 2 -> -.8f; 3 -> -1.1f; 1 -> .5f; else -> 0f }
    val r = Strands(seedOf(a) + 5)
    for (side in listOf(-1f, 1f)) {
        val x0 = g.x(side * .14f)
        val x1 = g.x(side * .8f)
        val y0 = g.browY + (if (a.expression == 3) .8f else 0f) * s
        fun py(t: Float) = y0 - sin(t * 3.1416f) * arch * s - lift * (1f - t) * s * .6f + t * .8f * s
        val shape = Path().apply {
            moveTo(x0, py(0f) + thick * .9f * s)
            for (k in 1..10) { val t = k / 10f; lineTo(x0 + (x1 - x0) * t, py(t) + thick * (.9f - t * .65f) * s) }
            for (k in 10 downTo 0) { val t = k / 10f; lineTo(x0 + (x1 - x0) * t, py(t) - thick * (.55f - t * .4f) * s) }
            close()
        }
        drawPath(shape, color.copy(alpha = .72f))
        for (k in 0 until 22) {
            val t = r.next()
            val x = x0 + (x1 - x0) * t
            val y = py(t)
            drawLine(color.shade(.2f).copy(alpha = .55f), Offset(x, y + thick * .5f * s), Offset(x + side * r.range(.8f, 1.6f) * s, y - thick * .4f * s), .18f * s, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawNose(a: CharacterAppearance, g: FaceGeometry, tone: ArtPalette.Tone) {
    val s = g.s
    val size = .82f + a.noseSize / 200f
    val wide = when (a.noseShape) { 3 -> 1.3f; 2 -> .9f; else -> 1f }
    val tipY = g.noseY + (if (a.noseShape == 2) -1f else 0f) * s
    val bridge = Path().apply {
        moveTo(g.c.x + 1.2f * s, g.eyeY + 1f * s)
        if (a.noseShape == 1) cubicTo(g.c.x + 3.4f * s, g.eyeY + 5f * s, g.c.x + 3f * s, tipY - 3f * s, g.c.x + 2.4f * s * size, tipY)
        else cubicTo(g.c.x + 2.2f * s, g.eyeY + 5f * s, g.c.x + 2.6f * s, tipY - 3f * s, g.c.x + 2.4f * s * size, tipY)
    }
    drawPath(bridge, tone.shadow.copy(alpha = .55f), style = Stroke(1.1f * s, cap = StrokeCap.Round))
    // Underside shadow, nostrils and tip light.
    drawOval(Brush.radialGradient(listOf(tone.shadow.copy(alpha = .55f), Color.Transparent), Offset(g.c.x, tipY + 1.6f * s), 4.5f * s * wide),
        Offset(g.c.x - 4.5f * s * wide, tipY - .6f * s), Size(9f * s * wide, 4.4f * s))
    for (side in listOf(-1f, 1f)) drawOval(Color(0xFF2A1612).copy(alpha = .55f), Offset(g.c.x + side * 1.8f * s * wide - .9f * s, tipY + .7f * s), Size(1.8f * s, .9f * s))
    drawPath(Path().apply {
        moveTo(g.c.x - 3.3f * s * wide, tipY + .4f * s)
        cubicTo(g.c.x - 3.8f * s * wide, tipY - 1.6f * s, g.c.x - 1.6f * s, tipY - 1.2f * s, g.c.x - 1.4f * s, tipY + .4f * s)
    }, tone.shadow.copy(alpha = .6f), style = Stroke(.4f * s, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(g.c.x + 3.3f * s * wide, tipY + .4f * s)
        cubicTo(g.c.x + 3.8f * s * wide, tipY - 1.6f * s, g.c.x + 1.6f * s, tipY - 1.2f * s, g.c.x + 1.4f * s, tipY + .4f * s)
    }, tone.shadow.copy(alpha = .7f), style = Stroke(.45f * s, cap = StrokeCap.Round))
    drawCircle(Brush.radialGradient(listOf(tone.light.copy(alpha = .75f), Color.Transparent), Offset(g.c.x - .6f * s, tipY - 1.2f * s), 1.8f * s), 1.8f * s, Offset(g.c.x - .6f * s, tipY - 1.2f * s))
    drawLine(tone.light.copy(alpha = .35f), Offset(g.c.x - .4f * s, g.eyeY + 3f * s), Offset(g.c.x - .5f * s, tipY - 2.5f * s), .8f * s, StrokeCap.Round)
}

private fun DrawScope.drawMouth(a: CharacterAppearance, g: FaceGeometry, tone: ArtPalette.Tone) {
    val s = g.s
    val mw = g.halfW * (.34f + a.mouthWidth / 420f) * s
    val full = .7f + a.lips / 140f + if (g.female) .15f else 0f
    val curve = when (a.expression) { 1 -> 1.5f; 2 -> -.5f; 3 -> -.2f; else -> .25f } * s
    val y = g.mouthY
    val lipColor = tone.lip.mix(tone.base, if (g.female) .05f else .35f)
    val upper = Path().apply {
        moveTo(g.c.x - mw, y - curve)
        cubicTo(g.c.x - mw * .6f, y - 1.0f * full * s, g.c.x - mw * .25f, y - 1.25f * full * s, g.c.x, y - .85f * full * s)
        cubicTo(g.c.x + mw * .25f, y - 1.25f * full * s, g.c.x + mw * .6f, y - 1.0f * full * s, g.c.x + mw, y - curve)
        cubicTo(g.c.x + mw * .4f, y + .2f * s, g.c.x - mw * .4f, y + .2f * s, g.c.x - mw, y - curve)
        close()
    }
    val lower = Path().apply {
        moveTo(g.c.x - mw * .92f, y - curve * .9f)
        cubicTo(g.c.x - mw * .4f, y + .3f * s, g.c.x + mw * .4f, y + .3f * s, g.c.x + mw * .92f, y - curve * .9f)
        cubicTo(g.c.x + mw * .5f, y + 1.8f * full * s, g.c.x - mw * .5f, y + 1.8f * full * s, g.c.x - mw * .92f, y - curve * .9f)
        close()
    }
    drawPath(upper, Brush.verticalGradient(listOf(lipColor.shade(.02f), lipColor.shade(.14f)), y - 2f * s, y))
    drawPath(lower, Brush.verticalGradient(listOf(lipColor.shade(.1f), lipColor.tint(.1f), lipColor.shade(.2f)), y, y + 2.4f * full * s))
    drawOval(Color.White.copy(alpha = .18f), Offset(g.c.x - mw * .3f, y + .5f * full * s), Size(mw * .5f, .8f * s))
    drawPath(Path().apply {
        moveTo(g.c.x - mw * 1.02f, y - curve * 1.1f)
        cubicTo(g.c.x - mw * .4f, y + .25f * s, g.c.x + mw * .4f, y + .25f * s, g.c.x + mw * 1.02f, y - curve * 1.1f)
    }, Color(0xFF3A1A16).copy(alpha = .6f), style = Stroke(.3f * s, cap = StrokeCap.Round))
    // Shadow below the lower lip.
    drawOval(Brush.radialGradient(listOf(tone.shadow.copy(alpha = .35f), Color.Transparent), Offset(g.c.x, y + 3.6f * s), 3.5f * s),
        Offset(g.c.x - 3.5f * s, y + 2.4f * s), Size(7f * s, 2.6f * s))
    if (a.expression == 1) for (side in listOf(-1f, 1f))
        drawLine(tone.shadow.copy(alpha = .4f), Offset(g.c.x + side * (mw + .6f * s), y - curve - .6f * s), Offset(g.c.x + side * (mw + 1.4f * s), y - curve + 1.2f * s), .3f * s, StrokeCap.Round)
}

private fun DrawScope.drawMarks(a: CharacterAppearance, g: FaceGeometry, tone: ArtPalette.Tone) {
    val s = g.s
    val scarColor = tone.base.mix(Color(0xFFE8B4A8), .5f)
    fun scar(from: Offset, to: Offset) {
        drawLine(tone.shadow.copy(alpha = .6f), from + Offset(.25f * s, .25f * s), to + Offset(.25f * s, .25f * s), .9f * s, StrokeCap.Round)
        drawLine(scarColor, from, to, .6f * s, StrokeCap.Round)
        for (k in 1..3) {
            val p = Offset(from.x + (to.x - from.x) * k / 4f, from.y + (to.y - from.y) * k / 4f)
            drawLine(scarColor.copy(alpha = .7f), p + Offset(-.8f * s, .5f * s), p + Offset(.8f * s, -.5f * s), .25f * s)
        }
    }
    when (a.scar) {
        1 -> scar(Offset(g.x(.42f), g.eyeY + 4f * s), Offset(g.x(.75f), g.mouthY - 1f * s))
        2 -> scar(Offset(g.x(-.52f), g.browY - 3f * s), Offset(g.x(-.34f), g.eyeY + 4.5f * s))
        3 -> scar(Offset(g.x(-.2f), g.top + 6f * s), Offset(g.x(.25f), g.browY - 2.5f * s))
        4 -> scar(Offset(g.x(.12f), g.mouthY + 3f * s), Offset(g.x(.32f), g.chinY - .5f * s))
    }
    val ink = Color(0xFF1E3A44).copy(alpha = .7f)
    when (a.tattoo) {
        1 -> for (k in 0..2) drawLine(ink, Offset(g.x(.62f), g.eyeY + (3f + k * 2.2f) * s), Offset(g.x(.9f), g.eyeY + (1.8f + k * 2.2f) * s), .55f * s, StrokeCap.Round)
        2 -> drawPath(Path().apply {
            moveTo(g.x(-.85f), g.browY - 1f * s)
            cubicTo(g.x(-1f), g.eyeY + 4f * s, g.x(-.6f), g.eyeY + 6f * s, g.x(-.75f), g.noseY + 3f * s)
            moveTo(g.x(-.92f), g.eyeY + 2f * s)
            cubicTo(g.x(-.75f), g.eyeY + 1f * s, g.x(-.7f), g.eyeY + 3f * s, g.x(-.8f), g.eyeY + 4f * s)
        }, Color(0xFF3E6B4E).copy(alpha = .75f), style = Stroke(.45f * s, cap = StrokeCap.Round))
        3 -> {
            val m = Offset(g.c.x, g.browY - 4.5f * s)
            drawRect(ink, m - Offset(1.4f * s, 1.4f * s), Size(2.8f * s, 2.8f * s), style = Stroke(.4f * s))
            drawLine(ink, m - Offset(0f, 2.4f * s), m + Offset(0f, 2.4f * s), .35f * s)
        }
    }
}

private fun DrawScope.drawBeard(a: CharacterAppearance, g: FaceGeometry) {
    val s = g.s
    val base = ArtPalette.hair[a.hairColor]
    val r = Strands(seedOf(a) + 17)
    when (a.beard) {
        2 -> { // goatee + moustache
            val p = Path().apply {
                moveTo(g.c.x - 3.6f * s, g.mouthY + 1.4f * s)
                cubicTo(g.c.x - 3.8f * s, g.chinY - 1f * s, g.c.x - 1.5f * s, g.chinY + 2.6f * s, g.c.x, g.chinY + 3f * s)
                cubicTo(g.c.x + 1.5f * s, g.chinY + 2.6f * s, g.c.x + 3.8f * s, g.chinY - 1f * s, g.c.x + 3.6f * s, g.mouthY + 1.4f * s)
                cubicTo(g.c.x + 1.5f * s, g.mouthY + 2.6f * s, g.c.x - 1.5f * s, g.mouthY + 2.6f * s, g.c.x - 3.6f * s, g.mouthY + 1.4f * s)
                close()
            }
            drawPath(p, Brush.verticalGradient(listOf(base.tint(.08f), base.shade(.35f)), g.mouthY, g.chinY + 3f * s))
            moustache(a, g, base)
        }
        3, 4 -> { // full / long beard
            val drop = if (a.beard == 4) 11f else 3.5f
            val p = Path().apply {
                moveTo(g.x(-.96f), g.eyeY + 3f * s)
                cubicTo(g.x(-.95f), g.noseY + 4f * s, g.c.x - g.jawW * s, g.c.y + g.halfH * .78f * s, g.c.x - g.chinW * 2f * s, g.chinY + drop * .6f * s)
                cubicTo(g.c.x - 2f * s, g.chinY + drop * s, g.c.x + 2f * s, g.chinY + drop * s, g.c.x + g.chinW * 2f * s, g.chinY + drop * .6f * s)
                cubicTo(g.c.x + g.jawW * s, g.c.y + g.halfH * .78f * s, g.x(.95f), g.noseY + 4f * s, g.x(.96f), g.eyeY + 3f * s)
                cubicTo(g.x(.8f), g.noseY + 3f * s, g.x(.5f), g.noseY + 4.5f * s, g.c.x + 4f * s, g.mouthY + 1.5f * s)
                cubicTo(g.c.x + 2f * s, g.mouthY + 3.2f * s, g.c.x - 2f * s, g.mouthY + 3.2f * s, g.c.x - 4f * s, g.mouthY + 1.5f * s)
                cubicTo(g.x(-.5f), g.noseY + 4.5f * s, g.x(-.8f), g.noseY + 3f * s, g.x(-.96f), g.eyeY + 3f * s)
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(base.tint(.1f), base, base.shade(.45f)), Offset(g.x(-.3f), g.mouthY), g.halfW * 1.6f * s))
            clipPath(p) {
                repeat(170) {
                    val x = g.c.x + r.range(-1f, 1f) * g.halfW * s
                    val y = r.range(g.noseY, g.chinY + drop * s)
                    val col = if (r.next() < .5f) base.tint(.22f) else base.shade(.35f)
                    drawLine(col.copy(alpha = .55f), Offset(x, y), Offset(x + r.range(-.6f, .6f) * s, y + r.range(1.5f, 3.2f) * s), .22f * s, StrokeCap.Round)
                }
            }
            moustache(a, g, base)
        }
    }
}

private fun DrawScope.moustache(a: CharacterAppearance, g: FaceGeometry, base: Color) {
    val s = g.s
    for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(g.c.x, g.mouthY - 2.4f * s)
        cubicTo(g.c.x + side * 2.5f * s, g.mouthY - 3.2f * s, g.c.x + side * 5f * s, g.mouthY - 2f * s, g.c.x + side * 5.4f * s, g.mouthY + .6f * s)
        cubicTo(g.c.x + side * 3.4f * s, g.mouthY - 1f * s, g.c.x + side * 1.6f * s, g.mouthY - 1.2f * s, g.c.x, g.mouthY - 1.2f * s)
        close()
    }, Brush.verticalGradient(listOf(base.tint(.1f), base.shade(.3f)), g.mouthY - 3f * s, g.mouthY + .6f * s))
}

// ------------------------------------------------------------ hair

private fun hairColors(a: CharacterAppearance): Triple<Color, Color, Color> {
    val base = ArtPalette.hair[a.hairColor]
    return Triple(base, base.shade(.45f), base.tint(if (a.hairColor == 5) .3f else .28f))
}

private fun DrawScope.drawBackHair(a: CharacterAppearance, c: Offset, s: Float, gender: Gender) {
    val (base, dark, light) = hairColors(a)
    val length = 22f + a.hairLength * .5f
    val width = if (a.hairStyle == 6) 24f else 19.5f
    val r = Strands(seedOf(a) + 23)
    if (a.hairStyle == 3 || a.hairStyle == 5) {
        // Braid(s) over the shoulder.
        val sides = if (a.hairStyle == 5) listOf(-1f, 1f) else listOf(1f)
        for (side in sides) {
            var y = c.y + 6f * s
            val x = c.x + side * 15f * s
            while (y < c.y + 6f * s + length * s) {
                drawOval(Brush.radialGradient(listOf(light, base, dark), Offset(x - 1f * s, y), 3f * s), Offset(x - 2.4f * s, y - 1.6f * s), Size(4.8f * s, 3.6f * s))
                y += 3.1f * s
            }
            drawCircle(ArtPalette.leather, 1.1f * s, Offset(x, y))
        }
        return
    }
    val p = Path().apply {
        moveTo(c.x - width * s, c.y - 6f * s)
        cubicTo(c.x - (width + 3f) * s, c.y + length * .4f * s, c.x - (width + 1f) * s, c.y + length * .8f * s, c.x - (width - 4f) * s, c.y + length * s)
        cubicTo(c.x - 6f * s, c.y + (length + 2f) * s, c.x + 6f * s, c.y + (length + 2f) * s, c.x + (width - 4f) * s, c.y + length * s)
        cubicTo(c.x + (width + 1f) * s, c.y + length * .8f * s, c.x + (width + 3f) * s, c.y + length * .4f * s, c.x + width * s, c.y - 6f * s)
        close()
    }
    drawPath(p, Brush.horizontalGradient(listOf(base, dark, dark.shade(.3f)), c.x - width * s, c.x + width * s))
    clipPath(p) {
        repeat(120) {
            val x = c.x + r.range(-width, width) * s
            val y0 = c.y + r.range(-6f, length * .5f) * s
            val wave = when (a.hairTexture) { 1 -> 2.4f; 2 -> 4f; else -> .6f } * s
            val col = if (r.next() < .45f) light.copy(alpha = .35f) else dark.copy(alpha = .45f)
            drawPath(Path().apply {
                moveTo(x, y0)
                cubicTo(x + wave, y0 + length * .25f * s, x - wave, y0 + length * .5f * s, x + r.range(-1f, 1f) * s, y0 + length * .7f * s)
            }, col, style = Stroke(r.range(.25f, .5f) * s, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawFrontHair(a: CharacterAppearance, g: FaceGeometry, gender: Gender) {
    if (a.hairStyle == 7) {
        // Shaved head: a soft sheen on the scalp.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .25f), Color.Transparent), Offset(g.x(-.25f), g.top + 4f * g.s), 6f * g.s), 6f * g.s, Offset(g.x(-.25f), g.top + 4f * g.s))
        return
    }
    val s = g.s
    val (base, dark, light) = hairColors(a)
    val volume = when (a.hairStyle) { 6 -> 3.6f; 0 -> .6f; 4 -> .4f; else -> 1.6f } + a.hairTexture * .7f
    val fringe = when (a.hairStyle) { 0 -> .32f; 1 -> .5f; 6 -> .62f; 2 -> .3f; else -> .22f } + a.hairLength / 600f
    val hairline = g.top + g.halfH * fringe * s
    val parting = if (a.hairStyle == 1) .35f else 0f
    val sideBottom = if (a.hairStyle == 0 || a.hairStyle == 1) g.browY + 3f * s else g.eyeY
    val cap = Path().apply {
        moveTo(g.x(-1.07f), sideBottom)
        cubicTo(g.x(-1.2f) - volume * s, g.top - 2f * s, g.x(-.6f), g.top - (4f + volume) * s, g.c.x, g.top - (4.2f + volume) * s)
        cubicTo(g.x(.6f), g.top - (4f + volume) * s, g.x(1.2f) + volume * s, g.top - 2f * s, g.x(1.07f), sideBottom)
        cubicTo(g.x(1f), g.browY - 2f * s, g.x(.9f), hairline, g.x(.55f + parting * .2f), hairline + 1.5f * s)
        cubicTo(g.x(.25f), hairline - 1f * s, g.x(parting), hairline - 2.4f * s, g.x(parting - .2f), hairline + .6f * s)
        cubicTo(g.x(-.4f), hairline + 2.4f * s, g.x(-.8f), hairline, g.x(-1f), g.browY - 2f * s)
        close()
    }
    drawPath(cap, Brush.linearGradient(listOf(light, base, dark), Offset(g.x(-.8f), g.top - 4f * s), Offset(g.x(.9f), g.eyeY)))
    val r = Strands(seedOf(a) + 31)
    clipPath(cap) {
        val crown = Offset(g.x(parting), g.top - 2f * s)
        repeat(150) {
            val t = r.range(-1.15f, 1.15f)
            val end = Offset(g.x(t), if (kotlin.math.abs(t) > .9f) g.eyeY + r.range(-4f, 2f) * s else hairline + r.range(-1f, 2.5f) * s)
            val wave = when (a.hairTexture) { 1 -> 2f; 2 -> 3.6f; else -> .5f } * s * (if (r.next() < .5f) -1f else 1f)
            val mid = Offset((crown.x + end.x) / 2f + wave, (crown.y + end.y) / 2f - 2f * s)
            val col = when { r.next() < .3f -> light.copy(alpha = .55f); r.next() < .6f -> dark.copy(alpha = .5f); else -> base.tint(.1f).copy(alpha = .4f) }
            drawPath(Path().apply { moveTo(crown.x + r.range(-3f, 3f) * s, crown.y + r.range(0f, 2f) * s); cubicTo(mid.x, mid.y, mid.x - wave * .5f, end.y - 2f * s, end.x, end.y) },
                col, style = Stroke(r.range(.22f, .5f) * s, cap = StrokeCap.Round))
        }
        // Sheen band.
        drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), Offset(g.x(-.35f), g.top), 8f * s),
            Offset(g.x(-.35f) - 9f * s, g.top - 3f * s), Size(18f * s, 6f * s))
    }
    if (a.hairStyle == 4) {
        val bun = Offset(g.c.x + 2f * s, g.top - 5.5f * s)
        drawCircle(Brush.radialGradient(listOf(light, base, dark), bun + Offset(-1.5f * s, -1.5f * s), 6.5f * s), 5.4f * s, bun)
        drawArc(dark, 200f, 140f, false, bun - Offset(4f * s, 4f * s), Size(8f * s, 8f * s), style = Stroke(.4f * s))
    }
    if (a.hairStyle == 2 || a.hairStyle == 6) {
        // Locks framing the face.
        for (side in listOf(-1f, 1f)) drawPath(Path().apply {
            moveTo(g.x(side * 1.02f), g.browY - 3f * s)
            cubicTo(g.x(side * 1.18f), g.eyeY + 6f * s, g.x(side * 1.12f), g.mouthY, g.x(side * 1.22f), g.mouthY + (4f + a.hairLength / 10f) * s)
            lineTo(g.x(side * 1.02f), g.mouthY)
            cubicTo(g.x(side * .98f), g.eyeY + 4f * s, g.x(side * .96f), g.browY, g.x(side * .9f), g.browY - 3f * s)
            close()
        }, Brush.horizontalGradient(if (side < 0) listOf(light, base) else listOf(base, dark), g.x(side * 1.2f), g.x(side * .9f)))
    }
}

private fun DrawScope.drawDiadem(a: CharacterAppearance, g: FaceGeometry) {
    val s = g.s
    drawPath(Path().apply {
        moveTo(g.x(-.95f), g.browY - 4f * s)
        cubicTo(g.x(-.4f), g.browY - 8f * s, g.x(.4f), g.browY - 8f * s, g.x(.95f), g.browY - 4f * s)
    }, ArtPalette.gold, style = Stroke(.6f * s, cap = StrokeCap.Round))
    val gem = Offset(g.c.x, g.browY - 6.6f * s)
    drawCircle(Brush.radialGradient(listOf(ArtPalette.fabric[a.accentColor].tint(.6f), ArtPalette.fabric[a.accentColor].shade(.2f)), gem - Offset(.3f * s, .3f * s), 1.4f * s), 1.1f * s, gem)
}

// ------------------------------------------------------------ full figure (editor and tavern)

/**
 * Full figure standing on [feet]. [unit] is roughly 1/128 of the figure box height. In the
 * tavern the same function draws every person at small size, so looks stay recognisable.
 */
internal fun DrawScope.drawFullFigure(a: CharacterAppearance, gender: Gender, feet: Offset, unit: Float, detailed: Boolean, walkPhase: Float = 0f) {
    val female = gender == Gender.FEMALE
    val heightScale = .86f + a.height / 100f * .2f + if (female) -.03f else 0f
    val u = unit * heightScale
    val widthScale = .9f + a.build * .07f + (a.muscle - 50) / 500f
    val fabric = ArtPalette.fabric[a.primaryColor]
    val accent = ArtPalette.fabric[a.accentColor]
    val tone = ArtPalette.skin[a.skinTone]
    val lean = when (a.posture) { 1 -> 1.2f; 3 -> -1.5f; else -> 0f }
    val swing = sin(walkPhase * 6.283f)
    val hip = Offset(feet.x, feet.y - 56f * u)
    val shoulderY = feet.y - 94f * u
    val sw = (if (female) 13f else 15.5f) * widthScale
    val hw = (if (female) 12f else 11f) * widthScale
    // Shadow on the floor.
    drawOval(Color.Black.copy(alpha = .35f), Offset(feet.x - 16f * u, feet.y - 3f * u), Size(32f * u, 6f * u))
    if (a.cloak in 1..3) {
        val cloak = if (a.cloak == 2) Color(0xFF4A3524) else accent.shade(.2f)
        drawPath(Path().apply {
            moveTo(feet.x - sw * u, shoulderY)
            cubicTo(feet.x - (sw + 6f) * u, shoulderY + 30f * u, feet.x - (sw + 8f) * u, feet.y - 20f * u, feet.x - (sw + 6f) * u, feet.y - 6f * u)
            lineTo(feet.x + (sw + 6f) * u, feet.y - 6f * u)
            cubicTo(feet.x + (sw + 8f) * u, feet.y - 20f * u, feet.x + (sw + 6f) * u, shoulderY + 30f * u, feet.x + sw * u, shoulderY)
            close()
        }, Brush.horizontalGradient(listOf(cloak.tint(.05f), cloak.shade(.5f)), feet.x - sw * u, feet.x + sw * u))
    }
    if (a.weapon == 1) drawLine(ArtPalette.leather.tint(.2f), Offset(feet.x + 10f * u, shoulderY - 6f * u), Offset(feet.x - 12f * u, hip.y + 4f * u), 1.6f * u, StrokeCap.Round)
    // Legs (or skirt).
    val legColor = if (a.clothing == 2) fabric.shade(.1f) else Color(0xFF3A2E26).mix(fabric, .25f)
    if (a.clothing == 2) {
        drawPath(Path().apply {
            moveTo(hip.x - hw * u, hip.y - 6f * u)
            lineTo(hip.x - (hw + 7f) * u, feet.y - 6f * u)
            cubicTo(hip.x - 4f * u, feet.y - 3f * u, hip.x + 4f * u, feet.y - 3f * u, hip.x + (hw + 7f) * u, feet.y - 6f * u)
            lineTo(hip.x + hw * u, hip.y - 6f * u)
            close()
        }, Brush.horizontalGradient(listOf(fabric.tint(.1f), fabric.shade(.45f)), hip.x - hw * u, hip.x + hw * u))
    } else for (side in listOf(-1f, 1f)) {
        val step = side * swing * 4f * u
        drawLine(legColor.shade(if (side > 0) .25f else 0f), Offset(hip.x + side * 4.5f * u, hip.y), Offset(hip.x + side * 5f * u + step, feet.y - 7f * u), 7f * u * widthScale, StrokeCap.Round)
    }
    for (side in listOf(-1f, 1f)) {
        val step = if (a.clothing == 2) 0f else side * swing * 4f * u
        drawRoundRect(Color(0xFF2A1D14), Offset(hip.x + side * 5f * u + step - 4.2f * u, feet.y - 9f * u), Size(8.4f * u, 8f * u), androidx.compose.ui.geometry.CornerRadius(2f * u))
    }
    // Torso.
    val torso = Path().apply {
        moveTo(feet.x - sw * u + lean * u, shoulderY)
        lineTo(feet.x + sw * u + lean * u, shoulderY)
        cubicTo(feet.x + (sw + 1f) * u, shoulderY + 14f * u, feet.x + hw * u, hip.y - 14f * u, feet.x + hw * u, hip.y + 2f * u)
        lineTo(feet.x - hw * u, hip.y + 2f * u)
        cubicTo(feet.x - hw * u, hip.y - 14f * u, feet.x - (sw + 1f) * u, shoulderY + 14f * u, feet.x - sw * u + lean * u, shoulderY)
        close()
    }
    val armorColor = when (a.armor) { 1 -> ArtPalette.leather; 2, 4 -> ArtPalette.metal; 3 -> ArtPalette.metal.mix(accent, .35f); else -> fabric }
    drawPath(torso, Brush.linearGradient(listOf(armorColor.tint(.25f), armorColor, armorColor.shade(.5f)), Offset(feet.x - sw * u, shoulderY), Offset(feet.x + sw * u, hip.y)))
    if (a.armor == 0 && a.clothing == 1) drawLine(ArtPalette.leather.tint(.3f), Offset(feet.x, shoulderY + 4f * u), Offset(feet.x, hip.y), 1f * u)
    if (a.armor == 4) drawLine(Color.White.copy(alpha = .4f), Offset(feet.x - .5f * u, shoulderY + 6f * u), Offset(feet.x - .5f * u, hip.y - 4f * u), 1f * u)
    // Belt.
    drawRect(ArtPalette.leather.shade(.2f), Offset(feet.x - hw * u, hip.y - 4f * u), Size(hw * 2f * u, 4f * u))
    drawRect(ArtPalette.gold, Offset(feet.x - 1.8f * u, hip.y - 4f * u), Size(3.6f * u, 4f * u))
    // Arms.
    for (side in listOf(-1f, 1f)) {
        val sh = Offset(feet.x + side * (sw - 1f) * u + lean * u, shoulderY + 3f * u)
        val hand = Offset(feet.x + side * (sw + 2.5f) * u - side * swing * 2f * u, hip.y + 4f * u)
        drawLine((if (a.armor >= 2) ArtPalette.metal else fabric).shade(if (side > 0) .3f else .05f), sh, hand, 5.6f * u * widthScale, StrokeCap.Round)
        drawCircle(tone.base, 2.6f * u, hand)
        if (a.armor == 4 || a.armor == 1) drawCircle(armorColor.tint(.2f), 4.6f * u, sh)
    }
    // Weapon.
    when (a.weapon) {
        0 -> drawLine(ArtPalette.metal.tint(.3f), Offset(feet.x - (hw + 2f) * u, hip.y - 2f * u), Offset(feet.x - (hw + 9f) * u, hip.y + 26f * u), 1.6f * u, StrokeCap.Round)
        2 -> drawLine(Color(0xFF7A5634), Offset(feet.x + (sw + 3f) * u, shoulderY - 30f * u), Offset(feet.x + (sw + 3f) * u, feet.y - 2f * u), 1.4f * u).also {
            drawPath(Path().apply { moveTo(feet.x + (sw + 3f) * u, shoulderY - 38f * u); lineTo(feet.x + (sw + 5f) * u, shoulderY - 30f * u); lineTo(feet.x + (sw + 1f) * u, shoulderY - 30f * u); close() }, ArtPalette.metal.tint(.3f))
        }
        3 -> drawLine(Color(0xFF7A5634), Offset(feet.x - (hw + 2f) * u, hip.y - 6f * u), Offset(feet.x - (hw + 6f) * u, hip.y + 18f * u), 1.6f * u)
        5 -> drawLine(Color(0xFF6A4A2A), Offset(feet.x + (sw + 3f) * u, shoulderY - 18f * u), Offset(feet.x + (sw + 3f) * u, feet.y - 2f * u), 1.8f * u, StrokeCap.Round)
        4 -> for (side in listOf(-1f, 1f)) drawLine(ArtPalette.metal.tint(.4f), Offset(feet.x + side * 4f * u, hip.y - 1f * u), Offset(feet.x + side * 6f * u, hip.y + 9f * u), 1.1f * u)
    }
    if (a.cloak in 1..3) drawCircle(ArtPalette.gold, 1.6f * u, Offset(feet.x - 6f * u, shoulderY + 3f * u))
    // Neck and head.
    drawLine(tone.base.shade(.1f), Offset(feet.x + lean * u, shoulderY - 6f * u), Offset(feet.x + lean * u, shoulderY + 1f * u), 6f * u)
    val headCenter = Offset(feet.x + lean * 1.3f * u, shoulderY - 23f * u)
    val hs = u * .62f
    if (a.hairStyle in setOf(2, 3, 5, 6)) drawBackHair(a, headCenter, hs, gender)
    if (detailed) drawHead(a, gender, headCenter, hs, detailed = false) else drawTinyHead(a, gender, headCenter, hs)
}

/** Cheap head for the many small figures in the tavern; same colours and silhouette as the portrait. */
private fun DrawScope.drawTinyHead(a: CharacterAppearance, gender: Gender, c: Offset, s: Float) {
    val tone = ArtPalette.skin[a.skinTone]
    val (base, dark, light) = hairColors(a)
    if (a.ears > 0) for (side in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(c.x + side * 13f * s, c.y - 2f * s); lineTo(c.x + side * (20f + a.ears * 4f) * s, c.y - 12f * s); lineTo(c.x + side * 13f * s, c.y + 5f * s); close()
    }, tone.base)
    drawOval(Brush.radialGradient(listOf(tone.light, tone.base, tone.shadow), Offset(c.x - 5f * s, c.y - 6f * s), 22f * s), Offset(c.x - 14f * s, c.y - 19f * s), Size(28f * s, 38f * s))
    for (side in listOf(-1f, 1f)) drawCircle(Color(0xFF1A1210), 1.6f * s, Offset(c.x + side * 5.5f * s, c.y - 1f * s))
    if (a.beard >= 2) drawPath(Path().apply {
        moveTo(c.x - 13f * s, c.y + 2f * s); cubicTo(c.x - 12f * s, c.y + 20f * s, c.x + 12f * s, c.y + 20f * s, c.x + 13f * s, c.y + 2f * s)
        cubicTo(c.x + 6f * s, c.y + 10f * s, c.x - 6f * s, c.y + 10f * s, c.x - 13f * s, c.y + 2f * s); close()
    }, base)
    if (a.hairStyle != 7) drawPath(Path().apply {
        moveTo(c.x - 15f * s, c.y + 2f * s)
        cubicTo(c.x - 18f * s, c.y - 26f * s, c.x + 18f * s, c.y - 26f * s, c.x + 15f * s, c.y + 2f * s)
        cubicTo(c.x + 12f * s, c.y - 10f * s, c.x - 12f * s, c.y - 10f * s, c.x - 15f * s, c.y + 2f * s)
        close()
    }, Brush.linearGradient(listOf(light, base, dark), Offset(c.x - 14f * s, c.y - 20f * s), Offset(c.x + 14f * s, c.y)))
    if (a.hairStyle == 4) drawCircle(base, 6f * s, Offset(c.x, c.y - 22f * s))
}

// ------------------------------------------------------------ painted layer pipeline

/**
 * Asset contract for painted portrait layers. Files live under `assets/portraits/` as WebP with
 * transparent background, all at 1024×1280 and registered to the same head position:
 *
 *  base/{female|male}_{faceShape}.webp     neutral-grey skin+face, tinted by skin tone
 *  eyes/{eyeShape}.webp, eyes/iris_mask.webp
 *  brows/{browStyle}.webp, nose/{noseShape}.webp, mouth/{expression}.webp
 *  hair_back/{hairStyle}.webp, hair_front/{hairStyle}_{hairTexture}.webp (greyscale, tinted)
 *  beard/{beard}.webp, marks/scar_{scar}.webp, marks/tattoo_{tattoo}.webp
 *  outfit/{clothing}.webp, armor/{armor}.webp, cloak/{cloak}.webp (greyscale where tinted)
 *
 * [layersFor] returns null until at least the base face for the requested gender exists, so the
 * painted vector fallback is used and nothing pretends to be finished premium art.
 */
object PortraitAssets {
    data class Layer(val bitmap: ImageBitmap, val tint: Color?)

    private val cache = LruCache<String, ImageBitmap>(24)
    @Volatile private var index: Set<String>? = null

    private fun files(context: android.content.Context): Set<String> = index ?: run {
        val found = mutableSetOf<String>()
        runCatching {
            context.assets.list("portraits")?.forEach { dir ->
                context.assets.list("portraits/$dir")?.forEach { found += "portraits/$dir/$it" }
            }
        }
        found.also { index = it }
    }

    private fun load(context: android.content.Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val bitmap = runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        return bitmap.asImageBitmap().also { cache.put(path, it) }
    }

    fun installedCount(context: android.content.Context): Int = files(context).size

    fun layersFor(context: android.content.Context, a: CharacterAppearance, gender: Gender, framing: Framing): List<Layer>? {
        if (framing == Framing.FULL) return null
        val all = files(context)
        val sex = if (gender == Gender.FEMALE) "female" else "male"
        val base = "portraits/base/${sex}_${a.faceShape}.webp"
        if (base !in all) return null
        val hair = ArtPalette.hair[a.hairColor]
        val fabric = ArtPalette.fabric[a.primaryColor]
        val wanted = listOf(
            "portraits/hair_back/${a.hairStyle}.webp" to hair,
            "portraits/outfit/${a.clothing}.webp" to fabric,
            "portraits/armor/${a.armor}.webp" to null,
            "portraits/cloak/${a.cloak}.webp" to ArtPalette.fabric[a.accentColor],
            base to ArtPalette.skin[a.skinTone].base,
            "portraits/eyes/${a.eyeShape}.webp" to null,
            "portraits/eyes/iris_mask.webp" to ArtPalette.eyes[a.eyeColor],
            "portraits/brows/${a.browStyle}.webp" to hair,
            "portraits/nose/${a.noseShape}.webp" to null,
            "portraits/mouth/${a.expression}.webp" to null,
            "portraits/marks/scar_${a.scar}.webp" to null,
            "portraits/marks/tattoo_${a.tattoo}.webp" to null,
            "portraits/beard/${a.beard}.webp" to hair,
            "portraits/hair_front/${a.hairStyle}_${a.hairTexture}.webp" to hair,
        )
        return wanted.filter { it.first in all }.mapNotNull { (path, tint) -> load(context, path)?.let { Layer(it, tint) } }
    }
}

@Composable
private fun LayeredAssetPortrait(layers: List<PortraitAssets.Layer>, appearance: CharacterAppearance, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(ArtPalette.backgrounds[appearance.background], Offset(size.width * .32f, size.height * .28f), size.maxDimension))
        }
        layers.forEach { layer ->
            Image(layer.bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                colorFilter = layer.tint?.let { ColorFilter.tint(it, BlendMode.Modulate) })
        }
    }
}

