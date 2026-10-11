package com.example.normpix.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.normpix.finance.domain.MonthKey

/** The top-level destinations of the app shell. */
enum class AppDestination(val label: String) {
    Home("Home"),
    PlansAndTransactions("Plans & Transactions"),
    Insights("Insights"),
    Wallet("Wallet"),
}

/** The two views inside Plans & Transactions. */
enum class PlansAndTransactionsTab(val label: String) {
    Transactions("Transactions"),
    Plans("Plans"),
}

/** The sections inside Plans. */
enum class PlanSection(val label: String) {
    Budgets("Budgets"),
    Recurring("Recurring"),
    Reminders("Reminders"),
}

/** Settings pages reachable directly (from search, or links inside the app). */
enum class SettingsPage { Appearance, Finance, Data, Wallet, Availability, Security }

/**
 * Everything a screen may ask the app to show. Screens depend on this
 * interface rather than on the navigation library, so flows stay testable
 * and the graph can change without touching them.
 */
interface AppNavigator {
    fun back()

    fun openCategories()

    fun openBudgetHistory()

    /** The People page: what is spent on each person. */
    fun openPeople()

    /** One person's page: what has been spent on them and their expenses. */
    fun openPerson(personId: Long)

    fun openCategoryHistory(categoryId: Long, month: MonthKey)

    fun openAccountHistory(accountId: Long)

    fun openSettings(page: SettingsPage? = null)

    /** The Profile page: the user's name and picture, and where the account button leads. */
    fun openProfile()

    fun openSearch()

    /** The full-screen alert for a reminder that just came due (or whose notification was tapped). */
    fun openReminderAlert(reminderId: Long)

    /** Switches the shell to [destination]. */
    fun go(destination: AppDestination)

    /** Switches the shell to Plans & Transactions, showing the whole transaction history (every month, newest first). */
    fun openTransactions()

    /** Switches the shell to Plans & Transactions, showing Plans at [section] (or the last one shown). */
    fun openPlans(section: PlanSection? = null)
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("No AppNavigator provided") }
