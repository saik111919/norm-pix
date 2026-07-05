package com.ritesh.cashiro.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.presentation.ui.theme.expense_dark
import com.ritesh.cashiro.presentation.ui.theme.expense_light
import com.ritesh.cashiro.presentation.ui.theme.income_dark
import com.ritesh.cashiro.presentation.ui.theme.income_light
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun TransactionTotalsCard(
    modifier: Modifier = Modifier,
    income: BigDecimal,
    expenses: BigDecimal,
    netBalance: BigDecimal,
    currency: String,
    title: String? = null,
    isEstimated: Boolean = false,
    isLoading: Boolean = false,
) {
    val incomeAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = tween(300),
        label = "income_alpha"
    )
    
    val expenseAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = tween(300),
        label = "expense_alpha"
    )
    
    val netAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = tween(300),
        label = "net_alpha"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        CashiroCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(Spacing.lg),
            contentPadding = Spacing.sm
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Totals Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Income Column
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topEnd = Spacing.xs,
                                    topStart = Spacing.md,
                                    bottomEnd = Spacing.xs,
                                    bottomStart = Spacing.md)
                            )
                            .padding(Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        val formattedIncome = if (isEstimated) {
                            stringResource(R.string.estimated_amount_format, CurrencyFormatter.formatCurrency(income, currency))
                        } else {
                            CurrencyFormatter.formatCurrency(income, currency)
                        }
                        TotalColumn(
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(R.string.income),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (!isSystemInDarkTheme()) income_light else income_dark
                                )
                            },
                            label = stringResource(R.string.income),
                            amount = formattedIncome,
                            color = if (!isSystemInDarkTheme()) income_light else income_dark,
                            modifier = Modifier
                                .alpha(incomeAlpha)
                        )
                    }


                    // Vertical Divider
                    VerticalDivider(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 0.5.dp),
                        color = Color.Transparent
                    )

                    // Expenses Column
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topEnd = Spacing.xs,
                                    topStart = Spacing.xs,
                                    bottomEnd = Spacing.xs,
                                    bottomStart = Spacing.xs
                                )
                            )
                            .padding(Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        val formattedExpenses = if (isEstimated) {
                            stringResource(R.string.estimated_amount_format, CurrencyFormatter.formatCurrency(expenses, currency))
                        } else {
                            CurrencyFormatter.formatCurrency(expenses, currency)
                        }
                        TotalColumn(
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.expenses),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (!isSystemInDarkTheme()) expense_light else expense_dark
                                )
                            },
                            label = stringResource(R.string.expenses),
                            amount = formattedExpenses,
                            color = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                            modifier = Modifier
                                .alpha(expenseAlpha)
                        )
                    }

                    // Vertical Divider
                    VerticalDivider(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 0.5.dp),
                        color = Color.Transparent
                    )

                    // Net Balance Column
                    val netColor = when {
                        netBalance > BigDecimal.ZERO -> if (!isSystemInDarkTheme()) income_light else income_dark
                        netBalance < BigDecimal.ZERO -> if (!isSystemInDarkTheme()) expense_light else expense_dark
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val netPrefix = when {
                        netBalance > BigDecimal.ZERO -> "+"
                        else -> ""
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topEnd = Spacing.md,
                                    topStart = Spacing.xs,
                                    bottomEnd = Spacing.md,
                                    bottomStart = Spacing.xs
                                )
                            )
                            .padding(Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        val formattedNet = if (isEstimated) {
                            stringResource(R.string.estimated_amount_format, "$netPrefix${CurrencyFormatter.formatCurrency(netBalance, currency)}")
                        } else {
                            "$netPrefix${CurrencyFormatter.formatCurrency(netBalance, currency)}"
                        }
                        TotalColumn(
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.SettingsEthernet,
                                    contentDescription = stringResource(R.string.net),
                                    modifier = Modifier.size(20.dp),
                                    tint = netColor
                                )
                            },
                            label = stringResource(R.string.net),
                            amount = formattedNet,
                            color = netColor,
                            modifier = Modifier
                                .alpha(netAlpha)
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TotalColumn(
    icon: @Composable (() -> Unit)?,
    label: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE
            )
        )
    }
}
