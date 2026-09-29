package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun RealmScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
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
                }
            }
        }
        item { SectionTitle("Bauen & erweitern") }
        items(BuildingType.entries) { type ->
            BuildingRow(type, state.realm.level(type)) {
                val result = GameEngine.build(state, type)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
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
    var selectedType by remember { mutableStateOf(UnitType.HUMAN_ARCHER) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageTitle("ARMEE", state.armySize.toString() + " aktiv · " + state.trainingQueue.sumOf { it.amount } + " in Ausbildung") }
        item {
            StatGrid(
                listOf(
                    "Menschen" to state.population.humanRecruits.toString(),
                    "Waldelben" to state.population.woodElfRecruits.toString(),
                    "Goldelben" to state.population.goldElfRecruits.toString(),
                    "Mauerlegion" to state.population.wallRecruits.toString()
                )
            )
        }
        item { SectionTitle("Rekruten zuweisen") }
        items(UnitType.entries) { type ->
            RecruitTypeRow(
                type = type,
                selected = selectedType == type,
                unlocked = GameEngine.isUnitUnlocked(state, type),
                recruits = state.population.recruits(type.culture),
                onSelect = { selectedType = type }
            )
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(selectedType.label, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Verfügbar: " + state.population.recruits(selectedType.culture) +
                                " · " + selectedType.trainingDays + " Tage · " +
                                selectedType.goldCost + " Gold / " + selectedType.ironCost + " Eisen pro Rekrut",
                        color = Mist,
                        fontSize = 12.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 25, 50, 100).forEach { percent ->
                            OutlinedButton(
                                onClick = {
                                    val result = GameEngine.recruit(state, selectedType, percent)
                                    onState(result.state)
                                    onNotice(result.message)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(4.dp)
                            ) { Text(percent.toString() + "%", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

        if (state.trainingQueue.isNotEmpty()) {
            item { SectionTitle("Ausbildung") }
            items(state.trainingQueue) { order ->
                CompactCard(
                    order.amount.toString() + " " + order.type.label,
                    "Noch " + order.daysRemaining + " Tage"
                )
            }
        }

        item { SectionTitle("Regimenter") }
        if (state.regiments.isEmpty()) {
            item { EmptyCard("Noch keine fertigen Regimenter. Rekrutiere Soldaten und lasse die Ausbildung verstreichen.") }
        }
        items(state.regiments) { reg ->
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        reg.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        reg.soldiers.toString() + "/" + reg.maxSoldiers +
                                " · Moral " + reg.morale + "% · Erfahrung " + reg.experience +
                                "% · Kampfkraft " + reg.power,
                        color = Mist,
                        fontSize = 12.sp
                    )
                    LinearProgressIndicator(
                        progress = { reg.soldiers.toFloat() / reg.maxSoldiers.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = Gold,
                        trackColor = Color(0xFF2A333A)
                    )
                }
            }
        }

        item { SectionTitle("Individuelle Kommandanten") }
        item {
            OutlinedButton(
                onClick = {
                    val result = GameEngine.promoteCommander(state)
                    onState(result.state)
                    onNotice(result.message)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Soldaten hervorheben · 250 Gold") }
        }
        if (state.commanders.isEmpty()) {
            item { EmptyCard("Einzelne Soldaten werden erst bei der Beförderung zu persistenten Charakteren mit Bild und Stats.") }
        }
        items(state.commanders) { commander ->
            CommanderCard(commander) {
                val result = GameEngine.trainCommander(state, commander.id)
                onState(result.state)
                onNotice(result.message)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
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
private fun CommanderCard(commander: Commander, onTrain: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(portraitResource(commander.portraitKey)),
                null,
                Modifier.size(82.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
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
                TextButton(onClick = onTrain, contentPadding = PaddingValues(0.dp)) {
                    Text("Trainieren · 120 Gold", fontSize = 12.sp)
                }
            }
        }
    }
}
