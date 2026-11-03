package com.example.normpix

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import com.example.normpix.widgets.WidgetRequest
import com.example.normpix.widgets.RecentWidget
import android.app.Application
import android.content.Context
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.availabilityAt
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionPictures
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.reminders.AlarmReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.ReminderNotifications
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.storage.EncryptedDatabases
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
import com.example.normpix.updates.AppUpdater
import com.example.normpix.updates.UpdateSchedule
import com.example.normpix.updates.updateCheckPlan
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /**
     * Checks GitHub for a newer release and installs it with the user's say-so (or, in the background, as far as they chose);
     * null where nothing is updated (tests).
     */
    val updates: AppUpdater? = null,
    /**
     * What a home-screen widget asked for (add a transaction, show one, open Activity), done by the UI once the app is
     * unlocked, then cleared. Shared with the sample set, as the widgets are the app's, whichever data is showing.
     */
    val widgetRequests: MutableStateFlow<WidgetRequest?> = MutableStateFlow(null),
) {
    /** A reminder whose alert screen should open (notification tapped), consumed by the UI. */
    val reminderAlerts = MutableStateFlow<Long?>(null)

    /** Set when a card-expiry notification was tapped: the UI opens Wallet, then clears it. */
    val walletRequests = MutableStateFlow(false)

    /** Set when an update notification was tapped: the UI opens the update page (once unlocked), then clears it. */
    val updateRequests = MutableStateFlow(false)

    /** Set with [updateRequests] by Install on the "ready to install" notification: the update page installs it, too. */
    val updateInstallRequests = MutableStateFlow(false)

    init {
        // What the vault may keep is the user's say (Store cards, Save CVV): every way a card gets in is held to it.
        cards.followSettings(preferences)
    }
}

class NormPixApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Read before anything opens it: whether NormPix has kept a ledger on this phone yet (it is made on the first launch, with
        // the Cash account in it), plain or encrypted. None: a new install, or one whose storage was cleared.
        val hadData = EncryptedDatabases.exists(this, FinanceDatabase.DB_NAME)
        container = buildContainer(this)
        welcomeNewInstall(hadData)
        ReminderNotifications.createChannel(this)
        CardExpiryNotifications.createChannel(this)
        // The card-emulation service is started by the system, not by the UI, so it asks the schedule itself.
        NfcCardOutletService.isAllowed = {
            container.preferences.state.value.availabilityOf(ScheduledFeature.NfcExchange).availabilityAt(LocalDateTime.now()).isOpen
        }
        followCardsForExpiry()
        followCardsForBanks()
        container.updates?.start()
        followUpdateSettings()
        followLedgerForWidgets()
    }

    /**
     * A new install gets the welcome tour once, and so does one whose storage was cleared ([hadData] false). An update of an
     * install that already had NormPix does not (it is told apart by the package's first install being the same moment as its last
     * update, and by its ledger already being there) - and is marked as welcomed, so it never is.
     */
    private fun welcomeNewInstall(hadData: Boolean) {
        val (first, last) = runCatching { packageManager.getPackageInfo(packageName, 0).let { it.firstInstallTime to it.lastUpdateTime } }
            .getOrDefault(0L to 0L)
        welcomeNewInstall(container.preferences, first, last, hadData)
    }

    /**
     * The Recent widget shows the ledger as the app does, so it is drawn again whenever the ledger changes or how it is
     * shown does (amounts hidden, the app lock) - half a second after the last of a burst of changes.
     */
    private fun followLedgerForWidgets() {
        val container = container
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(container.finance.state, container.preferences.state) { finance, prefs ->
                listOf(finance.revision, prefs.amountsHidden, prefs.walletLockEnabled, prefs.appLockScope)
            }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest {
                    delay(500)
                    RecentWidget.refreshAll(this@NormPixApplication)
                }
        }
    }

    /**
     * The check in the background follows Settings > Automatic updates (whether, how often, at what time, and on what network):
     * scheduled, changed or cancelled with it, and set again at every start - which a run at a set time relies on, as it
     * waits for one time only.
     */
    private fun followUpdateSettings() {
        val updater = container.updates?.takeIf { it.enabled } ?: return
        val preferences = container.preferences
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            preferences.state
                .map { it.updateCheckPlan() }
                .distinctUntilChanged()
                .collect { plan -> UpdateSchedule.sync(this@NormPixApplication, plan, updater.lastCheckAt) }
        }
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
            val lock = AppLockService()
            // The ledger and the reminders are encrypted on the phone (each moved over from its plain file the first time it opens).
            val databases = EncryptedDatabases.forApp(app)
            return AppContainer(
                preferences = AppPreferences(prefs),
                finance = FinanceController(FinanceRepository(FinanceDatabase(app, engine = databases)), TransactionPictures(app)),
                reminders = RemindersController(ReminderDao(RemindersDatabase(app, engine = databases)), alarms),
                vault = FileCardVault(File(app.filesDir, "card_vault.bin")),
                lock = lock,
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
                updates = AppUpdater.forApp(app, host, lock),
                expiry = CardExpiryTracker(
                    store = PrefsCardExpiryStore(app.getSharedPreferences("normpix_card_expiry", Context.MODE_PRIVATE)),
                    scheduler = AlarmExpiryScheduler(app),
                    notifier = SystemExpiryNotifier(app),
                ),
            )
        }
    }
}

/**
 * Settles whether the welcome tour is to be shown, once: a new install (its first install and its last update are the same moment) is
 * welcomed, and so is an app with nothing of its own on the phone yet ([hadData] false: its storage was cleared, which leaves the
 * install's times as an update's - the owner found no tour after that, 2026-11-01); an update of an install that already had NormPix
 * - or a phone that will not say - is not, and is marked as welcomed so it never is. A decision already made is left as it is.
 */
internal fun welcomeNewInstall(preferences: AppPreferences, firstInstallTime: Long, lastUpdateTime: Long, hadData: Boolean = true) {
    if (preferences.onboardingDecided()) return
    val newInstall = !hadData || (firstInstallTime > 0 && firstInstallTime == lastUpdateTime)
    if (newInstall) preferences.startOnboarding() else preferences.finishOnboarding()
}
