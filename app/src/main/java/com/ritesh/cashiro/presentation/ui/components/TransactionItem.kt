package com.ritesh.cashiro.presentation.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.CategoryEntity
import com.ritesh.cashiro.data.database.entity.SubcategoryEntity
import com.ritesh.cashiro.data.database.entity.TransactionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import com.ritesh.cashiro.presentation.ui.icons.Card
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.presentation.ui.theme.credit_dark
import com.ritesh.cashiro.presentation.ui.theme.credit_light
import com.ritesh.cashiro.presentation.ui.theme.expense_dark
import com.ritesh.cashiro.presentation.ui.theme.expense_light
import com.ritesh.cashiro.presentation.ui.theme.income_dark
import com.ritesh.cashiro.presentation.ui.theme.income_light
import com.ritesh.cashiro.presentation.ui.theme.investment_dark
import com.ritesh.cashiro.presentation.ui.theme.investment_light
import com.ritesh.cashiro.presentation.ui.theme.transfer_dark
import com.ritesh.cashiro.presentation.ui.theme.transfer_light
import com.ritesh.cashiro.utils.CurrencyFormatter
import com.ritesh.cashiro.utils.formatAmount
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import androidx.core.graphics.toColorInt
import com.ritesh.cashiro.presentation.ui.icons.Calendar
import com.ritesh.cashiro.presentation.ui.icons.DocumentText2
import com.ritesh.cashiro.presentation.ui.icons.Paperclip2

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.TransactionItem(
    modifier: Modifier = Modifier,
    transaction: TransactionEntity? = null,
    merchantName: String? = null,
    amount: BigDecimal? = null,
    transactionType: TransactionType? = null,
    categoryEntity: CategoryEntity? = null,
    subcategoryEntity: SubcategoryEntity? = null,
    accountIconResId: Int = 0,
    accountIconName: String? = null,
    accountColorHex: String? = null,
    showDate: Boolean = true,
    useCardStyle: Boolean = false,
    shape: CornerBasedShape = listSingleItemShape,
    onClick: () -> Unit = {},
    subtitleOverride: String? = null,
    amountOverride: String? = null,
    amountColorOverride: Color? = null,
    balanceAfter: BigDecimal? = null,
    balanceCurrency: String? = null,
    animatedContentScope: AnimatedVisibilityScope? = null,
    sharedElementKey: String? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongClick: () -> Unit = {},
    convertedAmount: BigDecimal? = null,
    mainCurrency: String? = null
) {
    val finalMerchantName = merchantName ?: transaction?.merchantName ?: ""
    val finalAmount = amount ?: transaction?.amount ?: BigDecimal.ZERO
    val finalType = transactionType ?: transaction?.transactionType ?: TransactionType.EXPENSE
    val isRecurring = transaction?.isRecurring ?: false
    val isDark = isSystemInDarkTheme()
    val amountColor = amountColorOverride ?: remember(finalType, isRecurring, isDark) {
        when (finalType) {
            TransactionType.INCOME -> if (!isDark) income_light else income_dark
            TransactionType.EXPENSE -> if (!isDark) expense_light else expense_dark
            TransactionType.CREDIT -> if (!isDark) credit_light else credit_dark
            TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
            TransactionType.INVESTMENT -> if (!isDark) investment_light else investment_dark
            TransactionType.BALANCE_UPDATE -> if (!isDark) transfer_light else transfer_dark
        }
    }

    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val defaultSubtitle = remember(transaction?.dateTime) { 
        transaction?.dateTime?.format(dateTimeFormatter) ?: "" 
    }
    val amountText = remember(transaction, amountOverride, finalAmount) {
        amountOverride ?: transaction?.formatAmount() ?: finalAmount.toString()
    }

    val dateTagColor = remember(transaction?.dateTime,) {
        val colors = listOf(income_dark, expense_dark, credit_dark, transfer_dark, investment_dark)

        val dateHash = transaction?.dateTime?.toLocalDate()?.hashCode() ?: 0
        val index = Math.abs(dateHash) % colors.size
        colors[index]
    }

    val itemModifier = modifier.then(
        if (animatedContentScope != null && sharedElementKey != null) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = sharedElementKey),
                animatedVisibilityScope = animatedContentScope,
                boundsTransform = { _, _ ->
                    spring(
                        stiffness =  Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center),
                clipInOverlayDuringTransition = OverlayClip(shape),
                renderInOverlayDuringTransition = false
            )
                .skipToLookaheadSize()
        } else Modifier
    )

    val leadingContent: @Composable () -> Unit = {
        BlurredAnimatedVisibility(isSelectionMode) {
            CashiroCheckbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() },
                modifier = Modifier.size(40.dp)
            )
        }
        BlurredAnimatedVisibility(!isSelectionMode){
            BrandIcon(
                merchantName = finalMerchantName,
                size = 40.dp,
                showBackground = true,
                categoryEntity = categoryEntity,
                subcategoryEntity = subcategoryEntity,
                category = transaction?.category,
                subcategory = transaction?.subcategory,
                accountIconResId = accountIconResId,
                accountIconName = accountIconName,
                accountColorHex = accountColorHex,
                modifier = if (animatedContentScope != null && transaction != null) {
                    Modifier.sharedElement(
                        rememberSharedContentState(key = "brand_icon_${transaction.id}"),
                        animatedVisibilityScope = animatedContentScope
                    )
                } else Modifier
            )
        }
    }

    // Build subtitle parts
    val recurringStr = stringResource(R.string.recurring)
    val balanceAfterStr = balanceAfter?.let { balance ->
        stringResource(R.string.balance_after_format, CurrencyFormatter.formatCurrency(balance, balanceCurrency ?: "INR"))
    }
    val (subtitleParts, subtitleFinal) = remember(
        subtitleOverride,
        defaultSubtitle,
        isRecurring,
        recurringStr,
        balanceAfterStr
    ) {
        val parts = buildList {
            if (subtitleOverride != null) {
                add(subtitleOverride)
            } else {
                if (defaultSubtitle.isNotEmpty()) {
                    add(defaultSubtitle)
                }
                if (isRecurring) add(recurringStr)

                balanceAfterStr?.let { add(it) }
            }
        }
        parts to parts.joinToString(" • ")
    }

    if (useCardStyle) {
        ListItemCard(
            title = finalMerchantName,
            subtitle = subtitleFinal,
            amount = amountText,
            amountColor = amountColor,
            onClick = onClick,
            leadingContent = leadingContent,
            modifier = itemModifier
        )
    } else {
        ListItem(
            headline = {
                Text(
                    text = finalMerchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            },
            supporting = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.basicMarquee()
                ) {
                    var needsSeparator = false
                    
                    @Composable
                    fun TagSeparator() {
                        if (needsSeparator) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                            )
                        }
                    }

                    // Date Tag
                    if (subtitleOverride == null && defaultSubtitle.isNotEmpty()) {
                        TagSeparator()
                        SubtitleTag(
                            icon = {
                                Icon(
                                    imageVector = Iconax.Calendar,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.85f)
                                )
                            },
                            text = defaultSubtitle,
                            color = dateTagColor,
                        )
                        needsSeparator = true
                    }

                    // Category Tag
                    categoryEntity?.let { category ->
                        TagSeparator()
                        SubtitleTag(
                            text = category.name,
                            color = try {
                                Color(category.color.toColorInt())
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        needsSeparator = true
                    }

                    if (subtitleOverride != null) {
                        TagSeparator()
                        Text(
                            text = subtitleOverride,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        needsSeparator = true
                    } else {
                        // Recurring Tag
                        if (isRecurring) {
                            TagSeparator()
                            SubtitleTag(
                                text = stringResource(R.string.recurring),
                                color = Color(0xFF5B54D6)
                            )
                            needsSeparator = true
                        }


                        // Balance Tag
                        balanceAfter?.let { balance ->
                            TagSeparator()
                            SubtitleTag(
                                text = stringResource(R.string.balance_after_format, CurrencyFormatter.formatCurrency(balance, balanceCurrency ?: "INR")),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            needsSeparator = true
                        }

                        // Description Indicator
                        if (transaction?.description?.isNotBlank() == true) {
                            TagSeparator()
                            Icon(
                                imageVector = Iconax.DocumentText2,
                                contentDescription = stringResource(R.string.description_indicator_desc),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                            needsSeparator = true
                        }

                        // Attachments Indicator
                        if (transaction?.attachments?.isNotBlank() == true) {
                            TagSeparator()
                            Icon(
                                imageVector = Iconax.Paperclip2,
                                contentDescription = stringResource(R.string.attachments_indicator_desc),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                            needsSeparator = true
                        }
                    }
                }
            },
            leading = leadingContent,
            trailing = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (subtitleOverride == null) {
                            when (finalType) {
                                TransactionType.CREDIT -> Icon(
                                    Iconax.Card,
                                    contentDescription = stringResource(R.string.type_credit_card),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isSystemInDarkTheme()) credit_light else credit_dark
                                )

                                TransactionType.TRANSFER -> Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.type_transfer),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isSystemInDarkTheme()) transfer_light else transfer_dark
                                )

                                TransactionType.INVESTMENT -> Icon(
                                    Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = stringResource(R.string.type_investment),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isSystemInDarkTheme()) investment_light else investment_dark
                                )

                                TransactionType.INCOME -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(R.string.type_income),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isSystemInDarkTheme()) income_light else income_dark
                                )

                                TransactionType.EXPENSE -> Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.type_expense),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isSystemInDarkTheme()) expense_light else expense_dark
                                )

                                TransactionType.BALANCE_UPDATE -> Icon(
                                    Icons.Rounded.SwapHoriz,
                                    contentDescription = stringResource(R.string.type_balance_update),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = if (!isSystemInDarkTheme()) transfer_light else transfer_dark
                                )
                            }
                        }
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor
                        )
                    }

                    if (convertedAmount != null && mainCurrency != null && transaction?.currency != mainCurrency) {
                        Text(
                            text = "≈ ${CurrencyFormatter.formatCurrency(convertedAmount, mainCurrency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            },
            onClick = {
                if (isSelectionMode) {
                    onSelectionToggle()
                } else {
                    onClick()
                }
            },
            onLongClick = onLongClick,
            shape = shape,
            listColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerLow,
            padding = PaddingValues(vertical = 1.5.dp),
            modifier = itemModifier
        )
    }
}


