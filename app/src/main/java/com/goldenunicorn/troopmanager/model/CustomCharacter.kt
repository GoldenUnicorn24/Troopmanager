package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

/** Portable identity; no relationships or campaign state can be injected by a character pack. */
@Serializable
data class CustomCharacterDefinition(
    val name: String,
    val age: Int,
    val gender: Gender = Gender.FEMALE,
    val species: Species = Species.HUMAN,
    val culture: Culture = Culture.HUMAN,
    val origin: String = "",
    val profession: String = "Reisende",
    val personality: NpcPersonality = NpcPersonality(),
    val interests: List<String> = emptyList(),
    val role: NpcRole = NpcRole.REGULAR,
    val homeProvinceId: String = "gm_rabenfurt",
    val romanceable: Boolean = false,
    val appearance: CharacterAppearance = CharacterAppearance(),
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 80) { "Bitte einen Namen mit höchstens 80 Zeichen angeben." }
        require(age in 18..10000) { "Alle Figuren müssen erwachsen sein (Alter mindestens 18)." }
        require(Towns.byProvince(homeProvinceId) != null) { "Unbekannte Heimatstadt." }
        require(origin.length <= 2000 && profession.isNotBlank() && profession.length <= 120) { "Herkunft oder Beruf ist ungültig." }
        require(interests.size <= 20 && interests.all { it.isNotBlank() && it.length <= 120 }) { "Ungültige Interessen." }
        require(personality == personality.normalized()) { "Persönlichkeitswerte müssen zwischen 0 und 100 liegen." }
        require(appearance == appearance.normalized()) { "Ungültiges Aussehen." }
    }
}

/** Paths are derived from a validated id, never accepted from a ZIP or content provider. */
@Serializable
data class CustomCharacter(
    val id: String,
    val target: String,
    val definition: CustomCharacterDefinition,
    val source: String,
    /** Null only for a full-body-only set; the standard portrait is used then. */
    val portrait: String? = "portrait.webp",
    val fullbody: String? = null,
) {
    fun validate() {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Ungültige Bild-ID." }
        require(target.length in 1..200) { "Ungültige Personenzuordnung." }
        definition.validate()
        require(source.isNotBlank() && source.length <= 2000) { "Bitte Herkunft und Nutzungsrechte angeben." }
        require((portrait == "portrait.webp" || (portrait == null && fullbody != null)) && (fullbody == null || fullbody == "fullbody.webp")) { "Ungültiger interner Bildpfad." }
    }
}

/** Save-stable URI for engines that do not know Android's per-installation filesDir. */
object CustomImageReference {
    fun uri(id: String): String {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return "custom://$id/portrait"
    }
    fun idFromUri(raw: String?): String? = runCatching {
        val uri = java.net.URI(raw ?: return@runCatching null)
        val id = when (uri.scheme) {
            "custom" -> uri.host?.takeIf { uri.path == "/portrait" }
            "file" -> java.io.File(uri).takeIf { it.name == "portrait.webp" && it.parentFile?.parentFile?.name == "custom" }?.parentFile?.name
            else -> null
        }
        id?.takeIf { it.matches(Regex("[a-zA-Z0-9-]{1,80}")) }
    }.getOrNull()
}
