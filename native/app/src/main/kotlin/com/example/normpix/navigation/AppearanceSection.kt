package com.example.normpix.navigation

/**
 * The screens Settings > Appearance opens: one for each part of how the app looks and moves, under the theme control that
 * Appearance itself keeps. The name is what a route carries (see [Routes.appearanceSection]), so a name is never changed.
 *
 * What each one looks like on the Appearance page (its group, icon and summary) and the screen it opens are in
 * `AppearanceSectionPages.kt`; an entry added here has to be given those there, or the build says what is missing.
 */
enum class AppearanceSection(val title: String) {
    Colors("Colors"),
    AppIcon("App icon"),
    InterfaceFont("Interface font"),
    Effects("Effects"),
    ProgressIndicators("Progress indicators"),
    CategoryIcons("Category icons"),
    PageTransitions("Page transitions"),
    SheetTransitions("Sheet & dialog transitions"),
    BackButton("Back button style"),
    ;

    companion object {
        /** The section a route's [name] stands for, or null for a name that is none (a stale link). */
        fun named(name: String?): AppearanceSection? = entries.firstOrNull { it.name == name }
    }
}
