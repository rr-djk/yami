package app.yamy

import android.app.Application
import app.yamy.chat.ConversationRepository
import app.yamy.data.AppDatabase
import app.yamy.data.RoomConversationRepository
import app.yamy.engine.ChatEngine
import app.yamy.engine.LiteRtChatEngine
import app.yamy.engine.ModelLocator
import app.yamy.persona.AssetPersonaProvider
import app.yamy.persona.PersonaProvider
import app.yamy.privacy.AppLock
import app.yamy.privacy.DataWiper
import app.yamy.privacy.DatabasePassphrase
import app.yamy.privacy.KeystoreSecretBox
import app.yamy.privacy.PrivacyAudit
import app.yamy.skills.FileSkillRepository
import app.yamy.skills.SkillCreator
import app.yamy.skills.SkillRepository
import app.yamy.vision.ContentResolverImageSource
import app.yamy.vision.ImageSource

/** Racine de composition : câblage manuel des dépendances (injection par constructeur). */
class YamyApp : Application() {
    val modelLocator: ModelLocator by lazy { ModelLocator(this) }
    val engine: ChatEngine by lazy { LiteRtChatEngine(this, modelLocator) }
    val persona: PersonaProvider by lazy { AssetPersonaProvider(this) }
    val skills: SkillRepository by lazy { FileSkillRepository(java.io.File(filesDir, "skills"), secretBox) }
    val skillCreator: SkillCreator by lazy { SkillCreator(engine) }
    val imageSource: ImageSource by lazy { ContentResolverImageSource(this) }
    val secretBox: KeystoreSecretBox by lazy { KeystoreSecretBox() }
    val appLock: AppLock by lazy { AppLock(this) }
    val privacyAudit: PrivacyAudit by lazy { PrivacyAudit(this, secretBox, appLock) }
    val conversations: ConversationRepository by lazy {
        val passphrase = DatabasePassphrase(java.io.File(filesDir, "db.key"), secretBox).get()
        RoomConversationRepository(AppDatabase.create(this, passphrase).messageDao())
    }
    val dataWiper: DataWiper by lazy { DataWiper(conversations, skills, engine, filesDir) }
}
