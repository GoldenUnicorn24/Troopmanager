package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.engine.BattleEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.MissionEngine
import com.goldenunicorn.troopmanager.engine.ProgressionEngine
import com.goldenunicorn.troopmanager.engine.RelationshipEngine
import com.goldenunicorn.troopmanager.engine.RegionEngine
import com.goldenunicorn.troopmanager.engine.customizeCompanion
import com.goldenunicorn.troopmanager.engine.customizePlayer
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun WorldScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var selectedRegionId by remember { mutableStateOf(state.regions.firstOrNull()?.id) }
    var missionRegion by remember { mutableStateOf<WorldRegion?>(null) }
    var battleSetup by remember { mutableStateOf(false) }
    val selectedRegion = state.regions.firstOrNull { it.id == selectedRegionId }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTitle("WELT & MISSIONEN", "${state.regions.count { it.owned }} eigene Regionen · Ruhm ${state.renown} · Bedrohung ${state.realm.threat}%") }
        state.invasion?.let { invasion ->
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Danger)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(invasion.enemy.label, color = Danger, fontWeight = FontWeight.Bold)
                        Text(if (invasion.arrivalDay > state.day) "Ankunft in ${invasion.arrivalDay - state.day} Tagen" else "Der Angriff steht vor den Toren!", color = Color.White)
                        Text("${state.invasionStrengthEstimate()} Feinde · Mauer ${state.realm.wallIntegrity}%", color = Mist)
                        if (invasion.devices.isNotEmpty()) Text(invasion.devices.joinToString(" · ") { it.label }, color = Mist, fontSize = 12.sp)
                        SmallAction("Mauern reparieren") {
                            val result = GameEngine.repairWall(state); onState(result.state); onNotice(result.message)
                        }
                        OutlinedButton(enabled = !invasion.alliesRequested, onClick = {
                            val result = GameEngine.requestAllies(state); onState(result.state); onNotice(result.message)
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (invasion.alliesRequested) "Verbündete angefordert" else "Verbündete anfordern") }
                        state.activeMissions.filter { it.status == MissionStatus.ACTIVE }.forEach { mission ->
                            SmallAction("${mission.missionType.label}: zurückrufen (${mission.total})") {
                                val result = MissionEngine.recall(state, mission.id); onState(result.state); onNotice(result.message)
                            }
                        }
                        Text("Truppen zuweisen und Ausbildung starten: im Armeemenü. Rückgerufene Soldaten benötigen Heimreisezeit.", color = Mist, fontSize = 12.sp)
                    }
                }
            }
        }
        item { SectionTitle("Regionen") }
        item {
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(20.dp)).background(Panel)
            ) {
                val positions = listOf(0.08f to 0.63f, 0.50f to 0.65f, 0.08f to 0.14f, 0.60f to 0.15f, 0.40f to 0.42f, 0.64f to 0.39f, 0.12f to 0.39f)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFF17242A), Color(0xFF26372F), Color(0xFF151B20))
                        )
                    )
                    repeat(6) { i ->
                        val cx = size.width * (.15f + (i * .139f % .72f))
                        val cy = size.height * (.16f + ((i * 31) % 61) / 100f)
                        drawOval(
                            color = if (i % 2 == 0) Color(0xFF405443) else Color(0xFF5C5741),
                            topLeft = Offset(cx - size.width * .16f, cy - size.height * .10f),
                            size = Size(size.width * .32f, size.height * .20f),
                        )
                    }
                    val river = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * .02f, size.height * .27f)
                        cubicTo(
                            size.width * .24f, size.height * .20f,
                            size.width * .39f, size.height * .47f,
                            size.width * .56f, size.height * .38f,
                        )
                        cubicTo(
                            size.width * .75f, size.height * .27f,
                            size.width * .84f, size.height * .57f,
                            size.width * .99f, size.height * .50f,
                        )
                    }
                    drawPath(river, Color(0xFF426B79), style = androidx.compose.ui.graphics.drawscope.Stroke(11.dp.toPx()))
                    drawPath(river, Color(0xFF8AA8B0).copy(alpha = .25f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    positions.take(state.regions.take(7).size).zipWithNext().forEach { (a, b) ->
                        drawLine(
                            PaleGold.copy(alpha = .34f),
                            Offset(size.width * a.first, size.height * a.second),
                            Offset(size.width * b.first, size.height * b.second),
                            2.dp.toPx(),
                        )
                    }
                    state.regions.take(7).forEachIndexed { index, region ->
                        val (x, y) = positions[index % positions.size]
                        val center = Offset(size.width * x, size.height * y)
                        val color = if (region.owned) Success else ModernBlue
                        drawCircle(color.copy(alpha = .14f), 28.dp.toPx(), center)
                        drawCircle(color.copy(alpha = .65f), 21.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    }
                }
                state.regions.take(7).forEachIndexed { index, region ->
                    val (x, y) = positions[index % positions.size]
                    Surface(onClick = { selectedRegionId = region.id }, modifier = Modifier.offset(x = maxWidth * x, y = maxHeight * y).heightIn(min = 48.dp),
                        color = if (selectedRegionId == region.id) Gold else Panel.copy(alpha = 0.92f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (region.owned) Success else Gold.copy(alpha = .6f))) {
                        Text(region.name, color = if (selectedRegionId == region.id) Ink else Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(max = 94.dp).padding(7.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.regions.take(7).forEach { region ->
                    FilterChip(selected = selectedRegionId == region.id, onClick = { selectedRegionId = region.id }, label = { Text(region.name) })
                }
            }
        }
        if (state.regions.size > 7) item {
            Column {
                Text("Weitere eigene Gebiete",color=PaleGold,fontWeight=FontWeight.Bold)
                state.regions.drop(7).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach { region ->
                            FilterChip(selected=selectedRegionId==region.id,onClick={selectedRegionId=region.id},label={Text(region.name)},modifier=Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        selectedRegion?.let { region ->
            item {
                Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(region.name, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("${region.type.label} · ${if (region.owned) "Unter deinem Schutz" else if (RegionEngine.canAcquire(region)) "Unabhängig" else "Gefährliches Gebiet"}", color = Mist, fontSize = 12.sp)
                        val missionsHere = state.activeMissions.filter { it.regionId == region.id && it.status.isAway }
                        if (missionsHere.isNotEmpty()) Text("${missionsHere.size} Mission(en) unterwegs · ${missionsHere.sumOf { it.total }} Soldaten", color = Gold, fontSize = 12.sp)
                        if (RegionEngine.bonusDescription(region).isNotBlank()) Text(RegionEngine.bonusDescription(region), color = if (region.owned) Success else Mist, fontSize = 12.sp)
                        region.mission?.let { mission -> GoldButton("${mission.label} vorbereiten", { missionRegion = region }, Modifier.fillMaxWidth()) }
                        if (RegionEngine.canAcquire(region)) {
                            OutlinedButton(enabled = state.resources.gold >= RegionEngine.purchaseCost(region) && state.battleSession?.isActive != true,
                                onClick = { val result = RegionEngine.buy(state, region.id); onState(result.state); onNotice(result.message) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Gebiet kaufen · ${RegionEngine.purchaseCost(region)} Gold")
                            }
                            OutlinedButton(enabled = RegionEngine.diplomacy(state) >= 35 && state.resources.gold >= RegionEngine.diplomaticCost(state, region) && state.battleSession?.isActive != true,
                                onClick = { val result = RegionEngine.negotiate(state, region.id); onState(result.state); onNotice(result.message) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Diplomatisch übernehmen · ${RegionEngine.diplomaticCost(state, region)} Gold")
                            }
                            Text("Verhandeln: mindestens 35 Diplomatie. Gefährtin unterstützt ab 45 Vertrauen.", color = Mist, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item { StatGrid(listOf("Zu Hause" to "${state.homeArmySize} Soldaten", "Unterwegs" to "${state.awayArmySize} Soldaten")) }
        item { SectionTitle("Missionen") }
        item { MissionsSection(state, onState, onNotice) }
        state.battleSession?.takeIf { !it.isActive }?.let { battle ->
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (battle.status == BattleStatus.VICTORY) "Sieg auf dem Schlachtfeld" else "Schlacht verloren", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("${battle.ownRemaining} von ${battle.ownStart} Soldaten zurück · ${battle.ownStart - battle.ownRemaining} Verluste", color = Mist)
                        Text("Gegner: ${battle.enemyRemaining} verblieben · ${battle.lootGold} Gold Beute", color = Gold, fontSize = 12.sp)
                        battle.log.takeLast(4).forEach { Text("${it.minute}′ ${it.text}", color = Mist, fontSize = 12.sp) }
                        SmallAction("Bericht schließen") { onState(state.copy(battleSession = null)) }
                    }
                }
            }
        }
        item { SectionTitle("Schlachtaufstellung") }
        item {
            EmptyCard(if (state.world.initialized)
                "Entsende dein Feldheer über die Kampagnenkarte. Trifft es einen tatsächlichen Gegner, beginnt die Schlacht. Vor dem ersten Gefecht verteilst du die angereisten Kommandos auf Flügel, Zentrum und Reserve. Invasionen erreichen die Stadt über ihren Marschweg."
                else "Verteile verfügbare Kommandos auf Flügel, Zentrum und Reserve. Soldaten auf Missionen fehlen. Die Schlacht entwickelt sich Schritt für Schritt mit deinen Entscheidungen.")
        }
        if (!state.world.initialized) item { GoldButton("Schlacht vorbereiten", { battleSetup = true }, Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(16.dp)) }
    }
    missionRegion?.let { region -> region.mission?.let { mission ->
        MissionPreparationDialog(state, mission, region, { missionRegion = null }, onState, onNotice)
    } }
    if (battleSetup) BattleSetupDialog(state, { battleSetup = false }, onState, onNotice)
}

@Composable
internal fun BattleSetupDialog(state: GameState, onDismiss: () -> Unit, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val session = state.battleSession?.takeIf { it.isActive }
    val editingFormation = session?.minute == 0
    val invasion = state.invasion?.takeIf { it.arrivalDay <= state.day }
    val initialDeployments = remember(session?.seed) {
        if (session == null) BattleEngine.defaultDeployments(state)
        else session.contingents.groupBy { it.commanderId to it.section }.map { (key, contingents) ->
            BattleDeployment(key.first, key.second, contingents.groupBy { it.type }.map { (type, units) ->
                UnitAllocation(type, units.sumOf { it.soldiers.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }.filter { it.amount > 0 })
        }
    }
    var enemy by remember { mutableStateOf(session?.enemy ?: invasion?.enemy ?: EnemyType.ORC) }
    var tactic by remember { mutableStateOf(session?.tactic ?: Tactic.HOLD) }
    val commanders = state.commanders.filterNot { state.commanderAway(it.id) }
    var sections by remember {
        mutableStateOf(commanders.associate { commander ->
            commander.id to (initialDeployments.firstOrNull { it.commanderId == commander.id }?.section ?: BattleSection.CENTER)
        })
    }
    var directCounts by remember {
        mutableStateOf(UnitType.entries.associateWith { type ->
            BattleSection.entries.associateWith { section ->
                initialDeployments.filter { it.commanderId == null && it.section == section }
                    .sumOf { deployment -> deployment.units.filter { it.type == type }.sumOf { it.amount.toLong() } }
                    .coerceIn(0L, state.directCommand(type).toLong()).toInt()
            }
        })
    }
    val deployments = buildList {
        commanders.forEach { commander ->
            val units = UnitType.entries.mapNotNull { type ->
                val count = state.assignedTo(commander.id, type).coerceAtMost(state.homeSoldiers(type))
                if (count > 0) UnitAllocation(type, count) else null
            }
            if (units.isNotEmpty()) add(BattleDeployment(commander.id, sections[commander.id] ?: BattleSection.CENTER, units))
        }
        BattleSection.entries.forEach { section ->
            val units = UnitType.entries.mapNotNull { type ->
                val count = directCounts[type]?.get(section) ?: 0
                if (count > 0) UnitAllocation(type, count) else null
            }
            if (units.isNotEmpty()) add(BattleDeployment(null, section, units))
        }
    }
    val total = deployments.sumOf { deployment -> deployment.units.sumOf { it.amount.toLong() } }
    val validDirectCounts = UnitType.entries.all { type ->
        (directCounts[type]?.values?.sumOf { it.toLong() } ?: 0L) <= state.directCommand(type).toLong()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (editingFormation) "Aufstellung ändern" else "Schlacht vorbereiten") }, text = {
        LazyColumn(Modifier.heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (session != null) {
                    Text("${session.enemy.label} · ${session.enemyStart} Gegner", color = Danger)
                    Text("Taktik: ${session.tactic.label}", color = Gold)
                } else {
                    if (invasion == null) SelectionMenu("Gegner", enemy.label, EnemyType.entries, { it.label }) { enemy = it }
                    else Text("Festungsverteidigung: ${invasion.enemy.label} · ${state.invasionStrengthEstimate()} Gegner", color = Danger)
                    SelectionMenu("Taktik", tactic.label, Tactic.entries, { it.label }) { tactic = it }
                }
                Text("${state.awayArmySize} Soldaten unterwegs und nicht verfügbar.", color = Mist, fontSize = 12.sp)
                if (editingFormation) Text("Vor dem ersten Gefecht kannst du deine Aufstellung anpassen.", color = Mist, fontSize = 12.sp)
            }
            item {
                FormationZones(deployments, state)
            }
            commanders.forEach { commander -> item {
                val count = UnitType.entries.sumOf { state.assignedTo(commander.id, it).toLong() }
                Text("${commander.name} · $count Soldaten", color = PaleGold, fontWeight = FontWeight.Bold)
                SelectionMenu("Abschnitt", (sections[commander.id] ?: BattleSection.CENTER).label, BattleSection.entries, { it.label }) {
                    sections = sections + (commander.id to it)
                }
            } }
            UnitType.entries.filter { state.directCommand(it) > 0 }.forEach { type -> item {
                Surface(color = Panel2, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val available = state.directCommand(type).toLong()
                        val used = directCounts[type]?.values?.sumOf { it.toLong() } ?: 0L
                        Text("Dein persönliches Kommando: ${type.label}", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("$used von $available aufgestellt · ${available - used} noch frei", color = Mist, fontSize = 12.sp)
                        BattleSection.entries.forEach { section ->
                            val current = directCounts[type]?.get(section) ?: 0
                            val otherCounts = directCounts[type]?.filterKeys { it != section }?.values?.sumOf { it.toLong() } ?: 0L
                            val capacity = (available - otherCounts).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                            TroopCountPicker(section.label, capacity, current) { amount ->
                                val counts = directCounts[type].orEmpty() + (section to amount.coerceIn(0, capacity))
                                directCounts = directCounts + (type to counts)
                            }
                        }
                    }
                }
            } }
            item {
                Text("$total Soldaten aufgestellt", color = Gold, fontWeight = FontWeight.Bold)
                BattleSection.entries.forEach { section ->
                    val sectionTotal = deployments.filter { it.section == section }.sumOf { d -> d.units.sumOf { it.amount.toLong() } }
                    Text("${section.label}: $sectionTotal", color = Mist, fontSize = 12.sp)
                }
            }
        }
    }, confirmButton = {
        Button(enabled = total > 0 && total <= Int.MAX_VALUE.toLong() && validDirectCounts && (session == null || editingFormation), onClick = {
            val result = if (editingFormation) BattleEngine.redeploy(state, deployments)
                else BattleEngine.start(state, enemy, tactic, deployments, enemyStrength = invasion?.strength)
            onState(result.state); onNotice(result.message)
            if (result.state.battleSession?.isActive == true) onDismiss()
        }) { Text(if (editingFormation) "AUFSTELLUNG ÜBERNEHMEN" else "LIVE-SCHLACHT STARTEN") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Zurück") } })
}

@Composable
private fun FormationZones(deployments: List<BattleDeployment>, state: GameState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SCHLACHTFELD · Front nach oben", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BattleSection.entries.filter { it != BattleSection.RESERVE }.forEach { section ->
                FormationZone(section, deployments, state, Modifier.weight(1f))
            }
        }
        FormationZone(BattleSection.RESERVE, deployments, state, Modifier.fillMaxWidth())
        Text("Kommandos über die Abschnittsauswahl zuweisen. Persönliche Truppen je Zone verteilen.", color = Mist, fontSize = 11.sp)
    }
}

@Composable
private fun FormationZone(section: BattleSection, deployments: List<BattleDeployment>, state: GameState, modifier: Modifier) {
    val rows = deployments.filter { it.section == section }
    val count = rows.sumOf { row -> row.units.sumOf { it.amount.toLong() } }
    Surface(modifier = modifier.heightIn(min = 112.dp), color = Panel2, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .45f))) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(section.label, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("$count Soldaten", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            rows.forEach { row -> Text(state.commanders.firstOrNull { it.id == row.commanderId }?.name ?: "Persönliches Kommando", color = Mist, fontSize = 10.sp) }
            if (rows.isEmpty()) Text("Unbesetzt", color = Mist, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun <T> SelectionMenu(label: String, selected: String, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
internal fun CourtScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    var editPlayer by remember { mutableStateOf(false) }
    var editCompanion by remember { mutableStateOf(false) }

    var playerName by remember(state.player.name) { mutableStateOf(state.player.name) }
    var playerAge by remember(state.player.age) { mutableStateOf(state.player.age.toString()) }
    var playerArmor by remember(state.player.armorStyle) { mutableStateOf(state.player.armorStyle) }
    var playerWeapon by remember(state.player.weapon) { mutableStateOf(state.player.weapon) }

    var companionName by remember(state.companion.name) { mutableStateOf(state.companion.name) }
    var companionAge by remember(state.companion.age) { mutableStateOf(state.companion.age.toString()) }
    var companionArmor by remember(state.companion.armorStyle) { mutableStateOf(state.companion.armorStyle) }
    var companionWeapon by remember(state.companion.weapon) { mutableStateOf(state.companion.weapon) }

    val playerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onState(GameEngine.updatePlayerPortrait(state, uri.toString()))
        }
    }
    val companionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onState(GameEngine.updateCompanionIdentity(state, companionName, uri.toString()))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageTitle("CHARAKTER", "Attribute, Fertigkeitspunkte und dein persönliches Profil.") }

        item {
            CharacterPanel(
                title = state.player.name,
                subtitle = state.player.species.label + " · " + state.rank + " · " + state.title,
                uri = state.player.portraitUri,
                fallback = R.drawable.portrait_knight,
                stats = listOf(
                    "Schwert " + state.player.sword,
                    "Bogen " + state.player.bow,
                    "Führung " + state.player.leadership,
                    "Taktik " + state.player.tactics
                ),
                onPortrait = { playerPicker.launch(arrayOf("image/*")) }
            )
        }

        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val threshold = state.player.level * 100
                    Text("Stufe ${state.player.level}", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("${state.player.experience} / $threshold XP · ${threshold - state.player.experience} bis zur nächsten Stufe", color = Mist, fontSize = 12.sp)
                    LinearProgressIndicator(progress = { (state.player.experience.toFloat() / threshold.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Gold)
                    Text("Nächstes Level: +3 Skillpunkte", color = Gold, fontWeight = FontWeight.Bold)
                    Text("${state.player.skillPoints} freie Fertigkeitspunkte", color = if (state.player.skillPoints > 0) Success else Mist)
                    CourtInfo("Woher kommen Spieler-XP?", "Missionen, Schlachten und Reichsentscheidungen geben XP. Schlachten bringen größere Beträge; Gebietskauf und diplomatische Übernahmen geben kleine Mengen. Bei jedem Level werden +3 Skillpunkte frei.")
                    listOf(
                        Triple("Schwert", "sword", state.player.sword),
                        Triple("Bogen", "bow", state.player.bow),
                        Triple("Reiten", "riding", state.player.riding),
                        Triple("Führung", "leadership", state.player.leadership),
                        Triple("Taktik", "tactics", state.player.tactics),
                        Triple("Diplomatie", "diplomacy", state.player.diplomacy)
                    ).forEach { (label, key, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("$label: $value / 100", color = PaleGold, fontWeight = FontWeight.SemiBold)
                                Text(skillImpact(key), color = Mist, fontSize = 11.sp)
                            }
                            OutlinedButton(enabled = state.player.skillPoints > 0 && value < 100, onClick = {
                                val result = ProgressionEngine.spendPoint(state, key)
                                onState(result.state); onNotice(result.message)
                            }) { Text("+1") }
                        }
                    }
                }
            }
        }

        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editPlayer = !editPlayer }) { Text(if (editPlayer) "Anpassung schließen" else "Spieler anpassen", color = Gold) }
                    if (editPlayer) {
                        OutlinedTextField(playerName, { playerName = it.take(24) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(playerAge, { playerAge = it.filter(Char::isDigit).take(3) }, label = { Text("Alter") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(playerArmor, { playerArmor = it.take(36) }, label = { Text("Rüstung / Stil") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(playerWeapon, { playerWeapon = it.take(36) }, label = { Text("Hauptwaffe") }, modifier = Modifier.fillMaxWidth())
                        SmallAction("Spieler übernehmen") {
                            onState(
                                customizePlayer(
                                    state,
                                    playerName,
                                    playerAge.toIntOrNull() ?: state.player.age,
                                    playerArmor,
                                    playerWeapon
                                )
                            )
                            onNotice("Spieler angepasst.")
                        }
                    }
                }
            }
        }


    }
}

@Composable
private fun CourtInfo(title: String, detail: String) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text("? $title", color = Gold, fontSize = 12.sp) }
    if (expanded) Text(detail, color = Mist, fontSize = 12.sp)
}

private fun skillImpact(skill: String): String = when (skill) {
    "sword" -> "Stärkt Nahkampf, Patrouillen und Banditenmissionen."
    "bow" -> "Stärkt Fernkampf sowie Jagd und Erkundung."
    "riding" -> "Stärkt Ritter; ab 75 kürzere Geleitschutz- und Erkundungsmissionen."
    "leadership" -> "Stärkt Kontingente, Erholung der Moral und Hilfsmissionen."
    "tactics" -> "Verbessert Kampfkraft, Missionschancen und taktische Entscheidungen."
    else -> "Mehr Gold bei Handelsbonus und günstigere Gebietsverhandlungen."
}

@Composable
private fun RelationshipAction(
    label: String,
    state: GameState,
    action: String,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit
) {
    val cost = if (action in listOf("command", "court")) 2 else 1
    val spent = if (state.relationship.actionDay == state.day) state.relationship.spentActions else 0
    OutlinedButton(
        enabled = spent + cost <= 2 && !state.commanderAway(COMPANION_COMMANDER_ID) && state.battleSession?.isActive != true,
        onClick = {
            val result = GameEngine.companionAction(state, action)
            onState(result.state)
            onNotice(result.message)
        }, modifier = Modifier.fillMaxWidth()
    ) { Text(label) }
}
