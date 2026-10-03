package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.engine.FrontierEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.ArmyEngine
import com.goldenunicorn.troopmanager.model.*

/** One frontier hub, backed by the same persistent state as the map and daily simulation. */
@Composable
internal fun FrontierScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var expandedAlly by remember { mutableStateOf<AllyPeople?>(null) }
    var expandedWeapon by remember { mutableStateOf<WallWeaponType?>(null) }
    var expandedDesign by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var houseOpen by rememberSaveable { mutableStateOf(false) }
    val inBattle = state.battleSession?.isActive == true
    fun apply(result: GameEngine.ActionResult) {
        onState(result.state)
        onNotice(result.message)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PageTitle("FRONTIER & MAUER", "Die Grenze deines Reiches")
            TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Gold) {
                listOf("Lage", "Verbündete", "Mauer", "Regimenter", "Posten").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inBattle) item {
                EmptyCard("Während der laufenden Schlacht sind neue Frontier-Aufträge gesperrt.")
            }
            when (tab) {
                0 -> {
                    state.frontier.pendingDecision?.let { decision ->
                        item {
                            FrontierCard {
                                Text("ENTSCHEIDUNG · ${decision.title}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(decision.text, color = Color.White)
                                decision.choices.forEach { choice ->
                                    FrontierAction(choice.label, !inBattle, primary = true) {
                                        apply(FrontierEngine.resolveFrontierDecision(state, choice.id))
                                    }
                                    Text(choice.detail, color = Mist, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    item { FrontierMap(state, !inBattle, ::apply) }
                    item { SectionTitle("Grenzlage") }
                    val visibleHordes = state.frontier.hordes.filter { it.discovered }.sortedBy { it.daysToArrival }
                    if (visibleHordes.isEmpty()) item {
                        EmptyCard("Keine feindlichen Banner gemeldet. Patrouillen sichern die Grenze und halten Ausschau.")
                    }
                    items(visibleHordes, key = { it.id }) { horde -> HordeCard(horde) }
                    item { SectionTitle("Patrouille") }
                    item { PatrolCard(state, !inBattle, ::apply) }
                    item { SectionTitle("Kampfgefährten") }
                    item { FrontierBondCard(state, !inBattle, ::apply) { houseOpen = true } }
                    item { SectionTitle("Hilfe unterwegs") }
                    if (state.frontier.reinforcements.isEmpty()) item {
                        EmptyCard("Keine verbündete Verstärkung auf Reisen. Hilfe anfordern oder Soldaten kaufen unter Verbündete.")
                    }
                    items(state.frontier.reinforcements, key = { it.id }) { movement -> AidMovementCard(movement) }
                }
                1 -> {
                    item {
                        EmptyCard("Vertrauen beeinflusst Preise und Hilfsbereitschaft. Verstärkung reist von ihrem Herkunftsort zur Grenzfeste.")
                    }
                    items(state.frontier.allies, key = { it.people }) { pact ->
                        AllyCard(state, pact, expandedAlly == pact.people, !inBattle, {
                            expandedAlly = if (expandedAlly == pact.people) null else pact.people
                        }, ::apply)
                    }
                    if (state.frontier.reinforcements.isNotEmpty()) {
                        item { SectionTitle("Aktive Reisen") }
                        items(state.frontier.reinforcements, key = { "ally_${it.id}" }) { movement -> AidMovementCard(movement) }
                    }
                }
                2 -> {
                    item {
                        StatGrid(listOf(
                            "Mauerintegrität" to "${state.realm.wallIntegrity} %",
                            "Fertige Geschütze" to state.frontier.weapons.sumOf { it.count }.toString(),
                        ))
                    }
                    item {
                        EmptyCard("Geschütze wirken in befestigten Schlachten. Munition, Zustand und Nachladezeit bestimmen ihre Einsatzfähigkeit.")
                    }
                    items(WallWeaponType.entries, key = { it.name }) { type ->
                        WallWeaponCard(state, type, expandedWeapon == type, !inBattle, {
                            expandedWeapon = if (expandedWeapon == type) null else type
                        }, ::apply)
                    }
                }
                3 -> {
                    val unlocked = FrontierEngine.customUnitsUnlocked(state)
                    item {
                        FrontierCard {
                            Text("Eigene Regimenter", color = PaleGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Rolle und Ausrüstung bestimmen Stärke, Kosten und Ausbildungszeit.", color = Mist, fontSize = 13.sp)
                            FrontierAction("Einheit entwerfen", unlocked && !inBattle, primary = true) { editorOpen = true }
                            if (!unlocked) Text("Freischaltung: Tag 40, drei Siege oder Kaserne Stufe 4.", color = Mist, fontSize = 12.sp)
                        }
                    }
                    if (state.frontier.designs.isEmpty()) item { EmptyCard("Noch kein eigenes Einheitendesign gespeichert.") }
                    items(state.frontier.designs, key = { it.id }) { design ->
                        CustomDesignCard(state, design, expandedDesign == design.id, !inBattle, {
                            expandedDesign = if (expandedDesign == design.id) null else design.id
                        }, ::apply)
                    }
                }
                4 -> {
                    item {
                        FrontierCard {
                            Text("Außenposten", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Wachtürme verbessern Aufklärung und bremsen das Wachstum feindlicher Lager in derselben Region. Ausbau bis Stufe 3.", color = Mist, fontSize = 12.sp)
                            Text("Baukosten: 300 Gold · 180 Holz · 80 Stein", color = Gold, fontSize = 12.sp)
                        }
                    }
                    if (state.frontier.outposts.isEmpty()) item { EmptyCard("Noch keine Außenposten. Erobere oder erkunde Grenzorte und sichere wichtige Wege.") }
                    items(state.frontier.outposts, key = { "post_${it.id}" }) { post ->
                        val place = state.world.place(post.regionId)
                        FrontierCard {
                            Text(post.name, color = PaleGold, fontWeight = FontWeight.Bold)
                            Text("${place?.name ?: post.regionId} · Stufe ${post.level} · Zustand ${post.integrity} %", color = Mist)
                            Text("Aufklärung +${post.scoutBonus} · Lagerwachstum −${post.growthSuppression}/Tag · Depot ${post.stores}/1000 Nahrung", color = Gold, fontSize = 12.sp)
                            if (post.level < 3) FrontierAction("Ausbauen", !inBattle) { apply(FrontierEngine.upgradeOutpost(state, post.id)) }
                            FrontierAction("100 Nahrung einlagern", !inBattle && state.resources.food >= 100) { apply(FrontierEngine.stockOutpost(state, post.id)) }
                        }
                    }
                    val buildable = state.world.places.filter { it.id != "keep" && it.ownerId in setOf(PLAYER_FACTION, NEUTRAL_FACTION) && state.frontier.outposts.none { p -> p.regionId == it.id } }.take(8)
                    if (buildable.isNotEmpty()) item { SectionTitle("Neue Wacht errichten") }
                    items(buildable, key = { "build_${it.id}" }) { place ->
                        FrontierCard {
                            Text(place.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${place.terrain.label} · ${if (place.ownerId == PLAYER_FACTION) "eigenes Gebiet" else "neutrales Grenzland"}", color = Mist, fontSize = 12.sp)
                            FrontierAction("Außenposten errichten", !inBattle, primary = true) { apply(FrontierEngine.buildOutpost(state, place.id)) }
                        }
                    }
                }
            }
        }
    }
    if (editorOpen) UnitDesignDialog(state, { editorOpen = false }, onNotice) { result ->
        apply(result)
        if (result.state != state) editorOpen = false
    }
    if (houseOpen) HouseDialog(state, { houseOpen = false }, ::apply)
}

@Composable
private fun FrontierCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun FrontierAction(label: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
        shape = RoundedCornerShape(14.dp),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun HordeCard(horde: HordeBanner) {
    FrontierCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(horde.name, color = PaleGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(color = Danger.copy(alpha = .22f), shape = RoundedCornerShape(8.dp)) {
                Text("${horde.daysToArrival} Tage", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(7.dp))
            }
        }
        Text("${horde.kind.label} · Ziel: ${horde.target}", color = Mist, fontSize = 13.sp)
        if (horde.estimateMaximum > 0) Text("${horde.estimateMinimum}–${horde.estimateMaximum} Feinde geschätzt", color = Gold, fontSize = 13.sp)
        else Text("Stärke noch nicht verlässlich geschätzt", color = Gold, fontSize = 13.sp)
        Text("Herkunft: ${horde.origin}", color = Mist, fontSize = 12.sp)
        if (horde.jointWith != null) Text("Gemeinsamer Marsch mit einem weiteren Banner", color = Danger, fontSize = 12.sp)
        if (horde.kind.hostileToAll) Text("Tao Tei bedrohen auch andere Reiche.", color = Danger, fontSize = 12.sp)
    }
}

@Composable
private fun PatrolCard(state: GameState, actionsEnabled: Boolean, apply: (GameEngine.ActionResult) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("50") }
    val patrol = state.frontier.patrol
    FrontierCard {
        if (patrol != null) {
            Text("${patrol.soldiers} Soldaten auf Grenzpatrouille", color = PaleGold, fontWeight = FontWeight.Bold)
            Text("Rückkehr in ${patrol.daysLeft} Tagen", color = Gold, fontSize = 13.sp)
            Text("Diese Soldaten stehen zuhause erst nach ihrer Rückkehr wieder zur Verfügung.", color = Mist, fontSize = 12.sp)
            FrontierAction("Patrouille zurückrufen", actionsEnabled) { apply(FrontierEngine.recallPatrol(state)) }
        } else {
            val available = FrontierEngine.availablePatrolSoldiers(state)
            val maximum = minOf(500, available)
            val number = amount.toIntOrNull() ?: 0
            Text("$available Soldaten verfügbar", color = PaleGold, fontWeight = FontWeight.Bold)
            Text("Kleine Überfälle können abgefangen werden. Große Horden brauchen eine vorbereitete Verteidigung.", color = Mist, fontSize = 12.sp)
            OutlinedTextField(
                value = amount,
                onValueChange = { value -> amount = value.filter(Char::isDigit).take(6) },
                label = { Text("Soldaten") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FrontierAction("Für 5 Tage aussenden", actionsEnabled && number in 10..maximum, primary = true) {
                apply(FrontierEngine.sendPatrol(state, number, 5))
            }
            if (available < 10) Text("Eine Patrouille benötigt mindestens 10 unzugewiesene Soldaten zuhause.", color = Mist, fontSize = 12.sp)
            else if (number !in 10..maximum) Text("Wähle 10 bis $maximum verfügbare Soldaten.", color = Mist, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FrontierBondCard(state: GameState, actionsEnabled: Boolean, apply: (GameEngine.ActionResult) -> Unit, onHouse: () -> Unit) {
    val bond = state.frontier.bond
    FrontierCard {
        Text(bond.stage, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("${bond.sessions} gemeinsame Trainingseinheiten", color = Gold, fontSize = 13.sp)
        if (bond.sharedBattles > 0) Text("${bond.sharedBattles} gemeinsame Schlachten", color = Mist, fontSize = 12.sp)
        Text("Gemeinsame Übung stärkt eure Kampffertigkeiten und euer Zusammenspiel.", color = Mist, fontSize = 12.sp)
        FrontierAction("Haus öffnen", true, onClick = onHouse)
        val reason = FrontierEngine.companionTrainingUnavailableReason(state)
        FrontierAction("Mit ${state.companion.name} trainieren", actionsEnabled && reason == null) {
            apply(FrontierEngine.trainWithCompanion(state))
        }
        if (reason != null) Text(reason, color = Mist, fontSize = 12.sp)
    }
}

@Composable
private fun AidMovementCard(movement: AllyReinforcement) {
    FrontierCard {
        Text(if (movement.emergency) "Verbündete Nothilfe" else "Gekaufte Verstärkung", color = PaleGold, fontWeight = FontWeight.Bold)
        Text("${movement.amount} ${movement.type.label}", color = Color.White, fontSize = 16.sp)
        Text("${movement.people.label} · aus ${movement.origin}", color = Mist, fontSize = 12.sp)
        Text("Noch ${movement.daysRemaining} Tage · Ankunft Tag ${movement.arrivalDay}", color = Gold, fontSize = 13.sp)
        Text("Auf Reisen, noch nicht Teil der heimischen Armee.", color = Mist, fontSize = 12.sp)
    }
}

@Composable
private fun AllyCard(
    state: GameState,
    pact: AllyPact,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onExpand: () -> Unit,
    apply: (GameEngine.ActionResult) -> Unit,
) {
    var amount by rememberSaveable(pact.people) { mutableStateOf("25") }
    val price = FrontierEngine.reinforcementPrice(state, pact.people)
    val number = amount.toIntOrNull() ?: 0
    FrontierCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onExpand)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(when (pact.people) {
                    AllyPeople.GOLD_ELVES -> "file:///android_asset/frontier/ally_gold.webp"
                    AllyPeople.FREE_HOLDS -> "file:///android_asset/frontier/ally_holds.webp"
                    AllyPeople.WALL_ENVOYS -> "file:///android_asset/frontier/ally_wall.webp"
                }, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Column {
                    Text(pact.people.label, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${pact.stock} Soldaten verfügbar · ${if (expanded) "Weniger ↑" else "Details ↓"}", color = Gold, fontSize = 12.sp)
                }
            }
        }
        ArmyQuality("Vertrauen", pact.trust)
        if (expanded) {
            Text(pact.people.description, color = Mist, fontSize = 13.sp)
            Text("$price Gold je Soldat · Hilfe mit Reisezeit", color = Gold, fontSize = 13.sp)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit).take(6) },
                label = { Text("Verstärkung kaufen · Anzahl") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            FrontierAction("Kaufen · ${number.toLong() * price} Gold", actionsEnabled && number in 1..pact.stock, primary = true) {
                apply(FrontierEngine.buyReinforcements(state, pact.people, number))
            }
            if (pact.stock == 0) Text("Dieser Partner hat derzeit keine Soldaten verfügbar.", color = Mist, fontSize = 12.sp)
            else if (number !in 1..pact.stock) Text("Wähle 1 bis ${pact.stock} verfügbare Soldaten.", color = Mist, fontSize = 12.sp)
            FrontierAction("Nothilfe anfordern", actionsEnabled) { apply(FrontierEngine.requestAid(state, pact.people)) }
            Text("Nothilfe braucht eine tatsächliche Bedrohung und Vertrauen. Wiederholte Forderungen ohne Gegenleistung belasten das Bündnis.", color = Mist, fontSize = 12.sp)
            FrontierAction("Bündnis unterstützen · 400 Gold", actionsEnabled) { apply(FrontierEngine.contributeToAlly(state, pact.people)) }
            Text("Unterstützung stärkt Vertrauen und gleicht unbeantwortete Hilfsforderungen aus.", color = Mist, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WallWeaponCard(
    state: GameState,
    type: WallWeaponType,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onExpand: () -> Unit,
    apply: (GameEngine.ActionResult) -> Unit,
) {
    val stock = state.frontier.weapons.firstOrNull { it.type == type } ?: WallWeaponStock(type)
    FrontierCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onExpand)) {
            Text(type.label, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("${stock.count} fertig · ${if (expanded) "Weniger ↑" else "Details ↓"}", color = Gold, fontSize = 12.sp)
        }
        if (stock.daysRemaining > 0) Text("Bau läuft · noch ${stock.daysRemaining} Tage", color = Blue, fontSize = 13.sp)
        if (stock.count > 0) {
            Text("${stock.ammunition} Ladungen · Zustand ${stock.integrity} %", color = if (stock.integrity < 40 || stock.ammunition == 0) Danger else Mist, fontSize = 13.sp)
            if (stock.reloadRounds > 0) Text("Nachladen: ${stock.reloadRounds} Kampfrunden", color = Gold, fontSize = 12.sp)
        }
        if (expanded) {
            Text(type.filmRole, color = Mist, fontSize = 13.sp)
            if (type == WallWeaponType.CRANE_WINCH) Text("Für einen Ausfall muss echte Kranichgarde in der Schlacht aufgestellt sein.", color = Gold, fontSize = 12.sp)
            Text("Bau: ${type.gold} Gold · ${type.wood} Holz · ${type.iron} Eisen · ${type.days} Tage", color = Gold, fontSize = 12.sp)
            Text("Zusätzlich werden Militärgüter aus dem Arsenal benötigt.", color = Mist, fontSize = 12.sp)
            FrontierAction("Ein Geschütz bauen", actionsEnabled && stock.daysRemaining == 0, primary = true) { apply(FrontierEngine.buildWallWeapon(state, type)) }
            FrontierAction("Munition ergänzen", actionsEnabled && stock.count > 0) { apply(FrontierEngine.reloadWallWeapon(state, type)) }
            FrontierAction("Geschütze reparieren", actionsEnabled && stock.count > 0 && stock.integrity < 100) { apply(FrontierEngine.repairWallWeapon(state, type)) }
            if (stock.count == 0) Text("Erst ein fertiges Geschütz kann geladen und repariert werden.", color = Mist, fontSize = 12.sp)
            else if (stock.integrity == 100) Text("Alle Geschütze dieses Typs sind unbeschädigt.", color = Mist, fontSize = 12.sp)
        }
    }
}


@Composable
private fun FrontierMap(state: GameState, actionsEnabled: Boolean, apply: (GameEngine.ActionResult) -> Unit) {
    var picked by remember { mutableStateOf<String?>(null) }
    val horde = state.frontier.hordes.firstOrNull { it.id == picked }
    val discovered = state.frontier.hordes.filter { it.discovered }
    BoxWithConstraints(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(18.dp))) {
        AsyncImage("file:///android_asset/world_map.webp", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x18000000), Color(0xCC070A0D)))))
        val home = state.world.place("keep")
        Surface(
            color = Color(0xDD2A2518),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .offset(
                    x = (maxWidth - 78.dp) * (home?.x ?: .5f),
                    y = (maxHeight - 52.dp) * (home?.y ?: .5f),
                )
        ) {
            Text("🏰 ${state.realm.settlementName}", color = PaleGold, fontSize = 11.sp, modifier = Modifier.padding(7.dp), maxLines = 1)
        }
        discovered.forEachIndexed { index, banner ->
            val place = state.world.place(banner.regionId)
            val fallbackX = listOf(.08f, .72f, .18f, .62f)[index % 4]
            val fallbackY = listOf(.24f, .14f, .68f, .58f)[index % 4]
            val x = place?.x ?: fallbackX
            val y = place?.y ?: fallbackY
            val art = when (banner.kind) {
                HordeKind.ORC -> "file:///android_asset/frontier/horde_orc.webp"
                HordeKind.URUK -> "file:///android_asset/frontier/horde_uruk.webp"
                HordeKind.TAO_TEI -> "file:///android_asset/frontier/horde_taotei.webp"
            }
            Surface(
                color = Color(0xDD121920),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .offset(
                        x = (maxWidth - 96.dp) * x.coerceIn(0f, 1f),
                        y = (maxHeight - 74.dp) * y.coerceIn(0f, 1f),
                    )
                    .width(96.dp)
                    .clickable { picked = banner.id },
            ) {
                Column {
                    AsyncImage(art, null, Modifier.fillMaxWidth().height(42.dp), contentScale = ContentScale.Crop)
                    Text("${banner.kind.label} · ${banner.daysToArrival} T.", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(5.dp), maxLines = 1)
                }
            }
        }
        state.frontier.patrol?.let {
            Surface(
                color = Color(0xCC1B2B24),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text("Patrouille ${it.soldiers}", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(7.dp))
            }
        }
    }
    Text("Banner stehen an ihren Weltkoordinaten. Lager wachsen, wenn du sie lässt; ein Sturm kostet 40 Holz.", color = Mist, fontSize = 12.sp)
    if (horde != null) {
        val own = state.homeArmySize
        val risk = when {
            own * 2 < horde.soldiers -> "Extrem – automatischer Rückzug wahrscheinlich"
            own < horde.soldiers -> "Hoch"
            own < horde.soldiers * 2 -> "Mittel"
            else -> "Niedrig"
        }
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(horde.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${horde.kind.label} · noch ${horde.daysToArrival} Tage")
                    Text("${horde.estimatedStrengthLabel}")
                    Text("Eigene Truppen zuhause: $own")
                    Text("Lagersturm: 40 Holz · Risiko: $risk", color = if (risk.startsWith("Niedrig")) Success else if (risk == "Mittel") Gold else Danger)
                    Text("Jagd: kleiner Ausfall mit echten Verlusten; Lagersturm startet eine vollständige Schlacht.", color = Mist, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { apply(FrontierEngine.assaultCamp(state, horde.id)); picked = null }, enabled = actionsEnabled && state.resources.wood >= 40 && own >= 20) {
                    Text("Lager stürmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { apply(FrontierEngine.huntHorde(state, horde.id)); picked = null }, enabled = actionsEnabled && own >= 10) {
                    Text("Nur jagen")
                }
            },
        )
    }
}

@Composable
private fun HouseDialog(state: GameState, onDismiss: () -> Unit, apply: (GameEngine.ActionResult) -> Unit) {
    val children = state.dynasty.members.filter { it.alive && it.id != state.dynasty.rulerId }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Haus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(state.companion.portraitUri ?: "file:///android_asset/portrait_companion.webp", null, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
                Text("${state.companion.name} · ${state.frontier.bond.stage}", color = PaleGold, fontWeight = FontWeight.Bold)
                Text("Vertrauen ${state.companion.trust} · Zuneigung ${state.companion.affection}", color = Mist, fontSize = 12.sp)
                Text("Schwert ${state.player.sword}/${state.companion.sword} · Bogen ${state.player.bow}/${state.companion.bow}. Gemeinsames Training hebt beide.", color = Gold, fontSize = 12.sp)
                Text("Goldelben, Freie Höfe und Mauerlegion schicken Hilfe mit Reisezeit. Vertrauen steht unter Verbündete.", color = Mist, fontSize = 12.sp)
                TextButton(onClick = { apply(FrontierEngine.trainWithCompanion(state)) }) { Text("Mit ihr trainieren") }
                if (children.isEmpty()) Text("Noch kein Kind im Haus. Leichtes Training ab 10, Notensatz ab 16.", color = Mist, fontSize = 12.sp)
                children.forEach { child ->
                    val age = child.age(state.day)
                    Text("${child.name}, $age", color = Color.White)
                    TextButton(onClick = { apply(FrontierEngine.trainChild(state, child.id)) }, enabled = age >= 10) { Text(if (age < 10) "Ab 10" else "Leicht trainieren") }
                }
            }
        },
    )
}

@Composable
private fun CustomDesignCard(
    state: GameState,
    design: CustomUnitDesign,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onExpand: () -> Unit,
    apply: (GameEngine.ActionResult) -> Unit,
) {
    val available = ArmyEngine.recruitable(state, design.culture)
    FrontierCard {
        Row(Modifier.fillMaxWidth().clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignArt(design, Modifier.size(66.dp))
            Column(Modifier.weight(1f)) {
                Text(design.name, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${design.culture.label} · ${design.role.label}", color = Mist, fontSize = 12.sp)
                Text("${design.soldiers} ausgebildet · ${if (expanded) "Weniger ↑" else "Details ↓"}", color = Gold, fontSize = 12.sp)
            }
        }
        if (design.trainingAmount > 0) Text("${design.trainingAmount} in Ausbildung · noch ${design.trainingDaysLeft} Tage", color = Blue, fontSize = 13.sp)
        if (expanded) {
            Text("${design.weapon.label} · ${design.armor.label}${if (design.shield) " · Schild" else ""}", color = Mist, fontSize = 13.sp)
            DesignPreviewStats(design)
            Text("Veteranenstufe ${design.veteranLevel}/5 · ${design.victories} Siege · ${design.battleLosses} Gefallene", color = Gold, fontSize = 12.sp)
            design.epithet?.let { Text("Beiname: $it", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            val captain = design.captainId?.let { id -> state.commanders.firstOrNull { it.id == id } }
            Text("Führung: ${captain?.let { "${it.name} · ${it.trait}" } ?: "direktes Kommando"}", color = Mist, fontSize = 12.sp)
            state.commanders.filter { cmd -> state.frontier.designs.none { it.id != design.id && it.captainId == cmd.id } }.forEach { cmd ->
                FrontierAction("Hauptmann ${cmd.name} zuweisen", actionsEnabled && design.captainId != cmd.id) {
                    apply(FrontierEngine.assignCaptainToDesign(state, design.id, cmd.id))
                }
            }
            if (design.captainId != null) FrontierAction("Hauptmann abziehen", actionsEnabled) {
                apply(FrontierEngine.assignCaptainToDesign(state, design.id, null))
            }
            Text("Soldaten gehören zum regulären Pool ${design.unitType.label}.", color = Mist, fontSize = 12.sp)
            Text("$available Rekruten verfügbar", color = PaleGold, fontSize = 13.sp)
            listOf(10, 25, 50).forEach { amount ->
                FrontierAction("$amount ausbilden · ${amount * design.goldCost} Gold", actionsEnabled && design.trainingAmount == 0 && available >= amount, primary = true) {
                    apply(FrontierEngine.trainCustomUnit(state, design.id, amount))
                }
            }
            if (design.trainingAmount > 0) Text("Ein weiterer Auftrag ist nach Abschluss der laufenden Ausbildung möglich.", color = Mist, fontSize = 12.sp)
            else if (available < 50) Text("Ausbildungsgruppen benötigen 10, 25 oder 50 verfügbare Rekruten dieser Kultur.", color = Mist, fontSize = 12.sp)
            FrontierAction(if (design.trainingAmount > 0) "Erst nach der Ausbildung löschbar" else "Design löschen", actionsEnabled && design.trainingAmount == 0) {
                apply(FrontierEngine.deleteDesign(state, design.id))
            }
        }
    }
}

@Composable
private fun DesignArt(design: CustomUnitDesign, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = frontierDesignColor(design.colorHex), shape = RoundedCornerShape(12.dp)) {
        if (design.portraitUri != null) AsyncImage(model = design.portraitUri, contentDescription = design.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(3.dp))
        else UnitArt(design.culture, Modifier.fillMaxSize().padding(3.dp))
    }
}

@Composable
private fun DesignPreviewStats(design: CustomUnitDesign) {
    StatGrid(listOf(
        "Angriff / Abwehr" to "${design.attack} / ${design.defense}",
        "Fernkampf" to design.ranged.toString(),
        "Gold je Rekrut" to design.goldCost.toString(),
        "Ausbildungszeit" to "${design.trainingDays} Tage",
    ))
    Text("Versorgung: ${design.foodEach} Nahrung je Soldat/Tag", color = Gold, fontSize = 12.sp)
    val equipment = FrontierEngine.customUnitRequirements(design, 1)
    Text("Ausrüstung je Rekrut: ${equipment.entries.joinToString(" · ") { "${it.value} ${it.key.label}" }}", color = Mist, fontSize = 12.sp)
}

private fun frontierDesignColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Gold)

@Composable
private fun UnitDesignDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit,
    apply: (GameEngine.ActionResult) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("Grenzgarde") }
    var culture by rememberSaveable { mutableStateOf(Culture.HUMAN) }
    var role by rememberSaveable { mutableStateOf(CustomUnitRole.INFANTRY) }
    var weapon by rememberSaveable { mutableStateOf(CustomWeapon.SWORD) }
    var armor by rememberSaveable { mutableStateOf(CustomArmor.MAIL) }
    var shield by rememberSaveable { mutableStateOf(true) }
    var colorHex by rememberSaveable { mutableStateOf("#D6B66B") }
    var portraitUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (granted.isSuccess) portraitUri = uri.toString()
            else onNotice("Dieses Bild konnte nicht dauerhaft eingebunden werden. Bitte ein anderes Bild wählen.")
        }
    }
    fun chooseRole(selected: CustomUnitRole) {
        role = selected
        weapon = when (selected) {
            CustomUnitRole.RANGED -> CustomWeapon.BOW
            CustomUnitRole.SIEGE -> CustomWeapon.ARTILLERY
            else -> CustomWeapon.SWORD
        }
        shield = selected == CustomUnitRole.INFANTRY || selected == CustomUnitRole.CAVALRY
    }
    val roles = CustomUnitRole.entries.filter {
        when (it) {
            CustomUnitRole.CAVALRY -> culture == Culture.HUMAN
            CustomUnitRole.SIEGE -> culture == Culture.WALL
            else -> true
        }
    }
    val weapons = when (role) {
        CustomUnitRole.INFANTRY, CustomUnitRole.CAVALRY -> listOf(CustomWeapon.SWORD, CustomWeapon.SPEAR)
        CustomUnitRole.RANGED -> listOf(CustomWeapon.BOW, CustomWeapon.CROSSBOW)
        CustomUnitRole.SIEGE -> listOf(CustomWeapon.ARTILLERY)
    }
    val preview = remember(name, culture, role, weapon, armor, shield, colorHex, portraitUri) {
        FrontierEngine.customUnitPreview(name.trim(), culture, role, weapon, armor, shield, colorHex, portraitUri)
    }
    val validation = FrontierEngine.customUnitValidation(culture, role, weapon, shield)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einheit entwerfen") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesignArt(preview, Modifier.size(76.dp))
                    Column {
                        Text(preview.name.ifBlank { "Deine Einheit" }, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("${culture.label} · ${role.label}", color = Mist, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FrontierMenu("Kultur: ${culture.label}", Culture.entries, { it.label }) { selected ->
                    culture = selected
                    if ((role == CustomUnitRole.CAVALRY && selected != Culture.HUMAN) ||
                        (role == CustomUnitRole.SIEGE && selected != Culture.WALL)) chooseRole(CustomUnitRole.INFANTRY)
                }
                FrontierMenu("Rolle: ${role.label}", roles, { it.label }, ::chooseRole)
                Text("Reiterei ist Menschen, Belagerung der Mauerlegion vorbehalten.", color = Mist, fontSize = 11.sp)
                FrontierMenu("Waffe: ${weapon.label}", weapons, { it.label }) { weapon = it }
                FrontierMenu("Rüstung: ${armor.label}", CustomArmor.entries, { it.label }) { armor = it }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Schild", color = Mist, modifier = Modifier.weight(1f))
                    Switch(
                        checked = shield,
                        onCheckedChange = { shield = it },
                        enabled = role == CustomUnitRole.INFANTRY || role == CustomUnitRole.CAVALRY,
                    )
                }
                if (role == CustomUnitRole.RANGED || role == CustomUnitRole.SIEGE) Text("Fernkampf und Belagerung benötigen beide Hände; kein Schild.", color = Mist, fontSize = 11.sp)
                Text("Regimentsfarbe", color = PaleGold, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#D6B66B", "#6AAE7A", "#5189B7", "#B74C4C", "#A894C5", "#D8E0E5").forEach { selected ->
                        FilterChip(
                            selected = colorHex == selected,
                            onClick = { colorHex = selected },
                            label = { Box(Modifier.size(22.dp).background(frontierDesignColor(selected), RoundedCornerShape(4.dp))) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                FrontierAction(if (portraitUri == null) "Eigenes Portrait wählen" else "Portrait ersetzen", true) { picker.launch(arrayOf("image/*")) }
                if (portraitUri != null) TextButton(onClick = { portraitUri = null }) { Text("Standardportrait verwenden") }
                SectionTitle("Vorschau je Soldat")
                DesignPreviewStats(preview)
                if (name.trim().length < 2) Text("Der Regimentsname braucht mindestens zwei Zeichen.", color = Danger, fontSize = 12.sp)
                if (validation != null) Text(validation, color = Danger, fontSize = 12.sp)
                Text("Das Design reserviert noch keine Soldaten. Danach kannst du Ausrüstung bezahlen und die Ausbildung beauftragen.", color = Mist, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().length >= 2 && validation == null && state.battleSession?.isActive != true,
                onClick = { apply(FrontierEngine.designUnit(state, name.trim(), culture, role, weapon, armor, shield, colorHex, portraitUri)) },
            ) { Text("Design speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun <T> FrontierMenu(label: String, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(label, fontSize = 13.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}
