package com.orgzly.android.ui.note

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.BookName
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.mappers.OrgMapper
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.share.ShareActivity
import com.orgzly.android.usecase.*
import com.orgzly.android.util.MiscUtils
import com.orgzly.org.OrgProperties
import com.orgzly.org.datetime.OrgRange
import com.orgzly.org.parser.OrgParserWriter
import java.util.*

data class NoteInitialData(
    val bookId: Long,
    val noteId: Long, // Could be 0 if new note is being created
    val place: Place? = null, // Relative location, used for new notes
    val title: String? = null, // Initial title, used for when sharing
    val content: String? = null, // Initial content, used for when sharing
    val attachmentUri: Uri? = null // Initial attachment Uri, used for when sharing
)

class NoteViewModel(
    private val dataRepository: DataRepository,
    private val initialData: NoteInitialData) : CommonViewModel() {

    var bookId = initialData.bookId
    var noteId = initialData.noteId
    private val place = initialData.place
    private val title = initialData.title
    private val content = initialData.content
    private val attachmentUri = initialData.attachmentUri

    val bookView: MutableLiveData<BookView?> = MutableLiveData()

    val tags: LiveData<List<String>> by lazy {
        dataRepository.selectAllTagsLiveData()
    }

    val attachments: MutableLiveData<List<NoteAttachmentData>> = MutableLiveData(loadAttachments(null))

    data class NoteDetailsData(val book: BookView?, val note: NoteView?, val ancestors: List<Note>)

    val noteDetailsDataEvent: SingleLiveEvent<NoteDetailsData> = SingleLiveEvent()

    val noteCreatedEvent: SingleLiveEvent<Note> = SingleLiveEvent()
    val noteUpdatedEvent: SingleLiveEvent<Note> = SingleLiveEvent()
    val noteDeletedEvent: SingleLiveEvent<Int> = SingleLiveEvent()

    val noteDeleteRequest: SingleLiveEvent<Int> = SingleLiveEvent()
    val bookChangeRequestEvent: SingleLiveEvent<List<BookView>> = SingleLiveEvent()

    var notePayload: NotePayload? = null

    val propertyNames = MutableLiveData<List<String>>()

    private var originalHash: Long = 0L

    fun loadData() {
        App.EXECUTORS.diskIO().execute {
            val book = dataRepository.getBookView(bookId)

            val note = dataRepository.getNoteView(noteId)

            // If creating a new note under specific one include that note too
            val ancestors = if (place == Place.UNDER) {
                dataRepository.getNoteAndAncestors(noteId)
            } else {
                dataRepository.getNoteAncestors(noteId)
            }

            if (isNew()) {
                notePayload = NoteBuilder.newPayload(App.getAppContext(), title.orEmpty(), content)
            } else {
                notePayload = dataRepository.getNotePayload(noteId)
            }

            // Calculate payload's hash once for the original note
            notePayload?.let { payload ->
                App.EXECUTORS.mainThread().execute {
                    if (originalHash == 0L) {
                        originalHash = notePayloadHash(payload)
                    }
                }
            }

            attachments.postValue(loadAttachments(notePayload))
            notePayload?.attachments = attachments.value!!
            maybeUpdatePayloadForAttachment()

            bookView.postValue(book)

            noteDetailsDataEvent.postValue(NoteDetailsData(book, note, ancestors))

            propertyNames.postValue(dataRepository.getNotePropertyNames())
        }
    }

    private fun loadAttachments(notePayload: NotePayload?): List<NoteAttachmentData> {
        val context = App.getAppContext()
        val list = mutableListOf<NoteAttachmentData>()
        if (attachmentUri != null) {
            list.add(createNoteAttachmentData(context, attachmentUri, true))
        }
        // Initially read from the the local disk.
        // Only for ID attach method. Also check for ID/DIR properties.
        if (notePayload != null && notePayload.hasEligibleIdDirectory(context)) {
            list.addAll(dataRepository.listFiles(bookId, notePayload))
        }
        return list
    }

    private fun createNoteAttachmentData(context: Context, attachmentUri: Uri, isNew: Boolean, type: NoteAttachmentData.Type? = null): NoteAttachmentData {
        val filename = BookName.getFileName(context, attachmentUri)?: "Unknown filename"
        return NoteAttachmentData(attachmentUri, filename, isNew, type = type)
    }

    fun requestNoteDelete() {
        App.EXECUTORS.diskIO().execute {
            val count = dataRepository.getNotesAndSubtreesCount(setOf(noteId))
            noteDeleteRequest.postValue(count)
        }
    }

    fun deleteNote() {
        App.EXECUTORS.diskIO().execute {
            val useCase = NoteDelete(bookId, setOf(noteId))
            catchAndPostError {
                val result = UseCaseRunner.run(useCase)
                noteDeletedEvent.postValue(result.userData as Int)
            }
        }
    }

    fun requestNoteBookChange() {
        App.EXECUTORS.diskIO().execute {
            bookChangeRequestEvent.postValue(dataRepository.getBooks())
        }
    }

    fun savePayloadToBundle(outState: Bundle) {
        notePayload?.let {
            outState.putParcelable("payload", it)
        }
    }

    fun restorePayloadFromBundle(savedInstanceState: Bundle) {
        notePayload = savedInstanceState.getParcelable("payload") as? NotePayload
    }

    fun updatePayload(
        title: String,
        content: String,
        state: String?,
        priority: String?,
        tags: List<String>,
        properties: OrgProperties) {

        notePayload = notePayload?.copy(
            title = title,
            content = content,
            state = state,
            priority = priority,
            tags = tags,
            properties = properties,
            attachments = this.attachments.value!!)
    }

    fun updatePayloadState(state: String?) {
        notePayload?.let {
            notePayload = NoteBuilder.changeState(App.getAppContext(), it, state)
        }
    }

    fun updatePayloadScheduledTime(range: OrgRange?) {
        notePayload = notePayload?.copy(scheduled = range?.toString())
    }

    fun updatePayloadDeadlineTime(range: OrgRange?) {
        notePayload = notePayload?.copy(deadline = range?.toString())
    }

    fun updatePayloadClosedTime(range: OrgRange?) {
        notePayload = notePayload?.copy(closed = range?.toString())
    }

    private fun updatePayloadCreateIdProperty() {
        if (notePayload?.properties!!.containsKey("ID")) {
            return
        }
        notePayload = notePayload?.copy()
        val idStr = UUID.randomUUID().toString().uppercase()
        notePayload?.properties!!.put("ID", idStr)
    }

    /**
     * Adds one attachment. This may update payload (ID property).
     */
    fun addAttachment(attachmentUri: Uri, type: NoteAttachmentData.Type? = null) {
        val data = createNoteAttachmentData(App.getAppContext(), attachmentUri, true, type)
        attachments.value = attachments.value!! + data
        maybeUpdatePayloadForAttachment(type)
    }

    private fun maybeUpdatePayloadForAttachment(type: NoteAttachmentData.Type? = null) {
        // Auto generate ID property if it has attachment.
        val shouldAddId = if (type != null) {
            type == NoteAttachmentData.Type.COPY_TO_ID
        } else {
            // Fallback to preference for initial attachment or when type is not specified
            AppPreferences.attachMethod(App.getAppContext()) == ShareActivity.ATTACH_METHOD_COPY_ID
        }

        if (attachments.value!!.isNotEmpty() && shouldAddId) {
            updatePayloadCreateIdProperty()
        }
    }

    private fun createNote(postSave: ((note: Note) -> Unit)?) {
        val notePlace = if (place != Place.UNSPECIFIED)
            NotePlace(bookId, noteId, place)
        else
            NotePlace(bookId)

        notePayload?.let { payload ->
            App.EXECUTORS.diskIO().execute {
                catchAndPostError {
                    val result = UseCaseRunner.run(NoteCreate(payload, notePlace))
                    val note = result.userData as Note

                    // Update note ID after creating note
                    noteId = note.id

                    if (postSave != null) {
                        postSave(note)
                    } else {
                        noteCreatedEvent.postValue(note)
                    }
                }
            }
        }
    }

    private fun updateNote(postSave: ((note: Note) -> Unit)?) {
        notePayload?.let { payload ->
            App.EXECUTORS.diskIO().execute {
                catchAndPostError {
                    val result = UseCaseRunner.run(NoteUpdate(bookId, noteId, payload))
                    val note = result.userData as Note

                    if (postSave != null) {
                        postSave(note)
                    } else {
                        noteUpdatedEvent.postValue(note)
                    }
                }
            }
        }
    }

    /**
     * Hash used to detect note modifications.
     * TODO: Avoid generating org
     */
    private fun notePayloadHash(payload: NotePayload): Long {
        val head = OrgMapper.toOrgHead(payload)

        val parserWriter = OrgParserWriter()
        val str = parserWriter.whiteSpacedHead(head, 1, false)

        return MiscUtils.sha1(str)
    }

    fun isNoteModified(): Boolean {
        val payload = notePayload

        return if (payload != null) {
            notePayloadHash(payload) != originalHash
        } else {
            false
        }
    }

    fun isNew(): Boolean {
        return place != null
    }

    fun hasInitialTitleData(): Boolean {
        return !TextUtils.isEmpty(initialData.title)
    }

    fun setBook(b: BookView) {
        bookId = b.book.id
        bookView.value = b
    }

    fun followBookBreadcrumb() {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                UseCaseRunner.run(BookScrollToNote(noteId))
            }
        }
    }

    fun followNoteBreadcrumb(ancestor: Note) {
        when (AppPreferences.breadcrumbsTarget(App.getAppContext())) {
            "note_details" ->
                MainActivity.openSpecificNote(bookId, ancestor.id)

            "book_and_sparse_tree" ->
                App.EXECUTORS.diskIO().execute {
                    UseCaseRunner.run(BookSparseTreeForNote(ancestor.id))
                }

            "book_and_scroll" ->
                App.EXECUTORS.diskIO().execute {
                    UseCaseRunner.run(BookScrollToNote(ancestor.id))
                }

        }

    }

    fun saveNote(postSave: ((note: Note) -> Unit)? = null) {
        if (isBookSet() && isTitleValid()) {
            if (isNew()) {
                createNote(postSave)
            } else {
                updateNote(postSave)
            }
        }
    }

    private fun isTitleValid(): Boolean {
        return if (TextUtils.isEmpty(notePayload?.title)) {
            snackBarMessage.postValue(R.string.title_can_not_be_empty)
            false
        } else {
            true
        }
    }

    private fun isBookSet(): Boolean {
        return if (bookId == 0L) {
            snackBarMessage.postValue(R.string.note_book_not_set)
            false
        } else {
            true
        }
    }

    companion object {
    }
}