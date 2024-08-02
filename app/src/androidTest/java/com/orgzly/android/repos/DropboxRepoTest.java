package com.orgzly.android.repos;

import com.orgzly.android.App;
import com.orgzly.android.BookName;
import com.orgzly.android.OrgzlyTest;
import com.orgzly.android.db.entity.BookView;
import com.orgzly.android.util.MiscUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

public class DropboxRepoTest extends OrgzlyTest {
    private static final String DROPBOX_TEST_DIR = "/orgzly-android-tests";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        testUtils.dropboxTestPreflight();
    }

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testUrl() {
        assertEquals(
                "dropbox:/dir",
                testUtils.repoInstance(RepoType.DROPBOX, "dropbox:/dir").getUri().toString());
    }

    @Test
    public void testSyncingUrlWithTrailingSlash() throws IOException {
        testUtils.setupRepo(RepoType.DROPBOX, randomUrl() + "/");
        assertNotNull(testUtils.sync());
    }

    @Test
    public void testRenameBook() throws IOException {
        BookView bookView;
        String repoUriString = testUtils.repoInstance(RepoType.DROPBOX, randomUrl()).getUri().toString();

        testUtils.setupRepo(RepoType.DROPBOX, repoUriString);
        testUtils.setupBook("booky", "");

        testUtils.sync();
        bookView = dataRepository.getBookView("booky");

        assertEquals(repoUriString, bookView.getLinkRepo().getUrl());
        assertEquals(repoUriString, bookView.getSyncedTo().getRepoUri().toString());
        assertEquals(repoUriString + "/booky.org", bookView.getSyncedTo().getUri().toString());

        dataRepository.renameBook(bookView, "booky-renamed");
        bookView = dataRepository.getBookView("booky-renamed");

        assertEquals(repoUriString, bookView.getLinkRepo().getUrl());
        assertEquals(repoUriString, bookView.getSyncedTo().getRepoUri().toString());
        assertEquals(repoUriString + "/booky-renamed.org", bookView.getSyncedTo().getUri().toString());
    }

    @Test
    public void testIgnoreRulePreventsLinkingBook() throws Exception {
        Uri repoUri = testUtils.repoInstance(RepoType.DROPBOX, randomUrl()).getUri();
        testUtils.setupRepo(RepoType.DROPBOX, repoUri.toString());
        uploadFileToRepo(repoUri, RepoIgnoreNode.IGNORE_FILE, "*.org");
        testUtils.setupBook("booky", "");
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage("matches a rule in .orgzlyignore");
        testUtils.syncOrThrow();
    }

    @Test
    public void testIgnoreRulePreventsLoadingBook() throws Exception {
        SyncRepo repo = testUtils.repoInstance(RepoType.DROPBOX, randomUrl());
        testUtils.setupRepo(RepoType.DROPBOX, repo.getUri().toString());

        // Create two .org files
        uploadFileToRepo(repo.getUri(), "ignored.org", "1 2 3");
        uploadFileToRepo(repo.getUri(), "notignored.org", "1 2 3");
        // Create .orgzlyignore
        uploadFileToRepo(repo.getUri(), RepoIgnoreNode.IGNORE_FILE, "ignored.org");
        testUtils.sync();

        List<BookView> bookViews = dataRepository.getBooks();
        assertEquals(1, bookViews.size());
        assertEquals("notignored", bookViews.get(0).getBook().getName());
    }

    @Test
    public void testIgnoreRulePreventsRenamingBook() throws Exception {
        BookView bookView;
        Uri repoUri = testUtils.repoInstance(RepoType.DROPBOX, randomUrl()).getUri();
        testUtils.setupRepo(RepoType.DROPBOX, repoUri.toString());
        uploadFileToRepo(repoUri, RepoIgnoreNode.IGNORE_FILE, "badname*");
        testUtils.setupBook("goodname", "");
        testUtils.sync();
        bookView = dataRepository.getBookView("goodname");
        dataRepository.renameBook(bookView, "badname");
        bookView = dataRepository.getBooks().get(0);
        assertTrue(
                bookView.getBook()
                        .getLastAction()
                        .toString()
                        .contains("matches a rule in .orgzlyignore")
        );
    }

    @Test
    public void testDropboxFileRename() throws IOException {
        SyncRepo repo = testUtils.repoInstance(RepoType.DROPBOX, randomUrl());

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
        assertEquals("notebook-renamed.org", BookName.getInstance(context, repo.getBooks().get(0)).getFileName());
    }

    private void uploadFileToRepo(Uri repoUri, String fileName, String fileContents) throws IOException {
        DropboxClient client = new DropboxClient(App.getAppContext(), 0);
        File tmpFile = File.createTempFile("abc", null);
        MiscUtils.writeStringToFile(fileContents, tmpFile);
        client.upload(tmpFile, repoUri, fileName);
        tmpFile.delete();
    }

    private String randomUrl() {
        return "dropbox:"+ DROPBOX_TEST_DIR + "/" + UUID.randomUUID().toString();
    }
}
