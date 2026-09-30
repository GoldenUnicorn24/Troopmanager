package com.goldenunicorn.troopmanager.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.BuildingType
import com.goldenunicorn.troopmanager.model.GameState

private data class FortressSite(val type: BuildingType, val x: Float, val y: Float, val title: String, val color: Color)
private val fortressSites = listOf(
    FortressSite(BuildingType.FARM, .17f, .83f, "Höfe", Color(0xFFAAAC58)),
    FortressSite(BuildingType.SAWMILL, .83f, .81f, "Sägewerk", Color(0xFF916940)),
    FortressSite(BuildingType.QUARRY, .12f, .20f, "Steinbruch", Color(0xFF939A97)),
    FortressSite(BuildingType.IRONWORKS, .84f, .20f, "Mine", Color(0xFFBC7650)),
    FortressSite(BuildingType.MARKET, .40f, .59f, "Markt", Color(0xFFC58D53)),
    FortressSite(BuildingType.BARRACKS, .68f, .56f, "Kaserne", Color(0xFF707A87)),
    FortressSite(BuildingType.PALACE, .50f, .32f, "Residenz", Color(0xFFB3AC92)),
    FortressSite(BuildingType.WALL, .50f, .75f, "Tor / Mauer", Color(0xFF8D9796)),
    FortressSite(BuildingType.TOWER, .73f, .34f, "Wehrturm", Color(0xFF9DA4A1))
)

@Composable
internal fun FortressMap(state: GameState, onBuilding: (BuildingType) -> Unit = {}) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DEINE FESTUNG · ${state.realm.settlementTier.label}", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(state.realm.settlementName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Canvas(Modifier.fillMaxWidth().height(290.dp).pointerInput(onBuilding) {
                detectTapGestures { tap ->
                    fortressSites.minByOrNull { site ->
                        val dx = tap.x / size.width - site.x
                        val dy = tap.y / size.height - site.y
                        dx * dx + dy * dy
                    }?.let { onBuilding(it.type) }
                }
            }) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFF233E32))
                // Streams, farmland, woodland and the road outside the fortress.
                val river = Path().apply { moveTo(w * .92f, 0f); cubicTo(w * .92f, h * .4f, w * .97f, h * .6f, w * .93f, h) }
                drawPath(river, Color(0xFF456B79), style = Stroke(w * .045f))
                repeat(12) { i -> drawCircle(Color(0xFF183C2D), w * .022f, Offset(w * (.02f + (i % 4) * .029f), h * (.38f + (i / 4) * .06f))) }
                drawRect(Color(0xFF77674D), Offset(w * .475f, h * .62f), Size(w * .05f, h * .38f))
                // Interior districts and stone paths.
                drawRect(Color(0xFF5B5F50), Offset(w * .22f, h * .24f), Size(w * .56f, h * .47f))
                drawRect(Color(0xFF827867), Offset(w * .28f, h * .49f), Size(w * .44f, h * .035f))
                drawRect(Color(0xFF827867), Offset(w * .475f, h * .25f), Size(w * .05f, h * .46f))
                repeat(8) { i ->
                    val x = w * (.25f + (i % 4) * .055f)
                    val y = h * (.38f + (i / 4) * .055f)
                    drawRect(Color(0xFF918473), Offset(x, y), Size(w * .035f, h * .03f))
                    drawLine(Color(0xFF543F35), Offset(x - w * .005f, y), Offset(x + w * .04f, y), h * .009f)
                }
                // Curtain walls, crenellations and corner towers.
                val wall = if (state.realm.wallIntegrity < 50) Color(0xFF8C6B57) else Color(0xFF969A91)
                drawRect(wall, Offset(w * .21f, h * .23f), Size(w * .58f, h * .49f), style = Stroke(w * .022f))
                repeat(18) { i ->
                    val x = w * (.21f + i * .033f)
                    drawRect(Color(0xFFC2C1B1), Offset(x, h * .214f), Size(w * .017f, h * .025f))
                    drawRect(Color(0xFFC2C1B1), Offset(x, h * .704f), Size(w * .017f, h * .024f))
                }
                listOf(.21f to .23f, .79f to .23f, .21f to .72f, .79f to .72f).forEach { (x, y) ->
                    drawCircle(Color(0xFF414A48), w * .038f, Offset(w * x + 3f, h * y + 5f))
                    drawCircle(wall, w * .032f, Offset(w * x, h * y))
                    drawCircle(Color(0xFFC4C2B4), w * .02f, Offset(w * x, h * y), style = Stroke(3f))
                }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; textSize = w * .025f; setShadowLayer(3f, 0f, 2f, android.graphics.Color.BLACK) }
                fortressSites.forEach { site ->
                    val x = site.x * w
                    val y = site.y * h
                    val level = state.realm.level(site.type)
                    if (site.type == BuildingType.FARM) {
                        repeat(5) { row -> drawRect(site.color, Offset(x - w * .08f, y - h * .07f + row * h * .025f), Size(w * .16f, h * .012f)) }
                    } else {
                        drawRect(Color(0x55000000), Offset(x - w * .045f + 4f, y - h * .05f + 5f), Size(w * .09f, h * .085f))
                        drawRect(if (level > 0) site.color else Color(0xFF55594F), Offset(x - w * .045f, y - h * .05f), Size(w * .09f, h * .085f))
                        val roof = Path().apply { moveTo(x - w * .055f, y - h * .05f); lineTo(x, y - h * .09f); lineTo(x + w * .055f, y - h * .05f); close() }
                        drawPath(roof, Color(0xFF5B3F36))
                        drawRect(Color(0xFF302F2A), Offset(x - w * .008f, y), Size(w * .016f, h * .035f))
                    }
                    drawContext.canvas.nativeCanvas.drawText("${site.title} $level", x, y + h * .075f, labelPaint)
                }
            }
            Text("Innenstadt · Militärviertel · Produktion · Außenland · Mauer und Tor", color = Mist, fontSize = 12.sp)
            Text("Gebäude antippen: Details und Ausbau öffnen.", color = Gold, fontSize = 12.sp)
            // Named controls also make every map target available to accessibility services.
            fortressSites.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { site -> TextButton(onClick = { onBuilding(site.type) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text(site.title, fontSize = 12.sp) } }
                }
            }
        }
    }
}
