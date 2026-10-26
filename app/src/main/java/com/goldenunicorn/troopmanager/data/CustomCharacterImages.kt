package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Offline, bounded image IO. Call from Dispatchers.IO, never from a Canvas draw callback. */
class CustomCharacterImages(private val context: Context) {
    companion object {
        const val MAX_BYTES = 15 * 1024 * 1024
        private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
        fun readBounded(input: InputStream, limit: Int = MAX_BYTES): ByteArray = input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "Die Datei ist zu groß (maximal ${limit / 1024 / 1024} MB)." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    fun file(id: String, kind: CharacterImageKind): File {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Ungültige Bild-ID." }
        return File(context.filesDir, "custom/$id/${if (kind == CharacterImageKind.PORTRAIT) "portrait" else "fullbody"}.webp")
    }

    fun read(uri: Uri, kind: CharacterImageKind, legacy: Boolean = false): Bitmap =
        decode(readBounded(context.contentResolver.openInputStream(uri) ?: error("Das Bild kann nicht gelesen werden.")), kind, legacy)

    fun decode(bytes: ByteArray, kind: CharacterImageKind, legacy: Boolean = false): Bitmap {
        require(bytes.size <= MAX_BYTES) { "Das Bild darf höchstens 15 MB groß sein." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Bitte JPG, PNG oder WebP verwenden." }
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000) { "Ungültige oder zu große Bildabmessungen (maximal 80 Megapixel)." }
        val exif = ExifInterface(bytes.inputStream())
        val swap = exif.rotationDegrees % 180 != 0
        val w = if (swap) bounds.outHeight else bounds.outWidth
        val h = if (swap) bounds.outWidth else bounds.outHeight
        require(legacy || (w >= kind.minWidth && h >= kind.minHeight)) { "Das Bild braucht mindestens ${kind.minWidth} × ${kind.minHeight} Pixel nach Ausrichtung." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = CharacterImageFrames.sampleSize(bounds.outWidth, bounds.outHeight, kind.width * 2, kind.height * 2)
            while ((bounds.outWidth / inSampleSize).toLong() * (bounds.outHeight / inSampleSize) > 8_000_000) inSampleSize *= 2
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Das Bild ist beschädigt.")
        val matrix = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        val oriented = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (oriented !== raw) raw.recycle()
        if (!hasVisibleContent(oriented)) {
            oriented.recycle()
            error("Das Bild ist vollständig oder fast vollständig transparent. Bitte eine sichtbare erwachsene Figur verwenden.")
        }
        if (kind == CharacterImageKind.FULLBODY && !hasTransparency(oriented)) {
            oriented.recycle()
            error("Ganzkörperbilder brauchen einen transparenten Hintergrund. Bitte ein freigestelltes PNG oder WebP wählen; es wird kein Netz zur Freistellung verwendet.")
        }
        return oriented
    }

    fun crop(bitmap: Bitmap, kind: CharacterImageKind, crop: CharacterImageCrop): Bitmap {
        require(crop.zoom.isFinite() && crop.zoom in 1f..6f && crop.x.isFinite() && crop.y.isFinite() && crop.rotation.isFinite()) { "Ungültiger Zuschnitt." }
        require(kind == CharacterImageKind.FULLBODY || crop.coversFrame(bitmap.width, bitmap.height, kind)) { "Bitte das Porträt so vergrößern oder verschieben, dass es den Rahmen vollständig füllt." }
        val result = Bitmap.createBitmap(kind.width, kind.height, Bitmap.Config.ARGB_8888, true, ColorSpace.get(ColorSpace.Named.SRGB))
        val canvas = Canvas(result)
        canvas.translate(kind.width / 2f + crop.x, kind.height / 2f + crop.y)
        canvas.rotate(crop.rotation)
        val scale = crop.scale(bitmap.width, bitmap.height, kind)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (!hasVisibleContent(result) || (kind == CharacterImageKind.FULLBODY && !hasTransparency(result))) {
            result.recycle()
            error("Bitte die sichtbare Figur im Rahmen lassen; Ganzkörperbilder brauchen auch nach dem Zuschnitt transparente Ränder.")
        }
        return result
    }

    /** A new immutable directory keeps existing saves valid if a replacement is interrupted. */
    fun install(portrait: Bitmap, fullbody: Bitmap? = null): String {
        require(portrait.width == 1024 && portrait.height == 1280 && hasVisibleContent(portrait)) { "Das Porträt braucht eine sichtbare Figur im Standardformat 1024 × 1280." }
        require(fullbody == null || (fullbody.width == 768 && fullbody.height == 1536 && hasVisibleContent(fullbody) && hasTransparency(fullbody))) { "Das Ganzkörperbild braucht eine sichtbare Figur, transparente Ränder und 768 × 1536 Pixel." }
        val id = UUID.randomUUID().toString()
        val root = File(context.filesDir, "custom").apply { check(mkdirs() || isDirectory) }
        val staging = File(root, ".$id").apply { check(mkdir()) }
        try {
            fun write(bitmap: Bitmap, name: String) {
                File(staging, name).outputStream().use {
                    @Suppress("DEPRECATION")
                    val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    check(bitmap.compress(format, 90, it)) { "Das Bild konnte nicht gespeichert werden." }
                    it.fd.sync()
                }
            }
            write(portrait, "portrait.webp")
            fullbody?.let { write(it, "fullbody.webp") }
            check(staging.renameTo(File(root, id))) { "Der Bildimport konnte nicht abgeschlossen werden." }
            return id
        } finally { staging.deleteRecursively() }
    }

    fun load(id: String?, kind: CharacterImageKind, targetWidth: Int = 512): Bitmap? {
        if (id == null) return null
        require(targetWidth in 1..4096)
        val source = runCatching { file(id,kind) }.getOrNull() ?: return null
        val sample = CharacterImageFrames.sampleSize(kind.width,kind.height,targetWidth,targetWidth * kind.height / kind.width)
        val key = "${source.absolutePath}:$sample"
        return synchronized(cache) {
            cache.get(key) ?: run {
                if (!source.isFile || source.length() > MAX_BYTES) return@run null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path,bounds)
                if (bounds.outWidth != kind.width || bounds.outHeight != kind.height) return@run null
                BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply { inSampleSize = sample })?.also { cache.put(key,it) }
            }
        }
    }

    private fun hasVisibleContent(bitmap: Bitmap): Boolean {
        var visible = 0
        for (y in 0 until bitmap.height step maxOf(1,bitmap.height / 128))
            for (x in 0 until bitmap.width step maxOf(1,bitmap.width / 64))
                if (Color.alpha(bitmap.getPixel(x,y)) > 40) visible++
        return visible >= 80
    }

    private fun hasTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        var transparent = 0
        for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 128))
            for (x in 0 until bitmap.width step maxOf(1, bitmap.width / 64))
                if (Color.alpha(bitmap.getPixel(x, y)) < 250) transparent++
        return transparent >= 80
    }
}
