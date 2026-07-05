package com.ritesh.cashiro.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ritesh.cashiro.data.manager.SmsTransactionProcessor
import com.ritesh.cashiro.data.repository.BankNotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BankNotificationRetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bankNotificationRepository: BankNotificationRepository,
    private val smsTransactionProcessor: SmsTransactionProcessor
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "BankNotifRetryWorker"
        const val WORK_NAME = "bank_notification_retry"
    }

    override suspend fun doWork(): Result {
        val pending = bankNotificationRepository.getPendingNotifications(limit = 20)
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending bank notifications to retry")
            return Result.success()
        }

        Log.d(TAG, "Retrying ${pending.size} pending bank notifications")
        var successCount = 0
        var failCount = 0

        for (notification in pending) {
            try {
                val result = smsTransactionProcessor.processAndSaveTransaction(
                    sender = notification.senderAlias,
                    body = notification.messageBody,
                    timestamp = System.currentTimeMillis()
                )

                if (result.success) {
                    bankNotificationRepository.markProcessed(notification.id, result.transactionId)
                    successCount++
                } else {
                    bankNotificationRepository.markSkipped(notification.id, result.reason ?: "Unknown")
                    failCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying notification ${notification.id}", e)
                bankNotificationRepository.markFailed(notification.id, e.message ?: "Unknown error")
                failCount++
            }
        }

        Log.d(TAG, "Retry complete: $successCount succeeded, $failCount failed")
        return if (failCount > 0 && successCount == 0) Result.retry() else Result.success()
    }
}
