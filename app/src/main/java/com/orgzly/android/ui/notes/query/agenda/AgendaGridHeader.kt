package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.orgzly.R
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.painterIcon
import org.joda.time.DateTime
import java.util.Locale

/**
 * Rows of all-day entries: up to three. When a date has more, the shelf is [collapsible];
 * while [collapsed], the last row of such a date shows how many entries are hidden.
 */
internal data class AgendaGridHeaderLayout(
    val rows: Int,
    val dateHeaderHeight: Dp,
    val rowHeight: Dp,
    val shelfHeight: Dp,
    val headerHeight: Dp,
    val collapsible: Boolean,
    val collapsed: Boolean,
)

internal fun agendaGridHeaderLayout(
    mode: AgendaViewMode,
    compactHeight: Boolean,
    expanded: Boolean,
    maxAllDay: Int,
    scale: Float,
    maxHeaderHeight: Dp = Dp.Infinity,
): AgendaGridHeaderLayout {
    val dimensions = mode.gridDimensions
    val collapsible = maxAllDay > 3
    val rows = if (expanded || !collapsible) maxAllDay else 3
    val dateHeaderHeight = (if (compactHeight) 40.dp else 54.dp) * scale
    val rowHeight = dimensions.allDayRowHeight * scale
    // Expanded, the entries may fill the grid, hiding the hours, and scroll beyond it.
    val allDayHeight = rowHeight * rows
    val shelfHeight = if (mode.dayCount == 1) maxOf(dateHeaderHeight, allDayHeight).coerceAtMost(maxHeaderHeight)
        else allDayHeight.coerceAtMost((maxHeaderHeight - dateHeaderHeight).coerceAtLeast(0.dp))
    val headerHeight = if (mode.dayCount == 1) shelfHeight else dateHeaderHeight + shelfHeight
    return AgendaGridHeaderLayout(rows, dateHeaderHeight, rowHeight, shelfHeight, headerHeight, collapsible,
        collapsed = collapsible && !expanded)
}

@Composable
internal fun AgendaAllDayColumn(day: AgendaGridDay, dimensions: AgendaGridDimensions, layout: AgendaGridHeaderLayout,
                                onNoteOpen: (Long) -> Unit, modifier: Modifier, dayProgress: () -> Float = { 1f },
                                pendingSummary: (@Composable (Modifier) -> Unit)? = null, onExpand: () -> Unit = {}) {
    val colors = agendaGridColors()
    val titlePaints = rememberAgendaTitlePaints(dimensions.allDayTextSize, colors)
    val count = day.allDay.size + if (pendingSummary != null) 1 else 0
    // Collapsed, the last row tells how many entries it and the following ones would show.
    val hidden = if (layout.collapsed && count > layout.rows) count - (layout.rows - 1) else 0
    val slots = layout.rows - if (hidden > 0) 1 else 0
    Column(modifier.height(layout.rowHeight * layout.rows)
        // The end margin, wider in Day, narrows with a pinch from one day to two while measuring.
        .layout { measurable, constraints ->
            val end = lerp(AgendaViewMode.DAY.gridDimensions.blockEndSpacing,
                AgendaViewMode.TWO_DAYS.gridDimensions.blockEndSpacing, dayProgress()).roundToPx()
            val placeable = measurable.measure(constraints.offset(horizontal = -end))
            layout(placeable.width + end, placeable.height) { placeable.placeRelative(0, 0) }
        }) {
        pendingSummary?.invoke(Modifier.fillMaxWidth().height(layout.rowHeight))
        day.allDay.take((slots - if (pendingSummary != null) 1 else 0).coerceAtLeast(0)).forEach { entry ->
            AgendaEventBlock(entry, Modifier.fillMaxWidth().height(layout.rowHeight)
                .padding(bottom = dimensions.allDayBlockSpacing), onNoteOpen, dimensions, colors, titlePaints, dayProgress)
        }
        if (hidden > 0) {
            val description = stringResource(R.string.agenda_grid_more, hidden)
            val expandLabel = stringResource(R.string.agenda_grid_expand_all_day)
            Box(Modifier.fillMaxWidth().height(layout.rowHeight)
                .clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand)
                .semantics { contentDescription = description }
                .testTag("agenda_grid_all_day_more_${day.day.toLocalDate()}")) {
                Text("+$hidden", Modifier.align(Alignment.CenterStart).padding(start = dimensions.blockStartPadding),
                    color = colors.text, fontSize = dimensions.allDayTextSize,
                    lineHeight = dimensions.allDayTextSize * AGENDA_TITLE_LINE_HEIGHT, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The arrow beside the all-day entries that shows them all or only the first rows. It sits in the last row; the area
 * that toggles them starts at the third row, where the arrow is while they are collapsed.
 */
@Composable
internal fun AgendaAllDayArrow(expanded: Boolean, onToggle: () -> Unit, rowHeight: Dp, modifier: Modifier) {
    val colors = agendaGridColors()
    val label = stringResource(if (expanded) R.string.agenda_grid_collapse_all_day else R.string.agenda_grid_expand_all_day)
    Box(modifier.padding(top = rowHeight * 2).fillMaxSize()
        .clickable(role = Role.Button, onClick = onToggle)
        .semantics { contentDescription = label }
        .testTag("agenda_grid_all_day_toggle"), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
            Icon(painterIcon(Icons.COLLAPSE_INDICATOR), null, Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                tint = colors.secondaryText)
        }
    }
}

@Composable
internal fun AgendaDayHeader(
    day: DateTime,
    now: DateTime,
    locale: Locale,
    dimensions: AgendaGridDimensions,
    compactHeight: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = agendaGridColors()
    val isToday = day.toLocalDate() == now.toLocalDate()
    val numberSize = if (compactHeight) 13.sp else dimensions.dayNumberSize
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    // Single lines measured without a width limit, so that a pinch does not lay them out again; the column clips them.
    Column(
        modifier.clipToBounds().clickable(onClick = onClick)
            .semantics { contentDescription = day.toString("EEEE d MMMM yyyy", locale) }
            .padding(vertical = 2.dp * scale)
            .testTag("agenda_grid_date_${day.toLocalDate()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // On a low screen the weekday shares the line of the number, so that the arrow of the all-day entries fits
        // below the date of Day.
        if (!compactHeight) {
            Text(day.toString("EEE", locale), Modifier.wrapContentWidth(unbounded = true),
                fontSize = dimensions.weekdaySize, lineHeight = 14.sp, maxLines = 1, softWrap = false)
        }
        // A highlight close around the number keeps it near its weekday and leaves room below the date of Day for the
        // arrow of the all-day entries.
        Box(Modifier.then(if (compactHeight) Modifier.height(20.dp * scale) else Modifier.size(28.dp * scale))
            .clip(RoundedCornerShape(6.dp))
            .background(if (isToday) colors.todayBackground else Color.Transparent),
            contentAlignment = Alignment.Center) {
            Text(
                if (compactHeight) day.toString("EEE d", locale) else day.dayOfMonth.toString(),
                Modifier.wrapContentWidth(unbounded = true), color = colors.text, fontWeight = FontWeight.Normal, fontSize = numberSize,
                lineHeight = if (compactHeight) 18.sp else 24.sp, maxLines = 1, softWrap = false,
            )
        }
    }
}
