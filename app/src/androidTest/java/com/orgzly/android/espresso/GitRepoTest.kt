package com.orgzly.android.espresso

import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu
import com.orgzly.android.espresso.util.EspressoUtils.onBook
import com.orgzly.android.espresso.util.EspressoUtils.sync
import com.orgzly.android.git.GitPreferencesFromRepoPrefs
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.RepoPreferences
import com.orgzly.android.repos.GitRepo
import com.orgzly.android.repos.RepoType
import com.orgzly.android.ui.main.MainActivity
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GitRepoTest : OrgzlyTest() {
    private lateinit var bareRepoDir: File
    private lateinit var gitWorkingTree: File
    private lateinit var repo: Repo

    @Before
    override fun setUp() {
        super.setUp()
        bareRepoDir = createTempDirectory().toFile()
        Git.init().setBare(true).setDirectory(bareRepoDir).call()
        AppPreferences.gitIsEnabled(context, true)
        repo = Repo(0, RepoType.GIT, "file://$bareRepoDir")
        val repoPreferences = RepoPreferences(context, repo.id, repo.url.toUri())
        val gitPreferences = GitPreferencesFromRepoPrefs(repoPreferences)
        gitWorkingTree = File(gitPreferences.repositoryFilepath())
        gitWorkingTree.mkdirs()
        GitRepo.ensureRepositoryExists(gitPreferences, true, null)
        testUtils.setupRepo(RepoType.GIT, repo.url)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        gitWorkingTree.deleteRecursively()
        bareRepoDir.deleteRecursively()
    }

    /**
     * Regression test for issue #315, "Race condition when multiple notebooks are deleted on
     * parallel I/O threads"
     */
    @Test
    fun testDeleteMultipleBooks() {
        testUtils.setupBook("book-1", "...")
        testUtils.setupBook("book-2", "...")
        testUtils.setupBook("book-3", "...")
        ActivityScenario.launch(MainActivity::class.java)
        sync()
        assertEquals(3, dataRepository.getBooks().size)
        onBook(0, R.id.item_book_link_repo).check(ViewAssertions.matches(withText(repo.url)))
        onBook(1, R.id.item_book_link_repo).check(ViewAssertions.matches(withText(repo.url)))
        onBook(2, R.id.item_book_link_repo).check(ViewAssertions.matches(withText(repo.url)))
        onBook(0).perform(ViewActions.longClick())
        onBook(1).perform(ViewActions.click())
        onBook(2).perform(ViewActions.click())
        contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(withId(R.id.delete_linked_checkbox)).perform(ViewActions.click())
        Espresso.onView(withText(R.string.delete)).perform(ViewActions.click())
        assertEquals(0, dataRepository.getBooks().size)
    }
}
