package com.orgzly.android.testutil

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Debug-only DocumentsProvider used by instrumented tests to exercise DocumentRepo
 * without relying on system SAF picker permissions.
 */
class TestDocumentsProvider : DocumentsProvider() {
    private lateinit var rootDir: File

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        rootDir = File(ctx.cacheDir, ROOT_DIR_NAME)
        rootDir.mkdirs()
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(resolveRootProjection(projection))
        result.newRow()
            .add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            .add(DocumentsContract.Root.COLUMN_TITLE, "Orgzly Test Documents")
            .add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY or
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE
            )
            .add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, rootDir.usableSpace)
            .add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileForDocumentId(documentId)
        if (!file.exists()) {
            throw FileNotFoundException("Document not found: $documentId")
        }
        val cursor = MatrixCursor(resolveDocumentProjection(projection))
        includeDocument(cursor, documentId, file)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = fileForDocumentId(parentDocumentId)
        val cursor = MatrixCursor(resolveDocumentProjection(projection))
        val children = parent.listFiles()?.sortedBy { it.name } ?: emptyList()
        children.forEach { includeDocument(cursor, documentIdForFile(it), it) }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = fileForDocumentId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        if ((accessMode and ParcelFileDescriptor.MODE_WRITE_ONLY) != 0 || (accessMode and ParcelFileDescriptor.MODE_READ_WRITE) != 0) {
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.createNewFile()
            }
        }
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForDocumentId(parentDocumentId)
        val target = File(parent, displayName)
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (!target.exists()) {
                target.mkdirs()
            }
        } else {
            target.parentFile?.mkdirs()
            if (!target.exists()) {
                target.createNewFile()
            }
        }
        return documentIdForFile(target)
    }

    override fun deleteDocument(documentId: String) {
        val target = fileForDocumentId(documentId)
        deleteRecursively(target)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val current = fileForDocumentId(documentId)
        val destination = File(current.parentFile, displayName)
        if (!current.renameTo(destination)) {
            throw FileNotFoundException("Failed renaming $documentId to $displayName")
        }
        return documentIdForFile(destination)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val source = fileForDocumentId(sourceDocumentId)
        val targetParent = fileForDocumentId(targetParentDocumentId)
        val destination = File(targetParent, source.name)
        if (!source.renameTo(destination)) {
            throw FileNotFoundException("Failed moving $sourceDocumentId to $targetParentDocumentId")
        }
        return documentIdForFile(destination)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileForDocumentId(parentDocumentId).canonicalFile
        val child = fileForDocumentId(documentId).canonicalFile
        return child.path.startsWith(parent.path)
    }

    private fun resolveRootProjection(projection: Array<out String>?): Array<String> {
        return projection?.map { it }?.toTypedArray() ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            DocumentsContract.Root.COLUMN_ICON
        )
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<String> {
        return projection?.map { it }?.toTypedArray() ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    private fun includeDocument(cursor: MatrixCursor, documentId: String, file: File) {
        val flags = if (file.isDirectory) {
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        } else {
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document.FLAG_SUPPORTS_MOVE
        }
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            .add(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mimeTypeFor(file)
            )
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, if (file == rootDir) "root" else file.name)
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            .add(DocumentsContract.Document.COLUMN_SIZE, if (file.isDirectory) 0 else file.length())
    }

    private fun fileForDocumentId(documentId: String): File {
        val normalized = normalizeDocumentId(documentId)
        if (normalized == ROOT_ID) return rootDir
        val relative = normalized.removePrefix("$ROOT_ID:")
        val file = File(rootDir, relative)
        if (!file.canonicalPath.startsWith(rootDir.canonicalPath)) {
            throw FileNotFoundException("Outside root: $documentId")
        }
        return file
    }

    private fun documentIdForFile(file: File): String {
        if (file.canonicalPath == rootDir.canonicalPath) return ROOT_ID
        val rel = file.canonicalPath.removePrefix(rootDir.canonicalPath).trimStart(File.separatorChar)
        return if (rel.isEmpty()) ROOT_ID else "$ROOT_ID:$rel"
    }

    private fun normalizeDocumentId(documentId: String): String {
        val decoded = Uri.decode(documentId).trim()
        if (decoded == ROOT_ID) return ROOT_ID
        if (decoded.startsWith("$ROOT_ID:")) return decoded
        if (decoded.startsWith("$ROOT_ID/")) return "$ROOT_ID:${decoded.removePrefix("$ROOT_ID/")}"
        if (decoded.startsWith("/")) return normalizeDocumentId(decoded.removePrefix("/"))
        return if (decoded.contains('/')) {
            val parts = decoded.split('/', limit = 2)
            if (parts[0] == ROOT_ID) "$ROOT_ID:${parts.getOrElse(1) { "" }}" else decoded
        } else {
            decoded
        }
    }

    private fun mimeTypeFor(file: File): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(file.name).lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        if (file != rootDir) {
            file.delete()
        }
    }

    companion object {
        const val AUTHORITY = "com.orgzlyrevived.test.documents"
        const val ROOT_ID = "root"
        private const val ROOT_DIR_NAME = "test-documents-provider-root"
    }
}
