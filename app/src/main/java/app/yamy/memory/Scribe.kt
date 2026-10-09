package app.yamy.memory

import app.yamy.chat.ChatMessage
import app.yamy.chat.Role
import app.yamy.engine.ChatEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Résultat d'un passage du Scribe : les notes écrites et leur version précédente (pour Annuler). */
data class ScribeResult(val written: List<MemoryNote>, val previous: Map<String, MemoryNote?>, val raw: String = "")

/**
 * Agent Scribe : relit une discussion et met à jour les notes de mémoire (Karpathy « LLM Wiki » :
 * index d'une ligne par note + notes reliées). Tout reste chiffré sur le téléphone.
 */
class Scribe(private val engine: ChatEngine, private val memory: MemoryRepository) {

    suspend fun remember(messages: List<ChatMessage>): ScribeResult {
        val transcript = messages.takeLast(MAX_MESSAGES).joinToString("\n") {
            (if (it.role == Role.USER) "Personne : " else "Yamy : ") + it.text.take(MAX_CHARS)
        }
        if (transcript.isBlank()) return ScribeResult(emptyList(), emptyMap())
        val existing = withContext(Dispatchers.IO) { memory.list() }
        val raw = engine.complete(SYSTEM, buildString {
            appendLine("Notes existantes :")
            appendLine(MemoryFormat.index(existing).ifBlank { "(aucune)" })
            appendLine()
            appendLine("Discussion :")
            append(transcript)
        })
        val updates = MemoryFormat.parseScribe(raw)
        return withContext(Dispatchers.IO) {
            val previous = updates.associate { it.name to memory.get(it.name) }
            val written = updates.map { update -> MemoryFormat.merge(previous[update.name], update).also(memory::save) }
            ScribeResult(written, previous, raw)
        }
    }

    /** Annule un passage : restaure les notes modifiées, supprime celles qui ont été créées. */
    fun undo(result: ScribeResult) {
        result.written.forEach { written ->
            // Si la personne a modifié la note entre-temps, on respecte sa version.
            if (memory.get(written.name) != written) return@forEach
            val before = result.previous[written.name]
            if (before == null) memory.delete(written.name) else memory.save(before)
        }
    }

    private companion object {
        const val MAX_MESSAGES = 16
        const val MAX_CHARS = 400
        val SYSTEM = """
            Tu es le Scribe de Yamy. Tu tiens un carnet de notes sur la personne, comme un wiki personnel.
            Relis la discussion et retiens seulement ce qui sera utile plus tard : ses projets, ses proches, ses préférences, ce qui la préoccupe.
            Ne retiens jamais de mot de passe, de numéro ni d'adresse. Pas d'invention : seulement ce que la personne a dit.
            Une note par sujet ou par personne, avec un nom court d'un ou deux mots (ex. : examens, lea, travail).
            Réutilise le nom d'une note existante quand le sujet est le même. Relie une note aux AUTRES notes avec [[nom]], jamais à elle-même.
            Réponds UNIQUEMENT dans ce format, pour 1 à 3 notes :
            NOTE: nom-court-en-minuscules | résumé en une ligne
            Une à trois phrases à retenir, avec des [[liens]] si utile.

            S'il n'y a rien d'utile à retenir, réponds seulement : RIEN
        """.trimIndent()
    }
}
