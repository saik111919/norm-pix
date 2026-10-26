package com.example.normpix.navigation

/**
 * The screens Settings > Appearance opens: one for each part of how the app looks and moves, under the theme control that
 * Appearance itself keeps. Related choices share a screen - how things are laid out (Home's top spending, the Wallet's accounts
 * and cards) on one, the controls a page is driven by on another - rather than each choice being a screen of its own.
 *
 * The name is what a route carries (see [Routes.appearanceSection]), so a name is never changed; a screen that was folded into
 * another keeps its name as an alias ([named]), and a link to it opens the screen it is now part of.
 *
 * What each one looks like on the Appearance page (its group, icon and summary) and the screen it opens are in
 * `AppearanceSectionPages.kt`; an entry added here has to be given those there, or the build says what is missing.
 */
enum class AppearanceSection(val title: String) {
    Colors("Colors"),
    AppIcon("App icon"),
    InterfaceFont("Interface font"),
    /** Category icons, bank logos and the shape behind them. */
    CategoryIcons("Icons"),
    ProgressIndicators("Progress indicators"),
    /** Pages, and sheets & dialogs. */
    Transitions("Transitions"),
    /** Background blur, and the saved cards' tilt. */
    Effects("Effects"),
    /** Bottom controls, the back button and the month control. */
    Controls("Navigation & controls"),
    /** Home's top spending, and the Wallet's accounts and cards. */
    Layouts("Layouts"),
    ;

    companion object {
        /** Screens that are now part of another: their names still open it. */
        private val folded = mapOf(
            "PageTransitions" to Transitions,
            "SheetTransitions" to Transitions,
            "BackButton" to Controls,
            "BottomControls" to Controls,
            "FooterLabels" to Controls,
            "MonthControl" to Controls,
            "TopSpending" to Layouts,
        )

        /** The section a route's [name] stands for, or null for a name that is none (a stale link). */
        fun named(name: String?): AppearanceSection? = entries.firstOrNull { it.name == name } ?: folded[name]
    }
}
