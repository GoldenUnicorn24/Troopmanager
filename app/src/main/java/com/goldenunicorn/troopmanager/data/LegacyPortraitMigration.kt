package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.net.Uri
import com.goldenunicorn.troopmanager.model.*

/** Once copied or cleared, a content URI is never attempted again on subsequent loads. */
object LegacyPortraitMigration {
    fun migrate(state: GameState, copy: (String) -> String?): GameState {
        val migrated = mutableMapOf<String, String?>()
        fun uri(value: String?): String? {
            if (value == null || !value.startsWith("content:")) return value
            return migrated.getOrPut(value) { copy(value) }
        }
        val copied = state.copy(
            player = state.player.copy(portraitUri = uri(state.player.portraitUri)),
            companion = state.companion.copy(portraitUri = uri(state.companion.portraitUri)),
            commanders = state.commanders.map { it.copy(portraitUri = uri(it.portraitUri)) },
            crown = state.crown.copy(saga = state.crown.saga.copy(partner = state.crown.saga.partner?.let { it.copy(portraitUri = uri(it.portraitUri)) })),
        )
        fun imageId(value: String?): String? = runCatching {
            val path = java.net.URI(value ?: return@runCatching null)
            if (path.scheme != "file") return@runCatching null
            val file = java.io.File(path)
            file.parentFile?.name?.takeIf { file.name == "portrait.webp" && it.matches(Regex("[a-zA-Z0-9-]{1,80}")) }
        }.getOrNull()
        val playerId = imageId(copied.player.portraitUri)
        val companionId = imageId(copied.companion.portraitUri)
        val partner = copied.crown.saga.partner
        val partnerId = imageId(partner?.portraitUri)
        val appearance = copied.life.appearance ?: CharacterAppearance()
        return copied.copy(
            life = copied.life.copy(appearance = if (playerId != null) appearance.copy(customImageId = playerId) else copied.life.appearance,
                npcs = copied.life.npcs.map { npc -> if (companionId != null && npc.id == copied.life.companionNpcId) npc.copy(appearance = npc.appearance.copy(customImageId = companionId)) else npc }),
            crown = copied.crown.copy(playerLook = if (playerId != null) (copied.crown.playerLook ?: appearance.toPortraitSpec()).copy(customImageId = playerId) else copied.crown.playerLook,
                saga = copied.crown.saga.copy(partner = if (partner != null && partnerId != null) partner.copy(look = (partner.look ?: PortraitSpec()).copy(customImageId = partnerId)) else partner)),
        )
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
