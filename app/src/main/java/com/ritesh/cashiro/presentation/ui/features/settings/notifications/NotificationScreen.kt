package com.ritesh.cashiro.presentation.ui.features.settings.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.BrandIcon
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.ListItem
import com.ritesh.cashiro.presentation.ui.components.ListItemPosition
import com.ritesh.cashiro.presentation.ui.components.PreferenceSwitch
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.components.TimePicker
import com.ritesh.cashiro.presentation.ui.components.toShape
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.icons.Clock
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Notification
import com.ritesh.cashiro.presentation.ui.icons.Notifications
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.presentation.ui.theme.blue_dark
import com.ritesh.cashiro.presentation.ui.theme.blue_light
import com.ritesh.cashiro.presentation.ui.theme.green_dark
import com.ritesh.cashiro.presentation.ui.theme.green_light
import com.ritesh.cashiro.presentation.ui.theme.orange_dark
import com.ritesh.cashiro.presentation.ui.theme.orange_light
import com.ritesh.cashiro.presentation.ui.theme.purple_dark
import com.ritesh.cashiro.presentation.ui.theme.purple_light
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit,
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    blurEffects: Boolean,
) {
    val scanEnabled by notificationViewModel.scanNewTransactionsEnabled.collectAsStateWithLifecycle()
    val alertTimeMinutes by notificationViewModel.scanNewTransactionsAlertTime.collectAsStateWithLifecycle()
    val upcomingEnabled by notificationViewModel.upcomingNotificationsEnabled.collectAsStateWithLifecycle()
    val subscriptions by notificationViewModel.subscriptions.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }


    var showTimePicker by remember { mutableStateOf(false) }
    var showPermissionGuide by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var isNotificationAccessGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationAccessGranted = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showTimePicker) {
        val initialHour = (alertTimeMinutes / 60).toInt()
        val initialMinute = (alertTimeMinutes % 60).toInt()
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute
        )
        TimePicker(
            onDismiss = { showTimePicker = false },
            onConfirm =  {
                val newMinutes = (timePickerState.hour * 60 + timePickerState.minute).toLong()
                notificationViewModel.setScanNewTransactionsAlertTime(newMinutes)
                showTimePicker = false
            },
            timePickerState = timePickerState,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showPermissionGuide = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(orange_light, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = orange_dark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Notification Access Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "To automatically detect bank transactions in real-time, Cashiro requires notification access. This is a system-level permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(Spacing.md)
                    ) {
                        Text(
                            text = "How to enable:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("1.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Tap the 'Open Settings' button below.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("2.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Find 'Cashiro' in the list of applications.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("3.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Toggle 'Allow notification access' to enabled.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionGuide = false
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionGuide = false }
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = "Notifications",
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
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Notification Access
                SectionHeader(title = "Notification Access", modifier = Modifier.padding(start = Spacing.md))
                PreferenceSwitch(
                    visible = true,
                    title = "Bank Push Notifications",
                    subtitle = if (isNotificationAccessGranted) "Notification access is active"
                    else "Enable to detect bank transactions from push notifications",
                    checked = isNotificationAccessGranted,
                    onCheckedChange = {
                        if (!isNotificationAccessGranted) {
                            showPermissionGuide = true
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isNotificationAccessGranted) green_light else orange_light,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Iconax.Notifications,
                                contentDescription = null,
                                tint = if (isNotificationAccessGranted) green_dark else orange_dark
                            )
                        }
                    },
                    isSingle = true,
                    padding = PaddingValues(0.dp)
                )

                // Scan Settings
                SectionHeader(title = "Scan Settings", modifier = Modifier.padding(start = Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    PreferenceSwitch(
                        title = "Remind Transactions",
                        subtitle = "Get reminders to scan new messages",
                        checked = scanEnabled,
                        onCheckedChange = notificationViewModel::setScanNewTransactionsEnabled,
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(blue_light, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.Notification,
                                    contentDescription = null,
                                    tint = blue_dark
                                )
                            }
                        },
                        isSingle = !scanEnabled,
                        isFirst = scanEnabled,
                        isLast = !scanEnabled,
                        padding = PaddingValues(0.dp)
                    )

                    AnimatedVisibility(
                        visible = scanEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val time = LocalTime.of((alertTimeMinutes / 60).toInt(), (alertTimeMinutes % 60).toInt())
                        
                        ListItem(
                            headline = { Text("Alert Time") },
                            trailing = {
                                Box(
                                    modifier = Modifier
                                        .padding( vertical = 12.dp)
                                        .clickable { showTimePicker = true },
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        modifier = Modifier.wrapContentWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        val time = LocalTime.of((alertTimeMinutes / 60).toInt(), (alertTimeMinutes % 60).toInt())
                                        val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
                                        val minute = time.minute
                                        val amPm = if (time.hour < 12) "AM" else "PM"

                                        Box(
                                            modifier = Modifier
                                                .padding(5.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Text(
                                                text = String.format("%02d", hour),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(5.dp)
                                            )
                                        }

                                        Text(
                                            text = ":",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 16.sp,
                                        )

                                        Box(
                                            modifier = Modifier
                                                .padding(5.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Text(
                                                text = String.format("%02d", minute),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 16.sp,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(5.dp)
                                            )
                                        }

                                        Box(modifier = Modifier.padding(5.dp)) {
                                            Text(
                                                text = amPm,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }
                            },
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(green_light, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Iconax.Clock,
                                        contentDescription = null,
                                        tint = green_dark
                                    )
                                }
                            },
                            onClick = { showTimePicker = true },
                            shape = ListItemPosition.Bottom.toShape(),
                            padding = PaddingValues(0.dp)
                        )
                    }
                }

                // Upcoming Settings
                SectionHeader(title = "Upcoming Transactions", modifier = Modifier.padding(start = Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {

                    val itemsCount = subscriptions.size
                    PreferenceSwitch(
                        title = "Upcoming Transactions",
                        subtitle = "Get reminders for upcoming payments",
                        checked = upcomingEnabled,
                        onCheckedChange = notificationViewModel::setUpcomingNotificationsEnabled,
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(purple_light, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Upcoming,
                                    contentDescription = null,
                                    tint = purple_dark
                                )
                            }
                        },
                        isSingle = !upcomingEnabled || itemsCount == 0,
                        isFirst = true,
                        isLast = !upcomingEnabled || itemsCount == 0,
                        padding = PaddingValues(0.dp)
                    )

                    AnimatedVisibility(
                        visible = upcomingEnabled && subscriptions.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                            subscriptions.forEachIndexed { index, item ->
                                val isLastItem = index == subscriptions.lastIndex
                                PreferenceSwitch(
                                    title = item.subscription.merchantName,
                                    subtitle = item.subscription.nextPaymentDate?.format(DateTimeFormatter.ofPattern("MMM dd")) ?: "No date",
                                    checked = item.isNotificationEnabled,
                                    onCheckedChange = { 
                                        notificationViewModel.toggleSubscriptionNotification(item.subscription.id, it)
                                    },
                                    leadingIcon = {
                                        BrandIcon(
                                            merchantName = item.subscription.merchantName,
                                            category = item.subscription.category,
                                            subcategory = item.subscription.subcategory,
                                            size = 48.dp
                                        )
                                    },
                                    isFirst = false,
                                    isLast = isLastItem,
                                    isSingle = false,
                                    padding = PaddingValues(0.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(packageName) == true
}
