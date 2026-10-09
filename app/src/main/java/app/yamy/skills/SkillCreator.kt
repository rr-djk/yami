package app.yamy.skills

import app.yamy.engine.ChatEngine

/** Fait écrire un `SKILL.md` à Yamy à partir de la description de l'utilisateur. */
class SkillCreator(private val engine: ChatEngine) {

    /** Renvoie le brouillon : valide, ou le texte brut du modèle avec la raison du refus. */
    suspend fun draft(request: String): Draft {
        val raw = engine.complete(SYSTEM_PROMPT, request.trim())
        return when (val parsed = SkillFormat.parse(raw)) {
            is SkillFormat.Result.Valid -> Draft(parsed.skill, raw, null)
            is SkillFormat.Result.Invalid -> Draft(null, raw, parsed.reason)
        }
    }

    data class Draft(val skill: Skill?, val raw: String, val problem: String?)

    private companion object {
        val SYSTEM_PROMPT = """
            Tu écris des fichiers SKILL.md pour l'assistant Yamy. Réponds UNIQUEMENT avec le fichier, sans commentaire, dans ce format exact :

            ---
            name: nom-en-minuscules-avec-tirets
            description: une phrase qui dit quand utiliser ce skill
            ---

            Instructions claires, en français, à la deuxième personne, que Yamy suivra étape par étape.

            Règles : name en minuscules, chiffres et tirets seulement (40 caractères max) ; description sur une seule ligne (200 caractères max) ; instructions courtes et concrètes.
        """.trimIndent()
    }
}
