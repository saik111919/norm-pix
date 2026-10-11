package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.ui.components.SwipeActionButton
import com.example.normpix.ui.components.SwipeActionWidth
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.components.SwipeRevealRow
import com.example.normpix.ui.components.segmentShape
import com.example.normpix.ui.theme.AppSpacing

/**
 * A transaction in the history list, swipeable to Edit and Delete. The swipe is only a shortcut: it
 * runs the same [onEdit] / [onDelete] flows as the details sheet (Delete still asks first), and the
 * same two actions are always exposed to TalkBack as custom actions, whether or not swiping is on.
 *
 * [swipe] null (or an empty choice) turns the gesture off and leaves the plain row.
 */
@Composable
fun SwipeableTransactionRow(
    transaction: FinanceTransaction,
    shape: Shape,
    swipe: TransactionSwipeActions?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shows the date in the row (lists that are not grouped under day headings). */
    showDate: Boolean = false,
    /** The account whose list this is, if it is one account's: its transfers show as money in or out of it. */
    perspectiveAccountId: Long? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val edit = swipe?.edit == true
    val delete = swipe?.delete == true
    SwipeRevealRow(
        actionsWidth = SwipeActionWidth * (if (edit) 1 else 0) + SwipeActionWidth * (if (delete) 1 else 0),
        key = transaction.id,
        enabled = edit || delete,
        shape = shape,
        modifier = modifier,
        actions = {
            if (edit) SwipeActionButton(Icons.Outlined.Edit, "Edit", scheme.secondaryContainer, scheme.onSecondaryContainer, onEdit)
            if (delete) SwipeActionButton(Icons.Outlined.DeleteOutline, "Delete", scheme.errorContainer, scheme.onErrorContainer, onDelete)
        },
    ) {
        // No corner of its own: the swipe row clips it to [shape], and squares the trailing corners off as it opens so
        // it meets the action tiles flush (a rounded one would leave a notch between its curve and the tile's corner).
        Surface(color = scheme.surfaceContainer, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
            TransactionTile(
                transaction = transaction,
                onClick = onOpen,
                showDate = showDate,
                perspectiveAccountId = perspectiveAccountId,
                modifier = Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit") { onEdit(); true },
                        CustomAccessibilityAction("Delete") { onDelete(); true },
                    )
                },
            )
        }
    }
}

/**
 * Transactions as one group of tonal segments, each swipeable to Edit and Delete exactly like the history's rows: the
 * form for lists that sit among other things (Home's Recent, search results). It follows the same swipe setting, and
 * a swipe is never the only way - tapping a row opens its details, where Edit and Delete are too.
 */
@Composable
fun SwipeableTransactionSection(
    transactions: List<FinanceTransaction>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val prefs by LocalAppContainer.current.preferences.state.collectAsState()
    val swipe = if (prefs.swipeActionsEnabled) prefs.transactionSwipeActions else null
    val actions = rememberFinanceActions()
    SwipeRevealHost(modifier) {
        Column(Modifier.fillMaxWidth()) {
            if (title != null) {
                androidx.compose.material3.Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm)
                        .semantics { heading() },
                )
            }
            transactions.forEachIndexed { index, t ->
                Box(Modifier.padding(top = if (index == 0) 0.dp else 2.dp)) {
                    SwipeableTransactionRow(
                        transaction = t,
                        shape = segmentShape(isFirst = index == 0, isLast = index == transactions.lastIndex),
                        swipe = swipe,
                        onOpen = { actions.showDetails(t) },
                        onEdit = { actions.editTransaction(t) },
                        onDelete = { actions.deleteTransaction(t) },
                        showDate = true,
                    )
                }
            }
        }
    }
}
