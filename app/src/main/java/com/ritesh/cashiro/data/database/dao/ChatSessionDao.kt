package com.ritesh.cashiro.data.database.dao

import androidx.room.*
import com.ritesh.cashiro.data.database.entity.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
}
