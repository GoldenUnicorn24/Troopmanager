package com.goldenunicorn.troopmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goldenunicorn.troopmanager.data.SaveRepository
import com.goldenunicorn.troopmanager.data.SaveSlotMetadata
import com.goldenunicorn.troopmanager.engine.GameEngine
import com.goldenunicorn.troopmanager.engine.WorldEngine
import com.goldenunicorn.troopmanager.engine.PresentationEngine
import com.goldenunicorn.troopmanager.engine.RelationshipEngine
import com.goldenunicorn.troopmanager.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameUiState(
    val game: GameState? = null,
    val inMenu: Boolean = true,
    val busy: Boolean = false,
    val notice: String? = null,
    val activeSlot: Int = 1,
    val hasSave: Boolean = false,
    val slots: List<SaveSlotMetadata?> = List(3) { null },
)

/** The serialized campaign remains immutable. IO and the daily simulation never run on main. */
class GameViewModel(private val saves: SaveRepository) : ViewModel() {
    private val mutableUi = MutableStateFlow(GameUiState(activeSlot = saves.activeSlot))
    val ui: StateFlow<GameUiState> = mutableUi.asStateFlow()

    init { task { refreshSlots() } }

    fun notice(message: String?) { mutableUi.update { it.copy(notice = message) } }

    private fun task(block: suspend () -> Unit) {
        if (mutableUi.value.busy) return
        mutableUi.value = mutableUi.value.copy(busy = true)
        viewModelScope.launch {
            try { block() }
            catch (error: Exception) { notice(error.message ?: "Die Aktion konnte nicht abgeschlossen werden.") }
            finally { mutableUi.value = mutableUi.value.copy(busy = false) }
        }
    }

    private suspend fun refreshSlots() {
        val slots = withContext(Dispatchers.IO) { saves.slots() }
        val hasSave = withContext(Dispatchers.IO) { saves.hasSave() }
        mutableUi.value = mutableUi.value.copy(slots = slots, activeSlot = saves.activeSlot, hasSave = hasSave)
    }

    private suspend fun persist(next: GameState): Boolean {
        withContext(Dispatchers.IO) { saves.save(next) }
        saves.lastError?.let { notice(it); return false }
        mutableUi.value = mutableUi.value.copy(game = next)
        refreshSlots()
        return true
    }

    fun selectSlot(slot: Int) = task {
        withContext(Dispatchers.IO) { saves.selectSlot(slot) }
        mutableUi.value = mutableUi.value.copy(game = null, inMenu = true, notice = null)
        refreshSlots()
    }

    fun continueGame() = task {
        val loaded = withContext(Dispatchers.IO) { saves.load() }
        mutableUi.value = mutableUi.value.copy(game = loaded, inMenu = loaded == null, notice = saves.lastError)
        refreshSlots()
    }

    fun newGame() {
        if (!mutableUi.value.busy) mutableUi.value = mutableUi.value.copy(game = null, inMenu = false)
    }

    fun create(state: GameState) = task {
        if (persist(state)) mutableUi.value = mutableUi.value.copy(inMenu = false)
    }

    fun update(expected: GameState, next: GameState) = task {
        if (mutableUi.value.game != expected) return@task
        val reconciled = withContext(Dispatchers.Default) { PresentationEngine.tick(RelationshipEngine.normalize(WorldEngine.reconcileBattle(next))) }
        persist(reconciled)
    }

    fun advanceDay() = task {
        val current = mutableUi.value.game ?: return@task
        val result = withContext(Dispatchers.Default) { GameEngine.advanceDay(current) }
        if (persist(result.state)) notice(result.message)
    }

    fun menu() = task {
        mutableUi.value.game?.let { if (!persist(it)) return@task }
        mutableUi.value = mutableUi.value.copy(inMenu = true)
    }

    fun delete() = task {
        withContext(Dispatchers.IO) { saves.delete() }
        if (saves.lastError == null) mutableUi.value = mutableUi.value.copy(game = null, inMenu = true)
        else notice(saves.lastError)
        refreshSlots()
    }

    suspend fun exportSave(): String? = withContext(Dispatchers.IO) { saves.exportSave().also { saves.lastError?.let(::notice) } }

    fun importSave(raw: String) = task {
        val loaded = withContext(Dispatchers.IO) { saves.importSave(raw) }
        if (loaded != null) mutableUi.value = mutableUi.value.copy(game = loaded, inMenu = false)
        notice(saves.lastError ?: "Spielstand in Platz ${saves.activeSlot} importiert.")
        refreshSlots()
    }

    companion object {
        fun factory(saves: SaveRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(saves) as T
        }
    }
}
