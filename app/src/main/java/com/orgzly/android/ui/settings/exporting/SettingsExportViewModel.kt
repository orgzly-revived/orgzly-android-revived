package com.orgzly.android.ui.settings.exporting

import androidx.lifecycle.MutableLiveData
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.dao.NoteDao.NoteIdBookId
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.Note
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.usecase.UseCase.Companion.SYNC_DATA_MODIFIED
import com.orgzly.android.usecase.UseCaseResult
import com.orgzly.android.util.LogUtils
import java.util.Stack
import java.util.UUID

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
        val targetNote: NoteIdBookId? = dataRepository.findUniqueNoteHavingProperty(
            "ID", AppPreferences.settingsExportAndImportNoteId(App.getAppContext()))
        var item: Item? = null
        if (targetNote != null) {
            val note = dataRepository.getNote(targetNote.noteId)
            if (note != null) {
                val parentNote = dataRepository.getNoteAncestors(note.id).last()
                item = replayUntilNoteId(parentNote.id)
            }
        }
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
        val payload = item.payload as Note
        val notePayload = dataRepository.getNotePayload(payload.id) ?: throw RuntimeException(
            App.getAppContext().getString(R.string.failed_to_get_note_payload))
        if (notePayload.properties.get("ID") == null) {
            // Note has no "ID" property - let's add one
            notePayload.properties.put("ID", UUID.randomUUID().toString())
            dataRepository.updateNote(payload.id, notePayload)
        } else {
            // Check that the note's "ID" property value is unique
            if (dataRepository.findNotesHavingProperty("ID", notePayload.properties.get("ID")).size > 1) {
                exportedEvent.postValue(
                    UseCaseResult(
                        modifiesLocalData = true,
                        triggersSync = SYNC_DATA_MODIFIED,
                        userData = RuntimeException(App.getAppContext().getString(R.string.selected_notes_id_property_value_is_not_unique))
                    )
                )
                return
            }
        }
        AppPreferences.settingsExportAndImportNoteId(App.getAppContext(), notePayload.properties.get("ID"))
        dataRepository.exportSettingsAndSearchesToSelectedNote()
        exportedEvent.postValue(UseCaseResult(
            modifiesLocalData = true,
            triggersSync = SYNC_DATA_MODIFIED,
            userData = notePayload
        ))
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