package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.org.utils.LogDone

/**
 * Resolves what to record when a note is marked as done.
 *
 * Follows org's `org-log-done`, which the app setting supplies and narrower scopes override.
 */
object LogDonePolicy {

    fun resolve(context: Context): LogDone = fromPreferences(context)

    private fun fromPreferences(context: Context): LogDone =
        if (AppPreferences.logDone(context)) LogDone.TIME else LogDone.NONE
}
