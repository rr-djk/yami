package app.yamy.skills

/** Ajoute au prompt système la liste des skills disponibles (chargement progressif). */
object SkillPrompt {
    fun withSkills(base: String, skills: List<Skill>): String {
        if (skills.isEmpty()) return base
        return buildString {
            append(base.trimEnd())
            appendLine()
            appendLine()
            appendLine("## Skills disponibles")
            appendLine("L'utilisateur a créé ces tâches. Quand sa demande correspond à l'une d'elles, appelle d'abord l'outil loadSkill avec son nom, puis suis les instructions reçues.")
            skills.forEach { appendLine("- ${it.name} : ${it.description}") }
        }
    }
}
