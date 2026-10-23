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
import com.example.normpix.finance.ui.ShapedBadge
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.ui.components.BankGlyph

/**
 * The badge of a row, a field or a chip for something that belongs to [bank]: the bank's logo (or the generic bank glyph, for a
 * bank with no artwork) on the tonal badge every list row uses - in the shape the user chose for icon backgrounds (Settings >
 * Appearance > Icon background: a circle, a rounded square, a square, or none, where the logo is drawn larger on its own).
 * Without a bank - cash, an account none was chosen for - it is the [fallback] icon, on the same badge, so rows line up either way.
 */
@Composable
fun BankBadge(bank: Bank?, fallback: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    ShapedBadge(modifier, size) { drawSize, bare ->
        // On the badge the mark sits in the tone made for it; on nothing it takes one that reads on the page.
        val tint = if (bare) scheme.onSurfaceVariant else scheme.onSecondaryContainer
        if (bank == null) {
            Icon(fallback, contentDescription = null, tint = tint, modifier = Modifier.size(drawSize))
        } else {
            BankGlyph(bank, tint = tint, size = drawSize)
        }
    }
}

/** [BankBadge] for the bank called [bankName], for the places that only hold a name (a reminder's linked card). Unknown names get the generic glyph. */
@Composable
fun BankBadge(bankName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    ShapedBadge(modifier, size) { drawSize, bare ->
        BankGlyph(bankName, tint = if (bare) scheme.onSurfaceVariant else scheme.onSecondaryContainer, size = drawSize)
    }
}

/** An account's badge: its bank's logo when it has a bank, otherwise the icon of its kind - [BankBadge], so an account looks the same wherever it appears. */
@Composable
fun AccountBadge(account: Account, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    BankBadge(rememberFinanceState().catalog.byKey(account.bankKey), account.kind.icon, modifier, size)
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
