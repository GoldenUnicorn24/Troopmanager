package com.goldenunicorn.troopmanager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.goldenunicorn.troopmanager.data.SaveRepository
import com.goldenunicorn.troopmanager.model.*

/** Debug-only harness: renders the real app shell on any screen with a rich mid-game realm for screenshot tours. */
class TourActivity : ComponentActivity() {
    companion object {
        var page by mutableStateOf("spot:INBOX")
        var latestState: GameState? = null
        val rich: GameState by lazy { TourFixture.richState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = GameViewModel(SaveRepository(this))
        setContent {
            MaterialTheme(typography = AppTypography, colorScheme = darkColorScheme(primary = Gold, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Mist)) {
                Box(Modifier.fillMaxSize().background(Ink).testTag("tour_root")) {
                    val current = page
                    key(current) {
                        var state by remember { mutableStateOf(when {
                            current.startsWith("battle") -> TourFixture.battle(rich)
                            current == "scene" -> rich.copy(crown = rich.crown.copy(saga = rich.crown.saga.copy(pendingScene =
                                SagaScene("march_night", "alina", "Mira hat einen Plan", "Marsch nach Rabenfurt",
                                    "Mira breitet eine Karte im Zelt aus. „Sie erwarten uns am Tag. Wenn wir heute Nacht angreifen, sind sie halb im Schlaf. Ich führe die Vorhut.“",
                                    listOf(SagaOption("night", "Nachtangriff mit Mira an der Spitze", "Viel stärker · sie riskiert eine Verwundung"),
                                        SagaOption("day", "Zu gefährlich – wir greifen bei Tag an", "Kein Effekt"))))))
                            current == "celebration" -> com.goldenunicorn.troopmanager.engine.CelebrationEngine.add(rich, CelebrationKind.WONDER,
                                "Königliche Kornkammer", "Nach 7 Tagen ist das Werk vollendet. Nahrungslager ×3, Proviant für Feldzüge halbiert.")
                            else -> rich
                        }) }
                        SideEffect { latestState = state }
                        when {
                            current == "menu" -> MainMenu(GameUiState(inMenu = true, hasSave = true), controller)
                            current == "create" -> CharacterCreation({}, {})
                            current.startsWith("spot:") -> GameShell(state, { state = it }, {}, {}, {}, {}, controller,
                                initialSpot = Spot.valueOf(current.removePrefix("spot:")))
                            else -> GameShell(state, { state = it }, {}, {}, {}, {}, controller)
                        }
                    }
                }
            }
        }
    }
}
