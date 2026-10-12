package com.orgzly.android.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.SavedSearch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SavedSearchRepositoryTest {
    private lateinit var database: OrgzlyDatabase
    private lateinit var repository: SavedSearchRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrgzlyDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repository = SavedSearchRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creating after a deletion appends without changing existing searches`() {
        repository.replaceAll(listOf(
            SavedSearch(1, "Agenda", "s.today", 10),
            SavedSearch(2, "Later", "s.gt.today", 30)
        ))
        repository.delete(setOf(1))

        val id = repository.create(SavedSearch(0, "Tasks", "i.todo", -1))
        assertEquals(listOf("Later", "Tasks"), repository.getAll().map { it.name })
        assertEquals(31, repository.get(id)!!.position)
        assertNull(repository.get(1))

        val updated = repository.get(id)!!.copy(name = "Next", query = "i.next")
        repository.update(updated)
        assertEquals(listOf(SavedSearch(2, "Later", "s.gt.today", 30), updated), repository.getAll())
    }

    @Test
    fun `moving swaps adjacent positions even when positions have gaps`() {
        val first = SavedSearch(1, "First", "i.todo", 10)
        val second = SavedSearch(2, "Second", "i.next", 30)
        val third = SavedSearch(3, "Third", "i.done", 50)
        repository.replaceAll(listOf(first, second, third))

        repository.moveUp(third.id)
        assertEquals(listOf(first, third.copy(position = 30), second.copy(position = 50)), repository.getAll())

        repository.moveDown(first.id)
        assertEquals(listOf(third.copy(position = 10), first.copy(position = 30), second.copy(position = 50)), repository.getAll())

        val before = repository.getAll()
        repository.moveUp(third.id)
        repository.moveDown(second.id)
        repository.moveUp(999)
        repository.moveDown(999)
        assertEquals(before, repository.getAll())
    }

    @Test
    fun `name lookup ignores case and keeps position order`() {
        val first = SavedSearch(1, "Agenda", "s.today", 10)
        val last = SavedSearch(2, "AGENDA", "d.today", 30)
        repository.replaceAll(listOf(last, SavedSearch(3, "Other", "i.todo", 20), first))

        assertEquals(listOf(first, last), repository.getByNameIgnoreCase("agenda"))
    }

    @Test
    fun `failed replacement preserves the previous searches`() {
        val original = listOf(SavedSearch(1, "Agenda", "s.today", 10))
        repository.replaceAll(original)

        assertThrows(SQLiteConstraintException::class.java) {
            repository.replaceAll(listOf(
                SavedSearch(2, "First", "i.todo", 1),
                SavedSearch(2, "Duplicate id", "i.done", 2)
            ))
        }

        assertEquals(original, repository.getAll())
    }

    @Test
    fun `observers receive ordered searches after moves and replacement`() {
        val liveData = repository.getLiveData()
        val received = mutableListOf<List<SavedSearch>>()
        val observer = Observer<List<SavedSearch>> { received.add(it) }
        liveData.observeForever(observer)
        try {
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(emptyList<SavedSearch>(), received.last())

            repository.replaceAll(listOf(
                SavedSearch(1, "First", "i.todo", 1),
                SavedSearch(2, "Second", "i.done", 2)
            ))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("First", "Second"), received.last().map { it.name })

            repository.moveUp(2)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("Second", "First"), received.last().map { it.name })

            assertEquals(0, repository.replaceAll(emptyList()))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(emptyList<SavedSearch>(), received.last())
        } finally {
            liveData.removeObserver(observer)
        }
    }
}
