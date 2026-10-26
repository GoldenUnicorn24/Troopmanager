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
import androidx.compose.ui.platform.testTag
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
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, systemDensity.fontScale * settings.textScale)) {
        MaterialTheme(typography = AppTypography, colorScheme = darkColorScheme(primary = Gold, onPrimary = Ink, secondary = Blue,
            background = Ink, surface = Panel, onSurface = Mist)) {
            Box(Modifier.fillMaxSize().background(Ink)) {
                when {
                    ui.inMenu -> MainMenu(ui, controller)
                    state == null -> CharacterCreation(controller::create, controller::menu)
                    // Project Rebirth: landless characters live in the personal layer.
                    !state.life.landed -> LifeShell(state, { controller.update(state, it) }, { controller.notice(it) }, controller::menu)
                    // Rulers can visit a tavern too; while inside, the walkable scene replaces the shell.
                    state.life.skirmish != null -> SkirmishScreen(state, { controller.update(state, it) }, { controller.notice(it) })
                    state.life.place == LifePlace.MARKET -> MarketScreen(state, { controller.update(state, it) }, { controller.notice(it) })
                    state.life.place == LifePlace.CASTLE -> CastleScreen(state, { controller.update(state, it) }, { controller.notice(it) })
                    state.life.place == LifePlace.BARRACKS -> BarracksScreen(state, { controller.update(state, it) }, { controller.notice(it) })
                    state.life.place == LifePlace.TAVERN -> TavernScreen(state, { controller.update(state, it) }, { controller.notice(it) }, onExit = {})
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
                ModernPill("Offline Fantasy RPG", ModernBlue, filled = true)
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
                "Beginne als Söldner, Händler oder Grenzherr. Triff Menschen, kämpfe, liebe – und vielleicht gründest du ein Königreich.",
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
                    title = { Text("Neues Spiel in Platz ${ui.activeSlot}?") },
                    text = { Text("Der Spielstand in diesem Platz wird beim Start ersetzt. Wähle einen leeren Platz, um ihn zu behalten.") },
                    confirmButton = { TextButton(onClick = { confirmOverwrite = false; controller.newGame() }) { Text("Neues Spiel") } },
                    dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Abbrechen") } },
                )
                OutlinedButton(
                    onClick = { if (ui.hasSave) confirmOverwrite = true else controller.newGame() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .13f)),
                ) {
                    Text("NEUES SPIEL", fontWeight = FontWeight.Black, color = Color.White, fontSize = 11.sp)
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

/** v2.2 Project Rebirth: the new start wizard (modes, origin, editor, companion question). */
@Composable
internal fun CharacterCreation(onCreated: (GameState) -> Unit, onBack: () -> Unit) {
    NewGameWizard(onCreated, onBack)
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
                    Box {
                        var townMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { townMenu = true }, modifier = Modifier.testTag("town_menu")) { Icon(Icons.Outlined.Restaurant, "In die Stadt gehen", tint = Gold) }
                        DropdownMenu(expanded = townMenu, onDismissRequest = { townMenu = false }) {
                            val e = com.goldenunicorn.troopmanager.engine.LifeEngine.ensurePeople(state)
                            listOf<Pair<String, () -> com.goldenunicorn.troopmanager.engine.LifeEngine.Result>>(
                                "Taverne" to { com.goldenunicorn.troopmanager.engine.TavernEngine.enter(e) },
                                "Markt" to { com.goldenunicorn.troopmanager.engine.MarketEngine.enter(e) },
                                "Kaserne" to { com.goldenunicorn.troopmanager.engine.BarracksEngine.enter(e) },
                                "Burghof" to { com.goldenunicorn.troopmanager.engine.CastleEngine.enter(e) },
                            ).forEach { (label, go) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { townMenu = false; val r = go(); onState(r.state); onNotice(r.message) })
                            }
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
