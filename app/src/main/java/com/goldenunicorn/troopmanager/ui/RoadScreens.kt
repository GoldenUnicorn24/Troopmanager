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
import androidx.compose.ui.graphics.BlendMode
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.DialogueEngine
import com.goldenunicorn.troopmanager.engine.LifeEngine
import com.goldenunicorn.troopmanager.engine.MarketEngine
import com.goldenunicorn.troopmanager.engine.PartyEngine
import com.goldenunicorn.troopmanager.engine.SkirmishEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine.Cell
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ======================================================================== market

/**
 * The walkable market square: smith, trader and healer at their stalls, townsfolk around the
 * well. Same controls as the tavern; stalls open from morning to afternoon.
 */
@Composable
fun MarketScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val layout = MarketEngine.layout
    val placements = remember(state.life.npcs, state.day, state.life.timeSlot, state.life.provinceId) { MarketEngine.placements(state) }
    var walk by remember { mutableStateOf<WalkRequest?>(null) }
    var walkId by remember { mutableLongStateOf(0L) }
    val pos = remember { Animatable(Offset(layout.entry.x.toFloat(), layout.entry.y.toFloat()), Offset.VectorConverter) }
    var walking by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Cell?>(null) }
    var panel by remember { mutableStateOf<String?>(null) }
    var npcLine by remember { mutableStateOf("") }
    var playerLine by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    val infinite = rememberInfiniteTransition(label = "market")
    val time by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "t")
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
        target = cell
        walkId += 1
        walk = WalkRequest(path, then, walkId)
    }
    fun openStall(stall: MarketEngine.Stall) {
        panel = "stall:${stall.name}"
        summary = ""
        npcLine = MarketEngine.keeper(latestState, stall)?.let { k ->
            when (stall) {
                MarketEngine.Stall.SMITH -> "${k.firstName} wischt sich die Hände an der Schürze ab. „Klingen, Rüstung, Reparaturen. Was brauchst du?“"
                MarketEngine.Stall.TRADER -> "„Schönes für Menschen, die dir wichtig sind!“ ruft ${k.firstName}."
                MarketEngine.Stall.HEALER -> "${k.firstName} mustert dich prüfend. „Zeig her, wo es wehtut.“"
            }
        } ?: ""
    }
    fun openTalk(id: String) {
        val r = DialogueEngine.open(latestState, id)
        latestOnState(r.state)
        panel = "npc:$id"
        npcLine = r.npcLine; playerLine = ""; summary = ""
    }

    LaunchedEffect(walk) {
        val w = walk ?: return@LaunchedEffect
        walking = true
        for (step in w.path) pos.animateTo(Offset(step.x.toFloat(), step.y.toFloat()), tween(190, easing = LinearEasing))
        walking = false; target = null; walk = null
        w.then?.invoke()
    }
    BackHandler(enabled = panel != null) { panel = null }

    Box(Modifier.fillMaxSize().background(Ink).testTag("market")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Markt", color = Gold, fontFamily = Cinzel, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${LifeEngine.locationName(state)} · Tag ${state.day} · ${state.life.timeSlot.label} · " + if (MarketEngine.open(state)) "geöffnet" else "geschlossen", color = Mist, fontSize = 12.sp)
                }
                Text("${LifeEngine.money(state)} Gold", color = PaleGold, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { val r = MarketEngine.leave(state); onState(r.state); onNotice(r.message) }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 44.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) { Text("Hinaus", color = Mist, fontSize = 12.sp) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tw = constraints.maxWidth / 12f
                val th = tw * .66f
                val backH = tw * 2.4f
                val sceneH = backH + th * 9f
                val density = LocalDensity.current
                Canvas(
                    Modifier.fillMaxWidth().height(with(density) { sceneH.toDp() })
                        .semantics { contentDescription = "Begehbarer Marktplatz" }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val hit = latestPlacements.firstOrNull { p ->
                                    val c = Offset(tw * (p.cell.x + .5f), backH + th * (p.cell.y + .5f) - tw * 1.0f)
                                    kotlin.math.abs(tap.x - c.x) < tw * .7f && kotlin.math.abs(tap.y - c.y) < tw * 1.4f
                                }
                                val cell = Cell(floor(tap.x / tw).toInt().coerceIn(0, 11), floor((tap.y - backH) / th).toInt().coerceIn(0, 8))
                                val stallHit = MarketEngine.Stall.entries.firstOrNull { st -> cell in layout.stalls.getValue(st) || cell == layout.keeperSpots[st] }
                                    ?: hit?.stall
                                when {
                                    stallHit != null && MarketEngine.open(latestState) -> walkTo(layout.frontSpots.getValue(stallHit)) { openStall(stallHit) }
                                    hit != null -> {
                                        val spot = TavernEngine.approach(hit.cell, currentCell(), occupied(), layout)
                                        if (spot == null) openTalk(hit.npc.id) else walkTo(spot) { openTalk(hit.npc.id) }
                                    }
                                    cell == layout.entry -> walkTo(layout.entry) { panel = "exit" }
                                    layout.walkable(cell) -> walkTo(cell)
                                }
                            }
                        },
                ) {
                    if (tw < 2f) return@Canvas
                    drawMarketSquare(state.life.timeSlot, tw, th, backH, time, MarketEngine.open(state))
                    for (row in 0..8) {
                        drawMarketRow(row, tw, th, backH, MarketEngine.open(state))
                        placements.filter { it.cell.y == row }.forEach { p ->
                            val feet = Offset(tw * (p.cell.x + .5f), backH + th * (p.cell.y + .5f) + th * .3f)
                            drawFullFigure(p.npc.appearance, p.npc.gender, feet, tw * 2.4f / 140f, detailed = false)
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
                        val label = p.stall?.label ?: if (p.npc.relation.timesMet > 0) p.npc.firstName else p.npc.profession.substringBefore(' ').take(14)
                        val m = measurer.measure(label, TextStyle(color = if (p.stall != null) PaleGold else Mist, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val top = Offset(tw * (p.cell.x + .5f) - m.size.width / 2f, backH + th * (p.cell.y + .5f) - tw * 2.7f)
                        drawRoundRect(Color.Black.copy(alpha = .55f), top - Offset(6f, 2f), Size(m.size.width + 12f, m.size.height + 4f), CornerRadius(10f))
                        drawText(m, topLeft = top)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (MarketEngine.open(state)) "Tippe auf einen Stand zum Handeln, auf Leute zum Reden, auf den Boden zum Gehen."
                    else "Die Stände sind zu. Komm morgen früh wieder.", color = Muted, fontSize = 11.sp)
                if (state.life.wound > 0) RelationBar("Wunden", state.life.wound, Danger)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketEngine.Stall.entries.forEach { st ->
                        RbAction(st.label, enabled = MarketEngine.open(state)) { walkTo(layout.frontSpots.getValue(st)) { openStall(st) } }
                    }
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
                            GoldButton("Markt verlassen", { val r = MarketEngine.leave(state); onState(r.state); onNotice(r.message) }, Modifier.fillMaxWidth())
                            RbAction("Bleiben") { panel = null }
                        }
                        current.startsWith("stall:") -> StallPanel(state, MarketEngine.Stall.valueOf(current.removePrefix("stall:")), npcLine, summary,
                            onResult = { r -> onState(r.state); summary = r.message }, onClose = { panel = null })
                        else -> {
                            val npc = state.life.npc(current.removePrefix("npc:"))
                            if (npc != null) ConversationPanel(state, npc, npcLine, playerLine, summary, 0, null,
                                onInnTab = {},
                                onChoose = { topic ->
                                    val reply = DialogueEngine.choose(state, npc.id, topic)
                                    npcLine = reply.npcLine.ifBlank { npcLine }; playerLine = reply.playerLine; summary = reply.summary
                                    onState(reply.state)
                                },
                                onAction = { r -> onState(r.state); summary = r.message },
                                onDice = {},
                                onClose = { panel = null })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StallPanel(state: GameState, stall: MarketEngine.Stall, line: String, summary: String, onResult: (LifeEngine.Result) -> Unit, onClose: () -> Unit) {
    val keeper = MarketEngine.keeper(state, stall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (keeper != null) CharacterPortrait(keeper.appearance, keeper.gender, Modifier.size(width = 80.dp, height = 100.dp).clip(RoundedCornerShape(14.dp)), Framing.BUST)
        Column {
            Text(stall.label, color = PaleGold, fontFamily = Cinzel, fontSize = 20.sp)
            keeper?.let { Text("${it.name} · ${it.profession}", color = Mist, fontSize = 12.sp) }
        }
    }
    if (line.isNotBlank()) Text(line, color = Color.White, fontFamily = Cormorant, fontSize = 17.sp, lineHeight = 23.sp)
    if (summary.isNotBlank()) Text(summary, color = Gold, fontSize = 13.sp)
    when (stall) {
        MarketEngine.Stall.SMITH -> {
            SectionTitle("Waffen · im Besitz: ${GearCatalog.weapons[state.life.gear.weaponTier].name}")
            GearCatalog.weapons.drop(1).forEach { item ->
                val owned = item.tier <= state.life.gear.weaponTier
                ShopRow("${item.name} · ${MarketEngine.price(state, MarketEngine.Stall.SMITH, item.price)} Gold", "${item.description} Kampf +${item.bonus}",
                    !owned && LifeEngine.canPay(state, MarketEngine.price(state, MarketEngine.Stall.SMITH, item.price)), if (owned) "Besitzt du" else null) { onResult(MarketEngine.buyWeapon(state, item.tier)) }
            }
            SectionTitle("Rüstung · im Besitz: ${GearCatalog.armor[state.life.gear.armorTier].name}")
            GearCatalog.armor.drop(1).forEach { item ->
                val owned = item.tier <= state.life.gear.armorTier
                ShopRow("${item.name} · ${MarketEngine.price(state, MarketEngine.Stall.SMITH, item.price)} Gold", "${item.description} Schutz +${item.bonus}",
                    !owned && LifeEngine.canPay(state, MarketEngine.price(state, MarketEngine.Stall.SMITH, item.price)), if (owned) "Besitzt du" else null) { onResult(MarketEngine.buyArmor(state, item.tier)) }
            }
        }
        MarketEngine.Stall.TRADER -> {
            SectionTitle("Geschenke")
            GiftKind.entries.forEach { g ->
                val price = MarketEngine.price(state, MarketEngine.Stall.TRADER, g.price)
                ShopRow("${g.label} · $price Gold", "Im Gepäck: ${state.life.gifts[g] ?: 0} · Passt zu: ${g.keywords.joinToString()}",
                    LifeEngine.canPay(state, price), null) { onResult(MarketEngine.buyGift(state, g)) }
            }
        }
        MarketEngine.Stall.HEALER -> {
            RelationBar("Deine Wunden", state.life.wound, Danger)
            PartyEngine.members(state).forEach { m ->
                val w = state.life.party.firstOrNull { it.npcId == m.id }?.wounded ?: 0
                if (w > 0) RelationBar(m.firstName, w, Danger)
            }
            val price = MarketEngine.healPrice(state)
            ShopRow("Wunden versorgen · $price Gold", "Lindert 30 Punkte Wunden bei dir und deinem Gefolge.",
                LifeEngine.canPay(state, price) && (state.life.wound > 0 || state.life.party.any { it.wounded > 0 }), null) { onResult(MarketEngine.heal(state)) }
        }
    }
    RbAction("Zurück", onClick = onClose)
}

@Composable
private fun ShopRow(title: String, text: String, enabled: Boolean, badge: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), color = Panel2, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) Color.White else Muted, fontWeight = FontWeight.SemiBold)
                Text(text, color = Muted, fontSize = 12.sp)
            }
            badge?.let { Text(it, color = Success, fontSize = 11.sp) }
        }
    }
}

private fun skyFor(slot: TimeSlot): List<Color> = when (slot) {
    TimeSlot.MORNING -> listOf(Color(0xFFF6D8A8), Color(0xFFA9CBE3))
    TimeSlot.NOON -> listOf(Color(0xFF8EC3EA), Color(0xFFCFE6F5))
    TimeSlot.AFTERNOON -> listOf(Color(0xFFE9C287), Color(0xFF9DBED8))
    TimeSlot.EVENING -> listOf(Color(0xFF5A3A6A), Color(0xFFE77A3C))
    TimeSlot.NIGHT -> listOf(Color(0xFF070A16), Color(0xFF1B2440))
}

private fun DrawScope.drawMarketSquare(slot: TimeSlot, tw: Float, th: Float, backH: Float, time: Float, open: Boolean) {
    val w = size.width
    drawRect(Brush.verticalGradient(skyFor(slot), 0f, backH))
    // Timber-framed houses along the back.
    val roofs = listOf(Color(0xFF7A3A2A), Color(0xFF5A4A3A), Color(0xFF6A2E2E), Color(0xFF4A4A52))
    for (i in 0 until 6) {
        val x = i * w / 6f
        val hw = w / 6f
        val top = backH * (.25f + (i % 3) * .08f)
        drawRect(Color(0xFFD8C7A6).mix(Color.Black, .1f + (i % 2) * .08f), Offset(x, top + tw * .5f), Size(hw, backH - top))
        drawPath(Path().apply { moveTo(x - tw * .1f, top + tw * .55f); lineTo(x + hw / 2f, top - tw * .25f); lineTo(x + hw + tw * .1f, top + tw * .55f); close() }, roofs[i % roofs.size])
        for (k in 0..2) drawLine(Color(0xFF3A2516), Offset(x + hw * k / 2f, top + tw * .5f), Offset(x + hw * k / 2f, backH), tw * .07f)
        drawLine(Color(0xFF3A2516), Offset(x, top + tw * 1.2f), Offset(x + hw, top + tw * 1.2f), tw * .06f)
        val win = Offset(x + hw * .3f, top + tw * .7f)
        val lit = slot == TimeSlot.EVENING || slot == TimeSlot.NIGHT
        drawRect(if (lit) Color(0xFFFFC46A) else Color(0xFF2A3A4A), win, Size(tw * .45f, tw * .38f))
        drawRect(Color(0xFF3A2516), win, Size(tw * .45f, tw * .38f), style = Stroke(tw * .05f))
    }
    // Cobblestones.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6E665A), Color(0xFF8A8070)), backH, size.height), Offset(0f, backH), Size(w, size.height - backH))
    for (r in 0 until 18) {
        val y = backH + r * th * .5f
        var x = if (r % 2 == 0) 0f else tw * .35f
        while (x < w) {
            drawRoundRect(Color.Black.copy(alpha = .12f), Offset(x, y), Size(tw * .62f, th * .42f), CornerRadius(tw * .12f), style = Stroke(1.6f))
            x += tw * .7f
        }
    }
    // Bunting across the square.
    for (k in 0 until 14) {
        val x0 = k * w / 14f
        val sag = sin((k + .5f) / 14f * 3.1416f) * tw * .5f
        drawPath(Path().apply { moveTo(x0, tw * .3f + sag); lineTo(x0 + w / 14f, tw * .3f + sag); lineTo(x0 + w / 28f, tw * .7f + sag); close() },
            listOf(Color(0xFFB98E3A), Color(0xFF7A2430), Color(0xFF2F4A6A))[k % 3])
    }
    val dark = when (slot) { TimeSlot.EVENING -> .25f; TimeSlot.NIGHT -> .5f; else -> 0f }
    if (dark > 0f) drawRect(Color(0xFF05070C).copy(alpha = dark))
    if (!open) drawRect(Color(0xFF05070C).copy(alpha = .15f))
}

private fun DrawScope.drawMarketRow(row: Int, tw: Float, th: Float, backH: Float, open: Boolean) {
    val l = MarketEngine.layout
    fun cell(c: Cell) = Offset(tw * (c.x + .5f), backH + th * (c.y + .5f))
    val awnings = mapOf(MarketEngine.Stall.SMITH to listOf(Color(0xFF7A2430), Color(0xFFE6DCC6)),
        MarketEngine.Stall.TRADER to listOf(Color(0xFF2F4A6A), Color(0xFFE6DCC6)), MarketEngine.Stall.HEALER to listOf(Color(0xFF3F6B3A), Color(0xFFE6DCC6)))
    l.stalls.forEach { (stall, cells) ->
        if (cells.first().y != row) return@forEach
        val x0 = tw * cells.first().x
        val x1 = tw * (cells.last().x + 1)
        val c = cell(cells.first())
        // Counter.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF8A6038), Color(0xFF4A2E18)), c.y - th * .3f, c.y + th * .5f), Offset(x0, c.y - th * .3f), Size(x1 - x0, th * .8f))
        // Goods.
        for (k in 0 until cells.size * 2) {
            val gx = x0 + tw * (.25f + k * .5f)
            when (stall) {
                MarketEngine.Stall.SMITH -> drawLine(Color(0xFFB8BEC6), Offset(gx, c.y - th * .3f), Offset(gx + tw * .2f, c.y - tw * .55f), tw * .05f, StrokeCap.Round)
                MarketEngine.Stall.TRADER -> drawCircle(listOf(Color(0xFFE79A92), Color(0xFFD9B25C), Color(0xFF9FC3DD))[k % 3], tw * .1f, Offset(gx, c.y - th * .4f))
                MarketEngine.Stall.HEALER -> drawRoundRect(Color(0xFF6E9A5A), Offset(gx - tw * .06f, c.y - th * .6f), Size(tw * .12f, tw * .2f), CornerRadius(tw * .03f))
            }
        }
        // Posts and striped awning above.
        val awnTop = c.y - tw * 2.1f
        drawLine(Color(0xFF3A2516), Offset(x0 + tw * .05f, c.y + th * .5f), Offset(x0 + tw * .05f, awnTop), tw * .07f)
        drawLine(Color(0xFF3A2516), Offset(x1 - tw * .05f, c.y + th * .5f), Offset(x1 - tw * .05f, awnTop), tw * .07f)
        val stripes = ((x1 - x0) / (tw * .35f)).toInt().coerceAtLeast(1)
        for (k in 0 until stripes) {
            drawPath(Path().apply {
                val sx = x0 + k * (x1 - x0) / stripes
                val ex = x0 + (k + 1) * (x1 - x0) / stripes
                moveTo(sx, awnTop); lineTo(ex, awnTop); lineTo(ex, awnTop + tw * .45f); lineTo(sx, awnTop + tw * .55f); close()
            }, awnings.getValue(stall)[k % 2].copy(alpha = if (open) 1f else .55f))
        }
    }
    l.well.firstOrNull()?.takeIf { it.y == row }?.let { wc ->
        val c = Offset(tw * (wc.x + .5f), backH + th * (wc.y + 1f))
        drawOval(Color(0xFF8A8478), Offset(c.x - tw * .85f, c.y - th * .5f), Size(tw * 1.7f, th * 1.1f))
        drawOval(Color(0xFF1E3A44), Offset(c.x - tw * .6f, c.y - th * .35f), Size(tw * 1.2f, th * .7f))
        drawRect(Color(0xFF6C665E), Offset(c.x - tw * .85f, c.y), Size(tw * 1.7f, th * .6f))
        drawLine(Color(0xFF3A2516), Offset(c.x - tw * .7f, c.y), Offset(c.x - tw * .7f, c.y - tw * 1.4f), tw * .08f)
        drawLine(Color(0xFF3A2516), Offset(c.x + tw * .7f, c.y), Offset(c.x + tw * .7f, c.y - tw * 1.4f), tw * .08f)
        drawPath(Path().apply { moveTo(c.x - tw * .95f, c.y - tw * 1.3f); lineTo(c.x, c.y - tw * 1.9f); lineTo(c.x + tw * .95f, c.y - tw * 1.3f); close() }, Color(0xFF7A3A2A))
    }
    l.crates.filter { it.y == row }.forEach { cr ->
        val c = cell(cr)
        drawRect(Color(0xFF8A6038), Offset(c.x - tw * .38f, c.y - tw * .5f), Size(tw * .76f, tw * .7f))
        drawRect(Color(0xFF4A2E18), Offset(c.x - tw * .38f, c.y - tw * .5f), Size(tw * .76f, tw * .7f), style = Stroke(tw * .05f))
        drawLine(Color(0xFF4A2E18), Offset(c.x - tw * .38f, c.y - tw * .5f), Offset(c.x + tw * .38f, c.y + tw * .2f), tw * .04f)
    }
}

// ======================================================================== skirmish

/** A personal fight: own side, enemies, bars, the round log and five clear choices. */
@Composable
fun SkirmishScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val fight = state.life.skirmish ?: return
    val allies = SkirmishEngine.allies(state, fight)
    val playerLook = state.life.appearance ?: defaultLook(state.life.gender, state.player.species)
    val infinite = rememberInfiniteTransition(label = "fight")
    val time by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().testTag("skirmish")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(fight.kind.label.uppercase(), color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text(fight.title, color = Color.White, fontFamily = Cinzel, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${fight.enemyCount} × ${fight.enemyName} · Runde ${fight.round}/${SkirmishEngine.MAX_ROUNDS} · Kräfteverhältnis ${SkirmishEngine.estimate(state, fight)} %", color = Mist, fontSize = 12.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(210.dp)) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A3440), Color(0xFF3A3226), Color(0xFF241C14)), 0f, h))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4A5A3A), Color(0xFF2A3220)), h * .55f, h), Offset(0f, h * .55f), Size(w, h * .45f))
            val unit = h / 150f
            val sway = sin(time * 6.283f) * unit * 2f
            drawFullFigure(playerLook, state.life.gender, Offset(w * .2f + sway, h * .95f), unit, detailed = false, walkPhase = if (fight.outcome == null) time else 0f)
            allies.forEachIndexed { i, (npc, _) ->
                drawFullFigure(npc.appearance, npc.gender, Offset(w * (.08f + (i % 3) * .1f), h * (.8f + (i / 3) * .1f)), unit * .85f, detailed = false)
            }
            val shown = fight.enemyCount.coerceAtMost(6)
            val alive = (shown * fight.enemyHealth / 100f).roundToInt().coerceIn(if (fight.outcome == SkirmishOutcome.VICTORY) 0 else 1, shown)
            for (i in 0 until alive) {
                val x = w * (.62f + (i % 3) * .12f)
                val y = h * (.82f + (i / 3) * .12f)
                if (fight.kind == SkirmishKind.BEASTS) drawWolf(Offset(x, y), unit)
                else drawFullFigure(enemyLook(fight, i), Gender.MALE, Offset(x - sway, y), unit * .9f, detailed = false)
            }
            if (fight.outcome == null) drawLine(Color.White.copy(alpha = .15f + .1f * sin(time * 12f)), Offset(w * .35f, h * .6f), Offset(w * .55f, h * .62f), unit * 2f, StrokeCap.Round)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RelationBar("Du", fight.playerHealth, Success)
            RelationBar("Feind", fight.enemyHealth, Danger)
            RelationBar("Feindmoral", fight.enemyMorale, Gold)
            if (allies.isNotEmpty()) Text("An deiner Seite: " + allies.joinToString { it.first.firstName }, color = Mist, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fight.log.takeLast(6).forEach { Text(it, color = Mist, fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 21.sp) }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fight.outcome == null) {
                SkirmishAction.entries.filter { it != SkirmishAction.RALLY || allies.isNotEmpty() || state.player.leadership >= 40 }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { a ->
                            Surface(onClick = { val r = SkirmishEngine.act(state, a); onState(r.state) }, modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                                color = if (a == SkirmishAction.RETREAT) Color(0xFF221719) else Panel2, shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (a == SkirmishAction.ATTACK) Gold.copy(alpha = .5f) else Color.White.copy(alpha = .08f))) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(a.label, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(a.hint, color = Muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            } else {
                PremiumPanel(emphasized = fight.outcome == SkirmishOutcome.VICTORY) {
                    Text(fight.outcome?.label ?: "", color = if (fight.outcome == SkirmishOutcome.VICTORY) PaleGold else Danger, fontFamily = Cinzel, fontSize = 24.sp)
                    Text(when (fight.outcome) {
                        SkirmishOutcome.VICTORY -> "+${fight.reward} Gold · +${fight.renown} Ruf"
                        SkirmishOutcome.CAPTURED -> "${fight.enemyName} schleppen dich fort."
                        SkirmishOutcome.DEFEAT -> "Deine Wunden brauchen Tage."
                        else -> "Du kommst mit dem Leben davon."
                    }, color = Mist)
                    if (state.life.wound > 0) RelationBar("Wunden", state.life.wound, Danger)
                }
                GoldButton("Weiter", { val r = SkirmishEngine.close(state); onState(r.state); if (r.message.isNotBlank()) onNotice(r.message) }, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun enemyLook(fight: Skirmish, i: Int): CharacterAppearance {
    val ork = fight.enemyName.contains("Ork")
    return CharacterAppearance(
        skinTone = if (ork) 5 else (i * 2) % 6, hairStyle = if (ork) 7 else (i * 3) % 7, hairColor = 0, beard = if (ork) 0 else (i % 4) + 1,
        armor = if (ork) 3 else 1, cloak = if (i % 2 == 0) 1 else 0, primaryColor = 5, accentColor = 3, weapon = if (i % 2 == 0) 3 else 0,
        build = 2, muscle = 70, height = 55 + i * 5,
    )
}

private fun DrawScope.drawWolf(at: Offset, u: Float) {
    val fur = Color(0xFF5A5650)
    drawOval(Color.Black.copy(alpha = .3f), Offset(at.x - 24f * u, at.y - 4f * u), Size(48f * u, 8f * u))
    drawOval(Brush.verticalGradient(listOf(fur.mix(Color.White, .2f), fur.mix(Color.Black, .3f)), at.y - 34f * u, at.y - 14f * u), Offset(at.x - 22f * u, at.y - 34f * u), Size(40f * u, 22f * u))
    for (dx in listOf(-16f, -8f, 8f, 14f)) drawLine(fur.mix(Color.Black, .3f), Offset(at.x + dx * u, at.y - 18f * u), Offset(at.x + dx * u, at.y), 4f * u, StrokeCap.Round)
    drawPath(Path().apply { moveTo(at.x - 20f * u, at.y - 30f * u); lineTo(at.x - 36f * u, at.y - 34f * u); lineTo(at.x - 34f * u, at.y - 24f * u); lineTo(at.x - 20f * u, at.y - 20f * u); close() }, fur)
    drawPath(Path().apply { moveTo(at.x - 30f * u, at.y - 34f * u); lineTo(at.x - 28f * u, at.y - 42f * u); lineTo(at.x - 24f * u, at.y - 33f * u); close() }, fur.mix(Color.Black, .2f))
    drawCircle(Color(0xFFE8C04A), 1.4f * u, Offset(at.x - 31f * u, at.y - 31f * u), blendMode = BlendMode.SrcOver)
    drawLine(fur, Offset(at.x + 18f * u, at.y - 28f * u), Offset(at.x + 30f * u, at.y - 20f * u), 4f * u, StrokeCap.Round)
}

// ======================================================================== captivity

@Composable
fun CaptiveScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val life = state.life
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0806), Color(0xFF1A120C)))).testTag("captive"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).statusBarsPadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("GEFANGEN", color = Danger, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("In der Hand von ${life.captor ?: "Fremden"}", color = Color.White, fontFamily = Cinzel, fontSize = 24.sp)
            Text("Ein feuchtes Zelt, ein Pflock, ein Strick um die Handgelenke. Sie wollen Gold – oder dass du verschwindest. Freilassung spätestens an Tag ${life.captiveUntil}.",
                color = Mist, fontFamily = Cormorant, fontSize = 18.sp, lineHeight = 24.sp)
            RelationBar("Wunden", life.wound, Danger)
            GoldButton("Fliehen versuchen", { val r = SkirmishEngine.escape(state); onState(r.state); onNotice(r.message) }, Modifier.fillMaxWidth())
            RbAction("Freikaufen (${SkirmishEngine.ransomPrice(state)} Gold)", enabled = life.purse >= SkirmishEngine.ransomPrice(state)) {
                val r = SkirmishEngine.ransom(state); onState(r.state); onNotice(r.message)
            }
            RbAction("Abwarten (ein Tag vergeht)") {
                val r = LifeEngine.advanceLandlessDay(state); onState(r.state); onNotice(r.message)
            }
        }
    }
}

// ======================================================================== party

/** The following on the people tab: who travels with you, wages, wounds, dismissal. */
@Composable
internal fun PartySection(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val members = PartyEngine.members(state)
    SectionTitle("Gefolge · ${members.size}/${PartyEngine.MAX_PARTY}")
    if (members.isEmpty()) Text("Niemand reist mit dir. Freunde kannst du im Gespräch bitten, mitzukommen; Söldnerführer vermieten Klingen.", color = Muted, fontSize = 12.sp)
    members.forEach { npc ->
        val m = state.life.party.first { it.npcId == npc.id }
        PremiumPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CharacterPortrait(npc.appearance, npc.gender, Modifier.size(width = 60.dp, height = 75.dp).clip(RoundedCornerShape(12.dp)), Framing.BUST)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(npc.name, color = Color.White, fontFamily = Cinzel, fontWeight = FontWeight.Bold)
                    Text("${npc.profession} · ${npc.combatStyle}" + if (m.wage > 0) " · ${m.wage} Gold/Tag" else " · Freund", color = Mist, fontSize = 12.sp)
                    RelationBar("Kampfkraft", PartyEngine.power(npc, m).coerceAtMost(100), Gold)
                    if (m.wounded > 0) RelationBar("Verwundet", m.wounded, Danger)
                }
            }
            RbAction(if (m.wage > 0) "Entlassen" else "Wege trennen") { val r = PartyEngine.dismiss(state, npc.id); onState(r.state); onNotice(r.message) }
        }
    }
}
