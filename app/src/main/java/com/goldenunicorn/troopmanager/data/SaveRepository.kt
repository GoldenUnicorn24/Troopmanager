package com.goldenunicorn.troopmanager.data

import android.content.Context
import android.content.SharedPreferences
import com.goldenunicorn.troopmanager.model.GameState
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

/** Atomic rolling backup plus a protected, byte-exact copy of the last v0.4 save. */
class SaveRepository internal constructor(private val storage: SaveStorage) {
    constructor(context: Context) : this(PreferencesSaveStorage(
        context.getSharedPreferences("realm_save", Context.MODE_PRIVATE)))
    var lastError: String? = null
        private set
    var activeSlot: Int = 1
        private set

    @Synchronized
    fun selectSlot(slot: Int) {
        require(slot in 1..3) { "Es gibt drei Spielstandplätze." }
        activeSlot = slot
        lastError = null
    }

    @Synchronized
    fun slots(): List<SaveSlotMetadata?> = (1..3).map { slot ->
        val stored = storage.read(slotKey("slot_metadata", slot))
        val metadata = stored?.let { runCatching { Json.decodeFromString<SaveSlotMetadata>(it) }.getOrNull() }
        metadata ?: storage.read(slotKey("game_state_v1", slot))?.let { raw ->
            runCatching {
                val state = SaveCodec.decode(raw)
                SaveSlotMetadata(slot, state.player.name, state.day, state.title, state.population.total, 0, state.settings.ironman)
            }.getOrNull()
        }
    }

    fun hasSave(): Boolean = storage.contains(KEY) || storage.contains(BACKUP_KEY) || storage.contains(MIGRATION_BACKUP_KEY) || storage.contains(LEGACY_MIGRATION_BACKUP_KEY) || storage.contains(V045_BACKUP_KEY)

    @Synchronized
    fun save(state: GameState) {
        lastError = null
        try {
            val encoded = SaveCodec.encode(state)
            val primary = validRaw(read(KEY))
            val backup = validRaw(read(BACKUP_KEY))
            val previous = primary ?: backup ?: encoded
            val writes = mutableMapOf<String, String?>(KEY to encoded, BACKUP_KEY to if (state.settings.ironman) null else previous, METADATA_KEY to metadata(state))
            protectMigration(previous, writes)
            if (!storage.commit(writes)) lastError = "Der Spielstand konnte nicht dauerhaft gespeichert werden."
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
                    val upgraded = SaveCodec.encode(state)
                    if (upgraded != primary) {
                        try {
                            validRaw(read(BACKUP_KEY))
                        } catch (error: SaveFormatException) {
                            if (error.futureVersion) { lastError = error.message; return state }
                            throw error
                        }
                        val writes = mutableMapOf<String, String?>(KEY to upgraded, BACKUP_KEY to if (state.settings.ironman) null else primary, METADATA_KEY to metadata(state))
                        protectMigration(primary, writes)
                        if (!storage.commit(writes)) lastError = "Spielstand geladen, aber die Migration konnte nicht gespeichert werden."
                    }
                    return state
                } catch (error: SaveFormatException) {
                    if (error.futureVersion) throw error
                    lastError = error.message
                }
            }
            if (slots()[activeSlot - 1]?.ironman == true) {
                lastError = "Der Ironman-Spielstand ist beschädigt. Er bleibt zur manuellen Wiederherstellung erhalten."
                return null
            }
            for (key in listOf(BACKUP_KEY, V045_BACKUP_KEY, MIGRATION_BACKUP_KEY, LEGACY_MIGRATION_BACKUP_KEY)) {
                val backup = read(key) ?: continue
                val recovered = try { SaveCodec.decode(backup) } catch (error: SaveFormatException) {
                    if (error.futureVersion) throw error
                    continue
                }
                val writes = mutableMapOf<String, String?>(KEY to SaveCodec.encode(recovered), METADATA_KEY to metadata(recovered))
                protectMigration(backup, writes)
                lastError = if (storage.commit(writes)) "Der Hauptspielstand war beschädigt oder fehlte. Das Backup wurde wiederhergestellt."
                    else "Backup geladen; der Hauptspielstand konnte nicht wiederhergestellt werden."
                return recovered
            }
            return null
        } catch (error: Exception) {
            lastError = error.message ?: "Der Spielstand konnte nicht geladen werden."
            return null
        }
    }

    /** Only explicit new-game deletion removes the protected rollback copy. */
    @Synchronized
    fun delete() {
        lastError = if (storage.commit(mapOf(KEY to null, BACKUP_KEY to null, MIGRATION_BACKUP_KEY to null, LEGACY_MIGRATION_BACKUP_KEY to null, V045_BACKUP_KEY to null, METADATA_KEY to null))) null
        else "Der Spielstand konnte nicht gelöscht werden."
    }

    private fun protectMigration(raw: String, writes: MutableMap<String, String?>) {
        val version = Json.parseToJsonElement(raw).jsonObject["version"]?.jsonPrimitive?.intOrNull ?: 1
        val key = when (version) { 3 -> V045_BACKUP_KEY; 2 -> MIGRATION_BACKUP_KEY; 1 -> LEGACY_MIGRATION_BACKUP_KEY; else -> return }
        // An autosave may rotate the regular backup; this key is written only once.
        if (!storage.contains(key)) writes[key] = raw
    }

    private fun read(key: String): String? = storage.read(key)

    private fun validRaw(raw: String?): String? {
        if (raw == null) return null
        return try { SaveCodec.decode(raw); raw } catch (error: SaveFormatException) {
            if (error.futureVersion) throw error
            null
        }
    }

    @Synchronized
    fun exportSave(): String? = load()?.let(SaveCodec::encode)

    @Synchronized
    fun importSave(raw: String): GameState? {
        lastError = null
        return try {
            require(slots()[activeSlot - 1]?.ironman != true) { "Ein Ironman-Spielstand erlaubt keinen Import älterer Zustände." }
            val imported = SaveCodec.decode(raw)
            require(!imported.settings.ironman) { "Ironman-Kampagnen können nicht als fortsetzbarer Import übernommen werden." }
            save(imported)
            if (lastError == null) imported else null
        } catch (error: Exception) {
            lastError = error.message ?: "Der Import ist fehlgeschlagen."
            null
        }
    }

    private fun metadata(state: GameState) = Json.encodeToString(SaveSlotMetadata(activeSlot, state.player.name, state.day, state.title, state.population.total, System.currentTimeMillis(), state.settings.ironman))
    private fun slotKey(base: String, slot: Int = activeSlot) = if (slot == 1) base else "${base}_slot_$slot"
    private val KEY get() = slotKey("game_state_v1")
    private val BACKUP_KEY get() = slotKey("game_state_backup_v2")
    private val MIGRATION_BACKUP_KEY get() = slotKey("migration_backup_v2")
    private val LEGACY_MIGRATION_BACKUP_KEY get() = slotKey("migration_backup_v1")
    private val V045_BACKUP_KEY get() = slotKey("migration_backup_v3")
    private val METADATA_KEY get() = slotKey("slot_metadata")
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
