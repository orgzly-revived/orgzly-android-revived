package com.orgzly.android.repos

import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.orgzly.android.BookName
import com.orgzly.android.LocalStorage
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.testutil.TestDocumentsProvider
import com.orgzly.android.ui.note.NoteAttachmentData
import com.orgzly.android.util.MiscUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

class DocumentRepoTest : SyncRepoTest, OrgzlyTest() {

    private lateinit var repo: Repo
    private lateinit var syncRepo: SyncRepo
    private lateinit var repoDirectory: DocumentFile

    @get:Rule
    val mRetryTestRule = RetryTestRule()
    
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

    @Test
    fun testStoreFileInSubdirectory() {
        val tmpDir = localStorage.getCacheDirectory("tmp-document-repo-attachment")
        val file = File(tmpDir, "test.jpg")
        MiscUtils.writeStringToFile("image content", file)

        try {
            val rook = syncRepo.storeFile(file, "attachments", "test.jpg")
            Assert.assertNotNull(rook)
            Assert.assertTrue(
                BookName.getRepoRelativePath(rook.repoUri, rook.uri).endsWith("attachments/test.jpg")
            )

            val storedUri = syncRepo.getUriForPath("attachments/test.jpg")
            Assert.assertNotNull(storedUri)
            val storedContent = readTextFromUri(storedUri!!)
            Assert.assertEquals("image content", storedContent)
        } finally {
            LocalStorage.deleteRecursive(tmpDir)
        }
    }

    @Test
    fun testStoreFileInNestedPath() {
        val tmpDir = localStorage.getCacheDirectory("tmp-document-repo-attachment-nested")
        val file = File(tmpDir, "attachment.pdf")
        MiscUtils.writeStringToFile("pdf content", file)

        try {
            syncRepo.storeFile(file, "data/ab/cdef", "attachment.pdf")
            val storedUri = syncRepo.getUriForPath("data/ab/cdef/attachment.pdf")
            Assert.assertNotNull(storedUri)
        } finally {
            LocalStorage.deleteRecursive(tmpDir)
        }
    }

    @Test
    fun testListFilesInPath() {
        val tmpDir = localStorage.getCacheDirectory("tmp-document-repo-list-path")
        val fileA = File(tmpDir, "a.jpg")
        val fileB = File(tmpDir, "b.pdf")
        MiscUtils.writeStringToFile("content a", fileA)
        MiscUtils.writeStringToFile("content b", fileB)

        try {
            syncRepo.storeFile(fileA, "attachments", "a.jpg")
            syncRepo.storeFile(fileB, "attachments", "b.pdf")
            val files: List<NoteAttachmentData> = syncRepo.listFilesInPath("attachments")
            Assert.assertEquals(2, files.size)
            Assert.assertTrue(files.any { it.filename == "a.jpg" })
            Assert.assertTrue(files.any { it.filename == "b.pdf" })
        } finally {
            LocalStorage.deleteRecursive(tmpDir)
        }
    }

    @Test
    fun testListFilesInPathEmptyDir() {
        val files: List<NoteAttachmentData> = syncRepo.listFilesInPath("empty")
        Assert.assertEquals(0, files.size)
    }

    @Test
    fun testGetUriForPath() {
        val tmpDir = localStorage.getCacheDirectory("tmp-document-repo-uri-path")
        val file = File(tmpDir, "file.txt")
        MiscUtils.writeStringToFile("content", file)

        try {
            syncRepo.storeFile(file, "folder", "file.txt")
            val uri = syncRepo.getUriForPath("folder/file.txt")
            Assert.assertNotNull(uri)
            val storedContent = readTextFromUri(uri!!)
            Assert.assertEquals("content", storedContent)
        } finally {
            LocalStorage.deleteRecursive(tmpDir)
        }
    }

    @Test
    fun testGetUriForPathNonExistent() {
        val uri = syncRepo.getUriForPath("nonexistent/path.txt")
        Assert.assertNull(uri)
    }

    private fun setupDocumentRepo(extraDir: String? = null) {
        val rootTree = "content://${TestDocumentsProvider.AUTHORITY}/tree/${TestDocumentsProvider.ROOT_ID}"
        var treeDocumentFileUrl = rootTree
        if (extraDir != null) {
            treeDocumentFileUrl = "$treeDocumentFileUrl%2F" + Uri.encode(extraDir)
        }
        repoDirectory = DocumentFile.fromTreeUri(context, treeDocumentFileUrl.toUri())!!
        Assert.assertTrue(repoDirectory.exists())
        // Isolate each test run under a clean root.
        repoDirectory.listFiles().forEach { it.delete() }
        repo = testUtils.setupRepo(RepoType.DOCUMENT, treeDocumentFileUrl)
        syncRepo = testUtils.repoInstance(RepoType.DOCUMENT, repo.url, repo.id)
        Assert.assertEquals(treeDocumentFileUrl, repo.url)
    }

    private fun readTextFromUri(uri: Uri): String {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            checkNotNull(inputStream) { "Failed opening input stream for $uri" }
            return inputStream.bufferedReader().readText()
        }
    }

}
