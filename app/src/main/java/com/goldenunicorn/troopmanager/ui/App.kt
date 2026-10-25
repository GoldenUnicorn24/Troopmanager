package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.engine.InboxEngine

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.goldenunicorn.troopmanager.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goldenunicorn.troopmanager.R
import com.goldenunicorn.troopmanager.data.SaveRepository
import com.goldenunicorn.troopmanager.engine.CrownEngine
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.HeroineSagaEngine
import com.goldenunicorn.troopmanager.model.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goldenunicorn.troopmanager.audio.GameSoundscape

@Composable
fun RealmGameApp(saves: SaveRepository) {
    val controller: GameViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = GameViewModel.factory(saves))
    val ui by controller.ui.collectAsStateWithLifecycle()
    val state = ui.game
    val systemDensity = LocalDensity.current
    val settings = state?.settings ?: GameSettings()
    CompositionLocalProvider(
        LocalDensity provides Density(systemDensity.density, systemDensity.fontScale * settings.textScale),
        LocalGameHapticsEnabled provides settings.haptics,
    ) {
        MaterialTheme(typography = AppTypography, colorScheme = darkColorScheme(primary = Gold, onPrimary = Ink, secondary = Blue,
            background = Ink, surface = Panel, onSurface = Mist)) {
            Box(Modifier.fillMaxSize().background(Ink)) {
                when {
                    ui.inMenu -> MainMenu(ui, controller)
                    state == null -> CharacterCreation(controller::create, controller::menu)
                    else -> GameShell(state, { controller.update(state, it) }, { controller.notice(it) },
                        controller::menu, controller::delete, controller::advanceDay, controller)
                }
                ui.notice?.let { message ->
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 52.dp, start = 18.dp, end = 18.dp),
                        color = Color(0xEE24303A), shape = RoundedCornerShape(14.dp), shadowElevation = 8.dp) {
                        Text(message, color = Color.White, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { controller.notice(null) }.padding(16.dp))
                    }
                    LaunchedEffect(message) { kotlinx.coroutines.delay(4500); controller.notice(null) }
                }
                if (ui.busy) {
                    Surface(Modifier.fillMaxSize().clickable(onClick = {}), color = Ink.copy(alpha = .45f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainMenu(ui: GameUiState, controller: GameViewModel) {
    GameSoundscape(ui.game, "menu")
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = "file:///android_asset/menu_cover.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.splash_fortress),
            error = painterResource(R.drawable.splash_fortress),
        )
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0xF2070A0D))))
        )
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ModernPill("Offline Strategy RPG", ModernBlue, filled = true)
                ModernPill("v${com.goldenunicorn.troopmanager.BuildConfig.VERSION_NAME}", Gold)
            }
            Spacer(Modifier.height(12.dp))
            Text("REALM OF THE", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(
                "LAST WALL",
                color = Color.White,
                fontSize = 46.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Black,
                fontFamily = Cinzel,
            )
            Text(
                "Baue dein Reich. Führe Armeen. Regiere gemeinsam. Jede Entscheidung verändert die Grenze.",
                color = Mist,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp).widthIn(max = 420.dp),
            )

            PremiumPanel(emphasized = ui.hasSave) {
                Text("KAMPAGNE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                SaveSlotsPanel(ui, controller)

                if (ui.hasSave) {
                    GoldButton("Spiel fortsetzen", controller::continueGame, Modifier.fillMaxWidth())
                }

                var confirmOverwrite by remember { mutableStateOf(false) }
                if (confirmOverwrite) AlertDialog(
                    onDismissRequest = { confirmOverwrite = false },
                    title = { Text("Neues Reich in Platz ${ui.activeSlot}?") },
                    text = { Text("Die Kampagne in diesem Platz wird beim Gründen ersetzt. Wähle einen leeren Platz, um sie zu behalten.") },
                    confirmButton = { TextButton(onClick = { confirmOverwrite = false; controller.newGame() }) { Text("Neues Reich") } },
                    dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Abbrechen") } },
                )
                OutlinedButton(
                    onClick = { if (ui.hasSave) confirmOverwrite = true else controller.newGame() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .13f)),
                ) {
                    Text("NEUES REICH", fontWeight = FontWeight.Black, color = Color.White, fontSize = 11.sp)
                }
            }

            Text(
                "100 % offline · kein Pay-to-Win · lokale Spielstände",
                color = Color(0xFF8D99A2),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
internal fun CharacterCreation(onCreated: (GameState) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("23") }
    var species by remember { mutableStateOf(Species.HALF_ELF) }
    var startingCultures by remember(species) { mutableStateOf<Set<Culture>>(emptySet()) }
    var ironman by remember { mutableStateOf(false) }
    var portrait by remember { mutableStateOf<String?>(null) }
    var weapon by remember { mutableStateOf("Langschwert") }
    var armor by remember { mutableStateOf("Grenzwächter") }
    // v1.1: the future partner is designed now and met after about a week.
    var partnerName by remember { mutableStateOf("") }
    var partnerAge by remember { mutableStateOf("22") }
    var partnerSpecies by remember { mutableStateOf(Species.HALF_ELF) }
    var partnerBackground by remember { mutableStateOf("alina") }
    var partnerPortrait by remember { mutableStateOf<String?>(null) }
    var playerLook by remember { mutableStateOf(PortraitSpec(skin = 1, hairColor = 1, armorColor = 3, background = 3, elfEars = true)) }
    var partnerLook by remember { mutableStateOf(com.goldenunicorn.troopmanager.model.ActCatalog.heroineLook("alina")) }
    // v0.61 keeps roughly the old total starting power while letting the player shape it.
    var sword by remember { mutableStateOf(30) }
    var bow by remember { mutableStateOf(30) }
    var riding by remember { mutableStateOf(30) }
    var leadership by remember { mutableStateOf(30) }
    var tactics by remember { mutableStateOf(30) }
    var diplomacy by remember { mutableStateOf(30) }
    val remainingAttributePoints =
        100 - ((sword - 30) + (bow - 30) + (riding - 30) + (leadership - 30) +
            (tactics - 30) + (diplomacy - 30))
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                portrait = uri.toString()
            }
        }
    val partnerPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                partnerPortrait = uri.toString()
            }
        }
    val partnerAgeValue = partnerAge.toIntOrNull() ?: 0
    val partnerValid = partnerName.isNotBlank() && partnerAgeValue >= 18
    val nameValid = name.isNotBlank()

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("DEIN URSPRUNG", color = Gold, fontSize = 13.sp, letterSpacing = 2.sp)
        Text(
            "Erschaffe den Herrscher",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Du startest mit Grenzfeste, eigenem Gebiet, Vorräten und einer stehenden Armee.",
            color = Mist,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Surface(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (portrait != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(portrait, null, Modifier.size(112.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop)
                        Column {
                            Text("Eigenes Foto gewählt", color = PaleGold, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { portrait = null }) { Text("Lieber gezeichnet") }
                        }
                    }
                } else LookEditor(playerLook, { playerLook = it })
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Eigenes Foto verwenden") }
            }
        }

        OutlinedTextField(
            name,
            { name = it.take(24) },
            label = { Text("Dein Name") },
            placeholder = { Text("Wie soll man dich nennen?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        OutlinedTextField(
            age,
            { age = it.filter(Char::isDigit).take(2) },
            label = { Text("Alter") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        SectionTitle("Volk")
        Species.entries.forEach { option ->
            val selected = option == species
            Surface(
                modifier =
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { species = option; playerLook = playerLook.copy(elfEars = option != Species.HUMAN) },
                color = if (selected) Color(0xFF27313A) else Panel,
                shape = RoundedCornerShape(14.dp),
                border =
                    if (selected) androidx.compose.foundation.BorderStroke(1.dp, Gold) else null,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        option.label,
                        color = if (selected) PaleGold else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (option) {
                            Species.HUMAN ->
                                "Menschliche Herkunft · +5 % Goldertrag durch Verwaltung. Deine Startarmee bleibt frei wählbar."
                            Species.ELF ->
                                "Elbische Herkunft · günstigere Aufklärung und +10 % Marschtempo im Wald. Deine Startarmee bleibt frei wählbar."
                            Species.HALF_ELF ->
                                "Halbelbische Herkunft · +8 Diplomatie und geringere Kulturspannung. Deine Startarmee bleibt frei wählbar."
                        },
                        color = Mist,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        SectionTitle("Startarmeen auswählen")
        Text(
            "Wähle mindestens eine Kultur. Deine Rasse bestimmt deinen Charakter – die Startarmee kannst du frei zusammenstellen.",
            color = Mist,
            fontSize = 12.sp,
        )
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Culture.entries.forEach { culture ->
                    val selected = culture in startingCultures
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            startingCultures =
                                if (selected) startingCultures - culture
                                else startingCultures + culture
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                startingCultures =
                                    if (checked) startingCultures + culture
                                    else startingCultures - culture
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(culture.label, color = if (selected) PaleGold else Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                when (culture) {
                                    Culture.HUMAN -> "Schwertkämpfer · Bogenschützen · Schwere Ritter"
                                    Culture.WOOD_ELF -> "Waldläufer · Waldklingen"
                                    Culture.GOLD_ELF -> "Goldene Speerwache · Goldene Bogengarde"
                                    Culture.WALL -> "Kranich-, Adler-, Tiger-, Bären-, Hirschkorps · Drachenartillerie"
                                },
                                color = Mist,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                Text(
                    "Fairer Start: immer 2.000 Bevölkerung und 160 Rekruten. Die Armee wird nach einem festen Kampfkraftbudget verteilt: Elitekulturen starten mit weniger, dafür stärkeren Soldaten; Massenkulturen mit mehr Köpfen.",
                    color = Gold,
                    fontSize = 12.sp,
                )
                if (startingCultures.isEmpty())
                    Text("Mindestens eine Startkultur auswählen.", color = Danger, fontSize = 12.sp)
            }
        }

        SectionTitle("Ausrüstung")
        ChoiceRow(listOf("Langschwert", "Bastardschwert", "Streitaxt", "Speer", "Langbogen", "Zwillingsklingen"), weapon) { weapon = it }
        ChoiceRow(listOf("Grenzwächter", "Schwere Platte", "Kettenhemd", "Elbenschuppen", "Lamellenrüstung", "Leichtes Leder"), armor) { armor = it }

        SectionTitle("Deine zukünftige Partnerin")
        Text(
            "Gestalte schon jetzt, wer sie ist. Kennen lernt ihr euch erst nach etwa einer Woche – wo und wie, hängt von ihrem Werdegang ab. Danach entscheidest du, ob sie deine Feldherrin wird und wohin eure Geschichte führt.",
            color = Mist,
            fontSize = 13.sp,
        )
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (partnerPortrait != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(partnerPortrait, null, Modifier.size(112.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop)
                        Column {
                            Text("Eigenes Foto gewählt", color = PaleGold, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { partnerPortrait = null }) { Text("Lieber gezeichnet") }
                        }
                    }
                } else LookEditor(partnerLook, { partnerLook = it }, allowBeard = false)
                OutlinedButton(onClick = { partnerPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Eigenes Foto für sie verwenden") }
            }
        }
        OutlinedTextField(partnerName, { partnerName = it.take(24) }, label = { Text("Name der Partnerin") },
            placeholder = { Text("Ihr Name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        OutlinedTextField(partnerAge, { partnerAge = it.filter(Char::isDigit).take(3) }, label = { Text("Alter (ab 18)") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            isError = partnerAgeValue < 18,
            supportingText = { if (partnerAgeValue < 18) Text("Deine Partnerin muss mindestens 18 Jahre alt sein.") })
        Text("Volk", color = PaleGold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        ChoiceRow(Species.entries.map { it.label }, partnerSpecies.label) { label ->
            partnerSpecies = Species.entries.first { it.label == label }
            partnerLook = partnerLook.copy(elfEars = partnerSpecies != Species.HUMAN)
        }
        Text("Werdegang · Ort eurer Begegnung", color = PaleGold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        listOf(
            Triple("alina", "Grenzläuferin", "Meisterbogenschützin. Ihr begegnet euch im Nebel an der Furt von Rabenfurt."),
            Triple("seraphine", "Verstoßene Ritterin", "Unbesiegt im Schwertkampf. Ihr begegnet euch beim Turnier von Aldenburg."),
            Triple("liang", "Strategin", "Brillante Feldherrin der Mauerlegion. Ihr begegnet euch im Kriegsrat am Kranichtor."),
            Triple("ithiliel", "Klingentänzerin", "Diplomatin und Klingenmeisterin. Ihr begegnet euch am Hof von Sonnenhain."),
        ).forEach { (id, label, text) ->
            val selected = partnerBackground == id
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { partnerBackground = id },
                color = if (selected) Color(0xFF27313A) else Panel,
                shape = RoundedCornerShape(14.dp),
                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Gold) else null,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, color = if (selected) PaleGold else Color.White, fontWeight = FontWeight.Bold)
                    Text(text, color = Mist, fontSize = 12.sp)
                }
            }
        }

        SectionTitle("Attribute · 100 Punkte selbst verteilen")
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Noch $remainingAttributePoints Punkte frei · jeder Wert startet bei 30.",
                    color = if (remainingAttributePoints == 0) Gold else Mist,
                    fontSize = 12.sp,
                )
                AttributeAllocatorRow("Schwert", sword, remainingAttributePoints) { sword = it }
                AttributeAllocatorRow("Bogen", bow, remainingAttributePoints) { bow = it }
                AttributeAllocatorRow("Reiten", riding, remainingAttributePoints) { riding = it }
                AttributeAllocatorRow("Führung", leadership, remainingAttributePoints) { leadership = it }
                AttributeAllocatorRow("Taktik", tactics, remainingAttributePoints) { tactics = it }
                AttributeAllocatorRow("Diplomatie", diplomacy, remainingAttributePoints) { diplomacy = it }
                Text(
                    "Zusätzlich erhältst du beim Start 30 frei nutzbare Fertigkeitspunkte für die sechs Entwicklungswege.",
                    color = PaleGold,
                    fontSize = 12.sp,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(ironman, { ironman = it }); Text("Ironman · ein fortlaufender Spielstand", color = Mist)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                onCreated(
                    GameEngine.newGame(
                        name,
                        age.toIntOrNull() ?: 23,
                        species,
                        portrait,
                        GameEngine.StartingAttributes(
                            sword = sword,
                            bow = bow,
                            riding = riding,
                            leadership = leadership,
                            tactics = tactics,
                            diplomacy = diplomacy,
                        ),
                        startingCultures = startingCultures,
                    ).let { created ->
                        HeroineSagaEngine.setPartner(
                            created.copy(
                                settings = created.settings.copy(ironman = ironman),
                                player = created.player.copy(weapon = weapon, armorStyle = armor),
                                crown = created.crown.copy(playerLook = playerLook),
                            ),
                            PartnerProfile(partnerName, partnerAgeValue, partnerSpecies, partnerBackground, partnerPortrait, look = partnerLook),
                        )
                    }
                )
            },
            enabled = remainingAttributePoints == 0 && startingCultures.isNotEmpty() && partnerValid && nameValid,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
        ) {
            Text(
                when {
                    !nameValid -> "GIB DEINEN NAMEN EIN"
                    startingCultures.isEmpty() -> "STARTKULTUR AUSWÄHLEN"
                    !partnerValid -> "PARTNERIN: NAME UND ALTER AB 18"
                    remainingAttributePoints == 0 -> "REICH GRÜNDEN"
                    else -> "NOCH $remainingAttributePoints ATTRIBUTSPUNKTE VERTEILEN"
                },
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Zurück")
        }
    }
}

@Composable
private fun AttributeAllocatorRow(
    label: String,
    value: Int,
    remaining: Int,
    onValue: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > 30) onValue(value - 1) }, enabled = value > 30) {
            Text("−")
        }
        Text(
            value.toString(),
            color = PaleGold,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(34.dp),
        )
        TextButton(
            onClick = { if (remaining > 0 && value < 100) onValue(value + 1) },
            enabled = remaining > 0 && value < 100,
        ) {
            Text("+")
        }
    }
}

@Composable
internal fun GameShell(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onMenu: () -> Unit,
    onDelete: () -> Unit,
    onAdvanceDay: () -> Unit,
    controller: GameViewModel,
    initialSpot: Spot = Spot.INBOX,
) {
    var areaName by rememberSaveable { mutableStateOf(initialSpot.area.name) }
    val area = MainArea.valueOf(areaName)
    var tabs by rememberSaveable { mutableStateOf(List(MainArea.entries.size) { if (it == initialSpot.area.ordinal) initialSpot.tab else 0 }) }
    var subs by rememberSaveable { mutableStateOf(List(MainArea.entries.size) { if (it == initialSpot.area.ordinal) initialSpot.sub else 0 }) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showCensus by rememberSaveable { mutableStateOf(false) }
    var showDefense by rememberSaveable { mutableStateOf(false) }
    var dismissedScene by rememberSaveable { mutableStateOf<String?>(null) }
    fun open(spot: Spot) {
        showMore = false
        areaName = spot.area.name
        tabs = tabs.toMutableList().also { it[spot.area.ordinal] = spot.tab }
        subs = subs.toMutableList().also { it[spot.area.ordinal] = spot.sub }
    }
    fun openLegacy(destination: GameDestination) = open(Navigation.spotFor(destination))
    BackHandler(enabled = showMore || area != MainArea.TODAY || tabs[0] != 0) {
        if (showMore) showMore = false else open(Spot.INBOX)
    }
    var showTutorial by remember(state.tutorialSeen) { mutableStateOf(false) }
    val battleVisible = !showMore && state.battleSession != null
    val sagaScene = state.crown.saga.pendingScene
    val inboxCount = remember(state) { InboxEngine.count(state) }
    Scaffold(
        containerColor = Ink,
        topBar = {
            if (!battleVisible) Column(
                Modifier.fillMaxWidth().statusBarsPadding().background(Ink)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 5.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).clickable { showCensus = true },
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(state.realm.settlementName, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Cinzel, maxLines = 1)
                        Text("Tag ${state.day} · ${state.title}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    if (state.player.skillPoints > 0) {
                        Surface(
                            color = Gold.copy(alpha = .13f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .25f)),
                            modifier = Modifier.clickable { open(Spot.SKILLS) },
                        ) {
                            Text("${state.player.skillPoints} SKILL", color = PaleGold, fontSize = 11.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                        }
                    }
                    IconButton(onClick = { showTutorial = true }) { Icon(Icons.Outlined.HelpOutline, "Hilfe", tint = Muted) }
                    IconButton(onClick = { showMore = !showMore }) { Icon(Icons.Outlined.MoreVert, "Mehr: Optionen, Hilfe, Spielstand", tint = Gold) }
                }

                ResourceStrip(state.resources, EconomyEngine.production(state).net)

                val nearestThreat = state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }
                val crownThreat = state.crown.threats.minByOrNull { it.arrivalDay }
                val warningText = when {
                    state.crown.duel != null -> "Duell gegen ${state.crown.duel.opponentName} wartet"
                    sagaScene != null -> "${sagaScene.title} · ${sagaScene.location}"
                    crownThreat != null -> "Angriff auf ${CrownEngine.province(state, crownThreat.targetId)?.name ?: "Provinz"} in ${(crownThreat.arrivalDay - state.day).coerceAtLeast(0)} T."
                    state.invasion != null -> "${state.invasion.enemy.label} · ${(state.invasion.arrivalDay - state.day).coerceAtLeast(0)} T. · ${state.invasionStrengthEstimate()}"
                    nearestThreat != null -> "${nearestThreat.name} · ${nearestThreat.daysToArrival} T. · ${nearestThreat.estimatedStrengthLabel}"
                    inboxCount > 0 && !(area == MainArea.TODAY && tabs[0] == 0) -> "$inboxCount Entscheidung${if (inboxCount == 1) "" else "en"} warten"
                    state.war.wounded.isNotEmpty() -> "${state.war.wounded.sumOf { it.soldiers }} Verwundete warten auf Versorgung"
                    EconomyEngine.production(state).net.food < 0 -> "Nahrung ${EconomyEngine.production(state).net.food}/Tag"
                    else -> null
                }
                if (warningText != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp).heightIn(min = 44.dp).clickable {
                            showMore = false
                            when {
                                state.crown.duel != null -> open(Spot.ARTS)
                                sagaScene != null -> dismissedScene = null
                                crownThreat != null -> open(Spot.FRONT)
                                nearestThreat != null || state.invasion != null -> showDefense = true
                                inboxCount > 0 -> open(Spot.INBOX)
                                state.war.wounded.isNotEmpty() -> open(Spot.HOSPITAL)
                                else -> open(Spot.CITY_VIEW)
                            }
                        },
                        color = Color(0xFF221719),
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = .28f)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(Danger, RoundedCornerShape(6.dp)))
                            Text(warningText, color = PaleGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                            Text("›", color = Gold, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!battleVisible) {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = Color.White.copy(alpha = .10f),
                unselectedIconColor = Muted,
                unselectedTextColor = Muted,
            )
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .92f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xF20E151D),
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                    shadowElevation = 16.dp,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, modifier = Modifier.height(70.dp)) {
                        val frontAlarm = state.crown.threats.isNotEmpty() || state.invasion != null
                        listOf(
                            MainArea.TODAY to Icons.Outlined.Home,
                            MainArea.REALM to Icons.Outlined.Crown,
                            MainArea.ARMY to Icons.Outlined.Shield,
                            MainArea.COURT to Icons.Outlined.FavoriteBorder,
                            MainArea.CITY to Icons.Outlined.LocationCity,
                        ).forEach { (target, icon) ->
                            val selected = !showMore && area == target
                            NavigationBarItem(
                                selected = selected,
                                onClick = { showMore = false; areaName = target.name },
                                icon = {
                                    BadgedBox(badge = {
                                        when {
                                            target == MainArea.TODAY && (inboxCount > 0 || sagaScene != null) -> Badge(containerColor = Danger) { Text("${inboxCount + if (sagaScene != null) 1 else 0}") }
                                            target == MainArea.ARMY && frontAlarm -> Badge(containerColor = Danger)
                                            target == MainArea.COURT && state.relationship.pendingEvent != null -> Badge(containerColor = Color(0xFFE58FB0))
                                        }
                                    }) { Icon(icon, null) }
                                },
                                label = { Text(target.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium) },
                                colors = itemColors,
                            )
                        }
                    }
                }
            }
            }
        },
    ) { padding ->
        if (showCensus) PopulationDialog(state, onState, onNotice) { showCensus = false }
        if (showDefense) DefenseDashboard(state, onState, onNotice) { showDefense = false }
        Box(Modifier.fillMaxSize().padding(padding)) {
            val duel = state.crown.duel
            val ctx = HubContext(state, onState, onNotice, ::open, onAdvanceDay) { dismissedScene = null }
            if (showMore) {
                MoreScreen(state, onMenu, onDelete, { showTutorial = true }, onState, onNotice, controller) { destination -> openLegacy(destination) }
            } else if (duel != null) {
                DuelPanel(state, duel) { result -> onState(result.state); onNotice(result.message) }
            } else if (battleVisible) {
                TacticalBattleScreen(state, onState, onNotice)
            } else androidx.compose.animation.Crossfade(targetState = area, animationSpec = androidx.compose.animation.core.tween(if (state.settings.animations) 220 else 0), label = "area") { shown ->
                AreaHub(shown, tabs[shown.ordinal], subs[shown.ordinal],
                    onTab = { t -> tabs = tabs.toMutableList().also { it[shown.ordinal] = t }; subs = subs.toMutableList().also { it[shown.ordinal] = 0 } },
                    onSub = { v -> subs = subs.toMutableList().also { it[shown.ordinal] = v } },
                    ctx = ctx)
            }
        }
    }

    // Dawn interstitial whenever a new day begins (skipped when a celebration takes the stage).
    var dawnDay by remember { mutableIntStateOf(state.day) }
    val celebration = state.crown.celebrations.firstOrNull()
    if (state.day > dawnDay && celebration == null && state.battleSession == null && sagaScene == null) {
        DawnOverlay(state, state.settings.animations) { dawnDay = state.day }
    } else if (state.day > dawnDay) {
        SideEffect { dawnDay = state.day }
    }
    if (celebration != null && state.crown.duel == null && state.battleSession?.isActive != true) {
        CelebrationOverlay(celebration, state.crown.celebrations.size, state.settings.animations) {
            onState(com.goldenunicorn.troopmanager.engine.CelebrationEngine.dismiss(state))
        }
    } else if (sagaScene != null && dismissedScene != sagaScene.key && state.crown.duel == null && state.battleSession == null) {
        SagaStageDialog(state, sagaScene, { result -> onState(result.state); onNotice(result.message) }) { dismissedScene = sagaScene.key }
    }

    GameSoundscape(state, if (battleVisible) "battle" else when (area) {
        MainArea.CITY -> "city"
        MainArea.COURT -> "court"
        MainArea.ARMY -> "military"
        MainArea.REALM -> "world"
        MainArea.TODAY -> "command"
    })

    if (showTutorial) {
        TutorialDialog(
            onFinish = { showTutorial = false; onState(GameEngine.markTutorialSeen(state)) },
            onSkip = { showTutorial = false; onState(GameEngine.markTutorialSeen(state)) },
        )
    }
}

private fun partnerPortraitKey(background: String): String = when (background) {
    "seraphine" -> "knight"
    "liang" -> "wall_guard"
    "ithiliel" -> "gold_elf"
    else -> "companion"
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}
