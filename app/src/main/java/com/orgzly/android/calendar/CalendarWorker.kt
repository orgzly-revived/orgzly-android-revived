package com.orgzly.android.calendar

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.orgzly.android.App
import com.orgzly.android.data.DataRepository
import javax.inject.Inject

class CalendarWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    @Inject
    lateinit var dataRepository: DataRepository

    override fun doWork(): Result {
        App.appComponent.inject(this)

        val calendarManager = CalendarManager(applicationContext, dataRepository)
        
        val action = inputData.getString(KEY_ACTION)
        if (action == ACTION_DELETE) {
            calendarManager.deleteCalendar()
        } else {
            calendarManager.updateCalendar()
        }

        return Result.success()
    }

    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_DELETE = "delete"
        const val ACTION_UPDATE = "update"
    }
}
