package com.orgzly.android.util

import kotlin.math.roundToInt

class StateChangeParentTitleUpdater(
    private val todoKeywords: Collection<String>,
    private val doneKeywords: Collection<String>
) {
    fun updateTitleForStates(title: String, childStates: Collection<String?>): String {
        val percentageRegex = Regex("\\[\\d*%]")
        val fractionRegex = Regex("\\[\\d*/\\d*]")

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
}