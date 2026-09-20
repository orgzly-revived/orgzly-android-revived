package com.orgzly.android.ui.repo.git

import java.io.File

private const val DEFAULT_DIR_NAME = "orgzly-git"

fun File.isEmptyOrMissing(): Boolean =
    !exists() || (isDirectory && list()?.isEmpty() == true)

fun File.ensureDirectory(): Boolean =
    try {
        isDirectory || mkdirs()
    } catch (_: SecurityException) {
        false
    }

/** orgzly-git, orgzly-git-2, orgzly-git-3, ... until one is free. */
fun nextAvailableRepoDir(baseDir: File): File =
    generateSequence(1) { it + 1 }
        .map { File(baseDir, if (it == 1) DEFAULT_DIR_NAME else "$DEFAULT_DIR_NAME-$it") }
        .first { it.isEmptyOrMissing() }

/** On external storage, but outside the app-specific directory, so not writable on Android 11+. */
fun isOnPublicExternalStorage(dir: File, externalDir: File, appSpecificDir: File?): Boolean =
    appSpecificDir != null &&
            dir.absolutePath.startsWith(externalDir.absolutePath) &&
            !dir.absolutePath.startsWith(appSpecificDir.absolutePath)
