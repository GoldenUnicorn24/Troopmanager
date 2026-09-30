package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
        if (session.tactic == Tactic.FORTIFY) item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column {
                    CityScene(state, Modifier.fillMaxWidth().height(220.dp), season = state.city.season, defenseMode = true, wallIntegrity = session.wallIntegrity)
                    Text("Deine Stadt unter Belagerung · Tor und Mauern ${session.wallIntegrity} %", color = PaleGold, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
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
                                if (session.contingents.any { it.commanderId == id && it.commanderWounded }) Text(if (session.contingents.any { it.commanderId == id && it.commanderRescued }) "Geborgen · eingeschränkt" else "Verwundet", color = Danger, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Kontingente und Kommandos") }
        items(session.contingents.filter { it.soldiers > 0 || !session.isActive }) { contingent ->
            val commander = contingent.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.name } ?: state.player.name
            Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${contingent.type.label} · ${contingent.soldiers} / ${contingent.startSoldiers}", color = PaleGold, fontWeight = FontWeight.Bold)
                    Text("$commander · ${contingent.section.label}", color = Mist, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Moral ${contingent.morale} %", color = if (contingent.morale < 35) Danger else Success, fontSize = 12.sp)
                        Text("Erfahrung ${contingent.experience} · Ausrüstung ${contingent.equipment} %", color = Mist, fontSize = 12.sp)
                    }
                    if (contingent.commanderWounded) Text(if (contingent.commanderRescued) "Kommandant geborgen · Führung teilweise wiederhergestellt" else "Kommandant verwundet · Führung geschwächt", color = Danger, fontSize = 12.sp)
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
                        if (session.orderedRetreat) Text("Geordneter Rückzug · Überlebende in Sicherheit", color = PaleGold)
                        Text("Heer: ${session.ownStart} zu Beginn · ${session.ownRemaining} Überlebende · ${session.ownStart - session.ownRemaining} Tote", color = Color.White)
                        Text("Gegner: ${session.enemyStart} zu Beginn · ${session.enemyRemaining} verblieben · ${session.enemyStart - session.enemyRemaining} Verluste", color = Mist)
                        Text("+${session.xpReward} Spieler-XP · +${session.renownReward} Ruhm", color = PaleGold)
                        Text("Moral der Überlebenden: ${session.morale} % · Ausrüstungsverschleiß: ${session.equipmentDamage} Punkte im eingesetzten Heer", color = Mist)
                        Text("Beute: ${session.lootGold} Gold · ${session.lootFood} Nahrung", color = PaleGold)
                        session.contingents.groupBy { it.type }.forEach { (type, troops) ->
                            val initial = troops.sumOf { it.startSoldiers }
                            val survivors = troops.sumOf { it.soldiers }
                            Text("${type.label}: $initial → $survivors · ${initial - survivors} Tote", color = Mist, fontSize = 12.sp)
                        }
                        session.commanderEvents.forEach { Text(it, color = PaleGold, fontSize = 12.sp) }
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

/** Aggregate formations stay tied to persisted positions and casualties; pulse only animates the current exchange. */
@Composable
private fun SessionBattleField(session: BattleSession) {
    val transition = rememberInfiniteTransition(label = "battle-exchange")
    val animated by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "battle-motion")
    val pulse = if (session.isActive && session.minute > 0) animated else 0.5f
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GEFECHTSKARTE", color = PaleGold, fontWeight = FontWeight.Bold)
            Text("LINKER FLÜGEL       ZENTRUM       RECHTER FLÜGEL", color = Mist, fontSize = 10.sp)
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFF202A22))
                repeat(14) { n ->
                    val x = (n * 97f % w)
                    val y = (n * 73f % h)
                    drawCircle(Color(0xFF2D3A2B), 12.dp.toPx(), Offset(x, y))
                }
                val reserveY = h * 0.91f
                val sections = listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT)
                sections.forEachIndexed { index, section ->
                    val x = w * (index + 0.5f) / 3f
                    val front = session.fronts.firstOrNull { it.section == section }
                    val own = session.soldiers(section)
                    val y = h * (0.75f - (front?.position ?: 50) / 200f)
                    if (index > 0) drawLine(Color(0xFF56624A).copy(alpha = 0.55f), Offset(w * index / 3f, 0f), Offset(w * index / 3f, h * 0.84f), 1.dp.toPx())
                    val pressed = (front?.position ?: 50) < 40
                    drawLine((if (pressed) Danger else PaleGold).copy(alpha = 0.7f), Offset(x - w * 0.13f, y), Offset(x + w * 0.13f, y), 2.dp.toPx())
                    val ownMarkers = if (own == 0) 0 else (own / 35 + 1).coerceAtMost(18)
                    repeat(ownMarkers) { n ->
                        val marker = Offset(x + (n % 6 - 2.5f) * w * 0.036f, y + h * 0.09f + n / 6 * h * 0.047f)
                        drawRect(Gold, marker - Offset(3.dp.toPx(), 3.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx()))
                        drawLine(PaleGold, marker + Offset(-2.dp.toPx(), -4.dp.toPx()), marker + Offset(2.dp.toPx(), -4.dp.toPx()), 1.dp.toPx())
                    }
                    val enemies = front?.enemySoldiers ?: 0
                    val enemyMarkers = if (enemies == 0) 0 else (enemies / 35 + 1).coerceAtMost(18)
                    repeat(enemyMarkers) { n ->
                        drawCircle(Danger, 3.dp.toPx(), Offset(x + (n % 6 - 2.5f) * w * 0.036f, y - h * 0.085f - n / 6 * h * 0.047f))
                    }
                    val local = session.contingents.filter { it.section == section && it.soldiers > 0 }
                    if (session.phase == BattlePhase.RANGED && local.any { it.type.ranged >= 8 } && enemies > 0) repeat(4) { n ->
                        val arrowX = x + (n - 1.5f) * w * 0.05f
                        val arrowY = y + h * 0.10f - pulse * h * 0.24f
                        drawLine(PaleGold, Offset(arrowX, arrowY + 10.dp.toPx()), Offset(arrowX, arrowY), 1.5.dp.toPx())
                        drawLine(PaleGold, Offset(arrowX, arrowY), Offset(arrowX - 2.dp.toPx(), arrowY + 3.dp.toPx()), 1.dp.toPx())
                    }
                    if (local.any { it.type == UnitType.KNIGHT }) {
                        val cavalryY = y + h * 0.17f - if (session.phase != BattlePhase.RANGED && session.minute > 0 && session.isActive) pulse * h * 0.19f else 0f
                        repeat(3) { n ->
                            val cavalryX = x + (n - 1) * w * 0.06f
                            val diamond = Path().apply {
                                moveTo(cavalryX, cavalryY - 5.dp.toPx()); lineTo(cavalryX + 4.dp.toPx(), cavalryY)
                                lineTo(cavalryX, cavalryY + 5.dp.toPx()); lineTo(cavalryX - 4.dp.toPx(), cavalryY); close()
                            }
                            drawPath(diamond, Color(0xFFE6D4B2))
                        }
                    }
                    if (local.any { it.type == UnitType.DRAGON_ARTILLERY } && enemies > 0) {
                        val orange = Color(0xFFED8B45)
                        drawRect(orange, Offset(x - 7.dp.toPx(), h * 0.77f), Size(14.dp.toPx(), 6.dp.toPx()))
                        drawCircle(Color(0xFF171914), 3.dp.toPx(), Offset(x - 5.dp.toPx(), h * 0.80f))
                        drawCircle(Color(0xFF171914), 3.dp.toPx(), Offset(x + 5.dp.toPx(), h * 0.80f))
                        if (session.minute > 0 && session.isActive) {
                            drawCircle(orange, 3.dp.toPx(), Offset(x + w * 0.02f * pulse, h * 0.77f + (y - h * 0.13f - h * 0.77f) * pulse))
                            drawCircle(orange.copy(alpha = 1f - pulse), (4f + pulse * 12f).dp.toPx(), Offset(x, y - h * 0.13f), style = Stroke(2.dp.toPx()))
                        }
                    }
                    if ((pressed || session.orderedRetreat) && own > 0) {
                        val retreatY = y + h * 0.18f + pulse * h * 0.06f
                        drawLine(Color(0xFFE6B77C), Offset(x, retreatY), Offset(x, retreatY + 10.dp.toPx()), 2.dp.toPx())
                        drawLine(Color(0xFFE6B77C), Offset(x, retreatY + 10.dp.toPx()), Offset(x - 3.dp.toPx(), retreatY + 6.dp.toPx()), 2.dp.toPx())
                        drawLine(Color(0xFFE6B77C), Offset(x, retreatY + 10.dp.toPx()), Offset(x + 3.dp.toPx(), retreatY + 6.dp.toPx()), 2.dp.toPx())
                    }
                }
                if (session.tactic == Tactic.FORTIFY) {
                    val wallColor = if (session.wallIntegrity < 30) Color(0xFF765A47) else Color(0xFF7D8179)
                    drawRect(wallColor, Offset(0f, h * 0.84f), Size(w, h * 0.05f))
                    repeat(14) { n -> drawRect(wallColor, Offset(n * w / 14f, h * 0.81f), Size(w / 22f, h * 0.03f)) }
                    if (session.wallIntegrity < 60) repeat(3) { n -> drawLine(Color(0xFF29261F), Offset(w * (n + 1) / 4f, h * 0.82f), Offset(w * (n + 1) / 4f + 7.dp.toPx(), h * 0.89f), 3.dp.toPx()) }
                    drawRect(if (session.wallIntegrity == 0) Color(0xFF15160F) else Color(0xFF594732), Offset(w * 0.45f, h * 0.82f), Size(w * 0.10f, h * 0.07f))
                    session.devices.forEachIndexed { n, device ->
                        val siegeX = w * (n + 1f) / (session.devices.size + 1f)
                        val height = if (device == SiegeDevice.TOWER) 24.dp.toPx() else 12.dp.toPx()
                        drawRect(Color(0xFF9A7346), Offset(siegeX - 4.dp.toPx(), h * 0.055f), Size(8.dp.toPx(), height))
                        drawLine(Color(0xFFD0A56A), Offset(siegeX - 7.dp.toPx(), h * 0.055f + height), Offset(siegeX + 7.dp.toPx(), h * 0.055f + height), 3.dp.toPx())
                    }
                }
                val reserves = session.soldiers(BattleSection.RESERVE)
                if (reserves > 0) repeat((reserves / 40 + 1).coerceAtMost(12)) { n ->
                    drawRect(Color(0xFF6CA8C7), Offset(w * 0.30f + (n % 6) * w * 0.075f, reserveY + n / 6 * h * 0.045f), Size(5.dp.toPx(), 5.dp.toPx()))
                }
            }
            Text("■ Heer · ● Gegner · ◆ Kavallerie · Blau: Reserve · Orange: Artillerie", color = Mist, fontSize = 11.sp)
            Text("Front und Formationen folgen dem laufenden Schlachtstand.", color = Mist, fontSize = 11.sp)
        }
    }
}
