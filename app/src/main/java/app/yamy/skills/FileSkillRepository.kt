package app.yamy.skills

import java.io.File

/** Un dossier par skill (`skills/<nom>/SKILL.md`) dans le stockage interne de l'app. */
class FileSkillRepository(private val root: File) : SkillRepository {

    override fun list(): List<Skill> =
        root.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { get(it.name) }
            .sortedBy { it.name }

    override fun get(name: String): Skill? {
        if (!isSafe(name)) return null
        val file = File(File(root, name), FILE_NAME)
        if (!file.isFile) return null
        return (SkillFormat.parse(file.readText()) as? SkillFormat.Result.Valid)?.skill
    }

    override fun save(skill: Skill) {
        require(SkillFormat.validate(skill) is SkillFormat.Result.Valid) { "Skill invalide : ${skill.name}" }
        val dir = File(root, skill.name).apply { mkdirs() }
        File(dir, FILE_NAME).writeText(SkillFormat.format(skill))
    }

    override fun delete(name: String) {
        if (isSafe(name)) File(root, name).deleteRecursively()
    }

    override fun deleteAll() {
        root.deleteRecursively()
    }

    /** Refuse tout nom qui pourrait sortir du dossier des skills. */
    private fun isSafe(name: String) = name.isNotEmpty() && '/' !in name && '\\' !in name && name != ".." && name != "."

    companion object {
        const val FILE_NAME = "SKILL.md"
    }
}
