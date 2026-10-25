package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var resolving by remember(battle.seed) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var speed by rememberSaveable { mutableIntStateOf(2) }
    val stage = TacticalBattleEngine.stage(battle)
    fun apply(result: GameEngine.ActionResult) { onState(result.state); if (result.message.isNotBlank()) onNotice(result.message) }

    // The simulation clock: one combat step every 0.9 s (faster with the speed toggle).
    val running = battle.isActive && !paused && !resolving && stage in listOf(Stage.RUNNING, Stage.AFTER_MIDDLE, Stage.AFTER_FINAL)
    val latest by rememberUpdatedState(state)
    val latestOnState by rememberUpdatedState(onState)
    val latestOnNotice by rememberUpdatedState(onNotice)
    fun quickResolve() {
        if (resolving) return
        val expected = latest
        paused = true
        resolving = true
        scope.launch {
            try {
                val next = withContext(Dispatchers.Default) { TacticalBattleEngine.autoResolve(expected) }
                if (latest == expected) latestOnState(next)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                latestOnNotice(error.message ?: "Die Schlacht konnte nicht abgeschlossen werden.")
            } finally {
                resolving = false
            }
        }
    }
    LaunchedEffect(running, speed, battle.tacticalStage, battle.status) {
        while (running) {
            delay((if (latest.settings.animations) 1_800L else 250L) / speed)
            val current = latest
            val result = withContext(Dispatchers.Default) { TacticalBattleEngine.step(current) }
            if (latest != current || resolving) break
            if (result.state === current) break
            if (current.settings.haptics && result.state.battleSession?.tacticalStage != current.battleSession?.tacticalStage)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            latestOnState(result.state)
        }
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().testTag("battle_screen")) {
        BattleHeader(state, battle, stage, onExpert = { expert = true })
        // While a decision is open the panel gets more room; during the simulation the field dominates.
        val deciding = battle.isActive && stage in listOf(Stage.PLAN, Stage.MIDDLE, Stage.FINAL)
        Box(Modifier.weight(if (deciding) 0.9f else 2f).fillMaxWidth().padding(horizontal = 10.dp).testTag("tactical_field")) {
            SessionBattleField(battle, state.settings.animations, speed.toFloat(), battle.wallWeapons, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), compactLegend = true)
        }
        // The bottom panel may use up to its share of the height and scrolls inside it, so the field always stays visible.
        Box(Modifier.weight(if (deciding) 1.6f else 1f, fill = false).verticalScroll(rememberScrollState())) {
        when {
            resolving -> Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Gold)
                Text("Die Schlacht wird entschieden …", color = Mist)
            }
            !battle.isActive -> BattleResultPanel(state, battle) { onState(state.copy(battleSession = null)) }
            stage == Stage.PLAN -> PlanPicker(state, battle, onPlan = { plan ->
                if (state.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                apply(TacticalBattleEngine.choosePlan(state, plan))
            }, onQuick = ::quickResolve)
            stage == Stage.MIDDLE || stage == Stage.FINAL -> DecisionOverlay(state, battle, stage, Modifier.padding(horizontal = 10.dp)) { decision ->
                if (state.settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                apply(TacticalBattleEngine.decide(state, decision))
            }
            else -> SimulationControls(battle, paused, speed, onPause = { paused = !paused }, onSpeed = { speed = if (speed >= 4) 1 else speed * 2 },
                onQuick = ::quickResolve)
        }
        }
    }
}

@Composable
private fun BattleHeader(state: GameState, battle: BattleSession, stage: Stage, onExpert: () -> Unit) {
    val wall = TacticalBattleEngine.isWall(battle)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val plan = TacticalBattleEngine.plan(battle)?.let { " · ${it.label}" } ?: ""
                Text((if (wall) "MAUERSCHLACHT" else "FELDSCHLACHT") + " · ${battle.minute}′$plan", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
                Text(battle.tacticalTitle ?: battle.enemyArmyName ?: battle.location ?: battle.enemy.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Cinzel, maxLines = 1)
            }
            TextButton(onExpert) { Text("Details", color = Muted, fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val step = when (stage) { Stage.PLAN -> 0; Stage.RUNNING, Stage.MIDDLE -> 1; else -> 2 }
            listOf("Plan", if (wall) "Mauer" else "Kontakt", "Ende").forEachIndexed { i, label ->
                val done = i < step || (i == step && stage in listOf(Stage.AFTER_MIDDLE, Stage.AFTER_FINAL)) || !battle.isActive
                val active = i == step && battle.isActive
                Text((if (done && !active) "✓ " else "${i + 1} ") + label, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (active) Ink else if (done) Emerald else Muted,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (active) Gold else StoneRaised).padding(vertical = 5.dp), maxLines = 1)
            }
        }
        StrengthBar("Dein Heer", battle.fightingRemaining, battle.ownStart, Emerald, "Moral ${battle.morale} %", state.settings.animations)
        val enemyMorale = if (battle.enemyRemaining == 0) 0 else (battle.fronts.sumOf { it.morale.toLong() * it.enemySoldiers } / battle.enemyRemaining.coerceAtLeast(1)).toInt()
        StrengthBar(battle.enemyFactionName ?: battle.enemy.label, battle.enemyRemaining, battle.enemyStart, Crimson, "Moral $enemyMorale %", state.settings.animations)
    }
}

@Composable
private fun StrengthBar(label: String, value: Int, max: Int, color: Color, extra: String, animations: Boolean) {
    val target = (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
    val fraction = if (animations) animateFloatAsState(target, tween(600), label = label).value else target
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
    AnimatedVisibility(true, modifier = modifier,
        enter = if (state.settings.animations) fadeIn() + scaleIn(initialScale = .92f) else EnterTransition.None,
        exit = if (state.settings.animations) fadeOut() else ExitTransition.None) {
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
