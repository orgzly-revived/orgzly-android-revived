package com.orgzly.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.LocalStorage
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.repos.RepoFactory
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataRepositoryTest {

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ===== Tests for createBook() path traversal validation =====

    @Test
    fun testCreateBookWithPathTraversalAtStartThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("../malicious-name")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithPathTraversalInMiddleThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("prefix/../malicious")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithPathTraversalAtEndThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("malicious../")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithMultiplePathTraversalsThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("../../very-malicious")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithValidNameSucceeds() {
        // Should not throw
        val book = dataRepository.createBook("valid-book-name")
        assertThat(book.book.name, `is`("valid-book-name"))
    }

    @Test
    fun testCreateBookWithDotsInNameSucceeds() {
        // Single dots and multiple consecutive dots (not "../") should be allowed
        val book = dataRepository.createBook("file.with.dots")
        assertThat(book.book.name, `is`("file.with.dots"))
    }

    @Test
    fun testCreateBookWithConsecutiveDotsSucceeds() {
        // ".." without "/" should be allowed (edge case)
        val book = dataRepository.createBook("file..name")
        assertThat(book.book.name, `is`("file..name"))
    }

    // ===== Tests for renameBook() path traversal validation =====

    @Test
    fun testRenameBookWithPathTraversalGivesError() {
        val book = dataRepository.createBook("valid-book")
        dataRepository.renameBook(book, "../malicious-name")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithPathTraversalInMiddleGivesError() {
        val book = dataRepository.createBook("valid-book")

        dataRepository.renameBook(book, "prefix/../malicious")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithPathTraversalAtEndGivesError() {
        val book = dataRepository.createBook("valid-book")

        dataRepository.renameBook(book, "malicious../")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithMultiplePathTraversalsGivesError() {
        val book = dataRepository.createBook("valid-book")

        dataRepository.renameBook(book, "../../../very-malicious")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithValidNameSucceeds() {
        val book = dataRepository.createBook("original-name")

        // Should not throw
        dataRepository.renameBook(book, "new-valid-name")
        assertEquals("new-valid-name", dataRepository.getBook(book.book.id)!!.name)
    }

    @Test
    fun testRenameBookWithDotsInNameSucceeds() {
        val book = dataRepository.createBook("original-name")

        dataRepository.renameBook(book, "renamed.with.dots")
        assertEquals("renamed.with.dots", dataRepository.getBook(book.book.id)!!.name)
    }

    @Test
    fun testRenameBookWithConsecutiveDotsSucceeds() {
        val book = dataRepository.createBook("original-name")

        // ".." without "/" should be allowed
        dataRepository.renameBook(book, "renamed..name")
        assertEquals("renamed..name", dataRepository.getBook(book.book.id)!!.name)
    }
}