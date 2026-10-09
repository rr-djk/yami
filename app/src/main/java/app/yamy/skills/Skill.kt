package app.yamy.skills

/**
 * Tâche réutilisable créée par l'utilisateur. Format de fichier : `SKILL.md`
 * (en-tête `name` + `description`, puis instructions), comme dans google-ai-edge/gallery.
 */
data class Skill(
    val name: String,
    val description: String,
    val instructions: String,
)

/** Recherche d'instructions par nom, exposée au moteur sans le lier au stockage. */
fun interface SkillLookup {
    fun instructionsFor(name: String): String?
}
