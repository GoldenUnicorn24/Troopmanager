package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.LifeEngine
import com.goldenunicorn.troopmanager.engine.NpcEngine
import com.goldenunicorn.troopmanager.model.*

/**
 * Project Rebirth start: mode → origin → appearance → abilities → the companion question →
 * (realm, for landed modes) → summary. Every step validates itself; nothing is created until
 * the final button.
 */
private enum class WizardStep(val title: String) {
    MODE("Spielmodus"), ORIGIN("Herkunft"), LOOK("Aussehen"), SKILLS("Fähigkeiten"),
    COMPANION("Gefährtin"), REALM("Reich"), SUMMARY("Aufbruch"),
}

@Composable
fun NewGameWizard(
    onCreated: (GameState) -> Unit,
    onBack: () -> Unit,
    initialStep: Int = 0,
    initialCompanion: CompanionChoice? = null,
    initialMode: GameMode = GameMode.FREE_LIFE,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var name by remember { mutableStateOf(if (initialStep > 0) "Leon Falkner" else "") }
    var ageText by remember { mutableStateOf("24") }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var species by remember { mutableStateOf(Species.HUMAN) }
    var culture by remember { mutableStateOf(Culture.HUMAN) }
    var background by remember { mutableStateOf(Background.MERCENARY) }
    var profession by remember { mutableStateOf("") }
    var wealth by remember { mutableStateOf(WealthLevel.MODEST) }
    var home by remember { mutableStateOf(Towns.startable().first().provinceId) }
    var backstory by remember { mutableStateOf("") }
    var look by remember { mutableStateOf(defaultLook(Gender.MALE, Species.HUMAN)) }
    var sword by remember { mutableIntStateOf(30) }
    var bow by remember { mutableIntStateOf(30) }
    var riding by remember { mutableIntStateOf(30) }
    var leadership by remember { mutableIntStateOf(30) }
    var tactics by remember { mutableIntStateOf(30) }
    var diplomacy by remember { mutableIntStateOf(30) }
    var companionChoice by remember { mutableStateOf(initialCompanion) }
    var cName by remember { mutableStateOf(if (initialCompanion != null) "Mira Taufarn" else "") }
    var cAge by remember { mutableStateOf("23") }
    var cSpecies by remember { mutableStateOf(Species.HALF_ELF) }
    var cCulture by remember { mutableStateOf(Culture.WOOD_ELF) }
    var cOrigin by remember { mutableStateOf("") }
    var cProfession by remember { mutableStateOf("Grenzläuferin") }
    var cStanding by remember { mutableStateOf("Freie Frau") }
    var cCombat by remember { mutableStateOf("Bogenschützin") }
    var cMorals by remember { mutableStateOf("Hält Wort, auch wenn es wehtut.") }
    var cBackstory by remember { mutableStateOf("") }
    var cInterests by remember { mutableStateOf(setOf("Bogenschießen", "Sternkunde")) }
    var cPersonality by remember { mutableStateOf(NpcPersonality(warmth = 62, boldness = 70, honor = 60, humor = 55, curiosity = 64, temper = 30)) }
    var cLook by remember { mutableStateOf(defaultLook(Gender.FEMALE, Species.HALF_ELF)) }
    var cPath by remember { mutableStateOf("alina") }
    var cultures by remember { mutableStateOf(setOf(Culture.HUMAN, Culture.WALL)) }
    var storyActs by remember { mutableStateOf(true) }
    var ironman by remember { mutableStateOf(false) }

    val steps = WizardStep.entries.filter { it != WizardStep.REALM || mode.landed }
    var index by remember { mutableIntStateOf(initialStep.coerceIn(0, steps.lastIndex)) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val remaining = 100 - (sword + bow + riding + leadership + tactics + diplomacy - 180)
    val age = ageText.toIntOrNull() ?: 0
    val cAgeValue = cAge.toIntOrNull() ?: 0

    fun problem(s: WizardStep): String? = when (s) {
        WizardStep.ORIGIN -> when {
            name.isBlank() -> "Gib deinem Charakter einen Namen."
            age < 18 -> "Dein Charakter muss mindestens 18 Jahre alt sein."
            else -> null
        }
        WizardStep.SKILLS -> if (remaining != 0) "Verteile noch $remaining Attributpunkte." else null
        WizardStep.COMPANION -> when {
            companionChoice == null -> "Bitte beantworte die Frage zur Gefährtin."
            companionChoice == CompanionChoice.FULL && cName.isBlank() -> "Gib ihr einen Namen."
            companionChoice != CompanionChoice.NONE && cAgeValue < 18 && companionChoice == CompanionChoice.FULL -> "Sie muss mindestens 18 Jahre alt sein."
            else -> null
        }
        WizardStep.REALM -> if (cultures.isEmpty()) "Wähle mindestens eine Startkultur." else null
        else -> null
    }

    fun build(): GameState {
        val draft = when (companionChoice) {
            CompanionChoice.FULL -> NpcEngine.CompanionDraft(
                name = cName, age = cAgeValue, species = cSpecies, culture = cCulture, origin = cOrigin, profession = cProfession,
                socialStanding = cStanding, backstory = cBackstory, appearance = cLook, personality = cPersonality,
                interests = cInterests.toList(), morals = cMorals, combatStyle = cCombat,
            )
            CompanionChoice.PARTIAL -> NpcEngine.CompanionDraft(
                species = cSpecies, appearance = cLook,
                personality = NpcPersonality(warmth = cPersonality.warmth, boldness = cPersonality.boldness, humor = cPersonality.humor,
                    honor = 50, curiosity = 50, temper = 30),
            )
            else -> null
        }
        return LifeEngine.newGame(LifeEngine.StartConfig(
            mode = mode, name = name.trim(), age = age, gender = gender, species = species, culture = culture,
            background = if (mode.landed) Background.BORDER_LORD else background, profession = profession,
            wealth = if (mode == GameMode.SANDBOX) wealth else background.wealth, homeProvinceId = home,
            backstory = backstory, appearance = look,
            attributes = GameEngine.StartingAttributes(sword, bow, riding, leadership, tactics, diplomacy),
            startingCultures = if (mode.landed) cultures else null,
            weapon = AppearanceOptions.weapons[look.weapon], armorStyle = AppearanceOptions.armor[look.armor],
            ironman = ironman, storyActs = storyActs,
            companionChoice = companionChoice ?: CompanionChoice.NONE, companionDraft = draft, companionPath = cPath,
        ))
    }

    BackHandler(enabled = index > 0) { index-- }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        // Progress.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("NEUES SPIEL · SCHRITT ${index + 1} VON ${steps.size}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            Text(step.title, color = Color.White, fontSize = 26.sp, fontFamily = Cinzel, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEachIndexed { i, _ ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= index) Gold else Color.White.copy(alpha = .12f)))
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("wizard_${step.name.lowercase()}"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                WizardStep.MODE -> GameMode.entries.forEach { m ->
                    ModeCard(m, m == mode) {
                        mode = m
                        if (m == GameMode.FREE_LIFE && !background.freeLife) background = Background.MERCENARY
                    }
                }
                WizardStep.ORIGIN -> {
                    OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ageText, { ageText = it.filter(Char::isDigit).take(3) }, label = { Text("Alter (ab 18)") }, singleLine = true,
                        isError = age < 18, modifier = Modifier.fillMaxWidth())
                    WizardLabel("Geschlecht")
                    ChipLine(Gender.entries.map { it.label }, gender.label) { label ->
                        gender = Gender.entries.first { it.label == label }
                        look = defaultLook(gender, species).copy(hairColor = look.hairColor, skinTone = look.skinTone)
                    }
                    WizardLabel("Volk")
                    ChipLine(Species.entries.map { it.label }, species.label) { label ->
                        species = Species.entries.first { it.label == label }
                        look = look.copy(ears = if (species == Species.HUMAN) 0 else maxOf(1, look.ears), beard = if (species == Species.ELF) 0 else look.beard)
                        culture = when (species) { Species.HUMAN -> Culture.HUMAN; Species.ELF -> Culture.WOOD_ELF; Species.HALF_ELF -> Culture.WOOD_ELF }
                    }
                    WizardLabel("Kultur")
                    ChipLine(Culture.entries.map { it.label }, culture.label) { label -> culture = Culture.entries.first { it.label == label } }
                    if (!mode.landed) {
                        WizardLabel("Herkunft")
                        Background.entries.filter { !it.landed && (mode == GameMode.SANDBOX || it.freeLife) }.forEach { b ->
                            SelectCard(b.label, "${b.description}\nStart: ${b.wealth.label} · Ruf ${b.renown} · Stärken: ${b.hint}", b == background) {
                                background = b
                                profession = ""
                            }
                        }
                        OutlinedTextField(profession, { profession = it.take(28) }, label = { Text("Beruf") }, placeholder = { Text(background.profession) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (mode == GameMode.SANDBOX) {
                            WizardLabel("Anfangsvermögen")
                            ChipLine(WealthLevel.entries.map { "${it.label} (${it.gold})" }, "${wealth.label} (${wealth.gold})") { label ->
                                wealth = WealthLevel.entries.first { label.startsWith(it.label) }
                            }
                        }
                        WizardLabel("Startregion")
                        Towns.startable().forEach { t ->
                            SelectCard(CrownCatalog.info(t.provinceId).name, "${CrownCatalog.info(t.provinceId).land.label} · ${t.flavor}\nTaverne: „${t.tavernName}“", t.provinceId == home) { home = t.provinceId }
                        }
                    } else {
                        InfoBox("Du beginnst als Grenzherr mit Festung, eigenem Gebiet und stehender Armee.")
                    }
                    OutlinedTextField(backstory, { backstory = it.take(600) }, label = { Text("Hintergrundgeschichte (optional)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                WizardStep.LOOK -> CharacterEditor(look, gender, species, { look = it }, "player")
                WizardStep.SKILLS -> {
                    InfoBox("Jeder Wert startet bei 30. Verteile 100 Punkte. Deine Herkunft gibt zusätzlich +5 auf zwei passende Werte. Beim Start erhältst du außerdem 30 Fertigkeitspunkte.")
                    Text("Noch $remaining Punkte frei", color = if (remaining == 0) Gold else Mist, fontWeight = FontWeight.Bold)
                    AttrRow("Schwert", sword, remaining) { sword = it }
                    AttrRow("Bogen", bow, remaining) { bow = it }
                    AttrRow("Reiten", riding, remaining) { riding = it }
                    AttrRow("Führung", leadership, remaining) { leadership = it }
                    AttrRow("Taktik", tactics, remaining) { tactics = it }
                    AttrRow("Diplomatie (Charisma)", diplomacy, remaining) { diplomacy = it }
                }
                WizardStep.COMPANION -> {
                    PremiumPanel(emphasized = true) {
                        Text("Möchtest du eine mögliche spätere Gefährtin bzw. Ehefrau schon jetzt erstellen?", color = Color.White, fontSize = 18.sp,
                            fontFamily = Cinzel, lineHeight = 24.sp)
                        Text("Sie wird eine eigenständige Person in der Welt – mit eigenen Zielen, Launen und dem Recht, Nein zu sagen. Ob ihr euch begegnet, Freunde werdet, mehr oder gar nichts, entscheidet ihr beide im Spiel.",
                            color = Mist, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    SelectCard("A · Ja, vollständig erstellen", "Name, Alter, Volk, Herkunft, Beruf, Persönlichkeit, Interessen, Aussehen – alles in deiner Hand.", companionChoice == CompanionChoice.FULL) { companionChoice = CompanionChoice.FULL }
                    SelectCard("B · Teilweise festlegen", "Du bestimmst Aussehen, Volk und Grundzüge. Den Rest erfindet die Welt.", companionChoice == CompanionChoice.PARTIAL) { companionChoice = CompanionChoice.PARTIAL }
                    SelectCard("C · Nein", "Ich möchte Menschen später ganz natürlich in der Welt kennenlernen.", companionChoice == CompanionChoice.NONE) { companionChoice = CompanionChoice.NONE }
                    if (companionChoice == CompanionChoice.FULL || companionChoice == CompanionChoice.PARTIAL) {
                        val full = companionChoice == CompanionChoice.FULL
                        SectionTitle(if (full) "Sie gestalten" else "Grundzüge festlegen")
                        if (full) {
                            OutlinedTextField(cName, { cName = it.take(24) }, label = { Text("Ihr Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(cAge, { cAge = it.filter(Char::isDigit).take(3) }, label = { Text("Alter (ab 18)") }, singleLine = true,
                                isError = cAgeValue < 18, supportingText = { if (cAgeValue < 18) Text("Sie muss mindestens 18 Jahre alt sein.") }, modifier = Modifier.fillMaxWidth())
                        }
                        WizardLabel("Volk")
                        ChipLine(Species.entries.map { it.label }, cSpecies.label) { label ->
                            cSpecies = Species.entries.first { it.label == label }
                            cLook = cLook.copy(ears = if (cSpecies == Species.HUMAN) 0 else maxOf(1, cLook.ears))
                        }
                        if (full) {
                            WizardLabel("Kultur")
                            ChipLine(Culture.entries.map { it.label }, cCulture.label) { label -> cCulture = Culture.entries.first { it.label == label } }
                            OutlinedTextField(cOrigin, { cOrigin = it.take(40) }, label = { Text("Herkunft (Ort)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(cProfession, { cProfession = it.take(28) }, label = { Text("Beruf") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            WizardLabel("Kampffähigkeit")
                            ChipLine(listOf("Bogenschützin", "Schwert und Schild", "Zwillingsdolche", "Speerkämpferin", "Faustkampf", "Keine Kämpferin"), cCombat) { cCombat = it }
                            WizardLabel("Gesellschaftliche Stellung")
                            ChipLine(listOf("Freie Frau", "Gemeines Volk", "Bürgertum", "Niederer Adel", "Hochadel"), cStanding) { cStanding = it }
                            WizardLabel("Moralische Einstellung")
                            ChipLine(listOf("Hält Wort, auch wenn es wehtut.", "Sieht die Welt pragmatisch.", "Hilft Schwächeren ohne Zögern.", "Misstraut Mächtigen.", "Glaubt an zweite Chancen."), cMorals) { cMorals = it }
                            WizardLabel("Interessen (mehrere)")
                            FlowChips(listOf("Bogenschießen", "alte Lieder", "Pferde", "Würfelspiel", "Sternkunde", "Kräuterkunde", "Schwertkampf", "Handel", "Bücher", "Jagd", "Musik", "Seefahrt"), cInterests) { cInterests = it }
                        }
                        WizardLabel("Persönlichkeit")
                        TraitSlider("Wärme", "kühl", "herzlich", cPersonality.warmth) { cPersonality = cPersonality.copy(warmth = it) }
                        TraitSlider("Wagemut", "vorsichtig", "wagemutig", cPersonality.boldness) { cPersonality = cPersonality.copy(boldness = it) }
                        TraitSlider("Humor", "ernst", "verschmitzt", cPersonality.humor) { cPersonality = cPersonality.copy(humor = it) }
                        if (full) {
                            TraitSlider("Ehrgefühl", "gerissen", "ehrenhaft", cPersonality.honor) { cPersonality = cPersonality.copy(honor = it) }
                            TraitSlider("Neugier", "bodenständig", "neugierig", cPersonality.curiosity) { cPersonality = cPersonality.copy(curiosity = it) }
                            TraitSlider("Temperament", "gelassen", "hitzköpfig", cPersonality.temper) { cPersonality = cPersonality.copy(temper = it) }
                            OutlinedTextField(cBackstory, { cBackstory = it.take(600) }, label = { Text("Ihre Geschichte (optional)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        }
                        if (mode.landed) {
                            WizardLabel("Ihr Weg · Ort der ersten Begegnung")
                            listOf(
                                "alina" to "Grenzläuferin · im Nebel an der Furt von Rabenfurt",
                                "seraphine" to "Verstoßene Ritterin · beim Turnier von Aldenburg",
                                "liang" to "Strategin · im Kriegsrat am Kranichtor",
                                "ithiliel" to "Klingentänzerin · am Hof von Sonnenhain",
                            ).forEach { (id, text) -> SelectCard(text.substringBefore(" ·"), text.substringAfter("· "), cPath == id) { cPath = id } }
                        }
                        SectionTitle("Ihr Aussehen")
                        CharacterEditor(cLook, Gender.FEMALE, cSpecies, { cLook = it }, "companion")
                    }
                }
                WizardStep.REALM -> {
                    InfoBox("Fairer Start: immer 2.000 Bevölkerung und 160 Rekruten. Die Armee wird nach einem festen Kampfkraftbudget auf die gewählten Kulturen verteilt.")
                    Culture.entries.forEach { c ->
                        val sel = c in cultures
                        SelectCard(c.label, when (c) {
                            Culture.HUMAN -> "Schwertkämpfer · Bogenschützen · Schwere Ritter"
                            Culture.WOOD_ELF -> "Waldläufer · Waldklingen"
                            Culture.GOLD_ELF -> "Goldene Speerwache · Goldene Bogengarde"
                            Culture.WALL -> "Kranich-, Adler-, Tiger-, Bären-, Hirschkorps · Drachenartillerie"
                        }, sel) { cultures = if (sel) cultures - c else cultures + c }
                    }
                    if (mode == GameMode.BORDER_LORD) Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(storyActs, { storyActs = it })
                        Spacer(Modifier.width(10.dp))
                        Text("Kampagnenakte mit Bosskämpfen", color = Mist)
                    }
                }
                WizardStep.SUMMARY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CharacterPortrait(look, gender, Modifier.weight(1f).aspectRatio(.8f).clip(RoundedCornerShape(18.dp)), Framing.BUST)
                        if (companionChoice != CompanionChoice.NONE && companionChoice != null)
                            CharacterPortrait(cLook, Gender.FEMALE, Modifier.weight(1f).aspectRatio(.8f).clip(RoundedCornerShape(18.dp)), Framing.BUST)
                    }
                    PremiumPanel {
                        Text(name.ifBlank { "Namenlos" }, color = Color.White, fontSize = 22.sp, fontFamily = Cinzel)
                        Text("${gender.label} · $age Jahre · ${species.label} · ${culture.label}", color = Mist)
                        Text("Modus: ${mode.label} – ${mode.tagline}", color = PaleGold)
                        if (!mode.landed) Text("${background.label} · ${profession.ifBlank { background.profession }} · Start in ${CrownCatalog.info(home).name} · " +
                            "${if (mode == GameMode.SANDBOX) wealth.gold else background.wealth.gold} Gold", color = Mist, fontSize = 13.sp)
                        Text("Gefährtin: ${(companionChoice ?: CompanionChoice.NONE).label}" +
                            if (companionChoice == CompanionChoice.FULL) " – ${cName.ifBlank { "?" }}, $cAgeValue" else "", color = Mist, fontSize = 13.sp)
                        if (companionChoice != CompanionChoice.NONE && companionChoice != null)
                            Text("Keine Beziehung ist vorbestimmt. Sie lebt in der Welt und kann dich mögen – oder nicht.", color = Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(ironman, { ironman = it })
                        Spacer(Modifier.width(10.dp))
                        Text("Ironman · ein fortlaufender Spielstand", color = Mist)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val issue = problem(step)
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (issue != null) Text(issue, color = Danger, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (index == 0) onBack() else index-- }, modifier = Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .15f))) { Text(if (index == 0) "Hauptmenü" else "Zurück", color = Mist) }
                Button(
                    onClick = {
                        if (issue != null) return@Button
                        if (step == WizardStep.SUMMARY) onCreated(build()) else index++
                    },
                    enabled = issue == null,
                    modifier = Modifier.weight(1.4f).heightIn(min = 54.dp).testTag("wizard_next"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                ) { Text(if (step == WizardStep.SUMMARY) "SPIEL BEGINNEN" else "WEITER", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, selected: Boolean, onClick: () -> Unit) {
    val art = when (mode) {
        GameMode.SANDBOX -> "world_map.webp"
        GameMode.STORY -> "frontier/wall_crown.webp"
        GameMode.BORDER_LORD -> "menu_cover.webp"
        GameMode.FREE_LIFE -> "city_landscape.webp"
    }
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Color.White.copy(alpha = .1f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).testTag("mode_${mode.name.lowercase()}"),
    ) {
        ArtImage(art, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Ink.copy(alpha = .95f), Ink.copy(alpha = .55f), Color.Transparent))))
        Column(Modifier.align(Alignment.CenterStart).padding(18.dp).fillMaxWidth(.78f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModernPill(mode.tagline, if (selected) Gold else ModernBlue, filled = selected)
            }
            Text(mode.label, color = if (selected) PaleGold else Color.White, fontSize = 22.sp, fontFamily = Cinzel, fontWeight = FontWeight.Bold)
            Text(mode.description, color = Mist, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4)
        }
    }
}

@Composable
internal fun SelectCard(title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) Color(0xFF26313B) else Panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) Gold else Color.White.copy(alpha = .07f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (selected) PaleGold else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text, color = Mist, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun WizardLabel(text: String) = Text(text.uppercase(), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 4.dp))

@Composable
internal fun InfoBox(text: String) {
    Surface(color = Gold.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Gold.copy(alpha = .2f))) {
        Text(text, color = PaleGold, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ChipLine(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { RbChip(it, it == selected) { onSelect(it) } }
    }
}

@Composable
private fun FlowChips(options: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { o -> RbChip(o, o in selected) { onChange(if (o in selected) selected - o else (selected + o).take(4).toSet()) } }
            }
        }
    }
}

@Composable
private fun TraitSlider(label: String, low: String, high: String, value: Int, onValue: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row { Text(label, color = Mist, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${local.toInt()}", color = PaleGold, fontSize = 13.sp) }
        Slider(local, { local = it }, onValueChangeFinished = { onValue(local.toInt()) }, valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color.White.copy(alpha = .15f)))
        Row { Text(low, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(high, color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun AttrRow(label: String, value: Int, remaining: Int, onValue: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = { onValue((value - 5).coerceAtLeast(30)) }, enabled = value > 30, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−5") }
        TextButton(onClick = { onValue(value - 1) }, enabled = value > 30, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−") }
        Text("$value", color = PaleGold, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        TextButton(onClick = { onValue(value + 1) }, enabled = remaining > 0 && value < 100, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+") }
        TextButton(onClick = { onValue((value + minOf(5, remaining)).coerceAtMost(100)) }, enabled = remaining > 0 && value < 100, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+5") }
    }
}
