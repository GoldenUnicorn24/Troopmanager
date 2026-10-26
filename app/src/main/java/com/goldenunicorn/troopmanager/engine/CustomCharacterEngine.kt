package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object CustomCharacterEngine {
    fun apply(state: GameState, character: CustomCharacter, portraitUri: String): GameState {
        character.validate()
        require(state.customCharacters.size < 200 || state.customCharacters.any { it.target == character.target }) { "Höchstens 200 eigene Charaktere." }
        val previousImageId = state.customCharacters.firstOrNull { it.target == character.target }?.id
        val d = character.definition
        val look = d.appearance.copy(customImageId = character.id)
        var next = state.copy(customCharacters = state.customCharacters.filterNot { it.target == character.target } + character)
        next = when {
            character.target == "player" -> next.copy(
                player = next.player.copy(name = d.name, age = d.age, species = d.species, portraitUri = portraitUri),
                life = next.life.copy(gender = d.gender, culture = d.culture, origin = d.origin, profession = d.profession, appearance = look),
                crown = next.crown.copy(playerLook = look.toPortraitSpec()),
            )
            character.target == "companion" -> {
                val npcId = next.life.companionNpcId ?: "custom-companion-${character.id}"
                val original = next.life.npc(npcId) ?: NpcEngine.generate(npcId,d.role,d.homeProvinceId).copy(departed = next.life.landed)
                val npc = identity(original,d,look)
                val partner = next.crown.saga.partner
                val updated = next.copy(companion = next.companion.copy(name = d.name, age = d.age, species = d.species, portraitUri = portraitUri),
                    life = next.life.copy(companionNpcId = npcId, npcs = next.life.npcs.filterNot { it.id == npcId } + npc),
                    crown = next.crown.copy(saga = next.crown.saga.copy(partner = if (partner != null && partner.background == next.crown.saga.companionId)
                        partner.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri,look = look.toPortraitSpec()) else partner)))
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
                next.copy(crown = next.crown.copy(saga = next.crown.saga.copy(partner = partner.copy(name = d.name, age = d.age, species = d.species, portraitUri = portraitUri, look = look.toPortraitSpec()))))
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
                companion = if (shared(next.companion.portraitUri)) next.companion.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri) else next.companion,
                commanders = next.commanders.map { if (shared(it.portraitUri)) it.copy(name = d.name,culture = d.culture,portraitUri = portraitUri) else it },
                crown = next.crown.copy(saga = next.crown.saga.copy(partner = if (partner != null && shared(partner.portraitUri))
                    partner.copy(name = d.name,age = d.age,species = d.species,portraitUri = portraitUri,look = look.toPortraitSpec()) else partner)),
            )
        }
        val mirroredCommanders = next.commanders.filter { CustomImageReference.idFromUri(it.portraitUri) == character.id }.map { it.id }.toSet()
        if (mirroredCommanders.isNotEmpty()) next = next.copy(court = next.court.copy(characters = next.court.characters.map {
            if (it.commanderId in mirroredCommanders) it.copy(age = d.age,origin = d.origin) else it
        }))
        return next
    }

    private fun identity(original: NpcIdentity, d: CustomCharacterDefinition, look: CharacterAppearance) = original.copy(
        name = d.name,age = d.age,gender = d.gender,species = d.species,culture = d.culture,origin = d.origin,
        profession = d.profession,personality = d.personality,interests = d.interests,role = d.role,
        homeProvinceId = d.homeProvinceId,appearance = look,romanceable = d.romanceable,
        attractedTo = if (d.romanceable) Gender.entries.toSet() else emptySet(),designedByPlayer = true,
    )

    fun definition(npc: NpcIdentity) = CustomCharacterDefinition(npc.name, npc.age, npc.gender, npc.species, npc.culture,
        npc.origin, npc.profession, npc.personality, npc.interests, npc.role, npc.homeProvinceId, npc.romanceable, npc.appearance.copy(customImageId = null))
}
