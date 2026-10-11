package com.example.normpix.banks.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.Bank
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.LeadingBadge
import com.example.normpix.ui.components.LeadingIcon

/**
 * The leading badge of a list row for something that belongs to [bank]: the bank's logo (or the generic bank glyph, for a
 * bank with no artwork) in the same round tonal badge every list row uses. Without a bank - cash, an account none was
 * chosen for - it is the usual [fallback] icon badge, so rows line up either way.
 */
@Composable
fun BankBadge(bank: Bank?, fallback: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (bank == null) {
        LeadingIcon(fallback, modifier, size = size)
    } else {
        LeadingBadge(modifier, size = size) {
            BankGlyph(bank, tint = MaterialTheme.colorScheme.onSecondaryContainer, size = size / 2 + 2.dp)
        }
    }
}

/**
 * A small mark for [account] wherever one stands beside its name (a chip, a menu item): its bank's logo when it has a
 * bank, otherwise the icon of its kind - so an account looks the same in every list it appears in.
 */
@Composable
fun AccountMark(account: Account, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val bank = rememberFinanceState().catalog.byKey(account.bankKey)
    if (bank != null) {
        BankGlyph(bank, tint, modifier, size)
    } else {
        Icon(account.kind.icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
