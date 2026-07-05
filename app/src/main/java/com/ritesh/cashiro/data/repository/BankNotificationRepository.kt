package com.ritesh.cashiro.data.repository

import com.ritesh.cashiro.data.database.dao.BankNotificationDao
import com.ritesh.cashiro.data.database.entity.BankNotificationEntity
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankNotificationRepository @Inject constructor(
    private val bankNotificationDao: BankNotificationDao
) {
    suspend fun insert(notification: BankNotificationEntity): Long = bankNotificationDao.insert(notification)

    suspend fun markProcessed(id: Long, transactionId: Long?) {
        bankNotificationDao.markProcessed(
            id = id,
            status = "PROCESSED",
            transactionId = transactionId,
            processedAt = LocalDateTime.now(),
            errorMessage = null
        )
    }

    suspend fun markFailed(id: Long, errorMessage: String) {
        bankNotificationDao.markFailed(
            id = id,
            status = "FAILED",
            processedAt = LocalDateTime.now(),
            errorMessage = errorMessage
        )
    }

    suspend fun markSkipped(id: Long, reason: String) {
        bankNotificationDao.markFailed(
            id = id,
            status = "SKIPPED",
            processedAt = LocalDateTime.now(),
            errorMessage = reason
        )
    }

    suspend fun getPendingNotifications(limit: Int = 10): List<BankNotificationEntity> =
        bankNotificationDao.getPendingNotifications(limit)

    suspend fun getPendingCount(): Int = bankNotificationDao.getPendingCount()

    suspend fun getById(id: Long): BankNotificationEntity? = bankNotificationDao.getById(id)

    suspend fun deleteOldProcessed(before: LocalDateTime) = bankNotificationDao.deleteOldProcessed(before)
}
