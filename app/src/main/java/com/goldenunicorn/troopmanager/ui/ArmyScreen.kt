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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.WarEngine
import com.goldenunicorn.troopmanager.engine.ArmyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.CampaignInsightsEngine
import com.goldenunicorn.troopmanager.engine.MilitaryEconomyEngine
import com.goldenunicorn.troopmanager.model.*

private enum class ArmyFilter(val label: String) {
    ALL("Alle Einheiten"), MELEE("Nahkampf"), RANGED("Fernkampf"), CAVALRY("Kavallerie"),
    ELITE("Elite"), SIEGE("Belagerung"), RECRUITS("Rekruten"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArmyScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val visibleCultures = ArmyEngine.visibleCultures(state)
    var culture by remember { mutableStateOf(visibleCultures.firstOrNull() ?: Culture.HUMAN) }
    var filterCulture by remember { mutableStateOf<Culture?>(null) }
    var filter by remember { mutableStateOf(ArmyFilter.ALL) }
    var commanderId by remember { mutableStateOf<Long?>(null) }
    var unitDetails by remember { mutableStateOf<UnitType?>(null) }
    val selectedCommander = state.commanders.firstOrNull { it.id == commanderId }
    if (selectedCommander != null) {
        CommanderProfileScreen(state, selectedCommander, { commanderId = null }, onState, onNotice)
        return
    }
    LaunchedEffect(visibleCultures) {
        if (culture !in visibleCultures) culture = visibleCultures.firstOrNull() ?: Culture.HUMAN
        if (filterCulture != null && filterCulture !in visibleCultures) filterCulture = null
    }
    unitDetails?.let { type ->
        ModalBottomSheet(onDismissRequest = { unitDetails = null }, containerColor = Panel,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f), contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 32.dp)) {
                item { ArmyUnitCard(state, type, false, onState, onNotice) }
                item {
                    state.commanderAssignments.filter { a -> a.units.any { it.type == type } }.forEach { assignment ->
                        val amount = assignment.units.filter { it.type == type }.sumOf { it.amount }
                        Text("${state.commanders.firstOrNull { it.id == assignment.commanderId }?.name ?: "Führung"}: $amount Soldaten", color = Mist)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().testTag("army_screen")) {
        ModernTabStrip(listOf("Übersicht", "Ausbildung", "Kommandanten"), tab, { tab = it },
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        LazyColumn(modifier = Modifier.weight(1f).testTag("army_list"),
            contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ArmyTopSummary(state) }
            when (tab) {
                0 -> {
                    if (state.invasion != null || state.frontier.hordes.any { it.discovered }) item {
                        val ready = WarEngine.defenseReadiness(state)
                        Text("Mauer: ${ready.wallArchers} Schützen · ${ready.arrows} Pfeile · ${ready.activeWeapons} aktive Mauerwaffen",
                            color = if (ready.arrows < ready.wallArchers * 3L) Danger else Gold, fontSize = 12.sp)
                    }
                    item {
                        SelectionMenu("Einheiten filtern", filter.label, ArmyFilter.entries, { it.label }) { filter = it }
                        SelectionMenu("Kultur / Volk", filterCulture?.label ?: "Alle Völker", listOf<Culture?>(null) + visibleCultures,
                            { it?.label ?: "Alle Völker" }) { filterCulture = it }
                    }
                    val types = UnitType.entries.filter { type ->
                        (filterCulture == null || type.culture == filterCulture) && when (filter) {
                            ArmyFilter.ALL -> state.soldiers(type) > 0
                            ArmyFilter.MELEE -> state.soldiers(type) > 0 && type.ranged < 8 && type != UnitType.KNIGHT
                            ArmyFilter.RANGED -> state.soldiers(type) > 0 && type.ranged >= 8 && type != UnitType.DRAGON_ARTILLERY
                            ArmyFilter.CAVALRY -> state.soldiers(type) > 0 && type == UnitType.KNIGHT
                            ArmyFilter.ELITE -> state.soldiers(type) > 0 && (type.attack >= 9 || type.defense >= 9)
                            ArmyFilter.SIEGE -> state.soldiers(type) > 0 && type == UnitType.DRAGON_ARTILLERY
                            ArmyFilter.RECRUITS -> false
                        }
                    }
                    if (filter == ArmyFilter.RECRUITS) {
                        items(visibleCultures.filter { filterCulture == null || it == filterCulture }) { selected ->
                            Text("${selected.label}: ${state.population.recruits(selected)} Rekruten", color = Gold)
                        }
                        items(state.trainingQueue.filter { filterCulture == null || it.type.culture == filterCulture }) {
                            Text("${it.amount} ${it.type.label} · noch ${it.daysRemaining} Tage Ausbildung", color = Mist)
                        }
                    } else if (types.isEmpty()) item { EmptyCard("Keine Einheiten für diesen Filter.") }
                    items(types, key = { it.name }) { type ->
                        CompactArmyUnit(state, type) { unitDetails = type }
                    }
                }
                1 -> {
                    item { SelectionMenu("Ausbildungskultur", culture.label, visibleCultures, { it.label }) { culture = it } }
                    val orders = state.trainingQueue.filter { it.type.culture == culture }
                    items(orders) { Text("${it.amount} ${it.type.label} · noch ${it.daysRemaining} Tage", color = Gold) }
                    items(UnitType.entries.filter { it.culture == culture && culture in visibleCultures }) { type ->
                        ArmyUnitCard(state, type, true, onState, onNotice)
                    }
                }
                2 -> {
                    item {
                        OutlinedButton(onClick = {
                            val result = GameEngine.promoteCommander(state); onState(result.state); onNotice(result.message)
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = state.battleSession?.isActive != true) {
                            Text("Kommandant befördern · 250 Gold", fontSize = 13.sp)
                        }
                    }
                    if (state.commanders.isEmpty()) item { EmptyCard("Ab 100 aktiven Soldaten kannst du Führungskräfte befördern.") }
                    items(state.commanders, key = { it.id }) { commander -> ArmyCommanderCard(state, commander) { commanderId = commander.id } }
                }
            }
        }
    }
}

@Composable
private fun ArmyTopSummary(state: GameState) {
    PremiumPanel {
        Text("${state.armySize} Soldaten", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        val values = listOf("Gesamt" to state.armySize, "Bereit" to state.homeArmySize, "Garnison" to state.homeArmySize,
            "Unterwegs" to state.awayArmySize, "Verwundet" to state.war.wounded.sumOf { it.soldiers }, "Rekruten" to state.population.totalRecruits)
        values.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, amount) ->
                    Column(Modifier.weight(1f)) {
                        Text(amount.toString(), color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(label, color = Mist, fontSize = 11.sp)
                    }
                }
            }
        }
        val ranged = UnitType.entries.filter { it.ranged >= 8 }.sumOf { state.homeSoldiers(it) }
        Text("Zuhause: ${state.homeArmySize - ranged} Nahkampf · $ranged Fernkampf · ${state.trainingSize} in Ausbildung",
            color = ModernBlue, fontSize = 11.sp)
    }
}

@Composable
private fun CompactArmyUnit(state: GameState, type: UnitType, onClick: () -> Unit) {
    val pool = state.armyPools.firstOrNull { it.type == type } ?: return
    val assignments = state.commanderAssignments.filter { a -> a.units.any { it.type == type } }
    val commander = assignments.firstOrNull()?.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.name } ?: state.player.name
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("army_unit_${type.name}"),
        color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ModernLine)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${pool.soldiers} · ${type.label}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${state.homeSoldiers(type)} zuhause · ${state.away(type)} unterwegs", color = Gold, fontSize = 12.sp)
            Text("Moral ${pool.morale}% · Ausrüstung ${pool.equipment}% · Erfahrung ${pool.experience}", color = Mist, fontSize = 11.sp)
            Text("${CampaignInsightsEngine.unitRole(type)} · Führung: $commander${if (assignments.size > 1) " +${assignments.size - 1}" else ""}", color = ModernBlue, fontSize = 11.sp)
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
    Surface(
        color = Color(0xFF111820),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val named = state.frontier.designs.firstOrNull { it.unitType == type && it.soldiers > 0 }
            Text(named?.name ?: type.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (named != null) Text("Eigenes Regiment · Basis ${type.label}", color = PaleGold, fontSize = 12.sp)
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
            Text(CampaignInsightsEngine.unitRole(type), color = Mist, fontSize = 12.sp)
            if (training) {
                Text(
                    "Angriff ${type.attack} · Verteidigung ${type.defense} · Fernkampf ${type.ranged}",
                    color = Mist,
                    fontSize = 13.sp,
                )
                Text(
                    "${GameEngine.trainingDays(state, type)} Tage · ${type.goldCost} Gold je Rekrut",
                    color = Gold,
                    fontSize = 13.sp,
                )
                Text(
                    "Militärgüter je Soldat: ${MilitaryEconomyEngine.requirementText(type, 1)}",
                    color = Mist,
                    fontSize = 12.sp,
                )
                if (!unlocked)
                    Text(
                        "Noch gesperrt – diese Kultur braucht eine ausreichende Bevölkerungsbasis im Reich.",
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { index, (label, value) ->
                    StatusMetric(
                        label = label,
                        value = value.toString(),
                        modifier = Modifier.weight(1f),
                        accent = if (index == 0) Gold else ModernBlue,
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ArmyQuality(label: String, value: Int, explanation: String? = null) {
    val accent = when {
        value < 40 -> Danger
        value >= 80 -> Success
        else -> Gold
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Mist, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ModernPill("$value %", accent)
            if (explanation != null) InfoTip(label, explanation)
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = accent,
            trackColor = Color.White.copy(alpha = .07f),
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
