package com.orgzly.android.repos

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.ui.repos.ReposActivity
import org.hamcrest.core.AllOf
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DocumentRepoTest : SyncRepoTest, OrgzlyTest() {

    private lateinit var documentTreeSegment: String
    private lateinit var repo: Repo
    private lateinit var syncRepo: SyncRepo
    private lateinit var repoDirectory: DocumentFile

    @Before
    override fun setUp() {
        super.setUp()
        setupDocumentRepo()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        for (file in repoDirectory.listFiles()) {
            file.delete()
        }
    }

    @Test
    override fun testGetBooks_singleOrgFile() {
        SyncRepoTest.testGetBooks_singleOrgFile(repoDirectory, syncRepo)
    }

    @Test
    override fun testGetBooks_singleFileInSubfolderWhenEnabled() {
        SyncRepoTest.testGetBooks_singleFileInSubfolderWhenEnabled(repoDirectory, syncRepo)
    }

    @Test
    override fun testGetBooks_singleFileInSubfolderWhenDisabled() {
        SyncRepoTest.testGetBooks_singleFileInSubfolderWhenDisabled(repoDirectory, syncRepo)
    }

    @Test
    override fun testGetBooks_allFilesAreIgnored() {
        SyncRepoTest.testGetBooks_allFilesAreIgnored(repoDirectory, syncRepo)
    }

    @Test
    override fun testGetBooks_specificFileInSubfolderIsIgnored() {
        SyncRepoTest.testGetBooks_specificFileInSubfolderIsIgnored(repoDirectory, syncRepo)
    }

    @Test
    override fun testGetBooks_specificFileIsUnignored() {
        SyncRepoTest.testGetBooks_specificFileIsUnignored(repoDirectory, syncRepo)
    }

    @Test
    override fun testGetBooks_ignoredExtensions() {
        SyncRepoTest.testGetBooks_ignoredExtensions(repoDirectory, syncRepo)
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
        SyncRepoTest.testStoreBook_producesSameUriAsGetBooks(repoDirectory, syncRepo)
    }

    @Test
    override fun testStoreBook_inSubfolder() {
        SyncRepoTest.testStoreBook_inSubfolder(repoDirectory, syncRepo)
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
        SyncRepoTest.testRenameBook_repoFileAlreadyExists(repoDirectory, syncRepo)
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

    private fun setupDocumentRepo(extraDir: String? = null) {
        val repoDirName = SyncRepoTest.repoDirName
        documentTreeSegment = if (Build.VERSION.SDK_INT < 30) {
            "/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2F$repoDirName%2F"
        } else {
            "/document/primary%3A$repoDirName%2F"
        }
        var treeDocumentFileUrl = if (Build.VERSION.SDK_INT < 30) {
            "content://com.android.providers.downloads.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2F$repoDirName"
        } else {
            "content://com.android.externalstorage.documents/tree/primary%3A$repoDirName"
        }
        if (extraDir != null) {
            treeDocumentFileUrl = "$treeDocumentFileUrl%2F" + Uri.encode(extraDir)
        }
        repoDirectory = DocumentFile.fromTreeUri(context, treeDocumentFileUrl.toUri())!!
        repo = if (!repoDirectory.exists()) {
            if (extraDir != null) {
                setupDocumentRepoInUi(extraDir)
            } else {
                setupDocumentRepoInUi(repoDirName)
            }
            dataRepository.getRepos()[0]
        } else {
            testUtils.setupRepo(RepoType.DOCUMENT, treeDocumentFileUrl)
        }
        syncRepo = testUtils.repoInstance(RepoType.DOCUMENT, repo.url, repo.id)
        Assert.assertEquals(treeDocumentFileUrl, repo.url)
    }

    /**
     * Note that this solution only works the first time the tests are run on any given virtual
     * device. On the second run, the file picker will start in a different folder, resulting in
     * a different repo URL, making some tests fail. If you are running locally, you must work
     * around this by wiping the device's data between test suite runs.
     */
    private fun setupDocumentRepoInUi(repoDirName: String) {
        ActivityScenario.launch(ReposActivity::class.java).use {
            Espresso.onView(ViewMatchers.withId(R.id.activity_repos_directory))
                .perform(ViewActions.click())
            Espresso.onView(ViewMatchers.withId(R.id.activity_repo_directory_browse_button))
                .perform(ViewActions.click())
            SystemClock.sleep(500)
            // In Android file browser (Espresso cannot be used):
            val mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            if (Build.VERSION.SDK_INT < 30) {
                // Older system file picker UI
                mDevice.findObject(UiSelector().description("More options")).click()
                SystemClock.sleep(300)
                mDevice.findObject(UiSelector().text("New folder")).click()
                SystemClock.sleep(500)
                mDevice.findObject(UiSelector().text("Folder name")).text = repoDirName
                mDevice.findObject(UiSelector().text("OK")).click()
                mDevice.findObject(UiSelector().textContains("ALLOW ACCESS TO")).click()
                mDevice.findObject(UiSelector().text("ALLOW")).click()
            } else {
                mDevice.findObject(UiSelector().description("New folder")).click()
                SystemClock.sleep(500)
                mDevice.findObject(UiSelector().text("Folder name")).text = repoDirName
                mDevice.findObject(UiSelector().text("OK")).click()
                mDevice.findObject(UiSelector().text("USE THIS FOLDER")).click()
                mDevice.findObject(UiSelector().text("ALLOW")).click()
            }
            // Back in Orgzly:
            SystemClock.sleep(500)
            Espresso.onView(ViewMatchers.isRoot()).perform(EspressoUtils.waitId(R.id.fab, 5000))
            Espresso.onView(AllOf.allOf(ViewMatchers.withId(R.id.fab), ViewMatchers.isDisplayed()))
                .perform(ViewActions.click())
        }
    }
}