package com.orgzly.android.ui.savedsearches

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.data.SavedSearchRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.SavedSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SavedSearchesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private lateinit var database: OrgzlyDatabase
    private lateinit var repository: SavedSearchRepository
    private lateinit var viewModel: SavedSearchesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrgzlyDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(dispatcher)
            .build()
        repository = SavedSearchRepository(database)
        viewModel = SavedSearchesViewModel(repository)
        viewModelStore.put("saved-searches", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `state distinguishes initial loading from a loaded empty list`() = runTest {
        assertTrue(viewModel.state.value.isLoading)
        assertEquals(emptyList<SavedSearch>(), viewModel.state.value.searches)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(emptyList<SavedSearch>(), viewModel.state.value.searches)
    }

    @Test
    fun `state follows ordered searches through edits moves creation and deletion`() = runTest {
        val first = SavedSearch(1, "First", "i.todo", 10)
        val second = SavedSearch(2, "Second", "i.done", 30)
        repository.replaceAll(listOf(second, first))

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        runCurrent()
        assertEquals(SavedSearchesState(listOf(first, second), false), viewModel.state.value)

        repository.moveUp(second.id)
        runCurrent()
        assertEquals(listOf("Second", "First"), viewModel.state.value.searches.map { it.name })

        val renamed = repository.get(second.id)!!.copy(name = "Completed", query = "i.done b.work")
        repository.update(renamed)
        runCurrent()
        assertEquals(renamed, viewModel.state.value.searches.first())

        val newId = repository.create(SavedSearch(0, "Next", "i.next", 0))
        runCurrent()
        assertEquals(listOf(second.id, first.id, newId), viewModel.state.value.searches.map { it.id })

        repository.delete(setOf(first.id, second.id, newId))
        runCurrent()
        assertEquals(SavedSearchesState(emptyList(), false), viewModel.state.value)
    }

    @Test
    fun `resubscribing loads changes made after observation stops`() = runTest {
        val original = SavedSearch(1, "Original", "i.todo", 1)
        repository.replaceAll(listOf(original))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        runCurrent()
        assertEquals(listOf(original), viewModel.state.value.searches)

        collector.cancel()
        advanceTimeBy(5001)
        runCurrent()

        val replacement = SavedSearch(2, "Replacement", "i.done", 1)
        repository.replaceAll(listOf(replacement))
        runCurrent()
        assertEquals(listOf(original), viewModel.state.value.searches)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        runCurrent()
        assertEquals(SavedSearchesState(listOf(replacement), false), viewModel.state.value)
    }
}
