package com.example.normpix.navigation

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.normpix.reminders.ReminderAlertScreen
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.ui.AppearanceSettingsPage
import com.example.normpix.settings.ui.AvailabilitySettingsPage
import com.example.normpix.settings.ui.DataSettingsPage
import com.example.normpix.settings.ui.FinanceSettingsPage
import com.example.normpix.settings.ui.ProfilePage
import com.example.normpix.settings.ui.SecuritySettingsPage
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.settings.ui.WalletSettingsPage
import com.example.normpix.ui.components.LockGate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.normpix.AppContainer
import com.example.normpix.DummyData
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.AccountHistoryPage
import com.example.normpix.finance.ui.BudgetHistoryPage
import com.example.normpix.finance.ui.CategoriesPage
import com.example.normpix.finance.ui.CategoryHistoryPage
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.LocalCategoryIconStyle
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.LocalReminderLauncher
import com.example.normpix.finance.ui.LocalWalletCards
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.finance.ui.PeoplePage
import com.example.normpix.finance.ui.PersonDetailPage
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.share.ReceiveSharedTransactions
import com.example.normpix.settings.PageAnimationType
import kotlinx.coroutines.launch
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalPermissions
import com.example.normpix.ui.components.rememberPermissionRequester
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.components.SnackbarOverlay
import com.example.normpix.ui.components.rememberWindowBlurAvailable
import com.example.normpix.ui.components.rememberEasedBlur
import com.example.normpix.ui.components.blurredBy
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalSystemReducedMotion
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.ui.theme.isDark
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.ui.theme.rememberSystemReducedMotion

object Routes {
    const val MAIN = "main"
    const val CATEGORIES = "categories"
    const val BUDGET_HISTORY = "budget-history"
    const val PEOPLE = "people"
    const val PERSON = "person/{personId}"
    const val CATEGORY_HISTORY = "category/{categoryId}/{month}"
    const val ACCOUNT_HISTORY = "account/{accountId}"
    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/{page}"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val REMINDER_ALERT = "reminder/{reminderId}"

    fun categoryHistory(categoryId: Long, month: MonthKey) = "category/$categoryId/${month.toInt()}"
    fun accountHistory(accountId: Long) = "account/$accountId"
    fun person(personId: Long) = "person/$personId"
    fun settingsPage(page: SettingsPage) = "settings/${page.name}"
    fun reminderAlert(reminderId: Long) = "reminder/$reminderId"
}

/**
 * Which top-level tab is showing, which view of Plans & Transactions, and
 * which Plans section; survives process death.
 */
class ShellState(index: Int = 0, section: Int = 0, plansTab: Int = 0) {
    var selected by mutableIntStateOf(index)
    var planSection by mutableIntStateOf(section)
    var plansTab by mutableIntStateOf(plansTab)

    /** Whether the transaction history shows everything (true) or one month at a time. Kept here so Home's "See all" can set it. */
    var transactionsAllTime by mutableStateOf(false)

    val destination: AppDestination get() = AppDestination.entries.getOrElse(selected) { AppDestination.Home }
    val section: PlanSection get() = PlanSection.entries.getOrElse(planSection) { PlanSection.Budgets }
    val tab: PlansAndTransactionsTab
        get() = PlansAndTransactionsTab.entries.getOrElse(plansTab) { PlansAndTransactionsTab.Transactions }

    val canStepBackInPlans: Boolean
        get() = destination == AppDestination.PlansAndTransactions && tab == PlansAndTransactionsTab.Plans

    fun stepBackInPlans(): Boolean {
        if (!canStepBackInPlans) return false
        return if (section != PlanSection.Budgets) {
            planSection = PlanSection.Budgets.ordinal
            true
        } else {
            plansTab = PlansAndTransactionsTab.Transactions.ordinal
            true
        }
    }

    companion object {
        val Saver = listSaver<ShellState, Int>(
            save = { listOf(it.selected, it.planSection, it.plansTab) },
            restore = { ShellState(it[0], it[1], it.getOrElse(2) { 0 }) },
        )
    }
}

@Composable
fun rememberShellState(): ShellState = rememberSaveable(saver = ShellState.Saver) { ShellState() }

/** Root of the UI: theme, app-wide services, and the navigation graph. */
@Composable
fun NormPixRoot(container: AppContainer, onDarkChanged: (Boolean) -> Unit = {}) {
    val prefs by container.preferences.state.collectAsStateWithLifecycle()
    // Held above the data, so turning Dummy data on or off keeps you on the screen you were on.
    val nav = rememberNavController()
    val shell = rememberShellState()
    val data = rememberDataContainer(container, prefs.dummyDataEnabled)
    KeepRemindersArmed(container)
    NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent)) {
        val dark = MaterialTheme.colorScheme.isDark
        LaunchedEffect(dark) { onDarkChanged(dark) }
        ProvideAppEnvironment(data) {
            // Rebuilt for each data set, so no screen keeps reading or writing the other one.
            key(data) { AppContent(data, nav, shell, prefs.pageAnimation, sampleData = data !== container) }
        }
    }
}

/**
 * The data the screens use: the user's own [real] container or, while Sample
 * data is on, a fresh in-memory sample set (see [DummyData]).
 * Retains the current container while the in-memory sample set is created in the background,
 * eliminating blank loading screens when toggling.
 */
@Composable
private fun rememberDataContainer(real: AppContainer, dummy: Boolean): AppContainer {
    val context = LocalContext.current
    var activeContainer by remember { mutableStateOf(real) }
    var activeDummy by remember { mutableStateOf<DummyData?>(null) }

    LaunchedEffect(dummy, real) {
        if (!dummy) {
            val old = activeDummy
            activeDummy = null
            activeContainer = real
            old?.close()
        } else {
            val opened = DummyData.open(context, real)
            activeDummy?.close()
            activeDummy = opened
            activeContainer = opened.container
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeDummy?.close()
        }
    }
    return activeContainer
}

/**
 * The user's reminders load at launch, and alarms the OS dropped (update,
 * force-stop) or whose exact-alarm access changed are re-registered whenever
 * the app comes to the foreground - also while Dummy data is shown.
 */
@Composable
private fun KeepRemindersArmed(container: AppContainer) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(container.reminders) {
        runCatching {
            container.reminders.load()
            container.reminders.rearmAll()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch { runCatching { container.reminders.rearmAll() } }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }
val LocalAppPreferences = staticCompositionLocalOf<AppPreferences?> { null }

/** The app-wide services every screen may use. */
@Composable
fun ProvideAppEnvironment(container: AppContainer, content: @Composable () -> Unit) {
    // App-wide flows outlive any one screen. A supervisor scope, so one failed
    // flow (say, a write that hit a full disk) reports and leaves the others running.
    val snackbarsRef = remember { arrayOfNulls<AppSnackbars>(1) }
    val scope = remember {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ ->
                snackbarsRef[0]?.show("Something went wrong. Try again.")
            },
        )
    }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    val overlays = remember { AppOverlays() }
    val snackbars = remember { AppSnackbars(SnackbarHostState(), scope, heldBack = { overlays.isOpen }).also { snackbarsRef[0] = it } }
    val permissions = rememberPermissionRequester()

    // The page under an open sheet or dialog is blurred here, by the app, so it can move in step with the sheet: each
    // sheet reports how much of it is on screen every frame (AppOverlays.backdropBlur) and the blur is that share of
    // the full radius. Only where the system blurs at all - Android 12+, and not switched off by battery saver.
    val prefs by container.preferences.state.collectAsState()
    val blurOn = prefs.sheetBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && rememberWindowBlurAvailable()
    val pageBlur = rememberEasedBlur { if (blurOn) overlays.backdropBlur else 0f }
    val systemReducedMotion = rememberSystemReducedMotion()

    CompositionLocalProvider(
        LocalSystemReducedMotion provides systemReducedMotion,
        LocalAppContainer provides container,
        LocalAppPreferences provides container.preferences,
        LocalPermissions provides permissions,
        LocalReminders provides container.reminders,
        LocalFinance provides container.finance,
        LocalWalletCards provides container.cards,
        LocalOverlays provides overlays,
        LocalSnackbars provides snackbars,
        LocalAppScope provides scope,
        LocalCategoryIconStyle provides prefs.categoryIconStyle,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .blurredBy(pageBlur),
            ) {
                content()
            }
            SnackbarOverlay(snackbars)
            OverlayHost(overlays)
        }
    }
}

@Composable
private fun AppContent(
    container: AppContainer,
    nav: NavHostController,
    shell: ShellState,
    pageAnimation: PageAnimationType,
    sampleData: Boolean,
) {
    val finance = container.finance
    val state = rememberFinanceState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(finance) { finance.load() }
    // Catch recurring payments up whenever the app comes back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (finance.state.value.status == FinanceStatus.Ready) {
            scope.launch { runCatching { finance.refreshRecurring() } }
        }
    }

    when (state.status) {
        FinanceStatus.Loading -> AppLoadingState(Modifier.fillMaxSize(), label = "Opening NormPix")
        FinanceStatus.Failed -> AppErrorState(
            title = "Couldn't open your data",
            message = "Your data is safe on this device. Try again.",
            onRetry = { scope.launch { finance.load() } },
            modifier = Modifier.fillMaxSize(),
        )
        FinanceStatus.Ready -> AppNavigation(container, nav, shell, pageAnimation, sampleData)
    }
}

@Composable
private fun AppNavigation(
    container: AppContainer,
    nav: NavHostController,
    shell: ShellState,
    pageAnimation: PageAnimationType,
    sampleData: Boolean,
) {
    val reduced = rememberReducedMotion()
    val transitions = remember(pageAnimation, reduced) { PageTransitions(pageAnimation, reduced) }
    val navigator = remember(nav, shell) { NavControllerNavigator(nav, shell) }

    val prefs by container.preferences.state.collectAsStateWithLifecycle()
    val lockVersion by container.lock.version.collectAsStateWithLifecycle()
    val appLocked = remember(prefs, lockVersion) { container.lock.isAppLocked(prefs) }
    val walletLocked = remember(prefs, lockVersion) { container.lock.isWalletLocked(prefs) }
    val overlays = LocalOverlays.current
    // Sheets and dialogs float above everything (they're separate windows), so
    // close them when a lock engages instead of leaving a card form over the lock screen.
    LaunchedEffect(appLocked, walletLocked) {
        if (appLocked || walletLocked) overlays.dismissAll()
    }

    // A tapped reminder notification, or a reminder coming due while NormPix is open.
    val pendingAlert by container.reminderAlerts.collectAsStateWithLifecycle()
    LaunchedEffect(pendingAlert, appLocked) {
        val id = pendingAlert ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect // shown once the app is unlocked
        container.reminderAlerts.value = null
        navigator.openReminderAlert(id)
    }
    LaunchedEffect(container.reminders) {
        container.reminders.onDue.collect { reminder -> reminder.id?.let { container.reminderAlerts.value = it } }
    }

    // A tapped card-expiry notification: show Wallet, where each card says what it needs.
    val walletRequested by container.walletRequests.collectAsStateWithLifecycle()
    LaunchedEffect(walletRequested, appLocked) {
        if (!walletRequested || appLocked) return@LaunchedEffect // shown once the app is unlocked
        container.walletRequests.value = false
        navigator.go(AppDestination.Wallet)
    }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalShellState provides shell, LocalPageAnimation provides pageAnimation) {
     // Reminders are one tap from what they are about (an expense's receipt, a repeating payment, an account), so those
     // screens can start one; the flows need the navigator, so they are made here, once it is provided.
     CompositionLocalProvider(LocalReminderLauncher provides rememberReminderFlows()) {
      // Text shared from another app (ShareTransactionActivity) opens as a new-transaction draft, once unlocked.
      ReceiveSharedTransactions(container.sharedText, container.sharedPictures, appLocked = appLocked, sampleData = sampleData)
      // The app's own background behind every page: while one page leaves and another arrives (or a tab is still
      // composing for the first time) it is what shows between them, never the window's - which follows the
      // *system's* light or dark, not the app's.
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavHost(
            navController = nav,
            startDestination = Routes.MAIN,
            // Nothing behind the lock screen is reachable by touch or by TalkBack.
            modifier = if (appLocked) Modifier.clearAndSetSemantics {} else Modifier,
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
        ) {
            composable(Routes.MAIN) { AppShell(container, shell) }
            composable(Routes.CATEGORIES) { CategoriesPage() }
            composable(Routes.BUDGET_HISTORY) { BudgetHistoryPage() }
            composable(Routes.PEOPLE) { PeoplePage() }
            composable(
                Routes.PERSON,
                arguments = listOf(navArgument("personId") { type = NavType.LongType }),
            ) { entry -> PersonDetailPage(entry.arguments!!.getLong("personId")) }
            composable(
                Routes.CATEGORY_HISTORY,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("month") { type = NavType.IntType },
                ),
            ) { entry ->
                CategoryHistoryPage(
                    categoryId = entry.arguments!!.getLong("categoryId"),
                    month = MonthKey.fromInt(entry.arguments!!.getInt("month")),
                )
            }
            composable(
                Routes.ACCOUNT_HISTORY,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry -> AccountHistoryPage(entry.arguments!!.getLong("accountId")) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.SETTINGS_PAGE,
                arguments = listOf(navArgument("page") { type = NavType.StringType }),
            ) { entry ->
                when (SettingsPage.entries.firstOrNull { it.name == entry.arguments?.getString("page") }) {
                    SettingsPage.Appearance -> AppearanceSettingsPage()
                    SettingsPage.Finance -> FinanceSettingsPage()
                    SettingsPage.Data -> DataSettingsPage()
                    SettingsPage.Security -> SecuritySettingsPage()
                    SettingsPage.Wallet -> WalletSettingsPage()
                    SettingsPage.Availability -> AvailabilitySettingsPage()
                    null -> SettingsScreen()
                }
            }
            composable(Routes.PROFILE) { ProfilePage() }
            composable(Routes.SEARCH) { AppSearchPage() }
            composable(
                Routes.REMINDER_ALERT,
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType }),
            ) { entry -> ReminderAlertScreen(entry.arguments!!.getLong("reminderId")) }
        }
        if (appLocked) AppLockScreen(container)
      }
     }
    }
}

/**
 * Full-screen lock shown over everything while the whole app is locked.
 * Prompts for biometrics once on its own; the button retries. Back leaves
 * the app instead of reaching the screens underneath.
 */
@Composable
private fun AppLockScreen(container: AppContainer) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    fun unlock() {
        scope.launch { container.lock.authenticate("Unlock NormPix") }
    }
    LaunchedEffect(Unit) {
        if (container.lock.isDeviceSupported()) unlock()
    }
    BackHandler { activity?.moveTaskToBack(true) }
    // A Surface, so text defaults to onSurface; it also swallows touches meant for the screens underneath.
    androidx.compose.material3.Surface(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        color = MaterialTheme.colorScheme.surface,
    ) {
        LockGate(
            title = "NormPix is locked",
            message = "Unlock with your fingerprint, face or screen lock to open the app.",
            unlockLabel = "Unlock app",
            onUnlock = ::unlock,
        )
    }
}

/** [AppNavigator] backed by a Navigation Compose controller and the shell's tab state. */
class NavControllerNavigator(private val nav: NavHostController, private val shell: ShellState) : AppNavigator {
    override fun back() {
        // Ignore a second tap while the previous pop is still animating.
        if (nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
    }

    override fun openCategories() = nav.navigate(Routes.CATEGORIES)

    override fun openBudgetHistory() = nav.navigate(Routes.BUDGET_HISTORY)

    override fun openPeople() {
        nav.navigate(Routes.PEOPLE) { launchSingleTop = true }
    }

    override fun openPerson(personId: Long) {
        nav.navigate(Routes.person(personId))
    }

    override fun openCategoryHistory(categoryId: Long, month: MonthKey) {
        nav.navigate(Routes.categoryHistory(categoryId, month))
    }

    override fun openAccountHistory(accountId: Long) {
        nav.navigate(Routes.accountHistory(accountId))
    }

    override fun openSettings(page: SettingsPage?) {
        if (page == null && returnTo(Routes.SETTINGS)) return
        nav.navigate(if (page == null) Routes.SETTINGS else Routes.settingsPage(page)) { launchSingleTop = true }
    }

    override fun openProfile() {
        if (returnTo(Routes.PROFILE)) return
        nav.navigate(Routes.PROFILE) { launchSingleTop = true }
    }

    /**
     * Settings and Profile lead to each other. When the page asked for is already under this one, go back to it rather than
     * stacking a second copy, so a few taps between the two never need as many backs to leave.
     */
    private fun returnTo(route: String): Boolean = nav.popBackStack(route, inclusive = false)

    override fun openSearch() {
        nav.navigate(Routes.SEARCH) { launchSingleTop = true }
    }

    override fun openReminderAlert(reminderId: Long) {
        nav.navigate(Routes.reminderAlert(reminderId)) {
            // A newer alert replaces one that's already showing.
            popUpTo(Routes.REMINDER_ALERT) { inclusive = true }
            launchSingleTop = true
        }
    }

    override fun go(destination: AppDestination) {
        nav.popBackStack(Routes.MAIN, inclusive = false)
        shell.selected = destination.ordinal
    }

    override fun openTransactions() {
        shell.transactionsAllTime = true
        shell.plansTab = PlansAndTransactionsTab.Transactions.ordinal
        go(AppDestination.PlansAndTransactions)
    }

    override fun openPlans(section: PlanSection?) {
        shell.plansTab = PlansAndTransactionsTab.Plans.ordinal
        if (section != null) shell.planSection = section.ordinal
        go(AppDestination.PlansAndTransactions)
    }
}

val LocalShellState = staticCompositionLocalOf<ShellState> { error("No ShellState provided") }
