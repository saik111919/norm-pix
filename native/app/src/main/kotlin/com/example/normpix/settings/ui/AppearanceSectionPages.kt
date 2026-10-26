package com.example.normpix.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.BankBadge
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.CategoryAvatarSample
import com.example.normpix.finance.ui.CategoryGlyph
import com.example.normpix.finance.ui.LocalCategoryIconShape
import com.example.normpix.finance.ui.LocalCategoryIconStyle
import com.example.normpix.finance.ui.MonthScopeButton
import com.example.normpix.finance.ui.MonthSwitcher
import com.example.normpix.finance.ui.outline
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.settings.AppIcon
import com.example.normpix.settings.AppIcons
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.settings.CardsLayout
import com.example.normpix.settings.CategoryIconShape
import com.example.normpix.settings.CategoryIconStyle
import com.example.normpix.settings.InterfaceFont
import com.example.normpix.settings.MonthControlStyle
import com.example.normpix.settings.TopSpendingLayout
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.settings.ProgressStyle
import com.example.normpix.settings.SheetAnimationType
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ConfirmSheetContent
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/** One screen of Settings > Appearance: [section]'s own choices, under its name. */
@Composable
fun AppearanceSectionPage(section: AppearanceSection) {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()

    AppSubPage(title = section.title, onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                when (section) {
                    AppearanceSection.Colors -> ColorsSection(prefs, preferences, haptics)
                    AppearanceSection.AppIcon -> AppIconSection(haptics)
                    AppearanceSection.InterfaceFont -> InterfaceFontSection(prefs, preferences, haptics)
                    AppearanceSection.CategoryIcons -> CategoryIconsSection(prefs, preferences, haptics)
                    AppearanceSection.ProgressIndicators -> ProgressIndicatorsSection(prefs, preferences, haptics)
                    AppearanceSection.Transitions -> {
                        PageTransitionsSection(prefs, preferences, haptics)
                        SheetTransitionsSection(prefs, preferences, haptics)
                    }
                    AppearanceSection.Effects -> EffectsSection(prefs, preferences, haptics)
                    AppearanceSection.Controls -> {
                        FooterLabelsSection(prefs, preferences, haptics)
                        BottomControlsSection(prefs, preferences, haptics)
                        BackButtonSection(prefs, preferences, haptics)
                        MonthControlSection(prefs, preferences, haptics)
                    }
                    AppearanceSection.Layouts -> {
                        TopSpendingSection(prefs, preferences, haptics)
                        WalletLayoutSections(prefs, preferences, haptics)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconSection(haptics: AppHaptics) {
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    // Android holds the choice; read it each time the page opens.
    var appIcon by remember { mutableStateOf(AppIcons.current(context)) }
    ListSection(
        footer = "Android may close NormPix while it changes the icon, and some home screens drop the old one - " +
            "add it back from the app list.",
    ) {
        item {
            AppIconCard(
                selected = appIcon,
                onSelected = { icon ->
                    haptics.selection()
                    scope.launch {
                        val change = overlays.confirm(
                            title = "Use the ${icon.label} icon?",
                            message = "NormPix may close for a moment while Android changes its icon. " +
                                "If it leaves your home screen, add it back from the app list.",
                            confirmLabel = "Change icon",
                        )
                        if (!change) return@launch
                        AppIcons.use(context, icon)
                        appIcon = icon
                    }
                },
            )
        }
    }
}

@Composable
private fun InterfaceFontSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val custom = rememberCustomFontActions()
    ListSection(
        footer = when (prefs.interfaceFont) {
            InterfaceFont.GsFlex -> "Applies to all text in NormPix. GS Flex is Google Sans Flex, under the SIL Open Font License."
            InterfaceFont.Custom ->
                "Applies to all text in NormPix. Where your font has no letter for something, it is drawn in your phone's font."
            InterfaceFont.System ->
                "NormPix uses your phone's font. Pick GS Flex to choose a style, or to set its weight, width and roundness " +
                    "yourself - or Your font, for a font file of your own."
        },
    ) {
        item { InterfaceFontCard(prefs, preferences, haptics, custom) }
    }
}

@Composable
private fun CategoryIconsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Category icons",
        footer = "How categories are pictured in lists, charts and the transaction editor. A category with an emoji of " +
            "its own keeps it in every style.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (style in CategoryIconStyle.entries) {
            item {
                RadioRow(
                    headline = style.label,
                    supporting = style.description,
                    selected = prefs.categoryIconStyle == style,
                    onClick = {
                        haptics.selection()
                        preferences.setCategoryIconStyle(style)
                    },
                    trailing = { CategoryIconStylePreview(style) },
                )
            }
        }
    }
    ListSection(
        title = "Bank logos",
        footer = "How a bank is shown on the Wallet's accounts and cards.",
        modifier = Modifier.selectableGroup(),
    ) {
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
    ListSection(
        title = "Icon background",
        footer = "The shape behind category icons, people and banks in lists, charts and the editors. Brand logos, " +
            "like a payee's, are cut to the same shape.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (shape in CategoryIconShape.entries) {
            item {
                RadioRow(
                    headline = shape.label,
                    supporting = shape.description,
                    selected = prefs.categoryIconShape == shape,
                    onClick = {
                        haptics.selection()
                        preferences.setCategoryIconShape(shape)
                    },
                    trailing = { CategoryIconShapePreview(shape) },
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicatorsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        footer = "How budgets, spending shares and anything loading show their progress.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (style in ProgressStyle.entries) {
            item {
                RadioRow(
                    headline = style.label,
                    supporting = style.description,
                    selected = prefs.progressStyle == style,
                    onClick = {
                        haptics.selection()
                        preferences.setProgressStyle(style)
                    },
                    trailing = { ProgressPreview(style) },
                )
            }
        }
    }
}

/** The touches that are only for the eye: the blur behind what floats, and the saved cards' tilt (while cards are kept). */
@Composable
private fun EffectsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Blur",
        footer = "Needs Android 12 or later with the system's blur effects on. Where blur is off or unavailable, " +
            "sheets, dialogs and the floating bars are solid and the page behind them stays sharp.",
    ) {
        item {
            SwitchRow(
                headline = "Background blur",
                supporting = "Blur the page behind sheets and dialogs, and under the floating bars",
                checked = prefs.sheetBlurEnabled,
                onCheckedChange = { on ->
                    haptics.selection()
                    preferences.setSheetBlurEnabled(on)
                },
            )
        }
    }
    if (prefs.walletCardsEnabled) {
        ListSection(
            title = "Saved cards",
            footer = "Stays flat when animations are turned off in system settings.",
        ) {
            item {
                SwitchRow(
                    headline = "Card tilt",
                    supporting = "Cards lean a few degrees as you move your phone",
                    checked = prefs.cardTiltEnabled,
                    onCheckedChange = { on ->
                        haptics.selection()
                        preferences.setCardTiltEnabled(on)
                    },
                )
            }
        }
    }
}

@Composable
private fun PageTransitionsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Pages",
        footer = "Pages, tabs, a screen's sections and the back gesture's preview all move in the style you pick. " +
            "Sheets and dialogs have a style of their own, below. " +
            "When animations are turned off in system settings, pages change without animating.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (type in PageAnimationType.entries) {
            item {
                RadioRow(
                    headline = type.label,
                    supporting = type.description,
                    selected = prefs.pageAnimation == type,
                    onClick = {
                        haptics.selection()
                        preferences.setPageAnimation(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetTransitionsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    ListSection(
        title = "Sheets & dialogs",
        footer = "Every sheet - the forms and pickers - and every yes-or-no question moves in this style, whichever style " +
            "your pages use. When animations are turned off in system settings, they come up without extra motion.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (type in SheetAnimationType.entries) {
            item {
                RadioRow(
                    headline = type.label,
                    supporting = type.description,
                    selected = prefs.sheetAnimation == type,
                    onClick = {
                        haptics.selection()
                        preferences.setSheetAnimation(type)
                    },
                )
            }
        }
    }
    ListSection(
        title = "Try it",
        footer = "Opens a sample, so you can see the style without leaving this page.",
    ) {
        item {
            ListRow(
                headline = "Open a sheet",
                supporting = "Like a form or a picker",
                leading = { LeadingIcon(Icons.Outlined.Layers) },
                onClick = {
                    scope.launch {
                        overlays.sheet<Unit> { done ->
                            ConfirmSheetContent(
                                title = "A sheet",
                                message = "This is how sheets open and close. Drag it down, tap outside it or go back to close it.",
                                confirmLabel = "Done",
                                cancelLabel = null,
                                destructive = false,
                                icon = null,
                                onConfirm = { done(null) },
                                onCancel = { done(null) },
                            )
                        }
                    }
                },
            )
        }
        item {
            ListRow(
                headline = "Open a question",
                supporting = "Like the ones that ask before deleting",
                leading = { LeadingIcon(Icons.Outlined.Layers) },
                onClick = {
                    scope.launch {
                        overlays.confirm(
                            title = "A question",
                            message = "This is how a yes-or-no question opens and closes. Nothing happens either way.",
                            confirmLabel = "Yes",
                            cancelLabel = "No",
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BackButtonSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Back button",
        footer = "Choose how pages present their back navigation. Top floating circle also floats the header of Home, " +
            "Plans & Activity, Insights, Wallet and search over their content." +
            if (prefs.bottomControls) " Bottom controls is on, so on a phone pages are in the Bottom dock style whichever you pick." else "",
        modifier = Modifier.selectableGroup(),
    ) {
        for (style in BackButtonStyle.entries) {
            item {
                RadioRow(
                    headline = style.label,
                    supporting = style.description,
                    selected = prefs.backButtonStyle == style,
                    onClick = {
                        haptics.selection()
                        preferences.setBackButtonStyle(style)
                    },
                )
            }
        }
    }
}

/** Switch for showing or hiding destination labels on the bottom navigation bar / dock. */
@Composable
private fun FooterLabelsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Navigation bar",
        footer = "When turned off, the bottom navigation bar stays compact with icons only, without destination labels.",
    ) {
        item {
            SwitchRow(
                headline = "Footer labels",
                supporting = "Show destination label on the active bottom bar item",
                checked = prefs.showFooterLabels,
                onCheckedChange = { on ->
                    haptics.selection()
                    preferences.setShowFooterLabels(on)
                },
            )
        }
    }
}

/** The one switch for putting a page's controls where a thumb reaches: what moves, and what stays put. */
@Composable
private fun BottomControlsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Within reach",
        footer = "On a phone, the back button and a page's own buttons join the bar at the bottom, and the search and filters " +
            "of Activity, People, Settings and Search move down to a card above it. The dock stays where it is. " +
            "While this is on, pages are in the Bottom dock style of the back button below. Tablets and wide windows keep their layout.",
    ) {
        item {
            SwitchRow(
                headline = "Bottom controls",
                supporting = "Back, page buttons and search at the bottom of the screen",
                checked = prefs.bottomControls,
                onCheckedChange = { on ->
                    haptics.selection()
                    preferences.setBottomControls(on)
                },
            )
        }
    }
}

/** Home's biggest spending categories: a stack, as the cards are, or a carousel, as the accounts can be. */
@Composable
private fun TopSpendingSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Top spending on Home",
        footer = "How Home shows the month's biggest spending categories. Either way a tap opens the category's month, and " +
            "Insights has every category.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (layout in TopSpendingLayout.entries) {
            item {
                RadioRow(
                    headline = layout.label,
                    supporting = layout.description,
                    selected = prefs.topSpendingLayout == layout,
                    onClick = {
                        haptics.selection()
                        preferences.setTopSpendingLayout(layout)
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthControlSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Month control",
        footer = "How the month is switched on Activity, Budgets, Insights and People. Both work the same - the steps, the " +
            "month picker, \"Dates\" and \"All time\" - and only the look differs.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (style in MonthControlStyle.entries) {
            item {
                val choose = {
                    haptics.selection()
                    preferences.setMonthControlStyle(style)
                }
                Column {
                    RadioRow(
                        headline = style.label,
                        supporting = style.description,
                        selected = prefs.monthControlStyle == style,
                        onClick = choose,
                    )
                    MonthControlPreview(style, onClick = choose, modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg))
                }
            }
        }
    }
}

/**
 * How the Wallet lays out the accounts, and the saved cards while NormPix keeps them: the same choices the Wallet's own layout
 * switch makes, with what each does.
 */
@Composable
private fun WalletLayoutSections(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        title = "Accounts in the Wallet",
        footer = "The Wallet has a switch for this above the accounts too.",
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
    if (prefs.walletCardsEnabled) {
        ListSection(
            title = "Cards in the Wallet",
            footer = "The Wallet has a switch for this above the cards too.",
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
    }
}

/**
 * What a month control looks like in [style]: the real one, as it would show last month - both steps live, the way out beside
 * it - whichever style is chosen. A touch on it chooses the style rather than operating the sample, and a screen reader skips
 * it (the row above says what it is).
 */
@Composable
private fun MonthControlPreview(style: MonthControlStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val latest = remember { MonthKey.of(LocalDateTime.now()) }
    // On the page's own colour, as the control is on a page: on the list's tone the track would melt into it.
    Box(modifier.clearAndSetSemantics {}.background(MaterialTheme.colorScheme.background, AppRadius.rLg).padding(AppSpacing.sm)) {
        MonthSwitcher(
            month = latest.previous,
            latest = latest,
            onChanged = {},
            style = style,
            trailing = { MonthScopeButton("Dates", onClick = {}, icon = Icons.Rounded.DateRange, style = style) },
        )
        // A tap, and nothing a keyboard could land on: the row above is the control (a focusable sample here would be a stop with
        // no label and no ring), this is only so a touch on the picture chooses it too.
        Box(Modifier.matchParentSize().pointerInput(onClick) { detectTapGestures { onClick() } })
    }
}

/**
 * What an icon style looks like: three categories as they would be drawn in that style, whichever one is chosen - on the
 * background the user has chosen, or with none.
 */
@Composable
private fun CategoryIconStylePreview(style: CategoryIconStyle) {
    CompositionLocalProvider(LocalCategoryIconStyle provides style) {
        val shape = LocalCategoryIconShape.current
        val bare = shape == CategoryIconShape.None
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (bare) 2.dp else (-6).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            for ((index, key) in listOf("restaurant", "transport", "shopping").withIndex()) {
                val tone = paletteColor(index * 2)
                if (bare) {
                    CategoryGlyph(key, tone.accent, 24.dp)
                } else {
                    Box(
                        Modifier.size(32.dp).background(tone.container, shape.outline).border(2.dp, MaterialTheme.colorScheme.surfaceContainer, shape.outline),
                        contentAlignment = Alignment.Center,
                    ) { CategoryGlyph(key, tone.onContainer, 18.dp) }
                }
            }
        }
    }
}

/** What an icon background looks like: three avatars in that shape (a category, a brand's logo, a bank), whichever one is chosen. */
@Composable
private fun CategoryIconShapePreview(shape: CategoryIconShape) {
    CompositionLocalProvider(LocalCategoryIconShape provides shape) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            for ((index, icon) in listOf("restaurant", "swiggy").withIndex()) {
                CategoryAvatarSample(icon, colorIndex = index * 2, size = 32.dp)
            }
            BankBadge("HDFC Bank", size = 32.dp)
        }
    }
}

/** What a progress style looks like: a bar part-way along, and the loader, drawn in that style whichever is chosen. */
@Composable
private fun ProgressPreview(style: ProgressStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
        AppLinearProgress(Modifier.width(56.dp), progress = { 0.6f }, style = style)
        Spacer(Modifier.width(AppSpacing.sm))
        AppLoadingIndicator(Modifier.size(32.dp), style = style, strokeWidth = 3.dp)
    }
}
