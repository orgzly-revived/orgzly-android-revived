package com.orgzly.android.ui.capture

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.util.UUID

data class CaptureTemplate(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerializedName("name")
    val name: String = "",
    @SerializedName("title")
    val title: String = "",
    @SerializedName("content")
    val content: String = "",
    /**
     * Properties to set on the captured note, one "KEY: value" per line.
     * Nullable because Gson does not apply Kotlin defaults to templates stored before this
     * field existed.
     */
    @SerializedName("properties")
    val properties: String? = null,
    @SerializedName("targetBook")
    val targetBook: String = "",
    @SerializedName("targetHeadline")
    val targetHeadline: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("priority")
    val priority: String = "",
    @SerializedName("tags")
    val tags: String = "",
    @SerializedName("isScheduled")
    val isScheduled: Boolean = false
)

/**
 * Field names of [CaptureTemplate] in the 1.23.x releases, in declaration order.
 *
 * Those builds were minified without stable serialized names, so R8 renamed the fields "a"
 * through "j" and Gson stored templates under those keys. Maps each short key back to its field.
 */
private val LEGACY_TEMPLATE_KEYS: Map<String, String> = listOf(
    "id", "name", "title", "content", "targetBook", "targetHeadline",
    "state", "priority", "tags", "isScheduled"
).mapIndexed { index, field -> ('a' + index).toString() to field }.toMap()

/**
 * Returns [json] rewritten with current field names if it holds templates stored by a 1.23.x
 * release, or null if nothing needed rewriting. Elements already using current names are kept.
 */
fun migrateLegacyCaptureTemplatesJson(json: String?): String? {
    if (json.isNullOrBlank()) return null

    val array = try {
        JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray
    } catch (e: JsonSyntaxException) {
        null
    } ?: return null

    var migrated = false
    val result = JsonArray()
    for (element in array) {
        val legacy = element.takeIf { it.isJsonObject }?.asJsonObject?.takeIf { isLegacyTemplate(it) }
        if (legacy == null) {
            result.add(element)
            continue
        }
        migrated = true
        result.add(JsonObject().also { out ->
            legacy.entrySet().forEach { (key, value) -> out.add(LEGACY_TEMPLATE_KEYS.getValue(key), value) }
        })
    }

    return if (migrated) result.toString() else null
}

private fun isLegacyTemplate(obj: JsonObject): Boolean =
    obj.keySet().isNotEmpty() && obj.keySet().all { it in LEGACY_TEMPLATE_KEYS }

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
