package app.yamy.engine

import app.yamy.skills.SkillLookup
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** Outil exposé à Gemma : lit une note de la mémoire de Yamy (seul l'index est dans le prompt). */
class NoteToolSet(private val notes: SkillLookup) : ToolSet {
    @Tool(description = "Lit une note de la mémoire de Yamy sur la personne, à partir de son nom.")
    fun lireNote(
        @ToolParam(description = "Le nom exact de la note, tel qu'il apparaît entre [[ ]] dans l'index") nom: String,
    ): Map<String, Any> {
        val content = notes.instructionsFor(nom.trim().removePrefix("[[").removeSuffix("]]"))
        return if (content != null) mapOf("note" to content) else mapOf("error" to "Note introuvable : $nom")
    }
}
