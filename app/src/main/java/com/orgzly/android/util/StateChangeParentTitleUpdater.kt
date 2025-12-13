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

    private fun countMatches(content: String, regex: Regex): Int {
        return regex.findAll(content).count()
    }

    fun updateTitleForChildStates(title: String, childStates: Collection<String?>): String {
        if (!percentageRegex.containsMatchIn(title) && !fractionRegex.containsMatchIn(title)) {
            return title
        }

        val doneCount = childStates.count { this.doneKeywords.contains(it) }
        val totalCount = doneCount + childStates.count { todoKeywords.contains(it) }
        val percentage = ((doneCount.toDouble() / totalCount) * 100).roundToInt()

        return title
            .replace(percentageRegex, "[$percentage%]")
            .replace(fractionRegex, "[$doneCount/$totalCount]")
    }

    fun updateTitleForPossibleCheckboxes(title: String, content: String): String {
        if ((!percentageRegex.containsMatchIn(title) && !fractionRegex.containsMatchIn(title)) ||
            (!uncheckedRegex.containsMatchIn(content) && !checkedRegex.containsMatchIn(content))) {
            return title
        }

        val undoneCount = countMatches(content, uncheckedRegex)
        val doneCount = countMatches(content, checkedRegex)
        val totalCount = undoneCount + doneCount
        val percentage = ((doneCount.toDouble() / totalCount) * 100).roundToInt()

        return title
            .replace(percentageRegex, "[$percentage%]")
            .replace(fractionRegex, "[$doneCount/$totalCount]")
    }
}