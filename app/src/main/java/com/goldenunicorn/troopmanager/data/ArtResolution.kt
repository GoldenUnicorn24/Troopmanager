package com.goldenunicorn.troopmanager.data

/** Availability means successfully decoded, not merely a filename in an asset index. */
object ArtResolution {
    enum class Source { CUSTOM, PAINTED, VECTOR }
    fun source(customAvailable: Boolean, paintedAvailable: Boolean) = when {
        customAvailable -> Source.CUSTOM
        paintedAvailable -> Source.PAINTED
        else -> Source.VECTOR
    }
}
