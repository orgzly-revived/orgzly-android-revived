package com.orgzly.android.misc

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.data.BookWorkflow
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.DottedQueryParser
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.NoteStates
import com.orgzly.android.ui.Place
import com.orgzly.android.usecase.NoteCopy
import com.orgzly.android.usecase.NotePaste
import com.orgzly.android.usecase.NoteRefile
import com.orgzly.android.usecase.UseCaseRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun stateOf(title: String) = dataRepository.getLastNote(title)!!.state

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

    // --- notes moving between notebooks ---

    @Test
    fun testRefiledNoteLosesAStateTheTargetDoesNotDeclare() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")
        val (target) = testUtils.setupBook("book-b", "* Target\n")

        UseCaseRunner.run(NoteRefile(noteIds("Abandoned"), NotePlace(target.id)))

        assertNull(stateOf("CNCL Abandoned"))
    }

    @Test
    fun testRefiledNoteGainsAStateTheTargetDeclares() {
        testUtils.setupBook("book-a", "* CNCL Abandoned\n")
        val (target) = testUtils.setupBook("book-b", "#+TODO: NEXT | CNCL\n\n* Target\n")

        UseCaseRunner.run(NoteRefile(noteIds("CNCL Abandoned"), NotePlace(target.id)))

        assertEquals("CNCL", stateOf("Abandoned"))
    }

    @Test
    fun testPastedNoteIsReadWithTheTargetsWorkflow() {
        val (source) = testUtils.setupBook("book-a", "* CNCL Abandoned\n")
        val (target) = testUtils.setupBook("book-b", "#+TODO: NEXT | CNCL\n\n* Target\n")

        UseCaseRunner.run(NoteCopy(source.id, noteIds("CNCL Abandoned")))
        UseCaseRunner.run(NotePaste(target.id, dataRepository.getLastNote("Target")!!.id, Place.BELOW))

        assertEquals("CNCL", stateOf("Abandoned"))
    }

    // --- which notebook is in scope for a selection ---

    @Test
    fun testOneNoteResolvesToItsOwnNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL Abandoned\n")

        val preface = dataRepository.getSharedBookPreface(noteIds("Abandoned"))

        assertEquals(listOf("CNCL"), BookWorkflow.doneKeywords(context, preface).toList())
    }

    @Test
    fun testSeveralNotesFromOneNotebookResolveToIt() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL One\n* NEXT Two\n")

        val preface = dataRepository.getSharedBookPreface(noteIds("One", "Two"))

        assertEquals(listOf("CNCL"), BookWorkflow.doneKeywords(context, preface).toList())
    }

    /** A mixed selection has no single workflow, so the app's states are the only answer. */
    @Test
    fun testNotesFromSeveralNotebooksResolveToNothing() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL\n\n* CNCL One\n")
        testUtils.setupBook("book-b", "* DONE Two\n")

        assertNull(dataRepository.getSharedBookPreface(noteIds("One", "Two")))
    }

    @Test
    fun testNotesFromSeveralNotebooksOfferTheStatesTheyShare() {
        testUtils.setupBook("book-a", "#+TODO: NEXT | CNCL DONE\n\n* CNCL One\n")
        testUtils.setupBook("book-b", "* DONE Two\n")

        val states = NoteStates.fromBooks(context, dataRepository.getBookPrefaces(noteIds("One", "Two")))

        assertEquals(listOf("NEXT", "DONE"), states.array.toList())
    }

    @Test
    fun testANotebookDeclaringNothingResolvesToTheConfiguredStates() {
        testUtils.setupBook("book-b", "* DONE Finished\n")

        val preface = dataRepository.getSharedBookPreface(noteIds("Finished"))

        assertEquals(listOf("DONE"), BookWorkflow.doneKeywords(context, preface).toList())
    }

    // --- the done button ---

    @Test
    fun testDoneUsesTheNotebooksDoneState() {
        testUtils.setupBook("book-a", "#+TODO: NEED | FIN\n\n* NEED Task\n")

        dataRepository.setNoteStateToDone(dataRepository.getLastNote("Task")!!.id)

        assertEquals("FIN", stateOf("Task"))
    }

    @Test
    fun testToggleUsesTheNotebooksStates() {
        testUtils.setupBook("book-a", "#+TODO: NEED | FIN\n\n* NEED Task\n")

        val id = dataRepository.getLastNote("Task")!!.id

        dataRepository.toggleNotesState(setOf(id))
        assertEquals("FIN", stateOf("Task"))

        dataRepository.toggleNotesState(setOf(id))
        assertEquals("NEED", stateOf("Task"))
    }

    /** The symptom: the app's first todo state leaking into a notebook that never declared it. */
    @Test
    fun testToggleDoesNotWriteAConfiguredStateIntoADeclaringNotebook() {
        testUtils.setupBook("book-a", "#+TODO: NEED | FIN\n\n* FIN Task\n")

        dataRepository.toggleNotesState(setOf(dataRepository.getLastNote("Task")!!.id))

        assertEquals("NEED", stateOf("Task"))
    }

    @Test
    fun testANotebookDeclaringNothingStillUsesTheConfiguredStates() {
        testUtils.setupBook("book-b", "* TODO Task\n")

        dataRepository.setNoteStateToDone(dataRepository.getLastNote("Task")!!.id)

        assertEquals("DONE", stateOf("Task"))
    }

    /** Each notebook is toggled against its own workflow, not one answer for the batch. */
    @Test
    fun testTogglingAcrossNotebooksUsesEachOwnStates() {
        testUtils.setupBook("book-a", "#+TODO: NEED | FIN\n\n* NEED One\n")
        testUtils.setupBook("book-b", "* TODO Two\n")

        dataRepository.toggleNotesState(noteIds("One", "Two"))

        assertEquals("FIN", stateOf("One"))
        assertEquals("DONE", stateOf("Two"))
    }

    @Test
    fun testSettingAStateAcrossNotebooksClosesOnlyWhereItIsDone() {
        val todoBook = testUtils.setupBook("book-a", "#+TODO: TODO WAIT | DONE\n\n* TODO One\n")
        val doneBook = testUtils.setupBook("book-b", "#+TODO: TODO | WAIT DONE\n\n* TODO Two\n")

        dataRepository.setNotesState(noteIds("One", "Two"), "WAIT")

        assertFalse(exportBook(todoBook).contains("CLOSED:"))
        assertTrue(exportBook(doneBook).contains("CLOSED:"))
    }

    /** Marking done has to close the note even when only the file knows the state is done. */
    @Test
    fun testDoneClosesTheNoteForADeclaredState() {
        val book = testUtils.setupBook("book-a", "#+TODO: NEED | FIN\n\n* NEED Task\n")

        dataRepository.setNoteStateToDone(dataRepository.getLastNote("Task")!!.id)

        assertTrue(exportBook(book).contains("CLOSED:"))
    }
}
