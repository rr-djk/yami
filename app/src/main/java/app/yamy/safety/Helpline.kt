package app.yamy.safety

/** Un service d'écoute ou d'urgence humain. */
data class Helpline(val country: String, val label: String, val number: String)

/**
 * Services d'aide proposés dans la réponse de crise.
 * NUMÉROS À VÉRIFIER AVANT LA DÉMO, et adapter au pays de la démo (voir PLAN.md §9).
 */
object Helplines {
    val SELF_HARM = listOf(
        Helpline("France", "Prévention du suicide, 24 h/24", "3114"),
        Helpline("France", "Urgence médicale (SAMU)", "15"),
        Helpline("Canada", "Ligne de crise suicide (appel ou texto)", "9-8-8"),
        Helpline("Europe", "Urgences", "112"),
    )

    val VIOLENCE = listOf(
        Helpline("France", "Violences conjugales, écoute (3919)", "3919"),
        Helpline("France", "Police secours", "17"),
        Helpline("Canada", "Urgences", "911"),
        Helpline("Europe", "Urgences", "112"),
    )
}
