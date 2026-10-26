package com.goldenunicorn.troopmanager.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.goldenunicorn.troopmanager.data.*
import com.goldenunicorn.troopmanager.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An unreadable custom image always renders the supplied painted/vector fallback. */
@Composable
internal fun PersonImage(id: String?, uri: String?, framing: Framing, modifier: Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(id, uri, framing) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id, uri, framing) {
        bitmap = withContext(Dispatchers.IO) {
            val images = CustomCharacterImages(context)
            val kind = if (framing == Framing.FULL) CharacterImageKind.FULLBODY else CharacterImageKind.PORTRAIT
            images.load(id, kind) ?: if (framing != Framing.FULL && uri != null) runCatching {
                val file = File(Uri.parse(uri).path ?: "")
                // Only app-owned copies are opened; old content URIs are migrated by SaveRepository.
                if (Uri.parse(uri).scheme == "file" && file.canonicalPath.startsWith(File(context.filesDir, "custom").canonicalPath + "/")) {
                    images.load(file.parentFile!!.name, kind)
                } else null
            }.getOrNull() else null
        }
    }
    val loaded = bitmap
    if (loaded == null) fallback() else {
        val image = remember(loaded) { loaded.asImageBitmap() }
        Canvas(modifier.clipToBounds()) {
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

@Composable
internal fun rememberFigureArt(state: GameState): Map<String, androidx.compose.ui.graphics.ImageBitmap> {
    val context = LocalContext.current
    val ids = remember(state.customCharacters) { state.customCharacters.filter { it.fullbody != null }.map { it.id } }
    var images by remember(ids) { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    LaunchedEffect(ids) {
        images = withContext(Dispatchers.IO) { ids.mapNotNull { id -> CustomCharacterImages(context).load(id, CharacterImageKind.FULLBODY, 192)?.let { id to it.asImageBitmap() } }.toMap() }
    }
    return images
}

internal fun DrawScope.drawCustomFigure(image: androidx.compose.ui.graphics.ImageBitmap, feet: Offset, unit: Float) {
    val height = unit * 140f
    val width = height / 2f
    drawOval(androidx.compose.ui.graphics.Color.Black.copy(alpha = .32f), feet - Offset(width * .32f, height * .015f), Size(width * .64f, height * .035f))
    drawImage(image, dstOffset = IntOffset((feet.x - width / 2f).toInt(), (feet.y - height * CharacterImageFrames.FOOT_Y / 1536f).toInt()),
        dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)))
}
