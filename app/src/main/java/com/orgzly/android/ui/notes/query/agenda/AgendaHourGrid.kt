package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import org.joda.time.DateTime
import kotlin.math.roundToInt

/**
 * As a height of [minutes] of the grid, with the hour height read while measuring, so that a pinch of the hours lays
 * the grid out again without recomposing it.
 */
private fun Modifier.minutesHeight(hourHeight: () -> Dp, minutes: Float) = layout { measurable, constraints ->
    val height = constraints.constrainHeight((hourHeight() * minutes / 60).roundToPx())
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

@Composable
internal fun AgendaHourLabels(hourHeight: () -> Dp, gutter: Dp, dimensions: AgendaGridDimensions,
                              formatMinute: (Int) -> String) {
    val colors = agendaGridColors()
    val hourTextSize = dimensions.hourTextSize
    Layout(modifier = Modifier.width(gutter).minutesHeight(hourHeight, 1440f), content = {
        for (hour in 1..23) {
            val time = remember(formatMinute, hour) { formatMinute(hour * 60) }
            Text(time, Modifier.padding(end = dimensions.hourLabelEndPadding).testTag("agenda_grid_hour_$hour"),
                fontSize = hourTextSize, lineHeight = hourTextSize * 1.2f, letterSpacing = 0.sp,
                maxLines = 1, softWrap = false, color = colors.secondaryText)
        }
    }) { measurables, constraints ->
        // Constraints that do not depend on the zoom, so that a pinch of the hours only moves the labels.
        val labels = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val hour = hourHeight()
        layout(constraints.maxWidth, constraints.maxHeight) {
            labels.forEachIndexed { index, label ->
                label.placeRelative(constraints.maxWidth - label.width,
                    (hour * (index + 1)).roundToPx() - label.height / 2)
            }
        }
    }
}

@Composable
internal fun AgendaTimedDay(
    day: DateTime,
    entries: List<AgendaGridEntry>,
    hourHeight: () -> Dp,
    now: DateTime,
    onNoteOpen: (Long) -> Unit,
    dimensions: AgendaGridDimensions,
    dayProgress: () -> Float,
    narrowLanes: Boolean,
    modifier: Modifier,
    formatMinute: (Int) -> String,
) {
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    val placements = remember(entries) { AgendaGridLayout.place(entries) }
    // Titles get smaller once hours are compressed. Derived, so that a pinch of the hours recomposes the day only
    // when it crosses that height.
    val compressed by remember(hourHeight, dimensions, scale) {
        derivedStateOf { hourHeight() < dimensions.compressedHourHeight * scale }
    }
    val blockDimensions = if (compressed) {
        dimensions.copy(titleTextSize = dimensions.compressedTitleTextSize)
    } else dimensions
    val colors = agendaGridColors()
    val titlePaints = rememberAgendaTitlePaints(blockDimensions.titleTextSize, colors)
    val lineColor = colors.gridLine
    val isToday = day.toLocalDate() == now.toLocalDate()
    val currentTimeColor = colors.secondaryText
    val endMinute = maxOf(1440f, placements.maxOfOrNull { it.visualEndMinute } ?: 1440f)
    Box(modifier.minutesHeight(hourHeight, endMinute).testTag("agenda_grid_timed_day_${day.toLocalDate()}")) {
        Canvas(Modifier.fillMaxWidth().minutesHeight(hourHeight, 1440f)) {
            val step = size.height / 24
            for (hour in 0..24) {
                drawLine(lineColor, Offset(0f, hour * step), Offset(size.width, hour * step), 1.dp.toPx())
            }
            drawLine(lineColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
        }
        // Lanes take their share of the width while measuring, so a pinch lays the blocks out again without
        // recomposing the day.
        Layout(content = {
            placements.forEach { placement ->
                key(placement.entry.key) {
                    AgendaEventBlock(placement.entry,
                        Modifier.padding(end = 2.dp, top = dimensions.timedBlockVerticalPadding,
                            bottom = dimensions.timedBlockVerticalPadding),
                        onNoteOpen, blockDimensions, colors, titlePaints, dayProgress,
                        compactLane = narrowLanes && placement.columns > 1,
                        formatMinute = formatMinute)
                }
            }
        }, Modifier.matchParentSize()) { measurables, constraints ->
            val width = constraints.maxWidth
            val hour = hourHeight()
            // Day keeps a wider margin after its last lane only, so that lanes side by side stay 2 dp apart; it
            // narrows to those 2 dp as a pinch goes from one day to two.
            val margin = lerp(AgendaViewMode.DAY.gridDimensions.blockEndSpacing - 2.dp, 0.dp, dayProgress())
            val lanes = (width - margin.roundToPx()).coerceAtLeast(0).toFloat()
            val blocks = measurables.mapIndexed { index, measurable ->
                val placement = placements[index]
                val start = placement.entry.startMinute!!
                val height = (hour * (placement.visualEndMinute - start) / 60).roundToPx()
                measurable.measure(Constraints.fixed((lanes / placement.columns).roundToInt(), height))
            }
            layout(width, constraints.maxHeight) {
                blocks.forEachIndexed { index, block ->
                    val placement = placements[index]
                    block.placeRelative((lanes * placement.column / placement.columns).roundToInt(),
                        (hour * placement.entry.startMinute!! / 60).roundToPx())
                }
            }
        }
        if (isToday) {
            Canvas(Modifier.fillMaxWidth().minutesHeight(hourHeight, 1440f)) {
                val y = size.height * (now.hourOfDay * 60 + now.minuteOfHour) / 1440
                drawLine(currentTimeColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                drawCircle(currentTimeColor, 3.dp.toPx(), Offset(3.dp.toPx(), y))
            }
        }
    }
}

