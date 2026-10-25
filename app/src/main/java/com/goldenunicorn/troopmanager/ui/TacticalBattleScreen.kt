package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.TacticalBattleEngine
import com.goldenunicorn.troopmanager.engine.TacticalBattleEngine.Stage
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * v2.0 battle: plan → simulation → turning point → simulation → end. The field is drawn live;
 * the player decides three times and watches the rest. Experts can still open the detailed command view.
 */
@Composable
internal fun TacticalBattleScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val battle = state.battleSession ?: return
    var expert by rememberSaveable(battle.seed) { mutableStateOf(false) }
    if (expert) {
        Box(Modifier.fillMaxSize()) {
            LiveBattleScreen(state, onState, onNotice)
            TextButton({ expert = false }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) { Text("Taktikansicht", color = Gold, fontSize = 12.sp) }
        }
        return
    }
    val haptics = LocalHapticFeedback.current
    var paused by rememberSaveable(battle.seed) { mutableStateOf(false) }
    var speed by rememberSaveable { mutableIntStateOf(1) }
    val stage = TacticalBattleEngine.stage(battle)
    fun apply(result: GameEngine.ActionResult) { onState(result.state); if (result.message.isNotBlank()) onNotice(result.message) }

    // The simulation clock: one combat step every 0.9 s (faster with the speed toggle).
    val running = battle.isActive && !paused && stage in listOf(Stage.RUNNING, Stage.AFTER_MIDDLE, Stage.AFTER_FINAL)
    LaunchedEffect(battle.step, battle.tacticalStage, battle.status, running, speed) {
        if (!running) return@LaunchedEffect
        delay((if (state.settings.animations) 900L else 250L) / speed)
        val result = TacticalBattleEngine.step(state)
        if (result.state.battleSession?.tacticalStage != battle.tacticalStage) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onState(result.state)
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        BattleHeader(state, battle, stage, onExpert = { expert = true })
        Box(Modifier.weight(1.3f).fillMaxWidth().padding(horizontal = 10.dp).testTag("tactical_field")) {
            BattleSimulation(battle, state.settings.animations, Modifier.fillMaxSize())
        }
        // The bottom panel may use up to its share of the height and scrolls inside it, so the field always stays visible.
        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        when {
            !battle.isActive -> BattleResultPanel(state, battle) { onState(state.copy(battleSession = null)) }
            stage == Stage.PLAN -> PlanPicker(state, battle, onPlan = { plan ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                apply(TacticalBattleEngine.choosePlan(state, plan))
            }, onQuick = { onState(TacticalBattleEngine.autoResolve(state)) })
            stage == Stage.MIDDLE || stage == Stage.FINAL -> DecisionOverlay(state, battle, stage, Modifier.padding(horizontal = 10.dp)) { decision ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                apply(TacticalBattleEngine.decide(state, decision))
            }
            else -> SimulationControls(battle, paused, speed, onPause = { paused = !paused }, onSpeed = { speed = if (speed >= 4) 1 else speed * 2 },
                onQuick = { onState(TacticalBattleEngine.autoResolve(state)) })
        }
        }
    }
}

@Composable
private fun BattleHeader(state: GameState, battle: BattleSession, stage: Stage, onExpert: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (TacticalBattleEngine.isWall(battle)) "VERTEIDIGUNG DER MAUER" else "FELDSCHLACHT") + " · MINUTE ${battle.minute}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(battle.tacticalTitle ?: battle.enemyArmyName ?: battle.location ?: battle.enemy.label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
            TextButton(onExpert) { Text("Details", color = Muted, fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val step = when (stage) { Stage.PLAN -> 0; Stage.RUNNING, Stage.MIDDLE -> 1; else -> 2 }
            listOf("Schlachtplan", if (TacticalBattleEngine.isWall(battle)) "Mauer fällt" else "Zusammenprall", "Entscheidung").forEachIndexed { i, label ->
                val done = i < step || (i == step && stage in listOf(Stage.AFTER_MIDDLE, Stage.AFTER_FINAL))
                val active = i == step
                Text("${i + 1} $label", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (active) Ink else if (done) Emerald else Muted,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (active) Gold else StoneRaised).padding(horizontal = 6.dp, vertical = 6.dp), maxLines = 1)
            }
        }
        StrengthBar("Dein Heer", battle.fightingRemaining, battle.ownStart, Emerald, "Moral ${battle.morale} %")
        val enemyMorale = if (battle.enemyRemaining == 0) 0 else (battle.fronts.sumOf { it.morale.toLong() * it.enemySoldiers } / battle.enemyRemaining.coerceAtLeast(1)).toInt()
        StrengthBar(battle.enemyFactionName ?: battle.enemy.label, battle.enemyRemaining, battle.enemyStart, Crimson, "Moral $enemyMorale %")
        TacticalBattleEngine.plan(battle)?.let { Text("Plan: ${it.label} · ${battle.tacticalStageLabel}", color = Mist, fontSize = 12.sp) }
    }
}

@Composable
private fun StrengthBar(label: String, value: Int, max: Int, color: Color, extra: String) {
    val fraction by animateFloatAsState((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f), tween(600), label = label)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${fmt(value)} / ${fmt(max)} · $extra", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .08f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
        }
    }
}

// --------------------------------------------------------------------- the field

private fun unitColor(type: UnitType?): Color = when {
    type == null -> Color(0xFFB0B8C0)
    type == UnitType.KNIGHT -> Color(0xFFE8C766)
    type == UnitType.DRAGON_ARTILLERY -> Color(0xFFE07B3C)
    type.ranged >= 8 -> Color(0xFF7CCB8A)
    type.culture == Culture.GOLD_ELF -> Color(0xFFF2DFA0)
    type.culture == Culture.WALL -> Color(0xFF7FB6E6)
    else -> Color(0xFFCFD8E0)
}

private fun enemyColor(enemy: EnemyType): Color = when (enemy) {
    EnemyType.ORC -> Color(0xFFD0574A)
    EnemyType.URUK -> Color(0xFF9B2F2F)
    EnemyType.TAO_TEI -> Color(0xFF3FB8A8)
}

@Composable
internal fun BattleSimulation(battle: BattleSession, animations: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val wall = TacticalBattleEngine.isWall(battle)
    val time by rememberInfiniteTransition(label = "battle").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "t")
    val t = if (animations) time else 0f
    val sections = listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT)
    // Smoothly animated distances per section.
    val enemyDistance = sections.associateWith { s ->
        val d = battle.fronts.firstOrNull { it.section == s }?.enemyDistance ?: 180
        animateFloatAsState(d.coerceIn(0, 180) / 180f, tween(800), label = "d$s").value
    }
    val lastReport = battle.exchanges.lastOrNull()
    val melee = battle.segments.any { it.contactState.allowsMelee } || battle.phase in listOf(BattlePhase.MAIN, BattlePhase.CONTACT, BattlePhase.WALL, BattlePhase.BREACH, BattlePhase.COURTYARD)
    val ranged = lastReport?.fronts?.any { it.arrowsUsed > 0 || it.enemyArrowsUsed > 0 } == true || battle.phase == BattlePhase.RANGED
    val ownStart = battle.ownStart.coerceAtLeast(1)
    val enemyStart = battle.enemyStart.coerceAtLeast(1)
    val scale = maxOf(ownStart, enemyStart) / 150f // ~150 figures for the larger army
    Canvas(
        modifier.clip(RoundedCornerShape(20.dp)).semantics {
            contentDescription = "Schlachtfeld: ${battle.fightingRemaining} eigene gegen ${battle.enemyRemaining} feindliche Soldaten"
        }
    ) {
        val w = size.width
        val h = size.height
        // Ground
        drawRect(Brush.verticalGradient(
            if (wall) listOf(Color(0xFF2A2A24), Color(0xFF3B3A2C), Color(0xFF26302A))
            else listOf(Color(0xFF2E3A26), Color(0xFF3D4A2C), Color(0xFF2B3624))))
        // Furrows for depth
        for (i in 0 until 9) drawLine(Color.Black.copy(alpha = .08f), Offset(0f, h * (0.1f + i * 0.1f)), Offset(w, h * (0.12f + i * 0.1f)), strokeWidth = 2f)
        val contactY = if (wall) h * 0.50f else h * 0.48f
        val wallY = h * 0.55f
        if (wall) drawWall(battle, w, wallY, h)
        sections.forEachIndexed { index, section ->
            val x0 = w * index / 3f
            val cw = w / 3f
            val front = battle.fronts.firstOrNull { it.section == section }
            val segment = battle.segment(section)
            val breached = segment?.contactState in listOf(BattleContactState.BREACHED, BattleContactState.COURTYARD)
            val dist = enemyDistance.getValue(section)
            // Enemy block
            val enemyCount = ((front?.enemySoldiers ?: 0) / scale).toInt().coerceIn(if ((front?.enemySoldiers ?: 0) > 0) 1 else 0, 160)
            val enemyFront = contactY - (contactY - h * 0.08f) * dist
            val routedEnemy = front?.intent == BattleAiIntent.WITHDRAW
            drawFormation(enemyCount, x0, cw, enemyFront, -1f, enemyColor(battle.enemy), t, index * 97 + 7, routedEnemy, breached && wall, wallY)
            // Own block
            val own = battle.contingents.filter { it.section == section && it.soldiers > 0 }
            val ownFront = if (wall) (if (segment?.contactState == BattleContactState.WALL_ASSAULT || breached) wallY - h * 0.01f else wallY + h * 0.04f)
                else h * 0.90f - (h * 0.90f - contactY - h * 0.02f) * (1f - dist).coerceIn(0f, 1f)
            var row = 0
            own.groupBy { it.type }.forEach { (type, list) ->
                val n = (list.sumOf { it.soldiers } / scale).toInt().coerceIn(1, 120)
                val routed = list.all { it.routed }
                drawFormation(n, x0, cw, ownFront + row * h * 0.035f, 1f, unitColor(type), t, index * 31 + type.ordinal * 13, routed, false, wallY)
                row += 1 + n / 28
            }
            // Arrows
            if (ranged && dist > 0.05f) {
                for (k in 0 until 6) {
                    val p = ((t + k / 6f) % 1f)
                    val ax = x0 + cw * (0.15f + 0.7f * ((k * 37 + index * 11) % 10) / 10f)
                    val fromY = ownFront - 6f
                    val toY = enemyFront + 10f
                    val y = fromY + (toY - fromY) * p
                    val arc = sin(p * PI).toFloat() * h * 0.05f
                    drawLine(Color(0xFFEDE3C8).copy(alpha = .75f), Offset(ax, y - arc), Offset(ax + 2f, y - arc - 8f), strokeWidth = 1.6f)
                    if ((front?.enemySoldiers ?: 0) > 0 && k % 2 == 0) {
                        val ey = toY + (fromY - toY) * p
                        drawLine(Color(0xFFE06A5A).copy(alpha = .6f), Offset(ax + 10f, ey - arc), Offset(ax + 12f, ey - arc + 8f), strokeWidth = 1.4f)
                    }
                }
            }
            // Melee sparks on the contact line
            if (melee && segment?.contactState?.allowsMelee == true || (!wall && melee && dist < 0.08f)) {
                val lineY = if (wall && !breached) wallY - h * 0.02f else (ownFront + enemyFront) / 2f
                for (k in 0 until 5) {
                    val phase = ((t * 3 + k * 0.37f) % 1f)
                    val sx = x0 + cw * (0.12f + 0.76f * ((k * 53 + index * 7) % 17) / 17f)
                    drawCircle(Color(0xFFFFE08A).copy(alpha = (1f - phase) * .9f), 2f + phase * 7f, Offset(sx, lineY + (k % 3 - 1) * 6f), style = Stroke(1.6f))
                }
            }
            // Section label with counts and last losses
            val label = "${fmt(battle.fighting(section))} : ${fmt(front?.enemySoldiers ?: 0)}"
            val lossOwn = lastReport?.fronts?.firstOrNull { it.section == section }?.ownDamage?.total ?: 0
            val lossEnemy = lastReport?.fronts?.firstOrNull { it.section == section }?.enemyDamage?.total ?: 0
            val layout = measurer.measure(label, TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            drawRoundLabel(x0 + cw / 2 - layout.size.width / 2f, h - layout.size.height - 8f, layout.size.width.toFloat(), layout.size.height.toFloat())
            drawText(layout, topLeft = Offset(x0 + cw / 2 - layout.size.width / 2f, h - layout.size.height - 8f))
            if (lossEnemy > 0) drawText(measurer.measure("−${fmt(lossEnemy)}", TextStyle(color = Color(0xFFFF8C7A), fontSize = 11.sp, fontWeight = FontWeight.Black)),
                topLeft = Offset(x0 + cw * 0.62f, enemyFront - h * 0.06f - t * 10f))
            if (lossOwn > 0) drawText(measurer.measure("−${fmt(lossOwn)}", TextStyle(color = Color(0xFFFFD27A), fontSize = 11.sp, fontWeight = FontWeight.Black)),
                topLeft = Offset(x0 + cw * 0.12f, ownFront + h * 0.05f))
        }
        val phase = measurer.measure(battle.tacticalStageLabel.uppercase(), TextStyle(color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp))
        drawText(phase, topLeft = Offset(12f, 10f))
    }
}

private fun DrawScope.drawRoundLabel(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(Color.Black.copy(alpha = .45f), Offset(x - 8f, y - 3f), Size(w + 16f, h + 6f), androidx.compose.ui.geometry.CornerRadius(10f, 10f))
}

private fun DrawScope.drawWall(battle: BattleSession, w: Float, wallY: Float, h: Float) {
    val thickness = h * 0.05f
    listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT).forEachIndexed { i, section ->
        val seg = battle.segment(section)
        val integrity = (seg?.integrity ?: 100) / 100f
        val breached = seg?.contactState in listOf(BattleContactState.BREACHED, BattleContactState.COURTYARD)
        val x0 = w * i / 3f
        val x1 = w * (i + 1) / 3f
        val stone = Color(0xFF8E8A7E)
        if (breached) {
            val gap = (x1 - x0) * 0.28f
            val mid = (x0 + x1) / 2f
            drawRect(stone, Offset(x0, wallY), Size(mid - gap / 2 - x0, thickness))
            drawRect(stone, Offset(mid + gap / 2, wallY), Size(x1 - mid - gap / 2, thickness))
            for (k in 0 until 9) drawCircle(Color(0xFF6E6A60), 3f + (k % 3) * 2f, Offset(mid - gap / 2 + gap * k / 8f, wallY + thickness * (0.3f + (k % 2) * 0.5f)))
        } else {
            drawRect(stone.copy(alpha = 0.55f + 0.45f * integrity), Offset(x0, wallY), Size(x1 - x0, thickness))
        }
        // Battlements
        var x = x0 + 4f
        while (x < x1 - 8f) {
            if (!breached || x < (x0 + x1) / 2 - (x1 - x0) * 0.16f || x > (x0 + x1) / 2 + (x1 - x0) * 0.16f)
                drawRect(Color(0xFFA8A396), Offset(x, wallY - thickness * 0.35f), Size(8f, thickness * 0.35f))
            x += 16f
        }
        // Cracks show damage
        if (integrity < 0.7f) for (k in 0 until ((1 - integrity) * 8).toInt()) {
            val cx = x0 + (x1 - x0) * ((k * 29 + i * 13) % 17) / 17f
            drawLine(Color(0xFF3A3630), Offset(cx, wallY + 2f), Offset(cx + 6f, wallY + thickness - 2f), strokeWidth = 1.5f)
        }
        if (section == BattleSection.CENTER) {
            val gate = (seg?.gateIntegrity ?: 100) / 100f
            drawRect(Color(0xFF5A3C22).copy(alpha = 0.35f + 0.65f * gate), Offset((x0 + x1) / 2 - 16f, wallY), Size(32f, thickness))
        }
    }
}

/** A block of figures; direction -1 faces down (enemy), +1 faces up (own). */
private fun DrawScope.drawFormation(count: Int, x0: Float, cw: Float, frontY: Float, direction: Float, color: Color, t: Float, seed: Int, routed: Boolean, pastWall: Boolean, wallY: Float) {
    if (count <= 0) return
    val perRow = 14
    val spacingX = cw * 0.8f / perRow
    val spacingY = size.height * 0.022f
    for (i in 0 until count) {
        val r = i / perRow
        val c = i % perRow
        val jitter = sin((t * 2 * PI + (i * 1.7 + seed)).toFloat()) * 1.4f
        var x = x0 + cw * 0.1f + c * spacingX + (if (r % 2 == 1) spacingX / 2 else 0f) + jitter
        var y = frontY - direction * r * spacingY
        if (routed) { y -= direction * size.height * 0.12f * t; x += (if (c % 2 == 0) -1 else 1) * 10f * t }
        if (pastWall && i % 4 == 0) y = wallY + size.height * (0.04f + (i % 7) * 0.012f)
        val alpha = if (routed) 0.45f else 1f
        drawCircle(color.copy(alpha = alpha), 3.4f, Offset(x, y))
        drawCircle(Color.Black.copy(alpha = .35f * alpha), 3.4f, Offset(x, y), style = Stroke(0.8f))
    }
}

// --------------------------------------------------------------------- panels

@Composable
private fun PlanPicker(state: GameState, battle: BattleSession, onPlan: (TacticalBattleEngine.Plan) -> Unit, onQuick: () -> Unit) {
    val plans = TacticalBattleEngine.plans(battle)
    val recommended = TacticalBattleEngine.recommendedPlan(state, battle)
    Column(Modifier.fillMaxWidth().background(StoneRaised).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Entscheidung 1 · Dein Schlachtplan", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text("${fmt(battle.ownStart)} deiner Soldaten gegen ${fmt(battle.enemyStart)} ${battle.enemyFactionName ?: battle.enemy.label}.", color = Mist, fontSize = 13.sp)
        val odds = TacticalBattleEngine.odds(battle)
        Text("Kräfteverhältnis: ${odds.label} · Siegchance etwa ${odds.percent} %", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when { odds.percent >= 65 -> Emerald; odds.percent >= 40 -> Gold; else -> Crimson })
        plans.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { plan ->
                    val best = plan == recommended
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF17212B))
                            .border(1.dp, if (best) Gold else Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
                            .clickable { onPlan(plan) }.testTag("plan_${plan.name}").padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(plan.label + if (best) " ★" else "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(plan.description, color = Muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 4)
                    }
                }
            }
        }
        TextButton(onQuick, Modifier.fillMaxWidth()) { Text("Schnell entscheiden (ohne Zuschauen)", color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun SimulationControls(battle: BattleSession, paused: Boolean, speed: Int, onPause: () -> Unit, onSpeed: () -> Unit, onQuick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(StoneRaised).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        battle.log.takeLast(2).forEach { Text("${it.minute}′ · ${it.text}", color = Mist, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onPause, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (paused) "Weiter" else "Pause") }
            OutlinedButton(onSpeed, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Tempo ×$speed") }
            OutlinedButton(onQuick, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Zum Ende", maxLines = 1) }
        }
    }
}

@Composable
private fun DecisionOverlay(state: GameState, battle: BattleSession, stage: Stage, modifier: Modifier, onDecide: (BattleDecision) -> Unit) {
    val event = battle.pendingEvent ?: return
    val choices = TacticalBattleEngine.choices(state, battle)
    AnimatedVisibility(true, modifier = modifier, enter = fadeIn() + scaleIn(initialScale = .92f), exit = fadeOut()) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF2101820))
                .border(1.dp, Gold.copy(alpha = .6f), RoundedCornerShape(20.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(if (stage == Stage.MIDDLE) "ENTSCHEIDUNG 2 · WENDEPUNKT" else "ENTSCHEIDUNG 3 · DAS ENDE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(event.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Text(event.text, color = Mist, fontSize = 13.sp, lineHeight = 18.sp)
            choices.forEach { choice ->
                Surface(onClick = { onDecide(choice.decision) }, color = Color(0xFF1C2834), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("decision_${choice.decision.name}")) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(choice.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(choice.detail, color = Muted, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }
            if (choices.isEmpty()) GoldButton("Weiterkämpfen", { onDecide(BattleDecision.HOLD) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BattleResultPanel(state: GameState, battle: BattleSession, onClose: () -> Unit) {
    val victory = battle.status == BattleStatus.VICTORY
    Column(Modifier.fillMaxWidth().background(StoneRaised).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(battle.outcomeGrade?.label ?: if (victory) "Sieg" else "Niederlage", color = if (victory) Emerald else Crimson, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("Eigene Verluste: ${fmt(battle.ownStart - battle.ownRemaining)} (${battle.casualties.dead} gefallen, ${battle.casualties.wounded} verwundet) · Feind: ${fmt(battle.enemyStart - battle.enemyRemaining)}", color = Mist, fontSize = 13.sp)
        if (victory) Text("Beute ${fmt(battle.lootGold)} Gold, ${fmt(battle.lootFood)} Nahrung · +${battle.renownReward} Ruhm · +${battle.xpReward} XP", color = Gold, fontSize = 13.sp)
        TacticalBattleEngine.plan(battle)?.let { Text("Dein Plan: ${it.label}", color = Muted, fontSize = 12.sp) }
        GoldButton("Weiter", onClose, Modifier.fillMaxWidth().testTag("battle_result_continue"))
    }
}
