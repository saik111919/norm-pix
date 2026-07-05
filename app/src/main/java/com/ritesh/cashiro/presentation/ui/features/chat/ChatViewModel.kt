package com.ritesh.cashiro.presentation.ui.features.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.database.entity.ChatMessage
import com.ritesh.cashiro.data.database.entity.ChatSession
import com.ritesh.cashiro.data.repository.LlmRepository
import com.ritesh.cashiro.data.repository.ModelRepository
import com.ritesh.cashiro.data.repository.ModelState
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.utils.TokenUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmRepository: LlmRepository,
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()
    
    private val _contextMessage = MutableStateFlow<ChatMessage?>(null)
    
    private var currentGenerationJob: kotlinx.coroutines.Job? = null
    
    private val _currentSessionId = MutableStateFlow("legacy_session")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()
    
    val chatSessions: StateFlow<List<ChatSession>> = llmRepository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId.flatMapLatest { sessionId ->
        combine(
            llmRepository.getMessagesForSession(sessionId),
            _contextMessage
        ) { dbMessages, contextMsg ->
            if (dbMessages.isEmpty() && contextMsg != null) {
                // Show context message only when chat is empty
                listOf(contextMsg)
            } else {
                dbMessages
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val modelState: StateFlow<ModelState> = modelRepository.modelState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (modelRepository.isModelDownloaded()) ModelState.READY else ModelState.NOT_DOWNLOADED
        )
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = combine(
        _uiState,
        modelRepository.downloadProgress
    ) { state, progress ->
        state.copy(downloadProgress = progress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )
    
    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()
    
    val isTokenInfoEnabled = userPreferencesRepository.isTokenInfoEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    // Get all messages including system for accurate token count
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allMessagesIncludingSystem = _currentSessionId.flatMapLatest { sessionId -> 
        llmRepository.getAllMessagesIncludingSystemForSession(sessionId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Chat statistics for developer mode
    val chatStats = combine(
        allMessagesIncludingSystem,
        currentResponse,
        userPreferencesRepository.getSystemPrompt()
    ) { allMsgs, current, systemPromptText ->
        // Calculate system prompt tokens separately
        val actualSystemPromptText = systemPromptText ?: ""
        val systemPromptTokens = if (actualSystemPromptText.isNotEmpty()) {
            TokenUtils.estimateTokens(actualSystemPromptText)
        } else {
            0
        }
        
        // Calculate total tokens
        val allText = actualSystemPromptText + " " + allMsgs.filter { !it.isSystemPrompt }.joinToString(" ") { it.message } + " " + current
        val totalChars = allText.length
        val estimatedTokens = TokenUtils.estimateTokens(allText)
        val maxTokens = 4096 // Qwen 2.5 with KV cache size 4096
        
        // Count only visible messages for UI
        val visibleCount = allMsgs.count { !it.isSystemPrompt }
        
        ChatStats(
            messageCount = visibleCount,
            totalCharacters = totalChars,
            estimatedTokens = estimatedTokens,
            systemPromptTokens = systemPromptTokens,
            maxTokens = maxTokens,
            contextUsagePercent = TokenUtils.calculateContextUsagePercent(estimatedTokens, maxTokens)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatStats()
    )
    
    init {
        Log.d("ChatViewModel", "Initializing ChatViewModel")
        modelRepository.checkModelState()
        
        // Log initial state
        val isDownloaded = modelRepository.isModelDownloaded()
        Log.d("ChatViewModel", "Initial model downloaded check: $isDownloaded")
        
        // Observe model state changes
        viewModelScope.launch {
            modelRepository.modelState.collect { state ->
                Log.d("ChatViewModel", "Model state changed to: $state")
            }
        }
        
        // Restore last active session
        viewModelScope.launch {
            val lastSessionId = userPreferencesRepository.getLastChatSessionId()
            if (lastSessionId != null) {
                _currentSessionId.value = lastSessionId
            }
            loadContextMessage()
        }
        
        // Persist session ID whenever it changes
        viewModelScope.launch {
            _currentSessionId.collect { sessionId ->
                userPreferencesRepository.saveLastChatSessionId(sessionId)
            }
        }
    }
    
    private suspend fun loadContextMessage() {
        val contextMessage = llmRepository.getFormattedContextForDisplay()
        _contextMessage.value = ChatMessage(
            message = contextMessage,
            isUser = false,
            isSystemPrompt = false
        )
    }
    
    fun sendMessage(message: String) {
        if (message.isBlank() || _uiState.value.isLoading) return
        
        currentGenerationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
            _currentResponse.value = ""
            
            try {
                // Use streaming for better UX
                llmRepository.sendMessageStream(message, _currentSessionId.value)
                    .catch { error ->
                        Log.e("ChatViewModel", "Error in stream", error)
                        val errorMessage = when {
                            error.message?.contains("memory is full") == true -> 
                                "Chat memory is full. Please clear the chat to continue."
                            error.message?.contains("downloading") == true ->
                                "Model is downloading. Please wait."
                            error.message?.contains("not downloaded") == true ->
                                "AI model not downloaded. Go to Settings to download."
                            else -> error.message ?: "Failed to generate response"
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorMessage
                        )
                    }
                    .collect { partialResponse ->
                        _currentResponse.value += partialResponse
                    }
                
                // Stream completed successfully
                Log.d("ChatViewModel", "Stream completed, resetting state")
                _uiState.value = _uiState.value.copy(isLoading = false)
                _currentResponse.value = ""
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ChatViewModel", "Exception in sendMessage", e)
                val errorMessage = when {
                    e.message?.contains("memory is full") == true -> 
                        "Chat memory is full. Please clear the chat to continue."
                    e.message?.contains("downloading") == true ->
                        "Model is downloading. Please wait."
                    e.message?.contains("not downloaded") == true ->
                        "AI model not downloaded. Go to Settings to download."
                    else -> e.message ?: "Failed to send message"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMessage
                )
                _currentResponse.value = ""
            }
        }
    }
    
    fun clearChat() {
        viewModelScope.launch {
            llmRepository.deleteAllMessagesForSession(_currentSessionId.value)
            _uiState.value = _uiState.value.copy(
                error = null
            )
            // Reload context message after clearing chat
            loadContextMessage()
        }
    }
    
    fun startNewChat() {
        _currentSessionId.value = UUID.randomUUID().toString()
        _currentResponse.value = ""
        _uiState.value = _uiState.value.copy(error = null)
        viewModelScope.launch {
            loadContextMessage()
        }
    }
    
    fun loadSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _currentResponse.value = ""
        _uiState.value = _uiState.value.copy(error = null)
        _contextMessage.value = null
    }
    
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            llmRepository.deleteMessage(messageId)
        }
    }
    
    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            // First edit DB and delete subsequent
            llmRepository.editMessage(messageId, newText)
            
            // Re-submit to trigger AI stream
            // But wait, the system prompt doesn't need re-sending, just send the latest user message
            // `sendMessageStream` might think it's a new chat if existingMessages is empty, but it's not.
            sendMessage(newText)
        }
    }
    
    fun regenerateMessage(messageId: String) {
        viewModelScope.launch {
            val msg = llmRepository.getMessageById(messageId) ?: return@launch
            if (msg.isUser) return@launch // Only regenerate AI responses
            
            // Delete AI message and all subsequent messages
            llmRepository.deleteMessage(messageId)
            llmRepository.deleteMessagesAfter(msg.sessionId, msg.timestamp)
            
            val lastUserMsg = llmRepository.getLastUserMessage(msg.sessionId)
            if (lastUserMsg != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )
                _currentResponse.value = ""
                
                try {
                    // Start stream without inserting duplicate user message
                    currentGenerationJob = launch {
                        llmRepository.sendMessageStream(lastUserMsg.message, msg.sessionId, insertUserMessage = false)
                            .catch { error ->
                                Log.e("ChatViewModel", "Error in stream", error)
                                val errorMessage = when {
                                    error.message?.contains("memory is full") == true -> 
                                        "Chat memory is full. Please clear the chat to continue."
                                    error.message?.contains("downloading") == true ->
                                        "Model is downloading. Please wait."
                                    error.message?.contains("not downloaded") == true ->
                                        "AI model not downloaded. Go to Settings to download."
                                    else -> error.message ?: "Failed to generate response"
                                }
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = errorMessage
                                )
                            }
                            .collect { partialResponse ->
                                _currentResponse.value += partialResponse
                            }
                        
                        Log.d("ChatViewModel", "Regenerate stream completed")
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _currentResponse.value = ""
                    }
                } catch (e: Throwable) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e("ChatViewModel", "Exception in regenerateMessage", e)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to regenerate response"
                        )
                        _currentResponse.value = ""
                    } else throw e
                }
            }
        }
    }
    
    fun stopGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        _uiState.value = _uiState.value.copy(isLoading = false)
        _currentResponse.value = ""
    }
    
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            llmRepository.deleteAllMessagesForSession(sessionId)
            if (sessionId == _currentSessionId.value) {
                startNewChat()
            }
            _toastEvent.emit("Chat deleted")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadProgress: Int = 0
)

data class ChatStats(
    val messageCount: Int = 0,
    val totalCharacters: Int = 0,
    val estimatedTokens: Int = 0,
    val systemPromptTokens: Int = 0,
    val maxTokens: Int = 1280,
    val contextUsagePercent: Int = 0
)