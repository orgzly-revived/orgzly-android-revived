package com.orgzly.android.usecase

import com.orgzly.android.App
import com.orgzly.android.BookName
import com.orgzly.android.data.DataRepository
import com.orgzly.android.prefs.AppPreferences
import java.io.File

class LinkFindTarget(val path: String, val bookId: Long) : UseCase() {
    val context = App.getAppContext();

    override fun run(dataRepository: DataRepository): UseCaseResult {
        val target = openLink(dataRepository, path)

        return UseCaseResult(
                userData = target
        )
    }

    private fun openLink(dataRepository: DataRepository, path: String): Any {
        if (path.startsWith("content://")) {
            return android.net.Uri.parse(path)
        }

        return if (isAbsolute(path)) {
            val root = AppPreferences.fileAbsoluteRoot(context)
            File(root, path)
        } else {
            isMaybeBook(path)?.let { bookName ->
                dataRepository.getBook(bookName.name)?.let {
                    return it
                }
            }

            val book = dataRepository.getBookView(bookId)
            if (book != null) {
                val repoEntity = book.linkRepo ?: book.syncedTo?.let { vrook ->
                    dataRepository.getRepo(vrook.repoId)
                }

                if (repoEntity != null) {
                    val repo = dataRepository.getRepoInstance(repoEntity.id, repoEntity.type, repoEntity.url)

                    if (repo != null) {
                        val attachDir = AppPreferences.attachDirDefaultPath(context)
                        val relativePath = File(attachDir, path).path
                        val uri = repo.getUriForPath(relativePath)
                        if (uri != null) {
                            return uri
                        }
                        return File(relativePath)
                    }
                }
            }

            File(path)
        }
    }

    private fun isAbsolute(path: String): Boolean {
        return path.startsWith('/')
    }

    private fun isMaybeBook(path: String): BookName? {
        val file = File(path)

        return if (!hasParent(file) && BookName.isSupportedFormatFileName(file.name)) {
            BookName.fromRepoRelativePath(file.name)
        } else {
            null
        }
    }

    private fun hasParent(file: File): Boolean {
        val parentFile = file.parentFile
        return parentFile != null && parentFile.name != "."
    }
}