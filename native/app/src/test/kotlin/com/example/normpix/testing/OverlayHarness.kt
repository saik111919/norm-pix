package com.example.normpix.testing

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.quickadd.QuickAddHost
import com.example.normpix.quickadd.QuickAddRequest
import com.example.normpix.quickadd.QuickAddRequests
import com.example.normpix.wallet.Authenticator

/** A prompt that answers as the test says and remembers how often it was asked. */
class RecordingAuthenticator(var succeed: Boolean = true) : Authenticator {
    var asked = 0
        private set

    override suspend fun isSupported(): Boolean = true

    override suspend fun authenticate(reason: String): Boolean {
        asked++
        return succeed
    }
}

/**
 * The quick add overlay's screen ([QuickAddHost]) on a test's [TestEnvironment], as the activity composes it: asked for something,
 * it does it and ends ([finished]). A test that asks again after it ended starts it again (a Compose rule takes one `setContent`,
 * so the host is rebuilt inside it) - which is what a second launch of the overlay is.
 */
class OverlayHarness(private val compose: ComposeContentTestRule, private val env: TestEnvironment) {
    val requests = QuickAddRequests()

    /** True once the overlay has ended (the activity would have finished); false again when it is asked for something more. */
    var finished = false
        private set

    /** How many times the overlay ended. */
    var finishes = 0
        private set

    private var generation by mutableIntStateOf(0)
    private var started = false

    fun share(text: String?, picture: Uri? = null) = ask(QuickAddRequest.Share(text.orEmpty(), picture))

    fun add(type: TransactionType) = ask(QuickAddRequest.Add(type))

    fun ask(request: QuickAddRequest) {
        requests.offer(request)
        if (!started) {
            started = true
            compose.setContent {
                key(generation) {
                    QuickAddHost(env.container, requests) {
                        finished = true
                        finishes++
                    }
                }
            }
        } else if (finished) {
            finished = false
            compose.runOnUiThread { generation++ }
        }
        compose.waitForIdle()
    }
}
