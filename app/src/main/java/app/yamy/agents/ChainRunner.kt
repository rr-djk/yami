package app.yamy.agents

import app.yamy.engine.ChatEngine
import app.yamy.engine.ChatInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class StepStatus { WAITING, WORKING, DONE }

data class AgentStep(val agent: AgentSpec, val status: StepStatus = StepStatus.WAITING, val output: String = "")

/**
 * Orchestrateur écrit en code (pas un modèle) : il fait travailler les agents l'un après l'autre et
 * transmet à chacun ce que les précédents ont produit. Avec un petit modèle, un enchaînement
 * déterministe est bien plus fiable qu'un agent qui déciderait seul de la suite.
 */
class ChainRunner(private val engine: ChatEngine) {

    /** Émet l'état de toutes les étapes à chaque morceau généré. */
    fun run(chain: Chain, request: String, image: ByteArray? = null): Flow<List<AgentStep>> = flow {
        val steps = chain.agents.map { AgentStep(it) }.toMutableList()
        emit(steps.toList())
        for ((index, agent) in chain.agents.withIndex()) {
            steps[index] = steps[index].copy(status = StepStatus.WORKING)
            emit(steps.toList())
            val output = StringBuilder()
            val input = ChatInput(
                text = promptFor(request, steps.take(index)),
                images = if (index == 0 && image != null) listOf(image) else emptyList(),
            )
            engine.streamOnce(systemFor(agent), input).collect { piece ->
                output.append(piece)
                steps[index] = steps[index].copy(output = output.toString())
                emit(steps.toList())
            }
            steps[index] = steps[index].copy(status = StepStatus.DONE, output = output.toString().trim())
            emit(steps.toList())
        }
    }

    private fun systemFor(agent: AgentSpec) = buildString {
        appendLine("Tu es « ${agent.title} », un des agents de Yamy, une IA bienveillante qui tourne sur le téléphone de la personne.")
        appendLine(agent.instruction)
        append(
            "Tu tutoies la personne. Ne te présente pas et ne salue pas : va droit à ton rôle. " +
                "Réponds en français, en 2 à 5 phrases, en texte simple (pas de formules LaTeX ni de titres). " +
                "Tu es une IA, pas un professionnel de santé.",
        )
    }

    private fun promptFor(request: String, previous: List<AgentStep>) = buildString {
        appendLine("Demande de la personne : ${request.ifBlank { "(voir l'image)" }}")
        previous.forEach {
            appendLine()
            appendLine("Ce qu'a dit ${it.agent.title} :")
            appendLine(it.output)
        }
        appendLine()
        append("À toi.")
    }

    companion object {
        /** Texte enregistré dans la discussion une fois la chaîne terminée. */
        fun summary(steps: List<AgentStep>) =
            steps.joinToString("\n\n") { "${it.agent.emoji} ${it.agent.title}\n${it.output}" }
    }
}
