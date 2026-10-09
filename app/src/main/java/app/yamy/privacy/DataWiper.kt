package app.yamy.privacy

import app.yamy.chat.SessionRepository
import app.yamy.engine.ChatEngine
import app.yamy.memory.MemoryRepository
import app.yamy.persona.AssetPersonaProvider
import app.yamy.skills.SkillRepository
import java.io.File

/** « Tout effacer » : discussions et messages, mémoire, skills et personnalisation locale. Le modèle n'est pas touché. */
class DataWiper(
    private val sessions: SessionRepository,
    private val skills: SkillRepository,
    private val engine: ChatEngine,
    private val filesDir: File,
    private val cacheDir: File,
    private val memory: MemoryRepository,
    /** Réinstalle ce qui est livré avec l'app (chaînes d'agents) après l'effacement. */
    private val restoreDefaults: () -> Unit = {},
    /** Coupe les connecteurs (jeton + mode Hors ligne). Exécuté en premier, quoi qu'il arrive ensuite. */
    private val resetConnectors: () -> Unit = {},
) {
    suspend fun wipeAll() {
        resetConnectors()
        sessions.deleteAll()
        skills.deleteAll()
        memory.deleteAll()
        File(filesDir, AssetPersonaProvider.FILE_NAME).delete()
        File(cacheDir, "camera").deleteRecursively()
        engine.invalidateConversation()
        runCatching { restoreDefaults() }
    }
}
