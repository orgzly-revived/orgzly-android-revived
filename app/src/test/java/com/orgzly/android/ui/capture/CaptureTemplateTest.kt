package com.orgzly.android.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTemplateTest {

    @Test
    fun normalizeHeadlinePathReturnsNullForBlankOrSlashOnlyInput() {
        assertNull(normalizeHeadlinePath(null))
        assertNull(normalizeHeadlinePath(""))
        assertNull(normalizeHeadlinePath("   "))
        assertNull(normalizeHeadlinePath("/"))
        assertNull(normalizeHeadlinePath("//"))
        assertNull(normalizeHeadlinePath(" /  / "))
    }

    @Test
    fun normalizeHeadlinePathCollapsesSeparatorsAndTrimsComponents() {
        assertEquals("Projects", normalizeHeadlinePath("Projects"))
        assertEquals("Projects/Active", normalizeHeadlinePath(" Projects / Active "))
        assertEquals("Projects/Active", normalizeHeadlinePath("//Projects///Active//"))
    }
}
