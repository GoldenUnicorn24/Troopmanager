package com.goldenunicorn.troopmanager.ui

import android.content.res.Configuration
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private fun viewport(width: Int, height: Int, scale: Float = 1f, pending: Boolean = false) {
        rule.runOnIdle { BattlePreviewActivity.viewport = BattlePreviewActivity.Viewport(width, height, scale, pending) }
        val orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == orientation }
        rule.waitForIdle()
    }
    private fun verifyFieldAndTouchTargets(name: String) {
        val root = rule.onNodeWithTag("battle_screen").fetchSemanticsNode().boundsInRoot
        val field = rule.onNodeWithTag("battle_field").fetchSemanticsNode().boundsInRoot
        assertTrue("The battlefield must occupy at least 52% of the viewport: ${field.height/root.height}", field.height / root.height >= .52f)
        val density = rule.activity.resources.displayMetrics.density
        listOf("LEFT", "CENTER", "RIGHT").forEach {
            val node = rule.onNodeWithTag("battle_front_$it").assertIsDisplayed().fetchSemanticsNode()
            assertTrue(node.boundsInRoot.height >= 48 * density - 1)
            assertTrue(node.boundsInRoot.width >= 48 * density - 1)
        }
        rule.onNodeWithText("Details").assertIsDisplayed()
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
        rule.onNodeWithText("5′").assertIsDisplayed()
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
    @Test fun compactPendingEventLeavesItsReactionsReachable() {
        viewport(640, 280, 1.3f, pending = true)
        verifyFieldAndTouchTargets("landscape-pending-event")
        rule.onNodeWithText("Weitere Reaktionen").assertIsDisplayed().performClick()
        rule.onNodeWithText("Zentrum · Befehle", substring = true).assertIsDisplayed()
    }
}
