package com.example.normpix.wallet.clipboard

import com.example.normpix.settings.ClipboardTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/** A clipboard with the one thing that matters to the rules: who wrote what is on it, and whether a background app may look. */
private class FakePort : ClipboardPort {
    var text: String? = null
    var token: String? = null
    var readable = true
    var failWrites = false
    var clears = 0

    override fun write(label: String, value: String, token: String) {
        if (failWrites) throw IllegalStateException("clipboard unavailable")
        text = value
        this.token = token
    }

    override fun owner(): ClipboardOwner = when {
        !readable -> ClipboardOwner.Unknown
        token != null -> ClipboardOwner.NormPix(token!!)
        else -> ClipboardOwner.Someone
    }

    override fun clear() {
        text = null
        token = null
        clears++
    }

    /** Something else copies: not a NormPix clip any more. */
    fun copiedElsewhere(value: String) {
        text = value
        token = null
    }
}

private class FakeStore : PendingClearStore {
    var pending: PendingClear? = null

    override fun load() = pending

    override fun save(pending: PendingClear?) {
        this.pending = pending
    }
}

private class FakeBackup : ClearBackup {
    var scheduledAt: Long? = null
    var cancels = 0

    override fun schedule(atMillis: Long) {
        scheduledAt = atMillis
    }

    override fun cancel() {
        scheduledAt = null
        cancels++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardGuardTest {
    private val port = FakePort()
    private val store = FakeStore()
    private val backup = FakeBackup()
    private var tokens = 0

    private fun TestScope.guard(
        port: FakePort = this@ClipboardGuardTest.port,
        store: FakeStore = this@ClipboardGuardTest.store,
    ) = ClipboardGuard(port, store, backgroundScope, now = { currentTime }, backup = backup, newToken = { "t${++tokens}" })

    private fun TestScope.passSeconds(seconds: Int) = advanceTimeBy(seconds * 1000L + 1)

    @Test
    fun aCopyIsClearedWhenItsTimeIsUp() = runTest {
        val guard = guard()
        assertTrue(guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10))
        assertEquals("4111111111111111", port.text)

        passSeconds(9)
        assertEquals("4111111111111111", port.text, "still there before the time is up")
        passSeconds(2)
        assertNull(port.text, "gone after 10 seconds")
        assertNull(store.pending, "nothing left pending")
        assertNull(backup.scheduledAt, "the backup alarm is withdrawn")
    }

    @Test
    fun everyTimeoutIsHonoured() = runTest {
        val guard = guard()
        for (timeout in listOf(ClipboardTimeout.Sec10, ClipboardTimeout.Sec30, ClipboardTimeout.Min1, ClipboardTimeout.Min5)) {
            guard.copy("CVV", "123", timeout)
            passSeconds(timeout.seconds - 1)
            assertEquals("123", port.text, "$timeout: not yet")
            passSeconds(2)
            assertNull(port.text, "$timeout: cleared")
        }
    }

    @Test
    fun neverLeavesTheCopyAlone() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Never)
        passSeconds(60 * 60)
        assertEquals("4111111111111111", port.text)
        assertNull(store.pending)
        assertNull(backup.scheduledAt)
    }

    @Test
    fun somethingCopiedElsewhereInTheMeantimeIsLeftAlone() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        passSeconds(5)
        port.copiedElsewhere("an address the user copied")

        passSeconds(10)
        assertEquals("an address the user copied", port.text, "newer content from elsewhere is never removed")
        assertEquals(0, port.clears)
        assertNull(store.pending, "and the finished copy is forgotten")
    }

    @Test
    fun whenTheSystemWontSayWhoOwnsTheClipboardTheLastCopyIsClearedAnyway() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        port.readable = false // the user is in the app they are pasting into: a background app can't look

        passSeconds(11)
        assertNull(port.text)
        assertEquals(1, port.clears)
    }

    @Test
    fun aNewerCopySupersedesTheOlderOnesTimer() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        passSeconds(5)
        guard.copy("CVV", "123", ClipboardTimeout.Sec30)

        passSeconds(6) // the first copy's time is up
        assertEquals("123", port.text, "the CVV is not cleared by the card number's timer")
        passSeconds(20)
        assertEquals("123", port.text, "25 seconds in: the CVV's own 30 seconds have not passed")
        passSeconds(6)
        assertNull(port.text, "the CVV is cleared on its own schedule")
    }

    @Test
    fun copyingAgainRestartsTheTimerForThatCopy() = runTest {
        val guard = guard()
        guard.copy("CVV", "123", ClipboardTimeout.Sec10)
        passSeconds(8)
        guard.copy("CVV", "123", ClipboardTimeout.Sec10)
        passSeconds(4) // 12 s after the first copy
        assertEquals("123", port.text, "the second copy has its own full 10 seconds")
        passSeconds(7)
        assertNull(port.text)
    }

    @Test
    fun aNeverCopyAfterATimedOneCancelsTheTimer() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        guard.copy("Expiry date", "07/27", ClipboardTimeout.Never)
        passSeconds(30)
        assertEquals("07/27", port.text)
        assertNull(store.pending)
    }

    @Test
    fun aFailedWriteKeepsTheOlderCopysTimer() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        port.failWrites = true
        assertFalse(guard.copy("CVV", "123", ClipboardTimeout.Sec30))
        port.failWrites = false

        passSeconds(11)
        assertNull(port.text, "the card number is still on its original schedule")
    }

    @Test
    fun aCopyWhoseProcessDiedIsClearedOnTheNextStart() = runTest {
        guard().copy("Card number", "4111111111111111", ClipboardTimeout.Sec30)
        val deadline = store.pending!!.deadlineMillis

        // The process dies; the clipboard and the pending record survive. A new guard starts after the deadline.
        advanceTimeBy(deadline + 1000)
        val restarted = guard()
        restarted.reconcile()
        assertNull(port.text)
        assertNull(store.pending)
    }

    @Test
    fun aCopyStillWithinItsTimeResumesItsTimerAfterARestart() = runTest {
        guard().copy("Card number", "4111111111111111", ClipboardTimeout.Sec30)
        advanceTimeBy(10_000)

        val restarted = guard()
        restarted.reconcile()
        assertEquals("4111111111111111", port.text, "20 seconds still to go")
        passSeconds(21)
        assertNull(port.text)
    }

    @Test
    fun reconcileLeavesAnotherAppsClipAlone() = runTest {
        guard().copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        port.copiedElsewhere("something else")
        advanceTimeBy(60_000)

        guard().reconcile()
        assertEquals("something else", port.text)
    }

    @Test
    fun theBackupAlarmFollowsTheLatestCopyOnly() = runTest {
        val guard = guard()
        guard.copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        assertEquals(10_000L, backup.scheduledAt)
        advanceTimeBy(3_000)
        guard.copy("CVV", "123", ClipboardTimeout.Min1)
        assertEquals(63_000L, backup.scheduledAt)
    }

    @Test
    fun nothingIsStoredBeyondATokenAndADeadline() = runTest {
        guard().copy("Card number", "4111111111111111", ClipboardTimeout.Sec10)
        val pending = store.pending!!
        assertTrue("4111" !in pending.toString(), "no card data in what is persisted: $pending")
        assertEquals("t1", pending.token)
    }
}
