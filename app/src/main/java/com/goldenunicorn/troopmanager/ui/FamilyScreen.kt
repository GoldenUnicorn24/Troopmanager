package com.goldenunicorn.troopmanager.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.DynastyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
fun FamilyScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    val family = if (state.settings.dynasty) DynastyEngine.initialize(state) else state
    var selectedId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var newName by remember(state.dynasty.plannedChildName) { mutableStateOf(state.dynasty.plannedChildName.ifBlank { "Hoffnung" }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Familie & Dynastie", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        if (!state.settings.dynasty) {
            FamilyCard { Text("Die Dynastie ruht. Aktiviere sie in den Einstellungen, um Kinder, Erziehung und Nachfolge zu begleiten.", color = Mist) }
        } else {
            FamilyCard {
                Text("${family.dynasty.members.count { it.alive }} Familienmitglieder · Legitimität ${family.dynasty.legitimacy}%", color = Gold)
                LinearProgressIndicator(progress = { family.dynasty.legitimacy / 100f }, modifier = Modifier.fillMaxWidth(), color = Gold)
                Text("Ein Kampagnenjahr hat 365 Tage. Ausbildung ab 6; eigenständiger Hofcharakter ab 18.", color = Mist, fontSize = 12.sp)
            }
            TabRow(selectedTabIndex = tab, containerColor = Panel) {
                listOf("Stammbaum", "Erziehung", "Nachfolge").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, color = if (tab == index) Gold else Mist) })
                }
            }
            when (tab) {
                0 -> {
                    Text("Tippe auf eine Person, um ihr Profil zu öffnen. Große Familien lassen sich seitlich verschieben.", color = Mist, fontSize = 12.sp)
                    FamilyTree(family) { selectedId = it }
                    FamilyCard {
                        Text("Familienzuwachs", color = PaleGold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = newName, onValueChange = { newName = it.take(24) }, label = { Text("Name des Kindes") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedButton(onClick = { apply(DynastyEngine.adoptHeir(state, newName)) }, enabled = state.resources.gold >= 300) { Text("Kind aufnehmen · 300 Gold") }
                        if (state.resources.gold < 300) Text("Unterkunft und Fürsorge benötigen 300 Gold.", color = Mist, fontSize = 12.sp)
                        if (state.relationship.romanceStage >= RomanceStage.PARTNERSHIP && state.player.age >= 18 && state.companion.age >= 18) {
                            OutlinedButton(onClick = { apply(DynastyEngine.agreeFamilyPlanning(state)) }) { Text("Gemeinsamen Kinderwunsch besprechen") }
                            Button(onClick = {
                                val named = DynastyEngine.setBirthName(state, newName)
                                if (newName.isBlank()) apply(named) else apply(DynastyEngine.planFamily(named.state))
                            }) { Text("Familienplanung beginnen") }
                        }
                        family.dynasty.plannedBirthDay?.let { day ->
                            Text("Erwarteter Zuwachs an Tag $day · noch ${(day - state.day).coerceAtLeast(0)} Tage", color = Gold)
                            OutlinedButton(onClick = { apply(DynastyEngine.setBirthName(state, newName)) }) { Text("Namen für den Zuwachs vormerken") }
                        }
                        Text("Adoption ist unabhängig von Romanze. Ein Nein zur Familienplanung wird respektiert.", color = Mist, fontSize = 12.sp)
                    }
                }
                1 -> {
                    val children = family.dynasty.members.filter { it.alive && it.parents.isNotEmpty() }
                    if (children.isEmpty()) FamilyCard { Text("Noch keine Kinder in der Familie. Aufnahme und Familienplanung findest du beim Stammbaum.", color = Mist) }
                    children.forEach { child -> FamilyCard {
                        Text(child.name, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text("${child.age(state.day)} Jahre · ${child.traits.joinToString { it.label }}", color = Mist)
                        Text("${child.educationPath?.label ?: if (child.age(state.day) < 6) "Spielerische Kindheit" else "Grundbildung"} · ${DynastyEngine.mentorName(family, child.mentorId)}", color = Gold, fontSize = 12.sp)
                        Text(child.educationLog.lastOrNull()?.text ?: "Die Familie begleitet den nächsten Entwicklungsschritt.", color = Mist, fontSize = 12.sp)
                        OutlinedButton(onClick = { selectedId = child.id }) { Text("Profil & Ausbildung öffnen") }
                    } }
                }
                2 -> FamilyCard {
                    Text("Wer übernimmt die Regierung?", color = PaleGold, fontWeight = FontWeight.Bold)
                    Text(DynastyEngine.successionSummary(family), color = Mist)
                    if (family.dynasty.successionTension > 0) {
                        Text("Nachfolgespannung ${family.dynasty.successionTension}/100", color = if (family.dynasty.successionTension >= 60) Danger else Gold, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { family.dynasty.successionTension / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (family.dynasty.successionTension >= 60) Danger else Gold,
                        )
                        Text(family.dynasty.successionConcern.ifBlank { "Teile von Familie und Hof erwarten weitere Klärung." }, color = Mist, fontSize = 12.sp)
                        OutlinedButton(
                            onClick = { apply(DynastyEngine.addressSuccession(state, "family")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Als Familie beraten") }
                        OutlinedButton(
                            onClick = { apply(DynastyEngine.addressSuccession(state, "public")) },
                            enabled = state.resources.gold >= 60,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Öffentlich erklären · 60 Gold") }
                        Button(
                            onClick = { apply(DynastyEngine.addressSuccession(state, "council")) },
                            enabled = state.resources.gold >= 120,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Großen Nachfolgerat einberufen · 120 Gold") }
                    }
                    family.dynasty.members.filter { it.alive && it.id != family.dynasty.rulerId && it.id != "companion" }.forEach { member ->
                        OutlinedButton(onClick = { apply(DynastyEngine.selectHeir(state, member.id)) }, enabled = member.id != family.dynasty.heirId) {
                            Text(if (member.id == family.dynasty.heirId) "${member.name} · bestimmter Erbe" else "${member.name} als Erbe bestimmen")
                        }
                    }
                    val minor = family.dynasty.members.any { (it.id == family.dynasty.rulerId || it.id == family.dynasty.heirId) && it.age(state.day) < 18 }
                    if (minor) {
                        Text("Regent vorbereiten / bestimmen", color = Gold)
                        FamilyMenu("${state.commanders.firstOrNull { it.id == family.dynasty.regentCommanderId }?.name ?: "Erwachsenen Hofrat bestimmen"}",
                            DynastyEngine.regentCandidates(family).map { it.id to "${it.name} · Loyalität ${it.loyalty}%" }) { id -> apply(DynastyEngine.selectRegent(state, id)) }
                        Text(family.dynasty.regencyReason.ifBlank { "Regent: erwachsen, gesund, verfügbar und mindestens 40% loyal. Bei automatischer Wahl zählen Loyalität und Führung." }, color = Mist, fontSize = 12.sp)
                        family.dynasty.regencyConflict?.let { Text(it, color = Color(0xFFEEA080), fontSize = 12.sp) }
                    }
                    Text("Ursachen der Legitimität", color = PaleGold, fontWeight = FontWeight.Bold)
                    if (family.dynasty.legitimacyCauses.isEmpty()) Text("Ausgangswert ${family.dynasty.legitimacy}%. Siege, Versorgung, Familienentscheidungen und Nachfolge verändern die Anerkennung.", color = Mist, fontSize = 12.sp)
                    family.dynasty.legitimacyCauses.takeLast(6).reversed().forEach { cause ->
                        Text("Tag ${cause.day} · ${if (cause.change >= 0) "+" else ""}${cause.change} · ${cause.cause}", color = Mist, fontSize = 12.sp)
                    }
                }
            }
            family.dynasty.pendingFamilyEvent?.let { event -> FamilyCard {
                Text("Familienentscheidung · ${event.title}", color = Gold, fontWeight = FontWeight.Bold)
                Text(event.text, color = Mist)
                event.choices.forEach { choice ->
                    OutlinedButton(onClick = { apply(DynastyEngine.chooseFamilyEvent(state, choice.id)) }, enabled = state.resources.gold >= choice.goldCost, modifier = Modifier.fillMaxWidth()) { Text(choice.label) }
                    if (state.resources.gold < choice.goldCost) Text("Benötigt ${choice.goldCost} Gold.", color = Mist, fontSize = 11.sp)
                }
            } }
        }
    }
    selectedId?.let { id -> family.dynasty.members.firstOrNull { it.id == id }?.let { member ->
        FamilyProfile(family, member, onDismiss = { selectedId = null }, apply = ::apply)
    } }
}

private data class FamilyTreeNode(val member: FamilyMember, val bounds: Rect)

@Composable
private fun FamilyTree(state: GameState, onSelect: (String) -> Unit) {
    val members = state.dynasty.members
    fun generation(member: FamilyMember, seen: Set<String> = emptySet()): Int {
        if (member.id in seen) return 0
        val parents = member.parents.mapNotNull { id -> members.firstOrNull { it.id == id } }
        return if (parents.isEmpty()) 0 else 1 + parents.maxOf { generation(it, seen + member.id) }
    }
    val rows = members.groupBy { generation(it) }.toSortedMap()
    val density = LocalDensity.current
    val width = maxOf(360, (rows.values.maxOfOrNull { it.size } ?: 1) * 150).dp
    val height = maxOf(1, (rows.keys.maxOrNull() ?: 0) + 1).times(120).dp
    val widthPx = with(density) { width.toPx() }
    val nodeWidth = with(density) { 132.dp.toPx() }
    val nodeHeight = with(density) { 80.dp.toPx() }
    val rowHeight = with(density) { 120.dp.toPx() }
    val nodes = rows.flatMap { (generation, row) -> row.mapIndexed { index, member ->
        val centerX = widthPx * (index + .5f) / row.size
        val y = generation * rowHeight + with(density) { 18.dp.toPx() }
        FamilyTreeNode(member, Rect(centerX - nodeWidth / 2, y, centerX + nodeWidth / 2, y + nodeHeight))
    } }
    val byId = nodes.associateBy { it.member.id }
    val namePaint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = with(density) { 13.sp.toPx() }; color = android.graphics.Color.rgb(236, 218, 171); isFakeBoldText = true } }
    val detailPaint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = with(density) { 10.sp.toPx() }; color = android.graphics.Color.rgb(216, 224, 229) } }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(width).height(height)
            .semantics {
                contentDescription = "Stammbaum mit ${members.size} Personen. Gold: Regierung und Erbe; Grau: verstorben."
                customActions = members.map { member -> CustomAccessibilityAction("Profil ${member.name} öffnen") { onSelect(member.id); true } }
            }
            .pointerInput(nodes) { detectTapGestures { tap -> nodes.firstOrNull { it.bounds.contains(tap) }?.let { onSelect(it.member.id) } } }) {
            nodes.forEach { node -> node.member.parents.forEach { parentId -> byId[parentId]?.let { parent ->
                val start = Offset(parent.bounds.center.x, parent.bounds.bottom)
                val end = Offset(node.bounds.center.x, node.bounds.top)
                val midpoint = (start.y + end.y) / 2
                drawLine(Gold.copy(alpha = .6f), start, Offset(start.x, midpoint), 2.dp.toPx())
                drawLine(Gold.copy(alpha = .6f), Offset(start.x, midpoint), Offset(end.x, midpoint), 2.dp.toPx())
                drawLine(Gold.copy(alpha = .6f), Offset(end.x, midpoint), end, 2.dp.toPx())
            } } }
            nodes.forEach { node ->
                val member = node.member
                val color = when { !member.alive -> Color(0xFF525C65); member.id == state.dynasty.rulerId || member.id == state.dynasty.heirId -> Gold; else -> Color(0xFF789BAC) }
                drawRoundRect(Panel2, node.bounds.topLeft, Size(node.bounds.width, node.bounds.height), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
                drawRoundRect(color, node.bounds.topLeft, Size(node.bounds.width, node.bounds.height), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
                val role = when { !member.alive -> "Verstorben"; member.id == state.dynasty.rulerId -> "Regierung"; member.id == state.dynasty.heirId -> "Erbe"; member.adopted -> "Adoptivkind"; member.parents.isNotEmpty() -> "Kind"; else -> "Familie" }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(member.name.take(15), node.bounds.center.x, node.bounds.top + 23.dp.toPx(), namePaint)
                    canvas.nativeCanvas.drawText("${member.age(state.day)} Jahre · $role", node.bounds.center.x, node.bounds.top + 43.dp.toPx(), detailPaint)
                    canvas.nativeCanvas.drawText(member.traits.take(2).joinToString { it.label }, node.bounds.center.x, node.bounds.top + 63.dp.toPx(), detailPaint)
                }
            }
        }
    }
}

@Composable
private fun FamilyProfile(state: GameState, member: FamilyMember, onDismiss: () -> Unit, apply: (GameEngine.ActionResult) -> Unit) {
    var name by remember(member.id, member.name) { mutableStateOf(member.name) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text(member.name, color = PaleGold) },
        text = { Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val age = member.age(state.day)
            Text("$age Jahre · ${if (member.alive) "Lebend" else "Verstorben"}${if (member.adopted) " · adoptiert" else ""}", color = Mist)
            Text("Eltern: ${member.parents.mapNotNull { id -> state.dynasty.members.firstOrNull { it.id == id }?.name }.joinToString().ifBlank { "Gründergeneration" }}", color = Mist)
            Text("${member.traits.joinToString { it.label }.ifBlank { "Erfahrene Gründergeneration" }}", color = Gold)
            Text("Führung ${member.leadership} · Diplomatie ${member.diplomacy}\nVerwaltung ${member.stewardship} · Medizin ${member.medicine} · Taktik ${member.tactics}", color = Mist, fontSize = 12.sp)
            if (member.alive && member.parents.isNotEmpty()) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Name") }, singleLine = true)
                TextButton(onClick = { apply(DynastyEngine.renameMember(state, member.id, name)) }) { Text("Namen speichern") }
            }
            if (member.alive && member.parents.isNotEmpty() && age in 6..17) {
                FamilyMenu(member.educationPath?.label ?: "Ausbildungsweg wählen", EducationPath.entries.map { it to it.label }) { path -> apply(DynastyEngine.chooseEducation(state, member.id, path)) }
                member.educationPath?.let { Text(it.description, color = Mist, fontSize = 12.sp) }
                if (state.dynasty.companionFamilyOpinion.isNotBlank()) Text(state.dynasty.companionFamilyOpinion, color = Gold, fontSize = 12.sp)
                FamilyMenu("Mentor: ${DynastyEngine.mentorName(state, member.mentorId)}", DynastyEngine.mentorOptions(state)) { id -> apply(DynastyEngine.assignMentor(state, member.id, id)) }
                DynastyEngine.mentorBlocker(state, member.mentorId)?.let { Text("$it Der Hoflehrer sichert die Grundbildung.", color = Mist, fontSize = 12.sp) }
                Text("Mentorbindung ${member.mentorBond}% · Fähigkeiten entwickeln sich jährlich; Entscheidungen prägen Eigenschaften.", color = Mist, fontSize = 12.sp)
            } else if (age < 6) Text("Die frühe Kindheit bleibt spielerisch. Ab 6 stehen Mentor und Ausbildung zur Wahl.", color = Mist, fontSize = 12.sp)
            member.adultCommanderId?.let { Text("Als erwachsener Hofcharakter verfügbar: Fähigkeiten, Eigenschaften und Beziehungen sind übernommen.", color = Gold, fontSize = 12.sp) }
            if (member.alive && member.id != state.dynasty.rulerId && member.id != "companion") TextButton(onClick = { apply(DynastyEngine.selectHeir(state, member.id)) }) { Text("Als Erbe bestimmen") }
            Text("Entwicklung", color = PaleGold, fontWeight = FontWeight.Bold)
            member.educationLog.takeLast(6).reversed().forEach { entry -> Text("Tag ${entry.day} · ${entry.text}", color = Mist, fontSize = 11.sp) }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

@Composable
private fun FamilyCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun <T> FamilyMenu(label: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) }) }
        }
    }
}
