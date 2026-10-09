package app.yamy.engine

import app.yamy.skills.SkillLookup
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** Outil exposé à Gemma : charge les instructions d'un skill de l'utilisateur. */
class SkillToolSet(private val skills: SkillLookup) : ToolSet {
    @Tool(description = "Charge les instructions d'un skill créé par l'utilisateur, à partir de son nom.")
    fun loadSkill(
        @ToolParam(description = "Le nom exact du skill, tel que listé dans les skills disponibles") name: String,
    ): Map<String, Any> {
        val instructions = skills.instructionsFor(name.trim())
        return if (instructions != null) {
            mapOf("instructions" to instructions)
        } else {
            mapOf("error" to "Skill introuvable : $name")
        }
    }
}
