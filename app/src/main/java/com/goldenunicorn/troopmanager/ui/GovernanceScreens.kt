package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.engine.CoRulerEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.PresenceEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun GovernanceScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoRulerPanel(state,onState,onNotice)
    }
}

@Composable
internal fun CoRulerPanel(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var page by remember { mutableStateOf(0) }
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    val enabled=CoRulerEngine.isCoRuler(state)
    val regency=CoRulerEngine.regency(state)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Gemeinsam regieren",style=MaterialTheme.typography.headlineSmall)
        GovernmentCard {
            Text("${regency.actor} führt die Regierung",style=MaterialTheme.typography.titleMedium)
            Text(regency.reason,style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress={regency.efficiency/100f},modifier=Modifier.fillMaxWidth())
            Text("Verwaltungswirkung ${regency.efficiency} %",style=MaterialTheme.typography.labelMedium)
            if(!enabled)Text("Die Mitregentschaft beginnt mit einer freiwilligen Ernennung in der Beziehung. Hohe Werte allein ernennen niemanden.")
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Ressort","Vollmacht","Berichte","Aufenthalt").forEachIndexed { index,label ->
                FilterChip(page==index,{page=index},label={Text(label)})
            }
        }
        when(page) {
            0 -> GovernmentCard {
                Text("Hauptressort: ${state.coRuler.portfolio.label}",style=MaterialTheme.typography.titleMedium)
                Text("Ein Ressort bringt konkrete Aufgaben und Entscheidungen. Ausgaben werden im Tagesbericht begründet.")
                CoRulerPortfolio.entries.forEach { portfolio ->
                    FilterChip(selected=state.coRuler.portfolio==portfolio,
                        onClick={apply(CoRulerEngine.setPortfolio(state,portfolio))},enabled=enabled,
                        label={Text(portfolio.label)})
                }
                if(!enabled)Text("Ressortwahl ist nach der Ernennung verfügbar.",style=MaterialTheme.typography.labelMedium)
                Text("${CoRulerEngine.councilCases(state).size} Ratsfragen offen. Der Hofrat zeigt Stimmen und Folgen vor deiner Entscheidung.")
            }
            1 -> GovernmentCard {
                val policy=state.coRuler.delegation
                Text("Begrenzte Delegation",style=MaterialTheme.typography.titleMedium)
                Text("Kriege und offensive Einsätze benötigen immer deinen eigenen Befehl.")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(if(policy.enabled)"Verwaltungsaktionen freigegeben" else "Autonomie pausiert",modifier=Modifier.weight(1f))
                    Switch(policy.enabled,{apply(CoRulerEngine.setDelegation(state,policy.copy(enabled=it)))},enabled=enabled)
                }
                DelegatedAction.entries.forEach { action ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(action.label,modifier=Modifier.weight(1f))
                        Checkbox(action in policy.allowedActions,{ selected ->
                            val allowed=if(selected)policy.allowedActions+action else policy.allowedActions-action
                            apply(CoRulerEngine.setDelegation(state,policy.copy(allowedActions=allowed)))
                        },enabled=enabled)
                    }
                }
                Text("Maximal ${policy.maxGoldPerAction} Gold pro Aktion, ${policy.maxGoldPerDay} pro Tag")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(100,250,500).forEach { limit ->
                        FilterChip(policy.maxGoldPerAction==limit,{apply(CoRulerEngine.setDelegation(state,
                            policy.copy(maxGoldPerAction=limit,maxGoldPerDay=maxOf(limit,policy.maxGoldPerDay))))},enabled=enabled,label={Text("$limit Gold")})
                    }
                }
                Text("Tagesbudget")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(100,250,500).forEach { limit ->
                        FilterChip(policy.maxGoldPerDay==limit,{apply(CoRulerEngine.setDelegation(state,
                            policy.copy(maxGoldPerAction=minOf(limit,policy.maxGoldPerAction),maxGoldPerDay=limit)))},enabled=enabled,label={Text("$limit Gold")})
                    }
                }
                Text("Richtlinie")
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    GovernmentStance.entries.forEach { stance ->FilterChip(policy.stance==stance,
                        {apply(CoRulerEngine.setDelegation(state,policy.copy(stance=stance)))},enabled=enabled,label={Text(stance.label)}) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    GovernmentFocus.entries.forEach { focus ->FilterChip(policy.focus==focus,
                        {apply(CoRulerEngine.setDelegation(state,policy.copy(focus=focus)))},enabled=enabled,label={Text(focus.label)}) }
                }
                Text("Heute delegiert: ${if(state.coRuler.spendingDay==state.day)state.coRuler.goldSpentToday else 0} Gold")
                if(!enabled)Text("Vollmachten sind nach der Ernennung verfügbar.",style=MaterialTheme.typography.labelMedium)
            }
            2 -> {
                val records=state.coRuler.decisions.takeLast(8).reversed()
                if(records.isEmpty())GovernmentCard { Text("Noch keine Regierungsentscheidung protokolliert.") }
                records.forEach { record ->GovernmentCard {
                    Text("Tag ${record.day} · ${record.actor}",style=MaterialTheme.typography.labelLarge)
                    Text(record.title,style=MaterialTheme.typography.titleMedium)
                    Text(record.choice)
                    Text(record.reason,style=MaterialTheme.typography.bodySmall)
                    Text("${record.goldSpent} Gold · ${if(record.autonomous)"delegiert" else "persönlich entschieden"} · ${record.efficiency} % Wirkung",
                        style=MaterialTheme.typography.labelMedium)
                } }
            }
            else -> {
                RulerPresencePanel(state)
                GovernmentCard {
                    Text("Kurze Besuche",style=MaterialTheme.typography.titleMedium)
                    val presence=PresenceEngine.presence(state)
                    val blocker=PresenceEngine.sharedActivityBlocker(state)
                    PresenceLocation.entries.filter { it in listOf(PresenceLocation.PALACE,PresenceLocation.CITY,PresenceLocation.WALL) }.forEach { location ->
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({apply(PresenceEngine.visit(state,location))},enabled=presence.player.available){Text(location.label)}
                            OutlinedButton({apply(PresenceEngine.visit(state,location,true))},enabled=blocker==null){Text("Gemeinsam")}
                        }
                    }
                    if(blocker!=null)Text(blocker,style=MaterialTheme.typography.labelMedium)
                    Text("Eine Reise dauert drei Tage und kostet 75 Gold allein oder 120 Gold gemeinsam. Bei Abwesenheit übernimmt eine Vertretung.")
                    val travelCost=state.resources.gold>=75
                    Button({apply(PresenceEngine.travel(state))},enabled=presence.player.available&&travelCost){Text("Reise antreten")}
                    OutlinedButton({apply(PresenceEngine.travel(state,true))},enabled=blocker==null&&state.resources.gold>=120){Text("Gemeinsam reisen")}
                    if(!travelCost)Text("Für eine Reise fehlen 75 Gold.",style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun RulerPresencePanel(state: GameState) {
    val p=PresenceEngine.presence(state)
    GovernmentCard {
        Text("Aufenthalt",style=MaterialTheme.typography.titleMedium)
        Text("${state.player.name}: ${p.player.location.label}")
        Text(p.player.detail,style=MaterialTheme.typography.bodySmall)
        if(state.companion.met) {
            Text("${state.companion.name}: ${p.companion.location.label}")
            Text(p.companion.detail,style=MaterialTheme.typography.bodySmall)
        }
        listOf(p.player,p.companion).mapNotNull { it.returnDay }.distinct().forEach { day ->
            Text("Rückkehr / Genesung: Tag $day",style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun CouncilScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val cases=CoRulerEngine.councilCases(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Gemeinsamer Hof- und Kriegsrat",style=MaterialTheme.typography.headlineSmall)
        Text("Stimmen begründen ihre Empfehlungen mit Amt, Persönlichkeit und Lage. Du triffst die Entscheidung.")
        if(cases.isEmpty())GovernmentCard { Text("Alle aktuellen Ratsfragen sind entschieden. Neue Anliegen erscheinen an den folgenden Tagen.") }
        cases.forEach { case ->
            var expanded by remember(case.id){mutableStateOf(false)}
            GovernmentCard {
                Text(case.kind.label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(case.title,style=MaterialTheme.typography.titleLarge)
                Text(case.situation)
                TextButton({expanded=!expanded}){Text(if(expanded)"Ratsstimmen schließen" else "${case.recommendations.size} Ratsstimmen anhören")}
                if(expanded)case.recommendations.forEach { vote ->
                    val option=case.options.firstOrNull { it.id==vote.optionId }
                    Text("${vote.member} · ${vote.role}",style=MaterialTheme.typography.titleSmall)
                    Text("Empfiehlt: ${option?.label ?: vote.optionId}",style=MaterialTheme.typography.bodyMedium)
                    Text(vote.reason,style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
                case.options.forEach { option ->
                    val blocker=CoRulerEngine.optionBlocker(state,case.id,option.id)
                    val cost=ResourceKind.entries.filter { it.value(option.cost)>0 }.joinToString { "${it.value(option.cost)} ${it.label}" }
                    OutlinedButton({val result=CoRulerEngine.decide(state,case.id,option.id);onState(result.state);onNotice(result.message)},
                        enabled=blocker==null,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(option.label,style=MaterialTheme.typography.titleSmall)
                            Text(option.explanation,style=MaterialTheme.typography.bodySmall)
                            if(cost.isNotBlank())Text("Kosten: $cost",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    if(blocker!=null)Text(blocker,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun GovernmentCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
