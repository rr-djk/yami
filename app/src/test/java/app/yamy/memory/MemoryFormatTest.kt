package app.yamy.memory

import app.yamy.privacy.SecretBox
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemoryFormatTest {

    @get:Rule val folder = TemporaryFolder()

    private class XorBox : SecretBox {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x33).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private val note = MemoryNote("examens", "Stress avant les examens", "A peur d'oublier les définitions. Voir [[maman]].")

    @Test
    fun `format puis parse redonne la note, liens compris`() {
        val parsed = MemoryFormat.parse(MemoryFormat.format(note))
        assertEquals(note, parsed)
        assertEquals(listOf("maman"), parsed!!.links)
    }

    @Test
    fun `lit la sortie du Scribe, même avec du gras et du bruit`() {
        val raw = """
            Voici les notes :
            **NOTE: Examens de bio | Stress avant les examens**
            Révise le soir. Voir [[maman]].

            NOTE: maman | Sa mère, qui la soutient
            L'appelle chaque dimanche.
        """.trimIndent()
        val notes = MemoryFormat.parseScribe(raw)
        assertEquals(listOf("examens-de-bio", "maman"), notes.map { it.name })
        assertEquals("Stress avant les examens", notes[0].summary)
        assertEquals("Révise le soir. Voir [[maman]].", notes[0].content)
    }

    @Test
    fun `tolère les variantes de NOTE`() {
        val raw = "Note : examens | Stress\nRévise le soir.\n\n- **NOTE :** lea | Sa sœur\nL'aide à réviser."
        assertEquals(listOf("examens", "lea"), MemoryFormat.parseScribe(raw).map { it.name })
    }

    @Test
    fun `une note tout sur une ligne garde son contenu`() {
        val notes = MemoryFormat.parseScribe("NOTE: examens | Stress jeudi. [[lea]] l'aide.")
        assertEquals("Stress jeudi. [[lea]] l'aide.", notes.single().content)
    }

    @Test
    fun `RIEN ne produit aucune note`() {
        assertTrue(MemoryFormat.parseScribe("RIEN").isEmpty())
    }

    @Test
    fun `la fusion ajoute sans dupliquer`() {
        val merged = MemoryFormat.merge(note, note.copy(content = "A peur d'oublier les définitions. Voir [[maman]].\nExamen jeudi."))
        assertEquals("A peur d'oublier les définitions. Voir [[maman]].\nExamen jeudi.", merged.content)
    }

    @Test
    fun `l'index tient en une ligne par note`() {
        assertEquals("- [[examens]] : Stress avant les examens", MemoryFormat.index(listOf(note)))
    }

    @Test
    fun `un lien d'une note vers elle-même est retiré`() {
        val cleaned = MemoryFormat.valid(MemoryNote("lea", "Sa sœur", "Léa l'aide [[lea]]. Voir [[examens]]."))!!
        assertEquals("Léa l'aide. Voir [[examens]].", cleaned.content)
    }

    @Test
    fun `une note sans contenu est refusée`() {
        assertNull(MemoryFormat.valid(MemoryNote("x", "y", "  ")))
    }

    @Test
    fun `les notes sont chiffrées sur disque et ne sortent pas du dossier`() {
        val repo = FileMemoryRepository(File(folder.root, "memoire"), XorBox())
        repo.save(note)
        assertEquals(note, repo.get("examens"))
        val raw = File(folder.root, "memoire").listFiles()!!.single().readBytes()
        assertFalse(String(raw, Charsets.UTF_8).contains("définitions"))
        assertNull(repo.get("../../etc/passwd"))
        repo.deleteAll()
        assertTrue(repo.list().isEmpty())
    }
}
