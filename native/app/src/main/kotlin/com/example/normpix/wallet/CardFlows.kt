package com.example.normpix.wallet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.AppContainer
import com.example.normpix.finance.ui.FinanceActions
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalPermissions
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.PermissionRequester
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.clipboard.CardCopySheet
import com.example.normpix.wallet.clipboard.CardField
import com.example.normpix.wallet.clipboard.copiedMessage
import com.example.normpix.wallet.expiry.CardExpiryNotifications
import com.example.normpix.wallet.nfc.ExchangeTab
import com.example.normpix.wallet.nfc.NfcExchangeSheet
import com.example.normpix.wallet.nfc.PaymentCardInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Wallet flows (sheet/dialog -> shared card list -> feedback), used by the
 * Wallet tab, its floating action, and the reminder form's "Add card".
 */
class CardFlows(
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val haptics: AppHaptics,
    private val finance: FinanceActions,
    private val navigator: AppNavigator,
    private val scope: CoroutineScope,
    private val context: Context,
    private val permissions: PermissionRequester,
) {
    private val cards get() = container.cards

    /**
     * Shows the add-card form and stores the result; null if cancelled or the vault refused.
     * [tapped]: a bank card identified by NFC, so the form starts from what the tap could tell.
     */
    suspend fun addAndSave(tapped: PaymentCardInfo? = null): StoredCard? {
        val card = overlays.sheet<StoredCard> { dismiss -> AddCardSheet(initial = null, onDone = dismiss, tapped = tapped) } ?: return null
        if (!cards.add(card)) {
            snackbars.show("Couldn't save to the card vault. Try again.")
            return null
        }
        haptics.success()
        return card
    }

    fun addCard(tapped: PaymentCardInfo? = null) {
        scope.launch { if (addAndSave(tapped) != null) snackbars.show("Card added") }
    }

    fun editCard(card: StoredCard) {
        scope.launch {
            val updated = overlays.sheet<StoredCard> { dismiss -> AddCardSheet(initial = card, onDone = dismiss) } ?: return@launch
            if (cards.update(updated)) {
                haptics.success()
                snackbars.show("Card updated")
            } else {
                snackbars.show("Couldn't update the card")
            }
        }
    }

    /**
     * Copies one detail of [card] and says so, with how long it will stay. Returns whether it was copied. The number
     * and CVV are copied without ever being shown on screen.
     */
    fun copyField(card: StoredCard, field: CardField): Boolean {
        val timeout = container.preferences.state.value.clipboardTimeout
        if (!container.clipboard.copy(field.label, field.valueOf(card), timeout)) return false
        haptics.success()
        return true
    }

    /**
     * [copyField] with the confirmation said by [say] - a snackbar unless told otherwise - for copies that don't happen
     * inside the copy sheet. A snackbar sits under any open sheet, so a sheet that copies says it itself.
     */
    fun copyFieldAndSay(card: StoredCard, field: CardField, say: (String) -> Unit = { snackbars.show(it) }) {
        if (copyField(card, field)) {
            say(copiedMessage(field, container.preferences.state.value.clipboardTimeout))
        } else {
            say("Couldn't copy to the clipboard.")
        }
    }

    /** The "Copy card details" sheet: pick what to copy, as many times as needed. */
    fun copyDetails(card: StoredCard) {
        scope.launch {
            val timeout = container.preferences.state.value.clipboardTimeout
            overlays.sheet<Unit> { dismiss ->
                CardCopySheet(card = card, timeout = timeout, onCopy = { field -> copyField(card, field) }, onDone = { dismiss(null) })
            }
        }
    }

    /** Opens [card]'s details in a sheet: the card itself, each detail one tap from the clipboard, and Wallet's controls for it. */
    fun showCard(card: StoredCard) {
        scope.launch {
            overlays.sheet<Unit>(expandable = true) { dismiss ->
                CardDetailsSheet(cardId = card.id, flows = this@CardFlows, onDismiss = { dismiss(null) })
            }
        }
    }

    /** Asks for the notification permission (Android 13+), or sends the user to the system's notification settings for NormPix. */
    fun turnOnExpiryNotifications() {
        scope.launch {
            val granted = Build.VERSION.SDK_INT >= 33 && !ReminderNotifications.hasPermission(context) &&
                permissions.request(Manifest.permission.POST_NOTIFICATIONS)
            if (!granted && !CardExpiryNotifications.enabled(context)) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun deleteCard(card: StoredCard) {
        scope.launch {
            val confirmed = overlays.confirm(
                title = "Delete ${card.bankName} card?",
                message = "The card ending in ${card.last4} will be permanently removed from this device's encrypted vault.",
                confirmLabel = "Delete",
                destructive = true,
                icon = Icons.Outlined.DeleteOutline,
            )
            if (!confirmed) return@launch
            if (cards.delete(card.id)) {
                haptics.success()
                snackbars.show("Card deleted")
            }
        }
    }

    /**
     * Opens the NFC Card Exchange sheet.
     * Supports both Inlet (receive card) and Outlet (broadcast card). A bank card tapped on the
     * Inlet continues in the add-card form, since its details can only be typed or scanned.
     */
    fun openNfcExchange(tab: ExchangeTab = ExchangeTab.Inlet, card: StoredCard? = null) {
        scope.launch {
            val next = overlays.sheet<ManualEntry> { dismiss ->
                NfcExchangeSheet(
                    initialTab = tab,
                    initialCard = card,
                    onDismiss = { dismiss(null) },
                    onAddCardDetails = { info -> dismiss(ManualEntry(info)) },
                    // Sheets sit above the navigation graph, so the sheet closes and this flow does the navigating.
                    onOpenSchedule = { dismiss(ManualEntry(tapped = null, openSchedule = true)) },
                )
            } ?: return@launch
            if (next.openSchedule) {
                navigator.openSettings(SettingsPage.Availability)
                return@launch
            }
            addCard(next.tapped)
        }
    }

    /** The exchange sheet's way of saying "continue in the add-card form". */
    private class ManualEntry(val tapped: PaymentCardInfo?, val openSchedule: Boolean = false)

    /**
     * Tapping the Wallet tab's FAB offers both "Add card" (the primary task)
     * and "New account" (the finance tab's companion task), so the user doesn't
     * have to switch tabs just to add a bank account. Also supports NFC Inlet.
     */
    fun openAddMenu() {
        scope.launch {
            val choice = overlays.sheet<String> { dismiss ->
                Column(Modifier.padding(AppSpacing.page).padding(bottom = AppSpacing.lg)) {
                    SheetHeader(title = "Add to wallet")
                    Spacer(Modifier.height(AppSpacing.sm))
                    ListItem(
                        modifier = Modifier.clickable { dismiss("card") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Outlined.CreditCard, contentDescription = null) },
                        headlineContent = { Text("Payment card") },
                        supportingContent = { Text("Add debit or credit card to device vault") },
                    )
                    ListItem(
                        modifier = Modifier.clickable { dismiss("account") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                        headlineContent = { Text("Bank account or cash") },
                        supportingContent = { Text("Track a bank balance or cash on hand") },
                    )
                    ListItem(
                        modifier = Modifier.clickable { dismiss("nfc_inlet") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Outlined.Nfc, contentDescription = null) },
                        headlineContent = { Text("Tap to add (NFC)") },
                        supportingContent = { Text("Tap a phone sharing a card from NormPix, or your contactless card") },
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                }
            }
            when (choice) {
                "account" -> finance.addAccount()
                "card" -> addCard()
                "nfc_inlet" -> openNfcExchange(ExchangeTab.Inlet)
            }
        }
    }
}

@Composable
fun rememberCardFlows(): CardFlows {
    val container = LocalAppContainer.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val finance = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissions = LocalPermissions.current
    return remember(container, overlays, snackbars, haptics, finance, navigator, scope, context, permissions) {
        CardFlows(container, overlays, snackbars, haptics, finance, navigator, scope, context, permissions)
    }
}
