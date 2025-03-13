package com.orgzly.android.util

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.ui.note.NotePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentChangeTitleUpdateTest : OrgzlyTest() {
    @Test
    fun testTitleDoesNotChangeWhenContentHasNoCheckboxes() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        dataRepository.updateNote(
            noteId = onlyNote.id,
            NotePayload(
                title = onlyNote.title,
                content =
                    "there are no checkboxes in here\n" +
                    "no siree Bob"
            )
        )

        val exportedBook = exportBook(book)

        val expectedBook =
            "* Things to do [%] [/]\n\n" +
            "there are no checkboxes in here\n" +
            "no siree Bob"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testTitleDoesNotChangeWhenContentHasInvalidCheckboxes() {
        val notCheckboxes = listOf(
            "[ ] no bullet or number",
            "! - [ ] bullet or number should be first",
            "! 1. [ ] bullet or number should be first",
            "- ! [ ] checkbox should immediately follow bullet or number",
            "1. ! [ ] checkbox should immediately follow bullet or number",
            "- [f] checkbox should only contain space, hyphen or X",
            "1. [f] checkbox should only contain space, hyphen or X",
        )

        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (notCheckbox in notCheckboxes) {
            dataRepository.updateNote(
                noteId = onlyNote.id,
                NotePayload(
                    title = onlyNote.title,
                    content = notCheckbox
                )
            )

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [%] [/]\n\n$notCheckbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testAddingNewEmptyCheckboxUpdatesTitleCookies() {
        val checkboxes = listOf(
            "- [ ] First thing",
            "1. [ ] first thing"
        )
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (checkbox in checkboxes) {
            dataRepository.updateNote(
                noteId = onlyNote.id,
                NotePayload(
                    title = onlyNote.title,
                    content = checkbox
                )
            )

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [0%] [0/1]\n\n$checkbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testAddingNewHyphenatedCheckboxUpdatesTitleCookies() {
        val checkboxes = listOf(
            "- [-] First thing",
            "1. [-] first thing"
        )
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (checkbox in checkboxes) {
            dataRepository.updateNote(
                noteId = onlyNote.id,
                NotePayload(
                    title = onlyNote.title,
                    content = checkbox
                )
            )

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [0%] [0/1]\n\n$checkbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testAddingNewCheckedCheckboxUpdatesTitleCookies() {
        val checkboxes = listOf(
            "- [X] First thing",
            "1. [X] first thing"
        )
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]")

        val onlyNote = dataRepository.getLastNote("Things to do [%] [/]")!!

        for (checkbox in checkboxes) {
            dataRepository.updateNote(
                noteId = onlyNote.id,
                NotePayload(
                    title = onlyNote.title,
                    content = checkbox
                )
            )

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [100%] [1/1]\n\n$checkbox"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }
}