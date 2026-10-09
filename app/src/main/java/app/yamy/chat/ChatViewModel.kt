package app.yamy.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yamy.engine.ChatEngine
import app.yamy.engine.ChatInput
import app.yamy.engine.ModelLocator
import app.yamy.engine.Turn
import app.yamy.persona.PersonaProvider
import app.yamy.privacy.DataWiper
import app.yamy.safety.TurnPlan
import app.yamy.safety.TurnPlanner
import app.yamy.skills.SkillPrompt
import app.yamy.skills.SkillRepository
import app.yamy.vision.ImageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val session: Session? = null,
)

class ChatViewModel(
    private val engine: ChatEngine,
    private val repository: ConversationRepository,
    private val persona: PersonaProvider,
    private val locator: ModelLocator,
    private val imageSource: ImageSource,
    private val skills: SkillRepository,
    private val wiper: DataWiper,
    private val sessions: SessionRepository,
    private val planner: TurnPlanner = TurnPlanner(),
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val status = MutableStateFlow(EngineStatus.LOADING)
    private val error = MutableStateFlow<String?>(null)
    private val pendingImage = MutableStateFlow<PendingImage?>(null)
    private val currentSession = MutableStateFlow<Session?>(null)

    /** Toutes les discussions, la plus récente en premier. */
    val sessionList: StateFlow<List<Session>> =
        sessions.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sérialise les opérations qui touchent à la conversation du moteur (envoi, effacement, reprise). */
    private val conversationLock = Mutex()
    private var generation: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = currentSession.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else repository.observe(session.id)
    }

    val state: StateFlow<ChatUiState> =
        combine(combine(messages, draft, status, error, pendingImage) { m, d, s, e, p -> ChatUiState(m, d, s, e, p) }, currentSession) { ui, session ->
            ui.copy(session = session)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { prepareEngine() }
    }

    private suspend fun prepareEngine() {
        currentSession.value = sessions.mostRecentOrCreate()
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
        val session = currentSession.value ?: sessions.mostRecentOrCreate().also { currentSession.value = it }
        val history = normalized(repository.recent(session.id, HISTORY_TURNS)).map { Turn(it.role == Role.USER, it.text) }
        val (prompt, available) = withContext(Dispatchers.IO) {
            val all = skills.list()
            val base = persona.systemPrompt() + session.topic.takeIf { it.isNotBlank() }?.let { "\n\n## Sujet de cette discussion\n$it" }.orEmpty()
            SkillPrompt.withSkills(base, all) to all
        }
        engine.startConversation(prompt, history, skills.takeIf { available.isNotEmpty() })
    }

    /** Prépare une photo choisie (galerie ou appareil) pour le prochain message. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                pendingImage.value = PendingImage(imageSource.load(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error.value = "Image illisible : ${e.message}"
            } finally {
                imageSource.discard(uri)
            }
        }
    }

    /** Photo annulée ou perdue : on supprime la copie laissée par l'appareil photo. */
    fun discardCapture(uri: Uri) = imageSource.discard(uri)

    fun discardImage() {
        pendingImage.value = null
    }

    /** Vrai quand le moteur est libre : les skills ne peuvent être créés que dans ce cas. */
    val isIdle: Boolean get() = status.value == EngineStatus.READY

    fun send(text: String) {
        val image = pendingImage.value
        val prompt = text.trim().ifEmpty { if (image != null) DEFAULT_IMAGE_PROMPT else return }
        if (status.value != EngineStatus.READY) return
        generation = viewModelScope.launch {
            status.value = EngineStatus.GENERATING
            error.value = null
            draft.value = ""
            pendingImage.value = null
            val session = currentSession.value ?: sessions.mostRecentOrCreate().also { currentSession.value = it }
            repository.add(ChatMessage(sessionId = session.id, role = Role.USER, text = prompt, hasImage = image != null))
            sessions.touch(session.id)
            if (session.title == Session.DEFAULT_TITLE) {
                // Titre automatique : le début du premier message.
                val title = prompt.lineSequence().first().take(TITLE_LENGTH).trim().ifEmpty { Session.DEFAULT_TITLE }
                sessions.rename(session.id, title)
                currentSession.value = session.copy(title = title)
            }
            val plan = planner.plan(prompt)
            if (plan is TurnPlan.Crisis) {
                // Détresse aiguë : réponse fixe écrite en code, sans passer par le modèle.
                repository.add(ChatMessage(sessionId = session.id, role = Role.YAMY, text = plan.reply))
                status.value = EngineStatus.READY
                return@launch
            }
            val textForModel = (plan as TurnPlan.Model).textForModel
            val reply = StringBuilder()
            try {
                conversationLock.withLock {
                    if (!engine.hasConversation) restartConversation()
                    val input = ChatInput(textForModel, listOfNotNull(image?.bytes))
                    engine.send(input).collect { piece ->
                        reply.append(piece)
                        draft.value = reply.toString()
                    }
                    if (reply.isNotBlank()) repository.add(ChatMessage(sessionId = session.id, role = Role.YAMY, text = reply.toString()))
                }
                status.value = EngineStatus.READY
            } catch (e: CancellationException) {
                draft.value = ""
                throw e
            } catch (e: Throwable) {
                draft.value = ""
                recoverFromTurnError(e)
            }
            // En cas de succès, le brouillon n'est PAS vidé ici : l'écran le masque dès que la réponse
            // enregistrée arrive de la base (sinon la bulle disparaît un instant avant de réapparaître).
        }
    }

    /**
     * « Tout effacer » : interrompt la génération en cours, vide la mémoire de l'écran puis les données.
     * Ne peut pas être annulé en cours de route.
     */
    fun wipeAll(onDone: () -> Unit) {
        val running = generation
        viewModelScope.launch(NonCancellable) {
            running?.cancelAndJoin()
            conversationLock.withLock {
                pendingImage.value = null
                draft.value = ""
                error.value = null
                wiper.wipeAll()
                currentSession.value = sessions.mostRecentOrCreate()
            }
            if (status.value != EngineStatus.MODEL_MISSING) status.value = EngineStatus.READY
            onDone()
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
                currentSession.value?.let { repository.clear(it.id) }
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

    /** Ouvre une discussion existante : le moteur reprend avec son historique et son sujet. */
    fun openSession(id: Long, onOpened: () -> Unit = {}) = switchTo(onOpened) { sessions.get(id) }

    /** Crée une discussion (sujet optionnel) et l'ouvre. */
    fun newSession(title: String, topic: String, onOpened: () -> Unit = {}) = switchTo(onOpened) { sessions.create(title, topic) }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            if (currentSession.value?.id == id) {
                sessions.delete(id)
                switchTo { sessions.mostRecentOrCreate() }
            } else {
                sessions.delete(id)
            }
        }
    }

    private fun switchTo(onOpened: () -> Unit = {}, target: suspend () -> Session?) {
        val running = generation
        viewModelScope.launch {
            running?.cancelAndJoin()
            val session = target() ?: return@launch
            val usable = status.value == EngineStatus.READY || status.value == EngineStatus.GENERATING
            if (usable) status.value = EngineStatus.LOADING
            conversationLock.withLock {
                currentSession.value = session
                draft.value = ""
                error.value = null
                pendingImage.value = null
                if (usable) {
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
            onOpened()
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
        private const val TITLE_LENGTH = 40
        private const val DEFAULT_IMAGE_PROMPT = "Regarde cette photo et dis-moi ce que tu en penses."
    }
}
