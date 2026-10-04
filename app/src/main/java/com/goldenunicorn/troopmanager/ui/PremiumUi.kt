package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.PresenceEngine
import com.goldenunicorn.troopmanager.model.*

internal val Stone = Color(0xFF0E1318)
internal val StoneRaised = Color(0xFF151D24)
internal val Steel = Color(0xFF26323B)
internal val GoldSoft = Color(0xFFBFA365)
internal val Ivory = Color(0xFFF3E8CF)
internal val Muted = Color(0xFF98A4AC)
internal val Crimson = Color(0xFF8F3C42)
internal val Emerald = Color(0xFF5F9A75)

@Composable
internal fun PremiumPanel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaceModifier =
        if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick)
        else modifier.fillMaxWidth()
    Surface(
        modifier = surfaceModifier,
        color = if (emphasized) StoneRaised else Panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (emphasized) Gold.copy(alpha = .30f) else Color.White.copy(alpha = .06f)),
        shadowElevation = if (emphasized) 4.dp else 1.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun RealmHero(
    state: GameState,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val production = EconomyEngine.production(state).net
    val nearest = state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }
    val decision = state.campaign.pendingDecision
    val headline = when {
        decision != null -> decision.title
        nearest != null -> "${nearest.name} nähert sich"
        production.food < 0 -> "Die Versorgung braucht Aufmerksamkeit"
        state.city.satisfaction < 45 -> "Unruhe wächst in den Vierteln"
        else -> "Das Reich hält die Initiative"
    }
    val detail = when {
        decision != null -> decision.text
        nearest != null -> "${nearest.estimatedStrengthLabel} · Ankunft in ${nearest.daysToArrival} Tagen"
        production.food < 0 -> "Nahrung ${production.food}/Tag · Reserven ${state.resources.food}"
        state.city.satisfaction < 45 -> "Zufriedenheit ${state.city.satisfaction}% · Sicherheit ${state.city.security}%"
        else -> "Momentum ${state.campaign.momentum}/100 · Druck ${state.campaign.pressure}/100"
    }
    Box(
        Modifier.fillMaxWidth().height(282.dp).clip(RoundedCornerShape(24.dp))
    ) {
        AsyncImage(
            model = "file:///android_asset/city_landscape.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.splash_fortress),
            error = painterResource(R.drawable.splash_fortress),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x22000000),
                        Color(0x77080C0F),
                        Color(0xF20A0E12),
                    )
                )
            )
        )
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("TAG ${state.day}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(
                        state.realm.settlementName,
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${state.title} · ${state.realm.settlementTier.label}", color = Ivory, fontSize = 12.sp)
                }
                Surface(
                    color = Color.Black.copy(alpha = .45f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Gold.copy(alpha = .30f)),
                ) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text("MOMENTUM", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("${state.campaign.momentum}", color = if (state.campaign.momentum >= 65) Success else Gold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(headline, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(detail, color = Mist, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPrimary,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text(if (decision != null) "ENTSCHEIDEN" else "TAGESLAGE", fontWeight = FontWeight.Black, fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .26f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text("REICH ÖFFNEN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
internal fun StatusMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Gold,
    supporting: String? = null,
) {
    Surface(
        modifier = modifier,
        color = StoneRaised,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), color = Muted, fontSize = 9.sp, letterSpacing = .8.sp)
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
            supporting?.let { Text(it, color = accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun SituationCard(
    eyebrow: String,
    title: String,
    detail: String,
    urgent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (urgent) Color(0xFF211719) else StoneRaised,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (urgent) Danger.copy(alpha = .42f) else Color.White.copy(alpha = .07f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(4.dp).height(48.dp).background(if (urgent) Danger else Gold, RoundedCornerShape(8.dp))
            )
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), color = if (urgent) Danger else Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = PaleGold, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
internal fun RulerPairHero(state: GameState) {
    val presence = PresenceEngine.presence(state)
    val together = state.companion.met
    Box(
        Modifier.fillMaxWidth().height(if (together) 305.dp else 230.dp).clip(RoundedCornerShape(24.dp))
    ) {
        AsyncImage(
            model = "file:///android_asset/menu_cover.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.splash_fortress),
            error = painterResource(R.drawable.splash_fortress),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x55000000), Color(0xE80A0E12)))
            )
        )
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "GEMEINSAME HERRSCHAFT" else "HERRSCHERHAUS",
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp,
                )
                Text(
                    if (together) "${state.player.name} & ${state.companion.name}" else state.player.name,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (together) state.relationshipStage() else state.title,
                    color = Ivory,
                    fontSize = 12.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RulerMiniPortrait(
                    state.player.portraitUri,
                    "file:///android_asset/portrait_player.webp",
                    R.drawable.portrait_knight,
                    state.player.name,
                    "${state.title} · ${presence.player.location.label}",
                    Modifier.weight(1f),
                )
                if (together) {
                    RulerMiniPortrait(
                        state.companion.portraitUri,
                        "file:///android_asset/portrait_companion.webp",
                        R.drawable.portrait_companion,
                        state.companion.name,
                        "${state.companion.role} · ${presence.companion.location.label}",
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RulerMiniPortrait(
    uri: String?,
    asset: String,
    fallback: Int,
    name: String,
    subtitle: String,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = .42f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = uri ?: asset,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(fallback),
                error = painterResource(fallback),
            )
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
                Text(subtitle, color = Mist, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
