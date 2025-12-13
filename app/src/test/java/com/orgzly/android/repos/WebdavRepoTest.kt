package com.orgzly.android.repos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orgzly.android.RetryTestRule
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.repos.WebdavRepo.Companion.PASSWORD_PREF_KEY
import com.orgzly.android.repos.WebdavRepo.Companion.USERNAME_PREF_KEY
import io.github.atetzner.webdav.server.MiltonWebDAVFileServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException


@RunWith(AndroidJUnit4::class)
class WebdavRepoTest : SyncRepoTest {

    @get:Rule
    val retryTestRule = RetryTestRule(3)

    private val serverUrl = "http://localhost:8081"

    private lateinit var serverRootDir: File
    private lateinit var localServer: MiltonWebDAVFileServer
    private lateinit var syncRepo: SyncRepo
    private lateinit var tmpFile: File

    @Before
    fun setup() {
        serverRootDir = java.nio.file.Files.createTempDirectory("orgzly-webdav-test-").toFile()
        localServer = MiltonWebDAVFileServer(serverRootDir)
        localServer.userCredentials["user"] = "secret"
        localServer.start()
        val repo = Repo(0, RepoType.WEBDAV, serverUrl)
        val repoPropsMap = HashMap<String, String>()
        repoPropsMap[USERNAME_PREF_KEY] = "user"
        repoPropsMap[PASSWORD_PREF_KEY] = "secret"
        val repoWithProps = RepoWithProps(repo, repoPropsMap)
        syncRepo = WebdavRepo.getInstance(repoWithProps)
        assertEquals(serverUrl, repo.url)
        tmpFile = kotlin.io.path.createTempFile().toFile()
    }

    @After
    fun tearDown() {
        tmpFile.delete()
        if (this::localServer.isInitialized) {
            localServer.stop()
        }
        if (this::serverRootDir.isInitialized) {
            serverRootDir.deleteRecursively()
        }
    }

    @Test
    override fun testGetBooks_singleOrgFile() {
        SyncRepoTest.testGetBooks_singleOrgFile(serverRootDir, syncRepo)
    }

    @Test
    override fun testGetBooks_singleFileInSubfolderWhenEnabled() {
        SyncRepoTest.testGetBooks_singleFileInSubfolderWhenEnabled(serverRootDir, syncRepo)
    }

    @Test
    override fun testGetBooks_singleFileInSubfolderWhenDisabled() {
        SyncRepoTest.testGetBooks_singleFileInSubfolderWhenDisabled(serverRootDir, syncRepo)
    }

    @Test
    override fun testGetBooks_allFilesAreIgnored() {
        SyncRepoTest.testGetBooks_allFilesAreIgnored(serverRootDir, syncRepo)
    }

    @Test
    override fun testGetBooks_specificFileInSubfolderIsIgnored() {
        SyncRepoTest.testGetBooks_specificFileInSubfolderIsIgnored(serverRootDir, syncRepo)
    }

    @Test
    override fun testGetBooks_specificFileIsUnignored() {
        SyncRepoTest.testGetBooks_specificFileIsUnignored(serverRootDir, syncRepo)
    }

    @Test
    override fun testGetBooks_ignoredExtensions() {
        SyncRepoTest.testGetBooks_ignoredExtensions(serverRootDir, syncRepo)
    }

    @Test
    override fun testStoreBook_expectedUri() {
        SyncRepoTest.testStoreBook_expectedUri(syncRepo)
    }

    @Test
    override fun testStoreBook_producesSameUriAsRetrieveBookWithSubfolder() {
        SyncRepoTest.testStoreBook_producesSameUriAsRetrieveBookWithSubfolder(syncRepo)
    }

    @Test
    override fun testStoreBook_producesSameUriAsRetrieveBookWithoutSubfolder() {
        SyncRepoTest.testStoreBook_producesSameUriAsRetrieveBookWithoutSubfolder(syncRepo)
    }

    @Test
    override fun testStoreBook_producesSameUriAsGetBooks() {
        SyncRepoTest.testStoreBook_producesSameUriAsGetBooks(serverRootDir, syncRepo)
    }

    @Test
    override fun testStoreBook_inSubfolder() {
        SyncRepoTest.testStoreBook_inSubfolder(serverRootDir, syncRepo)
    }

    @Test(expected = IOException::class)
    override fun testStoreBook_inSubfolderWhenDisabled() {
        SyncRepoTest.testStoreBook_inSubfolderWhenDisabled(syncRepo)
    }

    @Test
    override fun testRenameBook_expectedUri() {
        SyncRepoTest.testRenameBook_expectedUri(syncRepo)
    }

    @Test(expected = IOException::class)
    override fun testRenameBook_repoFileAlreadyExists() {
        SyncRepoTest.testRenameBook_repoFileAlreadyExists(serverRootDir, syncRepo)
    }

    @Test
    override fun testRenameBook_fromRootToSubfolderWhenEnabled() {
        SyncRepoTest.testRenameBook_fromRootToSubfolderWhenEnabled(syncRepo)
    }

    @Test(expected = IOException::class)
    override fun testRenameBook_fromRootToSubfolderWhenDisabled() {
        SyncRepoTest.testRenameBook_fromRootToSubfolderWhenDisabled(syncRepo)
    }

    @Test
    override fun testRenameBook_fromSubfolderToRoot() {
        SyncRepoTest.testRenameBook_fromSubfolderToRoot(syncRepo)
    }

    @Test
    override fun testRenameBook_newSubfolderSameLeafName() {
        SyncRepoTest.testRenameBook_newSubfolderSameLeafName(syncRepo)
    }

    @Test
    override fun testRenameBook_newSubfolderAndLeafName() {
        SyncRepoTest.testRenameBook_newSubfolderAndLeafName(syncRepo)
    }

    @Test
    override fun testRenameBook_sameSubfolderNewLeafName() {
        SyncRepoTest.testRenameBook_sameSubfolderNewLeafName(syncRepo)
    }
}
