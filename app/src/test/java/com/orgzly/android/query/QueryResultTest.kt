package com.orgzly.android.query

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.BookFormat
import com.orgzly.android.LocalStorage
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.repos.RepoFactory
import com.orgzly.android.util.MiscUtils
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Unit tests for query results, migrated from QueryFragmentTest.java.
 * These tests verify query parsing, SQL generation, and result correctness
 * without requiring UI or Espresso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueryResultTest {

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var database: OrgzlyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Set up in-memory database for tests
        database = OrgzlyDatabase.forMemory(context)

        val dbRepoBookRepository = DbRepoBookRepository(database)
        val localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        dataRepository = DataRepository(
            context, database, repoFactory, context.resources, localStorage)

        // Set default preferences for tests
        AppPreferences.states(context, "TODO NEXT | DONE")
        AppPreferences.defaultPriority(context, "B")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun setupBook(name: String, content: String): BookView {
        // Use the same approach as TestUtils.setupBook() - write content to temp file
        // then load it using dataRepository.loadBookFromFile()
        val tmpFile = dataRepository.getTempBookFile()
        try {
            MiscUtils.writeStringToFile(content, tmpFile)
            return dataRepository.loadBookFromFile(name, BookFormat.ORG, tmpFile, null)!!
        } catch (e: IOException) {
            throw RuntimeException("Failed to setup book: $name", e)
        } finally {
            tmpFile.delete()
        }
    }

    private fun searchNotes(queryString: String): List<NoteView> {
        val parser = InternalQueryParser()
        val parsedQuery = parser.parse(queryString)
        return dataRepository.selectNotesFromQuery(parsedQuery)
    }

    @Test
    fun testSearchExpressionTodo() {
        setupBook("book-one",
            """First book used for testing
                |* Note A.
                |** [#A] Note B.
                |* TODO Note C.
                |SCHEDULED: <2014-01-01>
                |** Note D.
                |*** TODO Note E.
                |*** Same title in different notebooks.
                |*** Another note.
                |""".trimMargin())

        setupBook("book-two",
            """Sample book used for tests
                |* Note #1.
                |* Note #2.
                |** TODO Note #3.
                |** Note #4.
                |*** DONE Note #5.
                |CLOSED: [2014-06-03 Tue 13:34]
                |**** Note #6.
                |** Note #7.
                |* DONE Note #8.
                |CLOSED: [2014-06-03 Tue 3:34]
                |**** Note #9.
                |SCHEDULED: <2014-05-26 Mon>
                |** Note #10.
                |** Same title in different notebooks.
                |** Note #11.
                |** Note #12.
                |** Note #13.
                |DEADLINE: <2014-05-26 Mon>
                |** Note #14.
                |** [#A] Note #15.
                |** [#A] Note #16.
                |** [#B] Note #17.
                |** [#C] Note #18.
                |** Note #19.
                |** Note #20.
                |** Note #21.
                |** Note #22.
                |** Note #23.
                |** Note #24.
                |** Note #25.
                |** Note #26.
                |** Note #27.
                |** Note #28.
                |""".trimMargin())

        val results = searchNotes("i.todo")

        assertThat(results.size, `is`(3))
    }

    @Test
    fun testSearchExpressionsPriority() {
        setupBook("book-one",
            """First book used for testing
                |* Note A.
                |** [#A] Note B.
                |* TODO Note C.
                |SCHEDULED: <2014-01-01>
                |** Note D.
                |*** TODO Note E.
                |""".trimMargin())

        setupBook("book-two",
            """Sample book used for tests
                |* Note #1.
                |** [#A] Note #15.
                |** [#A] Note #16.
                |** [#B] Note #17.
                |** [#C] Note #18.
                |""".trimMargin())

        val results = searchNotes("p.a")

        assertThat(results.size, `is`(3))
        assertThat(results[0].note.title, containsString("Note B"))
        assertThat(results[1].note.title, containsString("Note #15"))
        assertThat(results[2].note.title, containsString("Note #16"))
    }

    @Test
    fun testNotPriority() {
        setupBook("book-one",
            """First book used for testing
                |* Note A.
                |** [#A] Note B.
                |""".trimMargin())

        setupBook("book-two",
            """Sample book used for tests
                |* Note #1.
                |** [#A] Note #15.
                |** [#A] Note #16.
                |** [#B] Note #17.
                |** [#C] Note #18.
                |""".trimMargin())

        val results = searchNotes(".p.b")

        assertThat(results.size, `is`(4))
        assertThat(results[0].note.title, containsString("Note B"))
        assertThat(results[1].note.title, containsString("Note #15"))
        assertThat(results[2].note.title, containsString("Note #16"))
        assertThat(results[3].note.title, containsString("Note #18"))
    }

    @Test
    fun testSearchInBook() {
        setupBook("book-one",
            """First book used for testing
                |* Note A.
                |** [#A] Note B.
                |* TODO Note C.
                |SCHEDULED: <2014-01-01>
                |** Note D.
                |*** TODO Note E.
                |*** Same title in different notebooks.
                |*** Another note.
                |""".trimMargin())

        setupBook("book-two",
            """Sample book used for tests
                |* Note #1.
                |** Note #2.
                |""".trimMargin())

        val results = searchNotes("b.book-one note")

        assertThat(results.size, `is`(7))
    }

    @Test
    fun testSearchForNonExistentTagShouldReturnAllNotes() {
        setupBook("notebook",
            """* Note A :a:
                |** Note B :b:
                |*** Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes(".t.c")

        assertThat(results.size, `is`(4))
    }

    @Test
    fun testNotTagShouldReturnSomeNotes() {
        setupBook("notebook",
            """* Note A :a:
                |** Note B :b:
                |*** Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes(".t.b")

        assertThat(results.size, `is`(2))
        assertThat(results[0].note.title, containsString("Note A"))
        assertThat(results[1].note.title, containsString("Note D"))
    }

    @Test
    fun testSearchForTagOrTag() {
        setupBook("notebook",
            """* Note A :a:
                |** Note B :b:
                |*** Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes("tn.a or tn.b")

        assertThat(results.size, `is`(2))
        assertThat(results[0].note.title, startsWith("Note A"))
        assertThat(results[1].note.title, startsWith("Note B"))
    }

    @Test
    fun testSortByPriority() {
        setupBook("notebook",
            """* [#B] Note A :a:
                |** [#A] Note B :b:
                |*** [#C] Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes("o.p")

        assertThat(results.size, `is`(4))
        assertThat(results[0].note.title, containsString("Note B"))
        assertThat(results[1].note.title, containsString("Note A"))
        assertThat(results[2].note.title, containsString("Note D"))
        assertThat(results[3].note.title, containsString("Note C"))
    }

    @Test
    fun testSortByPriorityDesc() {
        setupBook("notebook",
            """* [#B] Note A :a:
                |** [#A] Note B :b:
                |*** [#C] Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes(".o.p")

        assertThat(results.size, `is`(4))
        assertThat(results[0].note.title, containsString("Note C"))
        assertThat(results[1].note.title, containsString("Note D"))
        assertThat(results[2].note.title, containsString("Note A"))
        assertThat(results[3].note.title, containsString("Note B"))
    }

    @Test
    fun testSearchOrderScheduled() {
        setupBook("notebook-1",
            """* Note A
                |SCHEDULED: <2014-02-01>
                |** Note B
                |SCHEDULED: <2014-01-01>
                |*** Note C
                |*** Note D
                |""".trimMargin())

        val results = searchNotes("note o.scheduled")

        assertTrue("Expected at least 2 results, got ${results.size}", results.size >= 2)
        assertThat(results[0].note.title, `is`("Note B"))
        assertThat(results[1].note.title, `is`("Note A"))
    }

    @Test
    fun testOrderScheduledWithAndWithoutTimePart() {
        setupBook("notebook-1",
            """* Note A
                |SCHEDULED: <2014-01-01>
                |** Note B
                |SCHEDULED: <2014-01-02>
                |*** Note C
                |SCHEDULED: <2014-01-02 10:00>
                |*** DONE Note D
                |SCHEDULED: <2014-01-03>
                |""".trimMargin())

        val results = searchNotes("s.today .i.done o.s")

        assertThat(results.size, `is`(3))
        assertThat(results[0].note.title, `is`("Note A"))
        assertThat(results[1].note.title, `is`("Note C"))
        assertThat(results[2].note.title, `is`("Note B"))
    }

    @Test
    fun testSearchNoteStateType() {
        AppPreferences.states(context, "TODO NEXT | DONE")
        setupBook("notebook",
            """* TODO Note A :a:
                |** NEXT Note B :b:
                |* DONE Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes(".it.todo")

        assertThat(results.size, `is`(2))
        assertThat(results[0].note.title, containsString("Note C"))
        assertThat(results[1].note.title, containsString("Note D"))
    }

    @Test
    fun testSearchStateType() {
        AppPreferences.states(context, "TODO NEXT | DONE")
        setupBook("notebook",
            """* TODO Note A :a:
                |** NEXT Note B :b:
                |* DONE Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes("it.todo")

        assertThat(results.size, `is`(2))
        assertThat(results[0].note.title, containsString("Note A"))
        assertThat(results[1].note.title, containsString("Note B"))
    }

    @Test
    fun testSearchNoState() {
        AppPreferences.states(context, "TODO NEXT | DONE")
        setupBook("notebook",
            """* TODO Note A :a:
                |** NEXT Note B :b:
                |* DONE Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes("it.none")

        assertThat(results.size, `is`(1))
        assertThat(results[0].note.title, containsString("Note D"))
    }

    @Test
    fun testSearchWithState() {
        AppPreferences.states(context, "TODO NEXT | DONE")
        setupBook("notebook",
            """* TODO Note A :a:
                |** NEXT Note B :b:
                |* DONE Note C
                |* Note D
                |""".trimMargin())

        val results = searchNotes(".it.none")

        assertThat(results.size, `is`(3))
        assertThat(results[0].note.title, containsString("Note A"))
        assertThat(results[1].note.title, containsString("Note B"))
        assertThat(results[2].note.title, containsString("Note C"))
    }

    /**
     * Added after a bug when using insertWithOnConflict for timestamps,
     * due to https://code.google.com/p/android/issues/detail?id=13045
     */
    @Test
    fun testNotesWithSameScheduledTimeString() {
        setupBook("notebook-1", "* Note A\nSCHEDULED: <2014-01-01>")
        setupBook("notebook-2", "* Note B\nSCHEDULED: <2014-01-01>")

        val results = searchNotes("s.today")

        assertThat(results.size, `is`(2))
    }

    @Test
    fun testNotesWithSameDeadlineTimeString() {
        setupBook("notebook-1", "* Note A\nDEADLINE: <2014-01-01>")
        setupBook("notebook-2", "* Note B\nDEADLINE: <2014-01-01>")

        val results = searchNotes("d.today")

        assertThat(results.size, `is`(2))
    }

    @Test
    fun testInheritedTagSearchWhenMultipleAncestorsMatch() {
        setupBook("notebook-1",
            """* Note A :tagtag:
                |** Note B :tag:
                |*** Note C
                |*** Note D
                |""".trimMargin())

        val results = searchNotes("t.tag")

        assertThat(results.size, `is`(4))
    }

    @Test
    fun testInheritedAndOwnTag() {
        setupBook("notebook-1",
            """* Note A :tag1:
                |** Note B :tag2:
                |*** Note C
                |*** Note D
                |""".trimMargin())

        val results = searchNotes("t.tag1 t.tag2")

        assertThat(results.size, `is`(3))
        assertThat(results[0].note.title, startsWith("Note B"))
        assertThat(results[1].note.title, startsWith("Note C"))
        assertThat(results[2].note.title, startsWith("Note D"))
    }

    @Test
    fun testInactiveScheduled() {
        setupBook("notebook-1", "* Note A\nSCHEDULED: [2020-07-01]")

        val results = searchNotes("s.le.today")

        assertThat(results.size, `is`(0))
    }

    @Test
    fun testInactiveDeadline() {
        setupBook("notebook-1", "* Note A\nDEADLINE: [2020-07-01]")

        val results = searchNotes("d.le.today")

        assertThat(results.size, `is`(0))
    }

    @Test
    fun testNotScheduled() {
        setupBook("notebook-1", "* Note A")

        val results = searchNotes("s.no")

        assertThat(results.size, `is`(1))
    }

    @Test
    fun testMultipleNotState() {
        setupBook("notebook-1",
            """* Note A.
                |** [#A] Note B.
                |* TODO Note C.
                |SCHEDULED: <2014-01-01>
                |** Note D.
                |""".trimMargin())
        setupBook("notebook-2",
            """* Note #1.
                |** TODO Note #3.
                |** Note #4.
                |*** DONE Note #5.
                |CLOSED: [2014-06-03 Tue 13:34]
                |""".trimMargin())

        val results = searchNotes(".i.todo .i.done")

        assertThat(results.size, `is`(5))
    }

    @Test
    fun testSearchExpressionsDefaultPriority() {
        setupBook("book-one",
            """* Note A.
                |** [#A] Note B.
                |* TODO [#B] Note C.
                |SCHEDULED: <2014-01-01>
                |** [#C] Note D.
                |*** TODO Note E.
                |""".trimMargin())
        setupBook("book-two", "* Note #1.\n")

        val results = searchNotes("p.b")

        assertThat(results.size, `is`(4))
    }

    @Test
    fun testSearchExpressionsToday() {
        setupBook("book-one",
            """First book used for testing
                |* Note A.
                |** [#A] Note B.
                |* TODO Note C.
                |SCHEDULED: <2014-01-01>
                |** Note D.
                |*** TODO Note E.
                |*** Same title in different notebooks.
                |*** Another note.
                |""".trimMargin())

        setupBook("book-two",
            """Sample book used for tests
                |* Note #1.
                |* Note #2.
                |** TODO Note #3.
                |** Note #4.
                |*** DONE Note #5.
                |CLOSED: [2014-06-03 Tue 13:34]
                |**** Note #6.
                |** Note #7.
                |* DONE Note #8.
                |CLOSED: [2014-06-03 Tue 3:34]
                |**** Note #9.
                |SCHEDULED: <2014-05-26 Mon>
                |** Note #10.
                |** Same title in different notebooks.
                |** Note #11.
                |** Note #12.
                |** Note #13.
                |DEADLINE: <2014-05-26 Mon>
                |** Note #14.
                |** [#A] Note #15.
                |** [#A] Note #16.
                |** [#B] Note #17.
                |** [#C] Note #18.
                |** Note #19.
                |** Note #20.
                |** Note #21.
                |** Note #22.
                |** Note #23.
                |** Note #24.
                |** Note #25.
                |** Note #26.
                |** Note #27.
                |** Note #28.
                |""".trimMargin())

        val results = searchNotes("s.today")

        assertThat(results.size, `is`(2))
    }

    @Test
    fun testClosedTimeSearch() {
        setupBook("notebook-1", "* Note A\nCLOSED: [2014-01-01]")

        val results = searchNotes("c.ge.-2d")

        assertThat(results.size, `is`(0))
    }

    @Test
    fun testOrderDeadlineWithAndWithoutTimePartDesc() {
        setupBook("notebook-1",
            """* Note A
                |DEADLINE: <2014-01-01>
                |** Note B
                |DEADLINE: <2014-01-02>
                |*** Note C
                |DEADLINE: <2014-01-02 10:00>
                |*** DONE Note D
                |DEADLINE: <2014-01-03>
                |""".trimMargin())

        val results = searchNotes("d.today .i.done .o.d")

        assertThat(results.size, `is`(3))
        assertThat(results[0].note.title, `is`("Note B"))
        assertThat(results[1].note.title, `is`("Note C"))
        assertThat(results[2].note.title, `is`("Note A"))
    }
}
