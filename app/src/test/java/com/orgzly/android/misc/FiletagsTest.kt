package com.orgzly.android.misc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.LocalStorage
import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.TestUtils
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.toList
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.repos.RepoFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FiletagsTest {

    private lateinit var context: Context
    private lateinit var database: OrgzlyDatabase
    private lateinit var dataRepository: DataRepository
    private lateinit var testUtils: TestUtils

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppPreferences.setToDefaults(context)

        database = OrgzlyDatabase.forMemory(context)
        val dbRepoBookRepository = DbRepoBookRepository(database)
        val localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        dataRepository = DataRepository(
            context, database, repoFactory, context.resources, localStorage
        )

        testUtils = TestUtils(dataRepository, dbRepoBookRepository)

        dataRepository.clearDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun exportBook(bookName: String): String {
        val bookView = dataRepository.getBookView(bookName)!!
        val sw = StringWriter()
        NotesOrgExporter(dataRepository).exportBook(bookView.book, sw)
        return sw.toString()
    }

    @Test
    fun filetagsInheritance() {
        testUtils.setupBook("book-01", """
            #+FILETAGS: :project:work:

            * Untagged A
            * Untagged B
            * Tagged :urgent:
            * Parent :context:
            ** Child
        """.trimIndent())

        val notes = dataRepository.getNotes("book-01")

        // Filetags applied to all notes
        val untaggedA = notes.first { it.note.title == "Untagged A" }
        assertEquals(listOf("project", "work"), untaggedA.getInheritedTagsList())

        val untaggedB = notes.first { it.note.title == "Untagged B" }
        assertEquals(listOf("project", "work"), untaggedB.getInheritedTagsList())

        // Own tags stay on the note, filetags in inherited
        val tagged = notes.first { it.note.title == "Tagged" }
        assertEquals(listOf("urgent"), tagged.note.tags.toList())
        assertEquals(listOf("project", "work"), tagged.getInheritedTagsList())

        // Child inherits filetags + parent's tags
        val child = notes.first { it.note.title == "Child" }
        assertEquals(listOf("project", "work", "context"), child.getInheritedTagsList())
    }

    @Test
    fun noFiletagsOrEmpty() {
        testUtils.setupBook("no-filetags", """
            * Note A
            * Note B
        """.trimIndent())

        testUtils.setupBook("empty-filetags", """
            #+FILETAGS:

            * Note C
        """.trimIndent())

        // No filetags directive — no inherited tags
        dataRepository.getNotes("no-filetags").forEach {
            assertTrue(it.getInheritedTagsList().isEmpty())
        }

        // Empty filetags directive — no inherited tags
        dataRepository.getNotes("empty-filetags").forEach {
            assertTrue(it.getInheritedTagsList().isEmpty())
        }
    }

    @Test
    fun filetagsInSearchAndExport() {
        testUtils.setupBook("book-01", """
            #+FILETAGS: :project:

            * Note A :own:
        """.trimIndent())

        // Filetags findable via tag search
        val query = InternalQueryParser().parse("t.project")
        val results = dataRepository.selectNotesFromQuery(query)
        assertEquals(1, results.size)
        assertEquals("Note A", results[0].note.title)

        // Note's own tags in global tag list, filetags are not (they live on books)
        val tags = dataRepository.selectAllTags()
        assertTrue(tags.contains("own"))
        assertFalse(tags.contains("project"))

        // Filetags preserved on export
        val exported = exportBook("book-01")
        assertTrue(exported.contains("#+FILETAGS:"))
    }
}
