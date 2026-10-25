package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.goldenunicorn.troopmanager.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.model.*

internal val Ink = Color(0xFF070A0E)
internal val Panel = Color(0xFF0F151C)
internal val Panel2 = Color(0xFF151E27)
internal val Gold = Color(0xFFE0BA63)
internal val PaleGold = Color(0xFFF5D98B)
internal val Mist = Color(0xFFC7D0D7)
internal val Danger = Color(0xFFE2676B)
internal val Success = Color(0xFF69C08B)
internal val Blue = Color(0xFF72B8E8)

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            color = Gold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp,
        )
        Text(
            subtitle,
            color = Color.White,
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Cinzel,
            maxLines = 3,
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Gold,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
internal fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Button(
        onClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); onClick() },
        modifier = modifier.heightIn(min = 54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
    ) {
        Text(text.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .7.sp)
    }
}

@Composable
internal fun SmallAction(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PaleGold),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EmptyCard(text: String) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
    ) {
        Text(text, color = Mist, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
    }
}

@Composable
internal fun CompactCard(title: String, subtitle: String) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Gold, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun StatGrid(values: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, value) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Panel,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                value,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(label, color = Color(0xFFA3ADB4), fontSize = 11.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Compact number for tight spaces: 6.697 · 68,3k · 1,2 Mio. */
internal fun compact(value: Int): String {
    val v = kotlin.math.abs(value.toLong())
    val sign = if (value < 0) "−" else ""
    return sign + when {
        v >= 1_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f Mio", v / 1_000_000.0)
        v >= 100_000 -> "${v / 1_000}k"
        v >= 10_000 -> String.format(java.util.Locale.GERMANY, "%.1fk", v / 1_000.0)
        else -> String.format(java.util.Locale.GERMANY, "%,d", v)
    }
}

/** Small painted glyphs for the five raw resources. */
@Composable
internal fun ResourceIcon(kind: ResourceKind, size: androidx.compose.ui.unit.Dp = 16.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            ResourceKind.GOLD -> {
                drawCircle(Color(0xFFB8862F), w * .48f)
                drawCircle(Color(0xFFF2CF6B), w * .40f)
                drawCircle(Color(0xFFB8862F), w * .22f, style = androidx.compose.ui.graphics.drawscope.Stroke(w * .08f))
            }
            ResourceKind.FOOD -> {
                drawLine(Color(0xFF9C7A35), androidx.compose.ui.geometry.Offset(w * .5f, h * .95f), androidx.compose.ui.geometry.Offset(w * .5f, h * .2f), w * .1f)
                for (i in 0 until 3) {
                    val y = h * (.25f + i * .2f)
                    drawOval(Color(0xFFE6C15A), androidx.compose.ui.geometry.Offset(w * .18f, y), androidx.compose.ui.geometry.Size(w * .3f, h * .16f))
                    drawOval(Color(0xFFE6C15A), androidx.compose.ui.geometry.Offset(w * .52f, y), androidx.compose.ui.geometry.Size(w * .3f, h * .16f))
                }
            }
            ResourceKind.WOOD -> {
                drawRoundRect(Color(0xFF8A5A33), androidx.compose.ui.geometry.Offset(0f, h * .3f), androidx.compose.ui.geometry.Size(w * .8f, h * .42f), androidx.compose.ui.geometry.CornerRadius(h * .2f))
                drawCircle(Color(0xFFD9A86C), h * .21f, androidx.compose.ui.geometry.Offset(w * .78f, h * .51f))
                drawCircle(Color(0xFF8A5A33), h * .09f, androidx.compose.ui.geometry.Offset(w * .78f, h * .51f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .05f))
            }
            ResourceKind.STONE -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .1f, h * .75f); lineTo(w * .25f, h * .3f); lineTo(w * .6f, h * .15f); lineTo(w * .9f, h * .45f); lineTo(w * .8f, h * .85f); close()
                }
                drawPath(path, Color(0xFF9AA3AA))
                drawLine(Color(0xFF6E767C), androidx.compose.ui.geometry.Offset(w * .25f, h * .3f), androidx.compose.ui.geometry.Offset(w * .55f, h * .6f), w * .07f)
            }
            ResourceKind.IRON -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .05f, h * .78f); lineTo(w * .25f, h * .35f); lineTo(w * .75f, h * .35f); lineTo(w * .95f, h * .78f); close()
                }
                drawPath(path, Color(0xFF8C98A6))
                drawLine(Color(0xFFD5DEE6), androidx.compose.ui.geometry.Offset(w * .28f, h * .42f), androidx.compose.ui.geometry.Offset(w * .72f, h * .42f), w * .07f)
            }
        }
    }
}

/** One compact row with all five resources; the chevron shows the daily change. */
@Composable
internal fun ResourceStrip(r: Resources, production: Resources? = null) {
    var showDelta by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).testTag("resource_strip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ResourceKind.entries.forEachIndexed { index, kind ->
            val value = kind.value(r)
            val gain = production?.let { kind.value(it) }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111820))
                    .heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 5.dp).testTag("resource_$index")
                    .semantics(mergeDescendants = true) { contentDescription = "${kind.label}: ${String.format(java.util.Locale.GERMANY, "%,d", value)}" + (gain?.let { ", ${if (it >= 0) "plus" else "minus"} ${kotlin.math.abs(it)} pro Tag" } ?: "") },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ResourceIcon(kind, 14.dp)
                    Text(compact(value), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                if (showDelta && gain != null) Text((if (gain >= 0) "+" else "") + compact(gain), color = if (gain < 0) Danger else Success, fontSize = 11.sp, maxLines = 1)
            }
        }
        IconButton(onClick = { showDelta = !showDelta }, modifier = Modifier.size(36.dp).testTag("resource_next")) {
            Text(if (showDelta) "–" else "±", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Tägliche Veränderung ein- oder ausblenden" })
        }
    }
}

@Composable
internal fun CharacterPanel(
    title: String,
    subtitle: String,
    uri: String?,
    fallback: Int,
    stats: List<String>,
    onPortrait: () -> Unit,
) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxWidth().height(250.dp).clickable(onClick = onPortrait),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painterResource(fallback),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxWidth().height(250.dp).clickable(onClick = onPortrait),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(14.dp)) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Gold, fontSize = 12.sp)
                Text(
                    stats.joinToString(" · "),
                    color = Mist,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Portrait antippen, um ein eigenes Bild zu wählen.",
                    color = Color(0xFF87949C),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun UnitArt(culture: Culture, modifier: Modifier = Modifier) {
    val res =
        when (culture) {
            Culture.GOLD_ELF -> R.drawable.portrait_gold_elf
            Culture.WOOD_ELF -> R.drawable.portrait_wood_elf
            Culture.WALL -> R.drawable.portrait_wall_guard
            Culture.HUMAN -> R.drawable.portrait_knight
        }
    Image(
        painterResource(res),
        null,
        modifier.clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
internal fun CategoryArt(culture: Culture, modifier: Modifier = Modifier) {
    val asset =
        when (culture) {
            Culture.HUMAN -> "file:///android_asset/category_human.webp"
            Culture.WOOD_ELF -> "file:///android_asset/category_wood_elf.webp"
            Culture.GOLD_ELF -> "file:///android_asset/category_gold_elf.webp"
            Culture.WALL -> "file:///android_asset/category_wall.webp"
        }
    val fallback =
        when (culture) {
            Culture.HUMAN -> R.drawable.portrait_knight
            Culture.WOOD_ELF -> R.drawable.portrait_wood_elf
            Culture.GOLD_ELF -> R.drawable.portrait_gold_elf
            Culture.WALL -> R.drawable.portrait_wall_guard
        }
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        AsyncImage(
            model = asset,
            contentDescription = culture.label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(fallback),
            error = painterResource(fallback),
        )
    }
}

@Composable
internal fun InfoTip(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Info, contentDescription = "Information: $title", tint = Gold)
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Verstanden") } },
        )
}

internal fun resourceIcon(kind: ResourceKind): ImageVector =
    when (kind) {
        ResourceKind.GOLD -> Icons.Outlined.Paid
        ResourceKind.FOOD -> Icons.Outlined.Restaurant
        ResourceKind.WOOD -> Icons.Outlined.Forest
        ResourceKind.STONE -> Icons.Outlined.Landscape
        ResourceKind.IRON -> Icons.Outlined.Construction
    }

@Composable
internal fun EconomyStrip(state: GameState) {
    var selected by remember { mutableStateOf<ResourceKind?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResourceKind.entries.forEach { kind ->
            val detail = EconomyEngine.breakdown(state, kind)
            val accent = if (detail.net < 0) Danger else Success
            Surface(
                Modifier.widthIn(min = 104.dp).clickable { selected = kind },
                color = Color(0xFF121A22),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            resourceIcon(kind),
                            contentDescription = kind.label,
                            tint = Gold,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(kind.label.uppercase(), color = Color(0xFF8F9BA4), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Text(
                        kind.value(state.resources).toString(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${if (detail.net >= 0) "+" else ""}${detail.net}/Tag",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    selected?.let { kind ->
        val d = EconomyEngine.breakdown(state, kind)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(kind.label) },
            text = {
                Text(
                    "Grundproduktion: ${d.base}\nGebäude: +${d.building}\nGebiete: +${d.territory}\nEreignisse/Handel: +${d.eventBonus}\nArbeiterfaktor: ${(d.workerFactor * 100).toInt()} %\nSteuereffekt: ${d.taxBonus}\nBrutto: ${d.gross}\nUnterhalt: −${d.upkeep}\nNetto pro Tag: ${d.net}\nLager: ${kind.value(state.resources)} / ${d.capacity}\nNicht eingelagerter Überschuss: ${d.overflow}\n\nArbeiter, Priorität, Zufriedenheit und Wohlstand beeinflussen die tatsächliche Produktion."
                )
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Schließen") } },
        )
    }
}

