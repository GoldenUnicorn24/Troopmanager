package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.DialogueEngine
import com.goldenunicorn.troopmanager.engine.LifeEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The walkable tavern. Tap the floor to walk (A* pathfinding), tap a person to walk up and talk,
 * tap the bar for the innkeeper, the stairs for your room and the door to leave. The scene is a
 * hand-built 2.5D Canvas: back wall with shelves, windows that follow the time of day, a burning
 * hearth, tables with candles, depth-sorted people and warm light.
 */
@Composable
fun TavernScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onExit: () -> Unit,
    initialTalk: String? = null,
) {
    val layout = TavernEngine.layout
    val placements = remember(state.life.npcs, state.day, state.life.timeSlot, state.life.provinceId) { TavernEngine.placements(state) }
    var walk by remember { mutableStateOf<WalkRequest?>(null) }
    var walkId by remember { mutableLongStateOf(0L) }
    val pos = remember { Animatable(Offset(layout.door.x.toFloat(), layout.door.y.toFloat()), Offset.VectorConverter) }
    var walking by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Cell?>(null) }
    var talkTo by remember { mutableStateOf<String?>(null) }
    var npcLine by remember { mutableStateOf("") }
    var playerLine by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var fade by remember { mutableStateOf<String?>(null) }
    var innTab by remember { mutableIntStateOf(0) }
    var dice by remember { mutableStateOf<TavernEngine.DiceRound?>(null) }
    val infinite = rememberInfiniteTransition(label = "tavern")
    val time by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t")
    val measurer = rememberTextMeasurer()
    val town = LifeEngine.town(state)
    val playerLook = state.life.appearance ?: defaultLook(state.life.gender, state.player.species)

    // Gesture handlers outlive a single composition; always act on the newest state.
    val latestState by rememberUpdatedState(state)
    val latestOnState by rememberUpdatedState(onState)
    val latestPlacements by rememberUpdatedState(placements)

    fun currentCell() = Cell(pos.value.x.roundToInt(), pos.value.y.roundToInt())

    fun openTalk(id: String) {
        val r = DialogueEngine.open(latestState, id)
        latestOnState(r.state)
        talkTo = id
        npcLine = r.npcLine
        playerLine = ""
        summary = ""
        innTab = 0
        dice = null
    }

    fun walkTo(cell: Cell, then: (() -> Unit)? = null) {
        val path = TavernEngine.path(currentCell(), cell, latestPlacements.map { it.cell }.toSet())
        if (path.isEmpty()) { then?.invoke(); return }
        target = cell
        walkId += 1
        walk = WalkRequest(path, then, walkId)
    }

    fun walkUpTo(cell: Cell, then: () -> Unit) {
        val spot = if (currentCell().dist(cell) <= 1) null else TavernEngine.approach(cell, currentCell(), latestPlacements.map { it.cell }.toSet())
        if (spot == null) then() else walkTo(spot, then)
    }

    // Walking runs as an effect: a new request cancels the old one mid-step.
    LaunchedEffect(walk) {
        val w = walk ?: return@LaunchedEffect
        walking = true
        for (step in w.path) pos.animateTo(Offset(step.x.toFloat(), step.y.toFloat()), tween(190, easing = LinearEasing))
        walking = false
        target = null
        walk = null
        w.then?.invoke()
    }

    LaunchedEffect(initialTalk) { initialTalk?.let { id -> state.life.npc(id)?.let { openTalk(id) } } }
    BackHandler(enabled = talkTo != null) { talkTo = null }

    Box(Modifier.fillMaxSize().background(Ink).testTag("tavern")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TavernHud(state, town?.tavernName ?: "Taverne", onLeave = {
                val r = TavernEngine.leave(state); onState(r.state); onNotice(r.message); onExit()
            })
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tw = constraints.maxWidth / 12f
                val th = tw * .66f
                val wallH = tw * 2.2f
                val sceneH = wallH + th * 9f
                val density = androidx.compose.ui.platform.LocalDensity.current
                Canvas(
                    Modifier.fillMaxWidth().height(with(density) { sceneH.toDp() })
                        .semantics { contentDescription = "Begehbare Taverne mit ${placements.size} Personen" }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                // People first (their figures stand above their tile).
                                val hit = latestPlacements.minByOrNull { p ->
                                    val c = Offset(tw * (p.cell.x + .5f), wallH + th * (p.cell.y + .5f) - tw * .7f)
                                    (c - tap).getDistanceSquared()
                                }?.takeIf { p ->
                                    val c = Offset(tw * (p.cell.x + .5f), wallH + th * (p.cell.y + .5f) - tw * .7f)
                                    kotlin.math.abs(tap.x - c.x) < tw * .6f && kotlin.math.abs(tap.y - c.y) < tw * 1.0f
                                }
                                if (hit != null) {
                                    if (hit.npc.role == NpcRole.INNKEEPER) walkTo(layout.barFront) { openTalk(hit.npc.id) }
                                    else walkUpTo(hit.cell) { openTalk(hit.npc.id) }
                                    return@detectTapGestures
                                }
                                val cx = floor(tap.x / tw).toInt()
                                val cy = floor((tap.y - wallH) / th).toInt()
                                val cell = Cell(cx.coerceIn(0, 11), cy.coerceIn(0, 8))
                                when {
                                    tap.y < wallH || cell in layout.counter || cell.y <= 1 && cell.x in 1..4 -> {
                                        val keeper = latestPlacements.firstOrNull { it.npc.role == NpcRole.INNKEEPER }
                                        if (keeper != null && cell.x <= 5) walkTo(layout.barFront) { openTalk(keeper.npc.id) }
                                    }
                                    cell in layout.stairs -> walkTo(layout.stairFoot) { talkTo = "__room" }
                                    cell == layout.door -> walkTo(layout.door) { talkTo = "__door" }
                                    layout.walkable(cell) -> walkTo(cell)
                                }
                            }
                        },
                ) {
                    drawTavernRoom(state, tw, th, wallH, time)
                    // Depth-sorted actors and furniture rows.
                    val playerRow = pos.value.y
                    for (row in 0..8) {
                        drawFurnitureRow(row, tw, th, wallH, time)
                        placements.filter { it.cell.y == row }.sortedBy { it.cell.x }.forEach { p ->
                            val feet = Offset(tw * (p.cell.x + .5f), wallH + th * (p.cell.y + .5f) + th * .3f)
                            val bob = sin((time + p.cell.x * .13f) * 6.283f) * tw * .012f
                            drawFullFigure(p.npc.appearance, p.npc.gender, feet + Offset(0f, bob), tw * 1.75f / 140f, detailed = false)
                        }
                        if (floor(playerRow + .5f).toInt() == row) {
                            val feet = Offset(tw * (pos.value.x + .5f), wallH + th * (pos.value.y + .5f) + th * .3f)
                            drawOval(Gold.copy(alpha = .55f), Offset(feet.x - tw * .42f, feet.y - th * .2f), Size(tw * .84f, th * .42f), style = Stroke(tw * .04f))
                            drawFullFigure(playerLook, state.life.gender, feet, tw * 1.75f / 140f, detailed = false, walkPhase = if (walking) time * 6f else 0f)
                        }
                    }
                    target?.let { t ->
                        drawCircle(Gold.copy(alpha = .5f), tw * .22f, Offset(tw * (t.x + .5f), wallH + th * (t.y + .5f)), style = Stroke(tw * .035f))
                    }
                    drawLighting(state.life.timeSlot, tw, th, wallH, time)
                    // Name tags for people you know, a role tag for the rest.
                    placements.forEach { p ->
                        val known = p.npc.relation.timesMet > 0
                        val label = if (known) p.npc.firstName else p.npc.role.label
                        val style = TextStyle(color = if (known) PaleGold else Mist.copy(alpha = .8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val m = measurer.measure(label, style)
                        val top = Offset(tw * (p.cell.x + .5f) - m.size.width / 2f, wallH + th * (p.cell.y + .5f) - tw * 1.95f)
                        drawRoundRect(Color.Black.copy(alpha = .55f), top - Offset(6f, 2f), Size(m.size.width + 12f, m.size.height + 4f), CornerRadius(10f))
                        drawText(m, topLeft = top)
                    }
                }
                TavernEventBanner(state, onState, onNotice, Modifier.align(Alignment.TopCenter).padding(10.dp))
            }
            if (talkTo == null) TavernActionBar(state, onState, onNotice,
                onBar = { placements.firstOrNull { it.npc.role == NpcRole.INNKEEPER }?.let { k -> walkTo(layout.barFront) { openTalk(k.npc.id) } } },
                onRoom = { walkTo(layout.stairFoot) { talkTo = "__room" } },
                onLeave = { val r = TavernEngine.leave(state); onState(r.state); onNotice(r.message); onExit() })
        }
        val talking = talkTo
        if (talking != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)).clickable(enabled = false) {})
            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.62f).navigationBarsPadding(),
                color = Color(0xF20E141B), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = .25f)),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (talking) {
                        "__room" -> RoomPanel(state, onState, onNotice) { talkTo = null }
                        "__door" -> {
                            Text("Die Tür zur Straße", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
                            Text("Draußen liegt ${LifeEngine.locationName(state)}.", color = Mist)
                            GoldButton("Taverne verlassen", { val r = TavernEngine.leave(state); onState(r.state); onNotice(r.message); talkTo = null; onExit() }, Modifier.fillMaxWidth())
                            RbAction("Bleiben") { talkTo = null }
                        }
                        else -> {
                            val npc = state.life.npc(talking)
                            if (npc == null) { talkTo = null } else {
                                ConversationPanel(state, npc, npcLine, playerLine, summary, innTab, dice,
                                    onInnTab = { innTab = it },
                                    onChoose = { topicId ->
                                        val reply = DialogueEngine.choose(state, npc.id, topicId)
                                        var next = reply.state
                                        npcLine = reply.npcLine.ifBlank { npcLine }
                                        playerLine = reply.playerLine
                                        summary = reply.summary
                                        if (reply.brawl) {
                                            val b = TavernEngine.brawl(next, npc.id)
                                            next = b.state
                                            npcLine = b.text
                                            if (!b.won) talkTo = null
                                        }
                                        onState(next)
                                        if (reply.fadeToBlack != null) { fade = reply.fadeToBlack; talkTo = null }
                                        if (next.life.place != LifePlace.TAVERN) { onNotice(npcLine); onExit() }
                                    },
                                    onAction = { result -> onState(result.state); summary = result.message },
                                    onDice = { stake -> val d = TavernEngine.dice(state, stake); dice = d; onState(d.state) },
                                    onClose = { talkTo = null })
                            }
                        }
                    }
                }
            }
        }
        fade?.let { text -> FadeToBlack(text) { fade = null } }
    }
}

private data class WalkRequest(val path: List<Cell>, val then: (() -> Unit)?, val id: Long)

// ------------------------------------------------------------ HUD and bars

@Composable
private fun TavernHud(state: GameState, name: String, onLeave: () -> Unit) {
    val life = state.life
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Gold, fontFamily = Cinzel, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${LifeEngine.locationName(state)} · Tag ${state.day} · ${life.timeSlot.label} ${life.timeSlot.clock}", color = Mist, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${LifeEngine.money(state)} Gold", color = PaleGold, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(LifeEngine.intoxicationLabel(life.intoxication) + if (life.hangover) " · Kater" else "", color = if (life.intoxication >= 50) Danger else Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onLeave, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 44.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) {
            Text("Hinaus", color = Mist, fontSize = 12.sp)
        }
    }
    if (life.intoxication > 0) LinearProgressIndicator(
        progress = { life.intoxication / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp),
        color = if (life.intoxication >= 50) Danger else Gold, trackColor = Color.Transparent,
    )
}

@Composable
private fun TavernActionBar(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, onBar: () -> Unit, onRoom: () -> Unit, onLeave: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tippe auf den Boden zum Gehen, auf eine Person zum Ansprechen, auf den Tresen für den Wirt.", color = Muted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RbAction("Zum Wirt", onClick = onBar)
            RbAction("Zeit vergehen lassen", enabled = state.life.timeSlot != TimeSlot.NIGHT) {
                val r = LifeEngine.passTime(state); onState(r.state); onNotice(r.message)
            }
            RbAction(if (LifeEngine.hasRoom(state)) "Zimmer · schlafen" else "Zimmer", onClick = onRoom)
            RbAction("Verlassen", onClick = onLeave)
        }
    }
}

@Composable
private fun TavernEventBanner(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, modifier: Modifier) {
    val event = TavernEngine.event(state) ?: return
    var open by remember(event.key, state.day, state.life.timeSlot) { mutableStateOf(true) }
    if (!open) return
    Surface(modifier.fillMaxWidth(), color = Color(0xEE1A1410), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .35f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.title, color = PaleGold, fontFamily = Cinzel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("✕", color = Muted, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = false }.padding(8.dp))
            }
            Text(event.text, color = Mist, fontSize = 13.sp, fontFamily = Cormorant, fontStyle = FontStyle.Italic, lineHeight = 18.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                event.options.forEach { o ->
                    RbAction(o.label) { val r = TavernEngine.resolve(state, event.key, o.id); onState(r.state); onNotice(r.message); open = false }
                }
            }
        }
    }
}

@Composable
private fun RoomPanel(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, onClose: () -> Unit) {
    val price = LifeEngine.priced(state, LifeEngine.ROOM_PRICE)
    Text("Die Treppe zu den Zimmern", color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
    if (LifeEngine.hasRoom(state)) {
        Text("Dein Zimmer für heute Nacht ist bezahlt. Ein Bett, eine Truhe, ein Fenster zum Hof.", color = Mist)
        GoldButton("Schlafen bis zum Morgen", {
            val r = LifeEngine.sleep(state); onState(r.state); onNotice(r.message); onClose()
        }, Modifier.fillMaxWidth())
    } else {
        Text("Ein Zimmer kostet $price Gold für die Nacht.", color = Mist)
        GoldButton("Zimmer mieten ($price Gold)", { val r = LifeEngine.rentRoom(state); onState(r.state); onNotice(r.message) }, Modifier.fillMaxWidth())
    }
    RbAction("Zurück in die Schankstube", onClick = onClose)
}

// ------------------------------------------------------------ conversation

@Composable
private fun ConversationPanel(
    state: GameState,
    npc: NpcIdentity,
    npcLine: String,
    playerLine: String,
    summary: String,
    innTab: Int,
    dice: TavernEngine.DiceRound?,
    onInnTab: (Int) -> Unit,
    onChoose: (String) -> Unit,
    onAction: (LifeEngine.Result) -> Unit,
    onDice: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val r = npc.relation
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CharacterPortrait(npc.appearance, npc.gender, Modifier.size(width = 92.dp, height = 115.dp).clip(RoundedCornerShape(16.dp)), Framing.BUST)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (r.timesMet > 0) npc.name else "${npc.profession} (unbekannt)", color = Color.White, fontFamily = Cinzel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${npc.profession} · ${npc.species.label} · ${npc.age}", color = Mist, fontSize = 12.sp)
            Text(r.stage.label + if (npc.relation.romanceBoundary) " · keine Romantik" else "", color = PaleGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            RelationBar("Sympathie", (r.affinity + 100) / 2, Gold)
            RelationBar("Vertrauen", r.trust, ModernBlue)
            if (r.attraction > 0) RelationBar("Anziehung", r.attraction, Color(0xFFE58FB0))
        }
    }
    if (playerLine.isNotBlank()) Text(playerLine, color = Muted, fontSize = 13.sp, fontStyle = FontStyle.Italic)
    if (npcLine.isNotBlank()) Text(npcLine, color = Color.White, fontSize = 17.sp, fontFamily = Cormorant, lineHeight = 23.sp)
    if (summary.isNotBlank()) Text(summary, color = Gold, fontSize = 12.sp)
    val innkeeper = npc.role == NpcRole.INNKEEPER
    if (innkeeper) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Gespräch", "Bestellen", "Aufträge", "Würfeln").forEachIndexed { i, label -> RbChip(label, innTab == i) { onInnTab(i) } }
    }
    when {
        innkeeper && innTab == 1 -> {
            Drink.entries.forEach { d ->
                val price = LifeEngine.priced(state, d.price)
                OrderRow("${d.label} · $price Gold", d.description, LifeEngine.canPay(state, price)) { onAction(LifeEngine.drink(state, d)) }
            }
            Meal.entries.forEach { m ->
                val price = LifeEngine.priced(state, m.price)
                OrderRow("${m.label} · $price Gold", m.description, LifeEngine.canPay(state, price)) { onAction(LifeEngine.eat(state, m)) }
            }
            OrderRow("Zimmer für die Nacht · ${LifeEngine.priced(state, LifeEngine.ROOM_PRICE)} Gold", if (LifeEngine.hasRoom(state)) "Bereits bezahlt." else "Schlüssel gegen Münze.",
                !LifeEngine.hasRoom(state)) { onAction(LifeEngine.rentRoom(state)) }
        }
        innkeeper && innTab == 2 -> {
            val jobs = state.life.jobs.filter { it.provinceId == state.life.provinceId && !it.done && !it.failed }
            if (state.life.landed) Text("Als Herrscher erledigen deine Leute solche Aufträge.", color = Muted)
            else if (jobs.isEmpty()) Text("„Heute hab ich nichts für dich. Frag morgen wieder.“", color = Mist, fontFamily = Cormorant, fontSize = 16.sp)
            jobs.forEach { j ->
                val giver = state.life.npc(j.giverId)
                PremiumPanel {
                    Text(j.title, color = PaleGold, fontWeight = FontWeight.Bold)
                    Text(j.description, color = Mist, fontSize = 13.sp)
                    Text("Auftraggeber: ${giver?.name ?: "unbekannt"} · ${LifeEngine.skillLabel(j.skill)} gegen ${j.difficulty} · ${j.reward} Gold · +${j.renown} Ruf · ${j.slots} Tageszeit(en)", color = Muted, fontSize = 12.sp)
                    if (!j.accepted) RbAction("Annehmen") { onAction(LifeEngine.acceptJob(state, j.id)) }
                    else RbAction("Jetzt erledigen", enabled = LifeEngine.slotsLeft(state) >= j.slots) { onAction(LifeEngine.doJob(state, j.id)) }
                }
            }
        }
        innkeeper && innTab == 3 -> {
            Text("Zwei Würfel gegen zwei Würfel. Eine ruhige Hand darf den schlechteren Wurf wiederholen.", color = Mist, fontSize = 13.sp)
            dice?.let { d ->
                if (d.won != null || d.player.first > 0) Text("${face(d.player.first)} ${face(d.player.second)}   gegen   ${face(d.opponent.first)} ${face(d.opponent.second)}", color = Color.White, fontSize = 30.sp)
                Text(d.message, color = if (d.won == true) Success else if (d.won == false) Danger else Mist)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10).forEach { stake -> RbAction("$stake Gold", enabled = LifeEngine.canPay(state, stake)) { onDice(stake) } }
            }
        }
        else -> DialogueEngine.options(state, npc.id).groupBy { it.topic.kind }.forEach { (kind, options) ->
            Text(kind.label.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            options.forEach { o ->
                Surface(
                    onClick = { if (o.enabled) onChoose(o.topic.id) },
                    enabled = o.enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    color = if (o.enabled) Panel2 else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (kind == TopicKind.ROMANCE) Color(0x66E58FB0) else Color.White.copy(alpha = .08f)),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(o.label, color = if (o.enabled) Color.White else Muted, fontSize = 14.sp)
                        o.reason?.let { Text(it, color = Muted, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
    RbAction("Gespräch beenden", onClick = onClose)
}

private fun face(v: Int) = when (v) { 1 -> "⚀"; 2 -> "⚁"; 3 -> "⚂"; 4 -> "⚃"; 5 -> "⚄"; 6 -> "⚅"; else -> "·" }

@Composable
private fun OrderRow(title: String, text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), color = Panel2, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, color = if (enabled) Color.White else Muted, fontWeight = FontWeight.SemiBold)
            Text(text, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun RelationBar(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.width(70.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .08f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((value / 100f).coerceIn(0f, 1f)).background(color))
        }
    }
}

@Composable
internal fun FadeToBlack(text: String, onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(text) { alpha.animateTo(1f, tween(1400)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha.value)).clickable(onClick = onDone).testTag("fade_to_black"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Mist.copy(alpha = alpha.value), fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 27.sp)
            Text("Weiter", color = Gold.copy(alpha = alpha.value), fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------ painting the room

private fun skyFor(slot: TimeSlot): List<Color> = when (slot) {
    TimeSlot.MORNING -> listOf(Color(0xFFF2D6A2), Color(0xFF9FC3DD))
    TimeSlot.NOON -> listOf(Color(0xFFBFE0F5), Color(0xFF6FA8D6))
    TimeSlot.AFTERNOON -> listOf(Color(0xFFF5C77E), Color(0xFF8DB4D2))
    TimeSlot.EVENING -> listOf(Color(0xFFE77A3C), Color(0xFF5A3A6A))
    TimeSlot.NIGHT -> listOf(Color(0xFF1B2440), Color(0xFF080B16))
}

private val TavPlaster = Color(0xFF8C7458)
private val TavTimber = Color(0xFF3A2516)
private val TavWood = Color(0xFF6E4A2C)
private val TavStone = Color(0xFF6C665E)

private fun DrawScope.drawTavernRoom(state: GameState, tw: Float, th: Float, wallH: Float, time: Float) {
    val w = size.width
    // Back wall: plaster between timber, wainscot below.
    drawRect(Brush.verticalGradient(listOf(TavPlaster.copy(alpha = 1f).mix(Color.Black, .35f), TavPlaster), 0f, wallH))
    for (i in 0..12) drawRect(Brush.horizontalGradient(listOf(TavTimber.mix(Color.White, .08f), TavTimber), i * tw - tw * .08f, i * tw + tw * .08f),
        Offset(i * tw - tw * .08f, 0f), Size(tw * .16f, wallH))
    drawRect(TavTimber, Offset(0f, 0f), Size(w, tw * .22f))
    drawRect(Brush.verticalGradient(listOf(TavWood.mix(Color.Black, .2f), TavWood.mix(Color.Black, .45f)), wallH - tw * .7f, wallH), Offset(0f, wallH - tw * .7f), Size(w, tw * .7f))
    for (i in 0..24) drawLine(Color.Black.copy(alpha = .25f), Offset(i * tw * .5f, wallH - tw * .7f), Offset(i * tw * .5f, wallH), 1.5f)
    // Windows with the sky of the hour.
    val sky = skyFor(state.life.timeSlot)
    for (wx in listOf(5.55f, 10.45f)) {
        val tl = Offset(wx * tw, tw * .45f)
        val ws = Size(tw * .95f, tw * 1.05f)
        drawRect(Brush.verticalGradient(sky, tl.y, tl.y + ws.height), tl, ws)
        if (state.life.timeSlot == TimeSlot.NIGHT) drawCircle(Color(0xFFF3EBD0), tw * .12f, tl + Offset(ws.width * .7f, ws.height * .3f))
        drawRect(TavTimber, tl, ws, style = Stroke(tw * .08f))
        drawLine(TavTimber, tl + Offset(ws.width / 2f, 0f), tl + Offset(ws.width / 2f, ws.height), tw * .05f)
        drawLine(TavTimber, tl + Offset(0f, ws.height / 2f), tl + Offset(ws.width, ws.height / 2f), tw * .05f)
    }
    // Shelves with bottles and mugs behind the bar.
    for (shelf in 0..1) {
        val y = tw * (.75f + shelf * .62f)
        drawRect(TavWood.mix(Color.Black, .15f), Offset(tw * .9f, y), Size(tw * 4.2f, tw * .08f))
        for (k in 0 until 11) {
            val bx = tw * (1.05f + k * .37f)
            val col = listOf(Color(0xFF2E5A3A), Color(0xFF7A2430), Color(0xFFB98E3A), Color(0xFF3A4A6A))[(k + shelf) % 4]
            if ((k + shelf) % 3 == 0) {
                drawRoundRect(Color(0xFF9A7A52), Offset(bx, y - tw * .2f), Size(tw * .2f, tw * .2f), CornerRadius(tw * .03f))
            } else {
                drawRoundRect(col, Offset(bx, y - tw * .34f), Size(tw * .14f, tw * .34f), CornerRadius(tw * .04f))
                drawRect(col, Offset(bx + tw * .045f, y - tw * .44f), Size(tw * .05f, tw * .12f))
                drawLine(Color.White.copy(alpha = .35f), Offset(bx + tw * .035f, y - tw * .3f), Offset(bx + tw * .035f, y - tw * .06f), tw * .02f)
            }
        }
    }
    // Shield and crossed swords between window and hearth.
    val shield = Offset(tw * 7.05f, tw * .95f)
    drawLine(Color(0xFFB8BEC6), shield + Offset(-tw * .45f, -tw * .45f), shield + Offset(tw * .45f, tw * .45f), tw * .06f, StrokeCap.Round)
    drawLine(Color(0xFFB8BEC6), shield + Offset(tw * .45f, -tw * .45f), shield + Offset(-tw * .45f, tw * .45f), tw * .06f, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(shield.x - tw * .3f, shield.y - tw * .32f); lineTo(shield.x + tw * .3f, shield.y - tw * .32f)
        cubicTo(shield.x + tw * .3f, shield.y + tw * .1f, shield.x + tw * .1f, shield.y + tw * .3f, shield.x, shield.y + tw * .4f)
        cubicTo(shield.x - tw * .1f, shield.y + tw * .3f, shield.x - tw * .3f, shield.y + tw * .1f, shield.x - tw * .3f, shield.y - tw * .32f)
        close()
    }, Brush.verticalGradient(listOf(Color(0xFF7A2430), Color(0xFF3A1016)), shield.y - tw * .32f, shield.y + tw * .4f))
    // Hearth: stone arch with fire.
    val hx = tw * 7.75f
    val hw = tw * 2.5f
    val hTop = wallH - tw * 1.7f
    drawRoundRect(Brush.verticalGradient(listOf(TavStone.mix(Color.White, .1f), TavStone.mix(Color.Black, .3f)), hTop, wallH + th), Offset(hx, hTop), Size(hw, wallH - hTop + th * 1.2f), CornerRadius(tw * .3f))
    for (r in 0 until 6) for (c in 0 until 5) {
        drawRoundRect(Color.Black.copy(alpha = .18f), Offset(hx + c * hw / 5f + (if (r % 2 == 0) 0f else hw / 10f), hTop + r * tw * .3f), Size(hw / 5f - 3f, tw * .3f - 3f), CornerRadius(4f), style = Stroke(1.5f))
    }
    val fireTop = hTop + tw * .55f
    drawRoundRect(Color(0xFF120A06), Offset(hx + hw * .18f, fireTop), Size(hw * .64f, wallH - fireTop + th * .9f), CornerRadius(tw * .35f, tw * .35f))
    val flick = sin(time * 6.283f * 3f)
    for (k in 0 until 5) {
        val fx = hx + hw * (.3f + k * .1f)
        val fh = tw * (.55f + .18f * sin((time * 9f + k * .7f) * 6.283f / 3f))
        drawPath(Path().apply {
            moveTo(fx - tw * .14f, wallH + th * .6f)
            cubicTo(fx - tw * .16f, wallH + th * .6f - fh * .5f, fx + flick * tw * .05f, wallH + th * .6f - fh * .8f, fx + flick * tw * .03f, wallH + th * .6f - fh)
            cubicTo(fx + tw * .1f, wallH + th * .6f - fh * .6f, fx + tw * .16f, wallH + th * .6f - fh * .3f, fx + tw * .14f, wallH + th * .6f)
            close()
        }, Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFF9A2E), Color(0xFFB8360E)), wallH + th * .6f - fh, wallH + th * .6f))
    }
    drawRect(Color(0xFF3A2416), Offset(hx + hw * .25f, wallH + th * .55f), Size(hw * .5f, th * .18f))
    // Floor planks.
    val floorTop = wallH
    for (r in 0 until 18) {
        val y = floorTop + r * th * .5f
        val shade = .32f - r * .012f
        drawRect(TavWood.mix(if (r % 2 == 0) Color.Black else Color(0xFF2A1A10), shade.coerceIn(.05f, .4f)), Offset(0f, y), Size(w, th * .5f))
        drawLine(Color.Black.copy(alpha = .35f), Offset(0f, y), Offset(w, y), 1.6f)
        var x = ((r * 37) % 5) * tw * .6f
        while (x < w) { drawLine(Color.Black.copy(alpha = .3f), Offset(x, y), Offset(x, y + th * .5f), 1.4f); x += tw * 2.7f }
    }
    // Rug in the middle of the room.
    drawRoundRect(Brush.radialGradient(listOf(Color(0xFF7A2430), Color(0xFF3A1016)), Offset(tw * 7f, wallH + th * 5.5f), tw * 2.4f),
        Offset(tw * 5.4f, wallH + th * 4.1f), Size(tw * 3.6f, th * 2.9f), CornerRadius(tw * .2f))
    drawRoundRect(Color(0xFFB98E3A).copy(alpha = .6f), Offset(tw * 5.55f, wallH + th * 4.2f), Size(tw * 3.3f, th * 2.7f), CornerRadius(tw * .15f), style = Stroke(tw * .04f))
    // Door at the front edge with daylight spill.
    val door = TavernEngine.layout.door
    val dx = tw * door.x
    val doorLight = if (state.life.timeSlot == TimeSlot.NIGHT || state.life.timeSlot == TimeSlot.EVENING) Color(0x33F0B060) else Color(0x55FFF0C8)
    drawPath(Path().apply {
        moveTo(dx + tw * .1f, size.height); lineTo(dx - tw * .4f, size.height - th * 1.6f); lineTo(dx + tw * 1.4f, size.height - th * 1.6f); lineTo(dx + tw * .9f, size.height); close()
    }, Brush.verticalGradient(listOf(Color.Transparent, doorLight), size.height - th * 1.6f, size.height))
    drawRect(Color(0xFF4A3A2A), Offset(dx + tw * .05f, size.height - th * .55f), Size(tw * .9f, th * .5f))
}

private fun DrawScope.drawFurnitureRow(row: Int, tw: Float, th: Float, wallH: Float, time: Float) {
    val l = TavernEngine.layout
    fun cell(c: Cell) = Offset(tw * (c.x + .5f), wallH + th * (c.y + .5f))
    if (row == 2) {
        // The bar counter: top face and front face.
        val x0 = tw * 1f
        val x1 = tw * 5f
        val topY = wallH + th * 1.15f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF8A6038), Color(0xFF5A3A20)), topY, topY + th * .5f), Offset(x0, topY), Size(x1 - x0, th * .5f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF4A2E18), Color(0xFF2A190C)), topY + th * .5f, wallH + th * 3f), Offset(x0, topY + th * .5f), Size(x1 - x0, wallH + th * 3f - topY - th * .5f))
        for (k in 0..4) drawLine(Color.Black.copy(alpha = .35f), Offset(x0 + k * tw, topY + th * .5f), Offset(x0 + k * tw, wallH + th * 3f), 2f)
        drawLine(Color(0xFFB98E3A).copy(alpha = .5f), Offset(x0, topY + th * .5f), Offset(x1, topY + th * .5f), 3f)
        for (k in 0..2) {
            val mug = Offset(x0 + tw * (.6f + k * 1.3f), topY + th * .12f)
            drawRoundRect(Color(0xFF9A7A52), mug, Size(tw * .2f, tw * .22f), CornerRadius(tw * .03f))
            drawRect(Color(0xFFF3E6C4), mug, Size(tw * .2f, tw * .05f))
        }
        candle(Offset(x0 + tw * 3.4f, topY + th * .2f), tw, time)
    }
    l.barrels.filter { it.y == row }.forEach { b ->
        val c = cell(b)
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8A6038), Color(0xFF4A2E18)), c.x - tw * .42f, c.x + tw * .42f),
            Offset(c.x - tw * .42f, c.y - tw * .55f), Size(tw * .84f, tw * .85f), CornerRadius(tw * .2f))
        for (k in 0..1) drawLine(Color(0xFF2A2A2A), Offset(c.x - tw * .42f, c.y - tw * (.35f - k * .45f)), Offset(c.x + tw * .42f, c.y - tw * (.35f - k * .45f)), tw * .05f)
        drawOval(Color(0xFF5A3A20), Offset(c.x - tw * .38f, c.y - tw * .62f), Size(tw * .76f, tw * .2f))
    }
    l.tables.filter { it.y == row }.forEach { t ->
        val c = cell(t)
        drawRect(Color(0xFF3A2516), Offset(c.x - tw * .07f, c.y - tw * .45f), Size(tw * .14f, tw * .5f))
        drawOval(Color.Black.copy(alpha = .3f), Offset(c.x - tw * .55f, c.y), Size(tw * 1.1f, th * .35f))
        drawOval(Brush.radialGradient(listOf(Color(0xFF9A6A3E), Color(0xFF5A3A20)), Offset(c.x - tw * .2f, c.y - tw * .62f), tw * .9f),
            Offset(c.x - tw * .72f, c.y - tw * .82f), Size(tw * 1.44f, tw * .62f))
        drawOval(Color.Black.copy(alpha = .4f), Offset(c.x - tw * .72f, c.y - tw * .82f), Size(tw * 1.44f, tw * .62f), style = Stroke(2f))
        drawRoundRect(Color(0xFF9A7A52), Offset(c.x + tw * .2f, c.y - tw * .78f), Size(tw * .16f, tw * .18f), CornerRadius(tw * .03f))
        drawOval(Color(0xFFD8D0C0), Offset(c.x - tw * .45f, c.y - tw * .62f), Size(tw * .36f, tw * .16f))
        candle(Offset(c.x - tw * .05f, c.y - tw * .7f), tw, time)
    }
    l.stairs.filter { it.y == row }.forEach { s ->
        val c = cell(s)
        val steps = 4
        for (k in 0 until steps) {
            val y = c.y + th * .4f - k * th * .28f - (if (s == l.stairs.first()) th * 1.1f else 0f)
            drawRect(Color(0xFF6E4A2C).mix(Color.Black, k * .06f), Offset(c.x - tw * .5f, y - th * .25f), Size(tw * .95f, th * .3f))
            drawLine(Color.Black.copy(alpha = .4f), Offset(c.x - tw * .5f, y - th * .25f), Offset(c.x + tw * .45f, y - th * .25f), 2f)
        }
        if (s == l.stairs.last()) drawLine(Color(0xFF3A2516), Offset(c.x - tw * .55f, c.y + th * .2f), Offset(c.x - tw * .55f, c.y - th * 2.6f), tw * .07f)
    }
    // Stools at free seats so the room reads as a tavern even when quiet.
    l.seats.filter { it.cell.y == row && (it.kind == TavernEngine.SeatKind.BAR || it.kind == TavernEngine.SeatKind.TABLE) }.forEach { s ->
        val c = cell(s.cell)
        drawLine(Color(0xFF2A190C), Offset(c.x, c.y - tw * .1f), Offset(c.x, c.y + th * .25f), tw * .06f)
        drawOval(Color(0xFF6E4A2C), Offset(c.x - tw * .22f, c.y - tw * .2f), Size(tw * .44f, tw * .16f))
    }
}

private fun DrawScope.candle(at: Offset, tw: Float, time: Float) {
    drawRect(Color(0xFFEDE3C8), Offset(at.x - tw * .03f, at.y - tw * .16f), Size(tw * .06f, tw * .16f))
    val f = 1f + .15f * sin(time * 6.283f * 5f + at.x)
    drawOval(Brush.verticalGradient(listOf(Color(0xFFFFF4B0), Color(0xFFFF9A2E)), at.y - tw * .3f * f, at.y - tw * .15f),
        Offset(at.x - tw * .035f, at.y - tw * .3f * f), Size(tw * .07f, tw * .15f * f))
}

private fun DrawScope.drawLighting(slot: TimeSlot, tw: Float, th: Float, wallH: Float, time: Float) {
    val dark = when (slot) { TimeSlot.MORNING -> .18f; TimeSlot.NOON -> .1f; TimeSlot.AFTERNOON -> .16f; TimeSlot.EVENING -> .34f; TimeSlot.NIGHT -> .48f }
    drawRect(Color(0xFF0A0604).copy(alpha = dark))
    val pulse = .85f + .15f * sin(time * 6.283f * 2f)
    // Hearth glow.
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A3C).copy(alpha = .38f * pulse), Color.Transparent), Offset(tw * 9f, wallH), tw * 4.2f),
        tw * 4.2f, Offset(tw * 9f, wallH), blendMode = BlendMode.Screen)
    // Candles on tables and bar.
    (TavernEngine.layout.tables + Cell(4, 1)).forEach { t ->
        val c = Offset(tw * (t.x + .5f), wallH + th * (t.y + .5f) - tw * .7f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC46A).copy(alpha = .3f * pulse), Color.Transparent), c, tw * 1.7f), tw * 1.7f, c, blendMode = BlendMode.Screen)
    }
    // Daylight through the windows.
    if (slot != TimeSlot.NIGHT && slot != TimeSlot.EVENING) for (wx in listOf(6f, 10.9f)) {
        drawPath(Path().apply {
            moveTo(tw * (wx - .45f), tw * 1.5f); lineTo(tw * (wx + .45f), tw * 1.5f); lineTo(tw * (wx + 1.6f), wallH + th * 4f); lineTo(tw * (wx - .2f), wallH + th * 4f); close()
        }, Brush.verticalGradient(listOf(Color(0x44FFF4D6), Color.Transparent), tw * 1.5f, wallH + th * 4f), blendMode = BlendMode.Screen)
    }
    // Vignette.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .45f)), Offset(size.width / 2f, size.height * .55f), size.width * .8f))
}
