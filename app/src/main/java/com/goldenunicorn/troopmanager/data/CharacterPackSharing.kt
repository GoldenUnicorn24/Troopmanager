package com.goldenunicorn.troopmanager.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.goldenunicorn.troopmanager.model.CustomCharacter
import java.io.File
import java.io.InputStream

data class ImportedCharacterPack(val manifest: CharacterPackManifest, val portrait: Bitmap, val fullbody: Bitmap?)

/** Performs all validation before installing a new internal directory or changing the game. */
class CharacterPackSharing(private val context: Context) {
    private val images = CustomCharacterImages(context)

    private fun decode(name: String, bytes: ByteArray): Pair<PackImageInfo, Bitmap> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val kind = if (name == "portrait.webp") CharacterImageKind.PORTRAIT else CharacterImageKind.FULLBODY
        require(bounds.outWidth == kind.width && bounds.outHeight == kind.height && bounds.outMimeType == "image/webp") {
            "$name braucht WebP mit ${kind.width} × ${kind.height} Pixeln."
        }
        val bitmap = images.decode(bytes, kind)
        return PackImageInfo(bitmap.width, bitmap.height, bounds.outMimeType, kind == CharacterImageKind.FULLBODY) to bitmap
    }

    fun read(uri: Uri): ImportedCharacterPack {
        val decoded = mutableMapOf<String, Bitmap>()
        try {
            val pack = CharacterPackCodec.read(context.contentResolver.openInputStream(uri) ?: error("Das Paket ist nicht lesbar.")) { name, bytes ->
                val (info, bitmap) = decode(name, bytes)
                decoded[name] = bitmap
                info
            }
            return ImportedCharacterPack(pack.manifest, decoded.getValue("portrait.webp"), decoded["fullbody.webp"])
        } catch (e: Exception) {
            decoded.values.forEach { it.recycle() }
            throw e
        }
    }

    fun prepareShare(character: CustomCharacter): Intent {
        character.validate()
        val manifest = CharacterPackManifest("troopmanager-character", 1,
            character.definition.copy(appearance = character.definition.appearance.copy(customImageId = null)),
            character.source, "portrait.webp", character.fullbody)
        val payload = setOfNotNull("portrait.webp", character.fullbody).associateWith { name ->
            val kind = if (name == "portrait.webp") CharacterImageKind.PORTRAIT else CharacterImageKind.FULLBODY
            val file = images.file(character.id, kind)
            require(file.isFile) { "Das Bild $name fehlt. Bitte vor dem Export erneut importieren." }
            CharacterPackCodec.readLimited(file.inputStream(), CharacterPackCodec.MAX_IMAGE)
        }
        val zip = CharacterPackCodec.write(CharacterPack(manifest, payload)) { name, bytes ->
            val (info, bitmap) = decode(name, bytes)
            bitmap.recycle()
            info
        }
        val folder = File(context.cacheDir, "character-packs").apply { check(mkdirs() || isDirectory) }
        val file = File(folder, "${character.id}.zip")
        val temp = File(folder, "${character.id}.tmp")
        try { temp.writeBytes(zip); check(temp.renameTo(file)) { "Das Paket konnte nicht gespeichert werden." } }
        finally { temp.delete() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.characterpacks", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Charakterpaket", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
