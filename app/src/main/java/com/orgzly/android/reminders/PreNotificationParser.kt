package com.orgzly.android.reminders

import android.content.Context
import kotlin.time.Duration

/**
 * Parse the pre-notfication property.
 *
 * The format for specifying the pre-notification delay is a whitespace separated list of
 * elements that can be integers (interpreted as minutes) or quoted strings will be parsed
 * by [kotlin.time.Duration.parse].
 *
 * An example is `10 "2h" "1 day"` corresponding to three pre-notifications 10 minutes, two hours
 * and one day in advance, respectively.
 */
class PreNotificationParser() {
    companion object {
        private val periodRegex: Regex = Regex("(?:([0-9]+)|\"(.*?)\"\\s*)")
    }

    /**
     * Parse a given string [value] into a list of list of time intervals in milliseconds
     * sorted in descending order. For the format see [PreNotificationParser]. Importantly, a period
     * of zero milliseconds will always be included.
     */
    fun parse(value: String): List<Int> {
        var periods = mutableSetOf(0)
        if (value.isNotEmpty()) {
            periods = (periodRegex.findAll(value)
                .map {
                    try {
                        val minutesValue = it.groupValues[1]
                        if (minutesValue.isNotEmpty()) {
                            (minutesValue.toFloat() * 1000 * 60).toInt()
                        } else {
                            Duration.parse(it.groupValues[2].trim()).inWholeMilliseconds.toInt()
                        }
                    } catch (_: Exception) {
                        0 // safely default to 0
                    }
                }).toMutableSet()
        }

        // always notify at the actual reminder time
        periods.add(0)

        return periods.sortedDescending()
    }
}