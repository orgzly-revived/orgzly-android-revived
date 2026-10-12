package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.StateWorkflows
import com.orgzly.org.OrgFileSettings

/**
 * The state keywords in force for a notebook. A declared workflow replaces the app's states, as
 * in org, so a file may use states the app lacks and omit ones it has.
 */
object BookWorkflow {

    /** Org treats all three as sources of keywords, and a file may use more than one. */
    private val KEYWORDS = listOf("TODO", "SEQ_TODO", "TYP_TODO")

    /** Null when the preface declares no workflow, so the caller falls back to the setting. */
    fun fromPreface(preface: String?): StateWorkflows? {
        val settings = preface?.let { OrgFileSettings.fromPreface(it) } ?: return null

        val lines = KEYWORDS.flatMap { settings.getKeywordValues(it).orEmpty() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return null
        }

        // Each line is one workflow, which is how StateWorkflows already reads the setting.
        return StateWorkflows(lines.joinToString("\n"))
    }

    fun of(context: Context, preface: String?): StateWorkflows =
        fromPreface(preface) ?: StateWorkflows(AppPreferences.states(context))

    fun todoKeywords(context: Context, preface: String?): Set<String> =
        of(context, preface).flatMapTo(LinkedHashSet()) { it.todoKeywords }

    fun doneKeywords(context: Context, preface: String?): Set<String> =
        of(context, preface).flatMapTo(LinkedHashSet()) { it.doneKeywords }

    /** The states in every one of [states], in the order of the first. */
    fun shared(states: List<List<String>>): List<String> =
        states.firstOrNull().orEmpty().filter { state -> states.all { state in it } }
}
