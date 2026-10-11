package com.orgzly.android.ui.note

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.orgzly.R

/**
 * Window anchored to one of the note's controls, looking like the app's other menus,
 * kept 8dp from the edges of the screen and scrolling when it's too long.
 */
open class NotePillPopup(
    protected val context: Context,
    @LayoutRes layout: Int,
    private val fixedWidth: Int? = null,
    private val alignToWindowStart: Boolean = false
) {
    protected val content: View = LayoutInflater.from(context).inflate(layout, null)

    protected val popup = PopupWindow(
        content, fixedWidth ?: ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
    ).apply {
        setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.note_popup_background))
        // As the app's other menus (Material3 menu elevation)
        elevation = context.dp(3).toFloat()
    }

    @SuppressLint("RtlHardcoded")
    open fun show(anchor: View) {
        val widthSpec = if (fixedWidth != null) {
            View.MeasureSpec.makeMeasureSpec(fixedWidth, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }
        content.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))

        val yOffset = verticalOffset(anchor)
        val available = popup.getMaxAvailableHeight(anchor, yOffset)
        if (content.measuredHeight > available) {
            popup.height = available
        }

        // Aligned with the pill's start (its right edge right to left), with a margin from the
        // screen's edges. Computed here for both directions, so the gravity is absolute: aligned
        // to the right, a wrap-content window would start at the pill's right edge instead.
        val width = content.measuredWidth
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val start = if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            location[0] + anchor.width - width
        } else {
            location[0]
        }
        val margin = context.dp(8)
        val x = if (alignToWindowStart) {
            val frame = Rect()
            anchor.getWindowVisibleDisplayFrame(frame)
            if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                frame.right - margin - width
            } else {
                frame.left + margin
            }
        } else {
            start.coerceAtMost(context.resources.displayMetrics.widthPixels - margin - width)
                .coerceAtLeast(margin)
        }

        popup.showAsDropDown(anchor, x - location[0], yOffset, Gravity.TOP or Gravity.LEFT)
    }

    protected open fun verticalOffset(anchor: View): Int = 0

    val isShowing: Boolean get() = popup.isShowing

    fun dismiss() {
        popup.dismiss()
    }
}

/** Density-independent pixels to pixels, for the note screen's views. */
internal fun Context.dp(value: Int): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
