package com.orgzly.android.repos

import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.git.GitFileSynchronizer
import com.orgzly.android.git.GitPreferencesFromRepoPrefs
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.RepoPreferences
import com.orgzly.android.util.MiscUtils
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class GitRepoTest : OrgzlyTest() {

    private lateinit var bareRepoPath: Path
    private lateinit var repoUri: Uri
    private lateinit var gitPreferences: GitPreferencesFromRepoPrefs
    private lateinit var workingtree: File
    private lateinit var repoPreferences: RepoPreferences
    private lateinit var repo: Repo
    private lateinit var syncRepo: GitRepo
    private lateinit var git: Git
    private lateinit var synchronizer: GitFileSynchronizer

    @Rule
    @JvmField
    var exceptionRule: ExpectedException = ExpectedException.none()

    override fun setUp() {
        super.setUp()
        bareRepoPath = createTempDirectory()
        Git.init().setBare(true).setDirectory(bareRepoPath.toFile()).call()
        AppPreferences.gitIsEnabled(context, true)
        repoUri = bareRepoPath.toFile().toUri()
        repo = testUtils.setupRepo(RepoType.GIT, repoUri.toString())
        repoPreferences = RepoPreferences(context, repo.id, repoUri)
        gitPreferences = GitPreferencesFromRepoPrefs(repoPreferences)
        workingtree = File(gitPreferences.repositoryFilepath())
        workingtree.mkdirs()
        git = GitRepo.ensureRepositoryExists(gitPreferences, true, null)
        syncRepo = dataRepository.getRepoInstance(repo.id, RepoType.GIT, repo.url) as GitRepo
        synchronizer = GitFileSynchronizer(git, gitPreferences)
    }

    override fun tearDown() {
        super.tearDown()
        testUtils.deleteRepo(repo.url)
        workingtree.deleteRecursively()
        bareRepoPath.toFile()?.deleteRecursively()
    }

    @Test
    fun testSyncNewBookWithoutLinkAndOneRepo() {
        testUtils.setupBook("book1", "book content")
        testUtils.sync()
        val bookView = dataRepository.getBooks()[0]
        assertEquals(repoUri.toString(), bookView.linkRepo?.url)
        assertEquals(1, syncRepo.books.size)
        assertEquals(bookView.syncedTo.toString(), syncRepo.books[0].toString())
        assertEquals(context.getString(R.string.sync_status_saved, repo.url), bookView.book.lastAction!!.message)
        assertEquals("/book1.org", bookView.syncedTo!!.uri.toString())
    }

    @Test
    fun testIgnoredFilesInRepoAreNotLoaded() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        // Create ignore file in working tree and commit
        val ignoreFileContents = """
            ignoredbook.org
            ignored-*.org
        """.trimIndent()
        addAndCommitIgnoreFile(ignoreFileContents)
        // Add multiple files to repo
        for (fileName in arrayOf("ignoredbook.org", "ignored-3.org", "notignored.org")) {
            val tmpFile = File.createTempFile("orgzlytest", null)
            MiscUtils.writeStringToFile("book content", tmpFile)
            synchronizer.addAndCommitNewFile(tmpFile, fileName)
            tmpFile.delete()
        }
        testUtils.sync()
        assertEquals(1, syncRepo.books.size)
        assertEquals(1, dataRepository.getBooks().size)
        assertEquals("notignored", dataRepository.getBooks()[0].book.name)
    }

    @Test
    fun testUnIgnoredFilesInRepoAreLoaded() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        // Create ignore file in working tree and commit
        val ignoreFileContents = """
            *.org
            !notignored.org
        """.trimIndent()
        addAndCommitIgnoreFile(ignoreFileContents)
        // Add multiple files to repo
        for (fileName in arrayOf("ignoredbook.org", "ignored-3.org", "notignored.org")) {
            val tmpFile = File.createTempFile("orgzlytest", null)
            MiscUtils.writeStringToFile("book content", tmpFile)
            synchronizer.addAndCommitNewFile(tmpFile, fileName)
            tmpFile.delete()
        }
        testUtils.sync()
        assertEquals(1, syncRepo.books.size)
        assertEquals(1, dataRepository.getBooks().size)
        assertEquals("notignored", dataRepository.getBooks()[0].book.name)
    }

    @Test
    fun testIgnoreRulePreventsLinkingBook() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        addAndCommitIgnoreFile("*.org")
        testUtils.setupBook("booky", "")
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("matches a rule in .orgzlyignore")
        testUtils.syncOrThrow()
    }

    @Test
    fun testIgnoreRulePreventsRenamingBook() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        addAndCommitIgnoreFile("badname*")
        testUtils.setupBook("goodname", "")
        testUtils.sync()
        var bookView: BookView? = dataRepository.getBookView("goodname")
        dataRepository.renameBook(bookView!!, "badname")
        bookView = dataRepository.getBooks()[0]
        assertTrue(
            bookView.book.lastAction.toString().contains("matches a rule in .orgzlyignore")
        )
    }

    private fun addAndCommitIgnoreFile(contents: String) {
        val tmpFile = File.createTempFile("orgzlytest", null)
        MiscUtils.writeStringToFile(contents, tmpFile)
        synchronizer.addAndCommitNewFile(tmpFile, RepoIgnoreNode.IGNORE_FILE)
        tmpFile.delete()
    }
}