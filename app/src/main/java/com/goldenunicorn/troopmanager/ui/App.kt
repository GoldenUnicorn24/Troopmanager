package com.goldenunicorn.troopmanager.ui

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
import com.goldenunicorn.troopmanager.engine.EconomyEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
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
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, systemDensity.fontScale * settings.textScale)) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Gold, onPrimary = Ink, secondary = Blue,
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
private fun MainMenu(ui: GameUiState, controller: GameViewModel) {
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
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Bottom) {
            Text("REALM OF THE", color = PaleGold, fontSize = 18.sp, letterSpacing = 3.sp)
            Text("LAST WALL", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
            Text(
                "Vom jungen Grenzherrn zum Herrscher einer mächtigen Festung.",
                color = Mist,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp),
            )
            Text("v${com.goldenunicorn.troopmanager.BuildConfig.VERSION_NAME} · lebendige Grenze & Reich", color = PaleGold)
            SaveSlotsPanel(ui, controller)
            if (ui.hasSave) {
                GoldButton("Spiel fortsetzen", controller::continueGame, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            var confirmOverwrite by remember { mutableStateOf(false) }
            if (confirmOverwrite) AlertDialog(onDismissRequest = { confirmOverwrite = false },
                title = { Text("Neues Reich in Platz ${ui.activeSlot}?") },
                text = { Text("Die Kampagne in diesem Platz wird beim Gründen ersetzt. Wähle einen leeren Platz, um sie zu behalten.") },
                confirmButton = { TextButton(onClick = { confirmOverwrite = false; controller.newGame() }) { Text("Neues Reich") } },
                dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Abbrechen") } })
            OutlinedButton(onClick = { if (ui.hasSave) confirmOverwrite = true else controller.newGame() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("Neues Reich", fontWeight = FontWeight.Bold, color = PaleGold)
            }
            Text(
                "Offline · keine Echtgeldkäufe · lokaler Spielstand",
                color = Color(0xFFA8B2B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CharacterCreation(onCreated: (GameState) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("Leon") }
    var age by remember { mutableStateOf("23") }
    var species by remember { mutableStateOf(Species.HALF_ELF) }
    var startingCultures by remember(species) { mutableStateOf<Set<Culture>>(emptySet()) }
    var ironman by remember { mutableStateOf(false) }
    var portrait by remember { mutableStateOf<String?>(null) }
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

        Surface(
            modifier =
                Modifier.fillMaxWidth().padding(top = 12.dp).clickable {
                    picker.launch(arrayOf("image/*"))
                },
            color = Panel,
            shape = RoundedCornerShape(18.dp),
        ) {
            if (portrait != null) {
                AsyncImage(
                    portrait,
                    null,
                    Modifier.fillMaxWidth().height(230.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painterResource(R.drawable.portrait_knight),
                    null,
                    Modifier.fillMaxWidth().height(230.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        OutlinedTextField(
            name,
            { name = it.take(24) },
            label = { Text("Name") },
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
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { species = option },
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
                    ).let { it.copy(settings = it.settings.copy(ironman = ironman)) }
                )
            },
            enabled = remainingAttributePoints == 0 && startingCultures.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
        ) {
            Text(
                when {
                    startingCultures.isEmpty() -> "STARTKULTUR AUSWÄHLEN"
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
private fun GameShell(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onMenu: () -> Unit,
    onDelete: () -> Unit,
    onAdvanceDay: () -> Unit,
    controller: GameViewModel,
) {
    var screen by rememberSaveable { mutableStateOf(GameDestination.COMMAND) }
    var worldPage by rememberSaveable { mutableStateOf(0) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showCensus by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showMore || screen != GameDestination.COMMAND) {
        if (showMore) showMore = false else screen = GameDestination.COMMAND
    }
    var showTutorial by remember(state.tutorialSeen) { mutableStateOf(false) }
    Scaffold(
        containerColor = Ink,
        topBar = {
            Column(
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
                        Text(
                            state.realm.settlementName.uppercase(),
                            color = Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp,
                            maxLines = 1,
                        )
                        Text(
                            "Tag ${state.day} · ${state.title}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    if (state.player.skillPoints > 0) {
                        Surface(
                            color = Gold.copy(alpha = .13f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .25f)),
                            modifier = Modifier.clickable {
                                showMore = false
                                screen = GameDestination.CHARACTER
                            },
                        ) {
                            Text(
                                "${state.player.skillPoints} SKILL",
                                color = PaleGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            )
                        }
                    }
                    IconButton(onClick = { showTutorial = true }) {
                        Icon(Icons.Outlined.HelpOutline, "Hilfe", tint = Muted)
                    }
                    IconButton(onClick = { showMore = !showMore }) {
                        Icon(Icons.Outlined.MoreVert, "Menü", tint = Gold)
                    }
                }

                ResourceStrip(state.resources, EconomyEngine.production(state).net)

                val pendingCampaignDecision = state.campaign.pendingDecision
                val nearestThreat = state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }
                val warningText = when {
                    pendingCampaignDecision != null ->
                        "${pendingCampaignDecision.title} · bis Tag ${pendingCampaignDecision.expiresDay}"
                    nearestThreat != null ->
                        "${nearestThreat.name} · ${nearestThreat.daysToArrival} T. · ${nearestThreat.estimatedStrengthLabel}"
                    state.war.wounded.isNotEmpty() ->
                        "${state.war.wounded.sumOf { it.soldiers }} Verwundete warten auf Versorgung"
                    EconomyEngine.production(state).net.food < 0 ->
                        "Nahrung ${EconomyEngine.production(state).net.food}/Tag"
                    else -> null
                }
                if (warningText != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).clickable {
                            showMore = false
                            screen = when {
                                pendingCampaignDecision != null -> GameDestination.DECISIONS
                                nearestThreat != null -> GameDestination.FRONTIER
                                state.war.wounded.isNotEmpty() -> GameDestination.HOSPITAL
                                else -> GameDestination.CITY
                            }
                        },
                        color = Color(0xFF221719),
                        shape = RoundedCornerShape(13.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(alpha = .28f)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(Danger, RoundedCornerShape(6.dp)))
                            Text(
                                warningText,
                                color = PaleGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text("›", color = Gold, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        bottomBar = {
            val realmSelected = screen in setOf(GameDestination.COMMAND, GameDestination.DECISIONS, GameDestination.PALACE)
            val armySelected = screen in setOf(GameDestination.MILITARY, GameDestination.FRONTIER, GameDestination.HOSPITAL, GameDestination.MISSIONS)
            val moreSelected = showMore || (!realmSelected && !armySelected && screen !in setOf(GameDestination.CITY, GameDestination.WORLD))
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = Color.White.copy(alpha = .10f),
                unselectedIconColor = Muted,
                unselectedTextColor = Muted,
            )

            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Ink.copy(alpha = .92f))
                        )
                    )
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
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(68.dp),
                    ) {
                        NavigationBarItem(
                            selected = !showMore && realmSelected,
                            onClick = { showMore = false; screen = GameDestination.COMMAND },
                            icon = { Icon(Icons.Outlined.Castle, null) },
                            label = { Text("Reich", fontSize = 9.sp, fontWeight = if (!showMore && realmSelected) FontWeight.Black else FontWeight.Medium) },
                            colors = itemColors,
                        )
                        NavigationBarItem(
                            selected = !showMore && screen == GameDestination.CITY,
                            onClick = { showMore = false; screen = GameDestination.CITY },
                            icon = { Icon(Icons.Outlined.LocationCity, null) },
                            label = { Text("Stadt", fontSize = 9.sp, fontWeight = if (!showMore && screen == GameDestination.CITY) FontWeight.Black else FontWeight.Medium) },
                            colors = itemColors,
                        )
                        NavigationBarItem(
                            selected = !showMore && armySelected,
                            onClick = { showMore = false; screen = GameDestination.MILITARY },
                            icon = { Icon(Icons.Outlined.Shield, null) },
                            label = { Text("Heer", fontSize = 9.sp, fontWeight = if (!showMore && armySelected) FontWeight.Black else FontWeight.Medium) },
                            colors = itemColors,
                        )
                        NavigationBarItem(
                            selected = !showMore && screen == GameDestination.WORLD,
                            onClick = { showMore = false; screen = GameDestination.WORLD },
                            icon = { Icon(Icons.Outlined.Public, null) },
                            label = { Text("Welt", fontSize = 9.sp, fontWeight = if (!showMore && screen == GameDestination.WORLD) FontWeight.Black else FontWeight.Medium) },
                            colors = itemColors,
                        )
                        NavigationBarItem(
                            selected = moreSelected,
                            onClick = { showMore = true },
                            icon = { Icon(Icons.Outlined.MoreVert, null) },
                            label = { Text("Mehr", fontSize = 9.sp, fontWeight = if (moreSelected) FontWeight.Black else FontWeight.Medium) },
                            colors = itemColors,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (showCensus) PopulationDialog(state, onState, onNotice) { showCensus = false }
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showMore) {
                MoreScreen(state, onMenu, onDelete, { showTutorial = true }, onState, onNotice, controller) { destination ->
                    showMore = false
                    screen = destination
                }
            } else if (
                state.battleSession != null && screen != GameDestination.CITY && screen != GameDestination.COURT
            ) {
                LiveBattleScreen(
                    state,
                    { next ->
                        if (next.battleSession == null) screen = GameDestination.WORLD
                        onState(next)
                    },
                    onNotice,
                )
            } else
                when (screen) {
                    GameDestination.COMMAND -> CommandCenterScreen(state, onState, onNotice, { screen = it }, onAdvanceDay)
                    GameDestination.DECISIONS -> CommandCenterScreen(state, onState, onNotice, { screen = it }, onAdvanceDay, initialPage = 1)
                    GameDestination.CITY ->
                        CityScreen(
                            state,
                            onState,
                            onNotice,
                            onAdvanceDay,
                            onNavigate = { type ->
                                worldPage = if (type == BuildingType.EMBASSY) 2 else 0
                                screen = when (type) {
                                BuildingType.BARRACKS, BuildingType.STABLES, BuildingType.ARSENAL, BuildingType.HOSPITAL -> GameDestination.MILITARY
                                BuildingType.PALACE -> GameDestination.PALACE
                                BuildingType.ACADEMY -> GameDestination.RESEARCH
                                BuildingType.EMBASSY -> GameDestination.WORLD
                                BuildingType.WALL, BuildingType.TOWER -> GameDestination.FRONTIER
                                else -> GameDestination.CITY
                            } },
                        )
                    GameDestination.MILITARY -> ArmyHubScreen(state, onState, onNotice)
                    GameDestination.WORLD -> WorldHubScreen(state, onState, onNotice, worldPage)
                    GameDestination.COURT -> CourtHubScreen(state, onState, onNotice)
                    GameDestination.RULERS -> RulerPairScreen(state, onState, onNotice, { screen = GameDestination.COUNCIL })
                    GameDestination.FAMILY -> FamilyScreen(state, onState, onNotice)
                    GameDestination.CHARACTER -> CharacterHubScreen(state, onState, onNotice)
                    GameDestination.FRONTIER -> FrontierScreen(state, onState, onNotice)
                    GameDestination.HOSPITAL -> ArmyHubScreen(state, onState, onNotice, 3)
                    GameDestination.MISSIONS -> ArmyHubScreen(state, onState, onNotice, 2)
                    GameDestination.COUNCIL -> CouncilScreen(state, onState, onNotice)
                    GameDestination.PALACE -> PalaceHubScreen(state) { screen = it }
                    GameDestination.JOURNAL -> QuestJournalScreen(state) { screen = it }
                    GameDestination.CHRONICLE -> ChronicleScreen(state)
                    GameDestination.RESEARCH -> ResearchPanel(state, onState, onNotice)
                }
        }
    }

    GameSoundscape(state, if (!showMore && state.battleSession != null && screen != GameDestination.CITY && screen != GameDestination.COURT) "battle" else screen.name.lowercase())

    if (showTutorial) {
        TutorialDialog(
            onFinish = {
                showTutorial = false
                onState(GameEngine.markTutorialSeen(state))
            },
            onSkip = {
                showTutorial = false
                onState(GameEngine.markTutorialSeen(state))
            },
        )
    }
}
