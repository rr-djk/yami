package app.yamy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM messages ORDER BY createdAt DESC, id DESC LIMIT :limit) ORDER BY createdAt ASC, id ASC")
    suspend fun recent(limit: Int): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
