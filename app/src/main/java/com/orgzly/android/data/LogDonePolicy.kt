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

    /** Sets the done token in `#+STARTUP:`, or removes it when null. Other tokens stay. */
    fun withLogDoneInPreface(preface: String?, logDone: LogDone?): String {
        val token = when (logDone) {
            LogDone.NONE -> "nologdone"
            LogDone.TIME -> "logdone"
            LogDone.NOTE -> "lognotedone"
            null -> null
        }

        val original = preface ?: ""
        // "".lines() is one empty line, which would survive as a trailing blank.
        if (original.isEmpty()) return if (token != null) "#+STARTUP: " + token else ""

        val lines = original.lines()
        val startupLines =
            lines.indices.filter { lines[it].trimStart().startsWith(STARTUP_PREFIX, true) }
        val target = startupLines.lastOrNull()

        val out = ArrayList<String>(lines.size + 1)

        for (i in lines.indices) {
            if (i !in startupLines) {
                out.add(lines[i])
                continue
            }

            val colon = lines[i].indexOf(':')
            var kept = lines[i].substring(colon + 1)
                .trim()
                .split(WHITESPACE)
                .filter { it.isNotBlank() && LogDone.fromToken(it) == null }

            if (i == target && token != null) {
                kept = kept + token
            }

            if (kept.isNotEmpty()) {
                out.add(lines[i].substring(0, colon + 1) + " " + kept.joinToString(" "))
            }
        }

        if (token != null && target == null) {
            out.add(0, "#+STARTUP: " + token)
        }

        return out.joinToString("\n")
    }

    private const val STARTUP_PREFIX = "#+STARTUP:"

    private const val STARTUP = "STARTUP"
}
