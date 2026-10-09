package app.yamy.safety

/** Décision prise avant tout appel au modèle. */
sealed interface TurnPlan {
    /** Détresse aiguë : réponse écrite en code, le modèle n'est pas appelé. */
    data class Crisis(val reply: String) : TurnPlan

    /** Réponse du modèle, avec [textForModel] (qui peut contenir une consigne discrète de douceur). */
    data class Model(val textForModel: String, val distress: Distress) : TurnPlan
}

class TurnPlanner(
    private val detector: DistressDetector = KeywordDistressDetector(),
    private val responder: CrisisResponder = CrisisResponder(),
) {
    fun plan(userText: String): TurnPlan = when (val level = detector.assess(userText)) {
        Distress.ACUTE -> TurnPlan.Crisis(responder.reply(userText))
        Distress.ELEVATED -> TurnPlan.Model(GENTLE_NOTE + userText, level)
        Distress.NONE -> TurnPlan.Model(userText, level)
    }

    private companion object {
        /** Consigne invisible pour l'utilisateur, ajoutée seulement à ce qui est envoyé au modèle. */
        const val GENTLE_NOTE =
            "[Consigne pour Yamy : la personne semble en difficulté. Écoute avec douceur, ne banalise pas, " +
                "ne donne aucun diagnostic, et rappelle simplement qu'elle peut en parler à un proche ou à un service d'écoute.]\n"
    }
}
