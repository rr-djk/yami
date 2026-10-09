package app.yamy.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillPromptTest {
    @Test
    fun `sans skill le prompt reste inchangé`() {
        assertEquals("base", SkillPrompt.withSkills("base", emptyList()))
    }

    @Test
    fun `liste uniquement nom et description, jamais les instructions`() {
        val prompt = SkillPrompt.withSkills("base", listOf(Skill("a-b", "fait X", "SECRET-INSTRUCTIONS")))
        assertTrue(prompt.contains("- a-b : fait X"))
        assertTrue("SECRET-INSTRUCTIONS" !in prompt)
        assertTrue(prompt.contains("loadSkill"))
    }
}
