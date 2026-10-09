package app.yamy.engine

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Implémentation de [ChatEngine] sur LiteRT-LM (Gemma 4 E2B). */
class LiteRtChatEngine(
    private val context: Context,
    private val locator: ModelLocator,
) : ChatEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun load(backend: ComputeBackend) = withContext(Dispatchers.Default) {
        check(locator.isPresent()) { "Modèle introuvable : ${locator.modelFile.path}" }
        close()
        val config = EngineConfig(
            modelPath = locator.modelFile.path,
            backend = when (backend) {
                ComputeBackend.CPU -> Backend.CPU()
                ComputeBackend.GPU -> Backend.GPU()
            },
            cacheDir = context.cacheDir.path,
        )
        engine = Engine(config).also { it.initialize() }
    }

    override fun startConversation(systemPrompt: String?) {
        val loaded = checkNotNull(engine) { "Moteur non chargé" }
        conversation?.close()
        conversation = loaded.createConversation(
            if (systemPrompt == null) {
                ConversationConfig()
            } else {
                ConversationConfig(systemInstruction = Contents.of(systemPrompt))
            },
        )
    }

    override fun send(text: String): Flow<String> {
        val active = checkNotNull(conversation) { "Conversation non démarrée" }
        return active.sendMessageAsync(text).map { it.toString() }.flowOn(Dispatchers.Default)
    }

    override fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
