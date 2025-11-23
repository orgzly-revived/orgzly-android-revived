package com.orgzly.android.ui.views.richtext

import android.content.Context
import android.graphics.Rect
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ancestors
import androidx.core.widget.NestedScrollView
import com.orgzly.BuildConfig
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.util.LogUtils

class RichTextEdit : AppCompatEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val userEditingTextWatcher: TextWatcher = RichTextEditWatcher()

    fun activate(charOffset: Int) {
        visibility = View.VISIBLE

        // Position the cursor and open the keyboard
        if (charOffset in 0..(text?.length ?: 0)) {
            performClick()
            setSelection(charOffset)

            KeyboardUtils.openSoftKeyboard(this) {
                scrollForBetterCursorPosition(charOffset)
            }
        }

        addTextChangedListener(userEditingTextWatcher)
    }

    // TODO: Handle closed drawers (and such)
    private fun scrollForBetterCursorPosition(charOffset: Int) {
        val scrollView = ancestors.firstOrNull { view -> view is NestedScrollView } as? NestedScrollView

        if (scrollView != null) {
            post {
                val richText = parent as RichText

                val line = layout.getLineForOffset(charOffset)
                val baseline = layout.getLineBaseline(line)
                val ascent = layout.getLineAscent(line)

                val cursorY = richText.top + (baseline + ascent)

                val visibleHeight = Rect().let { rect ->
                    scrollView.getDrawingRect(rect)
                    rect.bottom - rect.top
                }

                val scrollTopY = scrollView.scrollY
                val scroll75pY = scrollTopY + (visibleHeight*3/4)

                // Scroll unless cursor is already in the top part of the visible rect
                val scrollTo = if (cursorY < scrollTopY) { // Too high
                    cursorY
                } else if (cursorY > scroll75pY) {  // Too low
                    cursorY - (visibleHeight*3/4)
                } else {
                    -1
                }

                if (scrollTo != -1) {
                    scrollView.smoothScrollTo(0, scrollTo)
                }

                if (BuildConfig.LOG_DEBUG) {
                    LogUtils.d(TAG, scrollTo)
                }
            }
        }
    }

    fun deactivate() {
        removeTextChangedListener(userEditingTextWatcher)

        visibility = View.GONE
    }

    /* Clear the focus on back press before letting IME handle the event. */
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Clear focus before IME handling the event")
            clearFocus()
        }

        return super.onKeyPreIme(keyCode, event)
    }

    companion object {
        val TAG: String = RichTextEdit::class.java.name
    }
}