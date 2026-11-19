package com.example.normpix.wallet.passes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.normpix.AppContainer
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Wallet does with loyalty, membership and gift cards: add one (the form, typed or scanned), show one at the counter, edit it,
 * delete it (asking first, with Undo). On the app scope, so a flow finishes - and says so - when the screen that started it goes.
 */
class PassFlows(
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val haptics: AppHaptics,
    private val scope: CoroutineScope,
) {
    private val passes get() = container.passes

    /** The form for a new pass; kept when saved. */
    fun add() {
        scope.launch {
            val made = overlays.sheet<StoredPass> { dismiss -> PassForm(null, dismiss) } ?: return@launch
            if (passes.add(made)) snackbars.show("${made.name} saved") else snackbars.show("Couldn't save the card. Try again.", SnackbarKind.Error)
        }
    }

    /** [pass] at the counter; Edit and Delete from there. */
    fun open(pass: StoredPass) {
        scope.launch {
            when (overlays.sheet<PassAction>(expandable = true) { dismiss -> PassSheet(pass, onAction = dismiss) }) {
                PassAction.Edit -> edit(pass)
                PassAction.Delete -> delete(pass)
                null -> Unit
            }
        }
    }

    private suspend fun edit(pass: StoredPass) {
        val edited = overlays.sheet<StoredPass> { dismiss -> PassForm(pass, dismiss) } ?: return
        if (passes.update(edited)) snackbars.show("${edited.name} updated") else snackbars.show("Couldn't save the changes. Try again.", SnackbarKind.Error)
    }

    private suspend fun delete(pass: StoredPass) {
        val confirmed = overlays.confirm(
            title = "Delete ${pass.name}?",
            message = "Its number" + (if (pass.hasPin) " and PIN" else "") + " go from this phone. A cards file or backup made before keeps it.",
            confirmLabel = "Delete",
            destructive = true,
            icon = Icons.Outlined.DeleteOutline,
        )
        if (!confirmed) return
        val at = passes.passes.indexOfFirst { it.id == pass.id }
        if (passes.delete(pass.id)) {
            haptics.success()
            snackbars.showUndoable(
                "${pass.name} deleted",
                undo = {
                    scope.launch {
                        if (passes.restore(pass, at)) snackbars.show("${pass.name} restored") else snackbars.show("Couldn't restore the card.", SnackbarKind.Error)
                    }
                },
            )
        } else {
            snackbars.show("Couldn't delete the card. Try again.", SnackbarKind.Error)
        }
    }

    @Composable
    private fun PassForm(existing: StoredPass?, dismiss: (StoredPass?) -> Unit) {
        val scanner = container.scanner
        PassFormSheet(
            existing = existing,
            reader = container.passScanner,
            photoTarget = scanner?.let { s -> { s.newPhotoTarget() } },
            onDone = dismiss,
        )
    }
}

@Composable
fun rememberPassFlows(): PassFlows {
    val container = LocalAppContainer.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = LocalAppScope.current
    return remember(container, overlays, snackbars, haptics, scope) { PassFlows(container, overlays, snackbars, haptics, scope) }
}
