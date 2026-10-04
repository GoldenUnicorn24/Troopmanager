package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** Fixed HUD, dominant battlefield and a compact command dock; details live in sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveBattleScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val battle = state.battleSession ?: return
    var selected by rememberSaveable { mutableStateOf(BattleSection.CENTER) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var detailTab by rememberSaveable { mutableIntStateOf(0) }
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    fun execute(order: BattleDecision) {
        sheet = null
        apply(if (battle.pendingEvent != null) BattleEngine.advance(state, order) else BattleEngine.order(state, order, selected))
    }
    if (setup) BattleSetupDialog(state, { setup = false }, onState, onNotice)
    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (sheet == "orders") {
            val section = battle.pendingEvent?.section ?: selected
            val orders = battle.pendingEvent?.options ?: BattleEngine.contextualOrders(battle, section)
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Text("${section.label} · Befehle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                items(orders) { order ->
                    val cost = BattleEngine.orderCost(battle, order)
                    OutlinedButton(onClick = { execute(order) }, enabled = battle.commandPoints >= cost && BattleEngine.canOrder(battle, order, section),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(order.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(BattleOrderExplanation(order), color = Mist, fontSize = 11.sp)
                        }
                        Text("$cost BP", color = ModernBlue, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.87f).padding(horizontal = 16.dp)) {
                ModernTabStrip(listOf("Bericht", "Heer", "Verlauf", "Vorbereitung"), detailTab, { detailTab = it })
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                    when (detailTab) {
                        0 -> {
                            if (!battle.isActive) item { BattleAfterAction(state, battle) }
                            items(battle.exchanges.asReversed()) { report ->
                                PremiumPanel {
                                    Text("Austausch ${report.minute}′ · −${report.ownLosses} / −${report.enemyLosses}", color = Color.White, fontWeight = FontWeight.Bold)
                                    report.fronts.forEach { front ->
                                        Text("${front.section.label} · ${front.contactState.label}", color = ModernBlue, fontSize = 12.sp)
                                        Text("−${front.ownDamage.total} eigene / −${front.enemyDamage.total} Gegner · aktive Linie ${front.ownActive}/${front.enemyActive} · Breite ${front.frontage}", color = Mist, fontSize = 12.sp)
                                        Text("Eigene Ausfälle: ${front.ownDamage.ranged} Pfeile · ${front.ownDamage.melee} Nahkampf · ${front.ownDamage.splash} Splitter", color = Mist, fontSize = 12.sp)
                                        Text("Deckung verhinderte rechnerisch ${String.format(java.util.Locale.GERMAN, "%.1f", front.preventedLosses)} weitere Ausfälle · ${front.arrowsUsed} Pfeile verbraucht", color = Success, fontSize = 12.sp)
                                        if (front.deviceDamage + front.artilleryChargesUsed > 0) Text("Geräteschaden ${front.deviceDamage} · Artillerieladungen ${front.artilleryChargesUsed}", color = ModernBlue, fontSize = 12.sp)
                                        if (front.structuralDamage + front.gateDamage > 0) Text("Mauer −${front.structuralDamage} · Tor −${front.gateDamage}", color = Danger, fontSize = 12.sp)
                                    }
                                    report.events.forEach { Text(it, color = Mist, fontSize = 12.sp) }
                                }
                            }
                            if (battle.exchanges.isEmpty()) item { EmptyCard("Noch kein Austausch. Schaden, Deckung und Kontakt werden hier nach jedem Schritt erklärt.") }
                        }
                        1 -> items(battle.contingents) { unit ->
                            PremiumPanel {
                                Text("${unit.displayName ?: unit.type.label} · ${unit.soldiers}/${unit.startSoldiers}", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${unit.section.label} · ${unit.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.name } ?: state.player.name}", color = ModernBlue, fontSize = 12.sp)
                                Text("Moral ${unit.morale} · Kohäsion ${unit.cohesion} · Erschöpfung ${unit.fatigue}", color = Mist, fontSize = 12.sp)
                                Text("Erfahrung ${unit.experience} · Ausrüstung ${unit.equipment}% · ${MoraleState.from(unit.morale, unit.routed).label}", color = Mist, fontSize = 12.sp)
                                if (unit.commanderWounded) Text(if (unit.commanderRescued) "Kommandant geborgen" else "Kommandant verwundet · lokale Führung geschwächt", color = Danger)
                            }
                        }
                        2 -> items(battle.log.asReversed()) { entry ->
                            Text("${entry.minute}′ · ${entry.text}", color = Mist, fontSize = 13.sp)
                        }
                        else -> {
                            item { BattlePreparationSummary(state, battle) }
                            if (battle.minute == 0 && battle.isActive) {
                                item { WarFormationPanel(state, onState, onNotice) }
                                item {
                                    SelectionMenu("Persönliche Position", battle.personalSection.label, BattleStateEngine.sections, { it.label }) {
                                        apply(BattleEngine.configure(state, personalSection = it))
                                    }
                                    SelectionMenu("Teilnahme", battle.participation.label, BattleParticipation.entries, { it.label }) {
                                        apply(BattleEngine.configure(state, participation = it))
                                    }
                                }
                            }
                            items(battle.wallWeapons) { weapon ->
                                PremiumPanel {
                                    Text("${weapon.type.label} ×${weapon.count}", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${weapon.ammunition} Ladungen · ${weapon.integrity}% Zustand · Nachladen ${weapon.reloadRounds} · ${weapon.priority.label}", color = Mist, fontSize = 12.sp)
                                    if (battle.minute == 0 && battle.isActive) {
                                        SelectionMenu("Mauerabschnitt", weapon.section.label, BattleStateEngine.sections, { it.label }) {
                                            apply(FrontierEngine.configureWallWeapon(state, weapon.type, it, weapon.priority, weapon.automatic))
                                        }
                                        SelectionMenu("Zielpriorität", weapon.priority.label, WallWeaponPriority.entries, { it.label }) {
                                            apply(FrontierEngine.configureWallWeapon(state, weapon.type, weapon.section, it, weapon.automatic))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(weapon.automatic, { apply(FrontierEngine.configureWallWeapon(state, weapon.type, weapon.section, weapon.priority, it)) })
                                            Text("Automatik", color = Mist)
                                        }
                                    }
                                }
                            }
                            items(battle.siegeDevices.filter { it.detected }) { device ->
                                Text("${device.section.label}: ${device.type.label} · ${device.integrity}% · ${device.distance} m · Sturm ${device.progress}%${if (device.disabled) " · ausgeschaltet" else ""}", color = if (device.disabled) Mist else Danger, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).testTag("battle_screen")) {
        val compact = maxHeight < 450.dp || LocalDensity.current.fontScale >= 1.3f
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
            Row(Modifier.fillMaxWidth().height(if (compact) 20.dp else 54.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                BattleHudValue("HEER", battle.ownRemaining.toString(), Gold, compact)
                BattleHudValue("GEGNER", battle.enemyRemaining.toString(), Danger, compact)
                BattleHudValue("MIN", "${battle.minute}′", Color.White, compact)
                BattleHudValue("MORAL", "${battle.morale}%", Success, compact)
                BattleHudValue("BEFEHLE", "${battle.commandPoints} BP", ModernBlue, compact)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                BattleStateEngine.sections.forEach { section ->
                    val segment = battle.segment(section)
                    val highlighted = selected == section
                    Surface(onClick = { selected = section }, modifier = Modifier.weight(1f).height(48.dp).testTag("battle_front_${section.name}").semantics { this.selected = highlighted },
                        color = if (selected == section) Panel2 else Panel, shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(if (selected == section) 2.dp else 0.dp, if (selected == section) ModernBlue else Color.Transparent)) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text(when (section) { BattleSection.LEFT -> "LINKS"; BattleSection.RIGHT -> "RECHTS"; else -> "TOR / MITTE" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (battle.tactic == Tactic.FORTIFY) "${segment?.integrity ?: battle.wallIntegrity}% · ${segment?.contactState?.label ?: "Anmarsch"}" else segment?.contactState?.label ?: "Anmarsch",
                                color = if (segment?.contactState?.allowsMelee == true) Danger else Mist, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().testTag("battle_field")) {
                SessionBattleField(battle, state.settings.animations, state.settings.battleSpeed, battle.wallWeapons,
                    Modifier.fillMaxSize(), selected) { selected = it }
                battle.pendingEvent?.let { event ->
                    Surface(modifier = Modifier.align(Alignment.Center).fillMaxWidth(.93f), color = Panel2.copy(alpha = .97f),
                        shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ModernBlue.copy(alpha = .7f))) {
                        Column(Modifier.padding(if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                            if (!compact) Text(event.text, color = Mist, fontSize = 12.sp, maxLines = 3)
                            event.options.take(if (compact) 1 else 2).forEach { order ->
                                OutlinedButton(onClick = { execute(order) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = battle.commandPoints >= BattleEngine.orderCost(battle, order) && BattleEngine.canOrder(battle, order, event.section)) {
                                    Text("${order.label} · ${BattleEngine.orderCost(battle, order)} BP", maxLines = 1, fontSize = 12.sp)
                                }
                            }
                            if (event.options.size > if (compact) 1 else 2) TextButton(onClick = { sheet = "orders" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Weitere Reaktionen") }
                        }
                    }
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 0.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val report = battle.lastReport(selected)
                    val segment = battle.segment(selected)
                    val active = report?.ownActive ?: 0
                    val secondLine = (battle.fighting(selected) - active).coerceAtLeast(0)
                    if (!compact) Text(if (!battle.isActive) battle.outcomeGrade?.label ?: "Schlacht beendet" else
                        "${selected.label} · $active aktiv · $secondLine zweite Linie · ${battle.battleArrowsRemaining} Pfeile", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text(if (report == null) "${segment?.contactState?.label ?: battle.tacticalStageLabel} · Reserve ${battle.fighting(BattleSection.RESERVE)}" else
                        "−${report.ownDamage.total} eigene / −${report.enemyDamage.total} Gegner · Deckung verhinderte ≈${report.preventedLosses.toInt()} Ausfälle", color = Mist, fontSize = 11.sp, maxLines = 1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (battle.isActive) {
                            OutlinedButton(onClick = { if (battle.minute == 0 && state.world.encounter == null) setup = true else sheet = "orders" },
                                modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (battle.minute == 0 && state.world.encounter == null) "Aufstellung" else "Befehle", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Button(onClick = { if (battle.pendingEvent == null) apply(BattleEngine.advance(state)) else sheet = "orders" },
                                modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                                Text(if (battle.pendingEvent == null) "+5 Minuten" else "Reagieren", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else Button(onClick = { onState(state.copy(battleSession = null)); onNotice("Bericht und Replay bleiben in der Chronik.") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Zur Welt") }
                        TextButton(onClick = { detailTab = if (battle.minute == 0) 3 else 0; sheet = "details" },
                            modifier = Modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Details", color = ModernBlue, fontSize = 12.sp, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BattleHudValue(label: String, value: String, accent: Color, compact: Boolean = false) {
    if (compact) {
        Text("${label.take(3)} $value", color = accent, fontSize = 11.sp, maxLines = 1)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Mist, fontSize = 8.sp)
    }
}

@Composable
internal fun BattlePreparationSummary(state: GameState, battle: BattleSession) {
    val shooters = battle.contingents.filter { it.type.ranged >= 8 && it.type != UnitType.DRAGON_ARTILLERY }.sumOf { it.soldiers }
    val exchanges = if (shooters == 0) 0 else battle.battleArrowsRemaining / shooters
    PremiumPanel {
        Text("VERSORGUNG & FESTUNG", color = Color.White, fontWeight = FontWeight.Bold)
        Text(if (shooters == 0) "Keine Bogenschützen aufgestellt" else "${battle.battleArrowsRemaining} Pfeile · voraussichtlich $exchanges Austausche mit Normalfeuer", color = if (shooters > 0 && exchanges < 3) Danger else Mist)
        Text("Artillerie: ${battle.battleArtilleryRemaining} Ladungen · Gegen-Tunnel ${if (battle.counterTunnelUnlocked) "freigeschaltet" else "benötigt Belagerungsforschung oder erfahrene Führung"}", color = Mist, fontSize = 12.sp)
        Text("Lazarett ${state.war.wounded.sumOf { it.soldiers }}/${WarEngine.hospitalCapacity(state)} · ${state.militaryStock.medicine} Heilmittel", color = Mist, fontSize = 12.sp)
        Text("${state.awayArmySize} Soldaten fehlen zu Hause · Reserve ${battle.fighting(BattleSection.RESERVE)}", color = Mist, fontSize = 12.sp)
        if (state.resources.food == 0) Text("Hunger: weniger Kampfkraft, Moral und Kohäsion gefährdet", color = Danger)
        battle.segments.forEach { Text("${it.section.label}: Mauer ${it.integrity}% · ${if (it.section == BattleSection.CENTER) "Tor ${it.gateIntegrity}% · " else ""}Deckung ${(it.cover * 100).toInt()}%", color = Mist, fontSize = 12.sp) }
    }
}

@Composable
private fun BattleAfterAction(state: GameState, battle: BattleSession) {
    PremiumPanel(emphasized = true) {
        Text(battle.outcomeGrade?.label ?: "Schlacht beendet", color = if (battle.status == BattleStatus.VICTORY) Success else Danger, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("${battle.casualties.dead} gefallen · ${battle.casualties.wounded} verwundet · ${battle.casualties.missing} vermisst · ${battle.casualties.captured} gefangen", color = Mist)
        Text("${battle.battleArrowsLoaded - battle.battleArrowsRemaining} Pfeile verbraucht · +${battle.xpReward} XP · +${battle.renownReward} Ruhm", color = Gold, fontSize = 12.sp)
        battle.weaponChargesUsed.forEach { (type, count) -> Text("${type.label}: $count Ladungen", color = Mist, fontSize = 12.sp) }
        battle.segments.forEach { segment ->
            Text("${segment.section.label}: ${battle.contingents.filter { it.section == segment.section }.sumOf { it.startSoldiers - it.soldiers }} Ausfälle · Mauer ${segment.integrity}% · Tor ${segment.gateIntegrity}%", color = Mist, fontSize = 12.sp)
        }
        Text("Belagerungsgeräte: ${battle.siegeDevices.count { it.disabled }} ausgeschaltet / ${battle.siegeDevices.count { !it.disabled }} verbleiben", color = Mist, fontSize = 12.sp)
        Text("Kriegsmüdigkeit ${state.society.warExhaustion}% · Zufriedenheit ${state.city.satisfaction}%", color = ModernBlue, fontSize = 12.sp)
        Text("${battle.battleArtilleryLoaded - battle.battleArtilleryRemaining} Artillerieladungen verbraucht", color = Mist, fontSize = 12.sp)
        state.war.history.lastOrNull { it.seed == battle.seed && it.day == state.day }?.aftermath?.forEach { Text(it, color = ModernBlue, fontSize = 12.sp) }
        battle.commanderEvents.forEach { Text(it, color = Mist, fontSize = 12.sp) }
    }
}

private fun BattleOrderExplanation(order: BattleDecision): String = when (order) {
    BattleDecision.HOLD_FIRE -> "Kein Fernkampfschaden; spart Munition. Deckung bleibt gleich."
    BattleDecision.NORMAL_FIRE -> "Schützen nehmen Normalfeuer wieder auf."
    BattleDecision.ARROW_VOLLEY -> "Lokaler Burst; dreifacher Pfeilverbrauch und Moralwirkung."
    BattleDecision.FOCUS_FIRE, BattleDecision.FOCUS_ARCHERS -> "Konzentriert lokale Schützen; höherer Verbrauch bei Fokusfeuer."
    BattleDecision.PRIORITIZE_DEVICES -> "Schützen und Mauerwaffen zielen auf lokale Geräte; weniger Beschuss auf Infanterie."
    BattleDecision.REPEL_LADDERS -> "Bremst Leitern und Kletterer an der Mauer."
    BattleDecision.FIRE_OIL -> "Verbraucht reale Ölladungen; wirkt nur in Mauernähe."
    BattleDecision.COUNTER_TUNNEL -> "Personal beschädigt einen entdeckten Tunnel."
    BattleDecision.HOLD_BREACH, BattleDecision.SECOND_LINE, BattleDecision.HOLD_GATE -> "Stabilisiert eine lokale Engpassverteidigung."
    BattleDecision.ROTATE_RESERVE -> "Tauscht eine erschöpfte Formation gegen frische Reserve."
    BattleDecision.FALL_BACK_COURTYARD -> "Verlässt die Mauer; Straßenengpässe bleiben erhalten."
    BattleDecision.ORDERED_RETREAT -> "Beendet den Kampf; weniger Vermisste und Gefangene."
    BattleDecision.RESCUE_COMMANDER -> "20 Reservisten bergen den lokal verwundeten Anführer."
    BattleDecision.ARTILLERY_TARGET -> "Beschädigt ein tatsächliches Gerät; braucht lokale Artillerie und reale Ladungen."
    else -> "${order.label} am gewählten Abschnitt."
}
