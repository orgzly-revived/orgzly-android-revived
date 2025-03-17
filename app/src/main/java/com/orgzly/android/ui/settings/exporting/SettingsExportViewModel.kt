package com.orgzly.android.ui.settings.exporting

import androidx.lifecycle.MutableLiveData
import com.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.usecase.UseCase.Companion.SYNC_NOT_REQUIRED
import com.orgzly.android.usecase.UseCaseResult
import com.orgzly.android.util.LogUtils
import java.util.Stack

class SettingsExportViewModel(
    val dataRepository: DataRepository,
    val noteIds: Set<Long>,
    val count: Int) : CommonViewModel() {

    class Home
    class Parent

    data class Item(val payload: Any? = null, val name: String? = null)

    private val breadcrumbs = Stack<Item>()

    val data = MutableLiveData<Pair<Stack<Item>, List<Item>>>()

    val exportedEvent: SingleLiveEvent<UseCaseResult> = SingleLiveEvent()

    fun openForTheFirstTime() {
        var item: Item? = null
        try {
            val targetNote = dataRepository.findUniqueNoteHavingProperty(
                "ID", AppPreferences.settingsExportAndImportNoteId(App.getAppContext()))
            val note = dataRepository.getNote(targetNote!!.noteId)
            val parentNote = dataRepository.getNoteAncestors(note!!.id).last()
            item = replayUntilNoteId(parentNote.id)
        } catch (_: Exception) {}
        if (item == null)
            item = HOME
        open(item)
    }

    fun open(item: Item) {
        val payload = item.payload

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, payload)

        when (payload) {
            is Parent -> {
                breadcrumbs.pop()

                open(breadcrumbs.pop())
            }

            is Home -> {
                App.EXECUTORS.diskIO().execute {
                    val items = dataRepository.getBooks().map { book ->
                        Item(book.book, book.book.name)
                    }

                    breadcrumbs.clear()
                    breadcrumbs.push(HOME)

                    data.postValue(Pair(breadcrumbs, items))
                }
            }

            is Book -> {
                App.EXECUTORS.diskIO().execute {
                    val items = dataRepository.getTopLevelNotes(payload.id).map { note ->
                        Item(note, note.title)
                    }

                    breadcrumbs.push(item)

                    data.postValue(Pair(breadcrumbs, items))
                }
            }

            is Note -> {
                App.EXECUTORS.diskIO().execute {
                    val items = dataRepository.getNoteChildren(payload.id).map { note ->
                        Item(note, note.title)
                    }

                    if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Items for $payload: $items")

                    if (items.isNotEmpty()) {
                        breadcrumbs.push(item)

                        data.postValue(Pair(breadcrumbs, items))
                    }
                }
            }
        }
    }

    fun export(item: Item) {
        try {
            val note = item.payload as Note
            dataRepository.exportSettingsAndSearchesToNote(note)
            exportedEvent.postValue(UseCaseResult(
                modifiesLocalData = true,
                // Let's not trigger auto-sync, in case of accidental export to the wrong note.
                triggersSync = SYNC_NOT_REQUIRED,
                userData = note.title,
            ))
        } catch (e: Exception) {
            exportedEvent.postValue(UseCaseResult(
                modifiesLocalData = false,
                triggersSync = SYNC_NOT_REQUIRED,
                userData = e,
            ))
        }
    }

    private fun replayUntilNoteId(noteId: Long): Item? {
        val notes = dataRepository.getNoteAndAncestors(noteId)

        if (notes.isNotEmpty()) {
            val lastNote = notes.last()

            val book = dataRepository.getBook(lastNote.position.bookId)

            if (book != null) {
                breadcrumbs.clear()
                breadcrumbs.add(HOME)
                breadcrumbs.add(Item(book, book.name))

                for (i in 0 until notes.count() - 1) {
                    val note = notes[i]

                    val item = Item(note, note.title)

                    breadcrumbs.push(item)
                }


                return Item(lastNote, lastNote.title)
            }
        }

        return null
    }

    fun onBreadcrumbClick(item: Item) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, item)
        while (breadcrumbs.pop() != item) {
            // Pop up to and including clicked item
        }
        open(item)
    }

    companion object {
        val HOME = Item(Home())
        private val TAG = SettingsExportViewModel::class.java.name
    }
}