package com.goldenunicorn.troopmanager.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldenunicorn.troopmanager.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Android's real WebP decoder and the same compositor as the game, not substitute art. */
@RunWith(AndroidJUnit4::class)
class PhotographicAssetsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun save(name: String,image: ImageBitmap) {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/troopmanager-battle/production")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use { assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)) }
    }

    @Test fun portraitRecipesDecodeAndRenderBothGendersAllFaceShapesAndDarkSkin() {
        assertEquals(82,PortraitAssets.installedCount(context))
        for (gender in listOf(Gender.FEMALE,Gender.MALE)) for (shape in 0..4) for (skin in listOf(0,4)) {
            val look = CharacterAppearance(faceShape = shape,skinTone = skin,hairStyle = shape,hairTexture = shape%3,
                eyeShape = shape%4,noseShape = shape%4,expression = shape%4,browStyle = shape%4,
                hairColor = if (shape==3) 5 else 1,eyeColor = 3,armor = shape,clothing = shape,
                cloak = shape%4,beard = if (gender==Gender.MALE) shape else 0).normalized()
            val layers = PortraitAssets.layersFor(context,look,gender,256)
            for (key in listOf("base","eyes","iris","brows","nose","mouth","outfit","hair_front","hair_back")) assertTrue("Missing $key for $gender/$shape",key in layers)
            val image = ImageBitmap(256,320)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(256f,320f)) {
                drawRect(Color(0xFF172532))
                scale(.25f,.25f,Offset.Zero) { drawRegisteredPortrait(look,gender,layers) }
            }
            assertTrue(android.graphics.Color.alpha(image.asAndroidBitmap().getPixel(128,180))>0)
            save("portrait-${gender.name}-$shape-skin$skin",image)
        }
    }

    @Test fun allFigurePosesAreVisibleAndFourWalkingFramesDiffer() {
        for (gender in listOf(Gender.FEMALE,Gender.MALE)) {
            val look = CharacterAppearance(faceShape = 2,armor = 4,clothing = 1,cloak = 1,hairStyle = 0,weapon = 2).normalized()
            val walking = mutableSetOf<Int>()
            for (pose in FigureAssets.poses) {
                val image = FigureAssets.load(context,look,gender,pose)
                assertNotNull("Missing $gender/$pose",image)
                val bitmap = image!!.asAndroidBitmap()
                assertEquals(192,bitmap.width); assertEquals(384,bitmap.height)
                assertTrue("No visible boots near foot reference",(0 until bitmap.width).any { android.graphics.Color.alpha(bitmap.getPixel(it,368))>120 })
                val pixels = IntArray(192*180);bitmap.getPixels(pixels,0,192,0,200,192,180)
                if (pose.startsWith("walk_")) walking += pixels.contentHashCode()
                save("figure-${gender.name}-$pose",image)
            }
            assertEquals("Walking must use four different images",4,walking.size)
        }
    }

    @Test fun fourScenesHaveCompleteBackgroundAndForegroundRegistrations() {
        for (scene in listOf("tavern","market","barracks","castle")) {
            val art = SceneAssets.load(context,scene)
            assertNotNull("Missing scene $scene",art)
            assertNotNull(art!!.background)
            assertEquals((0..8).toSet(),art.foreground.keys+art.emptyForeground)
            assertTrue(art.foreground.keys.intersect(art.emptyForeground).isEmpty())
        }
    }

    @Test fun eyeSeparationKeepsNeutralWhitesAndAllowsSkinTintWithoutChangingAlpha() {
        val bitmap = Bitmap.createBitmap(intArrayOf(android.graphics.Color.argb(128,220,220,215),android.graphics.Color.rgb(180,130,100)),2,1,Bitmap.Config.ARGB_8888)
        val result = PhotographicEyes.separate(bitmap)
        val detail = result.neutral.asAndroidBitmap()
        assertEquals(128,android.graphics.Color.alpha(detail.getPixel(0,0)))
        assertEquals(0,android.graphics.Color.alpha(detail.getPixel(1,0)))
        val skin = result.skin.asAndroidBitmap().getPixel(1,0)
        assertEquals(android.graphics.Color.red(skin),android.graphics.Color.blue(skin))
        assertEquals(255,android.graphics.Color.alpha(skin))
        bitmap.recycle()
    }
}
