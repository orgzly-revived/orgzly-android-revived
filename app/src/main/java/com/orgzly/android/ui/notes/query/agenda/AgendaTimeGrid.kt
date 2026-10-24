package com.orgzly.android.ui.notes.query.agenda

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import kotlinx.coroutines.delay
import org.joda.time.DateTime
import org.joda.time.LocalDate
import kotlin.math.roundToInt

@Composable
internal fun AgendaTimeGrid(
    state: QueryState,
    mode: AgendaViewMode,
    onModeChange: (AgendaViewMode) -> Unit,
    onNoteOpen: (Long) -> Unit,
    initialDate: LocalDate? = null,
    onSimpleOpen: (LocalDate) -> Unit = { onModeChange(AgendaViewMode.SIMPLE) },
    calendarState: AgendaCalendarState? = null,
    groupScheduledWithToday: Boolean = true,
    onNoteAction: (Long, Int) -> Unit = { _, _ -> },
) {
    val colors = agendaGridColors()
    val calendar = calendarState ?: rememberSaveable(saver = AgendaCalendarState.Saver) {
        AgendaCalendarState(initialDate ?: LocalDate.now())
    }
    var selectedDate by calendar.date
    var now by remember { mutableStateOf(DateTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, calendar) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = DateTime.now()
                calendar.today = now.toLocalDate()
                delay(60_000)
            }
        }
    }
    val locale = agendaLocale()
    val firstDayOfWeek = remember(locale) { agendaFirstDayOfWeek(locale) }
    // Read on every update rather than kept, so that the grid follows the settings even without being created again.
    // Preferences keep them ready, and the sources are built again only when they do change.
    val doneStates = AppPreferences.doneKeywordsSet(LocalContext.current)
    val sources = remember(state.notes, doneStates) { AgendaGridSources(state.notes, doneStates) }
    val anchor = LocalDate.parse(selectedDate).toDateTimeAtStartOfDay()
    val isMonth = mode == AgendaViewMode.MONTH
    val currentMode by rememberUpdatedState(mode)
    val dayZoomScope = rememberCoroutineScope()
    val dayZoom = remember { AgendaDayZoom(dayZoomScope) }
    // Read while measuring and drawing, so that a pinch does not recompose the grid on every frame.
    val visibleDayCount = remember(dayZoom) { { (dayZoom.level(currentMode) + 1f).coerceIn(1f, 7f) } }
    val slidingDays = !isMonth && mode != AgendaViewMode.DAY
    val headerWeek = AgendaGridModel.weekStart(anchor, firstDayOfWeek)
    var showAllDay by rememberSaveable(headerWeek.millis) { mutableStateOf(false) }
    val monthScroll = rememberScrollState()
    var monthReturnDate by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(mode) { if (mode != AgendaViewMode.DAY) monthReturnDate = null }
    BackHandler(enabled = mode == AgendaViewMode.DAY && monthReturnDate != null) {
        selectedDate = requireNotNull(monthReturnDate)
        monthReturnDate = null
        onModeChange(AgendaViewMode.MONTH)
    }
    var hourZoom by rememberSaveable { mutableStateOf(1f) }
    // Updated when a pinch ends rather than on each of its frames, so that the grid is not recomposed meanwhile.
    var zoomPercent by remember { mutableIntStateOf((hourZoom * 100).roundToInt()) }
    val density = LocalDensity.current
    val baseHourHeight = (80 * maxOf(1f, density.fontScale)).dp
    // Read while measuring and drawing, so that a pinch of the hours lays the days out again without recomposing them.
    val hourHeight = remember(baseHourHeight) { { baseHourHeight * hourZoom } }
    val verticalScroll = rememberSaveable(saver = ScrollState.Saver) {
        ScrollState(with(density) { (hourHeight() * 8).toPx().roundToInt() })
    }
    var zoomScrollTarget by remember { mutableStateOf<Float?>(null) }
    // Preserve fractional pixels between pinch updates instead of accumulating rounded scroll offsets.
    var gestureScrollPosition by remember { mutableStateOf(0f) }
    var hourViewportHeight by remember { mutableStateOf(0) }
    var gridTop by remember { mutableStateOf(0f) }
    var hourViewportTop by remember { mutableStateOf(0f) }
    var previousHeaderHeight by remember { mutableStateOf<Int?>(null) }
    var pinching by remember { mutableStateOf(false) }
    // Between two and seven days the view, and with it the toolbar menu, changes once when a pinch ends rather than
    // at every day count the fingers pass: the columns already follow the pinch through the zoom level. Day has a
    // pager of its own, so entering or leaving it cannot wait.
    var pinchMode by remember { mutableStateOf<AgendaViewMode?>(null) }
    fun zoomDays(factor: Float, newGesture: Boolean) {
        if (currentMode == AgendaViewMode.MONTH) return
        val nextMode = dayZoom.update(factor, newGesture, currentMode)
        if (nextMode != currentMode && AgendaViewMode.DAY in listOf(nextMode, currentMode)) onModeChange(nextMode)
        pinchMode = nextMode
    }
    fun zoomHours(factor: Float, focusY: Float, newGesture: Boolean = true) {
        if (newGesture) gestureScrollPosition = verticalScroll.value.toFloat()
        val previousZoom = hourZoom
        // From 32 to 160 dp an hour.
        val nextZoom = (previousZoom * factor).coerceIn(0.4f, 2f)
        if (nextZoom == previousZoom) return
        val scroll = zoomScrollTarget ?: gestureScrollPosition
        zoomScrollTarget = ((scroll + focusY) * nextZoom / previousZoom - focusY).coerceAtLeast(0f)
        gestureScrollPosition = requireNotNull(zoomScrollTarget)
        hourZoom = nextZoom
        if (!pinching) zoomPercent = (nextZoom * 100).roundToInt()
    }
    val zoomInLabel = stringResource(R.string.agenda_grid_zoom_in)
    val zoomOutLabel = stringResource(R.string.agenda_grid_zoom_out)
    val zoomDescription = stringResource(R.string.agenda_grid_zoom, zoomPercent)
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val formatMinute = rememberAgendaTimeFormatter()
    val today = now.withTimeAtStartOfDay()
    val pending = remember(sources, today, groupScheduledWithToday) {
        AgendaPendingModel.build(sources, today, groupScheduledWithToday)
    }

    fun hourViewportModifier(headerHeight: Dp, pageHours: ScrollState, isCurrentPage: Boolean,
                             pageVisible: () -> Boolean): Modifier = Modifier.testTag("agenda_grid_hours")
        .onSizeChanged { if (isCurrentPage) hourViewportHeight = it.height }
        .onGloballyPositioned { if (isCurrentPage) hourViewportTop = it.positionInRoot().y }
        .semantics {
            stateDescription = zoomDescription
            customActions = listOf(
                CustomAccessibilityAction(zoomInLabel) { zoomHours(1.25f, hourViewportHeight / 2f); true },
                CustomAccessibilityAction(zoomOutLabel) { zoomHours(0.8f, hourViewportHeight / 2f); true },
            )
        }
        .layout { measurable, constraints ->
            // Read here rather than while composing, so that a pinch of the hours does not recompose the grid.
            val scrollTarget = zoomScrollTarget
            val headerPixels = headerHeight.roundToPx()
            val headerDelta = if (pageVisible()) previousHeaderHeight?.let { headerPixels - it } ?: 0 else 0
            // Capture the scroll before measuring a shorter viewport can clamp its range.
            val target = if (isCurrentPage) scrollTarget ?: (pageHours.value + headerDelta).toFloat()
                else if (pageVisible()) (verticalScroll.value + headerDelta).toFloat()
                else pageHours.value.toFloat()
            val placeable = measurable.measure(constraints)
            if (isCurrentPage && (scrollTarget != null || headerDelta != 0) ||
                !isCurrentPage && pageHours.value != target.roundToInt()) {
                pageHours.dispatchRawDelta(target - pageHours.value)
                if (isCurrentPage) {
                    gestureScrollPosition = target.coerceIn(0f, pageHours.maxValue.toFloat())
                    zoomScrollTarget = null
                }
            }
            if (isCurrentPage) previousHeaderHeight = headerPixels
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 350.dp
        val gridHeight = maxHeight
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.text) {
            Column(Modifier.fillMaxSize().testTag("agenda_time_grid")
                .onGloballyPositioned { gridTop = it.positionInRoot().y }
                .agendaGridZoom(
                    onHourZoom = { factor, focus, start ->
                        if (!isMonth) zoomHours(factor, (focus - hourViewportTop + gridTop).coerceIn(0f, hourViewportHeight.toFloat()), start)
                    },
                    onDayZoom = ::zoomDays,
                    onPinchActiveChanged = {
                        pinching = it
                        if (!it) {
                            zoomPercent = (hourZoom * 100).roundToInt()
                            pinchMode?.let { mode -> if (mode != currentMode) onModeChange(mode) }
                            pinchMode = null
                            dayZoom.finish()
                        }
                    },
                )) {
                if (calendarState == null) AgendaMonthSelector(anchor.toLocalDate(), now.toLocalDate(), locale,
                    onDateSelected = { dayZoom.cancel(); selectedDate = it.toString() })
                if (state.loading == QueryViewModel.ViewState.LOADING) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.calendarAccent)
                    }
                } else Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    if (slidingDays) AgendaSlidingDays(
                        sources, anchor, now, locale, mode, hourHeight, verticalScroll, is24Hour, compactHeight, gridHeight,
                        showAllDay, { !pinching && !dayZoom.active && !verticalScroll.isScrollInProgress },
                        onDateChange = { selectedDate = it },
                        onDayOpen = { day ->
                            dayZoom.cancel()
                            selectedDate = day.toLocalDate().toString()
                            onModeChange(AgendaViewMode.DAY)
                        },
                        onNoteOpen = onNoteOpen,
                        onToggleAllDay = { showAllDay = !showAllDay },
                        viewportModifier = { hourViewportModifier(it, verticalScroll, true) { true } },
                        formatMinute = formatMinute,
                        modifier = Modifier.fillMaxSize(),
                        visibleDayCount = visibleDayCount,
                        // A pinch of the hours resizes the days too: those off screen follow once it ends.
                        resizing = { pinching || dayZoom.active },
                        pendingSummary = if (pending.isNotEmpty()) { modifier ->
                            AgendaPendingSummary(pending, onNoteOpen, modifier,
                                mode = mode, onNoteAction = onNoteAction)
                        } else null,
                    ) else key(mode) {
                        AgendaPeriodPager(
                            date = selectedDate,
                            adjacentDate = { page, direction ->
                                val date = LocalDate.parse(page)
                                if (isMonth) date.withDayOfMonth(1).plusMonths(direction).toString()
                                else date.plusDays(direction).toString()
                            },
                            onDateChange = { selectedDate = it },
                            modifier = Modifier.fillMaxSize(),
                            prefetchEnabled = {
                                !pinching && !dayZoom.active &&
                                    (if (isMonth) !monthScroll.isScrollInProgress else !verticalScroll.isScrollInProgress)
                            },
                        ) { pageDate, pageVisible ->
                            val pageAnchor = LocalDate.parse(pageDate).toDateTimeAtStartOfDay()
                            val pageShowPending = pending.isNotEmpty() && agendaPendingVisible(pageAnchor.toLocalDate(),
                                today.toLocalDate(), mode)
                            val isCurrentPage = pageDate == selectedDate
                            // Callbacks read this when invoked, so they stay unchanged when a swipe makes the page current
                            // and the days and appointments of the grid are not recomposed.
                            val currentPage by rememberUpdatedState(isCurrentPage)

                            Box(Modifier.fillMaxSize().testTag("agenda_grid_page_$pageDate")) {
                                Box(Modifier.fillMaxSize().then(if (isCurrentPage) Modifier else
                                    Modifier.clearAndSetSemantics {}.agendaInactivePage())) {
                                    if (isMonth) {
                                        val pageMonth = if (isCurrentPage) monthScroll else remember { ScrollState(monthScroll.value) }
                                        val entries = remember(sources, pageAnchor, firstDayOfWeek) {
                                            AgendaMonthModel.build(sources, pageAnchor, firstDayOfWeek)
                                        }
                                        val days = remember(pageAnchor, firstDayOfWeek) { AgendaMonthModel.days(pageAnchor, firstDayOfWeek) }
                                        AgendaMonthGrid(pageAnchor, days, entries, now, locale, pageMonth,
                                            onDayOpen = { day ->
                                                if (currentPage) {
                                                    dayZoom.cancel()
                                                    monthReturnDate = selectedDate
                                                    selectedDate = day.toLocalDate().toString()
                                                    onModeChange(AgendaViewMode.DAY)
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize().layout { measurable, constraints ->
                                                val target = if (pageVisible() && !isCurrentPage) monthScroll.value else null
                                                val placeable = measurable.measure(constraints)
                                                if (target != null && pageMonth.value != target) {
                                                    pageMonth.dispatchRawDelta((target - pageMonth.value).toFloat())
                                                }
                                                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                                            }, pending = if (pageShowPending) pending else emptyList())
                                    } else {
                                        var pageShowAllDay by remember { mutableStateOf(showAllDay) }
                                        if (isCurrentPage) pageShowAllDay = showAllDay
                                        val pageHours = if (isCurrentPage) verticalScroll else remember { ScrollState(verticalScroll.value) }
                                        val openNote: (Long) -> Unit = { if (currentPage) onNoteOpen(it) }
                                        AgendaSlidingDays(sources, pageAnchor, now, locale, mode, hourHeight,
                                            pageHours, is24Hour, compactHeight, gridHeight, pageShowAllDay, { false },
                                            onDateChange = { selectedDate = it },
                                            onDayOpen = { day ->
                                                if (currentPage) {
                                                    dayZoom.cancel()
                                                    onSimpleOpen(day.toLocalDate())
                                                }
                                            },
                                            onNoteOpen = openNote,
                                            onToggleAllDay = { showAllDay = !showAllDay },
                                            viewportModifier = { hourViewportModifier(it, pageHours, isCurrentPage, pageVisible) },
                                            formatMinute = formatMinute,
                                            modifier = Modifier.fillMaxSize(),
                                            visibleDayCount = visibleDayCount,
                                            swipeEnabled = false,
                                            resizing = { dayZoom.active },
                                            pendingSummary = if (pageShowPending) { modifier ->
                                                AgendaPendingSummary(pending, openNote, modifier, mode = mode,
                                                    onNoteAction = { noteId, actionId -> if (currentPage) onNoteAction(noteId, actionId) })
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
