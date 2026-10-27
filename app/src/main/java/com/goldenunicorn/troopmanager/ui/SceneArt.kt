package com.goldenunicorn.troopmanager.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal data class SceneArt(val background: ImageBitmap?, val foreground: Map<Int, ImageBitmap>, val emptyForeground: Set<Int> = emptySet())

internal object SceneAssets {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private fun load(context: Context, path: String, alpha: Boolean): ImageBitmap? {
        cache.get(path)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth == 2160 && bounds.outHeight == 1440 && bounds.outMimeType == "image/webp")
            val options = BitmapFactory.Options().apply { inSampleSize = CharacterImageFrames.sampleSize(2160,1440,1024,683) }
            val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            require(!alpha || bitmap.hasAlpha())
            bitmap.asImageBitmap()
        }.getOrNull()?.also { cache.put(path, it) }
    }

    fun load(context: Context, scene: String): SceneArt? = runCatching {
        val (grid, wall) = when (scene) {
            "tavern" -> TavernEngine.layout to 2.2f
            "market" -> MarketEngine.layout to 2.4f
            "barracks" -> BarracksEngine.layout to 2.2f
            "castle" -> CastleEngine.layout to 2.6f
            else -> return null
        }
        val layout = context.assets.open("scenes/$scene/layout.json").bufferedReader().use { Json.decodeFromString<SceneAssetLayout>(it.readText()) }
        layout.validate(grid, wall)
        val background = load(context, "scenes/$scene/${layout.image}", false)
        val foreground = layout.foreground.filterNot { it.empty }.mapNotNull { f -> load(context, "scenes/$scene/${f.path}", true)?.let { f.row to it } }.toMap()
        if (background == null && foreground.isEmpty()) null else SceneArt(background, foreground,layout.foreground.filter { it.empty }.map { it.row }.toSet())
    }.getOrNull()
}

@Composable
internal fun rememberSceneArt(scene: String): SceneArt? {
    val context = LocalContext.current
    var art by remember(scene) { mutableStateOf<SceneArt?>(null) }
    LaunchedEffect(scene) { art = ArtLoading.load { withContext(Dispatchers.IO) { SceneAssets.load(context, scene) } } }
    return art
}

internal fun sceneLight(slot: TimeSlot, indoors: Boolean): Color = when (slot) {
    TimeSlot.MORNING -> Color(0xFFFFE7CB)
    TimeSlot.NOON, TimeSlot.AFTERNOON -> if (indoors) Color(0xFFFFE4BF) else Color.White
    TimeSlot.EVENING -> if (indoors) Color(0xFFFFCD94) else Color(0xFFFFC4A1)
    TimeSlot.NIGHT -> if (indoors) Color(0xFFCBAD96) else Color(0xFF839DCB)
}

internal fun DrawScope.drawSceneBackground(art: SceneArt?, slot: TimeSlot, indoors: Boolean): Boolean {
    val image = art?.background ?: return false
    val darken = if (slot == TimeSlot.NIGHT) .5f else if (slot == TimeSlot.EVENING && !indoors) .2f else 0f
    drawTintedArt(image,sceneLight(slot,indoors).mix(Color.Black,darken),target = IntSize(size.width.toInt(),size.height.toInt()))
    return true
}

internal fun DrawScope.drawSceneForeground(art: SceneArt?, row: Int, slot: TimeSlot, indoors: Boolean): Boolean {
    if (row in art?.emptyForeground.orEmpty()) return true
    val image = art?.foreground?.get(row) ?: return false
    drawTintedArt(image,sceneLight(slot,indoors),target = IntSize(size.width.toInt(),size.height.toInt()))
    return true
}
