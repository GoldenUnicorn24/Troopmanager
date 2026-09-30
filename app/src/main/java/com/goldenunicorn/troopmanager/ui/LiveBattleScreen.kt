package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.BattleEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun LiveBattleScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val session = state.battleSession ?: return
    var showSetup by remember { mutableStateOf(false) }
    if (showSetup) BattleSetupDialog(state, { showSetup = false }, onState, onNotice)
    fun advance(decision: BattleDecision? = null) {
        val result = BattleEngine.advance(state, decision)
        onState(result.state)
        onNotice(result.message)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTitle("LIVE-SCHLACHT", "${session.enemy.label} · ${session.tactic.label}") }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Minute ${session.minute} · ${session.phase.label}", color = PaleGold, fontWeight = FontWeight.Bold)
                    BattleStrength("Dein Heer", session.ownRemaining, session.ownStart, Gold)
                    BattleStrength("Gegner", session.enemyRemaining, session.enemyStart, Danger)
                    Text("Moral ${session.morale} % · ${session.soldiers(BattleSection.RESERVE)} in Reserve", color = Mist)
                    if (session.tactic == Tactic.FORTIFY) {
                        Text("Mauerzustand ${session.wallIntegrity} %", color = if (session.wallIntegrity < 30) Danger else PaleGold)
                        Text(if (session.devices.isEmpty()) "Keine feindlichen Belagerungsgeräte mehr" else session.devices.joinToString(" · ") { it.label }, color = Mist, fontSize = 12.sp)
                    }
                    if (state.resources.food == 0) Text("Hunger schwächt die Kampfkraft und senkt die Moral.", color = Danger)
                }
            }
        }
        if (session.minute == 0 && session.isActive) item {
            GoldButton("Aufstellung vor Kampfbeginn anpassen", { showSetup = true }, Modifier.fillMaxWidth())
        }
        item { SessionBattleField(session) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT).forEach { section ->
                    val front = session.fronts.firstOrNull { it.section == section }
                    Surface(modifier = Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(section.label, color = PaleGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${session.soldiers(section)} eigene", color = Gold, fontSize = 12.sp)
                            Text("${front?.enemySoldiers ?: 0} Gegner", color = Danger, fontSize = 12.sp)
                            Text(when { (front?.position ?: 50) > 55 -> "Gewinnt Boden"; (front?.position ?: 50) < 40 -> "Unter Druck"; else -> "Linie hält" }, color = Mist, fontSize = 11.sp)
                            session.contingents.filter { it.section == section && it.soldiers > 0 }.map { it.commanderId }.distinct().forEach { id ->
                                Text(if (id == null) "Oberkommando" else state.commanders.firstOrNull { it.id == id }?.name ?: "Kommandant", color = Mist, fontSize = 11.sp)
                                if (session.contingents.any { it.commanderId == id && it.commanderWounded }) Text("Verwundet", color = Danger, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        val event = session.pendingEvent
        if (session.isActive && event != null) {
            item {
                Surface(color = Color(0xFF30291D), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(event.title, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(event.text, color = Mist)
                        event.options.forEach { option ->
                            OutlinedButton(onClick = { advance(option) }, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
                        }
                    }
                }
            }
        } else if (session.isActive) {
            item { GoldButton("Nächster Schlachtschritt (+5 Minuten)", { advance() }, Modifier.fillMaxWidth()) }
        } else {
            item {
                Surface(color = if (session.status == BattleStatus.VICTORY) Color(0xFF173024) else Color(0xFF331E1E), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (session.status == BattleStatus.VICTORY) "SIEG" else "NIEDERLAGE", color = if (session.status == BattleStatus.VICTORY) Success else Danger, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("${session.ownStart - session.ownRemaining} eigene Verluste · ${session.enemyStart - session.enemyRemaining} gegnerische Verluste", color = Color.White)
                        if (session.lootGold > 0) Text("Beute: ${session.lootGold} Gold", color = PaleGold)
                        Text("Das Ergebnis wurde übernommen. Deine überlebenden Truppen stehen wieder zur Verfügung.", color = Mist)
                    }
                }
            }
            item { GoldButton("Ergebnis speichern und zur Welt", { onState(state.copy(battleSession = null)); onNotice("Schlachtbericht bleibt in der Chronik gespeichert.") }, Modifier.fillMaxWidth()) }
        }
        item { SectionTitle("Schlachtverlauf") }
        items(session.log.asReversed()) { entry ->
            CompactCard("Minute ${entry.minute} · ${entry.text}", "−${entry.ownLosses} eigene · −${entry.enemyLosses} Gegner")
        }
        if (session.isActive) item { Text("Jeder Schritt wird gespeichert. Du kannst die Ansicht verlassen und diese Schlacht später fortsetzen.", color = Mist, fontSize = 12.sp) }
    }
}

@Composable
private fun BattleStrength(label: String, current: Int, initial: Int, color: Color) {
    Text("$label: $current / $initial", color = color, fontWeight = FontWeight.Bold)
    LinearProgressIndicator(progress = { (current.toFloat() / initial.coerceAtLeast(1)).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp), color = color, trackColor = Color(0xFF343D35))
}

/** Draws aggregate formations, never one object per soldier. Coordinates follow the persisted fronts. */
@Composable
private fun SessionBattleField(session: BattleSession) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Gefechtslage · Links / Zentrum / Rechts", color = PaleGold, fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(235.dp).padding(top = 10.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFF203226))
                listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT).forEachIndexed { index, section ->
                    val x = w * (index + 0.5f) / 3f
                    val front = session.fronts.firstOrNull { it.section == section }
                    val own = session.soldiers(section)
                    val y = h * (0.73f - (front?.position ?: 50) / 200f)
                    if (index > 0) drawLine(Color(0xFF4D624B), Offset(w * index / 3f, 0f), Offset(w * index / 3f, h), 2f)
                    drawLine(PaleGold.copy(alpha = 0.5f), Offset(x - w * 0.13f, y), Offset(x + w * 0.13f, y), 3f)
                    val ownMarkers = if (own == 0) 0 else (own / 40 + 1).coerceAtMost(12)
                    repeat(ownMarkers) { n ->
                        drawCircle(Gold, 4.dp.toPx(), Offset(x + (n % 4 - 1.5f) * w * 0.045f, y + h * 0.08f + n / 4 * h * 0.06f))
                    }
                    val enemyMarkers = if ((front?.enemySoldiers ?: 0) == 0) 0 else ((front?.enemySoldiers ?: 0) / 40 + 1).coerceAtMost(12)
                    repeat(enemyMarkers) { n ->
                        drawCircle(Danger, 4.dp.toPx(), Offset(x + (n % 4 - 1.5f) * w * 0.045f, y - h * 0.08f - n / 4 * h * 0.06f))
                    }
                    if (session.phase == BattlePhase.RANGED && own > 0) repeat(3) { n ->
                        val arrowX = x + (n - 1) * w * 0.065f
                        drawLine(PaleGold, Offset(arrowX, y + h * 0.09f), Offset(arrowX - w * 0.015f, y - h * 0.11f), 2f)
                    }
                    if (session.contingents.any { it.section == section && it.type == UnitType.DRAGON_ARTILLERY && it.soldiers > 0 }) {
                        drawCircle(Color(0xFFDB8042), 7.dp.toPx(), Offset(x, h * 0.82f))
                        drawLine(Color(0xFFDB8042), Offset(x, h * 0.82f), Offset(x + w * 0.05f, y - h * 0.12f), 3f)
                    }
                }
                if (session.tactic == Tactic.FORTIFY) {
                    drawRect(Color(0xFF747871), Offset(0f, h * 0.85f), Size(w * session.wallIntegrity / 100f, h * 0.055f))
                    repeat(5) { n -> drawRect(Color(0xFF92938A), Offset(n * w / 4f - 5.dp.toPx(), h * 0.81f), Size(10.dp.toPx(), h * 0.12f)) }
                    session.devices.forEachIndexed { n, _ ->
                        drawRect(Color(0xFF795D3D), Offset(w * (n + 1f) / (session.devices.size + 1f), h * 0.12f), Size(8.dp.toPx(), 12.dp.toPx()))
                    }
                }
                if (session.soldiers(BattleSection.RESERVE) > 0) repeat(5) { n -> drawCircle(Color(0xFF68A2BE), 4.dp.toPx(), Offset(w * 0.35f + n * w * 0.075f, h * 0.96f)) }
            }
            Text("Gold: dein Heer · Rot: Gegner · Blau: Reserve", color = Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
