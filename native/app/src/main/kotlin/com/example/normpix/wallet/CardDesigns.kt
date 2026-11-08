package com.example.normpix.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.settings.ColorSource
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.ArtworkColors

// ================================================================================================
// Design Tokens & Contrast Helpers
// ================================================================================================

// A card is clipped at AppRadius.lg by everything around it (the stacks, the carousel, the hold ring), so the Banner's outer corners
// are that radius too; at the bill banner's 28 dp the card behind showed through the gap in a stack. Inner corners are small.
private val BannerCornerOuter = AppRadius.lg
private val BannerCornerInner = AppRadius.xs
private val BannerTopShape = RoundedCornerShape(topStart = BannerCornerOuter, topEnd = BannerCornerOuter, bottomEnd = BannerCornerInner, bottomStart = BannerCornerInner)
private val BannerBottomLeftShape = RoundedCornerShape(topStart = BannerCornerInner, topEnd = BannerCornerInner, bottomEnd = BannerCornerInner, bottomStart = BannerCornerOuter)
private val BannerBottomRightShape = RoundedCornerShape(topStart = BannerCornerInner, topEnd = BannerCornerInner, bottomEnd = BannerCornerOuter, bottomStart = BannerCornerInner)

/** One panel alone in the bottom row takes all of it: the card's own corners below, small ones where it meets the top panel. */
private val BannerBottomShape = RoundedCornerShape(topStart = BannerCornerInner, topEnd = BannerCornerInner, bottomEnd = BannerCornerOuter, bottomStart = BannerCornerOuter)

private val BannerSeam = 3.dp

/** [preferred] if it reads on [fill] at WCAG 4.5:1, else [fallback]. */
internal fun readableOnTone(fill: Color, preferred: Color, fallback: Color): Color {
    val a = preferred.luminance()
    val b = fill.luminance()
    return if ((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f) preferred else fallback
}

/** Secondary ink readable at >= 4.5:1 on [container]. */
internal fun quietTone(ink: Color, container: Color): Color =
    readableOnTone(container, ink.copy(alpha = 0.78f).compositeOver(container), ink)

/**
 * The colours a Tinted card is dressed in: the bank's own hue through the Artwork-colours machinery, or null for the theme's own
 * colours (the Settings > Appearance > Colors > Artwork colors switch is off, or the hue is unusable, e.g. grey). It is the one place
 * this is decided, so a face, its back, its plate and a deck strip always agree on the colour they sit on.
 */
@Composable
internal fun tintedCardScheme(theme: BankBrandTheme): ColorScheme? {
    val seed = theme.gradientColors.firstOrNull()
    val base = MaterialTheme.colorScheme
    val prefs = LocalAppPreferences.current?.state?.collectAsState()?.value
    val enabled = prefs?.artworkColors ?: true
    val keepSurfaces = prefs != null &&
        (prefs.accent == AccentColor.Pure || prefs.accent == AccentColor.Volt) &&
        !(prefs.colorSource == ColorSource.System && AppTheme.dynamicColorSupported)
    return remember(base, seed, enabled, keepSurfaces) {
        if (enabled && seed != null && ArtworkColors.usable(seed)) ArtworkColors.scheme(base, seed, keepSurfaces) else null
    }
}

/** How far the print of a flat design may shrink or grow with its card: a carousel's card is narrower than the print was drawn for. */
private const val PrintMinScale = 0.8f
private const val PrintMaxScale = 1.6f

/**
 * [content] (a design's face) drawn as if the card were [PrintReferenceWidth] wide, whatever width it really is: every dp and sp
 * inside grows or shrinks with the card, so the face keeps its proportions. Classic's print stays one size and its gradient fills
 * the rest of a wider card; a flat design has nothing to fill it with, so on the Wallet's 380 dp card (wider again on most phones)
 * a print drawn for 328 dp was a small print in a big blank panel - the "space" the owner kept seeing. [content] is given the
 * scale, for what it measures in the card's own units (a deck's covered strip is [scale] times smaller inside).
 */
@Composable
internal fun ScaledToCard(content: @Composable (scale: Float) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = (maxWidth / PrintReferenceWidth).coerceIn(PrintMinScale, PrintMaxScale)
        val scaled = remember(base, scale) { Density(base.density * scale, fontScale = 1f) }
        CompositionLocalProvider(LocalDensity provides scaled) { content(scale) }
    }
}

/**
 * The bank's mark on a design whose surface is the theme's own (a pale panel in a light theme), where [BankLogoView]'s lettering,
 * drawn white for a dark card, would vanish. A bank with bundled artwork keeps it (the variant that suits the surface, [onDark]);
 * any other is its glyph on a disc and its name, in [ink].
 */
@Composable
internal fun DesignBankMark(bankName: String, theme: BankBrandTheme, compact: Boolean, ink: Color, onDark: Boolean) {
    if (BankLogoResolver.logoFor(bankName) != null) {
        BankLogoView(bankName, theme, compact = compact, onDark = onDark)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                BankGlyph(bankName, tint = ink, size = 16.dp)
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                bankName,
                style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How a pill on an account face is drawn: a status colour, the quiet outline, or the accent of DEFAULT. */
internal enum class FlagTone { Owed, Credit, Quiet, Default }

internal class AccountFlag(val text: String, val tone: FlagTone)

/**
 * What an account face says besides its name and balance, one pill each and in Classic's order (none left out): standing, due,
 * the limit (only when there is no meter to say it), DEFAULT, the cards it holds, ARCHIVED. The carousel's print is the name and
 * DEFAULT / ARCHIVED only, as on Classic.
 */
internal fun accountFlags(data: AccountCardData, compact: Boolean): List<AccountFlag> = buildList {
    if (compact) {
        if (data.isDefault) add(AccountFlag("DEFAULT", FlagTone.Default))
        if (data.archived) add(AccountFlag("ARCHIVED", FlagTone.Quiet))
        return@buildList
    }
    data.standing?.let { add(AccountFlag(it.uppercase(), if (it == "In credit") FlagTone.Credit else FlagTone.Owed)) }
    data.due?.let { add(AccountFlag(it.uppercase(), if (data.dueSoon) FlagTone.Owed else FlagTone.Quiet)) }
    if (data.creditUsed == null) data.credit?.let { add(AccountFlag(it.uppercase(), if (data.overLimit) FlagTone.Owed else FlagTone.Quiet)) }
    if (data.isDefault) add(AccountFlag("DEFAULT", FlagTone.Default))
    if (data.cards > 0) add(AccountFlag("${data.cards} ${if (data.cards == 1) "CARD" else "CARDS"}", FlagTone.Quiet))
    if (data.archived) add(AccountFlag("ARCHIVED", FlagTone.Quiet))
}

/** The [flags] as a wrapping row of small pills; each pill's words are on its own fill, so they read on any panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountFlagPills(flags: List<AccountFlag>, ink: Color, modifier: Modifier = Modifier, scheme: ColorScheme = MaterialTheme.colorScheme) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        for (flag in flags) {
            val (fill, words, edge) = when (flag.tone) {
                FlagTone.Owed -> Triple(scheme.errorContainer, scheme.onErrorContainer, Color.Transparent)
                FlagTone.Credit -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, Color.Transparent)
                FlagTone.Default -> Triple(scheme.primary, scheme.onPrimary, Color.Transparent)
                FlagTone.Quiet -> Triple(Color.Transparent, ink, ink.copy(alpha = 0.35f))
            }
            Box(
                Modifier
                    .clip(AppRadius.rFull)
                    .background(fill)
                    .border(1.dp, edge, AppRadius.rFull)
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
            ) {
                Text(
                    flag.text,
                    style = TextStyle(color = words, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.8.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** One of the Banner's panels: [shape]'s corners, the container, a hairline of ink, and room around what it holds. */
@Composable
private fun BannerPanel(shape: Shape, container: Color, ink: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(shape)
            .background(container)
            .drawWithContent {
                drawContent()
                drawOutline(shape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

private fun Modifier.drawConcentricRings(ink: Color): Modifier = drawBehind {
    val ringInk = ink.copy(alpha = 0.07f)
    val stroke = Stroke(1.dp.toPx())
    val center = Offset(size.width * 0.90f, size.height * 0.16f)
    val r = size.height * 0.28f
    drawCircle(ringInk, radius = r * 0.5f, center = center, style = stroke)
    drawCircle(ringInk, radius = r * 0.85f, center = center, style = stroke)
    drawCircle(ringInk, radius = r * 1.25f, center = center, style = stroke)
}

@Composable
private fun OutlineEmvChip(ink: Color, modifier: Modifier = Modifier) {
    val chipInk = ink.copy(alpha = 0.5f)
    val shape = AppRadius.rXs
    androidx.compose.foundation.Canvas(
        modifier
            .size(38.dp, 28.dp)
            .border(1.dp, chipInk, shape),
    ) {
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        drawLine(chipInk, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = stroke)
        drawLine(chipInk, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = stroke)
        val rectSize = size.height * 0.38f
        drawRoundRect(
            color = chipInk,
            topLeft = Offset((w - rectSize) / 2f, (h - rectSize) / 2f),
            size = Size(rectSize, rectSize),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(stroke),
        )
    }
}

// ================================================================================================
// 1. BANNER DESIGN
// ================================================================================================

@Composable
internal fun CardFrontBanner(
    card: StoredCard,
    theme: BankBrandTheme,
    revealed: Boolean,
    bankIconStyle: BankIconStyle,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val quiet = quietTone(ink, container)
    val network = CardNetwork.fromNumber(card.cardNumber)

    Column(
        modifier
            .fillMaxSize()
            .padding(bottom = bottomInset),
    ) {
        // Top panel (~1.45 weight)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1.45f)
                .clip(BannerTopShape)
                .background(container)
                .drawConcentricRings(ink)
                .drawWithContent {
                    drawContent()
                    drawOutline(BannerTopShape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
                }
                .padding(AppSpacing.md),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f, fill = false)) {
                        DesignBankMark(card.bankName, theme, compact = bankIconStyle == BankIconStyle.Compact, ink = ink, onDark = container.luminance() < 0.5f)
                    }
                    Spacer(Modifier.width(AppSpacing.sm))
                    Box(
                        Modifier
                            .clip(AppRadius.rFull)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                    ) {
                        Text(
                            if (card.kind == CardKind.Credit) "CREDIT" else "DEBIT",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.1.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (card.cardName.isNotBlank()) {
                    Text(
                        card.cardName.uppercase(),
                        style = TextStyle(
                            color = quiet,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                }
                if (revealed) {
                    Text(
                        groupDigits(card.cardNumber),
                        style = TextStyle(
                            color = ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 30.sp,
                            letterSpacing = 1.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 30.sp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "\u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  ",
                            style = TextStyle(
                                color = quiet,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                letterSpacing = 1.5.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            maxLines = 1,
                        )
                        Text(
                            card.last4,
                            style = TextStyle(
                                color = ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 30.sp,
                                letterSpacing = 1.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(BannerSeam))

        // Bottom panels (weight 1)
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(BannerSeam),
        ) {
            // Bottom-left panel: CARDHOLDER
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(BannerBottomLeftShape)
                    .background(container)
                    .drawWithContent {
                        drawContent()
                        drawOutline(BannerBottomLeftShape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
                    }
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        "CARDHOLDER",
                        style = TextStyle(color = quiet, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        card.cardholderName.uppercase(),
                        style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bottom-right panel: EXPIRES & Network
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(BannerBottomRightShape)
                    .background(container)
                    .drawWithContent {
                        drawContent()
                        drawOutline(BannerBottomRightShape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
                    }
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            "EXPIRES",
                            style = TextStyle(color = quiet, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(AppSpacing.xxs))
                        Text(
                            "${card.expiryMonth}/${card.expiryYear}",
                            style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                    }
                    if (network != CardNetwork.Unknown) {
                        CardNetworkBadge(network, 24.dp, tint = ink)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CardBackBanner(
    card: StoredCard,
    theme: BankBrandTheme,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val quiet = quietTone(ink, container)

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(container)
            .drawWithContent {
                drawContent()
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
            }
            .padding(bottom = bottomInset),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(AppSpacing.xl))
            // Magnetic stripe
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            Row(Modifier.padding(horizontal = AppSpacing.lg)) {
                Box(
                    Modifier
                        .weight(3f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topStart = AppRadius.xs, bottomStart = AppRadius.xs))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = AppSpacing.sm),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "AUTHORIZED SIGNATURE",
                        style = TextStyle(color = quiet, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topEnd = AppRadius.xs, bottomEnd = AppRadius.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topEnd = AppRadius.xs, bottomEnd = AppRadius.xs)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (card.hasCvv) {
                        Text(
                            if (revealed) card.cvv else "\u2022".repeat(card.cvv.length),
                            style = TextStyle(
                                color = ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            "No CVV",
                            style = TextStyle(color = quiet, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    theme.displayName,
                    style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
internal fun AccountFrontBanner(
    data: AccountCardData,
    theme: BankBrandTheme,
    bankIconStyle: BankIconStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val quiet = quietTone(ink, container)
    val flags = accountFlags(data, compact)
    val showMeter = !compact && data.creditUsed != null
    val showFlags = flags.isNotEmpty()
    // With no limit to draw, the left panel says what share of your money the account holds (the carousel's own "Of your money"):
    // an account with no flags was one big panel with a header and a balance and nothing between them.
    val showShare = !compact && !showMeter && data.share != null && !data.hidden
    // With nothing for the panels below, the top panel is the whole card and has the card's own corners all round.
    val topShape = if (showMeter || showShare || showFlags) BannerTopShape else AppRadius.rLg

    Column(modifier.fillMaxSize()) {
        // Top panel (~1.45 weight)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(if (compact) 1.45f else 1.7f)
                .clip(topShape)
                .background(container)
                .drawConcentricRings(ink)
                .drawWithContent {
                    drawContent()
                    drawOutline(topShape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
                }
                .padding(AppSpacing.md),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f, fill = false)) {
                        if (data.bankName != null) {
                            DesignBankMark(data.bankName, theme, compact = bankIconStyle == BankIconStyle.Compact, ink = ink, onDark = container.luminance() < 0.5f)
                        } else {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(data.kind.icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(AppSpacing.sm))
                    Box(
                        Modifier
                            .clip(AppRadius.rFull)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                    ) {
                        Text(
                            data.kind.label.uppercase(),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 1.1.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    Text(
                        data.name,
                        style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.3.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        "BALANCE",
                        style = TextStyle(color = quiet, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.9.sp),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        data.balance,
                        style = TextStyle(
                            color = ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 32.sp,
                            letterSpacing = 0.2.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 32.sp),
                    )
                } else {
                    Text(
                        data.name,
                        style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.3.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (showMeter || showShare || showFlags) {
            Spacer(Modifier.height(BannerSeam))
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(BannerSeam),
            ) {
                // Two panels share the row; one alone takes all of it, with the card's own corners.
                val creditUsed = data.creditUsed
                if (showMeter && creditUsed != null) {
                    BannerPanel(if (showFlags) BannerBottomLeftShape else BannerBottomShape, container, ink, Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    data.credit.orEmpty(),
                                    style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(AppSpacing.xs))
                                Text(
                                    "${(creditUsed * 100).toInt()}% used",
                                    style = TextStyle(color = quiet, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.height(AppSpacing.xs))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(ink.copy(alpha = 0.15f))) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(creditUsed.coerceIn(0f, 1f))
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(if (data.overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
                if (showShare) {
                    BannerPanel(if (showFlags) BannerBottomLeftShape else BannerBottomShape, container, ink, Modifier.weight(1f).fillMaxHeight()) {
                        Column {
                            Text(
                                "OF YOUR MONEY",
                                style = TextStyle(color = quiet, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(AppSpacing.xxs))
                            Text(
                                data.share.orEmpty(),
                                style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum"),
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (showFlags) {
                    BannerPanel(if (showMeter || showShare) BannerBottomRightShape else BannerBottomShape, container, ink, Modifier.weight(1f).fillMaxHeight()) {
                        AccountFlagPills(flags, ink)
                    }
                }
            }
        }
    }
}

@Composable
fun CardPlateBanner(modifier: Modifier = Modifier) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier
            .fillMaxSize()
            .clip(BannerTopShape)
            .background(container)
            .drawConcentricRings(ink)
            .drawWithContent {
                drawContent()
                drawOutline(BannerTopShape.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
            },
    )
}

// ================================================================================================
// 2. LEDGER DESIGN
// ================================================================================================

@Composable
internal fun CardFrontLedger(
    card: StoredCard,
    theme: BankBrandTheme,
    revealed: Boolean,
    bankIconStyle: BankIconStyle,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val network = CardNetwork.fromNumber(card.cardNumber)

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(surface)
            .border(1.dp, outlineVariant, AppRadius.rLg)
            .padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg + bottomInset),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top row: 28dp disc, bank name, small outlined kind pill
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BankGlyph(card.bankName, tint = primary, size = 16.dp)
                    }
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        card.bankName,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Box(
                    Modifier
                        .border(1.dp, outlineVariant, AppRadius.rXs)
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                ) {
                    Text(
                        if (card.kind == CardKind.Credit) "CREDIT" else "DEBIT",
                        style = TextStyle(
                            color = onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Middle: Card name caption & number
            if (card.cardName.isNotBlank()) {
                Text(
                    card.cardName,
                    style = TextStyle(color = onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.xs))
            }

            if (revealed) {
                Text(
                    card.cardNumber.chunked(4).joinToString("  "),
                    style = TextStyle(
                        color = onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                        letterSpacing = 1.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 28.sp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "\u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  ",
                        style = TextStyle(
                            color = onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            fontSize = 20.sp,
                            letterSpacing = 2.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                    )
                    Text(
                        card.last4,
                        style = TextStyle(
                            color = onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 28.sp,
                            letterSpacing = 1.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom row: CARDHOLDER, EXPIRES, Network
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1.3f, fill = false)) {
                    Text(
                        "CARDHOLDER",
                        style = TextStyle(color = onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        card.cardholderName.uppercase(),
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(Modifier.weight(0.9f, fill = false)) {
                    Text(
                        "EXPIRES",
                        style = TextStyle(color = onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        "${card.expiryMonth}/${card.expiryYear}",
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
                if (network != CardNetwork.Unknown) {
                    CardNetworkBadge(network, 24.dp, tint = onSurface)
                }
            }
        }
    }
}

@Composable
internal fun CardBackLedger(
    card: StoredCard,
    theme: BankBrandTheme,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(surface)
            .border(1.dp, outlineVariant, AppRadius.rLg)
            .padding(bottom = bottomInset),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(AppSpacing.xl))
            // Tabular magnetic rule
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(BorderStroke(1.dp, outlineVariant)),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            Row(Modifier.padding(horizontal = AppSpacing.lg)) {
                // Signature block
                Box(
                    Modifier
                        .weight(3f)
                        .height(32.dp)
                        .background(surface)
                        .border(1.dp, outlineVariant, RoundedCornerShape(topStart = AppRadius.xs, bottomStart = AppRadius.xs))
                        .padding(start = AppSpacing.sm),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "AUTHORIZED SIGNATURE",
                        style = TextStyle(color = onSurfaceVariant, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                    )
                }
                // CVV on monospace tabular block
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, outlineVariant, RoundedCornerShape(topEnd = AppRadius.xs, bottomEnd = AppRadius.xs)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (card.hasCvv) {
                        Text(
                            if (revealed) card.cvv else "\u2022".repeat(card.cvv.length),
                            style = TextStyle(
                                color = onSurface,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            "No CVV",
                            style = TextStyle(color = onSurfaceVariant, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    theme.displayName,
                    style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
internal fun AccountFrontLedger(
    data: AccountCardData,
    theme: BankBrandTheme,
    bankIconStyle: BankIconStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(surface)
            .border(1.dp, outlineVariant, AppRadius.rLg)
            .padding(AppSpacing.lg),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (data.bankName != null) {
                            BankGlyph(data.bankName, tint = primary, size = 16.dp)
                        } else {
                            Icon(data.kind.icon, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        data.bankName ?: data.kind.label,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Box(
                    Modifier
                        .border(1.dp, outlineVariant, AppRadius.rXs)
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                ) {
                    Text(
                        data.kind.label.uppercase(),
                        style = TextStyle(
                            color = onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!compact) {
                Text(
                    data.name,
                    style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.3.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    "BALANCE",
                    style = TextStyle(color = onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.8.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    data.balance,
                    style = TextStyle(
                        color = onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        letterSpacing = 0.2.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 32.sp),
                )
                if (data.creditUsed != null) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    // 8dp rounded meter
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(data.creditUsed.coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(if (data.overLimit) MaterialTheme.colorScheme.error else primary),
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            data.credit.orEmpty(),
                            style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                            maxLines = 1,
                        )
                        Text(
                            "${(data.creditUsed * 100).toInt()}% used",
                            style = TextStyle(color = onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Text(
                    data.name,
                    style = TextStyle(color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.3.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.weight(1f))

            // Every flag Classic's face has; the name is said once, above.
            val flags = accountFlags(data, compact)
            if (flags.isNotEmpty()) AccountFlagPills(flags, onSurface)
        }
    }
}

@Composable
fun CardPlateLedger(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(surface)
            .border(1.dp, outlineVariant, AppRadius.rLg),
    )
}

// ================================================================================================
// 3. TINTED DESIGN
// ================================================================================================

@Composable
internal fun CardFrontTinted(
    card: StoredCard,
    theme: BankBrandTheme,
    revealed: Boolean,
    bankIconStyle: BankIconStyle,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val base = MaterialTheme.colorScheme
    val tintedScheme = tintedCardScheme(theme)

    val container = tintedScheme?.primaryContainer ?: base.primaryContainer
    val ink = tintedScheme?.onPrimaryContainer ?: base.onPrimaryContainer
    val primary = tintedScheme?.primary ?: base.primary
    val onPrimary = tintedScheme?.onPrimary ?: base.onPrimary
    val quiet = quietTone(ink, container)
    val network = CardNetwork.fromNumber(card.cardNumber)

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(container)
            .drawConcentricRings(ink)
            .drawWithContent {
                drawContent()
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
            }
            .padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg + bottomInset),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    DesignBankMark(card.bankName, theme, compact = bankIconStyle == BankIconStyle.Compact, ink = ink, onDark = container.luminance() < 0.5f)
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Box(
                    Modifier
                        .clip(AppRadius.rFull)
                        .background(primary)
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                ) {
                    Text(
                        if (card.kind == CardKind.Credit) "CREDIT" else "DEBIT",
                        style = TextStyle(
                            color = onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.1.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (card.cardName.isNotBlank()) {
                Text(
                    card.cardName.uppercase(),
                    style = TextStyle(
                        color = quiet,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.sm))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlineEmvChip(ink)
            }

            Spacer(Modifier.height(AppSpacing.md))

            if (revealed) {
                Text(
                    groupDigits(card.cardNumber),
                    style = TextStyle(
                        color = ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 26.sp,
                        letterSpacing = 1.2.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 26.sp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "\u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  ",
                        style = TextStyle(
                            color = quiet,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                    )
                    Text(
                        card.last4,
                        style = TextStyle(
                            color = ink,
                            fontWeight = FontWeight.Medium,
                            fontSize = 26.sp,
                            letterSpacing = 1.2.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "CARDHOLDER",
                        style = TextStyle(color = quiet, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        card.cardholderName.uppercase(),
                        style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column {
                    Text(
                        "EXPIRES",
                        style = TextStyle(color = quiet, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        "${card.expiryMonth}/${card.expiryYear}",
                        style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                }
                if (network != CardNetwork.Unknown) {
                    CardNetworkBadge(network, 24.dp, tint = ink)
                }
            }
        }
    }
}

@Composable
internal fun CardBackTinted(
    card: StoredCard,
    theme: BankBrandTheme,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val base = MaterialTheme.colorScheme
    val tintedScheme = tintedCardScheme(theme)

    val container = tintedScheme?.primaryContainer ?: base.primaryContainer
    val ink = tintedScheme?.onPrimaryContainer ?: base.onPrimaryContainer
    val quiet = quietTone(ink, container)

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(container)
            .drawWithContent {
                drawContent()
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
            }
            .padding(bottom = bottomInset),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(AppSpacing.xl))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            Row(Modifier.padding(horizontal = AppSpacing.lg)) {
                Box(
                    Modifier
                        .weight(3f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topStart = AppRadius.xs, bottomStart = AppRadius.xs))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = AppSpacing.sm),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "AUTHORIZED SIGNATURE",
                        style = TextStyle(color = quiet, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topEnd = AppRadius.xs, bottomEnd = AppRadius.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topEnd = AppRadius.xs, bottomEnd = AppRadius.xs)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (card.hasCvv) {
                        Text(
                            if (revealed) card.cvv else "\u2022".repeat(card.cvv.length),
                            style = TextStyle(
                                color = ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            "No CVV",
                            style = TextStyle(color = quiet, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    theme.displayName,
                    style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
internal fun AccountFrontTinted(
    data: AccountCardData,
    theme: BankBrandTheme,
    bankIconStyle: BankIconStyle,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val base = MaterialTheme.colorScheme
    val tintedScheme = tintedCardScheme(theme)

    val container = tintedScheme?.primaryContainer ?: base.primaryContainer
    val ink = tintedScheme?.onPrimaryContainer ?: base.onPrimaryContainer
    val primary = tintedScheme?.primary ?: base.primary
    val onPrimary = tintedScheme?.onPrimary ?: base.onPrimary
    val quiet = quietTone(ink, container)

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(container)
            .drawConcentricRings(ink)
            .drawWithContent {
                drawContent()
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
            }
            .padding(AppSpacing.lg),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    if (data.bankName != null) {
                        DesignBankMark(data.bankName, theme, compact = bankIconStyle == BankIconStyle.Compact, ink = ink, onDark = container.luminance() < 0.5f)
                    } else {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(data.kind.icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Box(
                    Modifier
                        .clip(AppRadius.rFull)
                        .background(primary)
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                ) {
                    Text(
                        data.kind.label.uppercase(),
                        style = TextStyle(
                            color = onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.1.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!compact) {
                Text(
                    data.name,
                    style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    "BALANCE",
                    style = TextStyle(color = quiet, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.9.sp),
                    maxLines = 1,
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    data.balance,
                    style = TextStyle(
                        color = ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        letterSpacing = 0.2.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 30.sp),
                )
                if (data.creditUsed != null) {
                    Spacer(Modifier.height(AppSpacing.sm))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(data.creditUsed.coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(if (data.overLimit) MaterialTheme.colorScheme.error else primary),
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            data.credit.orEmpty(),
                            style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                            maxLines = 1,
                        )
                        Text(
                            "${(data.creditUsed * 100).toInt()}% used",
                            style = TextStyle(color = quiet, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Text(
                    data.name,
                    style = TextStyle(color = ink, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.3.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.weight(1f))

            // Every flag Classic's face has, on pills of this face's own colours; the name is said once, above.
            val flags = accountFlags(data, compact)
            if (flags.isNotEmpty()) AccountFlagPills(flags, ink, scheme = tintedScheme ?: base)
        }
    }
}

@Composable
fun CardPlateTinted(theme: BankBrandTheme, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme
    val tintedScheme = tintedCardScheme(theme)

    val container = tintedScheme?.primaryContainer ?: base.primaryContainer
    val ink = tintedScheme?.onPrimaryContainer ?: base.onPrimaryContainer

    Box(
        modifier
            .fillMaxSize()
            .clip(AppRadius.rLg)
            .background(container)
            .drawConcentricRings(ink)
            .drawWithContent {
                drawContent()
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), ink.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
            },
    )
}
