package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** v1.1 crown area: Kronlande, Heerbann, trade, heroines, combat arts and edicts. */
@Composable
internal fun CrownScreen(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    fun apply(result: GameEngine.ActionResult) { onState(result.state); onNotice(result.message) }
    val crown = state.crown
    if (!crown.initialized) {
        LaunchedEffect(Unit) { onState(CrownEngine.initialize(state)) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        return
    }
    crown.duel?.let { duel ->
        DuelPanel(state, duel, ::apply)
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Übersicht", "Provinzen", "Heerbann", "Handel", "Heldinnen", "Kampfkunst", "Edikte")
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("KRONE · ${state.title.uppercase()}", "${crown.ownedProvinces.size} Kronprovinzen · ${fmt(state.armySize)} Soldaten")
        }
        crown.saga.pendingScene?.let { scene -> item(key = "scene") { SagaSceneCard(state, scene, ::apply) } }
        item { ModernTabStrip(tabs, tab, { tab = it }) }
        when (tab) {
            0 -> overviewTab(state, ::apply) { tab = it }
            1 -> provincesTab(state, ::apply)
            2 -> levyTab(state, ::apply)
            3 -> tradeTab(state, ::apply)
            4 -> heroinesTab(state, ::apply, onState)
            5 -> artsTab(state, ::apply)
            else -> edictsTab(state, ::apply)
        }
    }
}

internal fun fmt(value: Int): String = String.format(java.util.Locale.GERMANY, "%,d", value)
internal fun fmt(value: Long): String = String.format(java.util.Locale.GERMANY, "%,d", value)

@Composable
private fun CrownCard(emphasized: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    PremiumPanel(emphasized = emphasized, content = content)

@Composable
private fun CardTitle(title: String, subtitle: String? = null, pill: String? = null, pillColor: Color = Gold) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        if (pill != null) ModernPill(pill, pillColor, filled = true)
    }
}

@Composable
private fun Meter(label: String, value: Int, max: Int = 100, color: Color = Gold) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Mist, fontSize = 12.sp)
            Text("$value / $max", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = .08f),
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, color: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) =
    GoldButton(text, onClick, Modifier.fillMaxWidth())

private fun costLabel(cost: Resources): String = ResourceKind.entries
    .filter { it.value(cost) > 0 }
    .joinToString(" · ") { "${fmt(it.value(cost))} ${it.label}" }

// ===================================================================== overview

private fun androidx.compose.foundation.lazy.LazyListScope.overviewTab(
    state: GameState,
    apply: (GameEngine.ActionResult) -> Unit,
    openTab: (Int) -> Unit,
) {
    val crown = state.crown
    item {
        val ladder = CrownCatalog.titleLadder
        val index = ladder.indexOfFirst { it.title == state.title }.coerceAtLeast(0)
        val next = ladder.getOrNull(index + 1)
        CrownCard(emphasized = true) {
            Text("DEIN WEG ZUR KRONE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text(state.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            if (next != null) Text("Nächster Titel: ${next.title} · ${next.requirement}", color = PaleGold, fontSize = 13.sp)
            else Text("Du hast den höchsten Titel der Kronlande erreicht.", color = PaleGold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ladder.forEachIndexed { i, step ->
                    ModernPill(step.title, if (i <= index) Gold else Muted, filled = i <= index)
                }
            }
        }
    }
    item {
        StatGrid(
            listOf(
                "Kronprovinzen" to "${crown.ownedProvinces.size} / ${crown.provinces.size}",
                "Untertanen" to fmt(crown.subjects),
                "Gold aus Provinzen/Tag" to "+${fmt(CrownEngine.provinceYield(state, ResourceKind.GOLD))}",
                "Nahrung aus Provinzen/Tag" to "+${fmt(CrownEngine.provinceYield(state, ResourceKind.FOOD))}",
                "Neue Rekruten/Tag" to "+${fmt(CrownEngine.dailySettlers(state))}",
                "Handel gestern" to "+${fmt(crown.lastTradeIncome)} Gold",
                "Söldnersold/Tag" to "−${fmt(CrownEngine.mercenaryUpkeep(state))} Gold",
                "Feldzüge heute" to "${CrownEngine.campaignsLeft(state)} frei",
            )
        )
    }
    item {
        val companion = HeroineSagaEngine.companionDef(state)
        CrownCard {
            if (state.companion.met) {
                CardTitle(state.companion.name, companion?.epithet ?: state.companion.role, state.companion.role, ModernBlue)
                val (ready, need) = HeroineSagaEngine.chapterReady(state)
                Text("Kapitel: ${HeroineSagaEngine.chapterTitle(state.crown.saga.chapter)}", color = PaleGold, fontSize = 13.sp)
                Text(if (ready) "Eine neue Szene wartet auf euch." else need, color = Mist, fontSize = 13.sp)
            } else {
                CardTitle("Noch keine Feldherrin", "Begib dich zu einer Heldin, besteht ihre Probe und gewinne sie für dein Heer.")
            }
            SmallAction("Heldinnen öffnen") { openTab(4) }
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModernActionTile("Provinzen", "Erobern, kaufen, ausbauen", Gold, { openTab(1) }, Modifier.weight(1f))
            ModernActionTile("Heerbann", "Rekruten & Söldner", Crimson, { openTab(2) }, Modifier.weight(1f))
        }
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModernActionTile("Handel", "Routen & Waffen", Emerald, { openTab(3) }, Modifier.weight(1f))
            ModernActionTile("Kampfkunst", "Training & Turniere", ModernBlue, { openTab(5) }, Modifier.weight(1f))
        }
    }
    if (crown.reports.isNotEmpty()) {
        item { SectionTitle("Letzte Feldzüge") }
        items(crown.reports.take(4), key = { "${it.day}_${it.provinceId}_${it.ownLosses}" }) { report ->
            CrownCard {
                CardTitle(report.provinceName, "Tag ${report.day}", if (report.victory) "Sieg" else "Rückzug", if (report.victory) Emerald else Crimson)
                Text(report.text, color = Mist, fontSize = 13.sp)
            }
        }
    }
}

// ===================================================================== provinces

private fun androidx.compose.foundation.lazy.LazyListScope.provincesTab(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    item(key = "provinces") { ProvincesPanel(state, apply) }
}

@Composable
private fun ProvincesPanel(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    var land by rememberSaveable { mutableStateOf(CrownLand.GRENZMARK) }
    var campaignTarget by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CrownLand.entries.forEach { l ->
                val owned = state.crown.provinces.count { it.land == l && it.owner == ProvinceOwner.PLAYER }
                val open = CrownEngine.reachable(state, l)
                FilterChip(
                    selected = land == l,
                    onClick = { land = l },
                    label = { Text((if (open) "" else "🔒 ") + l.label + if (owned > 0) " · $owned" else "") },
                )
            }
        }
        val reachable = CrownEngine.reachable(state, land)
        CrownCard {
            CardTitle(land.label, land.description, if (reachable) "Erreichbar" else "Gesperrt", if (reachable) Emerald else Muted)
            if (!reachable) {
                val via = (CrownCatalog.neighbors[land] ?: emptySet()).joinToString { it.label }
                Text("Sichere zuerst eine Provinz in einem Nachbarland: $via.", color = Mist, fontSize = 13.sp)
            }
        }
        state.crown.provinces.filter { it.land == land }.forEach { p ->
            ProvinceCard(state, p, reachable, apply) { campaignTarget = p.id }
        }
    }
    campaignTarget?.let { id ->
        CampaignDialog(state, id, apply) { campaignTarget = null }
    }
}

private fun ownerColor(owner: ProvinceOwner): Color = when (owner) {
    ProvinceOwner.PLAYER -> Emerald
    ProvinceOwner.FREE -> ModernBlue
    ProvinceOwner.RIVAL -> Gold
    else -> Crimson
}

@Composable
private fun ProvinceCard(
    state: GameState,
    p: CrownProvince,
    reachable: Boolean,
    apply: (GameEngine.ActionResult) -> Unit,
    onCampaign: () -> Unit,
) {
    CrownCard(emphasized = p.owner == ProvinceOwner.PLAYER) {
        CardTitle(p.name, "${p.terrain} · ${p.culture.label} · Wohlstand ${"★".repeat((p.wealth + 1) / 2)}", p.owner.label, ownerColor(p.owner))
        InfoLine("Bevölkerung", fmt(p.population))
        if (p.owner == ProvinceOwner.PLAYER) {
            Meter("Loyalität", p.loyalty, color = if (p.loyalty >= 40) Emerald else Crimson)
            if (p.unrest > 0) Meter("Unruhe", p.unrest, color = Crimson)
            InfoLine("Ausbau · Befestigung", "${p.development}/5 · ${p.fortification}/3")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SmallAction(if (p.development < 5) "Ausbauen" else "Voll ausgebaut") { apply(CrownEngine.develop(state, p.id)) } }
                Box(Modifier.weight(1f)) { SmallAction(if (p.fortification < 3) "Befestigen" else "Festung") { apply(CrownEngine.fortify(state, p.id)) } }
            }
            if (p.development < 5) Text("Ausbau: ${costLabel(CrownEngine.developCost(state, p))}", color = Muted, fontSize = 12.sp)
            if (p.fortification < 3) Text("Befestigung: ${costLabel(CrownEngine.fortifyCost(p))}", color = Muted, fontSize = 12.sp)
            SmallAction("Volksfest · ${fmt(CrownEngine.feastCost(state, p))} Gold") { apply(CrownEngine.feast(state, p.id)) }
        } else {
            InfoLine("Verteidiger", "${fmt(p.garrison)} · Kampfwert ${p.garrisonQuality}" + if (p.fortification > 0) " · Mauern ${p.fortification}" else "", Crimson)
            if (p.owner == ProvinceOwner.RIVAL || p.owner == ProvinceOwner.FREE) Meter("Gunst für Anschluss", p.favor, color = ModernBlue)
            if (!reachable) Text("Noch außer Reichweite.", color = Muted, fontSize = 12.sp)
            else {
                if (p.owner == ProvinceOwner.FREE) PrimaryAction("Landrecht kaufen · ${fmt(CrownEngine.landPrice(state, p))} Gold") { apply(CrownEngine.buy(state, p.id)) }
                if (p.owner == ProvinceOwner.RIVAL || p.owner == ProvinceOwner.FREE) {
                    if (p.favor >= 100) PrimaryAction("Friedlich anschließen · ${fmt(CrownEngine.annexPrice(p))} Gold") { apply(CrownEngine.annex(state, p.id)) }
                    else SmallAction("Gesandte schicken · ${250 + p.wealth * 50} Gold") { apply(CrownEngine.envoy(state, p.id)) }
                }
                if (p.garrison > 0 || p.owner != ProvinceOwner.FREE) SmallAction("Feldzug planen") { onCampaign() }
            }
        }
    }
}

@Composable
private fun CampaignDialog(state: GameState, provinceId: String, apply: (GameEngine.ActionResult) -> Unit, onClose: () -> Unit) {
    val p = CrownEngine.province(state, provinceId) ?: return
    var share by remember { mutableIntStateOf(100) }
    val preview = CrownEngine.preview(state, provinceId, share)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Feldzug gegen ${p.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wie viel deines Heeres daheim rückt aus?", color = Mist, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 50, 75, 100).forEach { s -> FilterChip(selected = share == s, onClick = { share = s }, label = { Text("$s %") }) }
                }
                if (preview != null) {
                    InfoLine("Dein Heer", "${fmt(preview.ownTroops)} Soldaten")
                    InfoLine("Verteidiger", "${fmt(preview.enemyTroops)} Mann")
                    InfoLine("Stärkeverhältnis", String.format(java.util.Locale.GERMANY, "%.2f : 1", preview.ratio), if (preview.ratio >= 1.05) Emerald else Crimson)
                    InfoLine("Einschätzung", preview.label, if (preview.ratio >= 1.05) Emerald else Crimson)
                    InfoLine("Erwartete Verluste", "≈ ${fmt(preview.expectedLosses)}")
                    InfoLine("Nachschub", "${fmt(preview.supplyGold)} Gold · ${fmt(preview.supplyFood)} Nahrung")
                }
                when {
                    state.crown.championBonusProvinceId == provinceId -> Text("Der feindliche Champion ist gefallen: −20 % Verteidigung.", color = Emerald, fontSize = 12.sp)
                    state.crown.championPenaltyProvinceId == provinceId -> Text("Dein Champion-Duell ging verloren: +10 % Verteidigung.", color = Crimson, fontSize = 12.sp)
                    else -> Text("Fordere vorher den feindlichen Champion zum Duell. Ein Sieg bricht den Mut der Verteidiger.", color = Muted, fontSize = 12.sp)
                }
                Text("Feldzüge heute noch: ${CrownEngine.campaignsLeft(state)}", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { apply(CrownEngine.conquer(state, provinceId, share)); onClose() }) { Text("Angreifen", color = Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (state.crown.championBonusProvinceId != provinceId)
                    TextButton(onClick = { apply(CombatArtsEngine.championDuel(state, provinceId)); onClose() }) { Text("Champion fordern") }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}

// ===================================================================== levy & mercenaries

private fun androidx.compose.foundation.lazy.LazyListScope.levyTab(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    val crown = state.crown
    item {
        CrownCard(emphasized = true) {
            CardTitle("Heerbann der Kronlande", "Ruf die Untertanen aller Kronprovinzen zu den Waffen.")
            InfoLine("Untertanen", fmt(crown.subjects))
            InfoLine("Rekruten durch Heerbann", "+${fmt(CrownEngine.levyAmount(state))}")
            InfoLine("Täglicher Zuzug", "+${fmt(CrownEngine.dailySettlers(state))} Rekruten")
            InfoLine("Freie Rekruten jetzt", fmt(state.population.totalRecruits), PaleGold)
            val cooldown = CrownEngine.levyCooldown(state)
            if (crown.ownedProvinces.isEmpty()) Text("Erobere oder kaufe zuerst eine Kronprovinz.", color = Muted, fontSize = 13.sp)
            else if (cooldown > 0) Text("Nächster Heerbann in $cooldown Tagen.", color = Muted, fontSize = 13.sp)
            else PrimaryAction("Heerbann ausrufen") { apply(CrownEngine.levy(state)) }
            Text("Jeder Heerbann senkt die Loyalität der Provinzen um 6.", color = Muted, fontSize = 12.sp)
        }
    }
    item(key = "mass_training") { MassTrainingCard(state, apply) }
    item { SectionTitle("Söldnermarkt · sofort einsatzbereit") }
    items(CrownCatalog.companies, key = { it.id }) { def ->
        val blocker = CrownEngine.companyBlocker(state, def)
        CrownCard {
            CardTitle(def.name, "${fmt(def.soldiers)} ${def.type.label} · ${def.type.culture.label}", blocker?.let { "Gesperrt" }, Muted)
            Text(def.description, color = Mist, fontSize = 13.sp)
            InfoLine("Handgeld · Sold", "${fmt(def.cost)} Gold · ${def.upkeep}/Tag")
            if (blocker != null) Text(blocker, color = Crimson, fontSize = 12.sp)
            else SmallAction("Anwerben · ${fmt(def.cost)} Gold") { apply(CrownEngine.hire(state, def.id)) }
        }
    }
    if (crown.mercenaries.isNotEmpty()) {
        item { SectionTitle("Laufende Söldnerverträge") }
        items(crown.mercenaries, key = { it.id }) { c ->
            val def = CrownCatalog.company(c.companyId)
            CrownCard {
                CardTitle(def?.name ?: c.companyId, "${fmt(c.soldiers)} ${c.type.label} · seit Tag ${c.hiredDay}", "${c.upkeep}/Tag", Gold)
                SmallAction("Vertrag lösen") { apply(CrownEngine.dismiss(state, c.id)) }
            }
        }
    }
    item {
        CrownCard {
            CardTitle("Arsenal", "Militärgüter für die Ausbildung")
            MilitaryGood.entries.forEach { good -> InfoLine(good.label, fmt(state.militaryStock.amount(good))) }
            Text("Waffen kommen aus Arsenal, Kronprovinzen und Lieferungen deiner Handelspartner.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MassTrainingCard(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    val choices = remember { mutableStateMapOf<Culture, UnitType>() }
    CrownCard {
        CardTitle("Massenausbildung", "Bilde alle freien Rekruten jedes Volkes auf einmal aus.")
        Text("Ausbildungsdauer ×${String.format(java.util.Locale.GERMANY, "%.1f", CrownEngine.trainingFactor(state))} · Kosten ×${String.format(java.util.Locale.GERMANY, "%.2f", CrownEngine.recruitCostFactor(state))}", color = Muted, fontSize = 12.sp)
        Culture.entries.forEach { culture ->
            val types = UnitType.entries.filter { it.culture == culture && GameEngine.isUnitUnlocked(state, it) }
            val available = ArmyEngine.recruitable(state, culture)
            if (types.isEmpty()) return@forEach
            val selected = choices[culture] ?: types.first()
            Text("${culture.label} · ${fmt(available)} Rekruten", color = PaleGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { type -> FilterChip(selected = selected == type, onClick = { choices[culture] = type }, label = { Text(type.label) }) }
            }
        }
        PrimaryAction("Alle Rekruten ausbilden") {
            val picks = Culture.entries.mapNotNull { culture ->
                val types = UnitType.entries.filter { it.culture == culture && GameEngine.isUnitUnlocked(state, it) }
                (choices[culture] ?: types.firstOrNull())?.let { culture to it }
            }.toMap()
            apply(CrownEngine.trainAll(state, picks))
        }
    }
}

// ===================================================================== trade

private fun androidx.compose.foundation.lazy.LazyListScope.tradeTab(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    item {
        Text("Handelsrouten liefern jeden Tag Gold und Waren. Abkommen und Bündnisse öffnen mehr Routen, bessere Preise und exklusive Söldner.", color = Mist, fontSize = 13.sp)
    }
    items(CrownCatalog.partners, key = { it.id }) { def ->
        val p = state.crown.partner(def.id) ?: TradePartnerState(def.id)
        val agreement = when (p.agreement) { 2 -> "Bündnis"; 1 -> "Abkommen"; else -> if (p.unlocked) "Kontakt" else "Unbekannt" }
        CrownCard(emphasized = p.agreement > 0) {
            CardTitle(def.name, "${def.land.label} · kauft ${def.buys.label}, liefert ${def.sells.label}", agreement, if (p.unlocked) Emerald else Muted)
            Text(def.motto, color = Mist, fontSize = 13.sp)
            if (!p.unlocked) {
                Text("Kontakt ab ${def.renownRequired} Ruhm" + if (def.needsLand) " und Zugang nach ${def.land.label}." else ".", color = Muted, fontSize = 12.sp)
                return@CrownCard
            }
            Meter("Beziehung", p.relation, color = ModernBlue)
            InfoLine("Routen", "${p.routes} / ${CrownEngine.maxRoutes(state, p)}")
            InfoLine("Gewinn gestern · gesamt", "${fmt(p.lastIncome)} · ${fmt(p.totalProfit)} Gold", Emerald)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SmallAction("Route · ${fmt(CrownEngine.routeCost(p))}") { apply(CrownEngine.openRoute(state, def.id)) } }
                Box(Modifier.weight(1f)) { SmallAction("Geschenk · 600") { apply(CrownEngine.gift(state, def.id)) } }
            }
            if (p.agreement < 2) {
                val label = if (p.agreement == 0) "Handelsabkommen (Beziehung 45)" else "Bündnis (Beziehung 80)"
                SmallAction("$label · ${fmt(CrownEngine.agreementCost(p))} Gold") { apply(CrownEngine.signAgreement(state, def.id)) }
            }
            SmallAction("Waffenlieferung · ${fmt(CrownEngine.goodsPrice(def, p))} Gold") { apply(CrownEngine.buyGoods(state, def.id)) }
            Text(def.goods.joinToString(" · ") { "${fmt(it.second)} ${it.first.label}" }, color = Muted, fontSize = 12.sp)
        }
    }
}

// ===================================================================== heroines

private fun androidx.compose.foundation.lazy.LazyListScope.heroinesTab(
    state: GameState,
    apply: (GameEngine.ActionResult) -> Unit,
    onState: (GameState) -> Unit,
) {
    if (state.companion.met) item(key = "companion_story") { CompanionStoryCard(state, onState) }
    item {
        Text(
            if (state.crown.saga.partner != null) "Deine Partnerin triffst du nach etwa einer Woche. Gewinne sie als persönliche Feldherrin – die anderen Heldinnen kannst du als Feldherrinnen eigener Heere anwerben."
            else "Vier Heldinnen leben in den Kronlanden. Reise zu ihnen, gewinne ihren Respekt und besteht ihre Bewährungsprobe. Die erste wird deine persönliche Feldherrin, die anderen führen eigene Heere.",
            color = Mist, fontSize = 13.sp,
        )
    }
    items(CrownCatalog.heroines.mapNotNull { HeroineSagaEngine.def(state, it.id) }, key = { it.id }) { def -> HeroineCard(state, def, apply) }
}

@Composable
private fun Portrait(key: String, uri: String?, diameter: Int = 64) {
    val modifier = Modifier.size(diameter.dp).clip(CircleShape).background(Steel)
    if (uri != null) AsyncImage(uri, null, modifier, contentScale = ContentScale.Crop)
    else Image(painterResource(portraitResource(key)), null, modifier, contentScale = ContentScale.Crop)
}

@Composable
private fun CompanionStoryCard(state: GameState, onState: (GameState) -> Unit) {
    val c = state.companion
    val saga = state.crown.saga
    val def = HeroineSagaEngine.companionDef(state)
    CrownCard(emphasized = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Portrait(def?.portraitKey ?: "companion", c.portraitUri, 72)
            Column(Modifier.weight(1f)) {
                Text(c.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                val role = if (state.relationship.romanceStage == RomanceStage.CO_RULERS) "Königin & Feldherrin" else c.role
                Text("$role · ${state.relationship.romanceStage.label}", color = PaleGold, fontSize = 13.sp)
            }
        }
        Meter("Vertrauen", c.trust, color = ModernBlue)
        Meter("Respekt", c.respect, color = Gold)
        Meter("Zuneigung", c.affection, color = Crimson)
        val (ready, need) = HeroineSagaEngine.chapterReady(state)
        Text("Kapitel ${if (saga.chapter <= HeroineSagaEngine.FINAL_CHAPTER) saga.chapter + 1 else "✓"}: ${HeroineSagaEngine.chapterTitle(saga.chapter)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(if (ready) "Die nächste Szene beginnt mit dem nächsten Tag." else need, color = Mist, fontSize = 13.sp)
        saga.lastOutcome?.let { Text("„$it“", color = Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic) }
        Text("Stärke eure Bindung durch gemeinsame Schlachten, Feldzüge und Zeit im Herrscherpaar-Bereich.", color = Muted, fontSize = 12.sp)
        if (state.settings.romance == RomanceMode.OFF && saga.chapter in 3..8) {
            Text("Romantik ist in den Optionen ausgeschaltet. Ihr könnt Schwurgeschwister werden – oder Romantik aktivieren.", color = PaleGold, fontSize = 12.sp)
            SmallAction("Romantik aktivieren") { onState(state.copy(settings = state.settings.copy(romance = RomanceMode.ROMANCE))) }
        }
    }
}

@Composable
private fun HeroineCard(state: GameState, def: CrownCatalog.HeroineDef, apply: (GameEngine.ActionResult) -> Unit) {
    val h = state.crown.heroine(def.id) ?: return
    val partner = HeroineSagaEngine.isPartner(state, def.id)
    val available = partner || HeroineSagaEngine.available(state, def)
    CrownCard(emphasized = h.stage == HeroineStage.COMPANION) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Portrait(def.portraitKey, partnerPortrait(state, def.id) ?: if (h.stage == HeroineStage.COMPANION) state.companion.portraitUri else null)
            Column(Modifier.weight(1f)) {
                Text(if (h.stage == HeroineStage.RUMOR && !available) "Unbekannte Heldin" else def.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("${def.epithet} · ${def.location}", color = Muted, fontSize = 12.sp)
            }
            ModernPill(h.stage.label, when (h.stage) {
                HeroineStage.COMPANION, HeroineStage.GENERAL -> Emerald
                HeroineStage.TRIAL -> Gold
                else -> ModernBlue
            }, filled = true)
        }
        when (h.stage) {
            HeroineStage.RUMOR -> {
                if (!available) Text("Gerüchte reichen noch nicht bis zu dir. ${def.unlockHint}", color = Muted, fontSize = 13.sp)
                else {
                    Text(def.rumor, color = Mist, fontSize = 13.sp)
                    val cost = HeroineSagaEngine.travelCost(def)
                    PrimaryAction("Aufbrechen · ${def.travelDays} Tage · ${cost.gold} Gold, ${cost.food} Nahrung") { apply(HeroineSagaEngine.travel(state, def.id)) }
                }
            }
            HeroineStage.TRAVELING -> Text(
                if (partner) "Euer Schicksal kreuzt sich bald: An Tag ${h.arrivalDay} begegnest du ihr bei ${def.location}. Noch kennt sie dich nicht."
                else "Du bist unterwegs nach ${def.location}. Ankunft an Tag ${h.arrivalDay}.",
                color = Mist, fontSize = 13.sp,
            )
            HeroineStage.MET -> {
                Text(h.firstImpression, color = Mist, fontSize = 13.sp, fontStyle = FontStyle.Italic)
                Meter("Ihr Eindruck von dir", h.favor, color = Crimson)
                Text("Kampfwerte: Schwert ${def.sword} · Bogen ${def.bow} · Führung ${def.leadership} · Taktik ${def.tactics}", color = Muted, fontSize = 12.sp)
                PrimaryAction("Als Feldherrin anwerben") { apply(HeroineSagaEngine.startTrial(state, def.id)) }
            }
            HeroineStage.TRIAL -> {
                val progress = HeroineSagaEngine.trialProgress(state, def.id)
                Text(progress.text, color = Mist, fontSize = 13.sp)
                Meter("Bewährungsprobe", progress.current, progress.target, color = Gold)
                if (def.trial == CrownCatalog.TrialKind.DUEL && !progress.done)
                    PrimaryAction("${def.name.substringBefore(' ')} zum Duell fordern") { apply(HeroineSagaEngine.challenge(state, def.id)) }
                if (progress.done) {
                    if (HeroineSagaEngine.canBecomeCompanion(state, def.id)) PrimaryAction("An deine Seite: persönliche Feldherrin") { apply(HeroineSagaEngine.recruit(state, def.id, true)) }
                    SmallAction("Feldherrin eines eigenen Heeres") { apply(HeroineSagaEngine.recruit(state, def.id, false)) }
                }
            }
            HeroineStage.COMPANION -> Text("Reitet an deiner Seite. Ihre Geschichte findest du oben.", color = Emerald, fontSize = 13.sp)
            HeroineStage.GENERAL -> Text("Führt ein eigenes Heer. Weise ihr im Heer-Bereich Truppen zu; jede Heldin stärkt deine Feldzüge.", color = Emerald, fontSize = 13.sp)
            HeroineStage.DEPARTED -> Text("Sie hat die Kronlande verlassen.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SagaSceneCard(state: GameState, scene: SagaScene, apply: (GameEngine.ActionResult) -> Unit) {
    val def = HeroineSagaEngine.def(state, scene.heroineId)
    PremiumPanel(emphasized = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Portrait(def?.portraitKey ?: "companion", partnerPortrait(state, scene.heroineId) ?: if (state.crown.saga.companionId == scene.heroineId) state.companion.portraitUri else null, 56)
            Column(Modifier.weight(1f)) {
                Text(scene.location.uppercase(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(scene.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(scene.text, color = Ivory, fontSize = 15.sp, lineHeight = 22.sp)
        scene.options.forEach { option ->
            OutlinedButton(
                onClick = { apply(HeroineSagaEngine.choose(state, option.id)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = .35f)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(option.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (option.hint.isNotBlank()) Text(option.hint, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

// ===================================================================== arts & duels

private fun androidx.compose.foundation.lazy.LazyListScope.artsTab(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    val arts = state.crown.arts
    item {
        StatGrid(listOf(
            "Angriff" to "${CombatArtsEngine.attackValue(state)}",
            "Verteidigung" to "${CombatArtsEngine.defenseValue(state)}",
            "Absichten lesen" to "${CombatArtsEngine.readChance(state)} %",
            "Duelle" to "${arts.duelsWon} Siege · ${arts.duelsLost} Niederl.",
        ))
    }
    item {
        val (name, skill) = CombatArtsEngine.tournamentOpponent(state)
        val wait = CombatArtsEngine.tournamentCooldown(state)
        CrownCard(emphasized = true) {
            CardTitle("Turnier", "${arts.tournamentWins} Turniersiege", "Gegner: $skill", Crimson)
            Text("Nächster Gegner: $name. Siege bringen Gold, Ruhm und Kampferfahrung.", color = Mist, fontSize = 13.sp)
            if (wait > 0) Text("Nächstes Turnier in $wait Tagen.", color = Muted, fontSize = 13.sp)
            else PrimaryAction("Antreten · 200 Gold Startgeld") { apply(CombatArtsEngine.tournament(state)) }
        }
    }
    item { SectionTitle("Kampfkünste · ein Training pro Tag") }
    items(CombatArt.entries, key = { it.name }) { art ->
        val level = arts.level(art)
        CrownCard {
            CardTitle(art.label, art.description, "Stufe $level", if (level >= 3) Gold else ModernBlue)
            if (level < CombatArtsEngine.MAX_LEVEL) Meter("Erfahrung", arts.xp(art), CombatArtsEngine.xpNeeded(level), ModernBlue)
            Text(if (level >= 3) "Technik: ${art.technique} (im Duell mit 3 Fokus)" else "Ab Stufe 3: ${art.technique}", color = if (level >= 3) PaleGold else Muted, fontSize = 12.sp)
            if (level < CombatArtsEngine.MAX_LEVEL)
                SmallAction(if (arts.lastTrainingDay == state.day) "Heute bereits trainiert" else "Trainieren · ${CombatArtsEngine.trainingCost(state, art)} Gold") { apply(CombatArtsEngine.train(state, art)) }
        }
    }
}

@Composable
private fun DuelPanel(state: GameState, duel: DuelSession, apply: (GameEngine.ActionResult) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("DUELL · RUNDE ${duel.round.coerceAtMost(12)}", "${duel.opponentName} · ${duel.opponentTitle}") }
        item {
            PremiumPanel(emphasized = true) {
                Meter(state.player.name, duel.playerHp, color = Emerald)
                Meter(duel.opponentName, duel.opponentHp, color = Crimson)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Fokus", color = Muted, fontSize = 12.sp)
                    repeat(5) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < duel.focus) Gold else Steel)) }
                }
                if (!duel.finished && duel.intentHint.isNotBlank())
                    Text(duel.intentHint, color = PaleGold, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                Text("Du liest Absichten mit ${CombatArtsEngine.readChance(state)} % Sicherheit.", color = Muted, fontSize = 12.sp)
            }
        }
        if (!duel.finished) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DuelStance.ATTACK to DuelStance.DEFEND, DuelStance.FEINT to DuelStance.TECHNIQUE).forEach { (left, right) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(left, right).forEach { stance ->
                                val enabled = stance != DuelStance.TECHNIQUE || (duel.focus >= 3 && CombatArtsEngine.techniqueUnlocked(state))
                                Button(
                                    onClick = { apply(CombatArtsEngine.move(state, stance)) },
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (stance == DuelStance.TECHNIQUE) Gold else Steel, contentColor = if (stance == DuelStance.TECHNIQUE) Ink else Color.White),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stance.label, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                        Text(stance.hint, fontSize = 10.sp, lineHeight = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                PremiumPanel(emphasized = true) {
                    Text(if (duel.won) "SIEG" else "NIEDERLAGE", color = if (duel.won) Emerald else Crimson, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    PrimaryAction("Duell abschließen") { apply(CombatArtsEngine.finish(state)) }
                }
            }
        }
        item { SectionTitle("Verlauf") }
        items(duel.log.reversed()) { line -> Text(line, color = Mist, fontSize = 13.sp) }
    }
}

// ===================================================================== edicts

private fun androidx.compose.foundation.lazy.LazyListScope.edictsTab(state: GameState, apply: (GameEngine.ActionResult) -> Unit) {
    item {
        CrownCard(emphasized = true) {
            CardTitle("Reichsedikte", "Dein Titel erlaubt ${CrownEngine.maxEdicts(state)} gleichzeitige Edikte.", "${state.crown.edicts.size}/${CrownEngine.maxEdicts(state)}", Gold)
            Text("Ein Edikt zu verkünden kostet 2.500 Gold. Aufheben ist kostenlos.", color = Mist, fontSize = 13.sp)
        }
    }
    items(CrownEdict.entries, key = { it.name }) { edict ->
        val active = CrownEngine.has(state, edict)
        CrownCard(emphasized = active) {
            CardTitle(edict.label, null, if (active) "Aktiv" else null, Emerald)
            Text(edict.description, color = Mist, fontSize = 13.sp)
            SmallAction(if (active) "Aufheben" else "Verkünden · 2.500 Gold") { apply(CrownEngine.toggleEdict(state, edict)) }
        }
    }
}

private fun partnerPortrait(state: GameState, heroineId: String): String? =
    state.crown.saga.partner?.takeIf { it.background == heroineId }?.portraitUri
