package com.ritesh.cashiro.presentation.ui.features.settings.unrecognized

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.core.Constants
import com.ritesh.cashiro.data.database.entity.UnrecognizedSmsEntity
import com.ritesh.cashiro.data.repository.UnrecognizedSmsRepository
import com.ritesh.cashiro.utils.DeviceEncryption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class UnrecognizedSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _showReported = MutableStateFlow(true)
    val showReported: StateFlow<Boolean> = _showReported.asStateFlow()
    
    private val allMessages = unrecognizedSmsRepository.getAllVisible()
    
    val unrecognizedMessages: StateFlow<List<UnrecognizedSmsEntity>> = 
        combine(allMessages, _showReported) { messages, showReported ->
            if (showReported) {
                messages
            } else {
                messages.filter { !it.reported }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun toggleShowReported() {
        _showReported.value = !_showReported.value
    }
    
    fun reportMessage(message: UnrecognizedSmsEntity) {
        viewModelScope.launch {
            try {
                val issueTitle = "[Unrecognized SMS] From: ${message.sender}"
                val issueBody = """
                    ### Unrecognized SMS Details
                    - **Sender:** ${message.sender}
                    - **Date:** ${message.receivedAt}
                    
                    ### Original SMS
                    ```
                    ${message.smsBody}
                    ```
                    
                    ### Expected Behavior
                    _Describe how this SMS should have been parsed (e.g., this is a transaction of 500 INR from HDFC bank)_
                """.trimIndent()

                val encodedTitle = URLEncoder.encode(issueTitle, "UTF-8")
                val encodedBody = URLEncoder.encode(issueBody, "UTF-8")

                val url = "https://github.com/saik111919/norm-pix/issues/new?title=$encodedTitle&body=$encodedBody"
                
                // Open in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                
                // Mark as reported
                unrecognizedSmsRepository.markAsReported(listOf(message.id))
                
                Log.d("UnrecognizedSmsViewModel", "Opened report for message from: ${message.sender}")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Error opening report", e)
            }
        }
    }
    
    fun deleteMessage(message: UnrecognizedSmsEntity) {
        viewModelScope.launch {
            try {
                // Delete the specific message
                unrecognizedSmsRepository.deleteMessage(message.id)
                Log.d("UnrecognizedSmsViewModel", "Deleted message from: ${message.sender}")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Error deleting message", e)
            }
        }
    }
    
    fun deleteAllMessages() {
        viewModelScope.launch {
            try {
                unrecognizedSmsRepository.deleteAll()
                Log.d("UnrecognizedSmsViewModel", "Deleted all unrecognized messages")
            } catch (e: Exception) {
                Log.e("UnrecognizedSmsViewModel", "Error deleting all messages", e)
            }
        }
    }
}
