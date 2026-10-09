package app.yamy.engine

import app.yamy.mcp.EmailDrafts
import app.yamy.skills.SkillLookup
import kotlinx.coroutines.flow.Flow

/** Backend de calcul demandé au runtime. CPU par défaut (voir docs/MESURES.md). */
enum class ComputeBackend { CPU, GPU }

/**
 * Message à envoyer au modèle : texte, images optionnelles (octets JPEG/PNG) et audio optionnel
 * (fichier WAV 16 kHz mono, 30 s maximum : limite de Gemma 4).
 */
class ChatInput(val text: String, val images: List<ByteArray> = emptyList(), val audio: ByteArray? = null)

/** Un tour de parole déjà échangé, rejoué au moteur pour rétablir le contexte. */
data class Turn(val fromUser: Boolean, val text: String)

/**
 * Abstraction du moteur de langage embarqué. L'UI et la logique de chat ne
 * dépendent que de cette interface, jamais d'un runtime précis (DIP).
 */
interface ChatEngine : AutoCloseable {
    /** Vrai si une conversation est prête à recevoir un message. */
    val hasConversation: Boolean

    /** Charge le modèle en mémoire (sans effet s'il l'est déjà avec ce backend). */
    suspend fun load(backend: ComputeBackend = ComputeBackend.CPU)

    /**
     * Démarre une conversation, avec un prompt système et un historique optionnels.
     * Si [skills] est fourni, le modèle peut charger un skill par son nom ; si [notes] l'est, il peut lire
     * une note de mémoire.
     */
    suspend fun startConversation(
        systemPrompt: String? = null,
        history: List<Turn> = emptyList(),
        skills: SkillLookup? = null,
        notes: SkillLookup? = null,
        emailDrafts: EmailDrafts? = null,
    )

    /**
     * Marque la conversation courante comme périmée : le prochain tour la redémarre. Ne la ferme pas
     * immédiatement, pour ne jamais couper une réponse en cours de génération.
     */
    suspend fun invalidateConversation()

    /** Envoie un message et émet la réponse au fil de sa génération. */
    fun send(input: ChatInput): Flow<String>

    /**
     * Génération ponctuelle, hors conversation. Invalide la conversation courante
     * (le moteur n'en gère qu'une à la fois).
     */
    suspend fun complete(systemPrompt: String, userText: String): String

    /**
     * Génération ponctuelle diffusée au fil de l'eau (ex. transcription d'un extrait vocal),
     * hors conversation. Invalide la conversation courante, comme [complete].
     */
    fun streamOnce(systemPrompt: String, input: ChatInput): Flow<String>
}
