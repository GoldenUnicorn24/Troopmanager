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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.EnemyType
import com.goldenunicorn.troopmanager.model.GameState
import com.goldenunicorn.troopmanager.model.Tactic
import kotlinx.coroutines.delay

@Composable
internal fun LiveBattleScreen(
    stateBeforeBattle: GameState,
    enemy: EnemyType,
    tactic: Tactic,
    result: GameEngine.LiveBattleResult,
    onApplyResult: (GameState) -> Unit,
    onCancel: () -> Unit
) {
    var index by remember(result) { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1) }

    val lastIndex = (result.frames.size - 1).coerceAtLeast(0)
    val frame = result.frames.getOrNull(index)
    val finished = result.frames.isEmpty() || index >= lastIndex

    LaunchedEffect(index, paused, speed, result) {
        if (!paused && index < lastIndex) {
            delay((1150L / speed.coerceAtLeast(1)).coerceAtLeast(220L))
            index++
        }
    }

    val ownNow = frame?.ownRemaining ?: result.ownStart
    val enemyNow = frame?.enemyRemaining ?: result.enemyStart
    val ownPct = if (result.ownStart > 0) ownNow.toFloat() / result.ownStart else 0f
    val enemyPct = if (result.enemyStart > 0) enemyNow.toFloat() / result.enemyStart else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageTitle(
                "LIVE-SCHLACHT",
                enemy.label + " · " + tactic.label
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BattleSideCard(
                    title = "DEIN HEER",
                    now = ownNow,
                    start = result.ownStart,
                    losses = result.ownStart - ownNow,
                    color = Gold,
                    modifier = Modifier.weight(1f)
                )
                BattleSideCard(
                    title = "GEGNER",
                    now = enemyNow,
                    start = result.enemyStart,
                    losses = result.enemyStart - enemyNow,
                    color = Danger,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Minute " + (frame?.minute ?: 0) + " / 90", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(if (finished) result.headline else "Schlacht läuft", color = if (finished && result.victory) Success else Mist)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Eigene Kampfstärke", color = Mist, fontSize = 10.sp)
                    LinearProgressIndicator(
                        progress = { ownPct.coerceIn(0f,1f) },
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = Gold,
                        trackColor = Color(0xFF2A3137)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Feindliche Kampfstärke", color = Mist, fontSize = 10.sp)
                    LinearProgressIndicator(
                        progress = { enemyPct.coerceIn(0f,1f) },
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = Danger,
                        trackColor = Color(0xFF2A3137)
                    )
                }
            }
        }

        item {
            LiveBattleField(
                front = frame?.front ?: 50,
                ownRemaining = ownNow,
                ownStart = result.ownStart,
                enemyRemaining = enemyNow,
                enemyStart = result.enemyStart
            )
        }

        item {
            Surface(color = Panel, shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Aktueller Verlauf", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        frame?.event ?: "Die Schlacht beginnt.",
                        color = PaleGold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (frame != null) {
                        Text(
                            "Diese Phase: -" + frame.ownLossesThisFrame + " eigene · -" + frame.enemyLossesThisFrame + " Gegner",
                            color = Mist,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Deine Einheiten vor der Schlacht")
        }
        items(stateBeforeBattle.regiments.groupBy { it.type }.entries.sortedByDescending { e -> e.value.sumOf { it.soldiers } }.size) { pos ->
            val entry = stateBeforeBattle.regiments.groupBy { it.type }.entries.sortedByDescending { e -> e.value.sumOf { it.soldiers } }[pos]
            val amount = entry.value.sumOf { it.soldiers }
            CompactCard(entry.key.label, amount.toString() + " Soldaten")
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { paused = !paused },
                    enabled = !finished,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (paused) "Fortsetzen" else "Pause")
                }
                listOf(1,2,4).forEach { s ->
                    FilterChip(
                        selected = speed == s,
                        onClick = { speed = s },
                        label = { Text(s.toString() + "x") }
                    )
                }
            }
        }

        if (finished) {
            item {
                Surface(
                    color = if (result.victory) Color(0xFF173024) else Color(0xFF331E1E),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (result.victory) Success else Danger)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (result.victory) "SIEG" else "NIEDERLAGE",
                            color = if (result.victory) Success else Danger,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Eigene Verluste: " + (result.ownStart - ownNow) +
                                    " · Gegnerische Verluste: " + (result.enemyStart - enemyNow),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
            item {
                GoldButton(
                    "Schlacht abschließen",
                    { onApplyResult(result.finalState) },
                    Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Live-Ansicht verlassen", color = Mist)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun BattleSideCard(
    title: String,
    now: Int,
    start: Int,
    losses: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(now.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Start " + start + " · Verluste " + losses, color = Mist, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LiveBattleField(
    front: Int,
    ownRemaining: Int,
    ownStart: Int,
    enemyRemaining: Int,
    enemyStart: Int
) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Frontverlauf", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                when {
                    front >= 65 -> "Deine Armee drückt den Gegner zurück."
                    front <= 35 -> "Der Gegner drängt tief in deine Linien."
                    else -> "Die Front ist hart umkämpft."
                },
                color = Mist,
                fontSize = 11.sp
            )
            Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 10.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFF26332A))
                val frontX = w * (front / 100f)

                drawRect(
                    color = Color(0x335189B7),
                    topLeft = Offset(0f, 0f),
                    size = Size(frontX, h)
                )
                drawRect(
                    color = Color(0x33B74C4C),
                    topLeft = Offset(frontX, 0f),
                    size = Size(w - frontX, h)
                )

                drawLine(
                    color = PaleGold,
                    start = Offset(frontX, 8f),
                    end = Offset(frontX, h - 8f),
                    strokeWidth = 6f
                )

                val ownDots = ((ownRemaining.toFloat() / ownStart.coerceAtLeast(1)) * 35).toInt().coerceAtLeast(1)
                repeat(ownDots) { i ->
                    val col = i % 7
                    val row = i / 7
                    drawCircle(
                        color = if (i % 4 == 0) Blue else Gold,
                        radius = 6f,
                        center = Offset(24f + col * 25f, 35f + row * 28f)
                    )
                }

                val enemyDots = ((enemyRemaining.toFloat() / enemyStart.coerceAtLeast(1)) * 35).toInt().coerceAtLeast(1)
                repeat(enemyDots) { i ->
                    val col = i % 7
                    val row = i / 7
                    drawCircle(
                        color = Danger,
                        radius = 6f,
                        center = Offset(w - 24f - col * 25f, 35f + row * 28f)
                    )
                }
            }
        }
    }
}
