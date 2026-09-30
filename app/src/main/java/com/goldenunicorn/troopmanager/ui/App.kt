package com.goldenunicorn.troopmanager.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.goldenunicorn.troopmanager.model.GameState
import com.goldenunicorn.troopmanager.model.Species

private enum class Screen(val label: String, val icon: String) {
    REALM("Reich", "♜"),
    ARMY("Armee", "⚔"),
    WORLD("Welt", "◉"),
    COURT("Hof", "♛"),
    MORE("Mehr", "?")
}

@Composable
fun RealmGameApp(saves: SaveRepository) {
    var state by remember { mutableStateOf<GameState?>(null) }
    var inMenu by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Ink,
            secondary = Blue,
            background = Ink,
            surface = Panel,
            onSurface = Mist
        )
    ) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            when {
                inMenu -> MainMenu(
                    hasSave = saves.hasSave(),
                    onContinue = {
                        state = saves.load()
                        if (state != null) inMenu = false
                    },
                    onNew = {
                        state = null
                        inMenu = false
                    }
                )

                state == null -> CharacterCreation(
                    onCreated = {
                        state = it
                        saves.save(it)
                    },
                    onBack = { inMenu = true }
                )

                else -> GameShell(
                    state = state!!,
                    onState = {
                        state = it
                        saves.save(it)
                    },
                    onNotice = { notice = it },
                    onMenu = {
                        saves.save(state!!)
                        inMenu = true
                    },
                    onDelete = {
                        saves.delete()
                        state = null
                        inMenu = true
                    }
                )
            }

            notice?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp, start = 18.dp, end = 18.dp),
                    color = Color(0xEE24303A),
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { notice = null }.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
                LaunchedEffect(text) {
                    kotlinx.coroutines.delay(2300)
                    notice = null
                }
            }
        }
    }

    if (showTutorial) {
        TutorialDialog(
            onFinish = {
                showTutorial = false
                onState(GameEngine.markTutorialSeen(state))
            },
            onSkip = {
                showTutorial = false
                onState(GameEngine.markTutorialSeen(state))
            }
        )
    }
}

@Composable
private fun MainMenu(hasSave: Boolean, onContinue: () -> Unit, onNew: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.splash_fortress),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0xF2070A0D)))))
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
            Text("REALM OF THE", color = PaleGold, fontSize = 18.sp, letterSpacing = 3.sp)
            Text("LAST WALL", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
            Text(
                "Vom unbekannten Soldaten zum Herrscher einer gigantischen Festung.",
                color = Mist,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp)
            )
            if (hasSave) {
                GoldButton("Spiel fortsetzen", onContinue, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("Neues Reich", fontWeight = FontWeight.Bold, color = PaleGold)
            }
            Text(
                "Offline · keine Echtgeldkäufe · lokaler Spielstand",
                color = Color(0xFFA8B2B8),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun CharacterCreation(onCreated: (GameState) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("Leon") }
    var age by remember { mutableStateOf("23") }
    var species by remember { mutableStateOf(Species.HALF_ELF) }
    var portrait by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            portrait = uri.toString()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("DEIN URSPRUNG", color = Gold, fontSize = 13.sp, letterSpacing = 2.sp)
        Text("Erschaffe den Herrscher", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Du beginnst landlos. Alles danach musst du dir verdienen.", color = Mist, modifier = Modifier.padding(vertical = 8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { picker.launch(arrayOf("image/*")) },
            color = Panel,
            shape = RoundedCornerShape(18.dp)
        ) {
            if (portrait != null) {
                AsyncImage(portrait, null, Modifier.fillMaxWidth().height(230.dp), contentScale = ContentScale.Crop)
            } else {
                Image(painterResource(R.drawable.portrait_knight), null, Modifier.fillMaxWidth().height(230.dp), contentScale = ContentScale.Crop)
            }
        }

        OutlinedTextField(
            name,
            { name = it.take(24) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        )
        OutlinedTextField(
            age,
            { age = it.filter(Char::isDigit).take(2) },
            label = { Text("Alter") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )

        SectionTitle("Volk")
        Species.entries.forEach { option ->
            val selected = option == species
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { species = option },
                color = if (selected) Color(0xFF27313A) else Panel,
                shape = RoundedCornerShape(14.dp),
                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Gold) else null
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(option.label, color = if (selected) PaleGold else Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        when (option) {
                            Species.HUMAN -> "Viele Rekruten, Ritter und Mauer-Korps. Elben werden später diplomatisch freigeschaltet."
                            Species.ELF -> "Kleine Bevölkerung, starke Wald- und Goldelben. Menschenbündnisse folgen später."
                            Species.HALF_ELF -> "Menschen, Waldelben, Goldelben und Mauerlegionen können sofort gemeinsam dienen."
                        },
                        color = Mist,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        GoldButton(
            "Karriere beginnen",
            {
                onCreated(GameEngine.newGame(name, age.toIntOrNull() ?: 23, species, portrait))
            },
            Modifier.fillMaxWidth()
        )
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Zurück") }
    }
}

@Composable
private fun GameShell(
    state: GameState,
    onState: (GameState) -> Unit,
    onNotice: (String) -> Unit,
    onMenu: () -> Unit,
    onDelete: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.REALM) }
    var showTutorial by remember(state.tutorialSeen) { mutableStateOf(!state.tutorialSeen) }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E1419)) {
                Screen.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == screen,
                        onClick = { screen = tab },
                        icon = { Text(tab.icon, fontSize = 19.sp) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink,
                            selectedTextColor = PaleGold,
                            indicatorColor = Gold,
                            unselectedIconColor = Mist,
                            unselectedTextColor = Mist
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.REALM -> RealmScreen(state, onState, onNotice)
                Screen.ARMY -> ArmyScreen(state, onState, onNotice)
                Screen.WORLD -> WorldScreen(state, onState, onNotice)
                Screen.COURT -> CourtScreen(state, onState, onNotice)
                Screen.MORE -> MoreScreen(
                    state = state,
                    onMenu = onMenu,
                    onDelete = onDelete,
                    onReplayTutorial = { showTutorial = true }
                )
            }
        }
    }
}
