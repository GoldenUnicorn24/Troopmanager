package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goldenunicorn.troopmanager.audio.GameSoundscape

private enum class Screen(val label: String, val icon: String) {
    REALM("Reich", "♜"),
    CITY("Stadt", "⌂"),
    ARMY("Armee", "⚔"),
    WORLD("Welt", "◉"),
    COURT("Hof", "♛"),
}

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
            Text("v0.6 · Lebendige Reiche", color = PaleGold)
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
    var ironman by remember { mutableStateOf(false) }
    var portrait by remember { mutableStateOf<String?>(null) }
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
                                "330 Soldaten, viele Rekruten und schwere Ritter. Andere Kulturen kommen durch Bündnisse und Einwanderung."
                            Species.ELF ->
                                "Kleine Bevölkerung, starke Wald- und Goldelben. Menschenbündnisse folgen später."
                            Species.HALF_ELF ->
                                "Menschen, Waldelben, Goldelben und Mauerlegionen können sofort gemeinsam dienen."
                        },
                        color = Mist,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(ironman, { ironman = it }); Text("Ironman · ein fortlaufender Spielstand", color = Mist)
        }
        Spacer(Modifier.height(18.dp))
        GoldButton(
            "Reich gründen",
            { onCreated(GameEngine.newGame(name, age.toIntOrNull() ?: 23, species, portrait).let { it.copy(settings = it.settings.copy(ironman = ironman)) }) },
            Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Zurück")
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
    var screen by rememberSaveable { mutableStateOf(Screen.CITY) }
    var worldPage by rememberSaveable { mutableStateOf(0) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showMore || screen != Screen.CITY) {
        if (showMore) showMore = false else screen = Screen.CITY
    }
    var showTutorial by remember(state.tutorialSeen) { mutableStateOf(false) }
    Scaffold(
        containerColor = Ink,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.realm.settlementName,
                    color = PaleGold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (state.player.skillPoints > 0)
                    TextButton(
                        onClick = {
                            showMore = false
                            screen = Screen.COURT
                        }
                    ) {
                        Text("${state.player.skillPoints} Skillpunkte")
                    }
                IconButton(onClick = { showMore = !showMore }) {
                    Icon(Icons.Outlined.HelpOutline, "Hilfe und Menü", tint = Gold)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E1419)) {
                Screen.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == screen,
                        onClick = {
                            showMore = false
                            screen = tab
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    Screen.REALM -> Icons.Outlined.Castle
                                    Screen.CITY -> Icons.Outlined.LocationCity
                                    Screen.ARMY -> Icons.Outlined.Shield
                                    Screen.WORLD -> Icons.Outlined.Public
                                    Screen.COURT -> Icons.Outlined.Person
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = Ink,
                                selectedTextColor = PaleGold,
                                indicatorColor = Gold,
                                unselectedIconColor = Mist,
                                unselectedTextColor = Mist,
                            ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showMore) {
                MoreScreen(state, onMenu, onDelete, { showTutorial = true }, onState, onNotice, controller)
            } else if (
                state.battleSession != null && screen != Screen.CITY && screen != Screen.COURT
            ) {
                LiveBattleScreen(
                    state,
                    { next ->
                        if (next.battleSession == null) screen = Screen.WORLD
                        onState(next)
                    },
                    onNotice,
                )
            } else
                when (screen) {
                    Screen.REALM ->
                        RealmDashboard(
                            state,
                            onState,
                            onNotice,
                            { screen = Screen.CITY },
                            { screen = Screen.WORLD },
                            { screen = Screen.ARMY },
                            { screen = Screen.COURT },
                            onAdvanceDay,
                        )
                    Screen.CITY ->
                        CityScreen(
                            state,
                            onState,
                            onNotice,
                            onAdvanceDay,
                            onNavigate = { type ->
                                worldPage = if (type == BuildingType.EMBASSY) 2 else 0
                                screen = when (type) {
                                BuildingType.BARRACKS, BuildingType.STABLES, BuildingType.ARSENAL, BuildingType.HOSPITAL -> Screen.ARMY
                                BuildingType.PALACE, BuildingType.ACADEMY -> Screen.COURT
                                BuildingType.EMBASSY, BuildingType.WALL, BuildingType.TOWER -> Screen.WORLD
                                else -> Screen.CITY
                            } },
                        )
                    Screen.ARMY -> ArmyHubScreen(state, onState, onNotice)
                    Screen.WORLD -> WorldHubScreen(state, onState, onNotice, worldPage)
                    Screen.COURT -> CourtHubScreen(state, onState, onNotice)
                }
        }
    }

    GameSoundscape(state, if (!showMore && state.battleSession != null && screen != Screen.CITY && screen != Screen.COURT) "battle" else screen.name.lowercase())

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
