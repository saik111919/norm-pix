package com.ritesh.cashiro.presentation.ui.features.settings.about

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ritesh.cashiro.BuildConfig
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.effects.rememberOverscrollFlingBehavior
import com.ritesh.cashiro.presentation.ui.components.*
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.features.settings.SettingsViewModel
import com.ritesh.cashiro.presentation.common.icons.IconResource
import com.ritesh.cashiro.presentation.ui.icons.*
import com.ritesh.cashiro.presentation.ui.theme.*
import androidx.compose.foundation.clickable
import com.ritesh.cashiro.core.Constants
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToDeveloper: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    blurEffects: Boolean
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.about),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            //Banner image
            Box(
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) {
                val primary = MaterialTheme.colorScheme.primary
                val tertiary = MaterialTheme.colorScheme.tertiary
                val alternatingIcons = remember {
                    listOf(
                        IconResource.VectorIcon(Iconax.Cashiro, primary),
                        IconResource.VectorIcon(Iconax.CashiroOutline, tertiary)
                    )
                }

                TiledScrollingIconBackground(
                    iconResources = alternatingIcons,
                    opacity = 0.05f,
                    iconSize = 56.dp
                )
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .verticalScroll(
                        state = scrollState,
                        flingBehavior = rememberOverscrollFlingBehavior { scrollState }
                    )
                    .padding(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content +
                                paddingValues.calculateTopPadding(),
                        bottom = 0.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // App Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.Padding.card),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.cashiro),
                            contentDescription = stringResource(R.string.cashiro_logo_cd),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.Padding.content))

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.version_format, BuildConfig.VERSION_NAME, settingsViewModel.databaseVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }

                val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(8000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                AboutDeveloperItem(
                    title = stringResource(R.string.developed_by),
                    subtitle = "modestcat0309@gmail.com",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:modestcat0309@gmail.com".toUri()
                            putExtra(Intent.EXTRA_SUBJECT, "Feedback for Cashiro")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                    },
                    position = ListItemPosition.Single,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .rotate(rotation)
                                .clip(
                                    shape = MaterialShapes.Cookie9Sided.toShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.lead_developer),
                                contentDescription = stringResource(R.string.lead_developer_cd),
                                modifier = Modifier
                                    .rotate(-rotation)
                                    .size(48.dp)
                            )
                        }
                    },
                )

                // Connect Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    AboutListItem(
                        title = stringResource(R.string.github),
                        subtitle = "saik111919/norm-pix",
                        icon = Iconax.Github,
                        iconColor = green_dark,
                        iconBackground = green_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.GITHUB_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Top
                    )
                    AboutListItem(
                        title = stringResource(R.string.discord),
                        subtitle = stringResource(R.string.join_community),
                        icon = Iconax.Discord,
                        iconColor = purple_dark,
                        iconBackground = purple_light,
                        isLink = true,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_VIEW, Constants.Links.DISCORD_URL.toUri())
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Bottom
                    )

                }

                // Links Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {

                    AboutListItem(
                        title = stringResource(R.string.report_bug),
                        subtitle = stringResource(R.string.report_bug_desc),
                        icon = Iconax.Ghost,
                        iconColor = red_dark,
                        iconBackground = red_light,
                        isLink = true,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Constants.Links.REPORT_BUG_URL.toUri()
                            )
                            context.startActivity(intent)
                        },
                        position = ListItemPosition.Top
                    )

                    AboutListItem(
                        title = stringResource(R.string.licenses),
                        subtitle = stringResource(R.string.licenses_desc),
                        icon = Iconax.Status,
                        iconColor = orange_dark,
                        iconBackground = orange_light,
                        onClick = onNavigateToLicenses,
                        position = ListItemPosition.Middle
                    )

                    AboutListItem(
                        title = stringResource(R.string.developer_options),
                        subtitle = stringResource(R.string.developer_options_desc),
                        icon = Iconax.CodeCircle,
                        iconColor = grey_dark,
                        iconBackground = grey_light,
                        onClick = onNavigateToDeveloper,
                        position = ListItemPosition.Middle
                    )

                    AboutListItem(
                        title = stringResource(R.string.delete_all_data),
                        subtitle = stringResource(R.string.delete_all_data_desc),
                        icon = Icons.Rounded.Delete,
                        iconColor = red_dark,
                        iconBackground = red_light,
                        onClick = { showDeleteDialog = true },
                        position = ListItemPosition.Bottom,
                        textColor = MaterialTheme.colorScheme.error
                    )
                }

                /*
                Need to check Google play policy for donation. for now, I'll comment out these sections
                 */

//                CashiroCard(
//                    modifier = Modifier.fillMaxWidth(),
//                ){
//                    Row(
//                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
//                        verticalAlignment = Alignment.CenterVertically
//                    ){
//                        Box(
//                            modifier = Modifier
//                                .size(24.dp)
//                                .background(
//                                    color = yellow_light,
//                                    shape = CircleShape
//                                ),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = Icons.Rounded.Favorite,
//                                contentDescription = null,
//                                tint = yellow_dark,
//                                modifier = Modifier.size(14.dp)
//                            )
//                        }
//                        Text(
//                            text = "Support Development",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold,
//                            color = MaterialTheme.colorScheme.error
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(Spacing.md))
//                    Text(
//                        text = "Cashiro is developed and maintained with passion. If you find the app helpful, consider supporting the development.",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
//                    )
//                }
//
//                // Support Section
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(listItemPadding)
//                        .padding(Dimensions.Padding.card),
//                    horizontalAlignment = Alignment.CenterHorizontally,
//                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
//                ) {
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceEvenly,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        SupportIcon(
//                            icon = Iconax.BuyMeCoffeeIcon,
//                            iconBackground = yellow_light,
//                            onClick = {
//                                val intent = Intent(
//                                    Intent.ACTION_VIEW,
//                                    "https://buymeacoffee.com/modestcat0a".toUri()
//                                )
//                                context.startActivity(intent)
//                            }
//                        )
//
//                        SupportIcon(
//                            icon = Iconax.KoFiIcon,
//                            iconBackground = cyan_light,
//                            onClick = {
//                                val intent = Intent(
//                                    Intent.ACTION_VIEW,
//                                    "https://ko-fi.com/modestcat03".toUri()
//                                )
//                                context.startActivity(intent)
//                            }
//                        )
//
//                        SupportIcon(
//                            icon = Iconax.BhimUpiIcon,
//                            iconBackground = orange_light,
//                            onClick = {
//                                val upiUri =
//                                    "upi://pay?pa=riteshkanwar0309@axl&pn=Ritesh%20Kanwar&cu=INR".toUri()
//                                val intent = Intent(Intent.ACTION_VIEW, upiUri)
//                                try {
//                                    context.startActivity(intent)
//                                } catch (e: Exception) {
//                                    // Handle no UPI app case or show message
//                                }
//                            }
//                        )
//                    }
//                }

                Spacer(modifier = Modifier.height(Dimensions.Padding.card))

                Text(
                    text = stringResource(R.string.built_with_love),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                )

            }
        }
    }

    if (showDeleteDialog) {
        val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Iconax.Danger,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.delete_all_data_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.delete_all_data_confirm_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    ) {
                        Button(
                            onClick = { showDeleteDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(
                                topStart = Dimensions.Radius.xxl,
                                topEnd = Dimensions.Radius.xs,
                                bottomStart = Dimensions.Radius.xxl,
                                bottomEnd = Dimensions.Radius.xs
                            ),
                            modifier = Modifier
                                .padding(start = Spacing.xl)
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Button(
                            onClick = {
                                settingsViewModel.deleteAllData()
                                showDeleteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(
                                topStart = Dimensions.Radius.xs,
                                topEnd = Dimensions.Radius.xxl,
                                bottomStart = Dimensions.Radius.xs,
                                bottomEnd = Dimensions.Radius.xxl
                            ),
                            modifier = Modifier
                                .padding(end = Spacing.xs)
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.delete_all),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            },
            containerColor = if (blurEffects) MaterialTheme.colorScheme.surfaceContainerLow.copy(0.5f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
            dismissButton = {},
            modifier = Modifier
                .clip(RoundedCornerShape(Dimensions.Radius.md))
                .then(
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
                ),
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
fun AboutListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    onClick: () -> Unit,
    isLink: Boolean = false,
    position: ListItemPosition = ListItemPosition.Middle,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headline = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        },
        supporting = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leading = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = iconBackground,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        trailing = {
            Icon(
                if (isLink) Iconax.ExportArrow02 else Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@Composable
fun AboutDeveloperItem(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    position: ListItemPosition = ListItemPosition.Middle,
) {
    ListItem(
        headline = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        supporting = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
            )
        },
        leading = leading,
        trailing = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp),
        listColor = MaterialTheme.colorScheme.primaryContainer
    )
}

@Composable
fun SupportIcon(
    icon: ImageVector,
    iconBackground: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                color = iconBackground,
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(32.dp)
        )
    }
}
