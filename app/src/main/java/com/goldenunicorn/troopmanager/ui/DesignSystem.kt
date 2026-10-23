package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.AdvisorEngine

/**
 * v1.2 design system: one type scale, one spacing scale and a few semantic building blocks,
 * so screens stop inventing their own 8–10 sp labels and card styles.
 */
internal object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

internal val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
)

internal fun severityColor(severity: AdvisorEngine.Severity): Color = when (severity) {
    AdvisorEngine.Severity.CRITICAL -> Crimson
    AdvisorEngine.Severity.WARNING -> Color(0xFFE3A44C)
    AdvisorEngine.Severity.INFO -> ModernBlue
    AdvisorEngine.Severity.OPPORTUNITY -> Emerald
}

/** Section heading with an optional one-line explanation. */
@Composable
internal fun Heading(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = Space.s), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** A single actionable piece of advice: colored edge, headline, explanation and a jump target. */
@Composable
internal fun AdviceCard(advice: AdvisorEngine.Advice, onOpen: () -> Unit) {
    val color = severityColor(advice.severity)
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(StoneRaised)
            .border(1.dp, color.copy(alpha = .30f), shape)
            .clickable(onClick = onOpen)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(horizontal = Space.m, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(advice.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(advice.text, color = Mist, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text("›", color = color, fontSize = 24.sp, modifier = Modifier.padding(end = Space.m))
    }
}

/** Big number with label, for the top of hub screens. */
@Composable
internal fun BigStat(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Gold) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(StoneRaised).padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
