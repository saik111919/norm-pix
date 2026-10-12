package com.example.normpix.finance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.ui.Modifier
import com.example.normpix.finance.domain.BILLING_DAYS
import com.example.normpix.finance.domain.ordinalDay

/** The choices of a billing day: not set, then the 1st to the 31st, the last three saying what a short month does with them. */
private val billingDayOptions: List<ChoiceOption<Int?>> = buildList {
    add(ChoiceOption<Int?>(key = null, label = "Not set", icon = Icons.Outlined.EventBusy))
    for (day in BILLING_DAYS) {
        val label = "${ordinalDay(day)} of every month" + if (day >= 29) " · last day in shorter months" else ""
        add(ChoiceOption<Int?>(key = day, label = label, icon = Icons.Outlined.CalendarMonth))
    }
}

/**
 * A card's statement date or payment due date: one day of the month, or none. The same dropdown as the other choices in the
 * editor, so a day is one tap and a pick, not typing.
 */
@Composable
fun BillingDayField(label: String, day: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val options = remember { billingDayOptions }
    ChoiceDropdownField(
        label = label,
        options = options,
        selected = day,
        onSelect = onChange,
        modifier = modifier,
    )
}
