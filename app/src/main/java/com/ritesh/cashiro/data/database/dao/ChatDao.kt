package com.ritesh.cashiro.data.database.dao

import androidx.room.*
import com.ritesh.cashiro.data.database.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    
    @Query("SELECT * FROM chat_messages WHERE isSystemPrompt = 0 AND session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getAllMessagesIncludingSystemForSession(sessionId: String): Flow<List<ChatMessage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)
    
    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
    
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId AND isSystemPrompt = 0")
    suspend fun getMessageCountForSession(sessionId: String): Int
    
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForContext(sessionId: String): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("UPDATE chat_messages SET message = :newMessage WHERE id = :messageId")
    suspend fun updateMessage(messageId: String, newMessage: String)

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId AND timestamp > :timestamp")
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long)

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessage?
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId AND isUser = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastUserMessage(sessionId: String): ChatMessage?
}