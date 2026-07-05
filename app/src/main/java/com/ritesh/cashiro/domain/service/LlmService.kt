package com.ritesh.cashiro.domain.service

import kotlinx.coroutines.flow.Flow

interface LlmService {
    suspend fun initialize(modelPath: String): Result<Unit>
    suspend fun generateResponse(prompt: String): Result<String>
    fun generateResponseStream(prompt: String): Flow<String>
    suspend fun reset()
    /** Resets the conversation history without unloading the model engine. */
    suspend fun resetConversation()
    fun isInitialized(): Boolean
}