package app.yamy.chat

import kotlinx.coroutines.flow.Flow

/** Une discussion thématique : historique séparé et sujet ajouté au prompt système. */
data class Session(
    val id: Long,
    val title: String,
    /** Consigne de sujet (vide = discussion libre). */
    val topic: String,
    val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_TITLE = "Nouvelle discussion"
    }
}

interface SessionRepository {
    fun observe(): Flow<List<Session>>
    suspend fun get(id: Long): Session?
    suspend fun create(title: String, topic: String): Session

    /** La discussion la plus récente, ou une nouvelle si aucune n'existe. */
    suspend fun mostRecentOrCreate(): Session
    suspend fun touch(id: Long)
    suspend fun rename(id: Long, title: String)

    /** Supprime la discussion et tous ses messages. */
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
