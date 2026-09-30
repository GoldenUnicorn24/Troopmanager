package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun RealmDashboard(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onCity: () -> Unit,
    onWorld: () -> Unit,
    onArmy: () -> Unit,
    onCourt: () -> Unit,
) {
    fun apply(result: GameEngine.ActionResult) {
        onState(result.state)
        onNotice(result.message)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                "TAG ${state.day} · ${state.realm.settlementTier.label.uppercase()}",
                "${state.player.name} · ${state.title}",
            )
        }
        item { EconomyStrip(state) }
        item {
            GoldButton(
                "Nächsten Tag beginnen",
                { apply(GameEngine.advanceDay(state)) },
                Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCity,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Stadt & Bauen")
                }
                OutlinedButton(
                    onClick = onWorld,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Mission planen")
                }
            }
        }
        if (state.player.skillPoints > 0)
            item {
                DashboardCard(
                    "${state.player.skillPoints} Skillpunkte verfügbar",
                    "Stufe ${state.player.level} · Stärke deinen Herrscher im Hof.",
                    "Skillpunkte vergeben",
                    onCourt,
                )
            }
        state.invasion?.let { invasion ->
            item {
                Surface(color = Color(0xFF351F22), shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Invasion · ${invasion.enemy.label}",
                            color = PaleGold,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Ankunft Tag ${invasion.arrivalDay} · Stärke ${invasion.strength} · Mauer ${state.realm.wallIntegrity}%",
                            color = Mist,
                        )
                        SmallAction("Verteidigung in der Stadt prüfen", onCity)
                        if (state.realm.wallIntegrity < 100)
                            SmallAction("Mauer reparieren") { apply(GameEngine.repairWall(state)) }
                        if (!invasion.alliesRequested)
                            SmallAction("Verbündete um Hilfe bitten") {
                                apply(GameEngine.requestAllies(state))
                            }
                    }
                }
            }
        }
        if (state.city.constructionQueue.isNotEmpty())
            item {
                DashboardCard(
                    "${state.city.constructionQueue.size} Bauprojekte",
                    state.city.constructionQueue.joinToString("\n") {
                        "${it.type.label}: ${it.daysRemaining} Tage bis Stufe ${it.targetLevel}"
                    },
                    "Bauverwaltung",
                    onCity,
                )
            }
        val away = state.activeMissions.filter { it.status.isAway }
        if (away.isNotEmpty())
            item {
                DashboardCard(
                    "${away.size} laufende Missionen",
                    away.joinToString("\n") {
                        "${it.missionType.label} · ${it.total} Soldaten · ${it.remainingDays} Tage"
                    },
                    "Missionen ansehen",
                    onWorld,
                )
            }
        state.pendingRealmEvent?.let { event ->
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${event.category} · ${event.title}",
                            color = PaleGold,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(event.text, color = Mist)
                        EventEngine.choices(event).forEachIndexed { index, label ->
                            SmallAction(label) { apply(EventEngine.choose(state, index)) }
                        }
                    }
                }
            }
        }
        item {
            DashboardCard(
                "Dein nächster Schritt",
                if (state.realm.wallIntegrity < 80) "Die Mauer braucht Reparaturen."
                else if (state.population.total >= state.city.housingCapacity)
                    "Wohnraum ist voll. Baue ein Wohnviertel."
                else if (state.armyPools.any { it.equipment < 70 })
                    "Erneuere die Ausrüstung deiner Truppen."
                else "Baue deine Wirtschaft aus und plane einen sicheren Einsatz.",
                "Truppen & Kommandanten",
                onArmy,
            )
        }
        item {
            StatGrid(
                listOf(
                    "Bevölkerung" to "${state.population.total}/${state.city.housingCapacity}",
                    "Armee zu Hause" to state.homeArmySize.toString(),
                    "Armee unterwegs" to state.awayArmySize.toString(),
                    "Ruhm" to state.renown.toString(),
                )
            )
        }
        if (state.completedRealm)
            item { EmptyCard("Hochkönigreich errichtet. Dein Reich bleibt offen spielbar.") }
    }
}

@Composable
private fun DashboardCard(title: String, text: String, action: String, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = PaleGold, fontWeight = FontWeight.Bold)
            Text(text, color = Mist)
            SmallAction(action, onClick)
        }
    }
}
