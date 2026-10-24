package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.joda.time.DateTime
import org.joda.time.LocalDate
import java.util.Locale

@Composable
internal fun AgendaSlidingDays(
    sources: AgendaGridSources,
    anchor: DateTime,
    now: DateTime,
    locale: Locale,
    mode: AgendaViewMode,
    hourHeight: () -> Dp,
    scroll: ScrollState,
    is24Hour: Boolean,
    compactHeight: Boolean,
    /** Height of the whole grid, which expanded all-day entries may fill. */
    gridHeight: Dp,
    expanded: Boolean,
    prefetchEnabled: () -> Boolean,
    onDateChange: (String) -> Unit,
    onDayOpen: (DateTime) -> Unit,
    onNoteOpen: (Long) -> Unit,
    onToggleAllDay: () -> Unit,
    viewportModifier: (Dp) -> Modifier,
    formatMinute: (Int) -> String,
    modifier: Modifier = Modifier,
    visibleDayCount: () -> Float = { mode.dayCount.toFloat() },
    swipeEnabled: Boolean = true,
    resizing: () -> Boolean = { false },
    pendingSummary: (@Composable (Modifier) -> Unit)? = null,
) {
    // Columns depend on these rather than on the view, so that a pinch between day counts leaves them unchanged.
    val dimensions = mode.gridDimensions
    val lineColor = agendaGridColors().gridLine
    val narrowLanes = mode.dayCount >= 4
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    val gutter = dimensions.hourGutter(is24Hour) * scale
    val cache = remember(sources) { mutableMapOf<LocalDate, AgendaGridDay>() }
    fun dayData(date: LocalDate): AgendaGridDay = cache.getOrPut(date) {
        val day = date.toDateTimeAtStartOfDay()
        val (allDay, timed) = AgendaGridModel.build(sources, day, 1).partition { it.isAllDay }
        AgendaGridDay(day, allDay, timed)
    }
    val currentDayCount by rememberUpdatedState(visibleDayCount)
    // Changes on every frame only while a pinch moves between one and two days.
    val headerProgressState = remember { derivedStateOf { (currentDayCount() - 1f).coerceIn(0f, 1f) } }
    val headerProgress by headerProgressState
    // The same progress read while measuring and drawing, so that the margins and paddings of Day turn into those of
    // the other views with a pinch instead of jumping. Derived, so that pinches beyond two days leave them alone.
    val dayProgress = remember { { headerProgressState.value } }
    val allDayScroll = rememberScrollState()
    LaunchedEffect(anchor, sources) {
        val start = anchor.toLocalDate().minusDays(mode.dayCount * 3)
        val end = anchor.toLocalDate().plusDays(mode.dayCount * 4)
        cache.keys.removeAll { it < start || it > end }
    }
    AgendaDayPager(anchor.toLocalDate().toString(), mode.dayCount, onDateChange,
        modifier, prefetchEnabled, visibleDayCount, swipeEnabled, resizing,
    ) { pages ->
        // Size the all-day area for the dates on screen, including those a drag is revealing. Derived, so that a
        // drag recomposes the header only when its height changes.
        val today = now.toLocalDate()
        val hasPending = pendingSummary != null
        val maxAllDay by remember(pages, cache, today, hasPending) {
            derivedStateOf {
                val showPending = hasPending && today in pages.visibleDates
                pages.visibleDates.maxOfOrNull { date ->
                    dayData(date).allDay.size + if (showPending && date == today) 1 else 0
                } ?: 0
            }
        }
        val headerMode = if (mode == AgendaViewMode.DAY) AgendaViewMode.TWO_DAYS else mode
        val multiHeader = agendaGridHeaderLayout(headerMode, compactHeight, expanded, maxAllDay, scale, gridHeight)
        val dayHeader = agendaGridHeaderLayout(AgendaViewMode.DAY, compactHeight, expanded, maxAllDay, scale, gridHeight)
        val header = multiHeader.copy(
            dateHeaderHeight = multiHeader.dateHeaderHeight * headerProgress,
            rowHeight = lerp(dayHeader.rowHeight, multiHeader.rowHeight, headerProgress),
            shelfHeight = lerp(dayHeader.shelfHeight, multiHeader.shelfHeight, headerProgress),
            headerHeight = lerp(dayHeader.headerHeight, multiHeader.headerHeight, headerProgress),
        )
        Column(Modifier.fillMaxSize()) {
            if (headerProgress > 0.001f) Row(Modifier.fillMaxWidth().height(header.dateHeaderHeight)
                .clipToBounds().graphicsLayer { alpha = headerProgress }.testTag("agenda_grid_dates")
                .then(if (headerProgress < 0.5f) Modifier.clearAndSetSemantics {} else Modifier)) {
                Spacer(Modifier.width(gutter))
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    pages.dates.forEach { date ->
                        key(date) {
                            pages.Day(date) { dayModifier ->
                                Box(dayModifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    val day = dayData(date).day
                                    AgendaDayHeader(day, now, locale, dimensions, compactHeight,
                                        Modifier.fillMaxWidth()) { onDayOpen(day) }
                                }
                            }
                        }
                    }
                }
            }
            if (maxAllDay > 0 || headerProgress < 1f) {
                Row(Modifier.fillMaxWidth().height(header.shelfHeight).testTag("agenda_grid_all_day")) {
                    // Beside the entries: the date of Day, and the arrow that shows them all in their last row.
                    Box(Modifier.width(gutter).fillMaxHeight()) {
                        if (headerProgress < 1f) Box(Modifier.fillMaxWidth().height(dayHeader.dateHeaderHeight)
                            .graphicsLayer { alpha = 1f - headerProgress }
                            .then(if (headerProgress >= 0.5f) Modifier.clearAndSetSemantics {} else Modifier)) {
                            AgendaDayHeader(anchor, now, locale, AgendaViewMode.DAY.gridDimensions, compactHeight,
                                Modifier.fillMaxWidth()) { onDayOpen(anchor) }
                        }
                        if (header.collapsible) AgendaAllDayArrow(expanded, onToggleAllDay, header.rowHeight, Modifier)
                    }
                    Box(Modifier.weight(1f).clipToBounds().verticalScroll(allDayScroll)) {
                        pages.dates.forEach { date ->
                            key(date) {
                                pages.Day(date) { dayModifier ->
                                    AgendaAllDayColumn(dayData(date), dimensions, header, onNoteOpen, dayModifier,
                                        dayProgress, pendingSummary = if (date == today) pendingSummary else null,
                                        onExpand = onToggleAllDay)
                                }
                            }
                        }
                    }
                }
            }
            // A line under the all-day entries, which the hours scroll beneath, beside the hour labels.
            Row(Modifier.weight(1f).then(viewportModifier(header.headerHeight))
                .drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    val labels = gutter.toPx()
                    val (start, end) = if (layoutDirection == LayoutDirection.Ltr) labels to size.width
                        else 0f to size.width - labels
                    drawLine(lineColor, Offset(start, stroke / 2), Offset(end, stroke / 2), stroke)
                }
                .verticalScroll(scroll)) {
                AgendaHourLabels(hourHeight, gutter, dimensions, formatMinute)
                Box(Modifier.weight(1f).clipToBounds()) {
                    pages.dates.forEach { date ->
                        key(date) {
                            pages.Day(date) { dayModifier ->
                                val day = dayData(date)
                                AgendaTimedDay(day.day, day.timed, hourHeight, now, onNoteOpen, dimensions, dayProgress,
                                    narrowLanes, dayModifier, formatMinute)
                            }
                        }
                    }
                }
            }
        }
    }
}
