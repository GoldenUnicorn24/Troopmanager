package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun KingdomsScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    var section by remember { mutableIntStateOf(0) }
    val names = listOf("Diplomatie", "Spionage", "Gesellschaft")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("REICHE & GESELLSCHAFT", "Verträge, Informationen und politische Verantwortung") }
        item {
            ModernTabStrip(names, section, { section = it })
        }
        if (section == 0) {
            item { Text("Angebote werden nach Vertrauen, Persönlichkeit, Nutzen und Risiko bewertet. Gegenforderungen übertragen Ressourcen erst bei Annahme.", color = Mist) }
            items(state.world.factions.filter { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }, key = { it.id }) { faction ->
                DiplomacyCard(state, faction, ::apply)
            }
            val visible = state.diplomacy.treaties.filter { it.firstFactionId == PLAYER_FACTION ||
                it.secondFactionId == PLAYER_FACTION || it.id in state.espionage.knownTreatyIds }
            if (visible.isNotEmpty()) item { SectionTitle("Bekannte Verträge") }
            items(visible, key = { it.id }) { treaty ->
                val first = state.world.faction(treaty.firstFactionId)?.name ?: treaty.firstFactionId
                val second = state.world.faction(treaty.secondFactionId)?.name ?: treaty.secondFactionId
                CompactCard("${treaty.kind.label} · $first / $second", "Bis Tag ${treaty.expiresDay}" +
                    if (treaty.tributeGold > 0) " · ${treaty.tributeGold} Gold alle 7 Tage, Zahler: ${state.world.faction(treaty.payerFactionId ?: "")?.name}" else "")
            }
        }
        if (section == 1) {
            item {
                Text("Agenten benötigen Zeit und Gold. Entdeckung schadet Beziehungen; Gefangene können ausgelöst werden. Aufklärung aktualisiert die Karte, Sabotage verändert vorhandene Vorräte und Befestigungen.", color = Mist)
                SmallAction("Agent anwerben · 300 Gold") { apply(EspionageEngine.recruitAgent(state)) }
                if (state.espionage.counterintelligenceUntilDay > state.day)
                    Text("Gegenaufklärung aktiv bis Tag ${state.espionage.counterintelligenceUntilDay}", color = PaleGold)
            }
            if (state.espionage.agents.isEmpty()) item { EmptyCard("Noch keine Agenten. Ein Nachrichtendienst ist optional und kostet reale Ressourcen.") }
            items(state.espionage.agents, key = { it.id }) { agent -> AgentCard(state, agent, ::apply) }
            if (state.espionage.reports.isNotEmpty()) item { SectionTitle("Berichte") }
            items(state.espionage.reports.takeLast(8).reversed()) { report ->
                CompactCard("Tag ${report.day} · ${report.kind.label}", report.text)
            }
        }
        if (section == 2) {
            item {
                val s = state.society
                KingdomCard {
                    Text("Gesellschaftliche Lage", color = PaleGold, fontWeight = FontWeight.Bold)
                    Text("Hunger ${s.hunger} · Krankheit ${s.disease} · Kriminalität ${s.crime}\nUngleichheit ${s.inequality} · Kulturspannung ${s.culturalTension}\nPolitische Loyalität ${s.politicalLoyalty} · Kriegsmüdigkeit ${s.warExhaustion}", color = Mist)
                    Text("Migration heute: ${s.lastMigration}; aufgenommen ${s.totalImmigrants}, abgewandert ${s.totalEmigrants}.", color = Mist)
                    Text("Hunger und Spannungen senken Zufriedenheit; Krankheit kostet Versorgung; Kriminalität kostet Gold und Sicherheit. Kriegsmüdigkeit schwächt Armeemoral. Loyalität bestimmt Forderungen und politische Krisen.", color = Mist)
                    CampaignInsightsEngine.societyDrivers(state).forEach { (driver, reason) -> Text("$driver: $reason", color = Mist) }
                }
            }
            state.society.story.pending?.let { event ->
                item {
                    KingdomCard {
                        Text(event.title, color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(event.text, color = Mist)
                        Text("Entscheidung bis Tag ${event.expiresDay}", color = Mist)
                        StoryDirector.choices(event).forEach { choice ->
                            SmallAction(choice.label) { apply(StoryDirector.resolve(state, event.id, choice.id)) }
                        }
                    }
                }
            }
            item { SectionTitle("Maßnahmen für 14 Tage") }
            items(SocietyPolicy.entries) { policy ->
                val until = state.society.policyUntilDay[policy] ?: 0
                val cost = SocietyEngine.policyCost(policy)
                KingdomCard {
                    Text(policy.label, color = PaleGold)
                    if (until > state.day) Text("Aktiv bis Tag $until", color = Mist) else
                        SmallAction("Finanzieren · ${cost.gold} Gold, ${cost.food} Nahrung") { apply(SocietyEngine.fundPolicy(state, policy)) }
                }
            }
            if (state.society.demands.isNotEmpty()) item { SectionTitle("Offene Forderungen") }
            items(state.society.demands, key = { it.id }) { demand ->
                KingdomCard {
                    Text("${demand.group.label}: ${demand.kind.label}", color = PaleGold)
                    Text(speaker(state, demand.group), color = ModernBlue)
                    Text("Frist Tag ${demand.deadlineDay} · Kosten ${demand.kind.cost} ${if (demand.kind == PoliticalDemandKind.FOOD_RELIEF) "Nahrung" else "Gold"}", color = Mist)
                    val accepted = SocietyEngine.resolveDemand(state, demand.id, true).state
                    val rejected = SocietyEngine.resolveDemand(state, demand.id, false).state
                    Text("Erfüllen: ${demandPreview(state, accepted, demand.group)}", color = Success)
                    Text("Ablehnen: ${demandPreview(state, rejected, demand.group)}", color = Danger)
                    SmallAction("Forderung erfüllen") { apply(SocietyEngine.resolveDemand(state, demand.id, true)) }
                    SmallAction("Forderung ablehnen · Loyalität sinkt") { apply(SocietyEngine.resolveDemand(state, demand.id, false)) }
                }
            }
            item { SectionTitle("Politische Gruppen") }
            items(state.society.groups, key = { it.kind.name }) { group ->
                CompactCard(group.kind.label, "${speaker(state, group.kind)}\nLoyalität ${group.loyalty} · Einfluss ${group.influence}; Unzufriedenheit führt zu Forderungen.")
            }
        }
    }
}

@Composable
private fun KingdomCard(content: @Composable ColumnScope.() -> Unit) {
    PremiumPanel(content = content)
}

private fun speaker(state: GameState, group: PoliticalGroupKind): String {
    val office = when (group) {
        PoliticalGroupKind.MILITARY -> CourtOffice.MARSHAL
        PoliticalGroupKind.MERCHANTS, PoliticalGroupKind.NOBILITY -> CourtOffice.TREASURER
        PoliticalGroupKind.FARMERS -> CourtOffice.STEWARD
        PoliticalGroupKind.CULTURAL_REPRESENTATIVES -> CourtOffice.AMBASSADOR
    }
    val person = state.commanders.firstOrNull { it.id == state.court.offices[office] }
    return if (person != null) "Sprecher: ${person.name} · ${office.label}" else when (group) {
        PoliticalGroupKind.MILITARY -> "Sprecherrolle: Veteranenvertretung"
        PoliticalGroupKind.MERCHANTS -> "Sprecherrolle: Handelsgilde"
        PoliticalGroupKind.NOBILITY -> "Sprecherrolle: Adelsrat"
        PoliticalGroupKind.FARMERS -> "Sprecherrolle: Erntegemeinschaft"
        PoliticalGroupKind.CULTURAL_REPRESENTATIVES -> "Sprecherrolle: Gesandte der Kulturen"
    }
}

private fun demandPreview(before: GameState, after: GameState, group: PoliticalGroupKind): String {
    if (before == after) return "Derzeit nicht ausführbar; Ressourcen oder verfügbare Entscheidungszeit fehlen."
    val changes = mutableListOf<String>()
    fun add(label: String, old: Int, new: Int) { if (old != new) changes += "$label $old → $new" }
    add("Gruppenloyalität", before.society.groups.first { it.kind == group }.loyalty, after.society.groups.first { it.kind == group }.loyalty)
    add("Kriegsmüdigkeit", before.society.warExhaustion, after.society.warExhaustion)
    add("Hunger", before.society.hunger, after.society.hunger)
    add("Kriminalität", before.society.crime, after.society.crime)
    add("Kulturspannung", before.society.culturalTension, after.society.culturalTension)
    add("Ungleichheit", before.society.inequality, after.society.inequality)
    add("Sicherheit", before.city.security, after.city.security)
    add("Zufriedenheit", before.city.satisfaction, after.city.satisfaction)
    if (before.city.taxLevel != after.city.taxLevel) changes += "Steuern: ${after.city.taxLevel.label}"
    return changes.joinToString(" · ")
}

@Composable
private fun DiplomacyMetric(
    label: String,
    value: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = Mist, fontSize = androidx.compose.ui.unit.sp(9f), maxLines = 1)
        Text(value.toString(), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = accent,
            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = .08f),
        )
    }
}

@Composable
private fun DiplomacyCard(state: GameState, faction: WorldFaction, apply: (GameEngine.ActionResult) -> Unit) {
    val relation = DiplomacyEngine.relation(state, PLAYER_FACTION, faction.id)
    var expanded by remember(faction.id) { mutableStateOf(false) }
    var kind by remember(faction.id, relation.atWar) { mutableStateOf(if (relation.atWar) TreatyKind.PEACE else TreatyKind.NON_AGGRESSION) }
    var gold by remember(faction.id) { mutableStateOf("0") }
    var food by remember(faction.id) { mutableStateOf("0") }
    var requestedGold by remember(faction.id) { mutableStateOf("0") }
    var tribute by remember(faction.id) { mutableStateOf("50") }
    var playerPays by remember(faction.id) { mutableStateOf(true) }
    KingdomCard {
        Text("${faction.name} · ${if (relation.atWar) "Krieg" else "Frieden"}", color = PaleGold, fontWeight = FontWeight.Bold)
        Text("${faction.ruler} · ${faction.personality.label}", color = Mist)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DiplomacyMetric("Beziehung", ((relation.relation + 100) / 2).coerceIn(0, 100), ModernBlue, Modifier.weight(1f))
            DiplomacyMetric("Vertrauen", relation.trust.coerceIn(0, 100), Success, Modifier.weight(1f))
            DiplomacyMetric("Respekt", relation.respect.coerceIn(0, 100), Gold, Modifier.weight(1f))
            DiplomacyMetric("Furcht", relation.fear.coerceIn(0, 100), Danger, Modifier.weight(1f))
        }
        Text("Ziel: ${faction.longTermGoal}", color = Mist)
        if (relation.atWar || relation.playerWarGoal != null) {
            Text("Kriegsziel: ${relation.playerWarGoal?.label ?: "Noch offen"}", color = ModernBlue)
            Text(WarGoalsEngine.progress(state, faction.id), color = Mist)
            Text("Ausfälle im Krieg: ${relation.warLosses[PLAYER_FACTION] ?: 0} eigene / ${relation.warLosses[faction.id] ?: 0} Gegner · Verhandlungsdruck ${WarGoalsEngine.peacePressure(state, faction.id)}", color = Mist)
        }
        state.diplomacy.politics.firstOrNull { it.factionId == faction.id }?.let { politics ->
            Text("Herrscheralter ${faction.rulerAge} · Nachfolgen ${faction.successionCount}\nStabilität ${politics.stability} · Kriegsmüdigkeit ${politics.warExhaustion} · Nahrungskrise ${politics.hungerDays} Tage", color = Mist)
            politics.history.takeLast(2).forEach { Text("Tag ${it.day}: ${it.text}", color = Mist) }
        }
        SmallAction(if (expanded) "Verhandlungen schließen" else "Vertrag aushandeln") { expanded = !expanded }
        if (expanded) {
            if (relation.atWar) {
                var goal by remember(faction.id) { mutableStateOf(relation.playerWarGoal ?: WarGoal.FORCE_PEACE) }
                var regionId by remember(faction.id) { mutableStateOf(relation.warGoalRegionId) }
                var allyId by remember(faction.id) { mutableStateOf(relation.warGoalAllyId) }
                KingdomChoice("Kriegsziel", goal.label, WarGoal.entries.map { it to it.label }) { goal = it }
                if (goal == WarGoal.SECURE_REGION) {
                    val regions = state.world.places.filter { it.ownerId == faction.id && it.id in state.world.knowledgeFor(PLAYER_FACTION).exploredRegions }
                    KingdomChoice("Zielregion", state.world.place(regionId ?: "")?.name ?: "Bekannte Region wählen", regions.map { it.id to it.name }) { regionId = it }
                }
                if (goal == WarGoal.DEFEND_ALLY) {
                    val allies = state.world.factions.filter { it.id != PLAYER_FACTION && it.id != faction.id &&
                        DiplomacyEngine.hasTreaty(state, PLAYER_FACTION, it.id, TreatyKind.DEFENSIVE_ALLIANCE) && DiplomacyEngine.atWar(state, it.id, faction.id) }
                    KingdomChoice("Verbündeter", state.world.faction(allyId ?: "")?.name ?: "Bündnispartner wählen", allies.map { it.id to it.name }) { allyId = it }
                }
                SmallAction("Kriegsziel festlegen") { apply(WarGoalsEngine.set(state, faction.id, goal, regionId, allyId)) }
                Text("Frieden berücksichtigt bekannte Schlachtausfälle, besetzte Zielregionen, Kriegsdauer und Erschöpfung beider Reiche. Ein Angebot überträgt Ressourcen erst bei Annahme.", color = Mist)
            }
            KingdomChoice("Vertragsart", kind.label, TreatyKind.entries.filter {
                (!relation.atWar || it == TreatyKind.PEACE) && (it != TreatyKind.DYNASTIC_ALLIANCE || state.settings.dynasty)
            }.map { it to it.label }) { kind = it }
            NumberField("Gold anbieten", gold) { gold = it }
            NumberField("Nahrung anbieten", food) { food = it }
            NumberField("Gold vom Partner fordern", requestedGold) { requestedGold = it }
            if (kind in listOf(TreatyKind.TRIBUTE, TreatyKind.VASSAL, TreatyKind.PROTECTION)) {
                NumberField("Tribut alle sieben Tage", tribute) { tribute = it }
                Row { Checkbox(checked = playerPays, onCheckedChange = { playerPays = it }); Text("Unser Reich zahlt Tribut", color = Mist, modifier = Modifier.padding(top = 12.dp)) }
            }
            SmallAction("Angebot senden · Laufzeit 60 Tage") {
                apply(DiplomacyEngine.propose(state, faction.id, kind,
                    Resources(gold.toIntOrNull() ?: 0, food.toIntOrNull() ?: 0, 0, 0, 0),
                    Resources(requestedGold.toIntOrNull() ?: 0, 0, 0, 0, 0), tributeGold = tribute.toIntOrNull() ?: 50,
                    playerPaysTribute = playerPays))
            }
        }
        state.diplomacy.proposals.filter { it.targetFactionId == faction.id && it.status == ProposalStatus.COUNTER_OFFER && it.expiresDay > state.day }.forEach { proposal ->
            Text("${proposal.kind.label}: ${proposal.explanation}\nAngebot ${proposal.offered.gold} Gold, ${proposal.offered.food} Nahrung; Frist Tag ${proposal.expiresDay}", color = PaleGold)
            SmallAction("Gegenangebot annehmen") { apply(DiplomacyEngine.acceptCounter(state, proposal.id)) }
            SmallAction("Gegenangebot ablehnen") { apply(DiplomacyEngine.declineCounter(state, proposal.id)) }
        }
        if (!relation.atWar) {
            var confirmWar by remember(faction.id) { mutableStateOf(false) }
            if (confirmWar) {
                Text("Krieg kann Bündnisse mobilisieren. Bestehende Verträge werden gebrochen und Vertrauen sinkt.", color = Danger)
                SmallAction("Kriegserklärung bestätigen") { apply(DiplomacyEngine.declareWar(state, faction.id)); confirmWar = false }
                SmallAction("Zurück") { confirmWar = false }
            } else SmallAction("Kriegserklärung vorbereiten") { confirmWar = true }
        }
        relation.history.takeLast(2).forEach { Text("Tag ${it.day}: ${it.text}", color = Mist) }
    }
}

@Composable
private fun AgentCard(state: GameState, agent: SpyAgent, apply: (GameEngine.ActionResult) -> Unit) {
    var kind by remember(agent.id) { mutableStateOf(SpyMissionKind.SCOUT_ARMY) }
    var targetId by remember(agent.id) { mutableStateOf(state.world.factions.firstOrNull { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }?.id ?: "") }
    var provinceId by remember(agent.id, targetId) { mutableStateOf<String?>(null) }
    val mission = state.espionage.missions.firstOrNull { it.agentId == agent.id }
    KingdomCard {
        Text("${agent.name} · Fähigkeit ${agent.skill} · Erfahrung ${agent.experience}", color = PaleGold, fontWeight = FontWeight.Bold)
        when {
            agent.capturedByFactionId != null -> {
                Text("Gefangen bei ${state.world.faction(agent.capturedByFactionId)?.name}", color = Danger)
                SmallAction("Auslösen · 350 Gold") { apply(EspionageEngine.ransomAgent(state, agent.id)) }
            }
            mission != null -> Text("${mission.kind.label} · Abschluss Tag ${mission.completesDay}", color = Mist)
            else -> {
                KingdomChoice("Auftrag", kind.label, SpyMissionKind.entries.map { it to it.label }) { kind = it }
                if (kind != SpyMissionKind.COUNTERINTELLIGENCE) {
                    val targets = state.world.factions.filter { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }
                    KingdomChoice("Zielreich", state.world.faction(targetId)?.name ?: "Wählen", targets.map { it.id to it.name }) { targetId = it }
                    val places = state.world.places.filter { it.ownerId == targetId }
                    KingdomChoice("Zielort", state.world.place(provinceId ?: state.world.faction(targetId)?.capitalId ?: "")?.name ?: "Hauptstadt",
                        places.map { it.id to it.name }) { provinceId = it }
                }
                SmallAction("Auftrag beginnen · ${kind.cost} Gold, ${kind.duration} Tage") {
                    apply(EspionageEngine.startMission(state, agent.id, kind,
                        if (kind == SpyMissionKind.COUNTERINTELLIGENCE) PLAYER_FACTION else targetId,
                        if (kind == SpyMissionKind.COUNTERINTELLIGENCE) null else provinceId))
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(9)) }, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun <T> KingdomChoice(label: String, value: String, options: List<Pair<T, String>>, onChoice: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label: $value") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChoice(key); open = false }) }
        }
    }
}
