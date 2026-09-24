package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.prefs.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether marking a note done records its closed time, end to end. */
class ClosedTimeLoggingTest : OrgzlyTest() {

    private fun markDone(title: String) {
        val note = dataRepository.getLastNote(title)!!
        dataRepository.setNotesState(setOf(note.id), "DONE")
    }

    @Test
    fun testPropertyOnTheNoteSuppressesClosedTime() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "* TODO Task\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: nil\n" +
                ":END:\n")

        markDone("Task")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testPropertyIsInheritedByDescendants() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "* Project\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: nil\n" +
                ":END:\n" +
                "** TODO Child\n")

        markDone("Child")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testNearestAncestorWins() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "* Project\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: nil\n" +
                ":END:\n" +
                "** TODO Child\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: logdone\n" +
                ":END:\n")

        markDone("Child")

        assertTrue(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testPropertyOverridesTheNotebookStartup() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "#+STARTUP: nologdone\n" +
                "\n" +
                "* TODO Task\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: logdone\n" +
                ":END:\n")

        markDone("Task")

        assertTrue(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testNotebookStartupAppliesWithoutTheProperty() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook("book-a", "#+STARTUP: nologdone\n\n* TODO Task\n")

        markDone("Task")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testSettingAppliesWithoutPropertyOrStartup() {
        AppPreferences.logDone(context, false)

        val book = testUtils.setupBook("book-a", "* TODO Task\n")

        markDone("Task")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testClosedTimeIsStillClearedWhenLoggingIsOff() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "* DONE Task\n" +
                "CLOSED: [2018-01-01 Mon 12:00]\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: nil\n" +
                ":END:\n")

        val note = dataRepository.getLastNote("Task")!!
        dataRepository.setNotesState(setOf(note.id), "TODO")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }

    @Test
    fun testClosedTimeIsKeptOnDoneToDoneWhenLoggingIsOff() {
        AppPreferences.states(context, "TODO | DONE CANCEL")
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "* DONE Task\n" +
                "CLOSED: [2018-01-01 Mon 12:00]\n" +
                ":PROPERTIES:\n" +
                ":LOGGING: nil\n" +
                ":END:\n")

        val note = dataRepository.getLastNote("Task")!!
        dataRepository.setNotesState(setOf(note.id), "CANCEL")

        assertTrue(exportBook(book).contains("CLOSED: [2018-01-01 Mon 12:00]"))
    }

    /** Org matches property names case-insensitively. */
    @Test
    fun testPropertyNameIsCaseInsensitive() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook(
            "book-a",
            "* TODO Task\n" +
                ":PROPERTIES:\n" +
                ":logging: nil\n" +
                ":END:\n")

        markDone("Task")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }

    /** Org matches in-buffer keyword names case-insensitively too. */
    @Test
    fun testStartupKeywordIsCaseInsensitive() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook("book-a", "#+startup: nologdone\n\n* TODO Task\n")

        markDone("Task")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }
}
