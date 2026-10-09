package app.yamy.engine

import kotlinx.coroutines.flow.Flow

/** Backend de calcul demandé au runtime. CPU par défaut (voir docs/MESURES.md). */
enum class ComputeBackend { CPU, GPU }

/** Un tour de parole déjà échangé, rejoué au moteur pour rétablir le contexte. */
data class Turn(val fromUser: Boolean, val text: String)

/**
 * Abstraction du moteur de langage embarqué. L'UI et la logique de chat ne
 * dépendent que de cette interface, jamais d'un runtime précis (DIP).
 */
interface ChatEngine : AutoCloseable {
    /** Charge le modèle en mémoire. Lourd : à appeler hors du thread principal. */
    suspend fun load(backend: ComputeBackend = ComputeBackend.CPU)

    /** Démarre une conversation, avec un prompt système et un historique optionnels. */
    fun startConversation(systemPrompt: String? = null, history: List<Turn> = emptyList())

    /** Envoie un message et émet la réponse au fil de sa génération. */
    fun send(text: String): Flow<String>
}
