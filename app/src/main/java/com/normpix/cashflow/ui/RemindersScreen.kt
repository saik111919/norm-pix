package com.normpix.cashflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.normpix.cashflow.CashflowState
import com.normpix.cashflow.LocalCashTokens
import com.normpix.cashflow.Reminder
import com.normpix.cashflow.appsList
import com.normpix.cashflow.fmt
import java.util.Locale

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun parseTime24(t: String?): Pair<Int, Int> {
    val parts = (t ?: "09:00").split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 9) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun format24(h: Int, m: Int) = "%02d:%02d".format(h, m)

private fun formatTime12(t24: String?): String {
    val (hh, mm) = parseTime24(t24)
    val ap = if (hh >= 12) "PM" else "AM"
    val h12 = ((hh + 11) % 12) + 1
    return "$h12:${mm.toString().padStart(2, '0')} $ap"
}

private fun formatDateLabel(key: String?): String {
    if (key == null) return "Pick a date"
    val d = runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key) }.getOrNull() ?: return "Pick a date"
    return java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(d)
}

private fun reminderSub(r: Reminder): String {
    if (!r.on) return "Off"
    if (r.freq == "instant") return "Instant · fires the moment it’s triggered"
    if (r.freq == "daily") return "Daily · ${formatTime12(r.time)}"
    val freqLabel = if (r.freq == "weekly") "Weekly" else "Custom"
    val daysLabel = if (r.days.isNotEmpty()) r.days.joinToString(", ") else "Pick days"
    return "$freqLabel · $daysLabel · ${formatTime12(r.time)}"
}

/** Body text of the full-screen alarm, computed live from the app's current data (design's `alarmBody`). */
fun alarmBody(state: CashflowState, r: Reminder): String = when (r.type) {
    "daily_summary" -> {
        val today = state.txns.filter { it.day == "Today" && it.amount < 0 }
            .sortedByDescending { kotlin.math.abs(it.amount) }
        if (today.isEmpty()) "No expenses logged yet today."
        else "Highest to lowest: " + today.joinToString(", ") { "${it.merchant} ${state.fmt(it.amount)}" }
    }
    "weekly_summary" -> {
        val exp = state.txns.filter { it.amount < 0 }
        "You’ve spent ${state.fmt(exp.sumOf { kotlin.math.abs(it.amount) })} across ${exp.size} transactions this week."
    }
    "budget_alert" -> {
        val top = state.budgetCaps.entries
            .filter { it.value > 0 }
            .maxByOrNull { state.categorySpent(it.key) / it.value }
        if (top != null) {
            val pct = (state.categorySpent(top.key) / top.value * 100).let { kotlin.math.round(it).toInt() }
            "${top.key} is at $pct% of its ${state.fmt(top.value)} cap."
        } else "A category is nearing its cap."
    }
    "bill_due" -> {
        val sub = state.subsList.firstOrNull()
        if (sub != null) "${sub.name} ${state.fmt(sub.amt)} is ${sub.due}." else "A bill is coming due soon."
    }
    else -> {
        val tx = r.linkedTxnId?.let { id -> state.txns.find { it.id == id } }
        if (tx != null) "Review: ${tx.merchant} ${state.fmt(tx.amount)} · ${tx.whenLabel}" else "Time to check in on this."
    }
}

@Composable
private fun SegmentedControl(options: List<Triple<String, String, ImageVector?>>, value: String, onChange: (String) -> Unit) {
    val t = LocalCashTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, t.out, RoundedCornerShape(16.dp)),
    ) {
        options.forEachIndexed { i, (key, label, icon) ->
            val sel = value == key
            if (i > 0) Box(modifier = Modifier.width(1.dp).height(38.dp).background(t.out))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(if (sel) t.sc else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(key) },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    sel -> Icon(Icons.Rounded.Check, null, tint = t.onSc, modifier = Modifier.size(14.dp))
                    icon != null -> Icon(icon, null, tint = t.onSV, modifier = Modifier.size(14.dp))
                }
                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (sel) t.onSc else t.onSV)
            }
        }
    }
}

@Composable
private fun DayChips(days: List<String>, onToggle: (String) -> Unit) {
    val t = LocalCashTokens.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekdays.forEach { d ->
            val sel = d in days
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) t.sc else t.cont)
                    .clickable { onToggle(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(d, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (sel) t.onSc else t.onSV)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time24: String?, onChange: (String) -> Unit) {
    val t = LocalCashTokens.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, t.out, RoundedCornerShape(14.dp))
            .clickable { open = true }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = t.onSV, modifier = Modifier.size(18.dp))
        Text(formatTime12(time24), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.onS, modifier = Modifier.weight(1f))
    }
    if (open) {
        val (hh, mm) = parseTime24(time24)
        val pickerState = rememberTimePickerState(initialHour = hh, initialMinute = mm, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(format24(pickerState.hour, pickerState.minute)); open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateField(dateKey: String?, onChange: (String) -> Unit) {
    val t = LocalCashTokens.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, t.out, RoundedCornerShape(14.dp))
            .clickable { open = true }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = t.onSV, modifier = Modifier.size(18.dp))
        Text(formatDateLabel(dateKey), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.onS, modifier = Modifier.weight(1f))
    }
    if (open) {
        val initialMillis = dateKey?.let { runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time }.getOrNull() }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(it))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ReminderCard(state: CashflowState, r: Reminder) {
    val t = LocalCashTokens.current
    val editing = state.reminderEditing == r.id
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(t.hi),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { state.reminderEditing = if (editing) null else r.id }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(r.icon, 40.dp, bg = t.sc, tint = t.onSc, iconSize = 20.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(reminderSub(r), fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(
                Icons.Rounded.PlayCircle, contentDescription = "Preview full-screen alert", tint = t.onSV,
                modifier = Modifier.size(22.dp).clickable { state.fireAlarm(r) },
            )
            TogglePill(r.on) { state.toggleReminder(r.id) }
            Icon(if (editing) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = t.onSV, modifier = Modifier.size(20.dp))
        }
        if (editing) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(t.out.copy(alpha = 0.5f)))

                if (r.type == "custom") {
                    r.linkedTxnId?.let { id ->
                        state.txns.find { it.id == id }?.let { tx ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.cont).padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBubble(tx.icon, 34.dp, bg = t.sc, tint = t.onSc, iconSize = 16.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.merchant, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.onS, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${tx.whenLabel} · linked spend to review", fontSize = 11.sp, color = t.onSV, modifier = Modifier.padding(top = 1.dp))
                                }
                                Text(state.fmt(tx.amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = t.onS)
                            }
                        }
                    }
                    FieldLabel("Date")
                    DateField(r.date) { state.setReminderDate(r.id, it) }
                    FieldLabel("Time")
                    TimeField(r.time) { state.setReminderTime(r.id, it) }
                } else if (r.freq != "instant") {
                    FieldLabel("Frequency")
                    SegmentedControl(
                        listOf<Triple<String, String, ImageVector?>>(Triple("daily", "Daily", null), Triple("weekly", "Weekly", null), Triple("custom", "Custom", null)),
                        r.freq,
                    ) { state.setReminderFreq(r.id, it) }
                    if (r.freq != "daily") DayChips(r.days) { state.toggleReminderDay(r.id, it) }
                    FieldLabel("Time")
                    TimeField(r.time) { state.setReminderTime(r.id, it) }
                } else {
                    FieldLabel("Fires the instant a category nears its cap — no schedule needed")
                }

                FieldLabel("Priority")
                SegmentedControl(
                    listOf(
                        Triple("silent", "Silent", Icons.Rounded.NotificationsOff),
                        Triple("normal", "Normal", Icons.Rounded.Notifications),
                        Triple("urgent", "Urgent", Icons.Rounded.PriorityHigh),
                    ),
                    r.priority,
                ) { state.setReminderPriority(r.id, it) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sound", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = t.onS)
                        Text("Play a tone when it fires", fontSize = 11.5.sp, color = t.onSV, modifier = Modifier.padding(top = 1.dp))
                    }
                    TogglePill(r.sound) { state.setReminderSound(r.id, !r.sound) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vibration", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = t.onS)
                        Text("Buzz alongside the alert", fontSize = 11.5.sp, color = t.onSV, modifier = Modifier.padding(top = 1.dp))
                    }
                    TogglePill(r.vibration) { state.setReminderVibration(r.id, !r.vibration) }
                }

                if (r.type == "custom") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { state.removeReminder(r.id) },
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = t.err, modifier = Modifier.size(16.dp))
                        Text("Delete reminder", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.err)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddReminderForm(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.cont).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New reminder", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.onS)
        CashTextField(
            value = state.newReminderTitle, onValueChange = { state.newReminderTitle = it },
            placeholder = "Title (e.g. Review Amazon return window)", modifier = Modifier.fillMaxWidth(),
        )
        SegmentedControl(
            listOf<Triple<String, String, ImageVector?>>(Triple("event", "General event", null), Triple("review_spend", "Review a spend", null)),
            state.newReminderKind,
        ) { state.newReminderKind = it; state.newReminderTxnId = null }
        if (state.newReminderKind == "review_spend") {
            FieldLabel("Which spend?")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.txns.take(8).forEach { tx ->
                    ChoiceChip(
                        "${tx.merchant} · ${state.fmt(tx.amount)}", state.newReminderTxnId == tx.id,
                        { state.newReminderTxnId = tx.id }, icon = tx.icon,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Date")
                DateField(state.newReminderDate) { state.newReminderDate = it }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Time")
                TimeField(state.newReminderTime) { state.newReminderTime = it }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .border(1.dp, t.out, RoundedCornerShape(22.dp)).clickable { state.cancelAddReminder() },
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p) }
            Box(
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(t.p).clickable { state.saveAddReminder() },
                contentAlignment = Alignment.Center,
            ) { Text("Add reminder", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.onP) }
        }
    }
}

@Composable
fun RemindersSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Reminders", "Schedule when and how NormPix notifies you")
        if (!state.exactAlarmsAllowed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(t.warnBg)
                    .clickable { state.requestExactAlarmPermission() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Alarm, null, tint = t.warnFg, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow exact alarms", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = t.warnFg)
                    Text(
                        "Without this, reminders may fire late. Tap to enable in Settings.",
                        fontSize = 11.5.sp, color = t.warnFg, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        GroupCard {
            val fsCount = state.appExceptions.values.count { it }
            SettingRow(
                Icons.Rounded.Smartphone, "Full-screen behavior",
                "$fsCount of ${state.appExceptions.size} apps set to full-screen",
                onClick = { state.subScreen = "appExceptions" },
            )
        }
        SectionTitle("Scheduled")
        state.reminders.forEach { r -> ReminderCard(state, r) }
        if (state.addingReminder) {
            AddReminderForm(state)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.5.dp, t.out, RoundedCornerShape(26.dp))
                    .clickable { state.startAddReminder() },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = t.p, modifier = Modifier.size(18.dp))
                Text("New reminder — event or spend to review", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p)
            }
        }
    }
}

@Composable
fun AppExceptionsSubScreen(state: CashflowState) {
    val t = LocalCashTokens.current
    val query = state.appExceptionSearch.trim().lowercase()
    val filtered = appsList.filter { it.name.lowercase().contains(query) }
    val callApps = filtered.filter { it.category == "call" }
    val otherApps = filtered.filter { it.category == "other" }
    Column(modifier = subScreenModifier(state), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SubHeader(state, "Full-screen behavior", "Choose which apps stay notification-only")
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.cont).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Call, null, tint = t.onSV, modifier = Modifier.size(18.dp))
            Text(
                "Calling apps default to notification-only so a reminder never interrupts a call.",
                fontSize = 12.sp, color = t.onSV, lineHeight = 16.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(t.cont).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = t.onSV, modifier = Modifier.size(22.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.appExceptionSearch.isEmpty()) {
                    Text("Search apps", fontSize = 15.sp, color = t.onSV.copy(alpha = 0.7f))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = state.appExceptionSearch,
                    onValueChange = { state.appExceptionSearch = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = t.onS),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(t.p),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.appExceptionSearch.isNotEmpty()) {
                Icon(Icons.Rounded.Close, null, tint = t.onSV, modifier = Modifier.size(20.dp).clickable { state.appExceptionSearch = "" })
            }
        }
        if (callApps.isNotEmpty()) {
            SectionTitle("Suggested · calling & video apps")
            GroupCard { callApps.forEach { AppExceptionRow(state, it) } }
        }
        if (otherApps.isNotEmpty()) {
            SectionTitle("Other apps")
            GroupCard { otherApps.forEach { AppExceptionRow(state, it) } }
        }
        if (callApps.isEmpty() && otherApps.isEmpty()) {
            Text("No apps found", fontSize = 14.sp, color = t.onSV, modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { state.resetAppExceptions() },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.RestartAlt, null, tint = t.p, modifier = Modifier.size(18.dp))
            Text("Reset to defaults", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.p)
        }
    }
}

@Composable
private fun AppExceptionRow(state: CashflowState, app: com.normpix.cashflow.AppInfo) {
    val t = LocalCashTokens.current
    val isFs = state.appExceptions[app.name] == true
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.hi).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(app.icon, 40.dp, bg = t.sc, tint = t.onSc, iconSize = 19.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = t.onS)
            Text(if (isFs) "Full-screen reminder" else "Notification only", fontSize = 12.sp, color = t.onSV, modifier = Modifier.padding(top = 2.dp))
        }
        TogglePill(isFs) { state.setAppException(app.name, !isFs) }
    }
}
