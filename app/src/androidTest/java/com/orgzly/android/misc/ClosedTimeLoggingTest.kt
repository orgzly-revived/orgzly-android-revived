package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.prefs.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Test

/** Whether marking a note done records its closed time, end to end. */
class ClosedTimeLoggingTest : OrgzlyTest() {

    private fun markDone(title: String) {
        val note = dataRepository.getLastNote(title)!!
        dataRepository.setNotesState(setOf(note.id), "DONE")
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

    /** Org matches in-buffer keyword names case-insensitively too. */
    @Test
    fun testStartupKeywordIsCaseInsensitive() {
        AppPreferences.logDone(context, true)

        val book = testUtils.setupBook("book-a", "#+startup: nologdone\n\n* TODO Task\n")

        markDone("Task")

        assertFalse(exportBook(book).contains("CLOSED:"))
    }
}
