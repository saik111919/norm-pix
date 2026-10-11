package com.example.normpix.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CompletableDeferred

/**
 * Asks for runtime permissions from flows that are not themselves composables
 * (they `await` the answer). [OverlayHost]'s owner wires the actual launcher
 * with [rememberPermissionRequester].
 */
class PermissionRequester {
    internal var launch: ((String) -> Unit)? = null
    private var pending: CompletableDeferred<Boolean>? = null

    /** Shows the system dialog for [permission]; false if it was denied or can't be shown. */
    suspend fun request(permission: String): Boolean {
        val start = launch ?: return false
        pending?.cancel()
        val answer = CompletableDeferred<Boolean>()
        pending = answer
        start(permission)
        return answer.await()
    }

    internal fun deliver(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}

val LocalPermissions = compositionLocalOf<PermissionRequester> { error("No PermissionRequester provided") }

/** Creates the requester and connects it to the Activity Result API. */
@Composable
fun rememberPermissionRequester(): PermissionRequester {
    val requester = androidx.compose.runtime.remember { PermissionRequester() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requester.deliver(it) }
    SideEffect { requester.launch = { launcher.launch(it) } }
    return requester
}
