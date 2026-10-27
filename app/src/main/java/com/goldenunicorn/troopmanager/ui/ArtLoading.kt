package com.goldenunicorn.troopmanager.ui

import java.util.concurrent.atomic.AtomicInteger

/** Lets screenshot tests wait for real IO instead of accidentally capturing the transient fallback. */
internal object ArtLoading {
    val pending = AtomicInteger(0)
    suspend fun <T> load(block: suspend () -> T): T {
        pending.incrementAndGet()
        try { return block() } finally { pending.decrementAndGet() }
    }
}
