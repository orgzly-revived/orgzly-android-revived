package com.orgzly.android.ui.notes.query.agenda

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.preference.PreferenceManager
import com.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.db.entity.Tags
import com.orgzly.android.ui.OnViewHolderClickListener
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.NoteItemViewHolder
import com.orgzly.databinding.ItemHeadBinding
import com.orgzly.org.datetime.OrgRange
import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaPendingNoteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private val today get() = DateTime.now().withTimeAtStartOfDay()
    private val note get() = NoteView(
        Note(42, title = "Prepare report", state = "TODO", priority = "A", tags = Tags(listOf("work")),
            content = "Details", contentLineCount = 1, position = NotePosition(1)),
        scheduledRangeString = "<${today.minusDays(3).toString("yyyy-MM-dd")} 09:00-09:30 .+2d>",
        deadlineRangeString = "<${today.minusDays(1).toString("yyyy-MM-dd")}>", bookName = "Project")

    @Before fun setTheme() {
        compose.activity.setTheme(R.style.AppLightTheme)
        preferences(planning = true, bookName = "2")
    }

    private fun preferences(planning: Boolean, bookName: String) {
        val context = compose.activity
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.pref_key_display_planning), planning)
            .putString(context.getString(R.string.pref_key_book_name_in_search), bookName).commit()
    }

    private fun row(type: TimeType) = note.let { note ->
        val text = if (type == TimeType.SCHEDULED) note.scheduledRangeString else note.deadlineRangeString
        AgendaPendingRow(AgendaItem.Note(42, note, type), AgendaPendingDate(type, OrgRange.parse(text), false))
    }

    private fun actualBinding() = ItemHeadBinding.bind(
        requireNotNull(compose.activity.findViewById<View>(R.id.item_head_container)))

    private fun simpleBinding(row: AgendaPendingRow): ItemHeadBinding {
        val adapter = AgendaAdapter(compose.activity, object : OnViewHolderClickListener<AgendaItem> {
            override fun onClick(view: View, position: Int, item: AgendaItem) = Unit
            override fun onLongClick(view: View, position: Int, item: AgendaItem) = Unit
        })
        adapter.submitList(listOf(row.item))
        val holder = adapter.createViewHolder(FrameLayout(compose.activity), AgendaAdapter.NOTE_ITEM_TYPE)
        adapter.onBindViewHolder(holder, 0)
        return (holder as NoteItemViewHolder).binding
    }

    private fun assertSamePresentation(actual: ItemHeadBinding, expected: ItemHeadBinding) {
        listOf(R.id.item_head_title_view, R.id.item_head_book_name_text,
            R.id.item_head_scheduled_text, R.id.item_head_deadline_text).forEach { id ->
            val actualText = actual.root.findViewById<TextView>(id)
            val expectedText = expected.root.findViewById<TextView>(id)
            assertEquals(expectedText.visibility, actualText.visibility)
            if (actualText.visibility == View.VISIBLE) assertEquals(expectedText.text.toString(), actualText.text.toString())
            assertEquals(expectedText.textSize, actualText.textSize, 0f)
            assertEquals(expectedText.currentTextColor, actualText.currentTextColor)
            fun colors(text: CharSequence) = (text as? Spanned)?.let { styled ->
                styled.getSpans(0, styled.length, ForegroundColorSpan::class.java).map {
                    Triple(styled.getSpanStart(it), styled.getSpanEnd(it), it.foregroundColor)
                }
            }
            if (actualText.visibility == View.VISIBLE) assertEquals(colors(expectedText.text), colors(actualText.text))
        }
        assertEquals(expected.itemHeadScheduledIcon.visibility, actual.itemHeadScheduledIcon.visibility)
        assertEquals(expected.itemHeadDeadlineIcon.visibility, actual.itemHeadDeadlineIcon.visibility)
        assertEquals(expected.itemHeadBookNameIcon.visibility, actual.itemHeadBookNameIcon.visibility)
    }

    @Test fun `scheduled row matches Simple title metadata icons and text styling`() {
        val row = row(TimeType.SCHEDULED)
        compose.setContent { OrgzlyBootstrap { AgendaPendingNote(row, {}) } }
        compose.runOnIdle {
            val actual = actualBinding()
            assertSamePresentation(actual, simpleBinding(row))
            val title = actual.root.findViewById<TextView>(R.id.item_head_title_view).text.toString()
            assertTrue(title.contains("TODO") && title.contains("#A") && title.contains("work"))
            assertEquals(View.VISIBLE, actual.itemHeadScheduledText.visibility)
            assertEquals(View.GONE, actual.itemHeadDeadlineText.visibility)
        }
    }

    @Test fun `deadline row honors the same hidden metadata preferences as Simple`() {
        preferences(planning = false, bookName = "0")
        val row = row(TimeType.DEADLINE)
        compose.setContent { OrgzlyBootstrap { AgendaPendingNote(row, {}) } }
        compose.runOnIdle {
            val actual = actualBinding()
            assertSamePresentation(actual, simpleBinding(row))
            assertEquals(View.GONE, actual.itemHeadDeadlineText.visibility)
            assertEquals(View.GONE, actual.itemHeadBookNameText.visibility)
        }
    }

    @Test fun `touching the native note row opens its note`() {
        var opened: Long? = null
        compose.setContent { OrgzlyBootstrap { AgendaPendingNote(row(TimeType.SCHEDULED), { opened = it }) } }
        compose.onNodeWithTag("agenda_pending_note_42_SCHEDULED").performTouchInput { click() }
        assertEquals(42L, opened)
    }

    @Test fun `reusing a row updates its planning type metadata and click target`() {
        var current by mutableStateOf(row(TimeType.SCHEDULED))
        var opened: Long? = null
        compose.setContent { OrgzlyBootstrap { AgendaPendingNote(current, { opened = it }) } }
        val previousView = compose.runOnIdle { actualBinding().root }
        val deadline = row(TimeType.DEADLINE).let { original ->
            original.copy(item = AgendaItem.Note(43, original.item.note.copy(
                note = original.item.note.note.copy(id = 43, title = "Second task")), TimeType.DEADLINE))
        }
        compose.runOnIdle { current = deadline }
        compose.runOnIdle {
            val actual = actualBinding()
            assertSame(previousView, actual.root)
            assertSamePresentation(actual, simpleBinding(deadline))
            assertEquals(View.GONE, actual.itemHeadScheduledText.visibility)
            assertEquals(View.VISIBLE, actual.itemHeadDeadlineText.visibility)
        }
        compose.onNodeWithTag("agenda_pending_note_43_DEADLINE").performTouchInput { click() }
        assertEquals(43L, opened)
    }
}
