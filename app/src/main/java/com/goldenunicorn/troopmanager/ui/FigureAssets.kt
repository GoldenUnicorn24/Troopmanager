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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
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
    private val heads = object : LruCache<String,ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String,value: ImageBitmap) = value.width * value.height * 4
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
    private fun head(context: Context,a: CharacterAppearance,gender: Gender): ImageBitmap {
        val key = "${context.packageName}:$gender:$a"
        synchronized(heads) { heads.get(key)?.let { return it } }
        val portrait = PortraitAssets.layersFor(context,a,gender,256,headOnly = true)
        val image = ImageBitmap(256,320)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(256f,320f)) {
            scale(.25f,.25f,Offset.Zero) { drawRegisteredPortrait(a,gender,portrait,includeTorso = false,includeHair = false) }
        }
        synchronized(heads) { heads.put(key,image) }
        return image
    }
    fun load(context: Context, a: CharacterAppearance, gender: Gender, pose: String): ImageBitmap? {
        require(pose in poses)
        val look = a.normalized()
        val base = if (gender == Gender.FEMALE) "female" else "male"
        val prefix = "figures/$base/$pose"
        val requested = listOfNotNull(
            Triple("body","$prefix/body/${look.build}.webp",ArtPalette.skin[look.skinTone].base),
            Triple("outfit","$prefix/outfit/${look.clothing}.webp",ArtPalette.fabric[look.primaryColor]),
            if (look.armor > 0) Triple("armor","$prefix/armor/${look.armor}.webp",null) else null,
            if (look.cloak > 0) Triple("cloak","$prefix/cloak/${look.cloak}.webp",ArtPalette.fabric[look.accentColor]) else null,
            Triple("hair","$prefix/hair_front/${look.hairStyle}_${look.hairTexture}.webp",ArtPalette.hair[look.hairColor]),
            Triple("weapon","$prefix/weapon/${look.weapon}.webp",null),
        )
        if (requested.any { it.second !in files(context) }) return null
        val cacheKey = "${context.packageName}:${key(look,gender,pose)}"
        composites.get(cacheKey)?.let { return it }
        val decoded = requested.associate { (role,path,tint) -> role to ((layer(context,path) ?: return null) to tint) }
        val shadow = if ("$prefix/shadow.webp" in files(context)) layer(context,"$prefix/shadow.webp") else null
        val head = head(context,look,gender)
        val image = ImageBitmap(192,384)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(192f,384f)) {
            if (shadow != null) drawImage(shadow,dstSize = IntSize(192,384))
            else drawOval(Color.Black.copy(alpha = .32f),Offset(52f,367f),Size(88f,9f))
            fun drawPart(role: String) {
                val (bitmap,tint) = decoded[role] ?: return
                val fit = if (role in setOf("outfit","armor","cloak") && !(role == "cloak" && look.cloak == 3)) listOf(.88f,1f,1.1f,1.18f)[look.build] else 1f
                val width = (192*fit).toInt().coerceAtLeast(1)
                drawTintedArt(bitmap,tint,IntOffset((192-width)/2,0),IntSize(width,384))
            }
            drawPart("body"); drawPart("outfit")
            // Hands rest above trousers; sleeves still overlap the upper wrists.
            clipRect(top = if (pose == "sitting") 245f else 205f) { drawPart("body") }
            drawPart("armor")
            val eyeY = if (pose == "sitting") 546f else 186f
            drawImage(head,dstOffset = IntOffset(((384-512*.29f)/4).toInt(),((eyeY-560*.29f)/4).toInt()),
                dstSize = IntSize((256*.29f).toInt(),(320*.29f).toInt()))
            if (look.cloak == 3) clipRect(top = (eyeY+(210-560)*.29f)/4) { drawPart("hair") }
            else drawPart("hair")
            drawPart("cloak"); drawPart("weapon")
        }
        composites.put(cacheKey,image)
        return image
    }
}

@Composable
internal fun PaintedFigurePortrait(a: CharacterAppearance, gender: Gender, modifier: Modifier) {
    val context = LocalContext.current
    var image by remember(a,gender) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(a,gender) { image = ArtLoading.load { withContext(Dispatchers.IO) { FigureAssets.load(context,a,gender,"standing") } } }
    val loaded = image
    if (loaded == null) PaintedPortrait(a,gender,modifier,Framing.FULL)
    else Image(loaded,null,modifier,contentScale = ContentScale.Fit)
}
