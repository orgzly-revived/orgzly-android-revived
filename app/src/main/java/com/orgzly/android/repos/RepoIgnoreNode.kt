package com.orgzly.android.repos

import android.os.Build
import androidx.annotation.RequiresApi
import com.orgzly.R
import com.orgzly.android.App
import org.eclipse.jgit.ignore.IgnoreNode
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.io.path.Path

class RepoIgnoreNode(repo: SyncRepo) : IgnoreNode() {

    init {
        try {
            val inputStream = repo.openRepoFileInputStream(IGNORE_FILE)
            inputStream.use {
                parse(it)
            }
            inputStream.close()
        } catch (ignored: FileNotFoundException) {}
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun isPathIgnored(pathString: String, isDirectory: Boolean): Boolean {
        if (rules.isEmpty()) {
            return false
        }
        val path = Path(pathString)
        return when (isIgnored(pathString, isDirectory)) {
            MatchResult.IGNORED ->
                true
            MatchResult.NOT_IGNORED ->
                false
            MatchResult.CHECK_PARENT ->
                if (path.parent != null) {
                    // Recursive call
                    isPathIgnored(path.parent.toString(), true)
                } else {
                    false
                }
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun ensurePathIsNotIgnored(filePath: String) {
        if (isPathIgnored(filePath, false)) {
            throw IOException(
                App.getAppContext().getString(
                    R.string.error_file_matches_repo_ignore_rule,
                    IGNORE_FILE,
                )
            )
        }
    }

    companion object {
        const val IGNORE_FILE = ".orgzlyignore"
    }
}
