package com.ritesh.cashiro.presentation.ui.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import com.ritesh.cashiro.presentation.ui.theme.LocalBlurEffects
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeDefaults.tint
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CustomTitleTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    hasBackButton: Boolean = false,
    hasActionButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    extraInfoCard: @Composable () -> Unit = {},
    hazeState: HazeState = HazeState(),
    blurEffects: Boolean = LocalBlurEffects.current
) {
    val collapsedFraction = scrollBehaviorLarge.state.collapsedFraction

    // LargeTopAppBar
    if(scrollBehaviorLarge != scrollBehaviorSmall) {
        LargerTopAppBar(
            scrollBehaviorLarge = scrollBehaviorLarge,
            title = title,
            hasBackButton = hasBackButton,
            collapsedFraction = collapsedFraction,
            actionContent = actionContent,
            navigationContent = navigationContent,
            extraInfoCard = extraInfoCard,
            hazeState = hazeState,
            blurEffects = blurEffects,
            themeColors = MaterialTheme.colorScheme
        )
    }

    // Regular TopAppBar
    RegularTopAppBar(
        scrollBehaviorSmall = scrollBehaviorSmall,
        title = title,
        hasBackButton = hasBackButton,
        hasActionButton = hasActionButton,
        actionContent = actionContent,
        navigationContent = navigationContent,
        collapsedFraction = if(scrollBehaviorLarge != scrollBehaviorSmall)collapsedFraction else 1f,
        modifier = modifier,
        hazeState = hazeState,
        blurEffects = blurEffects
    )

}


@Composable
private fun Modifier.animatedOffsetModifier(
    hasBackButton: Boolean,
    hasActionButton: Boolean = false,
    isHomeScreen: Boolean = false,
): Modifier {
    // Define the target offset based on conditions
    val targetOffsetX = when {
        hasBackButton && hasActionButton-> 0.dp
        isHomeScreen-> (0).dp
        hasBackButton -> (-26).dp
        else -> (-10).dp
    }

    // Convert to pixels for animation
    val density = LocalDensity.current
    val targetOffsetXPx = with(density) { targetOffsetX.toPx() }

    val transition = updateTransition(
        targetState = Triple(hasBackButton, false, targetOffsetXPx), // false for isInSelectionMode
        label = "offsetTransition"
    )

    val animatedOffsetX by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "offsetX"
    ) { (_, _, offset) -> offset }

    // Apply offset directly as a float value instead of rounding to Int
    return this
        .fillMaxWidth()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                // Use the exact float value for positioning
                placeable.placeRelative(x = animatedOffsetX.toInt(), y = 0)
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
private fun LargerTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    hasBackButton: Boolean = false,
    collapsedFraction: Float,
    extraInfoCard: @Composable () -> Unit = {},
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    hazeState: HazeState,
    blurEffects: Boolean = true,
    themeColors: ColorScheme,

    ){
    val homeTitle = stringResource(R.string.cashiro_title)
    LargeTopAppBar(
        title = {
            TitleForLargeTopAppBar(
                title = title,
                modifier = modifier ,
                extraInfoCard = extraInfoCard,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =  Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        navigationIcon = {
            NavigationForLargeTopAppBar(
                hasBackButton = hasBackButton,
                navigationContent = navigationContent,
                isHomeScreen = title == homeTitle
            )
        },
        actions = {
            ActionForLargeTopAppBar(
                actionContent = actionContent,
                isHomeScreen = title == homeTitle
            )
        },
        collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
        expandedHeight = if (title == homeTitle) 150.dp else 110.dp,
        windowInsets = WindowInsets(0.dp),
        scrollBehavior = scrollBehaviorLarge,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (blurEffects) Modifier.hazeEffect(
                    state = hazeState,
                    block = fun HazeEffectScope.() {
                        style = HazeDefaults.style(
                            backgroundColor = Color.Transparent,
                            tint = tint(backgroundColor),
                            blurRadius = 10.dp,
                            noiseFactor = -1f,
                        )
                        progressive =
                            HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    }
                ) else Modifier
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        themeColors.background,
                        Color.Transparent
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .alpha(1f - collapsedFraction)
    )
}

@Composable
private fun TitleForLargeTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    extraInfoCard: @Composable () -> Unit = {},
){
    val homeTitle = stringResource(R.string.cashiro_title)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        BlurredAnimatedVisibility(
            visible = title != homeTitle,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp)
            )
        }
        extraInfoCard()
    }
}

@Composable
private fun NavigationForLargeTopAppBar(
    hasBackButton: Boolean = false,
    isHomeScreen: Boolean = false,
    navigationContent: @Composable () -> Unit = {},
){
    BlurredAnimatedVisibility(
        visible = hasBackButton && !isHomeScreen,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        navigationContent()
    }
}

@Composable
private fun ActionForLargeTopAppBar(
    actionContent: @Composable () -> Unit = {},
    isHomeScreen: Boolean = false,
){
    BlurredAnimatedVisibility(
        visible = !isHomeScreen,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        actionContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
private fun RegularTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    title: String,
    hasBackButton: Boolean = false,
    hasActionButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    collapsedFraction: Float,
    hazeState: HazeState,
    blurEffects: Boolean = true
){
    BlurredAnimatedVisibility(
        visible = collapsedFraction > 0.01f,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val homeTitle = stringResource(R.string.cashiro_title)
        val isHomeScreen = title == homeTitle

        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.animatedOffsetModifier(
                        hasBackButton = hasBackButton,
                        hasActionButton = hasActionButton,
                        isHomeScreen = title == homeTitle,
                    )
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            navigationIcon = {
                BlurredAnimatedVisibility(
                    visible = hasBackButton || isHomeScreen,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    navigationContent()
                }
            },
            actions = {
                actionContent()
            },
            scrollBehavior = scrollBehaviorSmall,
            windowInsets = WindowInsets(0.dp),
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (blurEffects) Modifier.hazeEffect(
                        state = hazeState,
                        block = fun HazeEffectScope.() {
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                blurRadius = 10.dp,
                                noiseFactor = -1f,
                            )
                            progressive =
                                HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                    ) else Modifier
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            Color.Transparent
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .alpha(collapsedFraction)
        )
    }
}

