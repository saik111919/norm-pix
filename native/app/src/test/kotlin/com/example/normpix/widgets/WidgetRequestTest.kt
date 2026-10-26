package com.example.normpix.widgets

import com.example.normpix.finance.domain.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a widget asks for travels as plain words, and only those words are taken back. */
class WidgetRequestTest {
    @Test
    fun `every request comes back as itself from its words`() {
        val requests = TransactionType.entries.map { WidgetRequest.Add(it) } + WidgetRequest.Show(42) + WidgetRequest.Activity
        for (request in requests) {
            val id = (request as? WidgetRequest.Show)?.transactionId ?: -1
            assertEquals(request, WidgetRequest.of(request.key, id), request.key)
        }
        assertEquals("add-expense", WidgetRequest.Add(TransactionType.Expense).key)
    }

    @Test
    fun `anything else is no request`() {
        assertNull(WidgetRequest.of(null))
        assertNull(WidgetRequest.of(""))
        assertNull(WidgetRequest.of("add-loan"))
        assertNull(WidgetRequest.of("delete-everything"))
        // A transaction to show needs its number.
        assertNull(WidgetRequest.of("show"))
        assertNull(WidgetRequest.of("show", 0))
    }
}
