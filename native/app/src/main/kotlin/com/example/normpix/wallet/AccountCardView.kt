package com.example.normpix.wallet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.DeckStack
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius

/**
 * What an account card says, already worked out: the money is formatted and the standing ("Owed", "In credit") decided by
 * the screen, so the card is just a picture of it and can be drawn without the ledger.
 */
@Immutable
data class AccountCardData(
    val id: Long,
    val name: String,
    val kind: AccountKind,
    /** The bank's name, for its logo and its card colours; null for an account with no bank (cash, say). */
    val bankName: String?,
    /** The balance as written, with its sign and currency. */
    val balance: String,
    /** "Owed", "Overdrawn" or "In credit" when the balance needs saying in words; null otherwise. */
    val standing: String?,
    val isDefault: Boolean,
    val archived: Boolean,
    /** How many saved cards belong to the account's bank. */
    val cards: Int,
    /** What is left of a credit card's limit ("₹38,000 left", or "Over limit"); null for an account with no limit. */
    val credit: String? = null,
    /** [credit] in full, for a screen reader: the limit and what is available. */
    val creditSpoken: String? = null,
    /** The card is over its limit. */
    val overLimit: Boolean = false,
    /** How much of the limit is used, 0..1, drawn as a meter on the card; null for an account with no limit. */
    val creditUsed: Float? = null,
    /** When the card's bill is due ("Due in 3 days"); null unless the card has a due day and something is owed on it. */
    val due: String? = null,
    /** The bill is due within a few days, so [due] is said loudly. */
    val dueSoon: Boolean = false,
    /** [due] in full for a screen reader: the date and how far away it is. */
    val dueSpoken: String? = null,
) {
    /** The card as a screen reader reads it. */
    val spoken: String
        get() = buildString {
            append(name).append(", ").append(kind.label)
            if (bankName != null) append(", ").append(bankName)
            append(", balance ").append(balance)
            if (standing != null) append(", ").append(standing)
            if (creditSpoken != null) append(", ").append(creditSpoken)
            if (dueSpoken != null) append(", ").append(dueSpoken)
            if (isDefault) append(", default account")
            if (archived) append(", archived")
            if (cards > 0) append(", ").append(cards).append(if (cards == 1) " saved card" else " saved cards")
        }
}

/**
 * A bank's accounts look like that bank's cards - same colours, pattern and logo - so an account and the card beside it
 * read as one thing. An account with no bank is coloured by what it is.
 */
internal object AccountCardDesign {
    fun themeFor(bankName: String?, kind: AccountKind): BankBrandTheme =
        if (bankName != null) BankCardDesign.themeFor(bankName) else kindThemes.getValue(kind)

    private fun kindTheme(
        kind: AccountKind,
        name: String,
        palette: List<Color>,
        pattern: BankPatternType,
        accent: Color,
        finish: EmvChipFinish,
    ) = BankBrandTheme(
        id = "account_${kind.key}",
        displayName = name,
        gradientColors = palette,
        gradient = GradientSpec.Linear(Anchor(-1f, -1f), Anchor(1f, 1f), palette),
        patternType = pattern,
        chipFinish = finish,
        accentColor = accent,
        logo = BankLogo.Text(name),
    )

    private val kindThemes: Map<AccountKind, BankBrandTheme> = mapOf(
        AccountKind.Cash to kindTheme(
            AccountKind.Cash, "Cash",
            listOf(Color(0xFF1B4332), Color(0xFF0E281E), Color(0xFF07140F)),
            BankPatternType.MetallicBrushed, Color(0xFF95D5B2), EmvChipFinish.Gold,
        ),
        AccountKind.Bank to kindTheme(
            AccountKind.Bank, "Bank account",
            listOf(Color(0xFF374151), Color(0xFF1F2937), Color(0xFF111827)),
            BankPatternType.MinimalGeometric, Color(0xFF9CA3AF), EmvChipFinish.Silver,
        ),
        AccountKind.Card to kindTheme(
            AccountKind.Card, "Card",
            listOf(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF0F0D27)),
            BankPatternType.MinimalGeometric, Color(0xFFA5B4FC), EmvChipFinish.Silver,
        ),
        AccountKind.Wallet to kindTheme(
            AccountKind.Wallet, "UPI / e-wallet",
            listOf(Color(0xFF005F73), Color(0xFF0A3A40), Color(0xFF041C1F)),
            BankPatternType.MetallicBrushed, Color(0xFF5EEAD4), EmvChipFinish.Silver,
        ),
        AccountKind.Savings to kindTheme(
            AccountKind.Savings, "Savings",
            listOf(Color(0xFF7C2D12), Color(0xFF4A1A09), Color(0xFF260D04)),
            BankPatternType.MinimalGeometric, Color(0xFFFDBA74), EmvChipFinish.Gold,
        ),
        AccountKind.Other to kindTheme(
            AccountKind.Other, "Account",
            listOf(Color(0xFF3F2B96), Color(0xFF261961), Color(0xFF130B33)),
            BankPatternType.MetallicBrushed, Color(0xFFC4B5FD), EmvChipFinish.Silver,
        ),
    )
}

// Pills that carry a status on the card. Each has its own dark backing, so the words read on any bank's colours.
private val OwedPill = Triple(Color(0x99410002), Color(0xAAFFB4AB), Color(0xFFFFDAD6))
private val CreditPill = Triple(Color(0x99003910), Color(0xAA7EE08A), Color(0xFFC8F7C5))
private val QuietPill = Triple(Color(0x26FFFFFF), Color(0x44FFFFFF), Color.White)

/** The bank's mark on the card, or for an account with no bank, the icon of its kind. */
@Composable
private fun KindMark(kind: AccountKind) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountFront(data: AccountCardData, theme: BankBrandTheme, bankIconStyle: BankIconStyle) {
    CardFace(theme) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) {
                    if (data.bankName != null) {
                        BankLogoView(data.bankName, theme, compact = bankIconStyle == BankIconStyle.Compact)
                    } else {
                        KindMark(data.kind)
                    }
                }
                Spacer(Modifier.width(8.dp))
                CardBadge(
                    text = data.kind.label.uppercase(),
                    background = theme.badgeColor,
                    border = theme.badgeBorderColor,
                    textColor = theme.badgeTextColor,
                )
            }
            // What the account is called sits where the number sits on a bank card; the balance, the figure that
            // matters, is the biggest thing on it.
            Spacer(Modifier.weight(1f))
            Text(
                data.name,
                style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp, shadow = embossShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "BALANCE",
                style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.9.sp, shadow = embossShadow),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                data.balance,
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    letterSpacing = 0.2.sp,
                    fontFeatureSettings = "tnum",
                    shadow = embossShadow,
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 30.sp),
            )
            val pills = buildList {
                data.standing?.let { standing ->
                    add(standing.uppercase() to if (standing == "In credit") CreditPill else OwedPill)
                }
                data.due?.let { due -> add(due.uppercase() to if (data.dueSoon) OwedPill else QuietPill) }
                // A card with a limit says what is left of it on a meter below instead of as one more pill.
                if (data.creditUsed == null) {
                    data.credit?.let { credit -> add(credit.uppercase() to if (data.overLimit) OwedPill else QuietPill) }
                }
                if (data.isDefault) add("DEFAULT" to Triple(theme.badgeColor, theme.badgeBorderColor, theme.badgeTextColor))
                if (data.cards > 0) add("${data.cards} ${if (data.cards == 1) "CARD" else "CARDS"}" to QuietPill)
                if (data.archived) add("ARCHIVED" to QuietPill)
            }
            if (data.creditUsed != null) {
                Spacer(Modifier.height(10.dp))
                CreditMeter(used = data.creditUsed, over = data.overLimit, caption = data.credit)
            }
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(if (data.creditUsed != null) 8.dp else 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((text, colors) in pills) CardBadge(text, background = colors.first, border = colors.second, textColor = colors.third)
                }
            }
        }
    }
}

/**
 * How much of a credit limit is spent: a slim track filled to [used] (a warm red once the card is [over] its limit), with
 * [caption] - what is left, or "Over limit" - and the share used written under it. Drawn on the card's own colours, so it is
 * white-on-dark like the rest of the face.
 */
@Composable
private fun CreditMeter(used: Float, over: Boolean, caption: String?) {
    val fill = if (over) Color(0xFFFFB4AB) else Color.White.copy(alpha = 0.92f)
    val progress by animateFloatAsState(used.coerceIn(0f, 1f), AppMotion.spatial<Float>(), label = "credit_meter")
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(CircleShape).background(fill))
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                caption.orEmpty(),
                style = TextStyle(color = Color.White.copy(alpha = 0.86f), fontWeight = FontWeight.SemiBold, fontSize = 10.sp, shadow = embossShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "${(used.coerceIn(0f, 1f) * 100).toInt()}% used",
                style = TextStyle(color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Medium, fontSize = 10.sp, shadow = embossShadow),
                maxLines = 1,
            )
        }
    }
}

/** An account card's front on its own, without a way to press it: the front of an [AccountStack]. */
@Composable
fun AccountCardFacePreview(
    data: AccountCardData,
    modifier: Modifier = Modifier,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
) {
    // Card art keeps a card's proportions, so it ignores the system font scale (as the bank cards do).
    val density = LocalDensity.current
    val fixedDensity = remember(density) { Density(density.density, fontScale = 1f) }
    val theme = remember(data.bankName, data.kind) { AccountCardDesign.themeFor(data.bankName, data.kind) }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        Box(modifier.fillMaxWidth().aspectRatio(CARD_ASPECT).alpha(if (data.archived) 0.72f else 1f)) {
            AccountFront(data, theme, bankIconStyle)
        }
    }
}

/** An account card's colours and pattern with nothing printed on it: all that shows of a card that lies behind another. */
@Composable
fun AccountCardPlate(data: AccountCardData, modifier: Modifier = Modifier) {
    val theme = remember(data.bankName, data.kind) { AccountCardDesign.themeFor(data.bankName, data.kind) }
    Box(modifier.fillMaxWidth().aspectRatio(CARD_ASPECT)) { CardFace(theme) {} }
}

/**
 * An account as a card you can press: a tap calls [onOpen] (its history), a press and hold [onManage] (managing it).
 * Both are also offered to a screen reader, which hears the whole card as one sentence.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountCardView(
    data: AccountCardData,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, AppMotion.spatial<Float>(), label = "account_card_press")
    // No clip and no ripple: the card paints its own shadow outside its edge, and a clip here would cut it off.
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = press,
                indication = null,
                onClickLabel = "Opens the history",
                onLongClickLabel = "Manage account",
                onLongClick = onManage,
                onClick = onOpen,
            )
            .clearAndSetSemantics {
                contentDescription = data.spoken
                role = Role.Button
                onClick(label = "Opens the history") { onOpen(); true }
                onLongClick(label = "Manage account") { onManage(); true }
            },
    ) {
        AccountCardFacePreview(data, bankIconStyle = bankIconStyle)
    }
}

/**
 * The accounts as a stack, as Home's cards are: the first in front, printed in full, the rest a hair of colour behind it.
 * A tap on the front one calls [onOpen], a press and hold [onManage], a swipe brings the next forward (see [DeckStack]).
 */
@Composable
fun AccountStack(
    accounts: List<AccountCardData>,
    onOpen: (AccountCardData) -> Unit,
    onManage: (AccountCardData) -> Unit,
    modifier: Modifier = Modifier,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
) {
    DeckStack(
        items = accounts,
        itemKey = { it.id },
        shape = AppRadius.rLg,
        description = { account, index, count ->
            account.spoken + if (count > 1) ", account ${index + 1} of $count" else ""
        },
        onOpen = onOpen,
        modifier = modifier,
        onLongPress = onManage,
        longPressLabel = "Manage account",
        plate = { AccountCardPlate(it) },
        face = { AccountCardFacePreview(it, bankIconStyle = bankIconStyle) },
    )
}
