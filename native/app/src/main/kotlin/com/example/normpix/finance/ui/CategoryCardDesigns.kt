package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.percentLabel
import com.example.normpix.settings.CardDesign
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.quietTone
import com.example.normpix.wallet.readableOnTone

/*
 * The category card (Home's Top spending, the Insights carousel) in each of the Card designs (owner, 2026-11-13: "categories card
 * redesign as the other cards we did, same setting apply to this also"). Classic is the card as it always was (CategoryStack.kt);
 * the others are the same family as the bank cards and the accounts (`wallet/CardDesigns.kt`) - Banner's panels, Ledger's flat
 * hairline surface, Tinted's flat tone with rings - drawn from the category's own colour where a bank card uses the bank's.
 *
 * Every face is as wide as it is given and [SPEND_ASPECT] times as wide as tall, so a deck of them (and the plate behind the front
 * one) is the same size whichever design is chosen. [compact] is the carousel's print: who it is and what it came to, nothing the
 * panel under the carousel says again.
 */

// As the bank cards' Banner: a card is clipped at AppRadius.lg by everything around it, so the outer corners are that; the corners
// where two panels meet are small.
private val BannerOuter = AppRadius.lg
private val BannerInner = AppRadius.xs
private val BannerSeam = 3.dp
private val BannerTop = RoundedCornerShape(topStart = BannerOuter, topEnd = BannerOuter, bottomStart = BannerInner, bottomEnd = BannerInner)
private val BannerBottomStart = RoundedCornerShape(topStart = BannerInner, topEnd = BannerInner, bottomStart = BannerOuter, bottomEnd = BannerInner)
private val BannerBottomEnd = RoundedCornerShape(topStart = BannerInner, topEnd = BannerInner, bottomStart = BannerInner, bottomEnd = BannerOuter)
private val BannerBottom = RoundedCornerShape(topStart = BannerInner, topEnd = BannerInner, bottomStart = BannerOuter, bottomEnd = BannerOuter)

/** A category's card in [design]: [compact] for the carousel's, else the stack's. */
@Composable
internal fun SpendCardFace(design: CardDesign, spend: CategorySpend, compact: Boolean) {
    when (design) {
        CardDesign.Classic -> if (compact) SpendCarouselFace(spend) else SpendFace(spend)
        CardDesign.Banner -> SpendFaceBanner(spend, compact)
        CardDesign.Ledger -> SpendFaceFlat(spend, compact, ledger = true)
        CardDesign.Tinted -> SpendFaceFlat(spend, compact, ledger = false)
    }
}

/** What stands behind the front card of a stack in [design]: the same card with nothing on it, and the same size. */
@Composable
internal fun SpendCardPlate(design: CardDesign, category: Category?) {
    val tone = spendTone(category)
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outlineVariant
    val edge = tone.onContainer.copy(alpha = 0.14f)
    val rings = tone.onContainer
    when (design) {
        CardDesign.Classic -> SpendSurface(tone) {}
        // The top panel only, as the bank cards' Banner plate is: the strip that shows above the next card is that panel's.
        CardDesign.Banner -> Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(SPEND_ASPECT)
                .clip(BannerTop)
                .background(tone.container)
                .drawConcentricRings(rings)
                .drawWithContent {
                    drawContent()
                    drawOutline(BannerTop.createOutline(size, layoutDirection, this), edge, style = Stroke(1.dp.toPx()))
                },
        )
        CardDesign.Ledger -> Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(SPEND_ASPECT)
                .clip(AppRadius.rLg)
                .background(scheme.surfaceContainerHigh)
                .drawWithContent {
                    drawContent()
                    drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), outline, style = Stroke(2.dp.toPx()))
                },
        )
        CardDesign.Tinted -> Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(SPEND_ASPECT)
                .clip(AppRadius.rLg)
                .background(tone.container)
                .drawConcentricRings(rings)
                .drawWithContent {
                    drawContent()
                    drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), edge, style = Stroke(2.dp.toPx()))
                },
        )
    }
}

/** Three faint rings in the top right corner of a flat face: the quiet mark the bank cards' Banner and Tinted have. */
private fun Modifier.drawConcentricRings(ink: Color): Modifier = drawBehind {
    val ringInk = ink.copy(alpha = 0.07f)
    val stroke = Stroke(1.dp.toPx())
    val center = Offset(size.width * 0.90f, size.height * 0.16f)
    val r = size.height * 0.28f
    drawCircle(ringInk, radius = r * 0.5f, center = center, style = stroke)
    drawCircle(ringInk, radius = r * 0.85f, center = center, style = stroke)
    drawCircle(ringInk, radius = r * 1.25f, center = center, style = stroke)
}

// ================================================================================================
// Banner: a wide top panel and two small ones under it
// ================================================================================================

@Composable
private fun SpendFaceBanner(spend: CategorySpend, compact: Boolean) {
    val money = rememberFinanceState().money
    val tone = spendTone(spend.category)
    val type = MaterialTheme.typography
    val ink = tone.onContainer
    // The panels under the top one are a step deeper in the category's own colour, so the three read as parts of one card.
    val lower = tone.accent.copy(alpha = 0.16f).compositeOver(tone.container)
    val lowerQuiet = quietTone(ink, lower)
    Column(Modifier.fillMaxWidth().aspectRatio(SPEND_ASPECT), verticalArrangement = Arrangement.spacedBy(BannerSeam)) {
        if (compact) {
            SpendPanel(BannerTop, tone.container, ink, Modifier.fillMaxWidth().weight(0.8f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpendAvatar(spend.category, tone)
                    Spacer(Modifier.width(AppSpacing.md))
                    Text(spend.name, style = type.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SpendPanel(BannerBottom, lower, ink, Modifier.fillMaxWidth().weight(1.1f)) {
                Column {
                    Text(
                        "SPENT IN ${monthLabel(spend.month).uppercase()}",
                        style = type.labelMedium,
                        color = lowerQuiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SpendAmount(money.format(spend.amount), ink)
                }
            }
        } else {
            SpendPanel(BannerTop, tone.container, ink, Modifier.fillMaxWidth().weight(1.25f)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpendAvatar(spend.category, tone)
                        Spacer(Modifier.width(AppSpacing.md))
                        Text(spend.name, style = type.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        spend.change?.let { ChangePill(it, ink) }
                    }
                    SpendAmount(money.format(spend.amount), ink)
                }
            }
            Row(Modifier.fillMaxWidth().weight(0.75f), horizontalArrangement = Arrangement.spacedBy(BannerSeam)) {
                SpendPanel(BannerBottomStart, lower, ink, Modifier.weight(1f).fillMaxSize()) {
                    Column {
                        Text("TRANSACTIONS", style = type.labelSmall, color = lowerQuiet, maxLines = 1)
                        Text(
                            "${spend.count}",
                            style = type.titleLarge.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
                            color = ink,
                            maxLines = 1,
                        )
                    }
                }
                SpendPanel(BannerBottomEnd, lower, ink, Modifier.weight(1f).fillMaxSize()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("OF SPENDING", style = type.labelSmall, color = lowerQuiet, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                        Text(
                            percentLabel(spend.share),
                            style = type.titleLarge.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
                            color = ink,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** One of the Banner's panels: [shape]'s corners, the container, a hairline of ink and room around what it holds. */
@Composable
private fun SpendPanel(shape: Shape, container: Color, ink: Color, modifier: Modifier, content: @Composable () -> Unit) {
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

@Composable
private fun SpendAmount(text: String, ink: Color) {
    val type = MaterialTheme.typography
    Text(
        text,
        style = type.headlineMedium.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
        color = ink,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = type.headlineMedium.fontSize),
    )
}

// ================================================================================================
// Ledger and Tinted: one flat surface; Ledger on the theme's own tone with a hairline, Tinted on the category's colour with rings
// ================================================================================================

@Composable
private fun SpendFaceFlat(spend: CategorySpend, compact: Boolean, ledger: Boolean) {
    val money = rememberFinanceState().money
    val scheme = MaterialTheme.colorScheme
    val tone = spendTone(spend.category)
    val type = MaterialTheme.typography
    val container = if (ledger) scheme.surfaceContainerHigh else tone.container
    val ink = if (ledger) scheme.onSurface else tone.onContainer
    val quiet = if (ledger) scheme.onSurfaceVariant else quietTone(ink, container)
    val edge = if (ledger) scheme.outlineVariant else ink.copy(alpha = 0.15f)
    val edgeWidth = if (ledger) 1.dp else 2.dp
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SPEND_ASPECT)
            .clip(AppRadius.rLg)
            .background(container)
            .then(if (ledger) Modifier else Modifier.drawConcentricRings(ink))
            .drawWithContent {
                drawContent()
                // Twice as wide as it shows: the clip takes the half that is outside.
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), edge, style = Stroke(edgeWidth.toPx() * 2))
            }
            .padding(AppSpacing.lg),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ledger) {
                    // The category's icon on a soft disc of its accent: a ledger's quiet mark, smaller than the tonal card's.
                    Box(Modifier.size(32.dp).background(tone.accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                        if (spend.category == null) SpendAvatar(null, tone) else CategoryGlyph(spend.category.icon, tone.accent, AppSpacing.xl)
                    }
                } else {
                    SpendAvatar(spend.category, tone)
                }
                Spacer(Modifier.width(AppSpacing.md))
                Text(
                    spend.name,
                    style = if (ledger) type.titleSmall.copy(fontWeight = FontWeight.SemiBold) else type.titleMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!compact) spend.change?.let { ChangePill(it, ink) }
            }
            Spacer(Modifier.weight(1f))
            if (compact) {
                Text(
                    "SPENT IN ${monthLabel(spend.month).uppercase()}",
                    style = type.labelMedium,
                    color = quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SpendAmount(money.format(spend.amount), ink)
            if (!compact) {
                Text(
                    "${spend.count} ${if (spend.count == 1) "transaction" else "transactions"} in ${monthLabel(spend.month)}",
                    style = type.bodyMedium,
                    color = quiet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLinearProgress(
                        progress = { spend.share.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        thickness = if (ledger) AppSpacing.xs else AppSpacing.sm,
                        color = tone.accent,
                        trackColor = if (ledger) scheme.surfaceContainerHighest else ink.copy(alpha = 0.14f),
                        calm = true,
                    )
                    Spacer(Modifier.width(AppSpacing.md))
                    if (ledger) {
                        Text(percentLabel(spend.share), style = type.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES), color = ink)
                    } else {
                        // Tinted: the share as the filled chip a Tinted card has its kind in.
                        Box(
                            Modifier
                                .clip(AppRadius.rFull)
                                .background(tone.accent)
                                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                        ) {
                            Text(
                                percentLabel(spend.share),
                                style = type.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.Bold),
                                color = readableOnTone(tone.accent, Color.White, Color.Black),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
