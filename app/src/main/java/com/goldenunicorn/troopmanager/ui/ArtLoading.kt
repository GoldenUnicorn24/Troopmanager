package com.goldenunicorn.troopmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger

/** Lets screenshot tests wait for real IO instead of accidentally capturing the transient fallback. */
internal object ArtLoading {
    val pending = AtomicInteger(0)
    suspend fun <T> load(block: suspend () -> T): T {
        pending.incrementAndGet()
        try { return block() } finally { pending.decrementAndGet() }
    }
}

/**
 * Three explicit states instead of "null means fallback": a still-loading image must never be shown
 * as the procedural drawing, otherwise the old art flashes before the photographic art appears.
 */
internal sealed interface ArtState {
    data object Loading : ArtState
    data object Missing : ArtState
    data class Ready(val image: ImageBitmap) : ArtState
}

/** The portrait background of the character, without any figure. */
@Composable
internal fun ArtBackdrop(background: Int, modifier: Modifier) {
    val colors = ArtPalette.backgrounds[background.coerceIn(0, ArtPalette.backgrounds.lastIndex)]
    Box(modifier.background(Brush.radialGradient(colors, Offset(120f, 90f), 1400f)))
}

/** Neutral loading state: same backdrop, no stale face or figure. */
@Composable
internal fun ArtLoadingPlaceholder(background: Int, modifier: Modifier) {
    Box(modifier.clipToBounds().semantics { contentDescription = "Bild wird geladen" }, contentAlignment = Alignment.Center) {
        ArtBackdrop(background, Modifier.matchParentSize())
        CircularProgressIndicator(Modifier.size(22.dp), color = Gold.copy(alpha = .55f), strokeWidth = 2.dp)
    }
}
