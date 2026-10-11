package com.example.normpix.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Back inside Plans & Transactions climbs to the root of each level: section, then view. */
class ShellStateTest {
    private fun plans(section: PlanSection, tab: PlansAndTransactionsTab = PlansAndTransactionsTab.Plans) =
        ShellState(AppDestination.PlansAndTransactions.ordinal, section.ordinal, tab.ordinal)

    @Test
    fun recurringAndReminders_stepBackToBudgets_thenToTransactions() {
        for (section in listOf(PlanSection.Recurring, PlanSection.Reminders)) {
            val shell = plans(section)
            assertTrue(shell.canStepBackInPlans)

            assertTrue(shell.stepBackInPlans())
            assertEquals(PlanSection.Budgets, shell.section)
            assertEquals(PlansAndTransactionsTab.Plans, shell.tab, "still in Plans after one step")

            assertTrue(shell.stepBackInPlans())
            assertEquals(PlansAndTransactionsTab.Transactions, shell.tab)
            assertEquals(AppDestination.PlansAndTransactions, shell.destination, "stays on the tab until the history takes over")

            assertFalse(shell.canStepBackInPlans)
            assertFalse(shell.stepBackInPlans(), "at the root there is nothing left to climb")
        }
    }

    @Test
    fun budgets_stepsStraightBackToTransactions() {
        val shell = plans(PlanSection.Budgets)
        assertTrue(shell.stepBackInPlans())
        assertEquals(PlansAndTransactionsTab.Transactions, shell.tab)
    }

    @Test
    fun nothingToClimb_onTransactions_orOnAnotherTab() {
        val transactions = plans(PlanSection.Reminders, PlansAndTransactionsTab.Transactions)
        assertFalse(transactions.canStepBackInPlans)
        assertFalse(transactions.stepBackInPlans())
        assertEquals(PlanSection.Reminders, transactions.section, "the remembered section is left alone")

        // Plans is only "open" while its tab is the one showing.
        val elsewhere = ShellState(AppDestination.Wallet.ordinal, PlanSection.Reminders.ordinal, PlansAndTransactionsTab.Plans.ordinal)
        assertFalse(elsewhere.canStepBackInPlans)
        assertFalse(elsewhere.stepBackInPlans())
        assertEquals(PlansAndTransactionsTab.Plans, elsewhere.tab)
    }
}
