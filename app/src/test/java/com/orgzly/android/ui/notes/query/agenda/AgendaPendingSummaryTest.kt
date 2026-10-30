package com.orgzly.android.ui.notes.query.agenda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.core.view.children
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaPendingSummaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private val today get() = DateTime.now().withTimeAtStartOfDay()
    private var notes by mutableStateOf(emptyList<NoteView>())
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private var groupScheduledWithToday by mutableStateOf(true)
    private val calendar = AgendaCalendarState(today.toLocalDate())

    @Before fun setTheme() {
        compose.activity.setTheme(R.style.AppLightTheme)
        AppCompatDelegate.create(compose.activity, null).installViewFactory()
    }

    private fun date(offset: Int, suffix: String = "") = "<${today.plusDays(offset).toString("yyyy-MM-dd")} $suffix>"
    private fun note(id: Long, scheduled: String? = null, deadline: String? = null) =
        NoteView(Note(id, title = "Pending task $id", state = "TODO", position = NotePosition(1)),
            scheduledRangeString = scheduled, deadlineRangeString = deadline, bookName = "Work")

    private fun show(fontScale: Float = 1f, onAction: (Long, Int) -> Unit = { _, _ -> }, onOpen: (Long) -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OrgzlyBootstrap {
                    AgendaTimeGrid(QueryState.default.copy(notes = notes, loading = QueryViewModel.ViewState.LOADED),
                        mode, { mode = it }, onOpen, calendarState = calendar,
                        groupScheduledWithToday = groupScheduledWithToday, onNoteAction = onAction)
                }
            }
        }
    }

    @Test fun `summary counts planning dates just like Simple and lists them by date`() {
        notes = listOf(note(1, date(-3)), note(2, date(-2), date(-1)))
        var opened: Long? = null
        show { opened = it }
        compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed()
            .assertTextContains("Overdue 3")
        compose.onNodeWithText("Today", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Pending task 1").assertDoesNotExist()
        assertTrue(AgendaGridModel.build(notes, today, 1).isEmpty())
        screenshot("collapsed")
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onAllNodes(hasText("Overdue", substring = true) and hasAnyAncestor(isDialog())).assertCountEquals(1)
        compose.onNodeWithText("Overdue").assertDoesNotExist()
        val tops = listOf("1_SCHEDULED", "2_SCHEDULED", "2_DEADLINE").map {
            compose.onNodeWithTag("agenda_pending_note_$it").fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals("One list, the oldest date first", tops.sorted(), tops)
        screenshot("expanded")
        compose.onNodeWithTag("agenda_pending_note_2_DEADLINE").performClick()
        assertEquals(2L, opened)
        compose.onNodeWithTag("agenda_pending_list").assertDoesNotExist()
    }

    @Test fun `a single summary serves every day count and Month`() {
        notes = listOf(note(1, date(-3)))
        show()
        AgendaViewMode.entries.filter { it != AgendaViewMode.SIMPLE }.forEach { next ->
            compose.runOnIdle { mode = next }
            compose.onAllNodesWithTag("agenda_pending_summary", useUnmergedTree = true).assertCountEquals(1)
            compose.onNodeWithTag("agenda_pending_summary", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test fun `summary appears above all-day entries in every timed view`() {
        notes = listOf(note(1, date(-3)), note(2, date(0)))
        show()
        AgendaViewMode.entries.filter { it != AgendaViewMode.SIMPLE && it != AgendaViewMode.MONTH }.forEach { next ->
            compose.runOnIdle { mode = next }
            val allDay = compose.onNodeWithTag("agenda_grid_all_day").fetchSemanticsNode().boundsInRoot
            val summary = compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val entry = compose.onNodeWithText("Pending task 2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val hours = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot
            assertTrue("$next must place pending activities above all-day entries", summary.bottom <= entry.top)
            assertTrue("$next must keep all-day entries above hours", entry.bottom <= hours.top)
            assertTrue(summary.top >= allDay.top)
        }
    }

    @Test fun `Day uses the empty all-day space beside its date for the summary`() {
        notes = listOf(note(1, date(-3)))
        show()
        val date = compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").fetchSemanticsNode().boundsInRoot
        val shelf = compose.onNodeWithTag("agenda_grid_all_day").fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val hours = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot
        assertTrue(summary.top >= date.top)
        assertTrue(summary.left >= date.right)
        assertTrue(summary.bottom <= date.bottom)
        assertEquals(shelf.bottom, hours.top, 1f)
    }

    @Test fun `summary disappears outside today and outside the current month`() {
        notes = listOf(note(1, date(-3)))
        show()
        compose.runOnIdle { calendar.date.value = today.plusDays(1).toLocalDate().toString() }
        compose.onNodeWithTag("agenda_pending_summary").assertDoesNotExist()
        compose.runOnIdle { mode = AgendaViewMode.MONTH }
        // Tomorrow can be in the next month at a month boundary.
        compose.runOnIdle { calendar.date.value = today.toLocalDate().toString() }
        compose.onNodeWithTag("agenda_pending_summary", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { calendar.date.value = today.plusMonths(1).toLocalDate().toString() }
        compose.onNodeWithTag("agenda_pending_summary", useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test fun `summary and all-day notes share their height and spacing`() {
        notes = listOf(note(1, date(-3)), note(2, date(0)), note(3, date(0)))
        val entries = AgendaGridModel.build(notes, today, 1).associateBy { it.noteId }
        show()
        AgendaViewMode.entries.filter { it != AgendaViewMode.SIMPLE && it != AgendaViewMode.MONTH }.forEach { next ->
            compose.runOnIdle { mode = next }
            val summary = compose.onNodeWithTag("agenda_pending_summary").fetchSemanticsNode().boundsInRoot
            val first = compose.onNodeWithTag("agenda_grid_entry_${entries.getValue(2).key}").fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithTag("agenda_grid_entry_${entries.getValue(3).key}").fetchSemanticsNode().boundsInRoot
            assertEquals("$next summary height", first.height, summary.height, 1f)
            assertEquals("$next block spacing", second.top - first.bottom, first.top - summary.bottom, 1f)
            assertEquals("$next column alignment", first.left, summary.left, 1f)
            assertEquals("$next column width", first.right, summary.right, 1f)
        }
    }

    @Test fun `weekly summary stays inside the current day column and opens a readable list`() {
        notes = listOf(note(1, deadline = date(-3)))
        show()
        listOf(today, today.minusDays(3)).forEach { start ->
            compose.runOnIdle {
                mode = AgendaViewMode.SEVEN_DAYS
                calendar.date.value = start.toLocalDate().toString()
            }
            val date = compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").fetchSemanticsNode().boundsInRoot
            val summary = compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(summary.left >= date.left && summary.right <= date.right)
            compose.onNodeWithTag("agenda_pending_summary").performClick()
            val list = compose.onNodeWithTag("agenda_pending_list").fetchSemanticsNode().boundsInRoot
            assertTrue("The list must be wider than a day column", list.width > summary.width * 3)
            compose.onNodeWithTag("agenda_pending_note_1_DEADLINE").performClick()
        }
    }

    @Test fun `dialog heading and note layout stay the same in every timed view`() {
        notes = listOf(note(1, date(-3)), note(2, deadline = date(-1)))
        show()
        val layouts = AgendaViewMode.entries.filter { it != AgendaViewMode.SIMPLE && it != AgendaViewMode.MONTH }
            .map { next ->
                compose.runOnIdle { mode = next }
                compose.onNodeWithTag("agenda_pending_summary").performClick()
                val bounds = listOf(
                    compose.onNodeWithTag("agenda_pending_list").fetchSemanticsNode().boundsInRoot,
                    compose.onNode(hasText("Overdue 2") and hasAnyAncestor(isDialog())).fetchSemanticsNode().boundsInRoot,
                    compose.onNodeWithTag("agenda_pending_note_1_SCHEDULED").fetchSemanticsNode().boundsInRoot,
                    compose.onNodeWithTag("agenda_pending_note_2_DEADLINE").fetchSemanticsNode().boundsInRoot,
                )
                compose.onNodeWithTag("agenda_pending_close").performClick()
                bounds
            }
        assertEquals("Dialog sizes must not depend on the number of calendar columns", 1, layouts.distinct().size)
    }

    @Test fun `Month shows one summary in today cell and tapping it opens the day`() {
        notes = listOf(note(1, deadline = date(-3)), note(2, date(0)))
        mode = AgendaViewMode.MONTH
        var opened: Long? = null
        show { opened = it }
        val cell = compose.onNodeWithTag("agenda_grid_month_day_${today.toLocalDate()}")
            .fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("agenda_pending_summary", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(summary.left >= cell.left && summary.right <= cell.right)
        assertTrue(summary.top > cell.top && summary.bottom < cell.bottom)
        screenshot("month-cell")
        compose.onNodeWithTag("agenda_pending_summary", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(AgendaViewMode.DAY, mode)
        assertNull(opened)
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_1_DEADLINE").performClick()
        assertEquals(1L, opened)
    }

    @Test fun `preference changes update the summary while the grid is shown`() {
        // Grouped with today, the deadline today takes the place of the past scheduled date, as in Simple.
        notes = listOf(note(1, date(-3), date(0)))
        show()
        compose.onNodeWithTag("agenda_pending_summary").assertDoesNotExist()
        compose.runOnIdle { groupScheduledWithToday = false }
        compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed().assertTextContains("Overdue 1")
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onAllNodesWithTag("agenda_pending_note_1_SCHEDULED").assertCountEquals(1)
    }

    @Test fun `restart repeater retains its notation and updates when the planning date advances`() {
        notes = listOf(note(1, date(-1, ".+2d")))
        show()
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_1_SCHEDULED")
            .assertContentDescriptionContains("Next date depends on completion", substring = true)
        compose.runOnIdle { notes = listOf(note(1, date(2, ".+2d"))) }
        compose.onNodeWithTag("agenda_pending_summary").assertDoesNotExist()
        compose.onNodeWithTag("agenda_pending_list").assertDoesNotExist()
        compose.runOnIdle { calendar.date.value = today.plusDays(2).toLocalDate().toString() }
        compose.onNodeWithText("Pending task 1").assertIsDisplayed()
    }

    @Test fun `summary changes and opening its list preserve the visible hour position`() {
        notes = listOf(note(1, date(0, "09:00-10:00")))
        show()
        fun hourY() = compose.onNodeWithTag("agenda_grid_hour_11").fetchSemanticsNode().positionInRoot.y
        val before = hourY()
        compose.runOnIdle { notes = notes + note(2, date(-3)) }
        compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed()
        assertEquals(before, hourY(), 1f)
        val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        assertEquals(viewport, compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("agenda_pending_note_2_SCHEDULED").performClick()
        compose.runOnIdle { notes = notes.filter { it.note.id == 1L } }
        compose.onNodeWithTag("agenda_pending_summary").assertDoesNotExist()
        assertEquals(before, hourY(), 1f)
    }

    @Test fun `long pending lists create only nearby rows and scroll to the last note`() {
        notes = (1L..200L).map { note(it, deadline = date(it.toInt() - 201)) }
        var opened: Long? = null
        show { opened = it }
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_1_DEADLINE").assertIsDisplayed()
        compose.onNodeWithTag("agenda_pending_note_200_DEADLINE").assertDoesNotExist()
        compose.onNodeWithTag("agenda_pending_rows")
            .performScrollToNode(hasTestTag("agenda_pending_note_200_DEADLINE"))
        compose.onNodeWithTag("agenda_pending_note_200_DEADLINE").assertIsDisplayed().performClick()
        assertEquals(200L, opened)
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_200_DEADLINE").assertIsDisplayed()
    }

    @Test fun `a short pending list opens in a nearly full-screen dialog with no repeated heading`() {
        notes = listOf(note(1, deadline = date(-3)))
        show()
        val calendar = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNode(isDialog()).assertIsDisplayed()
        val list = compose.onNodeWithTag("agenda_pending_list").fetchSemanticsNode().boundsInRoot
        assertTrue("The dialog must use most of the screen width", list.width >= calendar.width * 0.85f)
        assertTrue("The dialog must use most of the screen height", list.height >= calendar.height * 0.8f)
        val close = compose.onNodeWithTag("agenda_pending_close").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val title = compose.onNode(hasText("Overdue 1") and hasAnyAncestor(isDialog())).fetchSemanticsNode().boundsInRoot
        assertTrue("The close button must precede the title", close.right <= title.left)
        assertTrue("The close button must be on the left of the dialog", close.center.x < list.center.x)
        compose.onNodeWithTag("agenda_pending_note_1_DEADLINE").assertIsDisplayed()
        compose.onAllNodes(hasText("Overdue", substring = true) and hasAnyAncestor(isDialog())).assertCountEquals(1)
        screenshot("dialog-short-list")
    }

    @Test @Config(qualifiers = "en-rUS-w851dp-h393dp-land")
    fun `landscape dialog keeps its close button visible and scrolls to the last note`() {
        notes = (1L..30L).map { note(it, deadline = date(it.toInt() - 31)) }
        show()
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_close").assertIsDisplayed()
        compose.onNodeWithTag("agenda_pending_rows")
            .performScrollToNode(hasTestTag("agenda_pending_note_30_DEADLINE"))
        compose.onNodeWithTag("agenda_pending_note_30_DEADLINE").assertIsDisplayed()
        screenshot("dialog-landscape")
        compose.onNodeWithTag("agenda_pending_close").performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_30_DEADLINE").assertIsDisplayed()
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test @Config(qualifiers = "it-rIT-w320dp-h640dp")
    fun `Italian summary fits a narrow screen with larger text`() {
        notes = listOf(note(1, date(-3)), note(2, deadline = date(-1)))
        show(fontScale = 1.5f)
        compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed()
            .assertTextContains("Scaduto", substring = true)
        compose.onNodeWithText("Oggi", substring = true).assertDoesNotExist()
        screenshot("italian-large-text")
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_rows")
            .performScrollToNode(hasTestTag("agenda_pending_note_1_SCHEDULED"))
        compose.onNodeWithTag("agenda_pending_note_1_SCHEDULED").assertIsDisplayed()
    }

    @Test fun `right swipe shows the Simple actions for the swiped note and closes with the list`() {
        notes = listOf(note(1, deadline = date(-3)), note(2, deadline = date(-2)))
        val actions = mutableListOf<Pair<Long, Int>>()
        var opened: Long? = null
        show(onAction = { id, action -> actions.add(id to action) }) { opened = it }
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_2_DEADLINE").performTouchInput { swipeRight() }
        compose.runOnIdle {
            val popup = requireNotNull(shadowOf(compose.activity.application).latestPopupWindow)
            assertTrue(popup.isShowing)
            val buttons = popup.contentView.findViewById<ViewGroup>(R.id.group).children.toList()
            assertEquals(listOf(R.string.schedule, R.string.deadline, R.string.state, R.string.done)
                .map { compose.activity.getString(it) }, buttons.map { it.tooltipText.toString() })
            buttons[0].performClick()
            assertEquals(listOf(2L to R.id.note_popup_set_schedule), actions)
            assertNull(opened)
            assertFalse(popup.isShowing)
        }
        compose.onNodeWithTag("agenda_pending_note_1_DEADLINE").performTouchInput { swipeRight() }
        val popup = compose.runOnIdle { requireNotNull(shadowOf(compose.activity.application).latestPopupWindow) }
        compose.onNodeWithTag("agenda_pending_close").performClick()
        compose.runOnIdle { assertFalse(popup.isShowing) }
    }

    @Test fun `pending swipes honor the configured single action and empty action list`() {
        notes = listOf(note(1, date(-3), date(-2)))
        val actions = mutableListOf<Pair<Long, Int>>()
        val key = compose.activity.getString(R.string.pref_key_note_popup_buttons_in_query_right)
        AppPreferences.notePopupActions(compose.activity, key, "set-state")
        show(onAction = { id, action -> actions.add(id to action) })
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_note_1_SCHEDULED").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(listOf(1L to R.id.note_popup_set_state), actions) }
        AppPreferences.notePopupActions(compose.activity, key, "")
        compose.onNodeWithTag("agenda_pending_note_1_DEADLINE").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, actions.size) }
    }

    @Test fun `vertical scrolling preserves note taps without triggering swipe actions`() {
        notes = (1L..40L).map { note(it, deadline = date(it.toInt() - 41)) }
        val actions = mutableListOf<Pair<Long, Int>>()
        var opened: Long? = null
        val key = compose.activity.getString(R.string.pref_key_note_popup_buttons_in_query_right)
        AppPreferences.notePopupActions(compose.activity, key, "toggle-state")
        show(onAction = { id, action -> actions.add(id to action) }) { opened = it }
        compose.onNodeWithTag("agenda_pending_summary").performClick()
        compose.onNodeWithTag("agenda_pending_rows").performTouchInput { swipeUp() }
        compose.onNodeWithTag("agenda_pending_rows")
            .performScrollToNode(hasTestTag("agenda_pending_note_40_DEADLINE"))
        compose.onNodeWithTag("agenda_pending_note_40_DEADLINE").performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()); assertEquals(40L, opened) }
    }

    private fun screenshot(name: String) {
        val path = System.getProperty("agenda.grid.screenshots.dir") ?: return
        val file = File(path, "pending-$name.png").apply { requireNotNull(parentFile).mkdirs() }
        val bitmap = compose.runOnIdle {
            val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
                ?: compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
