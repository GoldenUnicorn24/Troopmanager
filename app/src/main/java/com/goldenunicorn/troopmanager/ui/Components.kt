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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.model.*

internal val Ink = Color(0xFF080B0E)
internal val Panel = Color(0xFF10161B)
internal val Panel2 = Color(0xFF172027)
internal val Gold = Color(0xFFD0AE62)
internal val PaleGold = Color(0xFFF4D894)
internal val Mist = Color(0xFFCCD4D8)
internal val Danger = Color(0xFFB65A5D)
internal val Success = Color(0xFF72A985)
internal val Blue = Color(0xFF6B91AD)

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title.uppercase(), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
        Text(subtitle, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Box(Modifier.padding(top = 4.dp).width(42.dp).height(2.dp).background(Gold, RoundedCornerShape(4.dp)))
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
    Button(
        onClick = onClick,
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
                            Text(label, color = Color(0xFFA3ADB4), fontSize = 10.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ResourceStrip(r: Resources, production: Resources? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
                Triple("◈", "Gold", r.gold),
                Triple("✦", "Nahrung", r.food),
                Triple("♣", "Holz", r.wood),
                Triple("◆", "Stein", r.stone),
                Triple("⚒", "Eisen", r.iron),
            )
            .forEach { (icon, label, value) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Panel2,
                ) {
                    Column(
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("$icon $label", color = Color(0xFF9FAAB1), fontSize = 9.sp, maxLines = 1)
                        Text(
                            value.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                        if (production != null) {
                            val gain =
                                when (label) {
                                    "Gold" -> production.gold
                                    "Nahrung" -> production.food
                                    "Holz" -> production.wood
                                    "Stein" -> production.stone
                                    else -> production.iron
                                }
                            Text(
                                "${if (gain >= 0) "+" else ""}$gain",
                                color = if (gain < 0) Danger else Gold,
                                fontSize = 9.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
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
                    fontSize = 10.sp,
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ResourceKind.entries.forEach { kind ->
            val detail = EconomyEngine.breakdown(state, kind)
            Surface(
                Modifier.weight(1f).clickable { selected = kind },
                color = Panel2,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        resourceIcon(kind),
                        contentDescription = kind.label,
                        tint = Gold,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        kind.value(state.resources).toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${if(detail.net>=0) "+" else ""}${detail.net}/Tag",
                        color = if (detail.net < 0) Danger else Success,
                        fontSize = 10.sp,
                    )
                    Text(
                        if (detail.net < 0) "Defizit" else kind.label,
                        color = if (detail.net < 0) Danger else Mist,
                        fontSize = 10.sp,
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
                    "Grundproduktion: ${d.base}\nGebäude: +${d.building}\nGebiete: +${d.territory}\nEreignisse/Handel: +${d.eventBonus}\nArbeiterfaktor: ${(d.workerFactor*100).toInt()} %\nSteuereffekt: ${d.taxBonus}\nBrutto: ${d.gross}\nUnterhalt: −${d.upkeep}\nNetto pro Tag: ${d.net}\nLager: ${kind.value(state.resources)} / ${d.capacity}\nNicht eingelagerter Überschuss: ${d.overflow}\n\nArbeiter, Priorität, Zufriedenheit und Wohlstand beeinflussen die tatsächliche Produktion."
                )
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Schließen") } },
        )
    }
}
