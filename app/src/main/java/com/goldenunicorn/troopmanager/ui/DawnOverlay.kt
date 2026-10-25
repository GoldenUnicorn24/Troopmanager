package com.goldenunicorn.troopmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.delay

/** A short dawn interstitial after "Tag beenden": the new day, the season and what happened overnight. */
@Composable
internal fun DawnOverlay(state: GameState, animations: Boolean, onDone: () -> Unit) {
    val fade = remember(state.day) { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(state.day) {
        if (animations) fade.animateTo(1f, tween(380))
        delay(1_900)
        if (animations) fade.animateTo(0f, tween(380))
        onDone()
    }
    val season = Season.forDay(state.day)
    val net = EconomyEngine.production(state).net
    val news = state.dailyReport.entries.sortedByDescending { it.important }.take(3)
    Box(
        Modifier.fillMaxSize().alpha(fade.value)
            .background(Brush.verticalGradient(listOf(Color(0xF0101722), Color(0xF7070A0E))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize()) {
            ArtImage("menu_cover.webp", Modifier.fillMaxWidth().fillMaxHeight(.55f).alpha(.35f))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color(0xF7070A0E))))
        }
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(season.label.uppercase() + " · DIE SONNE GEHT AUF", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Tag ${state.day}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = Cinzel)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("${signed(net.gold)} Gold", color = if (net.gold < 0) Crimson else PaleGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${signed(net.food)} Nahrung", color = if (net.food < 0) Crimson else Emerald, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            news.forEach { Text(it.title, color = Mist, fontSize = 15.sp, textAlign = TextAlign.Center, fontFamily = Cormorant, lineHeight = 20.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Tippen zum Fortfahren", color = Muted, fontSize = 11.sp)
        }
    }
}
