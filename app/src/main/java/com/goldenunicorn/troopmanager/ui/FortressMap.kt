package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.BuildingType
import com.goldenunicorn.troopmanager.model.GameState

@Composable
internal fun FortressMap(state: GameState) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("DEINE FESTUNG", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Text(state.realm.settlementName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)

            Canvas(Modifier.fillMaxWidth().height(230.dp).padding(top = 10.dp)) {
                val w = size.width
                val h = size.height
                val territory = state.realm.territory
                val wallLevel = state.realm.level(BuildingType.WALL)
                val towerLevel = state.realm.level(BuildingType.TOWER)
                val palace = state.realm.level(BuildingType.PALACE)

                drawRect(Color(0xFF263528))
                drawRect(Color(0xFF314231), Offset(0f, h * 0.1f), Size(w, h * 0.9f))

                repeat(5 + territory.coerceAtMost(6)) { i ->
                    val x = w * (0.08f + (i % 6) * 0.15f)
                    val y = h * (0.18f + (i / 6) * 0.22f)
                    drawRect(Color(0xFF7D6547), Offset(x, y), Size(32f, 22f))
                    drawRect(Color(0xFF4E3928), Offset(x - 3f, y - 9f), Size(38f, 10f))
                }

                val margin = (55f - territory * 3f).coerceAtLeast(28f)
                val wallColor = if (wallLevel >= 4) Color(0xFFAAA69B) else Color(0xFF77766E)
                val stroke = 5f + wallLevel * 2f
                drawRect(
                    color = wallColor,
                    topLeft = Offset(margin, margin),
                    size = Size(w - margin * 2, h - margin * 1.55f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )

                val towers = (4 + towerLevel * 2).coerceAtMost(12)
                repeat(towers) { i ->
                    val fraction = i.toFloat() / towers
                    val x = if (i % 2 == 0) margin else w - margin
                    val y = margin + fraction * (h - margin * 1.6f)
                    drawCircle(Color(0xFF8D8A80), 8f + towerLevel, Offset(x, y))
                }

                val palaceW = 62f + palace * 8f
                val palaceH = 48f + palace * 6f
                drawRect(
                    Color(0xFF4B5660),
                    Offset(w / 2 - palaceW / 2, h / 2 - palaceH / 2),
                    Size(palaceW, palaceH)
                )
                drawCircle(Gold, 7f + palace, Offset(w / 2, h / 2))

                val market = state.realm.level(BuildingType.MARKET)
                repeat(market.coerceAtMost(5)) { i ->
                    drawCircle(Color(0xFFD09C58), 6f, Offset(w * 0.36f + i * 15f, h * 0.60f))
                }

                val farms = state.realm.level(BuildingType.FARM)
                repeat(farms.coerceAtMost(6)) { i ->
                    drawRect(
                        Color(0xFF74854D),
                        Offset(w * 0.1f + i * 18f, h * 0.73f),
                        Size(13f, 26f)
                    )
                }
            }

            Text(
                "Gebiete " + state.realm.territory +
                        " · Mauer St. " + state.realm.level(BuildingType.WALL) +
                        " · Türme St. " + state.realm.level(BuildingType.TOWER) +
                        " · Residenz St. " + state.realm.level(BuildingType.PALACE),
                color = Mist,
                fontSize = 11.sp
            )
        }
    }
}
