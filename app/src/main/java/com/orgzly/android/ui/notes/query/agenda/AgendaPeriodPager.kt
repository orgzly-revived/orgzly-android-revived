package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.joda.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
internal fun AgendaPeriodPager(
    date: String,
    adjacentDate: (String, Int) -> String,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefetchEnabled: () -> Boolean = { true },
    content: @Composable (String, () -> Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val adjacent by rememberUpdatedState(adjacentDate)
    val selectDate by rememberUpdatedState(onDateChange)
    val minimumFlingVelocity = with(LocalDensity.current) { 400.dp.toPx() }
    val decay = rememberSplineBasedDecay<Float>()
    var pageDate by remember { mutableStateOf(date) }
    var offset by remember { mutableFloatStateOf(0f) }
    var outgoingDate by remember { mutableStateOf<String?>(null) }
    var outgoingSide by remember { mutableStateOf(0) }
    var animation by remember { mutableStateOf<Job?>(null) }
    var gestureOrigin by remember { mutableFloatStateOf(0f) }
    var gestureActive by remember { mutableStateOf(false) }
    var preparedDates by remember { mutableStateOf(listOf(date)) }
    val side by remember { derivedStateOf { offset.sign.toInt() } }
    val atRest by remember { derivedStateOf { offset == 0f } }
    val neighbor by remember {
        derivedStateOf {
            if (side == 0) null else outgoingDate?.takeIf { side == outgoingSide } ?: adjacent(pageDate, -side)
        }
    }

    val prefetchAllowed by rememberUpdatedState(prefetchEnabled)
    // Read through a derived state, so that the start and end of a gesture do not recompose the grid.
    val idle by remember { derivedStateOf { atRest && !gestureActive && prefetchAllowed() } }

    LaunchedEffect(pageDate, idle) {
        if (!idle) return@LaunchedEffect
        val wanted = listOf(pageDate, adjacent(pageDate, -1), adjacent(pageDate, 1))
        preparedDates = (listOf(pageDate) + preparedDates.filter { it in wanted }).distinct()
        // Prepare one neighboring page per frame, after the selected page has been displayed.
        wanted.drop(1).forEach { page ->
            if (page !in preparedDates) {
                withFrameNanos { }
                preparedDates = preparedDates + page
            }
        }
    }

    suspend fun snapToRest() {
        if (offset != 0f) {
            animate(offset, 0f, animationSpec = tween(300, easing = AgendaSnapEasing)) { value, _ ->
                offset = value
            }
        }
        outgoingDate = null
    }

    fun settle() {
        animation?.cancel()
        animation = scope.launch { snapToRest() }
    }

    BoxWithConstraints(modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        fun selectNeighbor(direction: Int) {
            val next = outgoingDate?.takeIf { direction == outgoingSide }
                ?: adjacent(pageDate, -direction)
            outgoingDate = pageDate
            pageDate = next
            offset -= direction * width
            outgoingSide = offset.sign.toInt()
            selectDate(next)
        }

        fun release(velocity: Float) {
            animation?.cancel()
            animation = scope.launch {
                if (width <= 0f) return@launch
                if (abs(velocity) >= minimumFlingVelocity) {
                    if (offset != 0f && velocity.sign == offset.sign) selectNeighbor(offset.sign.toInt())
                    val origin = offset
                    val predicted = decay.calculateTargetValue(origin, velocity)
                    if (origin != 0f && predicted * origin <= 0f) {
                        AnimationState(initialValue = origin, initialVelocity = velocity).animateDecay(decay) {
                            val reachedCenter = value * origin <= 0f
                            offset = if (reachedCenter) 0f else value
                            if (reachedCenter) cancelAnimation()
                        }
                    }
                } else {
                    if (abs(velocity) > 1f) {
                        AnimationState(initialValue = offset, initialVelocity = velocity).animateDecay(decay) {
                            offset = value.coerceIn(-width, width)
                            if (abs(value) >= width) cancelAnimation()
                        }
                    }
                    if (abs(offset) > width / 2) selectNeighbor(offset.sign.toInt())
                }
                snapToRest()
            }
        }

        LaunchedEffect(date, width) {
            if (date != pageDate && width > 0) {
                animation?.cancel()
                outgoingDate = pageDate
                outgoingSide = if (LocalDate.parse(date).isAfter(LocalDate.parse(pageDate))) 1 else -1
                pageDate = date
                offset = outgoingSide * width
                settle()
            }
        }
        Box(Modifier.fillMaxSize().agendaPeriodDrag(
            onStart = {
                gestureActive = true
                animation?.cancel()
                gestureOrigin = offset
            },
            onDrag = { distance ->
                offset = (gestureOrigin + distance).coerceIn(-width, width)
                if (offset.sign.toInt() != outgoingSide) outgoingDate = null
            },
            onRelease = { velocity -> if (velocity == null) settle() else release(velocity) },
            onCancel = {
                animation?.cancel()
                offset = 0f
                outgoingDate = null
            },
            onFinish = { gestureActive = false },
        )) {
            (listOfNotNull(pageDate, neighbor) + preparedDates).distinct().forEach { page ->
                key(page) {
                    val visible = remember { { page == pageDate || page == neighbor } }
                    Box(Modifier.fillMaxSize()
                        .then(if (visible()) Modifier else Modifier.clearAndSetSemantics {})
                        .graphicsLayer {
                            val position = when (page) {
                                pageDate -> offset
                                neighbor -> offset - side * width
                                else -> width * 2
                            }
                            translationX = position.roundToInt().toFloat()
                        }) {
                        content(page, visible)
                    }
                }
            }
        }
    }
}
