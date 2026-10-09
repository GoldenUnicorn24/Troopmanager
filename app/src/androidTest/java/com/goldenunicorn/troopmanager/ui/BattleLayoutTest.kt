package com.goldenunicorn.troopmanager.ui

import android.content.res.Configuration
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BattleLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<BattlePreviewActivity>()
    @Before fun reset() { rule.runOnIdle { BattlePreviewActivity.viewport = BattlePreviewActivity.Viewport() } }
    private fun viewport(width: Int, height: Int, scale: Float = 1f, pending: Boolean = false,
        scenario: String = "default", animations: Boolean = false) {
        rule.runOnIdle { BattlePreviewActivity.viewport = BattlePreviewActivity.Viewport(width, height, scale, pending,
            scenario = scenario, animations = animations) }
        val orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == orientation }
        rule.waitForIdle()
        if (rule.onAllNodesWithTag("battle_plan_panel").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("battle_plan_panel").performScrollToNode(hasTestTag("battle_plan_apply"))
            rule.onNodeWithTag("battle_plan_apply").performClick()
            rule.waitForIdle()
        }
    }
    private fun verifyFieldAndTouchTargets(name: String) {
        saveScreenshot(name)
        val root = rule.onNodeWithTag("battle_screen").fetchSemanticsNode().boundsInRoot
        val field = rule.onNodeWithTag("battle_field").fetchSemanticsNode().boundsInRoot
        assertTrue("The battlefield must occupy at least 52% of the viewport: ${field.height/root.height}", field.height / root.height >= .52f)
        val density = rule.activity.resources.displayMetrics.density
        fun verifyText(tag: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Text $tag must be laid out", layouts.isNotEmpty())
            // Paragraph width includes the available constraint, while Text can wrap its box
            // to the shorter content. Check rendered line extents and allow pixel rounding.
            assertTrue("Text $tag must not be clipped: ${layouts.map { "${it.size} / ${it.multiParagraph.width}×${it.multiParagraph.height}" }}",
                layouts.all { !it.multiParagraph.didExceedMaxLines &&
                    it.size.height + 1 >= it.multiParagraph.height &&
                    (0 until it.lineCount).all { line -> it.getLineLeft(line) >= -1 && it.getLineRight(line) <= it.size.width + 1 } })
        }
        verifyText("battle_doctrine")
        verifyText("battle_scene_caption")
        listOf("LEFT", "CENTER", "RIGHT").forEach {
            val node = rule.onNodeWithTag("battle_front_$it").assertIsDisplayed().fetchSemanticsNode()
            assertTrue(node.boundsInRoot.height >= 48 * density - 1)
            assertTrue(node.boundsInRoot.width >= 48 * density - 1)
            verifyText("battle_contact_$it")
            verifyText("battle_cohesion_$it")
        }
        rule.onNodeWithText("Details").assertIsDisplayed()
        listOf("orders", "advance", "plan", "reserve", "targets", "details").forEach { tag ->
            val node = rule.onNodeWithTag("battle_$tag").assertIsDisplayed().fetchSemanticsNode()
            assertTrue("Action $tag must remain fully inside the viewport", root.contains(node.boundsInRoot.topLeft) && root.contains(node.boundsInRoot.bottomRight - androidx.compose.ui.geometry.Offset(1f, 1f)))
            assertTrue(node.boundsInRoot.height >= 48 * density - 1)
            verifyText("battle_label_$tag")
        }
    }
    private fun saveScreenshot(name: String) {
        val bitmap = rule.onNodeWithTag("battle_screen").captureToImage().asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= 29) {
            // UTP uninstalls the app after the suite. Shared Downloads survive that cleanup.
            val resolver = rule.activity.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/troopmanager-battle")
            }) ?: error("Screenshot destination unavailable")
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val folder = File(rule.activity.getExternalFilesDir(null), "battle-screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun smallPortraitKeepsTheFieldAndActionsVisible() {
        viewport(320, 568)
        verifyFieldAndTouchTargets("portrait-320x568")
        rule.onNodeWithTag("battle_front_RIGHT").performClick().assertIsSelected()
        rule.onNodeWithText("+5 Minuten").performClick()
        rule.onNodeWithTag("battle_hud").assertTextContains("5′", substring = true)
    }
    @Test fun landscapeKeepsTheFieldAbove52Percent() {
        viewport(640, 280, 1.3f)
        verifyFieldAndTouchTargets("landscape-640x280-font130")
        rule.onNodeWithText("+5 Minuten").assertIsDisplayed()
        rule.onNodeWithText("Details").performClick()
        rule.onNodeWithText("VERSORGUNG & FESTUNG").assertIsDisplayed()
    }
    @Test fun largerFontsRemainUsableOnSmallPortrait() {
        viewport(320, 568, 1.6f)
        verifyFieldAndTouchTargets("portrait-320x568-font160")
        rule.onNodeWithText("+5 Minuten").assertIsDisplayed()
    }

    @Test fun actualFortressVolleyAnimatesOnceThenRestsWithoutInventedLosses() {
        viewport(320, 568, scenario = "small-assault", animations = true)
        saveScreenshot("fortress-formation-1000-vs-200")
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("battle_advance").performClick()
            rule.mainClock.advanceTimeBy(800)
            saveScreenshot("fortress-volley-in-flight")
            rule.runOnIdle {
                val battle = BattlePreviewActivity.latestState!!.battleSession!!
                assertEquals(1000, battle.ownRemaining)
                assertTrue(battle.exchanges.last().fronts.sumOf { it.arrowsUsed } > 0)
                assertTrue(battle.exchanges.last().fronts.all { !it.contactState.allowsMelee && it.ownDamage.total == 0 })
            }
            rule.mainClock.advanceTimeBy(2200)
            saveScreenshot("fortress-volley-completed")
            val resting = rule.onNodeWithTag("battle_field").captureToImage().asAndroidBitmap()
            rule.mainClock.advanceTimeBy(500)
            val later = rule.onNodeWithTag("battle_field").captureToImage().asAndroidBitmap()
            assertTrue("No looping arrows or damage after the exchange", resting.sameAs(later))
        } finally { rule.mainClock.autoAdvance = true }
    }
    @Test fun compactPendingEventLeavesItsReactionsReachable() {
        viewport(640, 280, 1.3f, pending = true)
        verifyFieldAndTouchTargets("landscape-pending-event")
        rule.onNodeWithText("Weitere Reaktionen").assertIsDisplayed().performClick()
        rule.onNodeWithText("Zentrum · Befehle", substring = true).assertIsDisplayed()
    }

    @Test fun preparationAndLivePlanChangesUpdateTheRealBattleState() {
        viewport(320, 568)
        rule.onNodeWithTag("battle_plan").performClick()
        rule.onNodeWithText("Taktikprofil: Mauer halten").performClick()
        rule.onNodeWithText("Fernkampf-Überlegenheit").performClick()
        rule.onNodeWithText("Taktikprofil: Fernkampf-Überlegenheit").assertIsDisplayed()
        rule.onNodeWithTag("battle_plan_panel").performScrollToNode(hasTestTag("battle_plan_apply"))
        rule.onNodeWithTag("battle_plan_apply").performClick()
        rule.onNodeWithTag("battle_plan_panel").assertDoesNotExist()
        rule.waitUntil(10_000) { BattlePreviewActivity.latestState?.battleSession?.plan?.doctrine == com.goldenunicorn.troopmanager.model.BattleDoctrine.RANGED_SUPERIORITY }
        rule.onNodeWithTag("battle_doctrine").assertTextContains("Fernkampf", substring = true)
        rule.runOnIdle { assertEquals(com.goldenunicorn.troopmanager.model.BattleDoctrine.RANGED_SUPERIORITY, BattlePreviewActivity.latestState!!.battleSession!!.plan.doctrine) }
        rule.onNodeWithTag("battle_advance").performClick()
        rule.waitUntil(10_000) { (BattlePreviewActivity.latestState?.battleSession?.minute ?: 0) > 0 }
        val points = BattlePreviewActivity.latestState!!.battleSession!!.commandPoints
        rule.onNodeWithTag("battle_plan").performClick()
        rule.onNodeWithText("Taktikprofil: Fernkampf-Überlegenheit").performClick()
        rule.onNodeWithText("Flexible Verteidigung").performClick()
        rule.onNodeWithTag("battle_plan_panel").performScrollToNode(hasTestTag("battle_plan_apply"))
        rule.onNodeWithTag("battle_plan_apply").performClick()
        rule.onNodeWithTag("battle_plan_panel").assertDoesNotExist()
        rule.waitUntil(10_000) { BattlePreviewActivity.latestState?.battleSession?.plan?.doctrine == com.goldenunicorn.troopmanager.model.BattleDoctrine.FLEXIBLE_DEFENSE }
        rule.runOnIdle { assertEquals(points - 3, BattlePreviewActivity.latestState!!.battleSession!!.commandPoints) }
    }

    @Test fun resourcesAndSelectedArmyTabsRemainFullyVisibleAtLargeFonts() {
        rule.runOnIdle { BattlePreviewActivity.viewport = BattlePreviewActivity.Viewport(fontScale = 1.6f, page = "army") }
        rule.waitForIdle()
        val strip = rule.onNodeWithTag("resource_strip").fetchSemanticsNode().boundsInRoot
        val resource = rule.onNodeWithTag("resource_0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(resource.left >= strip.left && resource.right <= strip.right)
        rule.onNodeWithTag("resource_next").assertIsDisplayed().performClick()
        rule.onNodeWithText("Ausbildung").performClick()
        rule.waitForIdle()
        val tabs = rule.onNodeWithTag("adaptive_tabs").fetchSemanticsNode().boundsInRoot
        val selected = rule.onNodeWithTag("tab_1").assertIsSelected().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(selected.left >= tabs.left && selected.right <= tabs.right)
        rule.onNodeWithText("Militärbereich: Heer").assertIsDisplayed()
    }

    @Test fun invasionDashboardStoresTheDefensePlan() {
        rule.runOnIdle { BattlePreviewActivity.viewport = BattlePreviewActivity.Viewport(page = "defense") }
        rule.waitForIdle()
        rule.onNodeWithTag("defense_dashboard").performScrollToNode(hasTestTag("defense_plan_edit"))
        rule.onNodeWithTag("defense_plan_edit").performClick()
        rule.onNodeWithText("Taktikprofil: Mauer halten").performClick()
        rule.onNodeWithText("Truppen schonen").performClick()
        rule.onNodeWithTag("battle_plan_panel").performScrollToNode(hasTestTag("battle_plan_apply"))
        rule.onNodeWithTag("battle_plan_apply").performClick()
        rule.onNodeWithTag("defense_dashboard").assertExists()
        rule.waitUntil(10_000) { BattlePreviewActivity.latestState?.defensePlan?.doctrine == com.goldenunicorn.troopmanager.model.BattleDoctrine.PRESERVE_TROOPS }
        rule.runOnIdle { assertEquals(com.goldenunicorn.troopmanager.model.BattleDoctrine.PRESERVE_TROOPS, BattlePreviewActivity.latestState!!.defensePlan.doctrine) }
    }
}
