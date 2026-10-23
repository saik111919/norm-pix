package com.example.normpix.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing

/**
 * A page's content in one column in reading order ([narrow]) on phones, and in two balanced columns ([left], [right]) once the
 * room it is given is [AppLayout.twoColumnMinWidth] or more: a tablet in landscape, a foldable opened out. What counts is the
 * room the content has, not the window's width - a tablet held upright has a rail beside the page and stays one column.
 *
 * Both columns scroll together with the page, so a column that runs longer than the other just ends first.
 */
@Composable
fun AdaptiveColumns(
    narrow: @Composable ColumnScope.() -> Unit,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (!isTwoColumn(maxWidth)) {
            Column(content = narrow)
        } else {
            Row {
                Column(Modifier.weight(1f), content = left)
                Spacer(Modifier.width(AppSpacing.xl))
                Column(Modifier.weight(1f), content = right)
            }
        }
    }
}

/** Whether [room] is wide enough for two columns. */
fun isTwoColumn(room: Dp): Boolean = room >= AppLayout.twoColumnMinWidth
