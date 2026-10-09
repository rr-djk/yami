package app.yamy

import android.app.Application
import app.yamy.chat.ConversationRepository
import app.yamy.chat.SessionRepository
import app.yamy.data.RoomSessionRepository
import app.yamy.data.AppDatabase
import app.yamy.data.RoomConversationRepository
import app.yamy.engine.ChatEngine
import app.yamy.engine.LiteRtChatEngine
import app.yamy.engine.ModelLocator
import app.yamy.persona.AssetPersonaProvider
import app.yamy.persona.PersonaProvider
import app.yamy.agents.ChainCatalog
import app.yamy.connect.NetworkGate
import app.yamy.connect.NetworkMode
import app.yamy.connect.NetworkModeStore
import app.yamy.mcp.discord.DiscordConnector
import app.yamy.mcp.discord.DiscordTokenStore
import app.yamy.mcp.discord.PrefsChannelApprovals
import app.yamy.memory.FileMemoryRepository
import app.yamy.memory.MemoryRepository
import app.yamy.memory.Scribe
import app.yamy.agents.ChainRunner
import app.yamy.privacy.AppLock
import app.yamy.privacy.DataWiper
import app.yamy.privacy.DatabasePassphrase
import app.yamy.privacy.KeystoreSecretBox
import app.yamy.privacy.PrivacyAudit
import app.yamy.skills.FileSkillRepository
import app.yamy.skills.SkillCreator
import app.yamy.skills.SkillRepository
import app.yamy.vision.ContentResolverImageSource
import app.yamy.voice.Speaker
import app.yamy.voice.VoiceRecorder
import app.yamy.vision.ImageSource

/** Racine de composition : câblage manuel des dépendances (injection par constructeur). */
class YamyApp : Application() {
    val modelLocator: ModelLocator by lazy { ModelLocator(this) }
    val engine: ChatEngine by lazy { LiteRtChatEngine(this, modelLocator) }
    val persona: PersonaProvider by lazy { AssetPersonaProvider(this) }
    val skills: SkillRepository by lazy { FileSkillRepository(java.io.File(filesDir, "skills"), secretBox) }
    val skillCreator: SkillCreator by lazy { SkillCreator(engine) }
    val voiceRecorder: VoiceRecorder by lazy { VoiceRecorder() }
    val speaker: Speaker by lazy { Speaker(this) }
    val memory: MemoryRepository by lazy { FileMemoryRepository(java.io.File(filesDir, "memoire"), secretBox) }
    val scribe: Scribe by lazy { Scribe(engine, memory) }
    val networkMode: NetworkModeStore by lazy { NetworkModeStore(this) }
    val networkGate: NetworkGate by lazy { NetworkGate { networkMode.mode.value } }
    val discordToken: DiscordTokenStore by lazy { DiscordTokenStore(java.io.File(filesDir, "discord.key"), secretBox) }
    val discord: DiscordConnector by lazy { DiscordConnector(discordToken, networkGate, PrefsChannelApprovals(this)) }
    val chainRunner: ChainRunner by lazy { ChainRunner(engine) }
    val imageSource: ImageSource by lazy { ContentResolverImageSource(this) }
    val secretBox: KeystoreSecretBox by lazy { KeystoreSecretBox() }
    val appLock: AppLock by lazy { AppLock(this) }
    val privacyAudit: PrivacyAudit by lazy { PrivacyAudit(this, secretBox, appLock, networkMode, discordToken) }
    private val database: AppDatabase by lazy { AppDatabase.create(this, databasePassphrase()) }
    val conversations: ConversationRepository by lazy { RoomConversationRepository(database.messageDao()) }
    val sessions: SessionRepository by lazy { RoomSessionRepository(database) }
    val dataWiper: DataWiper by lazy { DataWiper(
            sessions, skills, engine, filesDir, cacheDir, memory,
            restoreDefaults = { ChainCatalog.installDefaults(assets, skills) },
            resetConnectors = {
                discord.forget()
                networkMode.set(NetworkMode.OFFLINE)
            },
        ) }

    override fun onCreate() {
        super.onCreate()
        // Une photo d'appareil restée en cache après un arrêt brutal ne doit pas survivre.
        java.io.File(cacheDir, "camera").deleteRecursively()
        // Chaînes d'agents livrées avec l'app : installées une fois, hors du thread principal.
        Thread { runCatching { ChainCatalog.installDefaults(assets, skills) } }.start()
    }

    /**
     * Si la clé du Keystore est perdue, les données chiffrées sont définitivement illisibles :
     * on repart d'une base vide plutôt que de planter à chaque lancement.
     */
    private fun databasePassphrase(): ByteArray {
        val store = DatabasePassphrase(java.io.File(filesDir, "db.key"), secretBox)
        return try {
            store.get()
        } catch (e: DatabasePassphrase.UnreadableException) {
            store.delete()
            deleteDatabase("yamy-secure.db")
            secretBox.destroyKey()
            java.io.File(filesDir, "skills").deleteRecursively()
            java.io.File(filesDir, "memoire").deleteRecursively()
            java.io.File(filesDir, "discord.key").delete()
            store.get()
        }
    }
}
