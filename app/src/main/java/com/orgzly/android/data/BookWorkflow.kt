package com.orgzly.android.data

import android.content.Context
import com.orgzly.android.db.dao.BookDao
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

    /** The workflow of each notebook that declares one; an absent notebook uses the app's. */
    fun declaredBy(prefaces: List<BookDao.BookPreface>): Map<Long, StateWorkflows> =
        prefaces.mapNotNull { book -> fromPreface(book.preface)?.let { book.id to it } }.toMap()

    fun todoKeywords(context: Context, preface: String?): Set<String> =
        of(context, preface).flatMapTo(LinkedHashSet()) { it.todoKeywords }

    fun doneKeywords(context: Context, preface: String?): Set<String> =
        of(context, preface).flatMapTo(LinkedHashSet()) { it.doneKeywords }

    /** The states in every one of [states], in the order of the first. */
    fun shared(states: List<List<String>>): List<String> =
        states.firstOrNull().orEmpty().filter { state -> states.all { state in it } }

    /** The notebook's own `#+TODO:` as written, or null when it declares none. */
    fun todoLineValue(preface: String?): String? =
        preface?.let { OrgFileSettings.fromPreface(it) }
            ?.getKeywordValues("TODO")
            ?.firstOrNull { it.isNotBlank() }

    /**
     * Sets the preface's `#+TODO:` to [workflow], or removes it when null. Several `#+TODO:` lines
     * collapse into one; `#+SEQ_TODO:` and `#+TYP_TODO:` came from elsewhere and are kept.
     */
    fun withWorkflowInPreface(preface: String?, workflow: String?): String {
        val line = workflow?.trim()?.takeIf { it.isNotEmpty() }?.let { "#+TODO: $it" }

        val original = preface ?: ""
        if (original.isEmpty()) {
            return line ?: ""
        }

        val lines = original.lines()
        val isTodo = { l: String -> l.trimStart().startsWith("#+TODO:", true) }
        val first = lines.indexOfFirst(isTodo)

        val out = ArrayList<String>(lines.size + 1)
        lines.forEachIndexed { i, l ->
            when {
                !isTodo(l) -> out.add(l)
                i == first && line != null -> out.add(line)
                else -> Unit // a later #+TODO: line collapses into the first
            }
        }

        if (line != null && first < 0) {
            out.add(0, line)
        }

        return out.joinToString("\n")
    }
}
