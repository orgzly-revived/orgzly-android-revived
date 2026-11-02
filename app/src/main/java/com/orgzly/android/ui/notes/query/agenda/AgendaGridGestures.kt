package com.orgzly.android.ui.notes.query.agenda

import android.view.ViewConfiguration as AndroidViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

private enum class AgendaPinchTarget { UNDECIDED, HOURS, DAYS }

@Composable
internal fun Modifier.agendaGridZoom(
    onHourZoom: (Float, Float, Boolean) -> Unit,
    onDayZoom: (Float, Boolean) -> Unit,
    onPinchActiveChanged: (Boolean) -> Unit = {},
): Modifier {
    val hourZoom by rememberUpdatedState(onHourZoom)
    val dayZoom by rememberUpdatedState(onDayZoom)
    val pinchActive by rememberUpdatedState(onPinchActiveChanged)
    val daySlop = with(LocalDensity.current) { 16.dp.toPx() }
    return pointerInput(daySlop) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var hasTwoPointers = false
            var initialSpan: Offset? = null
            var previousSpan: Offset? = null
            var target = AgendaPinchTarget.UNDECIDED
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointers = event.changes.filter { it.pressed }
                    if (pointers.size >= 2) {
                        if (!hasTwoPointers) pinchActive(true)
                        hasTwoPointers = true
                        val span = pointers[0].position - pointers[1].position
                        val start = initialSpan ?: span.also { initialSpan = it }
                        val previous = previousSpan ?: span
                        previousSpan = span
                        val newGesture = target == AgendaPinchTarget.UNDECIDED
                        if (newGesture) {
                            val motion = span - start
                            val verticalMotion = abs(abs(span.y) - abs(start.y))
                            // Require both the fingers and their movement to be clearly horizontal.
                            // Decide once per gesture; diagonal pinches keep the hour zoom.
                            val horizontal = abs(start.x) > abs(start.y) * 2f &&
                                abs(span.x) > abs(span.y) * 2f && start.x * span.x > 0f &&
                                abs(motion.x) > abs(motion.y) * 2.5f
                            if (horizontal) {
                                if (abs(motion.x) > maxOf(daySlop, viewConfiguration.touchSlop)) target = AgendaPinchTarget.DAYS
                            } else if (verticalMotion > viewConfiguration.touchSlop) target = AgendaPinchTarget.HOURS
                        }
                        if (target != AgendaPinchTarget.UNDECIDED) {
                            val isDayZoom = target == AgendaPinchTarget.DAYS
                            val from = if (newGesture) start else previous
                            val minimumSpan = if (isDayZoom) 1f else maxOf(viewConfiguration.touchSlop, 1f)
                            val currentSpan = abs(if (isDayZoom) span.x else span.y)
                            val fromSpan = abs(if (isDayZoom) from.x else from.y)
                            val factor = maxOf(currentSpan, minimumSpan) / maxOf(fromSpan, minimumSpan)
                            if (isDayZoom) dayZoom(factor, newGesture)
                            else hourZoom(factor, event.calculateCentroid(useCurrent = false).y, newGesture)
                        }
                    }
                    // A two-finger gesture must not also scroll the grid or open an appointment.
                    if (hasTwoPointers) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            } finally {
                if (hasTwoPointers) pinchActive(false)
            }
        }
    }
}

@Composable
internal fun Modifier.agendaPeriodSwipe(onSwipe: (Int) -> Unit): Modifier {
    val swipe by rememberUpdatedState(onSwipe)
    val threshold = with(LocalDensity.current) { 50.dp.toPx() }
    return pointerInput(threshold) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var distance = Offset.Zero
            var horizontal = false
            var vertical = false
            var multiplePointers = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) multiplePointers = true
                event.changes.firstOrNull { it.id == first.id }?.let { distance = it.position - first.position }
                if (!multiplePointers && !vertical) {
                    if (!horizontal) {
                        vertical = abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) >= abs(distance.x)
                        horizontal = !vertical && abs(distance.x) > threshold && abs(distance.x) > abs(distance.y)
                    }
                    if (horizontal) event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (!multiplePointers && horizontal && abs(distance.x) > threshold && abs(distance.x) > abs(distance.y)) {
                swipe(if (distance.x < 0) 1 else -1)
            }
        }
    }
}

internal fun Modifier.agendaInactivePage(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

@Composable
internal fun Modifier.agendaPeriodDrag(
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (Float?) -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    val finish by rememberUpdatedState(onFinish)
    val nativeGestures = LocalAgendaGestureLayout.current
    val context = LocalContext.current
    val maximumVelocity = remember(context) { AndroidViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat() }
    return pointerInput(nativeGestures, maximumVelocity) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            start()
            val nativeTracker = nativeGestures?.velocityTracker
            val velocity = nativeTracker ?: VelocityTracker().apply { addPosition(first.uptimeMillis, first.position) }
            var distance = Offset.Zero
            var horizontal = false
            var vertical = false
            var multiplePointers = false
            var dragSlop = 0f
            var completed = false
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1 && !multiplePointers) {
                        multiplePointers = true
                        cancel()
                    }
                    val primary = event.changes.firstOrNull { it.id == first.id }
                    primary?.let {
                        distance = it.position - first.position
                        if (it.pressed && nativeTracker == null) {
                            it.historical.forEach { sample -> velocity.addPosition(sample.uptimeMillis, sample.position) }
                            velocity.addPosition(it.uptimeMillis, it.position)
                        }
                    }
                    if (!multiplePointers && !vertical) {
                        if (!horizontal) {
                            vertical = abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) >= abs(distance.x)
                            horizontal = !vertical && abs(distance.x) > viewConfiguration.touchSlop && abs(distance.x) > abs(distance.y)
                            if (horizontal) dragSlop = distance.x.sign * viewConfiguration.touchSlop
                            if (vertical) cancel()
                        }
                        if (horizontal) {
                            if (primary?.pressed == true) drag(distance.x - dragSlop)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
                if (!multiplePointers && !vertical) {
                    release(if (horizontal) nativeGestures?.releaseVelocity
                        ?: velocity.calculateVelocity().x.coerceIn(-maximumVelocity, maximumVelocity) else null)
                }
                completed = true
            } finally {
                if (!completed) cancel()
                finish()
            }
        }
    }
}
