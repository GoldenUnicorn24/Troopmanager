package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.ArmyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun ArmyScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val visibleCultures = ArmyEngine.visibleCultures(state)
    var culture by remember { mutableStateOf(visibleCultures.firstOrNull() ?: Culture.HUMAN) }
    var expandedCulture by remember { mutableStateOf<Culture?>(null) }

    LaunchedEffect(visibleCultures) {
        if (culture !in visibleCultures) {
            culture = visibleCultures.firstOrNull() ?: Culture.HUMAN
        }
        if (expandedCulture != null && expandedCulture !in visibleCultures) {
            expandedCulture = null
        }
    }
    var commanderId by remember { mutableStateOf<Long?>(null) }
    val selectedCommander = state.commanders.firstOrNull { it.id == commanderId }
    if (selectedCommander != null) {
        CommanderProfileScreen(state, selectedCommander, { commanderId = null }, onState, onNotice)
        return
    }
    BackHandler(expandedCulture != null) { expandedCulture = null }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PageTitle(
                "ARMEE",
                "${state.armySize} Soldaten · ${state.trainingQueue.sumOf { it.amount }} in Ausbildung",
            )
            TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Gold) {
                listOf("Übersicht", "Ausbildung", "Kommandanten").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(label, fontSize = 13.sp) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    item {
                        ArmyMetrics(
                            listOf(
                                "Gesamt" to state.armySize,
                                "Zuhause" to state.homeArmySize,
                                "Auf Mission" to state.awayArmySize,
                                "Zugewiesen" to UnitType.entries.sumOf { state.assigned(it) },
                            )
                        )
                    }
                    item { PersonalCommandCard(state) }
                    items(visibleCultures) { selected ->
                        ArmyCultureCard(state, selected, expandedCulture == selected) {
                            expandedCulture = if (expandedCulture == selected) null else selected
                        }
                        if (expandedCulture == selected) {
                            Column(
                                Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                UnitType.entries
                                    .filter { it.culture == selected }
                                    .forEach { type ->
                                        ArmyUnitCard(state, type, false, onState, onNotice)
                                    }
                                OutlinedButton(
                                    onClick = {
                                        culture = selected
                                        tab = 1
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text("${selected.label}: Ausbildung öffnen", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            visibleCultures.forEach { selected ->
                                FilterChip(
                                    selected = culture == selected,
                                    onClick = { culture = selected },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(selected.label, fontSize = 13.sp) },
                                )
                            }
                        }
                    }
                    if (visibleCultures.isEmpty()) {
                        item { EmptyCard("Keine aktive Kultur im Reich verfügbar.") }
                    } else {
                        item { ArmyCultureCard(state, culture, true, interactive = false) {} }
                    }
                    val orders = state.trainingQueue.filter { it.type.culture == culture }
                    if (orders.isNotEmpty()) {
                        item {
                            Surface(color = Panel2, shape = RoundedCornerShape(16.dp)) {
                                Column(
                                    Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Text(
                                        "Laufende Ausbildung",
                                        color = PaleGold,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    orders.forEach { order ->
                                        Text(
                                            "${order.amount} ${order.type.label} · noch ${order.daysRemaining} Tage",
                                            color = Mist,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(
                        UnitType.entries.filter {
                            it.culture == culture && culture in visibleCultures
                        }
                    ) { type ->
                        ArmyUnitCard(state, type, true, onState, onNotice)
                    }
                }
                2 -> {
                    item {
                        OutlinedButton(
                            onClick = {
                                val result = GameEngine.promoteCommander(state)
                                onState(result.state)
                                onNotice(result.message)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = state.battleSession?.isActive != true,
                        ) {
                            Text("Kommandant befördern · 250 Gold", fontSize = 13.sp)
                        }
                    }
                    if (state.commanders.isEmpty())
                        item {
                            EmptyCard(
                                "Ab 100 aktiven Soldaten kannst du eine Führungskraft befördern und ihr Truppen zuweisen."
                            )
                        }
                    items(state.commanders, key = { it.id }) { commander ->
                        ArmyCommanderCard(state, commander) { commanderId = commander.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalCommandCard(state: GameState) {
    Surface(
        color = Color(0xFF252619),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = .4f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Dein persönliches Kommando",
                    color = PaleGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    "Dir direkt unterstellt: ${UnitType.entries.sumOf { state.directCommand(it) }} Soldaten",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
            InfoTip(
                "Dein persönliches Kommando",
                "Diese Soldaten sind zuhause und keinem anderen Kommandanten zugewiesen. Sie stehen direkt unter deinem Kommando. Truppen auf Mission sind nicht verfügbar.",
            )
        }
    }
}

@Composable
private fun ArmyCultureCard(
    state: GameState,
    culture: Culture,
    expanded: Boolean,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    val types = UnitType.entries.filter { it.culture == culture }
    val pools = state.armyPools.filter { it.type.culture == culture }
    val total = types.sumOf { state.soldiers(it) }
    fun quality(value: (ArmyUnitPool) -> Int): Int =
        if (total == 0) 0 else (pools.sumOf { value(it).toLong() * it.soldiers } / total).toInt()
    Surface(
        modifier = Modifier.clickable(enabled = interactive, onClick = onClick),
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (expanded) Gold else Panel2),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(160.dp)) {
                CategoryArt(culture, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0090D10)))
                        )
                )
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            culture.label,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                        )
                        Text(
                            "$total Soldaten · ${ArmyEngine.recruitable(state, culture)} Rekruten",
                            color = PaleGold,
                            fontSize = 14.sp,
                        )
                    }
                    if (interactive)
                        Text(
                            if (expanded) "Weniger ↑" else "Einheiten ↓",
                            color = Gold,
                            fontSize = 13.sp,
                        )
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ArmyMetrics(
                    listOf(
                        "Zuhause" to types.sumOf { state.homeSoldiers(it) },
                        "Auf Mission" to types.sumOf { state.away(it) },
                        "Zugewiesen" to types.sumOf { state.assigned(it) },
                        "Persönlich" to types.sumOf { state.directCommand(it) },
                    )
                )
                if (total > 0) {
                    ArmyQuality(
                        "Moral",
                        quality { it.morale },
                        "Hohe Moral hält Formationen stabil und stärkt die Kampfkraft. Versorgung und erfolgreiche Einsätze helfen.",
                    )
                    ArmyQuality(
                        "Erfahrung",
                        quality { it.experience },
                        "Erfahrene Soldaten kämpfen stärker. Rekruten senken zunächst den Durchschnitt; Einsätze bauen Erfahrung auf.",
                    )
                    ArmyQuality(
                        "Ausrüstung",
                        quality { it.equipment },
                        "Ausrüstung stärkt die Kampfkraft und wird im Kampf beschädigt. Reparatur kostet Gold und Eisen.",
                    )
                }
            }
        }
    }
}

@Composable
private fun ArmyUnitCard(
    state: GameState,
    type: UnitType,
    training: Boolean,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    val pool = state.armyPools.firstOrNull { it.type == type }
    val unlocked = GameEngine.isUnitUnlocked(state, type)
    val available = ArmyEngine.recruitable(state, type.culture)
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(type.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            ArmyMetrics(
                listOf(
                    "Gesamt" to state.soldiers(type),
                    "Zuhause" to state.homeSoldiers(type),
                    "Unterwegs" to state.away(type),
                    "Zugewiesen" to state.assigned(type),
                )
            )
            Text(
                "Dir direkt unterstellt: ${state.directCommand(type)} Soldaten",
                color = PaleGold,
                fontSize = 14.sp,
            )
            if (pool != null) {
                ArmyQuality("Moral", pool.morale)
                ArmyQuality("Erfahrung", pool.experience)
                ArmyQuality("Ausrüstung · ${armyEquipmentQuality(pool.equipment)}", pool.equipment)
                if (pool.equipment < 100) {
                    val cost = ArmyEngine.equipmentRepairCost(state, type)
                    Text(
                        "Reparatur +${minOf(20, 100 - pool.equipment)}: ${cost.gold} Gold · ${cost.iron} Eisen",
                        color = Gold,
                        fontSize = 13.sp,
                    )
                    OutlinedButton(
                        onClick = {
                            val result = ArmyEngine.repairEquipment(state, type)
                            onState(result.state)
                            onNotice(result.message)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = state.away(type) == 0 && state.battleSession?.isActive != true,
                    ) {
                        Text("Ausrüstung erneuern", fontSize = 13.sp)
                    }
                }
            }
            if (training) {
                Text(
                    "Angriff ${type.attack} · Verteidigung ${type.defense} · Fernkampf ${type.ranged}",
                    color = Mist,
                    fontSize = 13.sp,
                )
                Text(
                    "${GameEngine.trainingDays(state, type)} Tage · ${type.goldCost} Gold / ${type.ironCost} Eisen je Rekrut",
                    color = Gold,
                    fontSize = 13.sp,
                )
                if (!unlocked)
                    Text(
                        "Noch gesperrt – baue dein Reich weiter aus.",
                        color = Mist,
                        fontSize = 13.sp,
                    )
                else {
                    Text("Ausbilden aus $available Rekruten", color = Color.White, fontSize = 14.sp)
                    listOf(10, 25, 50, 100).chunked(2).forEach { percentages ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            percentages.forEach { percent ->
                                val amount = kotlin.math.ceil(available * percent / 100.0).toInt()
                                OutlinedButton(
                                    onClick = {
                                        val result = GameEngine.recruit(state, type, percent)
                                        onState(result.state)
                                        onNotice(result.message)
                                    },
                                    enabled =
                                        available > 0 && state.battleSession?.isActive != true,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(6.dp),
                                ) {
                                    Text(
                                        "${if (percent == 100) "ALLE" else "$percent %"} · $amount",
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArmyMetrics(values: List<Pair<String, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f)
                            .background(Panel2, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            value.toString(),
                            color = PaleGold,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(label, color = Mist, fontSize = 13.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ArmyQuality(label: String, value: Int, explanation: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Mist, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$value %", color = PaleGold, fontSize = 13.sp)
            if (explanation != null) InfoTip(label, explanation)
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = if (value < 40) Danger else Gold,
            trackColor = Panel2,
        )
    }
}

private fun armyEquipmentQuality(value: Int) =
    when {
        value >= 95 -> "Meisterhaft"
        value >= 85 -> "Elite"
        value >= 70 -> "Gut"
        else -> "Standard"
    }
