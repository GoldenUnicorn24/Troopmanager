package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goldenunicorn.troopmanager.engine.MissionEngine
import com.goldenunicorn.troopmanager.engine.OriginEngine
import com.goldenunicorn.troopmanager.engine.OccupationEngine
import com.goldenunicorn.troopmanager.engine.WorldEngine
import com.goldenunicorn.troopmanager.engine.DiplomacyEngine
import com.goldenunicorn.troopmanager.engine.SiegeEngine
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampaignMapScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    val world = state.world
    val knowledge = world.knowledgeFor(PLAYER_FACTION)
    var selectedRegionId by remember { mutableStateOf(world.places.firstOrNull()?.id) }
    var showRegion by remember { mutableStateOf(false) }
    var showRegionList by remember { mutableStateOf(false) }
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
    } + reports.filterNot { observation ->
        state.frontier.hordes.any { it.discovered && it.worldArmyId == observation.armyId }
    }.mapNotNull { observation ->
        markers.firstOrNull { it.id == observation.regionId }?.let {
            CampaignArmyMarker(it.x, it.y, false, observation.day < state.day)
        }
    }
    val knownHordes = state.frontier.hordes.filter { it.discovered }
    val frontierMarkers = knownHordes.mapNotNull { horde ->
        val region = markers.firstOrNull { it.id == horde.regionId } ?: return@mapNotNull null
        CampaignFrontierMarker(region.x, region.y, horde.kind == HordeKind.TAO_TEI, horde.name)
    }
    val aidRoutes = state.frontier.reinforcements.mapNotNull { aid ->
        val origin = markers.firstOrNull { it.id == aid.originRegionId } ?: return@mapNotNull null
        val home = markers.firstOrNull { it.id == "keep" } ?: markers.firstOrNull { it.owned } ?: return@mapNotNull null
        val progress = if (aid.departureDay > 0 && aid.arrivalDay > aid.departureDay)
            ((state.day - aid.departureDay).toFloat() / (aid.arrivalDay - aid.departureDay)).coerceIn(0f, 1f) else null
        CampaignAidRoute(origin.x, origin.y, home.x, home.y, aid.people.label, progress)
    }
    val convoyRoutes = world.convoys.filter { it.factionId == PLAYER_FACTION && !it.complete && !it.lost }.mapNotNull { convoy ->
        val current = markers.firstOrNull { it.id == convoy.regionId } ?: return@mapNotNull null
        val nextId = convoy.route.getOrNull(convoy.routeIndex + 1)
            ?: world.armies.firstOrNull { it.id == convoy.destinationArmyId }?.regionId
        val next = markers.firstOrNull { it.id == nextId } ?: current
        CampaignConvoyRoute(current.x, current.y, next.x, next.y, convoy.food, convoy.id)
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
                armyMarkers, frontierMarkers, aidRoutes, convoyRoutes, state.frontier.outposts, state.presentation.heraldry, selectedRegion?.id,
            ) { selectedRegionId = it; showRegion = true }
        }
        if (knownHordes.isNotEmpty() || state.frontier.reinforcements.isNotEmpty()) item {
            CampaignPanel {
                Text("FRONTIER & VERBÜNDETE", color = PaleGold, fontWeight = FontWeight.Bold)
                knownHordes.forEach { horde ->
                    val estimate = if (horde.estimateMinimum > 0 && horde.estimateMaximum > 0)
                        "${horde.estimateMinimum}–${horde.estimateMaximum} Gegner geschätzt" else "Stärke noch unklar"
                    Text("${horde.name} · $estimate · Ankunft in ${horde.daysToArrival} Tagen", color = Danger, fontSize = 12.sp)
                    if (horde.kind.hostileToAll) Text("Tao Tei bedrohen auch fremde Reiche.", color = Mist, fontSize = 11.sp)
                }
                state.frontier.reinforcements.forEach { aid ->
                    Text("${aid.amount} ${aid.type.label} aus ${aid.origin} · unterwegs, noch ${aid.daysRemaining} Tage", color = Gold, fontSize = 12.sp)
                }
                Text("Rote Rauten: entdeckte Horden · goldene Routen: Hilfe vom Verbündeten zur Heimat", color = Mist, fontSize = 11.sp)
            }
        }
        item { ModernChoice(showRegionList, { showRegionList = !showRegionList }, { Text("Alle Orte als Liste") }) }
        if (showRegionList) items(markers, key = { "region_${it.id}" }) { marker ->
            val number = markers.indexOf(marker) + 1
            Surface(
                onClick = { selectedRegionId = marker.id; showRegion = true },
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
                PremiumPanel(onClick = { showRegion = true }) {
                    Text(if (isKnown(region)) region.name else "Unkartiertes Gebiet", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Region, Außenposten, Stationierungen und Aktionen öffnen →", color = ModernBlue)
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
                army.lastSupplyOutpostId?.let { postId -> state.frontier.outposts.firstOrNull { it.id == postId }?.let { post ->
                    val corridor = WorldEngine.route(world, post.regionId, army.regionId).orEmpty()
                    Text("Letzte Außenpostenversorgung: ${post.name} · ${post.suppliedFood} Nahrung insgesamt", color = ModernBlue, fontSize = 12.sp)
                    if (corridor.isNotEmpty()) Text("Straßenverbindung: ${corridor.joinToString(" → ") { placeName(it) }} · Nachschub benötigt Vorräte am Ort oder einen Konvoi.", color = Mist, fontSize = 11.sp)
                } }
                if (army.delayUntilDay > state.day) Text("Außenpostenkampf: aufgehalten bis Tag ${army.delayUntilDay}", color = Danger)
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
                val uncertainty = (age * 6).coerceAtMost(45)
                val agedMin = (report.minimum * (100 - uncertainty) / 100).coerceAtLeast(0)
                val agedMax = (report.maximum.toLong() * (100 + uncertainty) / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val confidence = when {
                    report.exact && age == 0 -> "Gesichert"
                    age <= 1 -> "Hoch"
                    age <= 3 -> "Gut"
                    age <= 7 -> "Unsicher"
                    else -> "Veraltet"
                }
                Text(
                    if (age == 0 && report.exact) "${report.minimum} Soldaten (gesichert)"
                    else "$agedMin–$agedMax mögliche Stärke · Vertrauen: $confidence",
                    color = if (age == 0) Danger else Mist,
                )
                Text("${placeName(report.regionId)} · Bericht von Tag ${report.day}", color = Mist, fontSize = 12.sp)
                Text(
                    when {
                        age == 0 -> "Aktuelle Beobachtung"
                        age >= 5 && world.faction(report.factionId)?.personality in setOf(FactionPersonality.CUNNING, FactionPersonality.PARANOID) ->
                            "$age Tage alt · mögliche Täuschung oder veraltete Marschrichtung"
                        else -> "$age Tage alt · Unsicherheit wächst täglich; Stärke und Position können sich geändert haben"
                    },
                    color = if (age == 0) Success else Gold,
                    fontSize = 12.sp,
                )
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
    if (showRegion) selectedRegion?.let { region ->
        ModalBottomSheet(onDismissRequest = { showRegion = false }, containerColor = Panel,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                RegionCampaignPanel(state, region, onState, onNotice,
                    { showRegion = false; dispatchDestinationId = region.id },
                    { showRegion = false; depotRegionId = region.id },
                    { showRegion = false; missionRegion = it })
            }
        }
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
private fun RegionCampaignPanel(state: GameState, region: WorldPlace, onState: (GameState) -> Unit,
    onNotice: (String) -> Unit, onDispatch: () -> Unit, onDepot: () -> Unit, onMission: (WorldRegion) -> Unit) {
    val world = state.world
    val knowledge = world.knowledgeFor(PLAYER_FACTION)
    val known = region.ownerId == PLAYER_FACTION || region.id in knowledge.exploredRegions || region.id in knowledge.visibleRegions
    val visible = region.ownerId == PLAYER_FACTION || region.id in knowledge.visibleRegions
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
        if (known) {
            val terrain = WorldEngine.terrainFor(region.terrain)
            Text("Gefechtsgelände: ${terrain.label} · aktive Frontbreite ${SiegeEngine.terrainFrontage(terrain)}", color = ModernBlue)
            Text("Lokaler Ruf ${(world.regionReputation[region.id] ?: 0)} · erfolgreiche Expeditionen machen weitere Einsätze sicherer.", color = Mist)
            if (visible && region.ownerId !in listOf(PLAYER_FACTION, NEUTRAL_FACTION)) {
                val relation = DiplomacyEngine.relation(state, PLAYER_FACTION, region.ownerId)
                Text("${if (relation.atWar) "Krieg" else "Frieden"} · Beziehung ${relation.relation} · Vertrauen ${relation.trust}", color = Mist)
            }
            state.frontier.outposts.filter { it.regionId == region.id }.forEach { post ->
                val guard = world.armies.firstOrNull { it.id == post.garrisonArmyId && it.regionId == post.regionId && it.status == WorldArmyStatus.HOLDING }
                Text("${post.name} · ${post.role} · ${post.integrity}% · ${post.stores}/${post.capacity} Nahrung", color = if (post.integrity == 0) Danger else Success)
                Text("Garnison ${guard?.total ?: 0} · bisher ${post.suppliedFood} Nahrung ausgeliefert", color = Mist)
                if (post.lastDefense.isNotBlank()) Text(post.lastDefense, color = Mist)
            }
            world.armies.filter { it.factionId == PLAYER_FACTION && it.regionId == region.id && it.status.isAway }.forEach {
                Text("Stationiert: ${it.name} · ${it.total} Soldaten · ${it.supplyDays} Tage Vorräte", color = Success)
            }
            knowledge.observations.filter { it.regionId == region.id && it.factionId != PLAYER_FACTION }.forEach {
                Text("Kontakt: ${it.name} · ${it.minimum}–${it.maximum} · Bericht Tag ${it.day}", color = Danger)
            }
            state.regions.firstOrNull { it.id == region.id }?.let { legacy ->
                val yield = EconomyEngine.regionalYield(legacy.type)
                Text("Gebietsbeitrag vor Arbeiterfaktor: ${yield.gold} Gold · ${yield.wood} Holz · ${yield.iron} Eisen/Tag${if (region.ownerId != PLAYER_FACTION) " bei eigener Kontrolle" else ""}", color = Mist)
            }
        }
        val depots = world.depots.filter { it.regionId == region.id && it.factionId == PLAYER_FACTION }
        depots.forEach { depot -> Text("Versorgungslager: ${depot.food} / ${depot.capacity} Nahrung", color = Success, fontSize = 12.sp) }
        state.occupations.firstOrNull { it.regionId == region.id }?.let { occupation ->
            val garrison =
                world.armies.filter {
                    it.factionId == PLAYER_FACTION &&
                        it.regionId == region.id &&
                        it.status != WorldArmyStatus.DESTROYED
                }.sumOf { it.total }
            HorizontalDivider(color = Gold.copy(alpha = .35f))
            Text("BESATZUNGSVERWALTUNG", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(
                "Unruhe ${occupation.unrest}% · Garnison $garrison · ${occupation.policy.label}",
                color = if (occupation.unrest >= 70) Danger else if (occupation.unrest <= 25) Success else Mist,
                fontSize = 12.sp,
            )
            Text(occupation.policy.description, color = Mist, fontSize = 11.sp)
            OccupationPolicy.entries.forEach { policy ->
                if (policy != occupation.policy)
                    OutlinedButton(
                        onClick = {
                            val result = OccupationEngine.setPolicy(state, region.id, policy)
                            onState(result.state)
                            onNotice(result.message)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) { Text(policy.label) }
            }
        }
        val scoutGold =
            kotlin.math.ceil(75 * OriginEngine.scoutingCostFactor(state)).toInt()
        OutlinedButton(
            onClick = {
                val result = WorldEngine.scout(state, region.id)
                onState(result.state); onNotice(result.message)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = state.battleSession?.isActive != true &&
                state.resources.gold >= scoutGold &&
                state.resources.food >= 100 &&
                (region.id in knowledge.exploredRegions || world.roads.any { it.connects(region.id) && it.other(region.id) in knowledge.exploredRegions }),
        ) { Text("Kundschafter · $scoutGold Gold / 100 Nahrung") }
        Button(
            enabled = state.homeArmySize >= 30 && state.battleSession?.isActive != true && region.id != "keep" && region.id in knowledge.exploredRegions,
            onClick = { onDispatch() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
        ) { Text("Feldheer hierhin entsenden") }
        if (!known) Text("Kundschafter erreichen bekannte Orte und ihre Nachbarn. Ein Heer benötigt zuerst ein aufgeklärtes Ziel.", color = Mist, fontSize = 12.sp)
        if (region.ownerId == PLAYER_FACTION && depots.isEmpty()) OutlinedButton(
            onClick = { onDepot() },
            enabled = state.battleSession?.isActive != true && state.resources.gold >= 300 && state.resources.wood >= 150 && state.resources.food > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Depot bauen · 300 Gold / 150 Holz") }
        if (known) state.regions.firstOrNull { it.id == region.id && it.mission != null }?.let { legacy ->
            SmallAction("${legacy.mission?.label} vorbereiten") { onMission(legacy) }
        }
    }
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

private data class CampaignFrontierMarker(val x: Float, val y: Float, val taoTei: Boolean, val name: String)
private data class CampaignAidRoute(val originX: Float, val originY: Float, val targetX: Float, val targetY: Float, val name: String, val progress: Float?)
private data class CampaignConvoyRoute(val x: Float, val y: Float, val targetX: Float, val targetY: Float, val food: Int, val id: String)

/** Each map marker has its own accessible touch target; the region list repeats every target. */
@Composable
private fun CampaignMapScene(
    regions: List<CampaignRegionMarker>,
    roads: List<Pair<String, String>>,
    armies: List<CampaignArmyMarker>,
    frontier: List<CampaignFrontierMarker>,
    aidRoutes: List<CampaignAidRoute>,
    convoys: List<CampaignConvoyRoute>,
    outposts: List<FrontierOutpost>,
    playerHeraldry: Heraldry,
    selectedRegionId: String?,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(390.dp).clip(RoundedCornerShape(22.dp))
            .background(Panel).semantics {
                contentDescription = "Kampagnenkarte. " + frontier.joinToString { "${it.name}, entdeckte Horde" } +
                    aidRoutes.joinToString(prefix = ". ") { "Hilfsroute von ${it.name} zur Heimat" } +
                    convoys.joinToString(prefix = ". ") { "Nachschubkonvoi mit ${it.food} Nahrung" }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)

            // v0.96: one coherent campaign map instead of markers floating above a static picture.
            // The terrain is generated in the same coordinate space as regions, roads and armies.
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF17242A), Color(0xFF24362F), Color(0xFF151C20))
                )
            )

            // Large terrain masses give the map readable geography without pretending to be an
            // exact illustrated world that does not match the simulated regions.
            repeat(7) { i ->
                val cx = size.width * (.12f + (i * .137f % .78f))
                val cy = size.height * (.14f + ((i * 37) % 67) / 100f)
                val rw = size.width * (.25f + (i % 3) * .04f)
                val rh = size.height * (.18f + (i % 2) * .07f)
                drawOval(
                    color = if (i % 3 == 0) Color(0xFF405340) else Color(0xFF35483D),
                    topLeft = Offset(cx - rw / 2f, cy - rh / 2f),
                    size = Size(rw, rh),
                )
            }

            // Rivers are part of the map layer and sit below roads/units.
            val river = Path().apply {
                moveTo(size.width * .02f, size.height * .28f)
                cubicTo(
                    size.width * .23f, size.height * .18f,
                    size.width * .37f, size.height * .48f,
                    size.width * .55f, size.height * .39f,
                )
                cubicTo(
                    size.width * .72f, size.height * .29f,
                    size.width * .82f, size.height * .58f,
                    size.width * .99f, size.height * .52f,
                )
            }
            drawPath(river, Color(0xFF3C6574), style = Stroke(13.dp.toPx()))
            drawPath(river, Color(0xFF6E94A0).copy(alpha = .38f), style = Stroke(3.dp.toPx()))

            // Local terrain cues cluster around actual regions so the geography belongs to the
            // simulation rather than to a decorative background image.
        regions.forEachIndexed { index, region ->
                val center = point(region.x, region.y)
                val patch = when (index % 4) {
                    0 -> Color(0xFF556343)
                    1 -> Color(0xFF6A6044)
                    2 -> Color(0xFF465C4F)
                    else -> Color(0xFF66584A)
                }
                drawCircle(patch.copy(alpha = if (region.known) .36f else .18f), 46.dp.toPx(), center)
                if (index % 4 == 0) {
                    repeat(5) { n ->
                        val dx = ((n * 17 % 41) - 20).dp.toPx()
                        val dy = ((n * 23 % 37) - 18).dp.toPx()
                        drawCircle(Color(0xFF294232), 5.dp.toPx(), center + Offset(dx, dy))
                        drawLine(
                            Color(0xFF66543C),
                            center + Offset(dx, dy + 4.dp.toPx()),
                            center + Offset(dx, dy + 10.dp.toPx()),
                            2.dp.toPx(),
                        )
                    }
                } else if (index % 4 == 1) {
                    repeat(3) { n ->
                        val base = center + Offset((n - 1) * 12.dp.toPx(), 7.dp.toPx())
                        val mountain = Path().apply {
                            moveTo(base.x - 12.dp.toPx(), base.y + 10.dp.toPx())
                            lineTo(base.x, base.y - 13.dp.toPx())
                            lineTo(base.x + 12.dp.toPx(), base.y + 10.dp.toPx())
                            close()
                        }
                        drawPath(mountain, Color(0xFF777462))
                        drawLine(
                            Color.White.copy(alpha = .23f),
                            base,
                            base + Offset(0f, -8.dp.toPx()),
                            2.dp.toPx(),
                        )
                    }
                }
            }

            roads.forEach { (startId, endId) ->
                val start = regions.firstOrNull { it.id == startId }
                val end = regions.firstOrNull { it.id == endId }
                if (start != null && end != null) {
                    val a = point(start.x, start.y)
                    val b = point(end.x, end.y)
                    val known = start.known && end.known
                    val mid = Offset(
                        (a.x + b.x) / 2f,
                        (a.y + b.y) / 2f - size.height * .035f,
                    )
                    val route = Path().apply {
                        moveTo(a.x, a.y)
                        quadraticBezierTo(mid.x, mid.y, b.x, b.y)
                    }
                    drawPath(route, Color(0xFF171C1C).copy(alpha = .75f), style = Stroke(7.dp.toPx()))
                    drawPath(
                        route,
                        if (known) PaleGold.copy(alpha = .55f) else Mist.copy(alpha = .12f),
                        style = Stroke(if (known) 2.4.dp.toPx() else 1.5.dp.toPx()),
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
                drawCircle(Color.Black.copy(alpha = .35f), 31.dp.toPx(), center)
                drawCircle(color.copy(alpha = .16f), 28.dp.toPx(), center)
                drawCircle(color.copy(alpha = .85f), 22.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                if (!region.known) {
                    drawCircle(Ink.copy(alpha = .68f), 26.dp.toPx(), center)
                    drawCircle(Mist.copy(alpha = .32f), 21.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                }
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
            aidRoutes.forEach { route ->
                val origin = point(route.originX, route.originY)
                val target = point(route.targetX, route.targetY)
                drawLine(Gold.copy(alpha = .85f), origin, target, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
                drawCircle(Gold, 4.dp.toPx(), origin + Offset(-24.dp.toPx(), -20.dp.toPx()))
                val source = if (route.progress != null) origin + (target - origin) * route.progress else origin
                val diamond = Path().apply {
                    moveTo(source.x, source.y - 6.dp.toPx())
                    lineTo(source.x + 6.dp.toPx(), source.y)
                    lineTo(source.x, source.y + 6.dp.toPx())
                    lineTo(source.x - 6.dp.toPx(), source.y)
                    close()
                }
                drawPath(diamond, Gold)
            }
            convoys.forEach { convoy ->
                val start = point(convoy.x, convoy.y)
                val end = point(convoy.targetX, convoy.targetY)
                drawLine(
                    Blue.copy(alpha = .9f),
                    start,
                    end,
                    2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val cart = start + (end - start) * .45f
                drawRoundRect(
                    Blue,
                    topLeft = cart - Offset(6.dp.toPx(), 4.dp.toPx()),
                    size = Size(12.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                drawCircle(Ink, 2.dp.toPx(), cart + Offset(-3.dp.toPx(), 5.dp.toPx()))
                drawCircle(Ink, 2.dp.toPx(), cart + Offset(3.dp.toPx(), 5.dp.toPx()))
            }
            outposts.forEach { post ->
                val region = regions.firstOrNull { it.id == post.regionId } ?: return@forEach
                val position = point(region.x, region.y) + Offset(-24.dp.toPx(), -24.dp.toPx())
                val color = if (post.integrity == 0) Mist else ModernBlue
                drawRect(color, position, Size(12.dp.toPx(), 12.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawLine(color, position + Offset(-2.dp.toPx(), 15.dp.toPx()), position + Offset(14.dp.toPx() * (post.stores.toFloat()/post.capacity).coerceIn(0f,1f), 15.dp.toPx()), 2.dp.toPx())
            }
            frontier.forEach { marker ->
                val position = point(marker.x, marker.y) + Offset(22.dp.toPx(), -25.dp.toPx())
                val color = if (marker.taoTei) Color(0xFFB16CCA) else Danger
                val banner = Path().apply {
                    moveTo(position.x, position.y - 10.dp.toPx())
                    lineTo(position.x + 7.dp.toPx(), position.y)
                    lineTo(position.x, position.y + 10.dp.toPx())
                    lineTo(position.x - 7.dp.toPx(), position.y)
                    close()
                }
                drawPath(banner, color)
                drawCircle(Ink, 2.dp.toPx(), position)
                drawLine(color, position + Offset(-3.dp.toPx(), -8.dp.toPx()), position + Offset(-7.dp.toPx(), -12.dp.toPx()), 2.dp.toPx())
                drawLine(color, position + Offset(3.dp.toPx(), -8.dp.toPx()), position + Offset(7.dp.toPx(), -12.dp.toPx()), 2.dp.toPx())
            }
            drawRect(Gold.copy(alpha = .35f), style = Stroke(1.dp.toPx()))
        }
        regions.firstOrNull { it.id == selectedRegionId }?.let { selected ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                color = Ink.copy(alpha = .88f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (selected.owned) Success.copy(alpha = .55f) else Gold.copy(alpha = .45f),
                ),
            ) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                    Text(selected.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        when {
                            selected.owned -> "Eigenes Gebiet"
                            selected.known -> "Aufgeklärte Region"
                            else -> "Unbekanntes Gebiet"
                        },
                        color = if (selected.owned) Success else if (selected.known) ModernBlue else Mist,
                        fontSize = 10.sp,
                    )
                }
            }
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
                "Quadrat: Außenposten · Banner: eigenes Heer · Blau gestrichelt: Nachschub · Gold: Verbündetenhilfe · Rot: Feind · Grau: alter Bericht",
                color = Mist, fontSize = 10.sp, modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun CampaignPanel(content: @Composable ColumnScope.() -> Unit) {
    PremiumPanel(content = content)
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
