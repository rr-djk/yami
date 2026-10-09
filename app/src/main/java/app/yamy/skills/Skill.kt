package app.yamy.skills

/**
 * Tâche réutilisable créée par l'utilisateur. Format de fichier : `SKILL.md`
 * (en-tête `name` + `description`, puis instructions), comme dans google-ai-edge/gallery.
 */
data class Skill(
    val name: String,
    val description: String,
    val instructions: String,
    /** [TYPE_SIMPLE] (consignes chargées par le modèle) ou [TYPE_CHAINE] (agents enchaînés par l'orchestrateur). */
    val type: String = TYPE_SIMPLE,
) {
    val isChain: Boolean get() = type == TYPE_CHAINE

    companion object {
        const val TYPE_SIMPLE = "simple"
        const val TYPE_CHAINE = "chaine"
    }
}

/** Recherche d'instructions par nom, exposée au moteur sans le lier au stockage. */
fun interface SkillLookup {
    fun instructionsFor(name: String): String?
}
