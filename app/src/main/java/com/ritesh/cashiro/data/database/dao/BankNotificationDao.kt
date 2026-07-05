package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.BankNotificationEntity
import java.time.LocalDateTime

@Dao
interface BankNotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: BankNotificationEntity): Long

    @Update
    suspend fun update(notification: BankNotificationEntity)

    @Query("""
        UPDATE bank_notifications 
        SET status = :status, transaction_id = :transactionId, processed_at = :processedAt, error_message = :errorMessage
        WHERE id = :id
    """)
    suspend fun markProcessed(id: Long, status: String, transactionId: Long?, processedAt: LocalDateTime, errorMessage: String?)

    @Query("""
        UPDATE bank_notifications 
        SET status = :status, processed_at = :processedAt, error_message = :errorMessage
        WHERE id = :id
    """)
    suspend fun markFailed(id: Long, status: String, processedAt: LocalDateTime, errorMessage: String?)

    @Query("SELECT * FROM bank_notifications WHERE status = 'PENDING' ORDER BY received_at ASC LIMIT :limit")
    suspend fun getPendingNotifications(limit: Int = 10): List<BankNotificationEntity>

    @Query("SELECT * FROM bank_notifications WHERE id = :id")
    suspend fun getById(id: Long): BankNotificationEntity?

    @Query("SELECT COUNT(*) FROM bank_notifications WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM bank_notifications WHERE received_at < :before AND status IN ('PROCESSED', 'SKIPPED', 'FAILED')")
    suspend fun deleteOldProcessed(before: LocalDateTime)
}
