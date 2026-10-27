package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object CustomCharacterEngine {
    fun apply(state: GameState, character: CustomCharacter, portraitUri: String): GameState {
        character.validate()
        if (character.target == "commander:$COMPANION_COMMANDER_ID" && state.companion.met)
            return apply(state,character.copy(target = "companion"),portraitUri)
        val previousImageId = imageIdForTarget(state,character.target) ?: state.customCharacters.firstOrNull { it.target == character.target }?.id
        require(state.customCharacters.size < 200 || state.customCharacters.any { it.target == character.target || it.id == previousImageId }) { "Höchstens 200 eigene Charaktere." }
        val npcIdentityId = npcForTarget(state,character.target)
        val d = character.definition
        val look = d.appearance.copy(customImageId = character.id)
        var next = state.copy(customCharacters = state.customCharacters.filterNot { it.target == character.target || it.id == previousImageId } + character)
        next = when {
            character.target == "player" -> next.copy(
                player = next.player.copy(name = d.name, age = d.age, species = d.species, portraitUri = portraitUri),
                life = next.life.copy(gender = d.gender, culture = d.culture, origin = d.origin, profession = d.profession, homeProvinceId = d.homeProvinceId, appearance = look),
                crown = next.crown.copy(playerLook = look.toPortraitSpec()),
            )
            character.target == "companion" -> {
                val npcId = next.companion.npcIdentityId ?: next.life.companionNpcId?.takeIf { !next.companion.met } ?: "custom-companion-${character.id}"
                val original = next.life.npc(npcId) ?: NpcEngine.generate(npcId,d.role,d.homeProvinceId).copy(departed = next.life.landed)
                val npc = identity(original,d,look)
                val partner = next.crown.saga.partner
                val updated = next.copy(companion = next.companion.copy(name = d.name, age = d.age, species = d.species, portraitUri = portraitUri,npcIdentityId = npcId),
                    life = next.life.copy(companionNpcId = npcId, npcs = next.life.npcs.filterNot { it.id == npcId } + npc),
                    crown = next.crown.copy(saga = next.crown.saga.copy(partner = if (partner != null && partner.background == next.crown.saga.companionId)
                        partner.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri,look = look.toPortraitSpec(),npcIdentityId = npcId) else partner)))
                if (updated.companion.met) RelationshipEngine.syncCommander(updated) else updated
            }
            character.target.startsWith("commander:") -> {
                val id = character.target.substringAfter(':').toLongOrNull()
                require(next.commanders.any { it.id == id }) { "Dieser Kommandant existiert nicht mehr." }
                next.copy(commanders = next.commanders.map { if (it.id == id) it.copy(name = d.name, culture = d.culture, portraitUri = portraitUri) else it })
            }
            character.target.startsWith("partner:") -> {
                val partner = next.crown.saga.partner ?: error("Die Partnerin existiert nicht mehr.")
                require(partner.background == character.target.substringAfter(':'))
                val updated = next.copy(crown = next.crown.copy(saga = next.crown.saga.copy(partner = partner.copy(name = d.name, age = d.age, species = d.species, portraitUri = portraitUri, look = look.toPortraitSpec()))))
                if (next.companion.met && next.crown.saga.companionId == partner.background)
                    RelationshipEngine.syncCommander(updated.copy(companion = updated.companion.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri)))
                else updated
            }
            character.target.startsWith("npc:") -> {
                val id = character.target.removePrefix("npc:")
                val original = next.life.npc(id) ?: NpcEngine.generate(id, d.role, d.homeProvinceId)
                val npc = identity(original,d,look)
                next.copy(life = next.life.copy(npcs = next.life.npcs.filterNot { it.id == id } + npc))
            }
            else -> error("Unbekannte Personenzuordnung.")
        }
        if (previousImageId != null) {
            fun shared(uri: String?) = CustomImageReference.idFromUri(uri) == previousImageId
            val partner = next.crown.saga.partner
            next = next.copy(
                player = if (shared(next.player.portraitUri)) next.player.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri) else next.player,
                life = next.life.copy(appearance = next.life.appearance?.let { if (it.customImageId == previousImageId) look else it },
                    npcs = next.life.npcs.map { if (it.appearance.customImageId == previousImageId) identity(it,d,look) else it }),
                companion = if (shared(next.companion.portraitUri)) next.companion.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri) else next.companion,
                commanders = next.commanders.map { if (shared(it.portraitUri)) it.copy(name = d.name,culture = d.culture,portraitUri = portraitUri) else it },
                crown = next.crown.copy(playerLook = next.crown.playerLook?.let { if (it.customImageId == previousImageId) look.toPortraitSpec() else it },
                    saga = next.crown.saga.copy(partner = if (partner != null && (shared(partner.portraitUri) || partner.look?.customImageId == previousImageId))
                    partner.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri,look = look.toPortraitSpec()) else partner)),
            )
        }
        if (npcIdentityId != null) {
            val partner = next.crown.saga.partner
            next = next.copy(life = next.life.copy(npcs = next.life.npcs.map { if (it.id == npcIdentityId) identity(it,d,look) else it }),
                companion = if (next.companion.npcIdentityId == npcIdentityId) next.companion.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri) else next.companion,
                commanders = next.commanders.map { if (it.npcIdentityId == npcIdentityId) it.copy(name = d.name,culture = d.culture,portraitUri = portraitUri) else it },
                crown = next.crown.copy(saga = next.crown.saga.copy(partner = if (partner != null && partner.npcIdentityId == npcIdentityId)
                    partner.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri,look = look.toPortraitSpec()) else partner)))
        }
        val mirroredCommanders = next.commanders.filter { CustomImageReference.idFromUri(it.portraitUri) == character.id }.map { it.id }.toSet()
        if (mirroredCommanders.isNotEmpty()) next = next.copy(court = next.court.copy(characters = next.court.characters.map {
            if (it.commanderId in mirroredCommanders) it.copy(age = d.age,origin = d.origin) else it
        }))
        return next
    }

    /** Image files of one set; the UI looks them up, the engine stays free of Android IO. */
    data class ImageFiles(val portrait: Boolean, val fullbody: Boolean, val source: String)

    /**
     * Applies a look edited in the character editor (player) including its own image set.
     * [state] is the state *before* the edit, so the previous image can be released correctly.
     * Relationship values, memories and all other person data are untouched.
     */
    fun syncEditorImage(state: GameState, target: String, look: CharacterAppearance, files: ImageFiles?): GameState {
        require(target == "player") { "Nur die Spielerfigur wird direkt im Editor gespeichert." }
        val normalized = look.normalized()
        val previous = imageIdForTarget(state, target)
        fun withLook(s: GameState) = s.copy(life = s.life.copy(appearance = normalized), crown = s.crown.copy(playerLook = normalized.toPortraitSpec()))
        val id = normalized.customImageId
        if (id == previous) return withLook(state)
        if (id == null) {
            val cleared = clearImage(state, previous)
            return withLook(cleared.copy(customCharacters = cleared.customCharacters.filterNot { it.target == target || it.id == previous }))
        }
        val info = files ?: ImageFiles(portrait = true, fullbody = false, source = DEFAULT_SOURCE)
        val definition = CustomCharacterDefinition(state.player.name, state.player.age, state.life.gender, state.player.species,
            state.life.culture, state.life.origin.take(2000), state.life.profession.ifBlank { "Reisende" },
            homeProvinceId = state.life.homeProvinceId.takeIf { Towns.byProvince(it) != null } ?: Towns.all.first().provinceId,
            appearance = normalized.copy(customImageId = null))
        val record = CustomCharacter(id, target, definition, info.source.ifBlank { DEFAULT_SOURCE },
            portrait = if (info.portrait) "portrait.webp" else null, fullbody = if (info.fullbody) "fullbody.webp" else null)
        val applied = apply(state, record, CustomImageReference.uri(id))
        // A full-body-only set has no portrait file; portrait screens keep the standard face.
        return if (info.portrait) applied else applied.copy(player = applied.player.copy(portraitUri = null))
    }

    /**
     * Registers an image chosen during character creation for a designed person (the future
     * companion). Only the picture and its record are added; the person, her routine, relations
     * and the heroine saga stay exactly as the world generated them.
     */
    fun registerDesignedImage(state: GameState, npcId: String, imageId: String, files: ImageFiles): GameState {
        val npc = state.life.npc(npcId) ?: return state
        val look = npc.appearance.copy(customImageId = imageId)
        val record = CustomCharacter(imageId, "npc:$npcId", definition(npc), files.source.ifBlank { DEFAULT_SOURCE },
            portrait = if (files.portrait) "portrait.webp" else null, fullbody = if (files.fullbody) "fullbody.webp" else null)
        record.validate()
        var next = state.copy(customCharacters = state.customCharacters.filterNot { it.target == record.target || it.id == imageId } + record,
            life = state.life.copy(npcs = state.life.npcs.map { if (it.id == npcId) it.copy(appearance = look) else it }))
        val partner = next.crown.saga.partner
        if (partner != null && partner.npcIdentityId == npcId) next = next.copy(crown = next.crown.copy(saga = next.crown.saga.copy(
            partner = partner.copy(look = look.toPortraitSpec(), portraitUri = if (files.portrait) CustomImageReference.uri(imageId) else partner.portraitUri))))
        return next
    }

    const val DEFAULT_SOURCE = "Eigenes Bild, vom Spieler importiert; Bildrechte und Volljährigkeit bestätigt."

    private fun npcForTarget(state: GameState,target: String): String? = when {
        target.startsWith("npc:") -> target.removePrefix("npc:")
        target == "companion" -> state.companion.npcIdentityId ?: state.life.companionNpcId?.takeIf { !state.companion.met }
        target.startsWith("commander:") -> state.commanders.firstOrNull { it.id == target.substringAfter(':').toLongOrNull() }?.npcIdentityId
        target.startsWith("partner:") -> state.crown.saga.partner?.takeIf { it.background == target.substringAfter(':') }?.npcIdentityId
        else -> null
    }

    fun imageIdForTarget(state: GameState, target: String): String? = (when {
        target == "player" -> state.life.appearance?.customImageId ?: CustomImageReference.idFromUri(state.player.portraitUri) ?: state.crown.playerLook?.customImageId
        target == "companion" -> CustomImageReference.idFromUri(state.companion.portraitUri)
        target.startsWith("npc:") -> state.life.npc(target.removePrefix("npc:"))?.appearance?.customImageId
        target.startsWith("commander:") -> CustomImageReference.idFromUri(state.commanders.firstOrNull { it.id == target.substringAfter(':').toLongOrNull() }?.portraitUri)
        target.startsWith("partner:") -> state.crown.saga.partner?.takeIf { it.background == target.substringAfter(':') }?.let { CustomImageReference.idFromUri(it.portraitUri) ?: it.look?.customImageId }
        else -> null
    }) ?: npcForTarget(state,target)?.let { state.life.npc(it)?.appearance?.customImageId }

    /** Clear the current image everywhere while preserving immutable image files for old saves. */
    fun clearImage(state: GameState, id: String?): GameState {
        if (id == null) return state
        fun uri(value: String?) = value?.takeUnless { CustomImageReference.idFromUri(it) == id }
        fun appearance(value: CharacterAppearance?) = value?.let { if (it.customImageId == id) it.copy(customImageId = null) else it }
        fun look(value: PortraitSpec?) = value?.let { if (it.customImageId == id) it.copy(customImageId = null) else it }
        return state.copy(player = state.player.copy(portraitUri = uri(state.player.portraitUri)),
            companion = state.companion.copy(portraitUri = uri(state.companion.portraitUri)),
            commanders = state.commanders.map { it.copy(portraitUri = uri(it.portraitUri)) },
            life = state.life.copy(appearance = appearance(state.life.appearance),npcs = state.life.npcs.map { it.copy(appearance = appearance(it.appearance)!!) }),
            crown = state.crown.copy(playerLook = look(state.crown.playerLook),saga = state.crown.saga.copy(partner = state.crown.saga.partner?.let {
                it.copy(portraitUri = uri(it.portraitUri),look = look(it.look))
            })))
    }

    fun currentDefinition(state: GameState, record: CustomCharacter): CustomCharacterDefinition {
        val d = record.definition
        return when {
            record.target == "player" -> d.copy(name = state.player.name,age = state.player.age,species = state.player.species,
                gender = state.life.gender,culture = state.life.culture,origin = state.life.origin,profession = state.life.profession,
                homeProvinceId = state.life.homeProvinceId,appearance = (state.life.appearance ?: d.appearance).copy(customImageId = null))
            record.target == "companion" -> (state.life.companion?.let(::definition) ?: d).copy(name = state.companion.name,age = state.companion.age,species = state.companion.species)
            record.target.startsWith("npc:") -> state.life.npc(record.target.removePrefix("npc:"))?.let(::definition) ?: d
            record.target.startsWith("commander:") -> state.commanders.firstOrNull { it.id == record.target.substringAfter(':').toLongOrNull() }?.let { c ->
                val details = state.court.characters.firstOrNull { it.commanderId == c.id }
                d.copy(name = c.name,culture = c.culture,age = details?.age ?: d.age,origin = details?.origin ?: d.origin)
            } ?: d
            record.target.startsWith("partner:") -> state.crown.saga.partner?.takeIf { it.background == record.target.substringAfter(':') }?.let { d.copy(name = it.name,age = it.age,species = it.species) } ?: d
            else -> d
        }
    }

    private fun identity(original: NpcIdentity, d: CustomCharacterDefinition, look: CharacterAppearance) = original.copy(
        name = d.name,age = d.age,gender = d.gender,species = d.species,culture = d.culture,origin = d.origin,
        profession = d.profession,personality = d.personality,interests = d.interests,role = d.role,
        homeProvinceId = d.homeProvinceId,appearance = look,romanceable = d.romanceable,
        routine = if (original.role == d.role) original.routine else NpcEngine.generate(original.id,d.role,d.homeProvinceId).routine,
        attractedTo = if (d.romanceable) Gender.entries.toSet() else emptySet(),designedByPlayer = true,
    )

    fun definition(npc: NpcIdentity) = CustomCharacterDefinition(npc.name, npc.age, npc.gender, npc.species, npc.culture,
        npc.origin, npc.profession, npc.personality, npc.interests, npc.role, npc.homeProvinceId, npc.romanceable, npc.appearance.copy(customImageId = null))
}
