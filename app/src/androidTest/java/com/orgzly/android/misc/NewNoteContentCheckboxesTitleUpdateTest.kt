package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.note.NotePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class NewNoteContentCheckboxesTitleUpdateTest : OrgzlyTest() {
    @Test
    fun testNewNoteTitleDoesNotChangeWhenContentHasNoCheckboxes() {
        val book = testUtils.setupBook("book-a", "")

        dataRepository.createNote(
            NotePayload(
                title = "Things to do [%] [/]",
                content =
                    "there are no checkboxes in here\n" +
                    "none at all",
            ),
            NotePlace(book.book.id)
        )

        val exportedBook = exportBook(book)

        val expectedBook =
            "* Things to do [%] [/]\n\n" +
            "there are no checkboxes in here\n" +
            "none at all"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testNewNoteTitleDoesNotChangeWhenContentHasInvalidCheckboxes() {
       for (notCheckbox in ContentCheckboxesTitleUpdateTestBase.notCheckboxes) {
            val book = testUtils.setupBook("book-a", "")

            dataRepository.createNote(
                NotePayload(
                    title = "Things to do [%] [/]",
                    content = notCheckbox
                ),
                NotePlace(book.book.id)
            )

            val exportedBook = exportBook(book)

            val expectedBook =
                "* Things to do [%] [/]\n\n" +
                notCheckbox

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testNewNoteWithEmptyCheckboxUpdatesTitleCookies() {
        for (checkbox in ContentCheckboxesTitleUpdateTestBase.uncheckedCheckboxes) {
            val book = testUtils.setupBook("book-a", "")

            dataRepository.createNote(
                NotePayload(
                    title = "Things to do [%] [/]",
                    content = checkbox
                ),
                NotePlace(book.book.id)
            )

            val exportedBook = exportBook(book)

            val expectedBook =
                "* Things to do [0%] [0/1]\n\n" +
                checkbox

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testNewNoteWithHyphenatedCheckboxUpdatesTitleCookies() {
        for (checkbox in ContentCheckboxesTitleUpdateTestBase.hyphenatedCheckboxes) {
            val book = testUtils.setupBook("book-a", "")

            dataRepository.createNote(
                NotePayload(
                    title = "Things to do [%] [/]",
                    content = checkbox
                ),
                NotePlace(book.book.id)
            )

            val exportedBook = exportBook(book)

            val expectedBook =
                "* Things to do [0%] [0/1]\n\n" +
                checkbox

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testNewNoteWithCheckedCheckboxUpdatesTitleCookies() {
        for (checkbox in ContentCheckboxesTitleUpdateTestBase.checkedCheckboxes) {
            val book = testUtils.setupBook("book-a", "")

            dataRepository.createNote(
                NotePayload(
                    title = "Things to do [%] [/]",
                    content = checkbox
                ),
                NotePlace(book.book.id)
            )

            val exportedBook = exportBook(book)

            val expectedBook =
                "* Things to do [100%] [1/1]\n\n" +
                checkbox

            assertEquals(expectedBook, exportedBook.trim())
        }
    }
}