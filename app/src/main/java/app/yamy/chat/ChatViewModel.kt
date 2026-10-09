package app.yamy.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yamy.engine.ChatEngine
import app.yamy.engine.ChatInput
import app.yamy.engine.ModelLocator
import app.yamy.engine.Turn
import app.yamy.persona.PersonaProvider
import app.yamy.vision.ImageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class EngineStatus { LOADING, READY, GENERATING, MODEL_MISSING, ERROR }

/** Photo en attente d'envoi, gardée en mémoire seulement. */
class PendingImage(val bytes: ByteArray)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** Réponse en cours de génération, pas encore enregistrée. */
    val draft: String = "",
    val status: EngineStatus = EngineStatus.LOADING,
    val error: String? = null,
    val pendingImage: PendingImage? = null,
)

class ChatViewModel(
    private val engine: ChatEngine,
    private val repository: ConversationRepository,
    private val persona: PersonaProvider,
    private val locator: ModelLocator,
    private val imageSource: ImageSource,
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val status = MutableStateFlow(EngineStatus.LOADING)
    private val error = MutableStateFlow<String?>(null)
    private val pendingImage = MutableStateFlow<PendingImage?>(null)

    /** Sérialise les opérations qui touchent à la conversation du moteur (envoi, effacement, reprise). */
    private val conversationLock = Mutex()
    private var generation: Job? = null

    val state: StateFlow<ChatUiState> =
        combine(repository.observe(), draft, status, error, pendingImage) { messages, d, s, e, p ->
            ChatUiState(messages, d, s, e, p)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { prepareEngine() }
    }

    private suspend fun prepareEngine() {
        if (!withContext(Dispatchers.IO) { locator.isPresent() }) {
            status.value = EngineStatus.MODEL_MISSING
            return
        }
        try {
            engine.load()
            conversationLock.withLock { restartConversation() }
            status.value = EngineStatus.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail(e)
        }
    }

    private suspend fun restartConversation() {
        val history = normalized(repository.recent(HISTORY_TURNS)).map { Turn(it.role == Role.USER, it.text) }
        engine.startConversation(withContext(Dispatchers.IO) { persona.systemPrompt() }, history)
    }

    /** Prépare une photo choisie (galerie ou appareil) pour le prochain message. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                pendingImage.value = PendingImage(imageSource.load(uri))
            } catch (e: Throwable) {
                error.value = "Image illisible : ${e.message}"
            }
        }
    }

    fun discardImage() {
        pendingImage.value = null
    }

    fun send(text: String) {
        val image = pendingImage.value
        val prompt = text.trim().ifEmpty { if (image != null) DEFAULT_IMAGE_PROMPT else return }
        if (status.value != EngineStatus.READY) return
        generation = viewModelScope.launch {
            status.value = EngineStatus.GENERATING
            error.value = null
            pendingImage.value = null
            repository.add(ChatMessage(role = Role.USER, text = prompt, hasImage = image != null))
            val reply = StringBuilder()
            try {
                conversationLock.withLock {
                    val input = ChatInput(prompt, listOfNotNull(image?.bytes))
                    engine.send(input).collect { piece ->
                        reply.append(piece)
                        draft.value = reply.toString()
                    }
                    if (reply.isNotBlank()) repository.add(ChatMessage(role = Role.YAMY, text = reply.toString()))
                }
                status.value = EngineStatus.READY
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                recoverFromTurnError(e)
            } finally {
                draft.value = ""
            }
        }
    }

    /** Efface la conversation affichée et repart d'un contexte vierge. */
    fun clearConversation() {
        val running = generation
        viewModelScope.launch {
            running?.cancelAndJoin()
            val wasUsable = status.value != EngineStatus.MODEL_MISSING && status.value != EngineStatus.LOADING
            if (wasUsable) status.value = EngineStatus.LOADING
            conversationLock.withLock {
                repository.clear()
                draft.value = ""
                error.value = null
                if (wasUsable) {
                    try {
                        restartConversation()
                        status.value = EngineStatus.READY
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        fail(e)
                    }
                }
            }
        }
    }

    /** Une erreur pendant un tour n'est pas fatale : on repart d'un contexte propre. */
    private suspend fun recoverFromTurnError(e: Throwable) {
        error.value = "${e::class.simpleName}: ${e.message}"
        try {
            conversationLock.withLock { restartConversation() }
            status.value = EngineStatus.READY
        } catch (inner: CancellationException) {
            throw inner
        } catch (inner: Throwable) {
            fail(inner)
        }
    }

    /** Garantit une alternance valide : commence par l'utilisateur, finit par Yamy. */
    private fun normalized(messages: List<ChatMessage>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        for (m in messages) {
            if (out.isEmpty() && m.role != Role.USER) continue
            if (out.isNotEmpty() && out.last().role == m.role) out[out.lastIndex] = m else out.add(m)
        }
        if (out.isNotEmpty() && out.last().role == Role.USER) out.removeAt(out.lastIndex)
        return out
    }

    private fun fail(e: Throwable) {
        error.value = "${e::class.simpleName}: ${e.message}"
        status.value = EngineStatus.ERROR
    }

    companion object {
        private const val HISTORY_TURNS = 20
        private const val DEFAULT_IMAGE_PROMPT = "Regarde cette photo et dis-moi ce que tu en penses."
    }
}
