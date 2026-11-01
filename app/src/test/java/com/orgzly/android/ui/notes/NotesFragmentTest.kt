package com.orgzly.android.ui.notes

import android.os.Looper
import androidx.fragment.app.FragmentManager
import com.orgzly.R
import com.orgzly.android.AppDataRule
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.notes.book.BookFragment
import com.orgzly.android.ui.notes.query.agenda.AgendaFragment
import com.orgzly.android.ui.notes.query.search.SearchFragment
import com.orgzly.org.datetime.OrgDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.TreeSet

/**
 * Updates notes from each kind of [NotesFragment], also after the activity is recreated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotesFragmentTest {
    @get:Rule
    val data = AppDataRule()

    @Test
    fun testBook() {
        val bookId = data.loadBook("book", "* Note A\n")

        testUpdates(BookFragment::class.java) {
            DisplayManager.displayBook(it, bookId, 0)
        }
    }

    @Test
    fun testSearch() {
        data.loadBook("book", "* Note A\n")

        testUpdates(SearchFragment::class.java) {
            DisplayManager.displayQuery(it, "b.book")
        }
    }

    @Test
    fun testAgenda() {
        data.loadBook("book", "* Note A\n")

        testUpdates(AgendaFragment::class.java) {
            DisplayManager.displayQuery(it, "ad.3")
        }
    }

    private fun testUpdates(type: Class<out NotesFragment>, display: (FragmentManager) -> Unit) {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        display(controller.get().supportFragmentManager)
        idle()

        val noteIds = TreeSet(setOf(data.noteId("Note A")))

        notesFragment(controller, type).onDateTimeSet(R.id.schedule, noteIds, OrgDateTime.parse("<2026-10-17 Sat>"))
        assertEquals("<2026-10-17 Sat>", noteView().scheduledTimeString)

        controller.recreate()
        idle()

        notesFragment(controller, type).onDateTimeSet(R.id.deadline, noteIds, OrgDateTime.parse("<2026-10-18 Sun>"))
        assertEquals("<2026-10-18 Sun>", noteView().deadlineTimeString)

        controller.pause().stop().destroy()
    }

    private fun notesFragment(controller: ActivityController<MainActivity>, type: Class<out NotesFragment>): NotesFragment {
        val fragment = controller.get().supportFragmentManager.fragments.single { it is NotesFragment }
        assertEquals(type, fragment.javaClass)
        return fragment as NotesFragment
    }

    private fun noteView() = data.dataRepository.getLastNoteView("Note A")!!

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
