package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.model.*

internal val Ink = Color(0xFF0A0E12)
internal val Panel = Color(0xFF121920)
internal val Panel2 = Color(0xFF19232C)
internal val Gold = Color(0xFFD6B66B)
internal val PaleGold = Color(0xFFFFE2A1)
internal val Mist = Color(0xFFD8E0E5)
internal val Danger = Color(0xFFB74C4C)
internal val Success = Color(0xFF6AAE7A)
internal val Blue = Color(0xFF5189B7)

@Composable
internal fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = PaleGold, fontSize = 13.sp, letterSpacing = 2.sp)
        Text(subtitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
internal fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text, fontWeight = FontWeight.Black) }
}

@Composable
internal fun SmallAction(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
internal fun EmptyCard(text: String) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Text(text, color = Mist, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
    }
}

@Composable
internal fun CompactCard(title: String, subtitle: String) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
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
                    Surface(modifier = Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
internal fun ResourceStrip(r: Resources) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(
            Triple("◈", "Gold", r.gold),
            Triple("✦", "Nahrung", r.food),
            Triple("♣", "Holz", r.wood),
            Triple("◆", "Stein", r.stone),
            Triple("⚒", "Eisen", r.iron)
        ).forEach { (icon, label, value) ->
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp), color = Panel2) {
                Column(
                    Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(icon, color = Gold, fontSize = 12.sp)
                    Text(value.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(label, color = Color(0xFF9FAAB1), fontSize = 7.sp, maxLines = 1)
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
    onPortrait: () -> Unit
) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(250.dp).clickable(onClick = onPortrait),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painterResource(fallback),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(250.dp).clickable(onClick = onPortrait),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(14.dp)) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Gold, fontSize = 12.sp)
                Text(stats.joinToString(" · "), color = Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Portrait antippen, um ein eigenes Bild zu wählen.", color = Color(0xFF87949C), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
internal fun UnitArt(culture: Culture, modifier: Modifier = Modifier) {
    val res = when (culture) {
        Culture.GOLD_ELF -> R.drawable.portrait_gold_elf
        Culture.WOOD_ELF -> R.drawable.portrait_wood_elf
        Culture.WALL -> R.drawable.portrait_wall_guard
        Culture.HUMAN -> R.drawable.portrait_knight
    }
    Image(painterResource(res), null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
}


@Composable
internal fun CategoryArt(culture: Culture, modifier: Modifier = Modifier) {
    val asset = when (culture) {
        Culture.HUMAN -> "file:///android_asset/category_human.jpg"
        Culture.WOOD_ELF -> "file:///android_asset/category_wood_elf.png"
        Culture.GOLD_ELF -> "file:///android_asset/category_gold_elf.jpg"
        Culture.WALL -> "file:///android_asset/category_wall.jpg"
    }
    val fallback = when (culture) {
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
            error = painterResource(fallback)
        )
        val tint = when (culture) {
            Culture.HUMAN -> Color.Transparent
            Culture.WOOD_ELF -> Color(0x223E7B50)
            Culture.GOLD_ELF -> Color(0x44D6A947)
            Culture.WALL -> Color(0x33386F9C)
        }
        if (tint != Color.Transparent) {
            Box(Modifier.fillMaxSize().background(tint))
        }
    }
}
