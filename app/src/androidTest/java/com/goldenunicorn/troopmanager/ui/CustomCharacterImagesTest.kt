package com.goldenunicorn.troopmanager.ui

import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
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
            assertSame(images.load(id,CharacterImageKind.PORTRAIT,80),CustomCharacterImages(instrumentation.targetContext).load(id,CharacterImageKind.PORTRAIT,100))
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
    @Test fun sharedPackRoundTripUsesGrantedContentUriAndKeepsAdultIdentity() {
        val context = instrumentation.targetContext
        val source = java.io.File(context.cacheDir, "example-character.zip").apply { writeBytes(fixture("character-example.zip")) }
        val imported = CharacterPackSharing(context).read(Uri.fromFile(source))
        val id = images.install(imported.portrait, imported.fullbody)
        try {
            val record = com.goldenunicorn.troopmanager.model.CustomCharacter(id, "npc:package-test", imported.manifest.character,
                imported.manifest.source, fullbody = "fullbody.webp")
            val intent = CharacterPackSharing(context).prepareShare(record)
            assertEquals(android.content.Intent.ACTION_SEND, intent.action)
            assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = intent.clipData!!.getItemAt(0).uri
            assertEquals("content", uri.scheme)
            val roundTrip = CharacterPackSharing(context).read(uri)
            assertEquals("Mira Testfigur", roundTrip.manifest.character.name)
            assertEquals(27, roundTrip.manifest.character.age)
            assertEquals(1024, roundTrip.portrait.width)
            assertNotNull(roundTrip.fullbody)
            roundTrip.portrait.recycle(); roundTrip.fullbody?.recycle()
        } finally {
            images.file(id, CharacterImageKind.PORTRAIT).parentFile!!.deleteRecursively()
            source.delete()
            imported.portrait.recycle(); imported.fullbody?.recycle()
        }
    }
    @Test fun invalidFileCannotCreateAnImage() {
        try { images.decode("not an image".toByteArray(), CharacterImageKind.PORTRAIT); fail() }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("JPG")) }
    }
    @Test fun transparentPortraitAndOffscreenBodyAreRejected() {
        val empty = android.graphics.Bitmap.createBitmap(1024,1280,android.graphics.Bitmap.Config.ARGB_8888)
        try { images.install(empty); fail("An empty portrait must not replace the fallback") }
        catch (_: IllegalArgumentException) { } finally { empty.recycle() }
        val body = images.decode(fixture("adult-fullbody.png"),CharacterImageKind.FULLBODY)
        try {
            images.crop(body,CharacterImageKind.FULLBODY,CharacterImageCrop(x = 2000f))
            fail("Moving the entire figure out of frame must not create an invisible actor")
        } catch (_: IllegalStateException) { } finally { body.recycle() }
    }
    @Test fun multiplyTintPreservesTransparentBorders() {
        val source = androidx.compose.ui.graphics.ImageBitmap(32,32)
        val output = androidx.compose.ui.graphics.ImageBitmap(32,32)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        scope.draw(androidx.compose.ui.unit.Density(1f),androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(source),androidx.compose.ui.geometry.Size(32f,32f)) {
            drawRect(androidx.compose.ui.graphics.Color.Gray,androidx.compose.ui.geometry.Offset(8f,8f),androidx.compose.ui.geometry.Size(16f,16f))
            drawRect(androidx.compose.ui.graphics.Color.Gray.copy(alpha = .5f),androidx.compose.ui.geometry.Offset(2f,2f),androidx.compose.ui.geometry.Size(4f,4f))
        }
        scope.draw(androidx.compose.ui.unit.Density(1f),androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(output),androidx.compose.ui.geometry.Size(32f,32f)) {
            drawTintedArt(source,androidx.compose.ui.graphics.Color(.2f,.8f,.2f))
        }
        val bitmap = output.asAndroidBitmap()
        assertEquals(0,android.graphics.Color.alpha(bitmap.getPixel(0,0)))
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(3,3)) in 127..129)
        val center = bitmap.getPixel(16,16)
        assertEquals(255,android.graphics.Color.alpha(center))
        assertTrue(android.graphics.Color.green(center) > android.graphics.Color.red(center) + 50)
    }
}
