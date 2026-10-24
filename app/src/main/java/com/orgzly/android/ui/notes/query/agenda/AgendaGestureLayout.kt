package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker

internal val LocalAgendaGestureLayout = staticCompositionLocalOf<AgendaGestureLayout?> { null }

/** Observes the original touch stream, including stationary moves omitted by Compose. */
class AgendaGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    internal var velocityTracker: VelocityTracker? = null
        private set
    private val maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    internal var releaseVelocity: Float? = null
        private set

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearTracker()
                releaseVelocity = null
                velocityTracker = VelocityTracker().apply { addPosition(event.eventTime, Offset(event.x, event.y)) }
            }
            MotionEvent.ACTION_MOVE -> velocityTracker?.let {
                repeat(event.historySize) { sample ->
                    it.addPosition(event.getHistoricalEventTime(sample),
                        Offset(event.getHistoricalX(sample), event.getHistoricalY(sample)))
                }
                it.addPosition(event.eventTime, Offset(event.x, event.y))
            }
            MotionEvent.ACTION_UP -> velocityTracker?.let {
                releaseVelocity = it.calculateVelocity().x.coerceIn(-maximumVelocity, maximumVelocity)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                releaseVelocity = null
                clearTracker()
            }
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) clearTracker()
        }
    }

    private fun clearTracker() {
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        clearTracker()
        releaseVelocity = null
        super.onDetachedFromWindow()
    }
}
