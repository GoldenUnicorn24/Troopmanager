package com.goldenunicorn.troopmanager.data

import com.goldenunicorn.troopmanager.model.CustomCharacterDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CharacterPackCodecTest {
    private val manifest = CharacterPackManifest("troopmanager-character", 1, CustomCharacterDefinition("Mira", 27), "Eigene Testgrafik, CC0-1.0", "portrait.webp")
    private val inspect: (String, ByteArray) -> PackImageInfo = { name, _ -> if (name == "portrait.webp") PackImageInfo(1024,1280,"image/webp") else PackImageInfo(768,1536,"image/webp",true) }
    private fun zip(m: CharacterPackManifest = manifest, files: Map<String, ByteArray> = mapOf("portrait.webp" to byteArrayOf(1))): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            (mapOf("manifest.json" to Json.encodeToString(m).toByteArray()) + files).forEach { (name, content) -> out.putNextEntry(ZipEntry(name)); out.write(content); out.closeEntry() }
        }
        return bytes.toByteArray()
    }
    private fun rejects(bytes: ByteArray, probe: (String, ByteArray) -> PackImageInfo = inspect) {
        try { CharacterPackCodec.read(bytes.inputStream(), probe); fail("Invalid package was accepted") }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }
    @Test fun roundTripKeepsIdentitySourceAndImageBytes() {
        val pack = CharacterPack(manifest, mapOf("portrait.webp" to byteArrayOf(1,2,3)))
        val imported = CharacterPackCodec.read(CharacterPackCodec.write(pack, inspect).inputStream(), inspect)
        assertEquals(manifest, imported.manifest)
        assertArrayEquals(pack.images.getValue("portrait.webp"), imported.images.getValue("portrait.webp"))
    }
    @Test fun futureVersionIsRejected() = rejects(zip(manifest.copy(version = 2)))
    @Test fun wrongFormatIsRejected() = rejects(zip(manifest.copy(format = "other")))
    @Test fun missingPortraitIsRejected() = rejects(zip(files = emptyMap()))
    @Test fun minorIsRejectedBeforeImageDecode() = rejects(zip(manifest.copy(character = manifest.character.copy(age = 17)))) { _, _ -> fail("Must validate age first"); inspect("portrait.webp",byteArrayOf(1)) }
    @Test fun tooSmallImageIsRejected() = rejects(zip()) { _, _ -> PackImageInfo(511,639,"image/webp") }
    @Test fun disguisedJpegIsRejected() = rejects(zip()) { _, _ -> PackImageInfo(1024,1280,"image/jpeg") }
    @Test fun missingOptionalImageIsRejectedWhenDeclared() = rejects(zip(manifest.copy(fullbody = "fullbody.webp")))
    @Test fun unlistedImageIsRejected() = rejects(zip(files = mapOf("portrait.webp" to byteArrayOf(1),"fullbody.webp" to byteArrayOf(2))))
    @Test fun opaqueFullbodyIsRejected() = rejects(zip(manifest.copy(fullbody = "fullbody.webp"),mapOf("portrait.webp" to byteArrayOf(1),"fullbody.webp" to byteArrayOf(2)))) { name, _ ->
        if (name == "portrait.webp") PackImageInfo(1024,1280,"image/webp") else PackImageInfo(768,1536,"image/webp",false)
    }
    @Test fun traversalEntryIsRejected() = rejects(zip(files = mapOf("../portrait.webp" to byteArrayOf(1))))
    @Test fun unknownManifestFieldsAreRejected() {
        try { CharacterPackCodec.parseManifest(Json.encodeToString(manifest).dropLast(1) + ",\"gold\":100}"); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun ageIsMandatoryAndCannotBeAString() {
        val raw = Json.encodeToString(manifest)
        listOf(raw.replace("\"age\":27", "\"age\":\"27\""),raw.replace(",\"age\":27", "")).forEach {
            try { CharacterPackCodec.parseManifest(it); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun compressedOversizedEntryIsRejected() = rejects(zip(files = mapOf("portrait.webp" to ByteArray(CharacterPackCodec.MAX_IMAGE + 1))))
}
