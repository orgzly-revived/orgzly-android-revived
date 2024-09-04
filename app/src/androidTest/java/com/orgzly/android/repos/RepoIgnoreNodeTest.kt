package com.orgzly.android.repos

import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.Repo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.HashMap

class RepoIgnoreNodeTest : OrgzlyTest() {

    class MockRepoWithMockIgnoreFile : MockRepo(repoWithProps, null) {
        override fun openRepoFileInputStream(repoRelativePath: String): InputStream {
            if (repoRelativePath == RepoIgnoreNode.IGNORE_FILE) {
                val ignoreFileContents = """
                    IgnoredAnywhere.org
                    /OnlyIgnoredInRoot.org
                    CompletelyExcludedFolder/
                    PartiallyExcludedFolder/**
                    !PartiallyExcludedFolder/included-*.org
                """.trimIndent()
                return ByteArrayInputStream(ignoreFileContents.toByteArray())
            } else {
                throw FileNotFoundException()
            }
        }
    }

    @Test
    fun testIsPathIgnoredSyntax() {
        val repo = MockRepoWithMockIgnoreFile()
        val ignores = RepoIgnoreNode(repo)
        assertEquals(true, ignores.isPathIgnored("IgnoredAnywhere.org", false))
        assertEquals(true, ignores.isPathIgnored("SomeFolder/IgnoredAnywhere.org", false))
        assertEquals(true, ignores.isPathIgnored("OnlyIgnoredInRoot.org", false))
        assertEquals(false, ignores.isPathIgnored("SomeFolder/OnlyIgnoredInRoot.org", false))
        assertEquals(true, ignores.isPathIgnored("CompletelyExcludedFolder/file.org", false))
        assertEquals(true, ignores.isPathIgnored("PartiallyExcludedFolder/whatever.org", false))
        assertEquals(false, ignores.isPathIgnored("PartiallyExcludedFolder/included-file.org", false))
    }

    companion object {
        private val repoWithProps = RepoWithProps(Repo(0, RepoType.MOCK, "mock://repo"), HashMap<String, String>())
    }
}