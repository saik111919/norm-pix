package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.StatusColorRole
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve

/**
 * The semantic color role of a transaction type, built from the theme (never raw RGB):
 * money leaving is warm, money arriving is positive, money moving between your own
 * accounts is the calm neutral secondary role. Color is only ever a second cue next to
 * the type's icon, label and the amount's sign.
 */
@Composable
fun TransactionType.role(): StatusColorRole {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        TransactionType.Expense -> StatusTone.Warning.resolve()
        TransactionType.Income -> StatusTone.Success.resolve()
        TransactionType.Transfer -> StatusColorRole(
            color = scheme.secondary,
            onColor = scheme.onSecondary,
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
        )
    }
}

/** "↑ Expense": a small tonal tag naming the type, in its [role] colors. */
@Composable
fun TransactionTypeTag(type: TransactionType, modifier: Modifier = Modifier) {
    val role = type.role()
    Row(
        modifier
            .clip(AppRadius.rFull)
            .background(role.container)
            .padding(start = 8.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(type.icon, contentDescription = null, tint = role.onContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            type.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = role.onContainer,
        )
    }
}
