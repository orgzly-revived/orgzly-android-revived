package com.orgzly.android.data

import com.google.gson.Gson
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRepositoryTest : OrgzlyTest() {

    /**
     * If the user attempts to export app settings to a note with a non-unique "ID" value, then
     * - an exception should be thrown
     * - no export should happen
     */
    @Test(expected = RuntimeException::class)
    fun testExportSettingsToNonUniqueNoteId() {
        // Given
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
        assertEquals(2, dataRepository.getNotes("book1").size)
        AppPreferences.settingsExportAndImportNoteId(context, "not-unique-value")
        val targetNote = dataRepository.getNotes("book1")[0].note
        
        // Expect
        try {
            dataRepository.exportSettingsAndSearchesToNote(targetNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("Found multiple"))
            throw e
        } finally {
            assertEquals("content", dataRepository.getNotes("book1")[0].note.content)
            assertEquals("content", dataRepository.getNotes("book1")[1].note.content)
        }
    }

    /**
     * Unknown keys in the JSON blob must be silently ignored during import
     * without causing issues.
     */
    @Test
    fun testImportSettingsWithInvalidEntries() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{"pref_key_states":"NEXT | DONE","invalid_key":"invalid_value"},"saved_searches":{}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        // Check that a setting has its default value
        assertEquals("TODO NEXT | DONE", AppPreferences.states(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // When
        dataRepository.importSettingsAndSearchesFromNote(sourceNote)

        // Expect the ssetting to have changed
        assertEquals("NEXT | DONE", AppPreferences.states(context))
        // Expect searches not to have changed
        assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
    }

    /**
     * An attempt to import completely invalid data must fail gracefully, with no changes.
     */
    @Test(expected = RuntimeException::class)
    fun testImportInvalidSettingsData() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                Sorry, I'm just a little note. I may even look a little bit like
                JSON. {"something":"nothing"}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("valid JSON"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
        }
    }

    /**
     * If the "settings" key is missing, no import should happen.
     */
    @Test(expected = RuntimeException::class)
    fun testImportSettingsNoSettingsKey() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"saved_searches":{"Agenda":".it.done ad.7"}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("missing mandatory fields"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
        }
    }

    /**
     * If the "searches" key is missing, no import should happen.
     */
    @Test(expected = RuntimeException::class)
    fun testImportSettingsNoSearchesKey() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{"pref_key_states":"NEXT | DONE"}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("missing mandatory fields"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
        }
    }

    /**
     * The "settings" and "saved_searches" keys must be present, but they may be empty.
     */

    @Test
    fun testImportSettingsWithSettingsDataWithoutSearchesData() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1", """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{"pref_key_states":"NEXT | DONE"},"saved_searches":{}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        // Check that the setting has the default value
        assertEquals("TODO NEXT | DONE", AppPreferences.states(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // When
        dataRepository.importSettingsAndSearchesFromNote(sourceNote)

        // Expect searches not to have changed
        assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
        // Expect settings to have changed
        assertEquals("NEXT | DONE", AppPreferences.states(context))
    }

    @Test
    fun testImportSettingsWithSearchesDataWithoutSettingsData() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1", """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{},"saved_searches":{"Agenda":".it.done ad.7"}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        // Assert default number of searches
        assertEquals(4, searchesBeforeImport.size)
        // Store current settings
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // When
        dataRepository.importSettingsAndSearchesFromNote(sourceNote)

        // Then
        // Searches have changed
        assertEquals(1, dataRepository.getSavedSearches().size)
        // Settings have not changed
        assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
    }

    @Test(expected = RuntimeException::class)
    fun testImportSettingsValidJsonButNoData() {
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1", """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{},"saved_searches":{}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("Found no settings or saved searches to import"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(
                settingsBeforeImport,
                Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
            )
        }
    }
}
