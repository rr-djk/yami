package app.yamy.agents

import android.content.res.AssetManager
import app.yamy.skills.SkillFormat
import app.yamy.skills.SkillRepository

/** Chaînes livrées avec l'app : copiées dans les skills (chiffrés) et remises à jour quand l'app change. */
object ChainCatalog {
    private const val DIR = "chains"

    fun installDefaults(assets: AssetManager, skills: SkillRepository) {
        assets.list(DIR).orEmpty().forEach { file ->
            val text = assets.open("$DIR/$file").bufferedReader().use { it.readText() }
            val skill = (SkillFormat.parse(text) as? SkillFormat.Result.Valid)?.skill ?: return@forEach
            if (skills.get(skill.name) != skill) skills.save(skill)
        }
    }
}
