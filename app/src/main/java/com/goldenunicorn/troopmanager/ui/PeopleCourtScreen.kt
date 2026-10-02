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
    var expandedBranch by remember { mutableStateOf<SkillBranch?>(null) }
    var heirName by remember { mutableStateOf("Hoffnung") }
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
        if (state.companion.met) {
            SectionTitle("Freiwillige Beziehungen")
            CourtDomainCard {
                Text(state.relationshipStage(), color = PaleGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Freundschaft entwickelt sich durch Vertrauen. Jede romantische Stufe braucht eine eigene, gemeinsame Entscheidung.", color = Mist, fontSize = 12.sp)
                Text("Konflikt ${state.relationship.conflict}% · Bindung ${state.relationship.commitment}%", color = Mist)
                Text("${state.companion.name}: ${state.relationship.politicalOpinion}", color = Gold, fontSize = 12.sp)
                Text("Beziehungsregeln: ${state.relationship.consent.relationshipStyle.label} · Privatsphäre wird geschützt.", color = Mist, fontSize = 12.sp)
                state.relationship.pendingEvent?.let { event ->
                    Text(event.title, color = PaleGold, fontWeight = FontWeight.Bold)
                    Text(event.text, color = Mist, fontSize = 12.sp)
                    val choices = if (event.key == "intimacy") listOf("Gemeinsam den Abend verbringen", "Über den Krieg sprechen", "Ruhe lassen") else listOf("Unterstützen", "Herausfordern", "Zeit geben")
                    choices.forEachIndexed { index, label -> SmallAction(label) { apply(RelationshipEngine.choose(state, index)) } }
                }
                if (!state.relationship.consent.romanceAllowed || "no_romance" in state.relationship.consent.boundaries)
                    Text("Die Grenze ist Freundschaft. Sie wird respektiert.", color = Mist)
                PeopleAction("Nur Freundschaft vereinbaren", "friendship", state, ::apply)
                PeopleAction("Streit besprechen / entschuldigen", "apologize", state, ::apply)
                PeopleAction("Versöhnung suchen", "reconcile", state, ::apply)
                if (state.settings.romance != RomanceMode.OFF && state.player.age >= 18 && state.companion.age >= 18) {
                    val step = when (state.relationship.romanceStage) {
                        RomanceStage.NONE -> "Gefühle offen ansprechen" to "confess"
                        RomanceStage.INTEREST -> "Um einen Kuss bitten" to "kiss"
                        RomanceStage.ROMANCE -> "Partnerschaft vorschlagen" to "partner"
                        RomanceStage.PARTNERSHIP -> "Heiratsantrag stellen" to "propose"
                        RomanceStage.ENGAGED -> "Lebenspartnerschaft eingehen" to "marry"
                        RomanceStage.MARRIED -> "Gemeinsame Regentschaft vorschlagen" to "co_ruler"
                        RomanceStage.CO_RULERS -> null
                    }
                    step?.let { PeopleAction(it.first, it.second, state, ::apply) }
                    if (state.relationship.romanceStage != RomanceStage.NONE) {
                        PeopleAction("Spaziergang", "walk", state, ::apply)
                        PeopleAction("Abendessen · 40 Gold", "dinner", state, ::apply)
                        PeopleAction("Persönliches Geschenk · 60 Gold", "gift", state, ::apply)
                        PeopleAction("Gemeinsamer Ritt", "ride", state, ::apply)
                        PeopleAction("Partnerschaft respektvoll beenden", "breakup", state, ::apply)
                    }
                    if (state.settings.romance == RomanceMode.MATURE && state.relationship.romanceStage >= RomanceStage.PARTNERSHIP) {
                        PeopleAction("Privatsphäre und Grenzen besprechen", "boundaries", state, ::apply)
                        PeopleAction("Einen privaten Abend vorschlagen", "intimacy", state, ::apply)
                        Text("Nur freiwillige erwachsene Nähe; die Szene blendet aus. Ein Nein hat keine Strafe, und jede Begegnung braucht neues Einverständnis.", color = Mist, fontSize = 11.sp)
                    }
                    if (state.relationship.romanceStage >= RomanceStage.PARTNERSHIP) {
                        PeopleAction("Beziehungsregel: Treue vereinbaren", "monogamy", state, ::apply)
                        PeopleAction("Eine offene Beziehung besprechen", "open_relationship", state, ::apply)
                    }
                } else Text("Romanze ist ${if (state.settings.romance == RomanceMode.OFF) "in den Einstellungen ausgeschaltet" else "bis zur Volljährigkeit gesperrt"}.", color = Mist, fontSize = 12.sp)
                state.relationship.memories.takeLast(5).reversed().forEach { memory -> Text("Tag ${memory.day} · ${memory.text}", color = Mist, fontSize = 11.sp) }
            }
        }
        SectionTitle("Dynastie & Nachfolge")
        CourtDomainCard {
            Text(if (state.settings.dynasty) "Dynastie aktiv · Ein Kampagnenjahr hat 365 Tage." else "Dynastie ausgeschaltet. Du kannst sie unabhängig von der Romanze in den Einstellungen aktivieren.", color = Mist, fontSize = 12.sp)
            if (state.settings.dynasty) {
                Text("Legitimität ${state.dynasty.legitimacy}% · ${state.dynasty.successionCount} Nachfolgen", color = Gold)
                state.dynasty.regentCommanderId?.let { id -> Text("Regentschaft: ${state.commanders.firstOrNull { it.id == id }?.name ?: "Hofrat"}", color = PaleGold) }
                state.dynasty.members.forEach { member ->
                    val suffix = when { !member.alive -> "Verstorben"; member.id == state.dynasty.rulerId -> "Regierung"; member.id == state.dynasty.heirId -> "Erbe"; else -> "Familie" }
                    Text("${member.name} · ${member.age(state.day)} Jahre · $suffix", color = Mist, fontSize = 12.sp)
                    if (member.alive && member.id != state.dynasty.rulerId) TextButton(onClick = { apply(DynastyEngine.selectHeir(state, member.id)) }) { Text("Als Erbe bestimmen", color = Gold) }
                }
                OutlinedTextField(heirName, { heirName = it.take(24) }, label = { Text("Name des Adoptivkindes") }, modifier = Modifier.fillMaxWidth())
                SmallAction("Kind aufnehmen · 300 Gold") { apply(DynastyEngine.adoptHeir(state, heirName)) }
                if (state.relationship.romanceStage >= RomanceStage.PARTNERSHIP) {
                    SmallAction("Gemeinsamen Kinderwunsch besprechen") { apply(DynastyEngine.agreeFamilyPlanning(state)) }
                    SmallAction("Familienplanung gemeinsam beginnen") { apply(DynastyEngine.planFamily(state)) }
                }
                state.dynasty.plannedBirthDay?.let { Text("Erwarteter Familienzuwachs: Tag $it", color = PaleGold) }
                Text("Kinder haben keine romantischen Aktionen. Minderjährige Erben regieren mit einem erwachsenen Hofrat.", color = Mist, fontSize = 11.sp)
            }
        }
        if (state.court.socialLinks.isNotEmpty()) CourtDomainCard {
            Text("Das Leben am Hof", color = PaleGold, fontWeight = FontWeight.Bold)
            state.court.socialLinks.takeLast(6).forEach { link ->
                val names = listOf(link.firstId, link.secondId).mapNotNull { id -> state.commanders.firstOrNull { it.id == id }?.name }
                Text("${names.joinToString(" & ")} · ${link.kind.label}", color = Mist, fontSize = 12.sp)
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
