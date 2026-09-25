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
    fun parseTemplatePropertiesReturnsEmptyForNoUsableLines() {
        assertEquals(emptyList<Pair<String, String>>(), parseTemplateProperties(null))
        assertEquals(emptyList<Pair<String, String>>(), parseTemplateProperties(""))
        assertEquals(emptyList<Pair<String, String>>(), parseTemplateProperties("   \n  \n"))
        assertEquals(
            emptyList<Pair<String, String>>(),
            parseTemplateProperties(":PROPERTIES:\n:END:"))
        assertEquals(emptyList<Pair<String, String>>(), parseTemplateProperties("no colon here"))
        // A colon not followed by whitespace is not a property line.
        assertEquals(
            emptyList<Pair<String, String>>(),
            parseTemplateProperties("https://example.com"))
    }

    @Test
    fun parseTemplatePropertiesAcceptsBareAndDrawerSyntax() {
        assertEquals(
            listOf("CREATED" to "%U", "CREATED_BY" to "henk"),
            parseTemplateProperties("CREATED: %U\nCREATED_BY: henk"))

        assertEquals(
            listOf("CREATED" to "%U", "CREATED_BY" to "henk"),
            parseTemplateProperties(":PROPERTIES:\n:CREATED: %U\n:CREATED_BY: henk\n:END:"))
    }

    @Test
    fun parseTemplatePropertiesKeepsColonsInValuesAndAllowsEmptyValue() {
        assertEquals(
            listOf("URL" to "https://orgzlyrevived.com"),
            parseTemplateProperties(":URL: https://orgzlyrevived.com"))
        assertEquals(listOf("CATEGORY" to ""), parseTemplateProperties(":CATEGORY:"))
    }

    @Test
    fun normalizeHeadlinePathCollapsesSeparatorsAndTrimsComponents() {
        assertEquals("Projects", normalizeHeadlinePath("Projects"))
        assertEquals("Projects/Active", normalizeHeadlinePath(" Projects / Active "))
        assertEquals("Projects/Active", normalizeHeadlinePath("//Projects///Active//"))
    }
}
