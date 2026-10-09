package app.yamy.data

import app.yamy.chat.ChatMessage
import app.yamy.chat.ConversationRepository
import app.yamy.chat.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationRepository(private val dao: MessageDao) : ConversationRepository {
    override fun observe(sessionId: Long): Flow<List<ChatMessage>> =
        dao.observe(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun recent(sessionId: Long, limit: Int): List<ChatMessage> =
        dao.recent(sessionId, limit).map { it.toDomain() }

    override suspend fun add(message: ChatMessage) = dao.insert(message.toEntity())

    override suspend fun clear(sessionId: Long) = dao.deleteForSession(sessionId)

    override suspend fun clearAll() = dao.deleteAll()

    private fun MessageEntity.toDomain() = ChatMessage(id, sessionId, Role.valueOf(role), text, hasImage, viaVoice, createdAt)

    private fun ChatMessage.toEntity() = MessageEntity(id, sessionId, role.name, text, hasImage, viaVoice, createdAt)
}
