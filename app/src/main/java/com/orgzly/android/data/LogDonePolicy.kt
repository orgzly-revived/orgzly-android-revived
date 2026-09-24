package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.org.OrgFileSettings
import com.orgzly.org.utils.LogDone

/**
 * What to record when a note is marked done, as org's `org-log-done`: an inherited `LOGGING`
 * property beats a notebook's `#+STARTUP:`, which beats the app setting.
 */
object LogDonePolicy {

    private val WHITESPACE = Regex("\\s+")

    const val LOGGING = "LOGGING"

    fun resolve(loggingProperty: String?, preface: String?, appSetting: LogDone): LogDone =
        fromLoggingProperty(loggingProperty) ?: fromPreface(preface) ?: appSetting

    /** Null when absent. LOGGING replaces all logging settings, so silence on done means off. */
    fun fromLoggingProperty(value: String?): LogDone? =
        value?.let { lastLogDone(it.trim().split(WHITESPACE)) ?: LogDone.NONE }

    /** Null when no done token is set, because `#+STARTUP:` changes only what it names. */
    fun fromPreface(preface: String?): LogDone? =
        preface
            ?.let { OrgFileSettings.fromPreface(it) }
            ?.getKeywordValues(STARTUP)
            ?.let { values -> lastLogDone(values.flatMap { it.trim().split(WHITESPACE) }) }

    fun fromPreferences(context: Context): LogDone =
        if (AppPreferences.logDone(context)) LogDone.TIME else LogDone.NONE

    private fun lastLogDone(tokens: List<String>): LogDone? =
        tokens.mapNotNull(LogDone::fromToken).lastOrNull()

    private const val STARTUP = "STARTUP"
}
