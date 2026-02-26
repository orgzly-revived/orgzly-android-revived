package com.orgzly.android.usecase

import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.note.NotePayload
import java.lang.IllegalStateException

class NoteUpdate(val bookId: Long, val noteId: Long, val notePayload: NotePayload) : UseCase() {
    override fun run(dataRepository: DataRepository): UseCaseResult {
        val note = dataRepository.updateNote(noteId, notePayload)
                ?: throw IllegalStateException("Note not found")

        // TODO: Handle attachment deletions during note updates.
        notePayload.attachments.forEach {
            if (it.isNew && !it.isDeleted) {
                dataRepository.storeAttachment(bookId, notePayload, it)
            }
        }

        return UseCaseResult(
                modifiesLocalData = true,
                triggersSync = SYNC_DATA_MODIFIED,
                userData = note)
    }
}
