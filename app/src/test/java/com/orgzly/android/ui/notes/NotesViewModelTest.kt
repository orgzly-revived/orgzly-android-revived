package com.orgzly.android.ui.notes

import com.orgzly.android.AppDataRule
import com.orgzly.org.datetime.OrgDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the use cases for real, against the app's database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotesViewModelTest {
    @get:Rule
    val data = AppDataRule()

    private val viewModel = object : NotesViewModel() {}

    @Test
    fun testChangeState() {
        data.loadBook("book", "* Note A\n* TODO Note B\n")
        val ids = setOf(data.noteId("Note A"), data.noteId("Note B"))

        viewModel.changeState(ids, "DONE")
        assertEquals("DONE", state("Note A"))
        assertEquals("DONE", state("Note B"))

        viewModel.changeState(ids, null)
        assertNull(state("Note A"))
        assertNull(state("Note B"))
    }

    @Test
    fun testToggleState() {
        data.loadBook("book", "* TODO Note A\n")
        val ids = setOf(data.noteId("Note A"))

        viewModel.toggleState(ids)
        assertEquals("DONE", state("Note A"))

        viewModel.toggleState(ids)
        assertEquals("TODO", state("Note A"))
    }

    @Test
    fun testUpdateScheduledTime() {
        data.loadBook("book", "* Note A\n")
        val ids = setOf(data.noteId("Note A"))

        viewModel.updateScheduledTime(ids, OrgDateTime.parse("<2026-10-17 Sat>"))
        assertEquals("<2026-10-17 Sat>", noteView("Note A").scheduledTimeString)

        viewModel.updateScheduledTime(ids, null)
        assertNull(noteView("Note A").scheduledTimeString)
    }

    @Test
    fun testUpdateDeadlineTime() {
        data.loadBook("book", "* Note A\n")
        val ids = setOf(data.noteId("Note A"))

        viewModel.updateDeadlineTime(ids, OrgDateTime.parse("<2026-10-17 Sat>"))
        assertEquals("<2026-10-17 Sat>", noteView("Note A").deadlineTimeString)

        viewModel.updateDeadlineTime(ids, null)
        assertNull(noteView("Note A").deadlineTimeString)
    }

    @Test
    fun testClock() {
        data.loadBook("book", "* Note A\n")
        val ids = setOf(data.noteId("Note A"))

        viewModel.clockIn(ids)
        assertTrue(content("Note A").contains("CLOCK: ["))
        assertFalse(content("Note A").contains("=>"))

        viewModel.clockOut(ids)
        assertTrue(content("Note A").contains("=>"))

        viewModel.clockIn(ids)
        viewModel.clockCancel(ids)
        assertEquals(1, Regex("CLOCK:").findAll(content("Note A")).count())
    }

    private fun noteView(title: String) = data.dataRepository.getLastNoteView(title)!!

    private fun state(title: String) = noteView(title).note.state

    private fun content(title: String) = noteView(title).note.content.orEmpty()
}
