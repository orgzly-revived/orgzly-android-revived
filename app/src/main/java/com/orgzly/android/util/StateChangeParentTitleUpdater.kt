package com.orgzly.android.util

import kotlin.math.roundToInt

class StateChangeParentTitleUpdater(
    private val todoKeywords: Collection<String>,
    private val doneKeywords: Collection<String>
) {
    private val percentageRegex = Regex("\\[\\d*%]")
    private val fractionRegex = Regex("\\[\\d*/\\d*]")
    private val uncheckedRegex =
        Regex("^\\s*(?:-|\\d+\\.)\\s+\\[[ -]\\]\\s+.*$", RegexOption.MULTILINE)
    private val checkedRegex =
        Regex("^\\s*(?:-|\\d+\\.)\\s+\\[X\\]\\s+.*$", RegexOption.MULTILINE)

    private fun percentage(done: Int, total: Int): Int =
        if (total == 0) 0 else ((done.toDouble() / total) * 100).roundToInt()

    fun updateTitleForChildStates(title: String, childStates: Collection<String?>): String {
        if (!percentageRegex.containsMatchIn(title) && !fractionRegex.containsMatchIn(title)) {
            return title
        }

        val doneCount = childStates.count { this.doneKeywords.contains(it) }
        val totalCount = doneCount + childStates.count { todoKeywords.contains(it) }

        return title
            .replace(percentageRegex, "[${percentage(doneCount, totalCount)}%]")
            .replace(fractionRegex, "[$doneCount/$totalCount]")
    }

    fun updateTitleForPossibleCheckboxes(title: String, content: String): String {
        if ((!percentageRegex.containsMatchIn(title) && !fractionRegex.containsMatchIn(title)) ||
            (!uncheckedRegex.containsMatchIn(content) && !checkedRegex.containsMatchIn(content))) {
            return title
        }

        val doneCount = checkedRegex.findAll(content).count()
        val totalCount = uncheckedRegex.findAll(content).count() + doneCount

        return title
            .replace(percentageRegex, "[${percentage(doneCount, totalCount)}%]")
            .replace(fractionRegex, "[$doneCount/$totalCount]")
    }
}