package app.yamy.skills

/** Stockage local des skills de l'utilisateur. */
interface SkillRepository : SkillLookup {
    fun list(): List<Skill>
    fun get(name: String): Skill?
    fun save(skill: Skill)
    fun delete(name: String)
    fun deleteAll()

    override fun instructionsFor(name: String): String? = get(name)?.instructions
}
