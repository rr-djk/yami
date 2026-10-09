package app.yamy.agents

import android.content.res.AssetManager
import app.yamy.skills.SkillFormat
import app.yamy.skills.SkillRepository

/** Chaînes livrées avec l'app : copiées dans les skills (chiffrés) et remises à jour quand l'app change. */
object ChainCatalog {
    private const val DIR = "chains"

    /** Chaînes autrefois livrées puis retirées : supprimées des téléphones à la mise à jour. */
    private val RETIRED = setOf("revision")

    fun installDefaults(assets: AssetManager, skills: SkillRepository) {
        RETIRED.forEach { name -> if (skills.get(name)?.isChain == true) skills.delete(name) }
        assets.list(DIR).orEmpty().forEach { file ->
            val text = assets.open("$DIR/$file").bufferedReader().use { it.readText() }
            val skill = (SkillFormat.parse(text) as? SkillFormat.Result.Valid)?.skill ?: return@forEach
            if (skills.get(skill.name) != skill) skills.save(skill)
        }
    }
}
