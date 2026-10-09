package app.yamy.safety

import java.text.Normalizer

/**
 * Réponse écrite en code, jamais générée : sobre, sans promesse, avec orientation vers une aide humaine.
 * Elle remplace l'appel au modèle quand la détresse est aiguë, dans la langue (français ou anglais) et
 * avec les ressources adaptées (idées suicidaires / violence subie) du message.
 */
class CrisisResponder {

    fun reply(userText: String = ""): String {
        val normalized = normalize(userText)
        val violence = VIOLENCE.containsMatchIn(normalized)
        val english = isEnglish(normalized)
        val lines = (if (violence) Helplines.VIOLENCE else Helplines.SELF_HARM)
            .joinToString("\n") { "• ${it.label} (${it.country}) : ${it.number}" }
        return when {
            english && violence -> "I'm glad you told me, and I take this seriously.\n\nI'm an AI: I can't help in an emergency or replace a real person. If you are in danger, contact someone who can act right now:\n\n$lines\n\nIf you can, get to a safe place and tell someone you trust. I'm still here if you want to keep writing."
            english -> "I'm glad you told me, and I take you seriously.\n\nI'm an AI: I can't help in an emergency or replace a real person. If you are in danger or thinking about hurting yourself, please talk right now to someone who can act:\n\n$lines\n\nIf you can, call someone you trust and don't stay alone with this. I'm still here if you want to keep writing."
            violence -> "Je suis content que tu m'en parles, et ce que tu décris est grave. Tu n'y es pour rien.\n\nJe suis une IA : je ne peux pas t'aider en urgence ni remplacer une vraie personne. Si tu es en danger, contacte quelqu'un qui peut agir maintenant :\n\n$lines\n\nSi tu peux, mets-toi en sécurité et parle à un proche de confiance. Je reste là si tu veux continuer à écrire."
            else -> "Je suis content que tu m'en parles, et je te prends au sérieux.\n\nJe suis une IA : je ne peux pas t'aider en urgence ni remplacer une vraie personne. Si tu es en danger ou si tu penses à te faire du mal, parle maintenant à quelqu'un qui peut agir :\n\n$lines\n\nSi tu peux, appelle aussi un proche ou quelqu'un de confiance, et ne reste pas seul(e) avec ça. Je reste là si tu veux continuer à écrire."
        }
    }

    private fun normalize(text: String) =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('’', '\'')

    private fun isEnglish(normalized: String) =
        ENGLISH_WORDS.findAll(normalized).map { it.value }.toSet().size >= 2

    private companion object {
        val VIOLENCE = Regex("\\b(frappe|bat|tape|tabasse|etrangle|viole|violee|agresse|agressee|abus|hits|beats|raped|abuses|assault)")
        val ENGLISH_WORDS = Regex("\\b(i|my|myself|want|hurt|kill|end|life|he|she|hits|beats|raped|die|dying|am|going|to)\\b")
    }
}
