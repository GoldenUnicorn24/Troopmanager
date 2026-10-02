package com.goldenunicorn.troopmanager.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.goldenunicorn.troopmanager.engine.CharacterEngine
import com.goldenunicorn.troopmanager.model.*
import kotlin.math.*

private enum class NetworkFilter(val label: String) { ALL("Alle"), OFFICES("Ämter"), FAMILY("Familie"), MILITARY("Militär") }
private enum class NetworkKind(val label: String, val color: Color) {
    FRIEND("Freundschaft", Color(0xFF72BC99)), RIVAL("Rivalität", Color(0xFFE18D82)),
    FAMILY("Familie", Gold), PARTNER("Partnerschaft", Color(0xFFC69CE0)), OPINION("Meinung zum Herrscher", Color(0xFF88AFC9)),
}
private data class NetworkPerson(val id: String, val name: String, val commanderId: Long? = null, val memberId: String? = null)
private data class NetworkConnection(
    val id: String, val from: String, val to: String, val kind: NetworkKind,
    val cause: String, val strength: Int, val previous: Int = strength,
    val sinceDay: Int? = null, val changedDay: Int? = null, val directed: Boolean = false,
)

/** NPC opinions point toward the ruler; undirected friendships and family ties have no arrows. */
@Composable
fun CourtNetworkScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var filter by remember { mutableStateOf(NetworkFilter.ALL) }
    var page by remember { mutableIntStateOf(0) }
    var selectedEdge by remember { mutableStateOf<NetworkConnection?>(null) }
    var selectedPerson by remember { mutableStateOf<NetworkPerson?>(null) }
    val rulerId = "ruler"
    val rulerCommanderId = state.dynasty.members.firstOrNull { it.id == state.dynasty.rulerId }?.adultCommanderId
    fun npcId(id: Long) = if (id == rulerCommanderId) rulerId else "npc:$id"
    val people = buildList {
        add(NetworkPerson(rulerId, state.player.name, memberId = state.dynasty.rulerId))
        state.commanders.filter { it.id != rulerCommanderId }.forEach { c -> add(NetworkPerson(npcId(c.id), c.name, c.id,
            state.dynasty.members.firstOrNull { it.adultCommanderId == c.id || (it.id == "companion" && c.id == COMPANION_COMMANDER_ID) }?.id)) }
        state.dynasty.members.filter { it.id != state.dynasty.rulerId && it.adultCommanderId == null && !(it.id == "companion" && state.commanders.any { c -> c.id == COMPANION_COMMANDER_ID }) }.forEach { member ->
            add(NetworkPerson("family:${member.id}", member.name, memberId = member.id))
        }
    }.distinctBy { it.id }
    fun familyPerson(id: String): NetworkPerson? = people.firstOrNull { it.memberId == id }
    val allEdges = buildList {
        state.court.socialLinks.forEach { link ->
            val ageAllowed = link.kind !in listOf(SocialKind.ROMANCE, SocialKind.MARRIAGE) || listOf(link.firstId, link.secondId).all { id -> state.court.characters.any { it.commanderId == id && it.age >= 18 } }
            if (ageAllowed) {
                val first = if (link.directed && link.sourceId == link.secondId) link.secondId else link.firstId
                val second = if (first == link.firstId) link.secondId else link.firstId
                val kind = when (link.kind) { SocialKind.FRIENDSHIP -> NetworkKind.FRIEND; SocialKind.RIVALRY -> NetworkKind.RIVAL; SocialKind.ROMANCE, SocialKind.MARRIAGE -> NetworkKind.PARTNER }
                val fallback = "In einem älteren Spielstand gespeichert; die ursprüngliche Ursache wurde damals noch nicht aufgezeichnet."
                add(NetworkConnection("social:${link.firstId}:${link.secondId}", npcId(first), npcId(second), kind,
                    link.cause.ifBlank { fallback }, link.strength, link.previousStrength, link.sinceDay, link.lastChangedDay, link.directed))
            }
        }
        state.court.characters.forEach { detail ->
            listOf(detail.friends to NetworkKind.FRIEND, detail.rivals to NetworkKind.RIVAL).forEach { (ids, kind) -> ids.forEach { otherId ->
                if (state.court.socialLinks.none { setOf(it.firstId, it.secondId) == setOf(detail.commanderId, otherId) }) {
                    val other = state.court.characters.firstOrNull { it.commanderId == otherId }
                    val reciprocal = if (kind == NetworkKind.FRIEND) other?.friends?.contains(detail.commanderId) == true else other?.rivals?.contains(detail.commanderId) == true
                    if (!reciprocal || detail.commanderId < otherId) add(NetworkConnection("detail:${kind.name}:${detail.commanderId}:$otherId", npcId(detail.commanderId), npcId(otherId), kind,
                        if (kind == NetworkKind.RIVAL) "Als Rivalität im Personenprofil festgehalten. ${detail.serviceHistory.lastOrNull { it.kind.contains("promotion") || it.kind.contains("passed") }?.text.orEmpty()}"
                        else "Als Freundschaft im Personenprofil festgehalten.", 25, directed = !reciprocal))
                }
            } }
            if (people.any { it.commanderId == detail.commanderId }) add(NetworkConnection("opinion:${detail.commanderId}", npcId(detail.commanderId), rulerId, NetworkKind.OPINION,
                detail.serviceHistory.lastOrNull()?.text ?: "Aktuelle Meinung aus Dienst und persönlicher Loyalität.", detail.playerOpinion, directed = true))
        }
        state.dynasty.members.forEach { child -> child.parents.forEach { parentId ->
            val from = familyPerson(parentId); val to = familyPerson(child.id)
            if (from != null && to != null) add(NetworkConnection("family:$parentId:${child.id}", from.id, to.id, NetworkKind.FAMILY,
                if (child.adopted) "${child.name} wurde in die Familie aufgenommen." else "Eltern-Kind-Verbindung seit dem Familienzuwachs.", 100, sinceDay = child.bornDay, directed = true))
        } }
        if (state.player.age >= 18 && state.companion.age >= 18 && state.relationship.romanceStage >= RomanceStage.PARTNERSHIP) {
            val companion = people.firstOrNull { it.commanderId == COMPANION_COMMANDER_ID || it.memberId == "companion" }
            if (companion != null) add(NetworkConnection("ruler-partner", rulerId, companion.id, NetworkKind.PARTNER,
                "Bewusst vereinbarte ${state.relationship.romanceStage.label}; jede romantische Stufe bleibt eine gemeinsame Entscheidung.", state.relationship.commitment))
        }
    }
    val filtered = people.filter { person -> person.id != rulerId && when (filter) {
        NetworkFilter.ALL -> true
        NetworkFilter.OFFICES -> person.commanderId in state.court.offices.values
        NetworkFilter.FAMILY -> person.memberId != null || person.commanderId == COMPANION_COMMANDER_ID
        NetworkFilter.MILITARY -> person.commanderId != null
    } }
    val pageCount = maxOf(1, (filtered.size + 7) / 8)
    val safePage = page.coerceIn(0, pageCount - 1)
    val visible = listOf(people.first { it.id == rulerId }) + filtered.drop(safePage * 8).take(8)
    val visibleIds = visible.map { it.id }.toSet()
    val edges = allEdges.filter { it.from in visibleIds && it.to in visibleIds }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Das Hofnetzwerk", color = PaleGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("${state.player.name} im Mittelpunkt. Tippe auf eine Person oder Verbindung. Pfeile zeigen die Richtung; Linien ohne Pfeil gelten für beide Personen.", color = Mist, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { NetworkFilter.entries.forEach { choice ->
            FilterChip(selected = choice == filter, onClick = { filter = choice; page = 0 }, label = { Text(choice.label, fontSize = 11.sp) })
        } }
        if (pageCount > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { page = (safePage - 1).coerceAtLeast(0) }, enabled = safePage > 0) { Text("Zurück") }
            Text("${safePage + 1}/$pageCount · ${filtered.size} Personen", color = Mist, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { page = (safePage + 1).coerceAtMost(pageCount - 1) }, enabled = safePage + 1 < pageCount) { Text("Weiter") }
        }
        CourtGraph(visible, edges, onPerson = { selectedPerson = it }, onEdge = { selectedEdge = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("● Freunde", color = NetworkKind.FRIEND.color, fontSize = 10.sp)
            Text("● Rivalen", color = NetworkKind.RIVAL.color, fontSize = 10.sp)
            Text("● Familie", color = Gold, fontSize = 10.sp)
            Text("● Partner", color = NetworkKind.PARTNER.color, fontSize = 10.sp)
        }
        Text("Blau: Meinung einer Hofperson zum Herrscher. Goldene Pfeile führen von Eltern zu Kindern. Minderjährige erscheinen nur in Familienbeziehungen.", color = Mist, fontSize = 11.sp)
        if (edges.isEmpty()) Text("In diesem Ausschnitt bestehen noch keine aufgezeichneten Verbindungen.", color = Mist)
        edges.filter { it.kind != NetworkKind.OPINION }.forEach { edge ->
            OutlinedButton(onClick = { selectedEdge = edge }, modifier = Modifier.fillMaxWidth()) {
                val from = people.firstOrNull { it.id == edge.from }?.name ?: "Person"
                val to = people.firstOrNull { it.id == edge.to }?.name ?: "Person"
                Text("$from ${if (edge.directed) "→" else "↔"} $to · ${edge.kind.label}", color = edge.kind.color, fontSize = 12.sp)
            }
        }
    }
    selectedEdge?.let { edge ->
        val trend = edge.strength - edge.previous
        AlertDialog(onDismissRequest = { selectedEdge = null }, containerColor = Panel,
            title = { Text(edge.kind.label, color = edge.kind.color) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${people.firstOrNull { it.id == edge.from }?.name ?: "Person"} ${if (edge.directed) "→" else "↔"} ${people.firstOrNull { it.id == edge.to }?.name ?: "Person"}", color = PaleGold)
                Text(edge.cause, color = Mist)
                Text("Stärke ${edge.strength}% ${if (trend > 0) "↑ +$trend" else if (trend < 0) "↓ $trend" else "→ stabil"}", color = Gold)
                edge.sinceDay?.let { Text("Seit Tag $it · letzte Entwicklung Tag ${edge.changedDay ?: it}", color = Mist, fontSize = 12.sp) }
                Text(if (edge.directed) "Diese Verbindung geht von der ersten Person zur zweiten." else "Diese Verbindung wird gegenseitig geführt.", color = Mist, fontSize = 12.sp)
            } }, confirmButton = { TextButton(onClick = { selectedEdge = null }) { Text("Schließen") } })
    }
    selectedPerson?.let { person ->
        val commander = state.commanders.firstOrNull { it.id == person.commanderId }
        val details = state.court.characters.firstOrNull { it.commanderId == person.commanderId }
        val member = state.dynasty.members.firstOrNull { it.id == person.memberId }
        AlertDialog(onDismissRequest = { selectedPerson = null }, containerColor = Panel,
            title = { Text(person.name, color = PaleGold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (person.id == rulerId) Text("${state.player.age} Jahre · Herrscher · Führung ${state.player.leadership} · Diplomatie ${state.player.diplomacy}", color = Mist)
                member?.let { Text("Familie · ${it.age(state.day)} Jahre · ${it.traits.joinToString { trait -> trait.label }}", color = Gold) }
                details?.let { Text("${it.age} Jahre · ${it.personality.label} · Meinung ${it.playerOpinion}%", color = Mist) }
                commander?.let { Text("Loyalität ${it.loyalty}% · Führung ${it.leadership} · ${it.rank}", color = Mist) }
                state.court.offices.filterValues { it == person.commanderId }.keys.forEach { Text("Amt: ${it.label}", color = Gold) }
                details?.serviceHistory?.takeLast(3)?.reversed()?.forEach { Text("Tag ${it.day} · ${it.text}", color = Mist, fontSize = 12.sp) }
                if (commander != null && details?.alive == true && details.age >= 18) OutlinedButton(onClick = {
                    val result = CharacterEngine.promote(state, commander.id)
                    onState(result.state); onNotice(result.message)
                }) { Text("Beförderung prüfen") }
            } }, confirmButton = { TextButton(onClick = { selectedPerson = null }) { Text("Schließen") } })
    }
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x; val dy = end.y - start.y
    val squared = dx * dx + dy * dy
    if (squared == 0f) return (point - start).getDistance()
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / squared).coerceIn(0f, 1f)
    return (point - Offset(start.x + t * dx, start.y + t * dy)).getDistance()
}

@Composable
private fun CourtGraph(people: List<NetworkPerson>, edges: List<NetworkConnection>, onPerson: (NetworkPerson) -> Unit, onEdge: (NetworkConnection) -> Unit) {
    val density = LocalDensity.current
    val paint = remember(density) { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = with(density) { 10.sp.toPx() }; color = android.graphics.Color.rgb(236, 218, 171) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val diameter = minOf(maxWidth, 390.dp)
        val canvasSize = with(density) { diameter.toPx() }
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val radius = canvasSize * .37f
        val nodeRadius = with(density) { 25.dp.toPx() }
        val positions = people.mapIndexed { index, person ->
            val angle = if (index == 0) 0.0 else (-PI / 2 + 2 * PI * (index - 1) / maxOf(1, people.size - 1))
            person.id to if (index == 0) center else Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
        }.toMap()
        Canvas(Modifier.size(diameter).semantics {
            contentDescription = "Hofnetzwerk mit ${people.size} Personen und ${edges.size} Verbindungen."
            customActions = people.map { person -> CustomAccessibilityAction("Profil ${person.name} öffnen") { onPerson(person); true } } +
                edges.map { edge -> CustomAccessibilityAction("${edge.kind.label} zwischen ${people.firstOrNull { it.id == edge.from }?.name} und ${people.firstOrNull { it.id == edge.to }?.name}") { onEdge(edge); true } }
        }.pointerInput(positions, edges) { detectTapGestures { tap ->
            val person = people.firstOrNull { (tap - positions.getValue(it.id)).getDistance() <= nodeRadius + 10.dp.toPx() }
            if (person != null) onPerson(person) else edges.asReversed().minByOrNull { edge -> distanceToSegment(tap, positions.getValue(edge.from), positions.getValue(edge.to)) }
                ?.takeIf { edge -> distanceToSegment(tap, positions.getValue(edge.from), positions.getValue(edge.to)) < 12.dp.toPx() }?.let(onEdge)
        } }) {
            edges.forEach { edge ->
                val rawStart = positions[edge.from] ?: return@forEach
                val rawEnd = positions[edge.to] ?: return@forEach
                val vector = rawEnd - rawStart
                val length = vector.getDistance().coerceAtLeast(1f)
                val unit = vector / length
                val start = rawStart + unit * nodeRadius
                val end = rawEnd - unit * nodeRadius
                drawLine(edge.kind.color.copy(alpha = if (edge.kind == NetworkKind.OPINION) .38f else .85f), start, end,
                    (if (edge.kind == NetworkKind.OPINION) 1.5f else 2f + edge.strength / 50f).dp.toPx())
                if (edge.directed) {
                    val perpendicular = Offset(-unit.y, unit.x)
                    val back = end - unit * 9.dp.toPx()
                    val arrow = Path().apply { moveTo(end.x, end.y); val left = back + perpendicular * 4.dp.toPx(); lineTo(left.x, left.y); val right = back - perpendicular * 4.dp.toPx(); lineTo(right.x, right.y); close() }
                    drawPath(arrow, edge.kind.color)
                }
            }
            people.forEachIndexed { index, person ->
                val point = positions.getValue(person.id)
                drawCircle(Panel2, nodeRadius, point)
                drawCircle(if (index == 0) Gold else Color(0xFF789BAC), nodeRadius, point, style = Stroke(2.dp.toPx()))
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(person.name.split(" ").take(2).joinToString("") { it.take(1) }, point.x, point.y + 4.dp.toPx(), paint)
                    canvas.nativeCanvas.drawText(person.name.take(12), point.x, point.y + nodeRadius + 13.dp.toPx(), paint)
                }
            }
        }
    }
}
