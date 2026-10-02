package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.engine.MissionEngine
import com.goldenunicorn.troopmanager.engine.WorldEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun CampaignMapScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    val world = state.world
    val knowledge = world.knowledgeFor(PLAYER_FACTION)
    var selectedRegionId by remember { mutableStateOf(world.places.firstOrNull()?.id) }
    var dispatchDestinationId by remember { mutableStateOf<String?>(null) }
    var redirectArmyId by remember { mutableStateOf<String?>(null) }
    var convoyArmyId by remember { mutableStateOf<String?>(null) }
    var depotRegionId by remember { mutableStateOf<String?>(null) }
    var missionRegion by remember { mutableStateOf<WorldRegion?>(null) }
    val selectedRegion = world.place(selectedRegionId ?: "") ?: world.places.firstOrNull()
    val ownArmies = world.armies.filter {
        it.factionId == PLAYER_FACTION && it.status !in listOf(WorldArmyStatus.HOME, WorldArmyStatus.DESTROYED)
    }
    val reports = WorldEngine.observations(state).filter { it.factionId != PLAYER_FACTION }
    fun isKnown(place: WorldPlace) = place.ownerId == PLAYER_FACTION ||
        place.id in knowledge.exploredRegions || place.id in knowledge.visibleRegions
    fun isVisible(place: WorldPlace) = place.ownerId == PLAYER_FACTION || place.id in knowledge.visibleRegions
    fun placeName(id: String?) = world.place(id ?: "")?.let { if (isKnown(it)) it.name else "Unkartiertes Gebiet" } ?: "Heimat"
    val knownFactionIds = buildSet {
        add(PLAYER_FACTION)
        reports.forEach { add(it.factionId) }
        world.places.filter { isVisible(it) }.forEach { add(it.ownerId) }
        state.diplomacy.relations.forEach { relation ->
            if (relation.firstFactionId == PLAYER_FACTION) add(relation.secondFactionId)
            if (relation.secondFactionId == PLAYER_FACTION) add(relation.firstFactionId)
        }
    }
    val knownFactions = world.factions.filter { it.id in knownFactionIds }
    val markers = world.places.mapIndexed { index, place ->
        val known = isKnown(place)
        val label = if (known) place.name else "Unkartiertes Gebiet ${index + 1}"
        CampaignRegionMarker(
            place.id, label, place.x.coerceIn(.09f, .91f), place.y.coerceIn(.1f, .84f),
            place.ownerId == PLAYER_FACTION, known,
            "Region ${index + 1}: $label${if (place.id == selectedRegion?.id) ", ausgewählt" else ""}",
        )
    }
    val armyMarkers = ownArmies.mapNotNull { army ->
        val current = markers.firstOrNull { it.id == army.regionId } ?: return@mapNotNull null
        val nextId = army.route.getOrNull(army.routeIndex + 1)
        val next = markers.firstOrNull { it.id == nextId }
        val road = nextId?.let { target -> world.roads.firstOrNull { it.connects(army.regionId) && it.other(army.regionId) == target } }
        val progress = if (army.status in listOf(WorldArmyStatus.MARCHING, WorldArmyStatus.RETURNING) && next != null && road != null)
            (army.legProgress.toFloat() / road.distance.coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
        CampaignArmyMarker(
            current.x + ((next?.x ?: current.x) - current.x) * progress,
            current.y + ((next?.y ?: current.y) - current.y) * progress,
            true, false,
        )
    } + reports.mapNotNull { observation ->
        markers.firstOrNull { it.id == observation.regionId }?.let {
            CampaignArmyMarker(it.x, it.y, false, observation.day < state.day)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BannerBadge(state.presentation.heraldry, extent = 36.dp)
                Box(Modifier.weight(1f)) {
                    PageTitle(
                        "LEBENDE WELT",
                        "${world.weather.season.label} · Tag ${state.day} · ${world.places.count { it.ownerId == PLAYER_FACTION }} eigene Orte",
                    )
                }
            }
        }
        item {
            StatGrid(listOf(
                "Feldheere" to "${ownArmies.count { it.missionId == null }}",
                "Aufgeklärt" to "${world.places.count { isVisible(it) }} / ${world.places.size} Orte",
                "Nahrung" to "${state.resources.food}",
                "Erntefaktor" to "${(world.weather.season.harvestFactor * 100).toInt()} %",
            ))
        }
        item {
            EmptyCard("${world.weather.season.label}: saisonbedingtes Marschtempo ${(world.weather.season.marchFactor * 100).toInt()} %, Nahrungsertrag ${(world.weather.season.harvestFactor * 100).toInt()} %. Regionales Wetter verändert zusätzlich Marsch, Sicht, Fernkampf und Kavallerie. Sichere Vorräte und Straßen vor langen Feldzügen.")
        }
        if (world.places.isEmpty()) item {
            EmptyCard("Die Kampagnenkarte wird beim Laden des Spielstands aufgebaut.")
        } else item {
            CampaignMapScene(
                markers,
                world.roads.map { it.from to it.to },
                armyMarkers, state.presentation.heraldry, selectedRegion?.id,
            ) { selectedRegionId = it }
        }
        item { SectionTitle("Orte auswählen") }
        items(markers, key = { "region_${it.id}" }) { marker ->
            val number = markers.indexOf(marker) + 1
            Surface(
                onClick = { selectedRegionId = marker.id },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = marker.description },
                color = if (selectedRegion?.id == marker.id) Panel2 else Panel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (selectedRegion?.id == marker.id) Gold else Color.Transparent),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("$number", color = Gold, fontWeight = FontWeight.Bold)
                    Text(marker.name, color = Color.White, modifier = Modifier.weight(1f))
                    Text(
                        if (marker.owned) "Eigen" else if (marker.known) "Kartiert" else "Unbekannt",
                        color = if (marker.owned) Success else Mist, fontSize = 11.sp,
                    )
                }
            }
        }
        selectedRegion?.let { region ->
            item {
                val known = isKnown(region)
                val visible = isVisible(region)
                val weather = world.weather.at(region.id)
                CampaignPanel {
                    Text(if (known) region.name else "Unkartiertes Gebiet", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (known) Text("${region.kind.label} · ${region.terrain.label}", color = Mist)
                    Text(
                        when {
                            region.ownerId == PLAYER_FACTION -> "Unter deinem Schutz"
                            !visible -> "Herrschaft derzeit nicht aufgeklärt"
                            else -> "Herrschaft: ${world.faction(region.ownerId)?.name ?: "Freie Siedlung"}"
                        },
                        color = if (region.ownerId == PLAYER_FACTION) Success else Mist, fontSize = 12.sp,
                    )
                    Text(
                        if (visible) "Aktuelle Sicht durch eigene Gebiete, Heere oder Kundschafter"
                        else if (known) "Kartiert · keine aktuelle Sicht" else "Noch keine gesicherte Ortskenntnis",
                        color = Gold, fontSize = 12.sp,
                    )
                    if (visible) {
                        Text("${weather.label} · ${world.weather.season.label}", color = Color.White)
                        Text(
                            "Marsch ${(weather.marchFactor * world.weather.season.marchFactor * 100).toInt()} % · Fernkampf ${(weather.rangedFactor * 100).toInt()} % · Kavallerie ${(weather.cavalryFactor * 100).toInt()} %",
                            color = Mist, fontSize = 12.sp,
                        )
                        Text("${region.population} Einwohner · Befestigung ${region.fortification} · Wohlstand ${region.prosperity}", color = Mist, fontSize = 12.sp)
                    }
                    val depots = world.depots.filter { it.regionId == region.id && it.factionId == PLAYER_FACTION }
                    depots.forEach { depot -> Text("Versorgungslager: ${depot.food} / ${depot.capacity} Nahrung", color = Success, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            val result = WorldEngine.scout(state, region.id)
                            onState(result.state); onNotice(result.message)
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = state.battleSession?.isActive != true && state.resources.gold >= 75 && state.resources.food >= 100 &&
                            (region.id in knowledge.exploredRegions || world.roads.any { it.connects(region.id) && it.other(region.id) in knowledge.exploredRegions }),
                    ) { Text("Kundschafter · 75 Gold / 100 Nahrung") }
                    Button(
                        enabled = state.homeArmySize >= 30 && state.battleSession?.isActive != true && region.id != "keep" && region.id in knowledge.exploredRegions,
                        onClick = { dispatchDestinationId = region.id },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                    ) { Text("Feldheer hierhin entsenden") }
                    if (!known) Text("Kundschafter erreichen bekannte Orte und ihre Nachbarn. Ein Heer benötigt zuerst ein aufgeklärtes Ziel.", color = Mist, fontSize = 12.sp)
                    if (region.ownerId == PLAYER_FACTION && depots.isEmpty()) OutlinedButton(
                        onClick = { depotRegionId = region.id },
                        enabled = state.battleSession?.isActive != true && state.resources.gold >= 300 && state.resources.wood >= 150 && state.resources.food > 0,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Depot bauen · 300 Gold / 150 Holz") }
                    if (known) state.regions.firstOrNull { it.id == region.id && it.mission != null }?.let { legacy ->
                        SmallAction("${legacy.mission?.label} vorbereiten") { missionRegion = legacy }
                    }
                }
            }
        }
        item { SectionTitle("Eigene Heere") }
        if (ownArmies.isEmpty()) item { EmptyCard("Noch kein Heer auf der Karte. Wähle einen Ort und entsende verfügbare Truppen.") }
        items(ownArmies, key = { "own_${it.id}" }) { army ->
            CampaignPanel {
                Text(army.name, color = PaleGold, fontWeight = FontWeight.Bold)
                Text(state.commanders.firstOrNull { it.id == army.commanderId }?.name ?: "Persönliches Kommando", color = Gold, fontSize = 12.sp)
                Text("${army.total} Soldaten · ${army.status.label}", color = Color.White)
                Text("${placeName(army.regionId)}${army.destinationId?.let { " → ${placeName(it)}" } ?: ""}", color = Mist, fontSize = 12.sp)
                army.arrivalDay?.let { arrival -> Text("Ankunft: Tag $arrival · ${(arrival - state.day).coerceAtLeast(0)} Tage", color = Mist, fontSize = 12.sp) }
                Text("Moral ${army.morale} % · ${army.supplyDays} Tage Vorräte · ${army.marchPolicy.label}", color = if (army.supplyDays <= 1) Danger else Success, fontSize = 12.sp)
                Text(army.cultures.entries.joinToString(" · ") { "${it.key.label}: ${it.value}" }, color = Mist, fontSize = 12.sp)
                if (army.lastLosses > 0) Text("Letzter Marschtag: ${army.lastLosses} Verluste", color = Danger, fontSize = 12.sp)
                if (army.missionId == null && army.status.isAway) {
                    SmallAction("Route und Marschtempo ändern") { redirectArmyId = army.id }
                    SmallAction("Nachschubkonvoi schicken") { convoyArmyId = army.id }
                    OutlinedButton(onClick = {
                        val result = WorldEngine.recall(state, army.id)
                        onState(result.state); onNotice(result.message)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = army.status != WorldArmyStatus.RETURNING) { Text("Heimkehr anordnen") }
                } else if (army.missionId != null) Text("Missionsbefehle im Abschnitt Missionen.", color = Mist, fontSize = 12.sp)
            }
        }
        item { SectionTitle("Aufklärungsberichte") }
        item {
            Text("Sicht liefert Stärke und Standort vom Berichtstag. Kundschafter präzisieren Schätzungen; alte Berichte altern, während fremde Heere weiterziehen und rekrutieren. Vor dem Abfangen erneut aufklären.", color = Mist, fontSize = 12.sp)
        }
        if (reports.isEmpty()) item { EmptyCard("Keine bekannten fremden Heere. Kundschafter verbessern Sichtweite und Genauigkeit.") }
        items(reports, key = { "observation_${it.armyId}" }) { report ->
            CampaignPanel {
                val age = (state.day - report.day).coerceAtLeast(0)
                Text(report.name, color = PaleGold, fontWeight = FontWeight.Bold)
                Text(world.faction(report.factionId)?.name ?: "Unbekannte Fraktion", color = Mist, fontSize = 12.sp)
                Text(
                    if (report.exact) "${report.minimum} Soldaten (gesichert am Berichtstag)"
                    else "${report.minimum}–${report.maximum} Soldaten (Schätzung)",
                    color = if (age > 0) Mist else Danger,
                )
                Text("${placeName(report.regionId)} · Bericht von Tag ${report.day}", color = Mist, fontSize = 12.sp)
                Text(if (age == 0) "Aktuelle Beobachtung" else "$age Tage alt · Stärke und Position können sich geändert haben", color = if (age == 0) Success else Gold, fontSize = 12.sp)
                ownArmies.filter { it.missionId == null && it.regionId == report.regionId && it.status.isAway && age == 0 }.forEach { army ->
                    OutlinedButton(
                        onClick = {
                            val result = WorldEngine.engage(state, army.id, report.armyId)
                            onState(result.state); onNotice(result.message)
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = state.battleSession?.isActive != true,
                    ) { Text("Mit ${army.name} abfangen") }
                }
            }
        }
        val convoys = world.convoys.filter { it.factionId == PLAYER_FACTION }.takeLast(8).asReversed()
        if (convoys.isNotEmpty()) item { SectionTitle("Nachschublinien") }
        items(convoys, key = { "convoy_${it.id}" }) { convoy ->
            CampaignPanel {
                Text("${convoy.food} Nahrung → ${world.armies.firstOrNull { it.id == convoy.destinationArmyId && it.factionId == PLAYER_FACTION }?.name ?: "Feldheer"}", color = PaleGold, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        convoy.lost -> "Konvoi verloren"
                        convoy.complete -> "Vorräte ausgeliefert"
                        else -> "${placeName(convoy.regionId)} · Ankunft an Tag ${convoy.arrivalDay}"
                    }, color = if (convoy.lost) Danger else if (convoy.complete) Success else Mist, fontSize = 12.sp,
                )
            }
        }
        if (knownFactions.isNotEmpty()) {
            item { SectionTitle("Reiche & Ressourcen") }
            item {
                Text("Öffentliche Haushaltsberichte: Rekrutierung kostet Gold, Eisen und Bewohner und benötigt Ausbildungszeit. Nahrung versorgt die Heere; Gold finanziert Unterhalt und Ausbau. Begrenzte Vorräte bestimmen, wie lange ein Reich Krieg führen kann.", color = Mist, fontSize = 12.sp)
            }
        }
        items(knownFactions, key = { "faction_${it.id}" }) { faction ->
            CampaignPanel {
                val player = faction.id == PLAYER_FACTION
                Text(faction.name, color = PaleGold, fontWeight = FontWeight.Bold)
                Text("${faction.ruler} · ${faction.personality.label}", color = Gold, fontSize = 12.sp)
                Text("Hauptstadt: ${placeName(faction.capitalId)}", color = Mist, fontSize = 12.sp)
                StatGrid(listOf(
                    "Gold" to "${if (player) state.resources.gold else faction.gold}",
                    "Nahrung" to "${if (player) state.resources.food else faction.food}",
                    "Eisen" to "${if (player) state.resources.iron else faction.iron}",
                    "Bevölkerung" to "${if (player) state.population.total else faction.population}",
                ))
                val pending = world.recruitments.filter { it.factionId == faction.id }.sortedWith(compareBy({ it.completionDay }, { it.id }))
                if (pending.isEmpty()) Text("Keine offenen Feldheer-Rekrutierungen", color = Mist, fontSize = 12.sp)
                pending.forEach { recruitment ->
                    val targetName = ownArmies.firstOrNull { it.id == recruitment.armyId }?.name
                        ?: reports.firstOrNull { it.armyId == recruitment.armyId }?.name
                    Text("${recruitment.amount} ${recruitment.type.label}", color = Color.White, fontSize = 13.sp)
                    Text("Ausbildung bis Tag ${recruitment.completionDay} · ${(recruitment.completionDay - state.day).coerceAtLeast(0)} Tage verbleiben", color = Gold, fontSize = 12.sp)
                    Text(targetName?.let { "Verstärkung für $it" } ?: "Zielheer noch nicht aufgeklärt", color = Mist, fontSize = 12.sp)
                }
            }
        }
        val decisions = state.activeMissions.filter { it.status == MissionStatus.ACTIVE && it.pendingDecision != null }
        if (decisions.isNotEmpty()) item { SectionTitle("Missionsentscheidungen") }
        items(decisions, key = { "decision_${it.id}" }) { mission ->
            mission.pendingDecision?.let { decision ->
                CampaignPanel {
                    Text(decision.title, color = PaleGold, fontWeight = FontWeight.Bold)
                    Text("${mission.missionType.label} · ${mission.total} Soldaten", color = Gold, fontSize = 12.sp)
                    Text(decision.text, color = Mist)
                    decision.options.forEachIndexed { choice, label ->
                        SmallAction(label) {
                            val result = MissionEngine.chooseRoute(state, mission.id, choice)
                            onState(result.state); onNotice(result.message)
                        }
                    }
                }
            }
        }
        val stories = world.stories.filter { !it.resolved && it.options.isNotEmpty() }
        if (stories.isNotEmpty()) item { SectionTitle("Geschichten der Welt") }
        items(stories, key = { "story_${it.id}" }) { story ->
            CampaignPanel {
                Text(story.title, color = PaleGold, fontWeight = FontWeight.Bold)
                Text(story.text, color = Mist)
                story.options.forEachIndexed { choice, label ->
                    SmallAction(label) {
                        val result = WorldEngine.chooseStory(state, story.id, choice)
                        onState(result.state); onNotice(result.message)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    dispatchDestinationId?.let { destinationId ->
        CampaignDispatchDialog(state, destinationId, { dispatchDestinationId = null }, onState, onNotice)
    }
    redirectArmyId?.let { armyId ->
        ownArmies.firstOrNull { it.id == armyId }?.let { army ->
            CampaignRouteDialog(state, army, { redirectArmyId = null }, onState, onNotice)
        }
    }
    convoyArmyId?.let { armyId ->
        CampaignFoodDialog("Nachschubkonvoi", "Ein Konvoi kostet 100 Gold und die gewählte Nahrung. Vorräte reisen zur Armee und können auf feindlichen Straßen verloren gehen.", state.resources.food, 1000, { convoyArmyId = null }, canConfirm = state.resources.gold >= 100 && state.battleSession?.isActive != true) { food ->
            val result = WorldEngine.sendConvoy(state, armyId, food)
            onState(result.state); onNotice(result.message)
            if (result.state != state) convoyArmyId = null
        }
    }
    depotRegionId?.let { regionId ->
        CampaignFoodDialog("Versorgungslager", "Der Bau kostet 300 Gold und 150 Holz. Ein eigenes Depot versorgt vorbeiziehende Heere; die gewählte Nahrung wird aus deinem Vorrat eingelagert.", state.resources.food, 2000, { depotRegionId = null }, maxFood = 10000, canConfirm = state.resources.gold >= 300 && state.resources.wood >= 150 && state.battleSession?.isActive != true) { food ->
            val result = WorldEngine.buildDepot(state, regionId, food)
            onState(result.state); onNotice(result.message)
            if (result.state != state) depotRegionId = null
        }
    }
    missionRegion?.let { region -> region.mission?.let { mission ->
        MissionPreparationDialog(state, mission, region, { missionRegion = null }, onState, onNotice)
    } }
}

@Composable
private fun CampaignDispatchDialog(
    state: GameState,
    destinationId: String,
    onDismiss: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var commanderId by remember { mutableStateOf<Long?>(null) }
    var counts by remember(commanderId) { mutableStateOf(emptyMap<UnitType, Int>()) }
    var policy by remember { mutableStateOf(MarchPolicy.NORMAL) }
    var supplyDays by remember { mutableStateOf(6) }
    val available = UnitType.entries.associateWith { MissionEngine.available(state, commanderId, it) }
    val allocations = UnitType.entries.mapNotNull { type ->
        val count = (counts[type] ?: 0).coerceIn(0, available.getValue(type))
        if (count > 0) UnitAllocation(type, count) else null
    }
    val preview = WorldArmy("preview", PLAYER_FACTION, "Marschplanung", allocations, "keep", commanderId = commanderId, marchPolicy = policy)
    val route = WorldEngine.route(state.world, "keep", destinationId)
    val travel = WorldEngine.travelDays(state, preview, route)
    val supply = preview.dailyFood.toLong() * supplyDays
    val total = allocations.sumOf { it.amount.toLong() }
    val commanders = listOf<Long?>(null) + state.commanders.filterNot { state.commanderAway(it.id) }.map { it.id }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(), containerColor = Ink,
            topBar = {
                Surface(color = Panel) {
                    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("Zurück", color = Gold) }
                        Text("FELDHEER AUFSTELLEN", color = PaleGold, fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = {
                Surface(color = Panel, modifier = Modifier.navigationBarsPadding()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$total Soldaten · $travel Tage Marsch · $supply Nahrung", color = Gold, fontSize = 13.sp)
                        Button(
                            enabled = total >= 30 && total <= Int.MAX_VALUE.toLong() && supply <= state.resources.food && route.size >= 2 && destinationId in state.world.knowledgeFor(PLAYER_FACTION).exploredRegions && state.battleSession?.isActive != true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                            onClick = {
                                val result = WorldEngine.dispatch(state, destinationId, commanderId, allocations, policy, supplyDays)
                                onState(result.state); onNotice(result.message)
                                if (result.state.world.armies.any { launched -> state.world.armies.none { it.id == launched.id } }) onDismiss()
                            },
                        ) { Text("HEER ENTSENDEN", fontWeight = FontWeight.Bold) }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    val destination = state.world.place(destinationId)
                    val known = destination?.let { it.ownerId == PLAYER_FACTION || it.id in state.world.knowledgeFor(PLAYER_FACTION).exploredRegions || it.id in state.world.knowledgeFor(PLAYER_FACTION).visibleRegions } == true
                    PageTitle("MARSCHPLANUNG", if (known) destination?.name ?: "Ziel" else "Unkartiertes Gebiet")
                    Text("Entsandte Soldaten fehlen bis zur Heimkehr in der Festung. Wetter, Gelände, Vorräte und Marschtempo bestimmen Ankunft und Verluste.", color = Mist, fontSize = 12.sp)
                }
                item {
                    CampaignSelection("Kommando", state.commanders.firstOrNull { it.id == commanderId }?.name ?: "Persönliches Kommando", commanders, { id -> state.commanders.firstOrNull { it.id == id }?.name ?: "Persönliches Kommando" }) { commanderId = it }
                }
                item {
                    CampaignSelection("Marschtempo", policy.label, MarchPolicy.entries, { it.label }) { policy = it }
                    Text("Sicher: langsamer, geringe Marschverluste. Gewaltmarsch: schneller, zusätzliche Erschöpfung und Verluste.", color = Mist, fontSize = 12.sp)
                }
                item {
                    CampaignSelection("Vorräte mitnehmen", "$supplyDays Tage", listOf(3, 6, 10, 15), { "$it Tage" }) { supplyDays = it }
                    Text("$supply / ${state.resources.food} Nahrung · täglicher Verbrauch ${preview.dailyFood}", color = if (supply > state.resources.food) Danger else Success, fontSize = 12.sp)
                    if (supplyDays < travel) Text("Die Vorräte reichen nicht für den gesamten Weg. Sichere Depots oder schicke Nachschub.", color = Gold, fontSize = 12.sp)
                }
                item { SectionTitle("Verfügbare Einheiten") }
                item { Text("Mindestens 30 Soldaten benötigt.", color = Mist, fontSize = 12.sp) }
                items(UnitType.entries.filter { available.getValue(it) > 0 }) { type ->
                    CampaignPanel { TroopCountPicker(type.label, available.getValue(type), counts[type] ?: 0) { counts = counts + (type to it) } }
                }
                if (available.values.all { it == 0 }) item { EmptyCard("Dieses Kommando verfügt über keine freien Soldaten. Prüfe die Zuweisungen im Armeemenü.") }
            }
        }
    }
}

@Composable
private fun CampaignRouteDialog(
    state: GameState,
    army: WorldArmy,
    onDismiss: () -> Unit,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    var destinationId by remember(army.id) { mutableStateOf(army.destinationId ?: army.regionId) }
    var policy by remember(army.id) { mutableStateOf(army.marchPolicy) }
    val knowledge = state.world.knowledgeFor(PLAYER_FACTION)
    val destinations = state.world.places.filter { it.id != army.regionId && it.id in knowledge.exploredRegions }
    fun label(place: WorldPlace) = if (place.ownerId == PLAYER_FACTION || place.id in knowledge.exploredRegions || place.id in knowledge.visibleRegions) place.name else "Unkartiertes Gebiet ${state.world.places.indexOf(place) + 1}"
    val route = WorldEngine.route(state.world, army.regionId, destinationId)
    val travel = WorldEngine.travelDays(state, army.copy(marchPolicy = policy), route)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marschbefehle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(army.name, color = Gold)
                CampaignSelection("Ziel", state.world.place(destinationId)?.let { label(it) } ?: "Ziel wählen", destinations, { label(it) }) { destinationId = it.id }
                CampaignSelection("Marschtempo", policy.label, MarchPolicy.entries, { it.label }) { policy = it }
                Text("Voraussichtlich $travel Tage · ${army.supplyDays} Tage Vorräte", color = Mist)
                Text("Ein neuer Befehl ändert den Weg ab der aktuellen Region. Vorräte werden nicht aufgefüllt.", color = Mist, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(enabled = route.isNotEmpty() && destinationId != army.regionId && state.battleSession?.isActive != true, onClick = {
                val result = WorldEngine.redirect(state, army.id, destinationId, policy)
                onState(result.state); onNotice(result.message)
                if (result.state != state) onDismiss()
            }) { Text("Befehl geben") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zurück") } },
    )
}

@Composable
private fun CampaignFoodDialog(
    title: String,
    explanation: String,
    available: Int,
    initial: Int,
    onDismiss: () -> Unit,
    maxFood: Int = available,
    canConfirm: Boolean = true,
    onConfirm: (Int) -> Unit,
) {
    val limit = minOf(available, maxFood).coerceAtLeast(0)
    var food by remember { mutableStateOf(initial.coerceIn(0, limit)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explanation, color = Mist)
                OutlinedTextField(
                    value = food.toString(),
                    onValueChange = { input -> food = (input.filter(Char::isDigit).take(10).toLongOrNull() ?: 0L).coerceIn(0L, limit.toLong()).toInt() },
                    label = { Text("Nahrung") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("$available Nahrung im Vorrat", color = Gold, fontSize = 12.sp)
                if (limit < available) Text("Maximal $limit Nahrung für diesen Auftrag", color = Mist, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(enabled = canConfirm && food > 0 && food <= limit, onClick = { onConfirm(food) }) { Text("Versorgen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zurück") } },
    )
}

private data class CampaignRegionMarker(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val owned: Boolean,
    val known: Boolean,
    val description: String,
)

private data class CampaignArmyMarker(
    val x: Float,
    val y: Float,
    val player: Boolean,
    val stale: Boolean,
)

/** Each map marker has its own accessible touch target; the region list repeats every target. */
@Composable
private fun CampaignMapScene(
    regions: List<CampaignRegionMarker>,
    roads: List<Pair<String, String>>,
    armies: List<CampaignArmyMarker>,
    playerHeraldry: Heraldry,
    selectedRegionId: String?,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(18.dp))
            .background(Panel),
    ) {
        AsyncImage(
            model = "file:///android_asset/world_map.webp",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Ink.copy(alpha = .48f))
            fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
            roads.forEach { (startId, endId) ->
                val start = regions.firstOrNull { it.id == startId }
                val end = regions.firstOrNull { it.id == endId }
                if (start != null && end != null) {
                    val known = start.known && end.known
                    drawLine(
                        if (known) PaleGold.copy(alpha = .52f) else Mist.copy(alpha = .15f),
                        point(start.x, start.y), point(end.x, end.y), strokeWidth = 3.dp.toPx(),
                    )
                }
            }
            regions.forEach { region ->
                val center = point(region.x, region.y)
                val color = when {
                    region.id == selectedRegionId -> Gold
                    region.owned -> Success
                    region.known -> Blue
                    else -> Mist.copy(alpha = .35f)
                }
                drawCircle(color.copy(alpha = .12f), 29.dp.toPx(), center)
                drawCircle(color.copy(alpha = .75f), 23.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            }
            armies.forEach { army ->
                val position = point(army.x, army.y) + Offset(18.dp.toPx(), -22.dp.toPx())
                if (army.player) {
                    drawHeraldry(playerHeraldry, position, 10.dp.toPx())
                    return@forEach
                }
                val color = if (army.player) Success else if (army.stale) Mist else Danger
                val flag = Path().apply {
                    moveTo(position.x, position.y)
                    lineTo(position.x + 15.dp.toPx(), position.y + 5.dp.toPx())
                    lineTo(position.x, position.y + 10.dp.toPx())
                    close()
                }
                drawLine(color, position, position + Offset(0f, 18.dp.toPx()), 2.dp.toPx())
                drawPath(flag, color.copy(alpha = if (army.stale) .55f else 1f))
            }
            drawRect(Gold.copy(alpha = .35f), style = Stroke(1.dp.toPx()))
        }
        regions.forEachIndexed { index, region ->
            Surface(
                onClick = { onSelect(region.id) },
                modifier = Modifier.offset(x = maxWidth * region.x - 24.dp, y = maxHeight * region.y - 24.dp)
                    .size(48.dp).semantics { contentDescription = region.description },
                color = if (region.id == selectedRegionId) Gold else Panel.copy(alpha = .93f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (region.owned) Success else Gold.copy(alpha = .6f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}", color = if (region.id == selectedRegionId) Ink else PaleGold,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            color = Ink.copy(alpha = .88f),
        ) {
            Text(
                "Banner: eigenes Heer · Grün: eigenes Gebiet · Rot: fremdes Heer · Grau: alter Bericht",
                color = Mist, fontSize = 10.sp, modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun CampaignPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun <T> CampaignSelection(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Gold, fontSize = 12.sp)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}
