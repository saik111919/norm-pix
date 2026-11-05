package com.example.normpix.finance.ui

import androidx.compose.material3.SnackbarHostState
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppSnackbars
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Undo snackbar of a delete tells its caller when the delete is final: the snackbar went without the Undo. That is when a
 * deleted repeating payment's reminders are removed too - and not while the Undo can still bring the payment back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferUndoSettlesTest {
    private val env = TestEnvironment("offer_undo_settles")
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val host = SnackbarHostState()
    private val snackbars = AppSnackbars(host, scope)

    @After
    fun tearDown() = env.close()

    private fun waitForSnackbar() = runBlocking {
        withTimeout(5_000) { while (host.currentSnackbarData == null) kotlinx.coroutines.delay(10) }
    }

    private fun offer(onSettled: () -> Unit) = runBlocking {
        val undo = env.container.finance.undoable { emptyList() }
        snackbars.offerUndo("Deleted", env.container.finance, undo, scope, onSettled = onSettled)
        waitForSnackbar()
    }

    @Test
    fun aSnackbarThatGoesWithoutTheUndoSettlesTheDelete() {
        var settled = 0
        offer { settled++ }
        assertEquals(0, settled, "not before it has gone")
        host.currentSnackbarData?.dismiss()
        runBlocking { withTimeout(5_000) { while (settled == 0) kotlinx.coroutines.delay(10) } }
        assertEquals(1, settled)
    }

    @Test
    fun anUndoThatIsUsedDoesNotSettleIt() {
        var settled = 0
        offer { settled++ }
        host.currentSnackbarData?.performAction()
        runBlocking { kotlinx.coroutines.delay(300) }
        assertFalse(settled > 0, "taken back: the payment and its reminder stay")
        assertTrue(true)
    }
}
