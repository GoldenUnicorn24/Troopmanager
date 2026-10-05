package com.goldenunicorn.troopmanager.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.goldenunicorn.troopmanager.engine.BattleEngine
import com.goldenunicorn.troopmanager.model.*

/** Non-exported debug harness renders the real battle screen with bounded, deterministic fixtures. */
class BattlePreviewActivity : ComponentActivity() {
    data class Viewport(val width: Int = 320, val height: Int = 568, val fontScale: Float = 1f, val pending: Boolean = false, val page: String = "battle")
    companion object {
        var viewport by mutableStateOf(Viewport())
        var latestState: GameState? = null
        var latestNotice: String = ""
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val view = viewport
            SideEffect { requestedOrientation = if (view.width > view.height) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            var state by remember(view.pending, view.page) { mutableStateOf(fixture(view.pending)) }
            SideEffect { latestState = state }
            fun update(next: GameState) {
                state = next
                latestState = next
                Log.i("BattlePreview", "State: battle=${next.battleSession?.plan?.doctrine}, defense=${next.defensePlan.doctrine}")
            }
            fun notice(message: String) {
                latestNotice = message
                Log.i("BattlePreview", message)
            }
            val density = LocalDensity.current
            Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
                Box(Modifier.requiredSize(view.width.dp, view.height.dp)) {
                    CompositionLocalProvider(LocalDensity provides Density(density.density, view.fontScale)) {
                        MaterialTheme {
                            when (view.page) {
                                "army" -> Column(Modifier.fillMaxSize()) {
                                    ResourceStrip(state.resources)
                                    Box(Modifier.weight(1f)) { ArmyHubScreen(state.copy(battleSession = null), ::update, ::notice) }
                                }
                                "defense" -> DefenseDashboard(state.copy(battleSession = null), ::update, ::notice) {}
                                else -> LiveBattleScreen(state, ::update, ::notice)
                            }
                        }
                    }
                }
            }
        }
    }
    private fun fixture(pending: Boolean): GameState {
        val base = GameState(player = CharacterProfile("Mira"),
            armyPools = listOf(ArmyUnitPool(UnitType.HUMAN_SWORD, 200), ArmyUnitPool(UnitType.HUMAN_ARCHER, 100)),
            settings = GameSettings(animations = false))
        val started = BattleEngine.start(base, EnemyType.URUK, Tactic.FORTIFY, seed = 90, enemyStrength = 300).state
        return if (!pending) started else started.copy(battleSession = started.battleSession!!.copy(pendingEvent =
            BattleEvent("Das Tor steht unter Druck", "Die Rammbockbesatzung rückt vor.", BattleSection.CENTER,
                listOf(BattleDecision.HOLD, BattleDecision.SEND_RESERVE, BattleDecision.HOLD_GATE))))
    }
}
