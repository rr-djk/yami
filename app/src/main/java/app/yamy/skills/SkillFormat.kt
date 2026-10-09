package app.yamy.skills

/** Lecture et écriture du format `SKILL.md`. Logique pure, testable sans Android. */
object SkillFormat {
    const val MAX_NAME = 40
    const val MAX_DESCRIPTION = 200
    const val MAX_INSTRUCTIONS = 4000

    private val NAME_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    sealed interface Result {
        data class Valid(val skill: Skill) : Result
        data class Invalid(val reason: String) : Result
    }

    fun format(skill: Skill): String = buildString {
        appendLine("---")
        appendLine("name: ${skill.name}")
        appendLine("description: ${skill.description}")
        if (skill.isChain) appendLine("type: ${Skill.TYPE_CHAINE}")
        appendLine("---")
        appendLine()
        append(skill.instructions.trim())
        appendLine()
    }

    /** Analyse un texte `SKILL.md`, en tolérant les clôtures de code que les petits modèles ajoutent. */
    fun parse(raw: String): Result {
        val text = stripCodeFence(raw.trim())
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return Result.Invalid("L'en-tête `---` est absent.")
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return Result.Invalid("L'en-tête n'est pas refermé par `---`.")

        val header = lines.subList(1, end + 1)
            .mapNotNull { line -> line.substringBefore(':', "").trim().takeIf { it.isNotEmpty() }?.let { it to line.substringAfter(':').trim() } }
            .toMap()
        val instructions = lines.drop(end + 2).joinToString("\n").trim()

        val type = if (header["type"] == Skill.TYPE_CHAINE) Skill.TYPE_CHAINE else Skill.TYPE_SIMPLE
        return validate(Skill(header["name"].orEmpty(), header["description"].orEmpty(), instructions, type))
    }

    fun validate(skill: Skill): Result {
        val problem = when {
            skill.name.isEmpty() -> "Le nom est vide."
            skill.name.length > MAX_NAME -> "Le nom dépasse $MAX_NAME caractères."
            !NAME_PATTERN.matches(skill.name) -> "Le nom doit être en minuscules, chiffres et tirets (ex. preparer-entretien)."
            skill.description.isEmpty() -> "La description est vide."
            skill.description.length > MAX_DESCRIPTION -> "La description dépasse $MAX_DESCRIPTION caractères."
            '\n' in skill.description -> "La description doit tenir sur une ligne."
            skill.instructions.isEmpty() -> "Les instructions sont vides."
            skill.instructions.length > MAX_INSTRUCTIONS -> "Les instructions dépassent $MAX_INSTRUCTIONS caractères."
            else -> null
        }
        return if (problem == null) Result.Valid(skill) else Result.Invalid(problem)
    }

    /** Transforme un titre libre en nom valide : « Préparer mon entretien ! » → « preparer-mon-entretien ». */
    fun slugify(title: String): String =
        java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(MAX_NAME)
            .trim('-')

    private fun stripCodeFence(text: String): String {
        if (!text.startsWith("```")) return text
        val withoutOpening = text.substringAfter('\n', "")
        return withoutOpening.removeSuffix("```").trim()
    }
}
