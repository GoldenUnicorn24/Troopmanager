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
import androidx.compose.ui.unit.sp
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
    onAdvanceDay: () -> Unit,
    onNavigate: (GameDestination) -> Unit,
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
            RealmHero(
                state = state,
                onPrimary = onCity,
                onSecondary = onWorld,
            )
        }
        item { EconomyStrip(state) }
        item { NextMoveCard(state, onNavigate) }
        item { CampaignPulseCard(state, onState, onNotice) }
        item {
            val production = EconomyEngine.production(state)
            val foodDeficit = (production.upkeep - production.gross.food).coerceAtLeast(0)
            val foodDays = if (foodDeficit == 0) "stabil" else "${state.resources.food / foodDeficit.coerceAtLeast(1)} T."
            val nearest = state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }
            val presentCultures = Culture.entries.filter { state.population.count(it) > 0 }
            val weakest = presentCultures.minByOrNull {
                FrontierEngine.cultureStanding(state, it) + FrontierEngine.cultureIntegration(state, it)
            }
            PremiumPanel {
                ModernSectionHeader("Reich", "Lageübersicht")
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "${state.realm.settlementName} · ${state.world.weather.season.label} · Mauer ${state.realm.wallIntegrity}%",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.relationship.romanceStage == RomanceStage.CO_RULERS) {
                        Text(
                            "${state.player.name} & ${state.companion.name} · gemeinsames Ressort: ${state.coRuler.portfolio.label}",
                            color = Gold,
                            fontSize = 12.sp,
                        )
                    } else {
                        Text("${state.player.name} · ${state.title}", color = Gold, fontSize = 12.sp)
                    }
                    StatGrid(
                        listOf(
                            "Einwohner" to "${state.population.total}",
                            "Garnison" to "${state.homeArmySize}",
                            "Nahrung" to foodDays,
                            "Sicherheit" to "${state.city.security}%",
                        )
                    )
                    nearest?.let {
                        Text("⚠ ${it.name} · ${it.estimatedStrengthLabel} · noch ${it.daysToArrival} Tage", color = Danger, fontSize = 12.sp)
                    } ?: Text("Grenze: keine unmittelbar entdeckte Horde", color = Success, fontSize = 12.sp)
                    weakest?.let { culture ->
                        val standing = FrontierEngine.cultureStanding(state, culture)
                        val integration = FrontierEngine.cultureIntegration(state, culture)
                        Text(
                            "Völkerlage: ${culture.label} am schwächsten eingebunden · Loyalität $standing · Integration $integration",
                            color = if (standing < 35 || integration < 35) Danger else Mist,
                            fontSize = 11.sp,
                        )
                    }
                    if (state.dynasty.successionTension > 0) {
                        Text(
                            "Nachfolge: ${state.dynasty.successionTension}/100 · ${state.dynasty.successionConcern.ifBlank { "weitere Klärung nötig" }}",
                            color = if (state.dynasty.successionTension >= 60) Danger else Gold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        item {
            StatGrid(
                listOf(
                    "Verwundet" to state.war.wounded.sumOf { it.soldiers }.toString(),
                    "Missionen" to state.activeMissions.count { it.status.isAway }.toString(),
                    "Bauprojekte" to state.city.constructionQueue.size.toString(),
                    "Forschung" to (state.research.active?.remainingDays?.let { "$it Tage" } ?: "frei"),
                )
            )
        }
        if (state.dailyReport.day == state.day && state.dailyReport.entries.isNotEmpty()) {
            item {
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "TAGESBERICHT · TAG ${state.dailyReport.day}",
                            color = PaleGold,
                            fontWeight = FontWeight.Bold,
                        )
                        state.dailyReport.entries
                            .sortedByDescending { it.important }
                            .take(8)
                            .forEach { entry ->
                                Surface(
                                    color = if (entry.important) Panel2 else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(
                                            "${entry.category.label} · ${entry.title}",
                                            color = if (entry.important) Gold else Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(entry.detail, color = Mist, fontSize = 12.sp)
                                    }
                                }
                            }
                    }
                }
            }
        }
        state.commanderEvents.pending?.let { event ->
            item {
                Surface(
                    color = Color(0xFF22202A),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("KRIEGSRAT · ${event.title}", color = PaleGold, fontWeight = FontWeight.Bold)
                        Text(event.text, color = Mist)
                        Text("Entscheidung bis Tag ${event.expiresDay}", color = Gold, fontSize = 11.sp)
                        CommanderEventEngine.choices(event).forEachIndexed { index, label ->
                            SmallAction(label) {
                                apply(CommanderEventEngine.resolve(state, index))
                            }
                        }
                    }
                }
            }
        }
        item {
            GoldButton(
                if (state.campaign.pendingDecision != null) "Tag fortsetzen · Entscheidung offen"
                else "Nächsten Tag beginnen",
                onAdvanceDay,
                Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernActionTile(
                    title = "Stadt",
                    subtitle = "${state.city.constructionQueue.size} Bauprojekte",
                    accent = Gold,
                    onClick = onCity,
                    modifier = Modifier.weight(1f),
                )
                ModernActionTile(
                    title = "Welt",
                    subtitle = "${state.activeMissions.count { it.status.isAway }} Missionen aktiv",
                    accent = ModernBlue,
                    onClick = onWorld,
                    modifier = Modifier.weight(1f),
                )
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
                            "Ankunft Tag ${invasion.arrivalDay} · Stärke ${state.invasionStrengthEstimate()} · Mauer ${state.realm.wallIntegrity}%",
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

/** Consistent, navigable recommendation used in the daily center and realm dashboard. */
@Composable
internal fun NextMoveCard(state: GameState, onNavigate: (GameDestination) -> Unit) {
    val next = androidx.compose.runtime.remember(state) { CampaignInsightsEngine.nextAction(state) }
    PremiumPanel(emphasized = next.priority >= 80) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("DEIN NÄCHSTER ZUG", color = Gold, fontSize = 10.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            ModernPill(
                if (next.priority >= 80) "Dringend" else if (next.priority >= 60) "Empfohlen" else "Optional",
                if (next.priority >= 80) Danger else Gold,
            )
        }
        Text(next.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Text(next.reason, color = Mist, fontSize = 13.sp, lineHeight = 19.sp)
        GoldButton(next.button, { onNavigate(next.destination) }, Modifier.fillMaxWidth())
    }
}
