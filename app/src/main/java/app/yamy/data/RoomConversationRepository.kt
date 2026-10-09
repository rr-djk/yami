package app.yamy.data

import app.yamy.chat.ChatMessage
import app.yamy.chat.ConversationRepository
import app.yamy.chat.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationRepository(private val dao: MessageDao) : ConversationRepository {
    override fun observe(): Flow<List<ChatMessage>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun recent(limit: Int): List<ChatMessage> = dao.recent(limit).map { it.toDomain() }

    override suspend fun add(message: ChatMessage) = dao.insert(message.toEntity())

    override suspend fun clear() = dao.deleteAll()

    private fun MessageEntity.toDomain() = ChatMessage(id, Role.valueOf(role), text, hasImage, createdAt)

    private fun ChatMessage.toEntity() = MessageEntity(id, role.name, text, hasImage, createdAt)
}
