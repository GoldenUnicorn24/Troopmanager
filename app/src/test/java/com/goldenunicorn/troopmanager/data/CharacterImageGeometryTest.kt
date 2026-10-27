package com.goldenunicorn.troopmanager.data

import org.junit.Assert.*
import org.junit.Test

class CharacterImageGeometryTest {
    @Test fun eyeLineIsPreservedAtStandardResolution() {
        val p = CharacterImageCrop().map(512f, 560f, 1024, 1280, CharacterImageKind.PORTRAIT)
        assertEquals(512f, p.first, .001f)
        assertEquals(560f, p.second, .001f)
    }
    @Test fun halfSizeImageMapsEyesTo560() {
        val p = CharacterImageCrop().map(256f, 280f, 512, 640, CharacterImageKind.PORTRAIT)
        assertEquals(512f, p.first, .001f)
        assertEquals(560f, p.second, .001f)
    }
    @Test fun rotationAndTranslationAreInDestinationCoordinates() {
        val p = CharacterImageCrop(x = 10f, y = -20f, rotation = 90f).map(612f, 640f, 1024, 1280, CharacterImageKind.PORTRAIT)
        assertEquals(522f, p.first, .01f)
        assertEquals(720f, p.second, .01f)
    }
    @Test fun uncoveredPortraitCornersAreRejected() {
        assertTrue(CharacterImageCrop().coversFrame(1024, 1280, CharacterImageKind.PORTRAIT))
        assertFalse(CharacterImageCrop(x = 30f).coversFrame(1024, 1280, CharacterImageKind.PORTRAIT))
        assertFalse(CharacterImageCrop(rotation = 45f).coversFrame(1024, 1280, CharacterImageKind.PORTRAIT))
        assertTrue(CharacterImageCrop(zoom = 2f, rotation = 45f).coversFrame(1024, 1280, CharacterImageKind.PORTRAIT))
    }
    @Test fun feetRemainAt1480() {
        assertEquals(1480f, CharacterImageCrop().map(384f, 1480f, 768, 1536, CharacterImageKind.FULLBODY).second, .001f)
        assertEquals(4, CharacterImageFrames.sampleSize(2048, 2560, 512, 640))
    }
}
