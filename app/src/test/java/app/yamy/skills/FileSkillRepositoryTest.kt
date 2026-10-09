package app.yamy.skills

import app.yamy.privacy.SecretBox
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSkillRepositoryTest {

    /** Faux chiffrement réversible, assez différent du clair pour prouver qu'on ne stocke pas le texte brut. */
    private class XorBox(private val key: Int = 0x5A) : SecretBox {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor key).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    @get:Rule val folder = TemporaryFolder()

    private val skill = Skill("preparer-entretien", "Aide à préparer un entretien", "1. Demande le poste.")

    private fun repo() = FileSkillRepository(File(folder.root, "skills"), XorBox())

    @Test
    fun `enregistre puis relit un skill`() {
        val repository = repo()
        repository.save(skill)
        assertEquals(skill, repository.get(skill.name))
        assertEquals(listOf(skill), repository.list())
    }

    @Test
    fun `le fichier sur disque ne contient pas le texte en clair`() {
        repo().save(skill)
        val raw = File(File(folder.root, "skills/${skill.name}"), FileSkillRepository.FILE_NAME).readBytes()
        assertFalse(String(raw, Charsets.UTF_8).contains("Demande le poste"))
    }

    @Test
    fun `un fichier chiffré par une autre clé est ignoré`() {
        repo().save(skill)
        val other = FileSkillRepository(File(folder.root, "skills"), XorBox(key = 0x11))
        assertNull(other.get(skill.name))
        assertTrue(other.list().isEmpty())
    }

    @Test
    fun `refuse les noms qui sortent du dossier`() {
        val repository = repo()
        repository.save(skill)
        assertNull(repository.get("../secret"))
        assertNull(repository.get(".."))
        repository.delete("../skills")
        repository.delete("..")
        assertEquals(skill, repository.get(skill.name))
    }

    @Test
    fun `supprimer un skill ou tout supprimer`() {
        val repository = repo()
        repository.save(skill)
        repository.save(skill.copy(name = "autre"))
        repository.delete(skill.name)
        assertEquals(listOf("autre"), repository.list().map { it.name })
        repository.deleteAll()
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun `refuse d'enregistrer un skill invalide`() {
        val failure = runCatching { repo().save(skill.copy(name = "Nom Invalide")) }
        assertTrue(failure.isFailure)
    }
}
