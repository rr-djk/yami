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
import app.yamy.agents.AgentStep
import app.yamy.agents.Chain
import app.yamy.agents.ChainFormat
import app.yamy.agents.ChainRunner
import app.yamy.memory.MemoryFormat
import app.yamy.memory.MemoryRepository
import app.yamy.memory.Scribe
import app.yamy.memory.ScribeResult
import app.yamy.connect.NetworkMode
import app.yamy.connect.NetworkModeStore
import app.yamy.mcp.EmailDraft
import app.yamy.mcp.EmailDrafts
import app.yamy.mcp.GmailConnector
import app.yamy.mcp.gmail.GmailMcpServer
import app.yamy.vision.ImageSource
import app.yamy.voice.SegmenterEvent
import app.yamy.voice.Speaker
import app.yamy.voice.VoiceRecorder
import app.yamy.voice.Wav
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class EngineStatus { LOADING, READY, LISTENING, GENERATING, MODEL_MISSING, ERROR }

/** Écoute en cours : niveaux récents pour l'onde, durée, et texte déjà transcrit. */
/** Passage du Scribe : [working] pendant la relecture, puis les notes écrites. */
data class ScribeUi(val working: Boolean, val result: ScribeResult? = null)

/** Chaîne d'agents en cours (ou terminée, jusqu'à ce que son résumé arrive de la base). */
data class ChainRunUi(val steps: List<AgentStep>, val summary: String? = null)

data class VoiceUi(val levels: List<Float> = emptyList(), val seconds: Int = 0, val transcript: String = "")

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
    val voice: VoiceUi? = null,
    val chainRun: ChainRunUi? = null,
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
    private val recorder: VoiceRecorder,
    private val speaker: Speaker,
    private val chainRunner: ChainRunner,
    private val memory: MemoryRepository,
    private val scribe: Scribe,
    private val network: NetworkModeStore,
    private val gmail: GmailConnector,
    private val planner: TurnPlanner = TurnPlanner(),
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val status = MutableStateFlow(EngineStatus.LOADING)
    private val error = MutableStateFlow<String?>(null)
    private val pendingImage = MutableStateFlow<PendingImage?>(null)
    private val currentSession = MutableStateFlow<Session?>(null)
    private val voice = MutableStateFlow<VoiceUi?>(null)
    private val chainRun = MutableStateFlow<ChainRunUi?>(null)
    private val _scribe = MutableStateFlow<ScribeUi?>(null)
    private val _pendingEmail = MutableStateFlow<EmailDraft?>(null)
    private val _sendingEmail = MutableStateFlow(false)

    /** Email proposé par Gemma, en attente de la décision de la personne. */
    val pendingEmail: StateFlow<EmailDraft?> = _pendingEmail
    val sendingEmail: StateFlow<Boolean> = _sendingEmail

    /** Hors ligne (défaut) ou Connecté : affiché en permanence dans le chat. */
    val networkMode: StateFlow<NetworkMode> = network.mode

    /** Gemma propose seulement : rien ne part sans le bouton Envoyer. Appelé par l'outil, sur un fil natif. */
    private val drafts = EmailDrafts { draft ->
        val problem = GmailMcpServer.validate(draft.to, draft.subject, draft.body)
        if (problem != null) {
            "Brouillon refusé : $problem"
        } else {
            _pendingEmail.value = draft
            "Brouillon prêt. Dis à la personne de le relire : elle seule peut confirmer l'envoi avec le bouton Envoyer."
        }
    }

    /** Passage du Scribe : en cours, ou terminé avec les notes écrites (annulable). */
    val scribeState: StateFlow<ScribeUi?> = _scribe
    private val _chains = MutableStateFlow<List<Chain>>(emptyList())

    /** Chaînes d'agents disponibles (livrées avec l'app ou créées par la personne). */
    val chains: StateFlow<List<Chain>> = _chains
    private var voiceJob: Job? = null

    /** Yamy est en train de parler (réponse à un message vocal). */
    val speaking: StateFlow<Boolean> = speaker.speaking

    /** Toutes les discussions, la plus récente en premier. */
    val sessionList: StateFlow<List<Session>> =
        sessions.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sérialise les opérations qui touchent à la conversation du moteur (envoi, effacement, reprise). */
    private val conversationLock = Mutex()
    private var notesAvailable = false
    private var emailEnabled = false

    /** Discussion dont le contexte est chargé dans le moteur : un tour la recharge si elle a changé. */
    private var conversationSessionId: Long? = null
    private var memoryIndex = ""
    private var generation: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = currentSession.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else repository.observe(session.id)
    }

    val state: StateFlow<ChatUiState> =
        combine(combine(messages, draft, status, error, pendingImage) { m, d, s, e, p -> ChatUiState(m, d, s, e, p) }, currentSession, voice, chainRun) { ui, session, v, c ->
            ui.copy(session = session, voice = v, chainRun = c)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { prepareEngine() }
        // Changer de mode recharge les outils et le prompt au prochain tour ; repasser Hors ligne annule tout brouillon.
        viewModelScope.launch {
            network.mode.drop(1).collect { mode ->
                engine.invalidateConversation()
                if (mode == NetworkMode.OFFLINE) _pendingEmail.value = null
            }
        }
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
            _chains.value = all.mapNotNull { ChainFormat.parse(it) }
            notesAvailable = memory.list().let { notes ->
                memoryIndex = MemoryFormat.index(notes)
                notes.isNotEmpty()
            }
            val canEmail = network.mode.value == NetworkMode.CONNECTED && gmail.isConfigured
            emailEnabled = canEmail
            val base = persona.systemPrompt() +
                session.topic.takeIf { it.isNotBlank() }?.let { "\n\n## Sujet de cette discussion\n$it" }.orEmpty() +
                memoryIndex.takeIf { it.isNotBlank() }?.let {
                    "\n\n## Ce que tu sais déjà de la personne (tes notes)\n$it\n" +
                        "Si un détail t'aide à répondre, appelle l'outil lireNote avec le nom de la note. N'invente rien au-delà."
                }.orEmpty() +
                (if (canEmail) {
                    "\n\n## Email (mode Connecté)\nSi la personne te demande d'envoyer un email, appelle l'outil preparerEmail. " +
                        "Tu n'envoies rien toi-même : la personne relit et confirme. N'invente jamais d'adresse."
                } else {
                    ""
                })
            SkillPrompt.withSkills(base, all) to all
        }
        engine.startConversation(
            prompt,
            history,
            skills.takeIf { available.any { !it.isChain } },
            memory.takeIf { notesAvailable },
            drafts.takeIf { emailEnabled },
        )
        conversationSessionId = session.id
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
        val chain = prompt.takeIf { it.startsWith("/") }?.let { command ->
            val name = command.removePrefix("/").substringBefore(' ').trim()
            _chains.value.firstOrNull { it.name == name }
        }
        if (chain != null) {
            launchChain(chain, prompt, prompt.substringAfter(' ', "").trim(), image)
        } else {
            launchTurn(prompt, image, viaVoice = false)
        }
    }

    /**
     * Lance une chaîne d'agents : chaque agent travaille à l'écran, puis le résumé est enregistré
     * dans la discussion. La détection de détresse passe avant, comme pour un message normal.
     */
    private fun launchChain(chain: Chain, shown: String, request: String, image: PendingImage?) {
        generation = viewModelScope.launch {
            status.value = EngineStatus.GENERATING
            error.value = null
            draft.value = ""
            chainRun.value = null
            pendingImage.value = null
            val session = currentSession.value ?: sessions.mostRecentOrCreate().also { currentSession.value = it }
            repository.add(ChatMessage(sessionId = session.id, role = Role.USER, text = shown, hasImage = image != null))
            sessions.touch(session.id)
            val plan = planner.plan(request)
            if (plan is TurnPlan.Crisis) {
                repository.add(ChatMessage(sessionId = session.id, role = Role.YAMY, text = plan.reply))
                status.value = EngineStatus.READY
                return@launch
            }
            try {
                var last = emptyList<AgentStep>()
                conversationLock.withLock {
                    // La chaîne utilise des générations ponctuelles : la conversation devra être rechargée ensuite.
                    conversationSessionId = null
                    chainRunner.run(chain, request, image?.bytes).collect { steps ->
                        last = steps
                        chainRun.value = ChainRunUi(steps)
                    }
                    val summary = ChainRunner.summary(last)
                    repository.add(ChatMessage(sessionId = session.id, role = Role.YAMY, text = summary))
                    // Le panneau reste affiché jusqu'à ce que le résumé arrive de la base (pas de clignotement).
                    chainRun.value = ChainRunUi(last, summary)
                }
                status.value = EngineStatus.READY
            } catch (e: CancellationException) {
                chainRun.value = null
                throw e
            } catch (e: Throwable) {
                chainRun.value = null
                recoverFromTurnError(e)
            }
        }
    }

    /**
     * Écoute au micro : chaque morceau (coupé sur une pause) est transcrit par Gemma pendant que la
     * personne continue de parler ; à la fin, le texte complet part comme un message normal
     * (détection de détresse comprise), et la réponse est lue à voix haute.
     */
    fun startListening() {
        if (status.value != EngineStatus.READY) return
        speaker.stop()
        error.value = null
        status.value = EngineStatus.LISTENING
        voice.value = VoiceUi()
        voiceJob = viewModelScope.launch {
            val segments = Channel<ShortArray>(Channel.UNLIMITED)
            val transcript = StringBuilder()
            try {
                coroutineScope {
                    launch {
                        for (segment in segments) {
                            val before = transcript.length
                            engine.streamOnce(TRANSCRIBE_PROMPT, ChatInput(TRANSCRIBE_REQUEST, audio = Wav.encode(segment))).collect { piece ->
                                transcript.append(piece)
                                voice.value = voice.value?.copy(transcript = transcript.toString())
                            }
                            if (transcript.length > before) transcript.append(' ')
                        }
                    }
                    var frames = 0
                    recorder.record().collect { event ->
                        when (event) {
                            is SegmenterEvent.Level -> {
                                frames++
                                voice.value = voice.value?.let {
                                    it.copy(levels = (it.levels + event.value).takeLast(LEVEL_BARS), seconds = frames / 10)
                                }
                            }
                            is SegmenterEvent.Segment -> segments.send(event.samples)
                            SegmenterEvent.Finished -> segments.close()
                        }
                    }
                    segments.close()
                }
                val text = transcript.toString().trim()
                voice.value = null
                status.value = EngineStatus.READY
                if (text.isEmpty()) {
                    error.value = "Je n'ai rien entendu. Réessaie en parlant un peu plus près du téléphone."
                } else {
                    launchTurn(text, image = null, viaVoice = true)
                }
            } catch (e: CancellationException) {
                voice.value = null
                if (status.value == EngineStatus.LISTENING) status.value = EngineStatus.READY
                throw e
            } catch (e: Throwable) {
                voice.value = null
                status.value = EngineStatus.READY
                error.value = "Micro : ${e.message}"
            }
        }
    }

    /** Le Scribe relit la discussion courante et met à jour les notes de mémoire. */
    fun rememberSession() {
        if (status.value != EngineStatus.READY) return
        val session = currentSession.value ?: return
        generation = viewModelScope.launch {
            status.value = EngineStatus.GENERATING
            _scribe.value = ScribeUi(working = true)
            try {
                val result = conversationLock.withLock {
                    conversationSessionId = null
                    scribe.remember(repository.recent(session.id, SCRIBE_MESSAGES))
                }
                _scribe.value = ScribeUi(working = false, result = result)
                status.value = EngineStatus.READY
            } catch (e: CancellationException) {
                _scribe.value = null
                throw e
            } catch (e: Throwable) {
                _scribe.value = null
                recoverFromTurnError(e)
            }
        }
    }

    /** Annule le dernier passage du Scribe. */
    fun undoRemember() {
        val result = _scribe.value?.result ?: return
        _scribe.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { scribe.undo(result) }
            engine.invalidateConversation()
        }
    }

    fun dismissScribe() {
        _scribe.value = null
    }

    /** La personne confirme : l'email part via le connecteur MCP Gmail (refusé en mode Hors ligne). */
    fun confirmEmail() {
        val draft = _pendingEmail.value ?: return
        if (_sendingEmail.value) return
        viewModelScope.launch {
            _sendingEmail.value = true
            val text = try {
                val result = withContext(Dispatchers.IO) { gmail.send(draft) }
                if (result.isError) "📧 L'email n'est pas parti : ${result.text}" else "📧 Email envoyé à ${draft.to} (« ${draft.subject} »)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "📧 L'email n'est pas parti : ${e.message}"
            }
            _sendingEmail.value = false
            _pendingEmail.value = null
            currentSession.value?.let { repository.add(ChatMessage(sessionId = it.id, role = Role.YAMY, text = text)) }
        }
    }

    fun cancelEmail() {
        _pendingEmail.value = null
    }

    /** Termine l'écoute : le dernier morceau est transcrit puis envoyé. */
    fun stopListening() = recorder.stop()

    /** Abandonne l'écoute sans rien envoyer. */
    fun cancelListening() {
        voiceJob?.cancel()
    }

    fun stopSpeaking() = speaker.stop()

    private fun launchTurn(prompt: String, image: PendingImage?, viaVoice: Boolean) {
        generation = viewModelScope.launch {
            chainRun.value = null
            status.value = EngineStatus.GENERATING
            error.value = null
            draft.value = ""
            pendingImage.value = null
            val session = currentSession.value ?: sessions.mostRecentOrCreate().also { currentSession.value = it }
            repository.add(ChatMessage(sessionId = session.id, role = Role.USER, text = prompt, hasImage = image != null, viaVoice = viaVoice))
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
                if (viaVoice) speaker.speak(plan.reply)
                return@launch
            }
            val textForModel = (plan as TurnPlan.Model).textForModel
            val reply = StringBuilder()
            try {
                conversationLock.withLock {
                    ensureConversationFor(session)
                    val input = ChatInput(textForModel, listOfNotNull(image?.bytes))
                    engine.send(input).collect { piece ->
                        reply.append(piece)
                        draft.value = reply.toString()
                    }
                    if (reply.isNotBlank()) repository.add(ChatMessage(sessionId = session.id, role = Role.YAMY, text = reply.toString()))
                }
                status.value = EngineStatus.READY
                if (viaVoice) speaker.speak(reply.toString())
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
        viewModelScope.launch(NonCancellable) {
            stopActivity()
            conversationLock.withLock {
                resetScreenState()
                memoryIndex = ""
                conversationSessionId = null
                wiper.wipeAll()
                currentSession.value = sessions.mostRecentOrCreate()
            }
            if (status.value != EngineStatus.MODEL_MISSING) status.value = EngineStatus.READY
            onDone()
        }
    }

    /** Efface la conversation affichée et repart d'un contexte vierge. */
    fun clearConversation() {
        viewModelScope.launch {
            stopActivity()
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
                // Arrêter d'abord : une réponse qui se terminerait après la suppression laisserait un message orphelin.
                stopActivity()
                sessions.delete(id)
                switchTo { sessions.mostRecentOrCreate() }
            } else {
                sessions.delete(id)
            }
        }
    }

    private fun switchTo(onOpened: () -> Unit = {}, target: suspend () -> Session?) {
        viewModelScope.launch {
            stopActivity()
            val session = target() ?: return@launch
            conversationLock.withLock {
                currentSession.value = session
                resetScreenState()
                // Le contexte est rechargé maintenant si le moteur est prêt, sinon au prochain tour
                // (conversationSessionId ne correspond plus) : jamais de réponse avec une autre discussion.
                if (status.value == EngineStatus.READY) {
                    status.value = EngineStatus.LOADING
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

    /** Arrête l'écoute, la voix et la génération en cours, et attend qu'elles soient vraiment finies. */
    private suspend fun stopActivity() {
        speaker.stop()
        voiceJob?.cancelAndJoin()
        generation?.cancelAndJoin()
    }

    /** Vide tout ce que l'écran garde en mémoire vive pour la discussion courante. */
    private fun resetScreenState() {
        chainRun.value = null
        voice.value = null
        _scribe.value = null
        _pendingEmail.value = null
        draft.value = ""
        error.value = null
        pendingImage.value = null
    }

    /** Recharge le contexte du moteur s'il est périmé ou s'il appartient à une autre discussion. */
    private suspend fun ensureConversationFor(session: Session) {
        if (!engine.hasConversation || conversationSessionId != session.id) restartConversation()
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
        private const val LEVEL_BARS = 24
        private const val SCRIBE_MESSAGES = 16
        private const val TRANSCRIBE_REQUEST = "Transcris cet audio."
        private const val TRANSCRIBE_PROMPT =
            "Tu es un outil de transcription. Écris uniquement, mot pour mot, ce qui est dit dans l'audio. Aucune réponse, aucun commentaire."

        private const val DEFAULT_IMAGE_PROMPT = "Regarde cette photo et dis-moi ce que tu en penses."
    }
}
