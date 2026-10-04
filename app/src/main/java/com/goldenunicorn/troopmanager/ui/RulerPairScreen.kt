package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.engine.CoRulerEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.PresenceEngine
import com.goldenunicorn.troopmanager.engine.RelationshipEngine
import com.goldenunicorn.troopmanager.engine.RelationshipEventDirector
import com.goldenunicorn.troopmanager.engine.customizeCompanion
import com.goldenunicorn.troopmanager.model.*

/** One hub for the same companion, decisions and memories used by the campaign engines. */
@Composable
internal fun RulerPairScreen(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onCouncil: () -> Unit = {},
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("Überblick") }
    var memoryFilter by remember { mutableStateOf("Alle") }
    var albumMemoryIds by remember { mutableStateOf<Set<String>?>(null) }
    val event = state.relationship.pendingEvent
    var showDialogue by remember(event) { mutableStateOf(event != null) }
    val presence = PresenceEngine.presence(state)
    val sharedBlocker = PresenceEngine.sharedActivityBlocker(state)
    val personality = state.relationship.personality
    val activities = remember(state) { RelationshipEngine.activities(state) }
    var editCompanion by remember { mutableStateOf(false) }
    var companionName by remember(state.companion.name) { mutableStateOf(state.companion.name) }
    var companionAge by remember(state.companion.age) { mutableStateOf(state.companion.age.toString()) }
    var companionArmor by remember(state.companion.armorStyle) { mutableStateOf(state.companion.armorStyle) }
    var companionWeapon by remember(state.companion.weapon) { mutableStateOf(state.companion.weapon) }
    val companionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onState(GameEngine.updateCompanionIdentity(state, state.companion.name, uri.toString()))
            onNotice("Portrait der Gefährtin gespeichert.")
        }
    }
    val nightPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onState(state.copy(relationship = state.relationship.copy(nightSceneUri = uri.toString())))
            onNotice("Eigenes Szenenbild gespeichert. Es erscheint im Abend.")
        }
    }
    fun action(id: String) {
        val result = RelationshipEngine.action(state, id)
        onState(result.state)
        onNotice(result.message)
    }
    if (showDialogue && event != null) {
        RelationshipDialogue(state, event, { showDialogue = false }, onState, onNotice)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RulerPairHero(state) }
        if (state.companion.met) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusMetric(
                        "Vertrauen",
                        "${state.companion.trust}",
                        Modifier.weight(1f),
                        accent = Success,
                        supporting = if (state.companion.trust >= 70) "sehr hoch" else if (state.companion.trust >= 45) "stabil" else "fragil",
                    )
                    StatusMetric(
                        "Nähe",
                        "${state.companion.affection}",
                        Modifier.weight(1f),
                        accent = Gold,
                        supporting = derivedRelationshipMood(state).replaceFirstChar { it.uppercase() },
                    )
                    StatusMetric(
                        "Konflikt",
                        "${state.relationship.conflict}",
                        Modifier.weight(1f),
                        accent = if (state.relationship.conflict >= 60) Danger else Success,
                        supporting = if (state.relationship.conflict >= 60) "angespannt" else "beherrschbar",
                    )
                }
            }
        } else {
            item {
                PremiumPanel {
                    Text("EINE GEMEINSAME GESCHICHTE BEGINNT", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text("Ab Tag 5 kann sich eine erfahrene Gefährtin deiner Grenzfeste anschließen.", color = Mist, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Überblick", "Gefährtin", "Regieren", "Gemeinsame Zeit", "Erinnerungen", "Aufgaben").forEach { label ->
                    FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
                }
            }
        }
        if (!state.companion.met) item { EmptyCard("Profil, persönliche Aufgaben und gemeinsame Erinnerungen öffnen sich nach eurer Begegnung.") }
        else when (tab) {
            "Überblick" -> {
                item {
                    RulerCard {
                        RelationshipBar("Vertrauen", state.companion.trust, Success)
                        RelationshipBar("Respekt", state.companion.respect, Blue)
                        RelationshipBar("Nähe", state.companion.affection, Gold)
                        RelationshipBar("Bindung", state.relationship.commitment, PaleGold)
                        RelationshipBar("Konflikt", state.relationship.conflict, Danger)
                        Text(relationshipTrendText(state), color = Mist, fontSize = 12.sp)
                    }
                }
                item {
                    RulerCard {
                        Text("Ihre Stimme im Reich", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(state.relationship.politicalOpinion, color = Mist)
                        Text("Prioritäten: ${personality.priorities.joinToString(" · ") { it.label }}", color = Gold, fontSize = 12.sp)
                        if (state.relationship.romanceStage == RomanceStage.CO_RULERS)
                            Text("Ressort: ${state.coRuler.portfolio.label}", color = PaleGold)
                        GoldButton("Gemeinsamen Rat öffnen", onCouncil, Modifier.fillMaxWidth())
                        SmallAction("Ressort und Delegation") { tab = "Regieren" }
                    }
                }
                if (state.relationship.romanceStage == RomanceStage.CO_RULERS) item {
                    val cultures = Culture.entries.filter { state.population.count(it) > 0 }
                    val weakest = cultures.minByOrNull {
                        (state.frontier.cultureStanding[it] ?: 50) + (state.frontier.cultureIntegration[it] ?: 50)
                    }
                    val lastDecision = state.coRuler.decisions.lastOrNull()
                    val nextCase = CoRulerEngine.councilCases(state).firstOrNull()
                    RulerCard {
                        Text("Gemeinsame Regierung", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("Aktuelle Führung: ${state.coRuler.actingRuler} · Ressort ${state.coRuler.portfolio.label}", color = Gold, fontSize = 12.sp)
                        Text(
                            if (state.coRuler.delegation.enabled)
                                "Vollmacht aktiv · ${state.coRuler.delegation.maxGoldPerAction} Gold/Aktion · ${state.coRuler.delegation.maxGoldPerDay} Gold/Tag"
                            else "Vollmacht pausiert · Entscheidungen bleiben beim gemeinsamen Rat.",
                            color = Mist,
                            fontSize = 12.sp,
                        )
                        weakest?.let { culture ->
                            val standing = state.frontier.cultureStanding[culture] ?: 50
                            val integration = state.frontier.cultureIntegration[culture] ?: 50
                            Text("Schwächste Einbindung: ${culture.label} · Loyalität $standing · Integration $integration", color = if (standing < 35 || integration < 35) Danger else Mist, fontSize = 12.sp)
                        }
                        if (state.dynasty.successionTension > 0)
                            Text("Nachfolge belastet die Regierung: ${state.dynasty.successionTension}/100", color = if (state.dynasty.successionTension >= 60) Danger else Gold, fontSize = 12.sp)
                        nextCase?.let { Text("Nächste Ratsfrage: ${it.title}", color = PaleGold, fontSize = 12.sp) }
                        lastDecision?.let {
                            Text("Letzte Entscheidung: ${it.title} · ${it.choice} · ${it.actor}", color = Mist, fontSize = 11.sp)
                        }
                        GoldButton("Gemeinsam regieren", onCouncil, Modifier.fillMaxWidth())
                    }
                }
                event?.let { pending -> item {
                    RulerCard {
                        Text("OFFENES GESPRÄCH", color = Gold, fontSize = 11.sp)
                        Text(pending.title, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(pending.text, color = Mist, maxLines = 3, fontSize = 12.sp)
                        GoldButton("Gespräch öffnen", { showDialogue = true }, Modifier.fillMaxWidth())
                    }
                } }
                item {
                    RulerCard {
                        Text("Nächster gemeinsamer Schritt", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(sharedBlocker ?: "Ein Gespräch oder gemeinsame Zeit stärkt euren Weg. Freundschaft bleibt eine vollständige Möglichkeit.", color = Mist, fontSize = 12.sp)
                        OutlinedButton(enabled = sharedBlocker == null, onClick = { action("talk") }, modifier = Modifier.fillMaxWidth()) { Text("Sprechen") }
                        SmallAction("Gemeinsame Zeit auswählen") { tab = "Gemeinsame Zeit" }
                    }
                }
                state.relationship.memories.lastOrNull()?.let { memory -> item { MemoryCard(memory, state) } }
                val unresolved = state.relationship.issues.filterNot { it.resolved }
                if (unresolved.isNotEmpty()) item { SectionTitle("Themen zwischen euch") }
                items(unresolved, key = { it.id }) { issue ->
                    RulerCard {
                        Text(issue.topic, color = if (issue.severity >= 60) Danger else PaleGold, fontWeight = FontWeight.Bold)
                        Text("Seit Tag ${issue.startedDay} · Belastung ${issue.severity}%", color = Mist, fontSize = 12.sp)
                        SmallAction("Dieses Thema besprechen") { action("issue:${issue.id}") }
                    }
                }
                item {
                    RulerCard {
                        Text("Beziehung & persönliche Grenzen", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("Jede Stufe braucht eine bewusste gemeinsame Entscheidung. Geschenke und gemeinsames Training ersetzen kein Ja.", color = Mist, fontSize = 12.sp)
                        romanceStep(state.relationship.romanceStage)?.let { (label, id) ->
                            if (state.settings.romance != RomanceMode.OFF && state.player.age >= 18 && state.companion.age >= 18 && state.relationship.consent.romanceAllowed) {
                                OutlinedButton(enabled = sharedBlocker == null, onClick = { action(id) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                            }
                        }
                        if (state.relationship.romanceStage != RomanceStage.NONE) SmallAction("Als Freunde weitergehen") { action("friendship") }
                        if (state.settings.romance == RomanceMode.MATURE && state.relationship.romanceStage >= RomanceStage.PARTNERSHIP) {
                            OutlinedButton(enabled = sharedBlocker == null, onClick = { action("intimacy") }, modifier = Modifier.fillMaxWidth()) { Text("Nacht miteinander") }
                            SmallAction("Eigenes Szenenbild aus der Galerie") { nightPicker.launch(arrayOf("image/*")) }
                            Text("Ab Partnerschaft reicht ein Ja. Im Dialog: Bett, Zuber, langsam, hart. Nein bleibt ohne Strafe.", color = Mist, fontSize = 11.sp)
                            if (state.relationship.nightAlbum.isNotEmpty()) {
                                Text("Galerie der Abende", color = PaleGold, fontWeight = FontWeight.Bold)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.relationship.nightAlbum.takeLast(6).forEach { shot ->
                                        val model = if (shot.startsWith("asset:")) "file:///android_asset/night_${if (shot.endsWith("bath")) "bath" else if (shot.endsWith("hard") || shot.endsWith("slow")) "close" else "bed"}.webp" else shot
                                        AsyncImage(model, "Abend", Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Gefährtin" -> {
                item {
                    RulerCard {
                        RulerPortrait(state.companion.portraitUri, "companion", state.companion.name, Modifier.fillMaxWidth(), 230) { companionPicker.launch(arrayOf("image/*")) }
                        Text("Portrait antippen, um ein lokales Bild zu wählen.", color = Mist, fontSize = 11.sp)
                        Text("${state.companion.age} Jahre · ${state.companion.species.label} · ${state.companion.role}", color = Gold)
                        Text("${presence.companion.location.label}: ${presence.companion.detail}", color = if (presence.companion.available) Mist else Danger)
                        presence.companion.returnDay?.let { Text("Voraussichtliche Rückkehr: Tag $it", color = Mist, fontSize = 12.sp) }
                        Text("${state.companion.armorStyle} · ${state.companion.weapon}", color = Mist)
                        Text(personality.traits.joinToString(" · ") { it.label }, color = PaleGold, fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    RulerCard {
                        TextButton(onClick = { editCompanion = !editCompanion }) { Text(if (editCompanion) "Anpassung schließen" else "Gefährtin anpassen", color = Gold) }
                        if (editCompanion) {
                            OutlinedTextField(companionName, { companionName = it.take(24) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(companionAge, { companionAge = it.filter(Char::isDigit).take(3) }, label = { Text("Alter") }, modifier = Modifier.fillMaxWidth(), enabled = state.dynasty.members.isEmpty())
                            if (state.dynasty.members.isNotEmpty()) Text("Das Alter folgt in einer Dynastie dem Kalender.", color = Mist, fontSize = 11.sp)
                            OutlinedTextField(companionArmor, { companionArmor = it.take(36) }, label = { Text("Rüstung / Stil") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(companionWeapon, { companionWeapon = it.take(36) }, label = { Text("Hauptwaffe") }, modifier = Modifier.fillMaxWidth())
                            GoldButton("Anpassungen übernehmen", {
                                onState(customizeCompanion(state, companionName, companionAge.toIntOrNull() ?: state.companion.age, companionArmor, companionWeapon))
                                onNotice("Gefährtin angepasst.")
                                editCompanion = false
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
                item { StatGrid(listOf("Führung" to "${state.companion.leadership}", "Taktik" to "${state.companion.tactics}", "Schwert" to "${state.companion.sword}", "Bogen" to "${state.companion.bow}", "Diplomatie" to "${state.companion.diplomacy}", "Eigene Truppen" to "${state.commanderAssignments.filter { it.commanderId == COMPANION_COMMANDER_ID }.sumOf { assignment -> assignment.units.sumOf { it.amount } }}")) }
                item {
                    RulerCard {
                        Text("Persönliche Ziele", color = PaleGold, fontWeight = FontWeight.Bold)
                        personality.personalGoals.forEach { Text("• $it", color = Mist) }
                        Text("Politische Prioritäten", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(personality.priorities.joinToString(" · ") { it.label }, color = Mist)
                        Text("Rote Linien", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(personality.redLines.joinToString(" · "), color = Mist, fontSize = 12.sp)
                    }
                }
                item {
                    RulerCard {
                        Text("Kampfgefährten", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("${state.frontier.bond.stage} · ${state.frontier.bond.sessions} gemeinsame Trainingseinheiten", color = Gold)
                        Text("Ein eingespieltes Duo entwickelt sich unabhängig von einer Romanze.", color = Mist, fontSize = 12.sp)
                        SmallAction("Training und gemeinsame Einsätze") { tab = "Gemeinsame Zeit" }
                    }
                }
            }
            "Regieren" -> {
                item { CoRulerPanel(state, onState, onNotice) }
            }
            "Gemeinsame Zeit" -> {
                sharedBlocker?.let { reason -> item { EmptyCard(reason) } }
                activities.groupBy { it.activity.group }.forEach { (group, entries) ->
                    item(key = "activity_group_$group") { SectionTitle(group) }
                    items(entries, key = { "activity_${it.activity.id}" }) { entry ->
                        RulerCard {
                            Text(entry.activity.title, color = PaleGold, fontWeight = FontWeight.Bold)
                            Text(entry.activity.description, color = Mist, fontSize = 12.sp)
                            val costs = buildList {
                                add("${entry.activity.actionCost} Zeitpunkt${if (entry.activity.actionCost == 1) "" else "e"}")
                                if (entry.activity.goldCost > 0) add("${entry.activity.goldCost} Gold")
                                if (entry.activity.foodCost > 0) add("${entry.activity.foodCost} Nahrung")
                            }
                            Text(costs.joinToString(" · "), color = Gold, fontSize = 11.sp)
                            OutlinedButton(enabled = entry.available, onClick = { action("activity:${entry.activity.id}") }, modifier = Modifier.fillMaxWidth()) { Text(entry.activity.title) }
                            if (!entry.available) Text(entry.reason ?: "Diese Aktivität ist derzeit nicht verfügbar.", color = Mist, fontSize = 12.sp)
                        }
                    }
                }
            }
            "Erinnerungen" -> {
                item { SectionTitle("Euer Erinnerungsalbum") }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Alle", "Wichtigste", "Krieg", "Familie", "Beziehung", "Politik", "Reisen").forEach { filter ->
                            FilterChip(selected = memoryFilter == filter, onClick = { memoryFilter = filter; albumMemoryIds = null }, label = { Text(filter) })
                        }
                    }
                }
                if (albumMemoryIds != null) item { EmptyCard("Erinnerungen an den ausgewählten persönlichen Weg. „Alle“ öffnet das vollständige Album.") }
                val memories = state.relationship.memories.filter { memoryMatches(it, memoryFilter) && (albumMemoryIds == null || it.id in albumMemoryIds.orEmpty()) }.asReversed()
                if (memories.isEmpty()) item { EmptyCard("In diesem Albumabschnitt gibt es noch keine Erinnerung.") }
                items(memories, key = { it.id }) { memory -> MemoryCard(memory, state) }
            }
            "Aufgaben" -> {
                item { SectionTitle("Ihre persönlichen Wege") }
                if (state.relationship.arcs.isEmpty()) item { EmptyCard("Persönliche Aufgaben entstehen aus eurer gemeinsamen Geschichte und der Lage im Reich.") }
                items(state.relationship.arcs, key = { it.id }) { arc ->
                    val definition = RelationshipContentCatalog.arcs.firstOrNull { it.id == arc.id }
                    RulerCard {
                        Text(definition?.title ?: arc.id, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(definition?.description ?: "Eine persönliche Aufgabe deiner Gefährtin.", color = Mist, fontSize = 12.sp)
                        val steps = definition?.stages?.size ?: 4
                        LinearProgressIndicator(progress = { if (arc.completed) 1f else (arc.stage.toFloat() / steps.coerceAtLeast(1)).coerceIn(0f, 1f) }, color = Gold, modifier = Modifier.fillMaxWidth())
                        Text(if (arc.completed) "Abgeschlossen · seit Tag ${arc.startedDay}" else "Station ${(arc.stage + 1).coerceAtMost(steps)} / $steps · nächster Schritt frühestens Tag ${arc.nextDay}", color = Gold, fontSize = 12.sp)
                        if (event?.arcId == arc.id) GoldButton("Offenen Schritt besprechen", { showDialogue = true }, Modifier.fillMaxWidth())
                        if (arc.memoryIds.isNotEmpty()) SmallAction("Erinnerungen an diesen Weg") { tab = "Erinnerungen"; memoryFilter = "Alle"; albumMemoryIds = arc.memoryIds.toSet() }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipDialogue(state: GameState, event: RelationshipEvent, onDismiss: () -> Unit, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val options = RelationshipEventDirector.options(event)
    val blocker = PresenceEngine.sharedActivityBlocker(state, allowHospitalVisit = event.category == "hospital")
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 660.dp), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { RulerPortrait(state.companion.portraitUri, "companion", state.companion.name, Modifier.fillMaxWidth(), 140) }
                if (event.key == "intimacy" && state.settings.romance == RomanceMode.MATURE && state.player.age >= 18 && state.companion.age >= 18) item {
                    val scene = state.relationship.nightSceneUri ?: listOf("file:///android_asset/night_bed.webp", "file:///android_asset/night_bath.webp", "file:///android_asset/night_close.webp")[state.relationship.intimacy % 3]
                    AsyncImage(scene, "Abend", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                    Text(if (state.relationship.nightSceneUri != null) "Dein Bild" else "Szene wechselt mit den Abenden", color = Mist, fontSize = 11.sp)
                }
                item { Text(event.title, color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                item { Text(event.text, color = Mist) }
                blocker?.let { item { Text(it, color = Gold, fontSize = 12.sp) } }
                items(options.indices.toList(), key = { options[it].id }) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(enabled = blocker == null, onClick = {
                            val result = RelationshipEngine.choose(state, index)
                            onState(result.state); onNotice(result.message)
                            if (result.state.relationship.pendingEvent == null) onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) { Text(options[index].label) }
                        if (options[index].consequenceHint.isNotBlank()) Text(options[index].consequenceHint, color = Mist, fontSize = 11.sp)
                    }
                }
                item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Später weiterreden", color = Gold) } }
            }
        }
    }
}

@Composable
private fun RulerCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun RulerPortrait(uri: String?, key: String, name: String, modifier: Modifier, height: Int = 130, onPortrait: (() -> Unit)? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val imageModifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = onPortrait != null) { onPortrait?.invoke() }
        if (uri != null) AsyncImage(uri, "Portrait von $name", imageModifier, contentScale = ContentScale.Crop)
        else Image(painterResource(portraitResource(key)), "Portrait von $name", imageModifier, contentScale = ContentScale.Crop)
        Text(name, color = PaleGold, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RelationshipBar(label: String, value: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Mist, fontSize = 12.sp)
        Text("$value / 100", color = color, fontSize = 12.sp)
    }
    LinearProgressIndicator(progress = { value.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp), color = color, trackColor = Panel2)
}

@Composable
private fun MemoryCard(memory: RelationshipMemory, state: GameState) {
    RulerCard {
        val milestone = memory.emotionalWeight >= 4
        Text("${if (milestone) "✦ Meilenstein · " else ""}Tag ${memory.day} · ${memory.location ?: state.realm.settlementName}", color = if (milestone) Gold else Blue, fontSize = 12.sp)
        Text(memory.text, color = Mist)
        val participants = memory.participants.map { if (it == "player") state.player.name else if (it == "companion") state.companion.name else state.dynasty.members.firstOrNull { member -> member.id == it }?.name ?: it }
        Text(participants.joinToString(" · "), color = Mist, fontSize = 11.sp)
    }
}

internal fun memoryMatches(memory: RelationshipMemory, filter: String): Boolean = when (filter) {
    "Wichtigste" -> memory.emotionalWeight >= 4
    "Krieg" -> "war" in memory.tags || memory.battleId != null || memory.type in setOf("attack", "defeat", "mission", "wounded")
    "Familie" -> "family" in memory.tags || memory.type in setOf("child_arrival", "family_planned", "bereavement")
    "Beziehung" -> "relationship" in memory.tags || memory.type in setOf("confess", "kiss", "partner", "propose", "marry", "co_ruler", "reconciliation", "breakup", "private_evening")
    "Politik" -> "politics" in memory.tags || memory.type == "politics"
    "Reisen" -> "travel" in memory.tags || memory.type in setOf("walk", "ride", "travel")
    else -> true
}

private fun relationshipTrendText(state: GameState): String {
    val topic = state.relationship.issues.filterNot { it.resolved }.maxByOrNull { it.severity }
    return when {
        topic != null -> "Konflikt ${state.relationship.conflict} · offenes Thema: ${topic.topic}"
        state.relationship.memories.lastOrNull()?.let { it.type == "reconciliation" && state.day - it.day <= 1 } == true -> "Konflikt ${state.relationship.conflict} ↓ · das letzte Gespräch wirkt nach"
        state.relationship.conflict == 0 -> "Stabil · derzeit keine offenen Spannungen"
        else -> "Konflikt ${state.relationship.conflict} · Zeit für ein ruhiges Gespräch"
    }
}

private fun romanceStep(stage: RomanceStage): Pair<String, String>? = when (stage) {
    RomanceStage.NONE -> "Gefühle offen ansprechen" to "confess"
    RomanceStage.INTEREST -> "Um einen Kuss bitten" to "kiss"
    RomanceStage.ROMANCE -> "Partnerschaft vorschlagen" to "partner"
    RomanceStage.PARTNERSHIP -> "Heiratsantrag stellen" to "propose"
    RomanceStage.ENGAGED -> "Lebenspartnerschaft eingehen" to "marry"
    RomanceStage.MARRIED -> "Gemeinsame Regentschaft vorschlagen" to "co_ruler"
    RomanceStage.CO_RULERS -> null
}
