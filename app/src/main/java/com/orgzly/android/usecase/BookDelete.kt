package com.orgzly.android.usecase

import com.orgzly.android.data.DataRepository

class BookDelete(val bookIds: Set<Long>, val deleteLinked: Boolean) : UseCase() {
    override fun run(dataRepository: DataRepository): UseCaseResult {
        for (bookId in bookIds) {
            val book = dataRepository.getBookView(bookId) ?: throw NotFound()
            dataRepository.deleteBook(book, deleteLinked)
        }
        return UseCaseResult(
                modifiesLocalData = true,
                triggersSync = SYNC_DATA_MODIFIED
        )
    }

    class NotFound: Throwable()
}