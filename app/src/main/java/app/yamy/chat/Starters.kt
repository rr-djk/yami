package app.yamy.chat

/** Amorce de discussion : un titre et une consigne de sujet pour Yamy. */
data class Starter(val emoji: String, val title: String, val topic: String)

object Starters {
    val ALL = listOf(
        Starter("💬", "Parler librement", ""),
        Starter("📚", "Réviser", "Aide la personne à réviser : explique simplement, puis pose une question de vérification à la fois."),
        Starter("💼", "Préparer un entretien", "Aide la personne à préparer un entretien : questions probables, conseils concrets, simulation question par question."),
        Starter("🌙", "Décompresser", "Aide la personne à décompresser après une journée difficile : écoute, douceur, et si elle le souhaite un petit exercice de respiration."),
        Starter("⚖️", "Prendre une décision", "Aide la personne à peser une décision : clarifie les options, le pour et le contre, sans décider à sa place."),
    )
}
