package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.PresentationEngine
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun PresentationScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val heraldry = state.presentation.heraldry
    var draft by remember(heraldry) { mutableStateOf(heraldry) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LegendPanel("Banner deines Reiches") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BannerBadge(draft, 84.dp)
                    Text("${state.realm.settlementName}\n${draft.symbol.label} · ${state.title}", color = PaleGold)
                }
                Text("Grundfarbe", color = Mist)
                BannerColors(draft.primaryArgb) { draft = draft.copy(primaryArgb = it) }
                Text("Symbolfarbe", color = Mist)
                BannerColors(draft.secondaryArgb) { draft = draft.copy(secondaryArgb = it) }
                HeraldicSymbol.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { symbol -> FilterChip(selected = draft.symbol == symbol,
                            onClick = { draft = draft.copy(symbol = symbol) }, label = { Text(symbol.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)) }
                    }
                }
                GoldButton("Banner übernehmen", {
                    val result = PresentationEngine.setHeraldry(state, draft)
                    onState(result.state); onNotice(result.message)
                }, Modifier.fillMaxWidth())
            }
        }
        item { SectionTitle("Halle der Legenden") }
        item { Text("Generalsstatuen geben Heimattruppen +1 Moral pro Tag, bis zu +3. Erfolge verleihen einmalig Ruhm.", color = Mist, fontSize = 12.sp) }
        if (state.presentation.legends.isEmpty()) item { EmptyCard("Deine Geschichte hat begonnen. Zehn Siege und Stufe 6 machen einen General zur Legende; erfahrene Veteranen und große Siege erhalten eigene Einträge.") }
        items(state.presentation.legends.asReversed(), key = { it.id }) { legend ->
            LegendPanel("${legend.name} · Tag ${legend.day}") { Text(legend.account, color = Mist, fontSize = 13.sp) }
        }
        item { SectionTitle("Lokale Erfolge") }
        items(Achievement.entries, key = { it.name }) { achievement ->
            val unlocked = state.presentation.achievements.firstOrNull { it.achievement == achievement }
            LegendPanel("${if (unlocked != null) "✓" else "◇"} ${achievement.label}") {
                Text(achievement.description, color = Mist, fontSize = 12.sp)
                Text(if (unlocked != null) "Tag ${unlocked.day} · +${achievement.renown} Ruhm verliehen"
                    else "+${achievement.renown} Ruhm bei Freischaltung", color = if (unlocked != null) Success else Gold, fontSize = 12.sp)
            }
        }
        item {
            val r = state.presentation.records
            LegendPanel("Kampagnenrekorde") {
                StatGrid(listOf("Größtes Heer" to "${r.largestArmy}", "Höchste Bevölkerung" to "${r.highestPopulation}",
                    "Größter Sieg: feindliches Heer" to "${r.largestVictory}", "Größte Niederlage: eigenes Heer" to "${r.largestDefeat}",
                    "Längste Schlacht" to "${r.longestBattleMinutes} Minuten", "Meiste Gebiete" to "${r.mostTerritories}",
                    "Höchste Bruttotagesproduktion" to "${r.highestDailyProduction}"))
            }
        }
    }
}

@Composable
private fun BannerColors(selected: Int, onSelect: (Int) -> Unit) {
    val names = listOf("Nachtblau", "Weinrot", "Waldgrün", "Violett", "Kohle", "Hellgold", "Silber", "Kupfer")
    PresentationEngine.bannerPalette.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { argb ->
                OutlinedButton(onClick = { onSelect(argb) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    contentDescription = "${names[PresentationEngine.bannerPalette.indexOf(argb)]}${if (selected == argb) ", gewählt" else ""}"
                },
                    contentPadding = PaddingValues(4.dp)) {
                    Box(Modifier.size(20.dp).background(Color(argb), RoundedCornerShape(3.dp)))
                    Text(if (selected == argb) " ✓" else " ${PresentationEngine.bannerPalette.indexOf(argb) + 1}", color = Mist)
                }
            }
        }
    }
}

@Composable
private fun LegendPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PaleGold, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun BannerBadge(heraldry: Heraldry, extent: Dp = 36.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(extent).semantics { contentDescription = "Reichsbanner: ${heraldry.symbol.label}" }) {
        drawHeraldry(heraldry, Offset(size.width / 2f, size.height / 2f), size.width * .38f)
    }
}

/** Geometric symbols are legible independently of font support on an Android device. */
internal fun DrawScope.drawHeraldry(heraldry: Heraldry, center: Offset, radius: Float) {
    val shield = Path().apply {
        moveTo(center.x - radius, center.y - radius)
        lineTo(center.x + radius, center.y - radius)
        lineTo(center.x + radius * .85f, center.y + radius * .55f)
        lineTo(center.x, center.y + radius * 1.2f)
        lineTo(center.x - radius * .85f, center.y + radius * .55f)
        close()
    }
    val ink = Color(heraldry.secondaryArgb)
    drawPath(shield, Color(heraldry.primaryArgb)); drawPath(shield, ink, style = Stroke(radius * .09f))
    val r = radius * .58f
    val line = radius * .12f
    when (heraldry.symbol) {
        HeraldicSymbol.WALL -> {
            drawLine(ink, center + Offset(-r, r * .4f), center + Offset(r, r * .4f), line * 3f)
            repeat(3) { i -> drawLine(ink, center + Offset((i - 1) * r * .7f, -.45f * r), center + Offset((i - 1) * r * .7f, r * .4f), line * 2f) }
        }
        HeraldicSymbol.STAR -> {
            val star = Path()
            repeat(10) { i ->
                val angle = -Math.PI / 2 + i * Math.PI / 5
                val length = if (i % 2 == 0) r else r * .45f
                val point = center + Offset(cos(angle).toFloat() * length, sin(angle).toFloat() * length)
                if (i == 0) star.moveTo(point.x, point.y) else star.lineTo(point.x, point.y)
            }
            star.close(); drawPath(star, ink)
        }
        HeraldicSymbol.TREE -> {
            drawLine(ink, center + Offset(0f, -r), center + Offset(0f, r), line)
            repeat(3) { i -> val y = -r * .6f + i * r * .5f
                drawLine(ink, center + Offset(0f, y - r * .4f), center + Offset(-r * .65f, y), line)
                drawLine(ink, center + Offset(0f, y - r * .4f), center + Offset(r * .65f, y), line) }
        }
        HeraldicSymbol.SUN -> {
            drawCircle(ink, r * .4f, center)
            repeat(8) { i -> val a = i * Math.PI / 4
                val v = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(ink, center + v * r * .6f, center + v * r, line * .75f) }
        }
        HeraldicSymbol.CROWN -> {
            val crown = Path().apply { moveTo(center.x-r, center.y-r*.5f); lineTo(center.x-r*.5f, center.y)
                lineTo(center.x, center.y-r*.8f); lineTo(center.x+r*.5f, center.y)
                lineTo(center.x+r, center.y-r*.5f); lineTo(center.x+r*.7f, center.y+r*.5f)
                lineTo(center.x-r*.7f, center.y+r*.5f); close() }
            drawPath(crown, ink)
        }
        HeraldicSymbol.SPEAR -> {
            drawLine(ink, center + Offset(0f, -r), center + Offset(0f, r), line)
            drawLine(ink, center + Offset(-r*.4f, -r*.45f), center + Offset(0f, -r), line)
            drawLine(ink, center + Offset(r*.4f, -r*.45f), center + Offset(0f, -r), line)
        }
    }
}

@Composable
internal fun ContextTutorialCard(state: GameState, context: String, onState: (GameState) -> Unit) {
    val tutorial = PresentationEngine.tutorial(state, context) ?: return
    Surface(color = Panel2, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tutorial.title, color = PaleGold, fontWeight = FontWeight.SemiBold)
            Text(tutorial.text, color = Mist, fontSize = 12.sp)
            TextButton(onClick = { onState(PresentationEngine.markTutorialSeen(state, tutorial.id)) }) { Text("Verstanden") }
        }
    }
}
