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
import app.yamy.vision.ContentResolverImageSource
import app.yamy.vision.ImageSource

/** Racine de composition : câblage manuel des dépendances (injection par constructeur). */
class YamyApp : Application() {
    val modelLocator: ModelLocator by lazy { ModelLocator(this) }
    val engine: ChatEngine by lazy { LiteRtChatEngine(this, modelLocator) }
    val persona: PersonaProvider by lazy { AssetPersonaProvider(this) }
    val imageSource: ImageSource by lazy { ContentResolverImageSource(this) }
    val conversations: ConversationRepository by lazy {
        RoomConversationRepository(AppDatabase.create(this).messageDao())
    }
}
