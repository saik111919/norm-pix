package com.example.normpix.wallet.expiry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.BannerStack
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.StatusTone

/** "11/26", the way the card prints it. */
fun CardExpiryState.printed(): String = "%02d/%02d".format(expiry.monthValue, expiry.year % 100)

fun CardExpiryState.title(): String = if (expired) "This card has expired" else "Expires soon"

/** When the card's last month is, from [now]'s month: "in 3 months", "next month", "this month", "last month", "2 months ago". */
fun CardExpiryState.relativeTo(now: java.time.YearMonth = java.time.YearMonth.now()): String =
    when (val months = java.time.temporal.ChronoUnit.MONTHS.between(now, expiry)) {
        0L -> "this month"
        1L -> "next month"
        -1L -> "last month"
        else -> if (months > 1) "in $months months" else "${-months} months ago"
    }

/**
 * The banner for one card whose expiry needs saying - the one design the Home page's Needs attention and the Wallet page both
 * use (2026-11-07, owner: the Wallet's own notice, a plain tonal box with text buttons, did not match the banners): the status
 * icon and "Expires soon" / "This card has expired", the card, a "Valid thru" panel with how far off that is, and the one way
 * out ("Mark as done", with "Turn on" before it when the system won't show notifications - then this banner is the only
 * reminder there is, and says so).
 */
fun cardExpiryBanner(
    key: String,
    card: String,
    state: CardExpiryState,
    notificationsOn: Boolean,
    onMarkDone: () -> Unit,
    onTurnOnNotifications: () -> Unit,
    onOpen: () -> Unit,
): Banner = Banner(
    key = key,
    tone = StatusTone.Warning,
    icon = if (state.expired) Icons.Outlined.EventBusy else Icons.Outlined.Schedule,
    eyebrow = state.title(),
    title = card,
    message = if (notificationsOn) "Reminders are on" else "Notifications are off",
    figure = BannerFigure(title = "Valid thru", figure = state.printed(), note = state.relativeTo()),
    primary = StatusAction("Mark as done", onClick = onMarkDone),
    secondary = if (notificationsOn) null else StatusAction("Turn on", onClick = onTurnOnNotifications),
    onOpen = onOpen,
)

/**
 * Sits on top of the one card it is about: [cardExpiryBanner] as a stack of one, so it is the same banner as on Home. Pressing
 * it opens the card ([onOpen]).
 */
@Composable
fun CardExpiryNotice(
    state: CardExpiryState,
    card: String,
    notificationsOn: Boolean,
    onMarkDone: () -> Unit,
    onTurnOnNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    val banner = cardExpiryBanner("card-expiry", card, state, notificationsOn, onMarkDone, onTurnOnNotifications, onOpen)
    BannerStack(listOf(banner), rememberDeckStackState(), modifier)
}
