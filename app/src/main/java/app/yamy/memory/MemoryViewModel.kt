package app.yamy.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yamy.engine.ChatEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MemoryUiState(val notes: List<MemoryNote> = emptyList(), val editing: MemoryNote? = null)

/** Onglet Mémoire : la personne garde la main sur tout ce que Yamy retient. */
class MemoryViewModel(private val memory: MemoryRepository, private val engine: ChatEngine) : ViewModel() {
    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val notes = withContext(Dispatchers.IO) { memory.list() }
            _state.value = _state.value.copy(notes = notes)
        }
    }

    fun edit(note: MemoryNote?) {
        _state.value = _state.value.copy(editing = note)
    }

    fun update(transform: (MemoryNote) -> MemoryNote) {
        _state.value.editing?.let { _state.value = _state.value.copy(editing = transform(it)) }
    }

    fun save() = change { note -> MemoryFormat.valid(note)?.let(memory::save) }

    fun delete() = change { note -> memory.delete(note.name) }

    fun forgetAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { memory.deleteAll() }
            engine.invalidateConversation()
            _state.value = MemoryUiState()
        }
    }

    /** Toute modification invalide la conversation : Yamy repartira avec l'index à jour. */
    private fun change(action: (MemoryNote) -> Unit) {
        val note = _state.value.editing ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { action(note) }
            engine.invalidateConversation()
            _state.value = _state.value.copy(editing = null)
            refresh()
        }
    }
}
