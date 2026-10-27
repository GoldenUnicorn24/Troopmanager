package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Per image set: who supplied it and how each kind was aligned (for "neu ausrichten"). */
@Serializable
data class CustomImageMeta(
    val source: String = "",
    val created: Long = 0L,
    val portraitCrop: CharacterImageCrop? = null,
    val fullbodyCrop: CharacterImageCrop? = null,
)

/** One stored image set in the app-private library. */
data class CustomImageSet(val id: String, val hasPortrait: Boolean, val hasFullbody: Boolean, val created: Long, val source: String)

/** Offline, bounded image IO. Call from Dispatchers.IO, never from a Canvas draw callback. */
class CustomCharacterImages(private val context: Context) {
    companion object {
        const val MAX_BYTES = 15 * 1024 * 1024
        /** Longest side of the kept original; enough to re-crop at full quality, bounded on disk. */
        private const val ORIGINAL_MAX = 2560
        const val DEFAULT_SOURCE = "Eigenes Bild, vom Spieler importiert; Bildrechte und Volljährigkeit bestätigt."
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
        /** Image directories are immutable, so a missing kind stays missing for an id. */
        private val missing = java.util.Collections.synchronizedSet(mutableSetOf<String>())
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

    private fun validId(id: String) = id.matches(Regex("[a-zA-Z0-9-]{1,80}"))
    private fun root() = File(context.filesDir, "custom")

    fun file(id: String, kind: CharacterImageKind): File {
        require(validId(id)) { "Ungültige Bild-ID." }
        return File(context.filesDir, "custom/$id/${if (kind == CharacterImageKind.PORTRAIT) "portrait" else "fullbody"}.webp")
    }
    private fun originalFile(id: String, kind: CharacterImageKind) =
        File(file(id, kind).parentFile, if (kind == CharacterImageKind.PORTRAIT) "original_portrait.webp" else "original_fullbody.webp")
    private fun metaFile(id: String) = File(file(id, CharacterImageKind.PORTRAIT).parentFile, "meta.json")

    fun has(id: String?, kind: CharacterImageKind): Boolean = id != null && validId(id) && file(id, kind).isFile

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
        require(legacy || (w >= kind.minWidth && h >= kind.minHeight)) { "Das Bild braucht mindestens ${kind.minWidth} × ${kind.minHeight} Pixel nach Ausrichtung (dieses Bild: $w × $h)." }
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
            error("Ganzkörperbilder brauchen einen transparenten Hintergrund. Dieses Bild ist vollständig deckend " +
                "(z. B. ein normales Foto). Bitte den Hintergrund zuerst entfernen – etwa mit einer Freistellungs-App – " +
                "und als PNG oder WebP mit Transparenz speichern. Die App stellt Bilder nicht automatisch frei.")
        }
        return oriented
    }

    fun crop(bitmap: Bitmap, kind: CharacterImageKind, crop: CharacterImageCrop): Bitmap {
        require(crop.zoom.isFinite() && crop.zoom in CharacterImageCrop.MIN_ZOOM..CharacterImageCrop.MAX_ZOOM && crop.x.isFinite() && crop.y.isFinite() && crop.rotation.isFinite()) { "Ungültiger Zuschnitt." }
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
    fun install(portrait: Bitmap?, fullbody: Bitmap? = null, source: String = DEFAULT_SOURCE,
                originals: Map<CharacterImageKind, Pair<Bitmap, CharacterImageCrop>> = emptyMap()): String {
        require(portrait != null || fullbody != null) { "Bitte mindestens ein Porträt oder ein Ganzkörperbild wählen." }
        require(portrait == null || (portrait.width == 1024 && portrait.height == 1280 && hasVisibleContent(portrait))) { "Das Porträt braucht eine sichtbare Figur im Standardformat 1024 × 1280." }
        require(fullbody == null || (fullbody.width == 768 && fullbody.height == 1536 && hasVisibleContent(fullbody) && hasTransparency(fullbody))) { "Das Ganzkörperbild braucht eine sichtbare Figur, transparente Ränder und 768 × 1536 Pixel." }
        return stage { staging ->
            portrait?.let { write(it, File(staging, "portrait.webp")) }
            fullbody?.let { write(it, File(staging, "fullbody.webp")) }
            originals.forEach { (kind, original) ->
                if ((kind == CharacterImageKind.PORTRAIT && portrait != null) || (kind == CharacterImageKind.FULLBODY && fullbody != null))
                    writeOriginal(original.first, File(staging, if (kind == CharacterImageKind.PORTRAIT) "original_portrait.webp" else "original_fullbody.webp"))
            }
            writeMeta(File(staging, "meta.json"), CustomImageMeta(source.take(2000), System.currentTimeMillis(),
                originals[CharacterImageKind.PORTRAIT]?.second?.takeIf { portrait != null },
                originals[CharacterImageKind.FULLBODY]?.second?.takeIf { fullbody != null }))
        }
    }

    /**
     * Portrait and full body are managed separately: replacing or removing one kind creates a new
     * image set that copies the other kind byte for byte. Returns null when nothing is left.
     */
    fun derive(baseId: String?, kind: CharacterImageKind, image: Bitmap?, original: Pair<Bitmap, CharacterImageCrop>? = null,
               source: String? = null): String? {
        val other = if (kind == CharacterImageKind.PORTRAIT) CharacterImageKind.FULLBODY else CharacterImageKind.PORTRAIT
        val keepOther = baseId != null && has(baseId, other)
        if (image == null && !keepOther) return null
        if (image != null) require(image.width == kind.width && image.height == kind.height && hasVisibleContent(image) &&
            (kind == CharacterImageKind.PORTRAIT || hasTransparency(image))) { "Das Bild entspricht nicht dem Standardformat ${kind.width} × ${kind.height}." }
        val baseMeta = baseId?.let { meta(it) }
        return stage { staging ->
            if (keepOther) {
                file(baseId!!, other).copyTo(File(staging, file(baseId, other).name))
                originalFile(baseId, other).takeIf { it.isFile }?.copyTo(File(staging, originalFile(baseId, other).name))
            }
            image?.let { write(it, File(staging, file("x", kind).name)) }
            if (image != null && original != null) writeOriginal(original.first, File(staging, originalFile("x", kind).name))
            val crop = if (image != null) original?.second else null
            writeMeta(File(staging, "meta.json"), CustomImageMeta(
                (source ?: baseMeta?.source ?: DEFAULT_SOURCE).take(2000), System.currentTimeMillis(),
                if (kind == CharacterImageKind.PORTRAIT) crop else baseMeta?.portraitCrop.takeIf { keepOther },
                if (kind == CharacterImageKind.FULLBODY) crop else baseMeta?.fullbodyCrop.takeIf { keepOther },
            ))
        }
    }

    /** A copy keeps the library entry and the person's image independent of each other. */
    fun copy(id: String): String {
        require(has(id, CharacterImageKind.PORTRAIT) || has(id, CharacterImageKind.FULLBODY)) { "Dieses Bild ist nicht mehr vorhanden." }
        return stage { staging ->
            file(id, CharacterImageKind.PORTRAIT).parentFile!!.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(staging, it.name)) }
        }
    }

    private fun stage(fill: (File) -> Unit): String {
        val id = UUID.randomUUID().toString()
        val root = root().apply { check(mkdirs() || isDirectory) }
        val staging = File(root, ".$id").apply { check(mkdir()) }
        try {
            fill(staging)
            check(staging.renameTo(File(root, id))) { "Der Bildimport konnte nicht abgeschlossen werden." }
            missing.removeIf { it.startsWith("$id:") }
            return id
        } finally { staging.deleteRecursively() }
    }

    private fun write(bitmap: Bitmap, target: File, quality: Int = 90) {
        target.outputStream().use {
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            check(bitmap.compress(format, quality, it)) { "Das Bild konnte nicht gespeichert werden." }
            it.fd.sync()
        }
    }

    private fun writeOriginal(bitmap: Bitmap, target: File) {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest <= ORIGINAL_MAX) bitmap
            else Bitmap.createScaledBitmap(bitmap, bitmap.width * ORIGINAL_MAX / longest, bitmap.height * ORIGINAL_MAX / longest, true)
        try { write(scaled, target, 92) } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    private fun writeMeta(target: File, meta: CustomImageMeta) = target.writeText(json.encodeToString(CustomImageMeta.serializer(), meta))

    fun meta(id: String): CustomImageMeta? = runCatching {
        json.decodeFromString(CustomImageMeta.serializer(), metaFile(id).takeIf { it.isFile && it.length() < 64 * 1024 }!!.readText())
    }.getOrNull()

    /** The kept original and its last crop; older imports fall back to the normalized image itself. */
    fun original(id: String, kind: CharacterImageKind): Pair<Bitmap, CharacterImageCrop>? {
        if (!has(id, kind)) return null
        val stored = originalFile(id, kind)
        val bitmap = BitmapFactory.decodeFile((if (stored.isFile) stored else file(id, kind)).path,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        val crop = if (stored.isFile) meta(id)?.let { if (kind == CharacterImageKind.PORTRAIT) it.portraitCrop else it.fullbodyCrop } else null
        return bitmap to (crop ?: CharacterImageCrop())
    }

    /** All stored sets, newest first. Hidden staging directories are skipped. */
    fun library(): List<CustomImageSet> = root().listFiles().orEmpty()
        .filter { it.isDirectory && validId(it.name) && !it.name.startsWith(".") }
        .map { dir ->
            val meta = meta(dir.name)
            CustomImageSet(dir.name, File(dir, "portrait.webp").isFile, File(dir, "fullbody.webp").isFile,
                meta?.created?.takeIf { it > 0 } ?: dir.lastModified(), meta?.source.orEmpty())
        }
        .filter { it.hasPortrait || it.hasFullbody }
        .sortedByDescending { it.created }

    private fun key(id: String, kind: CharacterImageKind, sample: Int) = "${file(id, kind).absolutePath}:$sample"
    private fun sampleFor(kind: CharacterImageKind, targetWidth: Int) =
        CharacterImageFrames.sampleSize(kind.width, kind.height, targetWidth, targetWidth * kind.height / kind.width)

    /** Memory-only lookup for the first frame. */
    fun peek(id: String?, kind: CharacterImageKind, targetWidth: Int = 512): Bitmap? {
        if (id == null || !validId(id) || targetWidth !in 1..4096) return null
        return synchronized(cache) { cache.get(key(id, kind, sampleFor(kind, targetWidth))) }
    }
    /** True when this id is known not to contain the kind (no disk access). */
    fun knownMissing(id: String?, kind: CharacterImageKind) = id == null || !validId(id) || "$id:$kind" in missing

    fun load(id: String?, kind: CharacterImageKind, targetWidth: Int = 512): Bitmap? {
        if (id == null) return null
        require(targetWidth in 1..4096)
        val source = runCatching { file(id,kind) }.getOrNull() ?: return null
        val sample = sampleFor(kind, targetWidth)
        val key = "${source.absolutePath}:$sample"
        return synchronized(cache) {
            cache.get(key) ?: run {
                if (!source.isFile || source.length() > MAX_BYTES) { missing += "$id:$kind"; return@run null }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path,bounds)
                if (bounds.outWidth != kind.width || bounds.outHeight != kind.height) { missing += "$id:$kind"; return@run null }
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
