package app.yamy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observe(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id DESC LIMIT :limit) ORDER BY id ASC")
    suspend fun recent(sessionId: Long, limit: Int): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
