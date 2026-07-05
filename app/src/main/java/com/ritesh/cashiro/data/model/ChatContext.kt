package com.ritesh.cashiro.data.model

import com.ritesh.cashiro.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Data models for AI chat context
 */
data class ChatContext(
    val currentDate: LocalDate,
    val monthSummary: MonthSummary,
    val recentTransactions: List<TransactionSummary>,
    val activeSubscriptions: List<SubscriptionSummary>,
    val topCategories: List<CategorySpending>,
    val quickStats: QuickStats,
    val budgets: List<BudgetSummary> = emptyList(),
    val accountBalances: List<AccountBalanceSummary> = emptyList(),
    val categories: List<CategoryInfo> = emptyList()
)

data class MonthSummary(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val transactionCount: Int,
    val daysInMonth: Int,
    val currentDay: Int
)

data class TransactionSummary(
    val merchantName: String,
    val amount: BigDecimal,
    val originalCurrency: String = "INR",
    val convertedAmount: BigDecimal? = null,
    val category: String,
    val subcategory: String? = null,
    val daysAgo: Int,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val transactionType: TransactionType = TransactionType.EXPENSE
)

data class SubscriptionSummary(
    val merchantName: String,
    val amount: BigDecimal,
    val nextPaymentDays: Int
)

data class CategorySpending(
    val category: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

data class QuickStats(
    val avgDailySpending: BigDecimal,
    val largestExpenseThisMonth: TransactionSummary?,
    val mostFrequentMerchant: String?,
    val mostFrequentMerchantCount: Int = 0
)

data class BudgetSummary(
    val name: String,
    val amount: BigDecimal,
    val currentSpending: BigDecimal,
    val remaining: BigDecimal,
    val percentUsed: Float,
    val currency: String
)

data class CategoryInfo(
    val name: String,
    val subcategories: List<String> = emptyList()
)

data class AccountBalanceSummary(
    val bankName: String,
    val accountLast4: String,
    val balance: BigDecimal,
    val currency: String,
    val isCreditCard: Boolean = false,
    val isWallet: Boolean = false,
    val creditLimit: BigDecimal? = null
) {
    val availableCredit: BigDecimal?
        get() = if (isCreditCard && creditLimit != null) creditLimit - balance else null
}