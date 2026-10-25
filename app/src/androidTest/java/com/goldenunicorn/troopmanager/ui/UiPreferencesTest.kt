package com.goldenunicorn.troopmanager.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldenunicorn.troopmanager.engine.BattleEngine
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises settings and accessible actions through the real Compose controls. */
@RunWith(AndroidJUnit4::class)
class UiPreferencesTest {
    @get:Rule val rule = createAndroidComposeRule<BattlePreviewActivity>()

    private class RecordedHaptics : HapticFeedback {
        var calls = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { calls++ }
    }

    private fun show(content: @Composable () -> Unit) {
        rule.runOnIdle { rule.activity.setContent { MaterialTheme { content() } } }
        rule.waitForIdle()
    }

    @Test fun goldButtonUsesTheCurrentHapticPreferenceWithoutSuppressingItsAction() {
        val feedback = RecordedHaptics()
        var settings by mutableStateOf(GameSettings(haptics = false))
        var clicks = 0
        show {
            CompositionLocalProvider(LocalHapticFeedback provides feedback,
                LocalGameHapticsEnabled provides settings.haptics) {
                GoldButton("Aktion", { clicks++ })
            }
        }
        rule.onNodeWithText("AKTION").performClick()
        rule.runOnIdle { assertEquals(1, clicks); assertEquals(0, feedback.calls); settings = settings.copy(haptics = true) }
        rule.onNodeWithText("AKTION").performClick()
        rule.runOnIdle { assertEquals(2, clicks); assertEquals(1, feedback.calls); settings = settings.copy(haptics = false) }
        rule.onNodeWithText("AKTION").performClick()
        rule.runOnIdle { assertEquals(3, clicks); assertEquals(1, feedback.calls) }
    }

    @Test fun tacticalPlanDoesNotVibrateWhenHapticsAreDisabled() {
        val feedback = RecordedHaptics()
        var state by mutableStateOf(BattleEngine.start(
            GameEngine.newGame("Mira", 24, Species.HUMAN, null).copy(settings = GameSettings(haptics = false, animations = false)),
            EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300).state)
        show {
            CompositionLocalProvider(LocalHapticFeedback provides feedback) {
                TacticalBattleScreen(state, { state = it }, {})
            }
        }
        rule.onNodeWithTag("plan_HOLD_WALL").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals("HOLD_WALL", state.battleSession!!.tacticalPlan)
            assertEquals(0, feedback.calls)
        }
    }

    @Test fun quickResolutionPublishesOneCompletedBattleFromTheBackgroundCalculation() {
        var state by mutableStateOf(BattleEngine.start(
            GameEngine.newGame("Mira", 24, Species.HUMAN, null).copy(settings = GameSettings(animations = false)),
            EnemyType.ORC, Tactic.FORTIFY, seed = 90, enemyStrength = 300).state)
        var updates = 0
        show { TacticalBattleScreen(state, { state = it; updates++ }, {}) }
        rule.onNodeWithText("Schnell entscheiden (ohne Zuschauen)").performScrollTo().performClick()
        rule.waitUntil(15_000) { state.battleSession?.isActive == false }
        rule.runOnIdle { assertEquals(1, updates) }
    }

    @Test fun subTabsHaveFortyEightDpTouchTargetsAndRemainSelectable() {
        var selected = -1
        show { Column { SubSwitch(listOf("Erste", "Zweite"), 0, { selected = it }) } }
        val density = rule.activity.resources.displayMetrics.density
        listOf("Erste", "Zweite").forEach { text ->
            val bounds = rule.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$text must have a 48dp touch target", bounds.height >= 48 * density - 1)
        }
        rule.onNodeWithText("Zweite").performClick()
        rule.runOnIdle { assertEquals(1, selected) }
    }

    @Test fun everyProvinceCanBeSelectedThroughTheMapsAccessibleActions() {
        val state = GameEngine.newGame("Mira", 24, Species.HUMAN, null).copy(settings = GameSettings(animations = false))
        var selected by mutableStateOf<String?>(null)
        show { CrownMap(state, selected) { selected = it } }
        val map = rule.onNodeWithContentDescription("Karte der Kronlande mit ${state.crown.provinces.size} Provinzen")
        val actions = map.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertNotNull("Canvas provinces need accessible selection actions", actions)
        assertEquals(state.crown.provinces.size, actions!!.size)
        val province = state.crown.provinces.last()
        val action = actions.single { it.label == "${province.name} auswählen" }
        rule.runOnIdle { assertTrue(action.action()); assertEquals(province.id, selected) }
        map.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${province.name}, ${province.ownerLabel}"))
    }
}
