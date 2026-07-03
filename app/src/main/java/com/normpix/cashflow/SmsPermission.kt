package com.normpix.cashflow

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Permissions the app needs to auto-capture bank transactions from SMS.
 * READ_SMS lets it scan the existing inbox; RECEIVE_SMS lets it react to new
 * messages as they arrive. Both are read-only — nothing is ever sent off-device.
 */
val SMS_PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

fun hasSmsPermission(context: Context): Boolean =
    SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Returns a function that requests SMS read/receive permission, updating
 * [CashflowState]'s permission + toggle state when the user responds.
 *
 * If the user has already permanently denied the permission (system won't show the
 * rationale dialog again), this opens the app's system settings page instead, since
 * requesting again would silently no-op.
 */
@Composable
fun rememberSmsPermissionRequester(state: CashflowState): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        state.onSmsPermissionResult(result.values.all { it })
    }
    return {
        if (hasSmsPermission(context)) {
            state.onSmsPermissionResult(true)
        } else {
            val activity = context.findActivity()
            val permanentlyDenied = state.smsPermissionRequestedBefore && activity != null &&
                SMS_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (permanentlyDenied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                state.showToast("Enable SMS permission in app settings")
            } else {
                state.markSmsPermissionRequested()
                launcher.launch(SMS_PERMISSIONS)
            }
        }
    }
}
