package app.yamy.memory

import app.yamy.privacy.SecretBox
import app.yamy.skills.SkillLookup
import java.io.File

/** Mémoire de Yamy : notes reliées, lisibles, modifiables et supprimables par la personne. */
interface MemoryRepository : SkillLookup {
    fun list(): List<MemoryNote>
    fun get(name: String): MemoryNote?
    fun save(note: MemoryNote)
    fun delete(name: String)
    fun deleteAll()

    /** Contenu d'une note, pour l'outil `lireNote` du modèle. */
    override fun instructionsFor(name: String): String? = get(name)?.content
}

/** Un fichier chiffré par note dans `memoire/`, dans le stockage interne de l'app. */
class FileMemoryRepository(private val root: File, private val box: SecretBox) : MemoryRepository {

    override fun list(): List<MemoryNote> =
        root.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty()
            .mapNotNull { read(it) }
            .sortedBy { it.name }

    override fun get(name: String): MemoryNote? = file(name)?.takeIf { it.isFile }?.let { read(it) }

    override fun save(note: MemoryNote) {
        val valid = requireNotNull(MemoryFormat.valid(note)) { "Note invalide" }
        root.mkdirs()
        File(root, valid.name + EXT).writeBytes(box.encrypt(MemoryFormat.format(valid).toByteArray(Charsets.UTF_8)))
    }

    override fun delete(name: String) {
        file(name)?.delete()
    }

    override fun deleteAll() {
        root.deleteRecursively()
    }

    private fun read(f: File): MemoryNote? =
        runCatching { MemoryFormat.parse(String(box.decrypt(f.readBytes()), Charsets.UTF_8)) }.getOrNull()

    /** Le nom passe par le même filtre que les noms de skills : impossible de sortir du dossier. */
    private fun file(name: String): File? {
        val slug = app.yamy.skills.SkillFormat.slugify(name)
        return if (slug.isEmpty()) null else File(root, slug + EXT)
    }

    private companion object {
        const val EXT = ".md.enc"
    }
}
