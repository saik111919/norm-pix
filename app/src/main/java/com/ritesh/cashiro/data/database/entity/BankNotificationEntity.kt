package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "bank_notifications",
    indices = [
        androidx.room.Index(value = ["package_name", "notification_id"], unique = true)
    ]
)
data class BankNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "notification_id")
    val notificationId: Int,

    @ColumnInfo(name = "sender_alias")
    val senderAlias: String,

    @ColumnInfo(name = "message_body")
    val messageBody: String,

    @ColumnInfo(name = "status")
    val status: String = "PENDING",

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "received_at")
    val receivedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "processed_at")
    val processedAt: LocalDateTime? = null
)
