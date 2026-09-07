package com.orgzly.android.ui

import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.view.ActionMode
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import com.orgzly.R
import com.orgzly.android.ui.views.richtext.RichText
import com.orgzly.android.ui.views.richtext.RichTextView
import com.orgzly.android.ui.views.style.DrawerMarkerSpan
import com.orgzly.android.prefs.AppPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RichTextCutTest {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.AppLightThemeCommon)

    private fun richText(editable: Boolean = true): RichText {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.editable, editable.toString())
            .addAttribute(android.R.attr.inputType, "textMultiLine")
            .build()
        return RichText(context, attrs)
    }

    @Test
    fun cutCopiesTheSelectionAndNotifiesTheOwnerWithUpdatedSource() {
        val richText = richText()
        richText.setSourceText("first second third")
        var changedSource: String? = null
        richText.setOnUserTextChangeListener { changedSource = it }
        val view = richText.findViewById<RichTextView>(R.id.rich_text_view)
        Selection.setSelection(view.text as Spannable, 6, 12)
        val mode = SelectionMode(context, PopupMenu(context, view).menu)
        val callback = view.customSelectionActionModeCallback!!
        callback.onPrepareActionMode(mode, mode.menu)

        assertTrue(callback.onActionItemClicked(mode, mode.menu.findItem(android.R.id.cut)))

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("second", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals("first  third", richText.getSourceText().toString())
        assertEquals("first  third", view.text.toString())
        assertEquals("first  third", changedSource)
        assertTrue(mode.finished)
        assertFalse(richText.isBeingEdited())
    }

    @Test
    fun cutAlsoUpdatesSourceWithoutAChangeListener() {
        val richText = richText()
        richText.setSourceText("title words")
        val view = richText.findViewById<RichTextView>(R.id.rich_text_view)
        Selection.setSelection(view.text as Spannable, 0, 5)
        val mode = SelectionMode(context, PopupMenu(context, view).menu)
        val callback = view.customSelectionActionModeCallback!!
        callback.onPrepareActionMode(mode, mode.menu)
        callback.onActionItemClicked(mode, mode.menu.findItem(android.R.id.cut))
        assertEquals(" words", richText.getSourceText().toString())
    }

    @Test
    fun expandingADrawerRebuildsSelectionOffsets() {
        AppPreferences.drawersFolded(context, true)
        val richText = richText()
        richText.setSourceText(":LOGBOOK:\nsecret\n:END:\nafter")
        val view = richText.findViewById<RichTextView>(R.id.rich_text_view)
        val spanned = view.text as Spanned
        richText.toggleDrawer(spanned.getSpans(0, spanned.length, DrawerMarkerSpan::class.java).first())
        val start = view.text.toString().indexOf("secret")
        assertTrue(start >= 0)
        Selection.setSelection(view.text as Spannable, start, start + 6)
        val mode = SelectionMode(context, PopupMenu(context, view).menu)
        val callback = view.customSelectionActionModeCallback!!
        callback.onPrepareActionMode(mode, mode.menu)
        callback.onActionItemClicked(mode, mode.menu.findItem(android.R.id.cut))
        assertEquals(":LOGBOOK:\n\n:END:\nafter", richText.getSourceText().toString())
    }

    @Test
    fun readOnlyAndUnmappedViewsDoNotOfferCut() {
        assertNull(richText(false).findViewById<RichTextView>(R.id.rich_text_view)
            .customSelectionActionModeCallback)
        val richText = richText()
        richText.setSourceText("source")
        richText.setVisibleText("different")
        val view = richText.findViewById<RichTextView>(R.id.rich_text_view)
        val mode = SelectionMode(context, PopupMenu(context, view).menu)
        view.customSelectionActionModeCallback!!.onPrepareActionMode(mode, mode.menu)
        assertNull(mode.menu.findItem(android.R.id.cut))
    }

    private class SelectionMode(context: Context, private val items: Menu) : ActionMode() {
        var finished = false
        private val inflater = MenuInflater(context)
        override fun finish() { finished = true }
        override fun invalidate() = Unit
        override fun getMenu(): Menu = items
        override fun getMenuInflater(): MenuInflater = inflater
        override fun setTitle(title: CharSequence?) = Unit
        override fun setTitle(resId: Int) = Unit
        override fun setSubtitle(subtitle: CharSequence?) = Unit
        override fun setSubtitle(resId: Int) = Unit
        override fun setCustomView(view: View?) = Unit
        override fun getTitle(): CharSequence? = null
        override fun getSubtitle(): CharSequence? = null
        override fun getCustomView(): View? = null
    }
}
