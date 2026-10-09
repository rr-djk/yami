package app.yamy.data

import androidx.room.withTransaction
import app.yamy.chat.Session
import app.yamy.chat.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository(private val db: AppDatabase) : SessionRepository {
    private val sessions = db.sessionDao()
    private val messages = db.messageDao()

    override fun observe(): Flow<List<Session>> = sessions.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: Long): Session? = sessions.get(id)?.toDomain()

    override suspend fun create(title: String, topic: String): Session {
        val now = System.currentTimeMillis()
        val id = sessions.insert(SessionEntity(title = title, topic = topic, createdAt = now, updatedAt = now))
        return Session(id, title, topic, now)
    }

    override suspend fun mostRecentOrCreate(): Session =
        sessions.mostRecent()?.toDomain() ?: create(Session.DEFAULT_TITLE, "")

    override suspend fun touch(id: Long) = sessions.touch(id, System.currentTimeMillis())

    override suspend fun rename(id: Long, title: String) = sessions.rename(id, title)

    override suspend fun delete(id: Long) = db.withTransaction {
        messages.deleteForSession(id)
        sessions.delete(id)
    }

    override suspend fun deleteAll() = db.withTransaction {
        messages.deleteAll()
        sessions.deleteAll()
    }

    private fun SessionEntity.toDomain() = Session(id, title, topic, updatedAt)
}
