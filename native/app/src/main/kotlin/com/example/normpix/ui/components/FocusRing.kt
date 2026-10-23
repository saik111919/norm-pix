package com.example.normpix.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** How thick the ring is: thick enough to be seen on a card, and well over the 2dp a visible focus indicator needs. */
private val FocusRingWidth = 3.dp

/**
 * A ring in the theme's primary colour inside the edge of a control while it has keyboard (or switch) focus, for the controls
 * that draw their own press - a card that sinks, a pill that fills - and so have turned off the ripple that would have shown
 * where focus is (WCAG 2.4.7: the focus indicator is visible). Give it the same [interactionSource] the control's click uses, and
 * its [shape]; apart from focus it changes nothing, and with a touch it is never there.
 */
@Composable
fun Modifier.focusRing(interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (focused) border(FocusRingWidth, MaterialTheme.colorScheme.primary, shape) else this
}
