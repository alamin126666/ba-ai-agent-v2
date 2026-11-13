package com.ba.aiagent.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val providerId: String,
    val modelId: String,
    val mode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPreview: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    val toolName: String? = null,
    val toolStatus: String? = null,
    val timestamp: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Query("UPDATE sessions SET title = :title, updatedAt = :updatedAt, lastPreview = :preview WHERE id = :id")
    suspend fun updateSession(id: Long, title: String, updatedAt: Long, preview: String)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun renameSession(id: Long, title: String)

    @Query("UPDATE sessions SET mode = :mode WHERE id = :id")
    suspend fun updateMode(id: Long, mode: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesFor(sessionId: Long)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun messages(sessionId: Long): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(m: MessageEntity): Long

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM messages")
    suspend fun clearMessages()
}

@Database(entities = [SessionEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        fun get(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ba_ai_agent.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
