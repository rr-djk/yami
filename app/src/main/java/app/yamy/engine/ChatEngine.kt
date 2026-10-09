package app.yamy.engine

import kotlinx.coroutines.flow.Flow

/** Backend de calcul demandé au runtime. CPU par défaut (voir docs/MESURES.md). */
enum class ComputeBackend { CPU, GPU }

/**
 * Abstraction du moteur de langage embarqué. L'UI et la logique de chat ne
 * dépendent que de cette interface, jamais d'un runtime précis (DIP).
 */
interface ChatEngine : AutoCloseable {
    /** Charge le modèle en mémoire. Lourd : à appeler hors du thread principal. */
    suspend fun load(backend: ComputeBackend)

    /** Démarre une nouvelle conversation, avec un prompt système optionnel. */
    fun startConversation(systemPrompt: String? = null)

    /** Envoie un message et émet la réponse au fil de sa génération. */
    fun send(text: String): Flow<String>
}
