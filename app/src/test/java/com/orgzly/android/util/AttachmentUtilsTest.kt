package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentUtilsTest {

    @Test
    fun testGetAttachDirShortId() {
        assertEquals("ab", AttachmentUtils.getAttachDir("ab"))
        assertEquals("a", AttachmentUtils.getAttachDir("a"))
    }

    @Test
    fun testGetAttachDirLongId() {
        assertEquals("ab/cdef123", AttachmentUtils.getAttachDir("abcdef123"))
    }

    @Test
    fun testGetAttachDirUUID() {
        assertEquals("55/0e8400-e29b-41d4-a716-446655440000", AttachmentUtils.getAttachDir("550e8400-e29b-41d4-a716-446655440000"))
    }
}
