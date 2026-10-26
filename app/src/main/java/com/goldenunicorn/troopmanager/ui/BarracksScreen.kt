package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.BarracksEngine
import com.goldenunicorn.troopmanager.engine.BarracksEngine.Station
import com.goldenunicorn.troopmanager.engine.DialogueEngine
import com.goldenunicorn.troopmanager.engine.LifeEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** The walkable barracks yard: drill sergeant, dummies, archery butts, sparring ring, horse pen, map table. */
@Composable
fun BarracksScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val layout = BarracksEngine.layout
    val placements = remember(state.life.npcs, state.day, state.life.timeSlot, state.life.provinceId) { BarracksEngine.placements(state) }
    var walk by remember { mutableStateOf<WalkRequest?>(null) }
    var walkId by remember { mutableLongStateOf(0L) }
    val pos = remember { Animatable(Offset(layout.entry.x.toFloat(), layout.entry.y.toFloat()), Offset.VectorConverter) }
    var walking by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Cell?>(null) }
    var panel by remember { mutableStateOf<String?>(null) }
    var npcLine by remember { mutableStateOf("") }
    var playerLine by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    val infinite = rememberInfiniteTransition(label = "barracks")
    val time by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "t")
    val measurer = rememberTextMeasurer()
    val latestState by rememberUpdatedState(state)
    val latestOnState by rememberUpdatedState(onState)
    val latestPlacements by rememberUpdatedState(placements)
    val playerLook = state.life.appearance ?: defaultLook(state.life.gender, state.player.species)

    fun currentCell() = Cell(pos.value.x.roundToInt(), pos.value.y.roundToInt())
    fun occupied() = latestPlacements.map { it.cell }.toSet()
    fun walkTo(cell: Cell, then: (() -> Unit)? = null) {
        val path = TavernEngine.path(currentCell(), cell, occupied(), layout)
        if (path.isEmpty()) { then?.invoke(); return }
        target = cell; walkId += 1
        walk = WalkRequest(path, then, walkId)
    }
    fun openTalk(id: String) {
        val r = DialogueEngine.open(latestState, id)
        latestOnState(r.state)
        panel = "npc:$id"; npcLine = r.npcLine; playerLine = ""; summary = ""
    }
    LaunchedEffect(walk) {
        val w = walk ?: return@LaunchedEffect
        walking = true
        for (step in w.path) pos.animateTo(Offset(step.x.toFloat(), step.y.toFloat()), tween(190, easing = LinearEasing))
        walking = false; target = null; walk = null
        w.then?.invoke()
    }
    BackHandler(enabled = panel != null) { panel = null }

    Box(Modifier.fillMaxSize().background(Ink).testTag("barracks")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kaserne", color = Gold, fontFamily = Cinzel, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${LifeEngine.locationName(state)} · ${state.life.timeSlot.label} · Übungen heute: ${BarracksEngine.sessionsLeft(state)}/${BarracksEngine.SESSIONS_PER_DAY}" +
                        if (state.life.enlisted) " · Stadtwache" else "", color = Mist, fontSize = 12.sp)
                }
                Text("${LifeEngine.money(state)} Gold", color = PaleGold, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { val r = BarracksEngine.leave(state); onState(r.state); onNotice(r.message) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 44.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) { Text("Hinaus", color = Mist, fontSize = 12.sp) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tw = constraints.maxWidth / 12f
                val th = tw * .66f
                val backH = tw * 2.2f
                val density = LocalDensity.current
                Canvas(
                    Modifier.fillMaxWidth().height(with(density) { (backH + th * 9f).toDp() })
                        .semantics { contentDescription = "Begehbarer Kasernenhof" }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val hit = latestPlacements.firstOrNull { p ->
                                    val c = Offset(tw * (p.cell.x + .5f), backH + th * (p.cell.y + .5f) - tw * 1.0f)
                                    kotlin.math.abs(tap.x - c.x) < tw * .7f && kotlin.math.abs(tap.y - c.y) < tw * 1.4f
                                }
                                val cell = Cell(floor(tap.x / tw).toInt().coerceIn(0, 11), floor((tap.y - backH) / th).toInt().coerceIn(0, 8))
                                val station = Station.entries.firstOrNull { cell in layout.stations.getValue(it) }
                                when {
                                    hit != null && hit.sergeant -> {
                                        val spot = TavernEngine.approach(hit.cell, currentCell(), occupied(), layout)
                                        if (spot == null) { panel = "sergeant" } else walkTo(spot) { panel = "sergeant" }
                                    }
                                    hit != null -> {
                                        val spot = TavernEngine.approach(hit.cell, currentCell(), occupied(), layout)
                                        if (spot == null) openTalk(hit.npc.id) else walkTo(spot) { openTalk(hit.npc.id) }
                                    }
                                    station != null -> walkTo(layout.fronts.getValue(station)) { panel = "station:${station.name}"; summary = "" }
                                    cell == layout.entry -> walkTo(layout.entry) { panel = "exit" }
                                    layout.walkable(cell) -> walkTo(cell)
                                }
                            }
                        },
                ) {
                    if (tw < 2f) return@Canvas
                    drawYard(state.life.timeSlot, tw, th, backH, time)
                    for (row in 0..8) {
                        drawYardRow(row, tw, th, backH, time)
                        placements.filter { it.cell.y == row }.forEach { p ->
                            val feet = Offset(tw * (p.cell.x + .5f), backH + th * (p.cell.y + .5f) + th * .3f)
                            drawFullFigure(p.npc.appearance, p.npc.gender, feet, tw * 2.4f / 140f, detailed = false, walkPhase = if (p.sergeant) 0f else time * .5f)
                        }
                        if (floor(pos.value.y + .5f).toInt() == row) {
                            val feet = Offset(tw * (pos.value.x + .5f), backH + th * (pos.value.y + .5f) + th * .3f)
                            drawOval(Gold.copy(alpha = .55f), Offset(feet.x - tw * .42f, feet.y - th * .2f), Size(tw * .84f, th * .42f), style = Stroke(tw * .04f))
                            drawFullFigure(playerLook, state.life.gender, feet, tw * 2.4f / 140f, detailed = false, walkPhase = if (walking) time * 8f else 0f)
                        }
                    }
                    target?.let { t -> drawCircle(Gold.copy(alpha = .5f), tw * .22f, Offset(tw * (t.x + .5f), backH + th * (t.y + .5f)), style = Stroke(tw * .035f)) }
                    drawWeather(com.goldenunicorn.troopmanager.engine.TownWeather.here(state), tw, time)
                    placements.forEach { p ->
                        val label = if (p.sergeant) p.npc.profession else if (p.npc.relation.timesMet > 0) p.npc.firstName else p.npc.profession.substringBefore(' ').take(14)
                        val m = measurer.measure(label, TextStyle(color = if (p.sergeant) PaleGold else Mist, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val top = Offset(tw * (p.cell.x + .5f) - m.size.width / 2f, backH + th * (p.cell.y + .5f) - tw * 2.7f)
                        drawRoundRect(Color.Black.copy(alpha = .55f), top - Offset(6f, 2f), Size(m.size.width + 12f, m.size.height + 4f), CornerRadius(10f))
                        drawText(m, topLeft = top)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tippe auf eine Übungsstation, auf den Ausbilder oder auf Leute.", color = Muted, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RbAction("Ausbilder") { panel = "sergeant" }
                    Station.entries.forEach { st -> RbAction(st.label) { walkTo(layout.fronts.getValue(st)) { panel = "station:${st.name}"; summary = "" } } }
                }
            }
        }
        val current = panel
        if (current != null) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.6f).navigationBarsPadding(),
                color = Color(0xF20E141B), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .25f))) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        current == "exit" -> {
                            Text("Zurück auf den Stadtplatz?", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
                            GoldButton("Kaserne verlassen", { val r = BarracksEngine.leave(state); onState(r.state); onNotice(r.message) }, Modifier.fillMaxWidth())
                            RbAction("Bleiben") { panel = null }
                        }
                        current == "sergeant" -> SergeantPanel(state, summary, onResult = { r -> onState(r.state); summary = r.message },
                            onTalk = { BarracksEngine.sergeant(state)?.let { openTalk(it.id) } }, onClose = { panel = null })
                        current.startsWith("station:") -> {
                            val st = Station.valueOf(current.removePrefix("station:"))
                            val skill = LifeEngine.skillLabel(st.skill)
                            val value = when (st.skill) { "sword" -> state.player.sword; "bow" -> state.player.bow; "riding" -> state.player.riding; "leadership" -> state.player.leadership; else -> state.player.tactics }
                            Text(st.label, color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
                            Text("Übt: $skill · derzeit $value · Zugewinn pro Übung: ${BarracksEngine.gain(value)}", color = Mist)
                            Text(if (state.life.enlisted) "Für die Stadtwache kostenlos." else "Kosten: ${LifeEngine.priced(state, BarracksEngine.TRAINING_PRICE)} Gold · eine Tageszeit.", color = Muted, fontSize = 12.sp)
                            if (summary.isNotBlank()) Text(summary, color = Gold)
                            val blocker = BarracksEngine.trainBlocker(state, st)
                            if (blocker != null) Text(blocker, color = Danger, fontSize = 13.sp)
                            GoldButton("Üben", { val r = BarracksEngine.train(state, st); onState(r.state); summary = r.message }, Modifier.fillMaxWidth())
                            RbAction("Zurück") { panel = null }
                        }
                        else -> {
                            val npc = state.life.npc(current.removePrefix("npc:"))
                            if (npc != null) ConversationPanel(state, npc, npcLine, playerLine, summary, 0, null,
                                onInnTab = {},
                                onChoose = { topic ->
                                    val reply = DialogueEngine.choose(state, npc.id, topic)
                                    npcLine = reply.npcLine.ifBlank { npcLine }; playerLine = reply.playerLine; summary = reply.summary
                                    onState(reply.state)
                                },
                                onAction = { r -> onState(r.state); summary = r.message }, onDice = {}, onClose = { panel = null })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SergeantPanel(state: GameState, summary: String, onResult: (LifeEngine.Result) -> Unit, onTalk: () -> Unit, onClose: () -> Unit) {
    val sarge = BarracksEngine.sergeant(state)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sarge != null) CharacterPortrait(sarge.appearance, sarge.gender, Modifier.size(width = 80.dp, height = 100.dp).clip(RoundedCornerShape(14.dp)), Framing.BUST)
        Column {
            Text(sarge?.name ?: "Ausbilder", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
            Text(if (state.life.enlisted) "„Wache! Haltung!“" else "„Willst du lernen oder nur gaffen?“", color = Mist, fontSize = 13.sp)
        }
    }
    if (summary.isNotBlank()) Text(summary, color = Gold)
    if (!state.life.landed) {
        if (!state.life.enlisted) {
            InfoBox("In der Stadtwache übst du kostenlos, verdienst ${BarracksEngine.GUARD_WAGE} Gold pro Schicht und steigst vom Rekruten zum Soldaten auf.")
            val blocker = BarracksEngine.enlistBlocker(state)
            if (blocker != null) Text(blocker, color = Danger, fontSize = 13.sp)
            GoldButton("In die Stadtwache eintreten", { onResult(BarracksEngine.enlist(state)) }, Modifier.fillMaxWidth())
        } else {
            val blocker = BarracksEngine.guardBlocker(state)
            if (blocker != null) Text(blocker, color = Muted, fontSize = 13.sp)
            GoldButton("Wachdienst (2 Tageszeiten, ${BarracksEngine.GUARD_WAGE} Gold)", { onResult(BarracksEngine.guardDuty(state)) }, Modifier.fillMaxWidth())
            RbAction("Dienst quittieren") { onResult(BarracksEngine.resign(state)) }
        }
    }
    RbAction("Mit dem Ausbilder reden", onClick = onTalk)
    RbAction("Zurück", onClick = onClose)
}

private fun DrawScope.drawYard(slot: TimeSlot, tw: Float, th: Float, backH: Float, time: Float) {
    val w = size.width
    val sky = when (slot) {
        TimeSlot.MORNING -> listOf(Color(0xFFF2D6A2), Color(0xFF9FC3DD)); TimeSlot.NOON -> listOf(Color(0xFF8EC3EA), Color(0xFFCFE6F5))
        TimeSlot.AFTERNOON -> listOf(Color(0xFFE9C287), Color(0xFF9DBED8)); TimeSlot.EVENING -> listOf(Color(0xFF5A3A6A), Color(0xFFE77A3C))
        TimeSlot.NIGHT -> listOf(Color(0xFF070A16), Color(0xFF1B2440))
    }
    drawRect(Brush.verticalGradient(sky, 0f, backH * .6f), size = Size(w, backH * .6f))
    // Stone wall with battlements and a gate tower.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8A8478), Color(0xFF5E584E)), backH * .35f, backH), Offset(0f, backH * .35f), Size(w, backH * .65f))
    var x = 0f
    while (x < w) { drawRect(Color(0xFF8A8478), Offset(x, backH * .22f), Size(tw * .5f, backH * .14f)); x += tw }
    for (r in 0 until 5) {
        val y = backH * .4f + r * backH * .12f
        var bx = if (r % 2 == 0) 0f else tw * .4f
        while (bx < w) { drawRect(Color.Black.copy(alpha = .14f), Offset(bx, y), Size(tw * .8f, backH * .12f), style = Stroke(1.4f)); bx += tw * .8f }
    }
    val banner = Offset(w * .5f, backH * .3f)
    drawLine(Color(0xFF3A2516), banner, banner + Offset(0f, -backH * .3f), tw * .06f)
    val wave = sin(time * 6.283f) * tw * .08f
    drawPath(Path().apply { moveTo(banner.x, banner.y - backH * .3f); lineTo(banner.x + tw * .9f + wave, banner.y - backH * .25f); lineTo(banner.x, banner.y - backH * .14f); close() }, Color(0xFF7A2430))
    // Packed earth.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7A6448), Color(0xFF9A8060)), backH, size.height), Offset(0f, backH), Size(w, size.height - backH))
    for (k in 0 until 60) {
        val px = (k * 97 % 1000) / 1000f * w
        val py = backH + (k * 53 % 1000) / 1000f * (size.height - backH)
        drawCircle(Color.Black.copy(alpha = .08f), tw * .06f, Offset(px, py))
    }
    // Sparring ring.
    val ring = Offset(tw * 6f, backH + th * 6f)
    drawOval(Color(0xFFB09870), Offset(ring.x - tw * 1.3f, ring.y - th * 1.2f), Size(tw * 2.6f, th * 2.4f))
    drawOval(Color.White.copy(alpha = .35f), Offset(ring.x - tw * 1.3f, ring.y - th * 1.2f), Size(tw * 2.6f, th * 2.4f), style = Stroke(tw * .05f))
    if (slot == TimeSlot.NIGHT || slot == TimeSlot.EVENING) drawRect(Color(0xFF05070C).copy(alpha = if (slot == TimeSlot.NIGHT) .45f else .2f))
}

private fun DrawScope.drawYardRow(row: Int, tw: Float, th: Float, backH: Float, time: Float) {
    val l = BarracksEngine.layout
    fun cell(c: Cell) = Offset(tw * (c.x + .5f), backH + th * (c.y + .5f))
    l.racks.filter { it.y == row }.forEach { r ->
        val c = cell(r)
        drawRect(Color(0xFF5A3A20), Offset(c.x - tw * .45f, c.y - tw * .9f), Size(tw * .9f, tw * .12f))
        for (k in 0..3) drawLine(Color(0xFFB8BEC6), Offset(c.x - tw * .35f + k * tw * .23f, c.y - tw * 1.1f), Offset(c.x - tw * .35f + k * tw * .23f, c.y), tw * .05f, StrokeCap.Round)
    }
    l.stations.forEach { (st, cells) ->
        cells.filter { it.y == row }.forEach { cc ->
            val c = cell(cc)
            when (st) {
                Station.DUMMIES -> {
                    drawLine(Color(0xFF5A3A20), Offset(c.x, c.y + th * .3f), Offset(c.x, c.y - tw * 1.3f), tw * .09f)
                    drawLine(Color(0xFF5A3A20), Offset(c.x - tw * .45f, c.y - tw * .95f), Offset(c.x + tw * .45f, c.y - tw * .95f), tw * .08f)
                    drawOval(Color(0xFFC9B07A), Offset(c.x - tw * .28f, c.y - tw * 1.2f), Size(tw * .56f, tw * .75f))
                    drawCircle(Color(0xFFC9B07A), tw * .2f, Offset(c.x, c.y - tw * 1.45f))
                }
                Station.BUTTS -> {
                    drawLine(Color(0xFF5A3A20), Offset(c.x - tw * .3f, c.y + th * .3f), Offset(c.x - tw * .1f, c.y - tw * .7f), tw * .06f)
                    drawLine(Color(0xFF5A3A20), Offset(c.x + tw * .3f, c.y + th * .3f), Offset(c.x + tw * .1f, c.y - tw * .7f), tw * .06f)
                    listOf(Color(0xFFE6DCC6), Color(0xFF7A2430), Color(0xFFE6DCC6), Color(0xFFB98E3A)).forEachIndexed { i, col ->
                        drawCircle(col, tw * (.45f - i * .1f), Offset(c.x, c.y - tw * .8f))
                    }
                }
                Station.PEN -> if (cc == cells.first()) {
                    val x0 = tw * 10f; val x1 = tw * 12f
                    val y0 = backH + th * 6f; val y1 = backH + th * 8f
                    drawRect(Color(0xFF8A7A50), Offset(x0, y0), Size(x1 - x0, y1 - y0))
                    for (k in 0..2) drawLine(Color(0xFF5A3A20), Offset(x0, y0 + k * th * .3f), Offset(x1, y0 + k * th * .3f), tw * .05f)
                    drawLine(Color(0xFF5A3A20), Offset(x0, y0 - th * .2f), Offset(x0, y1), tw * .07f)
                    val horse = Offset(tw * 11f + sin(time * 6.283f) * tw * .1f, backH + th * 7.6f)
                    drawOval(Color(0xFF6A4228), Offset(horse.x - tw * .55f, horse.y - tw * .95f), Size(tw * 1.1f, tw * .5f))
                    for (dx in listOf(-.4f, -.2f, .25f, .45f)) drawLine(Color(0xFF4A2E18), Offset(horse.x + dx * tw, horse.y - tw * .5f), Offset(horse.x + dx * tw, horse.y), tw * .07f)
                    drawLine(Color(0xFF6A4228), Offset(horse.x - tw * .45f, horse.y - tw * .8f), Offset(horse.x - tw * .75f, horse.y - tw * 1.25f), tw * .16f, StrokeCap.Round)
                    drawLine(Color(0xFF2A1A10), Offset(horse.x - tw * .55f, horse.y - tw * 1.1f), Offset(horse.x - tw * .35f, horse.y - tw * .85f), tw * .05f)
                }
                Station.TABLE -> if (cc == cells.first()) {
                    drawRect(Color(0xFF6E4A2C), Offset(c.x - tw * .45f, c.y - tw * .55f), Size(tw * 1.9f, tw * .4f))
                    drawRect(Color(0xFFE6DCC6), Offset(c.x - tw * .3f, c.y - tw * .62f), Size(tw * 1.5f, tw * .3f))
                    drawCircle(Color(0xFF7A2430), tw * .05f, Offset(c.x + tw * .2f, c.y - tw * .5f))
                    drawCircle(Color(0xFF2F4A6A), tw * .05f, Offset(c.x + tw * .7f, c.y - tw * .45f))
                }
                Station.RING -> Unit
            }
            Unit
        }
    }
}
