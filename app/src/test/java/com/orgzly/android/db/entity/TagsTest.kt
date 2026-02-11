package com.orgzly.android.db.entity

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for Tags class.
 * Tests parsing, serialization, and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagsTest {

    // fromString tests

    @Test
    fun `fromString parses single tag`() {
        val tags = Tags.fromString("tag1")
        assertThat(tags.tags, `is`(listOf("tag1")))
    }

    @Test
    fun `fromString parses multiple tags with single spaces`() {
        val tags = Tags.fromString("tag1 tag2 tag3")
        assertThat(tags.tags, `is`(listOf("tag1", "tag2", "tag3")))
    }

    @Test
    fun `fromString parses multiple tags with multiple spaces`() {
        val tags = Tags.fromString("tag1  tag2   tag3")
        assertThat(tags.tags, `is`(listOf("tag1", "tag2", "tag3")))
    }

    @Test
    fun `fromString handles leading whitespace`() {
        val tags = Tags.fromString("  tag1 tag2")
        assertThat(tags.tags, `is`(listOf("tag1", "tag2")))
    }

    @Test
    fun `fromString handles trailing whitespace`() {
        val tags = Tags.fromString("tag1 tag2  ")
        assertThat(tags.tags, `is`(listOf("tag1", "tag2")))
    }

    @Test
    fun `fromString treats tabs as part of tag name`() {
        // Implementation uses " +" regex which only matches spaces, not tabs
        // This is intentional - tags are space-separated in the database
        val tags = Tags.fromString("tag1\ttag2")
        assertThat(tags.tags, `is`(listOf("tag1\ttag2")))
    }

    @Test
    fun `fromString handles tabs mixed with spaces`() {
        // Tabs within tags are preserved, spaces are separators
        val tags = Tags.fromString("tag1  tag-with\ttab  tag3")
        assertThat(tags.tags, `is`(listOf("tag1", "tag-with\ttab", "tag3")))
    }

    @Test
    fun `fromString handles empty string`() {
        val tags = Tags.fromString("")
        assertThat(tags.tags, `is`(emptyList()))
    }

    @Test
    fun `fromString handles blank string with only spaces`() {
        val tags = Tags.fromString("   ")
        assertThat(tags.tags, `is`(emptyList()))
    }

    @Test
    fun `fromString handles newlines as part of tags`() {
        // Only spaces are separators, newlines would be part of tag name
        // (Though in practice, tags shouldn't contain newlines)
        val tags = Tags.fromString("tag1\ntag2")
        assertThat(tags.tags, `is`(listOf("tag1\ntag2")))
    }

    @Test
    fun `fromString returns empty tags for corrupted data`() {
        // This tests the graceful handling of corrupted data
        // Note: Currently there's no way to trigger the corruption check
        // since any string will either be blank or produce valid tags
        // This test is here for documentation and future-proofing
        val tags = Tags.fromString("")
        assertThat(tags.tags, `is`(emptyList()))
    }

    // fromList tests

    @Test
    fun `fromList creates Tags from list`() {
        val tags = Tags.fromList(listOf("tag1", "tag2", "tag3"))
        assertThat(tags?.tags, `is`(listOf("tag1", "tag2", "tag3")))
    }

    @Test
    fun `fromList returns null for null input`() {
        val tags = Tags.fromList(null)
        assertThat(tags, `is`(null as Tags?))
    }

    @Test
    fun `fromList returns null for empty list`() {
        val tags = Tags.fromList(emptyList())
        assertThat(tags, `is`(null as Tags?))
    }

    @Test
    fun `fromList creates Tags from single-element list`() {
        val tags = Tags.fromList(listOf("tag1"))
        assertThat(tags?.tags, `is`(listOf("tag1")))
    }

    // empty() tests

    @Test
    fun `empty creates empty Tags`() {
        val tags = Tags.empty()
        assertThat(tags.tags, `is`(emptyList()))
        assertThat(tags.isEmpty(), `is`(true))
    }

    // toString tests

    @Test
    fun `toString converts tags to space-separated string`() {
        val tags = Tags(listOf("tag1", "tag2", "tag3"))
        assertThat(tags.toString(), `is`("tag1 tag2 tag3"))
    }

    @Test
    fun `toString handles single tag`() {
        val tags = Tags(listOf("tag1"))
        assertThat(tags.toString(), `is`("tag1"))
    }

    @Test
    fun `toString handles empty tags`() {
        val tags = Tags(emptyList())
        assertThat(tags.toString(), `is`(""))
    }

    @Test
    fun `toString with custom separator`() {
        val tags = Tags(listOf("tag1", "tag2", "tag3"))
        assertThat(tags.toString(", "), `is`("tag1, tag2, tag3"))
    }

    @Test
    fun `toString with custom separator for single tag`() {
        val tags = Tags(listOf("tag1"))
        assertThat(tags.toString(", "), `is`("tag1"))
    }

    // isEmpty/isNotEmpty tests

    @Test
    fun `isEmpty returns true for empty tags`() {
        val tags = Tags(emptyList())
        assertThat(tags.isEmpty(), `is`(true))
        assertThat(tags.isNotEmpty(), `is`(false))
    }

    @Test
    fun `isEmpty returns false for non-empty tags`() {
        val tags = Tags(listOf("tag1"))
        assertThat(tags.isEmpty(), `is`(false))
        assertThat(tags.isNotEmpty(), `is`(true))
    }

    // Extension function tests

    @Test
    fun `nullable isEmpty returns true for null`() {
        val tags: Tags? = null
        assertThat(tags.isEmpty(), `is`(true))
    }

    @Test
    fun `nullable isEmpty returns true for empty Tags`() {
        val tags: Tags? = Tags(emptyList())
        assertThat(tags.isEmpty(), `is`(true))
    }

    @Test
    fun `nullable isEmpty returns false for non-empty Tags`() {
        val tags: Tags? = Tags(listOf("tag1"))
        assertThat(tags.isEmpty(), `is`(false))
    }

    @Test
    fun `nullable isNotEmpty returns false for null`() {
        val tags: Tags? = null
        assertThat(tags.isNotEmpty(), `is`(false))
    }

    @Test
    fun `nullable isNotEmpty returns false for empty Tags`() {
        val tags: Tags? = Tags(emptyList())
        assertThat(tags.isNotEmpty(), `is`(false))
    }

    @Test
    fun `nullable isNotEmpty returns true for non-empty Tags`() {
        val tags: Tags? = Tags(listOf("tag1"))
        assertThat(tags.isNotEmpty(), `is`(true))
    }

    @Test
    fun `toList returns empty list for null`() {
        val tags: Tags? = null
        assertThat(tags.toList(), `is`(emptyList()))
    }

    @Test
    fun `toList returns tag list for non-null Tags`() {
        val tags: Tags? = Tags(listOf("tag1", "tag2"))
        assertThat(tags.toList(), `is`(listOf("tag1", "tag2")))
    }

    // Round-trip tests

    @Test
    fun `round-trip fromString to toString`() {
        val original = "tag1 tag2 tag3"
        val tags = Tags.fromString(original)
        val result = tags.toString()
        assertThat(result, `is`(original))
    }

    @Test
    fun `round-trip fromString to toString normalizes whitespace`() {
        val original = "tag1   tag2  \t tag3"
        val tags = Tags.fromString(original)
        val result = tags.toString()
        assertThat(result, `is`("tag1 tag2 tag3"))
    }

    @Test
    fun `round-trip fromList to toString`() {
        val tagList = listOf("tag1", "tag2", "tag3")
        val tags = Tags.fromList(tagList)
        val result = tags?.toString()
        assertThat(result, `is`("tag1 tag2 tag3"))
    }

    // Edge cases and special characters

    @Test
    fun `handles tags with special characters`() {
        val tags = Tags.fromString("tag-1 tag_2 tag.3")
        assertThat(tags.tags, `is`(listOf("tag-1", "tag_2", "tag.3")))
    }

    @Test
    fun `handles tags with numbers`() {
        val tags = Tags.fromString("tag1 tag2 tag3 123")
        assertThat(tags.tags, `is`(listOf("tag1", "tag2", "tag3", "123")))
    }

    @Test
    fun `handles tags with unicode characters`() {
        val tags = Tags.fromString("测试 тест परीक्षण")
        assertThat(tags.tags, `is`(listOf("测试", "тест", "परीक्षण")))
    }
}
