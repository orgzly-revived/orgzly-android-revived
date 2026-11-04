package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceTextMapTest {
    @Test
    fun cutsTheSelectedOccurrence() {
        assertEquals("same  same", SourceTextMap("same same same").cut(5, 9))
    }

    @Test
    fun preservesPartialFormattingAndRemovesAnEmptyWrapper() {
        val map = SourceTextMap("*bold* rest")
        map.replace(0, 6, intArrayOf(1, 2, 3, 4), removeWrapper = true)
        assertEquals("*bd* rest", map.cut(1, 3))
        assertEquals(" rest", map.cut(0, 4))
        assertEquals("*bo*t", map.cut(2, 8))
    }

    @Test
    fun preservesHiddenTextAndOffsetsAfterGeneratedText() {
        val map = SourceTextMap("before SECRET after")
        map.replace(7, 13, intArrayOf(-1))
        assertNull(map.cut(7, 8))
        assertEquals("before SECRET ", map.cut(9, 14))
    }

    @Test
    fun rejectsInvalidOrSplitSurrogateSelections() {
        val map = SourceTextMap("a\uD83D\uDE00b")
        assertNull(map.cut(-1, 2))
        assertNull(map.cut(1, 1))
        assertNull(map.cut(0, 5))
        assertNull(map.cut(1, 2))
        assertEquals("ab", map.cut(1, 3))
    }
}
