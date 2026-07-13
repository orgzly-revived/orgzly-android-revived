package com.orgzly.android.usecase

import com.orgzly.android.db.NotesClipboard
import com.orgzly.android.data.DataRepository

class NoteCut(val bookId: Long, val ids: Set<Long>) : UseCase() {
    override fun run(dataRepository: DataRepository): UseCaseResult {
        val clipboard = NotesClipboard.create(dataRepository, ids, isCut = true).apply {
            save()
        }

        return UseCaseResult(
                userData = clipboard
        )
    }
}