package com.orgzly.android.ui.notes.book

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.usecase.BookCycleVisibility
import com.orgzly.android.usecase.NoteToggleFoldingSubtree
import com.orgzly.android.usecase.UseCaseRunner

class BookViewModel(private val dataRepository: DataRepository, val bookId: Long) : CommonViewModel() {

    enum class FlipperDisplayedChild {
        LOADING,
        LOADED,
        EMPTY,
        DOES_NOT_EXIST
    }

    val flipperDisplayedChild = MutableLiveData(FlipperDisplayedChild.LOADING)

    fun setFlipperDisplayedChild(child: FlipperDisplayedChild) {
        flipperDisplayedChild.value = child
    }

    data class Data(val book: Book?, val notes: List<NoteView>?)

    // Track narrowed state
    val narrowedNoteId = MutableLiveData<Long?>(null)

    val data = narrowedNoteId.switchMap { narrowedId ->
        MediatorLiveData<Data>().apply {
            addSource(dataRepository.getBookLiveData(bookId)) {
                value = Data(it, value?.notes)
            }
            // Query only the narrowed subtree if narrowed, otherwise all visible notes
            addSource(dataRepository.getVisibleNotesLiveData(bookId, narrowedId)) {
                value = Data(value?.book, it)
            }
        }
    }

    fun isNarrowed(): Boolean {
        return narrowedNoteId.value != null
    }

    /**
     * Calculate level offset for indentation when narrowed.
     * Returns null when not narrowed, otherwise returns the narrowed note's level - 1
     * so it displays as root (level 1).
     */
    fun levelOffset(notes: List<NoteView>?): Int? {
        return if (isNarrowed() && notes != null && notes.isNotEmpty()) {
            notes.first().note.position.level - 1
        } else {
            null
        }
    }

    companion object {
        const val APP_BAR_DEFAULT_MODE = 0
        const val APP_BAR_SELECTION_MODE = 1
        const val APP_BAR_SELECTION_MOVE_MODE = 2
    }

    val appBar = AppBar(mapOf(
        APP_BAR_DEFAULT_MODE to null,
        APP_BAR_SELECTION_MODE to APP_BAR_DEFAULT_MODE,
        APP_BAR_SELECTION_MOVE_MODE to APP_BAR_SELECTION_MODE))


    fun cycleVisibility() {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                if (isNarrowed()) {
                    // When narrowed, cycle visibility for the narrowed subtree only
                    narrowedNoteId.value?.let { noteId ->
                        UseCaseRunner.run(NoteToggleFoldingSubtree(noteId))
                    }
                } else {
                    // When not narrowed, cycle visibility for the entire book
                    data.value?.book?.let { book ->
                        UseCaseRunner.run(BookCycleVisibility(book))
                    }
                }
            }
        }
    }

    fun narrowToSubtree(noteId: Long) {
        narrowedNoteId.value = noteId
    }

    fun widenView() {
        narrowedNoteId.value = null
    }

    data class NotesToRefile(val selected: Set<Long>, val count: Int)

    val refileRequestEvent: SingleLiveEvent<NotesToRefile> = SingleLiveEvent()

    fun refile(ids: Set<Long>) {
        App.EXECUTORS.diskIO().execute {
            val count = dataRepository.getNotesAndSubtreesCount(ids)
            refileRequestEvent.postValue(NotesToRefile(ids, count))
        }
    }


    val notesDeleteRequest: SingleLiveEvent<Pair<Set<Long>, Int>> = SingleLiveEvent()

    fun requestNotesDelete(ids: Set<Long>) {
        App.EXECUTORS.diskIO().execute {
            val count = dataRepository.getNotesAndSubtreesCount(ids)
            notesDeleteRequest.postValue(Pair(ids, count))
        }
    }
}