package com.orgzly.android.ui.notes.book

import android.os.Looper
import androidx.lifecycle.LiveData
import com.orgzly.android.AppDataRule
import com.orgzly.android.ui.Place
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs the use cases for real, against the app's database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookViewModelTest {
    @get:Rule
    val data = AppDataRule()

    @Test
    fun testCutAndPaste() {
        val viewModel = BookViewModel(data.dataRepository, loadBook("* Note A\n* Note B\n"))
        val changes = viewModel.clipboardChangeEvent.collected()

        viewModel.cutNotes(setOf(note("Note A")))
        idle()
        viewModel.pasteNotes(note("Note B"), Place.BELOW)
        idle()

        assertEquals(
            listOf(BookViewModel.ClipboardChange.Cut(1), BookViewModel.ClipboardChange.Pasted(1)),
            changes)
        assertEquals(listOf("Note B", "Note A"), titles())
    }

    @Test
    fun testPasteWithEmptyClipboard() {
        val viewModel = BookViewModel(data.dataRepository, loadBook("* Note A\n"))
        val changes = viewModel.clipboardChangeEvent.collected()

        viewModel.pasteNotes(note("Note A"), Place.BELOW)
        idle()

        assertEquals(listOf(BookViewModel.ClipboardChange.Pasted(0)), changes)
        assertEquals(listOf("Note A"), titles())
    }

    @Test
    fun testCopy() {
        val viewModel = BookViewModel(data.dataRepository, loadBook("* Note A\n** Note A.1\n* Note B\n"))
        val changes = viewModel.clipboardChangeEvent.collected()

        viewModel.copyNotes(setOf(note("Note A")))
        idle()

        assertEquals(listOf(BookViewModel.ClipboardChange.Copied(2)), changes)
        assertEquals(listOf("Note A", "Note A.1", "Note B"), titles())
    }

    @Test
    fun testDelete() {
        val viewModel = BookViewModel(data.dataRepository, loadBook("* Note A\n* Note B\n"))

        viewModel.deleteNotes(setOf(note("Note A")))

        assertEquals(listOf("Note B"), titles())
    }

    @Test
    fun testDemoteAndPromote() {
        val viewModel = BookViewModel(data.dataRepository, loadBook("* Note A\n* Note B\n"))

        viewModel.demoteNotes(setOf(note("Note B")))
        assertEquals(2, level("Note B"))

        viewModel.promoteNotes(setOf(note("Note B")))
        assertEquals(1, level("Note B"))
    }

    @Test
    fun testMove() {
        val viewModel = BookViewModel(data.dataRepository, loadBook("* Note A\n* Note B\n"))

        viewModel.moveNotes(setOf(note("Note B")), -1)

        assertEquals(listOf("Note B", "Note A"), titles())
    }

    @Test
    fun testUpdatePreface() {
        val bookId = loadBook("Old preface\n* Note A\n")
        val viewModel = BookViewModel(data.dataRepository, bookId)

        viewModel.updatePreface("New preface")

        assertEquals("New preface", data.dataRepository.getBook(bookId)?.preface)
    }

    private fun loadBook(content: String) = data.loadBook("book", content)

    private fun note(title: String) = data.noteId(title)

    private fun titles() = data.dataRepository.getNotes("book").map { it.note.title }

    private fun level(title: String) = data.dataRepository.getLastNote(title)!!.position.level

    private fun <T> LiveData<T>.collected(): MutableList<T> {
        val values = mutableListOf<T>()
        observeForever { values.add(it) }
        return values
    }

    /** Delivers posted events. */
    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
