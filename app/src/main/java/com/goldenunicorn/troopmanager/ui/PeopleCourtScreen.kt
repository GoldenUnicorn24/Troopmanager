package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** Embedded in the existing portrait/customization court, preserving those established controls. */
@Composable
internal fun PeopleCourtScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    var selectedId by remember { mutableStateOf(state.commanders.firstOrNull()?.id) }
    val selected = state.commanders.firstOrNull { it.id == selectedId } ?: state.commanders.firstOrNull()
    val details = state.court.characters.firstOrNull { it.commanderId == selected?.id }
    val bonuses = CharacterEngine.bonuses(state)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Hofämter & Karrieren")
        CourtDomainCard {
            Text("Jedes Amt wirkt durch die Fähigkeiten seiner Person. Im Einsatz oder bei geringer Loyalität ruht das Amt.", color = Mist, fontSize = 12.sp)
            CourtOffice.entries.forEach { office ->
                val holder = state.commanders.firstOrNull { it.id == state.court.offices[office] }
                Text(office.label, color = PaleGold, fontWeight = FontWeight.SemiBold)
                Text(office.effect, color = Mist, fontSize = 11.sp)
                PeopleMenu(holder?.name ?: "Unbesetzt", listOf<Long?>(null) + state.commanders.map { it.id }, { id -> state.commanders.firstOrNull { it.id == id }?.name ?: "Amt freigeben" }) { id -> apply(CharacterEngine.assignOffice(state, office, id)) }
            }
            Text("Amtswirkung: +${bonuses.gold} Gold/Tag · +${bonuses.morale} Schlachtmoral · +${bonuses.diplomacy} Diplomatie · +${bonuses.counterintelligence} Gegenaufklärung", color = Gold, fontSize = 12.sp)
        }
        if (selected != null) CourtDomainCard {
            PeopleMenu("Person: ${selected.name}", state.commanders.map { it.id }, { id -> state.commanders.firstOrNull { it.id == id }?.name ?: "Unbekannt" }) { selectedId = it }
            if (details != null) {
                Text("${selected.name}${details.epithet?.let { ", $it" }.orEmpty()}", color = PaleGold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${details.age} Jahre · ${selected.culture.label} · ${details.origin}", color = Mist)
                if (!details.alive) Text("Verstorben · in der Chronik bewahrt", color = PaleGold)
                Text("${details.rank.label} · ${details.personality.label} · Loyalität ${selected.loyalty}%", color = Gold)
                Text("Mut ${details.courage} · Ambition ${details.ambition} · Meinung ${details.playerOpinion}", color = Mist, fontSize = 12.sp)
                Text("Führung ${selected.leadership} · Taktik ${selected.tactics} · Diplomatie ${details.diplomacy}", color = Mist, fontSize = 12.sp)
                Text("Verwaltung ${details.stewardship} · Intrige ${details.intrigue} · Medizin ${details.medicine}", color = Mist, fontSize = 12.sp)
                Text("${selected.battlesFought} Schlachten · ${selected.victories} Siege · ${selected.missionsCompleted} Missionen", color = Mist, fontSize = 12.sp)
                val friends = details.friends.mapNotNull { id -> state.commanders.firstOrNull { it.id == id }?.name }
                val rivals = details.rivals.mapNotNull { id -> state.commanders.firstOrNull { it.id == id }?.name }
                Text("Freunde: ${friends.joinToString().ifBlank { "Noch keine" }}", color = Mist, fontSize = 12.sp)
                Text("Rivalen: ${rivals.joinToString().ifBlank { "Noch keine" }}", color = Mist, fontSize = 12.sp)
                details.serviceHistory.takeLast(4).reversed().forEach { entry -> Text("Tag ${entry.day} · ${entry.text}", color = Mist, fontSize = 11.sp) }
                SmallAction("Nächsten Dienstgrad verleihen") { apply(CharacterEngine.promote(state, selected.id)) }
                Text("Beförderungen benötigen Siege und Gold. Sie stärken Führung und Treue; übergangene ehrgeizige Personen reagieren darauf.", color = Mist, fontSize = 11.sp)
            }
        }
        if (state.court.legends.isNotEmpty()) CourtDomainCard {
            Text("Halle der Legenden", color = PaleGold, fontWeight = FontWeight.Bold)
            state.court.legends.takeLast(8).forEach { legend ->
                Text(legend.name, color = Gold)
                Text("Tag ${legend.day} · ${legend.deed} · +${legend.moraleBonus} Moral", color = Mist, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CourtDomainCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun PeopleAction(label: String, action: String, state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    OutlinedButton(onClick = { apply(RelationshipEngine.action(state, action)) }, modifier = Modifier.fillMaxWidth(),
        enabled = !state.commanderAway(COMPANION_COMMANDER_ID) && state.battleSession?.isActive != true && !state.war.unavailableCommander(COMPANION_COMMANDER_ID)) { Text(label) }
}

@Composable
private fun <T> PeopleMenu(label: String, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
internal fun CharacterSkillsScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var expandedBranch by remember { mutableStateOf<SkillBranch?>(null) }
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Sechs Entwicklungswege")
        CourtDomainCard {
            Text("${state.player.skillPoints} freie Fertigkeitspunkte · Deine Startpunkte bleiben sofort frei auf alle Entwicklungswege verteilbar.", color = Mist, fontSize = 12.sp)
            SkillBranch.entries.forEach { branch ->
                TextButton(onClick = { expandedBranch = if (expandedBranch == branch) null else branch }, modifier = Modifier.fillMaxWidth()) {
                    Text("${branch.label} · ${state.court.perks.count { it.branch == branch }} gelernt", color = Gold)
                }
                if (expandedBranch == branch) PlayerPerk.entries.filter { it.branch == branch }.forEach { perk ->
                    Text(perk.label, color = PaleGold, fontWeight = FontWeight.SemiBold)
                    Text(perk.description, color = Mist, fontSize = 12.sp)
                    OutlinedButton(enabled = perk !in state.court.perks && state.player.skillPoints >= perk.cost, onClick = { apply(CharacterEngine.unlockPerk(state, perk)) }) {
                        Text(if (perk in state.court.perks) "Gelernt" else "Lernen · ${perk.cost} Punkte")
                    }
                }
            }
        }

        } }
    }
}
