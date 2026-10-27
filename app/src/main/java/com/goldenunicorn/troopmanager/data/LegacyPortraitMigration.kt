package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.net.Uri
import com.goldenunicorn.troopmanager.model.*

/** Once copied or cleared, a content URI is never attempted again on subsequent loads. */
object LegacyPortraitMigration {
    fun migrate(input: GameState, copy: (String) -> String?): GameState {
        val state = linkLegacyPeople(input)
        val companionKey = state.companion.npcIdentityId?.let { "npc:$it" } ?: "companion"
        val partnerKey = state.crown.saga.partner?.let { p -> p.npcIdentityId?.let { "npc:$it" }
            ?: if (state.companion.met && state.crown.saga.companionId == p.background) companionKey else "partner:${p.background}" }
        fun commanderKey(c: Commander) = c.npcIdentityId?.let { "npc:$it" }
            ?: if (c.id == COMPANION_COMMANDER_ID && state.companion.met) companionKey else "commander:${c.id}"
        val migrated = mutableMapOf<Pair<String,String>,String?>()
        fun uri(person: String, value: String?): String? {
            if (value == null || !value.startsWith("content:")) return value
            val key = person to value
            if (!migrated.containsKey(key)) migrated[key] = copy(value)
            return migrated[key]
        }
        val references = listOf("player" to state.player.portraitUri,companionKey to state.companion.portraitUri) +
            listOfNotNull(partnerKey?.let { it to state.crown.saga.partner?.portraitUri }) + state.commanders.map { commanderKey(it) to it.portraitUri }
        val owned = linkedMapOf<String,String>()
        references.forEach { (person,source) -> uri(person,source)?.let { owned.putIfAbsent(person,it) } }
        val copied = state.copy(
            player = state.player.copy(portraitUri = owned["player"]),
            companion = state.companion.copy(portraitUri = owned[companionKey]),
            commanders = state.commanders.map { it.copy(portraitUri = owned[commanderKey(it)]) },
            crown = state.crown.copy(saga = state.crown.saga.copy(partner = state.crown.saga.partner?.let { it.copy(portraitUri = owned[partnerKey]) })),
        )
        fun imageId(value: String?) = CustomImageReference.idFromUri(value)
        val playerId = imageId(copied.player.portraitUri)
        val companionId = imageId(copied.companion.portraitUri)
        val partner = copied.crown.saga.partner
        val partnerId = imageId(partner?.portraitUri)
        val appearance = copied.life.appearance ?: CharacterAppearance()
        return copied.copy(
            life = copied.life.copy(appearance = if (playerId != null) appearance.copy(customImageId = playerId) else copied.life.appearance,
                npcs = copied.life.npcs.map { npc ->
                    val id = if (npc.id == copied.companion.npcIdentityId) companionId
                        else if (npc.id == partner?.npcIdentityId) partnerId
                        else copied.commanders.firstOrNull { it.npcIdentityId == npc.id }?.portraitUri?.let(::imageId)
                    if (id != null) npc.copy(appearance = npc.appearance.copy(customImageId = id)) else npc
                }),
            crown = copied.crown.copy(playerLook = if (playerId != null) (copied.crown.playerLook ?: appearance.toPortraitSpec()).copy(customImageId = playerId) else copied.crown.playerLook,
                saga = copied.crown.saga.copy(partner = if (partner != null && partnerId != null) partner.copy(look = (partner.look ?: PortraitSpec()).copy(customImageId = partnerId)) else partner)),
        )
    }

    /** Old saves kept names and an explicit court memory; bind only unique matching people. */
    private fun linkLegacyPeople(state: GameState): GameState {
        val atCourt = state.life.npcs.filter { it.departed && it.remembers("court") }
        val companionId = state.companion.npcIdentityId ?: atCourt.filter { it.name == state.companion.name && it.species == state.companion.species }.singleOrNull()?.id
        val partner = state.crown.saga.partner
        val designed = state.life.companion
        val partnerId = partner?.let { p -> p.npcIdentityId ?: designed?.takeIf { it.designedByPlayer && it.name == p.name && it.species == p.species }?.id }
        return state.copy(companion = state.companion.copy(npcIdentityId = companionId),
            commanders = state.commanders.map { c -> c.copy(npcIdentityId = c.npcIdentityId ?:
                if (c.id == COMPANION_COMMANDER_ID && state.companion.met) companionId
                else atCourt.filter { it.name == c.name && it.culture == c.culture }.singleOrNull()?.id) },
            crown = state.crown.copy(saga = state.crown.saga.copy(partner = partner?.copy(npcIdentityId = partnerId))))
    }

    fun migrate(context: Context, state: GameState): GameState {
        val images = CustomCharacterImages(context)
        return migrate(state) { value ->
            runCatching {
                val original = images.read(Uri.parse(value), CharacterImageKind.PORTRAIT, legacy = true)
                try {
                    val normalized = images.crop(original, CharacterImageKind.PORTRAIT, CharacterImageCrop())
                    try { Uri.fromFile(images.file(images.install(normalized), CharacterImageKind.PORTRAIT)).toString() }
                    finally { normalized.recycle() }
                } finally { original.recycle() }
            }.getOrNull()
        }
    }
}
