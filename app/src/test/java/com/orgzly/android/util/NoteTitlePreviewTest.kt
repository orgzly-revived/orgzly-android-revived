package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTitlePreviewTest {
    @Test
    fun keepsShortTitlesAndNormalizesWhitespace() {
        assertEquals("Buy milk", NoteTitlePreview.from("  Buy\t\n milk\u00a0"))
        assertEquals("", NoteTitlePreview.from(" \n\t"))
    }

    @Test
    fun includesOnlyTheFirstEightWords() {
        assertEquals("one two three four five six seven eight…",
            NoteTitlePreview.from("one two three four five six seven eight nine"))
        assertEquals("one two three four five six seven eight",
            NoteTitlePreview.from("one two three four five six seven eight"))
    }

    @Test
    fun boundsTitlesWithoutWordSeparators() {
        assertEquals("文".repeat(80), NoteTitlePreview.from("文".repeat(80)))
        assertEquals("文".repeat(80) + "…", NoteTitlePreview.from("文".repeat(81)))
    }

    @Test
    fun doesNotSplitSupplementaryUnicodeCharacters() {
        val emoji = "\uD83D\uDE00"
        assertEquals("a".repeat(79) + emoji + "…",
            NoteTitlePreview.from("a".repeat(79) + emoji + "b"))
    }
}
