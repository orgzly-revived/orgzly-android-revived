package com.orgzly.android.ui.repo.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitRepoDirsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir(name: String, vararg files: String) =
        File(tmp.root, name).also { dir ->
            dir.mkdirs()
            files.forEach { File(dir, it).writeText("* Note") }
        }

    @Test
    fun `first repo gets the plain default name`() {
        assertEquals(File(tmp.root, "orgzly-git"), nextAvailableRepoDir(tmp.root))
    }

    @Test
    fun `existing but empty directory is reused`() {
        assertEquals(dir("orgzly-git"), nextAvailableRepoDir(tmp.root))
    }

    @Test
    fun `second repo gets a suffixed name when the first one is in use`() {
        dir("orgzly-git", "notes.org")

        assertEquals(File(tmp.root, "orgzly-git-2"), nextAvailableRepoDir(tmp.root))
    }

    @Test
    fun `suffix keeps incrementing past used directories`() {
        listOf("orgzly-git", "orgzly-git-2", "orgzly-git-3").forEach { dir(it, "notes.org") }

        assertEquals(File(tmp.root, "orgzly-git-4"), nextAvailableRepoDir(tmp.root))
    }

    @Test
    fun `a file with the default name is skipped`() {
        File(tmp.root, "orgzly-git").writeText("not a directory")

        assertEquals(File(tmp.root, "orgzly-git-2"), nextAvailableRepoDir(tmp.root))
    }

    @Test
    fun `ensureDirectory creates missing directories including parents`() {
        val dir = File(tmp.root, "files/orgzly-git-2")

        assertTrue(dir.ensureDirectory())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `ensureDirectory accepts an existing directory`() {
        assertTrue(dir("orgzly-git").ensureDirectory())
    }

    @Test
    fun `ensureDirectory fails when the path is an existing file`() {
        assertFalse(File(tmp.root, "orgzly-git").also { it.writeText("x") }.ensureDirectory())
    }

    @Test
    fun `ensureDirectory fails when the parent is not writable`() {
        val parent = dir("readonly")
        assertTrue(parent.setWritable(false))
        try {
            assertFalse(File(parent, "orgzly-git").ensureDirectory())
        } finally {
            parent.setWritable(true)
        }
    }

    @Test
    fun `app-specific directory is not public external storage`() {
        val external = dir("emulated")
        val appSpecific = File(external, "Android/data/com.orgzlyrevived/files")

        assertFalse(isOnPublicExternalStorage(File(appSpecific, "orgzly-git"), external, appSpecific))
    }

    @Test
    fun `documents directory is public external storage`() {
        val external = dir("emulated")
        val appSpecific = File(external, "Android/data/com.orgzlyrevived/files")

        assertTrue(isOnPublicExternalStorage(File(external, "Documents/org"), external, appSpecific))
    }

    @Test
    fun `paths outside external storage are not flagged`() {
        val external = dir("emulated")
        val appSpecific = File(external, "Android/data/com.orgzlyrevived/files")
        val internal = File(tmp.root, "data/com.orgzlyrevived/files/orgzly-git")

        assertFalse(isOnPublicExternalStorage(internal, external, appSpecific))
    }

    @Test
    fun `missing app-specific directory means nothing is flagged`() {
        val external = dir("emulated")

        assertFalse(isOnPublicExternalStorage(File(external, "Documents/org"), external, null))
    }
}
