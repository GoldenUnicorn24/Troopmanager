package com.goldenunicorn.troopmanager.ui

import android.os.Build

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Multiply lighting/color, then restore the original alpha; transparent borders stay transparent. */
internal fun DrawScope.drawTintedArt(image: ImageBitmap, tint: Color?, offset: IntOffset = IntOffset.Zero, target: IntSize = IntSize(image.width,image.height)) {
    if (tint == null || tint == Color.White) {
        drawImage(image,dstOffset = offset,dstSize = target)
        return
    }
    // Pre-29 PorterDuff Multiply has different alpha semantics; Modulate is the compatible product.
    if (Build.VERSION.SDK_INT < 29) {
        drawImage(image,dstOffset = offset,dstSize = target,colorFilter = ColorFilter.tint(tint,BlendMode.Modulate))
        return
    }
    val topLeft = Offset(offset.x.toFloat(),offset.y.toFloat())
    val bounds = Size(target.width.toFloat(),target.height.toFloat())
    drawContext.canvas.saveLayer(Rect(topLeft,bounds),Paint())
    drawImage(image,dstOffset = offset,dstSize = target)
    drawRect(tint,topLeft,bounds,blendMode = BlendMode.Multiply)
    drawImage(image,dstOffset = offset,dstSize = target,blendMode = BlendMode.DstIn)
    drawContext.canvas.restore()
}
