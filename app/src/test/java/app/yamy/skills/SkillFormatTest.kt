package app.yamy.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillFormatTest {

    private val valid = Skill("preparer-entretien", "Aide à préparer un entretien d'embauche", "1. Demande le poste.\n2. Pose trois questions.")

    private fun parsed(raw: String) = SkillFormat.parse(raw)

    @Test
    fun `format puis parse redonne le même skill`() {
        val result = parsed(SkillFormat.format(valid))
        assertEquals(SkillFormat.Result.Valid(valid), result)
    }

    @Test
    fun `tolère une clôture de code autour du fichier`() {
        val raw = "```markdown\n" + SkillFormat.format(valid) + "```"
        assertEquals(SkillFormat.Result.Valid(valid), parsed(raw))
    }

    @Test
    fun `refuse un en-tête absent`() {
        assertTrue(parsed("Juste du texte") is SkillFormat.Result.Invalid)
    }

    @Test
    fun `refuse un en-tête non refermé`() {
        assertTrue(parsed("---\nname: a\ndescription: b\nInstructions") is SkillFormat.Result.Invalid)
    }

    @Test
    fun `refuse un nom invalide`() {
        val raw = "---\nname: Mon Skill\ndescription: d\n---\ncorps"
        val result = parsed(raw) as SkillFormat.Result.Invalid
        assertTrue(result.reason.contains("nom"))
    }

    @Test
    fun `refuse des instructions vides`() {
        val raw = "---\nname: ok\ndescription: d\n---\n"
        assertTrue(parsed(raw) is SkillFormat.Result.Invalid)
    }

    @Test
    fun `les deux points dans la description sont conservés`() {
        val raw = "---\nname: ok\ndescription: Quand : l'utilisateur demande\n---\ncorps"
        val skill = (parsed(raw) as SkillFormat.Result.Valid).skill
        assertEquals("Quand : l'utilisateur demande", skill.description)
    }

    @Test
    fun `refuse des instructions trop longues`() {
        val tooLong = valid.copy(instructions = "x".repeat(SkillFormat.MAX_INSTRUCTIONS + 1))
        assertTrue(SkillFormat.validate(tooLong) is SkillFormat.Result.Invalid)
    }

    @Test
    fun `slugify retire accents et ponctuation`() {
        assertEquals("preparer-mon-entretien", SkillFormat.slugify("Préparer mon entretien !"))
    }

    @Test
    fun `slugify borne la longueur et respecte le motif`() {
        val slug = SkillFormat.slugify("é".repeat(100) + " fin")
        assertTrue(slug.length <= SkillFormat.MAX_NAME)
        assertTrue(slug.isEmpty() || SkillFormat.validate(Skill(slug, "d", "i")) is SkillFormat.Result.Valid)
    }
}
