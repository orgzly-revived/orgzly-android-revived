package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.data.BookWorkflow
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.DottedQueryParser
import com.orgzly.android.ui.NoteStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    /** Setting a workflow has to re-read the notebook, or its notes keep their old parse. */
    @Test
    fun testSettingAWorkflowRereadsTheNotebook() {
        testUtils.setupBook("book-a", "* CNCL Abandoned\n")

        // Parsed with the app's states, so the keyword is part of the title.
        assertEquals("CNCL Abandoned", dataRepository.getLastNote("CNCL Abandoned")!!.title)

        val book = dataRepository.getBook("book-a")!!
        dataRepository.setBookPreface(
            book.id, BookWorkflow.withWorkflowInPreface(book.preface, "NEXT | CNCL"))

        val note = dataRepository.getLastNote("Abandoned")!!
        assertEquals("CNCL", note.state)
        assertEquals(listOf("Abandoned"), search("it.done"))
    }

    /** Clearing it must put the keyword back, not leave a state nothing declares. */
    @Test
    fun testClearingAWorkflowRereadsTheNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        val book = dataRepository.getBook("book-a")!!
        dataRepository.setBookPreface(
            book.id, BookWorkflow.withWorkflowInPreface(book.preface, null))

        assertEquals("CNCL Abandoned", dataRepository.getLastNote("CNCL Abandoned")!!.title)
        assertTrue(search("it.done").isEmpty())
    }

    /** An open search list keeps its query running, so a workflow edit must reach it. */
    @Test
    fun testRunningSearchFollowsAWorkflowEdit() = runBlocking {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n* NEXT Ongoing\n")

        val results = Channel<List<String>>(Channel.UNLIMITED)
        val collecting = launch(Dispatchers.Default) {
            dataRepository.selectNotesFromQueryFlow("it.done")
                .collect { notes -> results.send(notes.map { it.note.title }) }
        }

        withTimeout(5000) { assertEquals(listOf("Abandoned"), results.receive()) }

        val book = dataRepository.getBook("book-a")!!
        dataRepository.setBookPreface(
            book.id, BookWorkflow.withWorkflowInPreface(book.preface, "CNCL | NEXT"))

        withTimeout(5000) {
            while (results.receive() != listOf("Ongoing")) Unit
        }

        collecting.cancel()
    }

    /**
     * The global reparse runs when the states setting changes and from background work. It
     * must not flatten a notebook that declares its own workflow.
     */
    @Test
    fun testGlobalReparseKeepsStatesDeclaredByANotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        dataRepository.reParseNotesStateAndTitles()

        val note = dataRepository.getLastNote("Abandoned")!!
        assertEquals("CNCL", note.state)
        assertEquals("Abandoned", note.title)
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
