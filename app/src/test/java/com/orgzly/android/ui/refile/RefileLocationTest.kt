package com.orgzly.android.ui.refile

import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefileLocationTest {

    @Test
    fun everyRefileLocationFieldHasStableSerializedName() {
        // Release builds are minified; without explicit names Gson writes R8's renamed fields.
        val fields = RefileLocation::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            val annotation = field.getAnnotation(SerializedName::class.java)
            assertEquals("field ${field.name}", field.name, annotation?.value)
        }
    }
}
