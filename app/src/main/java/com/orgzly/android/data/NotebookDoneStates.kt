package com.orgzly.android.data

import com.orgzly.android.db.dao.BookDao

/**
 * The done states of each notebook declaring its own workflow. The app's are passed in rather
 * than held, because that setting can change while this stays the same.
 */
data class NotebookDoneStates(private val byBook: Map<Long, Set<String>>) {

    fun isDone(bookId: Long, state: String?, appDoneStates: Set<String>): Boolean =
        state != null && state in (byBook[bookId] ?: appDoneStates)

    companion object {
        val NONE = NotebookDoneStates(emptyMap())

        fun declaredBy(prefaces: List<BookDao.BookPreface>): NotebookDoneStates =
            NotebookDoneStates(BookWorkflow.declaredBy(prefaces).mapValues { (_, workflows) ->
                workflows.flatMapTo(HashSet()) { it.doneKeywords }
            })
    }
}
