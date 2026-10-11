package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.settings.CardsLayout
import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.settings.WalletCardActionStyle
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing

/** Settings > Cards: how saved cards look and behave in Wallet. */
@Composable
fun WalletSettingsPage() {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()

    AppSubPage(title = "Cards", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
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
