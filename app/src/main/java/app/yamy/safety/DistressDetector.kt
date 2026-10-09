package app.yamy.safety

/** Niveau de détresse perçu dans un message. */
enum class Distress { NONE, ELEVATED, ACUTE }

/** Évalue un message avant tout appel au modèle. Doit rester rapide, local et déterministe. */
interface DistressDetector {
    fun assess(text: String): Distress
}
