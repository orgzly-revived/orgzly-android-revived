package com.orgzly.android.ui.notes.book

import com.orgzly.android.App
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.usecase.BookUpdatePreface
import com.orgzly.android.usecase.UseCaseRunner
import java.util.concurrent.atomic.AtomicBoolean

class BookPrefaceViewModel : CommonViewModel() {
    val prefaceUpdatedEvent: SingleLiveEvent<Unit> = SingleLiveEvent()

    /** Repeated taps would otherwise save, and close the editor, more than once. */
    private val isSaving = AtomicBoolean(false)

    fun updatePreface(bookId: Long, preface: String) {
        if (!isSaving.compareAndSet(false, true)) {
            return
        }

        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                try {
                    UseCaseRunner.run(BookUpdatePreface(bookId, preface))
                } catch (e: Throwable) {
                    // Can be saved again once the error is shown
                    isSaving.set(false)
                    throw e
                }

                prefaceUpdatedEvent.postValue(Unit)
            }
        }
    }
}
