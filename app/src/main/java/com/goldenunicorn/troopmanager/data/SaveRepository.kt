package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.content.SharedPreferences
import com.goldenunicorn.troopmanager.model.GameState

/** Both values are committed together; a corrupt primary can never replace a healthy backup. */
class SaveRepository internal constructor(private val storage: SaveStorage) {
    constructor(context: Context) : this(PreferencesSaveStorage(
        context.getSharedPreferences("realm_save", Context.MODE_PRIVATE)))
    var lastError: String? = null
        private set

    fun hasSave(): Boolean = storage.contains(KEY) || storage.contains(BACKUP_KEY)

    @Synchronized
    fun save(state: GameState) {
        lastError = null
        try {
            val encoded = SaveCodec.encode(state)
            val primary = read(KEY)
            val backup = read(BACKUP_KEY)
            // Do not replace a save created by a newer version, even when loading failed.
            val validPrimary = validRaw(primary)
            val validBackup = validRaw(backup)
            val previous = validPrimary ?: validBackup ?: encoded
            if (!storage.commit(mapOf(KEY to encoded, BACKUP_KEY to previous))) {
                lastError = "Der Spielstand konnte nicht dauerhaft gespeichert werden."
            }
        } catch (error: Exception) {
            lastError = error.message ?: "Der Spielstand konnte nicht gespeichert werden."
        }
    }

    @Synchronized
    fun load(): GameState? {
        lastError = null
        try {
            val primary = read(KEY)
            if (primary != null) {
                try {
                    val state = SaveCodec.decode(primary)
                    // A successful migration is persisted as v2, while the original remains a rollback copy.
                    val upgraded = SaveCodec.encode(state)
                    if (upgraded != primary) {
                        try {
                            validRaw(read(BACKUP_KEY)) // Preserve backups from a newer app as well.
                        } catch (error: SaveFormatException) {
                            if (error.futureVersion) {
                                lastError = error.message
                                return state
                            }
                            throw error
                        }
                        if (!storage.commit(mapOf(KEY to upgraded, BACKUP_KEY to primary))) {
                            lastError = "Spielstand geladen, aber die Migration konnte nicht gespeichert werden."
                        }
                    }
                    return state
                } catch (error: SaveFormatException) {
                    if (error.futureVersion) throw error
                    lastError = error.message
                }
            }
            val backup = read(BACKUP_KEY) ?: return null
            val recovered = SaveCodec.decode(backup)
            if (storage.commit(mapOf(KEY to SaveCodec.encode(recovered)))) {
                lastError = "Der Hauptspielstand war beschädigt oder fehlte. Das Backup wurde wiederhergestellt."
            } else {
                lastError = "Backup geladen; der Hauptspielstand konnte nicht wiederhergestellt werden."
            }
            return recovered
        } catch (error: Exception) {
            lastError = error.message ?: "Der Spielstand konnte nicht geladen werden."
            return null
        }
    }

    @Synchronized
    fun delete() {
        lastError = if (storage.commit(mapOf(KEY to null, BACKUP_KEY to null))) null
        else "Der Spielstand konnte nicht gelöscht werden."
    }

    private fun read(key: String): String? = storage.read(key)

    private fun validRaw(raw: String?): String? {
        if (raw == null) return null
        return try { SaveCodec.decode(raw); raw } catch (error: SaveFormatException) {
            if (error.futureVersion) throw error
            null
        }
    }

    companion object {
        // Keep the original key so installed v0.3 games are found without touching unrelated preferences.
        private const val KEY = "game_state_v1"
        private const val BACKUP_KEY = "game_state_backup_v2"
    }
}

/** Small storage boundary enables recovery tests without an Android runtime. */
internal interface SaveStorage {
    fun contains(key: String): Boolean
    fun read(key: String): String?
    fun commit(values: Map<String, String?>): Boolean
}

private class PreferencesSaveStorage(private val prefs: SharedPreferences) : SaveStorage {
    override fun contains(key: String): Boolean = prefs.contains(key)
    override fun read(key: String): String? = try { prefs.getString(key, null) } catch (_: ClassCastException) { "invalid stored value" }
    override fun commit(values: Map<String, String?>): Boolean {
        val editor = prefs.edit()
        values.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
        return editor.commit()
    }
}
