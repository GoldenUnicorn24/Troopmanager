package com.goldenunicorn.troopmanager.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.goldenunicorn.troopmanager.model.WeatherKind

/** Rain streaks, snowflakes, fog and storm gloom over the outdoor scenes (market, barracks, castle). */
internal fun DrawScope.drawWeather(kind: WeatherKind, unit: Float, time: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    when (kind) {
        WeatherKind.RAIN, WeatherKind.STORM -> {
            val storm = kind == WeatherKind.STORM
            if (storm) drawRect(Color(0xFF0B1018).copy(alpha = .28f))
            val n = if (storm) 90 else 55
            val slant = unit * (if (storm) .5f else .2f)
            for (k in 0 until n) {
                val x = (k * 137 % 1000) / 1000f * w
                val y = ((k * 271 % 1000) / 1000f + time * (if (storm) 3f else 2f)) % 1f * h
                drawLine(Color(0xFFB8C8DA).copy(alpha = .45f), Offset(x, y), Offset(x - slant, y + unit * .55f), unit * .035f, StrokeCap.Round)
            }
        }
        WeatherKind.SNOW -> {
            drawRect(Color.White.copy(alpha = .08f))
            for (k in 0 until 70) {
                val drift = kotlin.math.sin((time + k * .13f) * 6.283f) * unit * .15f
                val x = (k * 211 % 1000) / 1000f * w + drift
                val y = ((k * 389 % 1000) / 1000f + time * .6f) % 1f * h
                drawCircle(Color.White.copy(alpha = .8f), unit * (.04f + (k % 3) * .015f), Offset(x, y))
            }
        }
        WeatherKind.FOG -> drawRect(Color(0xFFD8DDE2).copy(alpha = .26f))
        WeatherKind.FROST -> drawRect(Color(0xFFCFE4F5).copy(alpha = .1f))
        WeatherKind.HEAT -> drawRect(Color(0xFFFFC870).copy(alpha = .07f))
        WeatherKind.CLEAR -> Unit
    }
}
