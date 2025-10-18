package com.orgzly.android

import android.net.Uri
import com.orgzly.BuildConfig
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoType
import com.orgzly.android.repos.RepoWithProps
import com.orgzly.android.repos.SyncRepo
import com.orgzly.android.repos.VersionedRook
import com.orgzly.android.sync.BookNamesake
import com.orgzly.android.sync.SyncUtils
import com.orgzly.android.util.MiscUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume
import java.io.IOException

/**
 * Utility methods used by tests.
 * Creating and checking books, rooks, encodings etc.
 *
 * Adapted for Robolectric unit tests from the instrumented test version.
 */
class TestUtils(
    private val dataRepository: DataRepository,
    private val dbRepoBookRepository: DbRepoBookRepository
) {

    fun repoInstance(type: RepoType, url: String): SyncRepo {
        return dataRepository.getRepoInstance(13, type, url)
    }

    fun repoInstance(type: RepoType, url: String, id: Long): SyncRepo {
        return dataRepository.getRepoInstance(id, type, url)
    }

    fun setupRepo(type: RepoType, url: String): Repo {
        val id = dataRepository.createRepo(RepoWithProps(Repo(0, type, url)))
        return dataRepository.getRepo(id)!!
    }

    fun setupRepo(type: RepoType, url: String, props: Map<String, String>): Repo {
        val id = dataRepository.createRepo(RepoWithProps(Repo(0, type, url), props))
        return dataRepository.getRepo(id)!!
    }

    fun deleteRepo(url: String) {
        val repo = dataRepository.getRepo(url)
        repo?.let {
            dataRepository.deleteRepo(it.id)
        }
    }

    fun renameRepo(fromUrl: String, toUrl: String) {
        val repo = dataRepository.getRepo(fromUrl)
            ?: throw IllegalStateException("Repo $fromUrl does not exist")
        val newRepo = Repo(repo.id, repo.type, toUrl)
        dataRepository.updateRepo(RepoWithProps(newRepo))
    }

    fun setupBook(name: String, content: String): BookView {
        return try {
            loadBookFromContent(name, BookFormat.ORG, content, null)
        } catch (e: IOException) {
            e.printStackTrace()
            fail(e.toString())
            throw e
        }
    }

    fun setupBook(name: String, content: String, link: Repo): BookView {
        return try {
            val bookView = loadBookFromContent(name, BookFormat.ORG, content, null)
            dataRepository.setLink(bookView.book.id, link)
            bookView
        } catch (e: IOException) {
            e.printStackTrace()
            fail(e.toString())
            throw e
        }
    }

    /**
     * Overwrites existing repoUrl / url combinations (due to table definition).
     */
    fun setupRook(repo: Repo, url: String, content: String, rev: String, mtime: Long) {
        val vrook = VersionedRook(
            repo.id, repo.type, Uri.parse(repo.url), Uri.parse(url), rev, mtime
        )

        dbRepoBookRepository.createBook(repo.id, vrook, content)
    }

    fun assertBook(name: String, expectedContent: String) {
        assertEquals(expectedContent, getBookContent(name))
    }

    private fun getBookContent(name: String): String? {
        return try {
            dataRepository.getBookContent(name, BookFormat.ORG)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Imports book to database overwriting the existing one with the same name.
     * @param name Notebook name
     * @param content Notebook's content
     */
    private fun loadBookFromContent(
        name: String,
        format: BookFormat,
        content: String,
        vrook: VersionedRook?
    ): BookView {
        // Save content to temporary file
        val tmpFile = dataRepository.getTempBookFile()
        MiscUtils.writeStringToFile(content, tmpFile)

        return try {
            dataRepository.loadBookFromFile(name, format, tmpFile, vrook)!!
        } finally {
            // Delete temporary file
            tmpFile.delete()
        }
    }

    fun sync(): Map<String, BookNamesake> {
        return try {
            val nameGroups = SyncUtils.groupAllNotebooksByName(dataRepository)

            for (group in nameGroups.values) {
                val action = SyncUtils.syncNamesake(dataRepository, group)
                dataRepository.setBookLastActionAndSyncStatus(
                    group.book.book.id, action, group.status.toString()
                )
            }

            nameGroups
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    @Throws(Exception::class)
    fun syncOrThrow(): Map<String, BookNamesake> {
        val nameGroups = SyncUtils.groupAllNotebooksByName(dataRepository)

        for (group in nameGroups.values) {
            val action = SyncUtils.syncNamesake(dataRepository, group)
            dataRepository.setBookLastActionAndSyncStatus(
                group.book.book.id, action, group.status.toString()
            )
        }

        return nameGroups
    }

    @Throws(org.json.JSONException::class)
    fun dropboxTestPreflight() {
        Assume.assumeTrue(BuildConfig.IS_DROPBOX_ENABLED)
        Assume.assumeTrue(BuildConfig.DROPBOX_APP_KEY.isNotEmpty())
        Assume.assumeTrue(BuildConfig.DROPBOX_REFRESH_TOKEN.isNotEmpty())

        val mockSerializedDbxCredential = JSONObject()
        mockSerializedDbxCredential.put("access_token", "dummy")
        mockSerializedDbxCredential.put("expires_at", System.currentTimeMillis())
        mockSerializedDbxCredential.put("refresh_token", BuildConfig.DROPBOX_REFRESH_TOKEN)
        mockSerializedDbxCredential.put("app_key", BuildConfig.DROPBOX_APP_KEY)
        AppPreferences.dropboxSerializedCredential(
            App.getAppContext(),
            mockSerializedDbxCredential.toString()
        )
    }

    /**
     * Creates a saved search with the given name and query.
     * @param name Name of the saved search
     * @param query Search query
     */
    fun createSavedSearch(name: String, query: String) {
        dataRepository.createSavedSearch(SavedSearch(0, name, query, 0))
    }
}
