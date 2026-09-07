package com.orgzly.android.util

/** Tracks rendered characters back to source while the formatter removes Org syntax. */
class SourceTextMap(private val source: String) {
    private val offsets = source.indices.toMutableList()
    private val wrappers = mutableListOf<IntRange>()

    /** Replacement offsets refer to the text before this replacement; -1 is generated text. */
    fun replace(start: Int, end: Int, replacement: IntArray, removeWrapper: Boolean = false) {
        if (removeWrapper) {
            val original = offsets.subList(start, end).filter { it >= 0 }
            if (original.isNotEmpty()) wrappers.add(original.min()..original.max())
        }
        val mapped = replacement.map { if (it < 0) -1 else offsets[it] }
        offsets.subList(start, end).clear()
        offsets.addAll(start, mapped)
    }

    /** Returns null for invalid selections or selections containing generated/hidden content. */
    fun cut(start: Int, end: Int): String? {
        if (start < 0 || end <= start || end > offsets.size) return null
        val selected = offsets.subList(start, end)
        if (selected.any { it < 0 }) return null

        val removed = BooleanArray(source.length)
        selected.forEach { removed[it] = true }
        // Remove a link/formatting wrapper only when all its visible content is selected.
        // Partial selections leave the surrounding syntax and unselected characters intact.
        for (wrapper in wrappers) {
            val visible = offsets.filter { it in wrapper }
            if (visible.isNotEmpty() && visible.all { removed[it] }) {
                wrapper.forEach { removed[it] = true }
            }
        }
        for (i in 0 until source.lastIndex) {
            if (source[i].isHighSurrogate() && source[i + 1].isLowSurrogate()
                && removed[i] != removed[i + 1]) return null
        }
        return source.filterIndexed { index, _ -> !removed[index] }
    }
}
