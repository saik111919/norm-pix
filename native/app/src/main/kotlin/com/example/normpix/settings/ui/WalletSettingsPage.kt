package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.settings.CardsLayout
import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.settings.WalletCardActionStyle
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Settings > Wallet & Cards: whether NormPix keeps cards at all and whether it keeps their CVVs (both delete what they cover,
 * after asking - see [CardsSettingsFlows]), and how saved cards look and behave in Wallet. With Store cards off, everything
 * about saved cards leaves the page, and the accounts' own settings stay.
 */
@Composable
fun WalletSettingsPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val preferences = container.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val files = rememberBackupFiles()
    val transfer = remember(container, overlays, snackbars, files) { CardsTransferFlows(container, overlays, snackbars, files) }
    val cardSwitches = remember(container, overlays, snackbars) { CardsSettingsFlows(container, overlays, snackbars) }
    /** The export or import in progress, if any (the other waits). */
    var busy by remember { mutableStateOf<String?>(null) }
    /** A switch for what the vault may hold is being dealt with: it asks, deletes, and only then moves. */
    var switching by remember { mutableStateOf(false) }
    val cardsOn = prefs.walletCardsEnabled
    // Sample data shows a stand-in vault: deleting from it would leave the real cards, so these switches wait until it is off.
    val sample = prefs.dummyDataEnabled

    fun switchVault(op: suspend () -> Unit) {
        if (switching) return
        switching = true
        scope.launch {
            try {
                op()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Something went wrong. Check the switch to see where things stand.", SnackbarKind.Error)
            } finally {
                switching = false
            }
        }
    }

    fun run(id: String, op: suspend () -> Unit) {
        if (busy != null) return
        busy = id
        scope.launch {
            try {
                op()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Something went wrong. Nothing was changed.", SnackbarKind.Error)
            } finally {
                busy = null
            }
        }
    }

    @Composable
    fun TransferRow(id: String, icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
        ListRow(
            headline = title,
            supporting = subtitle,
            enabled = enabled && busy == null,
            onClick = onClick,
            leading = {
                if (busy == id) {
                    AppLoadingIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    AppSubPage(title = "Wallet & Cards", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                ListSection(
                    title = "Saved cards",
                    footer = "Turning either one off deletes what it covers, after asking. A card never needs its CVV to be saved.",
                ) {
                    item {
                        SwitchRow(
                            headline = "Store cards",
                            supporting = when {
                                sample -> "Not while sample data is showing. Turn it off in Data & Backup first."
                                cardsOn -> "Debit and credit cards, kept encrypted on this device"
                                else -> "Off: no cards are kept, and the Wallet shows accounts only"
                            },
                            checked = cardsOn,
                            enabled = !sample && !switching,
                            onCheckedChange = { on ->
                                haptics.selection()
                                switchVault { cardSwitches.setCardsStored(on) }
                            },
                        )
                    }
                    if (cardsOn) {
                        item {
                            SwitchRow(
                                headline = "Save CVV",
                                supporting = when {
                                    sample -> "Not while sample data is showing. Turn it off in Data & Backup first."
                                    prefs.cardCvvEnabled -> "Optional on every card. Turn off to keep none"
                                    else -> "Off: cards are kept without a CVV, and the card form doesn't ask for one"
                                },
                                checked = prefs.cardCvvEnabled,
                                enabled = !sample && !switching,
                                onCheckedChange = { on ->
                                    haptics.selection()
                                    switchVault { cardSwitches.setCvvSaved(on) }
                                },
                            )
                        }
                    }
                }
                if (cardsOn) {
                    ListSection(
                        title = "Export and import",
                        footer = "Only your saved cards, apart from the finance backup. The file holds each card in full, so it is " +
                            "always locked with a password you choose. Importing adds cards; any already saved are kept as they are" +
                            if (prefs.cardCvvEnabled) "." else ", without their CVVs while Save CVV is off.",
                    ) {
                        item {
                            TransferRow("export", Icons.Rounded.SaveAlt, "Export cards", "A password-protected file of your saved cards") {
                                run("export") { transfer.export() }
                            }
                        }
                        item {
                            TransferRow(
                                "import",
                                Icons.Outlined.FileOpen,
                                "Import cards",
                                if (sample) "Not while sample data is showing. Turn it off in Data & Backup first." else "From a NormPix cards file",
                                enabled = !sample,
                            ) {
                                run("import") { transfer.import() }
                            }
                        }
                    }
                    ListSection(title = "Card actions", modifier = Modifier.selectableGroup()) {
                        for (style in WalletCardActionStyle.entries) {
                            item {
                                RadioRow(
                                    headline = style.label,
                                    supporting = style.description,
                                    selected = prefs.walletCardActionStyle == style,
                                    onClick = {
                                        haptics.selection()
                                        preferences.setWalletCardActionStyle(style)
                                    },
                                )
                            }
                        }
                    }
                }
                ListSection(
                    title = "Accounts layout",
                    footer = "How the Wallet shows your accounts. The Wallet has a switch for it too.",
                    modifier = Modifier.selectableGroup(),
                ) {
                    for (layout in AccountsLayout.entries) {
                        item {
                            RadioRow(
                                headline = layout.label,
                                supporting = layout.description,
                                selected = prefs.accountsLayout == layout,
                                onClick = {
                                    haptics.selection()
                                    preferences.setAccountsLayout(layout)
                                },
                            )
                        }
                    }
                }
                if (cardsOn) {
                    ListSection(
                        title = "Cards layout",
                        footer = "How the Wallet shows your saved cards. The Wallet has a switch for it too.",
                        modifier = Modifier.selectableGroup(),
                    ) {
                        for (layout in CardsLayout.entries) {
                            item {
                                RadioRow(
                                    headline = layout.label,
                                    supporting = layout.description,
                                    selected = prefs.cardsLayout == layout,
                                    onClick = {
                                        haptics.selection()
                                        preferences.setCardsLayout(layout)
                                    },
                                )
                            }
                        }
                    }
                }
                ListSection(title = "Bank logos", modifier = Modifier.selectableGroup()) {
                    for (style in BankIconStyle.entries) {
                        item {
                            RadioRow(
                                headline = style.label,
                                supporting = style.description,
                                selected = prefs.bankIconStyle == style,
                                onClick = {
                                    haptics.selection()
                                    preferences.setBankIconStyle(style)
                                },
                            )
                        }
                    }
                }
                ListSection(title = "Card numbers on accounts") {
                    item {
                        ListRow(
                            headline = "Card number on an account",
                            supporting = "A card account can keep the first 6 and last 4 digits of its number, so a shared receipt is put on " +
                                "the right card. Edit the account in the Wallet and type the number: only those ten digits are kept, " +
                                "never the whole number.",
                            leading = { Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingMaxLines = 5,
                        )
                    }
                }
                if (cardsOn) {
                    ListSection(title = "Motion") {
                        item {
                            SwitchRow(
                                headline = "Card tilt",
                                supporting = "Cards lean a few degrees as you move your phone. Stays flat when animations are turned off in system settings.",
                                checked = prefs.cardTiltEnabled,
                                onCheckedChange = { on ->
                                    haptics.selection()
                                    preferences.setCardTiltEnabled(on)
                                },
                            )
                        }
                    }
                    ListSection(
                        title = "Clear copied card details after",
                        footer = "Applies to the number, cardholder name, expiry and CVV you copy from a card. Copied details are marked sensitive, so keyboards and clipboard previews hide them.",
                        modifier = Modifier.selectableGroup(),
                    ) {
                        for (timeout in ClipboardTimeout.entries) {
                            item {
                                RadioRow(
                                    headline = timeout.label,
                                    supporting = timeout.description,
                                    selected = prefs.clipboardTimeout == timeout,
                                    onClick = {
                                        haptics.selection()
                                        preferences.setClipboardTimeout(timeout)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
