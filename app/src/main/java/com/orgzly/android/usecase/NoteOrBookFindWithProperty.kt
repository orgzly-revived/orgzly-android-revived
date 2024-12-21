package com.orgzly.android.usecase

import com.orgzly.android.data.DataRepository

class NoteOrBookFindWithProperty(val name: String, val value: String) : UseCase() {
    override fun run(dataRepository: DataRepository): UseCaseResult {
        val noteOrBook = dataRepository.findNoteOrBookHavingProperty(name, value)

        return UseCaseResult(
                userData = noteOrBook
        )
    }
}