package com.orgzly.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.LocalStorage
import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.TestUtils
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoFactory
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotesOrgExporterTest {

    private lateinit var context: Context
    private lateinit var database: OrgzlyDatabase
    private lateinit var dataRepository: DataRepository
    private lateinit var dbRepoBookRepository: DbRepoBookRepository
    private lateinit var testUtils: TestUtils

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppPreferences.setToDefaults(context)

        database = OrgzlyDatabase.forMemory(context)
        dbRepoBookRepository = DbRepoBookRepository(database)
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

    @Test
    fun testExportNoteWithBasicTitle() {
        testUtils.setupBook("test-book", "* Test Note")
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("* Test Note"))
    }

    @Test
    fun testExportNoteWithStateAndTags() {
        testUtils.setupBook(
            "test-book",
            "* TODO Test Note :tag1:tag2:\n"
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("* TODO Test Note"))
        assertTrue(exported.contains(":tag1:tag2:"))
    }

    @Test
    fun testExportNoteWithScheduledTimestamp() {
        testUtils.setupBook(
            "test-book",
            "* Test Note\nSCHEDULED: <2024-01-15 Mon>\n"
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("SCHEDULED:"))
        assertTrue(exported.contains("2024-01-15"))
    }

    @Test
    fun testExportNoteWithProperties() {
        testUtils.setupBook(
            "test-book",
            """
            * Test Note
            :PROPERTIES:
            :CUSTOM: value
            :ID: test-id-123
            :END:
            """.trimIndent()
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains(":PROPERTIES:"))
        assertTrue(exported.contains(":CUSTOM:"))
        assertTrue(exported.contains("value"))
        assertTrue(exported.contains(":END:"))
    }

    @Test
    fun testExportNoteWithContent() {
        testUtils.setupBook(
            "test-book",
            """
            * Test Note
            This is the note content.
            It has multiple lines.
            """.trimIndent()
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("This is the note content."))
        assertTrue(exported.contains("It has multiple lines."))
    }

    @Test
    fun testExportNoteWithPriority() {
        testUtils.setupBook(
            "test-book",
            "* TODO [#A] Important Note\n"
        )
        val note = dataRepository.getLastNote("Important Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("[#A]"))
    }

    @Test
    fun testExportNoteWithInvalidIdThrowsException() {
        val exporter = NotesOrgExporter(dataRepository)
        assertThrows(IllegalArgumentException::class.java) {
            exporter.exportNote(999999L) // Non-existent note ID
        }
    }

    @Test
    fun testExportNoteWithDeadline() {
        testUtils.setupBook(
            "test-book",
            "* Test Note\nDEADLINE: <2024-12-31 Tue>\n"
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("DEADLINE:"))
        assertTrue(exported.contains("2024-12-31"))
    }

    @Test
    fun testExportNoteWithRepeater() {
        testUtils.setupBook(
            "test-book",
            "* Test Note\nSCHEDULED: <2024-01-01 Mon +1w>\n"
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("+1w"))
    }

    @Test
    fun testExportNoteEndsWithNewline() {
        testUtils.setupBook("test-book", "* Test Note")
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue("Exported note should end with newline", exported.endsWith("\n"))
    }

}
