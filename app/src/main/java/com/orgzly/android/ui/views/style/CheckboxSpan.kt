package com.orgzly.android.ui.views.style

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import com.orgzly.android.ui.views.richtext.ActionableRichTextView

/**
 * @param content is `[ ]`, `[-]`, or `[X]`
 * @param rawStart is the position of `[`.
 */
class CheckboxSpan(val content: CharSequence, val rawStart: Int, val rawEnd: Int) : ClickableSpan() {

    enum class State {
        UNCHECKED, PARTIAL, CHECKED
    }

    override fun onClick(view: View) {
        Log.d("CheckboxSpan", "Checkbox clicked on $view")
        if (view is ActionableRichTextView) {
            view.toggleCheckbox(this)
        }
    }

    override fun updateDrawState(tp: TextPaint) {
    }

    fun getState(): State {
        when (content[1]) {
            '-' -> return State.PARTIAL
            'X' -> return State.CHECKED
            else -> return State.UNCHECKED
        }
    }
}
