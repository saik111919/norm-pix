package com.example.normpix.widgets

import com.example.normpix.finance.domain.TransactionType

/**
 * What a home-screen widget asked NormPix to do. It travels on the intent that opens the app ([WidgetIntents]) as [key] -
 * plain words, never the data itself - and the app does it once it is open and unlocked (`ReceiveWidgetRequests`).
 */
sealed interface WidgetRequest {
    /** The new-transaction sheet for [type]: the Add widget's buttons, and the Recent widget's +. */
    data class Add(val type: TransactionType) : WidgetRequest

    /** One transaction's details: a row of the Recent widget. */
    data class Show(val transactionId: Long) : WidgetRequest

    /** Activity, where every transaction is: the Recent widget's heading, and its message when there is nothing to list. */
    data object Activity : WidgetRequest

    /** The words the request travels as. */
    val key: String
        get() = when (this) {
            is Add -> ADD + type.name.lowercase()
            is Show -> SHOW
            Activity -> ACTIVITY
        }

    companion object {
        private const val ADD = "add-"
        private const val SHOW = "show"
        private const val ACTIVITY = "activity"

        /** The request [key] stands for (with [transactionId] for one transaction); null for anything else. */
        fun of(key: String?, transactionId: Long = -1): WidgetRequest? = when {
            key == null -> null
            key == ACTIVITY -> Activity
            key == SHOW -> if (transactionId > 0) Show(transactionId) else null
            key.startsWith(ADD) -> TransactionType.entries.firstOrNull { it.name.lowercase() == key.removePrefix(ADD) }?.let(::Add)
            else -> null
        }
    }
}
