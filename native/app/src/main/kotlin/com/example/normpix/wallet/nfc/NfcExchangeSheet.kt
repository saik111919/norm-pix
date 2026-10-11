package com.example.normpix.wallet.nfc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.normpix.availability.AvailabilityUi
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.rememberAvailability
import com.example.normpix.availability.ui.FeatureUnavailablePanel
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.sectionTransition
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.wallet.AppLockService
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.StoredCard
import kotlinx.coroutines.launch

/**
 * The two modes of the exchange: this phone receiving a card (the Inlet) or sharing one (the Outlet).
 * [label] is the tab; [modeName] is the technical name, shown as the mode's headline.
 */
enum class ExchangeTab(val label: String, val modeName: String) {
    Inlet("Receive", "NFC Inlet"),
    Outlet("Share", "NFC Outlet"),
}

/** The two exchange tabs as [AppPrimaryTabs] entries. */
private val ExchangeTabs = listOf(
    AppTab(ExchangeTab.Inlet.label, Icons.Outlined.Download),
    AppTab(ExchangeTab.Outlet.label, Icons.Outlined.Upload),
)

/**
 * Bottom sheet for two-way NFC card exchange: **Receive** (NFC Inlet) and **Share** (NFC Outlet) tabs.
 *
 * Only while [ScheduledFeature.NfcExchange] is available (Settings -> Availability & Schedules) does
 * anything listen or broadcast; outside it the sheet says why and where to change it. The check is by
 * composition: the reader and the card emulation start in the exchange's effects, so a closed window
 * means they are never started - and when the window closes while the sheet is open they are torn down.
 *
 * A tapped bank card is identified and handed to [onAddCardDetails], since its details can't be read
 * over NFC (see `NfcTagReader.kt`); a null there means "enter a card by hand" (a phone without NFC).
 */
@Composable
fun NfcExchangeSheet(
    initialTab: ExchangeTab = ExchangeTab.Inlet,
    initialCard: StoredCard? = null,
    onDismiss: () -> Unit,
    onAddCardDetails: (PaymentCardInfo?) -> Unit = {},
    /** Takes the user to Settings -> Availability & Schedules (the caller closes the sheet and navigates); null hides the shortcut. */
    onOpenSchedule: (() -> Unit)? = null,
) {
    val availability = rememberAvailability(ScheduledFeature.NfcExchange)
    if (availability.isOpen) {
        NfcExchangeContent(availability, initialTab, initialCard, onDismiss, onAddCardDetails)
    } else {
        NfcUnavailable(availability, onClose = onDismiss, onOpenSchedule = onOpenSchedule)
    }
}

/** "NFC is unavailable right now." with the reason and a way to the schedule. */
@Composable
private fun NfcUnavailable(
    availability: AvailabilityUi,
    onClose: () -> Unit,
    onOpenSchedule: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    FeatureUnavailablePanel(
        availability = availability,
        title = "NFC is unavailable right now.",
        onOpenSchedule = onOpenSchedule,
        onClose = onClose,
        modifier = Modifier.padding(AppSpacing.page).padding(bottom = AppSpacing.xl),
        extra = extra,
    )
}

@Composable
private fun NfcExchangeContent(
    availability: AvailabilityUi,
    initialTab: ExchangeTab,
    initialCard: StoredCard?,
    onDismiss: () -> Unit,
    onAddCardDetails: (PaymentCardInfo?) -> Unit,
) {
    val container = LocalAppContainer.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exchange = remember { NfcExchangeController(container.host) }
    val area = remember { exchange.phoneArea() }
    var currentTab by remember { mutableStateOf(initialTab) }

    val cardsState by container.cards.state.collectAsState()
    val cards = (cardsState as? CardsState.Ready)?.cards.orEmpty()
    var selectedCard by remember {
        mutableStateOf(initialCard ?: cards.firstOrNull())
    }

    val inletState by exchange.inletState.collectAsState()
    val outletState by exchange.outletState.collectAsState()

    fun startInlet() {
        exchange.startInlet(isAlreadySaved = { received -> container.cards.cards.any { it.sameCardAs(received) } }) { receivedCard ->
            scope.launch { container.cards.add(receivedCard) }
        }
    }

    // Only the selected tab's mode runs; leaving the tab (or the sheet) turns it off.
    when (currentTab) {
        ExchangeTab.Inlet -> InletSession(exchange, start = ::startInlet)
        ExchangeTab.Outlet -> DisposableEffect(selectedCard) {
            selectedCard?.let(exchange::startOutlet)
            onDispose { exchange.stopOutlet() }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.page)
            .padding(bottom = AppSpacing.xl),
    ) {
        SheetHeader(
            title = "NFC Card Exchange",
            // Only worth saying when a schedule is in force: "Available now until 8:00 PM."
            subtitle = availability.message.takeIf { availability.availability.closesAtOrNull() != null },
        )

        Spacer(Modifier.height(AppSpacing.sm))

        AppPrimaryTabs(
            tabs = ExchangeTabs,
            selectedIndex = currentTab.ordinal,
            onSelect = { currentTab = ExchangeTab.entries[it] },
        )

        Spacer(Modifier.height(AppSpacing.lg))

        val reduced = rememberReducedMotion()
        val shift = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.roundToPx() }
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { sectionTransition(forward = targetState.ordinal > initialState.ordinal, reduced = reduced, shiftPx = shift) },
            label = "nfc_exchange_mode",
        ) { tab ->
            Column {
                when (tab) {
                    ExchangeTab.Inlet -> {
                        val ready = inletState is NfcInletState.Idle || inletState is NfcInletState.Active
                        if (ready) {
                            ModeStatus(ExchangeTab.Inlet, "This device is ready to receive NFC data.")
                            Spacer(Modifier.height(AppSpacing.md))
                        }
                        NfcTapPanel(
                            state = inletState,
                            area = area,
                            acceptsPhones = true,
                            onRetry = ::startInlet,
                            onOpenSettings = { openNfcSettings(context, container.lock) },
                            onUsePaymentCard = { onAddCardDetails(it) },
                            onEnterManually = { onAddCardDetails(null) },
                        )
                        Spacer(Modifier.height(AppSpacing.lg))
                        InletFooter(done = inletState is NfcInletState.Success, onDone = onDismiss)
                    }
                    ExchangeTab.Outlet -> OutletContent(
                        cards = cards,
                        selectedCard = selectedCard,
                        onSelectCard = { selectedCard = it },
                        outletState = outletState,
                        onSwitchToInlet = {
                            haptics.selection()
                            currentTab = ExchangeTab.Inlet
                        },
                        onDone = onDismiss,
                    )
                }
            }
        }
    }
}

/**
 * What this mode is and what the phone is doing right now, above the instructions: "NFC Inlet" and
 * "This device is ready to receive NFC data." A live region, so TalkBack announces it when it appears.
 */
@Composable
private fun ModeStatus(tab: ExchangeTab, message: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(scheme.secondaryContainer, AppRadius.rLg)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(tab.modeName, style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
        Text(message, style = MaterialTheme.typography.titleSmall, color = scheme.onSecondaryContainer)
    }
}

private fun com.example.normpix.availability.Availability.closesAtOrNull() =
    (this as? com.example.normpix.availability.Availability.Open)?.closesAt

/**
 * Just the Inlet, for filling the Add card form by tap. A card shared from another phone comes back
 * whole; a bank card comes back identified only. Null: closed, or "enter it by hand".
 */
@Composable
fun NfcCardTapSheet(onResult: (NfcTapResult?) -> Unit) {
    val availability = rememberAvailability(ScheduledFeature.NfcExchange)
    if (!availability.isOpen) {
        // Typing the details by hand never needs NFC, so it stays on offer.
        NfcUnavailable(availability, onClose = { onResult(null) }, extra = {
            TextButton(onClick = { onResult(null) }) { Text("Enter card details") }
        })
        return
    }
    NfcCardTapContent(onResult)
}

@Composable
private fun NfcCardTapContent(onResult: (NfcTapResult?) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val exchange = remember { NfcExchangeController(container.host) }
    val area = remember { exchange.phoneArea() }
    val state by exchange.inletState.collectAsState()

    fun start() {
        exchange.startInlet(isAlreadySaved = { received -> container.cards.cards.any { it.sameCardAs(received) } }) { card ->
            onResult(NfcTapResult.Shared(card))
        }
    }
    InletSession(exchange, start = ::start)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NfcTapPanel(
            state = state,
            area = area,
            acceptsPhones = true,
            onRetry = ::start,
            onOpenSettings = { openNfcSettings(context, container.lock) },
            onUsePaymentCard = { onResult(NfcTapResult.Payment(it)) },
            onEnterManually = { onResult(null) },
            fillsForm = true,
        )
        Spacer(Modifier.height(AppSpacing.sm))
        TextButton(onClick = { onResult(null) }) { Text("Cancel") }
    }
}

/** What tapping produced for the Add card form. */
sealed interface NfcTapResult {
    /** A whole card, shared from another phone running NormPix. */
    data class Shared(val card: StoredCard) : NfcTapResult

    /** A bank card, identified from its public payment directory only. */
    data class Payment(val info: PaymentCardInfo) : NfcTapResult
}

/**
 * Keeps the Inlet listening while it's on screen: starts it, follows NFC being switched on or off
 * (from settings or quick settings) without the user doing anything, and turns the reader off when
 * the screen goes away. A result already on screen (a received or identified card) is left alone.
 */
@Composable
internal fun InletSession(exchange: NfcExchangeController, start: () -> Unit) {
    val context = LocalContext.current
    val latestStart by rememberUpdatedState(start)
    DisposableEffect(exchange) {
        latestStart()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val adapterState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                if (adapterState != NfcAdapter.STATE_ON && adapterState != NfcAdapter.STATE_OFF) return
                val shown = exchange.inletState.value
                if (shown is NfcInletState.Success || shown is NfcInletState.PaymentCard) return
                latestStart()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            exchange.stopInlet()
        }
    }
}

/** Opens the system NFC switch. Coming back isn't "leaving" NormPix, so the app lock doesn't re-engage. */
internal fun openNfcSettings(context: Context, lock: AppLockService) {
    lock.expectExternalActivity()
    // Some phones have no dedicated NFC screen; their switch lives with the other connections.
    runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
}

@Composable
private fun InletFooter(done: Boolean, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Button(onClick = onDone) { Text("View in Wallet") }
        } else {
            OutlinedButton(onClick = onDone) { Text("Cancel") }
        }
    }
}

@Composable
private fun OutletContent(
    cards: List<StoredCard>,
    selectedCard: StoredCard?,
    onSelectCard: (StoredCard) -> Unit,
    outletState: NfcOutletState?,
    onSwitchToInlet: () -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    if (cards.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.CreditCard,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "No cards available to share",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Add a card first, or switch to Receive to get a card from another phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppSpacing.md),
            )
            Spacer(Modifier.height(AppSpacing.md))
            Button(onClick = onSwitchToInlet) {
                Text("Receive a card instead")
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val currentCard = selectedCard ?: cards.first()

        // Card picker if user has multiple cards
        if (cards.size > 1) {
            Text(
                text = "Select card to share:",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                cards.forEach { card ->
                    val isSelected = card.id == currentCard.id
                    FilledTonalButton(
                        onClick = { onSelectCard(card) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "${card.bankName} ••${card.last4}",
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.md))
        }

        // Active Outlet beaming animation
        val isTransferred = outletState is NfcOutletState.Transferred
        if (isTransferred) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "Card Transferred!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        } else {
            PulsingNfcIcon(
                icon = Icons.Outlined.Sensors,
                tint = scheme.primary,
                bgColor = scheme.primaryContainer,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            ModeStatus(ExchangeTab.Outlet, "This device is ready to share NFC data.")
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "Waiting for another phone. Hold the back of this one against it while it is on Receive.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(AppSpacing.md))
        ReceivedCardPreview(card = currentCard)

        Spacer(Modifier.height(AppSpacing.lg))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onDone) {
                Text(if (isTransferred) "Done" else "Close")
            }
        }
    }
}

@Composable
internal fun ReceivedCardPreview(card: StoredCard) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = AppRadius.rLg,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(scheme.primaryContainer, AppRadius.rSm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${card.bankName} (${card.kind.label})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "•••• •••• •••• ${card.last4}  |  Exp ${card.expiryMonth}/${card.expiryYear}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (card.cardholderName.isNotBlank()) {
                    Text(
                        text = card.cardholderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingNfcIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bgColor: Color,
) {
    val transition = rememberInfiniteTransition(label = "nfcPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    // With animations off, it holds still at its resting size.
    val scale = if (rememberReducedMotion()) 1f else pulse

    Box(
        modifier = Modifier
            .size(96.dp)
            .scale(scale)
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(48.dp),
        )
    }
}
