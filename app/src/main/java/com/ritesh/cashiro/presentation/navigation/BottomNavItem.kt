package com.ritesh.cashiro.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.ritesh.cashiro.presentation.ui.icons.AiCommentary
import com.ritesh.cashiro.presentation.ui.icons.FavoriteChart
import com.ritesh.cashiro.presentation.ui.icons.Home
import com.ritesh.cashiro.presentation.ui.icons.ReceiptItem
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import kotlin.reflect.KClass
import com.ritesh.cashiro.presentation.navigation.Home as HomeDestination
import com.ritesh.cashiro.presentation.navigation.Analytics as AnalyticsDestination
import com.ritesh.cashiro.presentation.navigation.Transactions as TransactionsDestination

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val destination: Any,
    val destinationType: KClass<*>
) {
    data object Home : BottomNavItem(
        route = "home",
        title = "Home",
        icon = Iconax.Home,
        destination = HomeDestination,
        destinationType = HomeDestination::class
    )
    
    data object Analytics : BottomNavItem(
        route = "analytics",
        title = "Analytics",
        icon = Iconax.FavoriteChart,
        destination = AnalyticsDestination,
        destinationType = AnalyticsDestination::class
    )

    data object Transactions : BottomNavItem(
        route = "transactions",
        title = "Transactions",
        icon = Iconax.ReceiptItem,
        destination = TransactionsDestination(),
        destinationType = TransactionsDestination::class
    )
}