package com.orgzly.android.usecase

import com.orgzly.android.db.NotesClipboard
import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.Place

class NotePaste(val bookId: Long, val noteId: Long, val place: Place) : UseCase() {
    override fun run(dataRepository: DataRepository): UseCaseResult {
        val clipboard = NotesClipboard.load()

        val cutEntries = clipboard.entries.filter { it.isCut }
        cutEntries
                .groupBy { it.note.position.bookId }
                .forEach { (sourceBookId, entries) ->
                    val entryIds = entries.map { it.note.id }.toSet()
                    val rootIds = entries
                            .filter { it.note.position.parentId !in entryIds }
                            .map { it.note.id }
                            .toSet()
                    dataRepository.deleteNotes(sourceBookId, rootIds)
                }

        val count = dataRepository.pasteNotes(clipboard, bookId, noteId, place)

        return UseCaseResult(
                modifiesLocalData = count > 0,
                triggersSync = if (count > 0) SYNC_DATA_MODIFIED else SYNC_NOT_REQUIRED,
                userData = count)
    }
}