package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.renameSettlement
import com.goldenunicorn.troopmanager.engine.customizeCommanderPortrait
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun RealmScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
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
                    Box(
                        Modifier.fillMaxSize().padding(0.dp)
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
        item { ResourceStrip(state.resources) }
        item { DailyEconomyCard(state) }
        item { FortressMap(state) }
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
            BuildingDetailRow(type, state.realm.level(type)) {
                val result = GameEngine.build(state, type)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { SectionTitle("Militär & Verteidigung") }
        items(listOf(BuildingType.BARRACKS, BuildingType.WALL, BuildingType.TOWER)) { type ->
            BuildingDetailRow(type, state.realm.level(type)) {
                val result = GameEngine.build(state, type)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { SectionTitle("Herrschaft") }
        item {
            BuildingDetailRow(BuildingType.PALACE, state.realm.level(BuildingType.PALACE)) {
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
    val farm = state.realm.level(BuildingType.FARM)
    val saw = state.realm.level(BuildingType.SAWMILL)
    val quarry = state.realm.level(BuildingType.QUARRY)
    val iron = state.realm.level(BuildingType.IRONWORKS)
    val market = state.realm.level(BuildingType.MARKET)
    val territory = state.realm.territory
    val food = 28 + farm * 35 + territory * 12 - state.armySize / 40
    val wood = 10 + saw * 28
    val stone = 4 + quarry * 30 + territory * 3
    val ironGain = 4 + iron * 18
    val gold = 18 + market * 16 + territory * 10

    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Tagesproduktion", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "+" + gold + " Gold · " + (if (food >= 0) "+" else "") + food + " Nahrung · +" +
                        wood + " Holz · +" + stone + " Stein · +" + ironGain + " Eisen",
                color = Gold,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            Text(
                if (quarry == 0) "Tipp: Baue einen Steinbruch. Ohne ihn wächst dein Steinvorrat nur sehr langsam."
                else "Steinbruch Stufe " + quarry + " liefert +" + (quarry * 30) + " Stein pro Tag.",
                color = Mist,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun BuildingDetailRow(type: BuildingType, level: Int, onBuild: () -> Unit) {
    val effect = when (type) {
        BuildingType.FARM -> "+" + (level * 35) + " Nahrung/Tag"
        BuildingType.SAWMILL -> "+" + (level * 28) + " Holz/Tag"
        BuildingType.QUARRY -> "+" + (level * 30) + " Stein/Tag"
        BuildingType.IRONWORKS -> "+" + (level * 18) + " Eisen/Tag"
        BuildingType.MARKET -> "+" + (level * 16) + " Gold/Tag"
        BuildingType.BARRACKS -> "kürzere Ausbildung, höhere Startmoral"
        BuildingType.WALL -> "stärkere befestigte Verteidigung"
        BuildingType.TOWER -> "zusätzlicher Verteidigungsbonus"
        BuildingType.PALACE -> "entwickelt Siedlung und Herrschaft"
    }
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Stufe " + level + " · " + effect, color = Mist, fontSize = 11.sp)
            }
            OutlinedButton(onClick = onBuild) { Text("Ausbauen", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun BuildingRow(type: BuildingType, level: Int, onBuild: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Stufe " + level, color = Gold, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onBuild) { Text("Ausbauen", fontSize = 12.sp) }
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
                        "Keine künstliche Regimenter-Liste mehr: Hier siehst du die echten Gesamtzahlen pro Einheitentyp. Kommandanten erhalten daraus konkrete Kontingente.",
                        color = Mist,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        items(listOf(Culture.HUMAN, Culture.WOOD_ELF, Culture.GOLD_ELF, Culture.WALL)) { culture ->
            CultureArmyCard(state, culture) { selectedCulture = culture }
        }

        item { SectionTitle("Gesamtbestand nach Einheit") }
        items(UnitType.entries.filter { state.soldiers(it) > 0 || state.trainingQueue.any { q -> q.type == it } }) { type ->
            AggregatedUnitRow(state, type)
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
    val recruits = state.population.recruits(culture)
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
        Box(Modifier.fillMaxWidth().height(188.dp)) {
            CategoryArt(culture, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color(0xE7080B0E), Color(0x22080B0E)))
                )
            )
            Column(Modifier.align(Alignment.CenterStart).padding(18.dp).fillMaxWidth(0.72f)) {
                Text(culture.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(cultureSummary(culture), color = Mist, fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(active.toString() + " Soldaten", color = PaleGold, fontWeight = FontWeight.Bold)
                Text(recruits.toString() + " Rekruten · " + training + " in Ausbildung", color = Mist, fontSize = 11.sp)
            }
            Text(
                if (unlocked) "DETAILS & AUSBILDUNG ›" else "GESPERRT",
                color = if (unlocked) Gold else Color.Gray,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
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
    val available = state.population.recruits(culture)
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
                        Text(culture.label.uppercase(), color = PaleGold, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text(
                            UnitType.entries.filter { it.culture == culture }.sumOf { state.soldiers(it) }.toString() + " aktive Soldaten",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(available.toString() + " Rekruten verfügbar", color = Mist, fontSize = 12.sp)
                    }
                }
            }
        }

        items(types) { type ->
            UnitTypeDetailCard(
                state = state,
                type = type,
                availableRecruits = available,
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
    onRecruit: (Int) -> Unit
) {
    val active = state.soldiers(type)
    val training = state.trainingQueue.filter { it.type == type }.sumOf { it.amount }
    val assigned = state.assigned(type)
    val unlocked = GameEngine.isUnitUnlocked(state, type)

    Surface(
        color = Panel,
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Color(0xFF2D3942) else Color(0xFF25292C))
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(type.label, color = if (unlocked) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Aktiv: " + active, color = PaleGold, fontWeight = FontWeight.Bold)
                Text("Kommandos: " + assigned, color = Mist, fontSize = 11.sp)
                Text("Training: " + training, color = Mist, fontSize = 11.sp)
            }
            Text(
                "Angriff " + type.attack + " · Verteidigung " + type.defense + " · Fernkampf " + type.ranged +
                        " · " + type.trainingDays + " Tage",
                color = Mist,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                type.goldCost.toString() + " Gold + " + type.ironCost + " Eisen je Rekrut",
                color = Gold,
                fontSize = 11.sp
            )

            if (unlocked) {
                Text(
                    "Neue Ausbildung aus " + availableRecruits + " Rekruten:",
                    color = Color.White,
                    fontSize = 11.sp,
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
                                Text(percent.toString() + "%", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(amount.toString(), fontSize = 9.sp, color = Mist)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AggregatedUnitRow(state: GameState, type: UnitType) {
    val active = state.soldiers(type)
    val assigned = state.assigned(type)
    val training = state.trainingQueue.filter { it.type == type }.sumOf { it.amount }
    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            UnitArt(type.culture, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(type.label, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    active.toString() + " aktiv · " + assigned + " Kommandanten zugewiesen · " + training + " in Ausbildung",
                    color = Mist,
                    fontSize = 11.sp
                )
            }
            Text(active.toString(), color = PaleGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
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
                Text(commander.rank + " · " + commander.culture.label + " · " + commander.trait, color = Gold, fontSize = 11.sp)
                Text(
                    "Führung " + commander.leadership + " · Taktik " + commander.tactics + " · Loyalität " + commander.loyalty,
                    color = Mist,
                    fontSize = 11.sp
                )
                Text(
                    if (allocation == null || allocation.total == 0) "Noch keine Truppen zugewiesen"
                    else allocation.total.toString() + " Soldaten unter seinem Kommando",
                    color = PaleGold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (allocation != null && allocation.units.isNotEmpty()) {
                    Text(
                        allocation.units.joinToString(" · ") { it.type.label + " " + it.amount },
                        color = Color(0xFFAAB6BD),
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAssign, contentPadding = PaddingValues(0.dp)) {
                        Text("Truppen zuweisen", fontSize = 11.sp)
                    }
                    TextButton(onClick = onTrain, contentPadding = PaddingValues(0.dp)) {
                        Text("Trainieren", fontSize = 11.sp)
                    }
                    TextButton(onClick = onPortrait, contentPadding = PaddingValues(0.dp)) {
                        Text("Bild", fontSize = 11.sp)
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
    val types = UnitType.entries.filter { it.culture == commander.culture }
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
                    fontSize = 11.sp
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
                                total.toString() + " insgesamt · " + freeForThisCommander + " für diesen Kommandanten verfügbar",
                                color = Mist,
                                fontSize = 10.sp
                            )
                            OutlinedTextField(
                                value = fields[type] ?: "0",
                                onValueChange = { raw ->
                                    fields[type] = raw.filter(Char::isDigit).take(6)
                                },
                                label = { Text("Zuweisen") },
                                suffix = { Text("Soldaten") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(0 to "0", 25 to "25%", 50 to "50%", 100 to "ALLE").forEach { (pct, label) ->
                                    TextButton(
                                        onClick = {
                                            val amount = if (pct == 0) 0 else (freeForThisCommander * pct / 100.0).toInt()
                                            fields[type] = amount.toString()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(2.dp)
                                    ) { Text(label, fontSize = 10.sp) }
                                }
                            }
                            if (current > freeForThisCommander) {
                                Text("Zu hoch: maximal " + freeForThisCommander, color = Danger, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
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

@Composable
private fun RecruitTypeRow(
    type: UnitType,
    selected: Boolean,
    unlocked: Boolean,
    recruits: Int,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        enabled = unlocked,
        color = if (selected) Color(0xFF25323B) else Panel,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Gold) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UnitArt(type.culture, Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(type.label, color = if (unlocked) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                Text(type.culture.label + " · " + recruits + " Rekruten", color = Mist, fontSize = 11.sp)
                Text(
                    "A " + type.attack + " · V " + type.defense + " · F " + type.ranged,
                    color = Gold,
                    fontSize = 11.sp
                )
            }
            if (!unlocked) Text("🔒")
        }
    }
}

@Composable
private fun CommanderCard(commander: Commander, onPortrait: () -> Unit, onTrain: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
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
                Text(
                    commander.rank + " · " + commander.culture.label + " · " + commander.trait,
                    color = Gold,
                    fontSize = 11.sp
                )
                Text(
                    "Führung " + commander.leadership + " · Taktik " + commander.tactics +
                            " · Loyalität " + commander.loyalty,
                    color = Mist,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onTrain, contentPadding = PaddingValues(0.dp)) {
                        Text("Trainieren · 120 Gold", fontSize = 12.sp)
                    }
                    TextButton(onClick = onPortrait, contentPadding = PaddingValues(0.dp)) {
                        Text("Bild ändern", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
