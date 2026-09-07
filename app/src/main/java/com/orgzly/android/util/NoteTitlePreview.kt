package com.orgzly.android.util

/** A short, single-line identifier for a note in an action dialog. */
object NoteTitlePreview {
    fun from(title: String): String {
        val words = title.split(Regex("[\\s\\p{Z}]+")).filter { it.isNotEmpty() }
        val beginning = words.take(8).joinToString(" ")
        val end = beginning.offsetByCodePoints(0, minOf(80, beginning.codePointCount(0, beginning.length)))
        val preview = beginning.substring(0, end).trimEnd()
        return preview + if (words.size > 8 || end < beginning.length) "…" else ""
    }
}
