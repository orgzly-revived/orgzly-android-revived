package com.orgzly.android.ui

import android.content.Context

import com.orgzly.android.data.BookWorkflow

import java.util.ArrayList

class NoteStates {
    private val values = ArrayList<String>()

    val array: Array<String>
        get() = values.toTypedArray()

    fun indexOf(keyword: String) = values.indexOf(keyword)

    operator fun get(i: Int) = values[i]

    companion object {
        const val NO_STATE_KEYWORD = "NOTE"

        @JvmStatic
        fun fromPreferences(context: Context): NoteStates = fromBook(context, null)

        /** The notebook's own workflow, or the app's states when it declares none. */
        @JvmStatic
        fun fromBook(context: Context, preface: String?): NoteStates {
            val noteStates = NoteStates()

            noteStates.values.addAll(BookWorkflow.todoKeywords(context, preface))
            noteStates.values.addAll(BookWorkflow.doneKeywords(context, preface))

            return noteStates
        }

        /** The states every notebook declares, so the one picked is valid in each of them. */
        @JvmStatic
        fun fromBooks(context: Context, prefaces: List<String?>): NoteStates {
            val noteStates = NoteStates()

            noteStates.values.addAll(
                BookWorkflow.shared(prefaces.map { fromBook(context, it).values }))

            return noteStates
        }

        @JvmStatic
        fun isKeyword(keyword: String?): Boolean {
            return keyword != null && keyword != NO_STATE_KEYWORD
        }
    }
}