package com.example.normpix.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.statement.DateDetection
import com.example.normpix.finance.statement.StatementCsv
import com.example.normpix.finance.statement.StatementDateFormat
import com.example.normpix.finance.statement.StatementDates
import com.example.normpix.finance.statement.StatementDraft
import com.example.normpix.finance.statement.StatementField
import com.example.normpix.finance.statement.StatementImport
import com.example.normpix.finance.statement.StatementLayout
import com.example.normpix.finance.statement.StatementMapping
import com.example.normpix.finance.statement.StatementReader
import com.example.normpix.finance.statement.StatementReading
import com.example.normpix.finance.statement.StatementSheet
import com.example.normpix.finance.ui.AccountSelector
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceUndo
import com.example.normpix.finance.ui.HERO_STATS_STACK_SCALE
import com.example.normpix.finance.ui.PickerField
import com.example.normpix.finance.ui.PickerFieldRow
import com.example.normpix.finance.ui.TransactionAmount
import com.example.normpix.finance.ui.offerUndo
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.FloatingActionBar
import com.example.normpix.ui.components.FloatingActionBarDefaults
import com.example.normpix.ui.components.FloatingActionIconButton
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.ListSegment
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.OptionSheet
import com.example.normpix.ui.components.OptionSheetItem
import com.example.normpix.ui.components.fadingBottomEdge
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the user settled in the first sheet: the account the statement is for, the columns, the way the dates are written and - for
 * a statement with one signed amount and no Dr / Cr - which sign is money out; with what those make of the file ([reading]).
 */
internal class StatementSetup(
    val accountId: Long,
    val mapping: StatementMapping,
    val format: StatementDateFormat,
    val positiveIsDebit: Boolean,
    val reading: StatementReading,
)

/** What the preview sheet ended with: go back to the columns, or import the rows whose [StatementRow.index] are in [rows]. */
internal sealed interface StatementChoice {
    data object Back : StatementChoice

    class Import(val rows: Set<Int>) : StatementChoice
}

private sealed interface Opened {
    class Table(val sheet: StatementSheet) : Opened

    class NotText(val kind: StatementCsv.NotText) : Opened

    data object NoTable : Opened
}

/**
 * Settings > Data & backup > Import a bank statement, step by step: the file (the system's picker, read once and kept nowhere), the
 * account and the columns (guessed from the file, the user's to change), a preview of every transaction it would make with the ones
 * already in NormPix unticked, and the import - all the ticked rows in one SQL transaction, with Undo. Nothing is read from the file
 * twice, copied, or logged, and nothing is saved until the user has seen the rows.
 */
internal class StatementImportFlows(
    private val finance: FinanceController,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val files: BackupFiles,
    private val scope: CoroutineScope,
    private val haptics: AppHaptics,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    suspend fun run() {
        if (finance.state.value.activeAccounts.none { !it.isLoan }) {
            overlays.alert(
                "Add an account first",
                "A statement is imported into one of your accounts, and there is none to import into. Add the account it is for, then try again.",
                icon = Icons.Outlined.ErrorOutline,
            )
            return
        }
        val bytes: ByteArray? = try {
            files.openStatement(StatementCsv.MAX_BYTES.toLong())
        } catch (e: CancellationException) {
            throw e
        } catch (_: BackupTooLargeException) {
            overlays.alert(
                "That file is too large",
                "A bank statement is a small file, and NormPix reads files up to 2 MB. This one is bigger, so it isn't one NormPix can read.",
                icon = Icons.Outlined.ErrorOutline,
            )
            return
        } catch (_: Exception) {
            overlays.alert("The file couldn't be read", "Try picking it again, or download the statement again.", icon = Icons.Outlined.ErrorOutline)
            return
        }
        if (bytes == null) return

        val opened = overlays.loading("Reading the statement", "Looking for the transactions in the file. NormPix keeps nothing of it.") {
            withContext(Dispatchers.Default) { open(bytes) }
        } ?: return
        val sheet = when (opened) {
            is Opened.Table -> opened.sheet
            is Opened.NotText -> {
                val what = if (opened.kind == StatementCsv.NotText.Pdf) "a PDF" else "an Excel file"
                overlays.alert(
                    "NormPix can't read $what",
                    "It reads CSV files. Download the statement as a CSV from your bank's website or app - or, in Excel, save the sheet as CSV - and pick that file.",
                    icon = Icons.Outlined.ErrorOutline,
                )
                return
            }
            Opened.NoTable -> {
                overlays.alert(
                    "No transactions found",
                    "NormPix looked for a table with a date column and an amount column (or a debit and a credit column) and found none. Check that this is the CSV of a statement.",
                    icon = Icons.Outlined.ErrorOutline,
                )
                return
            }
        }

        var previous: StatementSetup? = null
        while (true) {
            val accounts = finance.state.value.activeAccounts.filter { !it.isLoan }
            val before = previous
            val setup = overlays.sheet<StatementSetup>(expandable = true) { dismiss ->
                StatementSetupSheet(sheet, accounts, before, today(), onDone = dismiss)
            } ?: return
            previous = setup
            val account = accounts.firstOrNull { it.id == setup.accountId } ?: return
            val drafts = overlays.loading("Checking against NormPix", "Looking for what is in NormPix already.") {
                StatementImport.prepare(setup.reading.rows, finance.state.value.categories, finance.repository)
            } ?: return
            val choice = overlays.sheet<StatementChoice>(expandable = true) { dismiss ->
                StatementPreviewSheet(account, drafts, setup.reading.skipped, onDone = dismiss)
            } ?: return
            when (choice) {
                StatementChoice.Back -> continue
                is StatementChoice.Import -> {
                    save(account, drafts.filter { it.row.index in choice.rows })
                    return
                }
            }
        }
    }

    private fun open(bytes: ByteArray): Opened {
        StatementCsv.notText(bytes)?.let { return Opened.NotText(it) }
        val sheet = StatementLayout.find(StatementCsv.parse(StatementCsv.decode(bytes)))
        return if (sheet == null) Opened.NoTable else Opened.Table(sheet)
    }

    /** Saves [drafts] into [account] in one go - all of them or none - and says so on the page once the sheets have closed. */
    private suspend fun save(account: Account, drafts: List<StatementDraft>) {
        val transactions = StatementImport.transactions(drafts, account.id!!, now())
        val undo: FinanceUndo? = try {
            overlays.loading(
                "Importing",
                "Adding ${count(transactions.size)} to ${account.name}. Either all of them are added or none.",
                cancellable = false,
            ) { finance.saveTransactionsUndoably(transactions) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FinanceValidationException) {
            overlays.alert("Nothing was imported", "${e.message} No transactions were added.", icon = Icons.Outlined.ErrorOutline)
            return
        } catch (_: Exception) {
            overlays.alert("Nothing was imported", "Something went wrong while saving, so no transactions were added.", icon = Icons.Outlined.ErrorOutline)
            return
        }
        if (undo == null) return
        haptics.success()
        snackbars.offerUndo("${count(transactions.size)} imported into ${account.name}", finance, undo, scope)
    }
}

private fun count(n: Int) = if (n == 1) "1 transaction" else "$n transactions"

/**
 * The line that says what an import will do: "Import 23 of 27 (4 already in NormPix, 2 lines skipped)" - how many rows are ticked
 * of how many were read, then what was set aside.
 */
internal fun importSummary(ticked: Int, read: Int, alreadyThere: Int, skipped: Int, unfiled: Int): String {
    val aside = listOfNotNull(
        alreadyThere.takeIf { it > 0 }?.let { "$it already in NormPix" },
        unfiled.takeIf { it > 0 }?.let { "$it without a category" },
        skipped.takeIf { it > 0 }?.let { if (it == 1) "1 line skipped" else "$it lines skipped" },
    )
    return "Import $ticked of $read" + if (aside.isEmpty()) "" else " (${aside.joinToString(", ")})"
}

// ------------------------------------------------------------------ the columns

private val sampleDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun StatementField.icon(): ImageVector = when (this) {
    StatementField.Date -> Icons.Outlined.CalendarMonth
    StatementField.Description -> Icons.Outlined.Description
    StatementField.Debit -> Icons.Rounded.ArrowUpward
    StatementField.Credit -> Icons.Rounded.ArrowDownward
    StatementField.Amount -> Icons.Outlined.Payments
    StatementField.Direction -> Icons.Outlined.SwapVert
    StatementField.Reference -> Icons.Outlined.Receipt
}

private fun StatementField.hint(): String = when (this) {
    StatementField.Date -> "The day each transaction happened"
    StatementField.Description -> "What the bank wrote about each one"
    StatementField.Debit -> "Money going out: a withdrawal or debit"
    StatementField.Credit -> "Money coming in: a deposit or credit"
    StatementField.Amount -> "One column for both, told apart by a minus sign or Dr / Cr"
    StatementField.Direction -> "A column that says Dr or Cr"
    StatementField.Reference -> "The bank's reference, used to find what is in NormPix already"
}

private const val NO_COLUMN = -1

/** How [format] reads the first of [dates] it can, for the sheet that picks a format: "15/11/2026 is 15 Nov 2026". */
private fun formatSample(format: StatementDateFormat, dates: List<String>, today: LocalDate): String? {
    for (text in dates) {
        val date = format.parse(text, today)?.date ?: continue
        return "$text is ${sampleDay.format(date)}"
    }
    return null
}

/**
 * The first sheet: which account the statement is for, and which column is which - as the file's own headings suggest, each one the
 * user's to change in a sheet of its own that shows the first values in every column - and how its dates are written (asked when
 * dd/MM and MM/dd both fit). Goes on only with a clear reading of at least one transaction, and says what is missing in the sheet.
 * [previous] is what the user chose last time round, when they came back from the preview to change something.
 */
@Composable
internal fun StatementSetupSheet(
    sheet: StatementSheet,
    accounts: List<Account>,
    previous: StatementSetup?,
    today: LocalDate,
    onDone: (StatementSetup?) -> Unit,
) {
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    var accountId by remember { mutableStateOf(previous?.accountId ?: accounts.singleOrNull()?.id) }
    var mapping by remember { mutableStateOf(previous?.mapping ?: StatementLayout.guess(sheet)) }
    var positiveOverride by remember { mutableStateOf(previous?.positiveIsDebit) }
    var error by remember { mutableStateOf<String?>(null) }

    val dates = remember(sheet, mapping.date) { sheet.values(mapping.date) }
    val detection: DateDetection = remember(dates) { StatementDates.detect(dates, today) }
    var format by remember(detection) { mutableStateOf(previous?.format?.takeIf { it in detection.candidates } ?: detection.format) }
    val account = accounts.firstOrNull { it.id == accountId }
    val positiveIsDebit = positiveOverride ?: (account?.kind == AccountKind.Card)
    val chosen = format
    val reading = remember(sheet, mapping, chosen, positiveIsDebit) {
        if (chosen != null && mapping.isUsable) StatementReader.read(sheet, mapping, chosen, today, positiveIsDebit) else null
    }

    fun pick(field: StatementField) {
        scope.launch {
            val options = buildList {
                if (field != StatementField.Date) add(OptionSheetItem(NO_COLUMN, "None", "Leave this out"))
                for (column in 0 until sheet.width) {
                    add(OptionSheetItem(column, sheet.columnName(column), sheet.samples(column).joinToString(" · ") { it.take(SAMPLE_CHARS) }.ifEmpty { "Nothing in it" }))
                }
            }
            val picked = overlays.sheet<Int>(expandable = true) { dismiss ->
                OptionSheet(
                    title = field.label,
                    subtitle = field.hint(),
                    options = options,
                    selected = setOf(mapping[field] ?: NO_COLUMN),
                    onSelect = { dismiss(it) },
                )
            } ?: return@launch
            haptics.selection()
            mapping = mapping.with(field, picked.takeIf { it != NO_COLUMN })
            error = null
        }
    }

    fun pickFormat() {
        scope.launch {
            val options = detection.candidates.map { OptionSheetItem(it, it.label, formatSample(it, dates, today)) }
            val picked = overlays.sheet<StatementDateFormat>(expandable = true) { dismiss ->
                OptionSheet(
                    title = "Date format",
                    subtitle = "How the dates in this file are written",
                    options = options,
                    selected = setOfNotNull(format),
                    onSelect = { dismiss(it) },
                )
            } ?: return@launch
            haptics.selection()
            format = picked
            error = null
        }
    }

    fun pickPositive() {
        scope.launch {
            val picked = overlays.sheet<Boolean>(expandable = true) { dismiss ->
                OptionSheet(
                    title = "Positive amounts are",
                    subtitle = "This file has one amount column with no Dr / Cr beside it",
                    options = listOf(
                        OptionSheetItem(false, "Money in", "A minus sign means money out. Usual for a bank account."),
                        OptionSheetItem(true, "Money out", "A minus sign means money in. Usual for a credit card."),
                    ),
                    selected = setOf(positiveIsDebit),
                    onSelect = { dismiss(it) },
                )
            } ?: return@launch
            haptics.selection()
            positiveOverride = picked
            error = null
        }
    }

    fun review() {
        val read = reading
        val problem = when {
            accountId == null -> "Choose the account this statement is for."
            mapping.date == null -> "Choose the date column."
            !mapping.isUsable -> "Choose where the amounts are: a debit and a credit column, or one amount column."
            chosen == null && detection.ambiguous -> "Choose how the dates are written."
            chosen == null -> "The dates in that column can't be read. Choose another column."
            read == null || read.rows.isEmpty() -> "No transactions could be read with these choices. Check the columns and the date format."
            else -> null
        }
        error = problem
        if (problem == null && read != null && chosen != null) {
            onDone(StatementSetup(accountId!!, mapping, chosen, positiveIsDebit, read))
        }
    }

    val status = when {
        mapping.date == null -> "Choose the date column"
        !mapping.isUsable -> "Choose where the amounts are"
        chosen == null && detection.ambiguous -> "Choose how the dates are written"
        chosen == null -> "The dates in that column can't be read"
        reading == null || reading.rows.isEmpty() -> "No transactions found yet"
        else -> count(reading.rows.size) + " found" +
            if (reading.skipped > 0) ", ${if (reading.skipped == 1) "1 line" else "${reading.skipped} lines"} skipped" else ""
    }

    EditorSheet(
        title = "Import a bank statement",
        subtitle = status,
        saveLabel = "Review",
        onSave = ::review,
        errorText = error,
    ) {
        AccountSelector(
            label = "Which account is this for?",
            accounts = accounts,
            selected = accountId,
            onSelect = {
                accountId = it
                error = null
            },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            "Columns",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "NormPix guessed these from the file's headings. Change any that look wrong.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppSpacing.xs, bottom = AppSpacing.sm),
        )
        val fields = StatementField.entries.filter { it != StatementField.Direction || mapping.amount != null }
        for (field in fields) {
            PickerFieldRow(
                PickerField(field.label, mapping[field]?.let(sheet::columnName) ?: "None", field.icon()) { pick(field) },
                modifier = Modifier.padding(bottom = AppSpacing.xs),
            )
            // How the dates are written goes with the date column, where a question about it is seen.
            if (field == StatementField.Date) {
                PickerFieldRow(
                    PickerField(
                        "Date format",
                        when {
                            chosen != null -> chosen.label
                            detection.ambiguous -> "Choose"
                            mapping.date == null -> "Choose the date column first"
                            else -> "Can't read these dates"
                        },
                        Icons.Outlined.CalendarMonth,
                    ) { if (detection.candidates.isNotEmpty()) pickFormat() },
                    modifier = Modifier.padding(bottom = AppSpacing.xs),
                )
                if (detection.ambiguous) {
                    Text(
                        "Both ways of reading fit every date, so NormPix can't tell which is right. " + detection.samples.joinToString(" ") {
                            "${it.text} is ${sampleDay.format(it.dayFirst)} (dd/MM) or ${sampleDay.format(it.monthFirst)} (MM/dd)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AppSpacing.sm).politeLiveRegion(),
                    )
                }
            }
        }
        if (mapping.amount != null && mapping.direction == null) {
            PickerFieldRow(
                PickerField("Positive amounts are", if (positiveIsDebit) "Money out" else "Money in", Icons.Outlined.SwapVert) { pickPositive() },
                modifier = Modifier.padding(bottom = AppSpacing.xs),
            )
        }
    }
}

private const val SAMPLE_CHARS = 28

// ------------------------------------------------------------------ the preview

/**
 * The second sheet: every transaction the statement would make, as a row with a tick box - date, what it was, the amount, its type,
 * the category it would be filed under. A row that is in NormPix already starts unticked and says so. At the foot is what the import
 * will do, and the button that does it; the arrow goes back to the columns. [onDone] gets [StatementChoice.Import] with the rows
 * ticked, [StatementChoice.Back], or null if the sheet is closed (nothing imported).
 */
@Composable
internal fun StatementPreviewSheet(
    account: Account,
    drafts: List<StatementDraft>,
    skipped: Int,
    onDone: (StatementChoice?) -> Unit,
) {
    val state = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val ticked = remember(drafts) { mutableStateMapOf<Int, Boolean>().apply { drafts.forEach { put(it.row.index, it.tickedByDefault) } } }
    val importable = remember(drafts) { drafts.filter { it.importable } }
    val tickedCount by remember(drafts) { derivedStateOf { importable.count { ticked[it.row.index] == true } } }
    val allTicked = tickedCount == importable.size
    val summary = importSummary(
        ticked = tickedCount,
        read = drafts.size,
        alreadyThere = drafts.count { it.duplicate != null },
        skipped = skipped,
        unfiled = drafts.count { !it.importable },
    )
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.xs)) {
            Text(
                "Review the transactions",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "They go into ${account.name}. Untick what you don't want.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.xs),
            )
            Row(Modifier.fillMaxWidth().padding(top = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    modifier = Modifier.weight(1f).politeLiveRegion(),
                )
                TextButton(
                    onClick = { for (draft in importable) ticked[draft.row.index] = !allTicked },
                    enabled = importable.isNotEmpty(),
                ) { Text(if (allTicked) "Clear all" else "Select all") }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fadingBottomEdge(),
            contentPadding = PaddingValues(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = FloatingActionBarDefaults.FadeHeight),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            items(drafts, key = { it.row.index }) { draft ->
                val first = draft === drafts.first()
                val last = draft === drafts.last()
                StatementDraftRow(
                    draft = draft,
                    ticked = ticked[draft.row.index] == true,
                    first = first,
                    last = last,
                    state = state,
                    accountId = account.id,
                    onToggle = { ticked[draft.row.index] = ticked[draft.row.index] != true },
                )
            }
        }
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.xs, bottom = AppSpacing.md)) {
            FloatingActionBar(
                primaryLabel = if (tickedCount > 0) "Import $tickedCount" else "Import",
                onPrimary = { onDone(StatementChoice.Import(importable.filter { ticked[it.row.index] == true }.map { it.row.index }.toSet())) },
                primaryEnabled = tickedCount > 0,
                actions = { FloatingActionIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Change the columns", onClick = { onDone(StatementChoice.Back) }) },
            )
        }
    }
}

@Composable
private fun StatementDraftRow(
    draft: StatementDraft,
    ticked: Boolean,
    first: Boolean,
    last: Boolean,
    state: com.example.normpix.finance.ui.FinanceState,
    accountId: Long?,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val row = draft.row
    val money: MoneyFormatter = state.money
    val category = state.category(draft.categoryId)?.name
    val title = draft.payee ?: row.description.ifEmpty { "No description" }
    val stacked = LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE
    val where = draft.duplicate?.let { state.account(it.accountId) }?.takeIf { it.id != accountId }?.name
    ListSegment(isFirst = first, isLast = last, color = scheme.surfaceContainerHigh) {
        ListItem(
            modifier = Modifier.toggleable(value = ticked, enabled = draft.importable, role = Role.Checkbox, onValueChange = { onToggle() }),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Checkbox(checked = ticked, onCheckedChange = null, enabled = draft.importable) },
            headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    if (stacked) TransactionAmount(row.type, row.amount, money)
                    Text(
                        listOfNotNull(shortDate(row.date.atStartOfDay()), row.type.label, category).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (draft.payee != null && !row.description.equals(draft.payee, ignoreCase = true) && row.description.isNotEmpty()) {
                        Text(
                            row.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (draft.duplicate != null) {
                        AlreadyThere(where)
                    }
                    if (!draft.importable) {
                        Text("No category to file it under", style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    }
                }
            },
            trailingContent = if (stacked) null else ({ TransactionAmount(row.type, row.amount, money, style = MaterialTheme.typography.titleSmall) }),
        )
    }
}

/** The tag on a row that is in NormPix already; [elsewhere] names the account it was found in when that is not the statement's. */
@Composable
private fun AlreadyThere(elsewhere: String?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(top = AppSpacing.xs)
            .clip(AppRadius.rFull)
            .background(scheme.secondaryContainer)
            .padding(start = AppSpacing.sm, end = AppSpacing.md, top = AppSpacing.xxs, bottom = AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            "Already in NormPix" + if (elsewhere != null) ", in $elsewhere" else "",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSecondaryContainer,
        )
    }
}
