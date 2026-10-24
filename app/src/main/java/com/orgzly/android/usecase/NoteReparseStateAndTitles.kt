package com.orgzly.android.usecase

import android.content.Intent
import com.orgzly.android.AppIntent
import com.orgzly.android.data.DataRepository

class NoteReparseStateAndTitles : UseCase() {
    override fun run(dataRepository: DataRepository): UseCaseResult {
        val updated = dataRepository.reParseNotesStateAndTitles()

        return UseCaseResult(
                modifiesLocalData = true,
                triggersSync = if (updated > 0) SYNC_DATA_MODIFIED else SYNC_NOT_REQUIRED
        )
    }

    override fun toAction(): String {
        return AppIntent.ACTION_REPARSE_NOTES
    }
}