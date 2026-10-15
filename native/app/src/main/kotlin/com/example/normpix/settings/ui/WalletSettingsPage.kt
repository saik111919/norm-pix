package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
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

/** Settings > Cards: how saved cards look and behave in Wallet. */
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
    /** The export or import in progress, if any (the other waits). */
    var busy by remember { mutableStateOf<String?>(null) }

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
    fun TransferRow(id: String, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
        ListRow(
            headline = title,
            supporting = subtitle,
            enabled = busy == null,
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

    AppSubPage(title = "Cards", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                ListSection(
                    title = "Export and import",
                    footer = "Only your saved cards, apart from the finance backup. The file holds each card in full, so it is " +
                        "always locked with a password you choose. Importing adds cards; any already saved are kept as they are.",
                ) {
                    item {
                        TransferRow("export", Icons.Rounded.SaveAlt, "Export cards", "A password-protected file of your saved cards") {
                            run("export") { transfer.export() }
                        }
                    }
                    item {
                        TransferRow("import", Icons.Outlined.FileOpen, "Import cards", "From a NormPix cards file") {
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
