package com.orgzly.android.repos

import android.os.Build
import androidx.annotation.RequiresApi
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import org.eclipse.jgit.ignore.IgnoreNode
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class RepoIgnoreNode(repo: SyncRepo) : IgnoreNode() {

    init {
        try {
            val inputStream = repo.openRepoFileInputStream(ignore_file())
            inputStream.use {
                parse(it)
            }
            inputStream.close()
        } catch (ignored: FileNotFoundException) {}
    }

    /**
     * Checks a repository-relative path with '/' separators against the ignore rules.
     * Falls back to parent directory rules when the path has no explicit match.
     */
    fun isPathIgnored(pathString: String, isDirectory: Boolean): Boolean {
        if (rules.isEmpty()) {
            return false
        }
        // File works below API 26.
        // Once minSdk reaches 26 (Android 8), this can use kotlin.io.path.Path instead.
        val parent = File(pathString).parent
        return when (isIgnored(pathString, isDirectory)) {
            MatchResult.IGNORED ->
                true
            MatchResult.NOT_IGNORED ->
                false
            MatchResult.CHECK_PARENT ->
                if (parent != null) {
                    // Recursive call
                    isPathIgnored(parent, true)
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
                    ignore_file(),
                )
            )
        }
    }

    companion object {
        @JvmStatic
        fun ignore_file(): String = AppPreferences.orgzlyignoreFile(App.getAppContext())
    }
}
