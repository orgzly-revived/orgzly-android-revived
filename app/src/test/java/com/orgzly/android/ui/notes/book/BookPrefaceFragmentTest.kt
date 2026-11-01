package com.orgzly.android.ui.notes.book

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.AppDataRule
import com.orgzly.android.AppExecutors
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.views.richtext.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Saves the preface from the editor, which then returns to the notebook, as it does when cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookPrefaceFragmentTest {
    @get:Rule
    val data = AppDataRule()

    @Test
    fun testDone() {
        val bookId = data.loadBook("book", "Old preface\n* Note A\n")
        val controller = openEditor(bookId)

        editor(controller).view!!.findViewById<RichText>(R.id.fragment_book_preface_content)
            .setSourceText("New preface")
        clickMenuItem(controller, R.id.done)

        assertEquals("New preface", data.dataRepository.getBook(bookId)?.preface)
        assertDisplayed(controller, BookFragment::class.java)

        controller.pause().stop().destroy()
    }

    @Test
    fun testDelete() {
        val bookId = data.loadBook("book", "Old preface\n* Note A\n")
        val controller = openEditor(bookId)

        clickMenuItem(controller, R.id.delete)

        assertEquals("", data.dataRepository.getBook(bookId)?.preface)
        assertDisplayed(controller, BookFragment::class.java)

        controller.pause().stop().destroy()
    }

    @Test
    fun testCancel() {
        val bookId = data.loadBook("book", "Old preface\n* Note A\n")
        val controller = openEditor(bookId)

        editor(controller).view!!.findViewById<RichText>(R.id.fragment_book_preface_content)
            .setSourceText("New preface")
        val toolbar = editor(controller).view!!.findViewById<Toolbar>(R.id.top_toolbar)
        toolbar.children.filterIsInstance<ImageButton>().first().performClick()
        idle()

        assertEquals("Old preface", data.dataRepository.getBook(bookId)?.preface)
        assertDisplayed(controller, BookFragment::class.java)

        controller.pause().stop().destroy()
    }

    @Test
    fun testFailedSaveKeepsTextAndCanBeRetried() {
        val bookId = data.loadBook("book", "Old preface\n* Note A\n")
        val controller = openEditor(bookId)
        val context = ApplicationProvider.getApplicationContext<Context>()

        try {
            editor(controller).view!!.findViewById<RichText>(R.id.fragment_book_preface_content)
                .setSourceText("New preface")

            // Reject the database write without changing the editor or its ViewModel.
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(OrgzlyDatabase.NAME).path,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { database ->
                database.execSQL("""
                    CREATE TRIGGER reject_preface_update BEFORE UPDATE OF preface ON books
                    BEGIN SELECT RAISE(ABORT, 'Preface save failed'); END
                """.trimIndent())
                try {
                    clickMenuItem(controller, R.id.done)

                    assertDisplayed(controller, BookPrefaceFragment::class.java)
                    assertEquals("Old preface", data.dataRepository.getBook(bookId)?.preface)
                    assertEquals("New preface", editor(controller).view!!
                        .findViewById<RichText>(R.id.fragment_book_preface_content).getSourceText().toString())
                    val snackbar = controller.get().findViewById<TextView>(
                        com.google.android.material.R.id.snackbar_text)
                    assertTrue(snackbar.text.contains("Preface save failed"))
                } finally {
                    database.execSQL("DROP TRIGGER reject_preface_update")
                }
            }

            clickMenuItem(controller, R.id.done)

            assertEquals("New preface", data.dataRepository.getBook(bookId)?.preface)
            assertDisplayed(controller, BookFragment::class.java)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun testRecreateWhileSavingClosesEditorOnce() {
        val bookId = data.loadBook("book", "Old preface\n* Note A\n")
        val controller = openEditor(bookId)
        val executors = App.EXECUTORS
        val diskTasks = ArrayDeque<Runnable>()
        App.EXECUTORS = AppExecutors(
            Executor { diskTasks.addLast(it) }, executors.networkIO(), executors.mainThread())

        try {
            val viewModel = ViewModelProvider(editor(controller))[BookPrefaceViewModel::class.java]
            val events = mutableListOf<BookPrefaceEvent>()
            viewModel.events.observeForever { events.add(it) }
            editor(controller).view!!.findViewById<RichText>(R.id.fragment_book_preface_content)
                .setSourceText("New preface")
            clickMenuItem(controller, R.id.done)

            assertEquals("Old preface", data.dataRepository.getBook(bookId)?.preface)
            assertDisplayed(controller, BookPrefaceFragment::class.java)

            controller.recreate()
            idle()

            clickMenuItem(controller, R.id.done)

            while (diskTasks.isNotEmpty()) {
                diskTasks.removeFirst().run()
                idle()
            }

            assertEquals("New preface", data.dataRepository.getBook(bookId)?.preface)
            assertEquals(listOf(BookPrefaceEvent.PrefaceUpdated), events)
            assertDisplayed(controller, BookFragment::class.java)
        } finally {
            App.EXECUTORS = executors
            controller.pause().stop().destroy()
        }
    }

    private fun openEditor(bookId: Long): ActivityController<MainActivity> {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()

        val fragmentManager = controller.get().supportFragmentManager
        DisplayManager.displayBook(fragmentManager, bookId, 0)
        idle()
        DisplayManager.displayEditor(fragmentManager, data.dataRepository.getBook(bookId)!!)
        idle()

        assertDisplayed(controller, BookPrefaceFragment::class.java)

        return controller
    }

    private fun editor(controller: ActivityController<MainActivity>) =
        controller.get().supportFragmentManager.findFragmentByTag(BookPrefaceFragment.FRAGMENT_TAG)!!

    private fun clickMenuItem(controller: ActivityController<MainActivity>, id: Int) {
        val toolbar = editor(controller).view!!.findViewById<Toolbar>(R.id.top_toolbar)
        assertTrue(toolbar.menu.performIdentifierAction(id, 0))
        idle()
    }

    private fun assertDisplayed(controller: ActivityController<MainActivity>, type: Class<*>) {
        val fragment = controller.get().supportFragmentManager.findFragmentById(R.id.single_pane_container)
        assertEquals(type, fragment?.javaClass)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
