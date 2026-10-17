package com.orgzly.android.ui.notes.book

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.NotesClipboard
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.NoteView
import androidx.lifecycle.LiveData
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.usecase.BookCycleVisibility
import com.orgzly.android.usecase.BookUpdatePreface
import com.orgzly.android.usecase.NoteCopy
import com.orgzly.android.usecase.NoteCut
import com.orgzly.android.usecase.NoteDelete
import com.orgzly.android.usecase.NoteDemote
import com.orgzly.android.usecase.NoteMove
import com.orgzly.android.usecase.NotePaste
import com.orgzly.android.usecase.NotePromote
import com.orgzly.android.usecase.NoteToggleFoldingSubtree
import com.orgzly.android.usecase.UseCase
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

    val tags: LiveData<List<String>> by lazy {
        dataRepository.selectAllTagsLiveData()
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

    sealed class ClipboardChange {
        data class Cut(val count: Int) : ClipboardChange()
        data class Copied(val count: Int) : ClipboardChange()
        data class Pasted(val count: Int) : ClipboardChange()
    }

    val clipboardChangeEvent: SingleLiveEvent<ClipboardChange> = SingleLiveEvent()

    fun cutNotes(ids: Set<Long>) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                val clipboard = UseCaseRunner.run(NoteCut(bookId, ids)).userData as NotesClipboard
                clipboardChangeEvent.postValue(ClipboardChange.Cut(clipboard.count))
            }
        }
    }

    fun copyNotes(ids: Set<Long>) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                val clipboard = UseCaseRunner.run(NoteCopy(bookId, ids)).userData as NotesClipboard
                clipboardChangeEvent.postValue(ClipboardChange.Copied(clipboard.count))
            }
        }
    }

    fun pasteNotes(noteId: Long, place: Place) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                val count = UseCaseRunner.run(NotePaste(bookId, noteId, place)).userData as Int
                clipboardChangeEvent.postValue(ClipboardChange.Pasted(count))
            }
        }
    }

    fun deleteNotes(ids: Set<Long>) {
        runUseCase(NoteDelete(bookId, ids))
    }

    fun promoteNotes(ids: Set<Long>) {
        runUseCase(NotePromote(ids))
    }

    fun demoteNotes(ids: Set<Long>) {
        runUseCase(NoteDemote(ids))
    }

    fun moveNotes(ids: Set<Long>, offset: Int) {
        runUseCase(NoteMove(bookId, ids, offset))
    }

    fun updatePreface(preface: String) {
        runUseCase(BookUpdatePreface(bookId, preface))
    }

    private fun runUseCase(useCase: UseCase) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                UseCaseRunner.run(useCase)
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