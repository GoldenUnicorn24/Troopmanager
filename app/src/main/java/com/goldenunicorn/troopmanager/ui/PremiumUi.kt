package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.PresenceEngine
import com.goldenunicorn.troopmanager.model.*

internal val Stone = Color(0xFF090D12)
internal val StoneRaised = Color(0xFF101720)
internal val Steel = Color(0xFF1D2A35)
internal val GoldSoft = Color(0xFFE1BE68)
internal val Ivory = Color(0xFFF8F2E5)
internal val Muted = Color(0xFF98A5AF)
internal val Crimson = Color(0xFFE2676B)
internal val Emerald = Color(0xFF69C08B)
internal val ModernBlue = Color(0xFF72B8E8)
internal val ModernSurface = Color(0xF2131921)
internal val ModernSurface2 = Color(0xEE1A2430)
internal val ModernLine = Color(0x22FFFFFF)

private val ModernPanelBrush =
    Brush.linearGradient(
        listOf(
            Color(0xFF171F29),
            Color(0xFF11171E),
        )
    )

@Composable
internal fun PremiumPanel(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (emphasized)
                    Brush.linearGradient(listOf(Color(0xFF202B35), Color(0xFF121922)))
                else ModernPanelBrush
            )
            .border(
                1.dp,
                if (emphasized) Gold.copy(alpha = .28f) else Color.White.copy(alpha = .075f),
                shape,
            )
            .then(click)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content,
        )
    }
}

@Composable
internal fun ModernPill(
    text: String,
    accent: Color = Gold,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (filled) accent.copy(alpha = .18f) else Color.Black.copy(alpha = .26f),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
    ) {
        Text(
            text.uppercase(),
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .8.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun ModernSectionHeader(
    eyebrow: String,
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                eyebrow.uppercase(),
                color = Gold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(action, color = ModernBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ModernTabStrip(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(selected, labels) {
        if (labels.isNotEmpty()) list.animateScrollToItem(selected.coerceIn(0, labels.lastIndex))
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
      val itemWidth = maxWidth - 8.dp
      LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("adaptive_tabs"),
        contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        itemsIndexed(labels) { index, label ->
            val active = index == selected
            Surface(
                modifier = Modifier.widthIn(min = 48.dp, max = itemWidth).heightIn(min = 48.dp)
                    .testTag("tab_$index").semantics { this.selected = active }.clickable { onSelect(index) },
                color = if (active) Color.White else Color(0xFF111820),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(
                    1.dp,
                    if (active) Color.White else Color.White.copy(alpha = .07f),
                ),
            ) {
                Text(
                    label,
                    color = if (active) Ink else Muted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    maxLines = 2,
                )
            }
        }
      }
    }
}

@Composable
internal fun ModernChoice(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        color = if (selected) ModernSurface2 else StoneRaised,
        contentColor = if (selected) Color.White else Muted,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (selected) ModernBlue else ModernLine)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { label() }
    }
}

@Composable
internal fun ModernActionTile(
    title: String,
    subtitle: String,
    accent: Color = ModernBlue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF17212B), Color(0xFF111820))))
            .border(1.dp, accent.copy(alpha = .16f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.width(24.dp).height(3.dp).background(accent, RoundedCornerShape(10.dp)))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(subtitle, color = Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        production.food < 0 -> "Versorgung unter Druck"
        state.city.satisfaction < 45 -> "Unruhe im Reich"
        state.campaign.momentum >= 70 -> "Du hältst die Initiative"
        else -> "Das Reich wartet auf deinen Befehl"
    }
    val detail = when {
        decision != null -> decision.text
        nearest != null -> "${nearest.estimatedStrengthLabel} · Ankunft in ${nearest.daysToArrival} Tagen"
        production.food < 0 -> "Nahrung ${production.food}/Tag · Reserve ${state.resources.food}"
        state.city.satisfaction < 45 -> "Zufriedenheit ${state.city.satisfaction}% · Sicherheit ${state.city.security}%"
        else -> "Momentum ${state.campaign.momentum}/100 · Druck ${state.campaign.pressure}/100 · Fokus ${state.campaign.focus.label}"
    }
    val urgency = decision != null || nearest?.daysToArrival?.let { it <= 3 } == true || state.campaign.pressure >= 70

    Box(
        Modifier.fillMaxWidth().height(318.dp).clip(RoundedCornerShape(30.dp))
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
                Brush.verticalGradient(
                    listOf(
                        Color(0x33060A0D),
                        Color(0x44060A0D),
                        Color(0xF2090D12),
                    )
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0x33000000), Color.Transparent, Color(0x22000000))
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
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ModernPill("Tag ${state.day}", Gold, filled = true)
                        ModernPill(state.realm.settlementTier.label, ModernBlue)
                    }
                    Text(
                        state.realm.settlementName,
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${state.title} · ${state.player.name}", color = Ivory.copy(alpha = .88f), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModernPill(
                        "Momentum ${state.campaign.momentum}",
                        if (state.campaign.momentum >= 65) Emerald else Gold,
                        filled = true,
                    )
                    ModernPill(
                        "Druck ${state.campaign.pressure}",
                        if (state.campaign.pressure >= 65) Crimson else Muted,
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(23.dp))
                    .background(Color(0xCC10161D))
                    .border(1.dp, Color.White.copy(alpha = .11f), RoundedCornerShape(23.dp))
            ) {
                Column(
                    Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).background(if (urgency) Crimson else Emerald, CircleShape))
                        Text(
                            if (urgency) "JETZT REAGIEREN" else "AKTUELLE LAGE",
                            color = if (urgency) Crimson else Emerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Text(headline, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, color = Mist, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPrimary,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(if (decision != null) "ENTSCHEIDEN" else "ÖFFNEN", fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = onSecondary,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text("REICH", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
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
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(ModernPanelBrush)
            .border(1.dp, Color.White.copy(alpha = .07f), shape)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.width(26.dp).height(3.dp).background(accent, RoundedCornerShape(6.dp)))
            Text(label.uppercase(), color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
            Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1)
            supporting?.let {
                Text(it, color = accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    val accent = if (urgent) Crimson else ModernBlue
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (urgent)
                        listOf(Color(0xFF25171B), Color(0xFF15171D))
                    else
                        listOf(Color(0xFF18222C), Color(0xFF111820))
                )
            )
            .border(1.dp, accent.copy(alpha = .20f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(accent.copy(alpha = .13f), CircleShape)
                .border(1.dp, accent.copy(alpha = .25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (urgent) "!" else "›",
                color = accent,
                fontSize = if (urgent) 15.sp else 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eyebrow.uppercase(), color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = Color.White.copy(alpha = .55f), fontSize = 23.sp)
    }
}

@Composable
internal fun RulerPairHero(state: GameState) {
    val presence = PresenceEngine.presence(state)
    val together = state.companion.met

    Box(
        Modifier.fillMaxWidth().height(if (together) 382.dp else 250.dp).clip(RoundedCornerShape(30.dp))
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
                Brush.verticalGradient(
                    listOf(Color(0x44000000), Color(0x55070B0F), Color(0xF1090D12))
                )
            )
        )
        Column(
            Modifier.fillMaxSize().padding(17.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModernPill(
                        if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "Co-Rulers" else "Herrscherhaus",
                        Gold,
                        filled = true,
                    )
                    Text(
                        if (together) "${state.player.name} & ${state.companion.name}" else state.player.name,
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (together) state.relationshipStage() else state.title, color = Ivory.copy(alpha = .85f), fontSize = 11.sp)
                }
                if (together) {
                    ModernPill(
                        if (state.relationship.conflict >= 60) "angespannt" else "verbunden",
                        if (state.relationship.conflict >= 60) Crimson else Emerald,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
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
                if (together) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PairMetric("Vertrauen", state.companion.trust, Emerald, Modifier.weight(1f))
                        PairMetric("Nähe", state.companion.affection, Gold, Modifier.weight(1f))
                        PairMetric("Respekt", state.companion.respect, ModernBlue, Modifier.weight(1f))
                        PairMetric("Konflikt", state.relationship.conflict, Crimson, Modifier.weight(1f), inverse = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PairMetric(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    inverse: Boolean = false,
) {
    val normalized = value.coerceIn(0, 100)
    val displayAccent = if (inverse && normalized < 35) Emerald else accent
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xB811171E))
            .border(1.dp, displayAccent.copy(alpha = .22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label.uppercase(), color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("$normalized", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        LinearProgressIndicator(
            progress = { if (inverse) 1f - normalized / 100f else normalized / 100f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = displayAccent,
            trackColor = Color.White.copy(alpha = .08f),
        )
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
    Box(
        modifier
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xD711171E))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(19.dp))
    ) {
        Row(
            Modifier.padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = uri ?: asset,
                contentDescription = null,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(15.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(fallback),
                error = painterResource(fallback),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
                Text(subtitle, color = Mist, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
