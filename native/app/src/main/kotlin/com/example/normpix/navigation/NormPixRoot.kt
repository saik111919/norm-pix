package com.example.normpix.navigation

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.normpix.reminders.ReminderAlertScreen
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.ui.SettingsDetail
import com.example.normpix.settings.ui.SettingsRoute
import com.example.normpix.ui.components.LockGate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.normpix.AppContainer
import com.example.normpix.DummyData
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.AccountHistoryPage
import com.example.normpix.finance.ui.BudgetHistoryPage
import com.example.normpix.finance.ui.CategoriesPage
import com.example.normpix.finance.ui.CategoryHistoryPage
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.LocalCategoryIconShape
import com.example.normpix.finance.ui.LocalCategoryIconStyle
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.LocalReminderLauncher
import com.example.normpix.finance.ui.LocalWalletCards
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.finance.ui.PeoplePage
import com.example.normpix.finance.ui.PersonDetailPage
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.share.ReceiveSharedTransactions
import com.example.normpix.updates.ui.OfferAppUpdates
import com.example.normpix.settings.PageAnimationType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalPermissions
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberPermissionRequester
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.components.SnackbarOverlay
import com.example.normpix.ui.components.rememberWindowBlurAvailable
import com.example.normpix.ui.components.rememberEasedBlur
import com.example.normpix.ui.components.blurredBy
import com.example.normpix.ui.theme.AppFont
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalRailCollapsed
import com.example.normpix.ui.theme.LocalSystemReducedMotion
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.ui.theme.isDark
import com.example.normpix.ui.theme.rememberCustomFontFile
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.ui.theme.rememberSystemReducedMotion

/** The value of an optional day argument that was not given. */
private const val NO_DAY = -1L

object Routes {
    const val MAIN = "main"
    const val CATEGORIES = "categories"
    const val BUDGET_HISTORY = "budget-history"
    const val PEOPLE = "people"
    const val PERSON = "person/{personId}"
    /** [from] and [to] (epoch days, [to] exclusive) are optional: given, the history shows those days instead of the month. */
    const val CATEGORY_HISTORY = "category/{categoryId}/{month}?from={from}&to={to}"
    const val ACCOUNT_HISTORY = "account/{accountId}"
    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/{page}"
    /** One screen of Settings > Appearance, named by an [AppearanceSection]. */
    const val APPEARANCE_SECTION = "appearance/{section}"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val REMINDER_ALERT = "reminder/{reminderId}"
    const val UPDATE = "update"
    const val CHANGELOG = "changelog"

    fun categoryHistory(categoryId: Long, month: MonthKey) = "category/$categoryId/${month.toInt()}"
    fun categoryHistory(categoryId: Long, range: DateRange) =
        "category/$categoryId/${MonthKey.of(range.start).toInt()}" +
            "?from=${range.start.toLocalDate().toEpochDay()}&to=${range.end.toLocalDate().toEpochDay()}"
    fun accountHistory(accountId: Long) = "account/$accountId"
    fun person(personId: Long) = "person/$personId"
    fun settingsPage(page: SettingsPage) = "settings/${page.name}"
    fun appearanceSection(section: AppearanceSection) = "appearance/${section.name}"
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

/**
 * Root of the UI: theme, app-wide services, and the navigation graph. [showSplash] (a cold start) writes the app's name out
 * first ([LaunchSplash]); nothing pops up over it - an update offered, a shared draft, a reminder's alert, the lock's
 * prompt - until it has gone.
 */
@Composable
fun NormPixRoot(container: AppContainer, showSplash: Boolean = false, onDarkChanged: (Boolean) -> Unit = {}) {
    val prefs by container.preferences.state.collectAsStateWithLifecycle()
    // Held above the data, so turning Dummy data on or off keeps you on the screen you were on.
    val nav = rememberNavController()
    val shell = rememberShellState()
    val data = rememberDataContainer(container, prefs.dummyDataEnabled)
    KeepRemindersArmed(container)
    val reducedMotion = rememberReducedMotion()
    var splashing by remember { mutableStateOf(showSplash && !reducedMotion) }
    // The app below the splash is first composed once the name is written, not while its letters move: that composition is
    // the heaviest thing a cold start does on the main thread, and a frame lost among the letters shows (see LaunchSplash).
    var splashWritten by remember { mutableStateOf(!splashing) }
    val splashLeaving = remember { Animatable(0f) }
    val finance by data.finance.state.collectAsStateWithLifecycle()
    // The user's own font file (Settings > Appearance > Interface font), made ready off the main thread; null for any other font.
    val customFontFile = rememberCustomFontFile(prefs.interfaceFont, prefs.customFontRevision)
    NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent, AppFont(prefs.interfaceFont, prefs.fontShape, customFontFile))) {
        val dark = MaterialTheme.colorScheme.isDark
        LaunchedEffect(dark) { onDarkChanged(dark) }
        Box(Modifier.fillMaxSize()) {
            Box(
                if (splashing) {
                    // Under the splash the app waits a little way back, and comes forward as it fades: a window opening.
                    Modifier.fillMaxSize().graphicsLayer {
                        val scale = SPLASH_APP_SCALE + (1f - SPLASH_APP_SCALE) * splashLeaving.value
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    Modifier.fillMaxSize()
                },
            ) {
                ProvideAppEnvironment(data) {
                    // Rebuilt for each data set, so no screen keeps reading or writing the other one.
                    key(data) {
                        AppContent(
                            data,
                            nav,
                            shell,
                            prefs.pageAnimation,
                            sampleData = data !== container,
                            splashing = splashing,
                            holdBack = !splashWritten,
                        )
                    }
                }
            }
            if (splashing) {
                LaunchSplash(
                    ready = finance.status != FinanceStatus.Loading,
                    leaving = splashLeaving,
                    onFinished = { splashing = false },
                    onWritten = { splashWritten = true },
                )
            }
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
    // Loading waits on the database, which answers off the main thread; re-registering the alarms does not - it is a call to
    // the system for each reminder, which has no business among the frames of the splash (or of the first screen).
    LaunchedEffect(container.reminders) {
        runCatching {
            container.reminders.load()
            withContext(Dispatchers.Default) { container.reminders.rearmAll() }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch(Dispatchers.Default) { runCatching { container.reminders.rearmAll() } }
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
                snackbarsRef[0]?.show("Something went wrong. Try again.", SnackbarKind.Error)
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
        // Here, above the sheets as well as the pages, so a sheet's own sections move in the chosen style too.
        LocalPageAnimation provides prefs.pageAnimation,
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
        LocalCategoryIconShape provides prefs.categoryIconShape,
        LocalRailCollapsed provides prefs.railCollapsed,
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
    splashing: Boolean = false,
    /** The splash is still writing its name: the data loads meanwhile, but nothing is composed until it is written. */
    holdBack: Boolean = false,
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
    // Not even the loading indicator, which would animate every frame behind a splash that hides it.
    if (holdBack) {
        Spacer(Modifier.fillMaxSize())
        return
    }

    when (state.status) {
        FinanceStatus.Loading -> AppLoadingState(Modifier.fillMaxSize(), label = "Opening NormPix")
        FinanceStatus.Failed -> AppErrorState(
            title = "Couldn't open your data",
            message = "Your data is safe on this device. Try again.",
            onRetry = { scope.launch { finance.load() } },
            modifier = Modifier.fillMaxSize(),
        )
        FinanceStatus.Ready -> AppNavigation(container, nav, shell, pageAnimation, sampleData, splashing)
    }
}

@Composable
private fun AppNavigation(
    container: AppContainer,
    nav: NavHostController,
    shell: ShellState,
    pageAnimation: PageAnimationType,
    sampleData: Boolean,
    splashing: Boolean,
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
    // Anything that would come up over the app waits for it to be unlocked, and for the splash to have gone.
    val held = appLocked || splashing
    LaunchedEffect(pendingAlert, held) {
        val id = pendingAlert ?: return@LaunchedEffect
        if (held) return@LaunchedEffect // shown once the app is unlocked
        container.reminderAlerts.value = null
        navigator.openReminderAlert(id)
    }
    LaunchedEffect(container.reminders) {
        container.reminders.onDue.collect { reminder -> reminder.id?.let { container.reminderAlerts.value = it } }
    }

    // A tapped card-expiry notification: show Wallet, where each card says what it needs.
    val walletRequested by container.walletRequests.collectAsStateWithLifecycle()
    LaunchedEffect(walletRequested, held) {
        if (!walletRequested || held) return@LaunchedEffect // shown once the app is unlocked
        container.walletRequests.value = false
        navigator.go(AppDestination.Wallet)
    }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalShellState provides shell, LocalPageAnimation provides pageAnimation) {
     // Reminders are one tap from what they are about (an expense's receipt, a repeating payment, an account), so those
     // screens can start one; the flows need the navigator, so they are made here, once it is provided.
     CompositionLocalProvider(LocalReminderLauncher provides rememberReminderFlows()) {
      // Text shared from another app (ShareTransactionActivity) opens as a new-transaction draft, once unlocked.
      ReceiveSharedTransactions(container.sharedText, container.sharedPictures, appLocked = held, sampleData = sampleData)
      // Once a day at launch, once unlocked: a newer release on GitHub is offered (Settings > Check for updates).
      OfferAppUpdates(
          container.updates,
          appLocked = held,
          requests = container.updateRequests,
          installRequests = container.updateInstallRequests,
      )
      // The app's own background behind every page: while one page leaves and another arrives (or a tab is still
      // composing for the first time) it is what shows between them, never the window's - which follows the
      // *system's* light or dark, not the app's.
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavHost(
            navController = nav,
            startDestination = Routes.MAIN,
            // Nothing behind the lock screen is reachable by touch or by TalkBack.
            modifier = if (appLocked) Modifier.clearAndSetSemantics {} else Modifier,
            // A Linux window opens from a strip along the bottom edge and sinks back into it: a page that is only a strip high is
            // a strip at the bottom of the screen (the other styles' pages are always the whole screen, wherever they are put).
            contentAlignment = if (pageAnimation == PageAnimationType.LinuxWindow) Alignment.BottomCenter else Alignment.Center,
            enterTransition = transitions.enter,
            exitTransition = transitions.exit,
            popEnterTransition = transitions.popEnter,
            popExitTransition = transitions.popExit,
        ) {
            page(Routes.MAIN) { AppShell(container, shell) }
            page(Routes.CATEGORIES) { CategoriesPage() }
            page(Routes.BUDGET_HISTORY) { BudgetHistoryPage() }
            page(Routes.PEOPLE) { PeoplePage() }
            page(
                Routes.PERSON,
                arguments = listOf(navArgument("personId") { type = NavType.LongType }),
            ) { entry -> PersonDetailPage(entry.arguments!!.getLong("personId")) }
            page(
                Routes.CATEGORY_HISTORY,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("month") { type = NavType.IntType },
                    navArgument("from") {
                        type = NavType.LongType
                        defaultValue = NO_DAY
                    },
                    navArgument("to") {
                        type = NavType.LongType
                        defaultValue = NO_DAY
                    },
                ),
            ) { entry ->
                val args = entry.arguments!!
                val from = args.getLong("from", NO_DAY)
                val to = args.getLong("to", NO_DAY)
                CategoryHistoryPage(
                    categoryId = args.getLong("categoryId"),
                    month = MonthKey.fromInt(args.getInt("month")),
                    range = if (from != NO_DAY && to > from) {
                        DateRange(LocalDate.ofEpochDay(from).atStartOfDay(), LocalDate.ofEpochDay(to).atStartOfDay())
                    } else {
                        null
                    },
                )
            }
            page(
                Routes.ACCOUNT_HISTORY,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry -> AccountHistoryPage(entry.arguments!!.getLong("accountId")) }
            // Settings and the pages in it: a window with room shows the list with the page beside it (see SettingsRoute).
            page(Routes.SETTINGS) { SettingsRoute() }
            page(
                Routes.SETTINGS_PAGE,
                arguments = listOf(navArgument("page") { type = NavType.StringType }),
            ) { entry ->
                // A stale link (a name that is no page any more) lands on Settings itself.
                val page = SettingsPage.entries.firstOrNull { it.name == entry.arguments?.getString("page") }
                SettingsRoute(page?.let { SettingsDetail.Page(it) })
            }
            page(
                Routes.APPEARANCE_SECTION,
                arguments = listOf(navArgument("section") { type = NavType.StringType }),
            ) { entry ->
                // A stale link (a name that is no section any more) lands on Appearance itself.
                val section = AppearanceSection.named(entry.arguments?.getString("section"))
                SettingsRoute(if (section == null) SettingsDetail.Page(SettingsPage.Appearance) else SettingsDetail.Appearance(section))
            }
            page(Routes.PROFILE) { SettingsRoute(SettingsDetail.Profile) }
            page(Routes.UPDATE) { SettingsRoute(SettingsDetail.Update) }
            page(Routes.CHANGELOG) { SettingsRoute(SettingsDetail.Changelog) }
            page(Routes.SEARCH) { AppSearchPage() }
            page(
                Routes.REMINDER_ALERT,
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType }),
            ) { entry -> ReminderAlertScreen(entry.arguments!!.getLong("reminderId")) }
        }
        if (appLocked) AppLockScreen(container, prompt = !splashing)
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
private fun AppLockScreen(container: AppContainer, prompt: Boolean = true) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    fun unlock() {
        scope.launch { container.lock.authenticate("Unlock NormPix") }
    }
    // Once the splash has gone: the system's prompt over it would hide the app's name half written.
    LaunchedEffect(prompt) {
        if (prompt && container.lock.isDeviceSupported()) unlock()
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

    override fun openCategoryHistory(categoryId: Long, range: DateRange) {
        nav.navigate(Routes.categoryHistory(categoryId, range))
    }

    override fun openAccountHistory(accountId: Long) {
        nav.navigate(Routes.accountHistory(accountId))
    }

    override fun openSettings(page: SettingsPage?) {
        if (page == null && returnTo(Routes.SETTINGS)) return
        nav.navigate(if (page == null) Routes.SETTINGS else Routes.settingsPage(page)) { launchSingleTop = true }
    }

    override fun openAppearance(section: AppearanceSection) {
        nav.navigate(Routes.appearanceSection(section)) { launchSingleTop = true }
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

    override fun openUpdates() {
        nav.navigate(Routes.UPDATE) { launchSingleTop = true }
    }

    override fun openChangelog() {
        nav.navigate(Routes.CHANGELOG) { launchSingleTop = true }
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
