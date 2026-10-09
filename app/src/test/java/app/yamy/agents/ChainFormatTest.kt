package app.yamy.agents

import app.yamy.skills.Skill
import app.yamy.skills.SkillFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainFormatTest {
    private val raw = """
        ---
        name: le-conseil
        description: Trois agents débattent
        type: chaine
        ---

        ## 🟦 Avocat du pour
        Défends l'option.

        ## 🟥 Avocat du contre
        Donne les risques.

        ## Le Sage
        Fais la synthèse.
    """.trimIndent()

    private fun skill() = (SkillFormat.parse(raw) as SkillFormat.Result.Valid).skill

    @Test
    fun `le type chaine est lu puis réécrit`() {
        val skill = skill()
        assertTrue(skill.isChain)
        assertEquals(skill, (SkillFormat.parse(SkillFormat.format(skill)) as SkillFormat.Result.Valid).skill)
    }

    @Test
    fun `chaque section devient un agent, avec ou sans emoji`() {
        val chain = ChainFormat.parse(skill())!!
        assertEquals(listOf("Avocat du pour", "Avocat du contre", "Le Sage"), chain.agents.map { it.title })
        assertEquals("🟦", chain.agents[0].emoji)
        assertEquals("🔹", chain.agents[2].emoji)
        assertEquals("Donne les risques.", chain.agents[1].instruction)
    }

    @Test
    fun `un skill simple n'est pas une chaîne`() {
        assertNull(ChainFormat.parse(Skill("a", "b", "## X\nfaire\n## Y\nfaire")))
    }

    @Test
    fun `une chaîne d'un seul agent est refusée`() {
        assertNull(ChainFormat.parse(Skill("a", "b", "## Seul\nfaire", Skill.TYPE_CHAINE)))
    }

    @Test
    fun `le nombre d'agents est plafonné`() {
        val body = (1..6).joinToString("\n") { "## Agent $it\nconsigne $it" }
        assertEquals(ChainFormat.MAX_AGENTS, ChainFormat.parse(Skill("a", "b", body, Skill.TYPE_CHAINE))!!.agents.size)
    }

    @Test
    fun `les chaînes livrées avec l'app sont valides`() {
        val dir = java.io.File("src/main/assets/chains")
        val files = dir.listFiles().orEmpty()
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val parsed = SkillFormat.parse(file.readText())
            assertTrue("${file.name} : $parsed", parsed is SkillFormat.Result.Valid)
            val chain = ChainFormat.parse((parsed as SkillFormat.Result.Valid).skill)
            assertTrue("${file.name} n'est pas une chaîne", chain != null && chain.agents.size == 3)
        }
    }

    @Test
    fun `le résumé reprend chaque agent`() {
        val steps = listOf(
            AgentStep(AgentSpec("🟦", "Pour", "x"), StepStatus.DONE, "oui"),
            AgentStep(AgentSpec("🟥", "Contre", "y"), StepStatus.DONE, "non"),
        )
        assertEquals("🟦 Pour\noui\n\n🟥 Contre\nnon", ChainRunner.summary(steps))
    }
}
