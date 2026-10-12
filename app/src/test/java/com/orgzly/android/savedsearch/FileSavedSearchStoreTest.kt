package com.orgzly.android.savedsearch

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.data.SavedSearchRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.SavedSearch
import org.json.JSONArray
import org.json.JSONException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FileSavedSearchStoreTest {
    private lateinit var context: Context
    private lateinit var database: OrgzlyDatabase
    private lateinit var repository: SavedSearchRepository
    private lateinit var store: FileSavedSearchStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = OrgzlyDatabase.forMemory(context)
        repository = SavedSearchRepository(database)
        store = FileSavedSearchStore(context, repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `export and import preserve ordered names and queries without database ids`() {
        repository.replaceAll(listOf(
            SavedSearch(20, "Più tardi", "s.gt.today", 30),
            SavedSearch(10, "Agenda \"oggi\"", "s.today\nt.work", 10)
        ))
        val expected = repository.getAll().map { it.name to it.query }
        val file = File(context.cacheDir, "searches.json")
        val uri = Uri.fromFile(file)

        assertEquals(2, store.exportSearches(uri))
        val json = JSONArray(file.readText())
        assertEquals("Agenda \"oggi\"", json.getJSONObject(0).getString("name"))
        assertFalse(json.getJSONObject(0).has("id"))
        assertFalse(json.getJSONObject(0).has("position"))

        repository.replaceAll(listOf(SavedSearch(1, "Replace me", "i.done", 1)))
        assertEquals(2, store.importSearches(uri))
        assertEquals(expected, repository.getAll().map { it.name to it.query })
        assertEquals(listOf(1, 2), repository.getAll().map { it.position })
    }

    @Test
    fun `invalid import keeps existing searches even after a valid entry`() {
        val original = listOf(SavedSearch(1, "Agenda", "s.today", 1))
        repository.replaceAll(original)
        val file = File(context.cacheDir, "invalid-searches.json")
        file.writeText("""[{"name":"Valid","query":"i.todo"},{"name":"Missing query"}]""")

        assertThrows(JSONException::class.java) {
            store.importSearches(Uri.fromFile(file))
        }
        assertEquals(original, repository.getAll())
    }

    @Test
    fun `importing an empty list removes existing searches`() {
        repository.create(SavedSearch(0, "Agenda", "s.today", 0))
        val file = File(context.cacheDir, "empty-searches.json")
        file.writeText("[]")

        assertEquals(0, store.importSearches(Uri.fromFile(file)))
        assertEquals(emptyList<SavedSearch>(), repository.getAll())
    }
}
