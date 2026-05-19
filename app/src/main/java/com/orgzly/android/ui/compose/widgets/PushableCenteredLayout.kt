package com.orgzly.android.ui.compose.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Dp
import cl.emilym.compose.units.rdp

@Composable
fun PushableCenteredLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 1.rdp,
    centerContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Layout(
            content = {
                Box(Modifier.layoutId("center")) { centerContent() }
                Box(Modifier.layoutId("right")) { rightContent() }
            }
        ) { measurables, constraints ->

            val rightMeasurable = measurables.first { it.layoutId == "right" }
            val centerMeasurable = measurables.first { it.layoutId == "center" }

            val rightPlaceable = rightMeasurable.measure(constraints)
            val adjustedSpacing = (if (rightPlaceable.width > 0) spacing.roundToPx() else 0)

            val centerPlaceable = centerMeasurable.measure(constraints.copy(
                maxWidth = constraints.maxWidth - rightPlaceable.width - adjustedSpacing
            ))

            val totalWidth = constraints.maxWidth
            val totalHeight = maxOf(centerPlaceable.height, rightPlaceable.height)
            val rightWidth = rightPlaceable.width
            val centerWidth = centerPlaceable.width

            val centerX = ((totalWidth - centerWidth) / 2).coerceAtMost(
                totalWidth - rightWidth - centerWidth - adjustedSpacing)

            layout(totalWidth, totalHeight) {
                centerPlaceable.place(x = centerX, y = (totalHeight - centerPlaceable.height) / 2)
                rightPlaceable.place(x = totalWidth - rightWidth, y = (totalHeight - rightPlaceable.height) / 2)
            }
        }
    }
}