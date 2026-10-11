package com.example.normpix

import android.app.Application
import android.content.Context
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.availabilityAt
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.reminders.AlarmReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import com.example.normpix.wallet.CardBankLinker
import com.example.normpix.wallet.CardScanner
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.CardsController
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.FileCardVault
import com.example.normpix.wallet.KeystoreKeyProvider
import com.example.normpix.wallet.NfcTagReveal
import com.example.normpix.wallet.PrefsTagStore
import com.example.normpix.wallet.ReaderModeNfcTagReveal
import com.example.normpix.wallet.RegisteredTags
import com.example.normpix.finance.ui.AndroidReceiptSharer
import com.example.normpix.finance.ui.NoReceiptSharer
import com.example.normpix.finance.ui.ReceiptSharer
import com.example.normpix.finance.share.MlKitSharedPictureReader
import com.example.normpix.finance.share.NoSharedPictureReader
import com.example.normpix.finance.share.SharedPictureReader
import com.example.normpix.finance.share.SharedTextInbox
import com.example.normpix.wallet.clipboard.NoClipboard
import com.example.normpix.wallet.clipboard.SecureClipboard
import com.example.normpix.wallet.clipboard.androidSecureClipboard
import com.example.normpix.wallet.expiry.AlarmExpiryScheduler
import com.example.normpix.wallet.expiry.CardExpiryNotifications
import com.example.normpix.wallet.expiry.CardExpiryTracker
import com.example.normpix.wallet.expiry.PrefsCardExpiryStore
import com.example.normpix.wallet.expiry.SystemExpiryNotifier
import com.example.normpix.wallet.nfc.NfcCardOutletService
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The app's long-lived objects, created once and handed to the UI. Plain
 * constructor injection: tests build their own container with in-memory
 * databases instead of the on-device ones.
 */
class AppContainer(
    val preferences: AppPreferences,
    val finance: FinanceController,
    val reminders: RemindersController,
    val vault: CardVault,
    val cards: CardsController = CardsController(vault),
    val lock: AppLockService = AppLockService(),
    val host: ActivityHost = ActivityHost(),
    val nfc: NfcTagReveal = ReaderModeNfcTagReveal(host),
    /** The NFC tags the user registered as theirs for revealing cards (nothing survives a restart in tests). */
    val registeredTags: RegisteredTags = RegisteredTags(),
    /** Null where no camera/OCR is available (tests). */
    val scanner: CardScanner? = null,
    /** Null where alarms are not scheduled (tests). */
    val alarms: AlarmReminderScheduler? = null,
    /** Copies card details to the clipboard and clears them again; copies nothing where there is no clipboard (tests). */
    val clipboard: SecureClipboard = NoClipboard,
    /** Each card's expiry cycle; inert (no alarms, no notifications) in tests and for the sample data. */
    val expiry: CardExpiryTracker = CardExpiryTracker.inert(),
    /** Hands a rendered receipt to the system share sheet; shares nothing where there is none (tests). */
    val receipts: ReceiptSharer = NoReceiptSharer,
    /** Text other apps shared with NormPix, waiting to be opened as a transaction draft (memory only). */
    val sharedText: SharedTextInbox = SharedTextInbox(),
    /** Reads the text of a shared picture on the device; reads nothing where there is no recognizer (tests). */
    val sharedPictures: SharedPictureReader = NoSharedPictureReader,
) {
    /** A reminder whose alert screen should open (notification tapped), consumed by the UI. */
    val reminderAlerts = MutableStateFlow<Long?>(null)

    /** Set when a card-expiry notification was tapped: the UI opens Wallet, then clears it. */
    val walletRequests = MutableStateFlow(false)
}

class NormPixApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = buildContainer(this)
        ReminderNotifications.createChannel(this)
        CardExpiryNotifications.createChannel(this)
        // The card-emulation service is started by the system, not by the UI, so it asks the schedule itself.
        NfcCardOutletService.isAllowed = {
            container.preferences.state.value.availabilityOf(ScheduledFeature.NfcExchange).availabilityAt(LocalDateTime.now()).isOpen
        }
        followCardsForExpiry()
        followCardsForBanks()
    }

    /**
     * Expiry reminders must work without Wallet ever being opened in this process, so the card list is read once
     * at start and the tracker follows every change to it. Only a successfully loaded list is passed on: a failed
     * read must never look like "no cards" and cancel every reminder.
     */
    private fun followCardsForExpiry() {
        val cards = container.cards
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (cards.state.value is CardsState.Loading) cards.load()
            cards.state.collect { state -> if (state is CardsState.Ready) container.expiry.sync(state.cards) }
        }
    }

    /**
     * Cards and accounts share their banks, so every card has to name one: once the finance data is loaded, each card
     * without a (known) bank is linked to the bank its name stands for. Runs again whenever either side changes, and
     * does nothing when there is nothing to link.
     */
    private fun followCardsForBanks() {
        val container = container
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.finance.state.first { it.status == FinanceStatus.Ready }
            val linker = CardBankLinker(container.cards, container.finance)
            // collect, not collectLatest: a link in progress is a vault write and must not be cancelled half way.
            combine(container.cards.state, container.finance.state) { cards, finance -> cards to finance.revision }
                .collect { (cards, _) -> if (cards is CardsState.Ready) linker.link() }
        }
    }

    companion object {
        fun buildContainer(context: Context): AppContainer {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("normpix_preferences", Context.MODE_PRIVATE)
            val alarms = AlarmReminderScheduler(app)
            val host = ActivityHost()
            return AppContainer(
                preferences = AppPreferences(prefs),
                finance = FinanceController(FinanceRepository(FinanceDatabase(app))),
                reminders = RemindersController(ReminderDao(RemindersDatabase(app)), alarms),
                vault = FileCardVault(File(app.filesDir, "card_vault.bin")),
                host = host,
                nfc = ReaderModeNfcTagReveal(host),
                registeredTags = RegisteredTags(
                    store = PrefsTagStore(app.getSharedPreferences("normpix_nfc_tags", Context.MODE_PRIVATE)),
                    keys = KeystoreKeyProvider(alias = "normpix_nfc_tags_key_v1"),
                ),
                scanner = CardScanner(app),
                alarms = alarms,
                clipboard = androidSecureClipboard(app),
                receipts = AndroidReceiptSharer(app, host),
                sharedPictures = MlKitSharedPictureReader(app),
                expiry = CardExpiryTracker(
                    store = PrefsCardExpiryStore(app.getSharedPreferences("normpix_card_expiry", Context.MODE_PRIVATE)),
                    scheduler = AlarmExpiryScheduler(app),
                    notifier = SystemExpiryNotifier(app),
                ),
            )
        }
    }
}
