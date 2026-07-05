package com.ritesh.cashiro.presentation.ui.features.accounts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import com.ritesh.cashiro.presentation.ui.components.BrandIcon
import com.ritesh.cashiro.presentation.ui.components.ColorPickerContent
import com.ritesh.cashiro.presentation.ui.components.CurrencyBottomSheet
import com.ritesh.cashiro.presentation.ui.components.DeleteAccountDialog
import com.ritesh.cashiro.presentation.ui.features.categories.IconSelector
import com.ritesh.cashiro.presentation.ui.icons.Bag
import com.ritesh.cashiro.presentation.ui.icons.Card
import com.ritesh.cashiro.presentation.ui.icons.DollarCircle
import com.ritesh.cashiro.presentation.ui.icons.Edit2
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Information
import com.ritesh.cashiro.presentation.ui.icons.Wallet3
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import com.ritesh.cashiro.utils.IconResolutionUtils
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditAccountSheet(
    account: AccountBalanceEntity? = null,
    allAccounts: List<AccountBalanceEntity> = emptyList(),
    defaultCurrency: String = "INR",
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (bankName: String,
        balance: BigDecimal,
        accountLast4: String,
        iconResId: Int,
        iconName: String,
        colorHex: String,
        isCreditCard: Boolean,
        isWallet: Boolean,
        creditLimit: BigDecimal?,
        currency: String
    ) -> Unit
) {
    val context = LocalContext.current
    var bankName by remember { mutableStateOf(account?.bankName ?: "") }
    var balance by remember { mutableStateOf(account?.balance ?: BigDecimal.ZERO) }
    var creditLimit by remember { mutableStateOf(account?.creditLimit ?: BigDecimal.ZERO) }
    var isCreditCard by remember { mutableStateOf(account?.isCreditCard ?: false) }
    var isWallet by remember { mutableStateOf(account?.isWallet ?: false) }
    var accountLast4 by remember { mutableStateOf(account?.accountLast4 ?: "") }
    var selectedCurrency by remember { mutableStateOf(account?.currency ?: defaultCurrency) }
    var iconResId by remember {
        mutableStateOf(
            if (account?.iconResId != 0 && account?.iconResId != null) account.iconResId
            else R.drawable.type_finance_bank
        )
    }
    var iconName by remember {
        mutableStateOf(account?.iconName ?: IconResolutionUtils.resIdToName(context, iconResId))
    }
    var colorHex by remember { mutableStateOf(account?.color ?: "#33B5E5") }

    var showNumberPad by remember { mutableStateOf(false) }
    var editingCreditLimit by remember { mutableStateOf(false) }
    var showIconSelector by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showNumberPad) {
        ModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = if (editingCreditLimit) creditLimit.toString() else balance.toString(),
                onDone = {
                    if (editingCreditLimit) {
                        creditLimit = it.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    } else {
                        balance = it.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    }
                    showNumberPad = false
                    },
                title = if (editingCreditLimit) stringResource(R.string.enter_credit_limit) 
                        else if (account == null) stringResource(R.string.enter_amount) 
                        else stringResource(R.string.update_amount)
            )
        }
    }

    if (showIconSelector) {
        ModalBottomSheet(
            onDismissRequest = { showIconSelector = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            IconSelector(
                selectedIconName = iconName,
                onIconSelected = { name ->
                    iconName = name
                    iconResId = IconResolutionUtils.nameToResId(context, name)
                    showIconSelector = false
                }
            )
        }
    }

    if (showCurrencySheet) {
        CurrencyBottomSheet(
            selectedCurrency = selectedCurrency,
            onCurrencySelected = { currency ->
                selectedCurrency = currency
                showCurrencySheet = false
            },
            onDismiss = { showCurrencySheet = false }
        )
    }


    if (showDeleteConfirmation) {
        DeleteAccountDialog(
            bankName = bankName,
            accountLast4 = accountLast4,
            accountIcon = iconResId,
            accountColor = colorHex,
            isCreditCard = isCreditCard,
            isWallet = isWallet,
            onDismiss = { showDeleteConfirmation = false },
            onDelete = {
                onDelete?.invoke()
                showDeleteConfirmation = false
            }
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = if (account == null) stringResource(R.string.add_account_title) else stringResource(R.string.edit_account_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.Bold
            )

            // Account Type Selection
            if (account == null) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = !isCreditCard && !isWallet,
                        onClick = { 
                            if (isWallet) {
                                // Clear fields if coming from Wallet
                                bankName = ""
                                accountLast4 = ""
                                iconResId = R.drawable.type_finance_bank
                                colorHex = "#33B5E5"
                            }
                            isCreditCard = false
                            isWallet = false 
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            inactiveBorderColor = Color.Transparent,
                            activeBorderColor = Color.Transparent
                        ),
                        icon = {}
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountBalance, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.type_bank))
                        }
                    }
                    SegmentedButton(
                        selected = isCreditCard,
                        onClick = { 
                            isCreditCard = true
                            isWallet = false 
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            inactiveBorderColor = Color.Transparent,
                            activeBorderColor = Color.Transparent
                        ),
                        icon = {}
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Iconax.Card, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.type_card))
                        }
                    }
                    SegmentedButton(
                        selected = isWallet,
                        onClick = { 
                            isWallet = true
                            isCreditCard = false
                            accountLast4 = "wallet"
                            bankName = "Cash"
                            iconName = "type_finance_dollar_banknote"
                            iconResId = R.drawable.type_finance_dollar_banknote
                            colorHex = "#8BC34A"
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            inactiveBorderColor = Color.Transparent,
                            activeBorderColor = Color.Transparent
                        ),
                        icon = {}
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Iconax.Wallet3, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.type_wallet))
                        }
                    }
                }
            }

            // Preview Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PreviewAccountCard(
                    bankName = bankName.ifEmpty { stringResource(R.string.preview_bank_name) },
                    balance = balance,
                    accountLast4 = accountLast4.ifEmpty { "0000" },
                    iconResId = iconResId,
                    iconName = iconName,
                    colorHex = colorHex,
                    currency = selectedCurrency,
                    isCreditCard = isCreditCard,
                    isWallet = isWallet,
                    creditLimit = creditLimit
                )
                Text(
                    text = stringResource(R.string.preview_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Input Fields
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon Button
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .clickable { showIconSelector = true },
                        contentAlignment = Alignment.Center
                    ) {
                        BrandIcon(
                            merchantName = bankName,
                            size = 58.dp,
                            accountIconResId = iconResId,
                            accountIconName = iconName,
                            accountColorHex = colorHex
                        )
                        // Edit badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.surface,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Iconax.Edit2,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    // Balance/Outstanding Input
                    Surface(
                        onClick = { 
                            editingCreditLimit = false
                            showNumberPad = true 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leading Icon
                            Icon(
                                imageVector = Iconax.Wallet3,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Label and Value
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = if (isCreditCard) stringResource(R.string.outstanding_label) else stringResource(R.string.balance_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = CurrencyFormatter.formatCurrency(
                                        balance,
                                        selectedCurrency
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (isCreditCard) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Credit Limit Input
                    Surface(
                        onClick = { 
                            editingCreditLimit = true
                            showNumberPad = true 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Iconax.Card,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = stringResource(R.string.credit_limit_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = CurrencyFormatter.formatCurrency(
                                        creditLimit,
                                        selectedCurrency
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Available Credit Tip
                    val availableCredit = creditLimit - balance
                    val utilization = if (creditLimit > BigDecimal.ZERO) {
                        ((balance.toDouble() / creditLimit.toDouble()) * 100).toInt()
                    } else 0

                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                Iconax.Information,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.available_credit_label, CurrencyFormatter.formatCurrency(availableCredit, selectedCurrency)),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.utilization_label, utilization),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bank Name Row
                TextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(if (isWallet) stringResource(R.string.wallet_name_label) else stringResource(R.string.bank_name_label), fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            0.7f
                        )
                    ),
                    leadingIcon = { Icon(Iconax.Edit2, contentDescription = null)
                    }
                )
 
                if (!isWallet) {
                    TextField(
                        value = accountLast4,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() })
                            accountLast4 = it },
                        label = { Text(stringResource(R.string.account_number_last_4_label), fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.placeholder_last_4_digits)) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                0.7f
                            )
                        ),
                        leadingIcon = { Icon(Icons.Rounded.Pin, contentDescription = null) }
                    )
                }

                // Currency Selection
                val currencyInteractionSource = remember { MutableInteractionSource() }
                TextField(
                    value = "$selectedCurrency (${CurrencyFormatter.getCurrencySymbol(selectedCurrency)})",
                    onValueChange = {},
                    label = { Text(stringResource(R.string.currency_label), fontWeight = FontWeight.SemiBold) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = currencyInteractionSource,
                            indication = null
                        ) {
                            showCurrencySheet = true
                        },
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledIndicatorColor = Color.Transparent,
                        disabledLabelColor = MaterialTheme.colorScheme.primary,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Iconax.DollarCircle,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.select_currency))
                    },
                    enabled = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Color Picker Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column{
                        ColorPickerContent(
                            initialColor = colorHex.toColorInt(),
                            onColorChanged = { colorInt ->
                                colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Action Buttons at Bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button (only for existing accounts)
                if (onDelete != null && account != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.colorScheme.error
                                )
                            )
                        ),
                        shape = MaterialTheme.shapes.extraExtraLarge
                    ) {
                        Icon(
                            imageVector = Iconax.Bag,
                            contentDescription = stringResource(R.string.delete_account_desc)
                        )
                    }
                }
 
                // Save button
                Button(
                    onClick = {
                        onSave(
                            bankName,
                            balance,
                            accountLast4,
                            iconResId,
                            iconName,
                            colorHex,
                            isCreditCard,
                            isWallet,
                            if (isCreditCard) creditLimit else null,
                            selectedCurrency
                        )
                    },
                    enabled = bankName.isNotBlank() && (isWallet || accountLast4.length == 4),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.extraExtraLarge
                ) {
                    Text(
                        text = if (account == null) stringResource(R.string.add_account_title) else stringResource(R.string.save_changes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewAccountCard(
    bankName: String,
    balance: BigDecimal,
    accountLast4: String,
    iconResId: Int,
    iconName: String,
    colorHex: String,
    currency: String,
    isCreditCard: Boolean = false,
    isWallet: Boolean = false,
    creditLimit: BigDecimal = BigDecimal.ZERO
) {
    Card(
        modifier = Modifier.padding(bottom = Spacing.sm).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Balance/Outstanding Section
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                Text(
                    text = if (isCreditCard) stringResource(R.string.outstanding_label) else stringResource(R.string.balance_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = CurrencyFormatter.formatCurrency(balance, currency),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            BlurredAnimatedVisibility(
                visible = isCreditCard,
                enter = fadeIn() + slideInVertically(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut() + slideOutVertically(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.credit_limit_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(creditLimit, currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Bank Info Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = bankName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isWallet) stringResource(R.string.type_wallet_lowercase) else "**** **** **** $accountLast4",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                    }

                    BrandIcon(
                        merchantName = bankName,
                        size = 48.dp,
                        showBackground = true,
                        accountIconResId = iconResId,
                        accountIconName = iconName,
                        accountColorHex = colorHex
                    )
                }
            }
        }
    }
}
