package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** Shared preparation and invasion editor; applying always calls the real saved engine API. */
@Composable
internal fun BattlePlanPanel(state: GameState, mode: String = "plan", onApply: (GameEngine.ActionResult) -> Unit,
    onDeploy: (() -> Unit)? = null) {
    val battle = state.battleSession
    val activePlan = battle?.plan ?: state.defensePlan
    var draft by remember(activePlan) { mutableStateOf(activePlan) }
    val cost = battle?.let { BattleEngine.planChangeCost(it, draft) } ?: 0
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("battle_plan_panel"),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(if (battle?.step == 0) "Schlachtvorbereitung" else if (battle == null) "Verteidigungsplan" else "Taktik & Zielprioritäten",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(if (battle == null) "Der gespeicherte Plan gilt für die nächste Verteidigung."
                else if (battle.step == 0) "Vor Kampfbeginn kostenlos. Dieser Plan gilt für die vorbereitete Schlacht."
                else "Taktikwechsel: 3 BP und −3 Kohäsion. Andere Planänderungen: 2 BP.", color = Mist, fontSize = 12.sp)
        }
        if (mode == "plan") item {
            SelectionMenu("Taktikprofil", draft.doctrine.label, BattleDoctrine.entries, { it.label }) { draft = BattlePlan.profile(it) }
            Text(draft.doctrine.description, color = Gold, fontSize = 13.sp)
        }
        if (mode != "reserve") item {
            SelectionMenu("Zielpriorität Fernkampf", draft.rangedPriority.label, TargetPriority.entries, { it.label }) { draft = draft.copy(rangedPriority = it) }
            SelectionMenu("Mauerwaffen-Priorität", draft.wallWeaponPriority.label, WallTargetPriority.entries, { it.label }) { draft = draft.copy(wallWeaponPriority = it) }
            if (draft.wallWeaponPriority == WallTargetPriority.FRONT) {
                SelectionMenu("Nur diese Mauerfront feuert", draft.wallWeaponFront.label, BattleStateEngine.sections, { it.label }) { draft = draft.copy(wallWeaponFront = it) }
            }
            SelectionMenu("Munition / Schussdisziplin", draft.ammunitionPolicy.label, AmmunitionPolicy.entries, { it.label }) { draft = draft.copy(ammunitionPolicy = it) }
            Text("Feuerrate ×${draft.ammunitionPolicy.fireRate}, Pfeilverbrauch ×${draft.ammunitionPolicy.consumption}. Leere Vorräte stoppen das Feuer.", color = Mist, fontSize = 12.sp)
        }
        if (mode != "targets") item {
            SelectionMenu("Reserveverhalten", draft.reservePolicy.label, ReservePolicy.entries, { it.label }) { draft = draft.copy(reservePolicy = it) }
            Text("Automatische Verstärkung benötigt einen 5-Minuten-Einsatzschritt. Manuelle Befehle verbrauchen BP.", color = Mist, fontSize = 12.sp)
            SelectionMenu("Tor / Ausfall", draft.gatePolicy.label, GatePolicy.entries, { it.label }) { draft = draft.copy(gatePolicy = it) }
            SelectionMenu("Breschenreserve zuerst", draft.breachReserveSection.label, BattleStateEngine.sections, { it.label }) { draft = draft.copy(breachReserveSection = it) }
            SelectionMenu("Reserve je Breschen-Einsatz", "${draft.breachReservePercent}%", listOf(10, 30, 50, 100), { "$it%" }) { draft = draft.copy(breachReservePercent = it) }
            SelectionMenu("Rückfallregel", draft.fallbackPolicy.label, FallbackPolicy.entries, { it.label }) { draft = draft.copy(fallbackPolicy = it) }
            SelectionMenu("Verfolgung nach Moralbruch", draft.pursuitPolicy.label, PursuitPolicy.entries, { it.label }) { draft = draft.copy(pursuitPolicy = it) }
        }
        item {
            Button(onClick = { onApply(BattleEngine.configurePlan(state, draft)) },
                enabled = battle == null || battle.isActive && battle.commandPoints >= cost,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("battle_plan_apply"),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                Text(if (cost == 0) "Plan speichern" else "Anwenden · $cost BP")
            }
            if (onDeploy != null && battle?.step == 0) OutlinedButton(onClick = onDeploy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("battle_deploy")) { Text("Aufstellung ändern") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DefenseDashboard(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, onClose: () -> Unit) {
    var editPlan by remember { mutableStateOf(false) }
    val ready = WarEngine.defenseReadiness(state)
    val horde = state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }
    val invasion = state.invasion
    val enemyArmy = horde?.worldArmyId?.let { id -> state.world.armies.firstOrNull { it.id == id } }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (editPlan) BattlePlanPanel(state, onApply = { result ->
            onState(result.state); onNotice(result.message); editPlan = false
        }) else LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f).testTag("defense_dashboard"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Verteidigungs-Dashboard", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(horde?.name ?: invasion?.enemy?.label ?: "Festungsbereitschaft", color = Gold)
                Text("Gegner: ${horde?.estimatedStrengthLabel ?: if (invasion != null) state.invasionStrengthEstimate() else "keine Invasion angekündigt"}", color = Mist)
                Text("Ankunft: ${horde?.daysToArrival ?: invasion?.let { (it.arrivalDay - state.day).coerceAtLeast(0) } ?: "?"} Tage · Herkunft: ${horde?.origin ?: "unbekannt"} · Front: noch unbekannt", color = Mist, fontSize = 13.sp)
                val scouted = enemyArmy?.let { army -> state.world.knowledge.firstOrNull { it.factionId == PLAYER_FACTION }?.observations?.any { it.armyId == army.id && it.exact } == true } == true
                val known = enemyArmy?.units?.takeIf { scouted }
                Text("Zusammensetzung: ${known?.joinToString { "${it.amount} ${it.type.label}" } ?: "noch nicht aufgeklärt"}", color = Mist, fontSize = 13.sp)
                val devices = invasion?.devices.orEmpty()
                if (devices.isNotEmpty()) Text("Bekannte Geräte: ${devices.joinToString { it.label }}", color = Danger, fontSize = 13.sp)
            }
            item {
                PremiumPanel {
                    Text("${ready.garrison} Garnison · ${ready.reserve} Reserve", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${ready.wallArchers} Bogenschützen auf Mauer-Schussplätzen · ${ready.arrows} Pfeile", color = Gold)
                    Text("Mauer ${ready.wall}% · Tor ${ready.gate}% · Türme Stufe ${ready.tower}", color = Mist)
                    Text("${ready.activeWeapons} aktive Mauerwaffen · ${state.militaryStock.siegeParts} Artillerieladungen", color = ModernBlue)
                    Text("Plan: ${state.defensePlan.doctrine.label} · ${state.defensePlan.ammunitionPolicy.label}", color = Gold)
                }
            }
            items(state.frontier.weapons.size) { index ->
                val weapon = state.frontier.weapons[index]
                if (weapon.count > 0) Text("${weapon.type.label} ×${weapon.count} · ${weapon.section.label} · ${weapon.integrity}% · ${weapon.ammunition} Ladungen", color = Mist, fontSize = 13.sp)
            }
            items(ready.warnings.size) { index -> Text("⚠ ${ready.warnings[index]}", color = Danger) }
            item {
                Button(onClick = { editPlan = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("defense_plan_edit")) { Text("Verteidigungstaktik einstellen") }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Schließen") }
            }
        }
    }
}
