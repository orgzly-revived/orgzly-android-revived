package com.orgzly.android.repos

import android.content.Context
import android.net.Uri
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.BookName
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.note.NoteAttachmentData
import com.orgzly.android.util.UriUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.Throws

class DropboxRepo(repoWithProps: RepoWithProps, context: Context?) : SyncRepo {
    private val repoUri: Uri
    private val client: DropboxClient

    override fun isConnectionRequired(): Boolean {
        return true
    }

    override fun isAutoSyncSupported(): Boolean {
        return false
    }

    override fun getUri(): Uri {
        return repoUri
    }

    @Throws(IOException::class)
    override fun getBooks(): List<VersionedRook> {
        val ignores = RepoIgnoreNode(this)
        return client.getBooks(repoUri, ignores)
    }

    @Throws(IOException::class)
    override fun retrieveBook(repoRelativePath: String, file: File): VersionedRook {
        return client.download(repoUri, repoRelativePath, file)
    }

    @Throws(IOException::class)
    override fun openRepoFileInputStream(repoRelativePath: String): InputStream {
        return client.streamFile(repoUri, repoRelativePath)
    }

    @Throws(IOException::class)
    override fun storeBook(file: File, repoRelativePath: String): VersionedRook {
        val context = App.getAppContext()
        if (repoRelativePath.contains("/") && !AppPreferences.subfolderSupport(context)) {
            throw IOException(context.getString(R.string.subfolder_support_disabled))
        }
        return client.upload(file, repoUri, repoRelativePath)
    }

    @Throws(IOException::class)
    override fun storeFile(file: File, pathInRepo: String, fileName: String): VersionedRook {
        if (!file.exists()) {
            throw FileNotFoundException("File $file does not exist")
        }

        val relativePath = if (pathInRepo.isNotEmpty()) "$pathInRepo/$fileName" else fileName
        return client.upload(file, repoUri, relativePath)
    }

    override fun listFilesInPath(pathInRepo: String?): MutableList<NoteAttachmentData> {
        // TODO: Implement listFilesInPath.
        return mutableListOf()
    }

    @Throws(IOException::class)
    override fun renameBook(oldFullUri: Uri, newName: String): VersionedRook {
        val context = App.getAppContext()
        if (newName.contains("/") && !AppPreferences.subfolderSupport(context)) {
            throw IOException(context.getString(R.string.subfolder_support_disabled))
        }
        val oldBookName = BookName.fromRepoRelativePath(BookName.getRepoRelativePath(repoUri, oldFullUri))
        val newRelativePath = BookName.repoRelativePath(newName, oldBookName.format)
        val newEncodedRelativePath = Uri.encode(newRelativePath, "/")
        val newFullUri = repoUri.buildUpon().appendEncodedPath(newEncodedRelativePath).build()
        return client.move(repoUri, oldFullUri, newFullUri)
    }

    @Throws(IOException::class)
    override fun delete(uri: Uri) {
        client.delete(uri.path)
    }

    override fun toString(): String {
        return repoUri.toString()
    }

    companion object {
        const val SCHEME = "dropbox"
    }

    override fun getUriForPath(path: String): Uri? {
        // TODO: Implement getUriForPath.
        return null
    }

    init {
        repoUri = Uri.parse(repoWithProps.repo.url)
        client = DropboxClient(context, repoWithProps.repo.id)
    }
}
