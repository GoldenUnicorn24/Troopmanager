package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.BattleEngine
import com.goldenunicorn.troopmanager.engine.WarEngine
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.delay

@Composable
internal fun WarManagementPanel(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var replayRecord by remember { mutableStateOf<BattleRecord?>(null) }
    var replayStep by remember { mutableIntStateOf(0) }
    var replayPlaying by remember { mutableStateOf(false) }
    var historyPage by remember { mutableIntStateOf(0) }
    val replay = remember(replayRecord, replayStep) { replayRecord?.let { BattleEngine.replay(it, replayStep) } }
    LaunchedEffect(replayRecord, replayPlaying) {
        val record = replayRecord ?: return@LaunchedEffect
        while (replayPlaying && replayStep < record.inputs.size) {
            delay((1400 / state.settings.battleSpeed.coerceIn(0.5f, 3f)).toLong())
            replayStep += 1
        }
        replayPlaying = false
    }
    fun apply(result: com.goldenunicorn.troopmanager.engine.GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    replay?.let { session ->
        AlertDialog(onDismissRequest = { replayRecord = null; replayPlaying = false }, title = { Text("Schlachtwiederholung") }, text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Minute ${session.minute} · ${session.ownRemaining} / ${session.ownStart} eigene · ${session.enemyRemaining} Gegner")
                    val steps = replayRecord?.inputs?.size ?: 0
                    if (steps > 0) {
                        Slider(value = replayStep.toFloat(), onValueChange = { replayPlaying = false; replayStep = it.toInt() }, valueRange = 0f..steps.toFloat(), steps = (steps - 1).coerceAtLeast(0))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { replayPlaying = false; replayStep = (replayStep - 1).coerceAtLeast(0) }) { Text("Zurück") }
                            TextButton(onClick = { if (replayStep == steps) replayStep = 0; replayPlaying = !replayPlaying }) { Text(if (replayPlaying) "Pause" else "Abspielen") }
                            TextButton(onClick = { replayPlaying = false; replayStep = (replayStep + 1).coerceAtMost(steps) }) { Text("Weiter") }
                        }
                    }
                }
                item { SessionBattleField(session, state.settings.animations, state.settings.battleSpeed) }
                session.log.forEach { entry -> item { Text("Minute ${entry.minute}: ${entry.text} (−${entry.ownLosses}/−${entry.enemyLosses})") } }
            }
        }, confirmButton = { TextButton(onClick = { replayRecord = null; replayPlaying = false }) { Text("Schließen") } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Kriegsführung und Versorgung")
        Text("Lazarett: ${state.war.wounded.sumOf { it.soldiers }} Verwundete · ${state.war.captives.filter { it.own }.sumOf { it.soldiers }} eigene Gefangene", color = PaleGold)
        state.war.wounded.forEach { Text("${it.type.label}: ${it.soldiers} · Rückkehr Tag ${it.recoveryDay}", color = Mist, fontSize = 12.sp) }
        Text("Persönlicher Zustand: ${state.war.playerCondition.label}", color = Mist)
        state.war.commanderConditions.forEach { condition -> Text("${state.commanders.firstOrNull { it.id == condition.commanderId }?.name ?: "Kommandant"}: ${condition.status.label}${if (condition.status in listOf(CombatantStatus.WOUNDED, CombatantStatus.UNCONSCIOUS)) " bis Tag ${condition.untilDay}" else ""}", color = Danger, fontSize = 12.sp) }
        state.war.commanderConditions.filter { it.status == CombatantStatus.CAPTURED }.forEach { condition ->
            OutlinedButton(onClick = { apply(WarEngine.ransomCommander(state, condition.commanderId)) }) { Text("${state.commanders.firstOrNull { it.id == condition.commanderId }?.name ?: "Kommandant"} auslösen · 500 Gold") }
        }
        if (state.war.playerCondition == CombatantStatus.CAPTURED) OutlinedButton(onClick = { apply(WarEngine.ransomCommander(state, null)) }) { Text("Eigene Freilassung verhandeln · 1.000 Gold") }
        Text("Belagerungsdepot ${state.war.siegeFoodStored} Nahrung · Torverstärkung ${state.war.gateReinforcement} · ${if (state.war.civiliansEvacuated) "Zivilisten geschützt" else "Evakuierung offen"}", color = Mist, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { apply(WarEngine.prepareSiege(state, "food")) }) { Text("Nahrung lagern") }
            TextButton(onClick = { apply(WarEngine.prepareSiege(state, "gate")) }) { Text("Tor verstärken") }
        }
        TextButton(onClick = { apply(WarEngine.prepareSiege(state, "evacuate")) }) { Text("Zivilisten evakuieren · 100 Gold / 200 Nahrung") }
        state.war.buildingDamage.filterValues { it > 0 }.forEach { (type, damage) ->
            OutlinedButton(onClick = { apply(WarEngine.repairBuilding(state, type)) }, modifier = Modifier.fillMaxWidth()) { Text("${type.label}: $damage % Schaden · Reparatur ${damage * 3} Gold / ${damage * 2} Holz / ${damage * 2} Stein") }
        }
        SectionTitle("Arsenal und besondere Verbände")
        Text("Arsenal ${WarEngine.effectiveLevel(state, BuildingType.ARSENAL)}: produziert aus Holz/Eisen; Ausrüstung repariert zuerst Heimattruppen.", color = Mist, fontSize = 12.sp)
        state.armyPools.forEach { pool ->
            val batch = state.war.equipment.firstOrNull { it.type == pool.type }
            CompactCard(pool.type.label, "${batch?.quality?.label ?: EquipmentQuality.NORMAL.label} · ${pool.equipment} % Zustand · ${batch?.stock ?: 0} auf Lager")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { apply(WarEngine.upgradeEquipment(state, pool.type)) }) { Text("Qualität verbessern") }
                if (pool.equipment < 50) TextButton(onClick = { apply(WarEngine.improviseEquipment(state, pool.type)) }) { Text("Notrüstung aus Holz") }
                if (pool.experience >= 30) TextButton(onClick = { apply(WarEngine.createElite(state, pool.type, "${state.war.eliteUnits.size + 1}. ${pool.type.label}", minOf(1000, pool.soldiers))) }) { Text("Elite benennen") }
            }
        }
        state.war.eliteUnits.forEach { CompactCard("${it.name} · ${it.banner}", "${it.soldiers} Soldaten · ${it.battles} Schlachten · ${it.victories} Siege · ${it.traits.joinToString()}") }
        SectionTitle("Gefangene")
        state.war.captives.filterNot { it.own }.forEach { group ->
            CompactCard("${group.type?.label ?: group.enemy.label}: ${group.soldiers}", "Gefangen seit Tag ${group.capturedDay}; freiwillige Aufnahme erst nach Bedenkzeit und Diplomatie 40.")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { CaptiveAction.entries.forEach { action -> TextButton(onClick = { apply(WarEngine.captiveAction(state, group.id, action)) }) { Text(action.label, fontSize = 10.sp) } } }
        }
        SectionTitle("Schlachthistorie und Legenden")
        val pageCount = ((state.war.history.size + 23) / 24).coerceAtLeast(1)
        val page = historyPage.coerceIn(0, pageCount - 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { historyPage = page - 1 }, enabled = page > 0) { Text("Neuere") }
            Text("${page + 1} / $pageCount · ${state.war.history.size} Schlachten", color = Mist, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { historyPage = page + 1 }, enabled = page + 1 < pageCount) { Text("Ältere") }
        }
        Text("Alle Zusammenfassungen bleiben erhalten; ausführliche Replays umfassen die letzten 24 Schlachten.", color = Mist, fontSize = 11.sp)
        state.war.history.asReversed().drop(page * 24).take(24).forEach { record ->
            CompactCard("Tag ${record.day} · ${record.place} · ${if (record.victory) "Sieg" else "Niederlage"}", "${record.ownStart} gegen ${record.enemyStart} · ${record.minute} Minuten · ${record.casualties.dead} gefallen / ${record.casualties.wounded} verwundet / ${record.casualties.missing} vermisst / ${record.casualties.captured} gefangen")
            if (record.replay != null) TextButton(onClick = { replayStep = 0; replayRecord = record; replayPlaying = false }) { Text("Schlacht aus Befehlen wiederholen") }
        }
    }
}

/** Long-press drag uses actual zone bounds; buttons provide an accessible equivalent. */
@Composable
internal fun WarFormationPanel(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val session = state.battleSession ?: return
    val targets = remember { mutableStateMapOf<BattleSection, Rect>() }
    var dragging by remember { mutableStateOf(false) }
    var targetSection by remember { mutableStateOf<BattleSection?>(null) }
    val deployments = session.contingents.groupBy { it.commanderId to it.section }.map { (key, troops) -> BattleDeployment(key.first, key.second, troops.groupBy { it.type }.map { (type, entries) -> UnitAllocation(type, entries.sumOf { it.soldiers }) }.filter { it.amount > 0 }) }.filter { it.units.isNotEmpty() }
    fun move(contingent: BattleContingent, destination: BattleSection) {
        val moved = deployments.flatMap { deployment ->
            if (contingent.commanderId != null) listOf(if (deployment.commanderId == contingent.commanderId) deployment.copy(section = destination) else deployment)
            else if (deployment.commanderId == null && deployment.section == contingent.section) {
                val movedUnits = deployment.units.filter { it.type == contingent.type }
                val otherUnits = deployment.units.filterNot { it.type == contingent.type }
                listOf(BattleDeployment(null, deployment.section, otherUnits), BattleDeployment(null, destination, movedUnits)).filter { it.units.isNotEmpty() }
            } else listOf(deployment)
        }.groupBy { it.commanderId to it.section }.map { (key, groups) -> BattleDeployment(key.first, key.second, groups.flatMap { it.units }.groupBy { it.type }.map { (type, units) -> UnitAllocation(type, units.sumOf { it.amount }) }) }
        val result = BattleEngine.redeploy(state, moved); onState(result.state); onNotice(result.message)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUFSTELLUNG · Formationen lange drücken und in eine Zone ziehen", color = PaleGold, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BattleSection.entries.forEach { section ->
                Surface(modifier = Modifier.weight(1f).onGloballyPositioned { targets[section] = it.boundsInRoot() }.border(if (dragging && targetSection == section) 2.dp else 1.dp, if (dragging && targetSection == section) Gold else Mist, RoundedCornerShape(8.dp)), color = Panel2, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) { Text(section.label, color = PaleGold, fontSize = 11.sp); Text("${session.soldiers(section)}", color = Gold); Text(session.terrain[section]?.label ?: "Ebene", color = Mist, fontSize = 10.sp) }
                }
            }
        }
        session.contingents.filter { it.soldiers > 0 }.forEach { contingent ->
            var cardBounds by remember { mutableStateOf(Rect.Zero) }
            var pointer by remember { mutableStateOf(Offset.Zero) }
            Column(Modifier.fillMaxWidth().background(Panel2, RoundedCornerShape(10.dp)).onGloballyPositioned { cardBounds = it.boundsInRoot() }.pointerInput(session, state, contingent.type, contingent.commanderId, contingent.section, targets.toMap()) {
                detectDragGesturesAfterLongPress(onDragStart = { offset -> dragging = true; pointer = cardBounds.topLeft + offset }, onDragCancel = { dragging = false; targetSection = null }, onDragEnd = { targetSection?.let { move(contingent, it) }; dragging = false; targetSection = null }, onDrag = { change, amount -> change.consume(); pointer += amount; targetSection = targets.entries.firstOrNull { it.value.contains(pointer) }?.key })
            }.padding(10.dp)) {
                Text("${contingent.type.label} · ${contingent.soldiers} · ${contingent.section.label}", color = PaleGold, fontSize = 12.sp)
                if (contingent.commanderId != null) Text("${state.commanders.firstOrNull { it.id == contingent.commanderId }?.name ?: "Kommandant"}: zieht mit seinem gesamten Kommando um", color = Mist, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { BattleSection.entries.filterNot { it == contingent.section }.forEach { section -> TextButton(onClick = { move(contingent, section) }) { Text(section.label, fontSize = 10.sp) } } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BattleParticipation.entries.forEach { mode -> OutlinedButton(enabled = mode == BattleParticipation.COMMAND || state.war.playerCondition == CombatantStatus.ACTIVE, onClick = {
                val result = BattleEngine.configure(state, participation = mode); onState(result.state); onNotice(result.message)
            }) { Text((if (session.participation == mode) "✓ " else "") + mode.label, fontSize = 11.sp) } }
        }
        Text("Persönliche Teilnahme erhöht Moral und Führung; Verwundung, Gefangenschaft${if (state.settings.permadeath) " und Tod" else ""} bleiben möglich.", color = Mist, fontSize = 11.sp)
        BattleSection.entries.filterNot { it == BattleSection.RESERVE }.forEach { section ->
            Text(section.label + " · " + (session.terrain[section] ?: BattleTerrain.PLAIN).label, color = PaleGold, fontSize = 12.sp)
            Text((session.terrain[section] ?: BattleTerrain.PLAIN).explanation, color = Mist, fontSize = 11.sp)
        }
    }
}
