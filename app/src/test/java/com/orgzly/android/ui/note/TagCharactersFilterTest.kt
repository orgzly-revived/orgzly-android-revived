package com.orgzly.android.ui.note

import android.app.Application
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.view.inputmethod.BaseInputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TagCharactersFilterTest {
    private val filter = NoteTagsPopup.TagCharactersFilter()
    private val empty = SpannableString("")
    private val rareChinese = "\uD840\uDC00" // U+20000

    @Test
    fun `Unicode letters and decimal digits are preserved including supplementary characters`() {
        val input = "工作${rareChinese}عملéЖ\uD801\uDC00\uD835\uDFD9١_@#%"
        assertNull(filter.filter(input, 0, input.length, empty, 0, 0))
    }

    @Test
    fun `Org combining marks and letter numbers are preserved without normalization`() {
        val input = SpannableString("عَمَلe\u0301\u093E\u20DDⅫ")
        assertNull(filter.filter(input, 0, input.length, empty, 0, 0))
    }

    @Test
    fun `unsupported supplementary symbols are removed as complete characters`() {
        val input = "work👩‍💻-😀/\uDB40\uDC23${rareChinese}home"
        assertEquals("work${rareChinese}home", filter.filter(input, 0, input.length, empty, 0, 0).toString())
    }

    @Test
    fun `whitespace still becomes underscores and permitted punctuation stays unchanged`() {
        val input = "work \t\u00A0\u2007\u202F\nhome_@#%"
        assertEquals("work______home_@#%", filter.filter(input, 0, input.length, empty, 0, 0).toString())
    }

    @Test
    fun `only the requested source range is read including at surrogate boundaries`() {
        val input = "!${rareChinese}?"
        assertNull(filter.filter(input, 1, 3, empty, 0, 0))
        assertEquals("", filter.filter(input, 1, 2, empty, 0, 0).toString())
        assertEquals("", filter.filter(input, 2, 3, empty, 0, 0).toString())
    }

    @Test
    fun `deletion does not filter or rewrite the existing destination`() {
        val existing = SpannableString("legacy-tag😀${rareChinese}")
        val original = existing.toString()
        assertNull(filter.filter("", 0, 0, existing, 0, existing.length))
        assertEquals(original, existing.toString())
    }

    @Test
    fun `replacing whitespace preserves the keyboard composing span`() {
        val input = SpannableString("ab cd")
        BaseInputConnection.setComposingSpans(input)

        val result = filter.filter(input, 0, input.length, empty, 0, 0) as Spannable

        assertEquals("ab_cd", result.toString())
        assertEquals(0, BaseInputConnection.getComposingSpanStart(result))
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(result))
        assertEquals("ab cd", input.toString())
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(input))
    }

    @Test
    fun `removing supplementary characters remaps composing and other spans`() {
        val input = SpannableString("ab😀-cd")
        val marker = Any()
        input.setSpan(marker, 5, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        BaseInputConnection.setComposingSpans(input)

        val result = filter.filter(input, 0, input.length, empty, 0, 0) as Spannable

        assertEquals("abcd", result.toString())
        assertEquals(0, BaseInputConnection.getComposingSpanStart(result))
        assertEquals(4, BaseInputConnection.getComposingSpanEnd(result))
        assertEquals(2, result.getSpanStart(marker))
        assertEquals(4, result.getSpanEnd(marker))
        assertEquals(input.getSpanFlags(marker), result.getSpanFlags(marker))
        assertEquals("ab😀-cd", input.toString())
        assertEquals(5, input.getSpanStart(marker))
        assertEquals(7, input.getSpanEnd(marker))
    }

    @Test
    fun `source slices clip and remap composing spans without changing the source`() {
        val input = SpannableString("!ab cd?")
        BaseInputConnection.setComposingSpans(input)

        val result = filter.filter(input, 1, 6, empty, 0, 0) as Spannable

        assertEquals("ab_cd", result.toString())
        assertEquals(0, BaseInputConnection.getComposingSpanStart(result))
        assertEquals(5, BaseInputConnection.getComposingSpanEnd(result))
        assertEquals(0, BaseInputConnection.getComposingSpanStart(input))
        assertEquals(7, BaseInputConnection.getComposingSpanEnd(input))
    }
}
