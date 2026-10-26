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
import com.goldenunicorn.troopmanager.engine.CastleEngine
import com.goldenunicorn.troopmanager.engine.DialogueEngine
import com.goldenunicorn.troopmanager.engine.LifeEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** The walkable castle courtyard: the lord on the dais, guards, banners, the tournament lists. */
@Composable
fun CastleScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, initialPanel: String? = null) {
    val figures = rememberFigureArt(state)
    val layout = CastleEngine.layout
    val placements = remember(state.life.npcs, state.day, state.life.timeSlot, state.life.provinceId) { CastleEngine.placements(state) }
    var walk by remember { mutableStateOf<WalkRequest?>(null) }
    var walkId by remember { mutableLongStateOf(0L) }
    val pos = remember { Animatable(Offset(layout.entry.x.toFloat(), layout.entry.y.toFloat()), Offset.VectorConverter) }
    var walking by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Cell?>(null) }
    var panel by remember { mutableStateOf(initialPanel) }
    var npcLine by remember { mutableStateOf("") }
    var playerLine by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    val infinite = rememberInfiniteTransition(label = "castle")
    val time by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "t")
    val measurer = rememberTextMeasurer()
    val latestState by rememberUpdatedState(state)
    val latestOnState by rememberUpdatedState(onState)
    val latestPlacements by rememberUpdatedState(placements)
    val playerLook = state.life.appearance ?: defaultLook(state.life.gender, state.player.species)
    val tournament = CastleEngine.tournamentToday(state)

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

    Box(Modifier.fillMaxSize().background(Ink).testTag("castle")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Burghof", color = Gold, fontFamily = Cinzel, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${LifeEngine.locationName(state)} · ${state.life.timeSlot.label}" + (if (tournament) " · Turniertag" else "") +
                        (if (state.life.knighted) " · Ritter" else ""), color = Mist, fontSize = 12.sp)
                }
                Text("${LifeEngine.money(state)} Gold", color = PaleGold, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { val r = CastleEngine.leave(state); onState(r.state); onNotice(r.message) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 44.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) { Text("Hinaus", color = Mist, fontSize = 12.sp) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tw = constraints.maxWidth / 12f
                val th = tw * .66f
                val backH = tw * 2.6f
                val density = LocalDensity.current
                Canvas(
                    Modifier.fillMaxWidth().height(with(density) { (backH + th * 9f).toDp() })
                        .semantics { contentDescription = "Begehbarer Burghof" }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val hit = latestPlacements.firstOrNull { p ->
                                    val c = Offset(tw * (p.cell.x + .5f), backH + th * (p.cell.y + .5f) - tw * 1.0f)
                                    kotlin.math.abs(tap.x - c.x) < tw * .7f && kotlin.math.abs(tap.y - c.y) < tw * 1.4f
                                }
                                val cell = Cell(floor(tap.x / tw).toInt().coerceIn(0, 11), floor((tap.y - backH) / th).toInt().coerceIn(0, 8))
                                when {
                                    hit != null && hit.lord -> walkTo(layout.audience) { panel = "lord"; summary = "" }
                                    hit != null -> {
                                        val spot = TavernEngine.approach(hit.cell, currentCell(), occupied(), layout)
                                        if (spot == null) openTalk(hit.npc.id) else walkTo(spot) { openTalk(hit.npc.id) }
                                    }
                                    cell in layout.dais || cell == layout.lordSpot -> walkTo(layout.audience) { panel = "lord"; summary = "" }
                                    cell in layout.lists -> walkTo(layout.listsFront) { panel = "lists"; summary = "" }
                                    cell == layout.entry -> walkTo(layout.entry) { panel = "exit" }
                                    layout.walkable(cell) -> walkTo(cell)
                                }
                            }
                        },
                ) {
                    if (tw < 2f) return@Canvas
                    drawCourtyard(state.life.timeSlot, tw, th, backH, time, tournament)
                    for (row in 0..8) {
                        drawCourtRow(row, tw, th, backH, time, tournament)
                        placements.filter { it.cell.y == row }.forEach { p ->
                            val feet = Offset(tw * (p.cell.x + .5f), backH + th * (p.cell.y + .5f) + th * .3f)
                            drawFullFigure(p.npc.appearance, p.npc.gender, feet, tw * 2.4f / 140f, detailed = false, seated = p.lord, customImage = figures[p.npc.appearance.customImageId])
                        }
                        if (floor(pos.value.y + .5f).toInt() == row) {
                            val feet = Offset(tw * (pos.value.x + .5f), backH + th * (pos.value.y + .5f) + th * .3f)
                            drawOval(Gold.copy(alpha = .55f), Offset(feet.x - tw * .42f, feet.y - th * .2f), Size(tw * .84f, th * .42f), style = Stroke(tw * .04f))
                            drawFullFigure(playerLook, state.life.gender, feet, tw * 2.4f / 140f, detailed = false, walkPhase = if (walking) time * 8f else 0f, customImage = figures[playerLook.customImageId])
                        }
                    }
                    target?.let { t -> drawCircle(Gold.copy(alpha = .5f), tw * .22f, Offset(tw * (t.x + .5f), backH + th * (t.y + .5f)), style = Stroke(tw * .035f)) }
                    drawWeather(com.goldenunicorn.troopmanager.engine.TownWeather.here(state), tw, time)
                    if (state.life.timeSlot == TimeSlot.NIGHT || state.life.timeSlot == TimeSlot.EVENING)
                        drawRect(Color(0xFF05070C).copy(alpha = if (state.life.timeSlot == TimeSlot.NIGHT) .45f else .18f))
                    placements.forEach { p ->
                        val label = if (p.lord) p.npc.profession else if (p.npc.relation.timesMet > 0) p.npc.firstName else p.npc.profession.substringBefore(' ').take(14)
                        val m = measurer.measure(label, TextStyle(color = if (p.lord) PaleGold else Mist, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val top = Offset(tw * (p.cell.x + .5f) - m.size.width / 2f, backH + th * (p.cell.y + .5f) - tw * (if (p.lord) 2.3f else 2.7f))
                        drawRoundRect(Color.Black.copy(alpha = .55f), top - Offset(6f, 2f), Size(m.size.width + 12f, m.size.height + 4f), CornerRadius(10f))
                        drawText(m, topLeft = top)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (CastleEngine.open(state)) "Tippe auf den Burgvogt, auf die Turnierschranken oder auf Leute." else "Nachts ist der Burghof verschlossen.", color = Muted, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RbAction("Audienz") { walkTo(layout.audience) { panel = "lord"; summary = "" } }
                    RbAction(if (tournament) "Turnier!" else "Turnierschranken") { walkTo(layout.listsFront) { panel = "lists"; summary = "" } }
                }
            }
        }
        val current = panel
        if (current != null) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.62f).navigationBarsPadding(),
                color = Color(0xF20E141B), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .25f))) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        current == "exit" -> {
                            Text("Zurück auf den Stadtplatz?", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
                            GoldButton("Burghof verlassen", { val r = CastleEngine.leave(state); onState(r.state); onNotice(r.message) }, Modifier.fillMaxWidth())
                            RbAction("Bleiben") { panel = null }
                        }
                        current == "lord" -> LordPanel(state, summary, onResult = { r -> onState(r.state); summary = r.message },
                            onTalk = { CastleEngine.lord(state)?.let { openTalk(it.id) } }, onClose = { panel = null })
                        current == "lists" -> ListsPanel(state, summary, onResult = { r -> onState(r.state); summary = r.message }, onClose = { panel = null })
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
private fun LordPanel(state: GameState, summary: String, onResult: (LifeEngine.Result) -> Unit, onTalk: () -> Unit, onClose: () -> Unit) {
    val lord = CastleEngine.lord(state)
    val life = state.life
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (lord != null) CharacterPortrait(lord.appearance, lord.gender, Modifier.size(width = 80.dp, height = 100.dp).clip(RoundedCornerShape(14.dp)), Framing.BUST)
        Column {
            Text(lord?.name ?: "Burgvogt", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
            Text(lord?.profession ?: "", color = Muted, fontSize = 12.sp)
            Text(if (life.knighted) "„Ritter. Was führt Euch zu mir?“" else "„Sprecht, aber fasst Euch kurz.“", color = Mist, fontSize = 13.sp)
        }
    }
    if (summary.isNotBlank()) Text(summary, color = Gold)
    if (life.landed) {
        InfoBox("Als Herr${if (life.gender == Gender.FEMALE) "in" else ""} der Grenzfeste bist du hier unter Gleichen. Einmal pro Woche kannst du Hoftag halten: " +
            "${lord?.firstName ?: "Der Burgvogt"} schickt dann Freiwillige an die Grenze – mehr, je besser ihr steht und wenn du Ritter bist.")
        val cb = CastleEngine.courtBlocker(state)
        if (cb != null) Text(cb, color = Muted, fontSize = 13.sp)
        else GoldButton("Hoftag halten (≈ ${CastleEngine.courtVolunteers(state)} Freiwillige)", { onResult(CastleEngine.courtDay(state)) }, Modifier.fillMaxWidth())
    }
    val blocker = if (life.landed) null else CastleEngine.audienceBlocker(state)
    if (blocker != null) Text(blocker, color = Danger, fontSize = 13.sp)
    val quest = life.lordQuest
    if (quest != null) {
        val name = CrownCatalog.info(quest.targetProvinceId).name
        InfoBox("Lehnsauftrag: $name von den Räuberbaronen befreien. ${quest.enemyCount} Räuber, Stärke ${quest.enemyPower}. Lohn ${quest.reward} Gold. " +
            "Ein Sieg macht $name auf der Kronkarte wieder frei.")
        val odds = remember(state.life.lordQuest, state.life.party, state.life.gear, state.life.wound, state.player.sword) { CastleEngine.questOdds(state) ?: 0 }
        Text("Aussichten: ${com.goldenunicorn.troopmanager.engine.SkirmishEngine.oddsLabel(odds)} ($odds %)" +
            if (odds < 40) " – mehr Gefolge, bessere Ausrüstung oder Training helfen." else "", color = if (odds >= 60) Gold else if (odds >= 40) Mist else Danger, fontSize = 13.sp)
        CastleEngine.questBlocker(state)?.let { Text(it, color = Muted, fontSize = 12.sp) }
        GoldButton("Nach $name reiten (2 Tageszeiten)", { onResult(CastleEngine.ride(state)) }, Modifier.fillMaxWidth())
    } else if (blocker == null && !life.landed) {
        GoldButton("Um einen Auftrag bitten", { onResult(CastleEngine.askForTask(state)) }, Modifier.fillMaxWidth())
    }
    if (!life.landed) {
        Text("Taten für die Krone: ${life.lordQuestsDone}/${CastleEngine.KNIGHT_QUESTS} · Ruf ${life.renown}/${CastleEngine.KNIGHT_RENOWN}", color = Mist, fontSize = 12.sp)
        if (!life.knighted) {
            val kb = CastleEngine.knightBlocker(state)
            if (kb == null) GoldButton("Um den Ritterschlag bitten", { onResult(CastleEngine.knight(state)) }, Modifier.fillMaxWidth())
            else Text("Ritterschlag: $kb", color = Muted, fontSize = 12.sp)
            Text("Als Ritter verlangt die Krone für die Grenzfeste nur ${CastleEngine.KNIGHT_KEEP_GOLD} statt ${LifeEngine.KEEP_GOLD} Gold.", color = Muted, fontSize = 11.sp)
        }
    }
    RbAction("Mit ${lord?.firstName ?: "dem Burgvogt"} reden", onClick = onTalk)
    RbAction("Zurück", onClick = onClose)
}

@Composable
private fun ListsPanel(state: GameState, summary: String, onResult: (LifeEngine.Result) -> Unit, onClose: () -> Unit) {
    val life = state.life
    Text("Turnierschranken", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
    if (summary.isNotBlank()) Text(summary, color = Gold)
    InfoBox("Drei Zweikämpfe gegen immer stärkere Ritter, ausgefochten mit stumpfen Waffen. Startgeld ${CastleEngine.TOURNAMENT_FEE} Gold, " +
        "dem Sieger winken 60 Gold und Ruhm. Wer verliert, ist für den Tag ausgeschieden.")
    val run = life.tournament?.takeIf { it.day == state.day }
    if (run != null) {
        Text(when {
            run.lost -> "Ausgeschieden in Runde ${run.round + 1}."
            run.round >= 3 -> "Turniersieger!"
            else -> "Runde ${run.round + 1}/3 gegen ${run.opponents.getOrNull(run.round) ?: "?"}"
        }, color = Mist)
    }
    Text("Turniersiege bisher: ${life.tournamentsWon}", color = Muted, fontSize = 12.sp)
    if (CastleEngine.tournamentToday(state)) {
        val odds = remember(state.life.tournament, state.life.gear, state.life.wound, state.player.sword) { CastleEngine.boutOdds(state) }
        Text("Aussichten im nächsten Gang: ${com.goldenunicorn.troopmanager.engine.SkirmishEngine.oddsLabel(odds)} ($odds %)", color = if (odds >= 60) Gold else Mist, fontSize = 13.sp)
    }
    val blocker = CastleEngine.tournamentBlocker(state)
    if (blocker != null) Text(blocker, color = Muted, fontSize = 13.sp)
    else if (run == null || (!run.lost && run.round < 3))
        GoldButton(if (run == null) "Antreten (${CastleEngine.TOURNAMENT_FEE} Gold)" else "Nächste Runde", { onResult(CastleEngine.joinTournament(state)) }, Modifier.fillMaxWidth())
    RbAction("Zurück", onClick = onClose)
}

private fun DrawScope.drawCourtyard(slot: TimeSlot, tw: Float, th: Float, backH: Float, time: Float, tournament: Boolean) {
    val w = size.width
    val sky = when (slot) {
        TimeSlot.MORNING -> listOf(Color(0xFFF2D6A2), Color(0xFF9FC3DD)); TimeSlot.NOON -> listOf(Color(0xFF8EC3EA), Color(0xFFCFE6F5))
        TimeSlot.AFTERNOON -> listOf(Color(0xFFE9C287), Color(0xFF9DBED8)); TimeSlot.EVENING -> listOf(Color(0xFF5A3A6A), Color(0xFFE77A3C))
        TimeSlot.NIGHT -> listOf(Color(0xFF070A16), Color(0xFF1B2440))
    }
    drawRect(Brush.verticalGradient(sky, 0f, backH * .5f), size = Size(w, backH * .5f))
    // The keep: a tall central tower flanked by the curtain wall.
    val stoneTop = Color(0xFF9A9284); val stoneLow = Color(0xFF625B50)
    drawRect(Brush.verticalGradient(listOf(stoneTop, stoneLow), backH * .3f, backH), Offset(0f, backH * .3f), Size(w, backH * .7f))
    var x = 0f
    while (x < w) { drawRect(stoneTop, Offset(x, backH * .18f), Size(tw * .5f, backH * .13f)); x += tw }
    val keepL = w * .36f; val keepR = w * .64f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFA8A090), stoneLow), 0f, backH), Offset(keepL, backH * .02f), Size(keepR - keepL, backH * .98f))
    var kx = keepL
    while (kx < keepR) { drawRect(Color(0xFFA8A090), Offset(kx, -backH * .06f), Size(tw * .4f, backH * .1f)); kx += tw * .75f }
    for (r in 0 until 7) {
        val y = backH * .12f + r * backH * .12f
        var bx = if (r % 2 == 0) keepL else keepL + tw * .4f
        while (bx < keepR) { drawRect(Color.Black.copy(alpha = .12f), Offset(bx, y), Size(tw * .8f, backH * .12f), style = Stroke(1.4f)); bx += tw * .8f }
    }
    // Great door with a warm glow.
    val door = Offset(w * .5f, backH)
    drawPath(Path().apply {
        moveTo(door.x - tw * .7f, door.y); lineTo(door.x - tw * .7f, door.y - backH * .32f)
        quadraticTo(door.x, door.y - backH * .55f, door.x + tw * .7f, door.y - backH * .32f); lineTo(door.x + tw * .7f, door.y); close()
    }, Color(0xFF3A2516))
    // Windows.
    for (wx in listOf(.42f, .58f)) drawRoundRect(Color(0xFFE8B860).copy(alpha = if (slot >= TimeSlot.EVENING) .85f else .35f),
        Offset(w * wx - tw * .15f, backH * .25f), Size(tw * .3f, backH * .16f), CornerRadius(tw * .15f))
    // Flagpole on the keep.
    val pole = Offset(w * .5f, -backH * .06f)
    drawLine(Color(0xFF3A2516), pole, pole + Offset(0f, backH * .2f), tw * .05f)
    // Paved courtyard.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8C8370), Color(0xFFA89E88)), backH, size.height), Offset(0f, backH), Size(w, size.height - backH))
    for (row in 0 until 9) {
        val y = backH + row * th
        drawLine(Color.Black.copy(alpha = .08f), Offset(0f, y), Offset(w, y), 1.2f)
        var px = if (row % 2 == 0) 0f else tw * .5f
        while (px < w) { drawLine(Color.Black.copy(alpha = .07f), Offset(px, y), Offset(px, y + th), 1.2f); px += tw }
    }
    // Carpet from the gate to the dais.
    drawRect(Color(0xFF7A2430).copy(alpha = .75f), Offset(tw * 5.6f, backH + th * 2f), Size(tw * .8f, th * 7f))
    if (tournament) {
        // Bunting across the yard.
        val colors = listOf(Color(0xFF7A2430), Color(0xFFB98E3A), Color(0xFF2F4A6A))
        for (k in 0 until 12) {
            val bx = tw * (k + .5f); val by = backH * .55f + sin(k * .9f) * tw * .1f
            drawPath(Path().apply { moveTo(bx - tw * .3f, by); lineTo(bx + tw * .3f, by); lineTo(bx, by + tw * .4f + sin(time * 6.283f + k) * tw * .05f); close() }, colors[k % 3])
        }
    }
}

private fun DrawScope.drawCourtRow(row: Int, tw: Float, th: Float, backH: Float, time: Float, tournament: Boolean) {
    val l = CastleEngine.layout
    fun cell(c: Cell) = Offset(tw * (c.x + .5f), backH + th * (c.y + .5f))
    if (row == l.dais.first().y) {
        val a = cell(l.dais.first()); val b = cell(l.dais.last())
        drawRect(Color(0xFF6E5A44), Offset(a.x - tw * .5f, a.y - th * .5f), Size(b.x - a.x + tw, th * 1.6f))
        drawRect(Color(0xFF7A2430), Offset(a.x - tw * .4f, a.y - th * .4f), Size(b.x - a.x + tw * .8f, th * 1.4f))
    }
    if (row == l.lordSpot.y) {
        // The throne: tall carved back behind the seated lord.
        val c = cell(l.lordSpot)
        drawRoundRect(Color(0xFF4A2E18), Offset(c.x - tw * .55f, c.y - tw * 2.1f), Size(tw * 1.1f, tw * 2.2f), CornerRadius(tw * .2f))
        drawRoundRect(Color(0xFF7A2430), Offset(c.x - tw * .4f, c.y - tw * 1.9f), Size(tw * .8f, tw * 1.6f), CornerRadius(tw * .15f))
        drawCircle(ArtPaletteGold, tw * .12f, Offset(c.x, c.y - tw * 2.1f))
    }
    l.banners.filter { it.y == row }.forEach { bc ->
        val c = cell(bc)
        drawLine(Color(0xFF3A2516), Offset(c.x, c.y + th * .3f), Offset(c.x, c.y - tw * 2.2f), tw * .08f)
        val wave = sin(time * 6.283f + bc.x) * tw * .05f
        drawPath(Path().apply {
            moveTo(c.x - tw * .35f, c.y - tw * 2.1f); lineTo(c.x + tw * .35f, c.y - tw * 2.1f)
            lineTo(c.x + tw * .35f + wave, c.y - tw * .9f); lineTo(c.x + wave, c.y - tw * 1.15f); lineTo(c.x - tw * .35f + wave, c.y - tw * .9f); close()
        }, Color(0xFF7A2430))
        drawCircle(ArtPaletteGold, tw * .12f, Offset(c.x + wave * .5f, c.y - tw * 1.6f))
    }
    l.guards.filter { it.y == row }.forEach { g ->
        val c = cell(g)
        val guard = CharacterAppearance(armor = 4, weapon = 2, cloak = 1, primaryColor = 0, accentColor = 1).normalized()
        drawFullFigure(guard, Gender.MALE, Offset(c.x, c.y + th * .3f), tw * 2.4f / 140f, detailed = false)
    }
    if (row == l.tables.first().y || row == l.tables.last().y) l.tables.filter { it.y == row }.let { cells ->
        if (cells.isEmpty()) return@let
        val a = cell(cells.first()); val b = cell(cells.last())
        drawRect(Color(0xFF6E4A2C), Offset(a.x - tw * .45f, a.y - tw * .45f), Size(b.x - a.x + tw * .9f, tw * .45f))
        drawRect(Color(0xFFE6DCC6).copy(alpha = .8f), Offset(a.x - tw * .4f, a.y - tw * .5f), Size(b.x - a.x + tw * .8f, tw * .12f))
        drawCircle(Color(0xFFB98E3A), tw * .08f, Offset(a.x, a.y - tw * .5f))
        drawCircle(Color(0xFFB98E3A), tw * .08f, Offset(b.x, b.y - tw * .5f))
    }
    if (row == l.lists.first().y) {
        // The tilt barrier for jousts; tents when there is a tournament.
        val a = cell(l.lists.first()); val b = cell(l.lists.last())
        drawRect(Color(0xFF5A3A20), Offset(a.x - tw * .5f, a.y - tw * .55f), Size(b.x - a.x + tw, tw * .14f))
        drawRect(Color(0xFF5A3A20), Offset(a.x - tw * .5f, a.y - tw * .2f), Size(b.x - a.x + tw, tw * .1f))
        var px = a.x - tw * .45f
        while (px <= b.x + tw * .5f) { drawLine(Color(0xFF4A2E18), Offset(px, a.y - tw * .6f), Offset(px, a.y + th * .3f), tw * .07f); px += tw * .5f }
        if (tournament) {
            for ((i, col) in listOf(Color(0xFF7A2430), Color(0xFF2F4A6A)).withIndex()) {
                val tx = a.x + i * tw * 1.6f; val ty = a.y - tw * .7f
                drawPath(Path().apply { moveTo(tx - tw * .5f, ty); lineTo(tx, ty - tw * 1.0f); lineTo(tx + tw * .5f, ty); close() }, col)
                drawLine(Color(0xFFE6DCC6), Offset(tx, ty - tw * 1.0f), Offset(tx, ty), tw * .03f, StrokeCap.Round)
            }
        }
    }
}

private val ArtPaletteGold = Color(0xFFD9B45A)
