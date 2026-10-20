package com.orgzly.android.ui.notes.book

import android.os.Looper
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import com.orgzly.R
import com.orgzly.android.AppDataRule
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
