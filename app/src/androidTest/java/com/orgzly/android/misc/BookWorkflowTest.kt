package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.NoteStates
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BookWorkflowTest : OrgzlyTest() {

    @Before
    override fun setUp() {
        super.setUp()
        AppPreferences.states(context, "TODO NEXT | DONE")
    }

    private fun noteIds(vararg titles: String): Set<Long> =
        titles.map { dataRepository.getLastNote(it)!!.id }.toSet()

    // --- which notebook is in scope for a selection ---

    @Test
    fun testNotesFromSeveralNotebooksOfferTheStatesTheyShare() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL DONE\n\n* CNCL One\n")
        testUtils.setupBook("book-b", "* DONE Two\n")

        val states = NoteStates.fromBooks(context, dataRepository.getBookPrefaces(noteIds("One", "Two")))

        assertEquals(listOf("NEXT", "DONE"), states.array.toList())
    }
}
