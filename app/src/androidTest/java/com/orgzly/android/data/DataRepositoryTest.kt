package com.orgzly.android.data

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.prefs.AppPreferences
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.io.IOException

class DataRepositoryTest : OrgzlyTest() {

    /**
     * If the user attempts to export app settings to a note with a non-unique "ID" value, then
     * - no export should happen
     * - a runtime exception should be thrown
     */
    @Test(expected = RuntimeException::class)
    fun testExportSettingsAndSearchesToSelectedNote() {
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: not-unique-value
                :END:
                
                content

                * Note 2
                :PROPERTIES:
                :ID: not-unique-value
                :END:
                
                content
                
           """.trimIndent()
        )
        TestCase.assertEquals(2, dataRepository.getNotes("book1").size)
        AppPreferences.settingsExportAndImportNoteId(context, "not-unique-value")
        try {
            dataRepository.exportSettingsAndSearchesToSelectedNote()
        } catch (e: IOException) {
            Assert.assertTrue(e.message!!.contains("Found multiple"))
            throw e
        } finally {
            TestCase.assertEquals("content", dataRepository.getNotes("book1")[0].note.content)
            TestCase.assertEquals("content", dataRepository.getNotes("book1")[1].note.content)
        }
    }
}