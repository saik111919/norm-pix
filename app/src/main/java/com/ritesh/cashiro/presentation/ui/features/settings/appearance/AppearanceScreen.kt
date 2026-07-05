package com.ritesh.cashiro.presentation.ui.features.settings.appearance

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.preferences.AccentColor
import com.ritesh.cashiro.data.preferences.AppIcon
import com.ritesh.cashiro.data.preferences.AppFont
import com.ritesh.cashiro.data.preferences.NavigationBarStyle
import com.ritesh.cashiro.data.preferences.ThemeStyle
import com.ritesh.cashiro.utils.IconSwitchingUtils
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.PreferenceSwitch
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = "Appearance",
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            MaterialTheme.motionScheme.fastSpatialSpec()
                        ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = Dimensions.Radius.md,
                                        topEnd = Dimensions.Radius.xs,
                                        bottomStart = Dimensions.Radius.md,
                                        bottomEnd = Dimensions.Radius.xs
                                    )
                                )
                                .background(
                                    color = if(themeUiState.isDarkTheme == null) {
                                        MaterialTheme.colorScheme.secondary
                                    } else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(
                                        topStart = Dimensions.Radius.md,
                                        topEnd = Dimensions.Radius.xs,
                                        bottomStart = Dimensions.Radius.md,
                                        bottomEnd = Dimensions.Radius.xs
                                    )
                                )
                                .padding(horizontal = Spacing.xs, vertical = Spacing.md)
                                .clickable(
                                    onClick = {themeViewModel.updateDarkTheme(null)},
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ),
                            contentAlignment = Alignment.Center
                        ){
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if(themeUiState.isDarkTheme == null)
                                        MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "System",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = if(themeUiState.isDarkTheme == null)
                                        MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = Dimensions.Radius.xs,
                                        topEnd = Dimensions.Radius.xs,
                                        bottomStart = Dimensions.Radius.xs,
                                        bottomEnd = Dimensions.Radius.xs
                                    )
                                )
                                .background(
                                    color = if(themeUiState.isDarkTheme == false) {
                                        MaterialTheme.colorScheme.secondary
                                    } else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(
                                        topStart = Dimensions.Radius.xs,
                                        topEnd = Dimensions.Radius.xs,
                                        bottomStart = Dimensions.Radius.xs,
                                        bottomEnd = Dimensions.Radius.xs
                                    )
                                )
                                .padding(horizontal = Spacing.xs, vertical = Spacing.md)
                                .clickable(
                                    onClick = {themeViewModel.updateDarkTheme(false)},
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ),
                            contentAlignment = Alignment.Center
                        ){
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = if(themeUiState.isDarkTheme == false)
                                        MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Light",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = if(themeUiState.isDarkTheme == false)
                                        MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = Dimensions.Radius.xs,
                                        topEnd = Dimensions.Radius.md,
                                        bottomStart = Dimensions.Radius.xs,
                                        bottomEnd = Dimensions.Radius.md
                                    )
                                )
                                .background(
                                    color = if(themeUiState.isDarkTheme == true) {
                                        MaterialTheme.colorScheme.secondary
                                    } else MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(
                                        topStart = Dimensions.Radius.xs,
                                        topEnd = Dimensions.Radius.md,
                                        bottomStart = Dimensions.Radius.xs,
                                        bottomEnd = Dimensions.Radius.md
                                    )
                                )
                                .padding(horizontal = Spacing.xs, vertical = Spacing.md)
                                .clickable(
                                    onClick = {themeViewModel.updateDarkTheme(true)},
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ),
                            contentAlignment = Alignment.Center
                        ){
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = if(themeUiState.isDarkTheme == true)
                                        MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Dark",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = if(themeUiState.isDarkTheme == true)
                                        MaterialTheme.colorScheme.onSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Dynamic Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(Dimensions.Radius.md))
                                    .background(
                                        color = if (themeUiState.themeStyle == ThemeStyle.DYNAMIC)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .clickable {
                                        themeViewModel.updateThemeStyle(ThemeStyle.DYNAMIC)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Dynamic",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (themeUiState.themeStyle == ThemeStyle.DYNAMIC)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Wallpaper Colors",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (themeUiState.themeStyle == ThemeStyle.DYNAMIC)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Default Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(Dimensions.Radius.md))
                                    .background(
                                        color = if (themeUiState.themeStyle == ThemeStyle.DEFAULT)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .clickable {
                                        themeViewModel.updateThemeStyle(ThemeStyle.DEFAULT)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Default",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (themeUiState.themeStyle == ThemeStyle.DEFAULT)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Default Colors",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (themeUiState.themeStyle == ThemeStyle.DEFAULT)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    BlurredAnimatedVisibility(
                        visible = themeUiState.themeStyle == ThemeStyle.DEFAULT,
                        enter = fadeIn() + slideInVertically{-it},
                        exit = fadeOut() + slideOutVertically{-it},
                        modifier = Modifier
                            .animateContentSize(
                            MaterialTheme.motionScheme.defaultSpatialSpec()
                            )
                            .zIndex(-1f)
                    ) {
                        val isDark = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            items(AccentColor.entries) { accent ->
                                val color = getAccentColorForDisplay(accent, isDark)
                                val secondary = getSecondaryColorForDisplay(accent, isDark)
                                val tertiary = getTertiaryColorForDisplay(accent, isDark)
                                val isSelected = themeUiState.accentColor == accent

                                ColorSchemeBox(
                                    accent = color,
                                    secondary = secondary,
                                    tertiary = tertiary,
                                    onClick = { themeViewModel.updateAccentColor(accent) },
                                    isSelected = isSelected
                                )
                            }
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        if (themeUiState.isDarkTheme != false) {
                            PreferenceSwitch(
                                title = "Amoled Black",
                                subtitle = "Use pure black background for deeper contrast",
                                checked = themeUiState.isAmoledMode,
                                onCheckedChange = { themeViewModel.updateAmoledMode(it) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                color = if (themeUiState.isAmoledMode) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DarkMode,
                                            contentDescription = null,
                                            tint = if (themeUiState.isAmoledMode) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                padding = PaddingValues(horizontal = Spacing.md),
                                isFirst = themeUiState.isDarkTheme != false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                isSingle = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PreferenceSwitch(
                                title = "Blur Effects",
                                subtitle = "Enable glassmorphism blur effects in UI components",
                                checked = themeUiState.blurEffects,
                                onCheckedChange = { themeViewModel.updateBlurEffects(it) },
                                padding = PaddingValues(horizontal = Spacing.md),
                                isLast = themeUiState.isDarkTheme != false,
                                isSingle = themeUiState.isDarkTheme == false
                            )
                        }
                    }
                }

                // App Logo Section
                SectionHeader(
                    title = "App Logo",
                    modifier = Modifier.padding(start = Spacing.xl, top = Spacing.md)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item {
                        AppLogoOption(
                            name = "Original",
                            icon = AppIcon.ORIGINAL,
                            backgroundColor = Color(0xFF1F1F1F),
                            drawableResId = R.drawable.cashiro_original,
                            isSelected = themeUiState.currentAppIcon == AppIcon.ORIGINAL,
                            onClick = {
                                IconSwitchingUtils.switchAppIcon(context, AppIcon.ORIGINAL)
                                themeViewModel.updateAppIcon(AppIcon.ORIGINAL)
                            }
                        )
                    }
                    item {
                        AppLogoOption(
                            name = "Anarchy",
                            icon = AppIcon.ANARCHY,
                            backgroundColor = Color(0xFFF5EEE5),
                            drawableResId = R.drawable.cashiro_anarchy,
                            isSelected = themeUiState.currentAppIcon == AppIcon.ANARCHY,
                            onClick = {
                                IconSwitchingUtils.switchAppIcon(context, AppIcon.ANARCHY)
                                themeViewModel.updateAppIcon(AppIcon.ANARCHY)
                            }
                        )
                    }
                    item {
                        AppLogoOption(
                            name = "Comic",
                            icon = AppIcon.COMIC,
                            backgroundColor = Color.Transparent,
                            drawableResId = R.drawable.cashiro_comic,
                            backgroundDrawableResId = R.drawable.ic_logo_comic_bg,
                            isSelected = themeUiState.currentAppIcon == AppIcon.COMIC,
                            onClick = {
                                IconSwitchingUtils.switchAppIcon(context, AppIcon.COMIC)
                                themeViewModel.updateAppIcon(AppIcon.COMIC)
                            }
                        )
                    }
                    item {
                        AppLogoOption(
                            name = "Zenith",
                            icon = AppIcon.ZENITH,
                            backgroundColor = Color(0xFFE6E6E6),
                            drawableResId = R.drawable.cashiro_zenith,
                            isSelected = themeUiState.currentAppIcon == AppIcon.ZENITH,
                            onClick = {
                                IconSwitchingUtils.switchAppIcon(context, AppIcon.ZENITH)
                                themeViewModel.updateAppIcon(AppIcon.ZENITH)
                            }
                        )
                    }
                    item {
                        AppLogoOption(
                            name = "Monochrome",
                            icon = AppIcon.MONOCHROME,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            drawableResId = R.drawable.cashiro_monochrome,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            isSelected = themeUiState.currentAppIcon == AppIcon.MONOCHROME,
                            onClick = {
                                IconSwitchingUtils.switchAppIcon(context, AppIcon.MONOCHROME)
                                themeViewModel.updateAppIcon(AppIcon.MONOCHROME)
                            }
                        )
                    }

                }

                // Navigation Style Section
                SectionHeader(
                    title = "Navigation",
                    modifier = Modifier.padding(start = Spacing.xl, top = Spacing.md)
                )
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            MaterialTheme.motionScheme.defaultSpatialSpec()
                        )
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ){
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        // Floating Option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(
                                    topStart = Dimensions.Radius.md,
                                    topEnd = Dimensions.Radius.xs,
                                    bottomStart = Dimensions.Radius.md,
                                    bottomEnd = Dimensions.Radius.xs
                                ))
                                .background(
                                    color = if (themeUiState.navigationBarStyle == NavigationBarStyle.FLOATING)
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .clickable {
                                    themeViewModel.updateNavigationBarStyle(NavigationBarStyle.FLOATING)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Floating",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (themeUiState.navigationBarStyle == NavigationBarStyle.FLOATING)
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Modern & Sleek",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (themeUiState.navigationBarStyle == NavigationBarStyle.FLOATING)
                                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Normal Option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(
                                    topStart = Dimensions.Radius.xs,
                                    topEnd = Dimensions.Radius.md,
                                    bottomStart = Dimensions.Radius.xs,
                                    bottomEnd = Dimensions.Radius.md
                                ))
                                .background(
                                    color = if (themeUiState.navigationBarStyle == NavigationBarStyle.NORMAL)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .clickable {
                                    themeViewModel.updateNavigationBarStyle(NavigationBarStyle.NORMAL)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Normal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (themeUiState.navigationBarStyle == NavigationBarStyle.NORMAL)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Standard M3",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (themeUiState.navigationBarStyle == NavigationBarStyle.NORMAL)
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    BlurredAnimatedVisibility(
                        visible = themeUiState.navigationBarStyle == NavigationBarStyle.NORMAL,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier.zIndex(-1f)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.5.dp)
                        ) {
                            PreferenceSwitch(
                                title = "Hide Navigation Labels",
                                subtitle = "Hide labels in the navigation bar",
                                checked = themeUiState.hideNavigationLabels,
                                onCheckedChange = { themeViewModel.updateHideNavigationLabels(it) },
                                padding = PaddingValues(0.dp),
                                isFirst = true
                            )
                            PreferenceSwitch(
                                title = "Hide Pill Indicator",
                                subtitle = "Hide the selection indicator in the navigation bar",
                                checked = themeUiState.hidePillIndicator,
                                onCheckedChange = { themeViewModel.updateHidePillIndicator(it) },
                                padding = PaddingValues(0.dp),
                                isLast = true
                            )
                        }
                    }
                }
                
                // Font Family Section
                SectionHeader(
                    title = "Fonts",
                    modifier = Modifier.padding(start = Spacing.xl)
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        // System Default Option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .clip(RoundedCornerShape(
                                    topStart = Dimensions.Radius.md,
                                    topEnd = Dimensions.Radius.xs,
                                    bottomStart = Dimensions.Radius.md,
                                    bottomEnd = Dimensions.Radius.xs
                                ))
                                .background(
                                    color = if (themeUiState.appFont == AppFont.SYSTEM)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .clickable {
                                    themeViewModel.updateAppFont(AppFont.SYSTEM)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Default",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default,
                                    color = if (themeUiState.appFont == AppFont.SYSTEM)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "System",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Default,
                                    color = if (themeUiState.appFont == AppFont.SYSTEM)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // SN Pro Option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)

                                .clip(RoundedCornerShape(
                                    topStart = Dimensions.Radius.xs,
                                    topEnd = Dimensions.Radius.md,
                                    bottomStart = Dimensions.Radius.xs,
                                    bottomEnd = Dimensions.Radius.md
                                ))
                                .background(
                                    color = if (themeUiState.appFont == AppFont.SN_PRO)
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .clickable {
                                    themeViewModel.updateAppFont(AppFont.SN_PRO)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SN Pro",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SNProFontFamily,
                                    color = if (themeUiState.appFont == AppFont.SN_PRO)
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Modern Mono",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = SNProFontFamily,
                                    color = if (themeUiState.appFont == AppFont.SN_PRO)
                                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}


@Composable
fun getAccentColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSEWATER -> Macchiato_Rosewater_dim
            AccentColor.FLAMINGO -> Macchiato_Flamingo_dim
            AccentColor.PINK -> Macchiato_Pink_dim
            AccentColor.MAUVE -> Macchiato_Mauve_dim
            AccentColor.RED -> Macchiato_Red_dim
            AccentColor.PEACH -> Macchiato_Peach_dim
            AccentColor.YELLOW -> Macchiato_Yellow_dim
            AccentColor.GREEN -> Macchiato_Green_dim
            AccentColor.TEAL -> Macchiato_Teal_dim
            AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim
            AccentColor.BLUE -> Macchiato_Blue_dim
            AccentColor.LAVENDER -> Macchiato_Lavender_dim
            AccentColor.PINE_ROSE -> RosePine_Rose
            AccentColor.PINE_IRIS -> RosePine_Iris
            AccentColor.PINE_PINE -> RosePine_Pine
            AccentColor.PINE_GOLD -> RosePine_Gold
            AccentColor.PINE_LOVE -> RosePine_Love
            AccentColor.PINE_FOAM -> RosePine_Foam
            AccentColor.PINE_MUTED -> RosePine_Muted
            AccentColor.PINE_SUBTLE -> RosePine_Subtle
            AccentColor.PINE_TEXT -> RosePine_Text
            AccentColor.PINE_HIGHLIGHT -> RosePine_Highlight
            AccentColor.PINE_SURFACE -> RosePine_Surface
            AccentColor.PINE_OVERLAY -> RosePine_Overlay
        }
    } else {
        when (accent) {
            AccentColor.ROSEWATER -> Latte_Rosewater
            AccentColor.FLAMINGO -> Latte_Flamingo
            AccentColor.PINK -> Latte_Pink
            AccentColor.MAUVE -> Latte_Mauve
            AccentColor.RED -> Latte_Red
            AccentColor.PEACH -> Latte_Peach
            AccentColor.YELLOW -> Latte_Yellow
            AccentColor.GREEN -> Latte_Green
            AccentColor.TEAL -> Latte_Teal
            AccentColor.SAPPHIRE -> Latte_Sapphire
            AccentColor.BLUE -> Latte_Blue
            AccentColor.LAVENDER -> Latte_Lavender
            AccentColor.PINE_ROSE -> Dawn_Rose
            AccentColor.PINE_IRIS -> Dawn_Iris
            AccentColor.PINE_PINE -> Dawn_Pine
            AccentColor.PINE_GOLD -> Dawn_Gold
            AccentColor.PINE_LOVE -> Dawn_Love
            AccentColor.PINE_FOAM -> Dawn_Foam
            AccentColor.PINE_MUTED -> Dawn_Muted
            AccentColor.PINE_SUBTLE -> Dawn_Subtle
            AccentColor.PINE_TEXT -> Dawn_Text
            AccentColor.PINE_HIGHLIGHT -> Dawn_Highlight
            AccentColor.PINE_SURFACE -> Dawn_Surface
            AccentColor.PINE_OVERLAY -> Dawn_Overlay
        }
    }
}

@Composable
fun getSecondaryColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSEWATER -> Macchiato_Rosewater_dim_secondary
            AccentColor.FLAMINGO -> Macchiato_Flamingo_dim_secondary
            AccentColor.PINK -> Macchiato_Pink_dim_secondary
            AccentColor.MAUVE -> Macchiato_Mauve_dim_secondary
            AccentColor.RED -> Macchiato_Red_dim_secondary
            AccentColor.PEACH -> Macchiato_Peach_dim_secondary
            AccentColor.YELLOW -> Macchiato_Yellow_dim_secondary
            AccentColor.GREEN -> Macchiato_Green_dim_secondary
            AccentColor.TEAL -> Macchiato_Teal_dim_secondary
            AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim_secondary
            AccentColor.BLUE -> Macchiato_Blue_dim_secondary
            AccentColor.LAVENDER -> Macchiato_Lavender_dim_secondary
            AccentColor.PINE_ROSE -> RosePine_Rose_secondary
            AccentColor.PINE_IRIS -> RosePine_Iris_secondary
            AccentColor.PINE_PINE -> RosePine_Pine_secondary
            AccentColor.PINE_GOLD -> RosePine_Gold_secondary
            AccentColor.PINE_LOVE -> RosePine_Love_secondary
            AccentColor.PINE_FOAM -> RosePine_Foam_secondary
            AccentColor.PINE_MUTED -> RosePine_Muted_secondary
            AccentColor.PINE_SUBTLE -> RosePine_Subtle_secondary
            AccentColor.PINE_TEXT -> RosePine_Text_secondary
            AccentColor.PINE_HIGHLIGHT -> RosePine_Highlight_secondary
            AccentColor.PINE_SURFACE -> RosePine_Surface_secondary
            AccentColor.PINE_OVERLAY -> RosePine_Overlay_secondary
        }
    } else {
        when (accent) {
            AccentColor.ROSEWATER -> Latte_Rosewater_secondary
            AccentColor.FLAMINGO -> Latte_Flamingo_secondary
            AccentColor.PINK -> Latte_Pink_secondary
            AccentColor.MAUVE -> Latte_Mauve_secondary
            AccentColor.RED -> Latte_Red_secondary
            AccentColor.PEACH -> Latte_Peach_secondary
            AccentColor.YELLOW -> Latte_Yellow_secondary
            AccentColor.GREEN -> Latte_Green_secondary
            AccentColor.TEAL -> Latte_Teal_secondary
            AccentColor.SAPPHIRE -> Latte_Sapphire_secondary
            AccentColor.BLUE -> Latte_Blue_secondary
            AccentColor.LAVENDER -> Latte_Lavender_secondary
            AccentColor.PINE_ROSE -> Dawn_Rose_secondary
            AccentColor.PINE_IRIS -> Dawn_Iris_secondary
            AccentColor.PINE_PINE -> Dawn_Pine_secondary
            AccentColor.PINE_GOLD -> Dawn_Gold_secondary
            AccentColor.PINE_LOVE -> Dawn_Love_secondary
            AccentColor.PINE_FOAM -> Dawn_Foam_secondary
            AccentColor.PINE_MUTED -> Dawn_Muted_secondary
            AccentColor.PINE_SUBTLE -> Dawn_Subtle_secondary
            AccentColor.PINE_TEXT -> Dawn_Text_secondary
            AccentColor.PINE_HIGHLIGHT -> Dawn_Highlight_secondary
            AccentColor.PINE_SURFACE -> Dawn_Surface_secondary
            AccentColor.PINE_OVERLAY -> Dawn_Overlay_secondary
        }
    }
}

@Composable
fun getTertiaryColorForDisplay(accent: AccentColor, isDark: Boolean): Color {
    return if (isDark) {
        when (accent) {
            AccentColor.ROSEWATER -> Macchiato_Rosewater_dim_tertiary
            AccentColor.FLAMINGO -> Macchiato_Flamingo_dim_tertiary
            AccentColor.PINK -> Macchiato_Pink_dim_tertiary
            AccentColor.MAUVE -> Macchiato_Mauve_dim_tertiary
            AccentColor.RED -> Macchiato_Red_dim_tertiary
            AccentColor.PEACH -> Macchiato_Peach_dim_tertiary
            AccentColor.YELLOW -> Macchiato_Yellow_dim_tertiary
            AccentColor.GREEN -> Macchiato_Green_dim_tertiary
            AccentColor.TEAL -> Macchiato_Teal_dim_tertiary
            AccentColor.SAPPHIRE -> Macchiato_Sapphire_dim_tertiary
            AccentColor.BLUE -> Macchiato_Blue_dim_tertiary
            AccentColor.LAVENDER -> Macchiato_Lavender_dim_tertiary
            AccentColor.PINE_ROSE -> RosePine_Rose_tertiary
            AccentColor.PINE_IRIS -> RosePine_Iris_tertiary
            AccentColor.PINE_PINE -> RosePine_Pine_tertiary
            AccentColor.PINE_GOLD -> RosePine_Gold_tertiary
            AccentColor.PINE_LOVE -> RosePine_Love_tertiary
            AccentColor.PINE_FOAM -> RosePine_Foam_tertiary
            AccentColor.PINE_MUTED -> RosePine_Muted_tertiary
            AccentColor.PINE_SUBTLE -> RosePine_Subtle_tertiary
            AccentColor.PINE_TEXT -> RosePine_Text_tertiary
            AccentColor.PINE_HIGHLIGHT -> RosePine_Highlight_tertiary
            AccentColor.PINE_SURFACE -> RosePine_Surface_tertiary
            AccentColor.PINE_OVERLAY -> RosePine_Overlay_tertiary
        }
    } else {
        when (accent) {
            AccentColor.ROSEWATER -> Latte_Rosewater_tertiary
            AccentColor.FLAMINGO -> Latte_Flamingo_tertiary
            AccentColor.PINK -> Latte_Pink_tertiary
            AccentColor.MAUVE -> Latte_Mauve_tertiary
            AccentColor.RED -> Latte_Red_tertiary
            AccentColor.PEACH -> Latte_Peach_tertiary
            AccentColor.YELLOW -> Latte_Yellow_tertiary
            AccentColor.GREEN -> Latte_Green_tertiary
            AccentColor.TEAL -> Latte_Teal_tertiary
            AccentColor.SAPPHIRE -> Latte_Sapphire_tertiary
            AccentColor.BLUE -> Latte_Blue_tertiary
            AccentColor.LAVENDER -> Latte_Lavender_tertiary
            AccentColor.PINE_ROSE -> Dawn_Rose_tertiary
            AccentColor.PINE_IRIS -> Dawn_Iris_tertiary
            AccentColor.PINE_PINE -> Dawn_Pine_tertiary
            AccentColor.PINE_GOLD -> Dawn_Gold_tertiary
            AccentColor.PINE_LOVE -> Dawn_Love_tertiary
            AccentColor.PINE_FOAM -> Dawn_Foam_tertiary
            AccentColor.PINE_MUTED -> Dawn_Muted_tertiary
            AccentColor.PINE_SUBTLE -> Dawn_Subtle_tertiary
            AccentColor.PINE_TEXT -> Dawn_Text_tertiary
            AccentColor.PINE_HIGHLIGHT -> Dawn_Highlight_tertiary
            AccentColor.PINE_SURFACE -> Dawn_Surface_tertiary
            AccentColor.PINE_OVERLAY -> Dawn_Overlay_tertiary
        }
    }
}

@Composable
fun ColorSchemeBox(
    accent: Color,
    secondary: Color,
    tertiary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isSelected: Boolean = false
){
    Box(
        modifier = modifier
            .size(110.dp)
            .clip(RoundedCornerShape(Spacing.md))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = accent.copy(0.7f),
                        shape = RoundedCornerShape(Spacing.md)
                    )
                } else Modifier
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ){
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Abc",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Column{
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .width(27.dp)
                        .background(
                            color = tertiary,
                            shape = RoundedCornerShape(Spacing.sm)
                        )
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .width(47.dp)
                        .background(
                            color = secondary,
                            shape = RoundedCornerShape(Spacing.sm)
                        )
                )
            }

            Box(
                modifier = Modifier.align(Alignment.End).size(20.dp).background(
                    accent,
                    RoundedCornerShape(Spacing.xs)
                )
            )
        }
    }
}

@Composable
fun AppLogoOption(
    name: String,
    icon: AppIcon,
    backgroundColor: Color,
    drawableResId: Int,
    isSelected: Boolean,
    iconTint: Color? = null,
    backgroundDrawableResId: Int? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(Dimensions.Radius.lg)),
            contentAlignment = Alignment.Center
        ) {
            if (backgroundDrawableResId != null) {
                Image(
                    painter = painterResource(id = backgroundDrawableResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                )
            }
            
            Image(
                painter = painterResource(id = drawableResId),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
                colorFilter = iconTint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) }
            )
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(Dimensions.Radius.lg)
                        )
                )
            }
        }
        
        Surface(
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = CircleShape
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 4.dp)
            )
        }
    }
}
