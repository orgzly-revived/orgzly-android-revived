package com.orgzly.android.ui.capture

import java.util.UUID

data class CaptureTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val title: String = "",
    val content: String = "",
    /**
     * Properties to set on the captured note, one "KEY: value" per line.
     * Nullable because Gson does not apply Kotlin defaults to templates stored before this
     * field existed.
     */
    val properties: String? = null,
    val targetBook: String = "",
    val targetHeadline: String? = null,
    val state: String = "",
    val priority: String = "",
    val tags: String = "",
    val isScheduled: Boolean = false
)

/** Returns the best available display name, using the numbered fallback if needed. */
fun CaptureTemplate.getDisplayName(fallback: String): String =
    name.ifBlank { fallback }

fun normalizeHeadlinePath(path: String?): String? =
    path
        ?.split("/")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.joinToString("/")
        ?.ifBlank { null }

private val PROPERTY_LINE = Regex("""^:?([^:\s]+):(?:[ \t]+(.*))?$""")

/**
 * Parses the template's properties field into name/value pairs.
 *
 * Accepts both "KEY: value" and org's own ":KEY: value", so a drawer pasted from a file works
 * as-is; the surrounding :PROPERTIES:/:END: lines are ignored. As in org, the key's closing colon
 * must be followed by whitespace or the end of the line, so a stray "https://example.com" is not
 * read as a property. Values are not expanded here — [TemplateExpander] runs over them when the
 * note is built.
 */
fun parseTemplateProperties(text: String?): List<Pair<String, String>> =
    text?.lineSequence()
        ?.map { it.trim() }
        ?.filterNot { it.isEmpty() }
        ?.filterNot { it.equals(":PROPERTIES:", true) || it.equals(":END:", true) }
        ?.mapNotNull { line ->
            PROPERTY_LINE.matchEntire(line)?.let { match ->
                match.groupValues[1].trim() to match.groupValues[2].trim()
            }
        }
        ?.filterNot { (name, _) -> name.isEmpty() }
        ?.toList()
        .orEmpty()
