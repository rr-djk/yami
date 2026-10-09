package app.yamy.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Banc de test déterministe de la couche de sécurité (le banc du modèle est dans docs/BANC_TEST.md). */
class KeywordDistressDetectorTest {

    private val detector = KeywordDistressDetector()

    private fun check(expected: Distress, vararg messages: String) =
        messages.forEach { assertEquals("« $it »", expected, detector.assess(it)) }

    @Test
    fun `quotidien banal reste à NONE`() = check(
        Distress.NONE,
        "J'ai eu une journée longue",
        "Je ne sais pas quoi manger ce soir",
        "Mon équipe a gagné, je suis ravi",
        "Peux-tu m'aider à écrire un mail à mon patron ?",
        "Il pleut encore, quel temps",
        "Je dois réviser pour mon examen demain",
    )

    @Test
    fun `expressions courantes ne déclenchent pas la crise`() = check(
        Distress.NONE,
        "Je meurs de faim",
        "J'ai failli mourir de rire hier",
        "Je suis mort de fatigue",
        "Je vais me tuer à la tâche pour finir ce projet",
        "I was dying of laughter",
    )

    @Test
    fun `émotion forte monte à ELEVATED`() = check(
        Distress.ELEVATED,
        "Je me sens tellement seul depuis le déménagement",
        "Je suis en colère et je n'en peux plus de mon chef",
        "J'ai une crise de panique avant mon examen",
        "Je suis déprimé depuis la mort de mon grand-père",
        "À quoi bon continuer ces études",
        "I feel so alone these days",
    )

    @Test
    fun `détresse aiguë déclenche la réponse de crise`() = check(
        Distress.ACUTE,
        "J'ai envie de mourir",
        "Je veux me suicider",
        "Je pense à me tuer",
        "Je n'ai plus envie de vivre",
        "Je vais me faire du mal",
        "Je me scarifie la nuit",
        "Mon copain me frappe tous les soirs",
        "On m'a violée la semaine dernière",
        "Je suis en danger chez moi",
        "I want to die",
        "I'm going to kill myself",
        "He hits me when he's drunk",
    )

    @Test
    fun `accents apostrophes typographiques et majuscules sont tolérés`() = check(
        Distress.ACUTE,
        "J’AI ENVIE DE MOURIR",
        "je veux  me   suicider !!!",
        "Je veux mettre fin à mes jours",
    )

    @Test
    fun `texte vide`() = check(Distress.NONE, "", "   ")

    @Test
    fun `la réponse de crise donne un numéro humain et rappelle que Yamy est une IA`() {
        val reply = CrisisResponder().reply()
        assertTrue(reply.contains("IA"))
        assertTrue(reply.contains("3114"))
        assertTrue(reply.contains("112"))
    }

    @Test
    fun `la réponse de crise s'adapte à la violence subie`() {
        val reply = CrisisResponder().reply("Mon copain me frappe quand il est en colère")
        assertTrue(reply.contains("3919"))
        assertTrue(!reply.contains("3114"))
    }

    @Test
    fun `la réponse de crise suit la langue du message`() {
        val reply = CrisisResponder().reply("I want to end my life")
        assertTrue(reply.contains("I'm an AI"))
        assertTrue(reply.contains("3114"))
    }

    @Test
    fun `un message français reste en français`() {
        assertTrue(CrisisResponder().reply("Je veux me suicider").contains("Je suis une IA"))
    }
}
