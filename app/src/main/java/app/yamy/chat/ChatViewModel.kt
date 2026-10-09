package app.yamy.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yamy.engine.ChatEngine
import app.yamy.engine.ModelLocator
import app.yamy.engine.Turn
import app.yamy.persona.PersonaProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EngineStatus { LOADING, READY, GENERATING, MODEL_MISSING, ERROR }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** Réponse en cours de génération, pas encore enregistrée. */
    val draft: String = "",
    val status: EngineStatus = EngineStatus.LOADING,
    val error: String? = null,
)

class ChatViewModel(
    private val engine: ChatEngine,
    private val repository: ConversationRepository,
    private val persona: PersonaProvider,
    private val locator: ModelLocator,
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val status = MutableStateFlow(EngineStatus.LOADING)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ChatUiState> = combine(repository.observe(), draft, status, error) { messages, d, s, e ->
        ChatUiState(messages, d, s, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { prepareEngine() }
    }

    private suspend fun prepareEngine() {
        if (!locator.isPresent()) {
            status.value = EngineStatus.MODEL_MISSING
            return
        }
        try {
            engine.load()
            restartConversation()
            status.value = EngineStatus.READY
        } catch (e: Throwable) {
            fail(e)
        }
    }

    private suspend fun restartConversation() {
        val history = repository.recent(HISTORY_TURNS).map { Turn(it.role == Role.USER, it.text) }
        engine.startConversation(persona.systemPrompt(), history)
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || status.value != EngineStatus.READY) return
        viewModelScope.launch {
            status.value = EngineStatus.GENERATING
            error.value = null
            repository.add(ChatMessage(role = Role.USER, text = prompt))
            val reply = StringBuilder()
            try {
                engine.send(prompt).collect { piece ->
                    reply.append(piece)
                    draft.value = reply.toString()
                }
                if (reply.isNotBlank()) repository.add(ChatMessage(role = Role.YAMY, text = reply.toString()))
                status.value = EngineStatus.READY
            } catch (e: Throwable) {
                fail(e)
            } finally {
                draft.value = ""
            }
        }
    }

    /** Efface la conversation affichée et repart d'un contexte vierge. */
    fun clearConversation() {
        viewModelScope.launch {
            repository.clear()
            if (status.value == EngineStatus.READY) restartConversation()
        }
    }

    private fun fail(e: Throwable) {
        error.value = "${e::class.simpleName}: ${e.message}"
        status.value = EngineStatus.ERROR
    }

    companion object {
        private const val HISTORY_TURNS = 20
    }
}
