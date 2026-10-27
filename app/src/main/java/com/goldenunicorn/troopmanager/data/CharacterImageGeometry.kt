package com.goldenunicorn.troopmanager.data

import kotlin.math.*

enum class CharacterImageKind(val width: Int, val height: Int, val minWidth: Int, val minHeight: Int) {
    PORTRAIT(1024, 1280, 512, 640), FULLBODY(768, 1536, 384, 768)
}

/** Transform in destination coordinates, shared by preview and the saved raster. */
data class CharacterImageCrop(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f, val rotation: Float = 0f) {
    fun scale(width: Int, height: Int, kind: CharacterImageKind): Float =
        max(kind.width.toFloat() / width, kind.height.toFloat() / height) * zoom

    fun map(px: Float, py: Float, width: Int, height: Int, kind: CharacterImageKind): Pair<Float, Float> {
        val angle = rotation * PI.toFloat() / 180f
        val dx = (px - width / 2f) * scale(width, height, kind)
        val dy = (py - height / 2f) * scale(width, height, kind)
        return (kind.width / 2f + x + dx * cos(angle) - dy * sin(angle)) to
            (kind.height / 2f + y + dx * sin(angle) + dy * cos(angle))
    }

    fun coversFrame(width: Int, height: Int, kind: CharacterImageKind): Boolean {
        val s = scale(width, height, kind)
        val angle = -rotation * PI.toFloat() / 180f
        return listOf(0f to 0f, kind.width.toFloat() to 0f, 0f to kind.height.toFloat(), kind.width.toFloat() to kind.height.toFloat()).all { (px, py) ->
            val dx = px - kind.width / 2f - x
            val dy = py - kind.height / 2f - y
            abs((dx * cos(angle) - dy * sin(angle)) / s) <= width / 2f + .01f &&
                abs((dx * sin(angle) + dy * cos(angle)) / s) <= height / 2f + .01f
        }
    }
}

object CharacterImageFrames {
    const val EYE_Y = 560
    const val FOOT_Y = 1480
    // Head crop retains the standard eye position; all screens use this same window.
    val head = intArrayOf(192, 160, 640, 800)
    fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }
}
