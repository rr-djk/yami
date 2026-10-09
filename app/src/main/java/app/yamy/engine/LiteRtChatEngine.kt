package app.yamy.engine

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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

    override suspend fun load(backend: ComputeBackend) = lifecycle.withLock {
        if (engine != null && loadedBackend == backend) return@withLock
        withContext(Dispatchers.Default) {
            check(locator.isPresent()) { "Modèle introuvable : ${locator.modelFile.path}" }
            release()
        val config = EngineConfig(
            modelPath = locator.modelFile.path,
            backend = when (backend) {
                ComputeBackend.CPU -> Backend.CPU()
                ComputeBackend.GPU -> Backend.GPU()
            },
            visionBackend = Backend.CPU(),
            cacheDir = context.cacheDir.path,
        )
        val created = Engine(config)
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

    override suspend fun startConversation(systemPrompt: String?, history: List<Turn>) = lifecycle.withLock {
        val loaded = checkNotNull(engine) { "Moteur non chargé" }
        withContext(Dispatchers.Default) {
        conversation?.close()
        conversation = loaded.createConversation(
            ConversationConfig(
                systemInstruction = systemPrompt?.let { Contents.of(it) },
                initialMessages = history.map { if (it.fromUser) Message.user(it.text) else Message.model(it.text) },
            ),
        )
        }
    }

    override fun send(input: ChatInput): Flow<String> {
        val active = checkNotNull(conversation) { "Conversation non démarrée" }
        val parts = input.images.map<ByteArray, Content> { Content.ImageBytes(it) } + Content.Text(input.text)
        return active.sendMessageAsync(Contents.of(parts)).map { it.toString() }.flowOn(Dispatchers.Default)
    }

    override fun close() = release()

    private fun release() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        loadedBackend = null
    }
}
