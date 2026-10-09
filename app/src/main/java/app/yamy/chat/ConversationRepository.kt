package app.yamy.chat

import kotlinx.coroutines.flow.Flow

/** Persistance locale de la conversation. */
interface ConversationRepository {
    fun observe(): Flow<List<ChatMessage>>
    suspend fun recent(limit: Int): List<ChatMessage>
    suspend fun add(message: ChatMessage)
    suspend fun clear()
}
