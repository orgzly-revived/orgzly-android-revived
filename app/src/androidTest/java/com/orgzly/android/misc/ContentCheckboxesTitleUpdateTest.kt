package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.Note
import com.orgzly.android.ui.note.NotePayload
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * There are multiple ways of changing the content of a note via the DataRepository, so this abstract base
 * class allows inheritors to test the same functionality calling the different methods to persist the
 * content changes.
 */
abstract class ContentCheckboxesTitleUpdateTestBase : OrgzlyTest() {
    abstract fun persistContentChange(note: Note, content: String)

    companion object {
        val notCheckboxes = listOf(
                "[ ] no bullet or number",
                "! - [ ] bullet or number should be first",
                "! 1. [ ] bullet or number should be first",
                "- ! [ ] checkbox should immediately follow bullet or number",
                "1. ! [ ] checkbox should immediately follow bullet or number",
                "- [f] checkbox should only contain space, hyphen or X",
                "1. [f] checkbox should only contain space, hyphen or X",
            )

        val uncheckedCheckboxes = listOf(
            "- [ ] First thing",
            "1. [ ] first thing"
        )

        val hyphenatedCheckboxes = listOf(
            "- [-] First thing",
            "1. [-] first thing"
        )

        val checkedCheckboxes = listOf(
            "- [X] First thing",
            "1. [X] first thing"
        )
    }

    @Test
    fun testExistingTitleDoesNotChangeWhenContentHasNoCheckboxes() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        persistContentChange(
            onlyNote,
            "there are no checkboxes in here\n" +
                    "none at all"
        )

        val exportedBook = exportBook(book)

        val expectedBook =
            "* Things to do [%] [/]\n\n" +
            "there are no checkboxes in here\n" +
            "none at all"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testExistingTitleDoesNotChangeWhenContentHasInvalidCheckboxes() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (notCheckbox in notCheckboxes) {
            persistContentChange(onlyNote, notCheckbox)

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [%] [/]\n\n$notCheckbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testAddingNewEmptyCheckboxUpdatesTitleCookies() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (checkbox in uncheckedCheckboxes) {
            persistContentChange(onlyNote, checkbox)

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [0%] [0/1]\n\n$checkbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testAddingNewHyphenatedCheckboxUpdatesTitleCookies() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (checkbox in hyphenatedCheckboxes) {
            persistContentChange(onlyNote, checkbox)

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [0%] [0/1]\n\n$checkbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testAddingNewCheckedCheckboxUpdatesTitleCookies() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (checkbox in checkedCheckboxes) {
            persistContentChange(onlyNote, checkbox)

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [100%] [1/1]\n\n$checkbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }
}

class ContentCheckboxesTitleUpdateViaUpdateNoteTest : ContentCheckboxesTitleUpdateTestBase() {
    override fun persistContentChange(note: Note, content: String) {
        dataRepository.updateNote(
            noteId = note.id,
            NotePayload(
                title = note.title,
                content = content
            )
        )
    }
}

class ContentCheckboxesTitleUpdateViaUpdateNoteContentTest : ContentCheckboxesTitleUpdateTestBase() {
    override fun persistContentChange(note: Note, content: String) {
        dataRepository.updateNoteContent(
            noteId = note.id,
            content = content
        )
    }
}