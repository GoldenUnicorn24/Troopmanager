package com.goldenunicorn.troopmanager.data

import android.content.Context
import com.goldenunicorn.troopmanager.model.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SaveRepository(context: Context) {
    private val prefs = context.getSharedPreferences("realm_save", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun hasSave(): Boolean = prefs.contains(KEY)

    fun save(state: GameState) {
        prefs.edit().putString(KEY, json.encodeToString(state)).apply()
    }

    fun load(): GameState? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching { json.decodeFromString<GameState>(raw) }.getOrNull()
    }

    fun delete() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "game_state_v1"
    }
}
