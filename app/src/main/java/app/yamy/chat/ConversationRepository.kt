package app.yamy.chat

import kotlinx.coroutines.flow.Flow

/** Persistance locale des messages, discussion par discussion. */
interface ConversationRepository {
    fun observe(sessionId: Long): Flow<List<ChatMessage>>
    suspend fun recent(sessionId: Long, limit: Int): List<ChatMessage>
    suspend fun add(message: ChatMessage)
    suspend fun clear(sessionId: Long)
    suspend fun clearAll()
}
