package com.ritesh.cashiro.receiver

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ritesh.cashiro.data.manager.SmsTransactionProcessor
import com.ritesh.cashiro.data.repository.BankNotificationRepository
import com.ritesh.cashiro.data.database.entity.BankNotificationEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class BankNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotifListener"
    }

    @Inject lateinit var smsTransactionProcessor: SmsTransactionProcessor
    @Inject lateinit var bankNotificationRepository: BankNotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        if (!BankNotificationConfig.isAllowed(packageName)) return

        val notification = sbn.notification ?: return
        val messageBody = BankNotificationConfig.extractMessage(notification) ?: return
        val senderAlias = BankNotificationConfig.senderAlias(packageName) ?: return
        val notificationId = sbn.id

        Log.d(TAG, "Bank notification from $packageName ($senderAlias): ${messageBody.take(100)}...")

        // Insert into tracking table
        val entity = BankNotificationEntity(
            packageName = packageName,
            notificationId = notificationId,
            senderAlias = senderAlias,
            messageBody = messageBody,
            status = "PENDING",
            receivedAt = LocalDateTime.now()
        )

        scope.launch {
            val dbId = bankNotificationRepository.insert(entity)
            if (dbId == -1L) {
                Log.d(TAG, "Duplicate notification, skipping: $notificationId from $packageName")
                return@launch
            }

            try {
                val result = smsTransactionProcessor.processAndSaveTransaction(
                    sender = senderAlias,
                    body = messageBody,
                    timestamp = System.currentTimeMillis()
                )

                if (result.success) {
                    Log.d(TAG, "Successfully processed bank notification: ${result.transactionId}")
                    bankNotificationRepository.markProcessed(dbId, result.transactionId)
                } else {
                    Log.w(TAG, "Failed to process bank notification: ${result.reason}")
                    bankNotificationRepository.markSkipped(dbId, result.reason ?: "Unknown")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing bank notification", e)
                bankNotificationRepository.markFailed(dbId, e.message ?: "Unknown error")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op
    }
}
