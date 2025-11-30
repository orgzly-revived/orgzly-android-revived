package com.orgzly.android.misc

import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.OrgzlyTest
import org.junit.Assert.*
import org.junit.Test

class NotesOrgExporterTest : OrgzlyTest() {

    @Test
    fun testExportNoteWithBasicTitle() {
        val book = testUtils.setupBook("test-book", "* Test Note")
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("* Test Note"))
    }

    @Test
    fun testExportNoteWithStateAndTags() {
        val book = testUtils.setupBook(
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
        val book = testUtils.setupBook(
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
        val book = testUtils.setupBook(
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
        val book = testUtils.setupBook(
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
        val book = testUtils.setupBook(
            "test-book",
            "* TODO [#A] Important Note\n"
        )
        val note = dataRepository.getLastNote("Important Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("[#A]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testExportNoteWithInvalidIdThrowsException() {
        val exporter = NotesOrgExporter(dataRepository)
        exporter.exportNote(999999L) // Non-existent note ID
    }

    @Test
    fun testExportNoteWithDeadline() {
        val book = testUtils.setupBook(
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
        val book = testUtils.setupBook(
            "test-book",
            "* Test Note\nSCHEDULED: <2024-01-01 Mon +1w>\n"
        )
        val note = dataRepository.getLastNote("Test Note")
        assertNotNull(note)

        val exporter = NotesOrgExporter(dataRepository)
        val exported = exporter.exportNote(note!!.id)

        assertTrue(exported.contains("+1w"))
    }
}
