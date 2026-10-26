package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class CharacterPackManifest(
    val format: String,
    val version: Int,
    val character: CustomCharacterDefinition,
    val source: String,
    val portrait: String,
    val fullbody: String? = null,
) {
    fun validate() {
        require(format == "troopmanager-character") { "Dies ist kein Troopmanager-Charakterpaket." }
        require(version == 1) { "Unbekannte Charakterpaket-Version $version. Benötigt wird Version 1." }
        character.validate()
        require(character.appearance.customImageId == null) { "Ein Paket darf keine internen Bild-IDs enthalten." }
        require(source.isNotBlank() && source.length <= 2000) { "Herkunft oder Lizenz des Bildes fehlt." }
        require(portrait == "portrait.webp" && (fullbody == null || fullbody == "fullbody.webp")) { "Ungültige Bildpfade im Paket." }
    }
}

data class PackImageInfo(val width: Int, val height: Int, val mime: String, val transparent: Boolean = false)
data class CharacterPack(val manifest: CharacterPackManifest, val images: Map<String, ByteArray>)

/** No Android dependencies: parses untrusted ZIPs without extracting a path to disk. */
object CharacterPackCodec {
    const val MAX_ZIP = 32 * 1024 * 1024
    const val MAX_IMAGE = 15 * 1024 * 1024
    const val MAX_MANIFEST = 64 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val allowed = setOf("manifest.json", "portrait.webp", "fullbody.webp")

    fun parseManifest(raw: String): CharacterPackManifest {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST) { "Das Manifest ist zu groß." }
        var depth = 0
        var quoted = false
        var escaped = false
        raw.forEach { c ->
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "Das Manifest ist zu tief verschachtelt." } }
                '}', ']' -> { depth--; require(depth >= 0) { "Das Manifest ist ungültig." } }
            }
        }
        val root = json.parseToJsonElement(raw) as? JsonObject ?: error("Das Manifest muss ein JSON-Objekt sein.")
        val version = root["version"] as? JsonPrimitive
        require(version != null && !version.isString && version.intOrNull != null) { "Die Paketversion muss als ganze Zahl angegeben werden." }
        val age = (root["character"] as? JsonObject)?.get("age") as? JsonPrimitive
        require(age != null && !age.isString && age.intOrNull != null) { "Das Alter muss als ganze Zahl angegeben werden." }
        return json.decodeFromJsonElement<CharacterPackManifest>(root).also { it.validate() }
    }

    fun read(input: InputStream, inspect: (String, ByteArray) -> PackImageInfo): CharacterPack {
        val archive = readLimited(input, MAX_ZIP)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in allowed) { "Unzulässiger ZIP-Eintrag: ${entry.name.take(120)}" }
                require(entry.name !in entries) { "Doppelter ZIP-Eintrag: ${entry.name}" }
                val limit = if (entry.name == "manifest.json") MAX_MANIFEST else MAX_IMAGE
                entries[entry.name] = readLimited(zip, limit, close = false)
                zip.closeEntry()
            }
        }
        val raw = entries.remove("manifest.json") ?: error("manifest.json fehlt im Paket.")
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        val manifest = parseManifest(decoder.decode(java.nio.ByteBuffer.wrap(raw)).toString())
        val pack = CharacterPack(manifest, entries)
        validateImages(pack, inspect)
        return pack
    }

    fun validateImages(pack: CharacterPack, inspect: (String, ByteArray) -> PackImageInfo) {
        pack.manifest.validate()
        val expected = setOfNotNull(pack.manifest.portrait, pack.manifest.fullbody)
        require(pack.images.keys == expected) { "Bilddateien fehlen oder sind nicht im Manifest aufgeführt." }
        expected.forEach { path ->
            val bytes = pack.images.getValue(path)
            require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE) { "Das Bild $path ist leer oder größer als 15 MB." }
            val info = inspect(path, bytes)
            val kind = if (path == "portrait.webp") CharacterImageKind.PORTRAIT else CharacterImageKind.FULLBODY
            require(info.mime == "image/webp" && info.width == kind.width && info.height == kind.height) {
                "$path muss WebP mit ${kind.width} × ${kind.height} Pixeln sein."
            }
            require(kind != CharacterImageKind.FULLBODY || info.transparent) { "fullbody.webp braucht Transparenz." }
        }
    }

    fun write(pack: CharacterPack, inspect: (String, ByteArray) -> PackImageInfo): ByteArray {
        validateImages(pack, inspect)
        val output = ByteArrayOutputStream()
        val manifest = json.encodeToString(pack.manifest).toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST)
        ZipOutputStream(output).use { zip ->
            (mapOf("manifest.json" to manifest) + pack.images.toSortedMap()).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray().also { require(it.size <= MAX_ZIP) { "Das Paket überschreitet 32 MB." } }
    }

    fun readLimited(input: InputStream, limit: Int, close: Boolean = true): ByteArray {
        try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "Die Datei überschreitet die erlaubte Größe (${limit / 1024} KiB)." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally { if (close) input.close() }
    }
}
