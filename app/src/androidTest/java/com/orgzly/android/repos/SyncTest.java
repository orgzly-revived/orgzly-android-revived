package com.orgzly.android.repos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.Uri;
import android.os.Build;

import com.orgzly.R;
import com.orgzly.android.BookFormat;
import com.orgzly.android.BookName;
import com.orgzly.android.LocalStorage;
import com.orgzly.android.OrgzlyTest;
import com.orgzly.android.db.entity.Book;
import com.orgzly.android.db.entity.BookView;
import com.orgzly.android.db.entity.NoteView;
import com.orgzly.android.db.entity.Repo;
import com.orgzly.android.sync.BookNamesake;
import com.orgzly.android.sync.BookSyncStatus;
import com.orgzly.android.util.EncodingDetect;
import com.orgzly.android.util.MiscUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyncTest extends OrgzlyTest {
    private static final String TAG = SyncTest.class.getName();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testOrgRange() {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(
                repo,
                "mock://repo-a/remote-book-1.org",
                "* Note\nSCHEDULED: <2015-01-13 уто 13:00-14:14>--<2015-01-14 сре 14:10-15:20>",
                "0abcdef",
                1400067156);

        testUtils.sync();

        NoteView noteView = dataRepository.getLastNoteView("Note");
        assertEquals(
                "<2015-01-13 уто 13:00-14:14>--<2015-01-14 сре 14:10-15:20>",
                noteView.getScheduledRangeString());
    }

    @Test
    public void testSync1() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupBook("todo", "hum hum");

        assertEquals(1, dataRepository.getRepos().size());
        assertEquals(1, dataRepository.getBooks().size());
        assertNull(dataRepository.getBooks().get(0).getSyncedTo());

        testUtils.sync();

        assertEquals(1, dataRepository.getRepos().size());
        assertEquals(1, dataRepository.getBooks().size());
        assertNotNull(dataRepository.getBooks().get(0).getSyncedTo());
        assertEquals("mock://repo-a/todo.org", dataRepository.getBooks().get(0).getSyncedTo().getUri().toString());
    }

    @Test
    public void testSync2() {
        /* Add remote books. */
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/remote-book-1.org", "", "1abcdef", 1400067156);
        testUtils.setupRook(repo, "mock://repo-a/remote-book-2.org", "", "2abcdef", 1400067156);

        assertEquals(1, dataRepository.getRepos().size());
        assertEquals(2, dbRepoBookRepository.getBooks(repo.getId(), Uri.parse("mock://repo-a")).size());
        assertEquals("mock://repo-a", dbRepoBookRepository.getBooks(
                repo.getId(), Uri.parse("mock://repo-a")).get(0).getRepoUri().toString());
        assertEquals(0, dataRepository.getBooks().size());

        /* Sync. */
        Map<String, BookNamesake> g1 = testUtils.sync();
        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK, g1.get("remote-book-1").getStatus());
        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK, g1.get("remote-book-2").getStatus());

        assertEquals(1, dataRepository.getRepos().size());
        assertEquals(2, dbRepoBookRepository.getBooks(
                repo.getId(), Uri.parse("mock://repo-a")).size());
        assertEquals("mock://repo-a", dbRepoBookRepository.getBooks(
                repo.getId(), Uri.parse("mock://repo-a")).get(0).getRepoUri().toString());
        assertEquals(2, dataRepository.getBooks().size());

        /* Sync. */
        Map<String, BookNamesake> g2 = testUtils.sync();
        assertEquals(BookSyncStatus.NO_CHANGE, g2.get("remote-book-1").getStatus());
        assertEquals(BookSyncStatus.NO_CHANGE, g2.get("remote-book-2").getStatus());

        assertEquals(1, dataRepository.getRepos().size());
        assertEquals(2, dbRepoBookRepository.getBooks(
                repo.getId(), Uri.parse("mock://repo-a")).size());
        assertEquals("mock://repo-a", dbRepoBookRepository.getBooks(
                repo.getId(), Uri.parse("mock://repo-a")).get(0).getRepoUri().toString());
        assertEquals(2, dataRepository.getBooks().size());
    }

    @Test
    public void testRenameUsedRepo() {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/book.org", "Content A", "1abcde", 1400067156000L);

        BookView book;

        testUtils.sync();

        testUtils.renameRepo("mock://repo-a", "mock://repo-b");

        book = dataRepository.getBookView("book");
        assertNull(book.getLinkRepo());
        assertNull(book.getSyncedTo());

        testUtils.sync();

        book = dataRepository.getBookView("book");
        assertEquals(BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO.toString(), book.getBook().getSyncStatus());
        assertEquals("mock://repo-b", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-b", book.getSyncedTo().getRepoUri().toString());
        assertEquals("mock://repo-b/book.org", book.getSyncedTo().getUri().toString());

        testUtils.renameRepo("mock://repo-b", "mock://repo-a");
        testUtils.sync();

        book = dataRepository.getBookView("book");
        assertEquals(BookSyncStatus.BOOK_WITHOUT_LINK_AND_ONE_OR_MORE_ROOKS_EXIST.toString(), book.getBook().getSyncStatus());
        assertNull(book.getLinkRepo());
        assertNull("mock://repo-b/book.org", book.getSyncedTo());
    }

    @Test
    public void testDeletingUsedRepo() {
        BookView book;

        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/book.org", "Content A", "1abcde", 1400067156000L);
        testUtils.sync();

        testUtils.deleteRepo("mock://repo-a");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        testUtils.sync();

        book = dataRepository.getBookView("book");
        assertEquals(BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO.toString(), book.getBook().getSyncStatus());
        assertEquals("mock://repo-b", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-b", book.getSyncedTo().getRepoUri().toString());
        assertEquals("mock://repo-b/book.org", book.getSyncedTo().getUri().toString());

        testUtils.deleteRepo("mock://repo-b");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.sync();

        book = dataRepository.getBookView("book");
        assertEquals(BookSyncStatus.BOOK_WITHOUT_LINK_AND_ONE_OR_MORE_ROOKS_EXIST.toString(), book.getBook().getSyncStatus());
        assertNull(book.getLinkRepo());
        assertNull("mock://repo-b/book.org", book.getSyncedTo());
    }

    @Test
    public void testEncodingStaysTheSameAfterSecondSync() {
        BookView book;

        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/book.org", "Content A", "1abcde", 1400067156000L);

        testUtils.sync();

        book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK.toString(), book.getBook().getSyncStatus());

        switch (EncodingDetect.USED_METHOD) {
//            case JCHARDET:
//                assertEquals("ASCII", versionedRook.getDetectedEncoding());
//                assertEquals("ASCII", versionedRook.getUsedEncoding());
            case JUNIVERSALCHARDET:
                assertNull(book.getBook().getDetectedEncoding());
                assertEquals("UTF-8", book.getBook().getUsedEncoding());
                break;
        }
        assertNull(book.getBook().getSelectedEncoding());

        testUtils.sync();

        book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.NO_CHANGE.toString(), book.getBook().getSyncStatus());

        switch (EncodingDetect.USED_METHOD) {
//            case JCHARDET:
//                assertEquals("ASCII", versionedRook.getDetectedEncoding());
//                assertEquals("ASCII", versionedRook.getUsedEncoding());
//                break;
            case JUNIVERSALCHARDET:
                assertNull(book.getBook().getDetectedEncoding());
                assertEquals("UTF-8", book.getBook().getUsedEncoding());
                break;
        }
        assertNull(book.getBook().getSelectedEncoding());
    }

    @Test
    public void testOnlyBookWithLink() {
        Repo repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");

        BookView book = testUtils.setupBook("book-1", "Content");
        dataRepository.setLink(book.getBook().getId(), repoA);

        testUtils.sync();

        book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.ONLY_BOOK_WITH_LINK.toString(), book.getBook().getSyncStatus());
    }

    @Test
    public void testOnlyBookWithoutLinkAndOneRepo() throws IOException {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupBook("book-1", "Content");
        testUtils.sync();

        BookView book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO.toString(), book.getBook().getSyncStatus());
        assertEquals(context.getString(R.string.sync_status_saved, repo.getUrl()),
                book.getBook().getLastAction().getMessage());
        assertEquals(repo.getUrl(), book.getLinkRepo().getUrl());
        SyncRepo syncRepo = testUtils.repoInstance(RepoType.MOCK, repo.getUrl());
        assertEquals(1, syncRepo.getBooks().size());
        assertEquals(syncRepo.getBooks().get(0).toString(), book.getSyncedTo().toString());
    }

    @Test
    public void testOnlyBookWithoutLinkAndMultipleRepos() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        testUtils.setupBook("book-1", "Content");
        testUtils.sync();

        BookView book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_MULTIPLE_REPOS.toString(),
                book.getBook().getSyncStatus());
    }

    @Test
    public void testMultipleRooks() {
        Repo repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repoA, "mock://repo-a/book.org", "Content A", "revA", 1234567890000L);

        Repo repoB = testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        testUtils.setupRook(repoB, "mock://repo-b/book.org", "Content B", "revB", 1234567890000L);

        testUtils.sync();

        BookView book = dataRepository.getBooks().get(0);

        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_MULTIPLE_ROOKS.toString(), book.getBook().getSyncStatus());
        assertTrue(book.getBook().isDummy());

        dataRepository.setLink(book.getBook().getId(), repoA);


        testUtils.sync();

        book = dataRepository.getBooks().get(0);

        assertEquals(BookSyncStatus.DUMMY_WITH_LINK.toString(), book.getBook().getSyncStatus());
        assertTrue(!book.getBook().isDummy());
        assertEquals("mock://repo-a/book.org", book.getSyncedTo().getUri().toString());
    }

    @Test
    public void testMtimeOfLoadedBook() {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/book.org", "Content", "rev", 1234567890000L);

        testUtils.sync();

        BookView book = dataRepository.getBooks().get(0);

        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK.toString(), book.getBook().getSyncStatus());
        assertEquals(1234567890000L, book.getBook().getMtime().longValue());
    }

    @Test
    public void testDummyShouldNotBeSavedWhenHavingOneRepo() {
        Repo repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        Repo repoB = testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        testUtils.setupRook(repoA, "mock://repo-a/booky.org", "", "1abcdef", 1400067155);
        testUtils.setupRook(repoB, "mock://repo-b/booky.org", "", "2abcdef", 1400067156);

        Book book;
        Map<String, BookNamesake> namesakes;

        namesakes = testUtils.sync();
        book = dataRepository.getBook("booky");

        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_MULTIPLE_ROOKS, namesakes.get("booky").getStatus());
        assertTrue(book.isDummy());

        testUtils.deleteRepo("mock://repo-a");
        testUtils.deleteRepo("mock://repo-b");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-c");

        namesakes = testUtils.sync();
        book = dataRepository.getBook("booky");

        assertEquals(BookSyncStatus.ONLY_DUMMY, namesakes.get("booky").getStatus());
        // TODO: We should delete it, no point of having a dummy and no remote book

        assertTrue(book.isDummy());
    }

    @Test
    public void testDeletedRepoShouldStayAsBookLink() {
        Repo repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        testUtils.setupRook(repoA, "mock://repo-a/booky.org", "", "1abcdef", 1400067155);

        BookView book;
        Map<String, BookNamesake> namesakes;

        namesakes = testUtils.sync();
        book = dataRepository.getBookView("booky");

        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK, namesakes.get("booky").getStatus());

        assertFalse(book.getBook().isDummy());
        assertEquals("mock://repo-a", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-a", book.getSyncedTo().getRepoUri().toString());

        testUtils.deleteRepo("mock://repo-a");
        testUtils.deleteRepo("mock://repo-b");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-c");

        namesakes = testUtils.sync(); // TODO: Don't use namesakes, be consistent and use book.status like in some methods
        book = dataRepository.getBookView("booky");

        assertEquals(BookSyncStatus.ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO, namesakes.get("booky").getStatus());

        assertFalse(book.getBook().isDummy());
        assertEquals("mock://repo-c", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-c", book.getSyncedTo().getRepoUri().toString());
    }

//    public void testEncodingOnSyncSavingStaysTheSame() {
//        setup.setupRepo("mock://repo-a");
//        setup.setupRook("mock://repo-a", "mock://repo-a/book.org", "Content A", "1abcde", 1400067156000L);
//        sync();
//        setup.renameRepo("mock://repo-a", "mock://repo-b");
//        sync();
//        VersionedRook vrook = CurrentRooksHelper.get(testContext, "mock://repo-b/book.org");
//        assertNull(vrook.getDetectedEncoding());
//        assertEquals("UTF-8", vrook.getUsedEncoding());
//
//    }

    @Test
    public void testSyncingOrgTxt() {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/booky.org.txt", "", "1abcdef", 1400067155);

        testUtils.sync();

        BookView book = dataRepository.getBookView("booky");
        assertEquals("mock://repo-a", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-a", book.getSyncedTo().getRepoUri().toString());
        assertEquals("mock://repo-a/booky.org.txt", book.getSyncedTo().getUri().toString());
    }

    @Test
    public void testMockFileRename() throws IOException {
        List<VersionedRook> vrooks;

        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        SyncRepo repo = testUtils.repoInstance(RepoType.MOCK, "mock://repo-a");

        BookView book = testUtils.setupBook("Booky", "1 2 3");

        testUtils.sync();

        vrooks = repo.getBooks();

        assertEquals(1, vrooks.size());
        assertEquals("Booky", BookName.fromRook(vrooks.get(0)).getName());

        long mtime = vrooks.get(0).getMtime();
        String rev = vrooks.get(0).getRevision();

        // Rename local notebook
        dataRepository.renameBook(book, "BookyRenamed");

        // Rename rook
        repo.renameBook(Uri.parse("mock://repo-a/Booky.org"), "BookyRenamed");

        vrooks = repo.getBooks();

        assertEquals(1, vrooks.size());
        assertEquals("BookyRenamed", BookName.fromRook(vrooks.get(0)).getName());
        assertEquals("mock://repo-a/BookyRenamed.org", vrooks.get(0).getUri().toString());
        assertTrue(mtime < vrooks.get(0).getMtime());
        assertNotSame(rev, vrooks.get(0).getRevision());
    }

    @Test
    public void testDirectoryFileRename() throws IOException {
        String uuid = UUID.randomUUID().toString();

        String repoDir = context.getCacheDir() + "/" + uuid;

        SyncRepo repo = testUtils.repoInstance(RepoType.DIRECTORY, "file:" + repoDir);

        assertNotNull(repo);
        assertEquals(0, repo.getBooks().size());

        File file = File.createTempFile("notebook.", ".org");
        MiscUtils.writeStringToFile("1 2 3", file);

        VersionedRook vrook = repo.storeBook(file, file.getName());

        file.delete();

        assertEquals(1, repo.getBooks().size());

        repo.renameBook(vrook.getUri(), "notebook-renamed");

        assertEquals(1, repo.getBooks().size());
        assertEquals(repo.getUri() + "/notebook-renamed.org", repo.getBooks().get(0).getUri().toString());
        assertEquals("notebook-renamed.org", BookName.fromRook(repo.getBooks().get(0)).getRepoRelativePath());

        LocalStorage.deleteRecursive(new File(repoDir));
    }

    @Test
    public void testRenameSyncedBook() throws IOException {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupBook("Booky", "1 2 3");

        testUtils.sync();

        BookView book = dataRepository.getBookView("Booky");

        assertEquals("mock://repo-a/Booky.org", book.getSyncedTo().getUri().toString());

        dataRepository.renameBook(book, "BookyRenamed");

        BookView renamedBook = dataRepository.getBookView("BookyRenamed");

        assertNotNull(renamedBook);
        assertEquals("mock://repo-a", renamedBook.getLinkRepo().getUrl());
        assertEquals("mock://repo-a", renamedBook.getSyncedTo().getRepoUri().toString());
        assertEquals("mock://repo-a/BookyRenamed.org", renamedBook.getSyncedTo().getUri().toString());
        assertEquals("1 2 3\n\n", dataRepository.getBookContent("BookyRenamed", BookFormat.ORG));
    }

    @Test
    public void testRenameBookToNameWithSpace() throws IOException {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupBook("Booky", "1 2 3");

        testUtils.sync();

        BookView book = dataRepository.getBookView("Booky");

        assertEquals("mock://repo-a/Booky.org", book.getSyncedTo().getUri().toString());

        dataRepository.renameBook(book, "Booky Renamed");

        BookView renamedBook = dataRepository.getBookView("Booky Renamed");

        assertNotNull(renamedBook);
        assertEquals("mock://repo-a", renamedBook.getLinkRepo().getUrl());
        assertEquals("mock://repo-a", renamedBook.getSyncedTo().getRepoUri().toString());
        assertEquals("mock://repo-a/Booky%20Renamed.org",
                renamedBook.getSyncedTo().getUri().toString());
        assertEquals("1 2 3\n\n", dataRepository.getBookContent("Booky Renamed", BookFormat.ORG));
    }

    @Test
    public void testRenameSyncedBookWithDifferentLink() throws IOException {
        BookView book;

        Repo repoA = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        Repo repoB = testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        book = testUtils.setupBook("Booky", "1 2 3");
        dataRepository.setLink(book.getBook().getId(), repoA);

        testUtils.sync();

        book = dataRepository.getBooks().get(0);

        assertEquals(1, testUtils.repoInstance(RepoType.MOCK, "mock://repo-a").getBooks().size());
        assertEquals(0, testUtils.repoInstance(RepoType.MOCK, "mock://repo-b").getBooks().size());
        assertEquals("mock://repo-a", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-a", book.getSyncedTo().getRepoUri().toString());
        assertEquals("mock://repo-a/Booky.org", book.getSyncedTo().getUri().toString());

        dataRepository.setLink(book.getBook().getId(), repoB);

        book = dataRepository.getBooks().get(0);

        dataRepository.renameBook(book, "BookyRenamed");

        book = dataRepository.getBooks().get(0);

        assertEquals("Booky", book.getBook().getName());
        assertEquals(BookSyncStatus.ROOK_AND_VROOK_HAVE_DIFFERENT_REPOS.toString(), book.getBook().getSyncStatus());
        assertEquals("mock://repo-b", book.getLinkRepo().getUrl());
        assertEquals("mock://repo-a/Booky.org", book.getSyncedTo().getUri().toString());
    }

    @Test
    public void testRenameBookToExistingBookName() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupBook("a", "");
        testUtils.setupBook("b", "");
        assertEquals(2, dataRepository.getBooks().size());
        dataRepository.renameBook(dataRepository.getBookView("a"), "b");
        assertTrue(dataRepository.getBook("a")
                .getLastAction()
                .getMessage()
                .contains("Renaming failed: Notebook b already exists")
        );
    }

    @Test
    public void testIgnoreRulePreventsRenamingBook() {
        assumeTrue(Build.VERSION.SDK_INT >= 26);
        String ignoreRules = "bad name*\n";
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");

        // Add ignore rules using repo properties (N.B. MockRepo-specific solution)
        Map<String, String> repoPropsMap = new HashMap<>();
        repoPropsMap.put(MockRepo.IGNORE_RULES_PREF_KEY, ignoreRules);
        RepoWithProps repoWithProps = new RepoWithProps(repo, repoPropsMap);
        dataRepository.updateRepo(repoWithProps);

        // Create book and sync it
        testUtils.setupBook("good name", "");
        testUtils.sync();
        BookView bookView = dataRepository.getBookView("good name");

        dataRepository.renameBook(bookView, "bad name");
        bookView = dataRepository.getBooks().get(0);
        assertTrue(bookView.getBook()
                .getLastAction()
                .toString()
                .contains("matches a rule in .orgzlyignore"));
    }

    @Test
    public void testIgnoreRulePreventsLinkingBook() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= 26);
        String ignoreRules = "*.org\n";
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");

        // Add ignore rules using repo properties (N.B. MockRepo-specific solution)
        Map<String, String> repoPropsMap = new HashMap<>();
        repoPropsMap.put(MockRepo.IGNORE_RULES_PREF_KEY, ignoreRules);
        RepoWithProps repoWithProps = new RepoWithProps(repo, repoPropsMap);
        dataRepository.updateRepo(repoWithProps);

        // Create book and sync it
        testUtils.setupBook("booky", "");
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage("matches a rule in .orgzlyignore");
        testUtils.syncOrThrow();
    }


    /**
     * Ensures that file names and book names are not parsed/created differently during
     * force-loading.
     */
    @Test
    public void testForceLoadBookInSubfolder() throws IOException {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        BookView bookView = testUtils.setupBook("a folder/a book", "content");
        testUtils.sync();
        var books = dataRepository.getBooks();
        assertEquals(1, books.size());
        assertEquals("a folder/a book", books.get(0).getBook().getName());
        dataRepository.forceLoadBook(bookView.getBook().getId());
        books = dataRepository.getBooks();
        assertEquals(1, books.size());
        // Check that the name has not changed
        assertEquals("a folder/a book", books.get(0).getBook().getName());
    }

    /**
     * We remove the local book's' syncedTo attribute and repository link when its remote file
     * has been deleted, to make it easier to ascertain the book's state during subsequent sync
     * attempts.
     */
    @Test
    public void testRemoteFileDeletion() throws IOException {
        BookView book;
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/book.org", "", "1abcdef", 1400067156);
        testUtils.sync();
        book = dataRepository.getBooks().get(0);
        assertNotNull(book.getLinkRepo());
        assertNotNull(book.getSyncedTo());
        dbRepoBookRepository.deleteBook(Uri.parse("mock://repo-a/book.org"));
        testUtils.sync();
        book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.ROOK_NO_LONGER_EXISTS.toString(), book.getBook().getSyncStatus());
        assertNull(book.getLinkRepo());
        assertNull(book.getSyncedTo());
    }

    /**
     * The "remote file has been deleted" error status is only shown once, and then the book's
     * repo link is removed. Subsequent syncing of the same book should result in a more general
     * message, indicating that the user may sync the book again by explicitly setting a repo link.
     */
    @Test
    public void testBookStatusAfterMultipleSyncsFollowingRemoteFileDeletion() throws IOException {
        BookView book;
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/book.org", "", "1abcdef", 1400067156);
        testUtils.sync();
        book = dataRepository.getBooks().get(0);
        assertEquals(BookSyncStatus.DUMMY_WITHOUT_LINK_AND_ONE_ROOK.toString(), book.getBook().getSyncStatus());

        dbRepoBookRepository.deleteBook(Uri.parse("mock://repo-a/book.org"));
        testUtils.sync();
        testUtils.sync();
        book = dataRepository.getBooks().get(0);
        assertNull(book.getLinkRepo());
        assertEquals(BookSyncStatus.BOOK_WITH_PREVIOUS_ERROR_AND_NO_LINK.toString(), book.getBook().getSyncStatus());
    }

    @Test
    public void testSpaceSeparatedBookName() {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/Book%20Name.org", "", "1abcdef", 1400067155);

        testUtils.sync();

        BookView bookView = dataRepository.getBooks().get(0);
        assertNotNull(bookView.getSyncedTo());
        assertEquals("Book Name", bookView.getBook().getName());
        assertEquals("mock://repo-a/Book%20Name.org", bookView.getSyncedTo().getUri().toString());
        assertEquals("Loaded from mock://repo-a/Book%20Name.org",
                bookView.getBook().getLastAction().getMessage());
    }

    @Test(expected = IOException.class)
    public void testForceLoadingBookWithLinkButNeverSynced() throws IOException {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/booky.org", "New content", "abc", 1234567890000L);
        Book book = testUtils.setupBook("booky", "First book used for testing\n* Note A").getBook();
        dataRepository.setLink(book.getId(), repo);
        try {
            dataRepository.forceLoadBook(book.getId());
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Notebook has never been synced before"));
            throw new IOException(e);
        }
    }

    /**
     * To ensure that book names are not parsed/constructed differently during force load
     */
    @Test
    public void testForceLoadBookWithSpaceInName() throws IOException {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRook(repo, "mock://repo-a/Book%20Name.org", "", "1abcdef", 1400067155);

        testUtils.sync();

        BookView bookView = dataRepository.getBooks().get(0);
        assertEquals("Book Name", bookView.getBook().getName());

        dataRepository.forceLoadBook(bookView.getBook().getId());
        assertEquals("Book Name", dataRepository.getBooks().get(0).getBook().getName());
    }

    @Test
    public void testForceLoadingBookWithNoLinkNoRepos() throws IOException {
        BookView book = testUtils.setupBook("booky", "First book used for testing\n* Note A");

        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage(context.getString(R.string.message_book_has_no_link));
        dataRepository.forceLoadBook(book.getBook().getId());
    }

    @Test
    public void testForceLoadingBookWithNoLinkSingleRepo() throws IOException {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        BookView book = testUtils.setupBook("booky", "First book used for testing\n* Note A");

        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage(context.getString(R.string.message_book_has_no_link));
        dataRepository.forceLoadBook(book.getBook().getId());
    }

    @Test
    public void testForceSavingBookWithNoLinkAndMultipleRepos() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-b");
        Book book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").getBook();
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage(context.getString(R.string.force_saving_failed, context.getString(R.string.multiple_repos)));
        dataRepository.forceSaveBook(book.getId());
    }

    @Test
    public void testForceSavingBookWithNoLinkNoRepos() {
        Book book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").getBook();
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage(context.getString(R.string.force_saving_failed, context.getString(R.string.no_repos)));
        dataRepository.forceSaveBook(book.getId());
    }

    @Test
    public void testForceSavingBookWithNoLinkSingleRepo() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        Book book = testUtils.setupBook("book-one", "First book used for testing\n* Note A").getBook();
        dataRepository.forceSaveBook(book.getId());
        assertEquals(context.getString(R.string.force_saved_to_uri, "mock://repo-a/book-one.org")
                , dataRepository.getBook(book.getId()).getLastAction().getMessage());
    }

    @Test
    public void testForceSavingBookWithLink() {
        Repo repo = testUtils.setupRepo(RepoType.MOCK, "mock://repo-a");
        Book book = testUtils.setupBook("booky", "First book used for testing\n* Note A", repo).getBook();
        dataRepository.setLink(book.getId(), repo);
        dataRepository.forceSaveBook(book.getId());
        assertEquals(context.getString(R.string.force_saved_to_uri, "mock://repo-a/booky.org")
                , dataRepository.getBook(book.getId()).getLastAction().getMessage());
    }
}
