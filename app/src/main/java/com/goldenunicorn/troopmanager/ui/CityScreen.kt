package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.CityEngine
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.renameSettlement
import com.goldenunicorn.troopmanager.model.*

/** Large city scene and its campaign-backed administration share one selected-building panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onNextDay: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var night by rememberSaveable { mutableStateOf(false) }
    var cityName by
        remember(state.realm.settlementName) { mutableStateOf(state.realm.settlementName) }
    var districtName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var accessibleList by rememberSaveable { mutableStateOf(false) }
    val selected =
        selectedName?.let { name -> BuildingType.entries.firstOrNull { it.name == name } }
    val district =
        districtName?.let { name -> CityDistrict.entries.firstOrNull { it.name == name } }
    fun applyAction(result: GameEngine.ActionResult) {
        onState(result.state)
        onNotice(result.message)
    }
    val buildings = citySites.filter { district == null || it.district == district }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("DEINE STADT", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    state.realm.settlementName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
                Text(
                    "${state.realm.settlementTier.label} · ${state.population.total} Einwohner",
                    color = Mist,
                    fontSize = 11.sp,
                )
            }
            TextButton(onClick = onNextDay, enabled = state.battleSession?.isActive != true) {
                Text("Tag ${state.day} →", color = Gold)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Gold) {
            listOf("Stadtansicht", "Verwaltung", "Bauen").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, fontSize = 12.sp) },
                )
            }
        }
        when (tab) {
            0 -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CityScene(
                        state,
                        Modifier.fillMaxSize(),
                        night = night,
                        season = state.city.season,
                        onBuilding = { selectedName = it.name },
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = night,
                            onClick = { night = !night },
                            label = { Text(if (night) "Nacht" else "Tag") },
                        )
                        FilterChip(
                            selected = accessibleList,
                            onClick = { accessibleList = !accessibleList },
                            label = { Text("Gebäudeliste") },
                        )
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Ink.copy(alpha = .96f))
                                )
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (state.invasion != null) {
                            val days = (state.invasion.arrivalDay - state.day).coerceAtLeast(0)
                            Text(
                                if (days == 0) "BELAGERUNG · ${state.invasion.enemy.label}"
                                else "VERTEIDIGUNG · Angriff in $days Tagen",
                                color = PaleGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                            Text(
                                "Mauer ${cityWallIntegrity(state)}% · ${state.invasion.devices.joinToString { it.label }}",
                                color = Mist,
                                fontSize = 11.sp,
                            )
                        } else {
                            Text(
                                "Eine Stadt, die mit deinem Reich wächst",
                                color = PaleGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "Zwei Finger: Zoom & Verschieben · Doppeltippen: Übersicht",
                            color = Mist,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (accessibleList) {
                    CityDistrictFilter(district) { districtName = it?.name }
                    LazyColumn(
                        Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(buildings, key = { it.type.name }) { site ->
                            CityBuildingRow(state, site) { selectedName = site.type.name }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CityDistrict.entries.forEach { area ->
                            AssistChip(
                                onClick = {
                                    districtName = area.name
                                    accessibleList = true
                                },
                                label = { Text(area.label, fontSize = 11.sp) },
                            )
                        }
                    }
                }
                QueueSummary(state)
            }
            1 -> {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item {
                        StatGrid(
                            listOf(
                                "Wohnraum" to
                                    "${state.population.total} / ${state.city.housingCapacity}",
                                "Arbeiter" to "${state.workers} / ${state.workerDemand}",
                                "Zufriedenheit" to "${state.city.satisfaction}%",
                                "Wohlstand" to "${state.city.prosperity}%",
                                "Sicherheit" to "${state.city.security}%",
                                "Mauerintegrität" to "${state.realm.wallIntegrity}%",
                            )
                        )
                    }
                    item {
                        CityPanel("Wohnen & Ordnung") {
                            OutlinedTextField(
                                cityName,
                                { cityName = it.take(28) },
                                label = { Text("Name deiner Stadt") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SmallAction("Stadt umbenennen") {
                                onState(renameSettlement(state, cityName))
                                onNotice("Stadt umbenannt.")
                            }
                            Text(
                                "Freie Plätze: ${(state.city.housingCapacity - state.population.total).coerceAtLeast(0)}",
                                color = Color.White,
                            )
                            CityProgress(
                                state.population.total.toFloat() /
                                    state.city.housingCapacity.coerceAtLeast(1),
                                "Wohnraumbelegung",
                            )
                            Text(
                                "Wohnviertel schaffen +600 Plätze je Stufe. Zuzug braucht Nahrung, Wohnraum und Zufriedenheit.",
                                color = Mist,
                                fontSize = 12.sp,
                            )
                            OutlinedButton(
                                onClick = { selectedName = BuildingType.RESIDENTIAL.name },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Wohnviertel ansehen")
                            }
                            if (state.realm.wallIntegrity < 100) {
                                val cost = (100 - state.realm.wallIntegrity) * 8
                                OutlinedButton(
                                    onClick = { applyAction(GameEngine.repairWall(state)) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Mauer reparieren · $cost Gold + $cost Stein")
                                }
                            }
                        }
                    }
                    item {
                        CityPanel("Steuern") {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TaxLevel.entries.forEach { level ->
                                    FilterChip(
                                        selected = state.city.taxLevel == level,
                                        onClick = {
                                            applyAction(CityEngine.setTaxLevel(state, level))
                                        },
                                        label = { Text(level.label, fontSize = 12.sp) },
                                    )
                                }
                            }
                            Text(
                                when (state.city.taxLevel) {
                                    TaxLevel.LOW ->
                                        "−20% Steuergold · +25% Wachstum · Zufriedenheit +1/Tag"
                                    TaxLevel.NORMAL -> "Ausgewogene Steuern · neutrales Wachstum"
                                    TaxLevel.HIGH ->
                                        "+25% Steuergold · −35% Wachstum · Zufriedenheit −2/Tag"
                                },
                                color = Mist,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    item {
                        CityPanel("Arbeiterpriorität") {
                            WorkerPriority.entries.chunked(3).forEach { priorities ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    priorities.forEach { priority ->
                                        FilterChip(
                                            selected = state.city.workerPriority == priority,
                                            onClick = {
                                                applyAction(
                                                    CityEngine.setWorkerPriority(state, priority)
                                                )
                                            },
                                            label = { Text(priority.label, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            Text(
                                "Ausgewogen verteilt verfügbare Arbeiter gleichmäßig. Ein Schwerpunkt verschiebt Arbeitskraft zur gewählten Produktion.",
                                color = Mist,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    item { SectionTitle("Lager & Tagesbilanz") }
                    items(ResourceKind.entries, key = { it.name }) { kind ->
                        val detail = EconomyEngine.breakdown(state, kind)
                        CityPanel(kind.label) {
                            CityValueRow(
                                "Bestand / Kapazität",
                                "${kind.value(state.resources)} / ${detail.capacity}",
                            )
                            CityProgress(
                                kind.value(state.resources).toFloat() /
                                    detail.capacity.coerceAtLeast(1),
                                "${kind.label}: Lagerbelegung",
                            )
                            if (detail.overflow > 0)
                                Text(
                                    "Überlauf: ${detail.overflow} / Tag · Lagerhaus ausbauen",
                                    color = Danger,
                                    fontSize = 12.sp,
                                )
                            CityValueRow(
                                "Brutto / Unterhalt",
                                "+${detail.gross} / −${detail.upkeep}",
                            )
                            CityValueRow(
                                "Netto pro Tag",
                                "${if (detail.net >= 0) "+" else ""}${detail.net}",
                                if (detail.net < 0) Danger else Success,
                            )
                            var expanded by rememberSaveable(kind.name) { mutableStateOf(false) }
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(
                                    if (expanded) "Aufschlüsselung schließen"
                                    else "Produktion aufschlüsseln"
                                )
                            }
                            if (expanded) {
                                CityValueRow("Grundproduktion", detail.base.toString())
                                CityValueRow("Gebäudewirkung", detail.building.toString())
                                CityValueRow("Gebietsbonus", detail.territory.toString())
                                CityValueRow(
                                    "Arbeiterfaktor",
                                    "${(detail.workerFactor * 100).toInt()}%",
                                )
                                CityValueRow("Events & Handel", detail.eventBonus.toString())
                                CityValueRow("Steuereffekt", detail.taxBonus.toString())
                            }
                        }
                    }
                    item {
                        MarketPanel(state) { kind, amount, buy ->
                            applyAction(CityEngine.trade(state, kind, amount, buy))
                        }
                    }
                    item {
                        CityPanel("Bauverwaltung") {
                            Text(
                                "${state.city.constructionQueue.size} / ${CityEngine.constructionSlots(state)} Bauplätze belegt",
                                color = Gold,
                            )
                            Text(
                                "Residenz Stufe 3: zweiter Bauplatz · Offiziersschule Stufe 2: dritter Bauplatz",
                                color = Mist,
                                fontSize = 12.sp,
                            )
                            QueueSummary(state)
                            OutlinedButton(
                                onClick = { selectedName = BuildingType.WAREHOUSE.name },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Lagerhaus ansehen")
                            }
                        }
                    }
                }
            }
            else -> {
                CityDistrictFilter(district) { districtName = it?.name }
                QueueSummary(state)
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(buildings, key = { it.type.name }) { site ->
                        CityBuildingRow(state, site) { selectedName = site.type.name }
                    }
                }
            }
        }
    }
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedName = null },
            containerColor = Panel,
            contentColor = Mist,
        ) {
            BuildingDetails(state, selected) {
                applyAction(CityEngine.startConstruction(state, selected))
            }
        }
    }
}

@Composable
private fun CityDistrictFilter(selected: CityDistrict?, onSelect: (CityDistrict?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Alle Bezirke") },
        )
        CityDistrict.entries.forEach { area ->
            FilterChip(
                selected = selected == area,
                onClick = { onSelect(area) },
                label = { Text(area.label, fontSize = 11.sp) },
            )
        }
    }
}

@Composable
private fun CityBuildingRow(state: GameState, site: CitySite, onClick: () -> Unit) {
    val level = state.realm.level(site.type)
    val queued = state.city.constructionQueue.firstOrNull { it.type == site.type }
    Surface(
        color = Panel2,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(site.type.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    if (queued != null) "Im Bau · noch ${queued.daysRemaining} Tage"
                    else site.district.label,
                    color = if (queued != null) Gold else Mist,
                    fontSize = 11.sp,
                )
            }
            Text(if (level == 0) "Bauplatz →" else "Stufe $level →", color = Gold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CityPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PaleGold, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CityValueRow(label: String, value: String, color: Color = Gold) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Mist, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CityProgress(progress: Float, description: String) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = if (progress > 1f) Danger else Gold,
        trackColor = Panel2,
        modifier =
            Modifier.fillMaxWidth().height(5.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun QueueSummary(state: GameState) {
    if (state.city.constructionQueue.isEmpty()) {
        if (state.city.lastCompletedBuildings.isNotEmpty()) {
            Text(
                "Fertig: ${state.city.lastCompletedBuildings.joinToString { it.label }}",
                color = Success,
                fontSize = 11.sp,
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.city.constructionQueue.forEach { order ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${order.type.label} → ${order.targetLevel}",
                    color = PaleGold,
                    fontSize = 11.sp,
                )
                Text("${order.daysRemaining} Tage", color = Gold, fontSize = 11.sp)
            }
            CityProgress(
                (order.totalDays - order.daysRemaining).toFloat() /
                    order.totalDays.coerceAtLeast(1),
                "${order.type.label}: Baufortschritt",
            )
        }
    }
}

@Composable
private fun MarketPanel(state: GameState, onTrade: (ResourceKind, Int, Boolean) -> Unit) {
    var resourceName by rememberSaveable { mutableStateOf(ResourceKind.FOOD.name) }
    var amount by rememberSaveable { mutableIntStateOf(100) }
    val resource = ResourceKind.valueOf(resourceName)
    CityPanel("Markt & Handel") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ResourceKind.entries
                .filter { it != ResourceKind.GOLD }
                .forEach { kind ->
                    FilterChip(
                        selected = resource == kind,
                        onClick = { resourceName = kind.name },
                        label = { Text(kind.label) },
                    )
                }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(100, 500, 1000).forEach { quantity ->
                FilterChip(
                    selected = amount == quantity,
                    onClick = { amount = quantity },
                    label = { Text(quantity.toString()) },
                )
            }
        }
        Text(
            "Bestand: ${resource.value(state.resources)} · Kauf ${CityEngine.buyPrice(resource)} / Verkauf ${CityEngine.sellPrice(resource)} Gold je Einheit",
            color = Mist,
            fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val buyCost = amount * CityEngine.buyPrice(resource)
            val sale = amount * CityEngine.sellPrice(resource)
            Button(
                onClick = { onTrade(resource, amount, true) },
                enabled =
                    state.resources.gold >= buyCost &&
                        resource.value(state.resources).toLong() + amount <=
                            resource.value(state.city.storageCapacity).toLong() &&
                        state.realm.level(BuildingType.MARKET) > 0 &&
                        state.battleSession?.isActive != true,
                modifier = Modifier.weight(1f),
            ) {
                Text("Kaufen · $buyCost G", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { onTrade(resource, amount, false) },
                enabled =
                    resource.value(state.resources) >= amount &&
                        state.resources.gold.toLong() + sale <=
                            state.city.storageCapacity.gold.toLong() &&
                        state.realm.level(BuildingType.MARKET) > 0 &&
                        state.battleSession?.isActive != true,
                modifier = Modifier.weight(1f),
            ) {
                Text("Verkaufen · $sale G", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BuildingDetails(state: GameState, type: BuildingType, onConstruction: () -> Unit) {
    val level = state.realm.level(type)
    val cost = GameEngine.buildingCost(state, type)
    val queue = state.city.constructionQueue.firstOrNull { it.type == type }
    val canAfford = ResourceKind.entries.all { it.value(state.resources) >= it.value(cost) }
    val freeSlot = state.city.constructionQueue.size < CityEngine.constructionSlots(state)
    val producing =
        ResourceKind.entries.firstOrNull { EconomyEngine.productionBuilding(it) == type }
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(type.label, color = PaleGold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Stufe $level · ${citySites.first { it.type == type }.district.label}", color = Gold)
        Text(buildingEffect(type, state), color = Mist, fontSize = 13.sp)
        if (producing != null) {
            val detail = EconomyEngine.breakdown(state, producing)
            CityPanel("${producing.label} / Tag") {
                CityValueRow("Gebäude · Brutto", "+${detail.building} · ${detail.gross}")
                CityValueRow("Arbeiterbedarf", "${level * 25}")
                CityValueRow("Arbeiterfaktor", "${(detail.workerFactor * 100).toInt()}%")
                CityValueRow("Gebietsbonus", "+${detail.territory}")
                CityValueRow("Events & Handel", "+${detail.eventBonus}")
                CityValueRow("Steuereffekt", "${detail.taxBonus}")
                CityValueRow(
                    "Unterhalt / Netto",
                    "−${detail.upkeep} / ${if (detail.net >= 0) "+" else ""}${detail.net}",
                    if (detail.net < 0) Danger else Success,
                )
            }
        } else
            CityValueRow(
                "Arbeiterbedarf",
                "${level * 25} · Reichsfaktor ${(state.workers.toFloat() / state.workerDemand.coerceAtLeast(1) * 100).toInt()}%",
            )
        CityPanel("Ausbau auf Stufe ${level + 1}") {
            CityValueRow("Gold / Holz", "${cost.gold} / ${cost.wood}")
            CityValueRow("Stein / Eisen", "${cost.stone} / ${cost.iron}")
            if (cost.food > 0) CityValueRow("Nahrung", cost.food.toString())
            CityValueRow("Bauzeit", "${CityEngine.buildingDays(state, type)} Tage")
            CityValueRow(
                "Bauplätze",
                "${state.city.constructionQueue.size} / ${CityEngine.constructionSlots(state)} belegt",
            )
            Text(appearancePreview(type, level + 1), color = Mist, fontSize = 12.sp)
            if (queue != null) {
                Text(
                    "Ausbau läuft: Stufe ${queue.targetLevel} in ${queue.daysRemaining} Tagen",
                    color = Gold,
                )
                CityProgress(
                    (queue.totalDays - queue.daysRemaining).toFloat() /
                        queue.totalDays.coerceAtLeast(1),
                    "Baufortschritt",
                )
            } else {
                Button(
                    onClick = onConstruction,
                    enabled =
                        canAfford &&
                            freeSlot &&
                            state.battleSession?.isActive != true &&
                            level < 100,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                ) {
                    Text(
                        if (level == 0) "Bau beauftragen" else "Ausbau beauftragen",
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!canAfford)
                    Text("Ressourcen reichen noch nicht aus.", color = Danger, fontSize = 12.sp)
                if (!freeSlot)
                    Text(
                        "Alle Bauplätze belegt. Freier Platz nach Abschluss eines Projekts.",
                        color = Gold,
                        fontSize = 12.sp,
                    )
            }
        }
    }
}

private fun appearancePreview(type: BuildingType, target: Int): String {
    val stage =
        when {
            target >= 6 ->
                "Monumentaler Ausbau: weitere Gebäudeflügel, hohe Dächer und große Wehranlagen."
            target >= 3 ->
                "Erweiterter Ausbau: mehr Geschosse, Nebenbauten und stärkere Silhouette."
            else -> "Früher Ausbau: kompakter Steinbau mit klar erkennbarer Funktion."
        }
    val next =
        when {
            target < 3 -> " Nächster Architekturwechsel auf Stufe 3."
            target < 6 -> " Nächster Architekturwechsel auf Stufe 6."
            else -> " Maximale Architekturklasse; weitere Stufen steigern die Wirkung."
        }
    return "${type.label}: $stage$next"
}

private fun buildingEffect(type: BuildingType, state: GameState): String =
    when (type) {
        BuildingType.FARM -> "Höfe liefern Nahrung für Reich, Armee und Ausbildung."
        BuildingType.SAWMILL -> "Waldwirtschaft liefert Holz für Bauprojekte und Ausrüstung."
        BuildingType.QUARRY -> "Stein für Befestigungen, Residenz und große Bauprojekte."
        BuildingType.IRONWORKS -> "Erzverarbeitung liefert Eisen für Waffen und schwere Truppen."
        BuildingType.MARKET ->
            "Handel und Steuern erzeugen Gold; der Markt erlaubt Kauf und Verkauf von Ressourcen."
        BuildingType.BARRACKS -> "Kasernen verbessern die Moral neu ausgebildeter Soldaten."
        BuildingType.WALL ->
            "Die Stadtmauer schützt das Reich; Ausbau stellt die Integrität auf 100% wieder her."
        BuildingType.TOWER -> "Türme verstärken die Verteidigung und verbessern die Sicherheit."
        BuildingType.PALACE ->
            "Die Residenz bildet das Zentrum deiner Stadt. Ab Stufe 3 steht ein zweiter Bauplatz zur Verfügung."
        BuildingType.RESIDENTIAL ->
            "Jede Stufe schafft 600 zusätzliche Wohnplätze. Aktuell ${state.city.housingCapacity} Plätze."
        BuildingType.WAREHOUSE ->
            "Jede Stufe schafft 5.000 weitere Lagerplätze je Ressource. Bestehende Bestände bleiben erhalten."
        BuildingType.HOSPITAL ->
            "Das Lazarett unterstützt die Erholung: bis zu +3 Truppenmoral pro Tag."
        BuildingType.ACADEMY ->
            "Die Offiziersschule erweitert die Verwaltung. Ab Stufe 2 steht ein dritter Bauplatz zur Verfügung."
        BuildingType.STABLES ->
            "Stallungen bilden das sichtbare Zentrum deiner Kavallerie. Zusätzliche Produktionsboni sind für den weiteren Ausbau vorbereitet."
        BuildingType.ARSENAL ->
            "Das Arsenal bildet das Zentrum für Ausrüstung und Reparaturen. Weitere Ausrüstungsboni sind vorbereitet."
        BuildingType.EMBASSY ->
            "Die Botschaft erhöht den angestrebten Wohlstand um 3 je Stufe und bereitet weitere Diplomatieoptionen vor."
    }
