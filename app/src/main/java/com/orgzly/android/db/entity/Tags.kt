package com.orgzly.android.db.entity

import android.util.Log

/**
 * Type-safe wrapper for tags stored in the database.
 * Internal storage format is abstracted away from callers.
 */
data class Tags(val tags: List<String>) {
    companion object {
        private const val TAG = "Tags"

        fun fromString(str: String): Tags {
            val parsed = str.split(" +".toRegex()).filter { it.isNotBlank() }

            // If string is non-blank but produces no valid tags, database is corrupted
            // Log the issue but return empty tags instead of crashing
            if (str.isNotBlank() && parsed.isEmpty()) {
                Log.w(TAG, "Corrupted tags in database: expected space-separated tags, got: '$str'")
                return Tags(emptyList())
            }

            return Tags(parsed)
        }

        fun fromList(list: List<String>?): Tags? {
            return list?.takeIf { it.isNotEmpty() }?.let { Tags(it) }
        }

        fun empty(): Tags = Tags(emptyList())
    }

    override fun toString(): String {
        return tags.joinToString(" ")
    }

    fun toString(separator: String): String {
        return tags.joinToString(separator)
    }

    fun isEmpty(): Boolean = tags.isEmpty()

    fun isNotEmpty(): Boolean = tags.isNotEmpty()
}

/**
 * Extension functions for nullable Tags
 */
fun Tags?.isEmpty(): Boolean = this == null || this.tags.isEmpty()

fun Tags?.isNotEmpty(): Boolean = !isEmpty()

fun Tags?.toList(): List<String> = this?.tags ?: emptyList()
