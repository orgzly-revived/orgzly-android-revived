package com.orgzly.android.ui.notes

import com.orgzly.android.App
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.usecase.NoteUpdateClockingState
import com.orgzly.android.usecase.NoteUpdateDeadlineTime
import com.orgzly.android.usecase.NoteUpdateScheduledTime
import com.orgzly.android.usecase.NoteUpdateState
import com.orgzly.android.usecase.NoteUpdateStateToggle
import com.orgzly.android.usecase.UseCase
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.org.datetime.OrgDateTime

/**
 * Operations available in every [NotesFragment],
 * such as BookFragment, SearchFragment or AgendaFragment.
 */
abstract class NotesViewModel : CommonViewModel() {
    fun changeState(noteIds: Set<Long>, state: String?) {
        runUseCase(NoteUpdateState(noteIds, state))
    }

    fun toggleState(noteIds: Set<Long>) {
        runUseCase(NoteUpdateStateToggle(noteIds))
    }

    fun updateScheduledTime(noteIds: Set<Long>, time: OrgDateTime?) {
        runUseCase(NoteUpdateScheduledTime(noteIds, time))
    }

    fun updateDeadlineTime(noteIds: Set<Long>, time: OrgDateTime?) {
        runUseCase(NoteUpdateDeadlineTime(noteIds, time))
    }

    fun clockIn(noteIds: Set<Long>) {
        runUseCase(NoteUpdateClockingState(noteIds, 0))
    }

    fun clockOut(noteIds: Set<Long>) {
        runUseCase(NoteUpdateClockingState(noteIds, 1))
    }

    fun clockCancel(noteIds: Set<Long>) {
        runUseCase(NoteUpdateClockingState(noteIds, 2))
    }

    protected fun runUseCase(useCase: UseCase) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                UseCaseRunner.run(useCase)
            }
        }
    }
}
