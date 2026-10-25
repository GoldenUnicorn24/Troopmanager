package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** Heer → Front: every war in one place – crown provinces under attack, the wall, hordes and invasions. */
@Composable
internal fun FrontScreen(ctx: HubContext) {
    val state = ctx.state
    fun apply(result: GameEngine.ActionResult) { ctx.onState(result.state); ctx.onNotice(result.message) }
    FrontierScreen(state, ctx.onState, ctx.onNotice) {
        item(key = "war_summary") {
            CrownCard(emphasized = state.crown.threats.isNotEmpty() || state.invasion != null) {
                CardTitle("Kriegslage", "Mauer ${state.realm.wallIntegrity} % · Bedrohung ${state.realm.threat}",
                    when { state.invasion != null || state.crown.threats.isNotEmpty() -> "Angriff"; else -> "Ruhig" },
                    if (state.invasion != null || state.crown.threats.isNotEmpty()) Crimson else Emerald)
                state.invasion?.let {
                    InfoLine("${it.enemy.label} an der Mauer", "Tag ${it.arrivalDay} · ${state.invasionStrengthEstimate()}", Crimson)
                }
                InfoLine("Kronprovinzen unter Angriff", "${state.crown.threats.size}", if (state.crown.threats.isEmpty()) Color.White else Crimson)
                InfoLine("Heer daheim", fmt(state.homeArmySize))
                val storm = state.reign.weekly?.takeIf { !it.resolved && it.kind == WeeklyKind.ORC_STORM }
                if (storm != null) PrimaryAction("Ausfall gegen den Ork-Sturm") { apply(ReignEngine.chooseWeekly(state, "sortie")) }
                if (ReignEngine.has(state, WonderKind.GREAT_WALL)) Text("Große Mauer II: +40 % Verteidigung", color = Emerald, fontSize = 12.sp)
                if (ReignEngine.has(state, WonderKind.DRAGON_BALLISTAE)) Text("Drachen-Ballisten: Feindmoral −20 zu Schlachtbeginn", color = Emerald, fontSize = 12.sp)
            }
        }
        items(state.crown.threats, key = { "threat_" + it.id }) { t -> ThreatCard(state, t, ::apply) }
    }
}

/** Stadt → Großbauten: wonders, feasts and the weekly event. */
@Composable
internal fun WondersScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CrownCard(emphasized = true) {
                CardTitle("Königsfest", "Moral +15 · Zufriedenheit +8 · Loyalität aller Provinzen +8")
                val cooldown = ReignEngine.feastCooldown(state)
                InfoLine("Kosten", "${fmt(ReignEngine.feastCost(state))} Gold · ${fmt(ReignEngine.feastCost(state) / 2)} Nahrung")
                InfoLine("Bisher gefeiert", "${state.reign.feastsHeld}")
                if (cooldown > 0) Text("Nächstes Fest in $cooldown Tagen.", color = Muted, fontSize = 12.sp)
                else PrimaryAction("Fest ausrichten") { apply(ReignEngine.holdFeast(state)) }
            }
        }
        state.reign.weekly?.let { w ->
            item {
                CrownCard {
                    CardTitle("Diese Woche: ${w.kind.label}", if (w.resolved) w.outcome else w.kind.text, if (w.resolved) "Erledigt" else "Offen", if (w.resolved) Emerald else Gold)
                    if (!w.resolved) ReignEngine.weeklyOptions(state, w).forEach { o ->
                        OutlinedButton({ apply(ReignEngine.chooseWeekly(state, o.id)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(o.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(o.detail, color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Heading("Großbauten", "Gewaltige Werke, die dein Reich für immer verändern. Es wird immer nur an einem gebaut.") }
        ReignEngine.building(state)?.let { p ->
            item {
                CrownCard(emphasized = true) {
                    CardTitle(p.kind.label, "Im Bau · fertig an Tag ${p.doneDay}", "Baustelle", ModernBlue)
                    Meter("Fortschritt", (state.day - p.startedDay).coerceAtLeast(0), p.kind.days, ModernBlue)
                    Text(p.kind.effect, color = Mist, fontSize = 12.sp)
                }
            }
        }
        items(WonderKind.entries.toList(), key = { it.name }) { kind ->
            val done = ReignEngine.has(state, kind)
            val blocker = ReignEngine.wonderBlocker(state, kind)
            CrownCard {
                CardTitle(kind.label, kind.description, if (done) "Vollendet" else "${kind.days} Tage", if (done) Emerald else Gold)
                Text(kind.effect, color = if (done) Emerald else PaleGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (!done) {
                    Text(costLabel(ReignEngine.wonderCost(kind)), color = Muted, fontSize = 12.sp)
                    if (blocker == null) PrimaryAction("Bau beginnen") { apply(ReignEngine.startWonder(state, kind)) }
                    else Text(blocker, color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { Heading("Erfolge · ${state.reign.achievements.size}/${AchievementEngine.all.size}") }
        items(AchievementEngine.all, key = { "a_" + it.id }) { a ->
            val got = a.id in state.reign.achievements
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(if (got) "★" else "☆", color = if (got) Gold else Muted, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = if (got) Color.White else Mist, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(a.text + if (a.gold > 0) " · ${fmt(a.gold)} Gold" else "", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
