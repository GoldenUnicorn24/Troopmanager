package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldenunicorn.troopmanager.audio.GameSoundscape
import com.goldenunicorn.troopmanager.engine.CrownEngine
import com.goldenunicorn.troopmanager.engine.LifeEngine
import com.goldenunicorn.troopmanager.engine.TavernEngine
import com.goldenunicorn.troopmanager.model.*

/**
 * Main screen of a landless character: where you are, what time it is, what people say, who you
 * know and who you are. The strategic world map is the same crown map rulers use, read-only here.
 */
@Composable
fun LifeShell(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onMenu: () -> Unit,
    initialTab: Int = 0,
    initialTalk: String? = null,
) {
    if (state.life.skirmish != null) {
        GameSoundscape(state, "battle")
        SkirmishScreen(state, onState, onNotice)
        return
    }
    if (state.life.captive) {
        CaptiveScreen(state, onState, onNotice)
        return
    }
    if (state.life.place == LifePlace.CASTLE) {
        GameSoundscape(state, "military")
        CastleScreen(state, onState, onNotice)
        return
    }
    if (state.life.place == LifePlace.BARRACKS) {
        GameSoundscape(state, "military")
        BarracksScreen(state, onState, onNotice)
        return
    }
    if (state.life.place == LifePlace.MARKET) {
        GameSoundscape(state, "city")
        MarketScreen(state, onState, onNotice)
        return
    }
    if (state.life.place == LifePlace.TAVERN) {
        GameSoundscape(state, "city")
        TavernScreen(state, onState, onNotice, onExit = {}, initialTalk = initialTalk)
        return
    }
    GameSoundscape(state, "world")
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var editLook by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Ink,
        topBar = { LifeHeader(state) },
        bottomBar = {
            NavigationBar(containerColor = Color(0xF20E151D), modifier = Modifier.navigationBarsPadding()) {
                listOf("Ort", "Welt", "Leute", "Ich").forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(listOf("⌂", "◈", "☺", "✦")[i], fontSize = 18.sp) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White,
                            indicatorColor = Color.White.copy(alpha = .1f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("life_tab_$tab"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> PlaceTab(state, onState, onNotice)
                1 -> WorldTab(state, onState, onNotice)
                2 -> { PartySection(state, onState, onNotice); PeopleTab(state) }
                else -> SelfTab(state, onState, onNotice, onMenu) { editLook = true }
            }
        }
    }
    if (editLook) {
        var look by remember { mutableStateOf(state.life.appearance ?: defaultLook(state.life.gender, state.player.species)) }
        AlertDialog(
            onDismissRequest = { editLook = false },
            containerColor = Panel,
            title = { Text("Aussehen ändern", color = PaleGold, fontFamily = Cinzel) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { CharacterEditor(look, state.life.gender, state.player.species, { look = it }, "player") } },
            confirmButton = { TextButton(onClick = {
                onState(state.copy(life = state.life.copy(appearance = look), crown = state.crown.copy(playerLook = look.toPortraitSpec())))
                editLook = false
            }) { Text("Übernehmen", color = Gold) } },
            dismissButton = { TextButton(onClick = { editLook = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun LifeHeader(state: GameState) {
    val life = state.life
    Column(Modifier.fillMaxWidth().statusBarsPadding().background(Ink).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CharacterPortrait(life.appearance ?: defaultLook(life.gender, state.player.species), life.gender,
                Modifier.size(44.dp).clip(RoundedCornerShape(100.dp)).border(1.dp, Gold.copy(alpha = .5f), RoundedCornerShape(100.dp)), Framing.HEAD)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.player.name, color = Color.White, fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text("${life.profession} · ${life.career.label}", color = Mist, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${life.purse} Gold", color = PaleGold, fontWeight = FontWeight.Black)
                Text("Ruf ${life.renown}", color = Muted, fontSize = 11.sp)
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModernPill("Tag ${state.day}", Gold)
            ModernPill("${life.timeSlot.label} ${life.timeSlot.clock}", ModernBlue)
            if (life.intoxication > 0 || life.hangover) ModernPill(if (life.hangover) "Kater" else LifeEngine.intoxicationLabel(life.intoxication), Danger)
        }
    }
}

// ------------------------------------------------------------ place

@Composable
private fun PlaceTab(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    val life = state.life
    val province = CrownEngine.province(state, life.provinceId)
    val town = LifeEngine.town(state)
    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(22.dp))) {
        ArtImage("city_landscape.webp", Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .92f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(LifeEngine.locationName(state), color = Color.White, fontFamily = Cinzel, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${CrownCatalog.info(life.provinceId).land.label} · ${province?.ownerLabel ?: ""}" + (town?.let { " · ${it.flavor}" } ?: ""), color = Mist, fontSize = 12.sp, maxLines = 2)
        }
    }
    if (LifeEngine.threatened(state, life.provinceId)) InfoBox("Unruhe in der Stadt: Flüchtlinge auf den Straßen, die Preise steigen, Söldnerwerber suchen Klingen.")
    if (town != null) {
        Surface(onClick = { val r = TavernEngine.enter(state); onState(r.state); onNotice(r.message) }, color = Color.Transparent,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("enter_tavern")) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A2414), Color(0xFF120A06))))
                .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(22.dp))) {
                Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                    Text("TAVERNE BETRETEN", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("„${town.tavernName}“", color = Color.White, fontFamily = Cinzel, fontSize = 20.sp)
                    Text(when (life.timeSlot) {
                        TimeSlot.EVENING -> "Abends ist die Schankstube voll."
                        TimeSlot.MORNING -> "Morgens ist es still; der Wirt fegt."
                        TimeSlot.NIGHT -> "Die letzten Gäste und die Nachtschwärmer."
                        else -> "Ein paar Gäste, Würfel und Gerüchte."
                    }, color = Mist, fontSize = 12.sp)
                }
            }
        }
    }
    if (town != null) {
        val open = com.goldenunicorn.troopmanager.engine.MarketEngine.open(state)
        Surface(onClick = { val r = com.goldenunicorn.troopmanager.engine.MarketEngine.enter(state); onState(r.state); onNotice(r.message) }, color = Color.Transparent,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("enter_market")) {
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF243447), Color(0xFF0E151D))))
                .border(1.dp, ModernBlue.copy(alpha = .35f), RoundedCornerShape(22.dp))) {
                Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                    Text("MARKT", color = ModernBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("Schmied · Krämer · Heilerin", color = Color.White, fontFamily = Cinzel, fontSize = 18.sp)
                    Text(if (open) "Die Stände sind offen." else "Geschlossen – morgen früh wieder.", color = Mist, fontSize = 12.sp)
                }
            }
        }
    }
    if (town != null) {
        Surface(onClick = { val r = com.goldenunicorn.troopmanager.engine.BarracksEngine.enter(state); onState(r.state); onNotice(r.message) }, color = Color.Transparent,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("enter_barracks")) {
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A2A2A), Color(0xFF120E0E))))
                .border(1.dp, Danger.copy(alpha = .35f), RoundedCornerShape(22.dp))) {
                Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                    Text("KASERNE", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("Training · Stadtwache · Söldner", color = Color.White, fontFamily = Cinzel, fontSize = 18.sp)
                    Text(if (life.enlisted) "Du dienst in der Stadtwache." else "Übungen kosten ${com.goldenunicorn.troopmanager.engine.BarracksEngine.TRAINING_PRICE} Gold.", color = Mist, fontSize = 12.sp)
                }
            }
        }
    }
    if (town != null) {
        val castle = com.goldenunicorn.troopmanager.engine.CastleEngine
        val tourney = castle.tournamentToday(state)
        Surface(onClick = { val r = castle.enter(state); onState(r.state); onNotice(r.message) }, color = Color.Transparent,
            shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("enter_castle")) {
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A3424), Color(0xFF12100A))))
                .border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(22.dp))) {
                Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                    Text("BURGHOF", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("Burgvogt · Aufträge · Turnier", color = Color.White, fontFamily = Cinzel, fontSize = 18.sp)
                    Text(when {
                        life.lordQuest != null -> "Lehnsauftrag offen: ${CrownCatalog.info(life.lordQuest.targetProvinceId).name}."
                        tourney -> "Heute ist Turniertag!"
                        else -> "Nächstes Turnier an Tag ${castle.nextTournamentDay(state)}."
                    }, color = Mist, fontSize = 12.sp)
                }
            }
        }
    }
    if (life.wound > 0) PremiumPanel {
        Text("Verwundet", color = Danger, fontWeight = FontWeight.Bold)
        RelationBar("Wunden", life.wound, Danger)
        Text(if (life.wound >= 70) "Reisen ist unmöglich. Ein Bett und die Heilerin helfen." else "Heilt mit der Zeit; ein Bett und die Heilerin beschleunigen es.", color = Muted, fontSize = 12.sp)
    }
    val jobs = life.jobs.filter { it.accepted && !it.done && !it.failed }
    if (jobs.isNotEmpty()) {
        SectionTitle("Deine Aufträge")
        jobs.forEach { j ->
            PremiumPanel {
                Text(j.title, color = PaleGold, fontWeight = FontWeight.Bold)
                Text(j.description, color = Mist, fontSize = 13.sp)
                Text("${LifeEngine.skillLabel(j.skill)} gegen ${j.difficulty} · ${j.reward} Gold · ${j.slots} Tageszeit(en)", color = Muted, fontSize = 12.sp)
                if (j.provinceId == life.provinceId) RbAction("Erledigen", enabled = LifeEngine.slotsLeft(state) >= j.slots) {
                    val r = LifeEngine.doJob(state, j.id); onState(r.state); onNotice(r.message)
                } else Text("Nur in ${CrownCatalog.info(j.provinceId).name} möglich.", color = Muted, fontSize = 12.sp)
            }
        }
    }
    SectionTitle("Der Tag")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RbAction("Zeit vergehen lassen", enabled = life.timeSlot != TimeSlot.NIGHT) { val r = LifeEngine.passTime(state); onState(r.state); onNotice(r.message) }
        RbAction("Schlafen") { val r = LifeEngine.sleep(state); onState(r.state); onNotice(r.message) }
    }
    Text(if (LifeEngine.hasRoom(state)) "Dein Zimmer in der Taverne ist bezahlt." else "Ohne Zimmer schläfst du unter freiem Himmel – kalt und nicht ganz sicher.", color = Muted, fontSize = 12.sp)
    SectionTitle("Reisen")
    Towns.all.filter { it.provinceId != life.provinceId }.forEach { t ->
        val blocker = LifeEngine.travelBlocker(state, t.provinceId)
        val days = LifeEngine.travelDays(life.provinceId, t.provinceId)
        Surface(onClick = { if (blocker == null) { val r = LifeEngine.travel(state, t.provinceId); onState(r.state); onNotice(r.message) } else onNotice(blocker) },
            color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .06f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(CrownCatalog.info(t.provinceId).name, color = if (blocker == null) Color.White else Muted, fontWeight = FontWeight.Bold)
                    Text(blocker ?: "${CrownCatalog.info(t.provinceId).land.label} · $days Tag(e) · ${days * 3} Gold Proviant", color = Muted, fontSize = 12.sp)
                }
                Text("›", color = Gold, fontSize = 22.sp)
            }
        }
    }
    SectionTitle("Aufstieg")
    val blocker = LifeEngine.keepBlocker(state)
    PremiumPanel(emphasized = blocker == null) {
        Text("Die Grenzfeste", color = PaleGold, fontFamily = Cinzel, fontSize = 18.sp)
        Text("Die Krone sucht einen Herrn für die verwaiste Grenzfeste. Wer genug Ruf und Gold hat, kann den Lehnseid leisten – und erhält Gebiet, Vorräte und eine stehende Armee.", color = Mist, fontSize = 13.sp)
        RelationBar("Ruf", (life.renown * 100 / LifeEngine.KEEP_RENOWN).coerceAtMost(100), Gold)
        RelationBar("Gold", (life.purse * 100 / LifeEngine.keepGold(state)).coerceAtMost(100), PaleGold)
        if (blocker == null) GoldButton("Lehnseid leisten", { val r = LifeEngine.claimKeep(state); onState(r.state); onNotice(r.message) }, Modifier.fillMaxWidth())
        else Text(blocker, color = Muted, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------ world

@Composable
private fun WorldTab(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit) {
    var selected by remember { mutableStateOf<String?>(state.life.provinceId) }
    PageTitle("Die Kronlande", "Dieselbe Welt, die Könige regieren – du siehst sie von unten.")
    CrownMap(state, selected) { selected = it }
    selected?.let { id ->
        val p = CrownEngine.province(state, id)
        if (p != null) PremiumPanel {
            Text(p.name, color = PaleGold, fontFamily = Cinzel, fontSize = 18.sp)
            Text("${p.land.label} · ${p.terrain} · ${p.ownerLabel}", color = Mist, fontSize = 13.sp)
            Text("Bevölkerung ${compact(p.population)} · Garnison ${compact(p.garrison)}", color = Muted, fontSize = 12.sp)
            if (id == state.life.provinceId) Text("Du bist hier.", color = Gold, fontSize = 12.sp)
            Towns.byProvince(id)?.let { t -> Text("Taverne: „${t.tavernName}“", color = Mist, fontSize = 12.sp) }
        }
    }
    SectionTitle("Was man sich erzählt")
    val news = state.life.news.reversed()
    if (news.isEmpty()) EmptyCard("Noch keine Neuigkeiten. Hör dich in den Tavernen um.")
    news.take(12).forEach { n ->
        PremiumPanel {
            Text("Tag ${n.day} · ${n.title}", color = if (n.severity >= 2) Danger.copy(alpha = .9f) else PaleGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(n.text, color = Mist, fontSize = 13.sp, fontFamily = Cormorant, fontStyle = FontStyle.Italic)
        }
    }
}

// ------------------------------------------------------------ people

@Composable
private fun PeopleTab(state: GameState) {
    val known = state.life.npcs.filter { it.relation.timesMet > 0 }.sortedByDescending { it.relation.affinity + it.relation.trust }
    PageTitle("Leute", "Wen du kennst – und was sie über dich denken.")
    if (known.isEmpty()) EmptyCard("Du kennst hier noch niemanden. Tavernen sind ein guter Anfang.")
    known.forEach { npc ->
        PremiumPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CharacterPortrait(npc.appearance, npc.gender, Modifier.size(width = 72.dp, height = 90.dp).clip(RoundedCornerShape(14.dp)), Framing.BUST)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(npc.name, color = Color.White, fontFamily = Cinzel, fontWeight = FontWeight.Bold)
                    Text("${npc.profession} · ${CrownCatalog.info(npc.homeProvinceId).name}", color = Mist, fontSize = 12.sp)
                    Text(npc.relation.stage.label + " · ${npc.personality.traits().take(2).joinToString()}", color = PaleGold, fontSize = 12.sp)
                    RelationBar("Sympathie", (npc.relation.affinity + 100) / 2, Gold)
                    RelationBar("Vertrauen", npc.relation.trust, ModernBlue)
                }
            }
            // What the player has learned about this person's own ties.
            com.goldenunicorn.troopmanager.engine.NpcSocialEngine.bondsOf(state, npc.id)
                .filter { b -> state.life.npc(b.other(npc.id))?.relation?.timesMet ?: 0 > 0 }
                .forEach { b ->
                    val other = state.life.npc(b.other(npc.id))!!
                    Text("${b.kind.label}: ${other.name}", color = if (b.kind == BondKind.RIVALS) Danger.copy(alpha = .9f) else ModernBlue, fontSize = 12.sp)
                }
            val memories = npc.memories.filter { it.key != "met" && it.text.isNotBlank() }.takeLast(3)
            memories.forEach { m -> Text("„${m.text.replaceFirstChar { it.uppercase() }}.“ · Tag ${m.day}", color = if (m.weight < 0) Danger else Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic) }
        }
    }
}

// ------------------------------------------------------------ self

@Composable
private fun SelfTab(state: GameState, onState: (GameState) -> Unit, onNotice: (String) -> Unit, onMenu: () -> Unit, onEditLook: () -> Unit) {
    val life = state.life
    val p = state.player
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CharacterPortrait(life.appearance ?: defaultLook(life.gender, p.species), life.gender, Modifier.weight(1f).aspectRatio(.62f).clip(RoundedCornerShape(20.dp)), Framing.FULL)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.name, color = Color.White, fontFamily = Cinzel, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${life.gender.label} · ${p.age} · ${p.species.label} · ${life.culture.label}", color = Mist, fontSize = 12.sp)
            Text("${life.background.label} · ${life.profession}", color = PaleGold, fontSize = 12.sp)
            Text("Modus: ${life.mode.label}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            listOf("Schwert" to p.sword, "Bogen" to p.bow, "Reiten" to p.riding, "Führung" to p.leadership, "Taktik" to p.tactics, "Diplomatie" to p.diplomacy)
                .forEach { (label, v) -> RelationBar("$label $v", v, Gold) }
            RbAction("Aussehen ändern", onClick = onEditLook)
        }
    }
    PremiumPanel {
        Text("Ausrüstung & Gepäck", color = PaleGold, fontWeight = FontWeight.Bold)
        Text("Waffe: ${GearCatalog.weapons[life.gear.weaponTier].name} (+${GearCatalog.weapons[life.gear.weaponTier].bonus})", color = Mist, fontSize = 13.sp)
        Text("Rüstung: ${GearCatalog.armor[life.gear.armorTier].name} (Schutz +${GearCatalog.armor[life.gear.armorTier].bonus})", color = Mist, fontSize = 13.sp)
        val gifts = life.gifts.filter { it.value > 0 }
        Text(if (gifts.isEmpty()) "Keine Geschenke im Gepäck." else "Geschenke: " + gifts.entries.joinToString { "${it.value}× ${it.key.label}" }, color = Muted, fontSize = 12.sp)
        Text("Kämpfe: ${life.battles} · Siege: ${life.battleVictories}" + if (life.wound > 0) " · Wunden ${life.wound}" else "", color = Muted, fontSize = 12.sp)
    }
    if (life.backstory.isNotBlank()) PremiumPanel {
        Text("Herkunft", color = PaleGold, fontWeight = FontWeight.Bold)
        Text(life.backstory, color = Mist, fontFamily = Cormorant, fontSize = 15.sp)
    }
    SectionTitle("Tagebuch")
    life.log.takeLast(12).reversed().forEach { Text(it, color = Muted, fontSize = 12.sp) }
    if (life.log.isEmpty()) Text("Noch leer.", color = Muted, fontSize = 12.sp)
    SectionTitle("Spiel")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RbAction("Hauptmenü", onClick = onMenu)
    }
}
