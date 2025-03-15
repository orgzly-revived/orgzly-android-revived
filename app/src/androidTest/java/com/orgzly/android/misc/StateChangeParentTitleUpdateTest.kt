package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.note.NotePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class StateChangeParentTitleUpdateTest : OrgzlyTest() {
    @Test
    fun testFinishingTodoItemUpdatesTitleCookiesIfPresent() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%] [/]\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** TODO Write integration tests for same")

        val secondNote = dataRepository.getLastNote("Write integration tests for same")!!

        dataRepository.updateNote(
            noteId = secondNote.id,
            NotePayload(
                title = secondNote.title,
                state = "DONE"
            )
        )

        val exportedBook = exportBook(book)

        val expectedBook = "* Things to do [100%] [2/2]\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** DONE Write integration tests for same"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testUnfinishingDoneItemUpdatesTitleCookiesIfPresent() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [100%] [2/2]\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** DONE Write integration tests for same")

        val secondNote = dataRepository.getLastNote("Write integration tests for same")!!

        dataRepository.updateNote(
            noteId = secondNote.id,
            NotePayload(
                title = secondNote.title,
                state = "TODO"
            )
        )

        val exportedBook = exportBook(book)

        val expectedBook = "* Things to do [50%] [1/2]\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** TODO Write integration tests for same"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testTitleIsNotChangedWhenCookiesAreNotPresent() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** TODO Write integration tests for same")

        val secondNote = dataRepository.getLastNote("Write integration tests for same")!!

        dataRepository.updateNote(
            noteId = secondNote.id,
            NotePayload(
                title = secondNote.title,
                state = "DONE"
            )
        )

        val exportedBook = exportBook(book)

        val expectedBook = "* Things to do\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** DONE Write integration tests for same"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testTitleIsNotChangedWhenTotalsHaveNotChanged() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [100%] [2/2]\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** DONE Write integration tests for same")

        val secondNote = dataRepository.getLastNote("Write integration tests for same")!!

        dataRepository.updateNote(
            noteId = secondNote.id,
            NotePayload(
                title = "This is a whole new title",
                state = secondNote.state
            )
        )

        val exportedBook = exportBook(book)

        val expectedBook = "* Things to do [100%] [2/2]\n" +
            "*** DONE Add percentage and fraction cookie handling for titles\n" +
            "*** DONE This is a whole new title"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testTitleWithNewPercentageCookieIsUpdatedWhenChildItemAdded() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [%]")

        val parentNote = dataRepository.getLastNote("Things to do [%]")!!

        dataRepository.createNote(
            NotePayload(
                title = "This is a new child TODO",
                state = "TODO"),
            NotePlace(
                book.book.id,
                parentNote.id,
                Place.UNDER)
        )

        val exportedBook = exportBook(book)

        val expectedBook = "* Things to do [0%]\n" +
            "** TODO This is a new child TODO"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testTitleWithNewFractionCookieIsUpdatedWhenChildItemAdded() {
        val book = testUtils.setupBook(
            "book-a",
            "* Things to do [/]")

        val parentNote = dataRepository.getLastNote("Things to do [/]")!!

        dataRepository.createNote(
            NotePayload(
                title = "This is a new child TODO",
                state = "TODO"),
            NotePlace(
                book.book.id,
                parentNote.id,
                Place.UNDER)
        )

        val exportedBook = exportBook(book)

        val expectedBook = "* Things to do [0/1]\n" +
            "** TODO This is a new child TODO"

        assertEquals(expectedBook, exportedBook.trim())
    }

    @Test
    fun testTitleWithIncorrectPercentageCookieIsUpdated() {
        val testValues = listOf("0%", "1%", "100%", "1000%")

        for (value in testValues) {
            val book = testUtils.setupBook(
                "book-a",
                "* Things to do [$value]\n" +
                "*** DONE Add percentage and fraction cookie handling for titles\n" +
                "*** DONE Write integration tests for same")

            val secondNote = dataRepository.getLastNote("Write integration tests for same")!!

            dataRepository.updateNote(
                noteId = secondNote.id,
                NotePayload(
                    title = secondNote.title,
                    state = "TODO"
                )
            )

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [50%]\n" +
                "*** DONE Add percentage and fraction cookie handling for titles\n" +
                "*** TODO Write integration tests for same"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }

    @Test
    fun testTitleWithIncorrectFractionCookieIsUpdated() {
        val testValues = listOf("0/2", "2/2", "2/1", "200/100", "2/", "/1")

        for (value in testValues) {
            val book = testUtils.setupBook(
                "book-a",
                "* Things to do [$value]\n" +
                "*** DONE Add percentage and fraction cookie handling for titles\n" +
                "*** DONE Write integration tests for same")

            val secondNote = dataRepository.getLastNote("Write integration tests for same")!!

            dataRepository.updateNote(
                noteId = secondNote.id,
                NotePayload(
                    title = secondNote.title,
                    state = "TODO"
                )
            )

            val exportedBook = exportBook(book)

            val expectedBook = "* Things to do [1/2]\n" +
                "*** DONE Add percentage and fraction cookie handling for titles\n" +
                "*** TODO Write integration tests for same"

            assertEquals(expectedBook, exportedBook.trim())
        }
    }
}