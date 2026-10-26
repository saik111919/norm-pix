package com.example.normpix.testing

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.ui.ReceiptSharer
import com.example.normpix.AppContainer
import com.example.normpix.about.GuideTopic
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionPictures
import com.example.normpix.finance.share.NoSharedPictureReader
import com.example.normpix.finance.share.SharedPictureReader
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.LocalShellState
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.ProvideAppEnvironment
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.navigation.ShellState
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.updates.AppUpdater
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.CardVaultException
import com.example.normpix.wallet.CardsController
import com.example.normpix.wallet.RegisteredTags
import com.example.normpix.wallet.StoredCard
import com.example.normpix.wallet.clipboard.SecureClipboard
import com.example.normpix.wallet.expiry.CardExpiryTracker
import com.example.normpix.settings.ClipboardTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** A navigator that only remembers where it was asked to go. */
class RecordingNavigator : AppNavigator {
    val settingsPages = mutableListOf<SettingsPage?>()
    val appearanceSections = mutableListOf<AppearanceSection>()
    val plansSections = mutableListOf<PlanSection?>()
    val categoryHistories = mutableListOf<Pair<Long, MonthKey>>()
    val accountHistories = mutableListOf<Long>()
    val people = mutableListOf<Long>()
    var peopleOpened = 0
    var profileOpened = 0
    var backs = 0

    override fun back() {
        backs++
    }
    override fun openCategories() {}
    override fun openBudgetHistory() {}
    override fun openPeople() {
        peopleOpened++
    }
    override fun openPerson(personId: Long) {
        people += personId
    }
    override fun openCategoryHistory(categoryId: Long, month: MonthKey) {
        categoryHistories += categoryId to month
    }
    override fun openAccountHistory(accountId: Long) {
        accountHistories += accountId
    }
    override fun openSettings(page: SettingsPage?) {
        settingsPages += page
    }
    override fun openAppearance(section: AppearanceSection) {
        appearanceSections += section
    }
    override fun openProfile() {
        profileOpened++
    }
    override fun openSearch() {}
    override fun openReminderAlert(reminderId: Long) {}

    var updatesOpened = 0

    override fun openUpdates() {
        updatesOpened++
    }

    var changelogOpened = 0

    override fun openChangelog() {
        changelogOpened++
    }

    var aboutOpened = 0
    val guides = mutableListOf<GuideTopic>()

    override fun openAbout() {
        aboutOpened++
    }

    override fun openGuide(topic: GuideTopic) {
        guides += topic
    }
    val destinations = mutableListOf<AppDestination>()

    override fun go(destination: AppDestination) {
        destinations += destination
    }
    var transactionsOpened = 0

    override fun openTransactions() {
        transactionsOpened++
    }
    override fun openPlans(section: PlanSection?) {
        plansSections += section
    }
}

/** A clipboard that remembers what it was asked to copy, so a test can check exactly what a tap would have put there. */
class RecordingClipboard(private val works: Boolean = true) : SecureClipboard {
    data class Copy(val label: String, val value: String, val timeout: ClipboardTimeout)

    val copies = mutableListOf<Copy>()

    override fun copy(label: String, value: String, timeout: ClipboardTimeout): Boolean {
        if (works) copies += Copy(label, value, timeout)
        return works
    }

    override fun reconcile() = Unit

    /** How many times the copied card detail was asked to come off the clipboard. */
    var clears = 0
        private set

    override fun clear() {
        clears++
    }
}

/** A share sheet that remembers what it was asked to offer, so a test can see exactly what would have been sent. */
class RecordingReceiptSharer(private val works: Boolean = true) : ReceiptSharer {
    class Shared(val fileName: String, val png: ByteArray)

    val shared = mutableListOf<Shared>()

    override suspend fun shareImage(png: ByteArray, fileName: String): Boolean {
        if (works) shared += Shared(fileName, png)
        return works
    }
}

class InMemoryVault(var cards: List<StoredCard> = emptyList()) : CardVault {
    /** Make [clear] fail the way a vault file that can't be deleted does; the cards stay where they are. */
    var failToClear = false

    /** Make [saveCards] fail, so a test can see nothing is half-written. */
    var failToSave = false

    override suspend fun loadCards(): List<StoredCard> = cards
    override suspend fun saveCards(cards: List<StoredCard>) {
        if (failToSave) throw CardVaultException("The vault could not be written.")
        this.cards = cards
    }

    override suspend fun clear() {
        if (failToClear) throw CardVaultException("The vault could not be erased.")
        cards = emptyList()
    }
}

/**
 * Everything a Compose test needs: an [AppContainer] on in-memory databases and a throwaway preference
 * file, plus [Content] that wraps a screen in the theme and the app's composition locals.
 */
class TestEnvironment(
    prefsName: String = "test_env_prefs",
    val vault: InMemoryVault = InMemoryVault(),
    val clipboard: RecordingClipboard = RecordingClipboard(),
    val expiry: CardExpiryTracker = CardExpiryTracker.inert(),
    val nfc: FakeNfcTagReveal = FakeNfcTagReveal(),
    val registeredTags: RegisteredTags = RegisteredTags(),
    val receipts: RecordingReceiptSharer = RecordingReceiptSharer(),
    /** What reads the text in pictures (ML Kit doesn't run under Robolectric). */
    val pictures: SharedPictureReader = NoSharedPictureReader,
    /** Checks for and installs updates; none unless a test brings one. */
    val updates: AppUpdater? = null,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val financeDb = FinanceDatabase(context, name = null)
    private val remindersDb = RemindersDatabase(context, name = null)

    // The databases are read and written right where they are called, on the test's own thread, never on a thread of their
    // own. A read finishing on another thread hands its result back to Compose's test dispatcher across threads, and under
    // Robolectric that hand-off races the test: now and then the result was never delivered (Home stayed on "Loading your
    // month" with the database idle) or the screen's next frame ran on the database's thread, which Compose rejects. Not
    // using the app's process-wide threads also means a job one test leaves behind can't hold up the tests after it.
    private val inPlace = Dispatchers.Unconfined
    val prefsStore = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    val navigator = RecordingNavigator()
    val shell = ShellState()
    val container: AppContainer

    init {
        container = AppContainer(
            preferences = AppPreferences(prefsStore),
            finance = FinanceController(
                FinanceRepository(financeDb, dispatcher = inPlace),
                TransactionPictures(context),
            ),
            reminders = RemindersController(
                ReminderDao(remindersDb, dispatcher = inPlace),
                scheduler = NoOpReminderScheduler,
            ),
            vault = vault,
            cards = CardsController(vault),
            clipboard = clipboard,
            expiry = expiry,
            nfc = nfc,
            registeredTags = registeredTags,
            receipts = receipts,
            sharedPictures = pictures,
            updates = updates,
        )
        runBlocking {
            container.finance.load()
            container.reminders.load()
            container.cards.load()
        }
    }

    val preferences: AppPreferences get() = container.preferences

    @Composable
    fun Content(content: @Composable () -> Unit) {
        val prefs by container.preferences.state.collectAsState()
        NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent, wallpaperPalette = prefs.wallpaperPalette)) {
            ProvideAppEnvironment(container) {
                CompositionLocalProvider(LocalNavigator provides navigator, LocalShellState provides shell) {
                    content()
                }
            }
        }
    }

    fun close() {
        financeDb.close()
        remindersDb.close()
    }
}
