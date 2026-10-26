package com.goldenunicorn.troopmanager.data

import org.junit.Assert.*
import org.junit.Test

class ArtResolutionTest {
    @Test fun customImageWinsOverPaintedLayers() {
        assertEquals(ArtResolution.Source.CUSTOM,ArtResolution.source(true,true))
        assertEquals(ArtResolution.Source.CUSTOM,ArtResolution.source(true,false))
    }
    @Test fun unreadableCustomImageFallsThroughToPaintedThenVector() {
        assertEquals(ArtResolution.Source.PAINTED,ArtResolution.source(false,true))
        assertEquals(ArtResolution.Source.VECTOR,ArtResolution.source(false,false))
    }
    @Test fun missingBaseDoesNotDiscardOtherPaintedLayers() {
        val decoded = setOf("hair_front","nose")
        val sources = listOf("base","eyes","nose","hair_front").associateWith { ArtResolution.source(false,it in decoded) }
        assertEquals(ArtResolution.Source.VECTOR,sources.getValue("base"))
        assertEquals(ArtResolution.Source.VECTOR,sources.getValue("eyes"))
        assertEquals(ArtResolution.Source.PAINTED,sources.getValue("nose"))
        assertEquals(ArtResolution.Source.PAINTED,sources.getValue("hair_front"))
    }
}
