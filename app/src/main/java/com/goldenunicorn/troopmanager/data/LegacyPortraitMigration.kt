package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.net.Uri
import com.goldenunicorn.troopmanager.model.*
import java.util.UUID

/** Once copied or cleared, a content URI is never attempted again on subsequent loads. */
object LegacyPortraitMigration {
    fun migrate(state: GameState, copy: (String) -> String?): GameState {
        val migrated = mutableMapOf<String, String?>()
        fun uri(value: String?): String? {
            if (value == null || !value.startsWith("content:")) return value
            return migrated.getOrPut(value) { copy(value) }
        }
        return state.copy(
            player = state.player.copy(portraitUri = uri(state.player.portraitUri)),
            companion = state.companion.copy(portraitUri = uri(state.companion.portraitUri)),
            commanders = state.commanders.map { it.copy(portraitUri = uri(it.portraitUri)) },
            crown = state.crown.copy(saga = state.crown.saga.copy(partner = state.crown.saga.partner?.let { it.copy(portraitUri = uri(it.portraitUri)) })),
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
