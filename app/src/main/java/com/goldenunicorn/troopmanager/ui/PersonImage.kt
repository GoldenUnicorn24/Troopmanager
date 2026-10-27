package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.model.GameState
import com.goldenunicorn.troopmanager.model.CustomImageReference
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Own pictures have priority. The state is resolved per image id and *kind* (portrait or full body),
 * not per framing, so switching between head and bust reuses the same decoded picture. A custom
 * picture that is still loading shows a neutral placeholder; the generated face is only drawn when
 * the picture is known to be missing or unreadable (previously it flashed before every own photo).
 */
@Composable
internal fun PersonImage(id: String?, uri: String?, framing: Framing, modifier: Modifier, useDefinitionFallback: Boolean = true,
                         background: Int? = null, fallback: @Composable (Int) -> Unit) {
    BoxWithConstraints(modifier) {
        val frameWidth = constraints.maxWidth.coerceIn(32,1024)
        val targetWidth = if (framing == Framing.HEAD) (frameWidth * 1024 / 640).coerceAtMost(1024) else frameWidth
        val context = LocalContext.current
        val imageId = id ?: CustomImageReference.idFromUri(uri)
        val definition = LocalCustomCharacterDefinitions.current[imageId]
        val kind = if (framing == Framing.FULL) CharacterImageKind.FULLBODY else CharacterImageKind.PORTRAIT
        val loadWidth = if (kind == CharacterImageKind.FULLBODY) targetWidth.coerceAtMost(768) else targetWidth
        val images = remember(context) { CustomCharacterImages(context) }
        var state by remember(imageId, uri, kind, loadWidth) { mutableStateOf<ArtState>(when {
            imageId == null && uri == null -> ArtState.Missing
            images.peek(imageId, kind, loadWidth) != null -> ArtState.Ready(images.peek(imageId, kind, loadWidth)!!.asImageBitmap())
            images.knownMissing(imageId, kind) && (kind == CharacterImageKind.FULLBODY || uri == null) -> ArtState.Missing
            else -> ArtState.Loading
        }) }
        LaunchedEffect(imageId, uri, kind, loadWidth) {
            if (state != ArtState.Loading) return@LaunchedEffect
            val loaded = ArtLoading.load { withContext(Dispatchers.IO) {
                images.load(imageId, kind, loadWidth) ?: if (kind == CharacterImageKind.PORTRAIT && uri != null) runCatching {
                    val file = File(Uri.parse(uri).path ?: "")
                    // Only app-owned copies are opened; old content URIs are migrated by SaveRepository.
                    if (Uri.parse(uri).scheme == "file" && file.canonicalPath.startsWith(File(context.filesDir, "custom").canonicalPath + "/")) {
                        images.load(file.parentFile!!.name, kind, loadWidth)
                    } else null
                }.getOrNull() else null
            } }
            state = if (loaded != null) ArtState.Ready(loaded.asImageBitmap()) else ArtState.Missing
        }
        when (val current = state) {
            ArtState.Loading -> ArtLoadingPlaceholder(background ?: definition?.appearance?.background ?: 0, Modifier.fillMaxSize())
            ArtState.Missing -> {
                if (definition != null && useDefinitionFallback) CharacterPortrait(definition.appearance.copy(customImageId = null), definition.gender, modifier, framing)
                else fallback(targetWidth)
            }
            is ArtState.Ready -> {
                val image = current.image
                Canvas(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = if (framing == Framing.FULL) "Eigenes Ganzkörperbild" else "Eigenes Porträt" }) {
                    if (framing == Framing.FULL) drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                        ArtPalette.backgrounds[(background ?: definition?.appearance?.background ?: 0).coerceIn(0, ArtPalette.backgrounds.lastIndex)],
                        Offset(size.width * .3f, size.height * .25f), size.maxDimension))
                    val reference = if (framing == Framing.HEAD) CharacterImageFrames.head else intArrayOf(0, 0, if (framing == Framing.FULL) 768 else 1024, if (framing == Framing.FULL) 1536 else 1280)
                    val ratio = image.width / (if (framing == Framing.FULL) 768f else 1024f)
                    val source = IntOffset((reference[0] * ratio).toInt(), (reference[1] * ratio).toInt())
                    val sourceSize = IntSize((reference[2] * ratio).toInt().coerceAtLeast(1).coerceAtMost(image.width), (reference[3] * ratio).toInt().coerceAtLeast(1).coerceAtMost(image.height))
                    val scale = if (framing == Framing.FULL) minOf(size.width / sourceSize.width, size.height / sourceSize.height)
                        else maxOf(size.width / sourceSize.width, size.height / sourceSize.height)
                    val w = (sourceSize.width * scale).toInt().coerceAtLeast(1)
                    val h = (sourceSize.height * scale).toInt().coerceAtLeast(1)
                    drawImage(image, srcOffset = source, srcSize = sourceSize, dstOffset = IntOffset((size.width.toInt() - w) / 2, (size.height.toInt() - h) / 2), dstSize = IntSize(w, h))
                }
            }
        }
    }
}

@Composable
internal fun rememberFigureArt(state: GameState, people: List<com.goldenunicorn.troopmanager.model.NpcIdentity>, seatedIds: Set<String> = emptySet(), extraLooks: List<Pair<com.goldenunicorn.troopmanager.model.CharacterAppearance,com.goldenunicorn.troopmanager.model.Gender>> = emptyList()): FigureArt {
    val context = LocalContext.current
    val looks = remember(people, seatedIds, extraLooks, state.life.appearance, state.life.gender, state.player.species) {
        (people.map { Triple(it.appearance,it.gender,setOf(if (it.id in seatedIds) "sitting" else "standing")) } +
            Triple(state.life.appearance ?: defaultLook(state.life.gender,state.player.species),state.life.gender,setOf("standing","walk_0","walk_1","walk_2","walk_3")) +
            extraLooks.map { Triple(it.first,it.second,setOf("standing")) }).distinct()
    }
    val images = remember(context) { CustomCharacterImages(context) }
    var art by remember(looks) { mutableStateOf(run {
        // First frame from memory: figures already composed earlier appear immediately.
        val custom = looks.mapNotNull { it.first.customImageId }.distinct().mapNotNull { id -> images.peek(id,CharacterImageKind.FULLBODY,192)?.let { id to it.asImageBitmap() } }.toMap()
        val painted = looks.filter { it.first.customImageId !in custom }.flatMap { (a,gender,poses) ->
            poses.mapNotNull { pose -> FigureAssets.cached(context,a,gender,pose)?.let { FigureAssets.key(a,gender,pose) to it } }
        }.toMap()
        val complete = looks.all { (a,gender,poses) -> a.customImageId in custom || poses.all { FigureAssets.key(a,gender,it) in painted } }
        FigureArt(custom,painted,loading = !complete)
    }) }
    LaunchedEffect(looks) {
        if (!art.loading) return@LaunchedEffect
        art = ArtLoading.load { withContext(Dispatchers.IO) {
            val custom = looks.mapNotNull { it.first.customImageId }.distinct().mapNotNull { id ->
                images.load(id,CharacterImageKind.FULLBODY,192)?.let { id to it.asImageBitmap() }
            }.toMap()
            val gate = Semaphore(2)
            val painted = coroutineScope {
                looks.filter { it.first.customImageId !in custom }.flatMap { (a,gender,poses) ->
                    poses.map { pose -> async { gate.withPermit {
                        FigureAssets.load(context,a,gender,pose)?.let { FigureAssets.key(a,gender,pose) to it }
                    } } }
                }.awaitAll().filterNotNull().toMap()
            }
            FigureArt(custom,painted,loading = false)
        } }
    }
    return art
}

internal fun DrawScope.drawCustomFigure(image: androidx.compose.ui.graphics.ImageBitmap, feet: Offset, unit: Float, shadow: Boolean = true, light: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White) {
    val height = unit * 140f
    val width = height / 2f
    if (shadow) drawOval(androidx.compose.ui.graphics.Color.Black.copy(alpha = .32f), feet - Offset(width * .32f, height * .015f), Size(width * .64f, height * .035f))
    drawTintedArt(image,light,IntOffset((feet.x - width / 2f).toInt(),(feet.y - height * CharacterImageFrames.FOOT_Y / 1536f).toInt()),
        IntSize(width.toInt().coerceAtLeast(1),height.toInt().coerceAtLeast(1)))
}
