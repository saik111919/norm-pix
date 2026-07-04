package com.normpix.cashflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.accountIcon
import com.normpix.cashflow.accounts
import com.normpix.cashflow.addSubCats
import com.normpix.cashflow.catIcons
import com.normpix.cashflow.evalAmount
import com.normpix.cashflow.formatShortDate
import com.normpix.cashflow.groupINR
import com.normpix.cashflow.keypad
import com.normpix.ui.floatingnav.glassPanel
import kotlin.math.roundToInt

private val KEYS = listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "-", ".", "0", "back", "+")

@Composable
fun AddScreen(state: CashflowState, glass: com.normpix.ui.floatingnav.GlassBackdropState?) {
    val t = LocalCashTokens.current
    AnimatedVisibility(
        visible = state.addOpen,
        enter = slideInVertically(tween(380, easing = Emphasized)) { it },
        exit = slideOutVertically(tween(380, easing = Emphasized)) { it },
    ) {
        // Drag-to-dismiss (design's startAddDrag): downward-only, 1:1 while dragging,
        // closes past a 110dp threshold, otherwise animates back to 0.
        var dragging by remember { mutableStateOf(false) }
        var dragDeltaPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val animatedOffsetPx by animateFloatAsState(
            targetValue = if (dragging) dragDeltaPx else 0f,
            animationSpec = if (dragging) snap() else tween(320, easing = Emphasized),
            label = "addDragOffset",
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedOffsetPx.roundToInt()) }
                .glassPanel(
                    glass, RectangleShape, blurRadius = state.sheetBlur.dp,
                    backgroundColor = t.surf, backgroundOpacity = state.sheetOpacity, saturation = 1.5f,
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Drag handle (design: 36x4 pill, in a 26dp-tall row, 6dp top margin)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(top = 6.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true; dragDeltaPx = 0f },
                            onDragEnd = {
                                if (dragDeltaPx > with(density) { 110.dp.toPx() }) state.addOpen = false
                                dragging = false
                            },
                            onDragCancel = { dragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            dragDeltaPx = (dragDeltaPx + dragAmount).coerceAtLeast(0f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(t.out),
                )
            }
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = { state.addOpen = false })
                Text("Add new", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = t.onS)
            }

            // Transaction | Subscription switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.cont)
                    .padding(4.dp),
            ) {
                listOf("txn" to "Transaction", "sub" to "Subscription").forEach { (key, name) ->
                    val sel = state.addTab == key
                    val bg by animateColorAsState(if (sel) t.hi else Color.Transparent, tween(240, easing = Emphasized), label = "addTab")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .then(if (sel) Modifier.shadow(2.dp, RoundedCornerShape(18.dp)) else Modifier)
                            .clip(RoundedCornerShape(18.dp))
                            .background(bg)
                            .clickable { state.addTab = key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) t.onS else t.onSV)
                    }
                }
            }

            // Scrollable middle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AmountDisplay(state)
                if (state.addTab == "txn") TxnFields(state) else SubFields(state)
            }

            // Keypad + save
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp * 4 + 8.dp * 3),
                ) {
                    items(KEYS) { k ->
                        val isOp = k.length == 1 && k[0] in charArrayOf('+', '-', '×', '÷')
                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isOp) t.sc else t.cont)
                                .clickable { state.addAmount = keypad(state.addAmount, k) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k == "back") {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = t.onSV, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    k, fontSize = if (isOp) 24.sp else 20.sp, fontWeight = FontWeight.Medium,
                                    color = if (isOp) t.onSc else t.onS,
                                )
                            }
                        }
                    }
                }
                val empty = state.addAmount.isEmpty() || evalAmount(state.addAmount) == 0.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (empty) t.p.copy(alpha = 0.4f) else t.p)
                        .clickable { if (state.addTab == "txn") state.saveAdd() else state.saveSub() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = t.onP, modifier = Modifier.size(20.dp))
                    Text(
                        if (state.addTab == "txn") {
                            when (state.addType) {
                                "income" -> "Save income"; "transfer" -> "Save transfer"; else -> "Save expense"
                            }
                        } else "Save subscription",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.onP,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountDisplay(state: CashflowState) {
    val t = LocalCashTokens.current
    val amtStr = state.addAmount.ifEmpty { "0" }
    val hasOp = amtStr.any { it in charArrayOf('+', '-', '×', '÷') }
    val amtDisplay = if (hasOp) amtStr else run {
        val parts = amtStr.split(".")
        val n = groupINR(parts[0].toDoubleOrNull() ?: 0.0).substringBefore(".")
        if (parts.size > 1) "$n.${parts[1]}" else n
    }
    val len = amtDisplay.length
    val targetSize = if (len <= 5) 56 else if (len <= 7) 48 else if (len <= 10) 40 else if (len <= 14) 32 else 26
    val amtSize by animateIntAsState(targetSize, tween(200, easing = Emphasized), label = "amtSize")
    val empty = state.addAmount.isEmpty() || evalAmount(state.addAmount) == 0.0
    val col = if (empty) t.onS.copy(alpha = 0.4f) else t.onS

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("₹", fontSize = (amtSize * 0.7).sp, fontWeight = FontWeight.Medium, color = col)
            Text(
                amtDisplay, fontSize = amtSize.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp, color = col, maxLines = 1,
            )
        }
        if (hasOp) {
            Text(
                "= ₹" + groupINR(evalAmount(state.addAmount)),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TxnFields(state: CashflowState) {
    val t = LocalCashTokens.current
    // Type pills: Expense / Income / Transfer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        listOf(
            Triple("expense", "Expense", Icons.Rounded.NorthEast),
            Triple("income", "Income", Icons.Rounded.SouthWest),
            Triple("transfer", "Transfer", Icons.Rounded.SwapHoriz),
        ).forEach { (key, name, icon) ->
            val sel = state.addType == key
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sel) t.pc else Color.Transparent)
                    .border(1.dp, if (sel) t.pc else t.out, RoundedCornerShape(18.dp))
                    .clickable {
                        state.addType = key
                        state.addCat = if (key == "income") "Income" else "Food"
                        state.addSubCat = null
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = if (sel) t.onPc else t.onSV, modifier = Modifier.size(16.dp))
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) t.onPc else t.onSV)
            }
        }
    }

    if (state.addType != "transfer") {
        CashTextField(
            value = state.addMerchant,
            onValueChange = { state.addMerchant = it },
            placeholder = if (state.addType == "income") "Source (e.g. Salary)" else "Title (e.g. Swiggy)",
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.addType == "expense") {
        FieldLabel("Category")
        ChipRowInline {
            listOf("Food", "Shopping", "Transport", "Bills").forEach { c ->
                ChoiceChip(c, state.addCat == c, { state.addCat = c; state.addSubCat = null }, icon = catIcons[c])
            }
        }
        FieldLabel("Subcategory")
        ChipRowInline {
            addSubCats[state.addCat].orEmpty().forEach { sc2 ->
                ChoiceChip(sc2, state.addSubCat == sc2, {
                    state.addSubCat = if (state.addSubCat == sc2) null else sc2
                })
            }
        }
    }

    if (state.addType == "transfer") {
        FieldLabel("From")
        ChipRowInline {
            accounts.forEach { a -> ChoiceChip(a, state.addFrom == a, { state.addFrom = a }, icon = accountIcon(a)) }
        }
        FieldLabel("To")
        ChipRowInline {
            accounts.filter { it != state.addFrom }.forEach { a ->
                ChoiceChip(a, state.addTo == a, { state.addTo = a }, icon = accountIcon(a))
            }
        }
    } else {
        FieldLabel("Account")
        ChipRowInline {
            accounts.forEach { a -> ChoiceChip(a, state.addAccount == a, { state.addAccount = a }, icon = accountIcon(a)) }
        }
    }

    FieldLabel("Date")
    ChipRowInline {
        ChoiceChip("Today", state.addDate == "Today", { state.addDate = "Today" }, icon = Icons.Rounded.Today)
        ChoiceChip("Yesterday", state.addDate == "Yesterday", { state.addDate = "Yesterday" }, icon = Icons.Rounded.History)
        ChoiceChip(
            state.addCustomDateMillis?.takeIf { state.addDate == "Custom" }?.let { formatShortDate(it) } ?: "Pick date",
            state.addDate == "Custom", { state.openDatePicker() }, icon = Icons.Rounded.CalendarMonth,
        )
    }
    if (state.showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = state.addCustomDateMillis ?: System.currentTimeMillis(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { state.closeDatePicker() },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { pickerState.selectedDateMillis?.let { state.pickCustomDate(it) } ?: state.closeDatePicker() },
                ) { Text("OK") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { state.closeDatePicker() }) { Text("Cancel") }
            },
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SubFields(state: CashflowState) {
    CashTextField(
        value = state.subName,
        onValueChange = { state.subName = it },
        placeholder = "Subscription name (e.g. Netflix)",
        modifier = Modifier.fillMaxWidth(),
    )
    FieldLabel("Billing cycle")
    ChipRowInline {
        listOf("Weekly", "Monthly", "Yearly").forEach { c ->
            ChoiceChip(c, state.subCycle == c, { state.subCycle = c }, icon = Icons.Rounded.Autorenew)
        }
    }
    FieldLabel("Account")
    ChipRowInline {
        accounts.forEach { a -> ChoiceChip(a, state.addAccount == a, { state.addAccount = a }, icon = accountIcon(a)) }
    }
}

/** Horizontally scrolling chip row used inside the already-padded add screen column. */
@Composable
private fun ChipRowInline(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
