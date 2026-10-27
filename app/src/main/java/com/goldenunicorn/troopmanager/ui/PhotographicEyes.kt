package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Decode-time separation: skin takes its palette, neutral sclera/lashes keep photographic color. */
internal object PhotographicEyes {
    data class Surfaces(val skin: ImageBitmap, val neutral: ImageBitmap)

    fun separate(bitmap: Bitmap): Surfaces {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val skin = IntArray(pixels.size)
        val neutral = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = Color.alpha(pixel)
            if (alpha == 0) continue
            val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
            val grey = (.2126f*r + .7152f*g + .0722f*b).toInt().coerceIn(0,255)
            skin[i] = Color.argb(alpha,grey,grey,grey)
            val maximum = maxOf(r,g,b)
            val chroma = (maximum-minOf(r,g,b)).toFloat()/maximum.coerceAtLeast(1)
            val keep = if (maximum < 40) 1f else (1f-(chroma-.1f)/.08f).coerceIn(0f,1f)
            neutral[i] = Color.argb((alpha*keep).toInt(),r,g,b)
        }
        return Surfaces(
            Bitmap.createBitmap(skin,bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).asImageBitmap(),
            Bitmap.createBitmap(neutral,bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).asImageBitmap(),
        )
    }
}
