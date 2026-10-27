package com.example.normpix.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.normpix.about.GuideTopic
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey

/** The top-level destinations of the app shell. */
enum class AppDestination(val label: String) {
    Home("Home"),
    PlansAndTransactions("Plans & Activity"),
    Insights("Insights"),
    Wallet("Wallet"),
}

/** The two views inside Plans & Activity. */
enum class PlansAndTransactionsTab(val label: String) {
    Transactions("Activity"),
    Plans("Plans"),
}

/** The sections inside Plans. */
enum class PlanSection(val label: String) {
    Budgets("Budgets"),
    Recurring("Recurring"),
    Reminders("Reminders"),
}

/** Settings pages reachable directly (from search, or links inside the app). */
enum class SettingsPage { Appearance, Widgets, Finance, Banks, Data, Wallet, Notifications, Availability, Security, Updates }

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

    /** One category's transactions over the whole days of [range]; a navigator without the route shows the month it starts in. */
    fun openCategoryHistory(categoryId: Long, range: DateRange) = openCategoryHistory(categoryId, MonthKey.of(range.start))

    fun openAccountHistory(accountId: Long)

    fun openSettings(page: SettingsPage? = null)

    /** One screen of Settings > Appearance (its colors, its font, its transitions...); a navigator without the route shows Appearance itself. */
    fun openAppearance(section: AppearanceSection) = openSettings(SettingsPage.Appearance)

    /** The Profile page: the user's name and picture, and where the account button leads. */
    fun openProfile()

    fun openSearch()

    /** The full-screen alert for a reminder that just came due (or whose notification was tapped). */
    fun openReminderAlert(reminderId: Long)

    /** The update page: checks GitHub for a newer NormPix, and downloads and installs it (a navigator without it does nothing). */
    fun openUpdates() = Unit

    /** The changelog page: what changed across NormPix releases (a navigator without it does nothing). */
    fun openChangelog() = Unit

    /** The About page: what NormPix is and a guide to how each of its features works (a navigator without it does nothing). */
    fun openAbout() = Unit

    /** One topic of the About guide, step by step; a navigator without the route shows the About page. */
    fun openGuide(topic: GuideTopic) = openAbout()

    /** Switches the shell to [destination]. */
    fun go(destination: AppDestination)

    /** Switches the shell to Plans & Transactions, showing the whole transaction history (every month, newest first). */
    fun openTransactions()

    /** Switches the shell to Plans & Transactions, showing Plans at [section] (or the last one shown). */
    fun openPlans(section: PlanSection? = null)
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> { error("No AppNavigator provided") }
