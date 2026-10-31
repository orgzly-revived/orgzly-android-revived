package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimation
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.joda.time.Days
import org.joda.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private const val DAY_SNAP_MILLIS = 300

private val hiddenDay = Modifier.clearAndSetSemantics {}

/** Starts and ends gently, for the snap of both agenda pagers. */
internal val AgendaSnapEasing = Easing { ((1 - cos(PI * it)) / 2).toFloat() }

private data class AgendaPrefetchStep(val firstDate: LocalDate, val dayCount: Int, val idle: Boolean, val resizing: Boolean)

/**
 * Dates are read through states, so that a day entering or leaving the screen recomposes only the composition that
 * reads it: [Day] for the column itself, or whoever reads [visibleDates].
 */
internal class AgendaDayPagerScope(
    private val datesState: State<List<LocalDate>>,
    private val visibleDatesState: State<List<LocalDate>>,
    internal val column: (LocalDate) -> Modifier,
) {
    val dates: List<LocalDate> get() = datesState.value

    /** Dates at least partly on screen, including those a drag or a pinch is revealing. */
    val visibleDates: List<LocalDate> get() = visibleDatesState.value
}

/** One day of the pager, recomposed only when its own visibility changes. */
@Composable
internal fun AgendaDayPagerScope.Day(day: LocalDate, content: @Composable (Modifier) -> Unit) {
    val visible by remember(this, day) { derivedStateOf { day in visibleDates } }
    val column = remember(this, day) { column(day) }
    content(column.then(if (visible) Modifier else hiddenDay))
}

@Composable
internal fun AgendaDayPager(
    date: String,
    dayCount: Int,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefetchEnabled: () -> Boolean = { true },
    visibleDayCount: () -> Float = { dayCount.toFloat() },
    swipeEnabled: Boolean = true,
    resizing: () -> Boolean = { false },
    content: @Composable (AgendaDayPagerScope) -> Unit,
) {
    val minimumFlingVelocity = with(LocalDensity.current) { 400.dp.toPx() }
    val scope = rememberCoroutineScope()
    val selectDate by rememberUpdatedState(onDateChange)
    val decay = rememberSplineBasedDecay<Float>()
    val firstDateState = remember { mutableStateOf(LocalDate.parse(date)) }
    var firstDate by firstDateState
    val offsetState = remember { mutableFloatStateOf(0f) }
    var offset by offsetState
    val atRest by remember { derivedStateOf { offset == 0f } }
    var gestureOrigin by remember { mutableFloatStateOf(0f) }
    var gestureActive by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf<Job?>(null) }
    var preparedDates by remember { mutableStateOf((0 until dayCount).map { firstDate.plusDays(it) }) }
    // Prepared days off screen are laid out only after being released here, so that neither a pinch nor its end
    // measures all of them in one frame.
    val measuredDatesState = remember { mutableStateOf(emptySet<LocalDate>()) }
    var measuredDates by measuredDatesState
    val prefetchAllowed by rememberUpdatedState(prefetchEnabled)
    val resizingNow by rememberUpdatedState(resizing)
    val currentDays by rememberUpdatedState(dayCount)
    val resizingState = remember { derivedStateOf { resizingNow() } }

    // Gesture flags are observed here rather than read during composition, so that the start and end of a gesture
    // recompose neither this function nor the days.
    LaunchedEffect(Unit) {
        snapshotFlow {
            AgendaPrefetchStep(firstDate, currentDays, atRest && !gestureActive && prefetchAllowed(), resizingState.value)
        }.collectLatest { (first, days, idle, resizing) ->
            if (resizing) {
                // The prepared days stay composed, and are laid out again for the final size once the pinch ends.
                measuredDates = emptySet()
                return@collectLatest
            }
            if (!idle) return@collectLatest
            val visible = (0 until days).map { first.plusDays(it) }
            val wanted = visible +
                (1..days * 2).flatMap { listOf(first.minusDays(it), first.plusDays(days - 1 + it)) }
            preparedDates = (preparedDates + visible).distinct()
            measuredDates = measuredDates + visible
            // One day per frame: prepare a missing day or lay out one measured for another width, then release the
            // days no longer wanted.
            wanted.forEach { day ->
                if (day !in measuredDates) {
                    withFrameNanos { }
                    if (day !in preparedDates) preparedDates = preparedDates + day
                    measuredDates = measuredDates + day
                }
            }
            preparedDates.filter { it !in wanted }.forEach { day ->
                withFrameNanos { }
                preparedDates = preparedDates - day
                measuredDates = measuredDates - day
            }
        }
    }
    LaunchedEffect(date, dayCount) {
        if (date != firstDate.toString()) {
            animation?.cancel()
            offset = 0f
            firstDate = LocalDate.parse(date)
        }
    }

    val currentDayCount by rememberUpdatedState(visibleDayCount)
    // The width beside the hour labels comes from the constraints of the days while they are measured, so that the
    // pager needs no BoxWithConstraints, which would compose all its days again whenever it is measured.
    val availableWidthState = remember { mutableFloatStateOf(0f) }
    val dayPixelsState = remember { derivedStateOf { availableWidthState.floatValue / currentDayCount() } }
    val dayPixels by dayPixelsState
    fun finish(targetDays: Int) {
        firstDate = firstDate.plusDays(targetDays)
        offset = 0f
        if (targetDays != 0) selectDate(firstDate.toString())
    }
    fun release(velocity: Float) {
        animation?.cancel()
        animation = scope.launch {
            if (dayPixels <= 0f) return@launch
            val targetDays: Int
            if (abs(velocity) >= minimumFlingVelocity) {
                // Continue an interrupted fling from the first column still visible.
                val firstDay = floor(-gestureOrigin / dayPixels).toInt()
                val firstOffset = -firstDay * dayPixels
                targetDays = if ((offset - firstOffset) * velocity > 0f)
                    firstDay - velocity.sign.toInt() * dayCount else firstDay
                val target = -targetDays * dayPixels
                val predicted = DecayAnimation(decay, Float.VectorConverter, offset, velocity)
                    .getValueFromNanos(DAY_SNAP_MILLIS * 1_000_000L)
                if ((target - offset) * velocity > 0f && (predicted - target) * velocity >= 0f) {
                    val direction = (target - offset).sign
                    AnimationState(initialValue = offset, initialVelocity = velocity).animateDecay(decay) {
                        val reached = (value - target) * direction >= 0f
                        offset = if (reached) target else value
                        if (reached) cancelAnimation()
                    }
                }
            } else {
                // Let momentum finish before choosing the nearest day.
                val limit = dayPixels * dayCount
                if (abs(velocity) > 1f) AnimationState(initialValue = offset, initialVelocity = velocity).animateDecay(decay) {
                    offset = value.coerceIn(gestureOrigin - limit, gestureOrigin + limit)
                    if (value <= gestureOrigin - limit || value >= gestureOrigin + limit) cancelAnimation()
                }
                targetDays = (-offset / dayPixels).roundToInt()
            }
            val target = -targetDays * dayPixels
            if (offset != target) animate(offset, target, animationSpec = tween(DAY_SNAP_MILLIS, easing = AgendaSnapEasing)) { value, _ ->
                offset = value
            }
            finish(targetDays)
        }
    }
    val visibleRangeState = remember {
        derivedStateOf {
            val count = currentDayCount()
            if (dayPixels > 0f) {
                floor(-offset / dayPixels).toInt()..ceil(-offset / dayPixels + count - 0.001f).toInt() - 1
            } else 0 until ceil(count).toInt()
        }
    }
    val pages = remember(swipeEnabled) {
        val visibleDates = derivedStateOf {
            visibleRangeState.value.map { firstDateState.value.plusDays(it) }
        }
        val dates = derivedStateOf {
            val range = visibleRangeState.value
            if (swipeEnabled) {
                val liveDates = (range.first - 1..range.last + 1).map { firstDateState.value.plusDays(it) }
                (preparedDates + liveDates).distinct()
            } else visibleDates.value
        }
        // The first date, width, visibility and pinch are read while measuring and drawing, so that neither a pinch
        // nor a swipe recomposes the days already prepared.
        AgendaDayPagerScope(dates, visibleDates) { day ->
            Modifier.layout { measurable, constraints ->
                val available = constraints.maxWidth.toFloat()
                if (availableWidthState.floatValue != available) availableWidthState.floatValue = available
                val index = Days.daysBetween(firstDateState.value, day).days
                // Days off screen stay composed but out of the layout during a pinch and until released after it.
                if (index !in visibleRangeState.value && (resizingState.value || day !in measuredDatesState.value)) {
                    return@layout layout(0, 0) {}
                }
                val width = constraints.constrainWidth(dayPixelsState.value.roundToInt())
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(width, placeable.height) { placeable.place(0, 0) }
            }.graphicsLayer {
                val index = Days.daysBetween(firstDateState.value, day).days
                translationX = (index * dayPixelsState.value + offsetState.value).roundToInt().toFloat()
            }
        }
    }
    Box(modifier.clipToBounds().then(if (swipeEnabled) Modifier.testTag("agenda_grid_page_$date") else Modifier)
        .then(if (swipeEnabled) Modifier.agendaPeriodDrag(
            onStart = {
                gestureActive = true
                animation?.cancel()
                gestureOrigin = offset
            },
            onDrag = { distance -> offset = gestureOrigin + distance },
            onRelease = { release(it ?: 0f) },
            onCancel = {
                animation?.cancel()
                offset = 0f
            },
            onFinish = { gestureActive = false },
        ) else Modifier)) {
        content(pages)
    }
}
