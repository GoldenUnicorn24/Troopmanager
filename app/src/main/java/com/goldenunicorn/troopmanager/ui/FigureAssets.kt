package com.goldenunicorn.troopmanager.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.goldenunicorn.troopmanager.data.CharacterImageFrames
import com.goldenunicorn.troopmanager.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

internal data class FigureKey(val appearance: CharacterAppearance, val gender: Gender, val pose: String)

/**
 * Loaded figure art for a scene. While [loading] is true, a figure without an entry is still being
 * composed and must not be replaced by the procedural drawing (that was the visible "old art" flash).
 */
internal data class FigureArt(
    val custom: Map<String, ImageBitmap> = emptyMap(),
    val painted: Map<FigureKey, ImageBitmap> = emptyMap(),
    val loading: Boolean = false,
) {
    operator fun get(id: String?): ImageBitmap? = custom[id]
    fun get(a: CharacterAppearance, gender: Gender, phase: Float, seated: Boolean): ImageBitmap? = painted[FigureAssets.key(a,gender,FigureAssets.pose(phase,seated))]
}

/**
 * How a weapon of the shared weapon sheet is carried. The exported weapon layers show every weapon
 * standing *beside* the figure, which looked like a floating object. Each part is therefore cut from
 * the sheet (source rectangle in 768×1536 coordinates) and attached to the body: on the back, on the
 * belt or at the hip. Angles are clockwise degrees around the part's pivot.
 */
internal object WeaponCarry {
    enum class Slot { BACK, HIP_UNDER_ARMOR, BELT }
    data class Part(val src: IntArray, val pivot: Offset, val target: Offset, val angle: Float, val scale: Float, val slot: Slot)

    /** Belt and hip positions follow the torso; the seated torso sits 360 px lower. */
    fun torsoOffset(pose: String) = if (pose == "sitting") 360f else 0f

    val parts: Map<Int, List<Part>> = mapOf(
        // Longsword: sheathed at the left hip; the hilt is drawn above armour, the scabbard below it.
        0 to listOf(
            Part(intArrayOf(637,718,710,1381), Offset(673f,718f), Offset(502f,690f), -12f, 1f, Slot.HIP_UNDER_ARMOR),
            Part(intArrayOf(491,602,633,770), Offset(562f,770f), Offset(502f,690f), -12f, .9f, Slot.BELT),
        ),
        // Bow and quiver across the back, arrows over the right shoulder.
        1 to listOf(
            Part(intArrayOf(154,862,273,1356), Offset(213f,880f), Offset(470f,330f), 24f, 1f, Slot.BACK),
            Part(intArrayOf(57,602,161,1478), Offset(109f,1040f), Offset(400f,640f), 24f, .95f, Slot.BACK),
        ),
        2 to listOf(Part(intArrayOf(594,92,646,1478), Offset(620f,785f), Offset(384f,640f), 20f, 1f, Slot.BACK)),
        3 to listOf(Part(intArrayOf(501,602,700,1388), Offset(600f,995f), Offset(384f,560f), 26f, 1f, Slot.BACK)),
        // Twin daggers sheathed on both sides of the belt.
        4 to listOf(
            Part(intArrayOf(478,602,568,1058), Offset(523f,620f), Offset(300f,650f), 8f, .55f, Slot.BELT),
            Part(intArrayOf(593,603,683,1057), Offset(638f,620f), Offset(468f,650f), -8f, .55f, Slot.BELT),
        ),
        5 to listOf(Part(intArrayOf(566,132,674,1478), Offset(620f,805f), Offset(384f,640f), -20f, 1f, Slot.BACK)),
    )
}

/**
 * Six registered front poses composed from photographic layers. A partial figure set uses the
 * complete procedural figure. Composition order (back to front): shadow, cloak, carried back
 * weapon, skin (neck, hands), garment, hip weapon, armour, belt weapon, head, hair.
 */
internal object FigureAssets {
    val poses = listOf("standing","sitting","walk_0","walk_1","walk_2","walk_3")
    private val composites = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val layers = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val heads = object : LruCache<String,ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String,value: ImageBitmap) = value.width * value.height * 4
    }
    private val indexes = mutableMapOf<String, Set<String>>()
    fun key(a: CharacterAppearance, gender: Gender, pose: String) = FigureKey(if (a.customImageId == null) a else a.copy(customImageId = null),gender,pose)
    fun pose(phase: Float, seated: Boolean) = if (seated) "sitting" else if (phase == 0f) "standing" else "walk_${floor(((phase % 1f + 1f) % 1f) * 4).toInt().coerceIn(0,3)}"
    /** Composite widths: scenes use 192, the editor preview a larger raster. */
    fun widthFor(target: Int) = when { target <= 200 -> 192; target <= 400 -> 384; else -> 768 }
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
    private fun layer(context: Context, path: String, width: Int): ImageBitmap? {
        val sample = CharacterImageFrames.sampleSize(768,1536,width,width * 2)
        val cacheKey = "${context.packageName}:$path:$sample"
        synchronized(layers) { layers.get(cacheKey)?.let { return it } }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it,null,bounds) }
            require(bounds.outWidth == 768 && bounds.outHeight == 1536 && bounds.outMimeType == "image/webp")
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open(path).use { BitmapFactory.decodeStream(it,null,options) }?.takeIf { it.hasAlpha() }?.asImageBitmap()
        }.getOrNull()?.also { synchronized(layers) { layers.put(cacheKey,it) } }
    }
    private fun head(context: Context, a: CharacterAppearance, gender: Gender, px: Int): ImageBitmap {
        val key = "${context.packageName}:$gender:$px:$a"
        synchronized(heads) { heads.get(key)?.let { return it } }
        val portrait = PortraitAssets.layersFor(context,a,gender,px,headOnly = true)
        val image = ImageBitmap(px,px * 5 / 4)
        val factor = px / 1024f
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(px.toFloat(),px * 5 / 4f)) {
            scale(factor,factor,Offset.Zero) { drawRegisteredPortrait(a,gender,portrait,includeTorso = false,includeHair = false) }
        }
        synchronized(heads) { heads.put(key,image) }
        return image
    }
    private fun cacheKey(context: Context, a: CharacterAppearance, gender: Gender, pose: String, width: Int) =
        "${context.packageName}:$width:${key(a.normalized(),gender,pose)}"

    /** Synchronous memory lookup for the first frame; never touches the disk. */
    fun cached(context: Context, a: CharacterAppearance, gender: Gender, pose: String, width: Int = 192): ImageBitmap? =
        composites.get(cacheKey(context,a,gender,pose,widthFor(width)))

    fun load(context: Context, a: CharacterAppearance, gender: Gender, pose: String, width: Int = 192): ImageBitmap? {
        require(pose in poses)
        val w = widthFor(width)
        val look = a.normalized()
        val base = if (gender == Gender.FEMALE) "female" else "male"
        val prefix = "figures/$base/$pose"
        val weaponPath = "figures/$base/standing/weapon/${look.weapon}.webp"
        val requested = listOfNotNull(
            Triple("body","$prefix/body/${look.build}.webp",ArtPalette.skin[look.skinTone].base),
            Triple("outfit","$prefix/outfit/${look.clothing}.webp",ArtPalette.fabric[look.primaryColor]),
            if (look.armor > 0) Triple("armor","$prefix/armor/${look.armor}.webp",null) else null,
            if (look.cloak > 0) Triple("cloak","$prefix/cloak/${look.cloak}.webp",ArtPalette.fabric[look.accentColor]) else null,
            Triple("hair","$prefix/hair_front/${look.hairStyle}_${look.hairTexture}.webp",ArtPalette.hair[look.hairColor]),
        )
        if (requested.any { it.second !in files(context) }) return null
        val key = cacheKey(context,look,gender,pose,w)
        composites.get(key)?.let { return it }
        val decoded = requested.associate { (role,path,tint) -> role to ((layer(context,path,w) ?: return null) to tint) }
        // Seated figures rest their hands on the table; weapons stay with the equipment.
        val weapon = if (pose != "sitting" && weaponPath in files(context)) layer(context,weaponPath,w) else null
        val shadow = if ("$prefix/shadow.webp" in files(context)) layer(context,"$prefix/shadow.webp",w) else null
        val head = head(context,look,gender,if (w >= 768) 512 else if (w >= 384) 384 else 256)
        val image = ImageBitmap(w,w * 2)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(w.toFloat(),w * 2f)) {
            scale(w / 768f,w / 768f,Offset.Zero) { composeFigure(look,pose,decoded,weapon,shadow,head) }
        }
        composites.put(key,image)
        return image
    }

    /** All coordinates in the 768×1536 registration space (centre x=384, feet y=1480). */
    private fun DrawScope.composeFigure(look: CharacterAppearance, pose: String, decoded: Map<String, Pair<ImageBitmap, Color?>>,
                                        weapon: ImageBitmap?, shadow: ImageBitmap?, head: ImageBitmap) {
        if (shadow != null) drawImage(shadow,dstSize = IntSize(768,1536))
        else drawOval(Color.Black.copy(alpha = .32f),Offset(208f,1468f),Size(352f,36f))
        // Garments and armour were produced for one body width; builds widen them around the centre.
        val fit = listOf(.88f,1f,1.1f,1.18f)[look.build]
        fun drawPart(role: String, widthFactor: Float = 1f) {
            val (bitmap,tint) = decoded[role] ?: return
            val width = (768 * widthFactor).toInt().coerceAtLeast(1)
            drawTintedArt(bitmap,tint,IntOffset((768 - width) / 2,0),IntSize(width,1536))
        }
        val carry = WeaponCarry.parts[look.weapon].orEmpty()
        fun drawWeapon(slot: WeaponCarry.Slot) {
            val sheet = weapon ?: return
            val ratio = sheet.width / 768f
            val dy = WeaponCarry.torsoOffset(pose)
            carry.filter { it.slot == slot }.forEach { part ->
                val (x0,y0,x1,y1) = part.src.toList()
                translate(part.target.x,part.target.y + dy) {
                    rotate(part.angle,Offset.Zero) {
                        scale(part.scale,part.scale,Offset.Zero) {
                            drawImage(sheet,srcOffset = IntOffset((x0 * ratio).toInt(),(y0 * ratio).toInt()),
                                srcSize = IntSize(((x1 - x0) * ratio).toInt().coerceAtLeast(1),((y1 - y0) * ratio).toInt().coerceAtLeast(1)),
                                dstOffset = IntOffset((x0 - part.pivot.x).toInt(),(y0 - part.pivot.y).toInt()),
                                dstSize = IntSize(x1 - x0,y1 - y0))
                        }
                    }
                }
            }
        }
        // A cape hangs behind the body; only its sides and hem are visible from the front.
        drawPart("cloak")
        drawWeapon(WeaponCarry.Slot.BACK)
        // Hands are drawn under the sleeves so cuffs overlap the wrists naturally.
        drawPart("body")
        drawPart("outfit",fit)
        drawWeapon(WeaponCarry.Slot.HIP_UNDER_ARMOR)
        drawPart("armor",fit)
        drawWeapon(WeaponCarry.Slot.BELT)
        val eyeY = if (pose == "sitting") 546f else 186f
        val headScale = .29f
        drawImage(head,dstOffset = IntOffset((384 - 512 * headScale).toInt(),(eyeY - 560 * headScale).toInt()),
            dstSize = IntSize((1024 * headScale).toInt(),(1280 * headScale).toInt()))
        drawPart("hair")
    }
}

/**
 * Full-figure view used by the editor and profiles. Shows the memory-cached composite in the very
 * first frame, a neutral placeholder while composing, and the procedural figure only if the photo
 * layers are really unavailable.
 */
@Composable
internal fun PaintedFigurePortrait(a: CharacterAppearance, gender: Gender, modifier: Modifier, targetWidth: Int = 384, identity: Any? = null) {
    val context = LocalContext.current
    val width = FigureAssets.widthFor(targetWidth)
    val look = remember(a) { a.normalized() }
    var state by remember(look,gender,width) {
        mutableStateOf(FigureAssets.cached(context,look,gender,"standing",width)?.let { ArtState.Ready(it) } ?: ArtState.Loading)
    }
    LaunchedEffect(look,gender,width) {
        if (state is ArtState.Ready) return@LaunchedEffect
        val loaded = ArtLoading.load { withContext(Dispatchers.IO) { FigureAssets.load(context,look,gender,"standing",width) } }
        state = if (loaded != null) ArtState.Ready(loaded) else ArtState.Missing
    }
    // In the editor the previous figure of the same person stays until the edited one is composed.
    val last = remember(identity,gender) { arrayOfNulls<ArtState.Ready>(1) }
    val current = state
    SideEffect { if (current is ArtState.Ready && identity != null) last[0] = current }
    val shown = if (current == ArtState.Loading && identity != null) last[0] ?: current else current
    when (shown) {
        is ArtState.Ready -> Box(modifier) { ArtBackdrop(look.background,Modifier.matchParentSize()); Image(shown.image,"Ganzkörperfigur",Modifier.matchParentSize(),contentScale = ContentScale.Fit) }
        ArtState.Loading -> ArtLoadingPlaceholder(look.background,modifier)
        ArtState.Missing -> PaintedPortrait(look,gender,modifier,Framing.FULL)
    }
}
