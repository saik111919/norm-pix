package com.example.normpix.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.LocalOverlays
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Does what a home-screen widget asked for, once the app is unlocked ([held] false): the new-transaction sheet, one
 * transaction's details over Activity, or Activity. The request is taken once.
 *
 * The widgets show the user's own ledger, never the sample set. So while [sampleData] is on, a transaction from the widget is
 * not looked up in the sample set (Activity opens instead), and adding asks first to switch back - the request waits for the
 * switch, so the sheet then opens on the user's own data.
 */
@Composable
fun ReceiveWidgetRequests(requests: MutableStateFlow<WidgetRequest?>, held: Boolean, sampleData: Boolean) {
    val request by requests.collectAsStateWithLifecycle()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val finance = LocalFinance.current
    val overlays = LocalOverlays.current
    val preferences = LocalAppPreferences.current
    LaunchedEffect(request, held, sampleData) {
        val asked = request ?: return@LaunchedEffect
        if (held) return@LaunchedEffect
        when (asked) {
            WidgetRequest.Activity -> {
                requests.value = null
                navigator.openTransactions()
            }
            is WidgetRequest.Show -> {
                requests.value = null
                navigator.openTransactions()
                if (sampleData) return@LaunchedEffect
                finance.repository.transactionById(asked.transactionId)?.let(actions::showDetails)
            }
            is WidgetRequest.Add -> {
                if (sampleData && preferences != null) {
                    val switch = overlays.confirm(
                        title = "Sample data is on",
                        message = "What you add from the widget goes into your own data, not the sample set, which is thrown " +
                            "away. Turn sample data off to add it?",
                        confirmLabel = "Turn off sample data",
                    )
                    if (switch) {
                        // The request stays: the sheet opens once the user's own data is back.
                        preferences.setDummyDataEnabled(false)
                    } else {
                        requests.value = null
                    }
                    return@LaunchedEffect
                }
                requests.value = null
                actions.addTransaction(asked.type)
            }
        }
    }
}
