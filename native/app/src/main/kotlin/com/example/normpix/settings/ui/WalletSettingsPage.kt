package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.rounded.SaveAlt
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
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.CardLimitWarning
import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.settings.WalletCardActionStyle
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.OptionSheet
import com.example.normpix.ui.components.OptionSheetItem
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Settings > Wallet & Cards: whether NormPix keeps cards at all and whether it keeps their CVVs (both delete what they cover,
 * after asking - see [CardsSettingsFlows]), and how saved cards behave in Wallet. With Store cards off, everything about saved
 * cards leaves the page. How the Wallet looks - the layouts of accounts and cards, bank logos, the cards' tilt - is with the
 * rest of how NormPix looks, in Appearance (Layouts, Icons and Effects).
 */
/** The switch that lets the phone's autofill service see the card form (and so offer Scan card above the keyboard). */
const val KEYBOARD_CARD_FILL_TITLE = "Keyboard card fill"

/** The row that sets how much of a credit card's limit may be used before NormPix says so (and its sheet's title). */
const val CARD_LIMIT_WARNING_TITLE = "Card limit warning"

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

    /** Asks how much of a credit card's limit may be used before NormPix says so (Off, 30%, 50%, 75% or 90%). */
    fun chooseLimitWarning() {
        scope.launch {
            val current = prefs.cardLimitWarning
            val chosen = overlays.sheet<CardLimitWarning> { dismiss ->
                OptionSheet(
                    title = CARD_LIMIT_WARNING_TITLE,
                    subtitle = "Tell me when a credit card has used this much of its limit",
                    options = CardLimitWarning.entries.map { OptionSheetItem(it, it.label, it.description) },
                    selected = setOf(current),
                    onSelect = { dismiss(it) },
                )
            } ?: return@launch
            if (chosen != current) {
                haptics.selection()
                preferences.setCardLimitWarning(chosen)
            }
        }
    }

    @Composable
    fun TransferRow(id: String, icon: ImageVector, emoji: String, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
        ListRow(
            headline = title,
            supporting = subtitle,
            enabled = enabled && busy == null,
            onClick = onClick,
            leading = {
                SettingsLeadingIcon(icon, emoji, tint = MaterialTheme.colorScheme.onSurfaceVariant, busy = busy == id)
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
                        item {
                            // Off by default: the card form is then hidden from the phone's autofill service altogether.
                            SwitchRow(
                                headline = KEYBOARD_CARD_FILL_TITLE,
                                supporting = if (prefs.cardKeyboardFill) {
                                    "On: your autofill service can offer a saved card or Scan card above the keyboard. What you " +
                                        "pick or scan passes through it; NormPix never asks it to save a card"
                                } else {
                                    "Off: the card form is hidden from your phone's autofill service. Scan card on the form " +
                                        "works either way, on this device"
                                },
                                checked = prefs.cardKeyboardFill,
                                onCheckedChange = { on ->
                                    haptics.selection()
                                    preferences.setCardKeyboardFill(on)
                                },
                            )
                        }
                    }
                }
                if (cardsOn) {
                    ListSection(
                        title = "Export and import",
                        footer = "Your saved cards, and your loyalty and gift cards, apart from the finance backup. The file holds each card in full, so it is " +
                            "always locked with a password you choose. Importing adds cards; any already saved are kept as they are" +
                            if (prefs.cardCvvEnabled) "." else ", without their CVVs while Save CVV is off.",
                    ) {
                        item {
                            TransferRow("export", Icons.Rounded.SaveAlt, SettingsRowEmoji.EXPORT, "Export cards", "A password-protected file of your saved cards and loyalty cards") {
                                run("export") { transfer.export() }
                            }
                        }
                        item {
                            TransferRow(
                                "import",
                                Icons.Outlined.FileOpen,
                                SettingsRowEmoji.IMPORT,
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
                // How the Wallet looks is with the rest of how NormPix looks; one row leads there rather than the choices sitting twice.
                ListSection(title = "Look") {
                    item {
                        ListRow(
                            headline = "Accounts and cards layout",
                            supporting = "In Appearance > Layouts, beside Home's. Bank logos and card tilt are in Appearance too",
                            leading = { SettingsLeadingIcon(Icons.Outlined.ViewCarousel, SettingsRowEmoji.FILE_BOX, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { navigator.openAppearance(AppearanceSection.Layouts) },
                        )
                    }
                }
                // About a credit card account's own balance and limit, not the saved cards: it is here with Store cards off too.
                ListSection(
                    title = "Credit card limit",
                    footer = "A card is listed in Notifications while it is at or past this, and drops off as soon as it is paid down.",
                ) {
                    item {
                        ListRow(
                            headline = CARD_LIMIT_WARNING_TITLE,
                            supporting = (if (prefs.cardLimitWarning.on) "Warns at ${prefs.cardLimitWarning.label} of a card's limit used. " else "No warnings. ") +
                                "Lenders look at how much of a card's limit is used, and keeping it under 30% is the usual advice.",
                            leading = { SettingsLeadingIcon(Icons.Outlined.CreditScore, SettingsRowEmoji.SPREADSHEET, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingMaxLines = 5,
                            onClick = { chooseLimitWarning() },
                        )
                    }
                }
                ListSection(title = "Card numbers on accounts") {
                    item {
                        ListRow(
                            headline = "Card number on an account",
                            supporting = "A card account can keep the first 6 and last 4 digits of its number, so a shared receipt is put on " +
                                "the right card. Edit the account in the Wallet and type the number: only those ten digits are kept, " +
                                "never the whole number.",
                            leading = { SettingsLeadingIcon(Icons.Outlined.CreditCard, SettingsRowEmoji.CARD, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            supportingMaxLines = 5,
                        )
                    }
                }
                if (cardsOn) {
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
