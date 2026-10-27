package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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

/** An unreadable custom image always renders the supplied painted/vector fallback. */
@Composable
internal fun PersonImage(id: String?, uri: String?, framing: Framing, modifier: Modifier, useDefinitionFallback: Boolean = true, fallback: @Composable (Int) -> Unit) {
    BoxWithConstraints(modifier) {
        val frameWidth = constraints.maxWidth.coerceIn(32,1024)
        val targetWidth = if (framing == Framing.HEAD) (frameWidth * 1024 / 640).coerceAtMost(1024) else frameWidth
        val context = LocalContext.current
        val imageId = id ?: CustomImageReference.idFromUri(uri)
        val definition = LocalCustomCharacterDefinitions.current[imageId]
        var bitmap by remember(imageId, uri, framing, targetWidth) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(imageId, uri, framing, targetWidth) {
            bitmap = ArtLoading.load { withContext(Dispatchers.IO) {
                val images = CustomCharacterImages(context)
                val kind = if (framing == Framing.FULL) CharacterImageKind.FULLBODY else CharacterImageKind.PORTRAIT
                images.load(imageId, kind, targetWidth) ?: if (framing != Framing.FULL && uri != null) runCatching {
                    val file = File(Uri.parse(uri).path ?: "")
                    // Only app-owned copies are opened; old content URIs are migrated by SaveRepository.
                    if (Uri.parse(uri).scheme == "file" && file.canonicalPath.startsWith(File(context.filesDir, "custom").canonicalPath + "/")) {
                        images.load(file.parentFile!!.name, kind, targetWidth)
                    } else null
                }.getOrNull() else null
            } }
        }
        val loaded = bitmap
        if (ArtResolution.source(loaded != null,false) != ArtResolution.Source.CUSTOM) {
            if (definition != null && useDefinitionFallback) CharacterPortrait(definition.appearance.copy(customImageId = null), definition.gender, modifier, framing)
            else fallback(targetWidth)
        } else {
            val image = remember(loaded) { loaded!!.asImageBitmap() }
            Canvas(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = if (framing == Framing.FULL) "Eigenes Ganzkörperbild" else "Eigenes Porträt" }) {
                val reference = if (framing == Framing.HEAD) CharacterImageFrames.head else intArrayOf(0, 0, image.width, image.height)
                val ratio = if (framing == Framing.HEAD) image.width / 1024f else 1f
                val source = IntOffset((reference[0] * ratio).toInt(), (reference[1] * ratio).toInt())
                val sourceSize = IntSize((reference[2] * ratio).toInt().coerceAtLeast(1), (reference[3] * ratio).toInt().coerceAtLeast(1))
                val scale = if (framing == Framing.FULL) minOf(size.width / sourceSize.width, size.height / sourceSize.height)
                    else maxOf(size.width / sourceSize.width, size.height / sourceSize.height)
                val w = (sourceSize.width * scale).toInt().coerceAtLeast(1)
                val h = (sourceSize.height * scale).toInt().coerceAtLeast(1)
                drawImage(image, srcOffset = source, srcSize = sourceSize, dstOffset = IntOffset((size.width.toInt() - w) / 2, (size.height.toInt() - h) / 2), dstSize = IntSize(w, h))
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
    var art by remember(looks) { mutableStateOf(FigureArt()) }
    LaunchedEffect(looks) {
        art = ArtLoading.load { withContext(Dispatchers.IO) {
            val custom = looks.mapNotNull { it.first.customImageId }.distinct().mapNotNull { id ->
                CustomCharacterImages(context).load(id,CharacterImageKind.FULLBODY,192)?.let { id to it.asImageBitmap() }
            }.toMap()
            val gate = Semaphore(2)
            val painted = coroutineScope {
                looks.filter { it.first.customImageId !in custom }.flatMap { (a,gender,poses) ->
                    poses.map { pose -> async { gate.withPermit {
                        FigureAssets.load(context,a,gender,pose)?.let { FigureAssets.key(a,gender,pose) to it }
                    } } }
                }.awaitAll().filterNotNull().toMap()
            }
            FigureArt(custom,painted)
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
