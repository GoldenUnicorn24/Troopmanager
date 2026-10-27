package com.goldenunicorn.troopmanager.ui


import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Multiply lighting/color and keep the original alpha. A colour matrix works on unpremultiplied
 * colours on every API level, so semi-transparent cut-out edges are tinted like their interior.
 * (The former Multiply blend mode added the pure tint colour to every partially transparent edge
 * pixel, which drew light outlines around nose, eye and garment patches.)
 * [alphaScale]/[alphaOffset] remap the alpha channel: a' = a·scale + offset.
 */
internal fun DrawScope.drawTintedArt(image: ImageBitmap, tint: Color?, offset: IntOffset = IntOffset.Zero, target: IntSize = IntSize(image.width,image.height),
                                     alphaScale: Float = 1f, alphaOffset: Float = 0f) {
    if ((tint == null || tint == Color.White) && alphaScale == 1f && alphaOffset == 0f) {
        drawImage(image,dstOffset = offset,dstSize = target)
        return
    }
    val t = tint ?: Color.White
    val matrix = ColorMatrix(floatArrayOf(
        t.red, 0f, 0f, 0f, 0f,
        0f, t.green, 0f, 0f, 0f,
        0f, 0f, t.blue, 0f, 0f,
        0f, 0f, 0f, alphaScale, alphaOffset * 255f,
    ))
    drawImage(image,dstOffset = offset,dstSize = target,colorFilter = ColorFilter.colorMatrix(matrix))
}
