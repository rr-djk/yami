package app.yamy.agents

import app.yamy.skills.Skill

/** Un agent d'une chaîne : un rôle court et une seule consigne (adapté à un petit modèle). */
data class AgentSpec(val emoji: String, val title: String, val instruction: String)

/** Une chaîne d'agents, décrite dans un `SKILL.md` de type « chaine ». */
data class Chain(val name: String, val description: String, val agents: List<AgentSpec>)

/** Lecture des sections `## <emoji> <Rôle>` d'un skill de type chaîne. Logique pure. */
object ChainFormat {
    const val MAX_AGENTS = 4

    fun parse(skill: Skill): Chain? {
        if (!skill.isChain) return null
        val agents = ArrayList<AgentSpec>()
        var heading: String? = null
        val body = StringBuilder()
        fun flush() {
            val h = heading ?: return
            val instruction = body.toString().trim()
            if (instruction.isNotEmpty()) agents += agentFrom(h, instruction)
        }
        for (line in skill.instructions.lines()) {
            if (line.startsWith("## ")) {
                flush()
                heading = line.removePrefix("## ").trim()
                body.clear()
            } else if (heading != null) {
                body.appendLine(line)
            }
        }
        flush()
        if (agents.size < 2) return null
        return Chain(skill.name, skill.description, agents.take(MAX_AGENTS))
    }

    private fun agentFrom(heading: String, instruction: String): AgentSpec {
        val first = heading.substringBefore(' ')
        val hasEmoji = first.isNotEmpty() && first.none { it.isLetterOrDigit() }
        return if (hasEmoji) {
            AgentSpec(first, heading.substringAfter(' ').trim(), instruction)
        } else {
            AgentSpec("🔹", heading, instruction)
        }
    }
}
