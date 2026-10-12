package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.DottedQueryParser
import com.orgzly.android.ui.NoteStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The whole path: a notebook declaring a workflow, its notes parsed with it, and a search
 * resolving done-ness against it. Each piece was unit-tested in isolation and the combination
 * still did not work, so the combination is what this asserts.
 */
class BookWorkflowTest : OrgzlyTest() {

    @Before
    override fun setUp() {
        super.setUp()
        AppPreferences.states(context, "TODO NEXT | DONE")
    }

    private fun search(query: String): List<String> =
        dataRepository.selectNotesFromQuery(DottedQueryParser().parse(query))
            .map { it.note.title }

    private fun noteIds(vararg titles: String): Set<Long> =
        titles.map { dataRepository.getLastNote(it)!!.id }.toSet()

    @Test
    fun testStateDeclaredByTheNotebookIsParsed() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        val note = dataRepository.getLastNote("Abandoned")!!
        assertEquals("CNCL", note.state)
        assertEquals("Abandoned", note.title)
    }

    @Test
    fun testSearchFindsAStateDeclaredByTheNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n* NEXT Ongoing\n")

        assertEquals(listOf("Abandoned"), search("it.done"))
        assertEquals(listOf("Ongoing"), search("it.todo"))
    }

    /** Replacement: the app's DONE is not a done state inside a notebook declaring its own. */
    @Test
    fun testSearchDoesNotUseConfiguredStatesForADeclaringNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* DONE Not a state here\n")

        assertTrue(search("it.done").isEmpty())
    }

    @Test
    fun testSearchStillUsesConfiguredStatesElsewhere() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")
        testUtils.setupBook("book-b", "* DONE Finished\n")

        assertEquals(setOf("Abandoned", "Finished"), search("it.done").toSet())
    }

    // --- which notebook is in scope for a selection ---

    @Test
    fun testNotesFromSeveralNotebooksOfferTheStatesTheyShare() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL DONE\n\n* CNCL One\n")
        testUtils.setupBook("book-b", "* DONE Two\n")

        val states = NoteStates.fromBooks(context, dataRepository.getBookPrefaces(noteIds("One", "Two")))

        assertEquals(listOf("NEXT", "DONE"), states.array.toList())
    }
}
