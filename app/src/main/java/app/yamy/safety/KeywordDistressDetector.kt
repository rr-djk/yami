package app.yamy.safety

import java.text.Normalizer

/**
 * Détection par règles (français et anglais). Volontairement prudente : en cas de doute on monte
 * d'un niveau, un faux positif coûte une réponse trop sobre, un faux négatif peut coûter bien plus.
 */
class KeywordDistressDetector : DistressDetector {

    override fun assess(text: String): Distress {
        val normalized = normalize(text)
        val cleaned = EXCEPTIONS.fold(normalized) { acc, rule -> rule.replace(acc, " ") }
        return when {
            ACUTE_RULES.any { it.containsMatchIn(cleaned) } -> Distress.ACUTE
            ELEVATED_RULES.any { it.containsMatchIn(cleaned) } -> Distress.ELEVATED
            else -> Distress.NONE
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')
            .replace(Regex("[^a-z0-9' ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        fun rules(vararg patterns: String) = patterns.map { Regex(it) }

        /** Expressions courantes qui ressemblent à une détresse sans en être une. */
        val EXCEPTIONS = rules(
            "\\bmourir de (rire|faim|honte|fatigue|chaud|froid|peur|soif|sommeil)\\b",
            "\\bmort de (rire|faim|honte|fatigue|chaud|froid|peur|soif|sommeil)\\b",
            "\\bme tuer a la tache\\b",
            "\\b(dying|dead) (of|from) (laughter|laughing|embarrassment|boredom)\\b",
        )

        val ACUTE_RULES = rules(
            // Idées suicidaires
            "\\bme suicid",
            "\\bsuicid",
            "\\bme tuer\\b",
            "\\bme donner la mort\\b",
            "\\bmettre fin a (mes jours|ma vie)\\b",
            "\\ben finir (avec )?(la vie|tout)\\b",
            "\\bj'en finir\\b",
            "\\bplus envie de vivre\\b",
            "\\b(envie|veux|voudrais|souhaite|aimerais) (de )?mourir\\b",
            "\\bvouloir mourir\\b",
            "\\bmieux sans moi\\b",
            "\\bme jeter (du|par|sous)\\b",
            "\\bavaler (tous |toutes )?(mes |les )?(cachets|comprimes|medicaments|pilules)\\b",
            "\\bkill myself\\b",
            "\\bwant to die\\b",
            "\\bend my (own )?life\\b",
            "\\bbetter off without me\\b",
            "\\bsuicidal\\b",
            // Automutilation
            "\\bme faire du mal\\b",
            "\\bme blesser\\b",
            "\\bme scarifi",
            "\\bautomutil",
            "\\bme couper les (veines|poignets)\\b",
            "\\bhurt myself\\b",
            "\\bself.?harm",
            "\\bcut myself\\b",
            // Violence subie ou danger immédiat
            "\\b(il|elle|ils|mon pere|ma mere|mon mari|ma femme|mon copain|ma copine|mon conjoint|ma conjointe|mon ex|quelqu'un|on) (me|m') ?(frappe|bat|tape|tabasse|etrangle|menace|violente|a frappe|a battu|a viole|a viole|a agresse|a etrangle|a menace)",
            "\\bje suis (battu|battue|viole|violee|agresse|agressee|victime de violences?)\\b",
            "\\bon m'a (viole|agresse|frappe|battu)",
            "\\b(abuse|abusee) sexuellement\\b",
            "\\bje suis en danger\\b",
            "\\bj'ai peur pour ma vie\\b",
            "\\b(he|she|they) (hits|beats|hit|beat|raped|abuses) me\\b",
            "\\bi was raped\\b",
            "\\bi'm in danger\\b",
        )

        val ELEVATED_RULES = rules(
            "\\bplus aucun espoir\\b",
            "\\bdesespoir\\b",
            "\\bdesespere",
            "\\ba quoi bon\\b",
            "\\bje suis un poids\\b",
            "\\bpersonne ne m'aime\\b",
            "\\bpersonne ne me comprend\\b",
            "\\bje n'en peux plus\\b",
            "\\bj'en peux plus\\b",
            "\\bje craque\\b",
            "\\btout est inutile\\b",
            "\\bje deteste ma vie\\b",
            "\\bje suis (\\w+ )?(seul|seule)\\b",
            "\\bje me sens (\\w+ )?(seul|seule|vide|nul|nulle|inutile)\\b",
            "\\bdepressi",
            "\\bdeprime",
            "\\bidees noires\\b",
            "\\bcrise d'angoisse\\b",
            "\\bcrise de panique\\b",
            "\\bi feel (so |very )?(alone|empty|worthless|hopeless)\\b",
            "\\bno hope\\b",
            "\\bi can't go on\\b",
        )
    }
}
