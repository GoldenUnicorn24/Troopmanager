package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.ArmyEngine
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.EventEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.renameSettlement
import com.goldenunicorn.troopmanager.engine.customizeCommanderPortrait
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun RealmScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var selectedBuilding by remember { mutableStateOf<BuildingType?>(null) }
    selectedBuilding?.let { type ->
        val cost = GameEngine.buildingCost(state,type)
        AlertDialog(
            onDismissRequest = { selectedBuilding = null },
            title = { Text(type.label + " · Stufe " + state.realm.level(type)) },
            text = { Text("${buildingEffect(type, state.realm.level(type))}\nAusbau: ${cost.gold} Gold · ${cost.wood} Holz · ${cost.stone} Stein") },
            confirmButton = { TextButton(onClick = {
                val result = GameEngine.build(state, type)
                onState(result.state)
                onNotice(result.message)
                selectedBuilding = null
            }) { Text("Ausbauen") } },
            dismissButton = { TextButton(onClick = { selectedBuilding = null }) { Text("Schließen") } }
        )
    }
    var settlementName by remember(state.realm.settlementName) { mutableStateOf(state.realm.settlementName) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Panel) {
                Box(Modifier.fillMaxWidth().height(190.dp)) {
                    Image(
                        painterResource(R.drawable.splash_fortress),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(state.realm.settlementName.uppercase(), color = PaleGold, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text(
                            state.player.name + " · " + state.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("Tag " + state.day + " · " + state.rank, color = Mist, fontSize = 13.sp)
                    }
                }
            }
        }
        item { ResourceStrip(state.resources, EconomyEngine.production(state).net) }
        item { DailyEconomyCard(state) }
        item { FortressMap(state) { selectedBuilding = it } }
        item { RealmGoalsCard(state) }
        item { PopulationCard(state) }
        item { InvasionPreparationCard(state, onState, onNotice) }
        state.pendingRealmEvent?.let { event ->
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(event.category + " · " + event.title, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(event.text, color = Mist)
                        EventEngine.choices(event).forEachIndexed { index, choice ->
                            OutlinedButton(onClick = {
                                val result = EventEngine.choose(state, index)
                                onState(result.state)
                                onNotice(result.message)
                            }, modifier = Modifier.fillMaxWidth()) { Text(choice) }
                        }
                    }
                }
            }
        }
        if (state.completedRealm) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2B2717),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("HOCHKÖNIGREICH ERRICHTET", color = PaleGold, fontWeight = FontWeight.Black)
                        Text(
                            "Der vollständige Aufstieg ist erreicht. Das Reich bleibt offen spielbar und Gegner skalieren weiter.",
                            color = Mist
                        )
                    }
                }
            }
        }
        item {
            GoldButton(
                "Nächsten Tag beginnen",
                {
                    val result = GameEngine.advanceDay(state)
                    onState(result.state)
                    onNotice(result.message)
                },
                Modifier.fillMaxWidth()
            )
        }
        item {
            StatGrid(
                listOf(
                    "Ruhm" to state.renown.toString(),
                    "Gebiete" to state.realm.territory.toString(),
                    "Bevölkerung" to state.population.total.toString(),
                    "Armee" to state.armySize.toString(),
                    "Bedrohung" to (state.realm.threat.toString() + "%"),
                    "Mauer" to (state.realm.wallIntegrity.toString() + "%")
                )
            )
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Land & Expansion", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Land öffnet Platz für Bevölkerung, Mauern und größere Armeen. Als Halbelb ziehen mehrere Kulturen gleichzeitig zu.",
                        color = Mist,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    val cost = 600 + state.realm.territory * 550
                    SmallAction("Landrecht kaufen · " + cost + " Gold") {
                        val result = GameEngine.buyLand(state)
                        onState(result.state)
                        onNotice(result.message)
                    }
                    OutlinedTextField(
                        value = settlementName,
                        onValueChange = { settlementName = it.take(28) },
                        label = { Text("Name deiner Stadt / Festung") },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    SmallAction("Festung umbenennen") {
                        onState(renameSettlement(state, settlementName))
                        onNotice("Festung umbenannt.")
                    }
                }
            }
        }
        item { SectionTitle("Produktion") }
        items(listOf(BuildingType.FARM, BuildingType.SAWMILL, BuildingType.QUARRY, BuildingType.IRONWORKS, BuildingType.MARKET)) { type ->
            BuildingDetailRow(state, type) {
                val result = GameEngine.build(state, type)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { SectionTitle("Militär & Verteidigung") }
        items(listOf(BuildingType.BARRACKS, BuildingType.WALL, BuildingType.TOWER)) { type ->
            BuildingDetailRow(state, type) {
                val result = GameEngine.build(state, type)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { SectionTitle("Herrschaft") }
        item {
            BuildingDetailRow(state, BuildingType.PALACE) {
                val result = GameEngine.build(state, BuildingType.PALACE)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DailyEconomyCard(state: GameState) {
    val production = EconomyEngine.production(state)
    val net = production.net
    fun signed(value: Int) = if (value >= 0) "+$value" else value.toString()
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Wirtschaft pro Tag", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Gold ${state.resources.gold} (${signed(net.gold)}/Tag) · Holz ${state.resources.wood} (${signed(net.wood)}/Tag)", color = PaleGold)
            Text("Stein ${state.resources.stone} (${signed(net.stone)}/Tag) · Eisen ${state.resources.iron} (${signed(net.iron)}/Tag)", color = PaleGold)
            Text("Nahrung ${state.resources.food} · Produktion +${production.gross.food} · Armeeunterhalt −${production.upkeep} · Netto ${signed(net.food)}/Tag", color = if (net.food < 0) Danger else Gold)
            if (state.resources.food == 0) Text("Nahrungsmangel schwächt Moral, Wachstum, Ausbildung und Kampfkraft.", color = Danger)
            Text("${state.workers} Arbeiter von ${state.workerDemand} benötigt. Fehlende Arbeiter senken die Produktion.", color = Mist, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RealmGoalsCard(state: GameState) {
    val goals = buildList {
        if (state.armySize < 1000) add("Heer auf 1.000 Soldaten ausbauen · ${state.armySize}/1.000")
        if (state.realm.territory < 2) add("Ein zweites Gebiet erwerben")
        if (state.realm.level(BuildingType.WALL) < 2) add("Mauer auf Stufe 2 ausbauen")
        if (state.commanders.none { it.level > 1 }) add("Einen Kommandanten entwickeln")
        if (state.activeMissions.none { !it.status.isAway }) add("Eine Mission abschließen")
        if (isEmpty()) {
            add("Reich auf ${state.realm.territory + 1} Gebiete erweitern")
            add("Heer auf ${((state.armySize / 5000) + 1) * 5000} Soldaten ausbauen")
            add("Verteidigung stärken und die nächste Invasion bestehen")
        }
    }
    Surface(color = Color(0xFF252619), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Deine nächsten Ziele", color = PaleGold, fontWeight = FontWeight.Bold)
            goals.take(3).forEach { Text("• $it", color = Mist, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun PopulationCard(state: GameState) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bevölkerung · ${state.population.total}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("${state.civilianPopulation} Zivilisten · ${state.workers} Arbeiter · ${state.population.totalRecruits} Rekruten · ${state.freePopulation} frei", color = Mist)
            Text("${state.armySize} Soldaten · ${state.trainingSize} in Ausbildung", color = PaleGold)
            Text("Soldaten und Auszubildende arbeiten nicht in der Wirtschaft. Rekrutierung verringert die zivile Reserve.", color = Mist, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InvasionPreparationCard(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val invasion = state.invasion
    Surface(color = if (invasion != null) Color(0xFF302020) else Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (invasion == null) "Grenzlage · ${state.realm.threat}% Bedrohung" else "${invasion.enemy.label} nähert sich", color = if (invasion == null) PaleGold else Danger, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { state.realm.threat / 100f }, modifier = Modifier.fillMaxWidth(), color = Danger)
            Text(if (invasion == null) "40% Späherwarnung · 60% Überfälle · 75% Heeresbewegung · 90% Invasion · 100% Angriff" else "Ankunft in ${(invasion.arrivalDay - state.day).coerceAtLeast(0)} Tagen · Stärke ${invasion.strength}", color = Mist, fontSize = 12.sp)
            if (invasion != null) Text("Belagerungsgerät: ${invasion.devices.joinToString { it.label }}", color = Mist, fontSize = 12.sp)
            Text("${state.homeArmySize} Soldaten daheim · ${state.awayArmySize} unterwegs · Mauer ${state.realm.wallIntegrity}%", color = PaleGold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val result = GameEngine.repairWall(state); onState(result.state); onNotice(result.message) }, enabled = state.realm.wallIntegrity < 100, modifier = Modifier.weight(1f)) { Text("Mauer reparieren") }
                if (invasion != null) OutlinedButton(onClick = { val result = GameEngine.requestAllies(state); onState(result.state); onNotice(result.message) }, enabled = !invasion.alliesRequested, modifier = Modifier.weight(1f)) { Text(if (invasion.alliesRequested) "Verbündete gerufen" else "Hilfe anfordern") }
            }
            if (invasion != null) Text("Vorräte sichern, Ausbildung starten, Kommandanten verteilen. Unter Welt → Missionen kannst du Truppen zurückrufen.", color = Mist, fontSize = 12.sp)
        }
    }
}

private fun buildingEffect(type: BuildingType, level: Int): String = when (type) {
    BuildingType.FARM -> "+${level * 150} Nahrung/Tag vor Arbeitskraftbonus"
    BuildingType.SAWMILL -> "+${level * 100} Holz/Tag vor Arbeitskraftbonus"
    BuildingType.QUARRY -> "+${level * 100} Stein/Tag vor Arbeitskraftbonus"
    BuildingType.IRONWORKS -> "+${level * 75} Eisen/Tag vor Arbeitskraftbonus"
    BuildingType.MARKET -> "+${level * 100} Gold/Tag vor Arbeitskraftbonus"
    BuildingType.BARRACKS -> "Kürzere Ausbildung und höhere Startmoral"
    BuildingType.WALL -> "Stärkere befestigte Verteidigung"
    BuildingType.TOWER -> "Zusätzlicher Verteidigungsbonus"
    BuildingType.PALACE -> "Entwickelt Siedlung und Herrschaft"
}

@Composable
private fun BuildingDetailRow(state:GameState, type: BuildingType, onBuild: () -> Unit) {
    val level = state.realm.level(type)
    val cost = GameEngine.buildingCost(state,type)
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Stufe $level · ${buildingEffect(type, level)}", color = Mist, fontSize = 12.sp)
                Text("${cost.gold} Gold · ${cost.wood} Holz · ${cost.stone} Stein", color = Gold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onBuild) { Text("Ausbauen") }
        }
    }
}

@Composable
internal fun ArmyScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var selectedCulture by remember { mutableStateOf<Culture?>(null) }
    var portraitTarget by remember { mutableStateOf<Long?>(null) }
    var allocationCommander by remember { mutableStateOf<Commander?>(null) }
    val context = LocalContext.current

    val commanderPortraitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = portraitTarget
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onState(customizeCommanderPortrait(state, target, uri.toString()))
            onNotice("Kommandanten-Portrait aktualisiert.")
        }
        portraitTarget = null
    }

    allocationCommander?.let { commander ->
        CommanderAllocationDialog(
            state = state,
            commander = commander,
            onDismiss = { allocationCommander = null },
            onSave = { requested ->
                val result = GameEngine.setCommanderAllocation(state, commander.id, requested)
                onState(result.state)
                onNotice(result.message)
                if (result.state !== state) allocationCommander = null
            }
        )
    }

    if (selectedCulture != null) {
        CultureTrainingView(
            state = state,
            culture = selectedCulture!!,
            onBack = { selectedCulture = null },
            onState = onState,
            onNotice = onNotice
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageTitle(
                "ARMEE",
                state.armySize.toString() + " Soldaten · " + state.trainingQueue.sumOf { it.amount } + " in Ausbildung"
            )
        }

        item {
            Surface(color = Color(0xFF162029), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Dein Heer auf einen Blick", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.homeArmySize} Soldaten daheim · ${state.awayArmySize} auf Mission. Nicht zugewiesene Soldaten stehen unter deinem direkten Oberkommando.",
                        color = Mist,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        items(listOf(Culture.HUMAN, Culture.WOOD_ELF, Culture.GOLD_ELF, Culture.WALL)) { culture ->
            CultureArmyCard(state, culture) { selectedCulture = culture }
        }


        if (UnitType.entries.none { state.soldiers(it) > 0 }) {
            item { EmptyCard("Noch keine fertig ausgebildeten Soldaten. Öffne oben eine Kategorie und starte eine Ausbildung.") }
        }

        item { SectionTitle("Kommandanten & Kontingente") }
        item {
            OutlinedButton(
                onClick = {
                    val result = GameEngine.promoteCommander(state)
                    onState(result.state)
                    onNotice(result.message)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Herausragenden Soldaten befördern · 250 Gold") }
        }

        if (state.commanders.isEmpty()) {
            item {
                EmptyCard(
                    "Ab 100 aktiven Soldaten kannst du einzelne Personen zu Kommandanten machen. Danach weist du ihnen konkrete Stückzahlen deiner Einheiten zu."
                )
            }
        } else {
            items(state.commanders) { commander ->
                CommanderOverviewCard(
                    commander = commander,
                    state = state,
                    onPortrait = {
                        portraitTarget = commander.id
                        commanderPortraitPicker.launch(arrayOf("image/*"))
                    },
                    onTrain = {
                        val result = GameEngine.trainCommander(state, commander.id)
                        onState(result.state)
                        onNotice(result.message)
                    },
                    onAssign = { allocationCommander = commander }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CultureArmyCard(state: GameState, culture: Culture, onClick: () -> Unit) {
    val recruits = ArmyEngine.recruitable(state, culture)
    val active = UnitType.entries.filter { it.culture == culture }.sumOf { state.soldiers(it) }
    val training = state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount }
    val unlocked = UnitType.entries.any { it.culture == culture && GameEngine.isUnitUnlocked(state, it) }

    Surface(
        onClick = onClick,
        enabled = unlocked,
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Color(0xFF33414B) else Color(0xFF252A2E))
    ) {
        Box(Modifier.fillMaxWidth().height(218.dp)) {
            CategoryArt(culture, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color(0xE7080B0E), Color(0x22080B0E)))
                )
            )
            Column(Modifier.align(Alignment.CenterStart).padding(18.dp).fillMaxWidth(0.72f)) {
                Text(culture.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(cultureSummary(culture), color = Mist, fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(active.toString() + " Soldaten", color = PaleGold, fontWeight = FontWeight.Bold)
                Text(recruits.toString() + " Rekruten · " + training + " in Ausbildung", color = Mist, fontSize = 12.sp)
                Text(UnitType.entries.filter { it.culture == culture && state.soldiers(it) > 0 }.joinToString(" · ") { "${it.label} ${state.soldiers(it)}" }.ifBlank { "Noch keine Truppen dieser Kultur" }, color = PaleGold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                if (unlocked) "DETAILS & AUSBILDUNG ›" else "GESPERRT",
                color = if (unlocked) Gold else Color.Gray,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp)
            )
        }
    }
}

@Composable
private fun CultureTrainingView(
    state: GameState,
    culture: Culture,
    onBack: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit
) {
    val available = ArmyEngine.recruitable(state, culture)
    val types = UnitType.entries.filter { it.culture == culture }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹ Zur Armeeübersicht")
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Box(Modifier.fillMaxWidth().height(205.dp)) {
                    CategoryArt(culture, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0x22000000), Color(0xED090D10)))
                        )
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text(culture.label.uppercase(), color = PaleGold, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text(
                            UnitType.entries.filter { it.culture == culture }.sumOf { state.soldiers(it) }.toString() + " Soldaten insgesamt",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(available.toString() + " Rekruten verfügbar", color = Mist, fontSize = 12.sp)
                    }
                }
            }
        }

        item { SectionTitle("Bestände & Ausbildung") }
        items(types) { type ->
            UnitTypeDetailCard(
                state = state,
                type = type,
                availableRecruits = available,
                onRepair = {
                    val result = ArmyEngine.repairEquipment(state, type)
                    onState(result.state)
                    onNotice(result.message)
                },
                onRecruit = { percent ->
                    val result = GameEngine.recruit(state, type, percent)
                    onState(result.state)
                    onNotice(result.message)
                }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun UnitTypeDetailCard(
    state: GameState,
    type: UnitType,
    availableRecruits: Int,
    onRepair: () -> Unit,
    onRecruit: (Int) -> Unit
) {
    val active = state.soldiers(type)
    val training = state.trainingQueue.filter { it.type == type }.sumOf { it.amount }
    val assigned = state.assigned(type)
    val unlocked = GameEngine.isUnitUnlocked(state, type)
    val pool = state.armyPools.firstOrNull { it.type == type }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Color(0xFF2D3942) else Color(0xFF25292C))
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(type.label, color = if (unlocked) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gesamt: " + active, color = PaleGold, fontWeight = FontWeight.Bold)
                Text("Kommandos: " + assigned, color = Mist, fontSize = 12.sp)
                Text("Training: " + training, color = Mist, fontSize = 12.sp)
            }
            Text("${state.homeSoldiers(type)} daheim · ${state.away(type)} unterwegs · ${state.directCommand(type)} direktes Oberkommando", color = Mist, fontSize = 12.sp)
            if (pool != null) {
                Text("Moral ${pool.morale}% · Erfahrung ${pool.experience}% · Ausrüstung ${pool.equipment}%", color = PaleGold, fontSize = 12.sp)
                if (pool.equipment < 100) {
                    val points = minOf(20, 100 - pool.equipment)
                    val ironCost = maxOf(1, (pool.soldiers.toLong() * points / 20).toInt())
                    OutlinedButton(onClick = onRepair, enabled = state.away(type) == 0 && state.battleSession?.isActive != true) {
                        Text("Ausrüstung +$points · ${ironCost * 2} Gold + $ironCost Eisen")
                    }
                }
            }
            Text(
                "Angriff " + type.attack + " · Verteidigung " + type.defense + " · Fernkampf " + type.ranged +
                        " · " + type.trainingDays + " Tage",
                color = Mist,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                type.goldCost.toString() + " Gold + " + type.ironCost + " Eisen je Rekrut",
                color = Gold,
                fontSize = 12.sp
            )

            if (unlocked) {
                Text(
                    "Neue Ausbildung aus " + availableRecruits + " Rekruten:",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 25, 50, 100).forEach { percent ->
                        val amount = if (availableRecruits <= 0) 0 else kotlin.math.ceil(availableRecruits * percent / 100.0).toInt()
                        OutlinedButton(
                            onClick = { onRecruit(percent) },
                            enabled = availableRecruits > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 7.dp, horizontal = 2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(percent.toString() + "%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(amount.toString(), fontSize = 12.sp, color = Mist)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommanderOverviewCard(
    commander: Commander,
    state: GameState,
    onPortrait: () -> Unit,
    onTrain: () -> Unit,
    onAssign: () -> Unit
) {
    val allocation = state.commanderAssignments.firstOrNull { it.commanderId == commander.id }
    val away = state.commanderAway(commander.id)
    Surface(color = Panel, shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (commander.portraitUri != null) {
                AsyncImage(
                    model = commander.portraitUri,
                    contentDescription = null,
                    modifier = Modifier.size(82.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painterResource(portraitResource(commander.portraitKey)),
                    null,
                    Modifier.size(82.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(commander.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(commander.rank + " · " + commander.culture.label + " · " + commander.trait, color = Gold, fontSize = 12.sp)
                Text(
                    "Führung " + commander.leadership + " · Taktik " + commander.tactics + " · Loyalität " + commander.loyalty,
                    color = Mist,
                    fontSize = 12.sp
                )
                Text(
                    if (allocation == null || allocation.total == 0) "Noch keine Truppen zugewiesen"
                    else allocation.total.toString() + " Soldaten unter seinem Kommando",
                    color = PaleGold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text("Schwert ${commander.sword} · Bogen ${commander.bow} · Belagerung ${commander.siege}", color = Mist, fontSize = 12.sp)
                if (away) Text("Unterwegs auf Mission", color = Gold, fontWeight = FontWeight.Bold)
                if (allocation != null && allocation.units.isNotEmpty()) {
                    Text(
                        allocation.units.joinToString(" · ") { it.type.label + " " + it.amount },
                        color = Color(0xFFAAB6BD),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAssign, enabled = !away, contentPadding = PaddingValues(0.dp)) {
                        Text("Truppen zuweisen", fontSize = 12.sp)
                    }
                    TextButton(onClick = onTrain, enabled = !away, contentPadding = PaddingValues(0.dp)) {
                        Text("Trainieren", fontSize = 12.sp)
                    }
                    TextButton(onClick = onPortrait, contentPadding = PaddingValues(0.dp)) {
                        Text("Bild", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommanderAllocationDialog(
    state: GameState,
    commander: Commander,
    onDismiss: () -> Unit,
    onSave: (List<UnitAllocation>) -> Unit
) {
    val types = UnitType.entries
    val fields = remember(commander.id, state.commanderAssignments) {
        mutableStateMapOf<UnitType, String>().apply {
            types.forEach { type ->
                this[type] = state.assignedTo(commander.id, type).toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Truppen an " + commander.name + " zuweisen")
                Text(
                    "Du verteilst konkrete Soldatenzahlen. Bereits an andere Kommandanten vergebene Soldaten sind hier nicht mehr frei.",
                    color = Mist,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                types.forEach { type ->
                    val total = state.soldiers(type)
                    val freeForThisCommander = GameEngine.freeSoldiersForCommander(state, commander.id, type)
                    val current = fields[type]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    Surface(color = Color(0xFF182129), shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(type.label, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "Gesamt $total · daheim ${state.homeSoldiers(type)} · unterwegs ${state.away(type)} · frei ${state.directCommand(type)} · aktuell ${state.assignedTo(commander.id, type)} zugewiesen · maximal $freeForThisCommander",
                                color = Mist,
                                fontSize = 12.sp
                            )
                            OutlinedTextField(
                                value = fields[type] ?: "0",
                                onValueChange = { raw ->
                                    fields[type] = raw.filter(Char::isDigit).take(10)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Zuweisen") },
                                suffix = { Text("Soldaten") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(0 to "0", 25 to "25%", 50 to "50%", 75 to "75%", 100 to "ALLE").forEach { (pct, label) ->
                                    TextButton(
                                        onClick = {
                                            val amount = if (pct == 0) 0 else (freeForThisCommander * pct / 100.0).toInt()
                                            fields[type] = amount.toString()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp)
                                    ) { Text(label, fontSize = 12.sp) }
                                }
                            }
                            if (current > freeForThisCommander) {
                                Text("Zu hoch: maximal " + freeForThisCommander, color = Danger, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !state.commanderAway(commander.id) && types.all { (fields[it].orEmpty().ifBlank { "0" }.toIntOrNull() ?: Int.MAX_VALUE) <= GameEngine.freeSoldiersForCommander(state, commander.id, it) }, onClick = {
                val requested = types.mapNotNull { type ->
                    val amount = fields[type]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    if (amount > 0) UnitAllocation(type, amount) else null
                }
                onSave(requested)
            }) { Text("Zuweisung speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun cultureSummary(culture: Culture): String = when (culture) {
    Culture.HUMAN -> "Vielseitig, günstig und zahlreich. Gute Basis für große Heere."
    Culture.WOOD_ELF -> "Schnell, präzise und besonders stark im Fernkampf."
    Culture.GOLD_ELF -> "Seltene Elite. Teuer und langsam ersetzbar, dafür extrem stark."
    Culture.WALL -> "Disziplinierte Spezialkorps mit Fernkampf, Verteidigung und Artillerie."
}
