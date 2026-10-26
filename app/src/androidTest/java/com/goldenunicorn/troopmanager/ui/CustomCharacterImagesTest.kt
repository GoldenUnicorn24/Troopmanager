package com.goldenunicorn.troopmanager.ui

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldenunicorn.troopmanager.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomCharacterImagesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val images = CustomCharacterImages(instrumentation.targetContext)
    private fun fixture(name: String) = instrumentation.context.assets.open(name).use { it.readBytes() }

    @Test fun installedCopySurvivesOriginalDeletionAndKeepsStandardDimensions() {
        val original = java.io.File(instrumentation.targetContext.cacheDir, "test-original.png").apply { writeBytes(fixture("adult-portrait.png")) }
        val decoded = images.read(Uri.fromFile(original), CharacterImageKind.PORTRAIT)
        val cropped = images.crop(decoded, CharacterImageKind.PORTRAIT, CharacterImageCrop())
        val body = images.decode(fixture("adult-fullbody.png"), CharacterImageKind.FULLBODY)
        val id = images.install(cropped, body)
        try {
            assertTrue(original.delete())
            val stored = images.load(id, CharacterImageKind.PORTRAIT, 1024)!!
            assertEquals(1024, stored.width)
            assertEquals(1280, stored.height)
            assertNotNull(CustomCharacterImages(instrumentation.targetContext).load(id, CharacterImageKind.FULLBODY))
        } finally {
            images.file(id, CharacterImageKind.PORTRAIT).parentFile!!.deleteRecursively()
            decoded.recycle(); cropped.recycle(); body.recycle()
        }
    }
    @Test fun exifOrientationIsAppliedBeforeMinimumResolutionValidation() {
        val decoded = images.decode(fixture("adult-exif.jpg"), CharacterImageKind.PORTRAIT)
        assertEquals(512, decoded.width)
        assertEquals(640, decoded.height)
        decoded.recycle()
    }
    @Test fun opaqueFullbodyIsRejected() {
        try {
            images.decode(fixture("adult-portrait.png"), CharacterImageKind.FULLBODY)
            fail("Opaque figures must be rejected")
        } catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("transparent")) }
    }
    @Test fun invalidFileCannotCreateAnImage() {
        try { images.decode("not an image".toByteArray(), CharacterImageKind.PORTRAIT); fail() }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("JPG")) }
    }
}
