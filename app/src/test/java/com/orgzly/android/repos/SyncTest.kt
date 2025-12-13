package com.orgzly.android.repos

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.orgzly.R
import com.orgzly.android.BookFormat
import com.orgzly.android.BookName
import com.orgzly.android.LocalStorage
import com.orgzly.android.TestUtils
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.BookNamesake
import com.orgzly.android.sync.BookSyncStatus
import com.orgzly.android.util.EncodingDetect
import com.orgzly.android.util.MiscUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Sync tests migrated from instrumented tests to JVM unit tests.
 * Tests synchronization logic, repository management, and book sync workflows
 * without requiring Android instrumentation or emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncTest {

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var database: OrgzlyDatabase
    private lateinit var dbRepoBookRepository: DbRepoBookRepository
    private lateinit var localStorage: LocalStorage
    private lateinit var testUtils: TestUtils

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // In-memory database for tests (faster than file-based)
        database = OrgzlyDatabase.forMemory(context)

        dbRepoBookRepository = DbRepoBookRepository(database)
        localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        dataRepository = DataRepository(
            context, database, repoFactory, context.resources, localStorage
        )

        testUtils = TestUtils(dataRepository, dbRepoBookRepository)

        // Set up default test preferences
        setupPreferences()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun setupPreferences() {
        AppPreferences.states(context, "TODO NEXT | DONE")
        AppPreferences.isGettingStartedNotebookLoaded(context, true)
        AppPreferences.displayedBookDetails(
            context,
            context.resources.getStringArray(R.array.displayed_book_details_values).toList()
        )
        AppPreferences.prefaceDisplay(
            context,
            context.getString(R.string.pref_value_preface_in_book_few_lines)
        )
        AppPreferences.inheritedTagsInSearchResults(context, true)
        AppPreferences.colorTheme(context, "light")
        AppPreferences.logMajorEvents(context, true)
    }

    @Test
    fun testOrgRange() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(
            repo,
            "mock://repo-a/remote-book-1.org",
            "* Note\nSCHEDULED: <2015-01-13 уто 13:00-14:14>--<2015-01-14 сре 14:10-15:20>",
            "0abcdef",
            1400067156
        )

        testUtils.sync()

        val noteView = dataRepository.getLastNoteView("Note")
        assertEquals(
            "<2015-01-13 уто 13:00-14:14>--<2015-01-14 сре 14:10-15:20>",
            noteView?.scheduledRangeString
        )
    }

    @Test
    fun testSync1() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupBook("todo", "hum hum")

        assertEquals(1, dataRepository.getRepos().size)
        assertEquals(1, dataRepository.getBooks().size)
        assertNull(dataRepository.getBooks()[0].syncedTo)

        testUtils.sync()

        assertEquals(1, dataRepository.getRepos().size)
        assertEquals(1, dataRepository.getBooks().size)
        assertNotNull(dataRepository.getBooks()[0].syncedTo)
        assertEquals(
            "mock://repo-a/todo.org",
            dataRepository.getBooks()[0].syncedTo?.uri.toString()
        )
    }

    @Test
    fun testSync2() {
        // Add remote books
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/remote-book-1.org", "", "1abcdef", 1400067156)
        testUtils.setupRook(repo, "mock://repo-a/remote-book-2.org", "", "2abcdef", 1400067156)

        assertEquals(1, dataRepository.getRepos().size)
        assertEquals(
            2,
            dbRepoBookRepository.getBooks(repo.id, Uri.parse("mock://repo-a")).size
        )
        assertEquals(
            "mock://repo-a",
            dbRepoBookRepository.getBooks(repo.id, Uri.parse("mock://repo-a"))[0]
                .repoUri.toString()
        )
        assertEquals(0, dataRepository.getBooks().size)

        // Sync
        val g1 = testUtils.sync()
        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK,
            g1["remote-book-1"]?.status
        )
        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK,
            g1["remote-book-2"]?.status
        )

        assertEquals(1, dataRepository.getRepos().size)
        assertEquals(
            2,
            dbRepoBookRepository.getBooks(repo.id, Uri.parse("mock://repo-a")).size
        )
        assertEquals(
            "mock://repo-a",
            dbRepoBookRepository.getBooks(repo.id, Uri.parse("mock://repo-a"))[0]
                .repoUri.toString()
        )
        assertEquals(2, dataRepository.getBooks().size)

        // Sync again
        val g2 = testUtils.sync()
        assertEquals(BookSyncStatus.NO_CHANGE, g2["remote-book-1"]?.status)
        assertEquals(BookSyncStatus.NO_CHANGE, g2["remote-book-2"]?.status)

        assertEquals(1, dataRepository.getRepos().size)
        assertEquals(
            2,
            dbRepoBookRepository.getBooks(repo.id, Uri.parse("mock://repo-a")).size
        )
        assertEquals(
            "mock://repo-a",
            dbRepoBookRepository.getBooks(repo.id, Uri.parse("mock://repo-a"))[0]
                .repoUri.toString()
        )
        assertEquals(2, dataRepository.getBooks().size)
    }

    @Test
    fun testRenameUsedRepo() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(
            repo,
            "mock://repo-a/book.org",
            "Content A",
            "1abcde",
            1400067156000L
        )

        var book: BookView

        testUtils.sync()

        testUtils.renameRepo("mock://repo-a", "mock://repo-b")

        book = dataRepository.getBookView("book")!!
        assertNull(book.linkRepo)
        assertNull(book.syncedTo)

        testUtils.sync()

        book = dataRepository.getBookView("book")!!
        assertEquals(
            BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO.toString(),
            book.book.syncStatus
        )
        assertEquals("mock://repo-b", book.linkRepo?.url)
        assertEquals("mock://repo-b", book.syncedTo?.repoUri.toString())
        assertEquals("mock://repo-b/book.org", book.syncedTo?.uri.toString())

        testUtils.renameRepo("mock://repo-b", "mock://repo-a")
        testUtils.sync()

        book = dataRepository.getBookView("book")!!
        assertEquals(
            BookSyncStatus.BOOK_WITHOUT_LINK_AND_ONE_OR_MORE_ROOKS_EXIST.toString(),
            book.book.syncStatus
        )
        assertNull(book.linkRepo)
        assertNull(book.syncedTo)
    }

    @Test
    fun testDeletingUsedRepo() {
        var book: BookView

        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(
            repo,
            "mock://repo-a/book.org",
            "Content A",
            "1abcde",
            1400067156000L
        )
        testUtils.sync()

        testUtils.deleteRepo("mock://repo-a")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        testUtils.sync()

        book = dataRepository.getBookView("book")!!
        assertEquals(
            BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO.toString(),
            book.book.syncStatus
        )
        assertEquals("mock://repo-b", book.linkRepo?.url)
        assertEquals("mock://repo-b", book.syncedTo?.repoUri.toString())
        assertEquals("mock://repo-b/book.org", book.syncedTo?.uri.toString())

        testUtils.deleteRepo("mock://repo-b")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.sync()

        book = dataRepository.getBookView("book")!!
        assertEquals(
            BookSyncStatus.BOOK_WITHOUT_LINK_AND_ONE_OR_MORE_ROOKS_EXIST.toString(),
            book.book.syncStatus
        )
        assertNull(book.linkRepo)
        assertNull(book.syncedTo)
    }

    @Test
    fun testEncodingStaysTheSameAfterSecondSync() {
        var book: BookView

        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(
            repo,
            "mock://repo-a/book.org",
            "Content A",
            "1abcde",
            1400067156000L
        )

        testUtils.sync()

        book = dataRepository.getBooks()[0]
        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK.toString(),
            book.book.syncStatus
        )

        when (EncodingDetect.USED_METHOD) {
            EncodingDetect.Method.JUNIVERSALCHARDET -> {
                assertNull(book.book.detectedEncoding)
                assertEquals("UTF-8", book.book.usedEncoding)
            }
        }
        assertNull(book.book.selectedEncoding)

        testUtils.sync()

        book = dataRepository.getBooks()[0]
        assertEquals(BookSyncStatus.NO_CHANGE.toString(), book.book.syncStatus)

        when (EncodingDetect.USED_METHOD) {
            EncodingDetect.Method.JUNIVERSALCHARDET -> {
                assertNull(book.book.detectedEncoding)
                assertEquals("UTF-8", book.book.usedEncoding)
            }
        }
        assertNull(book.book.selectedEncoding)
    }

    @Test
    fun testOnlyBookWithLink() {
        val repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")

        val book = testUtils.setupBook("book-1", "Content")
        dataRepository.setLink(book.book.id, repoA)

        testUtils.sync()

        val syncedBook = dataRepository.getBooks()[0]
        assertEquals(
            BookSyncStatus.ONLY_BOOK_WITH_LINK.toString(),
            syncedBook.book.syncStatus
        )
    }

    @Test
    fun testOnlyBookWithoutLinkAndOneRepo() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupBook("book-1", "Content")
        testUtils.sync()

        val book = dataRepository.getBooks()[0]
        assertEquals(
            BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO.toString(),
            book.book.syncStatus
        )
        assertEquals(
            context.getString(R.string.sync_status_saved, repo.url),
            book.book.lastAction?.message
        )
        assertEquals(repo.url, book.linkRepo?.url)
        val syncRepo = testUtils.repoInstance(RepoType.MOCK, repo.url)
        assertEquals(1, syncRepo.books.size)
        assertEquals(syncRepo.books[0].toString(), book.syncedTo.toString())
    }

    @Test
    fun testOnlyBookWithoutLinkAndMultipleRepos() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        testUtils.setupBook("book-1", "Content")
        testUtils.sync()

        val book = dataRepository.getBooks()[0]
        assertEquals(
            BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_MULTIPLE_REPOS.toString(),
            book.book.syncStatus
        )
    }

    @Test
    fun testMultipleRooks() {
        val repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(
            repoA,
            "mock://repo-a/book.org",
            "Content A",
            "revA",
            1234567890000L
        )

        val repoB = testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        testUtils.setupRook(
            repoB,
            "mock://repo-b/book.org",
            "Content B",
            "revB",
            1234567890000L
        )

        testUtils.sync()

        var book = dataRepository.getBooks()[0]

        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_MULTIPLE_ROOKS.toString(),
            book.book.syncStatus
        )
        assertTrue(book.book.isDummy)

        dataRepository.setLink(book.book.id, repoA)

        testUtils.sync()

        book = dataRepository.getBooks()[0]

        assertEquals(BookSyncStatus.DUMMY_WITH_LINK.toString(), book.book.syncStatus)
        assertFalse(book.book.isDummy)
        assertEquals("mock://repo-a/book.org", book.syncedTo?.uri.toString())
    }

    @Test
    fun testMtimeOfLoadedBook() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(
            repo,
            "mock://repo-a/book.org",
            "Content",
            "rev",
            1234567890000L
        )

        testUtils.sync()

        val book = dataRepository.getBooks()[0]

        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK.toString(),
            book.book.syncStatus
        )
        assertEquals(1234567890000L, book.book.mtime)
    }

    @Test
    fun testDummyShouldNotBeSavedWhenHavingOneRepo() {
        val repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val repoB = testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        testUtils.setupRook(repoA, "mock://repo-a/booky.org", "", "1abcdef", 1400067155)
        testUtils.setupRook(repoB, "mock://repo-b/booky.org", "", "2abcdef", 1400067156)

        var book: Book
        var namesakes: Map<String, BookNamesake>

        namesakes = testUtils.sync()
        book = dataRepository.getBook("booky")!!

        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_MULTIPLE_ROOKS,
            namesakes["booky"]?.status
        )
        assertTrue(book.isDummy)

        testUtils.deleteRepo("mock://repo-a")
        testUtils.deleteRepo("mock://repo-b")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-c")

        namesakes = testUtils.sync()
        book = dataRepository.getBook("booky")!!

        assertEquals(BookSyncStatus.ONLY_DUMMY, namesakes["booky"]?.status)
        // TODO: We should delete it, no point of having a dummy and no remote book

        assertTrue(book.isDummy)
    }

    @Test
    fun testDeletedRepoShouldStayAsBookLink() {
        val repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        testUtils.setupRook(repoA, "mock://repo-a/booky.org", "", "1abcdef", 1400067155)

        var book: BookView
        var namesakes: Map<String, BookNamesake>

        namesakes = testUtils.sync()
        book = dataRepository.getBookView("booky")!!

        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK,
            namesakes["booky"]?.status
        )

        assertFalse(book.book.isDummy)
        assertEquals("mock://repo-a", book.linkRepo?.url)
        assertEquals("mock://repo-a", book.syncedTo?.repoUri.toString())

        testUtils.deleteRepo("mock://repo-a")
        testUtils.deleteRepo("mock://repo-b")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-c")

        namesakes = testUtils.sync()
        book = dataRepository.getBookView("booky")!!

        assertEquals(
            BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO,
            namesakes["booky"]?.status
        )

        assertFalse(book.book.isDummy)
        assertEquals("mock://repo-c", book.linkRepo?.url)
        assertEquals("mock://repo-c", book.syncedTo?.repoUri.toString())
    }

    @Test
    fun testSyncingOrgTxt() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/booky.org.txt", "", "1abcdef", 1400067155)

        testUtils.sync()

        val book = dataRepository.getBookView("booky")!!
        assertEquals("mock://repo-a", book.linkRepo?.url)
        assertEquals("mock://repo-a", book.syncedTo?.repoUri.toString())
        assertEquals("mock://repo-a/booky.org.txt", book.syncedTo?.uri.toString())
    }

    @Test
    fun testMockFileRename() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val repo = testUtils.repoInstance(RepoType.MOCK, "mock://repo-a")

        val book = testUtils.setupBook("Booky", "1 2 3")

        testUtils.sync()

        var vrooks = repo.books

        assertEquals(1, vrooks.size)
        assertEquals("Booky", BookName.fromRook(vrooks[0]).name)

        val mtime = vrooks[0].mtime
        val rev = vrooks[0].revision

        // Rename local notebook
        dataRepository.renameBook(book, "BookyRenamed")

        // Rename rook
        repo.renameBook(Uri.parse("mock://repo-a/Booky.org"), "BookyRenamed")

        vrooks = repo.books

        assertEquals(1, vrooks.size)
        assertEquals("BookyRenamed", BookName.fromRook(vrooks[0]).name)
        assertEquals("mock://repo-a/BookyRenamed.org", vrooks[0].uri.toString())
        assertTrue(mtime < vrooks[0].mtime)
        assertNotSame(rev, vrooks[0].revision)
    }

    @Test
    fun testDirectoryFileRename() {
        val uuid = UUID.randomUUID().toString()

        val repoDir = "${context.cacheDir}/$uuid"

        val repo = testUtils.repoInstance(RepoType.DIRECTORY, "file:$repoDir")

        assertNotNull(repo)
        assertEquals(0, repo.books.size)

        val file = File.createTempFile("notebook.", ".org")
        MiscUtils.writeStringToFile("1 2 3", file)

        val vrook = repo.storeBook(file, file.name)

        file.delete()

        assertEquals(1, repo.books.size)

        repo.renameBook(vrook.uri, "notebook-renamed")

        assertEquals(1, repo.books.size)
        assertEquals(
            "${repo.uri}/notebook-renamed.org",
            repo.books[0].uri.toString()
        )
        assertEquals(
            "notebook-renamed.org",
            BookName.fromRook(repo.books[0]).repoRelativePath
        )

        LocalStorage.deleteRecursive(File(repoDir))
    }

    @Test
    fun testRenameSyncedBook() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupBook("Booky", "1 2 3")

        testUtils.sync()

        val book = dataRepository.getBookView("Booky")!!

        assertEquals("mock://repo-a/Booky.org", book.syncedTo?.uri.toString())

        dataRepository.renameBook(book, "BookyRenamed")

        val renamedBook = dataRepository.getBookView("BookyRenamed")!!

        assertNotNull(renamedBook)
        assertEquals("mock://repo-a", renamedBook.linkRepo?.url)
        assertEquals("mock://repo-a", renamedBook.syncedTo?.repoUri.toString())
        assertEquals("mock://repo-a/BookyRenamed.org", renamedBook.syncedTo?.uri.toString())
        assertEquals("1 2 3\n\n", dataRepository.getBookContent("BookyRenamed", BookFormat.ORG))
    }

    @Test
    fun testRenameBookToNameWithSpace() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupBook("Booky", "1 2 3")

        testUtils.sync()

        val book = dataRepository.getBookView("Booky")!!

        assertEquals("mock://repo-a/Booky.org", book.syncedTo?.uri.toString())

        dataRepository.renameBook(book, "Booky Renamed")

        val renamedBook = dataRepository.getBookView("Booky Renamed")!!

        assertNotNull(renamedBook)
        assertEquals("mock://repo-a", renamedBook.linkRepo?.url)
        assertEquals("mock://repo-a", renamedBook.syncedTo?.repoUri.toString())
        assertEquals(
            "mock://repo-a/Booky%20Renamed.org",
            renamedBook.syncedTo?.uri.toString()
        )
        assertEquals("1 2 3\n\n", dataRepository.getBookContent("Booky Renamed", BookFormat.ORG))
    }

    @Test
    fun testRenameSyncedBookWithDifferentLink() {
        var book: BookView

        val repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val repoB = testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        book = testUtils.setupBook("Booky", "1 2 3")
        dataRepository.setLink(book.book.id, repoA)

        testUtils.sync()

        book = dataRepository.getBooks()[0]

        assertEquals(1, testUtils.repoInstance(RepoType.MOCK, "mock://repo-a").books.size)
        assertEquals(0, testUtils.repoInstance(RepoType.MOCK, "mock://repo-b").books.size)
        assertEquals("mock://repo-a", book.linkRepo?.url)
        assertEquals("mock://repo-a", book.syncedTo?.repoUri.toString())
        assertEquals("mock://repo-a/Booky.org", book.syncedTo?.uri.toString())

        dataRepository.setLink(book.book.id, repoB)

        book = dataRepository.getBooks()[0]

        dataRepository.renameBook(book, "BookyRenamed")

        book = dataRepository.getBooks()[0]

        assertEquals("Booky", book.book.name)
        assertEquals(
            BookSyncStatus.ROOK_AND_VROOK_HAVE_DIFFERENT_REPOS.toString(),
            book.book.syncStatus
        )
        assertEquals("mock://repo-b", book.linkRepo?.url)
        assertEquals("mock://repo-a/Booky.org", book.syncedTo?.uri.toString())
    }

    @Test
    fun testRenameBookToExistingBookName() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupBook("a", "")
        testUtils.setupBook("b", "")
        assertEquals(2, dataRepository.getBooks().size)
        dataRepository.renameBook(dataRepository.getBookView("a")!!, "b")
        assertTrue(
            dataRepository.getBook("a")!!
                .lastAction
                ?.message
                ?.contains("Renaming failed: Notebook b already exists") == true
        )
    }

    @Test
    fun testIgnoreRulePreventsRenamingBook() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val ignoreRules = "bad name*\n"
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")

        // Add ignore rules using repo properties (N.B. MockRepo-specific solution)
        val repoPropsMap = hashMapOf(MockRepo.IGNORE_RULES_PREF_KEY to ignoreRules)
        val repoWithProps = RepoWithProps(repo, repoPropsMap)
        dataRepository.updateRepo(repoWithProps)

        // Create book and sync it
        testUtils.setupBook("good name", "")
        testUtils.sync()
        val bookView = dataRepository.getBookView("good name")!!

        dataRepository.renameBook(bookView, "bad name")
        val renamedBookView = dataRepository.getBooks()[0]
        assertTrue(
            renamedBookView.book
                .lastAction
                .toString()
                .contains("matches a rule in .orgzlyignore")
        )
    }

    @Test
    fun testIgnoreRulePreventsLinkingBook() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val ignoreRules = "*.org\n"
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")

        // Add ignore rules using repo properties (N.B. MockRepo-specific solution)
        val repoPropsMap = hashMapOf(MockRepo.IGNORE_RULES_PREF_KEY to ignoreRules)
        val repoWithProps = RepoWithProps(repo, repoPropsMap)
        dataRepository.updateRepo(repoWithProps)

        // Create book and sync it
        testUtils.setupBook("booky", "")
        val exception = assertThrows(IOException::class.java) { testUtils.syncOrThrow() }
        assertTrue(exception.message?.contains("matches a rule in .orgzlyignore") == true)
    }

    @Test
    fun testForceLoadBookInSubfolder() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val bookView = testUtils.setupBook("a folder/a book", "content")
        testUtils.sync()
        var books = dataRepository.getBooks()
        assertEquals(1, books.size)
        assertEquals("a folder/a book", books[0].book.name)
        dataRepository.forceLoadBook(bookView.book.id)
        books = dataRepository.getBooks()
        assertEquals(1, books.size)
        // Check that the name has not changed
        assertEquals("a folder/a book", books[0].book.name)
    }

    @Test
    fun testRemoteFileDeletion() {
        var book: BookView
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/book.org", "", "1abcdef", 1400067156)
        testUtils.sync()
        book = dataRepository.getBooks()[0]
        assertNotNull(book.linkRepo)
        assertNotNull(book.syncedTo)
        dbRepoBookRepository.deleteBook(Uri.parse("mock://repo-a/book.org"))
        testUtils.sync()
        book = dataRepository.getBooks()[0]
        assertEquals(
            BookSyncStatus.ROOK_NO_LONGER_EXISTS.toString(),
            book.book.syncStatus
        )
        assertNull(book.linkRepo)
        assertNull(book.syncedTo)
    }

    @Test
    fun testBookStatusAfterMultipleSyncsFollowingRemoteFileDeletion() {
        var book: BookView
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/book.org", "", "1abcdef", 1400067156)
        testUtils.sync()
        book = dataRepository.getBooks()[0]
        assertEquals(
            BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK.toString(),
            book.book.syncStatus
        )

        dbRepoBookRepository.deleteBook(Uri.parse("mock://repo-a/book.org"))
        testUtils.sync()
        testUtils.sync()
        book = dataRepository.getBooks()[0]
        assertNull(book.linkRepo)
        assertEquals(
            BookSyncStatus.BOOK_WITH_PREVIOUS_ERROR_AND_NO_LINK.toString(),
            book.book.syncStatus
        )
    }

    @Test
    fun testSpaceSeparatedBookName() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/Book%20Name.org", "", "1abcdef", 1400067155)

        testUtils.sync()

        val bookView = dataRepository.getBooks()[0]
        assertNotNull(bookView.syncedTo)
        assertEquals("Book Name", bookView.book.name)
        assertEquals("mock://repo-a/Book%20Name.org", bookView.syncedTo?.uri.toString())
        assertEquals(
            "Loaded from mock://repo-a/Book%20Name.org",
            bookView.book.lastAction?.message
        )
    }

    @Test
    fun testForceLoadingBookWithLink() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/booky.org", "New content", "abc", 1234567890000L)
        val book = testUtils.setupBook("booky", "First book used for testing\n* Note A").book
        dataRepository.setLink(book.id, repo)
        dataRepository.forceLoadBook(book.id)

        assertEquals(
            context.getString(R.string.force_loaded_from_uri, "mock://repo-a/booky.org"),
            dataRepository.getBook(book.name)?.lastAction?.message
        )
        assertEquals("New content\n\n", dataRepository.getBookContent("booky", BookFormat.ORG))
    }

    @Test
    fun testForceLoadBookWithSpaceInName() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/Book%20Name.org", "", "1abcdef", 1400067155)

        testUtils.sync()

        val bookView = dataRepository.getBooks()[0]
        assertEquals("Book Name", bookView.book.name)

        dataRepository.forceLoadBook(bookView.book.id)
        assertEquals("Book Name", dataRepository.getBooks()[0].book.name)
    }

    @Test
    fun testForceLoadingBookWithNoLinkNoRepos() {
        val book = testUtils.setupBook("booky", "First book used for testing\n* Note A")

        val exception = assertThrows(IOException::class.java) {
            dataRepository.forceLoadBook(book.book.id)
        }
        assertEquals(
            "Force-loading failed: ${context.getString(R.string.message_book_has_no_link)}",
            exception.message
        )
    }

    @Test
    fun testForceLoadingBookWithNoLinkSingleRepo() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val book = testUtils.setupBook("booky", "First book used for testing\n* Note A")

        val exception = assertThrows(IOException::class.java) {
            dataRepository.forceLoadBook(book.book.id)
        }
        assertEquals(
            "Force-loading failed: ${context.getString(R.string.message_book_has_no_link)}",
            exception.message
        )
    }

    @Test
    fun testForceLoadingMultipleTimes() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRook(repo, "mock://repo-a/book-one.org", "New content", "abc", 1234567890000L)
        val book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").book
        dataRepository.setLink(book.id, repo)
        dataRepository.forceLoadBook(book.id)
        assertEquals(
            context.getString(R.string.force_loaded_from_uri, "mock://repo-a/book-one.org"),
            dataRepository.getBook(book.id)?.lastAction?.message
        )
        dataRepository.forceLoadBook(book.id)
        assertEquals(
            context.getString(R.string.force_loaded_from_uri, "mock://repo-a/book-one.org"),
            dataRepository.getBook(book.id)?.lastAction?.message
        )
    }

    @Test
    fun testForceSavingBookWithNoLinkAndMultipleRepos() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b")
        val book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").book
        val exception = assertThrows(IOException::class.java) {
            dataRepository.forceSaveBook(book.id)
        }
        assertEquals(
            context.getString(
                R.string.force_saving_failed,
                context.getString(R.string.multiple_repos)
            ),
            exception.message
        )
    }

    @Test
    fun testForceSavingBookWithNoLinkNoRepos() {
        val book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").book
        val exception = assertThrows(IOException::class.java) {
            dataRepository.forceSaveBook(book.id)
        }
        assertEquals(
            context.getString(R.string.force_saving_failed, context.getString(R.string.no_repos)),
            exception.message
        )
    }

    @Test
    fun testForceSavingBookWithNoLinkSingleRepo() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").book
        dataRepository.forceSaveBook(book.id)
        assertEquals(
            context.getString(R.string.force_saved_to_uri, "mock://repo-a/book-one.org"),
            dataRepository.getBook(book.id)?.lastAction?.message
        )
    }

    @Test
    fun testForceSavingBookWithLink() {
        val repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a")
        val book = testUtils.setupBook("booky", "First book used for testing\n* Note A", repo).book
        dataRepository.setLink(book.id, repo)
        dataRepository.forceSaveBook(book.id)
        assertEquals(
            context.getString(R.string.force_saved_to_uri, "mock://repo-a/booky.org"),
            dataRepository.getBook(book.id)?.lastAction?.message
        )
    }
}
