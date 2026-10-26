package com.example.normpix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.FloatingBackRow
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.rememberScrollAwareBarState
import com.example.normpix.settings.AccountButtonTarget
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.ui.theme.AppFrostedTokens
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import kotlin.math.roundToInt

/** False for the tabs when the rail is beside them: no floating dock (or button above it) is laid over the bottom of their lists. */
val LocalFloatingDock = staticCompositionLocalOf { true }

/**
 * Set around a page that is the right-hand side of a list beside its page (Settings on a tablet). Such a page has the list
 * beside it for a way out, so it has no back control, no floating back dock - unless it is itself a step inside another page
 * ([canGoBack], an Appearance screen) - and no wish to sit under the dock's clearance.
 */
@Immutable
class DetailPane(
    val canGoBack: Boolean,
    /** What the page is drawn on, where the pane has a tone of its own (a panel beside the list); unspecified for the page colour. */
    val container: Color = Color.Unspecified,
)

/** The [DetailPane] this page is in, or null for a page that has the whole window. */
val LocalDetailPane = staticCompositionLocalOf<DetailPane?> { null }

/** What a page is drawn on: the page colour, or the tone of the panel it is the detail of. */
@Composable
internal fun pageColor(): Color =
    LocalDetailPane.current?.container?.takeIf { it.isSpecified } ?: MaterialTheme.colorScheme.surface

/**
 * True for the list of a two-pane screen whose frame draws the back dock once, across the whole window (Settings, in the bottom-dock
 * style): its page keeps its room for the dock - and so does the page beside it - but draws none of its own.
 */
val LocalDockHosted = staticCompositionLocalOf { false }

/**
 * The frame for every top-level tab and pushed page: a clean, standard top app bar
 * that keeps title and action icons comfortably compact and aligned, eliminating
 * excessive top whitespace, followed by the page's list.
 *
 * @param maxWidth null lets content use the full width (multi-column layouts).
 * @param pinnedHeader content held between the app bar and the list, so it
 *   never scrolls away (e.g. a switch between a tab's views).
 * @param floatingTopBar the caller floats its own controls over the top of the page (see [AppSubPage]): there is no app
 *   bar, the title opens the list instead, and the list starts below the controls and scrolls on under them.
 * @param onBack the page was pushed over another: the way back, as a back button in the app bar - or, in the top floating
 *   circle style, as the circle that opens the header.
 *
 * In the top floating circle style ([BackButtonStyle.TopFloatingCircle]) the page floats its own header, with no app bar,
 * all glass over the list, which scrolls on under it:
 *  - a top-level page (no [onBack]) has its title at the start and, at the end, one pill with its [actions] and an account
 *    circle that opens Settings;
 *  - a pushed page opens with a Back circle, its title (large on the page, up in the row once scrolled) and an [actions] pill.
 * A page with a [pinnedHeader] hangs it under that row, over the list, which fades out beneath it.
 *
 * With Bottom controls on ([rememberBottomControls]) a tab's [bottomSearch] - its search field and filters, which the tab
 * otherwise sets in its list - sits in a card at the foot of the page, above the dock. A pushed page ([onBack]) holds its search
 * in [AppSubPage]'s card instead, with its back row.
 */
@Composable
fun AppPage(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    pinnedHeader: (@Composable () -> Unit)? = null,
    maxWidth: Dp? = AppLayout.maxContentWidth,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = AppLayout.navClearance,
    insetBottom: Boolean = true,
    imeAware: Boolean = false,
    floatingTopBar: Boolean = false,
    onBack: (() -> Unit)? = null,
    displayTitle: Boolean = false,
    /** False for a page whose content opens with a heading of its own, so the page's name isn't set above it as well. */
    largeTitle: Boolean = true,
    bottomSearch: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    // Under a tab with the rail beside it nothing floats over the list: it ends a step under its last row, clear of the system bar.
    val railBeside = !LocalFloatingDock.current
    val listBottomPadding = if (railBeside) AppLayout.railBottomClearance else bottomPadding
    val listInsetBottom = insetBottom || railBeside
    val floatsItself = !floatingTopBar && rememberBackButtonStyle() == BackButtonStyle.TopFloatingCircle
    if (!floatsItself) {
        val dockedSearch = bottomSearch != null && onBack == null && rememberBottomControls()
        if (dockedSearch) {
            var cardHeightPx by remember { mutableIntStateOf(0) }
            val cardHeight = with(LocalDensity.current) { cardHeightPx.toDp() }
            Box(modifier.fillMaxSize()) {
                PageFrame(
                    title = title,
                    modifier = Modifier,
                    navigationIcon = navigationIcon,
                    actions = actions ?: {},
                    pinnedHeader = pinnedHeader,
                    maxWidth = maxWidth,
                    listState = listState,
                    // The list ends above the card, which sits on the dock.
                    bottomPadding = maxOf(listBottomPadding, searchCardClearance(cardHeight, liftedOverDock = true)),
                    insetBottom = listInsetBottom,
                    imeAware = imeAware,
                    floatingTopBar = floatingTopBar,
                    displayTitle = displayTitle,
                    largeTitle = largeTitle,
                    content = content,
                )
                DockedSearchCard(onHeightChange = { cardHeightPx = it }) { bottomSearch() }
            }
            return
        }
        PageFrame(
            title = title,
            modifier = modifier,
            navigationIcon = if (onBack != null) ({ BackButton(onBack) }) else navigationIcon,
            actions = actions ?: {},
            pinnedHeader = pinnedHeader,
            maxWidth = maxWidth,
            listState = listState,
            bottomPadding = listBottomPadding,
            insetBottom = listInsetBottom,
            imeAware = imeAware,
            floatingTopBar = floatingTopBar,
            displayTitle = displayTitle,
            largeTitle = largeTitle,
            content = content,
        )
        return
    }
    // The header is glass: it blurs a recording of the page it floats over (and stays out of the recording).
    val backdrop = rememberFrostedBackdrop()
    val pushed = onBack != null
    Box(Modifier.fillMaxSize()) {
        PageFrame(
            title = title,
            modifier = modifier.frostedSource(backdrop),
            navigationIcon = {},
            actions = {},
            pinnedHeader = pinnedHeader,
            maxWidth = maxWidth,
            listState = listState,
            bottomPadding = listBottomPadding,
            insetBottom = listInsetBottom,
            imeAware = imeAware,
            floatingTopBar = true,
            largeTitle = pushed && largeTitle,
            displayTitle = displayTitle,
            content = content,
        )
        FloatingTopHeader(
            title = title,
            listState = listState,
            backdrop = backdrop,
            maxWidth = maxWidth,
            // A top-level page's title is simply in the row; a pushed page's comes up from the list.
            titleFromList = pushed && pinnedHeader == null,
            listUnderRow = pinnedHeader == null,
            leading = onBack?.let { back -> @Composable { FloatingCircleBackButton(onBack = back) } },
            actions = if (pushed) {
                actions
            } else {
                {
                    actions?.invoke(this)
                    AccountButton()
                }
            },
        )
    }
}

/** What the page is made of, under whatever header it has: the app bar, or the room left for floating controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageFrame(
    title: String,
    modifier: Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    pinnedHeader: (@Composable () -> Unit)?,
    maxWidth: Dp?,
    listState: LazyListState,
    bottomPadding: Dp,
    insetBottom: Boolean,
    imeAware: Boolean,
    floatingTopBar: Boolean,
    content: LazyListScope.() -> Unit,
    /** With floating controls: the page opens with its large title, which scrolls up into them (a pushed page), or has none. */
    largeTitle: Boolean = true,
    /** The title in the display size, as a screen that opens with one (Settings) has it, not the headline size of other pages. */
    displayTitle: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val page = pageColor()
    val density = LocalDensity.current
    // A pinned header floats over the list, under the floating controls, and the list runs on beneath it.
    val overlayPinned = floatingTopBar && pinnedHeader != null
    var pinnedPx by remember { mutableIntStateOf(0) }
    val titleInList = floatingTopBar && pinnedHeader == null && largeTitle
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val bottomInset = if (insetBottom) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapse = rememberTitleCollapse(listState)
    // A Surface (not just a background) so every Text on the page defaults to onSurface.
    androidx.compose.material3.Surface(modifier.fillMaxSize(), color = page) {
      Box(contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxHeight()
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxWidth()
                .then(if (imeAware) Modifier.imePadding() else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (!floatingTopBar) TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = navigationIcon,
                actions = {
                    actions()
                    Spacer(Modifier.width(AppSpacing.xs))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = page,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
            if (pinnedHeader != null && !overlayPinned) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (scrollBehavior.state.contentOffset < -1f) {
                                drawRect(scheme.surfaceContainer)
                            }
                        },
                ) { pinnedHeader() }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = when {
                            // Below the header's fade as well as the header: the fade is a veil over what slides away under it,
                            // and at rest it would otherwise lie over the top of the first item (the search field, say).
                            overlayPinned -> with(density) { pinnedPx.toDp() } + FloatingTopControls.fade
                            floatingTopBar -> statusBar + FloatingTopControls.clearance
                            else -> 0.dp
                        },
                        bottom = bottomPadding + bottomInset,
                    ),
                    content = if (titleInList) {
                        {
                            item(key = PAGE_TITLE_KEY) {
                                val gone by remember(collapse) { derivedStateOf { collapse.value >= 1f } }
                                PageTitle(title, gone, displayTitle) { collapse.value }
                            }
                            content()
                        }
                    } else {
                        content
                    },
                )
                if (overlayPinned) {
                    // Page-coloured from the top of the screen to the foot of the header, then a short fade: the list slides
                    // away under it instead of being cut off by it.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { pinnedPx = it.height }
                            .drawBehind {
                                val fade = FloatingTopControls.fade.toPx()
                                drawRect(
                                    Brush.verticalGradient(listOf(page, page.copy(alpha = 0f)), startY = size.height, endY = size.height + fade),
                                    topLeft = Offset(0f, size.height),
                                    size = Size(size.width, fade),
                                )
                            }
                            .background(page),
                    ) {
                        Spacer(Modifier.height(statusBar + FloatingTopControls.clearance))
                        pinnedHeader()
                    }
                }
            }
        }
      }
    }
}

/**
 * Over the top of a page whose controls float ([AppSubPage]): the page's own colour behind the status bar, fading out just
 * below it, so its icons never sit on the list; and, as the title scrolls up into the controls' row, over that row too, so
 * the title reads there. It is not part of what the glass controls blur - they still show the page's real content.
 */
@Composable
private fun TopScrim(collapse: State<Float>) {
    val solid = pageColor()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusBar + FloatingTopControls.clearance)
            .drawBehind {
                val covered = solid.copy(alpha = collapse.value * FloatingTopControls.COLLAPSED_COVER)
                val statusPx = statusBar.toPx()
                val fadePx = FloatingTopControls.fade.toPx()
                drawRect(
                    Brush.verticalGradient(
                        0f to solid,
                        statusPx / size.height to solid,
                        (statusPx + fadePx) / size.height to covered,
                        (size.height - fadePx) / size.height to covered,
                        1f to solid.copy(alpha = 0f),
                    ),
                )
            },
    )
}

private const val PAGE_TITLE_KEY = "app_page_title"

/**
 * How far the page's own title has scrolled up and away: 0 while it is shown in full, 1 once it has gone under the
 * controls (and the same title shows in their row instead). It starts to give way a third of the way up.
 */
@Composable
private fun rememberTitleCollapse(listState: LazyListState): State<Float> = remember(listState) {
    derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            val title = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == PAGE_TITLE_KEY }?.size ?: 0
            if (title <= 0) 0f else ((listState.firstVisibleItemScrollOffset - TITLE_HOLD * title) / ((1f - TITLE_HOLD) * title)).coerceIn(0f, 1f)
        }
    }
}

private const val TITLE_HOLD = 0.35f

/**
 * The page's title at the top of its list, where there is no app bar to hold it. As it scrolls up into the controls it
 * fades out - over the first part of [collapse] - and the same title fades in in their row, so the two never show at once.
 */
@Composable
private fun PageTitle(title: String, gone: Boolean, display: Boolean, collapse: () -> Float) {
    Text(
        title,
        style = if (display) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = (1f - collapse() * 1.8f).coerceAtLeast(0f) }
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm)
            // Spoken once: by this while it is on the page, by the one in the controls' row once it is gone.
            .then(if (gone) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }),
    )
}

/** The controls floating over the top of a page ([AppSubPage] in the top floating circle style). */
private object FloatingTopControls {
    /** Their size: the back circle, and the actions pill's height. */
    val size = 42.dp

    /** Their gap from the status bar. */
    val margin = AppSpacing.sm

    /** How far below them the list starts. */
    val clearance = margin + size + AppSpacing.sm

    /** How far below the status bar the page's colour fades out over the list. */
    val fade = AppSpacing.md

    /** How much of the page's colour covers the controls' row once the title has scrolled up into it. */
    const val COLLAPSED_COVER = 0.94f
}

/**
 * A pushed page (settings sub-pages, forms): [AppPage] with customizable back button placement and style
 * (Bottom floating dock, Top floating circle button, or Standard top app bar).
 *
 * In the top floating circle style the back circle and the [actions] pill float over the page as glass, the page
 * scrolling on under them, and the title opens the page instead of sitting in a bar.
 *
 * [actionsVisible] says whether the [actions] are there at the moment: a page whose action only makes sense part of the time
 * (Settings' search icon, once the search field has scrolled away) turns it on and off, and the pill grows and shrinks around
 * it - rather than staying on as an empty capsule while there is nothing in it.
 *
 * A [bottomBar] is pinned at the foot of the page, under the list (which ends where it starts), on the page's colour: the
 * update page's buttons, say. It clears the navigation bar and, in the bottom dock style, the floating back dock as well.
 * None by default.
 *
 * With Bottom controls on ([rememberBottomControls]) the page is in the bottom dock style, and what it would have at its top
 * goes down with the back row: the [actions] join it in a pill, and the page's search - [bottomSearch], which the page then
 * leaves out of its list - sits in a card over it. The card stays while the row steps out of the way on a scroll down, settling
 * where the row was, and rests on the keyboard (with the row folded away) while the user types.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    actionsVisible: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    imeAware: Boolean = false,
    showTopBack: Boolean = false,
    showFloatingBack: Boolean = true,
    displayTitle: Boolean = false,
    largeTitle: Boolean = true,
    /** How wide the page's column may be: a form or settings page keeps its 720dp, a list can take more of a big window. */
    maxWidth: Dp = AppLayout.maxContentWidth,
    bottomSearch: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val navigator = LocalNavigator.current
    val backStyle = rememberBackButtonStyle()
    val bottomControls = rememberBottomControls()
    // Beside a list that is always on screen a page has nowhere to go back to (see DetailPane), whatever the style.
    val detail = LocalDetailPane.current
    // A page beside a list has a pane to itself, wider than a phone's column: its rows take what they can of it, up to a list's width.
    val columnMax = if (detail != null) maxOf(maxWidth, AppLayout.maxListWidth) else maxWidth

    val isTopFloatingCircle = backStyle == BackButtonStyle.TopFloatingCircle
    val isStandardTopBar = backStyle == BackButtonStyle.StandardTopBar
    val isBottomDock = backStyle == BackButtonStyle.BottomDock

    val showTopBackEffective = if (detail != null) detail.canGoBack else showTopBack || isTopFloatingCircle || isStandardTopBar
    // Room for the dock is kept wherever the style has one; it is drawn here only by a page with the window to itself.
    val reservesDock = showFloatingBack && isBottomDock
    val showFloatingBackEffective = reservesDock && detail == null && !LocalDockHosted.current
    // Bottom controls: the page's actions ride in the back row, and its search is a card over it.
    val actionsAtBottom = bottomControls && detail == null && actions != null
    val searchCard = bottomControls && detail == null && bottomSearch != null
    var searchCardPx by remember { mutableIntStateOf(0) }
    val searchCardRoom = if (searchCard) with(LocalDensity.current) { searchCardPx.toDp() } + SearchCardGap + AppSpacing.sm else 0.dp
    val typing = WindowInsets.isImeVisible
    val navigationBarPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)

    val barScroll = rememberScrollAwareBarState()
    val listAtEdge by remember(listState) {
        derivedStateOf { !listState.canScrollForward || !listState.canScrollBackward }
    }
    val barGone = barScroll.hidden && !listAtEdge
    val goneFraction by animateFloatAsState(
        targetValue = if (barGone) 1f else 0f,
        animationSpec = AppMotion.spatial(),
        label = "sub_page_back_bar_gone",
    )
    var barHeightPx by remember { mutableIntStateOf(0) }
    // The floating controls are glass: they blur a recording of the page they float over (and stay out of the recording).
    val backdrop = rememberFrostedBackdrop()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppPage(
                title = title,
                modifier = modifier
                    .weight(1f)
                    .nestedScroll(barScroll.connection)
                    .frostedSource(backdrop),
                navigationIcon = if (showTopBackEffective && !isTopFloatingCircle) {
                    { BackButton(onBack) }
                } else {
                    {}
                },
                actions = {
                    if (!isTopFloatingCircle && !actionsAtBottom && actions != null) {
                        AnimatedVisibility(visible = actionsVisible, enter = ActionsEnter, exit = ActionsExit) {
                            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                        }
                    }
                },
                listState = listState,
                imeAware = imeAware,
                // With a bottom bar the list ends where the bar starts, which has cleared the navigation bar and the dock already.
                // A search card over the back row takes room of its own.
                bottomPadding = when {
                    bottomBar != null -> AppSpacing.lg
                    reservesDock -> AppLayout.navClearance + 24.dp
                    else -> AppLayout.navClearance
                } + searchCardRoom,
                insetBottom = bottomBar == null,
                maxWidth = columnMax,
                floatingTopBar = isTopFloatingCircle,
                displayTitle = displayTitle,
                largeTitle = largeTitle,
                content = content,
            )
            if (bottomBar != null) {
                // The floating back dock sits over the foot of the screen: the bar rests above it (measured, once the dock has been).
                val dockClearance = if (barHeightPx > 0) {
                    with(LocalDensity.current) { barHeightPx.toDp() } + AppSpacing.sm
                } else {
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + DOCK_FALLBACK_HEIGHT
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(pageColor())
                        .then(if (reservesDock) Modifier.padding(bottom = dockClearance) else Modifier.navigationBarsPadding()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.widthIn(max = columnMax).fillMaxWidth()) { bottomBar() }
                }
            }
        }
        if (isTopFloatingCircle) {
            FloatingTopHeader(
                title = title,
                listState = listState,
                backdrop = backdrop,
                maxWidth = columnMax,
                titleFromList = true,
                listUnderRow = true,
                leading = if (showTopBackEffective) ({ FloatingCircleBackButton(onBack = onBack) }) else null,
                actions = actions,
                actionsVisible = actionsVisible,
            )
        }
        // The page's own search or actions stand in for the tile that opens Search.
        val backRowCompanion = if (actionsAtBottom || searchCard) null else ({ navigator.openSearch() })
        val backRowActions = if (actionsAtBottom && actionsVisible) actions else null
        if (searchCard) {
            CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        // With the back row under it the row clears the navigation bar; without, the card does.
                        .then(if (showFloatingBackEffective) Modifier else Modifier.navigationBarsPadding())
                        .imePadding()
                        // As the row goes, the card goes down into its place, above the navigation bar.
                        .offset { IntOffset(0, (goneFraction * (barHeightPx - navigationBarPx).coerceAtLeast(0)).roundToInt()) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BottomSearchCard(onHeightChange = { searchCardPx = it }) { bottomSearch() }
                    Spacer(Modifier.height(SearchCardGap))
                    if (showFloatingBackEffective) {
                        // Folded away while typing: the card keeps the keyboard's top to itself.
                        AnimatedVisibility(visible = !typing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            FloatingBackRow(
                                title = title,
                                onBack = onBack,
                                onCompanionClick = backRowCompanion,
                                actions = backRowActions,
                                spread = bottomControls,
                                modifier = Modifier
                                    .onSizeChanged { barHeightPx = it.height }
                                    .graphicsLayer { alpha = 1f - goneFraction.coerceIn(0f, 1f) },
                            )
                        }
                    }
                }
            }
        } else if (showFloatingBackEffective) {
            CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
                FloatingBackRow(
                    title = title,
                    onBack = onBack,
                    onCompanionClick = backRowCompanion,
                    actions = backRowActions,
                    spread = bottomControls,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { barHeightPx = it.height }
                        .offset { IntOffset(0, (goneFraction * barHeightPx).roundToInt()) }
                        .graphicsLayer { alpha = 1f - goneFraction.coerceIn(0f, 1f) },
                )
            }
        }
    }
}

/**
 * The account circle in the pill of a top-level page's header: the user's own picture (their emoji or initial, once they have
 * set a profile) and, as they chose in Settings, it opens their Profile or Settings.
 */
@Composable
private fun AccountButton() {
    val navigator = LocalNavigator.current
    val haptics = rememberAppHaptics()
    val prefs = LocalAppPreferences.current?.state?.collectAsState()?.value
    val target = prefs?.accountButtonTarget ?: AccountButtonTarget.Settings
    IconButton(
        onClick = {
            haptics.selection()
            when (target) {
                AccountButtonTarget.Profile -> navigator.openProfile()
                AccountButtonTarget.Settings -> navigator.openSettings()
            }
        },
        modifier = Modifier.size(40.dp).semantics { contentDescription = accountButtonLabel(target) },
    ) {
        ProfileAvatar(
            name = prefs?.profileName.orEmpty(),
            emoji = prefs?.profileEmoji.orEmpty(),
            size = 32.dp,
            photo = prefs?.profilePhoto.orEmpty(),
        )
    }
}

/** What a screen reader calls the account circle: where it leads. */
fun accountButtonLabel(target: AccountButtonTarget): String = when (target) {
    AccountButtonTarget.Profile -> PROFILE_BUTTON_LABEL
    AccountButtonTarget.Settings -> ACCOUNT_BUTTON_LABEL
}

/** What a screen reader calls the account circle while it opens Settings (the default). */
const val ACCOUNT_BUTTON_LABEL = "Account and settings"

/** ... and while it opens the Profile. */
const val PROFILE_BUTTON_LABEL = "Profile"

/**
 * How the app is set to present its pages' controls. With Bottom controls on (see [rememberBottomControls]) the pages are in the
 * bottom dock style, whichever style was picked: the way back is at the bottom, and the rest of their controls with it.
 */
@Composable
internal fun rememberBackButtonStyle(): BackButtonStyle {
    val state = LocalAppPreferences.current?.state?.collectAsState()
    if (rememberBottomControls()) return BackButtonStyle.BottomDock
    return state?.value?.backButtonStyle ?: AppPrefsState().backButtonStyle
}

/**
 * The header floating over the top of a page in the top floating circle style: an optional [leading] circle, the title, and the
 * [actions] pill (only when the page has any). With [titleFromList] the title is the page's own, come up from the list as
 * that scrolls under the controls; otherwise it is simply there. [listUnderRow] says the list does run under the row, so the
 * page's colour is laid behind it (see [TopScrim]).
 */
@Composable
private fun BoxScope.FloatingTopHeader(
    title: String,
    listState: LazyListState,
    backdrop: FrostedBackdrop,
    maxWidth: Dp?,
    titleFromList: Boolean,
    listUnderRow: Boolean,
    leading: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    actionsVisible: Boolean = true,
) {
    val scrolled = rememberTitleCollapse(listState)
    val always = remember { mutableFloatStateOf(1f) }
    val collapse: State<Float> = if (titleFromList) scrolled else always
    val collapsed by remember(collapse) { derivedStateOf { collapse.value >= 1f } }
    if (listUnderRow) Box(Modifier.align(Alignment.TopCenter)) { TopScrim(collapse) }
    CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = AppSpacing.lg, vertical = FloatingTopControls.margin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            // The title, up from the page: it fades in here as the one on the page scrolls under the controls.
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                // Set here: this row is beside the page's Surface, not in it, so it would be the default black.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (leading != null) AppSpacing.md else 0.dp, end = AppSpacing.md)
                    .graphicsLayer {
                        // In once the one on the page is out, so the two are never both there.
                        alpha = ((collapse.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        translationY = (1f - alpha) * 8.dp.toPx()
                    }
                    // Spoken once: by the page's own title while that is there, by this one once it is gone.
                    .then(if (collapsed) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
            )
            if (actions != null) {
                // The pill is there only while its actions are: it grows out of the end of the row and shrinks back into it.
                AnimatedVisibility(visible = actionsVisible, enter = ActionsEnter, exit = ActionsExit) {
                    FloatingHeaderActionsPill(content = actions)
                }
            }
        }
    }
}

/** Until the floating back dock has been measured: its height above the navigation bar (60dp, 12dp up) and a gap above it. */
private val DOCK_FALLBACK_HEIGHT = 80.dp

private val ActionsEnter = fadeIn() + scaleIn(initialScale = 0.6f) + expandHorizontally(expandFrom = Alignment.End)
private val ActionsExit = fadeOut() + scaleOut(targetScale = 0.6f) + shrinkHorizontally(shrinkTowards = Alignment.End)

/**
 * What a floating top control is made of: glass where the floating bars are ([rememberFrostedChrome]) - the page it
 * floats over, blurred, under a see-through tint - and otherwise a solid tonal surface, lifted by a slight shadow.
 */
private class FloatingControlStyle(val container: Color, val shadow: Dp)

@Composable
private fun rememberFloatingControlStyle(backdrop: FrostedBackdrop?): FloatingControlStyle {
    val glass = rememberFrostedChrome(backdrop)
    val solid = MaterialTheme.colorScheme.surfaceContainerHigh
    return remember(glass, solid) {
        // No shadow under glass: it would show through the tint as a smudge.
        if (glass) FloatingControlStyle(solid.copy(alpha = AppFrostedTokens.surfaceAlpha), 0.dp)
        else FloatingControlStyle(solid, 2.dp)
    }
}

/** A circular back button floating over the top of a page, glass over the page it floats over (see [AppSubPage]). */
@Composable
fun FloatingCircleBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: FrostedBackdrop? = LocalFrostedBackdrop.current,
) = FloatingCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, modifier, backdrop)

/** A circle floating over the top of a page, made as the back circle is: one [icon], named [description] for a screen reader. */
@Composable
fun FloatingCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: FrostedBackdrop? = LocalFrostedBackdrop.current,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val style = rememberFloatingControlStyle(backdrop)
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        shape = CircleShape,
        color = style.container,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
        shadowElevation = style.shadow,
        modifier = modifier
            .size(FloatingTopControls.size)
            .frostedGlass(backdrop, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The pill holding a page's actions at the top right, floating beside the back circle and made as it is. */
@Composable
fun FloatingHeaderActionsPill(
    modifier: Modifier = Modifier,
    backdrop: FrostedBackdrop? = LocalFrostedBackdrop.current,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val style = rememberFloatingControlStyle(backdrop)
    Surface(
        shape = CircleShape,
        color = style.container,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
        shadowElevation = style.shadow,
        modifier = modifier
            .height(FloatingTopControls.size)
            .frostedGlass(backdrop, CircleShape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
    }
}

/** Horizontal page gutter around one box item of a list. */
@Composable
fun PageItem(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(AppSpacing.page)) { content() }
}
