package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.normpix.finance.ui.CategoriesContent
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.theme.AppSpacing

/** What the page is called: on its own app bar, and on the Settings row that opens it. */
const val BANKS_AND_CATEGORIES_TITLE = "Banks & Categories"

/** The two sides of the page, in the order its switch shows them. */
enum class BanksAndCategoriesTab { Banks, Categories }

/** The two entries of the page's switch: the banks with their logos, and the categories of the ledger. */
private val BanksAndCategoriesTabs = listOf(
    AppTab("Banks", Icons.Outlined.AccountBalance),
    AppTab("Categories", Icons.Outlined.Category),
)

/**
 * Settings > Banks & Categories: every bank NormPix knows, with its logo, and the ledger's categories, behind one two-way
 * switch - the same primary switch the app's tabs use, with the chosen side's list under it. The switch is the first row of
 * that list and scrolls away with it; each side keeps exactly what its own page had, down to its search, its chips and its
 * sheet, and only the chosen side's controls are on the page.
 *
 * [initialTab] is the side the page opens on: Banks from the Settings row, Categories from the route the Finance settings row,
 * the "+" menu's tile and Settings search open (see [com.example.normpix.navigation.Routes.CATEGORIES]). The choice is kept
 * across rotation, and is the user's from the moment they touch the switch.
 */
@Composable
fun BanksAndCategoriesPage(initialTab: BanksAndCategoriesTab = BanksAndCategoriesTab.Banks) {
    var chosen by rememberSaveable { mutableStateOf(initialTab.name) }
    val tab = BanksAndCategoriesTab.entries.firstOrNull { it.name == chosen } ?: initialTab
    val tabBar: @Composable () -> Unit = {
        AppPrimaryTabs(
            tabs = BanksAndCategoriesTabs,
            selectedIndex = tab.ordinal,
            onSelect = { index -> chosen = BanksAndCategoriesTab.entries[index].name },
            modifier = Modifier.padding(AppSpacing.page).padding(bottom = AppSpacing.sm),
        )
    }
    // Each side keeps its search, its chips and its place in the list while the other is shown, as the app's primary
    // switches do, without leaving both long lists composed at once.
    val savedSides = rememberSaveableStateHolder()
    savedSides.SaveableStateProvider(tab.name) {
        when (tab) {
            BanksAndCategoriesTab.Banks -> BanksContent(BANKS_AND_CATEGORIES_TITLE, tabBar)
            BanksAndCategoriesTab.Categories -> CategoriesContent(BANKS_AND_CATEGORIES_TITLE, tabBar)
        }
    }
}
