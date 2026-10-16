package com.orgzly.android.ui.capture

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun everyCaptureTemplateFieldHasStableSerializedName() {
        // Release builds are minified; without explicit names Gson writes R8's renamed fields.
        val fields = CaptureTemplate::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            val annotation = field.getAnnotation(SerializedName::class.java)
            assertEquals("field ${field.name}", field.name, annotation?.value)
        }
    }

    @Test
    fun migrateLegacyCaptureTemplatesJsonRestoresFieldNamesFrom123Release() {
        // Exactly as written by the minified 1.23.0 Play build; "f" (targetHeadline) was null.
        val legacy = """[{"a":"d07f47a1-f1cc-4474-a73d-175cdb44c332","b":"todo","c":"","d":"",""" +
            """"e":"refile","g":"TODO","h":"C","i":"@home","j":true}]"""

        val migrated = migrateLegacyCaptureTemplatesJson(legacy)

        val type = object : TypeToken<List<CaptureTemplate>>() {}.type
        val templates: List<CaptureTemplate> = Gson().fromJson(migrated, type)
        assertEquals(
            listOf(CaptureTemplate(
                id = "d07f47a1-f1cc-4474-a73d-175cdb44c332",
                name = "todo",
                targetBook = "refile",
                state = "TODO",
                priority = "C",
                tags = "@home",
                isScheduled = true)),
            templates)
    }

    @Test
    fun migrateLegacyCaptureTemplatesJsonLeavesCurrentFormatAlone() {
        assertNull(migrateLegacyCaptureTemplatesJson(null))
        assertNull(migrateLegacyCaptureTemplatesJson(""))
        assertNull(migrateLegacyCaptureTemplatesJson("[]"))
        assertNull(migrateLegacyCaptureTemplatesJson("not json"))
        assertNull(migrateLegacyCaptureTemplatesJson("""[{"id":"x","name":"todo","isScheduled":true}]"""))
    }

    @Test
    fun migrateLegacyCaptureTemplatesJsonKeepsCurrentElementsInMixedArray() {
        val mixed = """[{"id":"x","name":"current"},{"a":"y","b":"legacy","j":false}]"""

        val migrated = migrateLegacyCaptureTemplatesJson(mixed)

        assertEquals(
            """[{"id":"x","name":"current"},{"id":"y","name":"legacy","isScheduled":false}]""",
            migrated)
    }
}
