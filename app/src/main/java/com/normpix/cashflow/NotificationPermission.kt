package com.normpix.cashflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** POST_NOTIFICATIONS doesn't exist before API 33 — treat those devices as always granted. */
fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Requests POST_NOTIFICATIONS once per app session on API 33+, so the transaction-captured
 * notifications CashflowState.notify() posts actually have a chance of showing. If the user
 * denies, we don't nag again this session — notify() simply no-ops until they grant it via
 * system Settings.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
