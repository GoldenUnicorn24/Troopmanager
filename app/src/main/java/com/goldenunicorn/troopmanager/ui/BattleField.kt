package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.EnemyType
import com.goldenunicorn.troopmanager.model.GameState
import com.goldenunicorn.troopmanager.model.Tactic

@Composable
internal fun TacticalBattlePreview(state: GameState, enemy: EnemyType, tactic: Tactic) {
    val transition = rememberInfiniteTransition(label = "battlefield")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "frontMovement"
    )

    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("2.5D Gefechtslage", color = PaleGold, fontWeight = FontWeight.Bold)
                Text(tactic.label, color = Mist, fontSize = 11.sp)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18221D))
            ) {
                val w = size.width
                val h = size.height

                drawRect(Color(0xFF253126))
                drawRect(
                    color = Color(0xFF505253),
                    topLeft = Offset(0f, h * 0.79f),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.07f)
                )
                repeat(9) { tower ->
                    val x = tower * (w / 8f)
                    drawRect(
                        color = Color(0xFF686A68),
                        topLeft = Offset(x - 7f, h * 0.74f),
                        size = androidx.compose.ui.geometry.Size(14f, h * 0.13f)
                    )
                }

                val ownColor = Color(0xFFD6B66B)
                val secondOwn = Color(0xFF5C91B8)
                val enemyColor = when (enemy) {
                    EnemyType.ORC -> Color(0xFF71834D)
                    EnemyType.URUK -> Color(0xFF8C463C)
                    EnemyType.TAO_TEI -> Color(0xFF6EA36A)
                }

                val ownRows = if (state.armySize > 1200) 4 else if (state.armySize > 300) 3 else 2
                repeat(ownRows) { row ->
                    repeat(8) { col ->
                        val x = w * 0.15f + col * w * 0.095f
                        val y = h * (0.65f - row * 0.08f)
                        drawCircle(if ((row + col) % 3 == 0) secondOwn else ownColor, 7f, Offset(x, y))
                    }
                }

                val move = phase * h * 0.05f
                repeat(5) { row ->
                    repeat(9) { col ->
                        val x = w * 0.1f + col * w * 0.1f
                        val baseY = h * (0.14f + row * 0.075f)
                        drawCircle(enemyColor, if (enemy == EnemyType.TAO_TEI) 8f else 7f, Offset(x, baseY + move))
                    }
                }

                if (tactic == Tactic.RANGED || tactic == Tactic.HOLD || tactic == Tactic.FORTIFY) {
                    repeat(7) { i ->
                        val x = w * (0.2f + i * 0.1f)
                        drawLine(
                            color = Color(0xFFE6D9A4),
                            start = Offset(x, h * 0.58f),
                            end = Offset(x - 20f, h * (0.30f + phase * 0.04f)),
                            strokeWidth = 2f
                        )
                    }
                }

                if (tactic == Tactic.FLANK) {
                    drawLine(ownColor, Offset(w * 0.12f, h * 0.62f), Offset(w * 0.04f, h * 0.34f), 5f)
                    drawLine(ownColor, Offset(w * 0.88f, h * 0.62f), Offset(w * 0.96f, h * 0.34f), 5f)
                }
            }
            Text(
                state.armySize.toString() + " eigene Soldaten · " + enemy.label,
                color = Mist,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
