package com.goldenunicorn.troopmanager.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldenunicorn.troopmanager.model.Spot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks through every screen of the real shell and stores screenshots for design review. */
@RunWith(AndroidJUnit4::class)
class TourTest {
    @get:Rule val rule = createAndroidComposeRule<TourActivity>()

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        rule.waitUntil(2_000) { ArtLoading.pending.get() == 0 }
        rule.waitForIdle()
        val roots = rule.onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        val started = android.os.SystemClock.elapsedRealtime()
        val bitmap = roots[count - 1].captureToImage().asAndroidBitmap()
        org.junit.Assert.assertTrue("$name überschreitet 2 s Zeichenzeit", android.os.SystemClock.elapsedRealtime() - started <= 2000)
        val resolver = rule.activity.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/troopmanager-battle/tour")
        }) ?: return
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun page(name: String) {
        rule.runOnIdle { TourActivity.page = name }
        rule.waitForIdle()
        rule.waitUntil(2_000) { ArtLoading.pending.get() == 0 }
        rule.waitForIdle()
    }

    @Test fun everyScreen() {
        listOf("menu", "create").forEach { page(it); shot("a-$it") }
        Spot.entries.forEachIndexed { i, spot -> page("spot:${spot.name}"); shot("b-${i.toString().padStart(2, '0')}-${spot.name}") }
        listOf("scene", "celebration", "battle").forEach { page(it); shot("c-$it") }
        // Project Rebirth: start wizard, landless life, walkable tavern, conversation, portraits.
        listOf("wizard-0", "wizard-1", "wizard-2", "wizard-4", "wizard-5", "life-0", "life-1", "life-2", "life-3",
            "tavern", "tavern-talk", "portraits").forEach { page(it); shot("d-$it") }
        // Milestone 2: market, personal fight, following, captivity.
        listOf("market", "skirmish", "party", "captive").forEach { page(it); shot("e-$it") }
        // Milestone 3: the barracks yard.
        listOf("barracks").forEach { page(it); shot("f-$it") }
        // Milestone 4: the castle courtyard on a tournament day, seated guests in the tavern.
        listOf("castle", "tavern").forEach { page(it); shot("g-$it") }
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        TourActivity.customPortraitBytes = testContext.assets.open("adult-portrait.png").use { it.readBytes() }
        TourActivity.customFullbodyBytes = testContext.assets.open("adult-fullbody.png").use { it.readBytes() }
        listOf("custom-import", "custom-crop", "custom-tavern", "custom-compare").forEach {
            TourActivity.customReady = false
            page(it)
            rule.waitUntil(2_000) { TourActivity.customReady }
            if (it == "custom-compare") rule.waitUntil(2_000) { rule.onAllNodesWithContentDescription("Eigenes Porträt").fetchSemanticsNodes().isNotEmpty() }
            shot("i-$it")
        }
        // Milestone 5: a ruler (and knight) visiting the castle of a town.
        listOf("ruler-castle").forEach { page(it); shot("h-$it") }
        page("photo-night"); shot("j-photoreal-night-market")
    }
}
