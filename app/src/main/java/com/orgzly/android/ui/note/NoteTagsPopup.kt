package com.orgzly.android.ui.note

import android.content.Context
import android.graphics.Rect
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.PopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.orgzly.R
import com.orgzly.android.ui.util.KeyboardUtils
import com.orgzly.android.ui.util.goneUnless
import com.orgzly.databinding.ItemNoteTagBinding
import com.orgzly.databinding.PopupNoteTagsBinding

/**
 * Tags menu with Search aligned vertically with the Tags control.
 *
 * Ticking a tag changes the note's tags right away. Tapping a tag ticks it and closes the menu,
 * tapping its check box only ticks it. Typing filters the list;
 * Enter or the add row ticks the typed tag, creating it if it's new.
 */
class NoteTagsPopup(
    context: Context,
    knownTags: List<String>,
    noteTags: List<String>,
    private val onChange: (List<String>) -> Unit
) : NotePillPopup(context, R.layout.popup_note_tags, width(context), alignToWindowStart = true) {

    private val binding = PopupNoteTagsBinding.bind(content)

    private val selected = noteTags.toMutableList()

    // The note's tags go first; the order then stays put while ticking
    private val tags = (noteTags + knownTags).distinct().toMutableList()

    private var anchor: View? = null

    // The keyboard can cover the bottom of the menu: keep the list above it
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        limitListHeight()
    }

    init {
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED

        binding.search.filters = arrayOf(TagCharactersFilter())

        binding.search.doAfterTextChanged {
            render()
        }

        binding.search.setOnEditorActionListener { _, _, _ ->
            addTypedTag()
            true
        }

        binding.addTag.setOnClickListener {
            addTypedTag()
        }

        render()
    }

    override fun show(anchor: View) {
        this.anchor = anchor

        limitListHeight()

        super.show(anchor)

        KeyboardUtils.openSoftKeyboard(binding.search)

        anchor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        content.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        popup.setOnDismissListener {
            anchor.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            content.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
    }

    protected override fun verticalOffset(anchor: View): Int =
        -anchor.height + (anchor.height - binding.search.measuredHeight) / 2 - content.paddingTop

    private fun query(): String {
        return binding.search.text.toString()
    }

    private fun render() {
        val query = query()

        binding.tagList.removeAllViews()

        tags.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }.forEach { tag ->
            val row = ItemNoteTagBinding.inflate(LayoutInflater.from(context), binding.tagList, false)

            row.label.text = tag
            row.check.contentDescription = tag
            row.check.isChecked = tag in selected

            fun updateRowDescription() {
                row.root.contentDescription = context.getString(
                    if (row.check.isChecked) R.string.deselect_tag_and_close else R.string.select_tag_and_close, tag)
            }
            updateRowDescription()

            row.check.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selected.add(tag)
                } else {
                    selected.removeAll { it == tag }
                }
                updateRowDescription()
                onChange(selected.toList())
            }

            // The check box only ticks, so more tags can be picked; the rest of the row ticks and closes
            row.root.setOnClickListener {
                row.check.toggle()
                dismiss()
            }

            binding.tagList.addView(row.root)
        }

        binding.addTag.text = context.getString(R.string.add_tag, query)
        binding.addTag.goneUnless(query.isNotEmpty() && query !in tags)

        binding.noTags.goneUnless(query.isEmpty() && tags.isEmpty())

        limitListHeight()
    }

    /**
     * The list scrolls once it's taller than [MAX_LIST_HEIGHT_DP],
     * or than the space left between the menu's top and the keyboard.
     */
    private fun limitListHeight() {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width(context), View.MeasureSpec.AT_MOST)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        binding.search.measure(widthSpec, unspecified)
        binding.tagList.measure(widthSpec, unspecified)

        var max = context.dp(MAX_LIST_HEIGHT_DP)

        val below = spaceBelowAnchor()
        if (below != null) {
            // Everything in the menu but the list
            var others = binding.root.paddingTop + binding.root.paddingBottom
            listOf(binding.search, binding.addTag, binding.noTags).filter { it.visibility == View.VISIBLE }.forEach {
                it.measure(widthSpec, unspecified)
                val lp = it.layoutParams as ViewGroup.MarginLayoutParams
                others += it.measuredHeight + lp.topMargin + lp.bottomMargin
            }

            max = minOf(max, maxOf(context.dp(MIN_LIST_HEIGHT_DP), below - others - context.dp(8)))
        }

        val height = if (binding.tagList.measuredHeight > max) max else ViewGroup.LayoutParams.WRAP_CONTENT

        if (binding.tagListScroll.layoutParams.height != height) {
            binding.tagListScroll.layoutParams = binding.tagListScroll.layoutParams.apply {
                this.height = height
            }
        }
    }

    private fun spaceBelowAnchor(): Int? {
        val anchor = anchor ?: return null

        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)

        val location = IntArray(2)
        val root = anchor.rootView
        root.getLocationOnScreen(location)
        val rootBounds = Rect(location[0], location[1], location[0] + root.width, location[1] + root.height)
        val ime = ViewCompat.getRootWindowInsets(anchor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

        anchor.getLocationOnScreen(location)
        val menuTop = location[1] + anchor.height + verticalOffset(anchor)

        return spaceBelowMenu(frame, rootBounds, ime, menuTop)
    }

    private fun addTypedTag() {
        val tag = query()

        if (tag.isEmpty()) {
            return
        }

        if (tag !in tags) {
            tags.add(tag)
        }

        if (tag !in selected) {
            selected.add(tag)
            onChange(selected.toList())
        }

        binding.search.setText("")
    }

    /**
     * Org's [:alnum:] includes letters, decimal digits, combining marks and letter numbers.
     * The additional tag characters are _ @ # and %. Spaces become _.
     */
    internal class TagCharactersFilter : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            val input = source.subSequence(start, end)
            var result: SpannableStringBuilder? = null

            // Work backwards so replacements keep the original offsets and remap the spans.
            var offset = input.length
            while (offset > 0) {
                val codePoint = Character.codePointBefore(input, offset)
                val previous = offset - Character.charCount(codePoint)
                val replacement = when {
                    Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> "_"
                    isTagCharacter(codePoint) -> null
                    else -> ""
                }
                if (replacement != null) {
                    if (result == null) {
                        result = SpannableStringBuilder(input.toString())
                        if (source is Spanned) {
                            TextUtils.copySpansFrom(source, start, end, Any::class.java, result, 0)
                        }
                    }
                    result.replace(previous, offset, replacement)
                }
                offset = previous
            }

            return result
        }

        private fun isTagCharacter(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.LETTER_NUMBER.toInt() -> true
            else -> Character.isLetterOrDigit(codePoint) ||
                (codePoint <= Char.MAX_VALUE.code && codePoint.toChar() in "_@#%")
        }
    }

    companion object {
        /** All bounds and the menu's top are in screen coordinates, including in multi-window. */
        internal fun spaceBelowMenu(frame: Rect, rootBounds: Rect, imeHeight: Int, menuTop: Int): Int {
            val bottom = if (imeHeight > 0) minOf(frame.bottom, rootBounds.bottom - imeHeight) else frame.bottom
            return bottom - menuTop
        }

        /** Screen width less 8dp on each side, capped at 480dp. */
        private fun width(context: Context): Int {
            val screen = context.resources.displayMetrics.widthPixels
            return minOf(screen - context.dp(16), context.dp(480))
        }

        private const val MAX_LIST_HEIGHT_DP = 240
        private const val MIN_LIST_HEIGHT_DP = 96
    }
}
