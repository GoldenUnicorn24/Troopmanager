package com.goldenunicorn.troopmanager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
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
        val life: GameState by lazy { TourFixture.lifeState() }
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
                            current == "market" -> TourFixture.roadState()
                            current == "barracks" -> com.goldenunicorn.troopmanager.engine.BarracksEngine.enter(TourFixture.roadState().let { it.copy(life = it.life.copy(timeSlot = TimeSlot.NOON)) }).state
                            current == "castle" -> com.goldenunicorn.troopmanager.engine.CastleEngine.let { c ->
                                val base = TourFixture.roadState().let { it.copy(day = 14, life = it.life.copy(timeSlot = TimeSlot.NOON, renown = 40)) }
                                c.askForTask(c.enter(base).state).state
                            }
                            current == "skirmish" -> TourFixture.skirmishState()
                            current == "party" -> TourFixture.roadState().let { it.copy(life = it.life.copy(place = LifePlace.TOWN)) }
                            current == "captive" -> life.copy(life = life.life.copy(captiveUntil = life.day + 2, captor = "Wegelagerer", place = LifePlace.AWAY, wound = 40))
                            current.startsWith("life") -> life
                            current.startsWith("tavern") -> life.copy(life = life.life.copy(place = LifePlace.TAVERN))
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
                            current.startsWith("wizard-") -> current.removePrefix("wizard-").toInt().let { step ->
                                NewGameWizard({}, {}, initialStep = step, initialCompanion = if (step >= 4) CompanionChoice.FULL else null)
                            }
                            current.startsWith("life-") -> LifeShell(state, { state = it }, {}, {}, initialTab = current.removePrefix("life-").toInt())
                            current == "tavern" -> LifeShell(state, { state = it }, {}, {})
                            current == "market" || current == "skirmish" || current == "captive" || current == "barracks" || current == "castle" -> LifeShell(state, { state = it }, {}, {})
                            current == "party" -> LifeShell(state, { state = it }, {}, {}, initialTab = 2)
                            current == "tavern-talk" -> LifeShell(state, { state = it }, {}, {}, initialTalk = "companion")
                            current == "portraits" -> PortraitComparison(life)
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

/** Old drawn portrait next to the new renderer, for the art comparison screenshot. */
@androidx.compose.runtime.Composable
private fun PortraitComparison(state: GameState) {
    val player = state.life.appearance!!
    val mira = state.life.companion!!
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().background(Ink).padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Text("ALT (v2.1 DrawnPortrait)  ·  NEU (Rebirth-Renderer)", color = Gold)
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            DrawnPortrait(player.toPortraitSpec(), Modifier.weight(1f).aspectRatio(1f))
            PaintedPortrait(player, Gender.MALE, Modifier.weight(1f).aspectRatio(.8f), Framing.BUST)
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            DrawnPortrait(mira.appearance.toPortraitSpec(), Modifier.weight(1f).aspectRatio(1f))
            PaintedPortrait(mira.appearance, Gender.FEMALE, Modifier.weight(1f).aspectRatio(.8f), Framing.BUST)
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            PaintedPortrait(player, Gender.MALE, Modifier.weight(1f).aspectRatio(.62f), Framing.FULL)
            PaintedPortrait(mira.appearance, Gender.FEMALE, Modifier.weight(1f).aspectRatio(.62f), Framing.FULL)
            PaintedPortrait(mira.appearance, Gender.FEMALE, Modifier.weight(1f).aspectRatio(.62f), Framing.HEAD)
        }
    }
}
