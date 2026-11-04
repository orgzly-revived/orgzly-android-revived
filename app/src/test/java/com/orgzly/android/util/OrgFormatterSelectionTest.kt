package com.orgzly.android.util

import androidx.preference.PreferenceManager
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrgFormatterSelectionTest : OrgFormatterTest() {
    @Before
    override fun setUp() {
        super.setUp()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.pref_key_style_text), true).apply()
        AppPreferences.styledTextWithMarks(context, false)
    }

    private fun cut(source: String, selection: String, folded: Boolean = true): String? {
        val map = SourceTextMap(source)
        val rendered = OrgFormatter.parseForEditing(source, context, true, true, map, listOf(folded))
        val start = rendered.indexOf(selection)
        check(start >= 0) { "Selection is absent from rendered text" }
        return map.cut(start, start + selection.length)
    }

    @Test
    fun cutsAfterLinksWithoutTouchingTheLink() {
        assertEquals("[[https://example.com][label]] ",
            cut("[[https://example.com][label]] after", "after"))
    }

    @Test
    fun cutsPartialAndWholeLinkLabels() {
        assertEquals("[[https://example.com][ll]] rest",
            cut("[[https://example.com][label]] rest", "abe"))
        assertEquals(" rest", cut("[[https://example.com][label]] rest", "label"))
    }

    @Test
    fun cutsAcrossFormattingBoundaries() {
        assertEquals("*bo*fter", cut("*bold* after", "ld a"))
        assertEquals(" after", cut("*bold* after", "bold"))
        assertEquals(" before ", cut("*one* before /two/", "two")?.let {
            // A subsequent render must still map the remaining formatted text correctly.
            cut(it, "one")
        })
    }

    @Test
    fun cutsNestedMarkupInALinkLabel() {
        assertEquals("[[https://example.com][*bo*]] rest",
            cut("[[https://example.com][*bold*]] rest", "ld"))
        assertEquals(" rest", cut("[[https://example.com][*bold*]] rest", "bold"))
    }

    @Test
    fun cutsAfterFoldedDrawersAndInsideExpandedDrawers() {
        val source = ":LOGBOOK:\nsecret *text*\n:END:\nafter"
        assertEquals(":LOGBOOK:\nsecret *text*\n:END:\n", cut(source, "after"))
        assertNull(cut(source, ":LOGBOOK:…"))
        assertEquals(":LOGBOOK:\nsecret \n:END:\nafter", cut(source, "text", folded = false))
    }

    @Test
    fun linkOffsetsWorkWhenFormattingMarksAreVisible() {
        AppPreferences.styledTextWithMarks(context, true)
        assertEquals("*bold* [[https://example.com][label]] ",
            cut("*bold* [[https://example.com][label]] after", "after"))
    }
}
