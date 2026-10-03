package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.goldenunicorn.troopmanager.engine.FrontierEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun LiveBattleScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val session = state.battleSession ?: return
    val homeFortified = FrontierEngine.isHomeFortifiedBattle(state, session)
    val wallWeapons = if (homeFortified) state.frontier.weapons.filter { it.count > 0 } else emptyList()
    var commandSection by remember { mutableStateOf(BattleSection.CENTER) }
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
        item { ContextTutorialCard(state, "battle", onState) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { BannerBadge(state.presentation.heraldry, extent = 42.dp); PageTitle("LIVE-SCHLACHT", "${session.enemyFactionName ?: session.enemyArmyName ?: session.enemy.label} · ${session.tactic.label}") } }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Minute ${session.minute} · ${session.phase.label}", color = PaleGold, fontWeight = FontWeight.Bold)
                    BattleStrength("Dein Heer", session.ownRemaining, session.ownStart, Gold)
                    BattleStrength("Gegner", session.enemyRemaining, session.enemyStart, Danger)
                    Text("Moral ${session.morale} % · ${MoraleState.from(session.morale).label} · ${session.soldiers(BattleSection.RESERVE)} in Reserve", color = Mist)
                    Text("Befehlspunkte ${session.commandPoints} / ${session.maxCommandPoints} · +${BattleEngine.commandRegeneration(state, session)} je Austausch", color = PaleGold)
                    if (session.enemyFortification > 0) Text("Gegnerische Befestigung ${session.enemyFortification} % · Leiter, Rammbock, Turm oder Tunnel öffnen Wege.", color = PaleGold, fontSize = 12.sp)
                    Text("${session.fightingRemaining} kämpfen · ${session.ownRemaining - session.fightingRemaining} auf der Flucht · ${session.participation.label}", color = Mist, fontSize = 12.sp)
                    if (session.tactic == Tactic.FORTIFY) {
                        Text("Mauerzustand ${session.wallIntegrity} %", color = if (session.wallIntegrity < 30) Danger else PaleGold)
                        Text(if (session.devices.isEmpty()) "Keine feindlichen Belagerungsgeräte mehr" else session.devices.joinToString(" · ") { it.label }, color = Mist, fontSize = 12.sp)
                    }
                    if (state.resources.food == 0) Text("Hunger schwächt die Kampfkraft und senkt die Moral.", color = Danger)
                }
            }
        }
        if (session.minute == 0 && session.isActive) item {
            WarFormationPanel(state, onState, onNotice)
            if (state.world.encounter == null) GoldButton("Truppenzahlen vor Kampfbeginn anpassen", { showSetup = true }, Modifier.fillMaxWidth())
            else Text("Die Expedition stellt nur ihre anwesenden Soldaten auf; Verstärkung muss zuerst zum Schlachtort marschieren.", color = Mist, fontSize = 12.sp)
        }
        if (homeFortified) item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column {
                    CityScene(state, Modifier.fillMaxWidth().height(220.dp), season = state.city.season, defenseMode = true, wallIntegrity = session.wallIntegrity)
                    Text("Deine Stadt unter Belagerung · Tor und Mauern ${session.wallIntegrity} %", color = PaleGold, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item { SessionBattleField(session, state.settings.animations, state.settings.battleSpeed, wallWeapons) }
        if (session.isActive && session.pendingEvent == null) item {
            SelectionMenu("Befehlsabschnitt", commandSection.label, BattleSection.entries.filterNot { it == BattleSection.RESERVE }, { it.label }) { commandSection = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(BattleDecision.ADVANCE, BattleDecision.HOLD, BattleDecision.RETREAT_LINE, BattleDecision.SEND_RESERVE, BattleDecision.STRENGTHEN_SECTION, BattleDecision.CAVALRY_CHARGE, BattleDecision.ARROW_VOLLEY, BattleDecision.FOCUS_FIRE, BattleDecision.ARTILLERY_TARGET, BattleDecision.HOLD_GATE, BattleDecision.OPEN_GATE, BattleDecision.RALLY, BattleDecision.FEIGNED_RETREAT, BattleDecision.ORDERED_RETREAT, BattleDecision.SCALE_WALL, BattleDecision.BREACH_GATE, BattleDecision.TOWER_ASSAULT, BattleDecision.UNDERMINE).filter { BattleEngine.canOrder(session, it, commandSection) }.forEach { order ->
                    OutlinedButton(enabled = session.commandPoints >= BattleEngine.orderCost(session, order), onClick = { val result = BattleEngine.order(state, order, commandSection); onState(result.state); onNotice(result.message) }) { Text("${order.label} (${BattleEngine.orderCost(session, order)})") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT).forEach { section ->
                    val front = session.fronts.firstOrNull { it.section == section }
                    Surface(modifier = Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(section.label, color = PaleGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${session.soldiers(section)} eigene", color = Gold, fontSize = 12.sp)
                            Text("${front?.enemySoldiers ?: 0} Gegner", color = Danger, fontSize = 12.sp)
                            Text(session.terrain[section]?.label ?: "Ebene", color = PaleGold, fontSize = 11.sp)
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
                    Text("${contingent.displayName ?: contingent.type.label} · ${contingent.soldiers} / ${contingent.startSoldiers}", color = PaleGold, fontWeight = FontWeight.Bold)
                    Text("$commander · ${contingent.section.label}", color = Mist, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Moral ${contingent.morale} % · ${MoraleState.from(contingent.morale, contingent.routed).label}", color = if (contingent.morale < 35) Danger else Success, fontSize = 12.sp)
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
                            OutlinedButton(enabled = session.commandPoints >= BattleEngine.orderCost(session, option), onClick = { advance(option) }, modifier = Modifier.fillMaxWidth()) { Text("${option.label} · ${BattleEngine.orderCost(session, option)} Punkte") }
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
                        Text("Heer: ${session.ownStart} zu Beginn · ${session.ownRemaining} einsatzfähige Überlebende", color = Color.White)
                        Text("${session.casualties.dead} gefallen · ${session.casualties.wounded} verwundet · ${session.casualties.missing} vermisst · ${session.casualties.captured} gefangen", color = Mist)
                        Text("Gegner: ${session.enemyStart} zu Beginn · ${session.enemyRemaining} verblieben · ${session.enemyStart - session.enemyRemaining} Verluste", color = Mist)
                        Text("+${session.xpReward} Spieler-XP · +${session.renownReward} Ruhm", color = PaleGold)
                        Text("Moral der Überlebenden: ${session.morale} % · Ausrüstungsverschleiß: ${session.equipmentDamage} Punkte im eingesetzten Heer", color = Mist)
                        Text("Beute: ${session.lootGold} Gold · ${session.lootFood} Nahrung", color = PaleGold)
                        session.contingents.groupBy { it.type }.forEach { (type, troops) ->
                            val initial = troops.sumOf { it.startSoldiers }
                            val survivors = troops.sumOf { it.soldiers }
                            Text("${type.label}: $initial → $survivors · ${initial - survivors} Ausfälle", color = Mist, fontSize = 12.sp)
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
internal fun SessionBattleField(session: BattleSession, animations: Boolean, battleSpeed: Float, wallWeapons: List<WallWeaponStock> = emptyList()) {
    val visualGroups = remember(session) { BattleEngine.visualGroups(session) }
    val transition = rememberInfiniteTransition(label = "battle-exchange")
    val animated by transition.animateFloat(0f, 1f, infiniteRepeatable(tween((1500 / battleSpeed.coerceIn(0.5f, 3f)).toInt()), RepeatMode.Restart), label = "battle-motion")
    val pulse = if (animations && session.isActive && session.minute > 0) animated else 0.5f
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GEFECHTSKARTE", color = PaleGold, fontWeight = FontWeight.Bold)
            Text("LINKER FLÜGEL       ZENTRUM       RECHTER FLÜGEL", color = Mist, fontSize = 10.sp)
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFF202A22))
                listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT).forEachIndexed { index, section ->
                    val terrainColor = when (session.terrain[section]) {
                        BattleTerrain.FOREST -> Color(0xFF263F29)
                        BattleTerrain.RIVER, BattleTerrain.BRIDGE -> Color(0xFF264958)
                        BattleTerrain.HILL, BattleTerrain.PASS -> Color(0xFF514B3A)
                        BattleTerrain.MUD -> Color(0xFF493C2C)
                        BattleTerrain.WALL, BattleTerrain.STREET -> Color(0xFF474844)
                        else -> Color(0xFF34432E)
                    }
                    drawRect(terrainColor, Offset(w * index / 3f, 0f), Size(w / 3f, h * 0.83f))
                }
                if (session.participation == BattleParticipation.PERSONAL) {
                    drawCircle(Color.White, 6.dp.toPx(), Offset(w * 0.5f, h * 0.64f), style = Stroke(2.dp.toPx()))
                }
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
                    val ownMarkers = visualGroups.count { !it.enemy && it.section == section }
                    repeat(ownMarkers) { n ->
                        val marker = Offset(x + (n % 10 - 4.5f) * w * 0.025f, y + h * 0.06f + (n / 10) * h * 0.20f / ((ownMarkers + 9) / 10).coerceAtLeast(1))
                        drawRect(if (visualGroups.filter { !it.enemy && it.section == section }.getOrNull(n)?.routed == true) Color(0xFFE6B77C) else Gold, marker - Offset(3.dp.toPx(), 3.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx()))
                        drawLine(PaleGold, marker + Offset(-2.dp.toPx(), -4.dp.toPx()), marker + Offset(2.dp.toPx(), -4.dp.toPx()), 1.dp.toPx())
                    }
                    val enemies = front?.enemySoldiers ?: 0
                    val enemyMarkers = visualGroups.count { it.enemy && it.section == section }
                    repeat(enemyMarkers) { n ->
                        drawCircle(Danger, 3.dp.toPx(), Offset(x + (n % 10 - 4.5f) * w * 0.025f, y - h * 0.06f - (n / 10) * h * 0.20f / ((enemyMarkers + 9) / 10).coerceAtLeast(1)))
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
                    wallWeapons.groupBy { it.section }.forEach { (section, stocks) ->
                        val sectionIndex = when (section) { BattleSection.LEFT -> 0; BattleSection.RIGHT -> 2; else -> 1 }
                        stocks.forEachIndexed { index, weapon ->
                            val position = Offset(w * (sectionIndex + (index + 1f) / (stocks.size + 1f)) / 3f, h * .80f)
                            val ready = weapon.integrity > 0 && weapon.ammunition > 0 && weapon.reloadRounds == 0
                            val color = if (weapon.integrity <= 0) Danger else if (!ready) Mist else PaleGold
                            drawWallWeapon(weapon.type, position, 9.dp.toPx(), color)
                            if (ready && session.isActive && session.minute > 0 && session.phase == BattlePhase.RANGED) {
                                val target = position + Offset(0f, -h * .35f * pulse)
                                if (weapon.type == WallWeaponType.BLACK_POWDER)
                                    drawCircle(Color(0xFFBCB4A0).copy(alpha = (1f - pulse) * .6f), (3 + pulse * 12).dp.toPx(), position + Offset(0f, -12.dp.toPx()))
                                else if (weapon.type == WallWeaponType.CRANE_WINCH) {
                                    drawLine(Blue, position + Offset(0f, -20.dp.toPx()), position + Offset(8.dp.toPx(), 13.dp.toPx() + pulse * 12.dp.toPx()), 1.dp.toPx())
                                    drawCircle(Gold, 2.dp.toPx(), position + Offset(8.dp.toPx(), 13.dp.toPx() + pulse * 12.dp.toPx()))
                                } else drawLine(if (weapon.type == WallWeaponType.FIRE_OIL) Danger else PaleGold, target, target + Offset(0f, -7.dp.toPx()), 2.dp.toPx())
                            }
                        }
                    }
                    session.devices.forEachIndexed { n, device ->
                        val siegeX = w * (n + 1f) / (session.devices.size + 1f)
                        val height = if (device == SiegeDevice.TOWER) 24.dp.toPx() else 12.dp.toPx()
                        drawRect(Color(0xFF9A7346), Offset(siegeX - 4.dp.toPx(), h * 0.055f), Size(8.dp.toPx(), height))
                        drawLine(Color(0xFFD0A56A), Offset(siegeX - 7.dp.toPx(), h * 0.055f + height), Offset(siegeX + 7.dp.toPx(), h * 0.055f + height), 3.dp.toPx())
                    }
                }
                val reserves = session.soldiers(BattleSection.RESERVE)
                if (reserves > 0) repeat(visualGroups.count { !it.enemy && it.section == BattleSection.RESERVE }) { n ->
                    drawRect(Color(0xFF6CA8C7), Offset(w * 0.25f + (n % 10) * w * 0.05f, reserveY + (n / 10) * h * 0.075f / ((visualGroups.count { !it.enemy && it.section == BattleSection.RESERVE } + 9) / 10).coerceAtLeast(1)), Size(5.dp.toPx(), 5.dp.toPx()))
                }
            }
            Text("■ Heer · ● Gegner · ◆ Kavallerie · Blau: Reserve · Orange: Artillerie", color = Mist, fontSize = 11.sp)
            wallWeapons.forEach { weapon ->
                Text("${weapon.type.label} ×${weapon.count} · ${weapon.section.label} · ${weapon.ammunition} Ladungen · Zustand ${weapon.integrity}%" +
                    if (weapon.reloadRounds > 0) " · Nachladen ${weapon.reloadRounds} Austausch" else if (weapon.ammunition == 0) " · Munition erschöpft" else "",
                    color = if (weapon.integrity <= 0 || weapon.ammunition == 0) Danger else Gold, fontSize = 11.sp)
            }
            Text("${visualGroups.size} sichtbare Gruppen · ${visualGroups.minOfOrNull { it.soldiers } ?: 0}–${visualGroups.maxOfOrNull { it.soldiers } ?: 0} Soldaten je Gruppe · weiße Markierung: persönliche Teilnahme.", color = Mist, fontSize = 11.sp)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWallWeapon(type: WallWeaponType, position: Offset, radius: Float, color: Color) {
    val x = position.x
    val y = position.y
    drawRect(Color(0xFF594735), position + Offset(-radius * .7f, -radius * .3f), Size(radius * 1.4f, radius * .6f))
    when (type) {
        WallWeaponType.BALLISTA, WallWeaponType.REPEATER -> {
            drawLine(color, Offset(x, y + radius * .3f), Offset(x, y - radius * 1.5f), radius * .25f)
            drawLine(color, Offset(x - radius, y - radius * .5f), Offset(x + radius, y - radius * .5f), radius * .25f)
            drawLine(color, Offset(x - radius, y - radius * .5f), Offset(x, y - radius), radius * .12f)
            drawLine(color, Offset(x + radius, y - radius * .5f), Offset(x, y - radius), radius * .12f)
            if (type == WallWeaponType.REPEATER) drawRect(Blue, Offset(x - radius * .3f, y - radius * 1.1f), Size(radius * .6f, radius * .5f))
        }
        WallWeaponType.BLACK_POWDER -> {
            drawLine(color, Offset(x, y), Offset(x, y - radius * 1.5f), radius * .6f)
            drawCircle(Ink, radius * .23f, Offset(x, y - radius * 1.5f))
            drawCircle(color, radius * .25f, Offset(x - radius * .5f, y + radius * .25f))
            drawCircle(color, radius * .25f, Offset(x + radius * .5f, y + radius * .25f))
        }
        WallWeaponType.FIRE_OIL -> {
            drawOval(color, Offset(x - radius * .65f, y - radius * .7f), Size(radius * 1.3f, radius * .65f))
            drawLine(Danger, Offset(x, y - radius), Offset(x + radius * .3f, y - radius * 1.7f), radius * .3f)
            drawLine(Gold, Offset(x + radius * .3f, y - radius * 1.7f), Offset(x + radius * .4f, y - radius * 1.1f), radius * .2f)
        }
        WallWeaponType.CRANE_WINCH -> {
            drawLine(Blue, Offset(x - radius * .6f, y), Offset(x - radius * .6f, y - radius * 2f), radius * .25f)
            drawLine(Blue, Offset(x - radius * .6f, y - radius * 2f), Offset(x + radius, y - radius * 2f), radius * .25f)
            drawLine(color, Offset(x + radius * .6f, y - radius * 2f), Offset(x + radius * .6f, y + radius * .8f), radius * .12f)
            drawCircle(Gold, radius * .22f, Offset(x + radius * .6f, y + radius * .8f))
        }
    }
}
