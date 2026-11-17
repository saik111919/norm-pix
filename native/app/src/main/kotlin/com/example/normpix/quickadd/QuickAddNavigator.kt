package com.example.normpix.quickadd

import android.content.Context
import android.content.Intent
import com.example.normpix.MainActivity
import com.example.normpix.about.GuideTopic
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.SettingsPage

/**
 * The navigator of the quick add overlay, which has no pages of its own: the add flow never asks for one, but the screens' shared
 * actions need a navigator, and none of them may quietly do nothing. So every page request opens the full app ([MainActivity], where
 * the user last left it - the page asked for is not carried over) and closes the overlay; [back] only closes the overlay.
 */
class QuickAddNavigator(private val context: Context, private val close: () -> Unit) : AppNavigator {
    private fun openApp() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        close()
    }

    override fun back() = close()

    override fun openCategories() = openApp()

    override fun openBudgetHistory() = openApp()

    override fun openRepeatingCosts() = openApp()

    override fun openForecast() = openApp()

    override fun openBillsCalendar() = openApp()

    override fun openPeople() = openApp()

    override fun openNotifications() = openApp()

    override fun openPerson(personId: Long) = openApp()

    override fun openCategoryHistory(categoryId: Long, month: MonthKey) = openApp()

    override fun openCategoryHistory(categoryId: Long, range: DateRange) = openApp()

    override fun openAccountHistory(accountId: Long) = openApp()

    override fun openTagHistory(tagId: Long, range: DateRange?) = openApp()

    override fun openYearByTag() = openApp()

    override fun openSettings(page: SettingsPage?) = openApp()

    override fun openAppearance(section: AppearanceSection) = openApp()

    override fun openProfile() = openApp()

    override fun openSearch() = openApp()

    override fun openReminderAlert(reminderId: Long) = openApp()

    override fun openUpdates() = openApp()

    override fun openChangelog() = openApp()

    override fun openAbout() = openApp()

    override fun openGuideIndex() = openApp()

    override fun openGuide(topic: GuideTopic) = openApp()

    override fun go(destination: AppDestination) = openApp()

    override fun openTransactions() = openApp()

    override fun openPlans(section: PlanSection?) = openApp()
}
