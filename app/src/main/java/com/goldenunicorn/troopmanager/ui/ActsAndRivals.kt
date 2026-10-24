package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** Current act with goals and the boss button; shown at the top of the Kronlande. */
@Composable
internal fun ActCard(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    val act = ActEngine.current(state)
    PremiumPanel(emphasized = true) {
        if (act == null) {
            Text("ALLE AKTE VOLLENDET", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text("Herrscher der Kronlande", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Die Endgegner sind besiegt. Erobere die letzten Provinzen, werde Kaiser und halte die Horden fern.", color = Mist, fontSize = 13.sp)
            return@PremiumPanel
        }
        Text("AKT ${act.number} VON ${ActCatalog.acts.size}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(act.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(act.summary, color = Mist, fontSize = 13.sp)
        ActEngine.goals(state).forEach { goal ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((if (goal.done) "✓ " else "") + goal.text, color = if (goal.done) Emerald else Mist, fontSize = 13.sp)
                    Text("${goal.current.coerceAtMost(goal.target)} / ${goal.target}", color = if (goal.done) Emerald else PaleGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { (goal.current.toFloat() / goal.target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (goal.done) Emerald else Gold,
                    trackColor = Color.White.copy(alpha = .08f),
                )
            }
        }
        val boss = act.boss
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Portrait(boss.portraitKey, null, 52)
            Column(Modifier.weight(1f)) {
                Text("Endgegner: ${boss.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${boss.title} · ${fmt(ActEngine.bossStrength(state, boss))} ${CrownCatalog.hordeName(boss.enemy)}", color = Crimson, fontSize = 12.sp)
            }
        }
        when {
            state.crown.act.bossBattleActive -> Text("Die Schlacht läuft.", color = PaleGold, fontSize = 13.sp)
            ActEngine.bossReady(state) -> GoldButton("Endkampf beginnen", { apply(ActEngine.startBoss(state)) }, Modifier.fillMaxWidth())
            ActEngine.goalsMet(state) -> Text("Dein Heer sammelt sich. Neuer Versuch ab Tag ${state.crown.act.bossRetryDay}.", color = Muted, fontSize = 13.sp)
            else -> Text("Erfülle alle Ziele, dann stellt sich ${boss.name} dir in einer Schlacht.", color = Muted, fontSize = 12.sp)
        }
        Text("Belohnung: ${fmt(act.rewardGold)} Gold · ${fmt(act.rewardRenown)} Ruhm", color = Muted, fontSize = 12.sp)
    }
}

/** Rival rulers with face, personality, attitude and diplomatic actions. */
@Composable
internal fun RivalsPanel(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    val ready = if (state.crown.rivals.isEmpty()) RivalEngine.initialize(state) else state
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Heading("Die Herrscher der Kronlande", "Jeder hat eigene Pläne. Erobere die Hauptstadt, um ein Reich zu stürzen, oder gewinne den Herrscher als Verbündeten.") }
        items(ActCatalog.rivals, key = { it.id }) { def ->
            val r = RivalEngine.rival(ready, def.id) ?: RivalState(def.id)
            val capital = CrownEngine.province(ready, def.capitalId)
            val color = when (r.status) {
                RivalStatus.ALLIED -> Emerald
                RivalStatus.HOSTILE -> Crimson
                RivalStatus.DEFEATED -> Muted
                RivalStatus.NEUTRAL -> Gold
            }
            PremiumPanel(emphasized = r.status == RivalStatus.HOSTILE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Portrait(def.portraitKey, null, 64, def.look)
                    Column(Modifier.weight(1f)) {
                        Text(def.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(def.title, color = Muted, fontSize = 12.sp)
                    }
                    ModernPill(r.status.label, color, filled = true)
                }
                Text(def.motto, color = PaleGold, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                Text("${def.personality.label}: ${def.personality.description}", color = Mist, fontSize = 13.sp)
                if (r.status != RivalStatus.DEFEATED) {
                    val attitude = r.attitude
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Haltung dir gegenüber", color = Mist, fontSize = 12.sp)
                            Text("$attitude", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { ((attitude + 100) / 200f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp), color = color, trackColor = Color.White.copy(alpha = .08f),
                        )
                    }
                    Text("Hauptstadt: ${capital?.name ?: "–"} · ${fmt(capital?.garrison ?: 0)} Verteidiger", color = Muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { SmallAction("Geschenk · ${fmt(RivalEngine.giftCost(ready))}") { apply(RivalEngine.gift(ready, def.id)) } }
                        if (r.status != RivalStatus.ALLIED) Box(Modifier.weight(1f)) { SmallAction("Bündnis (Haltung 40)") { apply(RivalEngine.proposeAlliance(ready, def.id)) } }
                    }
                } else Text("Sein Reich ist zerfallen. Die übrigen Provinzen sind freies Land.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}
