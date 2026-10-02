package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.DoctrineEngine
import com.goldenunicorn.troopmanager.engine.MilitaryEconomyEngine
import com.goldenunicorn.troopmanager.engine.ResearchEngine
import com.goldenunicorn.troopmanager.engine.WarEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun MilitaryLogisticsPanel(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    fun apply(result: com.goldenunicorn.troopmanager.engine.GameEngine.ActionResult) {
        onState(result.state)
        onNotice(result.message)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle(
            "ARSENAL & LOGISTIK",
            "${state.doctrine.label} · ${state.foundingCultures.ifEmpty {
                Culture.entries.filter { culture ->
                    com.goldenunicorn.troopmanager.engine.ArmyEngine.population(state.population, culture) > 0
                }.toSet()
            }.joinToString { it.label }}",
        )

        SectionTitle("Militärgüter")
        StatGrid(
            MilitaryGood.entries.map { good ->
                good.label to
                    "${state.militaryStock.amount(good)} / ${MilitaryEconomyEngine.storageCap(state, good)}"
            }
        )
        EmptyCard(
            "Schwerter, Speere, Bögen, Pfeile, Rüstungen, Schilde und Pferde werden bei der Ausbildung real verbraucht. " +
                "Pfeile werden zusätzlich beim Beginn einer Schlacht als Gefechtsvorrat geladen. " +
                "Belagerungsteile versorgen schwere Geräte; Heilmittel verbessern die Versorgung Verwundeter."
        )

        SectionTitle("Produktion")
        val arsenal = WarEngine.effectiveLevel(state, BuildingType.ARSENAL)
        val ironworks = WarEngine.effectiveLevel(state, BuildingType.IRONWORKS)
        val sawmill = WarEngine.effectiveLevel(state, BuildingType.SAWMILL)
        val stables = WarEngine.effectiveLevel(state, BuildingType.STABLES)
        val hospital = WarEngine.effectiveLevel(state, BuildingType.HOSPITAL)
        StatGrid(
            listOf(
                "Arsenal" to "Stufe $arsenal",
                "Schmiede" to "Stufe $ironworks",
                "Sägewerk" to "Stufe $sawmill",
                "Stallungen" to "Stufe $stables",
                "Lazarett" to "Stufe $hospital",
                "Lagerhaus" to "Stufe ${state.realm.level(BuildingType.WAREHOUSE)}",
            )
        )
        Text(
            "Die tägliche Militärproduktion verbraucht echte Rohstoffe. Forschung kann Schmieden, Bögen und Belagerungstechnik effizienter machen.",
            color = Mist,
            fontSize = 12.sp,
        )

        SectionTitle("Armeedoktrin")
        Text(
            "Eine Doktrin verändert die langfristige Einsatzweise des Heeres. Wechsel kosten 150 Gold und benötigen eine Offiziersschule.",
            color = Mist,
            fontSize = 12.sp,
        )
        MilitaryDoctrine.entries.forEach { doctrine ->
            Surface(
                color = if (state.doctrine == doctrine) Panel2 else Panel,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        doctrine.label,
                        color = if (state.doctrine == doctrine) PaleGold else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(doctrine.description, color = Mist, fontSize = 12.sp)
                    if (state.doctrine != doctrine)
                        OutlinedButton(
                            onClick = { apply(DoctrineEngine.set(state, doctrine)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Doktrin übernehmen · 150 Gold")
                        }
                    else Text("AKTIV", color = Success, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun ResearchPanel(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
) {
    fun apply(result: com.goldenunicorn.troopmanager.engine.GameEngine.ActionResult) {
        onState(result.state)
        onNotice(result.message)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle(
            "FORSCHUNG & DOKTRIN",
            "Offiziersschule Stufe ${WarEngine.effectiveLevel(state, BuildingType.ACADEMY)}",
        )
        state.research.active?.let { project ->
            SectionTitle("Aktives Projekt")
            Surface(
                color = Panel2,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(project.tech.label, color = PaleGold, fontWeight = FontWeight.Bold)
                    Text(project.tech.description, color = Mist, fontSize = 12.sp)
                    Text(
                        "Noch ${project.remainingDays} / ${project.totalDays} Tage",
                        color = Gold,
                        fontSize = 12.sp,
                    )
                    LinearProgressIndicator(
                        progress = {
                            (project.totalDays - project.remainingDays).toFloat() /
                                project.totalDays.coerceAtLeast(1)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = Gold,
                        trackColor = Panel,
                    )
                }
            }
        }

        SectionTitle("Forschungszweige")
        ResearchBranch.entries.forEach { branch ->
            Text(branch.name.replace('_', ' '), color = Gold, fontWeight = FontWeight.Bold)
            ResearchTech.entries.filter { it.branch == branch }.forEach { tech ->
                val complete = tech in state.research.completed
                val requirements = ResearchEngine.requirementsMet(state, tech)
                Surface(
                    color = Panel,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(tech.label, color = if (complete) Success else PaleGold, fontWeight = FontWeight.Bold)
                        Text(tech.description, color = Mist, fontSize = 12.sp)
                        Text(
                            "${tech.goldCost} Gold · ${tech.ironCost} Eisen · Basis ${tech.days} Tage",
                            color = Gold,
                            fontSize = 11.sp,
                        )
                        when {
                            complete -> Text("ERFORSCHT", color = Success, fontSize = 11.sp)
                            !requirements ->
                                Text("Gebäudevoraussetzung noch nicht erfüllt.", color = Danger, fontSize = 11.sp)
                            state.research.active != null ->
                                Text("Ein anderes Projekt läuft.", color = Mist, fontSize = 11.sp)
                            else ->
                                OutlinedButton(
                                    onClick = { apply(ResearchEngine.start(state, tech)) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Forschung beginnen")
                                }
                        }
                    }
                }
            }
        }
    }
}
