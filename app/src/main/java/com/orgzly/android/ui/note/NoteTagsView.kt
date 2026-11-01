package com.orgzly.android.ui.note

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.google.android.material.textview.MaterialTextView

/** Truncates the last visible tag and counts only the tags hidden after it. */
class NoteTagsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : MaterialTextView(context, attrs, defStyleAttr) {
    var tags: List<String> = emptyList()
        set(value) {
            field = value.toList()
            contentDescription = value.joinToString(" ").ifEmpty { null }
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            Float.POSITIVE_INFINITY
        } else {
            (MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight)
                .coerceAtLeast(0).toFloat()
        }
        val full = tags.joinToString(" ")
        val summary = if (paint.measureText(full) <= available) {
            full
        } else {
            val rtl = TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR.isRtl(full, 0, full.length)
            val bidi = BidiFormatter.getInstance(rtl)
            var result = ""
            for (visible in tags.size downTo 1) {
                val prefix = tags.take(visible).joinToString(" ")
                val hidden = tags.size - visible
                val suffix = if (hidden == 0) "" else {
                    " " + bidi.unicodeWrap("+$hidden", TextDirectionHeuristicsCompat.LTR)
                }
                var cut = prefix.length
                val truncated = TextUtils.ellipsize(
                    prefix, paint, (available - paint.measureText(suffix)).coerceAtLeast(0f),
                    TextUtils.TruncateAt.END, false
                ) { start, end -> if (start != end) cut = start }
                val lastTagStart = prefix.length - tags[visible - 1].length
                if (cut > lastTagStart || visible == 1) {
                    result = "$truncated$suffix"
                    break
                }
            }
            result
        }
        if (text.toString() != summary) text = summary
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
