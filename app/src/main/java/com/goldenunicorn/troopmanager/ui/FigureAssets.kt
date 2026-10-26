package com.goldenunicorn.troopmanager.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.goldenunicorn.troopmanager.data.CharacterImageFrames
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

internal data class FigureKey(val appearance: CharacterAppearance, val gender: Gender, val pose: String)
internal data class FigureArt(val custom: Map<String, ImageBitmap> = emptyMap(), val painted: Map<FigureKey, ImageBitmap> = emptyMap()) {
    operator fun get(id: String?): ImageBitmap? = custom[id]
    fun get(a: CharacterAppearance, gender: Gender, phase: Float, seated: Boolean): ImageBitmap? = painted[FigureAssets.key(a,gender,FigureAssets.pose(phase,seated))]
}

/** Six registered front poses. A partial figure set uses the complete procedural figure. */
internal object FigureAssets {
    val poses = listOf("standing","sitting","walk_0","walk_1","walk_2","walk_3")
    private val composites = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val layers = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val indexes = mutableMapOf<String, Set<String>>()
    fun key(a: CharacterAppearance, gender: Gender, pose: String) = FigureKey(if (a.customImageId == null) a else a.copy(customImageId = null),gender,pose)
    fun pose(phase: Float, seated: Boolean) = if (seated) "sitting" else if (phase == 0f) "standing" else "walk_${floor(((phase % 1f + 1f) % 1f) * 4).toInt().coerceIn(0,3)}"
    private fun files(context: Context): Set<String> = synchronized(indexes) {
        indexes.getOrPut(context.packageName) {
            val found = mutableSetOf<String>()
            fun visit(path: String, depth: Int) {
                if (path.endsWith(".webp")) { found += path; return }
                if (depth > 5) return
                context.assets.list(path)?.forEach { visit("$path/$it",depth+1) }
            }
            runCatching { visit("figures",0) }
            found
        }
    }
    private fun layer(context: Context, path: String): ImageBitmap? {
        val cacheKey = "${context.packageName}:$path"
        layers.get(cacheKey)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it,null,bounds) }
            require(bounds.outWidth == 768 && bounds.outHeight == 1536 && bounds.outMimeType == "image/webp")
            val options = BitmapFactory.Options().apply { inSampleSize = CharacterImageFrames.sampleSize(768,1536,192,384) }
            context.assets.open(path).use { BitmapFactory.decodeStream(it,null,options) }?.takeIf { it.hasAlpha() }?.asImageBitmap()
        }.getOrNull()?.also { layers.put(cacheKey,it) }
    }
    fun load(context: Context, a: CharacterAppearance, gender: Gender, pose: String): ImageBitmap? {
        require(pose in poses)
        val look = a.normalized()
        val base = if (gender == Gender.FEMALE) "female" else "male"
        val prefix = "figures/$base/$pose"
        val requested = listOfNotNull(
            "$prefix/body/${look.build}.webp" to ArtPalette.skin[look.skinTone].base,
            "$prefix/outfit/${look.clothing}.webp" to ArtPalette.fabric[look.primaryColor],
            if (look.armor > 0) "$prefix/armor/${look.armor}.webp" to null else null,
            if (look.cloak > 0) "$prefix/cloak/${look.cloak}.webp" to ArtPalette.fabric[look.accentColor] else null,
            "$prefix/hair_front/${look.hairStyle}_${look.hairTexture}.webp" to ArtPalette.hair[look.hairColor],
            "$prefix/weapon/${look.weapon}.webp" to null,
        )
        if (requested.any { it.first !in files(context) }) return null
        val cacheKey = "${context.packageName}:${key(look,gender,pose)}"
        composites.get(cacheKey)?.let { return it }
        val decoded = requested.map { (path,tint) -> (layer(context,path) ?: return null) to tint }
        val shadow = if ("$prefix/shadow.webp" in files(context)) layer(context,"$prefix/shadow.webp") else null
        val image = ImageBitmap(192,384)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(192f,384f)) {
            if (shadow != null) drawImage(shadow,dstSize = IntSize(192,384))
            else drawOval(Color.Black.copy(alpha = .32f),Offset(52f,367f),Size(88f,9f))
            decoded.forEach { (bitmap,tint) -> drawTintedArt(bitmap,tint,target = IntSize(192,384)) }
        }
        composites.put(cacheKey,image)
        return image
    }
}

@Composable
internal fun PaintedFigurePortrait(a: CharacterAppearance, gender: Gender, modifier: Modifier) {
    val context = LocalContext.current
    var image by remember(a,gender) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(a,gender) { image = withContext(Dispatchers.IO) { FigureAssets.load(context,a,gender,"standing") } }
    val loaded = image
    if (loaded == null) PaintedPortrait(a,gender,modifier,Framing.FULL)
    else Image(loaded,null,modifier,contentScale = ContentScale.Fit)
}
