package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.engine.CampaignInsightsEngine
import com.goldenunicorn.troopmanager.model.*

@Composable
internal fun LogisticsForecastPanel(state: GameState) {
    val week = remember(state) { CampaignInsightsEngine.forecast(state, 7) }
    val fortnight = remember(state) { CampaignInsightsEngine.forecast(state, 14) }
    PremiumPanel {
        Text("VERSORGUNG · 7 / 14 TAGE", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Prognose bei unveränderten Aufträgen. Saisonwechsel enthalten; Wetter, neue Bauten, Verträge und Ereignisse können die Spanne verändern.", color = Mist, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bestand", Modifier.weight(1f), color = Mist, fontSize = 12.sp)
            Text("7 Tage", Modifier.weight(1f), color = Mist, fontSize = 12.sp)
            Text("14 Tage", Modifier.weight(1f), color = Mist, fontSize = 12.sp)
        }
        ResourceKind.entries.forEach { kind ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kind.label, Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
                Text("${kind.value(week.lower)}–${kind.value(week.upper)}", Modifier.weight(1f), color = Mist, fontSize = 12.sp)
                Text("${kind.value(fortnight.lower)}–${kind.value(fortnight.upper)}", Modifier.weight(1f),
                    color = if (kind == ResourceKind.FOOD && fortnight.lower.food == 0) Danger else Mist, fontSize = 12.sp)
            }
        }
        val overflow = ResourceKind.entries.filter { it.value(week.overflow) > 0 }
        if (overflow.isNotEmpty()) Text("Erwarteter Überlauf in 7 Tagen: " + overflow.joinToString { "${it.value(week.overflow)} ${it.label}" }, color = PaleGold, fontSize = 12.sp)
        Text("Feldheere ${state.awayArmySize} · Außenpostenvorräte ${state.frontier.outposts.sumOf { it.stores }} · Belagerungsdepot ${state.war.siegeFoodStored}\nPfeile ${state.militaryStock.arrows} · Medizin ${state.militaryStock.medicine} · Ersatzteile ${state.militaryStock.siegeParts}", color = Mist, fontSize = 12.sp)
    }
}
