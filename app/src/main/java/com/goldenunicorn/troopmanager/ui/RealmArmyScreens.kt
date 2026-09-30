package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
                state.armySize.toString() + " aktiv · " + state.trainingQueue.sumOf { it.amount } + " in Ausbildung"
            )
        }
        item {
            Text(
                "Wähle zuerst eine Kategorie. Danach entscheidest du Einheitentyp und 10 / 25 / 50 / 100 % der aktuell verfügbaren Rekruten.",
                color = Mist,
                fontSize = 12.sp
            )
        }

        items(listOf(Culture.HUMAN, Culture.WOOD_ELF, Culture.GOLD_ELF, Culture.WALL)) { culture ->
            CultureArmyCard(state, culture) { selectedCulture = culture }
        }

        if (state.trainingQueue.isNotEmpty()) {
            item { SectionTitle("Aktuelle Ausbildung") }
            items(state.trainingQueue) { order ->
                CompactCard(
                    order.amount.toString() + " " + order.type.label,
                    "noch " + order.daysRemaining + " Tage"
                )
            }
        }

        item { SectionTitle("Fertige Regimenter") }
        if (state.regiments.isEmpty()) {
            item { EmptyCard("Noch keine fertigen Regimenter. Öffne oben eine Kategorie und beginne die Ausbildung.") }
        } else {
            items(state.regiments) { regiment ->
                RegimentSummaryCard(regiment, state)
            }
        }

        item { SectionTitle("Kommandanten") }
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
            item { EmptyCard("Wichtige Soldaten werden erst bei einer Beförderung zu individuellen Charakteren mit Bild, Stats und eigener Entwicklung.") }
        } else {
            items(state.commanders) { commander ->
                CommanderManageCard(
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
                    onAssign = { regimentId ->
                        val result = GameEngine.assignCommander(state, commander.id, regimentId)
                        onState(result.state)
                        onNotice(result.message)
                    }
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CultureArmyCard(state: GameState, culture: Culture, onClick: () -> Unit) {
    val recruits = state.population.recruits(culture)
    val active = state.regiments.filter { it.type.culture == culture }.sumOf { it.soldiers }
    val training = state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount }
    val unlocked = UnitType.entries.any { it.culture == culture && GameEngine.isUnitUnlocked(state, it) }

    Surface(
        onClick = onClick,
        enabled = unlocked,
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Color(0xFF33414B) else Color(0xFF252A2E))
    ) {
        Box(Modifier.fillMaxWidth().height(176.dp)) {
            CategoryArt(culture, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color(0xE7080B0E), Color(0x33080B0E)))
                )
            )
            Column(Modifier.align(Alignment.CenterStart).padding(18.dp).fillMaxWidth(0.7f)) {
                Text(culture.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(cultureSummary(culture), color = Mist, fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(recruits.toString() + " Rekruten", color = PaleGold, fontWeight = FontWeight.Bold)
                Text(active.toString() + " aktiv · " + training + " in Ausbildung", color = Mist, fontSize = 11.sp)
            }
            Text(
                if (unlocked) "ÖFFNEN ›" else "GESPERRT",
                color = if (unlocked) Gold else Color.Gray,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
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
                        Text(available.toString() + " verfügbare Rekruten", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        Text(cultureSummary(culture), color = Mist, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Surface(color = Color(0xFF162029), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Die Prozentzahl bezieht sich immer auf die aktuell verfügbaren Rekruten dieser Kategorie. 50 % bei 120 Rekruten bedeutet also 60 Soldaten.",
                    color = Mist,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(13.dp)
                )
            }
        }

        items(types) { type ->
            TrainingUnitCard(
                type = type,
                available = available,
                unlocked = GameEngine.isUnitUnlocked(state, type),
                onRecruit = { percent ->
                    val result = GameEngine.recruit(state, type, percent)
                    onState(result.state)
                    onNotice(result.message)
                }
            )
        }

        if (state.trainingQueue.any { it.type.culture == culture }) {
            item { SectionTitle("Bereits in Ausbildung") }
            items(state.trainingQueue.filter { it.type.culture == culture }) { order ->
                CompactCard(
                    order.amount.toString() + " " + order.type.label,
                    "noch " + order.daysRemaining + " Tage"
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrainingUnitCard(type: UnitType, available: Int, unlocked: Boolean, onRecruit: (Int) -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Color(0xFF2D3942) else Color(0xFF25292C))
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(type.label, color = if (unlocked) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Angriff " + type.attack + " · Verteidigung " + type.defense + " · Fernkampf " + type.ranged,
                        color = Gold,
                        fontSize = 11.sp
                    )
                    Text(
                        type.trainingDays.toString() + " Tage · " + type.goldCost + " Gold + " + type.ironCost + " Eisen je Rekrut",
                        color = Mist,
                        fontSize = 11.sp
                    )
                }
                if (!unlocked) Text("🔒")
            }

            if (unlocked) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 25, 50, 100).forEach { percent ->
                        val amount = if (available <= 0) 0 else kotlin.math.ceil(available * percent / 100.0).toInt()
                        OutlinedButton(
                            onClick = { onRecruit(percent) },
                            enabled = available > 0,
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
private fun RegimentSummaryCard(regiment: Regiment, state: GameState) {
    val commander = state.commanders.firstOrNull { it.id == regiment.commanderId }
    Surface(color = Panel, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(regiment.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        regiment.soldiers.toString() + "/" + regiment.maxSoldiers +
                                " · Moral " + regiment.morale + "% · Erfahrung " + regiment.experience + "% · Kraft " + regiment.power,
                        color = Mist,
                        fontSize = 11.sp
                    )
                }
                UnitArt(regiment.type.culture, Modifier.size(46.dp))
            }
            LinearProgressIndicator(
                progress = { regiment.soldiers.toFloat() / regiment.maxSoldiers.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                color = Gold,
                trackColor = Color(0xFF2A333A)
            )
            Text(
                if (commander != null) "Kommandant: " + commander.name + " · " + commander.rank else "Kein Kommandant zugewiesen",
                color = if (commander != null) PaleGold else Color(0xFF88959D),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun CommanderManageCard(
    commander: Commander,
    state: GameState,
    onPortrait: () -> Unit,
    onTrain: () -> Unit,
    onAssign: (Long) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val matching = state.regiments.filter { it.type.culture == commander.culture }
    val assigned = state.regiments.firstOrNull { it.commanderId == commander.id }

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
                Text(commander.rank + " · " + commander.culture.label + " · " + commander.trait, color = Gold, fontSize = 11.sp)
                Text(
                    "Führung " + commander.leadership + " · Taktik " + commander.tactics + " · Loyalität " + commander.loyalty,
                    color = Mist,
                    fontSize = 11.sp
                )
                Text(
                    if (assigned != null) "Führt: " + assigned.name else "Noch keinem Regiment zugewiesen",
                    color = PaleGold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onTrain, contentPadding = PaddingValues(0.dp)) { Text("Trainieren", fontSize = 11.sp) }
                    TextButton(onClick = onPortrait, contentPadding = PaddingValues(0.dp)) { Text("Bild", fontSize = 11.sp) }
                    Box {
                        TextButton(
                            onClick = { menuOpen = true },
                            enabled = matching.isNotEmpty(),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("Zuweisen", fontSize = 11.sp) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            matching.forEach { regiment ->
                                DropdownMenuItem(
                                    text = { Text(regiment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        menuOpen = false
                                        onAssign(regiment.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
