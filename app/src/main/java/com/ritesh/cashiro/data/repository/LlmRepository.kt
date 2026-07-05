package com.ritesh.cashiro.data.repository

import android.util.Log
import com.ritesh.cashiro.data.database.dao.ChatDao
import com.ritesh.cashiro.data.database.dao.ChatSessionDao
import com.ritesh.cashiro.data.database.entity.ChatMessage
import com.ritesh.cashiro.data.database.entity.ChatSession
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.data.model.ChatContext
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.domain.service.LlmService
import com.ritesh.cashiro.utils.CurrencyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository @Inject constructor(
    private val llmService: LlmService,
    private val chatDao: ChatDao,
    private val chatSessionDao: ChatSessionDao,
    private val modelRepository: ModelRepository,
    private val aiContextRepository: AiContextRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyRepository: CurrencyRepository
) {
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> = chatDao.getMessagesForSession(sessionId)
    
    fun getAllMessagesIncludingSystemForSession(sessionId: String): Flow<List<ChatMessage>> = chatDao.getAllMessagesIncludingSystemForSession(sessionId)
    
    fun getAllSessions(): Flow<List<ChatSession>> = chatSessionDao.getAllSessions()
    
    suspend fun sendMessage(userMessage: String, sessionId: String = "legacy_session"): Result<String> {
        // Save user message
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true,
            sessionId = sessionId
        )
        chatDao.insertMessage(userChatMessage)
        
        // Initialize LLM if needed
        if (!llmService.isInitialized()) {
            val modelFile = modelRepository.getModelFile()
            if (!modelFile.exists()) {
                return Result.failure(Exception("Model not downloaded"))
            }
            
            val initResult = llmService.initialize(modelFile.absolutePath)
            if (initResult.isFailure) {
                return Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize LLM"))
            }
        }
        
        // Generate response
        val responseResult = llmService.generateResponse(userMessage)
        
        return if (responseResult.isSuccess) {
            val response = responseResult.getOrNull() ?: ""
            
            // Save AI response
            val aiChatMessage = ChatMessage(
                message = response,
                isUser = false,
                sessionId = sessionId
            )
            chatDao.insertMessage(aiChatMessage)
            
            Result.success(response)
        } else {
            Result.failure(responseResult.exceptionOrNull() ?: Exception("Failed to generate response"))
        }
    }
    
    fun sendMessageStream(userMessage: String, sessionId: String, insertUserMessage: Boolean = true): Flow<String> = flow {
        // Check if this is the first message (no existing messages in session)
        val existingMessages = chatDao.getAllMessagesForContext(sessionId)
        val isNewChat = existingMessages.isEmpty()

        // Check if model is downloading
        val currentModelState = modelRepository.modelState.first()
        if (currentModelState == ModelState.DOWNLOADING) {
            throw Exception("Model is currently downloading. Please wait for download to complete.")
        }

        // Initialize LLM if needed
        if (!llmService.isInitialized()) {
            val modelFile = modelRepository.getModelFile()
            if (!modelFile.exists()) {
                throw Exception("Model not downloaded. Please download from Settings.")
            }
            val initResult = llmService.initialize(modelFile.absolutePath)
            if (initResult.isFailure) {
                throw initResult.exceptionOrNull() ?: Exception("Failed to initialize LLM")
            }
        }

        // Create the DB session record for new chats
        if (isNewChat) {
            val words = userMessage.split("\\s+".toRegex())
            val title = words.take(4).joinToString(" ") + if (words.size > 4) "..." else ""
            chatSessionDao.insertSession(ChatSession(id = sessionId, title = title))
            Log.d("LlmRepository", "New session created: $sessionId")
        }

        // Save user message to DB
        if (insertUserMessage) {
            val userChatMessage = ChatMessage(
                message = userMessage,
                isUser = true,
                sessionId = sessionId
            )
            chatDao.insertMessage(userChatMessage)
            Log.d("LlmRepository", "User message saved: ${userMessage.take(50)}")
        }

        // Build the full prompt context from scratch on every turn:
        //   [dynamic system prompt] + [recent chat history from DB] + [current user message]
        //
        // We ALWAYS reset the Conversation before sending so the model never sees
        // history duplicated — the reset wipes the internal KV-cache, then we
        // re-supply the history ourselves through the single combined prompt string.
        val chatContext = aiContextRepository.getChatContext()
        val currencyCode = currencyRepository.effectiveBaseCurrencyCode.first()
        val systemPrompt = buildSystemPrompt(chatContext, currencyCode)
        userPreferencesRepository.updateSystemPrompt(systemPrompt)

        // Fetch the DB history (now includes the user message we just saved)
        val history = chatDao.getAllMessagesForContext(sessionId)
        val fullPrompt = buildFullPrompt(systemPrompt, history, userMessage)

        Log.d("LlmRepository", "=== SENDING TO LLM ===")
        Log.d("LlmRepository", "Prompt length: ${fullPrompt.length} chars (~${fullPrompt.length / 4} tokens)")
        Log.d("LlmRepository", fullPrompt.take(300))

        // Reset the Conversation (wipes KV-cache) then send the full combined prompt.
        // This prevents history duplication that caused hallucination loops.
        llmService.resetConversation()

        val responseBuilder = StringBuilder()

        try {
            llmService.generateResponseStream(fullPrompt)
                .collect { partialResponse ->
                    responseBuilder.append(partialResponse)
                    emit(partialResponse)
                }
        } finally {
            val finalResponse = responseBuilder.toString()
            if (finalResponse.isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val aiMessage = ChatMessage(
                        message = finalResponse,
                        isUser = false,
                        sessionId = sessionId
                    )
                    chatDao.insertMessage(aiMessage)
                    Log.d("LlmRepository", "AI response saved: ${finalResponse.take(50)}")
                }
            }
        }
    }
    
    suspend fun deleteAllMessagesForSession(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
        chatSessionDao.deleteSession(sessionId)
    }
    
    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessage(messageId)
    }
    
    suspend fun editMessage(messageId: String, newText: String) {
        val msg = chatDao.getMessageById(messageId) ?: return
        chatDao.deleteMessage(messageId)
        chatDao.deleteMessagesAfter(msg.sessionId, msg.timestamp)
    }

    suspend fun getMessageById(messageId: String): ChatMessage? = chatDao.getMessageById(messageId)
    
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long) = chatDao.deleteMessagesAfter(sessionId, timestamp)
    
    suspend fun getLastUserMessage(sessionId: String): ChatMessage? = chatDao.getLastUserMessage(sessionId)

    suspend fun getMessageCountForSession(sessionId: String): Int = chatDao.getMessageCountForSession(sessionId)

    /**
     * Assembles the full prompt string sent to the model on every turn:
     *   [system prompt]
     *   [chat history turns (oldest first)]
     *   User: [current message]
     *   Assistant:
     *
     * The history already contains the current user message (saved before this call),
     * so we skip re-appending it and instead rely on the "User:" prefix added at the end.
     */
    private fun buildFullPrompt(
        systemPrompt: String,
        history: List<ChatMessage>,
        currentUserMessage: String
    ): String {
        val sb = StringBuilder()
        sb.append(systemPrompt)
        sb.append("\n\n")

        // Add prior turns (exclude system-prompt DB rows and the last user message,
        // which we will re-append with the "User:" prefix below)
        val priorTurns = history
            .filter { !it.isSystemPrompt }
            .dropLastWhile { it.isUser && it.message == currentUserMessage }

        priorTurns.forEach { msg ->
            if (msg.isUser) {
                sb.append("User: ${msg.message}\n")
            } else {
                sb.append("Assistant: ${msg.message}\n")
            }
        }

        sb.append("User: $currentUserMessage\n")
        sb.append("Assistant:")

        return sb.toString()
    }
    
    private fun buildSystemPrompt(context: ChatContext, currencyCode: String): String {
        val monthSummary = context.monthSummary
        val topCategories = context.topCategories
        val activeSubs = context.activeSubscriptions
        val stats = context.quickStats
        val budgets = context.budgets
        val accounts = context.accountBalances
        val categories = context.categories

        val totalSubAmount = activeSubs.sumOf { it.amount.toDouble() }.toBigDecimal()
        val upcomingPayments = activeSubs.filter { it.nextPaymentDays <= 7 }

        val recentLines = context.recentTransactions.take(10).joinToString("\n") { t ->
            val dateStr = t.dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
            val typeSign = when (t.transactionType) {
                TransactionType.INCOME -> "+"
                else -> "-"
            }
            val origFormatted = CurrencyFormatter.formatCurrency(t.amount, t.originalCurrency)
            val conversion = t.convertedAmount?.let { conv ->
                " (≈ ${CurrencyFormatter.formatCurrency(conv, currencyCode)})"
            } ?: ""
            "$dateStr: ${t.merchantName} $typeSign$origFormatted$conversion [${t.transactionType.name}] (${t.category})"
        }

        val budgetLines = budgets.joinToString("\n") { b ->
            val spent = CurrencyFormatter.formatCurrency(b.currentSpending, b.currency)
            val total = CurrencyFormatter.formatCurrency(b.amount, b.currency)
            val remaining = CurrencyFormatter.formatCurrency(b.remaining, b.currency)
            "- ${b.name}: $spent / $total (${(b.percentUsed * 100).toInt()}%) — $remaining remaining"
        }

        val bankLines = accounts.filter { !it.isCreditCard && !it.isWallet }
            .joinToString("\n") { a ->
                val bal = CurrencyFormatter.formatCurrency(a.balance, a.currency)
                "- ${a.bankName} (••••${a.accountLast4}): $bal"
            }

        val walletLines = accounts.filter { it.isWallet }
            .joinToString("\n") { a ->
                val bal = CurrencyFormatter.formatCurrency(a.balance, a.currency)
                "- ${a.bankName} (••••${a.accountLast4}): $bal"
            }

        val creditLines = accounts.filter { it.isCreditCard }
            .joinToString("\n") { a ->
                val bal = CurrencyFormatter.formatCurrency(a.balance, a.currency)
                val limit = a.creditLimit?.let { CurrencyFormatter.formatCurrency(it, a.currency) } ?: "N/A"
                val avail = a.availableCredit?.let { CurrencyFormatter.formatCurrency(it, a.currency) } ?: "N/A"
                "- ${a.bankName} (••••${a.accountLast4}): $bal outstanding / $limit limit ($avail available)"
            }

        val categoryLines = categories.joinToString("\n") { cat ->
            val subs = cat.subcategories.take(4)
            if (subs.isNotEmpty()) "- ${cat.name}: ${subs.joinToString(", ")}" else "- ${cat.name}"
        }

        val effSymbol = com.ritesh.cashiro.data.currency.model.CurrencySymbols.getSymbol(currencyCode)

        return """
You are Cashiro AI, a concise financial assistant. Answer only from the data below. Never guess or invent numbers.

DATA (as of ${context.currentDate}):
This month: spent ${CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currencyCode)}, income ${CurrencyFormatter.formatCurrency(monthSummary.totalIncome, currencyCode)}, ${monthSummary.transactionCount} transactions, daily avg ${CurrencyFormatter.formatCurrency(stats.avgDailySpending, currencyCode)}

Top categories:
${topCategories.joinToString("\n") { "- ${it.category}: ${CurrencyFormatter.formatCurrency(it.amount, currencyCode)} (${it.percentage.toInt()}%)" }}

Subscriptions: ${activeSubs.size} active, ${CurrencyFormatter.formatCurrency(totalSubAmount, currencyCode)}/month${if (upcomingPayments.isNotEmpty()) ", ${upcomingPayments.size} due within 7 days" else ""}

Recent transactions (last 14 days):
$recentLines

Active budgets:
${if (budgetLines.isNotEmpty()) budgetLines else "None"}

Bank Accounts:
${if (bankLines.isNotEmpty()) bankLines else "None"}

Wallets:
${if (walletLines.isNotEmpty()) walletLines else "None"}

Credit Cards:
${if (creditLines.isNotEmpty()) creditLines else "None"}

Categories:
${categoryLines}

## Currency Rules
- Display amounts in their original currency using the symbol (e.g., "$100", "€50", "₹500", "£75") — NEVER use currency codes like "USD", "INR", "EUR".
- When a converted amount is shown in parentheses (≈ ...), you may reference both: e.g., "the transaction is $5 (~₹450)".
- For summary totals (monthly income/expense, budget totals), use $effSymbol only.
- Each transaction and account line already shows the correct currency symbol. Always repeat amounts exactly as shown — do not convert or change the currency symbol.
        """.trimIndent()
    }
    
    suspend fun updateSystemPrompt() {
        val chatContext = aiContextRepository.getChatContext()
        val currencyCode = currencyRepository.effectiveBaseCurrencyCode.first()
        val newPrompt = buildSystemPrompt(chatContext, currencyCode)
        userPreferencesRepository.updateSystemPrompt(newPrompt)
        Log.d("LlmRepository", "System prompt updated with latest financial data")
    }
    
    suspend fun getFormattedContextForDisplay(): String {
        val chatContext = aiContextRepository.getChatContext()
        val monthSummary = chatContext.monthSummary
        val recentCount = minOf(chatContext.recentTransactions.size, 10)
        val activeSubs = chatContext.activeSubscriptions
        
        return """
        Hi! I'm Cashiro AI, your financial assistant.
        
        I have access to:
        • Your last 2 weeks of transactions ($recentCount recent ones)
        • This month's summary (${monthSummary.transactionCount} total transactions)
        • Monthly income and expenses
        • Top spending categories
        • Active subscriptions (${activeSubs.size} services)
        • Daily spending averages
        
        I can help you understand your spending, find savings, and answer questions about your recent finances.
        
        What would you like to know?
        """.trimIndent()
    }
}