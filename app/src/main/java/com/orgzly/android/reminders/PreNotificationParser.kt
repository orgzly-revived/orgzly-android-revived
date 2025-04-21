package com.orgzly.android.reminders

import android.content.Context
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import kotlin.time.Duration

/**
 * Parse the pre-notfication property.
 *
 * The parser can be configured to accept two formats. If the format is
 * [R.string.pref_value_pre_notify_format_minutes] the property value is parsed as
 * a whitespace-separated list of minutes (float values allowed). An example would be `"60 30 5"`.
 * Otherwise, the property is parsed as comma separated list of time interval strings
 * such as `"1d, 1h, 30m, 5m 30s"` (see [kotlin.time.Duration.parse]).
 *
 * @param context The app context used to read the settings.
 */
class PreNotificationParser(context: Context) {
    private val isMinuteFormat: Boolean
    private val delimiter: String

    init {
        val format = AppPreferences.preNotifyFormat(context)
        isMinuteFormat = context.getResources().getString(R.string.pref_value_pre_notify_format_minutes) == format;
        delimiter = if (isMinuteFormat) {
            " "
        } else {
            ";"
        }
    }

    /**
     * Parse a given string [value] into a list of list of time intervals in milliseconds
     * sorted in descending order. For the format see [PreNotificationParser]. Importantly, a period
     * of zero milliseconds will always be included.
     */
    fun parse(value: String): List<Int> {
        var periods = mutableSetOf(0)
        if (value.isNotEmpty()) {
            periods = (value.split(delimiter)
                .map {
                    try {
                        if (isMinuteFormat) {
                            (it.toFloat() * 1000 * 60).toInt()
                        } else {
                            Duration.parse(it.trim()).inWholeMilliseconds.toInt()
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