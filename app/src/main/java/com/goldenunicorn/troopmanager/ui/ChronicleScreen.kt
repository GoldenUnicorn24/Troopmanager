package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.model.*
import com.goldenunicorn.troopmanager.ui.icons.*
import java.util.Locale

internal enum class ChronicleCategory(val label: String) {
    ALL("Alles"), WAR("Krieg"), PEOPLE("Menschen"), KINGDOM("Reiche"), CITY("Stadt"), WORLD("Welt"), LEGENDS("Legenden"),
}

private enum class ChronicleKind(val label: String) {
    BATTLE("Schlacht"), LOSS("Verluste & Versorgung"), WEDDING("Ehe & Partnerschaft"), DEATH("Tod & Abschied"),
    RELATIONSHIP("Beziehungen"), ALLIANCE("Bündnisse & Verträge"), TERRITORY("Gebiete"), TITLE("Titel & Nachfolge"),
    BUILDING("Stadtentwicklung"), RESOURCES("Wirtschaft"), CULTURE("Kulturen"), LEGEND("Legenden"), WORLD("Weltgeschichte"),
}

private data class ChronicleViewEntry(val index: Int, val entry: ChronicleEntry, val kind: ChronicleKind) {
    val category: ChronicleCategory get() = when (kind) {
        ChronicleKind.BATTLE, ChronicleKind.LOSS -> ChronicleCategory.WAR
        ChronicleKind.WEDDING, ChronicleKind.DEATH, ChronicleKind.RELATIONSHIP -> ChronicleCategory.PEOPLE
        ChronicleKind.ALLIANCE, ChronicleKind.TITLE -> ChronicleCategory.KINGDOM
        ChronicleKind.BUILDING, ChronicleKind.RESOURCES, ChronicleKind.CULTURE -> ChronicleCategory.CITY
        ChronicleKind.LEGEND -> ChronicleCategory.LEGENDS
        ChronicleKind.TERRITORY, ChronicleKind.WORLD -> ChronicleCategory.WORLD
    }
}

@Composable
internal fun ChronicleScreen(state: GameState) {
    var category by rememberSaveable { mutableStateOf(ChronicleCategory.ALL) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        chronicleItems(state, category) { category = it }
    }
}

/** Share the timeline with MoreScreen's existing lazy list; do not nest scrolling containers. */
internal fun LazyListScope.chronicleItems(state: GameState, selected: ChronicleCategory, onSelect: (ChronicleCategory) -> Unit) {
    val history = state.chronicle.mapIndexed { index, entry -> ChronicleViewEntry(index, entry, chronicleKind(entry)) }.asReversed()
    val visible = history.filter { selected == ChronicleCategory.ALL || it.category == selected }
    item(key = "chronicle_banner") { ChronicleIdentity(state, history.size) }
    item(key = "chronicle_filters") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChronicleCategory.entries.forEach { category ->
                val count = if (category == ChronicleCategory.ALL) history.size else history.count { it.category == category }
                FilterChip(selected == category, { onSelect(category) }, label = { Text("${category.label} · $count") })
            }
        }
    }
    if (visible.isEmpty()) item(key = "chronicle_empty") { EmptyCard("In diesem Teil deiner Chronik stehen noch keine Ereignisse.") }
    visible.groupBy { (it.entry.day - 1) / 365 + 1 }.forEach { (year, chapter) ->
        item(key = "chronicle_year_$year") { SectionTitle("Jahr $year · Kapitel der Kampagne") }
        items(chapter, key = { "chronicle_${it.entry.day}_${it.index}" }) { row ->
        val battle = if (row.entry.title in setOf("Schlacht gewonnen", "Schlacht verloren")) {
            val candidates = state.war.history.filter { it.day == row.entry.day && it.victory == (row.entry.title == "Schlacht gewonnen") }
            candidates.singleOrNull { row.entry.text.contains("${it.ownStart} eigene zu Beginn, ${it.ownRemaining} Überlebende") }
                ?: candidates.singleOrNull()
        } else null
        ChronicleTimelineCard(row, state, battle)
        }
    }
}

@Composable
private fun ChronicleIdentity(state: GameState, count: Int) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .35f))) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Panel2, Panel))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BannerBadge(state.presentation.heraldry, 72.dp)
                Column(Modifier.weight(1f)) {
                    Text("DIE CHRONIK", color = Gold, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text(state.realm.settlementName, color = PaleGold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("${state.player.name} · ${state.title}", color = Mist, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Gold.copy(alpha = .2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChronicleHeadline("Tag ${state.day}", "Deine Kampagne")
                ChronicleHeadline(count.toString(), "Bewahrte Ereignisse")
            }
            Text("Siege und Verluste, Menschen und Bündnisse: die Geschichte unter deinem heutigen Reichsbanner.", color = Mist, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChronicleHeadline(value: String, label: String) {
    Column {
        Text(value, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = Mist, fontSize = 11.sp)
    }
}

@Composable
private fun ChronicleTimelineCard(row: ChronicleViewEntry, state: GameState, battle: BattleRecord?) {
    val entry = row.entry
    val accent = when (row.kind) {
        ChronicleKind.DEATH, ChronicleKind.LOSS -> Danger
        ChronicleKind.BATTLE -> if (entry.title.contains("verloren", ignoreCase = true)) Danger else Success
        ChronicleKind.WEDDING, ChronicleKind.RELATIONSHIP -> Color(0xFFC994BA)
        ChronicleKind.ALLIANCE, ChronicleKind.TERRITORY -> Blue
        else -> Gold
    }
    val icon = chronicleIcon(row.kind)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = accent.copy(alpha = .14f), shape = CircleShape, border = BorderStroke(1.dp, accent.copy(alpha = .6f))) {
                Icon(icon, contentDescription = row.kind.label, tint = accent, modifier = Modifier.size(42.dp).padding(9.dp))
            }
            Text("${entry.day}", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Canvas(Modifier.width(2.dp).weight(1f).padding(top = 6.dp)) {
                drawLine(accent.copy(alpha = .3f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
        Surface(modifier = Modifier.weight(1f).semantics { contentDescription = "Tag ${entry.day}, ${row.kind.label}, ${entry.title}" },
            color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, accent.copy(alpha = .3f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TAG ${entry.day} · ${row.kind.label.uppercase(Locale.GERMAN)}", color = accent, fontSize = 10.sp, letterSpacing = .8.sp)
                Text(entry.title, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                battle?.let { ChronicleBattleHighlight(it, accent) }
                ChronicleEntryTags(entry, state, battle)
                // The entire saved narrative stays available, including legacy entries without typed metadata.
                Text(entry.text, color = Mist, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChronicleBattleHighlight(battle: BattleRecord, accent: Color) {
    Surface(color = Panel2, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${battle.place} · ${battle.minute} Minuten", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ChronicleHeadline(battle.ownStart.toString(), "Eigene zu Beginn")
                ChronicleHeadline(battle.enemyStart.toString(), "Gegner zu Beginn")
            }
            HorizontalDivider(color = Mist.copy(alpha = .12f))
            val losses = listOf("Gefallen" to battle.casualties.dead, "Verwundet" to battle.casualties.wounded,
                "Vermisst" to battle.casualties.missing, "Gefangen" to battle.casualties.captured)
            losses.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (label, value) -> Column(Modifier.weight(1f)) {
                        Text(value.toString(), color = if (value > 0) Danger else Mist, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(label, color = Mist, fontSize = 10.sp)
                    } }
                }
            }
            battle.turningPoints.takeLast(2).forEach { Text(it, color = Mist, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ChronicleEntryTags(entry: ChronicleEntry, state: GameState, battle: BattleRecord?) {
    val text = "${entry.title} ${entry.text}".lowercase(Locale.GERMAN)
    val resources = ResourceKind.entries.filter { kind -> when (kind) {
        ResourceKind.GOLD -> "gold" in text || "schatz" in text
        ResourceKind.FOOD -> "nahrung" in text || "vorrät" in text || "hunger" in text
        ResourceKind.WOOD -> "holz" in text
        ResourceKind.STONE -> "stein" in text
        ResourceKind.IRON -> "eisen" in text
    } }
    val cultures = battle?.commanders.orEmpty().mapNotNull { id -> state.commanders.firstOrNull { it.id == id }?.culture }.distinct()
        .ifEmpty { Culture.entries.filter { culture -> when (culture) {
            Culture.HUMAN -> "menschen" in text
            Culture.WOOD_ELF -> "waldelb" in text
            Culture.GOLD_ELF -> "goldelb" in text
            Culture.WALL -> "mauerlegion" in text
        } } }
    if (resources.isEmpty() && cultures.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        resources.forEach { kind -> ChronicleTag(resourceIcon(kind), kind.label) }
        cultures.forEach { culture -> ChronicleTag(when (culture) {
            Culture.HUMAN -> Icons.Outlined.Person
            Culture.WOOD_ELF -> Icons.Outlined.Forest
            Culture.GOLD_ELF -> Icons.Outlined.Star
            Culture.WALL -> Icons.Outlined.Shield
        }, culture.label) }
    }
}

@Composable
private fun ChronicleTag(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(15.dp))
        Text(label, color = Gold, fontSize = 10.sp)
    }
}

private fun chronicleIcon(kind: ChronicleKind): ImageVector = when (kind) {
    ChronicleKind.BATTLE, ChronicleKind.LOSS -> Icons.Outlined.Shield
    ChronicleKind.WEDDING, ChronicleKind.RELATIONSHIP -> Icons.Outlined.Favorite
    ChronicleKind.DEATH -> Icons.Outlined.Close
    ChronicleKind.ALLIANCE -> Icons.Outlined.Public
    ChronicleKind.TERRITORY -> Icons.Outlined.Landscape
    ChronicleKind.TITLE -> Icons.Outlined.Castle
    ChronicleKind.BUILDING -> Icons.Outlined.LocationCity
    ChronicleKind.RESOURCES -> Icons.Outlined.Paid
    ChronicleKind.CULTURE -> Icons.Outlined.Person
    ChronicleKind.LEGEND -> Icons.Outlined.Star
    ChronicleKind.WORLD -> Icons.Outlined.Public
}

private fun chronicleKind(entry: ChronicleEntry): ChronicleKind {
    val title = entry.title.lowercase(Locale.GERMAN)
    val text = "$title ${entry.text.lowercase(Locale.GERMAN)}"
    return when {
        listOf("tod", "abschied", "gefallen im dienst").any { it in title } || "stirbt im alter" in text -> ChronicleKind.DEATH
        listOf("ehe", "verlob", "lebenspartner", "hochzeit").any { it in title } -> ChronicleKind.WEDDING
        title.startsWith("schlacht gewonnen") || title.startsWith("schlacht verloren") -> ChronicleKind.BATTLE
        listOf("legende", "erfolg:", "helden").any { it in title } -> ChronicleKind.LEGEND
        listOf("nachfolge", "regentschaft", "hochkönig", "herrscherpaar", "beförderung", "neuer titel").any { it in title } || title == "könig" -> ChronicleKind.TITLE
        listOf("verloren", "verlust", "verwund", "versorgungsnot", "gefangen", "lazarett").any { it in title } -> ChronicleKind.LOSS
        listOf("bündnis", "vertrag", "pakt", "frieden", "tribut", "vasall", "militärzugang", "krieg erklärt", "verbündete").any { it in title } -> ChronicleKind.ALLIANCE
        listOf("gebiet", "erober", "grenzland").any { it in title } -> ChronicleKind.TERRITORY
        listOf("familie", "bezieh", "gefährt", "interesse", "roman", "partnerschaft", "gemeinsam", "rat", "schließt sich an", "getrennte wege").any { it in title } -> ChronicleKind.RELATIONSHIP
        listOf("bau", "ausbau", "reparatur", "viertel").any { it in title } -> ChronicleKind.BUILDING
        listOf("gold", "handel", "ertrag", "markt", "steuer", "ernte", "schatz", "wirtschaft").any { it in title } -> ChronicleKind.RESOURCES
        listOf("kultur", "bevölkerung", "zuwander", "migration").any { it in title } -> ChronicleKind.CULTURE
        else -> ChronicleKind.WORLD
    }
}
