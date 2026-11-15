package com.orgzly.android.repos

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.git.GitFileSynchronizer
import com.orgzly.android.git.GitPreferencesFromRepoPrefs
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.RepoPreferences
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.pack.PackConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

@RunWith(AndroidJUnit4::class)
class GitRepoTest : SyncRepoTest {

    private lateinit var gitWorkingTree: File
    private lateinit var bareRepoDir: File
    private lateinit var gitFileSynchronizer: GitFileSynchronizer
    private lateinit var syncRepo: SyncRepo
    private lateinit var repoWithProps: RepoWithProps
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        bareRepoDir = createTempDirectory().toFile()
        Git.init().setBare(true).setDirectory(bareRepoDir).call()
        AppPreferences.gitIsEnabled(context, true)
        val repo = Repo(0, RepoType.GIT, "file://$bareRepoDir")
        val repoPreferences = RepoPreferences(context, repo.id, repo.url.toUri())
        val gitPreferences = GitPreferencesFromRepoPrefs(repoPreferences)
        gitWorkingTree = File(gitPreferences.repositoryFilepath())
        gitWorkingTree.mkdirs()
        val git = GitRepo.ensureRepositoryExists(gitPreferences, true, null)
        gitFileSynchronizer = GitFileSynchronizer(git, gitPreferences)
        val repoPropsMap = HashMap<String, String>()
        repoWithProps = RepoWithProps(repo, repoPropsMap)
        syncRepo = GitRepo.getInstance(repoWithProps, context)
    }

    @After
    fun tearDown() {
        gitWorkingTree.deleteRecursively()
        bareRepoDir.deleteRecursively()
    }

    @Test
    @Config(sdk = [31])
    fun testInitialization_disablesDeltaCompressionOnAndroid12() {
        Git.open(gitWorkingTree).use { git ->
            val config = git.repository.config
            assertEquals("false", config.getString("pack", null, "deltaCompression"))
            assertFalse(PackConfig(git.repository).isDeltaCompress)
            assertEquals(256, config.getInt("gc", null, "auto", 0))
            assertEquals(repoWithProps.repo.url, config.getString("remote", "origin", "url"))
            assertEquals("orgzly", config.getString("user", null, "name"))
        }
    }

    @Test
    @Config(sdk = [31])
    fun testInitialization_overridesExistingDeltaCompressionOnAndroid12() {
        Git.open(gitWorkingTree).use { git ->
            git.repository.config.apply {
                setBoolean("pack", null, "deltaCompression", true)
                save()
            }
        }

        repeat(2) {
            GitRepo.getInstance(repoWithProps, context)
            Git.open(gitWorkingTree).use { git ->
                assertEquals("false", git.repository.config.getString("pack", null, "deltaCompression"))
                assertFalse(PackConfig(git.repository).isDeltaCompress)
            }
        }
    }

    @Test
    @Config(sdk = [30, 32, 33])
    fun testInitialization_preservesDeltaCompressionOnOtherAndroidVersions() {
        Git.open(gitWorkingTree).use { git ->
            assertNull(git.repository.config.getString("pack", null, "deltaCompression"))
            assertEquals(true, PackConfig(git.repository).isDeltaCompress)
        }

        for (enabled in listOf(true, false)) {
            Git.open(gitWorkingTree).use { git ->
                git.repository.config.apply {
                    setBoolean("pack", null, "deltaCompression", enabled)
                    save()
                }
            }
            GitRepo.getInstance(repoWithProps, context)
            Git.open(gitWorkingTree).use { git ->
                assertEquals(enabled.toString(), git.repository.config.getString("pack", null, "deltaCompression"))
                assertEquals(enabled, PackConfig(git.repository).isDeltaCompress)
            }
        }
    }

    @Test
    override fun testGetBooks_singleOrgFile() {
        SyncRepoTest.testGetBooks_singleOrgFile(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testGetBooks_singleFileInSubfolderWhenEnabled() {
        SyncRepoTest.testGetBooks_singleFileInSubfolderWhenEnabled(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testGetBooks_singleFileInSubfolderWhenDisabled() {
        SyncRepoTest.testGetBooks_singleFileInSubfolderWhenDisabled(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testGetBooks_allFilesAreIgnored() {
        SyncRepoTest.testGetBooks_allFilesAreIgnored(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testGetBooks_specificFileInSubfolderIsIgnored() {
        SyncRepoTest.testGetBooks_specificFileInSubfolderIsIgnored(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testGetBooks_specificFileIsUnignored() {
        SyncRepoTest.testGetBooks_specificFileIsUnignored(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testGetBooks_ignoredExtensions() {
        SyncRepoTest.testGetBooks_ignoredExtensions(gitWorkingTree, syncRepo)
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
        SyncRepoTest.testStoreBook_producesSameUriAsGetBooks(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testStoreBook_inSubfolder() {
        SyncRepoTest.testStoreBook_inSubfolder(gitWorkingTree, syncRepo)
    }

    @Test
    override fun testStoreBook_inSubfolderWhenDisabled() {
        SyncRepoTest.testStoreBook_inSubfolderWhenDisabled(syncRepo)
    }

    @Test
    override fun testRenameBook_expectedUri() {
        SyncRepoTest.testRenameBook_expectedUri(syncRepo)
    }

    @Test(expected = IOException::class)
    override fun testRenameBook_repoFileAlreadyExists() {
        SyncRepoTest.testRenameBook_repoFileAlreadyExists(gitWorkingTree, syncRepo)
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
