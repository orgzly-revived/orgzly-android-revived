package com.orgzly.android.ui.capture

import androidx.lifecycle.MutableLiveData
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.ui.refile.RefileViewModel
import java.util.Stack
import java.util.concurrent.Executors

/**
 * Drives the headline picker used when editing a capture template's target heading.
 *
 * Navigation mirrors the refile dialog but is scoped to a single book: the picker starts
 * at the book root and drills down through existing headings. The user can either select
 * an existing heading (or the book root) as the target, or create a brand new child heading
 * under the current location. The heading is stored as a "/"-separated path of titles,
 * matching what [CaptureTemplateResolver] expects.
 *
 * All navigation/selection work runs on a dedicated single-threaded executor so the
 * breadcrumb [Stack] is mutated by one thread at a time, and only immutable snapshots are
 * published to [data].
 */
class CaptureTemplateHeadlinePickerViewModel(
    val dataRepository: DataRepository,
    private val bookId: Long
) : CommonViewModel() {

    data class Result(val path: String, val label: String, val ambiguous: Boolean)

    private val executor = Executors.newSingleThreadExecutor()

    private val breadcrumbs = Stack<RefileViewModel.Item>()

    private var bookName: String = ""

    val data = MutableLiveData<Pair<List<RefileViewModel.Item>, List<RefileViewModel.Item>>>()

    val selectedEvent: SingleLiveEvent<Result> = SingleLiveEvent()

    fun openForTheFirstTime() {
        executor.execute {
            catchAndPostError {
                if (breadcrumbs.isNotEmpty()) {
                    // View was recreated (e.g. rotation) while the picker was open;
                    // refresh the current location instead of pushing the root again.
                    reopenCurrent()
                    return@catchAndPostError
                }

                val book = dataRepository.getBook(bookId)
                    ?: throw IllegalStateException("Notebook not found")
                bookName = book.name
                openSync(RefileViewModel.Item(book, book.title ?: book.name))
            }
        }
    }

    fun open(item: RefileViewModel.Item) {
        executor.execute {
            catchAndPostError { openSync(item) }
        }
    }

    private fun reopenCurrent() {
        // Re-run the current location so its children are recomputed without duplicating it.
        openSync(breadcrumbs.pop())
    }

    private fun openSync(item: RefileViewModel.Item) {
        when (val payload = item.payload) {
            is RefileViewModel.Parent -> {
                breadcrumbs.pop()
                openSync(breadcrumbs.pop())
            }

            is Book -> {
                val items = dataRepository.getTopLevelNotes(payload.id).map { note ->
                    RefileViewModel.Item(note, note.title)
                }
                breadcrumbs.push(item)
                postData(items)
            }

            is Note -> {
                val items = dataRepository.getNoteChildren(payload.id).map { note ->
                    RefileViewModel.Item(note, note.title)
                }
                if (items.isNotEmpty()) {
                    breadcrumbs.push(item)
                    postData(items)
                }
            }
        }
    }

    private fun postData(items: List<RefileViewModel.Item>) {
        data.postValue(Pair(breadcrumbs.toList(), items))
    }

    fun openParent() {
        open(PARENT)
    }

    fun onBreadcrumbClick(item: RefileViewModel.Item) {
        executor.execute {
            catchAndPostError {
                while (breadcrumbs.isNotEmpty() && breadcrumbs.pop() != item) {
                    // Pop up to and including the clicked item; openSync re-pushes it.
                }
                openSync(item)
            }
        }
    }

    fun locationHasParent(): Boolean {
        return breadcrumbs.size > 1
    }

    /** Select the current location (book root or current heading) as the target. */
    fun selectHere() {
        executor.execute {
            catchAndPostError {
                if (breadcrumbs.isEmpty()) return@catchAndPostError
                emitSelection(breadcrumbs.peek().payload)
            }
        }
    }

    /** Select the given row's note as the target heading. */
    fun select(item: RefileViewModel.Item) {
        executor.execute {
            catchAndPostError {
                emitSelection(item.payload)
            }
        }
    }

    private fun emitSelection(payload: Any?) {
        when (payload) {
            is Book -> {
                // Book root: empty path means "top level of the book".
                selectedEvent.postValue(Result("", bookName, false))
            }

            is Note -> {
                val path = pathForNote(payload.id)
                val ambiguous =
                    dataRepository.getNoteAtPath("$bookName/$path")?.note?.id != payload.id
                selectedEvent.postValue(Result(path, path, ambiguous))
            }
        }
    }

    /** Create a new child heading under the current location and select it. */
    fun createHeadingHere(name: String) {
        executor.execute {
            catchAndPostError {
                if (breadcrumbs.isEmpty()) return@catchAndPostError
                val basePath = when (val payload = breadcrumbs.peek().payload) {
                    is Note -> pathForNote(payload.id)
                    else -> ""
                }
                val newPath = if (basePath.isEmpty()) name else "$basePath/$name"
                selectedEvent.postValue(Result(newPath, newPath, false))
            }
        }
    }

    private fun pathForNote(noteId: Long): String {
        return dataRepository.getNoteAndAncestors(noteId).joinToString("/") { it.title }
    }

    override fun onCleared() {
        super.onCleared()
        executor.shutdown()
    }

    companion object {
        private val PARENT = RefileViewModel.Item(RefileViewModel.Parent())
    }
}
