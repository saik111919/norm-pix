package com.ritesh.cashiro.presentation.navigation

import android.view.HapticFeedbackConstants
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.More
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.ritesh.cashiro.data.preferences.NavigationBarStyle
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeDefaults.tint
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

data class FabConfig(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit = {},
    val dropdownContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null
)

/**
 * Bottom navigation bar component that supports both NORMAL and FLOATING styles.
 * Used in flat navigation structure where all screens are at the same NavHost level.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class
)
@Composable
fun CashiroBottomNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    currentDestination: NavDestination?,
    navigationBarStyle: NavigationBarStyle,
    hideLabels: Boolean,
    hidePill: Boolean,
    blurEffects: Boolean,
    visible: Boolean,
    hazeState: HazeState = remember { HazeState() },
    fabConfig: FabConfig? = null
) {
    val navigationItems = listOf(BottomNavItem.Home, BottomNavItem.Analytics, BottomNavItem.Transactions)
    val containerColor = MaterialTheme.colorScheme.surface
    val view = LocalView.current

    Box(modifier = modifier) {
        // NORMAL style NavigationBar
        BlurredAnimatedVisibility(
            visible = visible && navigationBarStyle == NavigationBarStyle.NORMAL,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    thickness = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = if (blurEffects) 0.5f else 1f
                    ),
                    tonalElevation = 2.dp,
                    modifier = Modifier.then(
                        if (blurEffects) Modifier.hazeEffect(
                            state = hazeState,
                            block = fun HazeEffectScope.() {
                                style = HazeDefaults.style(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(containerColor),
                                    blurRadius = 20.dp,
                                    noiseFactor = -1f,
                                )
                                blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                            }
                        ) else Modifier
                    )
                ) {
                    navigationItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route?.contains(item.destinationType.qualifiedName ?: "") == true
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                val startDestId = navController.graph.findStartDestination().id
                                if (item.destination == Home) {
                                    navController.popBackStack(Home, inclusive = false, saveState = true)
                                } else if (selected) {
                                    navController.safeNavigate(item.destination) {
                                        popUpTo(item.destinationType) {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.safeNavigate(item.destination) {
                                        popUpTo(startDestId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (selected) {
                                        if (hidePill) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(
                                        if (hidePill && hideLabels) 28.dp else 24.dp
                                    )
                                )
                            },
                            label = if (hideLabels) null else {
                                {
                                    Text(
                                        text = item.title,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = if (hidePill) Color.Transparent
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        // FLOATING style HorizontalFloatingToolbar
        BlurredAnimatedVisibility(
            visible = visible && navigationBarStyle == NavigationBarStyle.FLOATING,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalFloatingToolbar(
                        modifier = Modifier
                            .clip(FloatingToolbarDefaults.ContainerShape)
                            .then(
                                if (blurEffects) Modifier.hazeEffect(
                                    state = hazeState,
                                    block = fun HazeEffectScope.() {
                                        style = HazeDefaults.style(
                                            backgroundColor = Color.Transparent,
                                            blurRadius = 20.dp,
                                            noiseFactor = -1f,
                                        )
                                        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                                    }
                                ) else Modifier
                            )
                            .zIndex(1000f)
                            .animateContentSize(
                                MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                alpha = if (blurEffects) 0.7f else 1f
                            ),
                        ),
                        expanded = true,
                    ) {
                        navigationItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route?.contains(item.destinationType.qualifiedName ?: "") == true
                            } == true

                            TonalToggleButton(
                                checked = selected,
                                onCheckedChange = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    val startDestId = navController.graph.findStartDestination().id
                                    if (item.destination == Home) {
                                        navController.popBackStack(Home, inclusive = false, saveState = true)
                                    } else if (selected) {
                                        navController.safeNavigate(item.destination) {
                                            popUpTo(item.destinationType) {
                                                inclusive = true
                                            }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.safeNavigate(item.destination) {
                                            popUpTo(startDestId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                shapes = ToggleButtonDefaults.shapes(
                                    shape = FloatingToolbarDefaults.ContainerShape,
                                    checkedShape = RoundedCornerShape(30.dp)
                                ),
                                colors = ToggleButtonDefaults.toggleButtonColors(
                                    containerColor = if(blurEffects)
                                        MaterialTheme.colorScheme.surfaceBright.copy(0.6f)
                                    else MaterialTheme.colorScheme.surfaceBright,
                                    contentColor = MaterialTheme.colorScheme.inverseSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceBright.copy(0.7f),
                                    disabledContentColor = MaterialTheme.colorScheme.inverseSurface.copy(0.5f),
                                    checkedContainerColor =  MaterialTheme.colorScheme.tertiaryContainer,
                                    checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                            ) {
                                Icon(imageVector = item.icon, contentDescription = item.title)
                                AnimatedVisibility(
                                    visible = selected,
                                    enter = fadeIn() + expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                                    exit = fadeOut() + shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                                ) {
                                    Text(
                                        text = item.title,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    BlurredAnimatedVisibility(
                        visible = fabConfig != null,
                        enter = fadeIn() + expandHorizontally(
                            expandFrom = Alignment.Start,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        ),
                        exit = fadeOut() + shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        )
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        val capturedDropdown = remember(expanded) { fabConfig?.dropdownContent }

                        Box(contentAlignment = Alignment.BottomEnd) {
                            FloatingActionButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    if (fabConfig?.dropdownContent != null) {
                                        expanded = !expanded
                                    } else {
                                        fabConfig?.onClick()
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = fabConfig?.contentDescription,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            val dropContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            if (capturedDropdown != null) {
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .then(
                                        if (blurEffects) Modifier.hazeEffect(
                                            state = hazeState,
                                            block = fun HazeEffectScope.() {
                                                style = HazeDefaults.style(
                                                    backgroundColor = Color.Transparent,
                                                    tint = HazeTint(dropContainerColor.copy(0.5f)),
                                                    blurRadius = 36.dp,
                                                    noiseFactor = -1f,
                                                )
                                                blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                                            }
                                        ) else Modifier
                                    ),
                                    containerColor = dropContainerColor.copy(
                                        alpha = if (blurEffects) 0.7f else 1f
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    capturedDropdown.invoke(this) { expanded = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
