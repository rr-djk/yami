package app.yamy.engine

import android.content.Context
import app.yamy.skills.SkillLookup
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Implémentation de [ChatEngine] sur LiteRT-LM (Gemma 4 E2B). */
class LiteRtChatEngine(
    private val context: Context,
    private val locator: ModelLocator,
) : ChatEngine {

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var loadedBackend: ComputeBackend? = null
    private val lifecycle = Mutex()

    override val hasConversation: Boolean get() = conversation != null

    override suspend fun load(backend: ComputeBackend) = lifecycle.withLock {
        if (engine != null && loadedBackend == backend) return@withLock
        withContext(Dispatchers.Default) {
            check(locator.isPresent()) { "Modèle introuvable : ${locator.modelFile.path}" }
            release()
            val created = Engine(engineConfig(backend))
            try {
                created.initialize()
            } catch (e: Throwable) {
                created.close()
                throw e
            }
            engine = created
            loadedBackend = backend
        }
    }

    override suspend fun startConversation(systemPrompt: String?, history: List<Turn>, skills: SkillLookup?) {
        lifecycle.withLock {
            val loaded = checkNotNull(engine) { "Moteur non chargé" }
            withContext(Dispatchers.Default) {
                conversation?.close()
                conversation = loaded.createConversation(
                    ConversationConfig(
                        systemInstruction = systemPrompt?.let { Contents.of(it) },
                        initialMessages = history.map { if (it.fromUser) Message.user(it.text) else Message.model(it.text) },
                        tools = skills?.let { listOf(tool(SkillToolSet(it))) }.orEmpty(),
                    ),
                )
            }
        }
    }

    override suspend fun invalidateConversation() {
        lifecycle.withLock {
            withContext(Dispatchers.Default) {
                conversation?.close()
                conversation = null
            }
        }
    }

    override fun send(input: ChatInput): Flow<String> {
        val active = checkNotNull(conversation) { "Conversation non démarrée" }
        val parts = input.images.map<ByteArray, Content> { Content.ImageBytes(it) } + Content.Text(input.text)
        return active.sendMessageAsync(Contents.of(parts)).map { it.toString() }.flowOn(Dispatchers.Default)
    }

    override suspend fun complete(systemPrompt: String, userText: String): String = lifecycle.withLock {
        val loaded = checkNotNull(engine) { "Moteur non chargé" }
        withContext(Dispatchers.Default) {
            conversation?.close()
            conversation = null
            loaded.createConversation(ConversationConfig(systemInstruction = Contents.of(systemPrompt))).use { oneShot ->
                oneShot.sendMessageAsync(userText).map { it.toString() }.fold(StringBuilder()) { acc, piece -> acc.append(piece) }.toString()
            }
        }
    }

    override fun close() = release()

    private fun release() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        loadedBackend = null
    }

    private fun engineConfig(backend: ComputeBackend) = EngineConfig(
        modelPath = locator.modelFile.path,
        backend = when (backend) {
            ComputeBackend.CPU -> Backend.CPU()
            ComputeBackend.GPU -> Backend.GPU()
        },
        visionBackend = Backend.CPU(),
        cacheDir = context.cacheDir.path,
    )
}
