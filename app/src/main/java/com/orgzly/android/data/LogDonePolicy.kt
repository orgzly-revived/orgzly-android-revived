package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.org.OrgFileSettings
import com.orgzly.org.utils.LogDone

/**
 * Resolves what to record when a note is marked as done.
 *
 * Follows org's `org-log-done`: a notebook's `#+STARTUP:` overrides the app setting, and a
 * scope that says nothing about done-logging defers to the one above it.
 */
object LogDonePolicy {

    private val WHITESPACE = Regex("\\s+")

    fun resolve(context: Context, preface: String?): LogDone =
        fromPreface(preface) ?: fromPreferences(context)

    /** Null when the preface carries no done-logging token, so the caller falls back. */
    fun fromPreface(preface: String?): LogDone? =
        preface
            ?.let { OrgFileSettings.fromPreface(it) }
            ?.getKeywordValues(STARTUP)
            ?.flatMap { it.trim().split(WHITESPACE) }
            ?.mapNotNull(LogDone::fromToken)
            ?.lastOrNull()

    private fun fromPreferences(context: Context): LogDone =
        if (AppPreferences.logDone(context)) LogDone.TIME else LogDone.NONE

    private const val STARTUP = "STARTUP"
}
